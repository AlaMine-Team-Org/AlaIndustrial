package dev.alaindustrial.item;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Loader-neutral {@link ToolMaterial} records for the mod's hand-held tools (MC 26.2 data-driven
 * tool system). In 26.2 the old {@code PickaxeItem}/{@code Tier}/{@code Tiers} classes were removed:
 * a pickaxe is now a plain {@link Item} whose {@code minecraft:tool} component is attached via
 * {@code Item.Properties.pickaxe(ToolMaterial, float, float)}. The material itself is an immutable
 * record — {@code (incorrectBlocksForDrops, durability, speed, attackDamageBonus, enchantmentValue,
 * repairItems)} — instantiated here, on the common side, so both loaders reference one definition.
 *
 * <p>Values are <b>not</b> wired into {@code Config.java}: a {@code ToolMaterial} is constructed at
 * item-registration time, which runs <i>before</i> {@code config/alaindustrial.json} is read. Exposing
 * these as runtime-tunable knobs would mislead server admins (they could "rebalance" the JSON with
 * no effect on the actual item). The numbers below are the source of truth and are mirrored in
 * {@code docs/PERFORMANCE.md} + the OKF spec for cross-checking. See task MOD-054.
 *
 * <p>Baseline reference (verified via {@code javap} against {@code minecraft-common-deobf-26.2.jar}):
 * vanilla {@code ToolMaterial.IRON} = {@code (INCORRECT_FOR_IRON_TOOL, 250, 6.0f, 2.0f, 14,
 * IRON_TOOL_MATERIALS)}, and {@code Items.IRON_PICKAXE} is registered as
 * {@code pickaxe(ToolMaterial.IRON, 1.0f, -2.8f)}.
 */
public final class ModToolMaterials {
	private ModToolMaterials() {
	}

	/**
	 * Tempered iron — a moderate, all-round upgrade over vanilla iron: better durability, mining
	 * speed, attack and enchantability, but it still cannot mine diamond-level blocks (it reuses
	 * {@link BlockTags#INCORRECT_FOR_IRON_TOOL}, so diamond ore / obsidian / ancient debris stay
	 * out of reach). Does not devalue the diamond progression.
	 *
	 * <p>Repair tag: {@code alaindustrial:tempered_iron_tool_materials}
	 * ({@code data/alaindustrial/tags/item/tempered_iron_tool_materials.json} →
	 * {@code alaindustrial:tempered_iron}), the tempered-iron analogue of vanilla
	 * {@code #minecraft:iron_tool_materials}.
	 */
	public static final ToolMaterial TEMPERED_IRON = new ToolMaterial(
			BlockTags.INCORRECT_FOR_IRON_TOOL, // diamond-level blocks stay unmineable
			350,    // durability (iron: 250, +40%)
			7.0f,   // mining speed (iron: 6.0)
			2.5f,   // attackDamageBonus (iron: 2.0) → final pickaxe damage 4.5 vs iron's 4
			16,     // enchantmentValue (iron: 14)
			tagKey("tempered_iron_tool_materials"));

	/** Build a mod-namespaced item tag key for a tool-material repair item set. */
	private static TagKey<Item> tagKey(String path) {
		return TagKey.create(Registries.ITEM, Industrialization.id(path));
	}
}
