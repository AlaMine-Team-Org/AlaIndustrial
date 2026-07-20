package dev.alaindustrial.stats;

/**
 * Pure math of the MOD-133 mod-XP progression: XP → level, level → rank/sub-level, progress to the
 * next level. No Minecraft or Config dependency — every tunable is passed in — so it is fully unit
 * testable (L1 JUnit) and the same numbers drive the GUI, the level-up sound and the docs.
 *
 * <p><b>Model.</b> There are {@link #MAX_LEVEL} = 40 levels, grouped into {@link #RANK_COUNT} = 8
 * named ranks of {@link #SUBLEVELS_PER_RANK} = 5 sub-levels each (Apprentice I..V, Journeyman I..V,
 * …, Legend I..V). Level 1 sits at 0 XP. The XP cost to advance from level {@code k} to {@code k+1}
 * is {@code round(xpLevelOneCost × levelXpMultiplier^(k-1))} — an exponential curve. Thresholds are
 * summed iteratively (not via a closed form) so the rounding matches exactly what the tests assert.
 *
 * <p>XP itself is never stored: it is derived from the two career EU totals — machine work plus the
 * much weaker generator trickle — by {@link #xpOf}. See {@link dev.alaindustrial.stats.PlayerModStats}.
 */
public final class LevelMath {
	/** Highest reachable level; also the length of the progression. */
	public static final int MAX_LEVEL = 40;
	/** Sub-levels per rank (shown as Roman numerals I..V). */
	public static final int SUBLEVELS_PER_RANK = 5;
	/** Number of named ranks (Apprentice … Legend). */
	public static final int RANK_COUNT = MAX_LEVEL / SUBLEVELS_PER_RANK;

	/** Lang-key suffixes for the eight ranks, indexed by {@link #rankIndex(int)}. */
	public static final String[] RANK_KEYS = {
			"apprentice", "journeyman", "technician", "operator",
			"engineer", "architect", "magnate", "legend"
	};

	private LevelMath() {
	}

	/**
	 * Career XP derived from the two career EU totals: hands-on machine work plus the deliberately
	 * weaker generator trickle ({@code euPerXpGenerated} is much larger than {@code euPerXp}, because a
	 * generator runs unattended). Lives here rather than on {@code PlayerModStats} so it stays free of
	 * Minecraft types and testable on the L1 lane.
	 *
	 * <p>Each term divides its own <em>running total</em>, never a per-event delta — incremental
	 * integer division would truncate every contribution to nothing (one machine operation is worth
	 * ~0.2–0.3 XP at the shipped rates). Both divisors are clamped to ≥1 so the function is total.
	 */
	public static long xpOf(long euUsefulConsumedTotal, long euProducedTotal,
			int euPerXp, int euPerXpGenerated) {
		return euUsefulConsumedTotal / Math.max(1, euPerXp)
				+ euProducedTotal / Math.max(1, euPerXpGenerated);
	}

	/**
	 * Cumulative XP required to be <em>at</em> the given level (1-based). Level 1 → 0. Computed by
	 * summing per-level costs {@code round(xpLevelOneCost × multiplier^(k-1))} for k = 1..level-1, so
	 * it is monotic and rounding-stable. {@code level} is clamped to {@code [1, MAX_LEVEL]}.
	 */
	public static long cumulativeXpForLevel(int level, long xpLevelOneCost, double levelXpMultiplier) {
		int target = Math.max(1, Math.min(MAX_LEVEL, level));
		long total = 0;
		double cost = xpLevelOneCost; // cost of level 1 → 2
		for (int k = 1; k < target; k++) {
			total += Math.max(1, Math.round(cost));
			cost *= levelXpMultiplier;
		}
		return total;
	}

	/**
	 * The level for a given XP total: the highest level whose cumulative threshold XP has been
	 * reached, clamped to {@code [1, MAX_LEVEL]}. Beyond the level-40 threshold the result stays 40.
	 */
	public static int levelForXp(long xp, long xpLevelOneCost, double levelXpMultiplier) {
		long clampedXp = Math.max(0, xp);
		int level = 1;
		while (level < MAX_LEVEL
				&& cumulativeXpForLevel(level + 1, xpLevelOneCost, levelXpMultiplier) <= clampedXp) {
			level++;
		}
		return level;
	}

	/**
	 * Progress toward the next level in {@code [0, 1]}: the fraction of XP earned between the current
	 * level's threshold and the next level's. At {@link #MAX_LEVEL} there is no next level, so this
	 * returns {@code 1.0} (the GUI shows a full bar) — never a divide-by-zero.
	 */
	public static double progressToNext(long xp, int level, long xpLevelOneCost, double levelXpMultiplier) {
		if (level >= MAX_LEVEL) {
			return 1.0;
		}
		long base = cumulativeXpForLevel(level, xpLevelOneCost, levelXpMultiplier);
		long next = cumulativeXpForLevel(level + 1, xpLevelOneCost, levelXpMultiplier);
		long span = next - base;
		if (span <= 0) {
			return 1.0;
		}
		double p = (double) (Math.max(0, xp) - base) / (double) span;
		return Math.max(0.0, Math.min(1.0, p));
	}

	/**
	 * The {@code euUsefulConsumedTotal} that makes total XP land exactly on {@code targetXp}, given a
	 * career production total that already contributes a generator term. Used by the {@code /ala
	 * profile set} QA command; pure, so the arithmetic — including the clamp below — is L1-testable.
	 *
	 * <p>Returns {@code -1} when the target is <em>unreachable</em>: production alone already grants
	 * more than {@code targetXp}, and the machine term cannot go negative to compensate. The caller
	 * must surface that rather than silently landing somewhere else — a QA command that reports a
	 * number it did not actually set is worse than one that refuses.
	 */
	public static long consumedForTargetXp(long targetXp, long euProducedTotal,
			int euPerXp, int euPerXpGenerated) {
		long fromGenerators = euProducedTotal / Math.max(1, euPerXpGenerated);
		if (fromGenerators > targetXp) {
			return -1L;
		}
		return (targetXp - fromGenerators) * Math.max(1, euPerXp);
	}

	/** Rank index {@code 0..RANK_COUNT-1} for a level (1-based). Apprentice = 0 … Legend = 7. */
	public static int rankIndex(int level) {
		int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
		return (clamped - 1) / SUBLEVELS_PER_RANK;
	}

	/** Sub-level {@code 1..SUBLEVELS_PER_RANK} within the rank (shown as Roman I..V). */
	public static int subLevel(int level) {
		int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
		return (clamped - 1) % SUBLEVELS_PER_RANK + 1;
	}

	/** Lang-key suffix for a level's rank (e.g. {@code "engineer"}), from {@link #RANK_KEYS}. */
	public static String rankKey(int level) {
		return RANK_KEYS[rankIndex(level)];
	}

	/** Roman numeral {@code I..V} for a sub-level {@code 1..5} — never localized (same in all languages). */
	public static String roman(int subLevel) {
		return switch (Math.max(1, Math.min(SUBLEVELS_PER_RANK, subLevel))) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> "V";
		};
	}
}
