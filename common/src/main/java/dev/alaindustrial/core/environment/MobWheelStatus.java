package dev.alaindustrial.core.environment;

import dev.alaindustrial.core.machine.StatusLine;

/**
 * What a mob wheel is doing right now (MOD-763) — the drive's status row and the renderer's cue. Carried to
 * an open screen as its ordinal on the {@code MAX_PROGRESS} channel, so the order is part of the sync
 * contract: append, never reorder.
 */
public enum MobWheelStatus implements StatusLine {
	/** The 3×3×3 box is not assembled. */
	UNFORMED(true),
	/** Assembled, but nobody is shut in: the gate is open, or closed on an empty deck. */
	NO_MOB(true),
	/** The occupant runs and the wheel makes EU. */
	RUNNING(false),
	/** A villager's short stop between runs: no EU, no stamina spent. */
	PAUSED(false),
	/** Exhausted and resting with hay nearby — four times faster than without. */
	RESTING(false),
	/** Exhausted and resting slowly; hay nearby or a portion of food in the feeder helps. */
	EXHAUSTED(true),
	/** The occupant could not be found after its chunk loaded; open the gate to reset. */
	LOST(true),
	/**
	 * The occupant has a live player target within {@link MobWheelDistraction#RANGE_BLOCKS} (D9): it turns to
	 * fight or shoot instead of running — no EU, no stamina spent — until the target is gone.
	 */
	DISTRACTED(true);

	private final boolean blocking;

	MobWheelStatus(boolean blocking) {
		this.blocking = blocking;
	}

	@Override
	public String translationKey() {
		return "gui.alaindustrial.mob_wheel.status." + name().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public boolean isBlocking() {
		return blocking;
	}

	/** The status for an ordinal read off a sync channel; out of range reads as {@link #UNFORMED}. */
	public static MobWheelStatus byOrdinal(int ordinal) {
		MobWheelStatus[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : UNFORMED;
	}
}
