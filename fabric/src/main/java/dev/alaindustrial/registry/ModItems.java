package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.ElectricDrillItem;
import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.ModArmorMaterials;
import dev.alaindustrial.item.ModToolMaterials;
import dev.alaindustrial.item.NetworkAnalyzerItem;
import dev.alaindustrial.item.PouchItem;
import dev.alaindustrial.item.ScytheItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;

/**
 * Central registration for all Industrialization items: block items for the registered blocks,
 * plus Phase 1 crafting components (electronic circuit, ore dusts). Machines go in Functional
 * Blocks; components go in Ingredients.
 */
public final class ModItems {
	private ModItems() {
	}

	/**
	 * The mod's own creative tab. Lists the release-visible blocks + items; pre-release blocks
	 * (pump, non-copper cables) stay registered but are intentionally omitted from the tab — see
	 * {@link #init()} and task MOD-010. The test block was removed entirely.
	 */
	public static final ResourceKey<CreativeModeTab> TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Industrialization.id("main"));

	// Crafting components (referenced by MaceratorBlockEntity recipes and crafting recipes).
	public static final Item ELECTRONIC_CIRCUIT = item("electronic_circuit");
	// Copper Coil — crafting component (copper cable + tin), gates the Electric Drill.
	public static final Item COPPER_COIL = item("copper_coil");
	public static final Item ALIGNMENT_CHIP_DAY = item("alignment_chip_day");
	public static final Item ALIGNMENT_CHIP_NIGHT = item("alignment_chip_night");
	public static final Item WINDMILL_ROTOR = item("windmill_rotor");
	public static final Item WOODEN_GEAR = item("wooden_gear");
	public static final Item TEMPERED_IRON = item("tempered_iron");
	public static final Item TEMPERED_IRON_PICKAXE =
			temperedIronTool("tempered_iron_pickaxe", p -> p.pickaxe(ModToolMaterials.TEMPERED_IRON, 1.0f, -2.8f));
	// Axe/Hoe/Shovel extend their vanilla subclasses so useOn (stripping/tilling/path) works —
	// in 26.2 these subclasses still exist and carry that behavior; PickaxeItem/SwordItem were
	// removed, so pickaxe/sword stay plain Item with .pickaxe()/.sword().
	public static final Item TEMPERED_IRON_AXE =
			temperedIronSubclass("tempered_iron_axe", p -> new AxeItem(ModToolMaterials.TEMPERED_IRON, 6.0f, -3.1f, p));
	public static final Item TEMPERED_IRON_HOE =
			temperedIronSubclass("tempered_iron_hoe", p -> new HoeItem(ModToolMaterials.TEMPERED_IRON, -2.0f, -1.0f, p));
	public static final Item TEMPERED_IRON_SHOVEL =
			temperedIronSubclass("tempered_iron_shovel", p -> new ShovelItem(ModToolMaterials.TEMPERED_IRON, 1.5f, -3.0f, p));
	public static final Item TEMPERED_IRON_SWORD =
			temperedIronTool("tempered_iron_sword", p -> p.sword(ModToolMaterials.TEMPERED_IRON, 3.0f, -2.4f));
	// Tempered-iron armor (MOD-056). MC 26.2 has no ArmorItem: each piece is a plain Item whose
	// equipment properties are attached via Item.Properties.humanoidArmor(ArmorMaterial, ArmorType).
	// That helper chains durability, attributes, enchantability, the EQUIPPABLE component (with the
	// material's asset id + equip sound) and the repair tag in one go (javap-verified).
	public static final Item TEMPERED_IRON_HELMET =
			temperedArmor("tempered_iron_helmet", ArmorType.HELMET);
	public static final Item TEMPERED_IRON_CHESTPLATE =
			temperedArmor("tempered_iron_chestplate", ArmorType.CHESTPLATE);
	public static final Item TEMPERED_IRON_LEGGINGS =
			temperedArmor("tempered_iron_leggings", ArmorType.LEGGINGS);
	public static final Item TEMPERED_IRON_BOOTS =
			temperedArmor("tempered_iron_boots", ArmorType.BOOTS);
	public static final Item IRON_DUST = item("iron_dust");
	public static final Item COPPER_DUST = item("copper_dust");
	public static final Item GOLD_DUST = item("gold_dust");
	public static final Item COAL_DUST = item("coal_dust");
	public static final Item DIAMOND_DUST = item("diamond_dust");
	public static final Item EMERALD_DUST = item("emerald_dust");
	public static final Item LAPIS_DUST = item("lapis_dust");
	public static final Item TIN_DUST = item("tin_dust");
	public static final Item RAW_TIN = item("raw_tin");
	public static final Item TIN_INGOT = item("tin_ingot");
	public static final Item SILVER_DUST = item("silver_dust");
	public static final Item RAW_SILVER = item("raw_silver");
	public static final Item SILVER_INGOT = item("silver_ingot");
	public static final Item NICKEL_DUST = item("nickel_dust");
	public static final Item RAW_NICKEL = item("raw_nickel");
	public static final Item NICKEL_INGOT = item("nickel_ingot");
	public static final Item URANIUM_DUST = item("uranium_dust");
	public static final Item RAW_URANIUM = item("raw_uranium");
	public static final Item URANIUM_INGOT = item("uranium_ingot");
	public static final Item NETWORK_ANALYZER = networkAnalyzer("network_analyzer");
	public static final Item BATTERY_POUCH = pouch("battery_pouch");
	// Energy Pack (MOD-065): worn LV buffer + the inert battery cell it is crafted from.
	public static final Item BATTERY = item("battery");
	public static final Item ENERGY_PACK = energyPack("energy_pack");
	// Electric Drill (MOD-079): first powered hand tool — a diamond-tier pickaxe that runs on EU.
	public static final Item ELECTRIC_DRILL = electricDrill("electric_drill");
	// Vacuum Capsule (MOD-063): empty (×64) + filled (×16, fluid in the capsule_fluid component).
	public static final Item VACUUM_CAPSULE = vacuumCapsule("vacuum_capsule");
	public static final Item FILLED_VACUUM_CAPSULE = filledCapsule("filled_vacuum_capsule");
	// Stock Display Frame (MOD-066). Referencing ModEntities here also triggers its eager class-init
	// registration, so the EntityType exists before the item is constructed.
	public static final Item STOCK_DISPLAY_FRAME = stockDisplayFrame("stock_display_frame");
	// Scythe (MOD-068): six material tiers, each an AOE foliage clearer. Registered like a hoe
	// (.hoe(material, attackDamage, -1.0f) attaches the tool component + enchantability) but as
	// ScytheItem, not HoeItem — the scythe must not till dirt on right-click, it clears its area
	// instead. attackDamage is per-tier so every scythe shows at least 1 attack damage: wood/stone/
	// copper/gold, whose material bonus alone would render as 0, are lifted to 1; iron and up keep
	// -2.0f and rely on the material bonus (iron 1, diamond 2, netherite 3).
	public static final Item SCYTHE_WOOD =
			scythe("scythe_wood", ToolMaterial.WOOD, 0.0f, new ScytheItem.Profile(3, 2, 12), false);
	public static final Item SCYTHE_STONE =
			scythe("scythe_stone", ToolMaterial.STONE, -1.0f, new ScytheItem.Profile(3, 3, 18), false);
	public static final Item SCYTHE_COPPER =
			scythe("scythe_copper", ToolMaterial.COPPER, -1.0f, new ScytheItem.Profile(3, 3, 24), false);
	public static final Item SCYTHE_IRON =
			scythe("scythe_iron", ToolMaterial.IRON, -2.0f, new ScytheItem.Profile(5, 3, 30), false);
	// Gold: iron-sized area but fragile (gold durability) and highly enchantable — the vanilla gold
	// philosophy (cheap, weak, enchant-hungry), a side-grade rather than a strict step up.
	public static final Item SCYTHE_GOLD =
			scythe("scythe_gold", ToolMaterial.GOLD, 0.0f, new ScytheItem.Profile(5, 3, 30), false);
	public static final Item SCYTHE_TEMPERED_IRON =
			scythe("scythe_tempered_iron", ModToolMaterials.TEMPERED_IRON, -2.0f, new ScytheItem.Profile(5, 4, 40), false);
	public static final Item SCYTHE_DIAMOND =
			scythe("scythe_diamond", ToolMaterial.DIAMOND, -2.0f, new ScytheItem.Profile(5, 5, 50), false);
	// Netherite tier is fire-resistant like all vanilla netherite gear (survives lava/fire).
	public static final Item SCYTHE_NETHERITE =
			scythe("scythe_netherite", ToolMaterial.NETHERITE, -2.0f, new ScytheItem.Profile(7, 5, 70), true);

	// Block items.
	public static final BlockItem GENERATOR_ITEM = blockItem("generator", ModBlocks.GENERATOR);
	public static final BlockItem GEOTHERMAL_GENERATOR_ITEM = blockItem("geothermal_generator", ModBlocks.GEOTHERMAL_GENERATOR);
	public static final BlockItem SOLAR_PANEL_ITEM = blockItem("solar_panel", ModBlocks.SOLAR_PANEL);
	public static final BlockItem MOONLIT_SOLAR_PANEL_ITEM = blockItem("moonlit_solar_panel", ModBlocks.MOONLIT_SOLAR_PANEL);
	public static final BlockItem DAYLIGHT_SOLAR_PANEL_ITEM = blockItem("daylight_solar_panel", ModBlocks.DAYLIGHT_SOLAR_PANEL);
	public static final BlockItem COPPER_CABLE_ITEM = blockItem("copper_cable", ModBlocks.COPPER_CABLE);
	public static final BlockItem TIN_CABLE_ITEM = blockItem("tin_cable", ModBlocks.TIN_CABLE);
	public static final BlockItem INSULATED_COPPER_CABLE_ITEM = blockItem("insulated_copper_cable", ModBlocks.INSULATED_COPPER_CABLE);
	public static final BlockItem INSULATED_TIN_CABLE_ITEM = blockItem("insulated_tin_cable", ModBlocks.INSULATED_TIN_CABLE);
	public static final BlockItem MACERATOR_ITEM = blockItem("macerator", ModBlocks.MACERATOR);
	public static final BlockItem BATTERY_BOX_ITEM = blockItem("battery_box", ModBlocks.BATTERY_BOX);
	public static final BlockItem ELECTRIC_FURNACE_ITEM = blockItem("electric_furnace", ModBlocks.ELECTRIC_FURNACE);
	public static final BlockItem EXTRACTOR_ITEM = blockItem("extractor", ModBlocks.EXTRACTOR);
	public static final BlockItem COMPRESSOR_ITEM = blockItem("compressor", ModBlocks.COMPRESSOR);
	public static final BlockItem PUMP_ITEM = blockItem("pump", ModBlocks.PUMP);
	public static final BlockItem WATER_MILL_ITEM = blockItem("water_mill", ModBlocks.WATER_MILL);
	public static final BlockItem WIND_MILL_ITEM = blockItem("wind_mill", ModBlocks.WIND_MILL);
	public static final BlockItem HIGH_ALTITUDE_WIND_MILL_ITEM = blockItem("high_altitude_wind_mill", ModBlocks.HIGH_ALTITUDE_WIND_MILL);
	public static final BlockItem STORM_WIND_MILL_ITEM = blockItem("storm_wind_mill", ModBlocks.STORM_WIND_MILL);
	public static final BlockItem TIN_ORE_ITEM = blockItem("tin_ore", ModBlocks.TIN_ORE);
	public static final BlockItem DEEPSLATE_TIN_ORE_ITEM = blockItem("deepslate_tin_ore", ModBlocks.DEEPSLATE_TIN_ORE);
	public static final BlockItem SILVER_ORE_ITEM = blockItem("silver_ore", ModBlocks.SILVER_ORE);
	public static final BlockItem DEEPSLATE_SILVER_ORE_ITEM = blockItem("deepslate_silver_ore", ModBlocks.DEEPSLATE_SILVER_ORE);
	public static final BlockItem NICKEL_ORE_ITEM = blockItem("nickel_ore", ModBlocks.NICKEL_ORE);
	public static final BlockItem DEEPSLATE_NICKEL_ORE_ITEM = blockItem("deepslate_nickel_ore", ModBlocks.DEEPSLATE_NICKEL_ORE);
	public static final BlockItem URANIUM_ORE_ITEM = blockItem("uranium_ore", ModBlocks.URANIUM_ORE);
	public static final BlockItem DEEPSLATE_URANIUM_ORE_ITEM = blockItem("deepslate_uranium_ore", ModBlocks.DEEPSLATE_URANIUM_ORE);
	public static final BlockItem IRON_CHEST_ITEM = blockItem("iron_chest", ModBlocks.IRON_CHEST);
	public static final BlockItem TEMPERED_IRON_BLOCK_ITEM = blockItem("tempered_iron_block", ModBlocks.TEMPERED_IRON_BLOCK);
	// Enriched Uranium Torch (MOD-085): a StandingAndWallBlockItem (like vanilla Items.TORCH) so using it
	// on a wall places the wall variant and on the floor the standing variant. The wall block has no item
	// of its own — this item maps to both blocks (StandingAndWallBlockItem#registerBlocks).
	public static final BlockItem ENRICHED_URANIUM_TORCH_ITEM = standingAndWallBlockItem(
			"enriched_uranium_torch", ModBlocks.ENRICHED_URANIUM_TORCH, ModBlocks.ENRICHED_URANIUM_WALL_TORCH);

	private static Item item(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	private static Item networkAnalyzer(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		// The default AnalyzerMode (TRAVERSE) is NOT set via Item.Properties.component(...): that path
		// resolves the DataComponentType during ModItems' static init, before the loader binds the
		// neutral handle (see IndustrializationFabric/ModDataComponentsNeoForge). NetworkAnalyzerItem
		// treats a missing component as TRAVERSE instead, and switchMode persists it on first use.
		return Registry.register(BuiltInRegistries.ITEM, key,
				new NetworkAnalyzerItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	private static Item pouch(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		// Like networkAnalyzer: no Item.Properties.component(...) defaults — PouchItem/ItemEnergy treat
		// absent pouch_energy/pouch_contents as 0 EU / empty (MOD-052).
		return Registry.register(BuiltInRegistries.ITEM, key,
				new PouchItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	// Energy Pack (MOD-065). Equipment properties come from the common helper (EQUIPPABLE + token armor
	// attribute, no ArmorMaterial — see EnergyPackItem.equipmentProperties); like the pouch, no
	// pouch_energy default is preset, ItemEnergy reads an absent component as 0 EU.
	private static Item energyPack(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new EnergyPackItem(EnergyPackItem.equipmentProperties(new Item.Properties().setId(key))));
	}

	// Electric Drill (MOD-079). Like the Energy Pack: a plain-Item subclass whose properties come from
	// a common static factory (the hand-built TOOL component + EU-item bar, no MAX_DAMAGE — see
	// ElectricDrillItem.electricDrillProperties). No pouch_energy default is preset; ItemEnergy reads an
	// absent component as 0 EU.
	private static Item electricDrill(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new ElectricDrillItem(ElectricDrillItem.electricDrillProperties(new Item.Properties().setId(key))));
	}

	// Vacuum Capsule (MOD-063). Empty capsule stacks to the vanilla default (64); no fluid component
	// default — ItemFluid treats an absent capsule_fluid as empty.
	private static Item vacuumCapsule(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.VacuumCapsuleItem(new Item.Properties().setId(key)));
	}

	// Filled capsule: one bucket of a single fluid, stacking to FilledCapsuleItem.STACK_SIZE (16).
	// MOD-077: craftRemainder = the empty capsule, so a lava capsule burnt in a vanilla furnace returns an
	// empty capsule (the fuel remainder), exactly as a lava bucket returns an empty bucket. VACUUM_CAPSULE is
	// initialised just above (field order), so it is already registered here.
	private static Item filledCapsule(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.FilledCapsuleItem(
						new Item.Properties().setId(key).stacksTo(dev.alaindustrial.item.FilledCapsuleItem.STACK_SIZE)
								.craftRemainder(VACUUM_CAPSULE)));
	}

	// Tempered-iron hand-held tools (MOD-054). In MC 26.2 PickaxeItem/SwordItem/DiggerItem were
	// removed — a pickaxe/sword is a plain Item whose `minecraft:tool` component is attached via
	// Item.Properties.{pickaxe,sword}(ToolMaterial, attackDamage, attackSpeed). AxeItem/HoeItem/
	// ShovelItem still exist (they carry useOn behavior: stripping/tilling/path) and are used for
	// those three tools — see temperedIronSubclass. attackDamage/attackSpeed mirror vanilla iron.
	private static Item temperedIronTool(String path, java.util.function.UnaryOperator<Item.Properties> toolProps) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new Item(toolProps.apply(new Item.Properties()).setId(key)));
	}

	// Axe/Hoe/Shovel subclass helper. The vanilla ctors (ToolMaterial, float attackDamage, float
	// attackSpeed, Properties) themselves call props.{axe,hoe,shovel}(...) and super(...), so the
	// tool component AND the useOn behavior (log stripping / dirt tilling / grass path) come free.
	// factory binds the two floats to a concrete ctor reference like AxeItem::new.
	private static Item temperedIronSubclass(String path,
			java.util.function.Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	// Tempered-iron armor helper (MOD-056). humanoidArmor(material, type) wires durability
	// (type.getDurability(material.durability())), attributes, enchantability, the EQUIPPABLE
	// component (equip sound + asset id from the material) and the repair tag in one call — this
	// is exactly how vanilla Items.IRON_HELMET is built (javap-verified against the 26.2 jar).
	private static Item temperedArmor(String path, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new Item(new Item.Properties().humanoidArmor(ModArmorMaterials.TEMPERED_IRON, type).setId(key)));
	}

	// Scythe helper (MOD-068). .hoe(material, attackDamage, attackSpeed) attaches the data-driven
	// tool component (durability/mining speed/enchantability/attack) exactly like a vanilla hoe, but
	// the instance is a ScytheItem so right-click runs the AOE clear instead of tilling. attackDamage
	// is per-tier: displayed damage = 1 (player base) + attackDamage + material.attackDamageBonus, so
	// the low tiers whose bonus alone would show 0 (wood/stone/copper/gold) pass a higher value to
	// reach the 1-damage floor; iron and up keep -2.0f and let the material bonus carry them.
	private static Item scythe(String path, ToolMaterial material, float attackDamage, ScytheItem.Profile profile, boolean fireResistant) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		Item.Properties props = new Item.Properties().hoe(material, attackDamage, -1.0f);
		if (fireResistant) {
			props.fireResistant();
		}
		return Registry.register(BuiltInRegistries.ITEM, key, new ScytheItem(profile, props.setId(key)));
	}

	private static Item stockDisplayFrame(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.StockDisplayFrameItem(ModEntities.STOCK_DISPLAY_FRAME,
						new Item.Properties().setId(key)));
	}

	private static BlockItem blockItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	// Torch-style item (MOD-085): places `standing` on the floor, `wall` on a vertical face — exactly how
	// vanilla Items.TORCH is built (StandingAndWallBlockItem(TORCH, WALL_TORCH, Direction.DOWN, p)).
	private static BlockItem standingAndWallBlockItem(String path, Block standing, Block wall) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		StandingAndWallBlockItem item = new StandingAndWallBlockItem(standing, wall,
				net.minecraft.core.Direction.DOWN, new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.alaindustrial"))
				.icon(() -> new ItemStack(MACERATOR_ITEM))
				.displayItems((params, output) -> {
						// NB: non-copper cables (tin, insulated copper/tin) are registered but deliberately
						// hidden for the v1.0 release (no tab entry, recipe removed). Re-add their
						// output.accept(...) + recipe JSON to bring them back. See task MOD-010.
						// Likewise the water mill + high-altitude/storm windmills are temporarily hidden
						// from the player (no tab entry; water_mill recipe removed). Re-add their
						// output.accept(...) + the water_mill recipe JSON to bring them back.
						// Generators
						output.accept(SOLAR_PANEL_ITEM);
						output.accept(DAYLIGHT_SOLAR_PANEL_ITEM);
						output.accept(MOONLIT_SOLAR_PANEL_ITEM);
						output.accept(GENERATOR_ITEM);
						output.accept(GEOTHERMAL_GENERATOR_ITEM);
						output.accept(WIND_MILL_ITEM);
					// Machines
					output.accept(MACERATOR_ITEM);
					output.accept(ELECTRIC_FURNACE_ITEM);
					output.accept(EXTRACTOR_ITEM);
					output.accept(COMPRESSOR_ITEM);
					output.accept(PUMP_ITEM);
					// Storage + cables
					output.accept(BATTERY_BOX_ITEM);
					output.accept(IRON_CHEST_ITEM);
					output.accept(STOCK_DISPLAY_FRAME);
					output.accept(COPPER_CABLE_ITEM);
					// Ores + materials
					output.accept(TIN_ORE_ITEM);
					output.accept(DEEPSLATE_TIN_ORE_ITEM);
					output.accept(RAW_TIN);
					output.accept(TIN_DUST);
					output.accept(TIN_INGOT);
					output.accept(SILVER_ORE_ITEM);
					output.accept(DEEPSLATE_SILVER_ORE_ITEM);
					output.accept(RAW_SILVER);
					output.accept(SILVER_DUST);
					output.accept(SILVER_INGOT);
					output.accept(NICKEL_ORE_ITEM);
					output.accept(DEEPSLATE_NICKEL_ORE_ITEM);
					output.accept(RAW_NICKEL);
					output.accept(NICKEL_DUST);
					output.accept(NICKEL_INGOT);
					output.accept(URANIUM_ORE_ITEM);
					output.accept(DEEPSLATE_URANIUM_ORE_ITEM);
					output.accept(RAW_URANIUM);
					output.accept(URANIUM_DUST);
					output.accept(URANIUM_INGOT);
					// Components
					output.accept(ELECTRONIC_CIRCUIT);
					output.accept(COPPER_COIL);
					output.accept(ALIGNMENT_CHIP_DAY);
					output.accept(ALIGNMENT_CHIP_NIGHT);
						output.accept(WINDMILL_ROTOR);
						output.accept(WOODEN_GEAR);
						output.accept(IRON_DUST);
					output.accept(COPPER_DUST);
					output.accept(GOLD_DUST);
					output.accept(COAL_DUST);
					output.accept(DIAMOND_DUST);
					output.accept(EMERALD_DUST);
					output.accept(LAPIS_DUST);
						output.accept(NETWORK_ANALYZER);
						output.accept(BATTERY_POUCH);
						output.accept(BATTERY);
						output.accept(ENERGY_PACK);
						output.accept(ELECTRIC_DRILL);
						// Empty capsule only — the filled form is obtained by using it on a fluid.
						output.accept(VACUUM_CAPSULE);
							output.accept(ENRICHED_URANIUM_TORCH_ITEM); // MOD-085 decorative light source
						// Tempered Iron — ingot, block, tools and armor as one continuous row
						output.accept(TEMPERED_IRON);
						output.accept(TEMPERED_IRON_BLOCK_ITEM);
						output.accept(TEMPERED_IRON_PICKAXE);
						output.accept(TEMPERED_IRON_AXE);
						output.accept(TEMPERED_IRON_SHOVEL);
						output.accept(TEMPERED_IRON_HOE);
						output.accept(TEMPERED_IRON_SWORD);
						output.accept(TEMPERED_IRON_HELMET);
						output.accept(TEMPERED_IRON_CHESTPLATE);
						output.accept(TEMPERED_IRON_LEGGINGS);
						output.accept(TEMPERED_IRON_BOOTS);
							// Scythes (MOD-068) — one continuous tier row wood → netherite.
							output.accept(SCYTHE_WOOD);
							output.accept(SCYTHE_STONE);
							output.accept(SCYTHE_COPPER);
							output.accept(SCYTHE_IRON);
							output.accept(SCYTHE_GOLD);
							output.accept(SCYTHE_TEMPERED_IRON);
							output.accept(SCYTHE_DIAMOND);
							output.accept(SCYTHE_NETHERITE);
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, tab);

		bindModContent();
		registerVanillaCreativeTabs();
	}

	private static void registerVanillaCreativeTabs() {
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.COMBAT)
				.register(output -> {
					output.insertAfter(Items.IRON_SWORD, ModContent.TEMPERED_IRON_SWORD.get());
					output.insertAfter(Items.IRON_BOOTS,
							ModContent.TEMPERED_IRON_HELMET.get(),
							ModContent.TEMPERED_IRON_CHESTPLATE.get(),
							ModContent.TEMPERED_IRON_LEGGINGS.get(),
							ModContent.TEMPERED_IRON_BOOTS.get());
					// The Energy Pack is worn in the chest slot, so a player looking for chest gear finds it
					// here too — it also sits with the other powered items under Tools & Utilities below.
					output.insertAfter(ModContent.TEMPERED_IRON_BOOTS.get(), ModContent.ENERGY_PACK.get());
				});
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.TOOLS_AND_UTILITIES)
				.register(output -> {
					// Each scythe sits right after the matching vanilla hoe tier. The iron scythe follows
					// the vanilla iron hoe; the tempered-iron scythe follows the mod's tempered-iron hoe.
					output.insertAfter(Items.IRON_HOE, ModContent.SCYTHE_IRON.get());
					// The tempered-iron tool set is the mod's tier between iron and gold, so it follows the
					// iron scythe right after the vanilla iron hoe.
					output.insertAfter(ModContent.SCYTHE_IRON.get(),
							ModContent.TEMPERED_IRON_PICKAXE.get(),
							ModContent.TEMPERED_IRON_AXE.get(),
							ModContent.TEMPERED_IRON_SHOVEL.get(),
							ModContent.TEMPERED_IRON_HOE.get());
					output.insertAfter(Items.WOODEN_HOE, ModContent.SCYTHE_WOOD.get());
					output.insertAfter(Items.STONE_HOE, ModContent.SCYTHE_STONE.get());
					output.insertAfter(Items.COPPER_HOE, ModContent.SCYTHE_COPPER.get());
					output.insertAfter(ModContent.TEMPERED_IRON_HOE.get(), ModContent.SCYTHE_TEMPERED_IRON.get());
					output.insertAfter(Items.GOLDEN_HOE, ModContent.SCYTHE_GOLD.get());
					output.insertAfter(Items.DIAMOND_HOE, ModContent.SCYTHE_DIAMOND.get());
					output.insertAfter(Items.NETHERITE_HOE, ModContent.SCYTHE_NETHERITE.get());
					output.insertAfter(Items.COMPASS, ModContent.NETWORK_ANALYZER.get());
					output.accept(ModContent.BATTERY_POUCH.get());
					output.accept(ModContent.ENERGY_PACK.get());
					output.accept(ModContent.ELECTRIC_DRILL.get());
				});
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.INGREDIENTS)
				.register(output -> CreativeTabContent.ingredients(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.BUILDING_BLOCKS)
				.register(output -> CreativeTabContent.buildingBlocks(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.NATURAL_BLOCKS)
				.register(output -> CreativeTabContent.naturalBlocks(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> CreativeTabContent.functionalBlocks(output::accept));
	}

	/**
	 * Publish each eagerly-registered item / block item into the loader-neutral {@link ModContent}
	 * facade so content classes (which read {@code ModContent.X.get()} at runtime) resolve the Fabric
	 * instance. Each handle wraps the already-registered value in a constant supplier; NeoForge binds a
	 * lazy {@code DeferredHolder} into the same handle. See {@link ModContent}.
	 */
	private static void bindModContent() {
		ModContent.ELECTRONIC_CIRCUIT = () -> ELECTRONIC_CIRCUIT;
		ModContent.COPPER_COIL = () -> COPPER_COIL;
		ModContent.ALIGNMENT_CHIP_DAY = () -> ALIGNMENT_CHIP_DAY;
		ModContent.ALIGNMENT_CHIP_NIGHT = () -> ALIGNMENT_CHIP_NIGHT;
		ModContent.WINDMILL_ROTOR = () -> WINDMILL_ROTOR;
		ModContent.WOODEN_GEAR = () -> WOODEN_GEAR;
		ModContent.TEMPERED_IRON = () -> TEMPERED_IRON;
		ModContent.TEMPERED_IRON_PICKAXE = () -> TEMPERED_IRON_PICKAXE;
		ModContent.TEMPERED_IRON_AXE = () -> TEMPERED_IRON_AXE;
		ModContent.TEMPERED_IRON_HOE = () -> TEMPERED_IRON_HOE;
		ModContent.TEMPERED_IRON_SHOVEL = () -> TEMPERED_IRON_SHOVEL;
		ModContent.TEMPERED_IRON_SWORD = () -> TEMPERED_IRON_SWORD;
		ModContent.TEMPERED_IRON_HELMET = () -> TEMPERED_IRON_HELMET;
		ModContent.TEMPERED_IRON_CHESTPLATE = () -> TEMPERED_IRON_CHESTPLATE;
		ModContent.TEMPERED_IRON_LEGGINGS = () -> TEMPERED_IRON_LEGGINGS;
		ModContent.TEMPERED_IRON_BOOTS = () -> TEMPERED_IRON_BOOTS;
		ModContent.IRON_DUST = () -> IRON_DUST;
		ModContent.COPPER_DUST = () -> COPPER_DUST;
		ModContent.GOLD_DUST = () -> GOLD_DUST;
		ModContent.COAL_DUST = () -> COAL_DUST;
		ModContent.DIAMOND_DUST = () -> DIAMOND_DUST;
		ModContent.EMERALD_DUST = () -> EMERALD_DUST;
		ModContent.LAPIS_DUST = () -> LAPIS_DUST;
		ModContent.TIN_DUST = () -> TIN_DUST;
		ModContent.RAW_TIN = () -> RAW_TIN;
		ModContent.TIN_INGOT = () -> TIN_INGOT;
		ModContent.SILVER_DUST = () -> SILVER_DUST;
		ModContent.RAW_SILVER = () -> RAW_SILVER;
		ModContent.SILVER_INGOT = () -> SILVER_INGOT;
		ModContent.NICKEL_DUST = () -> NICKEL_DUST;
		ModContent.RAW_NICKEL = () -> RAW_NICKEL;
		ModContent.NICKEL_INGOT = () -> NICKEL_INGOT;
		ModContent.URANIUM_DUST = () -> URANIUM_DUST;
		ModContent.RAW_URANIUM = () -> RAW_URANIUM;
		ModContent.URANIUM_INGOT = () -> URANIUM_INGOT;
		ModContent.NETWORK_ANALYZER = () -> NETWORK_ANALYZER;
		ModContent.BATTERY_POUCH = () -> BATTERY_POUCH;
		ModContent.BATTERY = () -> BATTERY;
		ModContent.ENERGY_PACK = () -> ENERGY_PACK;
		ModContent.ELECTRIC_DRILL = () -> ELECTRIC_DRILL;
		ModContent.VACUUM_CAPSULE = () -> VACUUM_CAPSULE;
		ModContent.FILLED_VACUUM_CAPSULE = () -> FILLED_VACUUM_CAPSULE;
		ModContent.STOCK_DISPLAY_FRAME_ITEM = () -> STOCK_DISPLAY_FRAME;
		ModContent.SCYTHE_WOOD = () -> SCYTHE_WOOD;
		ModContent.SCYTHE_STONE = () -> SCYTHE_STONE;
		ModContent.SCYTHE_COPPER = () -> SCYTHE_COPPER;
		ModContent.SCYTHE_IRON = () -> SCYTHE_IRON;
		ModContent.SCYTHE_GOLD = () -> SCYTHE_GOLD;
		ModContent.SCYTHE_TEMPERED_IRON = () -> SCYTHE_TEMPERED_IRON;
		ModContent.SCYTHE_DIAMOND = () -> SCYTHE_DIAMOND;
		ModContent.SCYTHE_NETHERITE = () -> SCYTHE_NETHERITE;

		ModContent.GENERATOR_ITEM = () -> GENERATOR_ITEM;
		ModContent.GEOTHERMAL_GENERATOR_ITEM = () -> GEOTHERMAL_GENERATOR_ITEM;
		ModContent.WATER_MILL_ITEM = () -> WATER_MILL_ITEM;
		ModContent.WIND_MILL_ITEM = () -> WIND_MILL_ITEM;
		ModContent.HIGH_ALTITUDE_WIND_MILL_ITEM = () -> HIGH_ALTITUDE_WIND_MILL_ITEM;
		ModContent.STORM_WIND_MILL_ITEM = () -> STORM_WIND_MILL_ITEM;
		ModContent.SOLAR_PANEL_ITEM = () -> SOLAR_PANEL_ITEM;
		ModContent.MOONLIT_SOLAR_PANEL_ITEM = () -> MOONLIT_SOLAR_PANEL_ITEM;
		ModContent.DAYLIGHT_SOLAR_PANEL_ITEM = () -> DAYLIGHT_SOLAR_PANEL_ITEM;
		ModContent.COPPER_CABLE_ITEM = () -> COPPER_CABLE_ITEM;
		ModContent.TIN_CABLE_ITEM = () -> TIN_CABLE_ITEM;
		ModContent.INSULATED_COPPER_CABLE_ITEM = () -> INSULATED_COPPER_CABLE_ITEM;
		ModContent.INSULATED_TIN_CABLE_ITEM = () -> INSULATED_TIN_CABLE_ITEM;
		ModContent.MACERATOR_ITEM = () -> MACERATOR_ITEM;
		ModContent.BATTERY_BOX_ITEM = () -> BATTERY_BOX_ITEM;
		ModContent.ELECTRIC_FURNACE_ITEM = () -> ELECTRIC_FURNACE_ITEM;
		ModContent.EXTRACTOR_ITEM = () -> EXTRACTOR_ITEM;
		ModContent.COMPRESSOR_ITEM = () -> COMPRESSOR_ITEM;
		ModContent.PUMP_ITEM = () -> PUMP_ITEM;
		ModContent.TIN_ORE_ITEM = () -> TIN_ORE_ITEM;
		ModContent.DEEPSLATE_TIN_ORE_ITEM = () -> DEEPSLATE_TIN_ORE_ITEM;
		ModContent.SILVER_ORE_ITEM = () -> SILVER_ORE_ITEM;
		ModContent.DEEPSLATE_SILVER_ORE_ITEM = () -> DEEPSLATE_SILVER_ORE_ITEM;
		ModContent.NICKEL_ORE_ITEM = () -> NICKEL_ORE_ITEM;
		ModContent.DEEPSLATE_NICKEL_ORE_ITEM = () -> DEEPSLATE_NICKEL_ORE_ITEM;
		ModContent.URANIUM_ORE_ITEM = () -> URANIUM_ORE_ITEM;
		ModContent.DEEPSLATE_URANIUM_ORE_ITEM = () -> DEEPSLATE_URANIUM_ORE_ITEM;
		ModContent.IRON_CHEST_ITEM = () -> IRON_CHEST_ITEM;
		ModContent.TEMPERED_IRON_BLOCK_ITEM = () -> TEMPERED_IRON_BLOCK_ITEM;
		ModContent.ENRICHED_URANIUM_TORCH_ITEM = () -> ENRICHED_URANIUM_TORCH_ITEM;
	}
}
