package dev.alaindustrial.block.entity;

import java.util.Locale;

/**
 * Why the Recycler is not chewing right now (MOD-145).
 *
 * <p>The machine has two upkeep gates nothing else in the mod has — a blade that wears out and an ash bin
 * that fills — and both of them stop it silently unless the screen says so. A farm that stopped without
 * a reason reads as a bug; the wind mill learned this first and answers with its own reason code.
 *
 * <p><b>The order of the constants is the wire format</b> (the ordinal travels on a
 * {@code ContainerData} channel), so new states are appended, never inserted. {@link #READY} stays at
 * ordinal 0 because a client menu stub starts its data array at zeroes.
 */
public enum RecyclerStatus {
	/** Working, or able to work. Draws no status line. */
	READY,
	/** No blades installed — the machine cannot run at all. */
	NO_BLADES,
	/** Nothing to chew. Silent by choice: the empty slot is right there. */
	NO_INPUT,
	/** The ash bin is full; empty it and the machine resumes. */
	ASH_FULL,
	/** The briquette slot cannot take the next casting. */
	OUTPUT_FULL,
	/** The buffer is empty. */
	NO_ENERGY;

	private final String key = "status.alaindustrial.recycler." + name().toLowerCase(Locale.ROOT);

	/** Translation key of this status line. */
	public String key() {
		return key;
	}

	/** Whether this state prints a line at all. */
	public boolean isSilent() {
		return this == READY || this == NO_INPUT;
	}

	public static RecyclerStatus byOrdinal(int ordinal) {
		RecyclerStatus[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : READY;
	}
}
