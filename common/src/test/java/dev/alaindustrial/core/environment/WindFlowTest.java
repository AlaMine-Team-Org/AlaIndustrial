package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link WindFlow} — the Wind Gauge reading (MOD-347). No Minecraft runtime;
 * deterministic. Shipped config as literals: sea level 63, cloud deck 192, dead height 248, ridge
 * factor 0.45, trace factor 0.06, peak 62.5 km/h, rain ×1.5, thunder ×2.0.
 *
 * <p>The load-bearing test is {@link #theGaugeAndTheMillRiseAndFallTogether()}: it walks the whole
 * buildable column and demands that wherever the gauge's reading moves up, the mill's EU/t never
 * moves down, and vice versa. The gauge exists to predict the generator; a curve edit that desynced
 * them would turn the tool into a liar, and this is what catches it.
 */
class WindFlowTest {

	private static final int SEA = 63;
	private static final int CLOUD = 192;
	private static final int DEAD = 248;
	private static final float RIDGE_F = 0.45f;
	private static final float TRACE_F = 0.06f;
	private static final float PEAK = 62.5f;
	private static final float RAIN = 1.5f;
	private static final float THUNDER = 2.0f;
	private static final int MAX_BASE = 4;
	private static final int BLOCKS_PER_BASE = 16;
	private static final int MAX_OUT = 8;
	private static final int RIDGE = SEA + MAX_BASE * BLOCKS_PER_BASE;

	private static int tenths(int y, boolean sky, boolean rain, boolean thunder) {
		return WindFlow.readingTenths(y, SEA, sky, rain, thunder, RIDGE, CLOUD, DEAD,
				RIDGE_F, TRACE_F, PEAK, RAIN, THUNDER);
	}

	private static int eu(int y) {
		return WindMillOutput.euFor(y, SEA, true, false, false, MAX_BASE, BLOCKS_PER_BASE, MAX_OUT,
				RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F);
	}

	@Test
	void closedSkyReadsZero() {
		assertEquals(0, tenths(CLOUD, false, false, false), "no open sky → 0, even at the windiest height");
		assertEquals(0, tenths(CLOUD, false, true, true), "no open sky → 0, even in a thunderstorm");
	}

	@Test
	void atOrBelowSeaLevelReadsZero() {
		assertEquals(0, tenths(SEA, true, false, false), "sea level → 0.0 km/h");
		assertEquals(0, tenths(SEA - 40, true, false, false), "below sea level → 0.0 km/h");
	}

	@Test
	void clearWeatherPeaksAtTheConfiguredSpeedUnderTheClouds() {
		assertEquals(625, tenths(CLOUD, true, false, false), "cloud deck, clear → 62.5 km/h");
		assertTrue(tenths(CLOUD - 1, true, false, false) < 625, "one block lower is measurably weaker");
	}

	@Test
	void windDiesAboveTheClouds() {
		int atCloud = tenths(CLOUD, true, false, false);
		int highUp = tenths(DEAD - 1, true, false, false);
		assertTrue(highUp < atCloud / 5,
				"just under the dead height the wind must be a fraction of the peak, got " + highUp);
		// This is the behaviour the old flat cap could not express: very high is WORSE, not equal.
		assertTrue(tenths(300, true, false, false) < tenths(RIDGE, true, false, false),
				"above the dead height must read below the ridge, not the same maximum");
	}

	@Test
	void everySingleBlockOfHeightChangesTheReading() {
		for (int y = SEA + 1; y <= DEAD; y++) {
			assertTrue(tenths(y, true, false, false) != tenths(y - 1, true, false, false),
					"Y " + y + " reads the same as Y " + (y - 1) + ": " + tenths(y, true, false, false));
		}
	}

	@Test
	void readingsAreNotRoundNumbers() {
		// The complaint that started this redesign was that the gauge always showed a tidy "100".
		// Across the climb, most heights must land off the whole-number grid.
		int offGrid = 0;
		for (int y = SEA + 1; y <= CLOUD; y++) {
			if (tenths(y, true, false, false) % 10 != 0) {
				offGrid++;
			}
		}
		int span = CLOUD - SEA;
		assertTrue(offGrid > span * 3 / 4,
				"expected most of the " + span + " heights to read with a non-zero decimal, got " + offGrid);
	}

	@Test
	void weatherMultipliesTheReading() {
		assertEquals(938, tenths(CLOUD, true, true, false), "cloud deck in rain → 62.5 × 1.5 = 93.8");
		assertEquals(1250, tenths(CLOUD, true, false, true), "cloud deck in a thunderstorm → 62.5 × 2.0");
	}

	@Test
	void thunderBeatsRainWhenBothAreSet() {
		assertEquals(tenths(150, true, false, true), tenths(150, true, true, true),
				"thundering must take precedence over raining");
	}

	@Test
	void theGaugeAndTheMillRiseAndFallTogether() {
		// The contract of the whole task: the gauge predicts the generator. Walk the column and
		// require the two to agree on direction at every step.
		for (int y = SEA + 1; y <= DEAD + 40; y++) {
			int dGauge = Integer.signum(tenths(y, true, false, false) - tenths(y - 1, true, false, false));
			int dEu = Integer.signum(eu(y) - eu(y - 1));
			if (dEu != 0) {
				assertEquals(dGauge, dEu,
						"at Y " + y + " the mill moved " + dEu + " while the gauge moved " + dGauge);
			}
		}
	}

	@Test
	void formatRendersOneDecimal() {
		assertEquals("0.0", WindFlow.format(0));
		assertEquals("2.4", WindFlow.format(24));
		assertEquals("62.5", WindFlow.format(625));
		assertEquals("125.0", WindFlow.format(1250));
	}
}
