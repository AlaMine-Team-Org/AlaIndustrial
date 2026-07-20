package dev.alaindustrial.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link LevelMath} — the pure mod-XP progression math (MOD-133). Loader-free and
 * Config-free (every tunable is passed in), so it pins the level curve, rank/sub-level mapping, the
 * level-40 cap and the divide-by-zero guard exactly, independent of any in-game state.
 *
 * @implements mod-XP level thresholds, rank/sub-level decomposition, progress-to-next, MAX_LEVEL cap
 */
class LevelMathTest {

	private static final long ONE_COST = 200L;
	private static final double MULT = 1.15;

	@Test
	void levelOneIsZeroXpAndMonotonicThresholds() {
		assertEquals(0L, LevelMath.cumulativeXpForLevel(1, ONE_COST, MULT), "level 1 must sit at 0 XP");
		long prev = -1;
		for (int level = 1; level <= LevelMath.MAX_LEVEL; level++) {
			long threshold = LevelMath.cumulativeXpForLevel(level, ONE_COST, MULT);
			assertTrue(threshold > prev, "thresholds must strictly increase at level " + level);
			prev = threshold;
		}
	}

	@Test
	void levelForXpMatchesThresholds() {
		// Right at a threshold → that level; one XP below → the previous level.
		for (int level = 2; level <= LevelMath.MAX_LEVEL; level++) {
			long at = LevelMath.cumulativeXpForLevel(level, ONE_COST, MULT);
			assertEquals(level, LevelMath.levelForXp(at, ONE_COST, MULT), "exactly at threshold of level " + level);
			assertEquals(level - 1, LevelMath.levelForXp(at - 1, ONE_COST, MULT), "one XP below level " + level);
		}
	}

	@Test
	void levelIsClampedToRange() {
		assertEquals(1, LevelMath.levelForXp(0, ONE_COST, MULT), "zero XP is level 1");
		assertEquals(1, LevelMath.levelForXp(-999, ONE_COST, MULT), "negative XP clamps to level 1");
		assertEquals(LevelMath.MAX_LEVEL, LevelMath.levelForXp(Long.MAX_VALUE, ONE_COST, MULT),
				"huge XP clamps to MAX_LEVEL");
	}

	@Test
	void rankAndSubLevelDecomposition() {
		assertEquals(0, LevelMath.rankIndex(1));
		assertEquals(1, LevelMath.subLevel(1));
		assertEquals("apprentice", LevelMath.rankKey(1));
		// Level 23 → rank index 4 (Engineer), sub-level 3 (III).
		assertEquals(4, LevelMath.rankIndex(23));
		assertEquals(3, LevelMath.subLevel(23));
		assertEquals("engineer", LevelMath.rankKey(23));
		assertEquals("III", LevelMath.roman(LevelMath.subLevel(23)));
		// Level 40 → rank index 7 (Legend), sub-level 5 (V).
		assertEquals(LevelMath.RANK_COUNT - 1, LevelMath.rankIndex(40));
		assertEquals(5, LevelMath.subLevel(40));
		assertEquals("legend", LevelMath.rankKey(40));
		assertEquals("V", LevelMath.roman(5));
	}

	@Test
	void progressToNextIsFractionAndSaturatesAtMax() {
		int level = 5;
		long base = LevelMath.cumulativeXpForLevel(level, ONE_COST, MULT);
		long next = LevelMath.cumulativeXpForLevel(level + 1, ONE_COST, MULT);
		assertEquals(0.0, LevelMath.progressToNext(base, level, ONE_COST, MULT), 1e-9, "at level start = 0%");
		double mid = LevelMath.progressToNext((base + next) / 2, level, ONE_COST, MULT);
		assertTrue(mid > 0.4 && mid < 0.6, "midway should be ~50%, got " + mid);
		// At the cap there is no next level: full bar, and never a divide-by-zero.
		assertEquals(1.0, LevelMath.progressToNext(Long.MAX_VALUE, LevelMath.MAX_LEVEL, ONE_COST, MULT), 1e-9);
	}

	@Test
	void ranksCoverAllForties() {
		// Every level 1..40 maps to a valid rank key (no index out of range).
		for (int level = 1; level <= LevelMath.MAX_LEVEL; level++) {
			String key = LevelMath.rankKey(level);
			assertTrue(key != null && !key.isEmpty(), "rank key present for level " + level);
		}
		assertEquals(LevelMath.RANK_COUNT, LevelMath.RANK_KEYS.length, "8 rank keys for 8 ranks");
	}
}
