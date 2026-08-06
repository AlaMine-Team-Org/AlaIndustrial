package dev.alaindustrial.core.environment;

/**
 * The Wind Gauge reading (MOD-347) — {@link WindProfile} expressed as a wind speed the player can
 * read. Pure arithmetic, no Minecraft imports, unit-testable at L1. {@code WindGaugeItem} calls this
 * so the tested code <em>is</em> the production code.
 *
 * <p><b>Units.</b> The reading is a speed in km/h, to one decimal, returned here as <em>tenths</em>
 * so nothing downstream has to compare floats. A speed rather than an abstract 0–100 score: "41.7"
 * reads as a measurement, "100" reads as a progress bar, and the whole point of the tool is that it
 * measures rather than scores. Clear-weather peak is {@code peakKmh}; rain and thunder multiply it by
 * the same factors the mills use, so a thunderstorm at the cloud deck is genuinely violent.
 *
 * <p><b>It cannot drift from the generator.</b> The gauge and all three wind mills read the same
 * {@link WindProfile#factor}; the only difference is what they multiply it by (km/h here, EU/t
 * there). A height where the gauge falls is a height where the mills fall.
 *
 * <p><b>Reference branch: T1.</b> The profile's climb segment ends at a per-branch ridge, and the
 * gauge has to pick one. It uses the T1 wind mill's — the first mill a player builds and the only one
 * that exists when the gauge is craftable. Above the ridge every branch shares the same shoulder and
 * the same thinning, so the choice only affects the shape of the climb, not where the wind peaks or
 * dies.
 */
public final class WindFlow {
	private WindFlow() {
	}

	/**
	 * Wind speed at {@code y} in tenths of km/h (2470 = 24.7 km/h), on the same inputs the mills
	 * sample. Zero without open sky, whatever the height and weather.
	 *
	 * @param openSky       column above is clear <em>and</em> we are in the Overworld
	 * @param raining       global rain flag, as the mills read it (not biome precipitation)
	 * @param thundering    global thunder flag; implies rain, checked first
	 * @param ridgeY        end of the climb segment — {@link WindProfile#ridgeY} for the T1 branch
	 * @param peakKmh       clear-weather speed at the cloud deck ({@code Config.windGaugePeakKmh})
	 */
	public static int readingTenths(int y, int seaLevel, boolean openSky, boolean raining, boolean thundering,
			int ridgeY, int cloudY, int deadY, float ridgeFactor, float traceFactor,
			float peakKmh, float rainFactor, float thunderFactor) {
		if (!openSky) {
			return 0; // roofed, under foliage, or outside the Overworld → the mill is dead here
		}
		float factor = WindProfile.factor(y, seaLevel, ridgeY, cloudY, deadY, ridgeFactor, traceFactor);
		float weather = thundering ? thunderFactor : (raining ? rainFactor : 1.0f);
		return Math.round(factor * peakKmh * weather * 10.0f);
	}

	/** Format tenths as a fixed one-decimal string ({@code 2470 → "247.0"}), locale-independent. */
	public static String format(int tenths) {
		return (tenths / 10) + "." + Math.abs(tenths % 10);
	}
}
