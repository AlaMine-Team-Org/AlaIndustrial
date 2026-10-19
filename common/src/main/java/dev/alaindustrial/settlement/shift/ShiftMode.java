package dev.alaindustrial.settlement.shift;

/**
 * What a shift worker is doing right now (MOD-832, ADR-050 point 10): the answer of {@link ShiftSchedule}.
 * Each mode stands for one or two goals of the worker; the first slice has no houses, posts or professions
 * yet, so several modes share the same behaviour there — staying near the town hall and walking a little.
 *
 * <p>Minecraft-free on purpose: the L1 lane loads it ({@code ShiftScheduleTest}).
 */
public enum ShiftMode {
	/** Working hours: at the post (first slice: near the town hall). */
	WORK,
	/** Night at home, "charging the battery" in the house capsule (epic decision R7; first slice: near the hall). */
	HOME,
	/** A night-shift worker's rounds (epic decision R8, the watchman; no profession has it in the first slice). */
	PATROL,
	/** Between shifts: standing about, looking around, short walks. */
	IDLE,
	/** The town is out of oil (epic decision R8): thinks rarely, walks slowly, almost does not move. */
	HALF_SLEEP,
	/** The core has no power (epic decision R10): frozen in place, goals off — nothing of the worker ticks. */
	HIBERNATE
}
