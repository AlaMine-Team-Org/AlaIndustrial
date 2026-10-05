package dev.alaindustrial.block.entity.machine;

/**
 * The four GUI sync channels every machine sends, at indices 0..3 (MOD-235, MOD-712 BE-7): its energy, its
 * capacity, and its progress bar. A machine with channels of its own declares them in an enum of its own
 * that starts with these four names, in this order — {@link SyncChannels.Builder} refuses one that does not,
 * so a menu may read a base channel through this enum whatever machine it is looking at.
 *
 * <p>A generator reuses the progress pair for its own readout (production and mode); the index is the
 * contract, the name says what the base writes there.
 */
public enum MachineChannels {
	/** Stored EU, clamped to {@code int}; a write sets the buffer (the vanilla sync on a client stub). */
	ENERGY,
	/** Buffer capacity, clamped to {@code int}; read-only. */
	CAPACITY,
	/** Progress of the running operation in ticks. */
	PROGRESS,
	/** Length of the running operation in ticks. */
	MAX_PROGRESS
}
