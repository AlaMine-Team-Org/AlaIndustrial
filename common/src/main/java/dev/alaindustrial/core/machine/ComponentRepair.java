package dev.alaindustrial.core.machine;

/**
 * Pure repair arithmetic for a generator's consumable component (MOD-384) — the counterpart to
 * {@link ComponentWear}. The repair bench resets a worn rotor/wheel's damage to zero, but each
 * repair permanently lowers the durability ceiling of <em>that specific stack</em>, so a component
 * can be nursed along a few times and then has to be replaced. Kept free of any Minecraft type so it
 * links and runs on the L1 (plain-JUnit) classpath; the caller
 * ({@code ComponentRepairBenchBlockEntity}) does the {@code ItemStack}/component side effects.
 *
 * <p><b>The decay is linear in the ORIGINAL ceiling, not compounding on the current one.</b> With a
 * 20 % step a 1000-point rotor goes 1000 → 800 → 600 → 400 → 200, never 1000 → 800 → 640 → 512. Two
 * reasons, and the first one is the requirement:
 *
 * <ul>
 *   <li>The spec asks for a share <em>of the original</em> — compounding would already be the wrong number
 *       on the second repair.</li>
 *   <li>{@link #maxDamageAfter} is a pure function of {@code (baseMaxDamage, step, repairsDone)}
 *       rather than of the previous ceiling, so it cannot accumulate rounding error across repairs
 *       and it re-derives the same answer from scratch every time. That also makes it self-healing:
 *       a stack whose stored ceiling was somehow lost still lands on the correct value from its
 *       repair counter alone.</li>
 * </ul>
 *
 * <p><b>Integer percent, not a float factor</b>, for the same reason: {@code base × 0.8f} four times
 * over is three chances to drift off a whole number, while {@code base − base × step × n / 100} is
 * exact for every input the mod can produce.
 *
 * <p><b>The decay IS the limit — there is no separate counter.</b> An earlier version carried a
 * configured {@code repairBenchMaxRepairs} on top of the decay, and it was the wrong shape twice
 * over: it refused a component that still had 60 % of its ceiling, which reads as an arbitrary rule
 * rather than as wear, and it was a second knob that could silently disagree with the first (raise
 * the step to 25 % and a count of 4 would still wave through a repair that zeroes the part).
 * {@link #maxRepairs} now derives the allowance from the step, so "you lose a share each time, until
 * there is nothing left to restore" is the whole rule.
 */
public final class ComponentRepair {
	private ComponentRepair() {
	}

	/** A ceiling can never fall below this — a 0-durability item would be broken on arrival. */
	private static final int MIN_CEILING = 1;

	/**
	 * The durability ceiling a component carries after {@code repairsDone} repairs.
	 *
	 * @param baseMaxDamage the component grade's registered durability (the value
	 *     {@link ComponentTier#maxDamage()} reports — i.e. what a freshly crafted one has)
	 * @param decayPercentPerRepair how much of the ORIGINAL ceiling one repair costs, in percent
	 *     (clamped to 0..100)
	 * @param repairsDone repairs already performed on this stack (negative is read as 0)
	 * @return the new ceiling, never below {@link #MIN_CEILING} and never above {@code baseMaxDamage}
	 */
	public static int maxDamageAfter(int baseMaxDamage, int decayPercentPerRepair, int repairsDone) {
		if (baseMaxDamage <= MIN_CEILING) {
			return Math.max(MIN_CEILING, baseMaxDamage);
		}
		int step = Math.clamp(decayPercentPerRepair, 0, 100);
		int repairs = Math.max(0, repairsDone);
		// Clamp the TOTAL decay rather than the per-step one: a mis-configured step × a large repair
		// count must saturate at "all of it gone", not wrap into a negative ceiling.
		int totalPercent = (int) Math.min(100L, (long) step * repairs);
		int lost = (int) ((long) baseMaxDamage * totalPercent / 100L);
		return Math.max(MIN_CEILING, baseMaxDamage - lost);
	}

	/**
	 * How many times a component may ever be repaired at a given decay step — the largest {@code n}
	 * for which {@code n} repairs still leave something of the ceiling standing.
	 *
	 * <p>Derived, not configured: at 20 % a part gets 4 repairs (1000 → 800 → 600 → 400 → 200, and a
	 * fifth would zero it), at 10 % it gets 9. {@code 99 / step} expresses exactly "the last step that
	 * keeps the total decay under 100 %", including the awkward divisors — 33 % yields 3, not the 4 a
	 * rounded-up division would hand out.
	 *
	 * @param decayPercentPerRepair the step, in percent of the ORIGINAL ceiling
	 * @return the allowance; {@link #UNLIMITED_REPAIRS} when the step is zero or negative, because a
	 *     decay of nothing never exhausts the part (an operator's explicit choice, not a default)
	 */
	public static int maxRepairs(int decayPercentPerRepair) {
		if (decayPercentPerRepair <= 0) {
			return UNLIMITED_REPAIRS;
		}
		return (100 - 1) / Math.min(100, decayPercentPerRepair);
	}

	/** Returned by {@link #maxRepairs} when the configured decay is zero — repairs never run out. */
	public static final int UNLIMITED_REPAIRS = Integer.MAX_VALUE;

	/**
	 * Whether a component repaired {@code repairsDone} times may be repaired again. The bench refuses
	 * beyond the allowance without spending EU or material, and its screen says why.
	 *
	 * @param repairsDone repairs already performed on this stack
	 * @param maxRepairs the allowance, from {@link #maxRepairs(int)}
	 */
	public static boolean canRepair(int repairsDone, int maxRepairs) {
		return maxRepairs > 0 && Math.max(0, repairsDone) < maxRepairs;
	}
}
