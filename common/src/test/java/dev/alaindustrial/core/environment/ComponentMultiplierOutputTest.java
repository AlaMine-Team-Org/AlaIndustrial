package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.machine.ComponentTier;
import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for the MOD-385 component multiplier where it actually lives — inside
 * {@link WindMillOutput#euFor} and {@link WaterMillOutput#euFor}, ahead of their final clamp.
 *
 * <p><b>What these tests are really guarding.</b> The multiplier could have been applied at the call
 * site, on the value {@code euFor} had already returned. That version passes every "×1.25 does not
 * exceed the cap" check written against today's numbers and breaks the moment someone raises a grade
 * or lowers a ceiling. {@link #noMultiplierHoweverAbsurdCanBreachTheCap} is the test that tells the
 * two designs apart: it feeds multipliers far beyond anything shipped and still demands the cap holds.
 */
class ComponentMultiplierOutputTest {
	// The shipped T1 wind mill profile (mirrors WindMillOutputTest's constants).
	private static final int SEA = 63;
	private static final int MAX_BASE = 4;
	private static final int BLOCKS_PER_BASE = 16;
	private static final int MAX_OUT = 8;
	private static final float RAIN = 1.5f;
	private static final float THUNDER = 2.0f;
	private static final int CLOUD = 192;
	private static final int DEAD = 248;
	private static final float RIDGE_F = 0.45f;
	private static final float TRACE_F = 0.06f;

	private static int windEu(int y, boolean rain, boolean thunder, float rotorFactor) {
		return WindMillOutput.euFor(y, SEA, true, rain, thunder, MAX_BASE, BLOCKS_PER_BASE, MAX_OUT,
				RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F, rotorFactor);
	}

	/** At the cloud deck in clear weather the base is the full 4 EU/t, well clear of the 8 cap. */
	@Test
	void rotorGradeRaisesOutputBelowTheCap() {
		int wooden = windEu(CLOUD, false, false, 1.0f);
		int reinforced = windEu(CLOUD, false, false, 1.25f);
		int advanced = windEu(CLOUD, false, false, 1.5f);

		assertEquals(4, wooden, "base 4 × 1.0 → 4 EU/t");
		assertEquals(5, reinforced, "base 4 × 1.25 → 5 EU/t");
		assertEquals(6, advanced, "base 4 × 1.5 → 6 EU/t");
	}

	/**
	 * The negative control the task demands: with the baseline grade's 1.0 the result must be bit-for-bit
	 * what the mill produced before MOD-385 existed. If someone ever "helpfully" applied a floor or a
	 * bonus to the T1 path, this reddens.
	 */
	@Test
	void baselineGradeLeavesEveryHeightAndWeatherUntouched() {
		for (int y = SEA - 8; y <= DEAD + 8; y += 7) {
			for (boolean rain : new boolean[] {false, true}) {
				for (boolean thunder : new boolean[] {false, true}) {
					int withBaseline = windEu(y, rain, thunder, 1.0f);
					// Recomputing the pre-MOD-385 expression by hand, independent of euFor's internals.
					int ridge = WindProfile.ridgeY(SEA, MAX_BASE, BLOCKS_PER_BASE, CLOUD);
					float factor = WindProfile.factor(y, SEA, ridge, CLOUD, DEAD, RIDGE_F, TRACE_F);
					int base = Math.round(MAX_BASE * factor);
					int expected = base <= 0 ? 0
							: Math.min(Math.round(base * (thunder ? THUNDER : rain ? RAIN : 1.0f)), MAX_OUT);
					assertEquals(expected, withBaseline,
							"y=" + y + " rain=" + rain + " thunder=" + thunder
									+ ": the 1.0 baseline must reproduce pre-MOD-385 output exactly");
				}
			}
		}
	}

	/**
	 * The structural guarantee. Applying the grade outside {@code euFor} would let any of these breach
	 * {@code maxOutput}; folded in before the clamp, none can — for ANY multiplier, not just the three
	 * the mod ships.
	 */
	@Test
	void noMultiplierHoweverAbsurdCanBreachTheCap() {
		for (float factor : new float[] {1.0f, 1.25f, 1.5f, 2.0f, 10.0f, 1000.0f}) {
			for (int y : new int[] {SEA + 32, 150, CLOUD, CLOUD + 20}) {
				for (boolean thunder : new boolean[] {false, true}) {
					int eu = windEu(y, false, thunder, factor);
					assertTrue(eu <= MAX_OUT,
							"factor=" + factor + " y=" + y + " thunder=" + thunder
									+ " produced " + eu + " EU/t, breaching the " + MAX_OUT + " cap");
				}
			}
		}
	}

	/** A dead mill stays dead at every grade: no sky, or thin air above deadY, is not a rate to scale. */
	@Test
	void gradeCannotReviveADeadMill() {
		for (float factor : new float[] {1.0f, 1.25f, 1.5f, 1000.0f}) {
			assertEquals(0, WindMillOutput.euFor(CLOUD, SEA, false, false, true, MAX_BASE,
					BLOCKS_PER_BASE, MAX_OUT, RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F, factor),
					"roofed mill stays at 0 EU/t regardless of rotor grade (factor=" + factor + ")");
			assertEquals(0, windEu(SEA, false, false, factor),
					"at sea level the base rounds to 0, and 0 × anything is 0 (factor=" + factor + ")");
		}
	}

	/** The ladder must never go backwards as the grade improves, at any height or weather. */
	@Test
	void windOutputIsMonotonicAcrossTheLadder() {
		for (int y = SEA; y <= DEAD; y += 5) {
			for (boolean thunder : new boolean[] {false, true}) {
				int wooden = windEu(y, false, thunder, ComponentTier.WINDMILL_ROTOR.outputMultiplier());
				int reinforced = windEu(y, false, thunder,
						ComponentTier.WINDMILL_ROTOR_REINFORCED.outputMultiplier());
				int advanced = windEu(y, false, thunder,
						ComponentTier.WINDMILL_ROTOR_ADVANCED.outputMultiplier());
				assertTrue(reinforced >= wooden && advanced >= reinforced,
						"y=" + y + " thunder=" + thunder + ": output must not drop as the grade improves ("
								+ wooden + " / " + reinforced + " / " + advanced + ")");
			}
		}
	}

	/**
	 * The water mill rounds rather than truncates. On the common two-cell channel {@code (int)} would
	 * give 2 × 1.25 = 2.5 → 2, i.e. the reinforced wheel would buy the player literally nothing.
	 */
	@Test
	void wheelGradeSurvivesIntegerRoundingAtSmallRates() {
		assertEquals(2, WaterMillOutput.euFor(2, 1, 1.0f), "2 cells × 1 EU × 1.0 → 2");
		assertEquals(3, WaterMillOutput.euFor(2, 1, 1.25f), "2 cells × 1.25 → 2.5 rounds UP to 3, not down to 2");
		assertEquals(3, WaterMillOutput.euFor(2, 1, 1.5f), "2 cells × 1.5 → 3");
		assertEquals(4, WaterMillOutput.euFor(4, 1, 1.0f), "the shipped 4-cell baseline stays 4 EU/t");
		assertEquals(5, WaterMillOutput.euFor(4, 1, 1.25f), "4 cells × 1.25 → 5");
		assertEquals(6, WaterMillOutput.euFor(4, 1, 1.5f), "4 cells × 1.5 → 6");
	}

	@Test
	void waterOutputIsMonotonicAcrossTheLadderAtEveryCellCount() {
		for (int cells = 0; cells <= 4; cells++) {
			int wooden = WaterMillOutput.euFor(cells, 1, ComponentTier.WATER_MILL_WHEEL.outputMultiplier());
			int reinforced = WaterMillOutput.euFor(cells, 1,
					ComponentTier.WATER_MILL_WHEEL_REINFORCED.outputMultiplier());
			int advanced = WaterMillOutput.euFor(cells, 1,
					ComponentTier.WATER_MILL_WHEEL_ADVANCED.outputMultiplier());
			assertTrue(reinforced >= wooden && advanced >= reinforced,
					"cells=" + cells + ": wheel output must not drop as the grade improves ("
							+ wooden + " / " + reinforced + " / " + advanced + ")");
		}
	}

	/**
	 * The water mill has no {@code *MaxEuPerTick} of its own, so the guard that matters is a different
	 * one: even the top grade must stay far under the LV tier voltage (32 EU/t) and under a copper
	 * cable's throughput (12 EU/t per segment), or MOD-385 would have quietly created a generator whose
	 * output no longer fits the wire the player is told to use.
	 */
	@Test
	void topWheelGradeStaysWellUnderTheLvAndCableLimits() {
		int peak = WaterMillOutput.euFor(4, 1, ComponentTier.WATER_MILL_WHEEL_ADVANCED.outputMultiplier());
		assertTrue(peak < 12, "advanced wheel peak " + peak + " EU/t must fit a copper cable (12 EU/t)");
		assertTrue(peak < 32, "advanced wheel peak " + peak + " EU/t must stay under the LV voltage (32)");
	}

	/** No water, no rate — and no grade turns a dry wheel into a generator. */
	@Test
	void gradeCannotDriveADryWheel() {
		for (float factor : new float[] {1.0f, 1.25f, 1.5f, 1000.0f}) {
			assertEquals(0, WaterMillOutput.euFor(0, 1, factor),
					"a wheel with no current produces 0 at any grade (factor=" + factor + ")");
		}
	}
}
