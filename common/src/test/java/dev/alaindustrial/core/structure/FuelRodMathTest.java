package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link FuelRodMath} (MOD-468, stage 4).
 *
 * <p>Small class, one load-bearing decision: the price of a damage point rounds UP. With a truncating
 * divide each point would be worth slightly less than it costs, and the last points of every rod would
 * be energy the player never paid uranium for.
 */
class FuelRodMathTest {

	@Test
	void aPointCostsItsShareOfTheRodRoundedUp() {
		// 288 000 EU over 1000 points divides evenly.
		assertEquals(288, FuelRodMath.euPerPoint(288_000));
		// 999 999 does not: 999.999 has to become 1000, not 999.
		assertEquals(1000, FuelRodMath.euPerPoint(999_999));
		assertEquals(1, FuelRodMath.euPerPoint(1));
	}

	@Test
	void aRodCanNeverOutliveItsOwnCharge() {
		for (long energy = 1; energy <= 5_000; energy += 7) {
			long perPoint = FuelRodMath.euPerPoint(energy);
			assertTrue(perPoint * FuelRodMath.ROD_DURABILITY >= energy,
					"the full bar must cost at least the rod's charge, at " + energy);
		}
	}

	@Test
	void aBrokenChargeDoesNotDivide() {
		assertEquals(0, FuelRodMath.euPerPoint(0));
		assertEquals(0, FuelRodMath.euPerPoint(-1));
	}
}
