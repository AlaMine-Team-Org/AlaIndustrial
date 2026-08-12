package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for the MOD-385 component ladder — the three grades of wind-mill rotor and of
 * water-mill wheel. Runs without a Minecraft runtime because {@link ComponentTier} is keyed by
 * registry path rather than by {@code Item}.
 *
 * <p><b>Life is measured, not derived.</b> {@link #rotorLifeIsMeasuredBySimulatingWear} and its wheel
 * twin drive the real {@link ComponentWear#step} in a loop until the component breaks and count the EU
 * that actually went through it. Asserting {@code maxDamage × euPerDamage} against
 * {@code maxDamage × euPerDamage} would be a tautology — the same arithmetic on both sides of the
 * equals — and the task explicitly refuses to count that as coverage.
 */
class ComponentTierTest {
	/** Rotor grades in ladder order. */
	private static final List<ComponentTier> ROTORS = List.of(
			ComponentTier.WINDMILL_ROTOR,
			ComponentTier.WINDMILL_ROTOR_REINFORCED,
			ComponentTier.WINDMILL_ROTOR_ADVANCED);
	/** Wheel grades in ladder order. */
	private static final List<ComponentTier> WHEELS = List.of(
			ComponentTier.WATER_MILL_WHEEL,
			ComponentTier.WATER_MILL_WHEEL_REINFORCED,
			ComponentTier.WATER_MILL_WHEEL_ADVANCED);

	/**
	 * Drive real wear ticks until the component breaks; return the total EU of production it absorbed.
	 * Mirrors {@code AbstractGeneratorBlockEntity#wearComponent}: accumulator carried across ticks,
	 * damage advanced by {@link ComponentWear#step}, stop on {@code broken()}.
	 */
	private static long measureLifeEu(ComponentTier tier, int euPerTick) {
		int accumulator = 0;
		int damage = 0;
		long producedEu = 0;
		int maxDamage = tier.maxDamage();
		// Generous bound: even the advanced rotor at 1 EU/t breaks long before this, and an infinite
		// loop here would hang the suite rather than fail it.
		for (int tick = 0; tick < 20_000_000; tick++) {
			ComponentWear.Result result = ComponentWear.step(accumulator, euPerTick, 1.0f,
					tier.euPerDamage(), damage, maxDamage);
			producedEu += euPerTick;
			accumulator = result.accumulatorEu();
			damage = result.newDamage();
			if (result.broken()) {
				return producedEu;
			}
		}
		throw new AssertionError("component " + tier + " never broke — wear model is stalled");
	}

	@Test
	void rotorLifeIsMeasuredBySimulatingWear() {
		// 8 EU/t = the T1 mill's own cap, so the loop stays short while the numbers stay realistic.
		long wooden = measureLifeEu(ComponentTier.WINDMILL_ROTOR, 8);
		long reinforced = measureLifeEu(ComponentTier.WINDMILL_ROTOR_REINFORCED, 8);
		long advanced = measureLifeEu(ComponentTier.WINDMILL_ROTOR_ADVANCED, 8);

		// Independent expectation, written out as the shipped balance promises it (docs/PERFORMANCE.md),
		// not recomputed from the tier's own getters. Tolerance is one wear tick: the last point breaks
		// the component partway through a tick, so the measured total overshoots by at most euPerTick.
		assertTrue(Math.abs(wooden - 480_000L) <= 8, "wooden rotor life ≈ 480 000 EU, measured " + wooden);
		assertTrue(Math.abs(reinforced - 1_800_000L) <= 8,
				"reinforced rotor life ≈ 1 800 000 EU, measured " + reinforced);
		assertTrue(Math.abs(advanced - 4_320_000L) <= 8,
				"advanced rotor life ≈ 4 320 000 EU, measured " + advanced);
		assertTrue(reinforced > wooden && advanced > reinforced, "rotor life must grow with the grade");
	}

	@Test
	void wheelLifeIsMeasuredBySimulatingWear() {
		long wooden = measureLifeEu(ComponentTier.WATER_MILL_WHEEL, 4);
		long reinforced = measureLifeEu(ComponentTier.WATER_MILL_WHEEL_REINFORCED, 4);
		long advanced = measureLifeEu(ComponentTier.WATER_MILL_WHEEL_ADVANCED, 4);

		assertTrue(Math.abs(wooden - 320_000L) <= 4, "wooden wheel life ≈ 320 000 EU, measured " + wooden);
		assertTrue(Math.abs(reinforced - 1_200_000L) <= 4,
				"reinforced wheel life ≈ 1 200 000 EU, measured " + reinforced);
		assertTrue(Math.abs(advanced - 2_880_000L) <= 4,
				"advanced wheel life ≈ 2 880 000 EU, measured " + advanced);
		assertTrue(reinforced > wooden && advanced > reinforced, "wheel life must grow with the grade");
	}

	@Test
	void everyGradeIsStrictlyBetterThanTheOneBelowIt() {
		for (List<ComponentTier> family : List.of(ROTORS, WHEELS)) {
			for (int i = 1; i < family.size(); i++) {
				ComponentTier lower = family.get(i - 1);
				ComponentTier upper = family.get(i);
				assertTrue(upper.outputMultiplier() > lower.outputMultiplier(),
						upper + " must out-produce " + lower);
				assertTrue(upper.maxDamage() > lower.maxDamage(),
						upper + " must outlast " + lower);
			}
		}
	}

	/**
	 * The rule that makes the advertised life gain honest: wear is charged on EU produced, so
	 * {@code euPerDamage} must scale with the output multiplier. If it did not, a ×1.5 rotor would burn
	 * durability 50 % faster and a third of its ×6 durability advantage would silently evaporate.
	 * Expressed as "EU of production per durability point, per unit of output multiplier is constant
	 * across the ladder".
	 */
	@Test
	void euPerDamageTracksTheOutputMultiplier() {
		for (List<ComponentTier> family : List.of(ROTORS, WHEELS)) {
			ComponentTier base = family.get(0);
			float baseline = base.euPerDamage() / base.outputMultiplier();
			for (ComponentTier tier : family) {
				assertEquals(baseline, tier.euPerDamage() / tier.outputMultiplier(), 0.001f,
						tier + ": wear per unit of mechanical work must match the T1 baseline, "
								+ "otherwise the durability gain is partly cancelled by faster wear");
			}
		}
	}

	/** The T1 baseline is exactly 1.0 — the negative control for every "multiplier applied" test. */
	@Test
	void baselineGradesDoNotMultiply() {
		assertEquals(1.0f, ComponentTier.WINDMILL_ROTOR.outputMultiplier(), 0.0001f,
				"the wooden rotor is the ladder's baseline and must not scale output");
		assertEquals(1.0f, ComponentTier.WATER_MILL_WHEEL.outputMultiplier(), 0.0001f,
				"the wooden wheel is the ladder's baseline and must not scale output");
	}

	@Test
	void gradesResolveByRegistryPath() {
		for (ComponentTier tier : ComponentTier.values()) {
			assertSame(tier, ComponentTier.forItemPath(tier.itemPath()),
					tier + " must resolve from its own registry path");
		}
		assertNotNull(ComponentTier.forItemPath("windmill_rotor_advanced"));
		assertNull(ComponentTier.forItemPath("iron_ingot"), "a foreign item resolves to no grade");
		assertNull(ComponentTier.forItemPath(""), "an empty path resolves to no grade");
	}

	/** Grades must not share an id — a duplicate would make {@code forItemPath} order-dependent. */
	@Test
	void registryPathsAreUnique() {
		long distinct = List.of(ComponentTier.values()).stream()
				.map(ComponentTier::itemPath).distinct().count();
		assertEquals(ComponentTier.values().length, distinct, "every grade needs its own item path");
	}

	/**
	 * Numbers are read live from {@link Config} (the {@code CableType} contract), not captured at class
	 * load. A server operator retuning {@code alaindustrial.json} must see the change without a code
	 * change — and the wear path reads {@code euPerDamage()} every tick expecting exactly that.
	 */
	@Test
	void knobsAreReadLiveFromConfig() {
		int original = Config.windMillRotorReinforcedEuPerDamage;
		try {
			Config.windMillRotorReinforcedEuPerDamage = original + 111;
			assertEquals(original + 111, ComponentTier.WINDMILL_ROTOR_REINFORCED.euPerDamage(),
					"euPerDamage must follow Config at runtime, not a load-time snapshot");
		} finally {
			Config.windMillRotorReinforcedEuPerDamage = original;
		}
	}
}
