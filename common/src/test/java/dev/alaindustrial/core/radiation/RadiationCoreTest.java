package dev.alaindustrial.core.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link RadiationCore} (MOD-470) — the whole arithmetic of a dose.
 *
 * <p>Two of the cases here are regressions from real playtests, and they are the reason this suite
 * asserts numbers rather than "something happened":
 *
 * <ul>
 * <li>{@link #sourceWeakerThanDecayNeverAccumulates()} — the dose lives in the duration of an effect
 * that vanilla ticks down once per tick, so a source contributing less per sweep than the sweep
 * interval is invisible no matter how long you stand there. The shipped numbers used to sit exactly on
 * that line: villagers took damage from a thrown ingot and could never transform.</li>
 * <li>{@link #cappedSourceCannotPushPastItsCeiling()} — raw ore may nauseate and must never kill, and
 * the ceiling is read against the dose already carried rather than against the ore's own contribution,
 * which is what stops a bag of ore from topping up a lethal dose.</li>
 * </ul>
 */
class RadiationCoreTest {

	private static final int CAPACITY = 6000;

	@Test
	void levelsFollowTheShareOfTheScale() {
		assertEquals(0, RadiationCore.level(0, CAPACITY), "clean");
		assertEquals(0, RadiationCore.level(59, CAPACITY), "under 1 % is still clean");
		assertEquals(1, RadiationCore.level(60, CAPACITY), "1 % is the first symptom");
		assertEquals(1, RadiationCore.level(1499, CAPACITY));
		assertEquals(2, RadiationCore.level(1500, CAPACITY), "25 %");
		assertEquals(2, RadiationCore.level(3599, CAPACITY));
		assertEquals(3, RadiationCore.level(3600, CAPACITY), "60 %");
		assertEquals(3, RadiationCore.level(5399, CAPACITY));
		assertEquals(4, RadiationCore.level(5400, CAPACITY), "90 % is lethal");
		assertEquals(4, RadiationCore.level(CAPACITY, CAPACITY));
	}

	@Test
	void levelIsZeroWithoutAScale() {
		assertEquals(0, RadiationCore.level(5000, 0), "a capacity of zero has no bands to fall in");
		assertEquals(0, RadiationCore.level(-5, CAPACITY));
	}

	@Test
	void doseAccumulatesAndStopsAtTheTop() {
		assertEquals(100, RadiationCore.addDose(0, 100, CAPACITY));
		assertEquals(300, RadiationCore.addDose(100, 200, CAPACITY));
		assertEquals(CAPACITY, RadiationCore.addDose(CAPACITY - 1, 999_999, CAPACITY), "never past the top");
		assertEquals(500, RadiationCore.addDose(500, 0, CAPACITY), "no source, no change");
		assertEquals(500, RadiationCore.addDose(500, -50, CAPACITY), "a negative source is not a cure");
	}

	/**
	 * The trap that shipped once: the dose decays by {@code radiationTickInterval} between sweeps, so a
	 * source contributing that much per sweep nets exactly zero and one contributing less goes backwards.
	 * The assertion is on the SHIPPED config values, so lowering a per-item number back under the decay
	 * rate turns this test red instead of turning the mechanic silently off.
	 */
	@Test
	void sourceWeakerThanDecayNeverAccumulates() {
		int decay = Config.radiationTickInterval;
		assertTrue(Config.radiationDoseHighPerItem > decay,
				"one refined-uranium item must outpace the decay, or fuel in the pockets does nothing");
		assertTrue(Config.radiationDoseMediumPerItem > decay,
				"one uranium ingot must outpace the decay");
		assertTrue(Config.radiationDoseLowPerItem > decay,
				"one piece of ore must outpace the decay, or the low tier is decoration");
		assertTrue(Config.radiationRodDosePerTick > decay,
				"a fuelled rod must outpace the decay");

		// The arithmetic itself, independent of the numbers: a sweep that adds exactly the decay leaves
		// the dose where it started.
		int dose = 500;
		int afterDecay = dose - decay;
		assertEquals(dose, RadiationCore.addDose(afterDecay, decay, CAPACITY));
	}

	@Test
	void shieldingScalesWithWornPieces() {
		assertEquals(1000, RadiationCore.shielded(1000, 0, 25, 100), "no suit, no help");
		assertEquals(750, RadiationCore.shielded(1000, 1, 25, 100));
		assertEquals(500, RadiationCore.shielded(1000, 2, 25, 100));
		assertEquals(0, RadiationCore.shielded(1000, 4, 25, 100), "the full set stops ordinary exposure");
		assertEquals(0, RadiationCore.shielded(1000, 9, 25, 100), "more pieces than slots is still four");
	}

	/** The core cap is what keeps a full suit from being immunity to a live reactor. */
	@Test
	void shieldingAgainstARodIsCapped() {
		assertEquals(50, RadiationCore.shielded(1000, 4, 25, 95), "5 % gets through in the open core");
		assertTrue(RadiationCore.shielded(1000, 4, 25, Config.radiationRodShieldCapPercent) > 0,
				"the shipped cap must leave something through, or the suit ends the mechanic");
	}

	@Test
	void shieldingHandlesEmptyExposure() {
		assertEquals(0, RadiationCore.shielded(0, 2, 25, 100));
		assertEquals(0, RadiationCore.shielded(-10, 0, 25, 100));
	}

	@Test
	void ceilingIsAShareOfTheScale() {
		assertEquals(1200, RadiationCore.cappedCeiling(CAPACITY, 20));
		assertEquals(0, RadiationCore.cappedCeiling(CAPACITY, 0));
		assertEquals(CAPACITY, RadiationCore.cappedCeiling(CAPACITY, 100));
		assertEquals(CAPACITY, RadiationCore.cappedCeiling(CAPACITY, 500), "percent clamps at 100");
	}

	@Test
	void cappedSourceCannotPushPastItsCeiling() {
		int ceiling = RadiationCore.cappedCeiling(CAPACITY, 20);
		assertEquals(100, RadiationCore.cappedContribution(0, 100, ceiling), "well under the ceiling");
		assertEquals(200, RadiationCore.cappedContribution(1000, 500, ceiling), "trimmed to what is left");
		assertEquals(0, RadiationCore.cappedContribution(ceiling, 500, ceiling), "sitting on the ceiling");
		assertEquals(0, RadiationCore.cappedContribution(CAPACITY, 500, ceiling),
				"a lethal dose from elsewhere is not topped up by ore");
		assertEquals(0, RadiationCore.cappedContribution(0, 0, ceiling));
	}

	/** Raw ore may make a miner queasy and must never do more than that. */
	@Test
	void lowTierStaysInsideTheFirstBand() {
		int ceiling = RadiationCore.cappedCeiling(Config.radiationDoseCapacity, Config.radiationLowDoseCapPercent);
		assertTrue(RadiationCore.level(ceiling, Config.radiationDoseCapacity) <= 1,
				"ore alone must never reach the band that damages the player");
	}

	/**
	 * Distance is a defence. Before the falloff a rod six blocks away hit exactly as hard as one at
	 * your feet, so the only way to survive a core was a wall.
	 */
	@Test
	void strengthFallsOffWithDistance() {
		int radius = 6;
		int inYourFace = RadiationCore.attenuate(1000, 0.0, radius);
		int oneBlock = RadiationCore.attenuate(1000, 1.0, radius);
		int threeBlocks = RadiationCore.attenuate(1000, 3.0, radius);
		assertEquals(1000, inYourFace, "at zero distance a source is at full strength");
		assertTrue(oneBlock < inYourFace, "one block out must already cost something");
		assertTrue(threeBlocks < oneBlock, "and it must keep falling");
		assertTrue(threeBlocks > 0, "inside the radius it is never nothing");
		assertEquals(500, RadiationCore.attenuate(1000, 1.5, radius),
				"the half-strength distance is what makes the curve explainable");
	}

	@Test
	void nothingReachesPastTheRadius() {
		assertEquals(0, RadiationCore.attenuate(1000, 6.0, 6), "the radius is a hard edge");
		assertEquals(0, RadiationCore.attenuate(1000, 99.0, 6));
		assertEquals(0, RadiationCore.attenuate(0, 1.0, 6), "no source, no dose");
		assertEquals(0, RadiationCore.attenuate(1000, 1.0, 0), "no radius, no field");
	}

	/**
	 * Contact wears the suit, and the pace follows the source.
	 *
	 * <p>Both shipped mistakes are pinned here: wear must never exceed a point per sweep (the version
	 * that scaled with the dose destroyed a helmet in ten seconds beside one column), and it must never
	 * be zero for real contact (the version after that only wore under a fierce field, so carrying
	 * uranium — the contact a player actually has most of the time — cost nothing).
	 */
	@Test
	void wearIsPacedByHowFierceTheSourceIs() {
		int perPoint = 200;
		assertEquals(0, RadiationCore.wearInterval(0, perPoint), "nothing absorbed, nothing spent");
		assertEquals(1, RadiationCore.wearInterval(5000, perPoint), "a live core is a point every sweep");
		assertEquals(1, RadiationCore.wearInterval(perPoint, perPoint), "exactly the threshold is still every sweep");
		assertEquals(2, RadiationCore.wearInterval(80, perPoint), "one refined-uranium item: every other sweep");
		assertEquals(8, RadiationCore.wearInterval(24, perPoint), "one piece of ore: slow, but not free");
		assertTrue(RadiationCore.wearInterval(1, perPoint) > 8, "a trace source wears slower still");
		assertEquals(1, RadiationCore.wearInterval(10, 0), "a broken threshold still spends at most a point");
	}
}
