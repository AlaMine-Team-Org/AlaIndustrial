package dev.alaindustrial.core.upgrade;

/**
 * The overclocker chip's arithmetic (MOD-392), kept free of Minecraft types so it can be unit-tested
 * and mutation-tested directly instead of only through a running machine.
 *
 * <p>The trade the chip makes is deliberately asymmetric: each chip multiplies the duration by
 * {@code speedFactor} (&lt; 1) and the draw by {@code euFactor} (&gt; 1), and because
 * {@code speedFactor × euFactor > 1} the energy PER OPERATION rises with every chip. That is what
 * separates the chip from the mod's global speed knob, which is energy-neutral by design.
 */
public final class OverclockMath {

	private OverclockMath() {
	}

	/**
	 * How many chips a machine can actually use: the largest {@code n} for which
	 * {@code baseEuPerTick × euFactor^n} still fits inside {@code tierVoltage}, never more than
	 * {@code ceiling}.
	 *
	 * <p>Stepped rather than derived from a logarithm on purpose. {@link #euPerTick} steps the same
	 * way, so both agree exactly at the boundary; {@code floor(log)} can land on the other side of a
	 * tie for a retuned factor and hand out a chip the tier cannot feed.
	 *
	 * @return 0 when the machine cannot be overclocked at all (no ceiling, a factor that does not
	 *     grow, or a base rate that already sits at the tier limit)
	 */
	public static int cap(int baseEuPerTick, long tierVoltage, float euFactor, int ceiling) {
		if (ceiling <= 0 || euFactor <= 1.0f || baseEuPerTick <= 0 || tierVoltage <= 0) {
			return 0;
		}
		double draw = baseEuPerTick;
		int chips = 0;
		while (chips < ceiling && draw * euFactor <= tierVoltage) {
			draw *= euFactor;
			chips++;
		}
		return chips;
	}

	/**
	 * The draw per working tick: the base rate after the global speed multiplier, then one
	 * {@code euFactor} per chip. Never below 1 — a machine that draws nothing would run for free.
	 */
	public static int euPerTick(int baseEuPerTick, float globalSpeedMultiplier, float euFactor, int chips) {
		float rate = baseEuPerTick * globalSpeedMultiplier
				* (float) Math.pow(euFactor, Math.max(0, chips));
		return Math.max(1, Math.round(rate));
	}

	/**
	 * The operation length: an ALREADY globally-scaled duration, shortened by one {@code speedFactor}
	 * per chip. Never below 1 tick — a zero-length operation would complete endlessly within one tick.
	 *
	 * <p>Takes the pre-scaled value because the two scalings must not be reordered: the base duration
	 * has to be divided by the machine's RAW tariff first, or the global multiplier lands twice.
	 */
	public static int duration(int scaledTicks, float speedFactor, int chips) {
		float ticks = scaledTicks * (float) Math.pow(speedFactor, Math.max(0, chips));
		return Math.max(1, Math.round(ticks));
	}
}
