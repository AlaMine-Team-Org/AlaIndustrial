package dev.alaindustrial.block.entity;

import dev.alaindustrial.Industrialization;

/**
 * Why a reactor is producing nothing (MOD-468, stage 3).
 *
 * <p>A built, sealed, fuelled reactor that sits silent is the hardest state this machine can put a
 * player in, and the panel used to answer it with a dash. Each of these has a different fix — a lever
 * to place, a rod to rack, a slider to move, a cable to run — so the readout names the one that
 * applies instead of leaving the player to try all four.
 *
 * <p>Sent over the menu's {@code ContainerData} as an ordinal, so the order is persisted in exactly
 * the way a blockstate is: append, never reorder.
 */
public enum ReactorIdleReason {

	/** Not idle at all — the output row shows a figure instead. */
	RUNNING,
	/** The shell does not pass its scan; the status headline above already says which way it failed. */
	NOT_SEALED,
	/** No rods racked in any column. */
	NO_FUEL,
	/** The throttle is at zero: the control rods are all the way out. */
	RODS_WITHDRAWN,
	/** No redstone signal on the controller — this is the scram, and it is holding. */
	NO_SIGNAL,
	/** Nothing is drawing power and the buffer is full. Idling here costs no uranium. */
	BUFFER_FULL;

	private static final ReactorIdleReason[] VALUES = values();

	/** Out-of-range ordinals answer {@link #RUNNING}: a stale packet must not invent a fault. */
	public static ReactorIdleReason byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : RUNNING;
	}

	public String translationKey() {
		return "gui." + Industrialization.MOD_ID + ".reactor_controller.idle."
				+ name().toLowerCase(java.util.Locale.ROOT);
	}
}
