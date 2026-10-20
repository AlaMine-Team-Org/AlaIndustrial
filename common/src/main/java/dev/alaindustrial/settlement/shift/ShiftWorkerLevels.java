package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.stats.LevelMath;

/**
 * The pure arithmetic of a shift worker's profile (MOD-854, epic decisions R67–R70): level from the EU that left the
 * battery, free points, battery capacity and maximum health from the points invested. Minecraft-free on purpose — the
 * L1 lane loads it ({@code ShiftWorkerLevelsTest}) — so it takes counts and totals, never a record or an entity.
 *
 * <p><b>The same curve as the player's mastery</b> ({@link LevelMath}: 40 levels, 8 ranks of 5), with the worker's
 * own knobs: one XP for {@link SettlementConfig#shiftWorkerEuPerXp} EU drained, level 2 costs
 * {@link SettlementConfig#shiftWorkerXpLevelOneCost} XP, each next level {@link
 * SettlementConfig#shiftWorkerLevelXpMultiplier} times more. The overloads without knob arguments read the live
 * knobs.
 */
public final class ShiftWorkerLevels {

	/** Number of ranks ("rank 1 … 8"), five levels each — the player's ladder. */
	public static final int RANK_COUNT = LevelMath.RANK_COUNT;
	/** The worker's own rank names: {@code RANK_KEY_PREFIX + (rankIndex + 1)}, "rank 1" … "rank 8". */
	public static final String RANK_KEY_PREFIX = "shift_worker.alaindustrial.rank.";

	private ShiftWorkerLevels() {
	}

	/** The XP {@code euDrained} is worth: integer division of the running total, never of a delta. */
	public static long xp(long euDrained, int euPerXp) {
		return Math.max(0L, euDrained) / Math.max(1, euPerXp);
	}

	/** {@link #xp(long, int)} at the live knob. */
	public static long xp(long euDrained) {
		return xp(euDrained, SettlementConfig.shiftWorkerEuPerXp);
	}

	/** The level the drained EU alone reaches, 1..{@link LevelMath#MAX_LEVEL}. */
	public static int levelFromDrained(long euDrained, int euPerXp, int levelOneCost, double multiplier) {
		return LevelMath.levelForXp(xp(euDrained, euPerXp), levelOneCost, multiplier);
	}

	/**
	 * The worker's level: what the drained EU reaches, but never below {@code highestLevel} — a knob change must not
	 * lower a worker (the player-profile lesson). Only a return (R39) lowers it, by lowering both inputs.
	 */
	public static int level(long euDrained, int highestLevel, int euPerXp, int levelOneCost, double multiplier) {
		int reached = levelFromDrained(euDrained, euPerXp, levelOneCost, multiplier);
		return Math.max(1, Math.min(LevelMath.MAX_LEVEL, Math.max(reached, highestLevel)));
	}

	/** {@link #level(long, int, int, int, double)} at the live knobs. */
	public static int level(long euDrained, int highestLevel) {
		return level(euDrained, highestLevel, SettlementConfig.shiftWorkerEuPerXp,
				SettlementConfig.shiftWorkerXpLevelOneCost, SettlementConfig.shiftWorkerLevelXpMultiplier);
	}

	/** Total XP needed to stand at {@code level}, at the live knobs; level 1 is 0. */
	public static long xpForLevel(int level) {
		return LevelMath.cumulativeXpForLevel(level, SettlementConfig.shiftWorkerXpLevelOneCost,
				SettlementConfig.shiftWorkerLevelXpMultiplier);
	}

	/** EU that must have left the battery to stand at {@code level}, at the live knobs. */
	public static long drainedForLevel(int level) {
		return xpForLevel(level) * Math.max(1, SettlementConfig.shiftWorkerEuPerXp);
	}

	/** Points the levels above the first give: one per level (R69). */
	public static int pointsForLevel(int level) {
		return Math.max(0, Math.min(LevelMath.MAX_LEVEL, level) - 1);
	}

	/** Points not yet invested; never negative (a lowered knob cannot take an investment back). */
	public static int freePoints(int level, int invested) {
		return Math.max(0, pointsForLevel(level) - Math.max(0, invested));
	}

	/** Battery capacity with {@code batteryPoints} in the «Battery» stat (R68). */
	public static long capacity(int batteryPoints, int base, int perPoint) {
		return Math.max(1L, (long) base + (long) Math.max(0, batteryPoints) * Math.max(0, perPoint));
	}

	/** {@link #capacity(int, int, int)} at the live knobs. */
	public static long capacity(int batteryPoints) {
		return capacity(batteryPoints, SettlementConfig.shiftWorkerBatteryBase,
				SettlementConfig.shiftWorkerBatteryPerPoint);
	}

	/** Maximum health with {@code strengthPoints} in the «Strength» stat, at the live knobs. */
	public static int maxHealth(int strengthPoints) {
		return Math.max(1, SettlementConfig.shiftWorkerMaxHealth
				+ Math.max(0, strengthPoints) * Math.max(0, SettlementConfig.shiftWorkerHealthPerPoint));
	}

	/** Rank index 0..7 of a level. */
	public static int rankIndex(int level) {
		return LevelMath.rankIndex(level);
	}

	/** The lang key of a level's rank — "rank N", N = 1..8. */
	public static String rankKey(int level) {
		return RANK_KEY_PREFIX + (rankIndex(level) + 1);
	}
}
