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
