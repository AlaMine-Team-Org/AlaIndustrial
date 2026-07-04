package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
