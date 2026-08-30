package dev.alaindustrial.loot;

import dev.alaindustrial.Industrialization;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Shared constants for the MOD-534 bastion injection — the only way to obtain the
 * Netherite Drill Upgrade smithing template, and therefore the gate on the drill's top tier.
 *
 * <p>Both loaders reference the same mod-owned sub-table
 * {@code alaindustrial:inject/bastion_drill_template} (backed by
 * {@code common/.../data/alaindustrial/loot_table/inject/bastion_drill_template.json}), so the drop
 * chance lives in exactly one file rather than being written out five times:
 *
 * <ul>
 *   <li><b>Fabric</b> adds a pool holding a single {@code NestedLootTable} reference to this key for
 *       each table in {@link #BASTION_TABLES}, from its {@code LootTableEvents.MODIFY} handler.</li>
 *   <li><b>NeoForge</b> points four data-driven {@code neoforge:add_table} Global Loot Modifiers at
 *       the same key (see {@code neoforge/.../data/alaindustrial/loot_modifiers/bastion_*.json}).</li>
 * </ul>
 *
 * <p>The four tables are the ones vanilla itself puts {@code netherite_upgrade_smithing_template} in,
 * so the template is found where a player already looks for the vanilla one. Unlike the bonus chest
 * (MOD-119) this injection has <b>no config gate</b>: the bonus chest only ever duplicated items
 * obtainable elsewhere, whereas this template has no other source, and disabling it would leave the
 * drill's third tier uncraftable with nothing to say why.
 */
public final class DrillTemplateLoot {

	/** The mod's injected bastion sub-table, referenced by both loaders. */
	public static final ResourceKey<LootTable> INJECT_TABLE =
			ResourceKey.create(Registries.LOOT_TABLE, Industrialization.id("inject/bastion_drill_template"));

	/** The four bastion chest tables that receive the template — vanilla's own list for its template. */
	public static final Set<ResourceKey<LootTable>> BASTION_TABLES = Set.of(
			BuiltInLootTables.BASTION_TREASURE,
			BuiltInLootTables.BASTION_OTHER,
			BuiltInLootTables.BASTION_BRIDGE,
			BuiltInLootTables.BASTION_HOGLIN_STABLE);

	private DrillTemplateLoot() {
	}
}
