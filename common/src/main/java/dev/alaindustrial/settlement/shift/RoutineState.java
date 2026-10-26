package dev.alaindustrial.settlement.shift;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One shift worker's day as it goes (MOD-889): the activity it does now, the switch it waits to make, its progress
 * toward the place it walks to, and the targets it could not plan a path to lately. Kept on the entity, not saved:
 * after a reload the worker takes up its day where the clock is.
 *
 * <p><b>Finish what you do</b> (RimWorld's rule, research of stage 5): a switch waits while the worker is busy — on its
 * way somewhere, or running from a mob — but never longer than {@link #FINISH_CAP} ticks.
 *
 * <p><b>Not stuck in an activity</b> (competitor research, idea #6; R32): a worker that walks to a place and gets no
 * {@link #PROGRESS_STEP} closer to it for {@link #STUCK_TICKS} ticks gives the place up until its next activity — it
 * is never moved there by any other means (idea #7: no teleport). The routine writes one line of the hall's log for it.
 *
 * <p><b>Unreachable targets</b> (research of stage 5, «what to avoid» 2): a target the worker could not plan a path
 * toward waits {@link #BACKOFF_BASE} ticks before the next try, doubling up to {@link #BACKOFF_MAX}; a path that gets
 * there clears it. A few targets are remembered, the oldest forgotten first.
 *
 * <p>Minecraft-free on purpose: the L1 lane tests it ({@code RoutineStateTest}). Positions are packed longs.
 */
public final class RoutineState {

	/** No target: a packed position no block ever has. */
	public static final long NONE = Long.MIN_VALUE;
	/** The longest a switch waits for the worker to finish what it does, in ticks. */
	public static final int FINISH_CAP = 200;
	/** Ticks without progress toward a place after which the worker gives the place up (the epic's K). */
	public static final int STUCK_TICKS = 600;
	/** How much closer, in blocks, counts as progress. */
	public static final double PROGRESS_STEP = 1.0;
	/** The first wait after a failed path, in ticks. */
	public static final int BACKOFF_BASE = 40;
	/** The longest wait after failed paths, in ticks. */
	public static final int BACKOFF_MAX = 1_200;
	/** How many unreachable targets a worker remembers. */
	static final int BACKOFF_MEMORY = 4;

	/** What became of a step toward the place the worker walks to. */
	public enum Progress {
		/** On its way, or there. */
		ON_WAY,
		/** No progress for {@link #STUCK_TICKS}: the place is given up now, once. */
		STUCK,
		/** The place was given up earlier in this activity. */
		GIVEN_UP
	}

	private @Nullable RoutineActivity activity;
	private long activitySince;
	private long pendingSince = -1L;

	private int rank;
	private long rankDay = Long.MIN_VALUE;

	private long goal = NONE;
	private double best;
	private long lastProgressAt;
	private long givenUp = NONE;
	private long spot = NONE;

	private final Map<Long, long[]> backoff = new LinkedHashMap<>();
	private int pathsPlanned;
	private boolean chargingByDay;

	/** The activity the worker does now; null before its first think. */
	public @Nullable RoutineActivity activity() {
		return activity;
	}

	/** Game time the current activity began at. */
	public long activitySince() {
		return activitySince;
	}

	/**
	 * Whether the worker switches to {@code wanted} on this think: at once on its first think, else once it is not
	 * {@code busy}, or {@link #FINISH_CAP} ticks after the switch fell due. False when it already does {@code wanted}.
	 */
	public boolean shouldSwitch(RoutineActivity wanted, long now, boolean busy) {
		if (activity == null) {
			return true;
		}
		if (wanted == activity) {
			pendingSince = -1L;
			return false;
		}
		if (pendingSince < 0) {
			pendingSince = now;
		}
		return !busy || now - pendingSince >= FINISH_CAP;
	}

	/** Starts {@code next} at {@code now}: what the last activity gave up and where it stood is forgotten. */
	public void adopt(RoutineActivity next, long now) {
		activity = next;
		activitySince = now;
		pendingSince = -1L;
		goal = NONE;
		givenUp = NONE;
		spot = NONE;
	}

	/** The worker's rank in its town, as last counted. */
	public int rank() {
		return rank;
	}

	/** Whether the rank was counted on day {@code day}; ranks are counted once a day. */
	public boolean rankCountedOn(long day) {
		return rankDay == day;
	}

	public void setRank(int rank, long day) {
		this.rank = rank;
		this.rankDay = day;
	}

	/** A spot the current activity keeps to (where the break began, where a given-up walk ended); NONE if none. */
	public long spot() {
		return spot;
	}

	public void setSpot(long packed) {
		this.spot = packed;
	}

	/**
	 * One step toward {@code target}, {@code distance} blocks away, at {@code now}. A new target starts the count
	 * again; a step {@link #PROGRESS_STEP} closer than the best so far is progress.
	 */
	public Progress track(long target, double distance, long now) {
		if (target == givenUp) {
			return Progress.GIVEN_UP;
		}
		if (target != goal) {
			goal = target;
			best = distance;
			lastProgressAt = now;
			return Progress.ON_WAY;
		}
		if (distance <= best - PROGRESS_STEP) {
			best = distance;
			lastProgressAt = now;
			return Progress.ON_WAY;
		}
		if (now - lastProgressAt >= STUCK_TICKS) {
			givenUp = target;
			goal = NONE;
			return Progress.STUCK;
		}
		return Progress.ON_WAY;
	}

	/** The worker is where it should be, or stood still on purpose: the count starts again from the next step. */
	public void arrived() {
		goal = NONE;
	}

	/** The worker paused its walk for a reason of its own (a mob to run from): the wait does not count. */
	public void paused(long now) {
		lastProgressAt = now;
	}

	/** Whether {@code target} was given up in this activity. */
	public boolean gaveUp(long target) {
		return target != NONE && target == givenUp;
	}

	/** Whether the worker may plan a path toward {@code target} at {@code now}, or must wait out a backoff. */
	public boolean mayPlan(long target, long now) {
		long[] entry = backoff.get(target);
		return entry == null || now >= entry[1];
	}

	/** A path toward {@code target} was not found at {@code now}: the next try waits longer. */
	public void planFailed(long target, long now) {
		long[] entry = backoff.remove(target);
		long failures = entry == null ? 1 : entry[0] + 1;
		long wait = Math.min(BACKOFF_MAX, (long) BACKOFF_BASE << Math.min(30, failures - 1));
		backoff.put(target, new long[] {failures, now + wait});
		while (backoff.size() > BACKOFF_MEMORY) {
			backoff.remove(backoff.keySet().iterator().next());
		}
	}

	/** A path toward {@code target} was found: it is no longer remembered as unreachable. */
	public void planFound(long target) {
		backoff.remove(target);
	}

	/** How many failed tries in a row {@code target} has, 0 if none. */
	public int failures(long target) {
		long[] entry = backoff.get(target);
		return entry == null ? 0 : (int) entry[0];
	}

	/** Counts a path planned (a diagnostic, read by the tests and the tick budget). */
	public void notePathPlanned() {
		pathsPlanned++;
	}

	/** How many paths the worker has planned since it joined the world. */
	public int pathsPlanned() {
		return pathsPlanned;
	}

	/** Whether the worker went to its bed by day, its battery all but empty (R156), rather than for the night. */
	public boolean chargingByDay() {
		return chargingByDay;
	}

	public void setChargingByDay(boolean byDay) {
		this.chargingByDay = byDay;
	}

	/**
	 * How many ticks, on average, the worker waits between two short walks in {@code mode}: its activity's, while the
	 * activity's mode is the one the town lets it have; the planner's otherwise (half-sleep).
	 */
	public int strollInterval(ShiftMode mode) {
		return activity != null && activity.mode() == mode ? activity.strollInterval()
				: ShiftSchedule.strollInterval(mode);
	}
}
