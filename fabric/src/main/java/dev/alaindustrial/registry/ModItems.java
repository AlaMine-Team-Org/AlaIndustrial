package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.tool.ElectricChainsawDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricChainsawItem;
import dev.alaindustrial.item.tool.ElectricDrillDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import dev.alaindustrial.item.tool.ElectricHoeItem;
import dev.alaindustrial.item.tool.ElectricSaberItem;
import dev.alaindustrial.item.tool.ElectricShovelItem;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.item.wearable.FluxweaveArmorItem;
import dev.alaindustrial.item.wearable.JetpackItem;
import dev.alaindustrial.item.fluid.FluidTankBlockItem;
import dev.alaindustrial.item.tool.ScytheTiers;
import dev.alaindustrial.item.tool.MagnetItem;
import dev.alaindustrial.item.material.ModArmorMaterials;
import dev.alaindustrial.item.material.ModToolMaterials;
import dev.alaindustrial.item.material.TemperedIronToolStats;
import dev.alaindustrial.item.tool.NetworkAnalyzerItem;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.tool.ScytheItem;
import dev.alaindustrial.item.teleport.TeleporterRemoteItem;
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
	public static final Item ELECTRONIC_CIRCUIT = manifestItem("electronic_circuit");
	// MOD-299 — the MV circuit: electronic circuit + gold plates + rubber. Gates the advanced casing.
	public static final Item ADVANCED_CIRCUIT = manifestItem("advanced_circuit");
	public static final Item ASSEMBLY_BLUEPRINT = assemblyBlueprint("assembly_blueprint");
	// Copper Coil — crafting component (copper cable + tin), gates the Electric Drill.
	public static final Item COPPER_COIL = manifestItem("copper_coil");
	public static final Item ALIGNMENT_CHIP_DAY = manifestItem("alignment_chip_day");
	public static final Item ALIGNMENT_CHIP_NIGHT = manifestItem("alignment_chip_night");
	// Upgrade chips (MOD-080): empty blank + the mute upgrade. Each shows a gray hint line.
	public static final Item EMPTY_CHIP = manifestItem("empty_chip");

	// Incubator (MOD-118): mode chips, by-products and the tier-1 evolution materials.
	public static final Item MUTATION_CHIP_TRANSFORM = manifestItem("mutation_chip_transform");
	public static final Item MUTATION_CHIP_DUPLICATE = manifestItem("mutation_chip_duplicate");
	public static final Item MUTATION_CHIP_CREATE = manifestItem("mutation_chip_create");
	public static final Item DEPLETED_URANIUM = manifestItem("depleted_uranium");
	public static final Item IRRADIATED_SLAG = manifestItem("irradiated_slag");
	public static final Item IRRADIATED_DIAMOND = manifestItem("irradiated_diamond");
	public static final Item RESONANT_SHARD = manifestItem("resonant_shard");
	public static final Item MUTAGEN_DUST = manifestItem("mutagen_dust");
	// Oil → rubber chain: the polymerizer's product and the vulcanizer's cured output.
	public static final Item RAW_RUBBER = manifestItem("raw_rubber");
	public static final Item RUBBER = manifestItem("rubber");

	// Cotton (MOD-280): the seed is planted onto a trellis by right-click (the block handles it, so this
	// stays a plain Item — no BlockItem/ItemNameBlockItem), the fibre is the harvest.
	public static final Item COTTON_SEEDS = manifestItem("cotton_seeds");
	public static final Item COTTON_FIBER = manifestItem("cotton_fiber");
	// Fluxweave chain (MOD-127): silver-plated fibre, then the woven sheet. Both are plain crafting
	// components — the EU buffer lives on the armor, not on the material.
	public static final Item FLUX_THREAD = manifestItem("flux_thread");
	public static final Item FLUXWEAVE_CLOTH = manifestItem("fluxweave_cloth");
	public static final Item UNSTABLE_ISOTOPE = manifestItem("unstable_isotope");
	public static final Item MUTE_CHIP = manifestItem("mute_chip");
	/** Cable breaker (MOD-276): clamps onto a laid cable to cut the line for maintenance. */
	public static final Item CABLE_BREAKER = manifestItem("cable_breaker");
	// Rotor / wheel (MOD-189): durability components — wear shows as a vanilla durability bar and, being
	// damageable, they are automatically non-stackable. maxDamage from Config (registration-time).
	public static final Item WINDMILL_ROTOR = manifestItem("windmill_rotor");
	public static final Item WATER_MILL_WHEEL = manifestItem("water_mill_wheel");
	public static final Item WOODEN_GEAR = manifestItem("wooden_gear");
	// Metal gears (MOD-105): crafting components for machinery still to come.
	public static final Item STONE_GEAR = manifestItem("stone_gear");
	public static final Item IRON_GEAR = manifestItem("iron_gear");
	public static final Item GOLD_GEAR = manifestItem("gold_gear");
	public static final Item SILVER_GEAR = manifestItem("silver_gear");
	public static final Item TEMPERED_IRON = manifestItem("tempered_iron");
	public static final Item TEMPERED_IRON_PICKAXE =
			temperedIronTool("tempered_iron_pickaxe", p -> p.pickaxe(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.PICKAXE.attackDamage(), TemperedIronToolStats.PICKAXE.attackSpeed()));
	// Axe/Hoe/Shovel extend their vanilla subclasses so useOn (stripping/tilling/path) works —
	// in 26.2 these subclasses still exist and carry that behavior; PickaxeItem/SwordItem were
	// removed, so pickaxe/sword stay plain Item with .pickaxe()/.sword().
	public static final Item TEMPERED_IRON_AXE =
			temperedIronSubclass("tempered_iron_axe", p -> new AxeItem(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.AXE.attackDamage(), TemperedIronToolStats.AXE.attackSpeed(), p));
	public static final Item TEMPERED_IRON_HOE =
			temperedIronSubclass("tempered_iron_hoe", p -> new HoeItem(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.HOE.attackDamage(), TemperedIronToolStats.HOE.attackSpeed(), p));
	public static final Item TEMPERED_IRON_SHOVEL =
			temperedIronSubclass("tempered_iron_shovel", p -> new ShovelItem(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.SHOVEL.attackDamage(), TemperedIronToolStats.SHOVEL.attackSpeed(), p));
	public static final Item TEMPERED_IRON_SWORD =
			temperedIronTool("tempered_iron_sword", p -> p.sword(ModToolMaterials.TEMPERED_IRON, TemperedIronToolStats.SWORD.attackDamage(), TemperedIronToolStats.SWORD.attackSpeed()));
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
	// Fluxweave armour (MOD-127): humanoidArmor gives it ordinary armour stats; FluxweaveArmorItem
	// layers the charge-driven worn asset and bonus attributes on top of that.
	public static final Item FLUXWEAVE_HELMET =
			fluxweaveArmor("fluxweave_helmet", ArmorType.HELMET);
	public static final Item FLUXWEAVE_CHESTPLATE =
			fluxweaveArmor("fluxweave_chestplate", ArmorType.CHESTPLATE);
	public static final Item FLUXWEAVE_LEGGINGS =
			fluxweaveArmor("fluxweave_leggings", ArmorType.LEGGINGS);
	public static final Item FLUXWEAVE_BOOTS =
			fluxweaveArmor("fluxweave_boots", ArmorType.BOOTS);
	public static final Item IRON_DUST = manifestItem("iron_dust");
	public static final Item COPPER_DUST = manifestItem("copper_dust");
	public static final Item GOLD_DUST = manifestItem("gold_dust");
	public static final Item COAL_DUST = manifestItem("coal_dust");
	public static final Item DIAMOND_DUST = manifestItem("diamond_dust");
	public static final Item EMERALD_DUST = manifestItem("emerald_dust");
	public static final Item LAPIS_DUST = manifestItem("lapis_dust");
	public static final Item TIN_DUST = manifestItem("tin_dust");
	public static final Item RAW_TIN = manifestItem("raw_tin");
	public static final Item TIN_INGOT = manifestItem("tin_ingot");
	public static final Item SILVER_DUST = manifestItem("silver_dust");
	public static final Item RAW_SILVER = manifestItem("raw_silver");
	public static final Item SILVER_INGOT = manifestItem("silver_ingot");
	public static final Item NICKEL_DUST = manifestItem("nickel_dust");
	public static final Item RAW_NICKEL = manifestItem("raw_nickel");
	public static final Item NICKEL_INGOT = manifestItem("nickel_ingot");
	// MOD-064 alloys.
	public static final Item BRONZE_INGOT = manifestItem("bronze_ingot");
	public static final Item INVAR_INGOT = manifestItem("invar_ingot");
	public static final Item CUPRONICKEL_INGOT = manifestItem("cupronickel_ingot");
	public static final Item ELECTRUM_INGOT = manifestItem("electrum_ingot");
	public static final Item SULFUR_DUST = manifestItem("sulfur_dust");
	public static final Item RAW_SULFUR = manifestItem("raw_sulfur");
	public static final Item URANIUM_DUST = manifestItem("uranium_dust");
	public static final Item RAW_URANIUM = manifestItem("raw_uranium");
	public static final Item URANIUM_INGOT = manifestItem("uranium_ingot");
	public static final Item NETWORK_ANALYZER = networkAnalyzer("network_analyzer");
	public static final Item WIND_GAUGE = windGauge("wind_gauge");
	public static final Item WRENCH = wrench("wrench");
	public static final Item GUIDE_BOOK = guideBook("guide_book");
	// Teleporter Remote (MOD-092): registered but kept out of the creative tab + no recipe until
	// MOD-093 finishes the feature (same treatment as the station — see CreativeTabContent).
	public static final Item TELEPORTER_REMOTE = teleporterRemote("teleporter_remote");
	public static final Item BATTERY_POUCH = pouch("battery_pouch");
	// Energy Pack (MOD-065): worn LV buffer + the inert battery cell it is crafted from.
	public static final Item BATTERY = manifestItem("battery");
	public static final Item ENERGY_PACK = energyPack("energy_pack");
	// Electric Drill (MOD-079): first powered hand tool — a diamond-tier pickaxe that runs on EU.
	public static final Item ELECTRIC_DRILL = electricDrill("electric_drill");
	// Diamond-Tipped Electric Drill (MOD-321): the drill's upgrade tier — faster, switchable Silk Touch.
	public static final Item ELECTRIC_DRILL_DIAMOND_TIP = electricDrillDiamondTip("electric_drill_diamond_tip");
	// Electric Chainsaw (MOD-337): the drill's wood-side counterpart — an EU axe for logs and leaves.
	public static final Item ELECTRIC_CHAINSAW = electricChainsaw("electric_chainsaw");
	// Diamond-Tipped Electric Chainsaw (MOD-374): the chainsaw's upgrade tier — faster, with a
	// switchable Silk Touch mode that drops leaves as blocks.
	public static final Item ELECTRIC_CHAINSAW_DIAMOND_TIP =
			electricChainsawDiamondTip("electric_chainsaw_diamond_tip");
	// Electric Shovel (MOD-338): the earth-side member of the same line — an EU shovel for loose ground.
	public static final Item ELECTRIC_SHOVEL = electricShovel("electric_shovel");
	// Electric Hoe (MOD-342): the farming member of the same line — an EU hoe that tills for free.
	public static final Item ELECTRIC_HOE = electricHoe("electric_hoe");
	// Electric Saber (MOD-149): the line's first weapon — EU per hit, plain sword when flat or off.
	public static final Item ELECTRIC_SABER = electricSaber("electric_saber");
	// Electromagnet (MOD-132): EU item in any inventory slot that pulls loose drops toward the carrier.
	public static final Item ELECTROMAGNET = magnet("electromagnet");
	// Jetpack (MOD-148): worn EU flight — thrust on held jump, powerless glide when drained.
	public static final Item JETPACK = jetpack("jetpack");
	// Vacuum Capsule (MOD-063): empty (×64) + filled (×16, fluid in the capsule_fluid component).
	public static final Item VACUUM_CAPSULE = vacuumCapsule("vacuum_capsule");
	public static final Item FILLED_VACUUM_CAPSULE = filledCapsule("filled_vacuum_capsule");
	// Stock Display Frame (MOD-066). Referencing ModEntities here also triggers its eager class-init
	// registration, so the EntityType exists before the item is constructed.
	public static final Item STOCK_DISPLAY_FRAME = stockDisplayFrame("stock_display_frame");
	// Scythe (MOD-068): six material tiers, each an AOE foliage clearer. Registered like a hoe
	// (.hoe(material, attackDamage, -1.0f) attaches the tool component + enchantability) but as
	// ScytheItem, not HoeItem — the scythe must not till dirt on right-click, it clears its area
	// instead. The eight tiers (material + AOE profile + attack bias) are declared once in the
	// loader-neutral dev.alaindustrial.item.tool.ScytheTiers — both loaders register from the same list,
	// so a balance tweak cannot drift between Fabric and NeoForge.
	public static final Item SCYTHE_WOOD = scythe(ScytheTiers.WOOD);
	public static final Item SCYTHE_STONE = scythe(ScytheTiers.STONE);
	public static final Item SCYTHE_COPPER = scythe(ScytheTiers.COPPER);
	public static final Item SCYTHE_IRON = scythe(ScytheTiers.IRON);
	public static final Item SCYTHE_GOLD = scythe(ScytheTiers.GOLD);
	public static final Item SCYTHE_TEMPERED_IRON = scythe(ScytheTiers.TEMPERED_IRON);
	public static final Item SCYTHE_DIAMOND = scythe(ScytheTiers.DIAMOND);
	public static final Item SCYTHE_NETHERITE = scythe(ScytheTiers.NETHERITE);
	// Metal plates (MOD-078): plain ingredient items, ingot form. Made by the Forge Hammer (by hand)
	// or the Compressor; recycled back to dust by the Macerator (except tempered_iron — no dust).
	public static final Item COPPER_PLATE = manifestItem("copper_plate");
	public static final Item GOLD_PLATE = manifestItem("gold_plate");
	public static final Item IRON_PLATE = manifestItem("iron_plate");
	public static final Item TIN_PLATE = manifestItem("tin_plate");
	public static final Item SILVER_PLATE = manifestItem("silver_plate");
	public static final Item NICKEL_PLATE = manifestItem("nickel_plate");
	public static final Item URANIUM_PLATE = manifestItem("uranium_plate");
	public static final Item TEMPERED_IRON_PLATE = manifestItem("tempered_iron_plate");
	// Forge Hammer (MOD-078): pre-machine hand tool — ingot + hammer on the grid → plate; the hammer
	// stays and loses 1 durability per plate via the Fabric craft-remainder hook (HammerItemFabric).
	public static final Item FORGE_HAMMER = forgeHammer("forge_hammer");
	// Oil Bucket (MOD-238): the vanilla WATER_BUCKET pattern — BucketItem(fluid, props with
	// craftRemainder(BUCKET).stacksTo(1)). Referencing ModFluids.OIL keeps fluid-before-item order.
	public static final Item OIL_BUCKET = oilBucket("oil_bucket");

	// Block items.
	public static final BlockItem GENERATOR_ITEM = blockItem("generator", ModBlocks.GENERATOR);
	public static final BlockItem GEOTHERMAL_GENERATOR_ITEM = blockItem("geothermal_generator", ModBlocks.GEOTHERMAL_GENERATOR);
	public static final BlockItem SOLAR_PANEL_ITEM = blockItem("solar_panel", ModBlocks.SOLAR_PANEL);
	public static final BlockItem MOONLIT_SOLAR_PANEL_ITEM = blockItem("moonlit_solar_panel", ModBlocks.MOONLIT_SOLAR_PANEL);
	public static final BlockItem DAYLIGHT_SOLAR_PANEL_ITEM = blockItem("daylight_solar_panel", ModBlocks.DAYLIGHT_SOLAR_PANEL);
	public static final BlockItem COPPER_CABLE_ITEM = blockItem("copper_cable", ModBlocks.COPPER_CABLE);
	public static final BlockItem TIN_CABLE_ITEM = blockItem("tin_cable", ModBlocks.TIN_CABLE);
	public static final BlockItem GOLD_CABLE_ITEM = blockItem("gold_cable", ModBlocks.GOLD_CABLE);
	public static final BlockItem ELECTRUM_CABLE_ITEM = blockItem("electrum_cable", ModBlocks.ELECTRUM_CABLE);
	public static final BlockItem INSULATED_COPPER_CABLE_ITEM = blockItem("insulated_copper_cable", ModBlocks.INSULATED_COPPER_CABLE);
	public static final BlockItem INSULATED_TIN_CABLE_ITEM = blockItem("insulated_tin_cable", ModBlocks.INSULATED_TIN_CABLE);
	public static final BlockItem INSULATED_GOLD_CABLE_ITEM = blockItem("insulated_gold_cable", ModBlocks.INSULATED_GOLD_CABLE);
	public static final BlockItem INSULATED_ELECTRUM_CABLE_ITEM = blockItem("insulated_electrum_cable", ModBlocks.INSULATED_ELECTRUM_CABLE);
	// MOD-108: its own BlockItem subclass so the pipe can carry a tooltip (plain hint + Shift for the
	// throughput numbers) — a plain blockItem() has none.
	public static final BlockItem ITEM_PIPE_ITEM = pipeItem("item_pipe", ModBlocks.ITEM_PIPE);
	public static final BlockItem FLUID_PIPE_ITEM = fluidPipeItem("fluid_pipe", ModBlocks.FLUID_PIPE);
	public static final BlockItem MACERATOR_ITEM = blockItem("macerator", ModBlocks.MACERATOR);
	public static final BlockItem BATTERY_BOX_ITEM = blockItem("battery_box", ModBlocks.BATTERY_BOX);
	public static final BlockItem CESU_ITEM = blockItem("cesu", ModBlocks.CESU);
	public static final BlockItem TELEPORTER_ITEM = blockItem("teleporter", ModBlocks.TELEPORTER);
	public static final BlockItem ELECTRIC_FURNACE_ITEM = blockItem("electric_furnace", ModBlocks.ELECTRIC_FURNACE);
	public static final BlockItem EXTRACTOR_ITEM = blockItem("extractor", ModBlocks.EXTRACTOR);
	public static final BlockItem COMPRESSOR_ITEM = blockItem("compressor", ModBlocks.COMPRESSOR);
	public static final BlockItem SAWMILL_ITEM = blockItem("sawmill", ModBlocks.SAWMILL);
	public static final BlockItem ASSEMBLER_ITEM = blockItem("assembler", ModBlocks.ASSEMBLER);
	public static final BlockItem POLYMERIZER_ITEM = blockItem("polymerizer", ModBlocks.POLYMERIZER);
	public static final BlockItem VULCANIZER_ITEM = blockItem("vulcanizer", ModBlocks.VULCANIZER);
	public static final BlockItem ALLOY_SMELTER_ITEM =
			blockItem("alloy_smelter", ModBlocks.ALLOY_SMELTER);
	public static final BlockItem GALVANIC_BATH_ITEM =
			blockItem("galvanic_bath", ModBlocks.GALVANIC_BATH);
	public static final BlockItem ELECTRIC_HEATER_ITEM = blockItem("electric_heater", ModBlocks.ELECTRIC_HEATER);
	public static final BlockItem CHARGE_PAD_ITEM = blockItem("charge_pad", ModBlocks.CHARGE_PAD);
	public static final BlockItem INCUBATOR_ITEM = blockItem("incubator", ModBlocks.INCUBATOR);
	public static final BlockItem TRELLIS_ITEM = blockItem("trellis", ModBlocks.TRELLIS);
	public static final BlockItem PUMP_ITEM = blockItem("pump", ModBlocks.PUMP);
	public static final BlockItem GARDEN_DRONE_STATION_ITEM =
			blockItem("garden_drone_station", ModBlocks.GARDEN_DRONE_STATION);
	public static final Item GARDEN_DRONE = manifestItem("garden_drone");
	public static final BlockItem FLUID_TANK_ITEM = fluidTankBlockItem("fluid_tank", ModBlocks.FLUID_TANK);
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
	public static final BlockItem SULFUR_ORE_ITEM = blockItem("sulfur_ore", ModBlocks.SULFUR_ORE);
	public static final BlockItem DEEPSLATE_SULFUR_ORE_ITEM = blockItem("deepslate_sulfur_ore", ModBlocks.DEEPSLATE_SULFUR_ORE);
	public static final BlockItem URANIUM_ORE_ITEM = blockItem("uranium_ore", ModBlocks.URANIUM_ORE);
	public static final BlockItem DEEPSLATE_URANIUM_ORE_ITEM = blockItem("deepslate_uranium_ore", ModBlocks.DEEPSLATE_URANIUM_ORE);
	public static final BlockItem IRON_CHEST_ITEM = blockItem("iron_chest", ModBlocks.IRON_CHEST);
	public static final BlockItem STORAGE_MODULE_ITEM =
			blockItem("storage_module", ModBlocks.STORAGE_MODULE);
	public static final BlockItem IRON_FURNACE_ITEM = blockItem("iron_furnace", ModBlocks.IRON_FURNACE);
	public static final BlockItem SILVER_CHEST_ITEM = blockItem("silver_chest", ModBlocks.SILVER_CHEST);
	public static final BlockItem GOLD_CHEST_ITEM = blockItem("gold_chest", ModBlocks.GOLD_CHEST);
	public static final BlockItem TEMPERED_IRON_BLOCK_ITEM = blockItem("tempered_iron_block", ModBlocks.TEMPERED_IRON_BLOCK);
	// MOD-225 block-items.
	public static final BlockItem MACHINE_CASING_ITEM = blockItem("machine_casing", ModBlocks.MACHINE_CASING);
	public static final BlockItem ADVANCED_MACHINE_CASING_ITEM =
			blockItem("advanced_machine_casing", ModBlocks.ADVANCED_MACHINE_CASING);
	public static final BlockItem SILVER_PLATE_BLOCK_ITEM = blockItem("silver_plate_block", ModBlocks.SILVER_PLATE_BLOCK);
	public static final BlockItem TEMPERED_IRON_PLATE_BLOCK_ITEM = blockItem("tempered_iron_plate_block", ModBlocks.TEMPERED_IRON_PLATE_BLOCK);
	public static final BlockItem INDUSTRIAL_WORKBENCH_ITEM = blockItem("industrial_workbench", ModBlocks.INDUSTRIAL_WORKBENCH);
	// Enriched Uranium Torch (MOD-085): a StandingAndWallBlockItem (like vanilla Items.TORCH) so using it
	// on a wall places the wall variant and on the floor the standing variant. The wall block has no item
	// of its own — this item maps to both blocks (StandingAndWallBlockItem#registerBlocks).
	public static final BlockItem ENRICHED_URANIUM_TORCH_ITEM = standingAndWallBlockItem(
			"enriched_uranium_torch", ModBlocks.ENRICHED_URANIUM_TORCH, ModBlocks.ENRICHED_URANIUM_WALL_TORCH);

	/** MOD-275 — needs its own class for the two-state stack size and tooltip. */
	private static Item assemblyBlueprint(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new AssemblyBlueprintItem(
				new Item.Properties().setId(key)
						.stacksTo(AssemblyBlueprintItem.BLANK_STACK_SIZE)));
	}

	/**
	 * Registers an item whose CONSTRUCTION is declared once for both loaders in
	 * {@link ContentManifest#ITEM_FACTORIES} (MOD-306) — plain components, hint items, mode chips,
	 * wearing parts. Before this, each of them was written out twice: here and in
	 * {@code ModItemsNeoForge}, same id and same comment, two registration syntaxes around an identical
	 * construction, with only a Python validator running after the fact to notice a drift.
	 *
	 * <p>The field stays (it is the handle 100+ call sites use); what moved out is the definition.
	 * Fabric registers eagerly, so it stamps the id itself and hands the manifest factory a
	 * {@code Properties} that already carries the key — the NeoForge side gets the same factory with a
	 * {@code Properties} whose id its {@code DeferredRegister} derived.
	 */
	// NB: the parameter is called `path` on purpose. The dashboard generator derives the names of the
	// registering helpers from the structural invariant `private static … name(String path…)`; calling
	// it `id` drops every item registered through this helper out of the generated catalogue silently
	// — the only symptom is a stale-artifact check going red, with no hint as to why.
	private static Item manifestItem(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				ContentManifest.itemFactory(path).apply(new Item.Properties().setId(key)));
	}

	private static Item teleporterRemote(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new TeleporterRemoteItem(new Item.Properties().setId(key).stacksTo(1)));
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

	/** Wind Gauge (MOD-347) — a read-only hand instrument, so a single unstacked tool like the rest. */
	private static Item windGauge(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.tool.WindGaugeItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	private static Item wrench(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.tool.WrenchItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	private static Item guideBook(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.misc.GuideBookItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	// Forge Hammer (MOD-078). durability(128) sets max_damage (non-stackable, standard durability bar);
	// repairable(IRON_INGOT) allows anvil repair with iron. Deliberately NOT enchantable and NOT in any
	// tool tag — Unbreaking cannot work through the craft-remainder hook (no Level/Player there), so the
	// hammer is honestly non-enchantable rather than carrying a no-op enchant. See MOD-078 task log.
	private static Item forgeHammer(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.tool.HammerItemFabric(new Item.Properties().setId(key).durability(128).repairable(Items.IRON_INGOT)));
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

	// Diamond-Tipped Electric Drill (MOD-321). Same shape as the base drill above, with its own
	// properties factory (higher mining speed); the Silk Touch mode is per-stack state, not a property.
	private static Item electricDrillDiamondTip(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new ElectricDrillDiamondTipItem(
						ElectricDrillDiamondTipItem.electricDrillDiamondTipProperties(new Item.Properties().setId(key))));
	}

	// Electric Chainsaw (MOD-337). Same shape as the drill above — a plain-Item subclass whose
	// properties come from a common static factory (hand-built TOOL component + EU-item bar, no
	// MAX_DAMAGE; see ElectricChainsawItem.electricChainsawProperties for why it is not an AxeItem).
	private static Item electricChainsaw(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new ElectricChainsawItem(
						ElectricChainsawItem.electricChainsawProperties(new Item.Properties().setId(key))));
	}

	// Diamond-Tipped Electric Chainsaw (MOD-374). Same shape as the base chainsaw above, with its own
	// properties factory (higher cutting speed); the Silk Touch mode is per-stack state, not a property.
	private static Item electricChainsawDiamondTip(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new ElectricChainsawDiamondTipItem(
						ElectricChainsawDiamondTipItem.electricChainsawDiamondTipProperties(
								new Item.Properties().setId(key))));
	}

	// Electric Shovel (MOD-338). Same shape as the two above — a plain-Item subclass whose properties
	// come from a common static factory (hand-built TOOL component + EU-item bar, no MAX_DAMAGE; see
	// ElectricShovelItem.electricShovelProperties for why it is not a ShovelItem).
	private static Item electricShovel(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new ElectricShovelItem(
						ElectricShovelItem.electricShovelProperties(new Item.Properties().setId(key))));
	}

	// Electric Hoe (MOD-342). Same shape as the three above — a plain-Item subclass whose properties
	// come from a common static factory (hand-built TOOL component + EU-item bar, no MAX_DAMAGE; see
	// ElectricHoeItem.electricHoeProperties for why it is not a HoeItem).
	// Electric Saber (MOD-149). Same shape as the four tools above — a plain-Item subclass whose
	// properties come from a common static factory (hand-built TOOL + WEAPON components, no MAX_DAMAGE;
	// see ElectricSaberItem.electricSaberProperties for why it is not built with Properties.sword).
	private static Item electricSaber(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new ElectricSaberItem(
						ElectricSaberItem.electricSaberProperties(new Item.Properties().setId(key))));
	}

	private static Item electricHoe(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new ElectricHoeItem(
						ElectricHoeItem.electricHoeProperties(new Item.Properties().setId(key))));
	}

	// Electromagnet (MOD-132). A plain-Item subclass; stacksTo(1) because it carries per-item state
	// (EU + on/off). No pouch_energy default — ItemEnergy reads an absent component as 0 EU.
	private static Item magnet(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new MagnetItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	// Jetpack (MOD-148). Equipment properties come from the common helper (EQUIPPABLE + 5 armor points
	// via attribute, no ArmorMaterial — see JetpackItem.equipmentProperties); like the pack, no
	// pouch_energy default is preset, ItemEnergy reads an absent component as 0 EU.
	private static Item jetpack(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new JetpackItem(JetpackItem.equipmentProperties(new Item.Properties().setId(key))));
	}

	// Vacuum Capsule (MOD-063). Empty capsule stacks to the vanilla default (64); no fluid component
	// default — ItemFluid treats an absent capsule_fluid as empty.
	private static Item vacuumCapsule(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.fluid.VacuumCapsuleItem(new Item.Properties().setId(key)));
	}

	// Filled capsule: one bucket of a single fluid, stacking to FilledCapsuleItem.STACK_SIZE (16).
	// MOD-077: craftRemainder = the empty capsule, so a lava capsule burnt in a vanilla furnace returns an
	// empty capsule (the fuel remainder), exactly as a lava bucket returns an empty bucket. VACUUM_CAPSULE is
	// initialised just above (field order), so it is already registered here.
	private static Item filledCapsule(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new dev.alaindustrial.item.fluid.FilledCapsuleItem(
						new Item.Properties().setId(key).stacksTo(dev.alaindustrial.item.fluid.FilledCapsuleItem.STACK_SIZE)
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
	// Fluxweave armour helper (MOD-127). Same shape as temperedArmor, but the concrete type is
	// FluxweaveArmorItem so the piece can carry its ArmorType and rewrite its own components as EU changes.
	private static Item fluxweaveArmor(String path, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new FluxweaveArmorItem(
				FluxweaveArmorItem.equipmentProperties(new Item.Properties().setId(key), type), type));
	}

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
	private static Item scythe(dev.alaindustrial.item.tool.ScytheTier tier) {
		return scythe(tier.id(), tier.material(), tier.attackDamage(), tier.profile(), tier.fireResistant());
	}

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
				new dev.alaindustrial.item.misc.StockDisplayFrameItem(ModEntities.STOCK_DISPLAY_FRAME,
						new Item.Properties().setId(key)));
	}

	/** The oil bucket (MOD-238) — built exactly like vanilla {@code Items.WATER_BUCKET}. */
	private static Item oilBucket(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new net.minecraft.world.item.BucketItem(ModFluids.OIL,
						new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1).setId(key)));
	}

	private static BlockItem blockItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** {@link #blockItem} for the item pipe, whose block item carries a tooltip (MOD-108). */
	private static BlockItem pipeItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		BlockItem item = new dev.alaindustrial.item.misc.ItemPipeBlockItem(block,
				new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** {@link #pipeItem} for the fluid pipe, whose tooltip quotes mB/tick rather than items (MOD-151). */
	private static BlockItem fluidPipeItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		BlockItem item = new dev.alaindustrial.item.misc.FluidPipeBlockItem(block,
				new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static BlockItem fluidTankBlockItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		BlockItem item = new FluidTankBlockItem(block,
				new Item.Properties().useBlockDescriptionPrefix().setId(key));
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
				.displayItems((params, output) ->
						// Single source of truth shared with NeoForge (CreativeTabContent) so the mod tab
						// is identical on both loaders. Hidden pre-release content (non-copper cables,
						// water/high-altitude mills) is omitted there, so it stays hidden here too — MOD-102.
						CreativeTabContent.main(output::accept))
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
					// The Jetpack is chest gear too (MOD-148) — it sits right after the pack here.
					output.insertAfter(ModContent.ENERGY_PACK.get(), ModContent.JETPACK.get());
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
					output.accept(ModContent.WRENCH.get());
					output.accept(ModContent.BATTERY_POUCH.get());
					output.accept(ModContent.ENERGY_PACK.get());
					output.accept(ModContent.ELECTRIC_DRILL.get());
					output.accept(ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get());
					output.accept(ModContent.ELECTRIC_CHAINSAW.get());
					output.accept(ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get());
					output.accept(ModContent.ELECTRIC_SHOVEL.get());
					output.accept(ModContent.ELECTRIC_HOE.get());
					output.accept(ModContent.ELECTROMAGNET.get());
					output.accept(ModContent.JETPACK.get());
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
		ModContent.ADVANCED_CIRCUIT = () -> ADVANCED_CIRCUIT;
		ModContent.ASSEMBLY_BLUEPRINT = () -> ASSEMBLY_BLUEPRINT;
		ModContent.COPPER_COIL = () -> COPPER_COIL;
		ModContent.ALIGNMENT_CHIP_DAY = () -> ALIGNMENT_CHIP_DAY;
		ModContent.ALIGNMENT_CHIP_NIGHT = () -> ALIGNMENT_CHIP_NIGHT;
		ModContent.EMPTY_CHIP = () -> EMPTY_CHIP;
		ModContent.MUTE_CHIP = () -> MUTE_CHIP;
		ModContent.CABLE_BREAKER = () -> CABLE_BREAKER;
		ModContent.WINDMILL_ROTOR = () -> WINDMILL_ROTOR;
		ModContent.WATER_MILL_WHEEL = () -> WATER_MILL_WHEEL;
		ModContent.WOODEN_GEAR = () -> WOODEN_GEAR;
		ModContent.STONE_GEAR = () -> STONE_GEAR;
		ModContent.IRON_GEAR = () -> IRON_GEAR;
		ModContent.GOLD_GEAR = () -> GOLD_GEAR;
		ModContent.SILVER_GEAR = () -> SILVER_GEAR;
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
		ModContent.BRONZE_INGOT = () -> BRONZE_INGOT;
		ModContent.INVAR_INGOT = () -> INVAR_INGOT;
		ModContent.CUPRONICKEL_INGOT = () -> CUPRONICKEL_INGOT;
		ModContent.ELECTRUM_INGOT = () -> ELECTRUM_INGOT;
		ModContent.SULFUR_DUST = () -> SULFUR_DUST;
		ModContent.RAW_SULFUR = () -> RAW_SULFUR;
		ModContent.URANIUM_DUST = () -> URANIUM_DUST;
		ModContent.RAW_URANIUM = () -> RAW_URANIUM;
		ModContent.URANIUM_INGOT = () -> URANIUM_INGOT;
		ModContent.NETWORK_ANALYZER = () -> NETWORK_ANALYZER;
		ModContent.WIND_GAUGE = () -> WIND_GAUGE;
		ModContent.GUIDE_BOOK = () -> GUIDE_BOOK;
		ModContent.WRENCH = () -> WRENCH;
		ModContent.TELEPORTER_REMOTE = () -> TELEPORTER_REMOTE;
		ModContent.BATTERY_POUCH = () -> BATTERY_POUCH;
		ModContent.BATTERY = () -> BATTERY;
		ModContent.ENERGY_PACK = () -> ENERGY_PACK;
		ModContent.ELECTRIC_DRILL = () -> ELECTRIC_DRILL;
		ModContent.ELECTRIC_DRILL_DIAMOND_TIP = () -> ELECTRIC_DRILL_DIAMOND_TIP;
		ModContent.ELECTRIC_CHAINSAW = () -> ELECTRIC_CHAINSAW;
		ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP = () -> ELECTRIC_CHAINSAW_DIAMOND_TIP;
		ModContent.ELECTRIC_SHOVEL = () -> ELECTRIC_SHOVEL;
		ModContent.ELECTRIC_HOE = () -> ELECTRIC_HOE;
		ModContent.ELECTRIC_SABER = () -> ELECTRIC_SABER;
		ModContent.ELECTROMAGNET = () -> ELECTROMAGNET;
		ModContent.JETPACK = () -> JETPACK;
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
		ModContent.COPPER_PLATE = () -> COPPER_PLATE;
		ModContent.GOLD_PLATE = () -> GOLD_PLATE;
		ModContent.IRON_PLATE = () -> IRON_PLATE;
		ModContent.TIN_PLATE = () -> TIN_PLATE;
		ModContent.SILVER_PLATE = () -> SILVER_PLATE;
		ModContent.NICKEL_PLATE = () -> NICKEL_PLATE;
		ModContent.URANIUM_PLATE = () -> URANIUM_PLATE;
		ModContent.TEMPERED_IRON_PLATE = () -> TEMPERED_IRON_PLATE;
		ModContent.FORGE_HAMMER = () -> FORGE_HAMMER;
		ModContent.OIL_BUCKET = () -> OIL_BUCKET;

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
		ModContent.GOLD_CABLE_ITEM = () -> GOLD_CABLE_ITEM;
		ModContent.ELECTRUM_CABLE_ITEM = () -> ELECTRUM_CABLE_ITEM;
		ModContent.INSULATED_COPPER_CABLE_ITEM = () -> INSULATED_COPPER_CABLE_ITEM;
		ModContent.INSULATED_TIN_CABLE_ITEM = () -> INSULATED_TIN_CABLE_ITEM;
		ModContent.INSULATED_GOLD_CABLE_ITEM = () -> INSULATED_GOLD_CABLE_ITEM;
		ModContent.INSULATED_ELECTRUM_CABLE_ITEM = () -> INSULATED_ELECTRUM_CABLE_ITEM;
		ModContent.ITEM_PIPE_ITEM = () -> ITEM_PIPE_ITEM;
		ModContent.FLUID_PIPE_ITEM = () -> FLUID_PIPE_ITEM;
		ModContent.MACERATOR_ITEM = () -> MACERATOR_ITEM;
		ModContent.BATTERY_BOX_ITEM = () -> BATTERY_BOX_ITEM;
		ModContent.CESU_ITEM = () -> CESU_ITEM;
		ModContent.TELEPORTER_ITEM = () -> TELEPORTER_ITEM;
		ModContent.ELECTRIC_FURNACE_ITEM = () -> ELECTRIC_FURNACE_ITEM;
		ModContent.EXTRACTOR_ITEM = () -> EXTRACTOR_ITEM;
		ModContent.COMPRESSOR_ITEM = () -> COMPRESSOR_ITEM;
		ModContent.SAWMILL_ITEM = () -> SAWMILL_ITEM;
		ModContent.ASSEMBLER_ITEM = () -> ASSEMBLER_ITEM;
		ModContent.POLYMERIZER_ITEM = () -> POLYMERIZER_ITEM;
		ModContent.VULCANIZER_ITEM = () -> VULCANIZER_ITEM;
		ModContent.ALLOY_SMELTER_ITEM = () -> ALLOY_SMELTER_ITEM;
		ModContent.GALVANIC_BATH_ITEM = () -> GALVANIC_BATH_ITEM;
		ModContent.ELECTRIC_HEATER_ITEM = () -> ELECTRIC_HEATER_ITEM;
		ModContent.CHARGE_PAD_ITEM = () -> CHARGE_PAD_ITEM;
		ModContent.INCUBATOR_ITEM = () -> INCUBATOR_ITEM;
		ModContent.TRELLIS_ITEM = () -> TRELLIS_ITEM;
		ModContent.MUTATION_CHIP_TRANSFORM = () -> MUTATION_CHIP_TRANSFORM;
		ModContent.MUTATION_CHIP_DUPLICATE = () -> MUTATION_CHIP_DUPLICATE;
		ModContent.MUTATION_CHIP_CREATE = () -> MUTATION_CHIP_CREATE;
		ModContent.DEPLETED_URANIUM = () -> DEPLETED_URANIUM;
		ModContent.IRRADIATED_SLAG = () -> IRRADIATED_SLAG;
		ModContent.IRRADIATED_DIAMOND = () -> IRRADIATED_DIAMOND;
		ModContent.RESONANT_SHARD = () -> RESONANT_SHARD;
		ModContent.MUTAGEN_DUST = () -> MUTAGEN_DUST;
		ModContent.RAW_RUBBER = () -> RAW_RUBBER;
		ModContent.COTTON_SEEDS = () -> COTTON_SEEDS;
		ModContent.COTTON_FIBER = () -> COTTON_FIBER;
		ModContent.FLUX_THREAD = () -> FLUX_THREAD;
		ModContent.FLUXWEAVE_CLOTH = () -> FLUXWEAVE_CLOTH;
		ModContent.FLUXWEAVE_HELMET = () -> FLUXWEAVE_HELMET;
		ModContent.FLUXWEAVE_CHESTPLATE = () -> FLUXWEAVE_CHESTPLATE;
		ModContent.FLUXWEAVE_LEGGINGS = () -> FLUXWEAVE_LEGGINGS;
		ModContent.FLUXWEAVE_BOOTS = () -> FLUXWEAVE_BOOTS;
		ModContent.RUBBER = () -> RUBBER;
		ModContent.UNSTABLE_ISOTOPE = () -> UNSTABLE_ISOTOPE;
		ModContent.PUMP_ITEM = () -> PUMP_ITEM;
		ModContent.GARDEN_DRONE_STATION_ITEM = () -> GARDEN_DRONE_STATION_ITEM;
		ModContent.GARDEN_DRONE = () -> GARDEN_DRONE;
		ModContent.FLUID_TANK_ITEM = () -> FLUID_TANK_ITEM;
		ModContent.TIN_ORE_ITEM = () -> TIN_ORE_ITEM;
		ModContent.DEEPSLATE_TIN_ORE_ITEM = () -> DEEPSLATE_TIN_ORE_ITEM;
		ModContent.SILVER_ORE_ITEM = () -> SILVER_ORE_ITEM;
		ModContent.DEEPSLATE_SILVER_ORE_ITEM = () -> DEEPSLATE_SILVER_ORE_ITEM;
		ModContent.NICKEL_ORE_ITEM = () -> NICKEL_ORE_ITEM;
		ModContent.DEEPSLATE_NICKEL_ORE_ITEM = () -> DEEPSLATE_NICKEL_ORE_ITEM;
		ModContent.SULFUR_ORE_ITEM = () -> SULFUR_ORE_ITEM;
		ModContent.DEEPSLATE_SULFUR_ORE_ITEM = () -> DEEPSLATE_SULFUR_ORE_ITEM;
		ModContent.URANIUM_ORE_ITEM = () -> URANIUM_ORE_ITEM;
		ModContent.DEEPSLATE_URANIUM_ORE_ITEM = () -> DEEPSLATE_URANIUM_ORE_ITEM;
		ModContent.IRON_CHEST_ITEM = () -> IRON_CHEST_ITEM;
		ModContent.STORAGE_MODULE_ITEM = () -> STORAGE_MODULE_ITEM;
		ModContent.IRON_FURNACE_ITEM = () -> IRON_FURNACE_ITEM;
		ModContent.SILVER_CHEST_ITEM = () -> SILVER_CHEST_ITEM;
		ModContent.GOLD_CHEST_ITEM = () -> GOLD_CHEST_ITEM;
		ModContent.TEMPERED_IRON_BLOCK_ITEM = () -> TEMPERED_IRON_BLOCK_ITEM;
		ModContent.MACHINE_CASING_ITEM = () -> MACHINE_CASING_ITEM;
		ModContent.ADVANCED_MACHINE_CASING_ITEM = () -> ADVANCED_MACHINE_CASING_ITEM;
		ModContent.SILVER_PLATE_BLOCK_ITEM = () -> SILVER_PLATE_BLOCK_ITEM;
		ModContent.TEMPERED_IRON_PLATE_BLOCK_ITEM = () -> TEMPERED_IRON_PLATE_BLOCK_ITEM;
		ModContent.INDUSTRIAL_WORKBENCH_ITEM = () -> INDUSTRIAL_WORKBENCH_ITEM;
		ModContent.ENRICHED_URANIUM_TORCH_ITEM = () -> ENRICHED_URANIUM_TORCH_ITEM;
	}
}
