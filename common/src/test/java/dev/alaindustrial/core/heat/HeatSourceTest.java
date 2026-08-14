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

	/**
	 * Every source maps to a drawable icon column, and only {@link HeatSource#NONE} draws nothing.
	 *
	 * <p>Pinned separately from {@link #sourceLevelsAndOutputMultipliersMatchTheHeatTable()} because the
	 * screen must not go back to deriving the column from the level: that derivation holds only while no
	 * two sources sharing a level look different, and it would break silently rather than loudly.
	 */
	@Test
	void everySourceHasItsOwnIconColumn() {
		assertEquals(-1, HeatSource.NONE.iconIndex(), "no heat draws no icon");
		assertEquals(0, HeatSource.CAMPFIRE.iconIndex());
		assertEquals(1, HeatSource.LAVA.iconIndex());
		assertEquals(1, HeatSource.MAGMA.iconIndex());
		assertEquals(1, HeatSource.LAVA_CAULDRON.iconIndex());
		assertEquals(2, HeatSource.ELECTRIC_HEATER.iconIndex());
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

	/**
	 * The ordinals already synced to clients and written into saves must not move.
	 *
	 * <p>The ordinal is the wire format ({@link HeatSource#byOrdinal}), so renumbering the table — by
	 * inserting a constant rather than appending one — would silently reinterpret every heat value in an
	 * existing world and every value already in flight to a client.
	 */
	@Test
	void shippedOrdinalsAreFrozen() {
		assertEquals(0, HeatSource.NONE.ordinal());
		assertEquals(1, HeatSource.CAMPFIRE.ordinal());
		assertEquals(2, HeatSource.LAVA.ordinal());
		assertEquals(3, HeatSource.MAGMA.ordinal());
		assertEquals(4, HeatSource.LAVA_CAULDRON.ordinal());
		assertEquals(5, HeatSource.ELECTRIC_HEATER.ordinal());
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
