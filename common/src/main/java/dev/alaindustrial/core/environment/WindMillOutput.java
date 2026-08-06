package dev.alaindustrial.core.environment;

/**
 * Pure production arithmetic for the wind mills — extracted so it can be unit-tested without a
 * Minecraft runtime (L1, see {@code docs/testing/AUTOMATION-STANDARDS.md}). No Minecraft imports,
 * no side effects. The {@code produce()} of all three wind mills calls this, so the tested code
 * <em>is</em> the production code.
 *
 * <p><b>Height term (MOD-347).</b> Output no longer follows a stepped ramp with a hard cap. It
 * follows {@link WindProfile#factor}: a climb from sea level to this branch's ridge, a slow shoulder
 * up to the cloud deck, and a collapse above it. {@code maxBase} is the base reached at the cloud
 * deck — every branch keeps the peak output it had before MOD-347, but reaches it under the clouds
 * instead of at the ridge, and loses it again in the thin air above them.
 *
 * <p>Before MOD-347 the term was {@code clamp((y − seaLevel) / blocksPerBase, 0, maxBase)}, which was
 * identical at every height above the cap: a tower at Y 250 produced exactly what a tower at Y 127
 * did. {@code blocksPerBase} survives as the length of the climb segment
 * ({@link WindProfile#ridgeY}), so the Sky Mill still gains height twice as fast as the T1 mill.
 *
 * <p><b>The Wind Gauge reads the same curve</b> ({@link WindFlow}) — that is what makes its reading a
 * prediction of this number rather than decoration.
 */
public final class WindMillOutput {
	private WindMillOutput() {
	}

	/**
	 * EU/t for a wind mill given its build height, the world's sea level, open-sky access and weather.
	 *
	 * <p>Weather multiplies the height term ({@code thunderFactor > rainFactor > 1.0}) and the rounded
	 * result is capped at {@code maxOutput}. Without open sky the mill is dead (0), no matter the
	 * height or the storm; likewise at or below sea level, and in the thin air above {@code deadY},
	 * where the height term rounds to a base of 0.
	 *
	 * @param y             the mill's Y level
	 * @param seaLevel      the world's sea level (the profile is measured from here)
	 * @param openSky       whether the column above the mill is clear to the sky
	 * @param raining       whether it is raining over the mill
	 * @param thundering    whether it is a thunderstorm over the mill (implies rain; checked first)
	 * @param maxBase       base EU/t at the cloud deck ({@code Config.windMillMaxBaseEuPerTick} etc.)
	 * @param blocksPerBase length of the climb segment per base point — 16 for T1 and the storm branch,
	 *                      {@code Config.highAltWindMillBlocksPerBase} (8) for the high-altitude branch
	 * @param maxOutput     final EU/t cap ({@code Config.windMillMaxEuPerTick})
	 * @param rainFactor    rain weather multiplier ({@code Config.windMillRainFactor})
	 * @param thunderFactor thunder weather multiplier ({@code Config.windMillThunderFactor})
	 * @param cloudY        the cloud deck, where the wind peaks ({@code Config.windCloudY})
	 * @param deadY         where the wind dies out ({@code Config.windDeadY})
	 * @param ridgeFactor   fraction of full strength at the ridge ({@code Config.windRidgeFactor})
	 * @param traceFactor   fraction of full strength above {@code deadY} ({@code Config.windTraceFactor})
	 */
	public static int euFor(int y, int seaLevel, boolean openSky, boolean raining, boolean thundering,
			int maxBase, int blocksPerBase, int maxOutput, float rainFactor, float thunderFactor,
			int cloudY, int deadY, float ridgeFactor, float traceFactor) {
		if (!openSky) {
			return 0; // roofed / below the sky column → dead, height and storm irrelevant
		}
		int ridge = WindProfile.ridgeY(seaLevel, maxBase, blocksPerBase, cloudY);
		float factor = WindProfile.factor(y, seaLevel, ridge, cloudY, deadY, ridgeFactor, traceFactor);
		int base = Math.round(maxBase * factor);
		if (base <= 0) {
			return 0; // sea level and below, or thin air above the clouds: nothing for a rotor to turn
		}
		float weather = thundering ? thunderFactor : (raining ? rainFactor : 1.0f);
		return Math.min(Math.round(base * weather), maxOutput);
	}
}
