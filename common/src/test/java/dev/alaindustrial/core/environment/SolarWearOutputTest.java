package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.core.environment.SolarWearOutput.Precipitation;
import dev.alaindustrial.core.environment.SolarWearOutput.Sky;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link SolarWearOutput#euPerSecond} — the sky-to-EU table of the Solar Fluxweave Helmet.
 *
 * @implements fluxweave solar helmet output (MOD-664): zero at night / blocked / rain / underwater,
 *     snow floored at 1, canopy scaled, clear sky full
 */
class SolarWearOutputTest {
	private static final float SNOW = 0.2f;
	private static final float CANOPY = 0.5f;

	private static long eu(long base, boolean day, Sky sky, Precipitation w, boolean wet) {
		return SolarWearOutput.euPerSecond(base, day, sky, w, wet, SNOW, CANOPY);
	}

	@Test
	void clearDaytimeSkyYieldsTheFullBase() {
		assertEquals(8, eu(8, true, Sky.CLEAR, Precipitation.NONE, false));
	}

	@Test
	void nightYieldsNothing() {
		assertEquals(0, eu(8, false, Sky.CLEAR, Precipitation.NONE, false));
	}

	@Test
	void blockedSkyYieldsNothing() {
		assertEquals(0, eu(8, true, Sky.BLOCKED, Precipitation.NONE, false));
	}

	@Test
	void rainAndThunderYieldNothing() {
		assertEquals(0, eu(8, true, Sky.CLEAR, Precipitation.RAIN, false));
		assertEquals(0, eu(8, true, Sky.PARTIAL, Precipitation.RAIN, false));
	}

	@Test
	void submergedEyesYieldNothing() {
		assertEquals(0, eu(8, true, Sky.CLEAR, Precipitation.NONE, true));
	}

	@Test
	void canopyScalesByTheTransparentFactor() {
		assertEquals(4, eu(8, true, Sky.PARTIAL, Precipitation.NONE, false));
		// round-half-up like the ground panel: 5 * 0.5 = 2.5 -> 3
		assertEquals(3, eu(5, true, Sky.PARTIAL, Precipitation.NONE, false));
	}

	@Test
	void snowScalesByTheSnowFactorButNeverBelowOne() {
		assertEquals(2, eu(8, true, Sky.CLEAR, Precipitation.SNOW, false));
		assertEquals(1, eu(1, true, Sky.CLEAR, Precipitation.SNOW, false), "floored at 1 like the ground panel");
	}

	@Test
	void snowBeatsCanopyInsteadOfStacking() {
		assertEquals(2, eu(8, true, Sky.PARTIAL, Precipitation.SNOW, false));
	}

	@Test
	void zeroBaseYieldsNothingEvenUnderSnow() {
		assertEquals(0, eu(0, true, Sky.CLEAR, Precipitation.SNOW, false));
		assertEquals(0, eu(0, true, Sky.CLEAR, Precipitation.NONE, false));
	}

	@Test
	void snowDoesNotRescueABlockedSkyOrNight() {
		assertEquals(0, eu(8, true, Sky.BLOCKED, Precipitation.SNOW, false));
		assertEquals(0, eu(8, false, Sky.CLEAR, Precipitation.SNOW, false));
	}
}
