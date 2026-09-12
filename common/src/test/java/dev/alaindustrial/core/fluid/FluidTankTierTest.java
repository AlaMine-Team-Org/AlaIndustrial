package dev.alaindustrial.core.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import org.junit.jupiter.api.Test;

/**
 * MOD-612 — the relation between the two tank grades, which is the part a config edit breaks in
 * silence.
 *
 * <p>Nothing here asserts a magic number for its own sake: the shipped defaults are pinned because
 * they are the balance decision (8 and 16 buckets), and the ORDER is pinned because that is what
 * makes the second grade a grade at all. A knob edit that leaves the advanced tank smaller than the
 * basic one compiles, loads, and ships a tier nobody would craft.
 */
class FluidTankTierTest {

	@Test
	void shippedDefaultsAreEightAndSixteenBuckets() {
		assertEquals(8_000, FluidTankTier.BASIC.capacity(),
				"the basic tank ships at 8 buckets — deliberately below a machine tank's 10");
		assertEquals(16_000, FluidTankTier.ADVANCED.capacity(),
				"the advanced tank ships at 16 buckets — twice the basic grade, the mod's ratio for a "
						+ "logistics tier");
	}

	@Test
	void advancedHoldsExactlyTwiceTheBasicGrade() {
		assertEquals(2 * FluidTankTier.BASIC.capacity(), FluidTankTier.ADVANCED.capacity(),
				"×2 is the decision, not a coincidence: a bigger multiplier obsoletes the grade below "
						+ "on the day the new one becomes craftable");
	}

	@Test
	void capacityFollowsAReloadedConfig() {
		int basic = Config.fluidTankCapacity;
		int advanced = Config.fluidTankAdvancedCapacity;
		try {
			Config.fluidTankCapacity = 1_234;
			Config.fluidTankAdvancedCapacity = 5_678;
			assertEquals(1_234, FluidTankTier.BASIC.capacity(),
					"the grade must read the knob live — Config is mutable at runtime, and a value "
							+ "captured at class-init would ignore a reload");
			assertEquals(5_678, FluidTankTier.ADVANCED.capacity());
		} finally {
			Config.fluidTankCapacity = basic;
			Config.fluidTankAdvancedCapacity = advanced;
		}
	}

	@Test
	void everyGradeHoldsSomething() {
		for (FluidTankTier tier : FluidTankTier.values()) {
			assertTrue(tier.capacity() > 0, tier + " must hold something");
		}
	}
}
