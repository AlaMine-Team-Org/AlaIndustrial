package dev.alaindustrial.core.environment;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each species in a mob wheel eats and how many items make one portion (MOD-763, decisions D5 and D8).
 *
 * <p>Every full portion does the same thing — an exhausted mob is full again — so a common food simply takes
 * more items than a rare one: four rotten flesh or one cooked steak for a zombie. Keyed by item id string so
 * the table stays Minecraft-free and testable on the L1 lane; the drive's feeder slot asks
 * {@link #anyFoodAccepts} before taking an item and {@link #portion} before spending one. Each species' first
 * food is its {@link #staple}, the item the empty feeder hints at. Not shown to players anywhere as a list (D7).
 */
public final class MobWheelFeed {
	private MobWheelFeed() {
	}

	private static final List<String> RAW_MEAT_AND_FISH = List.of("minecraft:beef", "minecraft:porkchop",
			"minecraft:chicken", "minecraft:mutton", "minecraft:rabbit", "minecraft:cod", "minecraft:salmon");
	private static final List<String> COOKED_MEAT_AND_FISH = List.of("minecraft:cooked_beef",
			"minecraft:cooked_porkchop", "minecraft:cooked_chicken", "minecraft:cooked_mutton",
			"minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon");

	private static final Map<MobWheelProfile, Map<String, Integer>> PORTIONS = build();

	private static Map<MobWheelProfile, Map<String, Integer>> build() {
		Map<MobWheelProfile, Map<String, Integer>> table = new EnumMap<>(MobWheelProfile.class);
		table.put(MobWheelProfile.CHICKEN, foods("wheat_seeds", 4, "pumpkin_seeds", 4, "melon_seeds", 4,
				"beetroot_seeds", 4, "torchflower_seeds", 4, "pitcher_pod", 4));
		table.put(MobWheelProfile.PIG, foods("carrot", 3, "potato", 3, "beetroot", 4, "golden_carrot", 1));
		table.put(MobWheelProfile.SHEEP, foods("wheat", 3, "hay_block", 1, "apple", 2, "sweet_berries", 6));
		table.put(MobWheelProfile.COW, foods("wheat", 3, "hay_block", 1));
		table.put(MobWheelProfile.GOAT, foods("wheat", 3, "hay_block", 1));
		table.put(MobWheelProfile.VILLAGER, foods("bread", 1, "carrot", 3, "potato", 3, "beetroot", 4,
				"baked_potato", 1, "apple", 2, "cookie", 4, "pumpkin_pie", 1));
		Map<String, Integer> zombie = foods("rotten_flesh", 4);
		RAW_MEAT_AND_FISH.forEach(id -> zombie.put(id, 2));
		COOKED_MEAT_AND_FISH.forEach(id -> zombie.put(id, 1));
		table.put(MobWheelProfile.ZOMBIE, zombie);
		table.put(MobWheelProfile.HUSK, zombie);
		table.put(MobWheelProfile.DROWNED, foods("cod", 2, "salmon", 2, "rotten_flesh", 4, "dried_kelp", 3));
		Map<String, Integer> bones = foods("bone", 3, "bone_block", 1);
		table.put(MobWheelProfile.SKELETON, bones);
		table.put(MobWheelProfile.STRAY, bones);
		table.put(MobWheelProfile.BOGGED, bones);
		table.put(MobWheelProfile.PARCHED, bones);
		table.put(MobWheelProfile.WITCH, foods("sugar", 4, "glowstone_dust", 2, "redstone", 4, "spider_eye", 2));
		Map<String, Integer> illager = foods("bread", 2, "baked_potato", 2, "emerald", 1);
		table.put(MobWheelProfile.VINDICATOR, illager);
		table.put(MobWheelProfile.PILLAGER, illager);
		table.put(MobWheelProfile.EVOKER, illager);
		table.put(MobWheelProfile.PIGLIN, foods("gold_nugget", 6, "gold_ingot", 1));
		table.put(MobWheelProfile.PIGLIN_BRUTE, foods("gold_ingot", 1, "gold_nugget", 9));
		table.put(MobWheelProfile.ZOMBIFIED_PIGLIN, foods("rotten_flesh", 4, "gold_nugget", 6));
		table.put(MobWheelProfile.CREEPER, foods("gunpowder", 4, "fire_charge", 2, "tnt", 1));
		table.replaceAll((species, foods) -> Collections.unmodifiableMap(foods));
		return table;
	}

	/** An ordered food table from {@code "path", count} pairs of vanilla items. */
	private static Map<String, Integer> foods(Object... pathsAndCounts) {
		Map<String, Integer> foods = new LinkedHashMap<>();
		for (int i = 0; i < pathsAndCounts.length; i += 2) {
			foods.put("minecraft:" + pathsAndCounts[i], (Integer) pathsAndCounts[i + 1]);
		}
		return foods;
	}

	/** Items of {@code itemId} that make one portion for {@code species}; 0 when it does not eat that item. */
	public static int portion(MobWheelProfile species, String itemId) {
		Integer count = PORTIONS.get(species).get(itemId);
		return count == null ? 0 : count;
	}

	/** Whether {@code species} eats {@code itemId} at all. */
	public static boolean accepts(MobWheelProfile species, String itemId) {
		return portion(species, itemId) > 0;
	}

	/** Every food of {@code species} with its portion, staple first. */
	public static Map<String, Integer> foodsOf(MobWheelProfile species) {
		return PORTIONS.get(species);
	}

	/** The species' first food — what the empty feeder hints at while it is in the wheel. */
	public static String staple(MobWheelProfile species) {
		return PORTIONS.get(species).keySet().iterator().next();
	}

	/** Whether any species eats {@code itemId} — the feeder slot's filter, which takes food before a mob is in. */
	public static boolean anyFoodAccepts(String itemId) {
		for (MobWheelProfile species : MobWheelProfile.values()) {
			if (accepts(species, itemId)) {
				return true;
			}
		}
		return false;
	}
}
