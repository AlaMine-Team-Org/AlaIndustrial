package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * L1 unit tests — voltage-tier invariants (pure logic, no Minecraft world).
 * Common-to-all-blocks layer: every energy block derives its caps from {@link EnergyTier}, so a
 * careless edit here changes game balance for all of them. Migrated from the legacy
 * {@code IndustrializationSelfTest} {@code BALANCE_INVARIANTS} check.
 *
 * @implements common-NRG voltage ordering (see docs/testing/AUTOMATION-STANDARDS.md §7 migration)
 */
class EnergyTierTest {

	@Test
	void voltage_stepsByFourPerTier() {
		assertEquals(4, EnergyTier.MV.maxVoltage() / EnergyTier.LV.maxVoltage(), "MV = 4× LV");
		assertEquals(4, EnergyTier.HV.maxVoltage() / EnergyTier.MV.maxVoltage(), "HV = 4× MV");
	}

	@Test
	void voltage_strictlyAscending() {
		assertTrue(EnergyTier.LV.maxVoltage() < EnergyTier.MV.maxVoltage());
		assertTrue(EnergyTier.MV.maxVoltage() < EnergyTier.HV.maxVoltage());
	}

	@ParameterizedTest
	@EnumSource(EnergyTier.class)
	void capacity_positive(EnergyTier tier) {
		assertTrue(tier.capacity() > 0, tier + " capacity must be > 0");
	}

	@Test
	void capacity_ascendingByTier() {
		assertTrue(EnergyTier.LV.capacity() < EnergyTier.MV.capacity());
		assertTrue(EnergyTier.MV.capacity() < EnergyTier.HV.capacity());
	}

	/**
	 * The {@code color()} ARGB value is the per-tier cable/UI tint (yellow/orange/red). Pitest flagged it as
	 * NO_COVERAGE — flipping the return to 0 would make every tier render black and no test noticed. Pin the
	 * exact ARGB constants (the raw hex literals from the enum decl) so a mutation changing any tier's colour
	 * is caught, and assert the three tiers have three distinct colours (a swap would otherwise go unnoticed).
	 */
	@Test
	void color_exactArgbPerTierAndAllDistinct() {
		assertEquals(0xFFD24A, EnergyTier.LV.color(), "LV cable tint — yellow");
		assertEquals(0xFF8A3D, EnergyTier.MV.color(), "MV cable tint — orange");
		assertEquals(0xFF3D5A, EnergyTier.HV.color(), "HV cable tint — red");
		// distinct — a swapped/aliased colour between tiers must be caught
		assertTrue(EnergyTier.LV.color() != EnergyTier.MV.color());
		assertTrue(EnergyTier.MV.color() != EnergyTier.HV.color());
		assertTrue(EnergyTier.LV.color() != EnergyTier.HV.color());
	}

	/**
	 * The tier ceiling reads live from {@link Config} at runtime — a mutation that broke that delegation
	 * (e.g. {@code return defaultMaxVoltage;} instead of {@code return Config.tierLvVoltage;}) would leave
	 * the runtime value stale and no existing test would notice, because every other test pins the value
	 * to a constant that matches BOTH paths. This test flips the Config field, calls the method, then
	 * restores it — proving the delegation is live and the config-backed value is what callers see.
	 */
	@Test
	void maxVoltage_readsLiveFromConfig() {
		int original = Config.tierLvVoltage;
		try {
			Config.tierLvVoltage = 99;
			assertEquals(99, EnergyTier.LV.maxVoltage(),
					"maxVoltage() must read Config.tierLvVoltage live — broken delegation");
		} finally {
			Config.tierLvVoltage = original;
		}
	}

	/**
	 * Same delegation check as {@link #maxVoltage_readsLiveFromConfig} for {@link #capacity()} — pins that
	 * the runtime capacity path is config-backed, not the compile-time default.
	 */
	@Test
	void capacity_readsLiveFromConfig() {
		int original = Config.tierHvCapacity;
		try {
			Config.tierHvCapacity = 999_999;
			assertEquals(999_999L, EnergyTier.HV.capacity(),
					"capacity() must read Config.tierHvCapacity live — broken delegation");
		} finally {
			Config.tierHvCapacity = original;
		}
	}

	/**
	 * The compile-time default the enum was constructed with is still exposed (for tests, migration code
	 * and documentation). It must equal the matching {@code Config.tier<V>v<Voltage>} default at class init
	 * — a divergence would mean the Config default shipped out of sync with the enum literal.
	 */
	@Test
	void defaultMaxVoltage_matchesConfigDefault() {
		assertEquals(Config.tierLvVoltage, EnergyTier.LV.defaultMaxVoltage());
		assertEquals(Config.tierMvVoltage, EnergyTier.MV.defaultMaxVoltage());
		assertEquals(Config.tierHvVoltage, EnergyTier.HV.defaultMaxVoltage());
	}
}
