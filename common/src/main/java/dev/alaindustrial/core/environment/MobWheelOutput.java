package dev.alaindustrial.core.environment;

/**
 * Pure arithmetic of the mob wheel (MOD-763, decisions D4 and D8): "this mob, this pace, this tired → EU/t".
 * Minecraft-free so the L1 lane can test it; {@code MobWheelBlockEntity.produce()} calls it, so the tested
 * code is the production code.
 *
 * <p>Output of a running mob =
 * {@code floor(power × euMultiplier × bonusFactor × pace × (0.5 + 0.5 × staminaFraction))}, with
 * {@code euMultiplier = mobWheelEuMultiplierPercent / 100} and {@code bonusFactor = (100 + bonusPercent) / 100}
 * while the species' bonus condition holds, 1 otherwise. Rounded DOWN, once, at the very end: a fresh night
 * zombie at its fastest pace makes {@code floor(7 × 1.5 × 1.2) = 12} — exactly a copper cable's throughput,
 * never one more. A mob that is not running — paused, exhausted, distracted, absent — gives nothing.
 */
public final class MobWheelOutput {
	private MobWheelOutput() {
	}

	/**
	 * Absorbs the binary representation error of products such as {@code 10.5 × 1.2} before the floor, so an
	 * exact {@code 5.0} that came out as {@code 4.9999999} still floors to 5. Far below any real fraction.
	 */
	private static final double EPSILON = 1e-9;

	/**
	 * EU/t of a running mob.
	 *
	 * @param power             the species' base power in EU/t (≤ 0 gives 0)
	 * @param multiplierPercent the roster-wide EU multiplier in percent (≤ 0 gives 0)
	 * @param bonusPercent      extra percent while the species' bonus holds; pass 0 otherwise (negative is 0)
	 * @param pace              pace multiplier for this tick (≤ 0 is a pause and gives 0)
	 * @param staminaFraction   how much stamina is left, {@code 0..1} (clamped)
	 */
	public static int euFor(int power, int multiplierPercent, int bonusPercent, double pace,
			double staminaFraction) {
		if (power <= 0 || multiplierPercent <= 0 || !(pace > 0)) {
			return 0;
		}
		double fraction = Math.max(0.0, Math.min(1.0, staminaFraction));
		double scale = multiplierPercent / 100.0 * (100 + Math.max(0, bonusPercent)) / 100.0;
		return (int) Math.floor(power * scale * pace * (0.5 + 0.5 * fraction) + EPSILON);
	}

	/**
	 * EU/t of a mob of the given species with the configured knobs — the call the block entity makes.
	 *
	 * @param running whether the mob is actually running this tick (gate closed, mob present, not exhausted,
	 *                not distracted)
	 * @param bonus   whether the species' bonus condition holds where the wheel stands
	 */
	public static int euFor(MobWheelProfile profile, boolean running, boolean bonus, int runTick, long seed,
			double staminaFraction) {
		if (!running) {
			return 0;
		}
		return euFor(profile.basePowerEuPerTick(), GeneratorConfig.mobWheelEuMultiplierPercent,
				bonus ? GeneratorConfig.mobWheelBonusPercent : 0, profile.pace(runTick, seed), staminaFraction);
	}

	/** The most EU/t a mob of this species ever makes: fresh, at its fastest pace, with its bonus. */
	public static int peakEuPerTick(MobWheelProfile profile) {
		int bonus = profile.bonus() == MobWheelProfile.Bonus.NONE ? 0 : GeneratorConfig.mobWheelBonusPercent;
		return euFor(profile.basePowerEuPerTick(), GeneratorConfig.mobWheelEuMultiplierPercent, bonus,
				profile.maxPace(), 1.0);
	}

	/** The most EU/t any species makes — the size of the drive's one-tick buffer. */
	public static int peakEuPerTick() {
		int peak = 0;
		for (MobWheelProfile profile : MobWheelProfile.values()) {
			peak = Math.max(peak, peakEuPerTick(profile));
		}
		return peak;
	}
}
