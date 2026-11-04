package dev.alaindustrial.loot;

import dev.alaindustrial.Industrialization;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Shared constants for the MOD-942 structure-chest injection: a pool of early-game mod items in three
 * vanilla chest tables. Same shape as {@link DrillTemplateLoot} — one mod-owned sub-table per structure
 * (backed by {@code common/.../data/alaindustrial/loot_table/inject/<name>.json}), so the weights live
 * in exactly one file each:
 *
 * <ul>
 *   <li><b>Fabric</b> adds a pool holding a single {@code NestedLootTable} reference to the sub-table,
 *       from its {@code LootTableEvents.MODIFY} handler.</li>
 *   <li><b>NeoForge</b> points three data-driven {@code neoforge:add_table} Global Loot Modifiers at the
 *       same keys (see {@code neoforge/.../data/alaindustrial/loot_modifiers/structure_loot_*.json}).</li>
 * </ul>
 *
 * <p>Two differences from the earlier injections, both on purpose:
 * <ul>
 *   <li><b>A table replaced by a datapack or another mod is extended too.</b> The Fabric handler does not
 *       ask {@code source.isBuiltin()} for these keys, so Fabric behaves like a NeoForge modifier, which
 *       applies whatever the table's source. Structure mods commonly rewrite these three tables.</li>
 *   <li><b>The config switch is a loot condition inside the sub-table</b>
 *       ({@link StructureLootEnabledCondition}), not a check at data load: it is read when a chest is
 *       rolled, identically on both loaders.</li>
 * </ul>
 *
 * <p>The injection only ever adds a pool after vanilla's: no container is read or written here, so the
 * pending-loot rule (ADR-010) is not touched.
 */
public final class StructureLoot {

	/** Mineshaft corridors and minecart chests ({@code minecraft:chests/abandoned_mineshaft}). */
	public static final ResourceKey<LootTable> MINESHAFT = inject("inject/mineshaft");

	/** Monster-spawner rooms ({@code minecraft:chests/simple_dungeon}). */
	public static final ResourceKey<LootTable> DUNGEON = inject("inject/dungeon");

	/** The village toolsmith's chest ({@code minecraft:chests/village/village_toolsmith}). */
	public static final ResourceKey<LootTable> VILLAGE_TOOLSMITH = inject("inject/village_toolsmith");

	/** Vanilla chest table → the mod sub-table it receives. */
	public static final Map<ResourceKey<LootTable>, ResourceKey<LootTable>> INJECTIONS = Map.of(
			BuiltInLootTables.ABANDONED_MINESHAFT, MINESHAFT,
			BuiltInLootTables.SIMPLE_DUNGEON, DUNGEON,
			BuiltInLootTables.VILLAGE_TOOLSMITH, VILLAGE_TOOLSMITH);

	private static ResourceKey<LootTable> inject(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Industrialization.id(path));
	}

	private StructureLoot() {
	}
}
