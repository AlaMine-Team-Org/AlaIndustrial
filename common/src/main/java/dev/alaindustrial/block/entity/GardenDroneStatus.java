package dev.alaindustrial.block.entity;

import java.util.Locale;

/**
 * What the Garden Drone Station is doing right now (MOD-277) — the screen's status line and, from
 * stage 3, the colour of the status light on the drone model.
 *
 * <p>Following the rule the incubator's status enum established: <b>if the machine is idle, its
 * screen says why</b>. The distinction that matters most here is {@link #IDLE} versus
 * {@link #NO_RESOURCES} — a farm that is fully tended and a station that stopped because the seed
 * slot ran dry look identical from across the base, and only one of them wants the player's
 * attention.
 *
 * <p>Ordinals travel over a {@code ContainerData} channel <em>and</em> are persisted, so appending
 * new constants is safe but reordering the existing ones is not.
 */
public enum GardenDroneStatus {
	/** Nothing to do: every tile in range is tended. Not a fault. */
	IDLE,
	/** Flying a job right now. */
	WORKING,
	/** Would work, but the seed or fertilizer slot is empty. */
	NO_RESOURCES,
	/** Not enough EU buffered to pay for a single action. */
	NO_ENERGY,
	/** No drone docked — the station is a base station with nothing to send out. */
	NO_DRONE;

	private static final GardenDroneStatus[] VALUES = values();

	/** Lang key of this status, for the screen's status line. */
	public String translationKey() {
		return "gui.alaindustrial.garden_drone_station.status." + name().toLowerCase(Locale.ROOT);
	}

	public static GardenDroneStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
	}
}
