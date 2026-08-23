package dev.alaindustrial.item.material;

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
	 * Visual asset key for charged Fluxweave armor (MOD-127) — the gold conductor tracks are lit. The
	 * drained look lives in {@code FluxweaveArmorItem.FLUXWEAVE_OFF_ASSET}; this one is the material's
	 * declared asset, so a piece that never passes through {@code ItemEnergy.set} still renders sanely.
	 */
	public static final ResourceKey<EquipmentAsset> FLUXWEAVE_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("fluxweave"));

	/**
	 * Fluxweave — the mod's first full EU armour set (MOD-127): silver-plated spider silk (or cotton)
	 * woven into cloth. Deliberately <b>not</b> a tank. Defense sits at the vanilla iron line, and what
	 * the player actually buys is the utility layer the charge switches on (breathing, swim and run
	 * speed, a toggleable step assist, softened falls) — see
	 * {@link dev.alaindustrial.item.wearable.FluxweaveArmorItem}.
	 *
	 * <p>Durability is wired by {@code humanoidArmor(...)} and then never spent: the item's EQUIPPABLE
	 * is rewritten with {@code setDamageOnHurt(false)}, so the bar in the inventory shows EU instead and
	 * the suit cannot break. The number below therefore only matters to the anvil.
	 *
	 * <p>Enchantability is high (18, above tempered iron's 12): the cloth is thin and conductive rather
	 * than thick plate, so enchanting is the intended way to make it survivable.
	 *
	 * <p>Repair tag: {@code alaindustrial:fluxweave_armor_materials} → {@code alaindustrial:fluxweave_cloth}.
	 */
	public static final ArmorMaterial FLUXWEAVE = new ArmorMaterial(
			15,                                     // durability factor (never consumed; anvil only)
			makeDefense(2, 6, 5, 2),                // helmet/chest/legs/boots — the iron line
			18,                                     // enchantmentValue (tempered iron: 12)
			SoundEvents.ARMOR_EQUIP_LEATHER,        // cloth, not plate
			1.0f,                                   // toughness
			0.0f,                                   // knockbackResistance (the chestplate adds its own when charged)
			tagKey("fluxweave_armor_materials"),    // repairIngredient
			FLUXWEAVE_ASSET);

	/**
	 * Visual asset key for the shielding suit (MOD-470). Unlike every other set in the mod, its worn
	 * textures are fully opaque in every region the model shows: the suit is sealed, so no pixel of the
	 * player's skin — face, hands, legs, or the skin's own second layer — is visible while the full set
	 * is worn. That works without a custom model because armour inflates further than the skin does
	 * (1.0 and 0.5 against the second layer's 0.25), so an opaque texture simply covers it.
	 */
	public static final ResourceKey<EquipmentAsset> SHIELDING_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("shielding"));

	/**
	 * Shielding suit (MOD-470) — leather sealed with the mod's own rubber, not plate armour.
	 *
	 * <p><b>Cheap on purpose, and weak to match.</b> The first version was built from
	 * {@code shielding_alloy_plate}, which sits at the far end of the palladium chain — so the only
	 * protection against radiation arrived after the reactor that emits it. The recipe is now leather +
	 * rubber + a glass pane for the visor + yellow dye (the suit IS yellow), early-game by design.
	 *
	 * <p>That forced the stats down with it: defense at the leather line, no toughness, low durability.
	 * A cheap suit with iron-grade protection would be strictly better than iron armour, and players
	 * would wear it for the numbers rather than for the job. What the suit sells is the radiation
	 * shielding that {@code RadiationTicker} reads off the {@code #alaindustrial:radiation_shielding}
	 * tag — and the durability it spends absorbing dose, which keeps it a consumable.
	 *
	 * <p><b>It insulates too (MOD-466).</b> The suit is sealed leather and rubber, so it also carries
	 * {@code #alaindustrial:shock_insulating} and stops a bare cable's shock exactly like the rubber
	 * set does. That does not make the insulated set redundant — this one arrives far later and costs a
	 * palladium-era craft — but a player wearing rubber head to toe and still being electrocuted was
	 * the material contradicting itself.
	 *
	 * <p>Repair tag: {@code alaindustrial:shielding_armor_materials} -> {@code alaindustrial:rubber}.
	 */
	public static final ArmorMaterial SHIELDING = new ArmorMaterial(
			15,                                      // durability factor (leather 5, iron 15) — dose eats it
			makeDefense(1, 3, 2, 1),                 // the leather line: this set is not armour
			10,                                      // enchantmentValue (leather 15, iron 9)
			SoundEvents.ARMOR_EQUIP_LEATHER,         // sealed cloth and rubber, not plate
			0.0f,                                    // toughness
			0.0f,
			tagKey("shielding_armor_materials"),
			SHIELDING_ASSET);

	/**
	 * Visual asset key for the insulated set (MOD-466). Ordinary armour layers, not a sealed suit:
	 * unlike {@link #SHIELDING_ASSET} the textures may leave transparent regions, because insulation is
	 * about what the wearer touches rather than about closing them off from the world.
	 */
	public static final ResourceKey<EquipmentAsset> INSULATED_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Industrialization.id("insulated"));

	/**
	 * Insulated set (MOD-466) — leather work gear dipped in slime.
	 *
	 * <p><b>Slime, and deliberately not the mod's own rubber.</b> The first version was rubber, and it
	 * was dominated before it shipped: rubber costs an oil well, a polymeriser and a vulcaniser, and
	 * three of it turns into six insulated cables — so anyone able to craft rubber armour could simply
	 * insulate the wire instead, permanently, for less. Slime breaks that loop because a slime ball
	 * cannot be made into cable insulation at all. It is also available from the first swamp, which is
	 * what finally puts this set in the window it was always meant for: the stretch between a player's
	 * first bare copper line and their first drop of rubber.
	 *
	 * <p>Each worn piece cuts a quarter of a shock ({@code Config.bareCableShockInsulationPerPiecePercent},
	 * read by {@code CableBlock} off the {@code #alaindustrial:shock_insulating} tag), and the set spends
	 * durability for what it stopped — a consumable, not a permanent upgrade. The permanent answer is
	 * still insulated cable; this is what keeps a player alive until they have it.
	 *
	 * <p><b>Defense is leather; durability is not.</b> Protection stays at the vanilla leather line
	 * (1/3/2/1) for the same reason the shielding suit's does — a craft this cheap with real armour
	 * numbers would replace leather armour outright and be worn for the wrong reason. But the two
	 * numbers answer different questions, and the durability one is "how long does the coating last",
	 * not "how much does it stop". At leather's factor 5 the set died in under half a minute of standing
	 * beside a wire, because contact is once a second rather than once a visit. Factor 25 gives
	 * 275/400/375/325 across the slots ({@code ArmorType.unitDurability} 11/16/15/13) — roughly four and
	 * a half minutes of unbroken LV contact out of a helmet, and a whole working session out of the
	 * chestplate. It still cannot tank anything.
	 *
	 * <p>Repair tag: {@code alaindustrial:insulated_armor_materials} -> {@code minecraft:leather}.
	 * Leather rather than the slime that coats it: an anvil repair is patching the gear, and a player
	 * who has worn the coating off has leather to hand long before they have another swamp trip.
	 */
	public static final ArmorMaterial INSULATED = new ArmorMaterial(
			25,                                      // durability factor — how long the coating lasts
			makeDefense(1, 3, 2, 1),                 // leather defense: this set is not armour
			15,                                      // enchantmentValue (leather 15) — cloth takes it well
			SoundEvents.ARMOR_EQUIP_LEATHER,         // coated leather, not plate
			0.0f,                                    // toughness
			0.0f,                                    // knockbackResistance
			tagKey("insulated_armor_materials"),
			INSULATED_ASSET);

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
