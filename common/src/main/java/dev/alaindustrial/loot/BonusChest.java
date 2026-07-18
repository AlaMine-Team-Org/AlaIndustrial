package dev.alaindustrial.loot;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Shared constants for the MOD-119 bonus-chest injection. Both loaders reference the same mod-owned
 * loot sub-table {@code alaindustrial:inject/bonus_chest} (backed by
 * {@code common/.../data/alaindustrial/loot_table/inject/bonus_chest.json}) so the item list and its
 * balance live in exactly one place:
 *
 * <ul>
 *   <li><b>Fabric</b> adds a pool holding a single {@code NestedLootTable} reference to this key from
 *       its {@code LootTableEvents.MODIFY} handler (see {@code IndustrializationFabric}).</li>
 *   <li><b>NeoForge</b> points a data-driven {@code neoforge:add_table} Global Loot Modifier at this
 *       key (see {@code data/alaindustrial/loot_modifiers/bonus_chest_inject.json}).</li>
 * </ul>
 *
 * The sub-table itself is a plain item list with no config gate: the {@code Config.bonusChestEnabled}
 * flag is enforced imperatively in the Fabric handler and via the
 * {@link BonusChestEnabledCondition} loot condition in the NeoForge modifier's {@code conditions}.
 */
public final class BonusChest {

	/** The mod's injected bonus-chest sub-table, referenced by both loaders. */
	public static final ResourceKey<LootTable> INJECT_TABLE =
			ResourceKey.create(Registries.LOOT_TABLE, Industrialization.id("inject/bonus_chest"));

	private BonusChest() {
	}
}
