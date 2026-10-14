package dev.alaindustrial.core.environment;

/**
 * Pure arithmetic of the Solar Fluxweave Helmet (MOD-664): how many EU a worn solar panel yields in one
 * second, given what the sky looks like from the wearer's eyes. Minecraft-free, so L1 can pin the
 * multiplier table; {@code FluxweaveSolarHelmetItem} only translates world state into these arguments.
 *
 * <p>The rules are the ground panel's own ({@code SolarPanelBlockEntity#produce}), not a second set:
 * rain and thunder silence it, snow dims it to a floored trickle, a translucent canopy halves it, and
 * night, a blocked sky, another dimension or being underwater yield nothing.
 */
public final class SolarWearOutput {
	private SolarWearOutput() {
	}

	/** How much of the sky the wearer sees (mirror of {@code SolarSky.Access}, kept MC-free). */
	public enum Sky {
		CLEAR, PARTIAL, BLOCKED
	}

	/** Precipitation over the wearer (mirror of {@code SolarSky.Weather}, kept MC-free). */
	public enum Precipitation {
		NONE, RAIN, SNOW
	}

	/**
	 * EU produced in one second.
	 *
	 * @param base        EU/s under a clear daytime sky ({@code fluxweaveSolarEuPerSecond})
	 * @param daylight    Overworld and bright outside — the only place and time a panel works
	 * @param sky         sky access from the eyes
	 * @param weather     precipitation over the eyes
	 * @param submerged   eyes in water
	 * @param snowFactor  output multiplier under snow ({@code solarSnowFactor})
	 * @param canopyFactor output multiplier through leaves or cobweb ({@code solarTransparentFactor})
	 */
	public static long euPerSecond(long base, boolean daylight, Sky sky, Precipitation weather,
			boolean submerged, float snowFactor, float canopyFactor) {
		if (base <= 0 || !daylight || submerged || sky == Sky.BLOCKED || weather == Precipitation.RAIN) {
			return 0L;
		}
		if (weather == Precipitation.SNOW) {
			// Floored at 1, exactly as the ground panel: round(base * factor) alone could read 0 at a low base.
			return Math.max(1L, scaled(base, snowFactor));
		}
		return sky == Sky.PARTIAL ? scaled(base, canopyFactor) : base;
	}

	private static long scaled(long base, float factor) {
		return Math.max(0L, Math.round(base * factor));
	}
}
