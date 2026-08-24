package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.ElectricChainsawDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricChainsawItem;
import dev.alaindustrial.item.tool.ElectricDrillDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import dev.alaindustrial.item.tool.ElectricHoeDiamondTipItem;
import dev.alaindustrial.item.tool.neoforge.ElectricHoeDiamondTipItemNeoForge;
import dev.alaindustrial.item.tool.neoforge.ElectricHoeItemNeoForge;
import dev.alaindustrial.item.tool.ElectricShovelDiamondTipItem;
import dev.alaindustrial.item.tool.neoforge.ElectricShovelDiamondTipItemNeoForge;
import dev.alaindustrial.item.tool.ElectricHoeItem;
import dev.alaindustrial.item.tool.ElectricSaberItem;
import dev.alaindustrial.item.tool.ElectricShovelItem;
import dev.alaindustrial.item.tool.neoforge.ElectricShovelItemNeoForge;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.item.wearable.FluxweaveArmorItem;
import dev.alaindustrial.item.fluid.FluidTankBlockItem;
import dev.alaindustrial.item.tool.ScytheTier;
import dev.alaindustrial.item.tool.ScytheTiers;
import dev.alaindustrial.item.material.ModToolMaterials;
import dev.alaindustrial.item.material.TemperedIronToolStats;
import dev.alaindustrial.item.tool.NetworkAnalyzerItem;
import dev.alaindustrial.item.teleport.TeleporterRemoteItem;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.tool.ScytheItem;
import dev.alaindustrial.item.tool.neoforge.HammerItemNeoForge;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge item registration (MOD-022 registration-facade). Mirrors the Fabric
 * {@code dev.alaindustrial.registry.ModItems} set 1:1 (same ids): the crafting components (plain
 * {@link Item}s), the Network Analyzer, and a {@link BlockItem} for every registered block — the real
 * content, not stubs.
 *
 * <p><b>Geothermal generator and pump block items (MOD-028).</b> Their blocks now live in {@code common}
 * (see {@code ModBlocksNeoForge}), so both get a {@code BlockItem} here like every other machine.
 *
 * <p><b>Split constraint (verified 26.2 API):</b> the {@code DeferredRegister} object and its
 * {@code register(modBus)} call must live on the {@code neoforge} side. NeoForge 26.2 applies
 * {@code Item.Properties.setId} automatically: {@code registerItem(name, Function&lt;Properties, I&gt;,
 * Supplier&lt;Properties&gt;)} and {@code registerSimpleBlockItem(name, Supplier&lt;Block&gt;)} both
 * derive the id from the deferred key (verified neoforge-26.2.0.67). {@code registerSimpleBlockItem}
 * also applies {@code useBlockDescriptionPrefix()}, matching the Fabric {@code blockItem(...)} helper.
 */
public final class ModItemsNeoForge {
	public static final DeferredRegister.Items ITEMS =
			DeferredRegister.createItems(Industrialization.MOD_ID);

	// --- Crafting components (plain items) ---
	public static final DeferredItem<Item> ELECTRONIC_CIRCUIT = manifestItem("electronic_circuit");
	// MOD-299 — the MV circuit: electronic circuit + gold plates + rubber. Gates the advanced casing.
	public static final DeferredItem<Item> ADVANCED_CIRCUIT = manifestItem("advanced_circuit");
	// MOD-275 — two-state stack size and tooltip, so it needs its own class.
	public static final DeferredItem<Item> ASSEMBLY_BLUEPRINT = ITEMS.registerItem("assembly_blueprint",
			props -> new AssemblyBlueprintItem(props.stacksTo(AssemblyBlueprintItem.BLANK_STACK_SIZE)));
	// Copper Coil — crafting component (copper cable + tin), gates the Electric Drill.
	public static final DeferredItem<Item> COPPER_COIL = manifestItem("copper_coil");
	// Resonance chain (MOD-116): spatial stock -> the coil above the copper one -> the station's chip.
	public static final DeferredItem<Item> SPATIAL_CRYSTAL = manifestItem("spatial_crystal");
	public static final DeferredItem<Item> RESONANCE_COIL = manifestItem("resonance_coil");
	public static final DeferredItem<Item> RTP_CHIP = manifestItem("rtp_chip");
	public static final DeferredItem<Item> ALIGNMENT_CHIP_DAY = manifestItem("alignment_chip_day");
	public static final DeferredItem<Item> ALIGNMENT_CHIP_NIGHT = manifestItem("alignment_chip_night");
	// Upgrade chips (MOD-080): empty blank + the mute upgrade. Each shows a gray hint line.
	public static final DeferredItem<Item> EMPTY_CHIP =
			manifestItem("empty_chip");
	public static final DeferredItem<Item> MUTE_CHIP =
			manifestItem("mute_chip");
	/** MOD-125: fitted to a machine's upgrade panel, it is what makes that machine keep statistics. */
	public static final DeferredItem<Item> STATS_CHIP =
			manifestItem("stats_chip");
	/** Soul Vessel (MOD-278): the Mob Repeller upgrade currency. */
	public static final DeferredItem<Item> SOUL_VESSEL =
			manifestItem("soul_vessel");
	/** Overclocker chips (MOD-392/393): three tiers trading energy for machine speed. */
	public static final DeferredItem<Item> OVERCLOCKER_CHIP_I = manifestItem("overclocker_chip_i");
	public static final DeferredItem<Item> OVERCLOCKER_CHIP_II = manifestItem("overclocker_chip_ii");
	public static final DeferredItem<Item> OVERCLOCKER_CHIP_III = manifestItem("overclocker_chip_iii");
	/** Energy clots (MOD-393): surplus grid power packed into an item by the energy condenser. */
	public static final DeferredItem<Item> ENERGY_CLOT_I = manifestItem("energy_clot_i");
	public static final DeferredItem<Item> ENERGY_CLOT_II = manifestItem("energy_clot_ii");
	public static final DeferredItem<Item> ENERGY_CLOT_III = manifestItem("energy_clot_iii");
	/** Cable breaker (MOD-276): clamps onto a laid cable to cut the line for maintenance. */
	public static final DeferredItem<Item> CABLE_BREAKER =
			manifestItem("cable_breaker");

	// Incubator (MOD-118): mode chips, by-products and the tier-1 evolution materials.
	public static final DeferredItem<Item> MUTATION_CHIP_TRANSFORM =
			manifestItem("mutation_chip_transform");
	public static final DeferredItem<Item> MUTATION_CHIP_DUPLICATE =
			manifestItem("mutation_chip_duplicate");
	public static final DeferredItem<Item> MUTATION_CHIP_CREATE =
			manifestItem("mutation_chip_create");
	public static final DeferredItem<Item> DEPLETED_URANIUM =
			manifestItem("depleted_uranium");
	public static final DeferredItem<Item> IRRADIATED_SLAG =
			manifestItem("irradiated_slag");
	public static final DeferredItem<Item> IRRADIATED_DIAMOND =
			manifestItem("irradiated_diamond");
	public static final DeferredItem<Item> RESONANT_SHARD =
			manifestItem("resonant_shard");
	public static final DeferredItem<Item> MUTAGEN_DUST =
			manifestItem("mutagen_dust");
	// Oil → rubber chain: the polymerizer's product and the vulcanizer's cured output.
	public static final DeferredItem<Item> RAW_RUBBER =
			manifestItem("raw_rubber");
	public static final DeferredItem<Item> RUBBER =
			manifestItem("rubber");
	// Cotton (MOD-280): the seed is planted onto a trellis by right-click (the block handles it, so this
	// stays a plain Item — no BlockItem/ItemNameBlockItem), the fibre is the harvest.
	public static final DeferredItem<Item> COTTON_SEEDS =
			manifestItem("cotton_seeds");
	public static final DeferredItem<Item> COTTON_FIBER =
			manifestItem("cotton_fiber");
	// Fluxweave chain (MOD-127): silver-plated fibre, then the woven sheet. Both are plain crafting
	// components — the EU buffer lives on the armor, not on the material.
	public static final DeferredItem<Item> FLUX_THREAD =
			manifestItem("flux_thread");
	public static final DeferredItem<Item> FLUXWEAVE_CLOTH =
			manifestItem("fluxweave_cloth");
	public static final DeferredItem<Item> UNSTABLE_ISOTOPE =
			manifestItem("unstable_isotope");
	// Rotor / wheel (MOD-189): durability components — wear shows as a vanilla durability bar and, being
	// damageable, they are automatically non-stackable. maxDamage from Config (registration-time).
	public static final DeferredItem<Item> WINDMILL_ROTOR =
			manifestItem("windmill_rotor");
	public static final DeferredItem<Item> WATER_MILL_WHEEL =
			manifestItem("water_mill_wheel");
	// MOD-385: upper grades — richer craft, higher output, longer life. See core.machine.ComponentTier.
	public static final DeferredItem<Item> WINDMILL_ROTOR_REINFORCED =
			manifestItem("windmill_rotor_reinforced");
	public static final DeferredItem<Item> WINDMILL_ROTOR_ADVANCED =
			manifestItem("windmill_rotor_advanced");
	// MOD-386: the lightning rod's conductor tips.
	public static final DeferredItem<Item> LIGHTNING_ROD_CONDUCTOR_TIP =
			manifestItem("lightning_rod_conductor_tip");
	public static final DeferredItem<Item> LIGHTNING_ROD_CONDUCTOR_TIP_REINFORCED =
			manifestItem("lightning_rod_conductor_tip_reinforced");
	public static final DeferredItem<Item> LIGHTNING_ROD_CONDUCTOR_TIP_ADVANCED =
			manifestItem("lightning_rod_conductor_tip_advanced");
	public static final DeferredItem<Item> WATER_MILL_WHEEL_REINFORCED =
			manifestItem("water_mill_wheel_reinforced");
	public static final DeferredItem<Item> WATER_MILL_WHEEL_ADVANCED =
			manifestItem("water_mill_wheel_advanced");
	public static final DeferredItem<Item> WOODEN_GEAR = manifestItem("wooden_gear");
	// Metal gears (MOD-105): crafting components for machinery still to come.
	public static final DeferredItem<Item> STONE_GEAR = manifestItem("stone_gear");
	public static final DeferredItem<Item> IRON_GEAR = manifestItem("iron_gear");
	public static final DeferredItem<Item> GOLD_GEAR = manifestItem("gold_gear");
	public static final DeferredItem<Item> SILVER_GEAR = manifestItem("silver_gear");
	public static final DeferredItem<Item> TEMPERED_IRON = manifestItem("tempered_iron");
	// Tempered-iron pickaxe — first mod tool (MOD-054). MC 26.2 has no PickaxeItem class: a pickaxe is
	// a plain Item whose `minecraft:tool` component is attached via Item.Properties.pickaxe(...). The
	// third arg is a Properties-unary-op that applies the tempered-iron material (durability/speed/
	// damage/enchant). setId is applied automatically by NeoForge, matching the Fabric helper.
	public static final DeferredItem<Item> TEMPERED_IRON_PICKAXE =
			ITEMS.registerItem("tempered_iron_pickaxe", Item::new,
					p -> p.pickaxe(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.PICKAXE.attackDamage(), TemperedIronToolStats.PICKAXE.attackSpeed()));
	// Tempered-iron tool line (MOD-054): axe/hoe/shovel/sword. Pickaxe/sword are plain Item (their
	// 26.2 subclasses were removed). Axe/Hoe/Shovel extend their vanilla subclasses so useOn works
	// (log stripping / dirt tilling / grass path) — those ctors call props.{axe,hoe,shovel}() and
	// super() themselves, so the Properties supplier stays default (NeoForge applies setId). Args
	// mirror vanilla iron equivalents (javap-verified).
	public static final DeferredItem<Item> TEMPERED_IRON_AXE =
			ITEMS.registerItem("tempered_iron_axe",
					p -> new net.minecraft.world.item.AxeItem(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.AXE.attackDamage(), TemperedIronToolStats.AXE.attackSpeed(), p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_HOE =
			ITEMS.registerItem("tempered_iron_hoe",
					p -> new net.minecraft.world.item.HoeItem(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.HOE.attackDamage(), TemperedIronToolStats.HOE.attackSpeed(), p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_SHOVEL =
			ITEMS.registerItem("tempered_iron_shovel",
					p -> new net.minecraft.world.item.ShovelItem(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.SHOVEL.attackDamage(), TemperedIronToolStats.SHOVEL.attackSpeed(), p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_SWORD =
			ITEMS.registerItem("tempered_iron_sword", Item::new,
					p -> p.sword(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.SWORD.attackDamage(), TemperedIronToolStats.SWORD.attackSpeed()));
	// Tempered-iron armor line (MOD-056): helmet/chestplate/leggings/boots. MC 26.2 has no ArmorItem
	// class — each piece is a plain Item whose equipment properties are attached via the single
	// Item.Properties.humanoidArmor(ArmorMaterial, ArmorType) helper (javap-verified against the
	// 26.2 jar; it is how vanilla Items.IRON_HELMET is built). setId is applied automatically by
	// NeoForge, matching the Fabric helper.
	public static final DeferredItem<Item> TEMPERED_IRON_HELMET =
			ITEMS.registerItem("tempered_iron_helmet", Item::new, ItemBuildersNeoForge.temperedArmor(ArmorType.HELMET));
	public static final DeferredItem<Item> TEMPERED_IRON_CHESTPLATE =
			ITEMS.registerItem("tempered_iron_chestplate", Item::new, ItemBuildersNeoForge.temperedArmor(ArmorType.CHESTPLATE));
	public static final DeferredItem<Item> TEMPERED_IRON_LEGGINGS =
			ITEMS.registerItem("tempered_iron_leggings", Item::new, ItemBuildersNeoForge.temperedArmor(ArmorType.LEGGINGS));
	public static final DeferredItem<Item> TEMPERED_IRON_BOOTS =
			ITEMS.registerItem("tempered_iron_boots", Item::new, ItemBuildersNeoForge.temperedArmor(ArmorType.BOOTS));
	// Fluxweave armour (MOD-127): concrete type is FluxweaveArmorItem so it carries its ArmorType.
	public static final DeferredItem<Item> FLUXWEAVE_HELMET =
			ITEMS.registerItem("fluxweave_helmet", p -> new FluxweaveArmorItem(p, ArmorType.HELMET),
					ItemBuildersNeoForge.fluxweaveArmor(ArmorType.HELMET));
	public static final DeferredItem<Item> FLUXWEAVE_CHESTPLATE =
			ITEMS.registerItem("fluxweave_chestplate", p -> new FluxweaveArmorItem(p, ArmorType.CHESTPLATE),
					ItemBuildersNeoForge.fluxweaveArmor(ArmorType.CHESTPLATE));
	public static final DeferredItem<Item> FLUXWEAVE_LEGGINGS =
			ITEMS.registerItem("fluxweave_leggings", p -> new FluxweaveArmorItem(p, ArmorType.LEGGINGS),
					ItemBuildersNeoForge.fluxweaveArmor(ArmorType.LEGGINGS));
	public static final DeferredItem<Item> FLUXWEAVE_BOOTS =
			ITEMS.registerItem("fluxweave_boots", p -> new FluxweaveArmorItem(p, ArmorType.BOOTS),
					ItemBuildersNeoForge.fluxweaveArmor(ArmorType.BOOTS));
	// Shielding suit (MOD-470): ordinary armour items; the shielding lives in the item tag.
	public static final DeferredItem<Item> SHIELDING_HELMET =
			ITEMS.registerItem("shielding_helmet", Item::new, ItemBuildersNeoForge.shieldingArmor(ArmorType.HELMET));
	public static final DeferredItem<Item> SHIELDING_CHESTPLATE =
			ITEMS.registerItem("shielding_chestplate", Item::new, ItemBuildersNeoForge.shieldingArmor(ArmorType.CHESTPLATE));
	public static final DeferredItem<Item> SHIELDING_LEGGINGS =
			ITEMS.registerItem("shielding_leggings", Item::new, ItemBuildersNeoForge.shieldingArmor(ArmorType.LEGGINGS));
	public static final DeferredItem<Item> SHIELDING_BOOTS =
			ITEMS.registerItem("shielding_boots", Item::new, ItemBuildersNeoForge.shieldingArmor(ArmorType.BOOTS));
	// Insulated set (MOD-466): ordinary armour items; the insulation lives in the item tag.
	public static final DeferredItem<Item> INSULATED_HELMET =
			ITEMS.registerItem("insulated_helmet", Item::new, ItemBuildersNeoForge.insulatedArmor(ArmorType.HELMET));
	public static final DeferredItem<Item> INSULATED_CHESTPLATE =
			ITEMS.registerItem("insulated_chestplate", Item::new, ItemBuildersNeoForge.insulatedArmor(ArmorType.CHESTPLATE));
	public static final DeferredItem<Item> INSULATED_LEGGINGS =
			ITEMS.registerItem("insulated_leggings", Item::new, ItemBuildersNeoForge.insulatedArmor(ArmorType.LEGGINGS));
	public static final DeferredItem<Item> INSULATED_BOOTS =
			ITEMS.registerItem("insulated_boots", Item::new, ItemBuildersNeoForge.insulatedArmor(ArmorType.BOOTS));
	public static final DeferredItem<Item> IRON_DUST = manifestItem("iron_dust");
	public static final DeferredItem<Item> COPPER_DUST = manifestItem("copper_dust");
	public static final DeferredItem<Item> GOLD_DUST = manifestItem("gold_dust");
	public static final DeferredItem<Item> COAL_DUST = manifestItem("coal_dust");
	public static final DeferredItem<Item> DIAMOND_DUST = manifestItem("diamond_dust");
	public static final DeferredItem<Item> EMERALD_DUST = manifestItem("emerald_dust");
	public static final DeferredItem<Item> EMPTY_CAN = manifestItem("empty_can");
	public static final DeferredItem<Item> CANNED_RATION = manifestItem("canned_ration");
	public static final DeferredItem<Item> LAPIS_DUST = manifestItem("lapis_dust");
	public static final DeferredItem<Item> TIN_DUST = manifestItem("tin_dust");
	public static final DeferredItem<Item> RAW_TIN = manifestItem("raw_tin");
	public static final DeferredItem<Item> TIN_INGOT = manifestItem("tin_ingot");
	public static final DeferredItem<Item> SILVER_DUST = manifestItem("silver_dust");
	public static final DeferredItem<Item> RAW_SILVER = manifestItem("raw_silver");
	public static final DeferredItem<Item> SILVER_INGOT = manifestItem("silver_ingot");
	public static final DeferredItem<Item> NICKEL_DUST = manifestItem("nickel_dust");
	public static final DeferredItem<Item> RAW_NICKEL = manifestItem("raw_nickel");
	public static final DeferredItem<Item> NICKEL_INGOT = manifestItem("nickel_ingot");
	// MOD-064 alloys.
	public static final DeferredItem<Item> BRONZE_INGOT = manifestItem("bronze_ingot");
	public static final DeferredItem<Item> INVAR_INGOT = manifestItem("invar_ingot");
	public static final DeferredItem<Item> CUPRONICKEL_INGOT = manifestItem("cupronickel_ingot");
	public static final DeferredItem<Item> ELECTRUM_INGOT = manifestItem("electrum_ingot");
	public static final DeferredItem<Item> SULFUR_DUST = manifestItem("sulfur_dust");
	public static final DeferredItem<Item> RAW_SULFUR = manifestItem("raw_sulfur");
	public static final DeferredItem<Item> URANIUM_DUST = manifestItem("uranium_dust");
	public static final DeferredItem<Item> RAW_URANIUM = manifestItem("raw_uranium");
	public static final DeferredItem<Item> URANIUM_INGOT = manifestItem("uranium_ingot");
	// MOD-424: the centrifuge's product, and what smelting it yields.
	public static final DeferredItem<Item> URANIUM_SHAVINGS = manifestItem("uranium_shavings");
	public static final DeferredItem<Item> REFINED_URANIUM = manifestItem("refined_uranium");

	// MOD-468, stage 1 — the shielding chain and the controller's parts.
	public static final DeferredItem<Item> SHIELDING_ALLOY_INGOT = manifestItem("shielding_alloy_ingot");
	public static final DeferredItem<Item> SHIELDING_ALLOY_PLATE = manifestItem("shielding_alloy_plate");
	public static final DeferredItem<Item> SHIELDING_ALLOY_REINFORCED_PLATE = manifestItem("shielding_alloy_reinforced_plate");
	public static final DeferredItem<Item> REACTOR_CIRCUIT = manifestItem("reactor_circuit");
	public static final DeferredItem<Item> CONTROL_ROD_DRIVE = manifestItem("control_rod_drive");
	public static final DeferredItem<Item> URANIUM_FUEL_ROD = manifestItem("uranium_fuel_rod");
	public static final DeferredItem<Item> EMPTY_FUEL_ROD = manifestItem("empty_fuel_rod");
	public static final DeferredItem<Item> PALLADIUM_DUST = manifestItem("palladium_dust");
	public static final DeferredItem<Item> RAW_PALLADIUM = manifestItem("raw_palladium");
	public static final DeferredItem<Item> PALLADIUM_INGOT = manifestItem("palladium_ingot");
	public static final DeferredItem<NetworkAnalyzerItem> NETWORK_ANALYZER =
			ITEMS.registerItem("network_analyzer", NetworkAnalyzerItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<dev.alaindustrial.item.tool.WindGaugeItem> WIND_GAUGE =
			ITEMS.registerItem("wind_gauge", dev.alaindustrial.item.tool.WindGaugeItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<dev.alaindustrial.item.tool.WrenchItem> WRENCH =
			ITEMS.registerItem("wrench", dev.alaindustrial.item.tool.WrenchItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<dev.alaindustrial.item.misc.GuideBookItem> GUIDE_BOOK =
			ITEMS.registerItem("guide_book", dev.alaindustrial.item.misc.GuideBookItem::new, p -> p.stacksTo(1));

	// Teleporter Remote (MOD-092) — hidden from the creative tab until MOD-093 (see CreativeTabContent).
	public static final DeferredItem<TeleporterRemoteItem> TELEPORTER_REMOTE =
			ITEMS.registerItem("teleporter_remote", TeleporterRemoteItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<PouchItem> BATTERY_POUCH =
			ITEMS.registerItem("battery_pouch", PouchItem::new, p -> p.stacksTo(1));
	// Energy Pack (MOD-065): worn LV buffer + the inert battery cell it is crafted from. Equipment
	// properties (EQUIPPABLE + token armor attribute, no ArmorMaterial) come from the common helper,
	// so both loaders build the same item; NeoForge supplies the id from the deferred key itself.
	public static final DeferredItem<Item> BATTERY = manifestItem("battery");
	public static final DeferredItem<EnergyPackItem> ENERGY_PACK =
			ITEMS.registerItem("energy_pack", EnergyPackItem::new, EnergyPackItem::equipmentProperties);
	// Electric Drill (MOD-079): first powered hand tool — a diamond-tier pickaxe that runs on EU. The
	// properties (hand-built TOOL component + EU-item bar, no MAX_DAMAGE) come from the common factory,
	// so both loaders build the same item; NeoForge supplies the id from the deferred key itself.
	public static final DeferredItem<ElectricDrillItem> ELECTRIC_DRILL =
			ITEMS.registerItem("electric_drill", ElectricDrillItem::new, ElectricDrillItem::electricDrillProperties);
	// Diamond-Tipped Electric Drill (MOD-321): the drill's upgrade tier — faster, switchable Silk Touch.
	// Same wiring as the base drill; the properties factory differs only in the mining speed.
	public static final DeferredItem<ElectricDrillDiamondTipItem> ELECTRIC_DRILL_DIAMOND_TIP =
			ITEMS.registerItem("electric_drill_diamond_tip", ElectricDrillDiamondTipItem::new,
					ElectricDrillDiamondTipItem::electricDrillDiamondTipProperties);
	// Electric Chainsaw (MOD-337): the drill's wood-side counterpart — an EU axe for logs and leaves.
	// Same wiring as the drill; the properties factory carries the axe/leaves TOOL rules.
	public static final DeferredItem<ElectricChainsawItem> ELECTRIC_CHAINSAW =
			ITEMS.registerItem("electric_chainsaw", ElectricChainsawItem::new,
					ElectricChainsawItem::electricChainsawProperties);
	// Diamond-Tipped Electric Chainsaw (MOD-374): the chainsaw's upgrade tier — faster, with a
	// switchable Silk Touch mode that drops leaves as blocks. Same wiring as the base chainsaw; the
	// properties factory differs only in the cutting speed.
	public static final DeferredItem<ElectricChainsawDiamondTipItem> ELECTRIC_CHAINSAW_DIAMOND_TIP =
			ITEMS.registerItem("electric_chainsaw_diamond_tip", ElectricChainsawDiamondTipItem::new,
					ElectricChainsawDiamondTipItem::electricChainsawDiamondTipProperties);
	// Electric Shovel (MOD-338): the earth-side member of the same line — an EU shovel for loose ground.
	// Same wiring; the properties factory carries the shovel TOOL rules.
	// MOD-379: registered as its NeoForge subclass, which exists only to declare the SHOVEL_FLATTEN and
	// SHOVEL_DOUSE item abilities. Without them NeoForge's patched ShovelItem.useOn makes no dirt paths
	// and douses no campfires — the same defect the base hoe shipped with, one item later; see
	// ElectricShovelItemNeoForge for the full mechanism.
	public static final DeferredItem<ElectricShovelItemNeoForge> ELECTRIC_SHOVEL =
			ITEMS.registerItem("electric_shovel", ElectricShovelItemNeoForge::new,
					ElectricShovelItem::electricShovelProperties);
	// Diamond-Tipped Electric Shovel (MOD-481): the shovel's upgrade tier — faster, and its drops switch
	// between normal and Silk Touch on the fly. Registered as its own NeoForge subclass for exactly the
	// reason the base shovel is: the upgrade extends the COMMON class, so it inherits none of the
	// SHOVEL_FLATTEN/SHOVEL_DOUSE declaration above and would make no dirt paths without its own.
	public static final DeferredItem<ElectricShovelDiamondTipItemNeoForge> ELECTRIC_SHOVEL_DIAMOND_TIP =
			ITEMS.registerItem("electric_shovel_diamond_tip", ElectricShovelDiamondTipItemNeoForge::new,
					ElectricShovelDiamondTipItem::electricShovelDiamondTipProperties);
	// Electric Hoe (MOD-342): the farming member of the same line — an EU hoe that tills for free.
	// MOD-378: both hoes are registered as their NeoForge subclasses, which exist only to declare the
	// HOE_TILL item ability. Without it NeoForge's patched HoeItem.useOn refuses to till at all — a
	// defect the base hoe shipped with; see ElectricHoeItemNeoForge for the full mechanism.
	public static final DeferredItem<ElectricHoeItemNeoForge> ELECTRIC_HOE =
			ITEMS.registerItem("electric_hoe", ElectricHoeItemNeoForge::new,
					ElectricHoeItem::electricHoeProperties);
	// Diamond-Tipped Electric Hoe (MOD-378): the hoe's upgrade tier — faster, and the plots it tills come
	// out already watered. Same wiring as the base hoe; the properties factory differs only in the speed.
	public static final DeferredItem<ElectricHoeDiamondTipItemNeoForge> ELECTRIC_HOE_DIAMOND_TIP =
			ITEMS.registerItem("electric_hoe_diamond_tip", ElectricHoeDiamondTipItemNeoForge::new,
					ElectricHoeDiamondTipItem::electricHoeDiamondTipProperties);
	// Electric Saber (MOD-149): the line's first weapon — EU per hit, plain sword when flat or off.
	public static final DeferredItem<ElectricSaberItem> ELECTRIC_SABER =
			ITEMS.registerItem("electric_saber", ElectricSaberItem::new,
					ElectricSaberItem::electricSaberProperties);
	// Electromagnet (MOD-132): EU item in any inventory slot that pulls loose drops toward the carrier.
	public static final DeferredItem<dev.alaindustrial.item.tool.MagnetItem> ELECTROMAGNET =
			ITEMS.registerItem("electromagnet", dev.alaindustrial.item.tool.MagnetItem::new, p -> p.stacksTo(1));
	// Jetpack (MOD-148): worn EU flight — thrust on held jump, powerless glide when drained. Equipment
	// properties (EQUIPPABLE + 5 armor points, no ArmorMaterial) come from the common helper.
	public static final DeferredItem<dev.alaindustrial.item.wearable.JetpackItem> JETPACK =
			ITEMS.registerItem("jetpack", dev.alaindustrial.item.wearable.JetpackItem::new,
					dev.alaindustrial.item.wearable.JetpackItem::equipmentProperties);
	// Vacuum Capsule (MOD-063): empty stacks to the vanilla default (64), filled to STACK_SIZE (16).
	public static final DeferredItem<dev.alaindustrial.item.fluid.VacuumCapsuleItem> VACUUM_CAPSULE =
			ITEMS.registerItem("vacuum_capsule", dev.alaindustrial.item.fluid.VacuumCapsuleItem::new);
	// MOD-077 craftRemainder (empty capsule) comes from ItemBuildersNeoForge#filledCapsuleProperties.
	// VACUUM_CAPSULE is an earlier entry in this DeferredRegister, so it is resolved by the time the
	// properties operator runs during the item RegisterEvent.
	public static final DeferredItem<dev.alaindustrial.item.fluid.FilledCapsuleItem> FILLED_VACUUM_CAPSULE =
			ITEMS.registerItem("filled_vacuum_capsule", dev.alaindustrial.item.fluid.FilledCapsuleItem::new,
					ItemBuildersNeoForge.filledCapsuleProperties(VACUUM_CAPSULE));
	// Stock Display Frame (MOD-066). The factory lambda runs during the ITEM RegisterEvent, by which
	// point the ENTITY_TYPE register has already fired (vanilla registry order) — so resolving the
	// entity-type holder here is safe, and never at static-init time.
	public static final DeferredItem<dev.alaindustrial.item.misc.StockDisplayFrameItem> STOCK_DISPLAY_FRAME_ITEM =
			ITEMS.registerItem("stock_display_frame", ItemBuildersNeoForge.stockDisplayFrame(), Item.Properties::new);

	// Scythe (MOD-068): eight AOE foliage tiers. The factory builds a ScytheItem (its own AOE useOn);
	// the properties operator applies .hoe(material, ...) for the tool component + enchantability,
	// exactly like the Fabric ModItems#scythe helper. NeoForge applies setId from the deferred key.
	// The tier tuple (material + AOE profile + attack bias + fire-resistance) is declared once in the
	// loader-neutral dev.alaindustrial.item.tool.ScytheTiers — both loaders register from the same list,
	// so the Fabric and NeoForge builds cannot drift (the comment-as-contract this used to rely on).
	public static final DeferredItem<ScytheItem> SCYTHE_WOOD = scythe(ScytheTiers.WOOD);
	public static final DeferredItem<ScytheItem> SCYTHE_STONE = scythe(ScytheTiers.STONE);
	public static final DeferredItem<ScytheItem> SCYTHE_COPPER = scythe(ScytheTiers.COPPER);
	public static final DeferredItem<ScytheItem> SCYTHE_IRON = scythe(ScytheTiers.IRON);
	public static final DeferredItem<ScytheItem> SCYTHE_GOLD = scythe(ScytheTiers.GOLD);
	public static final DeferredItem<ScytheItem> SCYTHE_TEMPERED_IRON = scythe(ScytheTiers.TEMPERED_IRON);
	public static final DeferredItem<ScytheItem> SCYTHE_DIAMOND = scythe(ScytheTiers.DIAMOND);
	public static final DeferredItem<ScytheItem> SCYTHE_NETHERITE = scythe(ScytheTiers.NETHERITE);

	/** Scythe factory driven by a loader-neutral {@link ScytheTier} — single source of truth for stats. */
	private static DeferredItem<ScytheItem> scythe(ScytheTier tier) {
		return ITEMS.registerItem(tier.id(),
				p -> new ScytheItem(tier.profile(), p),
				p -> {
					Item.Properties props = p.hoe(tier.material(), tier.attackDamage(), -1.0f);
					return tier.fireResistant() ? props.fireResistant() : props;
				});
	}

	// Metal plates (MOD-078): plain ingredient items (ingot form). Made by the Forge Hammer (by hand)
	// or the Compressor; recycled back to dust by the Macerator (except tempered_iron — no dust).
	public static final DeferredItem<Item> COPPER_PLATE = manifestItem("copper_plate");
	public static final DeferredItem<Item> GOLD_PLATE = manifestItem("gold_plate");
	public static final DeferredItem<Item> IRON_PLATE = manifestItem("iron_plate");
	public static final DeferredItem<Item> TIN_PLATE = manifestItem("tin_plate");
	public static final DeferredItem<Item> SILVER_PLATE = manifestItem("silver_plate");
	public static final DeferredItem<Item> NICKEL_PLATE = manifestItem("nickel_plate");
	public static final DeferredItem<Item> URANIUM_PLATE = manifestItem("uranium_plate");
	public static final DeferredItem<Item> PALLADIUM_PLATE = manifestItem("palladium_plate");
	public static final DeferredItem<Item> TEMPERED_IRON_PLATE = manifestItem("tempered_iron_plate");
	// Alloy plates + reinforced tier (MOD-460): same hammer/compressor path, no dust to recycle to.
	public static final DeferredItem<Item> BRONZE_PLATE = manifestItem("bronze_plate");
	public static final DeferredItem<Item> INVAR_PLATE = manifestItem("invar_plate");
	public static final DeferredItem<Item> CUPRONICKEL_PLATE = manifestItem("cupronickel_plate");
	public static final DeferredItem<Item> ELECTRUM_PLATE = manifestItem("electrum_plate");
	public static final DeferredItem<Item> BRONZE_REINFORCED_PLATE = manifestItem("bronze_reinforced_plate");
	public static final DeferredItem<Item> INVAR_REINFORCED_PLATE = manifestItem("invar_reinforced_plate");
	public static final DeferredItem<Item> CUPRONICKEL_REINFORCED_PLATE =
			manifestItem("cupronickel_reinforced_plate");
	public static final DeferredItem<Item> ELECTRUM_REINFORCED_PLATE = manifestItem("electrum_reinforced_plate");

	// Forge Hammer (MOD-078): pre-machine hand tool — ingot + hammer on the grid → plate; the hammer
	// stays and loses 1 durability per plate via the NeoForge craft-remainder hook (HammerItemNeoForge).
	// durability(128) → non-stackable + standard durability bar; repairable(IRON_INGOT) → anvil repair.
	// Deliberately NOT enchantable / NOT tool-tagged: Unbreaking cannot work through the craft-remainder
	// hook (no Level/Player there), so the hammer is honestly non-enchantable. See MOD-078 task log.
	public static final DeferredItem<HammerItemNeoForge> FORGE_HAMMER = ITEMS.registerItem("forge_hammer",
			HammerItemNeoForge::new, p -> p.durability(128).repairable(Items.IRON_INGOT));

	// Oil Bucket (MOD-238): the vanilla WATER_BUCKET pattern. The still fluid resolves eagerly in the
	// item factory — safe, the FLUID RegisterEvent fires before ITEM (vanilla registration order).
	public static final DeferredItem<Item> OIL_BUCKET =
			ITEMS.registerItem("oil_bucket",
					p -> new net.minecraft.world.item.BucketItem(ModFluidsNeoForge.OIL.get(), p),
					p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

	// Distillation fraction buckets (MOD-251) — same WATER_BUCKET pattern as the oil bucket.
	public static final DeferredItem<Item> DIESEL_BUCKET =
			ITEMS.registerItem("diesel_bucket",
					p -> new net.minecraft.world.item.BucketItem(ModFluidsNeoForge.DIESEL.get(), p),
					p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
	public static final DeferredItem<Item> FUEL_OIL_BUCKET =
			ITEMS.registerItem("fuel_oil_bucket",
					p -> new net.minecraft.world.item.BucketItem(ModFluidsNeoForge.FUEL_OIL.get(), p),
					p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

	// --- Block items ---
	public static final DeferredItem<BlockItem> GENERATOR_ITEM =
			ITEMS.registerSimpleBlockItem("generator", ModBlocksNeoForge.GENERATOR);
	public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM =
			ITEMS.registerSimpleBlockItem("solar_panel", ModBlocksNeoForge.SOLAR_PANEL);
	public static final DeferredItem<BlockItem> MOONLIT_SOLAR_PANEL_ITEM =
			ITEMS.registerSimpleBlockItem("moonlit_solar_panel", ModBlocksNeoForge.MOONLIT_SOLAR_PANEL);
	public static final DeferredItem<BlockItem> DAYLIGHT_SOLAR_PANEL_ITEM =
			ITEMS.registerSimpleBlockItem("daylight_solar_panel", ModBlocksNeoForge.DAYLIGHT_SOLAR_PANEL);
	public static final DeferredItem<BlockItem> MACERATOR_ITEM =
			ITEMS.registerSimpleBlockItem("macerator", ModBlocksNeoForge.MACERATOR);
	public static final DeferredItem<BlockItem> BATTERY_BOX_ITEM =
			ITEMS.registerSimpleBlockItem("battery_box", ModBlocksNeoForge.BATTERY_BOX);
	public static final DeferredItem<BlockItem> CESU_ITEM =
			ITEMS.registerSimpleBlockItem("cesu", ModBlocksNeoForge.CESU);

	public static final DeferredItem<BlockItem> TELEPORTER_ITEM =
			ITEMS.registerSimpleBlockItem("teleporter", ModBlocksNeoForge.TELEPORTER);
	public static final DeferredItem<BlockItem> ELECTRIC_FURNACE_ITEM =
			ITEMS.registerSimpleBlockItem("electric_furnace", ModBlocksNeoForge.ELECTRIC_FURNACE);
	public static final DeferredItem<BlockItem> IRON_FURNACE_ITEM =
			ITEMS.registerSimpleBlockItem("iron_furnace", ModBlocksNeoForge.IRON_FURNACE);
	public static final DeferredItem<BlockItem> EXTRACTOR_ITEM =
			ITEMS.registerSimpleBlockItem("extractor", ModBlocksNeoForge.EXTRACTOR);
	public static final DeferredItem<BlockItem> COMPRESSOR_ITEM =
			ITEMS.registerSimpleBlockItem("compressor", ModBlocksNeoForge.COMPRESSOR);
	public static final DeferredItem<BlockItem> COMPONENT_REPAIR_BENCH_ITEM =
			ITEMS.registerSimpleBlockItem("component_repair_bench", ModBlocksNeoForge.COMPONENT_REPAIR_BENCH);
	public static final DeferredItem<BlockItem> CANNING_MACHINE_ITEM =
			ITEMS.registerSimpleBlockItem("canning_machine", ModBlocksNeoForge.CANNING_MACHINE);
	public static final DeferredItem<BlockItem> SAWMILL_ITEM =
			ITEMS.registerSimpleBlockItem("sawmill", ModBlocksNeoForge.SAWMILL);
	public static final DeferredItem<BlockItem> ASSEMBLER_ITEM =
			ITEMS.registerSimpleBlockItem("assembler", ModBlocksNeoForge.ASSEMBLER);
	public static final DeferredItem<BlockItem> POLYMERIZER_ITEM =
			ITEMS.registerSimpleBlockItem("polymerizer", ModBlocksNeoForge.POLYMERIZER);
	// MOD-251: only the tower's base has an item; the segments are placed by the base.
	public static final DeferredItem<BlockItem> DISTILLATION_COLUMN_ITEM =
			ITEMS.registerSimpleBlockItem("distillation_column", ModBlocksNeoForge.DISTILLATION_COLUMN);
	public static final DeferredItem<BlockItem> RECTIFICATION_SECTION_ITEM =
			ITEMS.registerSimpleBlockItem("rectification_section", ModBlocksNeoForge.RECTIFICATION_SECTION);
	public static final DeferredItem<BlockItem> ALLOY_SMELTER_ITEM =
			ITEMS.registerSimpleBlockItem("alloy_smelter", ModBlocksNeoForge.ALLOY_SMELTER);
	public static final DeferredItem<BlockItem> VULCANIZER_ITEM =
			ITEMS.registerSimpleBlockItem("vulcanizer", ModBlocksNeoForge.VULCANIZER);
	public static final DeferredItem<BlockItem> GALVANIC_BATH_ITEM =
			ITEMS.registerSimpleBlockItem("galvanic_bath", ModBlocksNeoForge.GALVANIC_BATH);
	public static final DeferredItem<BlockItem> THERMAL_CENTRIFUGE_ITEM =
			ITEMS.registerSimpleBlockItem("thermal_centrifuge", ModBlocksNeoForge.THERMAL_CENTRIFUGE);

	// MOD-468, stage 1 — block items for the reactor shell.
	public static final DeferredItem<BlockItem> REACTOR_CASING_ITEM =
			ITEMS.registerSimpleBlockItem("reactor_casing", ModBlocksNeoForge.REACTOR_CASING);
	public static final DeferredItem<BlockItem> REACTOR_GLASS_ITEM =
			ITEMS.registerSimpleBlockItem("reactor_glass", ModBlocksNeoForge.REACTOR_GLASS);
	public static final DeferredItem<BlockItem> REACTOR_PORT_ITEM =
			ITEMS.registerSimpleBlockItem("reactor_port", ModBlocksNeoForge.REACTOR_PORT);
	public static final DeferredItem<BlockItem> REACTOR_DOOR_ITEM =
			ITEMS.registerSimpleBlockItem("reactor_door", ModBlocksNeoForge.REACTOR_DOOR);
	public static final DeferredItem<BlockItem> REACTOR_CONTROLLER_ITEM =
			ITEMS.registerSimpleBlockItem("reactor_controller", ModBlocksNeoForge.REACTOR_CONTROLLER);
	public static final DeferredItem<BlockItem> REACTOR_LAMP_ITEM =
			ITEMS.registerSimpleBlockItem("reactor_lamp", ModBlocksNeoForge.REACTOR_LAMP);
	public static final DeferredItem<BlockItem> STEAM_NOZZLE_ITEM =
			ITEMS.registerSimpleBlockItem("steam_nozzle", ModBlocksNeoForge.STEAM_NOZZLE);
	public static final DeferredItem<BlockItem> REACTOR_OUTLET_ITEM =
			ITEMS.registerSimpleBlockItem("reactor_outlet", ModBlocksNeoForge.REACTOR_OUTLET);
	public static final DeferredItem<BlockItem> REACTOR_BUTTON_ITEM =
			ITEMS.registerSimpleBlockItem("reactor_button", ModBlocksNeoForge.REACTOR_BUTTON);
	public static final DeferredItem<BlockItem> FUEL_ROD_ASSEMBLY_ITEM =
			ITEMS.registerSimpleBlockItem("fuel_rod_assembly", ModBlocksNeoForge.FUEL_ROD_ASSEMBLY);
	public static final DeferredItem<BlockItem> ELECTRIC_HEATER_ITEM =
			ITEMS.registerSimpleBlockItem("electric_heater", ModBlocksNeoForge.ELECTRIC_HEATER);
	public static final DeferredItem<BlockItem> CHARGE_PAD_ITEM =
			ITEMS.registerSimpleBlockItem("charge_pad", ModBlocksNeoForge.CHARGE_PAD);
	public static final DeferredItem<BlockItem> ENERGY_CONDENSER_ITEM =
			ITEMS.registerSimpleBlockItem("energy_condenser", ModBlocksNeoForge.ENERGY_CONDENSER);
	public static final DeferredItem<BlockItem> MOB_REPELLER_ITEM =
			ITEMS.registerSimpleBlockItem("mob_repeller", ModBlocksNeoForge.MOB_REPELLER);
	public static final DeferredItem<BlockItem> MOB_REPELLER_MV_ITEM =
			ITEMS.registerSimpleBlockItem("mob_repeller_mv", ModBlocksNeoForge.MOB_REPELLER_MV);
	public static final DeferredItem<BlockItem> MOB_REPELLER_HV_ITEM =
			ITEMS.registerSimpleBlockItem("mob_repeller_hv", ModBlocksNeoForge.MOB_REPELLER_HV);
	public static final DeferredItem<BlockItem> INCUBATOR_ITEM =
			ITEMS.registerSimpleBlockItem("incubator", ModBlocksNeoForge.INCUBATOR);
	public static final DeferredItem<BlockItem> TRELLIS_ITEM =
			ITEMS.registerSimpleBlockItem("trellis", ModBlocksNeoForge.TRELLIS);
	public static final DeferredItem<BlockItem> GEOTHERMAL_GENERATOR_ITEM =
			ITEMS.registerSimpleBlockItem("geothermal_generator", ModBlocksNeoForge.GEOTHERMAL_GENERATOR);
	public static final DeferredItem<BlockItem> PUMP_ITEM =
			ITEMS.registerSimpleBlockItem("pump", ModBlocksNeoForge.PUMP);
	public static final DeferredItem<BlockItem> GARDEN_DRONE_STATION_ITEM =
			ITEMS.registerSimpleBlockItem("garden_drone_station", ModBlocksNeoForge.GARDEN_DRONE_STATION);
	public static final DeferredItem<Item> GARDEN_DRONE =
			manifestItem("garden_drone");
	public static final DeferredItem<FluidTankBlockItem> FLUID_TANK_ITEM =
			ITEMS.registerItem("fluid_tank", ItemBuildersNeoForge.fluidTankBlockItem(ModBlocksNeoForge.FLUID_TANK));
	public static final DeferredItem<BlockItem> WATER_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("water_mill", ModBlocksNeoForge.WATER_MILL);
	public static final DeferredItem<BlockItem> WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("wind_mill", ModBlocksNeoForge.WIND_MILL);
	public static final DeferredItem<BlockItem> HIGH_ALTITUDE_WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("high_altitude_wind_mill", ModBlocksNeoForge.HIGH_ALTITUDE_WIND_MILL);
	public static final DeferredItem<BlockItem> STORM_WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("storm_wind_mill", ModBlocksNeoForge.STORM_WIND_MILL);
	public static final DeferredItem<BlockItem> LIGHTNING_ROD_GENERATOR_ITEM =
			ITEMS.registerSimpleBlockItem("lightning_rod_generator", ModBlocksNeoForge.LIGHTNING_ROD_GENERATOR);
	// MOD-479 — the only block item with extra properties; they come from the shared catalogue so the
	// two loaders cannot disagree about them (the parity gate does not compare item properties).
	public static final DeferredItem<BlockItem> CREATIVE_ENERGY_SOURCE_ITEM =
			ITEMS.registerSimpleBlockItem("creative_energy_source", ModBlocksNeoForge.CREATIVE_ENERGY_SOURCE,
					ContentManifest.CREATIVE_ONLY_ITEM);
	public static final DeferredItem<BlockItem> COPPER_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("copper_cable", ModBlocksNeoForge.COPPER_CABLE);
	public static final DeferredItem<BlockItem> TIN_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("tin_cable", ModBlocksNeoForge.TIN_CABLE);
	public static final DeferredItem<BlockItem> GOLD_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("gold_cable", ModBlocksNeoForge.GOLD_CABLE);
	public static final DeferredItem<BlockItem> ELECTRUM_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("electrum_cable", ModBlocksNeoForge.ELECTRUM_CABLE);
	public static final DeferredItem<BlockItem> INSULATED_COPPER_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("insulated_copper_cable", ModBlocksNeoForge.INSULATED_COPPER_CABLE);
	public static final DeferredItem<BlockItem> INSULATED_TIN_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("insulated_tin_cable", ModBlocksNeoForge.INSULATED_TIN_CABLE);
	public static final DeferredItem<BlockItem> INSULATED_GOLD_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("insulated_gold_cable", ModBlocksNeoForge.INSULATED_GOLD_CABLE);
	public static final DeferredItem<BlockItem> INSULATED_ELECTRUM_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("insulated_electrum_cable", ModBlocksNeoForge.INSULATED_ELECTRUM_CABLE);
	// MOD-108: not registerSimpleBlockItem — the pipe needs its own BlockItem subclass to carry a
	// tooltip (plain hint + Shift for the throughput numbers).
	public static final DeferredItem<BlockItem> ITEM_PIPE_ITEM =
			ITEMS.registerItem("item_pipe", ItemBuildersNeoForge.pipeItem(ModBlocksNeoForge.ITEM_PIPE));
	public static final DeferredItem<BlockItem> FLUID_PIPE_ITEM =
			ITEMS.registerItem("fluid_pipe", ItemBuildersNeoForge.fluidPipeItem(ModBlocksNeoForge.FLUID_PIPE));
	public static final DeferredItem<BlockItem> TIN_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("tin_ore", ModBlocksNeoForge.TIN_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_TIN_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_tin_ore", ModBlocksNeoForge.DEEPSLATE_TIN_ORE);
	public static final DeferredItem<BlockItem> SILVER_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("silver_ore", ModBlocksNeoForge.SILVER_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_SILVER_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_silver_ore", ModBlocksNeoForge.DEEPSLATE_SILVER_ORE);
	public static final DeferredItem<BlockItem> NICKEL_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("nickel_ore", ModBlocksNeoForge.NICKEL_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_NICKEL_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_nickel_ore", ModBlocksNeoForge.DEEPSLATE_NICKEL_ORE);
	public static final DeferredItem<BlockItem> SULFUR_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("sulfur_ore", ModBlocksNeoForge.SULFUR_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_SULFUR_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_sulfur_ore", ModBlocksNeoForge.DEEPSLATE_SULFUR_ORE);
	public static final DeferredItem<BlockItem> URANIUM_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("uranium_ore", ModBlocksNeoForge.URANIUM_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_uranium_ore", ModBlocksNeoForge.DEEPSLATE_URANIUM_ORE);
	public static final DeferredItem<BlockItem> PALLADIUM_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("palladium_ore", ModBlocksNeoForge.PALLADIUM_ORE);
	public static final DeferredItem<BlockItem> IRON_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("iron_chest", ModBlocksNeoForge.IRON_CHEST);
	public static final DeferredItem<BlockItem> STORAGE_MODULE_ITEM =
			ITEMS.registerSimpleBlockItem("storage_module", ModBlocksNeoForge.STORAGE_MODULE);
	// Silver Chest (MOD-087) — the tier above the iron chest: 45 slots (5×9).
	public static final DeferredItem<BlockItem> SILVER_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("silver_chest", ModBlocksNeoForge.SILVER_CHEST);
	// Gold Chest (MOD-088) — the tier above the silver chest: 54 slots (6×9).
	public static final DeferredItem<BlockItem> GOLD_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("gold_chest", ModBlocksNeoForge.GOLD_CHEST);
	// Electrum Chest (MOD-409) — the tier above the gold chest: 81 slots (9×9) behind a scrolling window.
	public static final DeferredItem<BlockItem> ELECTRUM_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("electrum_chest", ModBlocksNeoForge.ELECTRUM_CHEST);
	public static final DeferredItem<BlockItem> SHIELDING_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("shielding_chest", ModBlocksNeoForge.SHIELDING_CHEST);
	public static final DeferredItem<BlockItem> TEMPERED_IRON_BLOCK_ITEM =
			ITEMS.registerSimpleBlockItem("tempered_iron_block", ModBlocksNeoForge.TEMPERED_IRON_BLOCK);
	// MOD-225 block-items.
	public static final DeferredItem<BlockItem> MACHINE_CASING_ITEM =
			ITEMS.registerSimpleBlockItem("machine_casing", ModBlocksNeoForge.MACHINE_CASING);
	public static final DeferredItem<BlockItem> ADVANCED_MACHINE_CASING_ITEM =
			ITEMS.registerSimpleBlockItem("advanced_machine_casing", ModBlocksNeoForge.ADVANCED_MACHINE_CASING);
	public static final DeferredItem<BlockItem> SILVER_PLATE_BLOCK_ITEM =
			ITEMS.registerSimpleBlockItem("silver_plate_block", ModBlocksNeoForge.SILVER_PLATE_BLOCK);
	public static final DeferredItem<BlockItem> TEMPERED_IRON_PLATE_BLOCK_ITEM =
			ITEMS.registerSimpleBlockItem("tempered_iron_plate_block", ModBlocksNeoForge.TEMPERED_IRON_PLATE_BLOCK);
	public static final DeferredItem<BlockItem> INDUSTRIAL_WORKBENCH_ITEM =
			ITEMS.registerSimpleBlockItem("industrial_workbench", ModBlocksNeoForge.INDUSTRIAL_WORKBENCH);
	// Enriched Uranium Torch (MOD-085): a StandingAndWallBlockItem (like vanilla Items.TORCH) — floor use
	// places the standing block, wall use the wall block. Maps to both blocks; the wall block has no item
	// of its own. registerItem (not registerSimpleBlockItem) so we control the factory; useBlockDescriptionPrefix
	// is added explicitly (registerSimpleBlockItem would have added it). Block refs resolve during the item
	// RegisterEvent (blocks already registered).
	public static final DeferredItem<BlockItem> ENRICHED_URANIUM_TORCH_ITEM =
			ITEMS.registerItem("enriched_uranium_torch",
					ItemBuildersNeoForge.standingAndWallBlockItem(
							ModBlocksNeoForge.ENRICHED_URANIUM_TORCH, ModBlocksNeoForge.ENRICHED_URANIUM_WALL_TORCH),
					ItemBuildersNeoForge.blockItemProperties());

	private ModItemsNeoForge() {
	}

	/**
	 * Declares an item whose CONSTRUCTION is shared with Fabric through
	 * {@link ContentManifest#ITEM_FACTORIES} (MOD-306) — plain components, hint items, mode chips,
	 * wearing parts. Before this, each of them existed twice: here and in the Fabric {@code ModItems},
	 * same id and same comment, two registration syntaxes around an identical construction.
	 *
	 * <p>The {@code DeferredItem} field stays — it is this loader's handle, and the lazy timing is exactly
	 * why the manifest stores a FACTORY rather than a finished item: NeoForge only builds the item when
	 * its {@code RegisterEvent} fires, with {@code Properties} whose id the deferred key already supplied
	 * (so, unlike Fabric, no {@code setId} here).
	 */
	private static DeferredItem<Item> manifestItem(String id) {
		return ITEMS.registerItem(id, p -> ContentManifest.itemFactory(id).apply(p));
	}

	/**
	 * Bind each item / block-item {@code DeferredItem} into the loader-neutral {@link ModContent} facade,
	 * mirroring {@code dev.alaindustrial.registry.ModItems#init()} on the Fabric side. A {@code DeferredItem}
	 * <b>is</b> a {@code Supplier} ({@code DeferredItem<T> extends DeferredHolder<Item, T> implements
	 * Supplier<T>}, verified against neoforge-26.2.0.67), so it is assigned directly and resolves lazily
	 * after this register's {@code RegisterEvent} fires. Called from the {@code @Mod} constructor after
	 * {@code ITEMS.register(modBus)}.
	 */
	public static void init() {
		ModContent.ELECTRONIC_CIRCUIT = ELECTRONIC_CIRCUIT;
		ModContent.ADVANCED_CIRCUIT = ADVANCED_CIRCUIT;
		ModContent.ASSEMBLY_BLUEPRINT = ASSEMBLY_BLUEPRINT;
		ModContent.COPPER_COIL = COPPER_COIL;
		ModContent.SPATIAL_CRYSTAL = SPATIAL_CRYSTAL;
		ModContent.RESONANCE_COIL = RESONANCE_COIL;
		ModContent.RTP_CHIP = RTP_CHIP;
		ModContent.ALIGNMENT_CHIP_DAY = ALIGNMENT_CHIP_DAY;
		ModContent.ALIGNMENT_CHIP_NIGHT = ALIGNMENT_CHIP_NIGHT;
		ModContent.EMPTY_CHIP = EMPTY_CHIP;
		ModContent.MUTE_CHIP = MUTE_CHIP;
		ModContent.STATS_CHIP = STATS_CHIP;
		ModContent.OVERCLOCKER_CHIP_I = OVERCLOCKER_CHIP_I;
		ModContent.OVERCLOCKER_CHIP_II = OVERCLOCKER_CHIP_II;
		ModContent.OVERCLOCKER_CHIP_III = OVERCLOCKER_CHIP_III;
		ModContent.ENERGY_CLOT_I = ENERGY_CLOT_I;
		ModContent.ENERGY_CLOT_II = ENERGY_CLOT_II;
		ModContent.ENERGY_CLOT_III = ENERGY_CLOT_III;
		ModContent.CABLE_BREAKER = CABLE_BREAKER;
		ModContent.WINDMILL_ROTOR = WINDMILL_ROTOR;
		ModContent.WATER_MILL_WHEEL = WATER_MILL_WHEEL;
		ModContent.WINDMILL_ROTOR_REINFORCED = WINDMILL_ROTOR_REINFORCED;
		ModContent.WINDMILL_ROTOR_ADVANCED = WINDMILL_ROTOR_ADVANCED;
		ModContent.LIGHTNING_ROD_CONDUCTOR_TIP = LIGHTNING_ROD_CONDUCTOR_TIP;
		ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_REINFORCED = LIGHTNING_ROD_CONDUCTOR_TIP_REINFORCED;
		ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_ADVANCED = LIGHTNING_ROD_CONDUCTOR_TIP_ADVANCED;
		ModContent.WATER_MILL_WHEEL_REINFORCED = WATER_MILL_WHEEL_REINFORCED;
		ModContent.WATER_MILL_WHEEL_ADVANCED = WATER_MILL_WHEEL_ADVANCED;
		ModContent.WOODEN_GEAR = WOODEN_GEAR;
		ModContent.STONE_GEAR = STONE_GEAR;
		ModContent.IRON_GEAR = IRON_GEAR;
		ModContent.GOLD_GEAR = GOLD_GEAR;
		ModContent.SILVER_GEAR = SILVER_GEAR;
		ModContent.TEMPERED_IRON = TEMPERED_IRON;
		ModContent.TEMPERED_IRON_PICKAXE = TEMPERED_IRON_PICKAXE;
		ModContent.TEMPERED_IRON_AXE = TEMPERED_IRON_AXE;
		ModContent.TEMPERED_IRON_HOE = TEMPERED_IRON_HOE;
		ModContent.TEMPERED_IRON_SHOVEL = TEMPERED_IRON_SHOVEL;
		ModContent.TEMPERED_IRON_SWORD = TEMPERED_IRON_SWORD;
		ModContent.TEMPERED_IRON_HELMET = TEMPERED_IRON_HELMET;
		ModContent.TEMPERED_IRON_CHESTPLATE = TEMPERED_IRON_CHESTPLATE;
		ModContent.TEMPERED_IRON_LEGGINGS = TEMPERED_IRON_LEGGINGS;
		ModContent.TEMPERED_IRON_BOOTS = TEMPERED_IRON_BOOTS;
		ModContent.IRON_DUST = IRON_DUST;
		ModContent.COPPER_DUST = COPPER_DUST;
		ModContent.GOLD_DUST = GOLD_DUST;
		ModContent.COAL_DUST = COAL_DUST;
		ModContent.DIAMOND_DUST = DIAMOND_DUST;
		ModContent.EMERALD_DUST = EMERALD_DUST;
		ModContent.EMPTY_CAN = EMPTY_CAN;
		ModContent.CANNED_RATION = CANNED_RATION;
		ModContent.LAPIS_DUST = LAPIS_DUST;
		ModContent.TIN_DUST = TIN_DUST;
		ModContent.RAW_TIN = RAW_TIN;
		ModContent.TIN_INGOT = TIN_INGOT;
		ModContent.SILVER_DUST = SILVER_DUST;
		ModContent.RAW_SILVER = RAW_SILVER;
		ModContent.SILVER_INGOT = SILVER_INGOT;
		ModContent.NICKEL_DUST = NICKEL_DUST;
		ModContent.RAW_NICKEL = RAW_NICKEL;
		ModContent.NICKEL_INGOT = NICKEL_INGOT;
		ModContent.BRONZE_INGOT = BRONZE_INGOT;
		ModContent.INVAR_INGOT = INVAR_INGOT;
		ModContent.CUPRONICKEL_INGOT = CUPRONICKEL_INGOT;
		ModContent.ELECTRUM_INGOT = ELECTRUM_INGOT;
		ModContent.SULFUR_DUST = SULFUR_DUST;
		ModContent.RAW_SULFUR = RAW_SULFUR;
		ModContent.URANIUM_DUST = URANIUM_DUST;
		ModContent.RAW_URANIUM = RAW_URANIUM;
		ModContent.URANIUM_INGOT = URANIUM_INGOT;
		ModContent.URANIUM_SHAVINGS = URANIUM_SHAVINGS;
		ModContent.REFINED_URANIUM = REFINED_URANIUM;
		ModContent.PALLADIUM_DUST = PALLADIUM_DUST;
		ModContent.RAW_PALLADIUM = RAW_PALLADIUM;
		ModContent.PALLADIUM_INGOT = PALLADIUM_INGOT;
		// NETWORK_ANALYZER is a DeferredItem<NetworkAnalyzerItem>; the slot is Supplier<Item>. Generics are
		// invariant, so bind via the (still-lazy) method reference — see ModBlocksNeoForge#init javadoc.
		ModContent.NETWORK_ANALYZER = NETWORK_ANALYZER::get;
		// Same invariant-generics story: DeferredItem<WindGaugeItem> into a Supplier<Item> slot.
		ModContent.WIND_GAUGE = WIND_GAUGE::get;
		ModContent.WRENCH = WRENCH::get;
		ModContent.GUIDE_BOOK = GUIDE_BOOK::get;
		ModContent.TELEPORTER_REMOTE = TELEPORTER_REMOTE::get;
		// Same invariant-generics story as NETWORK_ANALYZER above.
		ModContent.BATTERY_POUCH = BATTERY_POUCH::get;
		ModContent.BATTERY = BATTERY::get;
		ModContent.ENERGY_PACK = ENERGY_PACK::get;
		// DeferredItem<ElectricDrillItem> into a Supplier<Item> slot — bind via ::get (invariant generics).
		ModContent.ELECTRIC_DRILL = ELECTRIC_DRILL::get;
		ModContent.ELECTRIC_DRILL_DIAMOND_TIP = ELECTRIC_DRILL_DIAMOND_TIP::get;
		ModContent.ELECTRIC_CHAINSAW = ELECTRIC_CHAINSAW::get;
		ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP = ELECTRIC_CHAINSAW_DIAMOND_TIP::get;
		ModContent.ELECTRIC_SHOVEL = ELECTRIC_SHOVEL::get;
		ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP = ELECTRIC_SHOVEL_DIAMOND_TIP::get;
		ModContent.ELECTRIC_HOE = ELECTRIC_HOE::get;
		ModContent.ELECTRIC_HOE_DIAMOND_TIP = ELECTRIC_HOE_DIAMOND_TIP::get;
		ModContent.ELECTRIC_SABER = ELECTRIC_SABER::get;
		ModContent.ELECTROMAGNET = ELECTROMAGNET::get;
		ModContent.JETPACK = JETPACK::get;
		ModContent.VACUUM_CAPSULE = VACUUM_CAPSULE::get;
		ModContent.FILLED_VACUUM_CAPSULE = FILLED_VACUUM_CAPSULE::get;
		ModContent.STOCK_DISPLAY_FRAME_ITEM = STOCK_DISPLAY_FRAME_ITEM::get;
		// DeferredItem<ScytheItem> into Supplier<Item> slots — bind via ::get (invariant generics).
		ModContent.SCYTHE_WOOD = SCYTHE_WOOD::get;
		ModContent.SCYTHE_STONE = SCYTHE_STONE::get;
		ModContent.SCYTHE_COPPER = SCYTHE_COPPER::get;
		ModContent.SCYTHE_IRON = SCYTHE_IRON::get;
		ModContent.SCYTHE_GOLD = SCYTHE_GOLD::get;
		ModContent.SCYTHE_TEMPERED_IRON = SCYTHE_TEMPERED_IRON::get;
		ModContent.SCYTHE_DIAMOND = SCYTHE_DIAMOND::get;
		ModContent.SCYTHE_NETHERITE = SCYTHE_NETHERITE::get;
		// Plates are DeferredItem<Item> → bind directly; the hammer is DeferredItem<HammerItemNeoForge>,
		// so it binds via ::get (invariant generics, same story as the scythes above).
		ModContent.COPPER_PLATE = COPPER_PLATE;
		ModContent.GOLD_PLATE = GOLD_PLATE;
		ModContent.IRON_PLATE = IRON_PLATE;
		ModContent.TIN_PLATE = TIN_PLATE;
		ModContent.SILVER_PLATE = SILVER_PLATE;
		ModContent.NICKEL_PLATE = NICKEL_PLATE;
		ModContent.URANIUM_PLATE = URANIUM_PLATE;
		ModContent.PALLADIUM_PLATE = PALLADIUM_PLATE;
		ModContent.TEMPERED_IRON_PLATE = TEMPERED_IRON_PLATE;
		ModContent.BRONZE_PLATE = BRONZE_PLATE;
		ModContent.INVAR_PLATE = INVAR_PLATE;
		ModContent.CUPRONICKEL_PLATE = CUPRONICKEL_PLATE;
		ModContent.ELECTRUM_PLATE = ELECTRUM_PLATE;
		ModContent.BRONZE_REINFORCED_PLATE = BRONZE_REINFORCED_PLATE;
		ModContent.INVAR_REINFORCED_PLATE = INVAR_REINFORCED_PLATE;
		ModContent.CUPRONICKEL_REINFORCED_PLATE = CUPRONICKEL_REINFORCED_PLATE;
		ModContent.ELECTRUM_REINFORCED_PLATE = ELECTRUM_REINFORCED_PLATE;
		ModContent.FORGE_HAMMER = FORGE_HAMMER::get;
		ModContent.OIL_BUCKET = OIL_BUCKET::get;
		ModContent.DIESEL_BUCKET = DIESEL_BUCKET::get;
		ModContent.FUEL_OIL_BUCKET = FUEL_OIL_BUCKET::get;

		ModContent.GENERATOR_ITEM = GENERATOR_ITEM;
		ModContent.SOLAR_PANEL_ITEM = SOLAR_PANEL_ITEM;
		ModContent.MOONLIT_SOLAR_PANEL_ITEM = MOONLIT_SOLAR_PANEL_ITEM;
		ModContent.DAYLIGHT_SOLAR_PANEL_ITEM = DAYLIGHT_SOLAR_PANEL_ITEM;
		ModContent.MACERATOR_ITEM = MACERATOR_ITEM;
		ModContent.BATTERY_BOX_ITEM = BATTERY_BOX_ITEM;
		ModContent.CESU_ITEM = CESU_ITEM;
		ModContent.TELEPORTER_ITEM = TELEPORTER_ITEM;
		ModContent.ELECTRIC_FURNACE_ITEM = ELECTRIC_FURNACE_ITEM;
		ModContent.IRON_FURNACE_ITEM = IRON_FURNACE_ITEM;
		ModContent.EXTRACTOR_ITEM = EXTRACTOR_ITEM;
		ModContent.COMPRESSOR_ITEM = COMPRESSOR_ITEM;
		ModContent.COMPONENT_REPAIR_BENCH_ITEM = COMPONENT_REPAIR_BENCH_ITEM;
		ModContent.CANNING_MACHINE_ITEM = CANNING_MACHINE_ITEM;
		ModContent.SAWMILL_ITEM = SAWMILL_ITEM;
		ModContent.ASSEMBLER_ITEM = ASSEMBLER_ITEM;
		ModContent.POLYMERIZER_ITEM = POLYMERIZER_ITEM;
		ModContent.DISTILLATION_COLUMN_ITEM = DISTILLATION_COLUMN_ITEM;
		ModContent.RECTIFICATION_SECTION_ITEM = RECTIFICATION_SECTION_ITEM;
		ModContent.VULCANIZER_ITEM = VULCANIZER_ITEM;
		ModContent.ALLOY_SMELTER_ITEM = ALLOY_SMELTER_ITEM;
		ModContent.GALVANIC_BATH_ITEM = GALVANIC_BATH_ITEM;
		ModContent.THERMAL_CENTRIFUGE_ITEM = THERMAL_CENTRIFUGE_ITEM;
		// MOD-468, stage 1.
		ModContent.SHIELDING_ALLOY_INGOT = SHIELDING_ALLOY_INGOT;
		ModContent.SHIELDING_ALLOY_PLATE = SHIELDING_ALLOY_PLATE;
		ModContent.SHIELDING_ALLOY_REINFORCED_PLATE = SHIELDING_ALLOY_REINFORCED_PLATE;
		ModContent.REACTOR_CIRCUIT = REACTOR_CIRCUIT;
		ModContent.CONTROL_ROD_DRIVE = CONTROL_ROD_DRIVE;
		ModContent.REACTOR_CASING_ITEM = REACTOR_CASING_ITEM;
		ModContent.REACTOR_GLASS_ITEM = REACTOR_GLASS_ITEM;
		ModContent.REACTOR_PORT_ITEM = REACTOR_PORT_ITEM;
		ModContent.REACTOR_DOOR_ITEM = REACTOR_DOOR_ITEM;
		ModContent.REACTOR_CONTROLLER_ITEM = REACTOR_CONTROLLER_ITEM;
		ModContent.REACTOR_LAMP_ITEM = REACTOR_LAMP_ITEM;
		ModContent.STEAM_NOZZLE_ITEM = STEAM_NOZZLE_ITEM;
		ModContent.REACTOR_OUTLET_ITEM = REACTOR_OUTLET_ITEM;
		ModContent.REACTOR_BUTTON_ITEM = REACTOR_BUTTON_ITEM;
		ModContent.FUEL_ROD_ASSEMBLY_ITEM = FUEL_ROD_ASSEMBLY_ITEM;
		ModContent.URANIUM_FUEL_ROD = URANIUM_FUEL_ROD;
		ModContent.EMPTY_FUEL_ROD = EMPTY_FUEL_ROD;
		ModContent.ELECTRIC_HEATER_ITEM = ELECTRIC_HEATER_ITEM;
		ModContent.CHARGE_PAD_ITEM = CHARGE_PAD_ITEM;
		ModContent.ENERGY_CONDENSER_ITEM = ENERGY_CONDENSER_ITEM;
		ModContent.MOB_REPELLER_ITEM = MOB_REPELLER_ITEM;
		ModContent.MOB_REPELLER_MV_ITEM = MOB_REPELLER_MV_ITEM;
		ModContent.MOB_REPELLER_HV_ITEM = MOB_REPELLER_HV_ITEM;
		ModContent.SOUL_VESSEL = SOUL_VESSEL;
		ModContent.INCUBATOR_ITEM = INCUBATOR_ITEM;
		ModContent.TRELLIS_ITEM = TRELLIS_ITEM;
		ModContent.MUTATION_CHIP_TRANSFORM = MUTATION_CHIP_TRANSFORM;
		ModContent.MUTATION_CHIP_DUPLICATE = MUTATION_CHIP_DUPLICATE;
		ModContent.MUTATION_CHIP_CREATE = MUTATION_CHIP_CREATE;
		ModContent.DEPLETED_URANIUM = DEPLETED_URANIUM;
		ModContent.IRRADIATED_SLAG = IRRADIATED_SLAG;
		ModContent.IRRADIATED_DIAMOND = IRRADIATED_DIAMOND;
		ModContent.RESONANT_SHARD = RESONANT_SHARD;
		ModContent.MUTAGEN_DUST = MUTAGEN_DUST;
		ModContent.RAW_RUBBER = RAW_RUBBER::get;
		ModContent.COTTON_SEEDS = COTTON_SEEDS::get;
		ModContent.COTTON_FIBER = COTTON_FIBER::get;
		ModContent.FLUX_THREAD = FLUX_THREAD::get;
		ModContent.FLUXWEAVE_CLOTH = FLUXWEAVE_CLOTH::get;
		ModContent.FLUXWEAVE_HELMET = FLUXWEAVE_HELMET::get;
		ModContent.SHIELDING_HELMET = SHIELDING_HELMET::get;
		ModContent.SHIELDING_CHESTPLATE = SHIELDING_CHESTPLATE::get;
		ModContent.SHIELDING_LEGGINGS = SHIELDING_LEGGINGS::get;
		ModContent.SHIELDING_BOOTS = SHIELDING_BOOTS::get;
		ModContent.INSULATED_HELMET = INSULATED_HELMET::get;
		ModContent.INSULATED_CHESTPLATE = INSULATED_CHESTPLATE::get;
		ModContent.INSULATED_LEGGINGS = INSULATED_LEGGINGS::get;
		ModContent.INSULATED_BOOTS = INSULATED_BOOTS::get;
		ModContent.FLUXWEAVE_CHESTPLATE = FLUXWEAVE_CHESTPLATE::get;
		ModContent.FLUXWEAVE_LEGGINGS = FLUXWEAVE_LEGGINGS::get;
		ModContent.FLUXWEAVE_BOOTS = FLUXWEAVE_BOOTS::get;
		ModContent.RUBBER = RUBBER::get;
		ModContent.UNSTABLE_ISOTOPE = UNSTABLE_ISOTOPE;
		ModContent.GEOTHERMAL_GENERATOR_ITEM = GEOTHERMAL_GENERATOR_ITEM;
		ModContent.PUMP_ITEM = PUMP_ITEM;
		ModContent.GARDEN_DRONE_STATION_ITEM = GARDEN_DRONE_STATION_ITEM;
		ModContent.GARDEN_DRONE = GARDEN_DRONE;
		ModContent.FLUID_TANK_ITEM = FLUID_TANK_ITEM::get;
		ModContent.WATER_MILL_ITEM = WATER_MILL_ITEM;
		ModContent.WIND_MILL_ITEM = WIND_MILL_ITEM;
		ModContent.HIGH_ALTITUDE_WIND_MILL_ITEM = HIGH_ALTITUDE_WIND_MILL_ITEM;
		ModContent.STORM_WIND_MILL_ITEM = STORM_WIND_MILL_ITEM;
		ModContent.LIGHTNING_ROD_GENERATOR_ITEM = LIGHTNING_ROD_GENERATOR_ITEM;
		ModContent.CREATIVE_ENERGY_SOURCE_ITEM = CREATIVE_ENERGY_SOURCE_ITEM;
		ModContent.COPPER_CABLE_ITEM = COPPER_CABLE_ITEM;
		ModContent.TIN_CABLE_ITEM = TIN_CABLE_ITEM;
		ModContent.GOLD_CABLE_ITEM = GOLD_CABLE_ITEM;
		ModContent.ELECTRUM_CABLE_ITEM = ELECTRUM_CABLE_ITEM;
		ModContent.INSULATED_COPPER_CABLE_ITEM = INSULATED_COPPER_CABLE_ITEM;
		ModContent.INSULATED_TIN_CABLE_ITEM = INSULATED_TIN_CABLE_ITEM;
		ModContent.INSULATED_GOLD_CABLE_ITEM = INSULATED_GOLD_CABLE_ITEM;
		ModContent.INSULATED_ELECTRUM_CABLE_ITEM = INSULATED_ELECTRUM_CABLE_ITEM;
		ModContent.ITEM_PIPE_ITEM = ITEM_PIPE_ITEM;
		ModContent.FLUID_PIPE_ITEM = FLUID_PIPE_ITEM;
		ModContent.TIN_ORE_ITEM = TIN_ORE_ITEM;
		ModContent.DEEPSLATE_TIN_ORE_ITEM = DEEPSLATE_TIN_ORE_ITEM;
		ModContent.SILVER_ORE_ITEM = SILVER_ORE_ITEM;
		ModContent.DEEPSLATE_SILVER_ORE_ITEM = DEEPSLATE_SILVER_ORE_ITEM;
		ModContent.NICKEL_ORE_ITEM = NICKEL_ORE_ITEM;
		ModContent.DEEPSLATE_NICKEL_ORE_ITEM = DEEPSLATE_NICKEL_ORE_ITEM;
		ModContent.SULFUR_ORE_ITEM = SULFUR_ORE_ITEM;
		ModContent.DEEPSLATE_SULFUR_ORE_ITEM = DEEPSLATE_SULFUR_ORE_ITEM;
		ModContent.URANIUM_ORE_ITEM = URANIUM_ORE_ITEM;
		ModContent.DEEPSLATE_URANIUM_ORE_ITEM = DEEPSLATE_URANIUM_ORE_ITEM;
		ModContent.PALLADIUM_ORE_ITEM = PALLADIUM_ORE_ITEM;
		ModContent.IRON_CHEST_ITEM = IRON_CHEST_ITEM;
		ModContent.STORAGE_MODULE_ITEM = STORAGE_MODULE_ITEM;
		ModContent.SILVER_CHEST_ITEM = SILVER_CHEST_ITEM;
		ModContent.GOLD_CHEST_ITEM = GOLD_CHEST_ITEM;
		ModContent.ELECTRUM_CHEST_ITEM = ELECTRUM_CHEST_ITEM;
		ModContent.SHIELDING_CHEST_ITEM = SHIELDING_CHEST_ITEM;
		ModContent.TEMPERED_IRON_BLOCK_ITEM = TEMPERED_IRON_BLOCK_ITEM;
		ModContent.MACHINE_CASING_ITEM = MACHINE_CASING_ITEM;
		ModContent.ADVANCED_MACHINE_CASING_ITEM = ADVANCED_MACHINE_CASING_ITEM;
		ModContent.SILVER_PLATE_BLOCK_ITEM = SILVER_PLATE_BLOCK_ITEM;
		ModContent.TEMPERED_IRON_PLATE_BLOCK_ITEM = TEMPERED_IRON_PLATE_BLOCK_ITEM;
		ModContent.INDUSTRIAL_WORKBENCH_ITEM = INDUSTRIAL_WORKBENCH_ITEM;
		ModContent.ENRICHED_URANIUM_TORCH_ITEM = ENRICHED_URANIUM_TORCH_ITEM;
	}
}
