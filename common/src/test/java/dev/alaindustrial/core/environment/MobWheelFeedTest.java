package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 tests for {@link MobWheelFeed} (MOD-763, D5/D8): the portion table per species.
 *
 * @implements mob wheel feed portions
 */
class MobWheelFeedTest {

	@ParameterizedTest
	@CsvSource({
		"CHICKEN, minecraft:wheat_seeds, 4", "CHICKEN, minecraft:pitcher_pod, 4",
		"CHICKEN, minecraft:torchflower_seeds, 4",
		"PIG, minecraft:carrot, 3", "PIG, minecraft:beetroot, 4", "PIG, minecraft:golden_carrot, 1",
		"SHEEP, minecraft:wheat, 3", "SHEEP, minecraft:hay_block, 1", "SHEEP, minecraft:sweet_berries, 6",
		"COW, minecraft:wheat, 3", "COW, minecraft:hay_block, 1", "GOAT, minecraft:hay_block, 1",
		"VILLAGER, minecraft:bread, 1", "VILLAGER, minecraft:carrot, 3", "VILLAGER, minecraft:baked_potato, 1",
		"VILLAGER, minecraft:cookie, 4", "VILLAGER, minecraft:pumpkin_pie, 1",
		"ZOMBIE, minecraft:rotten_flesh, 4", "ZOMBIE, minecraft:beef, 2", "ZOMBIE, minecraft:cooked_salmon, 1",
		"HUSK, minecraft:rotten_flesh, 4", "HUSK, minecraft:cooked_beef, 1",
		"DROWNED, minecraft:cod, 2", "DROWNED, minecraft:salmon, 2", "DROWNED, minecraft:dried_kelp, 3",
		"SKELETON, minecraft:bone, 3", "STRAY, minecraft:bone_block, 1", "BOGGED, minecraft:bone, 3",
		"PARCHED, minecraft:bone_block, 1",
		"WITCH, minecraft:sugar, 4", "WITCH, minecraft:glowstone_dust, 2", "WITCH, minecraft:spider_eye, 2",
		"VINDICATOR, minecraft:emerald, 1", "PILLAGER, minecraft:bread, 2", "EVOKER, minecraft:baked_potato, 2",
		"PIGLIN, minecraft:gold_nugget, 6", "PIGLIN, minecraft:gold_ingot, 1",
		"PIGLIN_BRUTE, minecraft:gold_nugget, 9", "PIGLIN_BRUTE, minecraft:gold_ingot, 1",
		"ZOMBIFIED_PIGLIN, minecraft:rotten_flesh, 4", "ZOMBIFIED_PIGLIN, minecraft:gold_nugget, 6",
		"CREEPER, minecraft:gunpowder, 4", "CREEPER, minecraft:fire_charge, 2", "CREEPER, minecraft:tnt, 1" })
	void portionSizes(MobWheelProfile species, String item, int expected) {
		assertEquals(expected, MobWheelFeed.portion(species, item));
		assertTrue(MobWheelFeed.accepts(species, item));
		assertTrue(MobWheelFeed.anyFoodAccepts(item));
	}

	@ParameterizedTest
	@CsvSource({
		"PIG, minecraft:rotten_flesh", "PIG, minecraft:wheat", "SHEEP, minecraft:carrot",
		"ZOMBIE, minecraft:bread", "VILLAGER, minecraft:rotten_flesh", "ZOMBIE, minecraft:pufferfish",
		"VILLAGER, alaindustrial:bread", "COW, minecraft:apple", "DROWNED, minecraft:beef",
		"CREEPER, minecraft:bone", "CHICKEN, minecraft:wheat", "PIGLIN, minecraft:rotten_flesh" })
	void wrongFoodHasNoPortion(MobWheelProfile species, String item) {
		assertEquals(0, MobWheelFeed.portion(species, item));
		assertFalse(MobWheelFeed.accepts(species, item));
	}

	@Test
	void sharedFoodIsAcceptedByBothAndForeignItemsByNobody() {
		assertTrue(MobWheelFeed.accepts(MobWheelProfile.SHEEP, "minecraft:apple"));
		assertTrue(MobWheelFeed.accepts(MobWheelProfile.VILLAGER, "minecraft:apple"));
		assertFalse(MobWheelFeed.anyFoodAccepts("minecraft:stone"));
		assertFalse(MobWheelFeed.anyFoodAccepts("minecraft:tropical_fish"));
		assertFalse(MobWheelFeed.anyFoodAccepts(""));
	}

	@Test
	void everySpeciesEatsSomethingAndItsStapleIsItsFirstFood() {
		for (MobWheelProfile species : MobWheelProfile.values()) {
			assertFalse(MobWheelFeed.foodsOf(species).isEmpty(), species.name());
			String staple = MobWheelFeed.staple(species);
			assertEquals(MobWheelFeed.foodsOf(species).keySet().iterator().next(), staple);
			assertTrue(MobWheelFeed.accepts(species, staple), species + " " + staple);
			MobWheelFeed.foodsOf(species).forEach((item, count) -> assertTrue(count >= 1 && count <= 9, item));
		}
		assertEquals("minecraft:bread", MobWheelFeed.staple(MobWheelProfile.VILLAGER));
		assertEquals("minecraft:carrot", MobWheelFeed.staple(MobWheelProfile.PIG));
		assertEquals("minecraft:rotten_flesh", MobWheelFeed.staple(MobWheelProfile.ZOMBIE));
	}
}
