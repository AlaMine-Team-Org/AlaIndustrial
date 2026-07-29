package dev.alaindustrial.core.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * L1 contract for MOD-258's Minecraft-free heat model.
 *
 * <p>The world adapter is deliberately excluded here: common L1 does not link Minecraft classes.
 * These tests pin the balance/wire values shared by the block entity, menu and screen.
 */
class HeatSourceTest {

	@Test
	void sourceLevelsAndOutputMultipliersMatchTheHeatTable() {
		assertHeat(HeatSource.NONE, 0);
		assertHeat(HeatSource.CAMPFIRE, 1);
		assertHeat(HeatSource.LAVA, 2);
		assertHeat(HeatSource.MAGMA, 2);
		assertHeat(HeatSource.LAVA_CAULDRON, 2);
		assertHeat(HeatSource.ELECTRIC_HEATER, 3);
	}

	@Test
	void translationKeysAreStableAndSourceSpecific() {
		assertEquals("gui.alaindustrial.vulcanizer.heat.none", HeatSource.NONE.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.heat.campfire", HeatSource.CAMPFIRE.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.heat.lava", HeatSource.LAVA.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.heat.magma", HeatSource.MAGMA.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.heat.lava_cauldron",
				HeatSource.LAVA_CAULDRON.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.heat.electric_heater",
				HeatSource.ELECTRIC_HEATER.translationKey());
	}

	@Test
	void ordinalWireFormatRoundTripsEverySource() {
		for (HeatSource source : HeatSource.values()) {
			assertEquals(source, HeatSource.byOrdinal(source.ordinal()));
		}
	}

	@Test
	void invalidOrdinalsFallBackToNoHeat() {
		assertEquals(HeatSource.NONE, HeatSource.byOrdinal(-1));
		assertEquals(HeatSource.NONE, HeatSource.byOrdinal(HeatSource.values().length));
		assertEquals(HeatSource.NONE, HeatSource.byOrdinal(Integer.MIN_VALUE));
		assertEquals(HeatSource.NONE, HeatSource.byOrdinal(Integer.MAX_VALUE));
	}

	private static void assertHeat(HeatSource source, int expectedLevel) {
		assertEquals(expectedLevel, source.level(), source + " level");
		assertEquals(expectedLevel, source.outputMultiplier(), source + " output multiplier");
	}
}
