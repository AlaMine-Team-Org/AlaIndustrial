package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's pool in vanilla structure chests (MOD-942): the abandoned mineshaft, the monster-spawner
 * dungeon and the village toolsmith. Each vanilla table is rolled exactly as a chest rolls it — through
 * the server's loot registry, so the Fabric {@code LootTableEvents.MODIFY} pool and the NeoForge
 * {@code add_table} modifier are both in the result — for fixed seeds, once with
 * {@code structureLootEnabled} on and once with it off.
 *
 * <p>Per table it checks four things: some seed gives a mod item; every mod item is one the owner put in
 * that table (nothing atomic, nothing that skips a tier); with the switch off no seed gives one (the
 * negative control — the same body fails if the switch stops working); and with the switch off the
 * vanilla part of every roll is exactly the vanilla part with it on, item by item and in order — the
 * injection only ever adds after vanilla's pools.
 */
public final class StructureLootScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(StructureLootScenarios::mineshaftChestGetsModLoot,
						"structure_loot_mineshaft_chest_gets_mod_loot"),
				RosterEntry.of(StructureLootScenarios::dungeonChestGetsModLoot,
						"structure_loot_dungeon_chest_gets_mod_loot"),
				RosterEntry.of(StructureLootScenarios::villageToolsmithChestGetsModLoot,
						"structure_loot_village_toolsmith_chest_gets_mod_loot"));

		private Roster() {}
	}

	/** Seeds rolled per table; with about 80 % of rolls non-empty a miss on all of them is not a fluke. */
	private static final int SEEDS = 200;

	private static final Set<String> MINESHAFT_ITEMS = Set.of("raw_tin", "raw_sulfur", "raw_nickel", "raw_silver",
			"coal_dust", "tin_ingot", "bronze_ingot", "raw_rubber", "kok_sagyz_seeds", "canned_ration", "copper_coil",
			"electronic_circuit");

	private static final Set<String> DUNGEON_ITEMS = Set.of("raw_tin", "raw_sulfur", "bronze_ingot", "raw_rubber",
			"rubber", "kok_sagyz_seeds", "cotton_seeds", "canned_ration", "chewing_gum", "iodine_tablets",
			"copper_coil", "electronic_circuit");

	private static final Set<String> TOOLSMITH_ITEMS = Set.of("tin_ingot", "bronze_ingot", "tin_plate", "iron_plate",
			"bronze_plate");

	private StructureLootScenarios() {}

	/** @implements MOD-942-MINESHAFT — the mineshaft chest gets the mod pool, gated, vanilla part kept. */
	private static void mineshaftChestGetsModLoot(GameTestHelper helper) {
		checkTable(helper, BuiltInLootTables.ABANDONED_MINESHAFT, MINESHAFT_ITEMS);
	}

	/** @implements MOD-942-DUNGEON — the dungeon chest gets the mod pool, gated, vanilla part kept. */
	private static void dungeonChestGetsModLoot(GameTestHelper helper) {
		checkTable(helper, BuiltInLootTables.SIMPLE_DUNGEON, DUNGEON_ITEMS);
	}

	/** @implements MOD-942-TOOLSMITH — the toolsmith chest gets the mod pool, gated, vanilla part kept. */
	private static void villageToolsmithChestGetsModLoot(GameTestHelper helper) {
		checkTable(helper, BuiltInLootTables.VILLAGE_TOOLSMITH, TOOLSMITH_ITEMS);
	}

	private static void checkTable(GameTestHelper helper, ResourceKey<LootTable> key, Set<String> allowed) {
		ServerLevel level = helper.getLevel();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO));
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, origin)
				.create(LootContextParamSets.CHEST);
		String name = key.identifier().toString();

		List<List<ItemStack>> enabled = new ArrayList<>();
		List<List<ItemStack>> disabled = new ArrayList<>();
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("structureLootEnabled", true);
			for (int seed = 0; seed < SEEDS; seed++) {
				enabled.add(table.getRandomItems(params, RandomSource.create(seed)));
			}
			o.set("structureLootEnabled", false);
			for (int seed = 0; seed < SEEDS; seed++) {
				disabled.add(table.getRandomItems(params, RandomSource.create(seed)));
			}
		}

		int seedsWithModItem = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			List<ItemStack> on = enabled.get(seed);
			List<ItemStack> off = disabled.get(seed);
			boolean found = false;
			List<ItemStack> vanillaPart = new ArrayList<>();
			for (ItemStack stack : on) {
				Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (!id.getNamespace().equals(Industrialization.MOD_ID)) {
					vanillaPart.add(stack);
					continue;
				}
				if (!allowed.contains(id.getPath())) {
					helper.fail("MOD-942 " + name + " seed " + seed + " gave " + id
							+ ", which is not in this table's approved list " + allowed);
					return;
				}
				found = true;
			}
			if (found) {
				seedsWithModItem++;
			}
			for (ItemStack stack : off) {
				Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (id.getNamespace().equals(Industrialization.MOD_ID)) {
					helper.fail("MOD-942 " + name + " seed " + seed + " gave " + id
							+ " with structureLootEnabled=false — the switch does not gate the pool");
					return;
				}
			}
			if (!sameStacks(vanillaPart, off)) {
				helper.fail("MOD-942 " + name + " seed " + seed + ": the vanilla part changed when the mod pool was"
						+ " switched on — " + off + " without it, " + vanillaPart + " with it");
				return;
			}
		}
		if (seedsWithModItem == 0) {
			helper.fail("MOD-942 " + name + ": no mod item in " + SEEDS + " seeds — the pool is not injected");
			return;
		}
		helper.succeed();
	}

	private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.matches(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}
}
