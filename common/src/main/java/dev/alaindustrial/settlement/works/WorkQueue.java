package dev.alaindustrial.settlement.works;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

/**
 * The work queue of one ALA town (MOD-857, epic decision R79): the jobs its core holds and the rules by which an
 * executor — a builder drone (MOD-858), later a shift worker — takes one, reports progress, gives it back, says why it
 * cannot go on, and finishes it; and by which the town's owner raises, starts again and calls off a job (R95 — the
 * owner check itself is the caller's).
 *
 * <p><b>Executors are a resource, not the jobs' owners.</b> A job knows its taker only while it is taken. A job given
 * back, or blocked, keeps its {@link WorkJob#progress()} and goes back to waiting; the next taker goes on from there.
 * An executor that disappears — a drone whose nest was broken — gives its job back through {@link #releaseTaker}.
 *
 * <p><b>The rules.</b>
 * <ul>
 *   <li>One open job per type and building: a second one is refused ({@link #raise}).</li>
 *   <li>One job per taker: a taker holding a job gets that job again from {@link #takeNext}, and is refused another
 *       by {@link #take}.</li>
 *   <li>One taker per building (R91: one drone, one building): a job is not handed out while another taker holds a
 *       job on the same building.</li>
 *   <li>The debris first (R85): a building job is not handed out while a clearing job of the same building is
 *       open.</li>
 *   <li>{@link #takeNext} hands out queued jobs only, oldest first. A blocked job waits until {@link #unblock} — the
 *       owner's «start again», or the code that removed the cause — or until an executor takes it by number.</li>
 *   <li>Only the taker reports, gives back, blocks or finishes its job; a job called off meanwhile answers the taker
 *       {@link Outcome#NOT_YOURS}, which is how it learns to stop.</li>
 *   <li>Finished and called-off jobs stay as history, the newest {@link #HISTORY} of them.</li>
 * </ul>
 *
 * <p>Minecraft-free on purpose: the L1 lane checks every rule ({@code WorkQueueTest}). Server thread only; every
 * change calls the hook installed by {@link #onChange}.
 */
public final class WorkQueue {

	/** How many finished and called-off jobs a town keeps. */
	public static final int HISTORY = 16;

	/** What a call on the queue came to. */
	public enum Outcome {
		/** Done as asked. */
		OK,
		/** No job under that number. */
		UNKNOWN_JOB,
		/** The caller is not the job's taker — or the job is not taken any more. */
		NOT_YOURS,
		/** The job's status does not allow that: it is not open, not blocked, or taken by somebody else. */
		WRONG_STATUS,
		/**
		 * The taker already holds another job, or another taker works on the same building, or the building's debris
		 * is not cleared yet.
		 */
		BUSY
	}

	private final List<WorkJob> jobs = new ArrayList<>();
	private int nextId;
	private Runnable onChange = () -> { };

	/** An empty queue. */
	public WorkQueue() {
		this(List.of(), 1);
	}

	/** A queue read back from a save: its jobs and the number the next job gets. */
	public WorkQueue(Collection<WorkJob> saved, int nextId) {
		int highest = 0;
		for (WorkJob job : saved) {
			if (job.id() > 0 && job(job.id()).isEmpty()) {
				jobs.add(job);
				highest = Math.max(highest, job.id());
			}
		}
		jobs.sort((a, b) -> Integer.compare(a.id(), b.id()));
		this.nextId = Math.max(Math.max(1, nextId), highest + 1);
	}

	/** The hook called after every change. */
	public void onChange(Runnable hook) {
		this.onChange = hook;
	}

	/** Every job, open and history, in number order. */
	public List<WorkJob> jobs() {
		return Collections.unmodifiableList(jobs);
	}

	/** Whether the queue holds no job at all. */
	public boolean isEmpty() {
		return jobs.isEmpty();
	}

	/** The number the next job gets. */
	public int nextId() {
		return nextId;
	}

	/** The job numbered {@code id}. */
	public Optional<WorkJob> job(int id) {
		for (WorkJob job : jobs) {
			if (job.id() == id) {
				return Optional.of(job);
			}
		}
		return Optional.empty();
	}

	/** The open job of {@code type} on {@code building}, if there is one. */
	public Optional<WorkJob> open(WorkJobType type, int building) {
		for (WorkJob job : jobs) {
			if (job.status().open() && job.type() == type && job.building() == building) {
				return Optional.of(job);
			}
		}
		return Optional.empty();
	}

	/** The job {@code taker} holds, if any. */
	public Optional<WorkJob> heldBy(String taker) {
		for (WorkJob job : jobs) {
			if (job.status() == WorkJobStatus.TAKEN && job.taker().equals(taker)) {
				return Optional.of(job);
			}
		}
		return Optional.empty();
	}

	/**
	 * Raises a job of {@code type} on {@code building}, queued: the new job, or empty when one of that type is already
	 * open on that building.
	 */
	public Optional<WorkJob> raise(WorkJobType type, int building) {
		return raise(type, building, false);
	}

	/**
	 * Raises a job as {@link #raise(WorkJobType, int)} does, marked as a {@linkplain WorkJob#repair() repair} of a
	 * building restored before when {@code repair} is set.
	 */
	public Optional<WorkJob> raise(WorkJobType type, int building, boolean repair) {
		if (open(type, building).isPresent()) {
			return Optional.empty();
		}
		WorkJob job = WorkJob.queued(nextId++, type, building, repair);
		jobs.add(job);
		changed();
		return Optional.of(job);
	}

	/**
	 * Hands {@code taker} the oldest queued job of one of {@code types} that it may take now (see the class rules) —
	 * or the job it already holds, whatever its type. Empty when there is none.
	 */
	public Optional<WorkJob> takeNext(String taker, Set<WorkJobType> types) {
		return takeNext(taker, types, building -> 0);
	}

	/**
	 * Hands out as {@link #takeNext(String, Set)} does, but in the town owner's order (round 2 of 0.1.219, Q3): the
	 * job whose building ranks first by {@code rank} — lower first — and within one building the oldest.
	 */
	public Optional<WorkJob> takeNext(String taker, Set<WorkJobType> types, IntUnaryOperator rank) {
		Optional<WorkJob> held = heldBy(taker);
		if (held.isPresent()) {
			return held;
		}
		for (WorkJob job : ranked(rank)) {
			if (job.status() == WorkJobStatus.QUEUED && types.contains(job.type()) && free(job, taker)) {
				return Optional.of(store(job.with(WorkJobStatus.TAKEN, taker, job.progress(), Optional.empty())));
			}
		}
		return Optional.empty();
	}

	/** The jobs by {@code rank} of their building, then by number. */
	private List<WorkJob> ranked(IntUnaryOperator rank) {
		List<WorkJob> out = new ArrayList<>(jobs);
		out.sort((a, b) -> {
			int byRank = Integer.compare(rank.applyAsInt(a.building()), rank.applyAsInt(b.building()));
			return byRank != 0 ? byRank : Integer.compare(a.id(), b.id());
		});
		return out;
	}

	/**
	 * Hands {@code taker} the oldest blocked job of one of {@code types} that it may take now (see the class rules),
	 * its reason gone — an executor trying again whether the cause went away. Empty when there is none, or when the
	 * taker already holds a job. Whoever blocked the job does not matter: the executor that stopped it may be gone, or
	 * may have lost track of it while a clearing of the same building went first (owner's round 3).
	 */
	public Optional<WorkJob> takeBlocked(String taker, Set<WorkJobType> types) {
		return takeBlocked(taker, types, building -> 0);
	}

	/** Hands out as {@link #takeBlocked(String, Set)} does, in the owner's order of the buildings ({@code rank}). */
	public Optional<WorkJob> takeBlocked(String taker, Set<WorkJobType> types, IntUnaryOperator rank) {
		if (heldBy(taker).isPresent()) {
			return Optional.empty();
		}
		for (WorkJob job : ranked(rank)) {
			if (job.status() == WorkJobStatus.BLOCKED && types.contains(job.type()) && free(job, taker)) {
				return Optional.of(store(job.with(WorkJobStatus.TAKEN, taker, job.progress(), Optional.empty())));
			}
		}
		return Optional.empty();
	}

	/** {@code taker} takes job {@code id} by number — a queued or a blocked one; a blocked job's reason goes. */
	public Outcome take(int id, String taker) {
		Optional<WorkJob> found = job(id);
		if (found.isEmpty()) {
			return Outcome.UNKNOWN_JOB;
		}
		WorkJob job = found.get();
		if (job.status() == WorkJobStatus.TAKEN) {
			return job.taker().equals(taker) ? Outcome.OK : Outcome.WRONG_STATUS;
		}
		if (!job.status().open()) {
			return Outcome.WRONG_STATUS;
		}
		if (heldBy(taker).isPresent() || !free(job, taker)) {
			return Outcome.BUSY;
		}
		store(job.with(WorkJobStatus.TAKEN, taker, job.progress(), Optional.empty()));
		return Outcome.OK;
	}

	/** The taker reports how far it got; the job stays taken. */
	public Outcome progress(int id, String taker, int cursor) {
		return byTaker(id, taker, job -> job.with(WorkJobStatus.TAKEN, taker, cursor, Optional.empty()));
	}

	/** The taker gives the job back: queued again, its progress kept. */
	public Outcome giveBack(int id, String taker, int cursor) {
		return byTaker(id, taker, job -> job.with(WorkJobStatus.QUEUED, "", cursor, Optional.empty()));
	}

	/** The taker cannot go on, for {@code reason}: blocked, its progress kept, nobody holds it. */
	public Outcome block(int id, String taker, BlockReason reason, int cursor) {
		return byTaker(id, taker, job -> job.with(WorkJobStatus.BLOCKED, "", cursor, Optional.of(reason)));
	}

	/** The taker finished the job. */
	public Outcome complete(int id, String taker) {
		Outcome outcome = byTaker(id, taker,
				job -> job.with(WorkJobStatus.DONE, "", job.progress(), Optional.empty()));
		if (outcome == Outcome.OK) {
			trimHistory();
		}
		return outcome;
	}

	/** A blocked job goes back to waiting — the owner's «start again», or the cause went away. */
	public Outcome unblock(int id) {
		Optional<WorkJob> found = job(id);
		if (found.isEmpty()) {
			return Outcome.UNKNOWN_JOB;
		}
		WorkJob job = found.get();
		if (job.status() != WorkJobStatus.BLOCKED) {
			return Outcome.WRONG_STATUS;
		}
		store(job.with(WorkJobStatus.QUEUED, "", job.progress(), Optional.empty()));
		return Outcome.OK;
	}

	/** Calls off an open job; its taker, if any, learns it on its next call. */
	public Outcome cancel(int id) {
		Optional<WorkJob> found = job(id);
		if (found.isEmpty()) {
			return Outcome.UNKNOWN_JOB;
		}
		WorkJob job = found.get();
		if (!job.status().open()) {
			return Outcome.WRONG_STATUS;
		}
		store(job.with(WorkJobStatus.CANCELLED, "", job.progress(), Optional.empty()));
		trimHistory();
		return Outcome.OK;
	}

	/** Every job {@code taker} holds goes back to waiting with its progress — the executor is gone. How many. */
	public int releaseTaker(String taker) {
		int released = 0;
		for (WorkJob job : List.copyOf(jobs)) {
			if (job.status() == WorkJobStatus.TAKEN && job.taker().equals(taker)) {
				store(job.with(WorkJobStatus.QUEUED, "", job.progress(), Optional.empty()));
				released++;
			}
		}
		return released;
	}

	/** Whether {@code job} may go to {@code taker} now: nobody else on its building, its debris cleared. */
	private boolean free(WorkJob job, String taker) {
		for (WorkJob other : jobs) {
			if (other.id() == job.id() || other.building() != job.building()) {
				continue;
			}
			if (other.status() == WorkJobStatus.TAKEN && !other.taker().equals(taker)) {
				return false;
			}
			if (job.type() == WorkJobType.BUILD && other.type() == WorkJobType.CLEAR && other.status().open()) {
				return false;
			}
			// The road before the house (MOD-861, R108): a building job waits while the building's road job is open.
			if (job.type() == WorkJobType.BUILD && other.type() == WorkJobType.ROAD && other.status().open()) {
				return false;
			}
		}
		return true;
	}

	private Outcome byTaker(int id, String taker, UnaryOperator<WorkJob> change) {
		Optional<WorkJob> found = job(id);
		if (found.isEmpty()) {
			return Outcome.UNKNOWN_JOB;
		}
		WorkJob job = found.get();
		if (job.status() != WorkJobStatus.TAKEN || !job.taker().equals(taker)) {
			return Outcome.NOT_YOURS;
		}
		store(change.apply(job));
		return Outcome.OK;
	}

	private WorkJob store(WorkJob job) {
		for (int i = 0; i < jobs.size(); i++) {
			if (jobs.get(i).id() == job.id()) {
				if (!jobs.get(i).equals(job)) {
					jobs.set(i, job);
					changed();
				}
				return job;
			}
		}
		throw new IllegalStateException("no job " + job.id());
	}

	/** Drops the oldest history beyond {@link #HISTORY}. */
	private void trimHistory() {
		int finished = 0;
		for (WorkJob job : jobs) {
			if (!job.status().open()) {
				finished++;
			}
		}
		int i = 0;
		while (i < jobs.size() && finished > HISTORY) {
			if (jobs.get(i).status().open()) {
				i++;
			} else {
				jobs.remove(i);
				finished--;
				changed();
			}
		}
	}

	private void changed() {
		onChange.run();
	}
}
