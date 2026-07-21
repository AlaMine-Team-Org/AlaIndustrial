package dev.alaindustrial.item;

import net.minecraft.world.item.ToolMaterial;

/**
 * Per-tier configuration for one scythe variant (MOD-068) — a loader-neutral record both loaders
 * read when registering their scythe items.
 *
 * <p><b>Why this exists.</b> Before this extract, the {@code (width, depth, maxBlocks)} triple plus
 * the matching {@code (material, attackDamage, fireResistant)} tuple were typed out <b>twice</b> —
 * once in {@code fabric/.../ModItems} and once in {@code neoforge/.../ModItemsNeoForge} — with a
 * literal code comment "{@code Keep these values in sync with the Fabric ModItems#scythe helper}".
 * That is exactly the silent-drift class the project's validators exist to catch: a tweak to one
 * side that forgets the other ships a balance divergence between loaders with no compile error.
 *
 * <p>The canonical tier instances ({@link ScytheTiers#WOOD}, {@link ScytheTiers#STONE}, …) live in
 * the sibling class {@link ScytheTiers} — this record is the shape, that class is the catalogue.
 *
 * @param id            the registry path suffix (e.g. {@code "scythe_wood"}), unique per tier
 * @param material      the vanilla {@link ToolMaterial} (WOOD, STONE, …) — drives durability + mining speed
 * @param attackDamage  the {@code Item.Properties.hoe(material, attackDamage, attackSpeed)} attack bias
 * @param profile       the {@link ScytheItem.Profile} (AOE width/depth + per-use block cap)
 * @param fireResistant true only for the netherite tier (vanilla fire-immunity contract)
 */
public record ScytheTier(String id, ToolMaterial material, float attackDamage,
		ScytheItem.Profile profile, boolean fireResistant) {
}
