package dev.alaindustrial.block.entity;

import java.util.Locale;

/**
 * Why the incubator is not working right now (MOD-234), shown as the status line of its screen.
 *
 * <p>Every one of these was a silent stall before. The worst was {@link #OUTPUT_BLOCKED}: the
 * machine holds a rolled outcome until the slot can take it — deliberately, so waiting cannot be
 * turned into a free re-roll — but with a stack of diamonds in the result slot and slag on the dice
 * it waits forever, which from the outside looks like "duplication worked once and then broke"
 * (owner report, 2026-07-26). The rule the mod follows: if the machine is idle, its screen says why.
 *
 * <p>Ordinals travel over a {@code ContainerData} channel, so the order is part of the client/server
 * contract of one session only — nothing persists it.
 */
public enum IncubatorStatus {
	/** Working, or idle with nothing to complain about — the screen shows the mode name instead. */
	READY,
	/** No glass on top: the chamber is open. */
	NO_DOME,
	/** No mutation chip, so there is no mode to run. */
	NO_CHIP,
	/** The input slot is empty — not a fault, just nothing to do. */
	NO_INPUT,
	/** There is an item in the input, but this mode has no recipe for it. */
	NO_RECIPE,
	/** No uranium: neither a loaded charge nor an ingot to load. */
	NO_FUEL,
	/** The result (or the ash) has nowhere to go, so a finished attempt is waiting on the player. */
	OUTPUT_BLOCKED;

	private static final IncubatorStatus[] VALUES = values();

	/**
	 * Lang key of this status. Only the {@link #isBlocking() blocking} states ship one — {@link #READY}
	 * and {@link #NO_INPUT} are not faults and the screen prints the mode name for them instead, so
	 * asking those two for a key yields a string with no translation behind it.
	 */
	public String translationKey() {
		return "gui.alaindustrial.incubator.status." + name().toLowerCase(Locale.ROOT);
	}

	/** True when the screen should print this instead of the mode name. */
	public boolean isBlocking() {
		return this != READY && this != NO_INPUT;
	}

	public static IncubatorStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
	}
}
