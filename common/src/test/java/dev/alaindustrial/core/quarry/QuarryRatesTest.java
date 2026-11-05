package dev.alaindustrial.core.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** What a quarry operation costs (MOD-951), with the spec's numbers. */
class QuarryRatesTest {

	private static final QuarryRates SPEC = new QuarryRates(133, 20, 40, 10, 4, 8, 8, 2);

	@Test
	void stoneCostsAboutTwoHundredEuAndTwoSeconds() {
		assertEquals(200L, SPEC.drillEu(1.5F));
		assertEquals(40, SPEC.drillTicks(1.5F));
	}

	@Test
	void harderBlocksCostAndTakeInProportion() {
		assertEquals(399L, SPEC.drillEu(3.0F), "deepslate");
		assertEquals(80, SPEC.drillTicks(3.0F));
	}

	@Test
	void softBlocksHitTheFloors() {
		assertEquals(20L, SPEC.drillEu(0.0F));
		assertEquals(10, SPEC.drillTicks(0.0F));
		assertEquals(67L, SPEC.drillEu(0.5F), "dirt is above the EU floor");
		assertEquals(13, SPEC.drillTicks(0.5F), "and above the time floor");
	}

	@Test
	void theBridgeAndTheCartTravelAtOnceAndBothArePaid() {
		assertEquals(10, SPEC.travelTicks(2, 1), "two blocks at four a second, the cart's one block meanwhile");
		assertEquals(24L, SPEC.travelEu(2, -1));
		assertEquals(0, SPEC.travelTicks(0, 0));
		assertEquals(0L, SPEC.travelEu(0, 0));
	}

	@Test
	void theRopeRunsAtEightBlocksASecond() {
		assertEquals(3, SPEC.ropeTicks(1), "a block of rope is two and a half ticks, rounded up");
		assertEquals(5, SPEC.ropeTicks(2));
		assertEquals(4L, SPEC.ropeEu(-2));
	}
}
