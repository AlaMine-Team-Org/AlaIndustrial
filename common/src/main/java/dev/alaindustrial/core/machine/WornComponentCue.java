package dev.alaindustrial.core.machine;

/**
 * Pure decision rules for the "this rotor / wheel is nearly spent" warning (MOD-654): when the installed
 * component of a running wind or water mill is close to breaking, the machine creaks and shows smoke or
 * splinters now and then, so the player notices the wear on the way to the tower instead of when the
 * generation stops. Free of Minecraft types so it links on the L1 classpath; the caller
 * ({@code AbstractGeneratorBlockEntity}) does the sound and particle side effects and owns the cooldown.
 *
 * <p><b>Window.</b> The warning starts when the remaining durability drops below
 * {@code min(percent% of the CURRENT max, capPoints)} (owner decision 2026-10-18). The percent is taken
 * from the current {@code getMaxDamage}, so a repaired component (whose max the repair bench lowered) is
 * judged against its own ceiling; the point cap keeps the warning window about the same length for every
 * grade, because wear time per durability point does not depend on the grade (the best grade would
 * otherwise creak for hours).
 */
public final class WornComponentCue {
	/** Share of the warning window (from its low end) in which the heavier smoke may appear. */
	public static final int CRITICAL_WINDOW_PERCENT = 25;
	/** Relative width of the random spread on every pause: a pause is 70%..130% of its nominal length. */
	/** Clouds of smoke in a rotor cue at the edge of the window / when nothing is left. */
	public static final int MIN_PUFFS = 2;
	public static final int MAX_PUFFS = 5;
	private static final float JITTER = 0.30f;

	private WornComponentCue() {
	}

	/**
	 * Remaining durability below which the warning is on: {@code min(maxDamage * percent / 100, capPoints)}.
	 * Zero when the warning is disabled or the component has no durability.
	 */
	public static int thresholdPoints(int maxDamage, int percent, int capPoints) {
		if (maxDamage <= 0 || percent <= 0 || capPoints <= 0) {
			return 0;
		}
		long byPercent = (long) maxDamage * Math.min(percent, 100) / 100L;
		return (int) Math.min(byPercent, capPoints);
	}

	/**
	 * Whether the component is worn enough to warn: it still exists ({@code damage < maxDamage}), has
	 * actually been used ({@code damage > 0}) and its remainder is strictly below the threshold.
	 */
	public static boolean isWorn(int damage, int maxDamage, int percent, int capPoints) {
		if (maxDamage <= 0 || damage <= 0 || damage >= maxDamage) {
			return false;
		}
		return maxDamage - damage < thresholdPoints(maxDamage, percent, capPoints);
	}

	/** Whether the remainder is inside the last {@link #CRITICAL_WINDOW_PERCENT} of the warning window. */
	public static boolean isCritical(int damage, int maxDamage, int percent, int capPoints) {
		if (!isWorn(damage, maxDamage, percent, capPoints)) {
			return false;
		}
		int threshold = thresholdPoints(maxDamage, percent, capPoints);
		return (long) (maxDamage - damage) * 100L <= (long) threshold * CRITICAL_WINDOW_PERCENT;
	}

	/**
	 * Nominal pause before the next cue: {@code slowTicks} at the edge of the window, shrinking linearly to
	 * {@code fastTicks} at zero remainder, so the cues come more often as the component dies. Never below 1.
	 */
	public static int nominalDelay(int damage, int maxDamage, int percent, int capPoints, int slowTicks,
			int fastTicks) {
		int threshold = thresholdPoints(maxDamage, percent, capPoints);
		if (threshold <= 0) {
			return Math.max(1, slowTicks);
		}
		int remaining = Math.max(0, Math.min(maxDamage - damage, threshold));
		float share = (float) remaining / threshold;
		int fast = Math.min(fastTicks, slowTicks);
		return Math.max(1, Math.round(fast + (slowTicks - fast) * share));
	}

	/** The pause before the next cue with its random spread; {@code roll01} is a uniform draw in [0, 1). */
	public static int nextDelay(int damage, int maxDamage, int percent, int capPoints, int slowTicks,
			int fastTicks, float roll01) {
		float factor = 1.0f - JITTER + 2.0f * JITTER * clamp01(roll01);
		return Math.max(1, Math.round(nominalDelay(damage, maxDamage, percent, capPoints, slowTicks, fastTicks)
				* factor));
	}

	/**
	 * The pause before the FIRST cue after the component crossed the threshold (or after a reload): a
	 * uniform point inside the FIRST HALF of the nominal pause (a player who has just noticed a worn part
	 * must not wait a full pause for the first sign), so a farm of mills that wore out together does not
	 * start its chorus in one tick.
	 */
	public static int firstDelay(int damage, int maxDamage, int percent, int capPoints, int slowTicks,
			int fastTicks, float roll01) {
		return Math.max(1, Math.round(
				nominalDelay(damage, maxDamage, percent, capPoints, slowTicks, fastTicks) * 0.5f
						* clamp01(roll01)));
	}

	/** Smoke clouds in one rotor cue: more of them the less is left, from {@link #MIN_PUFFS} to {@link #MAX_PUFFS}. */
	public static int puffCount(int damage, int maxDamage, int percent, int capPoints) {
		int threshold = thresholdPoints(maxDamage, percent, capPoints);
		if (threshold <= 0) {
			return MIN_PUFFS;
		}
		int remaining = Math.max(0, Math.min(maxDamage - damage, threshold));
		float wear = 1.0f - (float) remaining / threshold;
		return MIN_PUFFS + Math.round((MAX_PUFFS - MIN_PUFFS) * wear);
	}

	private static float clamp01(float v) {
		return v < 0.0f ? 0.0f : Math.min(v, 1.0f);
	}
}
