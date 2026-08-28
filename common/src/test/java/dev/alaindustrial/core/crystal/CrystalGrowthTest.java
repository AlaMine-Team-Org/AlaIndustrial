package dev.alaindustrial.core.crystal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link CrystalGrowth} (MOD-505) — the arithmetic behind how fast a greenhouse
 * grows.
 *
 * <p>Two of these tests are guards rather than assertions about maths: a zero in the config file
 * must never reach {@code RandomSource.nextInt}, because a zero divisor crashes the chunk that ticks
 * the farm (the shape of bug MOD-169 was). The rest pin the balance target down — "an hour or two
 * per crystal unaided" is the design, and without a test it is a number somebody once did on paper
 * and nobody would notice drifting.
 */
class CrystalGrowthTest {

	@Test
	void boostsDivideAndCompose() {
		assertEquals(240, CrystalGrowth.effectiveChanceDivisor(240, false, false, 3, 2));
		assertEquals(80, CrystalGrowth.effectiveChanceDivisor(240, true, false, 3, 2));
		assertEquals(120, CrystalGrowth.effectiveChanceDivisor(240, false, true, 3, 2));
		// Multiplicative, so neither boost can make the other pointless.
		assertEquals(40, CrystalGrowth.effectiveChanceDivisor(240, true, true, 3, 2));
	}

	@Test
	void theSprinklerIsAThirdIndependentAxis() {
		// MOD-525. Each axis alone, then the full stack: the point of the third one is that it composes
		// with the other two rather than replacing either, so 240 / 3 / 2 / 2 = 20.
		assertEquals(120, CrystalGrowth.effectiveChanceDivisor(240, false, false, true, 3, 2, 2));
		assertEquals(40, CrystalGrowth.effectiveChanceDivisor(240, true, false, true, 3, 2, 2));
		assertEquals(20, CrystalGrowth.effectiveChanceDivisor(240, true, true, true, 3, 2, 2));
		// And it is genuinely optional: absent, the answer is exactly what the two-axis form gives.
		assertEquals(CrystalGrowth.effectiveChanceDivisor(240, true, true, 3, 2),
				CrystalGrowth.effectiveChanceDivisor(240, true, true, false, 3, 2, 2));
	}

	@Test
	void theTwoAxisFormIgnoresTheSprinkler() {
		// The old signature is kept for its callers and must stay a pure delegation: if it ever started
		// applying a sprinkler bonus of its own, every pre-MOD-525 expectation above would shift.
		for (boolean water : new boolean[] {false, true}) {
			for (boolean powered : new boolean[] {false, true}) {
				assertEquals(CrystalGrowth.effectiveChanceDivisor(240, water, powered, false, 3, 2, 99),
						CrystalGrowth.effectiveChanceDivisor(240, water, powered, 3, 2));
			}
		}
	}

	@Test
	void divisorNeverReachesZero() {
		// A config holding 0 — or boosts large enough to divide the divisor away — must still leave a
		// legal argument for nextInt, which throws on anything below 1.
		assertEquals(1, CrystalGrowth.effectiveChanceDivisor(0, false, false, 3, 2));
		assertEquals(1, CrystalGrowth.effectiveChanceDivisor(2, true, true, 1000, 1000));
		assertEquals(1, CrystalGrowth.effectiveChanceDivisor(-5, true, true, 3, 2));
	}

	@Test
	void zeroSpeedupsAreTreatedAsNoBoost() {
		// Not merely "clamped": a 0 here would divide by zero outright, and a negative one would flip
		// the boost into a penalty — both have to read as "this boost does nothing".
		assertEquals(240, CrystalGrowth.effectiveChanceDivisor(240, true, true, 0, 0));
		assertEquals(240, CrystalGrowth.effectiveChanceDivisor(240, true, true, -3, -2));
	}

	@Test
	void shippedDefaultsHitTheBalanceTarget() {
		int unaided = CrystalGrowth.effectiveChanceDivisor(Config.crystalFarmGrowthChanceDivisor,
				false, false, Config.crystalFarmWaterSpeedup, Config.crystalFarmPowerSpeedup);
		double hours = CrystalGrowth.expectedSecondsPerCrystal(unaided,
				Config.crystalFarmGrowthIntervalTicks) / 3600.0;
		assertTrue(hours >= 1.0 && hours <= 2.0,
				"an unaided crystal should take one to two hours, got " + hours);
	}

	@Test
	void boostsActuallyShortenTheWait() {
		double unaided = CrystalGrowth.expectedSecondsPerCrystal(
				CrystalGrowth.effectiveChanceDivisor(Config.crystalFarmGrowthChanceDivisor, false, false,
						Config.crystalFarmWaterSpeedup, Config.crystalFarmPowerSpeedup),
				Config.crystalFarmGrowthIntervalTicks);
		double watered = CrystalGrowth.expectedSecondsPerCrystal(
				CrystalGrowth.effectiveChanceDivisor(Config.crystalFarmGrowthChanceDivisor, true, false,
						Config.crystalFarmWaterSpeedup, Config.crystalFarmPowerSpeedup),
				Config.crystalFarmGrowthIntervalTicks);
		double powered = CrystalGrowth.expectedSecondsPerCrystal(
				CrystalGrowth.effectiveChanceDivisor(Config.crystalFarmGrowthChanceDivisor, true, true,
						Config.crystalFarmWaterSpeedup, Config.crystalFarmPowerSpeedup),
				Config.crystalFarmGrowthIntervalTicks);
		assertTrue(watered < unaided, "water must help");
		assertTrue(powered < watered, "power must help on top of water");
	}

	@Test
	void aFedShardComesBackWorthMoreThanItself() {
		// The feature exists to make amethyst renewable, so a shard fed in must come back as more than
		// a shard. The profit is not the bud count -- it is what the ripe crystal drops.
		int perShard = Config.crystalSeedbedChargesPerShard * CrystalGrowth.SHARDS_PER_RIPE_CRYSTAL;
		assertTrue(perShard > 1,
				"a shard must come back worth more than itself, got " + perShard);
		// And the other side of it, which is the easier mistake to make: a knob nudged upwards turns a
		// farm that pays for patience into one that prints amethyst. It shipped at twelvefold once.
		assertTrue(perShard <= 8,
				"a return above eightfold per shard is a printer, not a farm, got " + perShard);
	}
}
