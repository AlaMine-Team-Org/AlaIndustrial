package dev.alaindustrial.core.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The quarry's heat and water (MOD-952), with the spec's numbers. */
class QuarryHeatTest {

	private static final QuarryHeat SPEC = new QuarryHeat(10, 1000, 1, 1);

	@Test
	void aBlockHeatsTheDrillByTen() {
		QuarryHeat.Gauge gauge = new QuarryHeat.Gauge();
		SPEC.drilled(gauge);
		assertEquals(10, gauge.heat());
		assertFalse(gauge.overheated());
	}

	@Test
	void withoutWaterTheDrillStopsAfterAHundredBlocks() {
		QuarryHeat.Gauge gauge = new QuarryHeat.Gauge();
		for (int i = 0; i < 99; i++) {
			SPEC.drilled(gauge);
		}
		assertFalse(gauge.overheated(), "99 blocks");
		SPEC.drilled(gauge);
		assertTrue(gauge.overheated(), "the hundredth reaches the top");
		assertEquals(1000, gauge.heat());
	}

	@Test
	void itCoolsOneASecondAndGoesOnAtHalf() {
		QuarryHeat.Gauge gauge = new QuarryHeat.Gauge();
		gauge.restore(1000, true);
		int ticks = 0;
		while (gauge.overheated()) {
			assertEquals(0, SPEC.cool(gauge, 0), "no water, none used");
			ticks++;
		}
		assertEquals(500, gauge.heat());
		assertEquals(500 * 20, ticks, "500 seconds to cool to half");
	}

	@Test
	void waterTakesTheHeatAwayOneUnitAMillibucket() {
		QuarryHeat.Gauge gauge = new QuarryHeat.Gauge();
		SPEC.drilled(gauge);
		assertEquals(10, SPEC.cool(gauge, 8000), "about ten millibuckets a block of stone");
		assertEquals(0, gauge.heat());
		assertEquals(0, SPEC.cool(gauge, 8000), "a cold drill uses no water");
	}

	@Test
	void littleWaterCoolsLittle() {
		QuarryHeat.Gauge gauge = new QuarryHeat.Gauge();
		gauge.restore(100, false);
		assertEquals(3, SPEC.cool(gauge, 3));
		assertEquals(97, gauge.heat());
	}

	@Test
	void waterBringsAnOverheatedDrillBackAtOnce() {
		QuarryHeat.Gauge gauge = new QuarryHeat.Gauge();
		gauge.restore(1000, true);
		SPEC.cool(gauge, 8000);
		assertEquals(0, gauge.heat());
		assertFalse(gauge.overheated());
	}

	@Test
	void theScaleNeverGoesPastItsTopOrBelowZero() {
		QuarryHeat.Gauge gauge = new QuarryHeat.Gauge();
		gauge.restore(995, false);
		SPEC.drilled(gauge);
		assertEquals(1000, gauge.heat());
		gauge.restore(-5, false);
		assertEquals(0, gauge.heat());
	}
}
