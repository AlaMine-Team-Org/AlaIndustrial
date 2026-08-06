package dev.alaindustrial.core.environment;

/**
 * The altitude profile of the wind (MOD-347) — one curve, shared by every consumer of wind in the
 * mod: all three wind mills convert it into EU, and the Wind Gauge shows it to the player. Pure
 * arithmetic, no Minecraft imports, unit-testable at L1.
 *
 * <p><b>Why a curve and not a ramp.</b> Until MOD-347 the wind was
 * {@code clamp((y − seaLevel) / blocksPerBase, 0, maxBase)}: it rose in steps and then sat at its cap
 * forever. Every height above the cap was identical, so "how high should this tower be" had exactly
 * one answer — "at least the cap, then stop caring". The gauge exposed how flat that was: at Y 250 it
 * read the same maximum as at Y 127.
 *
 * <p><b>The shape.</b> Four segments, all continuous, none of them flat inside the range a player
 * builds in:
 * <ol>
 *   <li><b>Calm</b> — at or below sea level: 0. Ground level has no usable wind.</li>
 *   <li><b>Climb</b> — sea level → the branch's <i>ridge</i>: a {@code t^0.75} rise to
 *       {@code ridgeFactor} of full strength. Fast at first, easing off; the shape is what makes the
 *       numbers land on values like 24.7 and 47.1 instead of a tidy arithmetic sequence.</li>
 *   <li><b>Shoulder</b> — ridge → the cloud deck: an accelerating climb to full strength. The
 *       windiest air in the world sits in a narrow jet just under the clouds.</li>
 *   <li><b>Thinning</b> — cloud deck → the dead height: a collapse to {@code traceFactor}, steepest
 *       right above the clouds, because overshooting the jet has to cost something immediately. Past
 *       the dead height only that trace remains, which rounds to 0 EU/t for every branch.</li>
 * </ol>
 *
 * <p><b>The ridge is per-branch.</b> {@code ridgeY = seaLevel + maxBase × blocksPerBase} — exactly
 * where each mill used to hit its cap before this change, so the branches keep their own approach to
 * the jet while sharing the shoulder and the thinning above it. Note that this currently separates
 * only the Tempest Mill (6 × 16 = +96): the T1 mill (4 × 16) and the Sky Mill (8 × 8) both work out
 * to +64 and therefore share a ridge. That coincidence predates MOD-347 — those two branches differ
 * by their cloud-deck base (4 vs 8) and their weather factors, not by altitude.
 *
 * <p><b>Two properties are load-bearing, and they pull against each other.</b> The gauge must change
 * on every single block of height, so no segment may be flat; and the band of heights that yields a
 * mill's full output must stay narrow, or choosing a height stops being a decision and the gauge
 * stops being worth carrying. {@link #SHOULDER_EXPONENT} is the knob between them.
 * {@code WindProfileTest} asserts both, so a retune cannot quietly trade one away for the other.
 */
public final class WindProfile {
	/**
	 * Curvature of the shoulder. Above 1 the climb accelerates towards the cloud deck, which is what
	 * keeps the maximum-output band narrow: with a linear shoulder the top of the curve was so flat
	 * that a T1 mill produced its cap anywhere from Y 127 to Y 194 — sixty-seven interchangeable
	 * heights, and a gauge with nothing useful to say about any of them. At 1.6 that band is eleven
	 * blocks wide.
	 *
	 * <p>Raising it further narrows the band more but flattens the curve just above the ridge, where
	 * the gauge would then repeat itself for several blocks in a row. 1.6 is the largest value that
	 * keeps every single block distinguishable at the shipped peak speed; {@code WindProfileTest}
	 * asserts both properties, so a retune cannot quietly break either.
	 */
	private static final double SHOULDER_EXPONENT = 1.6;

	/**
	 * Curvature of the thinning. Below 1 the collapse is fastest immediately above the cloud deck, so
	 * overshooting the jet costs output at once rather than gently — "above the clouds there is
	 * nothing" has to be felt within a few blocks to be a real decision.
	 */
	private static final double THINNING_EXPONENT = 0.6;

	/**
	 * Climb length per base point for every branch that does not configure its own — the T1 mill, the
	 * Tempest Mill, and the Wind Gauge, which must scale to the T1 ridge to predict the T1 mill. Only
	 * the Sky Mill overrides it ({@code Config.highAltWindMillBlocksPerBase}). Kept here rather than as
	 * a literal at each of those three call sites: they are only correct while they agree, and a
	 * gauge that disagreed with the mill would mispredict it silently.
	 */
	public static final int DEFAULT_BLOCKS_PER_BASE = 16;

	private WindProfile() {
	}

	/**
	 * Wind strength at {@code y} as a fraction of the world's strongest wind, in {@code [0, 1]}.
	 *
	 * @param y            the measured Y level
	 * @param seaLevel     the world's sea level — the calm floor the profile is measured from
	 * @param ridgeY       where this consumer's climb segment ends (see {@link #ridgeY})
	 * @param cloudY       the cloud deck: the windiest height ({@code Config.windCloudY})
	 * @param deadY        the height above which only a trace remains ({@code Config.windDeadY})
	 * @param ridgeFactor  fraction of full strength reached at {@code ridgeY} ({@code Config.windRidgeFactor})
	 * @param traceFactor  fraction left above {@code deadY} ({@code Config.windTraceFactor})
	 */
	public static float factor(int y, int seaLevel, int ridgeY, int cloudY, int deadY,
			float ridgeFactor, float traceFactor) {
		// Guard the ordering the segments assume; a hand-edited config must not produce NaN or a
		// negative-width segment, it must degrade to something monotone and boring.
		if (ridgeY <= seaLevel || cloudY <= ridgeY || deadY <= cloudY) {
			return degenerate(y, seaLevel, cloudY, ridgeFactor, traceFactor);
		}
		if (y <= seaLevel) {
			return 0.0f;
		}
		if (y >= deadY) {
			return traceFactor;
		}
		if (y <= ridgeY) {
			float t = (float) (y - seaLevel) / (ridgeY - seaLevel);
			return ridgeFactor * (float) Math.pow(t, 0.75);
		}
		if (y <= cloudY) {
			float t = (float) (y - ridgeY) / (cloudY - ridgeY);
			return ridgeFactor + (1.0f - ridgeFactor) * (float) Math.pow(t, SHOULDER_EXPONENT);
		}
		float t = (float) (y - cloudY) / (deadY - cloudY);
		return 1.0f - (1.0f - traceFactor) * (float) Math.pow(t, THINNING_EXPONENT);
	}

	/**
	 * The height at which a consumer's climb segment ends — {@code seaLevel + maxBase × blocksPerBase},
	 * i.e. the exact height at which that mill used to reach its cap before MOD-347. Clamped to stay
	 * below the cloud deck so a config with a very long climb cannot swallow the shoulder.
	 */
	public static int ridgeY(int seaLevel, int maxBase, int blocksPerBase, int cloudY) {
		int span = Math.max(1, maxBase * Math.max(1, blocksPerBase));
		return Math.min(seaLevel + span, cloudY - 1);
	}

	/** Fallback for a config whose heights are out of order: a plain ramp, never NaN. */
	private static float degenerate(int y, int seaLevel, int cloudY, float ridgeFactor, float traceFactor) {
		if (y <= seaLevel) {
			return 0.0f;
		}
		int span = Math.max(1, cloudY - seaLevel);
		float t = Math.min(1.0f, (float) (y - seaLevel) / span);
		return Math.max(traceFactor, ridgeFactor * t);
	}
}
