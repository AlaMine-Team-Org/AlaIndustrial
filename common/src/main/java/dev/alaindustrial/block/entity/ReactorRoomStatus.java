package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.structure.RoomScan;
import java.util.Locale;

/**
 * What the Reactor Controller's screen is reporting about its room (MOD-468, stage 1).
 *
 * <p>This is the player-facing face of {@link RoomScan.Status}: the same states, but as a sync-safe
 * ordinal with a translation key and a notion of whether the message points at a position worth
 * showing. Keeping it separate from the scanner's own enum is deliberate — {@link RoomScan} is
 * MC-free and L1-tested, and it must not grow a dependency on lang keys or on the wire format.
 *
 * <p><b>The order of the constants is the wire format</b> (the ordinal travels to the screen on a
 * sync channel), so new states must be appended, never inserted.
 *
 * <p>That wire is <em>session-local</em>: the status is never written to disk (the controller's
 * {@code saveAdditional} stores the box, the heat and the throttle — not this), so a renumbering is
 * visible only to a client talking to a server built from other code, and never to a saved world.
 */
public enum ReactorRoomStatus {
	/** The room is sealed, sized and has a way in. Stage 1's definition of success. */
	FORMED,
	/** The controller faces no interior: buried, in the floor or ceiling, in an edge, or turned around. */
	CONTROLLER_NOT_IN_WALL,
	/** A wall is missing far enough that the scan never found the far side. */
	ROOM_UNBOUNDED,
	/** Interior below the minimum along some axis. */
	TOO_SMALL,
	/** Interior above the maximum along some axis. */
	TOO_LARGE,
	/** A hole in the shell — the one status whose position the player really needs. */
	BREACH,
	/** There is a door, but no two-high doorway standing on the floor. */
	NO_DOORWAY,
	/** Two controllers share one shell. */
	SECOND_CONTROLLER,
	/** More of the shell is glass than the structure tolerates. */
	TOO_MUCH_GLASS;

	private static final ReactorRoomStatus[] VALUES = values();

	public String translationKey() {
		return "gui.alaindustrial.reactor_controller.status." + name().toLowerCase(Locale.ROOT);
	}

	/** Whether the player has something to fix — the screen prints those in red. */
	public boolean needsAttention() {
		return this != FORMED;
	}

	/**
	 * Whether the reported position is a place in the world the player should walk to. False for the
	 * statuses whose "position" is only the controller itself, where an offset would be noise.
	 */
	public boolean hasLocation() {
		// ROOM_UNBOUNDED is deliberately NOT here. Its "position" is wherever the ray gave up, up to 13
		// blocks out — open ground, usually, with nothing built on it. The first playtest saw a lone
		// controller spitting smoke into a hillside a dozen blocks away, which is worse than silence:
		// it points at a spot where there is nothing to fix.
		return this == BREACH || this == SECOND_CONTROLLER
				|| this == CONTROLLER_NOT_IN_WALL
				|| this == TOO_MUCH_GLASS;
	}

	/** Whether the measured interior box is worth printing (the size statuses). */
	public boolean hasSize() {
		return this == FORMED || this == TOO_SMALL || this == TOO_LARGE;
	}

	public static ReactorRoomStatus byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : CONTROLLER_NOT_IN_WALL;
	}

	/** Maps the scanner's verdict onto the reported state. Total by construction — no default arm. */
	public static ReactorRoomStatus of(RoomScan.Status status) {
		return switch (status) {
			case FORMED -> FORMED;
			case CONTROLLER_NOT_IN_WALL -> CONTROLLER_NOT_IN_WALL;
			case ROOM_UNBOUNDED -> ROOM_UNBOUNDED;
			case TOO_SMALL -> TOO_SMALL;
			case TOO_LARGE -> TOO_LARGE;
			case BREACH -> BREACH;
			case NO_DOORWAY -> NO_DOORWAY;
			case SECOND_CONTROLLER -> SECOND_CONTROLLER;
			case TOO_MUCH_GLASS -> TOO_MUCH_GLASS;
		};
	}
}
