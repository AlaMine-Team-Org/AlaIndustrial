package dev.alaindustrial.item;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;
import java.util.Map;

/**
 * Loader-neutral {@link ArmorMaterial} definitions for the mod's armor sets (MC 26.2 equipment
 * system). In 26.2 the old {@code ArmorItem}/{@code ArmorMaterials} enum were removed: an armor
 * piece is now a plain {@link Item} whose equipment properties (durability, defense, enchantability,
 * equip sound, toughness, knockback resistance, repair tag, visual asset) are bundled in an
 * immutable {@link ArmorMaterial} record, then attached to the item via the single helper
 * {@code Item.Properties.humanoidArmor(ArmorMaterial, ArmorType)}. The material is instantiated
 * here, on the common side, so both loaders reference one definition — mirrors {@link ModToolMaterials}.
 *
 * <p>Values are <b>not</b> wired into {@code Config.java} for the same reason as tool materials: an
 * {@code ArmorMaterial} is constructed at item-registration time, which runs <i>before</i>
 * {@code config/alaindustrial.json} is read. Exposing these as runtime-tunable knobs would mislead
 * server admins (they could "rebalance" the JSON with no effect on the actual item). The numbers
 * below are the source of truth and are mirrored in {@code docs/PERFORMANCE.md} for cross-checking.
 * See task MOD-056.
 *
 * <p>Baseline reference (verified via {@code javap} against {@code minecraft-common-deobf-26.2.jar}):
 * vanilla {@code ArmorMaterials.IRON} = {@code (15, makeDefense(2,5,6,2,5), 9,
 * ARMOR_EQUIP_IRON, 0.0f, 0.0f, REPAIRS_IRON_ARMOR, EquipmentAssets.IRON)}. Defense map slots are
 * ordered {@code (helmet, chestplate, leggings, boots, body)}; the {@code body} slot is only used
 * by wolf/animal armor and is omitted here.
 */
public final class ModArmorMaterials {
	private ModArmorMaterials() {
	}

	/**
	 * Visual asset key for tempered-iron armor. A mod-namespaced {@link ResourceKey} into the
	 * {@code minecraft:equipment_asset} registry — the same kind of key vanilla holds in
	 * {@link EquipmentAssets#IRON}. The key is built manually (not via {@link EquipmentAssets#createId},
	 * which hardcodes the {@code minecraft} namespace — see its bytecode: it calls
	 * {@code Identifier.withDefaultNamespace}). The client resolves this key to the worn-armor
	 * definition at {@code assets/alaindustrial/equipment/tempered_iron.json}, whose layers point at
	 * the 64×32/64×64 textures under {@code textures/entity/equipment/{humanoid,humanoid_leggings,
	 * humanoid_baby}/tempered_iron.png}.
	 */
	public static final ResourceKey<EquipmentAsset> TEMPERED_IRON_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("tempered_iron"));

	/**
	 * Tempered iron — a moderate, all-round upgrade over vanilla iron armor: better durability,
	 * a small toughness bonus and higher enchantability, with the same base defense as iron so it
	 * does not devalue the diamond progression. Parallel to the tempered-iron tool line (MOD-054).
	 *
	 * <p>Repair tag: {@code alaindustrial:tempered_iron_armor_materials}
	 * ({@code data/alaindustrial/tags/item/tempered_iron_armor_materials.json} →
	 * {@code alaindustrial:tempered_iron}), the tempered-iron analogue of vanilla
	 * {@code #minecraft:repairs_iron_armor}.
	 *
	 * <p>Visual asset: {@link #TEMPERED_IRON_ASSET} — on the player body the armor renders with the
	 * mod's own 64×32/64×64 worn textures (humanoid + humanoid_leggings + humanoid_baby). Inventory
	 * icons are separate 16×16 item textures.
	 */
	public static final ArmorMaterial TEMPERED_IRON = new ArmorMaterial(
			17,                                       // durability factor (iron: 15, +13%)
			makeDefense(2, 6, 5, 2),                  // defense per slot (helmet/chest/legs/boots) — same as iron
			12,                                       // enchantmentValue (iron: 9, +33%)
			SoundEvents.ARMOR_EQUIP_IRON,             // equipSound (Holder<SoundEvent>)
			1.0f,                                     // toughness (iron: 0.0)
			0.0f,                                     // knockbackResistance
			tagKey("tempered_iron_armor_materials"),  // repairIngredient (TagKey<Item>)
			TEMPERED_IRON_ASSET);                     // assetId — mod-owned worn textures

	/**
	 * Build the per-slot defense map the way vanilla {@code ArmorMaterials.makeDefense(...)} does,
	 * minus the {@code body} slot (only wolf/animal armor uses it). Order: helmet, chestplate,
	 * leggings, boots — matching {@link ArmorType} ordinals.
	 */
	private static Map<ArmorType, Integer> makeDefense(int helmet, int chestplate, int leggings, int boots) {
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		defense.put(ArmorType.HELMET, helmet);
		defense.put(ArmorType.CHESTPLATE, chestplate);
		defense.put(ArmorType.LEGGINGS, leggings);
		defense.put(ArmorType.BOOTS, boots);
		return defense;
	}

	/** Build a mod-namespaced item tag key for an armor-material repair item set. */
	private static TagKey<Item> tagKey(String path) {
		return TagKey.create(Registries.ITEM, Industrialization.id(path));
	}
}
