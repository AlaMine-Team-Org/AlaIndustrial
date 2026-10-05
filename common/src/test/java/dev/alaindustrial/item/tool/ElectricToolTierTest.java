package dev.alaindustrial.item.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1 suite for the electric tool energy catalog (MOD-707). Pins the shipped numbers of every tier and
 * the one relation the catalog exists to keep: the netherite drill differs from the base drill in its
 * buffer only.
 */
class ElectricToolTierTest {

	@Test
	@DisplayName("each tier reads its shipped buffer, intake and per-block cost")
	void shippedNumbers() {
		assertTier(ElectricToolTier.DRILL, 10_000, 32, 50);
		assertTier(ElectricToolTier.DRILL_NETHERITE_TIP, 15_000, 32, 50);
		assertTier(ElectricToolTier.CHAINSAW, 10_000, 32, 30);
		assertTier(ElectricToolTier.SHOVEL, 10_000, 32, 20);
		assertTier(ElectricToolTier.HOE, 10_000, 32, 50);
	}

	@Test
	@DisplayName("the netherite drill shares the base drill's intake and cost (MOD-534)")
	void netheriteDiffersInBufferOnly() {
		assertEquals(ElectricToolTier.DRILL.inputRate(), ElectricToolTier.DRILL_NETHERITE_TIP.inputRate());
		assertEquals(ElectricToolTier.DRILL.euPerBlock(), ElectricToolTier.DRILL_NETHERITE_TIP.euPerBlock());
	}

	private static void assertTier(ElectricToolTier tier, int buffer, int inputRate, int euPerBlock) {
		assertEquals(buffer, tier.buffer(), tier + " buffer");
		assertEquals(inputRate, tier.inputRate(), tier + " input rate");
		assertEquals(euPerBlock, tier.euPerBlock(), tier + " EU per block");
	}
}
