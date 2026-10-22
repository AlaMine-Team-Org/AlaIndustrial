package dev.alaindustrial.settlement.works;

import java.util.Locale;

/**
 * Where a job of a town's work queue is (MOD-857, epic decision R79).
 *
 * <p>Minecraft-free on purpose: the L1 lane reads it.
 */
public enum WorkJobStatus {
	/** Waiting for a taker. */
	QUEUED,
	/** A taker works on it; nobody else may. */
	TAKEN,
	/** Its last taker could not go on, and said why ({@link BlockReason}); it waits for the cause to go away. */
	BLOCKED,
	/** Finished. Kept for a while as history. */
	DONE,
	/** Called off by the town's owner. Kept for a while as history. */
	CANCELLED;

	/** Whether the job still waits for work: queued, taken or blocked. */
	public boolean open() {
		return this == QUEUED || this == TAKEN || this == BLOCKED;
	}

	/** The name in saves and in translation keys: {@code queued}, {@code taken}, … */
	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The status saved as {@code name}, or {@link #QUEUED} for a name this build does not know. */
	public static WorkJobStatus byName(String name) {
		for (WorkJobStatus status : values()) {
			if (status.serializedName().equals(name)) {
				return status;
			}
		}
		return QUEUED;
	}
}
