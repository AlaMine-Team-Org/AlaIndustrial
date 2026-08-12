package dev.alaindustrial.core.environment;

/**
 * Pure production arithmetic for the water mill — extracted so it can be unit-tested without a
 * Minecraft runtime (L1, see {@code docs/testing/AUTOMATION-STANDARDS.md}). No Minecraft imports,
 * no side effects. {@code WaterMillBlockEntity.produce()} calls this so the tested code <em>is</em>
 * the production code.
 */
public final class WaterMillOutput {
	private WaterMillOutput() {
	}

	/**
	 * EU/t = {@code round(perSide × waterSides × wheelFactor)}, with {@code waterSides} clamped to the
	 * four cells the wheel sweeps (0..4), so the fully-driven case never exceeds
	 * {@code perSide × 4 × wheelFactor}.
	 *
	 * <p><b>No output cap here, unlike the wind mills.</b> The water mill has no
	 * {@code *MaxEuPerTick}: its ceiling is structural — four drive cells at
	 * {@code Config.waterMillEuPerTick} each. At the shipped defaults that is 4 EU/t, and even the
	 * advanced wheel's ×1.5 only reaches 6 EU/t, far below the LV tier voltage (32) and below a copper
	 * cable's 12 EU/t throughput. MOD-385 therefore did not introduce a cap for it; the guard that
	 * matters for this generator is the L2 assertion that the result stays well under the LV limit.
	 *
	 * <p>Rounding is deliberate rather than truncating: at these single-digit rates {@code (int)} would
	 * swallow the whole upgrade on the common two-cell channel (2 × 1.25 = 2.5 → 2, i.e. no gain at
	 * all), whereas rounding gives 3. The ladder stays monotonic under rounding at every cell count.
	 *
	 * @param waterSides count of wheel-swept cells carrying a current (any int; clamped to 0..4)
	 * @param perSide    EU/t produced per driven cell ({@code Config.waterMillEuPerTick})
	 * @param wheelFactor the installed wheel grade's output scale (MOD-385;
	 *                    {@code ComponentTier.outputMultiplier()}, 1.0 for the plain wooden wheel)
	 */
	public static int euFor(int waterSides, int perSide, float wheelFactor) {
		int sides = Math.max(0, Math.min(4, waterSides));
		return Math.round(perSide * sides * wheelFactor);
	}
}
