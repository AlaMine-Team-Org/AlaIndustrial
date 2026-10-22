package dev.alaindustrial.settlement.works;

import java.util.Objects;
import java.util.Optional;

/**
 * One job of a town's work queue (MOD-857, epic decision R79). Immutable — the queue stores a new one in place of the
 * old on every change.
 *
 * <p>Minecraft-free on purpose: the queue and its L1 test carry it.
 *
 * @param id       the job's number in its town, never reused
 * @param type     what it asks for
 * @param building the index of the building it works on, in the town's building list
 * @param status   where it is
 * @param taker    who works on it — an opaque id the executor chooses (a drone's nest position, a worker's number);
 *                 empty unless {@link WorkJobStatus#TAKEN}
 * @param progress how far it got — opaque to the queue, meaningful to the executor (an index into the building's
 *                 cells); kept when the job is given back or blocked, so the next taker goes on from there
 * @param reason   why it stands still, present only while {@link WorkJobStatus#BLOCKED}
 * @param repair   it mends a building that was restored before (owner's round 4 of stage 6): its stages are not news,
 *                 the hall says «damaged, the drones mend it» and «mended» instead
 */
public record WorkJob(int id, WorkJobType type, int building, WorkJobStatus status, String taker, int progress,
		Optional<BlockReason> reason, boolean repair) {

	public WorkJob {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(status, "status");
		taker = status == WorkJobStatus.TAKEN ? Objects.requireNonNull(taker, "taker") : "";
		reason = status == WorkJobStatus.BLOCKED ? reason : Optional.empty();
		progress = Math.max(0, progress);
	}

	/** A job that is no repair: a first restoration's, or one read from a save written before repairs. */
	public WorkJob(int id, WorkJobType type, int building, WorkJobStatus status, String taker, int progress,
			Optional<BlockReason> reason) {
		this(id, type, building, status, taker, progress, reason, false);
	}

	/** A new job, queued. */
	static WorkJob queued(int id, WorkJobType type, int building, boolean repair) {
		return new WorkJob(id, type, building, WorkJobStatus.QUEUED, "", 0, Optional.empty(), repair);
	}

	WorkJob with(WorkJobStatus newStatus, String newTaker, int newProgress, Optional<BlockReason> newReason) {
		return new WorkJob(id, type, building, newStatus, newTaker, newProgress, newReason, repair);
	}
}
