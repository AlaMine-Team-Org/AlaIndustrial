package dev.alaindustrial.block.entity;

import java.util.Locale;

/**
 * Why the Thermal Centrifuge is not turning out shavings right now (MOD-424).
 *
 * <p>A status enum rather than a bare "no matching recipe", for the same reason the Galvanic Bath has
 * one: this machine has <em>four</em> independent gates, and two of them — the redstone signal and the
 * heater below — are not part of the recipe at all. Without separate states, a player who forgot the
 * lever and a player whose heater is still warming would both read "no recipe" and go looking in the
 * wrong place.
 *
 * <p><b>The order of the constants is the wire format</b> (the ordinal travels to the screen on a sync
 * channel), so new states must be appended, never inserted.
 */
public enum ThermalCentrifugeStatus {
	/** Everything is in place; the machine is turning out shavings. Deliberately draws no status line. */
	READY,
	/** No redstone signal: the rotor is stopped and the machine spends nothing at all. */
	NO_SIGNAL,
	/** Nothing in the input slot. */
	NO_INPUT,
	/** The input is not something this machine centrifuges. */
	NO_RECIPE,
	/** The output slot cannot take another result. */
	OUTPUT_BLOCKED,
	/** Powered and fed, but the rotor has not reached working speed yet. */
	SPINNING_UP,
	/** No Electric Heater underneath. Unlike the Vulcanizer, lava and campfires do not qualify. */
	NO_HEATER,
	/** There is a heater below, but it has not finished warming up. */
	HEATER_COLD;

	private static final ThermalCentrifugeStatus[] VALUES = values();

	public String translationKey() {
		return "gui.alaindustrial.thermal_centrifuge.status." + name().toLowerCase(Locale.ROOT);
	}

	/** Whether this state should print a status line. {@link #READY} stays silent, like the Vulcanizer's. */
	public boolean isBlocking() {
		return this != READY;
	}

	/**
	 * Whether the heater below should light up for this state — the machine's half of
	 * {@link dev.alaindustrial.core.heat.HeatConsumer#isWaitingOnHeat()}.
	 *
	 * <p>Three states qualify. {@code HEATER_COLD} is the obvious one. {@code READY} must count as well,
	 * or the heater would cool between two paid ticks of work it is actively serving. {@code SPINNING_UP}
	 * counts because the rotor and the heater ramp in parallel by design — making the heater wait for the
	 * rotor would double the startup time for no gameplay reason.
	 *
	 * <p>Everything else must NOT: {@code NO_SIGNAL} above all, since a heater that warmed under a machine
	 * switched off at the lever would burn a full ramp for nothing — exactly the promise
	 * {@code fun06LoneHeaterNeverWarmsAndSpendsNothing} pins. {@code NO_INPUT}, {@code NO_RECIPE} and
	 * {@code OUTPUT_BLOCKED} are checked before {@code SPINNING_UP} in the diagnosis for the same reason:
	 * an idling rotor with an empty hopper is not "waiting on heat".
	 */
	public boolean waitsOnHeat() {
		return this == HEATER_COLD || this == READY || this == SPINNING_UP;
	}

	public static ThermalCentrifugeStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
	}
}
