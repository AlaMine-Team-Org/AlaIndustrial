package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests — balance invariants on the tunable {@link Config} knobs (pure logic, no world).
 * These read the compiled-in v0.2 defaults (never call {@link Config#load()} — that needs the
 * Fabric runtime). Migrated from the legacy {@code IndustrializationSelfTest}
 * {@code BALANCE_INVARIANTS} + {@code MULTIPLIERS} checks so a regression fails {@code ./gradlew test}
 * with a non-zero exit code instead of just logging.
 *
 * @implements common-NRG balance ordering + neutral-multiplier no-op
 */
class ConfigBalanceTest {

	@Test
	void generators_powerCurveOrdering() {
		assertTrue(Config.solarEuPerTick < Config.moonlitEuPerTick, "solar < moonlit");
		assertTrue(Config.moonlitEuPerTick < Config.daylightEuPerTick, "moonlit < daylight");
		assertTrue(Config.solarEuPerTick < Config.fuelEuPerTick, "solar < fuel");
		assertTrue(Config.fuelEuPerTick < Config.geothermalEuPerTick, "fuel < geothermal");
	}

	@Test
	void solar_weakerThanMachineDrain() {
		assertTrue(Config.solarEuPerTick < Config.machineEuPerTick,
				"a single T1 solar tick must not outpace a machine's drain");
	}

	@Test
	void speedMultiplier_neutralByDefault_isNoOp() {
		assertEquals(Config.machineEuPerTick, Config.machineEuPerTickEffective(),
				"default 1.0 speed multiplier must not change EU/t");
		assertEquals(100, Config.scaledDuration(100),
				"default 1.0 speed multiplier must not change duration");
	}

	/**
	 * MOD-203: the multiplier used to be exercised at 1.0 only, where it is a no-op by definition —
	 * so the direction of the scaling was pinned by nothing. A mutant swapping {@code /} for
	 * {@code *} in {@link Config#scaledDuration} survived the whole suite. Faster machine means
	 * FEWER ticks and MORE EU per tick; both literals below are hand-computed from the shipped
	 * defaults (100 t, 2 EU/t), never from the production formula.
	 */
	@Test
	void speedMultiplier_scalesDurationDownAndDrawUp() {
		float saved = Config.globalMachineSpeedMultiplier;
		try {
			Config.globalMachineSpeedMultiplier = 2.0f;
			assertEquals(50, Config.scaledDuration(100),
					"x2 speed must halve the duration (100 t -> 50 t), not double it");
			assertEquals(4, Config.machineEuPerTickEffective(),
					"x2 speed must double the per-tick draw (2 -> 4 EU/t)");

			Config.globalMachineSpeedMultiplier = 0.5f;
			assertEquals(200, Config.scaledDuration(100),
					"half speed must double the duration (100 t -> 200 t)");
			assertEquals(1, Config.machineEuPerTickEffective(),
					"half speed must halve the per-tick draw (2 -> 1 EU/t)");
		} finally {
			Config.globalMachineSpeedMultiplier = saved;
		}
	}

	/**
	 * MOD-086: the vanilla-smelt cost quoted in the electric furnace's recipe-viewer category must be
	 * what the machine really drains — scaled duration × effective per-tick draw. Each factor rounds
	 * on its own, so multiplying the raw config fields agrees only at the default multiplier.
	 *
	 * <p>MOD-203: the first assertion here used to compare
	 * {@code scaledDuration(dur) * machineEuPerTickEffective()} against
	 * {@link Config#electricFurnaceVanillaSmeltEu()} — which IS that expression, so it held no matter
	 * what the production code did. Both cases are now pinned by hand-computed literals, and the x3
	 * case is the one that matters: separate rounding makes it 33 × 6 = 198, a number the naive
	 * "100 × 2 × 1" reading cannot produce.
	 */
	@Test
	void electricFurnaceVanillaSmeltEu_roundsEachFactorSeparately() {
		assertEquals(200, Config.electricFurnaceVanillaSmeltEu(),
				"canonical vanilla smelt cost on the shipped defaults (100 t x 2 EU/t)");

		float saved = Config.globalMachineSpeedMultiplier;
		try {
			Config.globalMachineSpeedMultiplier = 3.0f;
			assertEquals(33, Config.scaledDuration(Config.electricFurnaceDuration),
					"x3: round(100 / 3) = 33 ticks");
			assertEquals(6, Config.machineEuPerTickEffective(),
					"x3: round(2 * 3) = 6 EU/t");
			assertEquals(198, Config.electricFurnaceVanillaSmeltEu(),
					"x3 costs 33 t x 6 EU/t = 198 EU, not the 200 a raw-field multiply would quote");
		} finally {
			Config.globalMachineSpeedMultiplier = saved;
		}
	}

	@Test
	void machineDurations_positive() {
		assertTrue(Config.maceratorDuration > 0);
		assertTrue(Config.electricFurnaceDuration > 0);
		assertTrue(Config.compressorDuration > 0);
		assertTrue(Config.extractorDuration > 0);
	}

	@Test
	void fuelGenerator_canonicalEightEuPerTick() {
		// Canon fixed 2026-06-29: code + PERFORMANCE.md agree on 8; concept doc was the outlier.
		assertEquals(8, Config.fuelEuPerTick);
	}
}
