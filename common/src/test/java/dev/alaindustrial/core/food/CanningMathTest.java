package dev.alaindustrial.core.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1 cover for the canning exchange (MOD-383). The arithmetic lives in a Minecraft-free class
 * precisely so these can run without a game runtime.
 */
class CanningMathTest {

	@Test
	@DisplayName("Food value is nutrition plus saturation, carried in tenths")
	void foodValueSumsBothAxes() {
		// Cooked porkchop: 8 nutrition, 12.8 saturation -> 20.8 -> 208 tenths.
		assertEquals(208, CanningMath.foodValue(8, 12.8f));
		// Sweet berries: 2 nutrition, 0.4 saturation -> 2.4 -> 24 tenths.
		assertEquals(24, CanningMath.foodValue(2, 0.4f));
	}

	@Test
	@DisplayName("Food with no nutrition is refused rather than swallowed for free")
	void zeroNutritionIsRejected() {
		// A modded food declaring zero nutrition would otherwise be eaten one item per tick while the
		// buffer never moved — an item voider with a progress bar that never turns.
		assertEquals(0, CanningMath.foodValue(0, 5.0f));
		assertEquals(0, CanningMath.foodValue(-3, 5.0f));
	}

	@Test
	@DisplayName("A single absurd item cannot overflow the buffer or its 16-bit GUI channel")
	void contributionsAndBufferAreClamped() {
		assertEquals(CanningMath.MAX_ITEM_VALUE, CanningMath.foodValue(Integer.MAX_VALUE, 1e9f));
		assertEquals(CanningMath.MAX_BUFFER,
				CanningMath.addToBuffer(CanningMath.MAX_BUFFER - 1, CanningMath.MAX_ITEM_VALUE));
		assertTrue(CanningMath.MAX_BUFFER < Short.MAX_VALUE, "buffer must fit a DataSlot");
	}

	@Test
	@DisplayName("Negative or zero contributions leave the buffer alone")
	void nonPositiveContributionIsIgnored() {
		assertEquals(50, CanningMath.addToBuffer(50, 0));
		assertEquals(50, CanningMath.addToBuffer(50, -10));
	}

	@Test
	@DisplayName("The press takes exactly one ration's worth and leaves the remainder")
	void consumeLeavesRemainder() {
		assertTrue(CanningMath.hasFullRation(120, 120));
		assertFalse(CanningMath.hasFullRation(119, 120));
		assertEquals(88, CanningMath.consumeRation(208, 120));
		assertEquals(0, CanningMath.consumeRation(50, 120));
	}

	@Test
	@DisplayName("ANTI-DUPE: the shipped rate loses value, and the guard would catch it if it did not")
	void shippedRateIsLossy() {
		// The real invariant: a ration must cost more than it is worth. This is the only thing between
		// the machine and a food duplicator, so it is asserted against the shipped config value.
		assertTrue(CanningMath.isLossy(Config.canningFoodValuePerCan),
				"shipped canningFoodValuePerCan must exceed the ration's own value");
		assertTrue(Config.canningFoodValuePerCan > CanningMath.RATION_VALUE);

		// Negative control — without it this test would pass on a broken constant just as happily.
		// At break-even and below the guard must go red, or it is not guarding anything.
		assertFalse(CanningMath.isLossy(CanningMath.RATION_VALUE), "break-even must not read as lossy");
		assertFalse(CanningMath.isLossy(CanningMath.RATION_VALUE - 1));
		assertFalse(CanningMath.isLossy(0));
	}

	@Test
	@DisplayName("ANTI-DUPE: no amount of food yields more value than it carried")
	void roundTripNeverGainsValue() {
		int rate = Config.canningFoodValuePerCan;
		// Sweep the whole vanilla range of single-item values, plus the ration itself fed back in.
		for (int value = 1; value <= 300; value++) {
			int rations = CanningMath.rationsFrom(value, rate);
			int out = rations * CanningMath.RATION_VALUE;
			assertTrue(out < value || rations == 0,
					"value " + value + " produced " + rations + " rations worth " + out);
		}
	}

	@Test
	@DisplayName("The ration's own value stays derived from its two stats")
	void rationValueIsDerived() {
		// Guards against someone retuning nutrition or saturation and leaving RATION_VALUE stale, which
		// would quietly move the break-even point the anti-dupe guard is measured against.
		float saturation = CanningMath.RATION_NUTRITION * CanningMath.RATION_SATURATION_MODIFIER * 2.0f;
		assertEquals(CanningMath.foodValue(CanningMath.RATION_NUTRITION, saturation),
				CanningMath.RATION_VALUE);
		assertEquals(96, CanningMath.RATION_VALUE);
	}
}
