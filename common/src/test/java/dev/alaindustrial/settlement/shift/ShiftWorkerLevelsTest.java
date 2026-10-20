package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.stats.LevelMath;
import org.junit.jupiter.api.Test;

/**
 * The shift worker's level table (MOD-854, R70): the numbers task.md states, at the shipped knobs, worked out by the
 * same summation {@link LevelMath} uses — so a changed knob default or a changed curve shows here first.
 */
class ShiftWorkerLevelsTest {

	private static final int EU_PER_XP = 1000;
	private static final int LEVEL_ONE = 15;
	private static final double MULTIPLIER = 1.12;

	/** The shipped knobs are the ones the table below is computed for. */
	@Test
	void shippedKnobsAreTheTablesKnobs() {
		assertEquals(EU_PER_XP, SettlementConfig.shiftWorkerEuPerXp);
		assertEquals(LEVEL_ONE, SettlementConfig.shiftWorkerXpLevelOneCost);
		assertEquals(MULTIPLIER, SettlementConfig.shiftWorkerLevelXpMultiplier);
		assertEquals(20000, SettlementConfig.shiftWorkerBatteryBase);
		assertEquals(5000, SettlementConfig.shiftWorkerBatteryPerPoint);
	}

	/** EU drained to stand at a level: level 2 = 15 000, 5 = 72 000, 20 = 951 000, 40 = 10 259 000. */
	@Test
	void euToReachLevelMatchesTheTable() {
		assertEquals(0L, euFor(1));
		assertEquals(15_000L, euFor(2));
		assertEquals(32_000L, euFor(3));
		assertEquals(72_000L, euFor(5));
		assertEquals(222_000L, euFor(10));
		assertEquals(486_000L, euFor(15));
		assertEquals(951_000L, euFor(20));
		assertEquals(3_219_000L, euFor(30));
		assertEquals(10_259_000L, euFor(40));
		assertEquals(euFor(5), ShiftWorkerLevels.drainedForLevel(5));
	}

	/** The level turns exactly on the threshold, and one EU short stays below. */
	@Test
	void levelTurnsOnTheThreshold() {
		for (int level = 2; level <= LevelMath.MAX_LEVEL; level++) {
			long eu = euFor(level);
			assertEquals(level, ShiftWorkerLevels.levelFromDrained(eu, EU_PER_XP, LEVEL_ONE, MULTIPLIER));
			assertEquals(level - 1, ShiftWorkerLevels.levelFromDrained(eu - 1, EU_PER_XP, LEVEL_ONE, MULTIPLIER));
		}
		assertEquals(LevelMath.MAX_LEVEL, ShiftWorkerLevels.levelFromDrained(Long.MAX_VALUE / 2, EU_PER_XP, LEVEL_ONE,
				MULTIPLIER));
	}

	/** The highest level reached holds when a knob makes the drained EU worth less. */
	@Test
	void highestLevelHoldsAgainstAKnobChange() {
		long eu = euFor(10);
		assertEquals(10, ShiftWorkerLevels.level(eu, 10, EU_PER_XP * 4, LEVEL_ONE, MULTIPLIER));
		assertEquals(10, ShiftWorkerLevels.level(eu, 1, EU_PER_XP, LEVEL_ONE, MULTIPLIER));
	}

	/** One point per level above the first; free points never negative; capacity and health per point. */
	@Test
	void pointsCapacityAndHealth() {
		assertEquals(0, ShiftWorkerLevels.pointsForLevel(1));
		assertEquals(39, ShiftWorkerLevels.pointsForLevel(40));
		assertEquals(2, ShiftWorkerLevels.freePoints(6, 3));
		assertEquals(0, ShiftWorkerLevels.freePoints(2, 5));
		assertEquals(20_000L, ShiftWorkerLevels.capacity(0, 20000, 5000));
		assertEquals(70_000L, ShiftWorkerLevels.capacity(10, 20000, 5000));
		assertEquals(SettlementConfig.shiftWorkerMaxHealth + 6, ShiftWorkerLevels.maxHealth(3));
	}

	/** Eight ranks of five levels, keyed "rank 1" … "rank 8". */
	@Test
	void ranksOfFive() {
		assertEquals(ShiftWorkerLevels.RANK_KEY_PREFIX + "1", ShiftWorkerLevels.rankKey(1));
		assertEquals(ShiftWorkerLevels.RANK_KEY_PREFIX + "1", ShiftWorkerLevels.rankKey(5));
		assertEquals(ShiftWorkerLevels.RANK_KEY_PREFIX + "2", ShiftWorkerLevels.rankKey(6));
		assertEquals(ShiftWorkerLevels.RANK_KEY_PREFIX + "8", ShiftWorkerLevels.rankKey(40));
	}

	private static long euFor(int level) {
		return LevelMath.cumulativeXpForLevel(level, LEVEL_ONE, MULTIPLIER) * EU_PER_XP;
	}
}
