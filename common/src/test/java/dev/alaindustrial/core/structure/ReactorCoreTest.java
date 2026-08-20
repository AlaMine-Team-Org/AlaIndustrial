package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ReactorCore} (MOD-468) — the reactor's whole balance, exercised as
 * arithmetic instead of by playing.
 *
 * <p><b>Every expectation here is a literal, not the formula written twice.</b> Re-deriving the
 * answer inside the assertion is the tautology trap this project has been bitten by: such a test
 * passes for any implementation, including the broken ones these numbers were computed against by
 * hand.
 *
 * <p>Three of the cases below are regressions, not coverage. The reactor shipped stage 2 with a
 * neighbour bonus shared between output and heat, which made density cosmetic; with a truncating
 * water conversion that cooled a trickle of heat for free; and with the shape of {@link
 * ReactorCore#output} depending on depth <em>before</em> the tier ceiling, which flattened the
 * throttle to a single setting on any core worth building.
 */
class ReactorCoreTest {

	/** Config defaults at the time of writing — see {@code Config.reactor*}. */
	private static final int EU_PER_ROD = 6;
	private static final int HEAT_PER_ROD = 4;
	private static final int ENERGY_BONUS = 25;
	private static final int HEAT_BONUS = 40;
	private static final int HEAT_PER_WATER = 2;

	@Test
	void loneColumnProducesItsRodsWorthAndNothingMore() {
		// 4 rods x 6 EU, no neighbours, rods fully inserted.
		assertEquals(24, ReactorCore.output(4, 0, EU_PER_ROD, ENERGY_BONUS, ReactorCore.FULL_DEPTH));
		assertEquals(16, ReactorCore.heatProduced(4, 0, HEAT_PER_ROD, HEAT_BONUS, ReactorCore.FULL_DEPTH));
	}

	@Test
	void eachAdjacencyPaysBothColumns() {
		// 8 rods = 48 base; one pair grants 25% to both racks, so +50%: 72.
		assertEquals(72, ReactorCore.output(8, 1, EU_PER_ROD, ENERGY_BONUS, ReactorCore.FULL_DEPTH));
	}

	/**
	 * The point of the whole stage: heat rises with density FASTER than output does. While the two
	 * bonuses were one number this was impossible to observe — heat was a fixed fraction of output for
	 * every arrangement, and a packed core ran exactly as cool as a sparse one at the same power.
	 */
	@Test
	void densityCostsMoreHeatThanItEarnsPower() {
		long sparseOut = ReactorCore.output(8, 0, EU_PER_ROD, ENERGY_BONUS, ReactorCore.FULL_DEPTH);
		long sparseHeat = ReactorCore.heatProduced(8, 0, HEAT_PER_ROD, HEAT_BONUS, ReactorCore.FULL_DEPTH);
		long packedOut = ReactorCore.output(8, 1, EU_PER_ROD, ENERGY_BONUS, ReactorCore.FULL_DEPTH);
		long packedHeat = ReactorCore.heatProduced(8, 1, HEAT_PER_ROD, HEAT_BONUS, ReactorCore.FULL_DEPTH);

		assertEquals(48, sparseOut);
		assertEquals(32, sparseHeat);
		assertEquals(72, packedOut);
		// 32 base heat, +80% for the pair counted on both racks.
		assertEquals(57, packedHeat);
		// Power grew by half; heat grew by four fifths. Stated as a comparison because THAT is the
		// invariant — the literals above would still pass if both bonuses were raised together.
		assertTrue(packedHeat * sparseOut > packedOut * sparseHeat,
				"heat must grow faster than output as columns are packed together");
	}

	@Test
	void depthScalesOutputAndHeatTogether() {
		assertEquals(12, ReactorCore.output(4, 0, EU_PER_ROD, ENERGY_BONUS, 500));
		assertEquals(8, ReactorCore.heatProduced(4, 0, HEAT_PER_ROD, HEAT_BONUS, 500));
		assertEquals(0, ReactorCore.output(4, 0, EU_PER_ROD, ENERGY_BONUS, 0));
		assertEquals(0, ReactorCore.heatProduced(4, 0, HEAT_PER_ROD, HEAT_BONUS, 0));
	}

	@Test
	void noRodsMeansNoReactor() {
		assertEquals(0, ReactorCore.output(0, 4, EU_PER_ROD, ENERGY_BONUS, ReactorCore.FULL_DEPTH));
		assertEquals(0, ReactorCore.heatProduced(0, 4, HEAT_PER_ROD, HEAT_BONUS, ReactorCore.FULL_DEPTH));
	}

	@Test
	void depthIsClampedRatherThanTrusted() {
		assertEquals(24, ReactorCore.output(4, 0, EU_PER_ROD, ENERGY_BONUS, 9999));
		assertEquals(0, ReactorCore.output(4, 0, EU_PER_ROD, ENERGY_BONUS, -50));
	}

	/**
	 * Water rounds UP. The truncating version returned zero for any heat below the exchange rate, so a
	 * reactor idling just under it held its temperature on no coolant at all — free cooling, in the one
	 * regime where a player is most likely to be watching the gauge.
	 */
	@Test
	void waterForHeatRoundsUpSoNoHeatIsCooledForFree() {
		assertEquals(1, ReactorCore.waterForHeat(1, HEAT_PER_WATER));
		assertEquals(1, ReactorCore.waterForHeat(2, HEAT_PER_WATER));
		assertEquals(2, ReactorCore.waterForHeat(3, HEAT_PER_WATER));
		assertEquals(104, ReactorCore.waterForHeat(208, HEAT_PER_WATER));
		assertEquals(0, ReactorCore.waterForHeat(0, HEAT_PER_WATER));
		assertEquals(0, ReactorCore.waterForHeat(-5, HEAT_PER_WATER));
	}

	/** A zero or negative exchange rate must not divide — a misconfigured file is not a crash. */
	@Test
	void aBrokenExchangeRateCoolsNothingInsteadOfThrowing() {
		assertEquals(0, ReactorCore.waterForHeat(100, 0));
		assertEquals(0, ReactorCore.waterForHeat(100, -3));
		assertEquals(0, ReactorCore.heatRemovedByWater(100, 0));
	}

	@Test
	void boiledWaterRemovesItsExchangeRateInHeat() {
		assertEquals(208, ReactorCore.heatRemovedByWater(104, HEAT_PER_WATER));
		assertEquals(0, ReactorCore.heatRemovedByWater(0, HEAT_PER_WATER));
		assertEquals(0, ReactorCore.heatRemovedByWater(-4, HEAT_PER_WATER));
	}

	/**
	 * Rounding up the demand and back down through the exchange must never remove MORE heat than was
	 * produced — otherwise a reactor cools itself below zero and the gauge reads a temperature the
	 * core never had.
	 */
	@Test
	void coolingNeverOvershootsTheHeatItWasAskedToRemove() {
		for (int heat = 1; heat <= 200; heat++) {
			long water = ReactorCore.waterForHeat(heat, HEAT_PER_WATER);
			long removed = ReactorCore.heatRemovedByWater(water, HEAT_PER_WATER);
			assertTrue(removed >= heat, "coolant must cover the heat it was sized for, at " + heat);
			assertTrue(removed - heat < HEAT_PER_WATER,
					"overshoot must stay under one millibucket's worth, at " + heat);
		}
	}

	/**
	 * The regression this whole model exists for. Heat is charged against the energy produced, and a
	 * core at the tier ceiling must therefore settle at a bounded temperature however many columns are
	 * packed into it. The previous version carried an "effective depth" per mille with a floor of 1,
	 * which made heat {@code fullHeat / 1000} — a figure that grows without limit with the room, and
	 * reached roughly 105 000 a tick in a room the size the scan actually allows.
	 */
	@Test
	void heatStaysBoundedHoweverLargeTheCoreGets() {
		long ceiling = 512;
		long worst = 0;
		for (int columns = 1; columns <= 350; columns++) {
			int rodCount = columns * 4;
			// Adjacencies grow faster than columns do in a packed block; 3 per column is already denser
			// than a 12-block room can be, so this over-states the case rather than flattering it.
			int pairs = Math.max(0, columns * 3 - 3);
			long euFull = ReactorCore.output(rodCount, pairs, EU_PER_ROD, ENERGY_BONUS,
					ReactorCore.FULL_DEPTH);
			long heatFull = ReactorCore.heatProduced(rodCount, pairs, HEAT_PER_ROD, HEAT_BONUS,
					ReactorCore.FULL_DEPTH);
			long output = Math.min(euFull, ceiling);
			worst = Math.max(worst, ReactorCore.heatForOutput(heatFull, output, euFull));
		}
		// Converges on ceiling x heatPerRod x heatBonus / (euPerRod x energyBonus)
		// = 512 x 4 x 1.6 / 6 = 546. The number itself is not the point — that it CONVERGES is, and the
		// bound is set just above it so a change that reintroduces unbounded growth still fails here.
		assertTrue(worst < 600, "heat at the tier ceiling must stay bounded, was " + worst);
	}

	@Test
	void aRodIsAnAmountOfEnergy() {
		assertEquals(144_000, ReactorCore.rodEnergy(6, 24_000));
		assertEquals(0, ReactorCore.rodEnergy(0, 24_000));
		assertEquals(0, ReactorCore.rodEnergy(12, -1));
	}

	@Test
	void heatForOutputIsProportionalAndSafeAtTheEdges() {
		assertEquals(50, ReactorCore.heatForOutput(100, 512, 1024));
		assertEquals(100, ReactorCore.heatForOutput(100, 1024, 1024));
		assertEquals(0, ReactorCore.heatForOutput(100, 0, 1024));
		assertEquals(0, ReactorCore.heatForOutput(0, 512, 1024));
		assertEquals(0, ReactorCore.heatForOutput(100, 512, 0));
	}

	/**
	 * The regression behind "why is the gauge always 0%". With a flat loss a reactor had two states and
	 * no others: production under the loss pinned it at zero forever, production over it climbed to the
	 * top. A player's first reactor lands in the first case, so the temperature readout on a working
	 * machine never moved at all.
	 */
	@Test
	void everyCoreSettlesAtItsOwnTemperature() {
		int base = 4;
		int loss = 8;
		int capacity = 10_000;
		// All three are inside what the shell can shed on its own: 4 + 10000 x 8/1000 = 84 at the top of
		// the scale, so anything under that finds a resting point.
		long[] outputs = {16, 40, 70};
		long previous = -1;
		for (long produced : outputs) {
			long heat = settle(produced, base, loss, capacity);
			assertTrue(heat > 0, "a running reactor must warm up, produced=" + produced);
			assertTrue(heat < capacity, "it must also stop short of the top, produced=" + produced);
			assertTrue(heat > previous, "a hotter core must settle higher, produced=" + produced);
			previous = heat;
		}
	}

	/**
	 * Past the shell's own ceiling there is no resting point, and that is the design: the coolant loop
	 * is what holds a core that the shell alone cannot, and this is the case it exists for.
	 */
	@Test
	void aCoreBeyondTheShellsCeilingRunsAwayWithoutCoolant() {
		int capacity = 10_000;
		assertEquals(capacity, settle(120, 4, 8, capacity));
	}

	private static long settle(long produced, int base, int loss, int capacity) {
		long heat = 0;
		for (int tick = 0; tick < 20_000; tick++) {
			heat = ReactorCore.settleHeat(heat, produced,
					ReactorCore.naturalCooling(heat, base, loss), capacity);
		}
		return heat;
	}

	@Test
	void naturalCoolingRisesWithTemperature() {
		assertEquals(4, ReactorCore.naturalCooling(0, 4, 8));
		assertEquals(12, ReactorCore.naturalCooling(1000, 4, 8));
		assertEquals(84, ReactorCore.naturalCooling(10_000, 4, 8));
		// A misconfigured file must not divide or go negative.
		assertEquals(0, ReactorCore.naturalCooling(1000, -5, 0));
		assertEquals(4, ReactorCore.naturalCooling(-1000, 4, 8));
	}

	@Test
	void heatSettlesTowardsZeroAndStopsThere() {
		assertEquals(70, ReactorCore.settleHeat(100, 0, 30, 10000));
		assertEquals(0, ReactorCore.settleHeat(10, 0, 30, 10000));
		assertEquals(10000, ReactorCore.settleHeat(9000, 5000, 30, 10000));
		// A shut-down core still cools: that is what makes "scram and wait" a recovery.
		assertEquals(0, ReactorCore.settleHeat(30, 0, 30, 10000));
	}

	@Test
	void heatPercentSurvivesAMisconfiguredScale() {
		assertEquals(50, ReactorCore.heatPercent(5000, 10000));
		assertEquals(100, ReactorCore.heatPercent(50000, 10000));
		assertEquals(0, ReactorCore.heatPercent(100, 0));
	}
}
