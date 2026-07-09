package dev.alaindustrial.core;

/**
 * Pure production arithmetic for the wind mill — extracted so it can be unit-tested without a
 * Minecraft runtime (L1, see {@code docs/testing/AUTOMATION-STANDARDS.md}). No Minecraft imports,
 * no side effects. {@code WindMillBlockEntity.produce()} calls this so the tested code <em>is</em>
 * the production code.
 */
public final class WindMillOutput {
	private WindMillOutput() {
	}

	/**
	 * EU/t for a wind mill given its build height, the world's sea level, open-sky access and weather.
	 *
	 * <p>Base grows with height: {@code clamp((y − seaLevel) / 16, 0, maxBase)} — 0 at/below sea level,
	 * +1 EU/t per 16 blocks up, capped at {@code maxBase}. Weather multiplies the base
	 * ({@code thunderFactor > rainFactor > 1.0}); the rounded result is capped at {@code maxOutput}.
	 * Without open sky the mill is dead (0), no matter the height or storm.
	 *
	 * @param y             the mill's Y level
	 * @param seaLevel      the world's sea level (base is measured from here)
	 * @param openSky       whether the column above the mill is clear to the sky
	 * @param raining       whether it is raining over the mill
	 * @param thundering    whether it is a thunderstorm over the mill (implies rain; checked first)
	 * @param maxBase       height base cap ({@code Config.windMillMaxBaseEuPerTick})
	 * @param maxOutput     final EU/t cap ({@code Config.windMillMaxEuPerTick})
	 * @param rainFactor    rain weather multiplier ({@code Config.windMillRainFactor})
	 * @param thunderFactor thunder weather multiplier ({@code Config.windMillThunderFactor})
	 */
	public static int euFor(int y, int seaLevel, boolean openSky, boolean raining, boolean thundering,
			int maxBase, int maxOutput, float rainFactor, float thunderFactor) {
		return euFor(y, seaLevel, openSky, raining, thundering, maxBase, 16, maxOutput, rainFactor, thunderFactor);
	}

	/**
	 * Generalised wind-mill output with a configurable {@code blocksPerBase} step. Identical contract
	 * to the 9-arg {@link #euFor} but the height dividend is a parameter: the T1 wind mill uses 16
	 * (one base step per 16 blocks), the high-altitude T2 variant uses 8 (gains base twice as fast with
	 * height). Everything else — open-sky gate, zero-base gate, weather multiplier, output cap — is
	 * identical. Kept backward-compatible: the original 9-arg overload delegates here with {@code 16}.
	 */
	public static int euFor(int y, int seaLevel, boolean openSky, boolean raining, boolean thundering,
			int maxBase, int blocksPerBase, int maxOutput, float rainFactor, float thunderFactor) {
		if (!openSky) {
			return 0; // roofed / below the sky column → dead, height and storm irrelevant
		}
		if (blocksPerBase <= 0) {
			blocksPerBase = 16; // guard: never divide by zero
		}
		int base = Math.max(0, Math.min(maxBase, (y - seaLevel) / blocksPerBase));
		if (base == 0) {
			return 0; // at/below sea level: base is 0, so a storm cannot lift it (height is required)
		}
		float factor = thundering ? thunderFactor : (raining ? rainFactor : 1.0f);
		return Math.min(Math.round(base * factor), maxOutput);
	}
}
