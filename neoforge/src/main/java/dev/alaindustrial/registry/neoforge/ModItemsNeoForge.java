package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.ModArmorMaterials;
import dev.alaindustrial.item.ModToolMaterials;
import dev.alaindustrial.item.NetworkAnalyzerItem;
import dev.alaindustrial.item.PouchItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
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
 * derive the id from the deferred key (verified neoforge-26.2.0.8-beta). {@code registerSimpleBlockItem}
 * also applies {@code useBlockDescriptionPrefix()}, matching the Fabric {@code blockItem(...)} helper.
 */
public final class ModItemsNeoForge {
	public static final DeferredRegister.Items ITEMS =
			DeferredRegister.createItems(Industrialization.MOD_ID);

	// --- Crafting components (plain items) ---
	public static final DeferredItem<Item> ELECTRONIC_CIRCUIT = ITEMS.registerItem("electronic_circuit", Item::new);
	public static final DeferredItem<Item> ALIGNMENT_CHIP_DAY = ITEMS.registerItem("alignment_chip_day", Item::new);
	public static final DeferredItem<Item> ALIGNMENT_CHIP_NIGHT = ITEMS.registerItem("alignment_chip_night", Item::new);
	public static final DeferredItem<Item> WINDMILL_ROTOR = ITEMS.registerItem("windmill_rotor", Item::new);
	public static final DeferredItem<Item> WOODEN_GEAR = ITEMS.registerItem("wooden_gear", Item::new);
	public static final DeferredItem<Item> TEMPERED_IRON = ITEMS.registerItem("tempered_iron", Item::new);
	// Tempered-iron pickaxe — first mod tool (MOD-054). MC 26.2 has no PickaxeItem class: a pickaxe is
	// a plain Item whose `minecraft:tool` component is attached via Item.Properties.pickaxe(...). The
	// third arg is a Properties-unary-op that applies the tempered-iron material (durability/speed/
	// damage/enchant). setId is applied automatically by NeoForge, matching the Fabric helper.
	public static final DeferredItem<Item> TEMPERED_IRON_PICKAXE =
			ITEMS.registerItem("tempered_iron_pickaxe", Item::new,
					p -> p.pickaxe(ModToolMaterials.TEMPERED_IRON, 1.0f, -2.8f));
	// Tempered-iron tool line (MOD-054): axe/hoe/shovel/sword. Pickaxe/sword are plain Item (their
	// 26.2 subclasses were removed). Axe/Hoe/Shovel extend their vanilla subclasses so useOn works
	// (log stripping / dirt tilling / grass path) — those ctors call props.{axe,hoe,shovel}() and
	// super() themselves, so the Properties supplier stays default (NeoForge applies setId). Args
	// mirror vanilla iron equivalents (javap-verified).
	public static final DeferredItem<Item> TEMPERED_IRON_AXE =
			ITEMS.registerItem("tempered_iron_axe",
					p -> new net.minecraft.world.item.AxeItem(ModToolMaterials.TEMPERED_IRON, 6.0f, -3.1f, p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_HOE =
			ITEMS.registerItem("tempered_iron_hoe",
					p -> new net.minecraft.world.item.HoeItem(ModToolMaterials.TEMPERED_IRON, -2.0f, -1.0f, p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_SHOVEL =
			ITEMS.registerItem("tempered_iron_shovel",
					p -> new net.minecraft.world.item.ShovelItem(ModToolMaterials.TEMPERED_IRON, 1.5f, -3.0f, p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_SWORD =
			ITEMS.registerItem("tempered_iron_sword", Item::new,
					p -> p.sword(ModToolMaterials.TEMPERED_IRON, 3.0f, -2.4f));
	// Tempered-iron armor line (MOD-056): helmet/chestplate/leggings/boots. MC 26.2 has no ArmorItem
	// class — each piece is a plain Item whose equipment properties are attached via the single
	// Item.Properties.humanoidArmor(ArmorMaterial, ArmorType) helper (javap-verified against the
	// 26.2 jar; it is how vanilla Items.IRON_HELMET is built). setId is applied automatically by
	// NeoForge, matching the Fabric helper.
	public static final DeferredItem<Item> TEMPERED_IRON_HELMET =
			ITEMS.registerItem("tempered_iron_helmet", Item::new,
					p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, ArmorType.HELMET));
	public static final DeferredItem<Item> TEMPERED_IRON_CHESTPLATE =
			ITEMS.registerItem("tempered_iron_chestplate", Item::new,
					p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, ArmorType.CHESTPLATE));
	public static final DeferredItem<Item> TEMPERED_IRON_LEGGINGS =
			ITEMS.registerItem("tempered_iron_leggings", Item::new,
					p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, ArmorType.LEGGINGS));
	public static final DeferredItem<Item> TEMPERED_IRON_BOOTS =
			ITEMS.registerItem("tempered_iron_boots", Item::new,
					p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, ArmorType.BOOTS));
	public static final DeferredItem<Item> IRON_DUST = ITEMS.registerItem("iron_dust", Item::new);
	public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerItem("copper_dust", Item::new);
	public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerItem("gold_dust", Item::new);
	public static final DeferredItem<Item> COAL_DUST = ITEMS.registerItem("coal_dust", Item::new);
	public static final DeferredItem<Item> DIAMOND_DUST = ITEMS.registerItem("diamond_dust", Item::new);
	public static final DeferredItem<Item> EMERALD_DUST = ITEMS.registerItem("emerald_dust", Item::new);
	public static final DeferredItem<Item> LAPIS_DUST = ITEMS.registerItem("lapis_dust", Item::new);
	public static final DeferredItem<Item> TIN_DUST = ITEMS.registerItem("tin_dust", Item::new);
	public static final DeferredItem<Item> RAW_TIN = ITEMS.registerItem("raw_tin", Item::new);
	public static final DeferredItem<Item> TIN_INGOT = ITEMS.registerItem("tin_ingot", Item::new);
	public static final DeferredItem<Item> SILVER_DUST = ITEMS.registerItem("silver_dust", Item::new);
	public static final DeferredItem<Item> RAW_SILVER = ITEMS.registerItem("raw_silver", Item::new);
	public static final DeferredItem<Item> SILVER_INGOT = ITEMS.registerItem("silver_ingot", Item::new);
	public static final DeferredItem<Item> NICKEL_DUST = ITEMS.registerItem("nickel_dust", Item::new);
	public static final DeferredItem<Item> RAW_NICKEL = ITEMS.registerItem("raw_nickel", Item::new);
	public static final DeferredItem<Item> NICKEL_INGOT = ITEMS.registerItem("nickel_ingot", Item::new);
	public static final DeferredItem<Item> URANIUM_DUST = ITEMS.registerItem("uranium_dust", Item::new);
	public static final DeferredItem<Item> RAW_URANIUM = ITEMS.registerItem("raw_uranium", Item::new);
	public static final DeferredItem<Item> URANIUM_INGOT = ITEMS.registerItem("uranium_ingot", Item::new);
	public static final DeferredItem<NetworkAnalyzerItem> NETWORK_ANALYZER =
			ITEMS.registerItem("network_analyzer", NetworkAnalyzerItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<PouchItem> BATTERY_POUCH =
			ITEMS.registerItem("battery_pouch", PouchItem::new, p -> p.stacksTo(1));

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
	public static final DeferredItem<BlockItem> ELECTRIC_FURNACE_ITEM =
			ITEMS.registerSimpleBlockItem("electric_furnace", ModBlocksNeoForge.ELECTRIC_FURNACE);
	public static final DeferredItem<BlockItem> EXTRACTOR_ITEM =
			ITEMS.registerSimpleBlockItem("extractor", ModBlocksNeoForge.EXTRACTOR);
	public static final DeferredItem<BlockItem> COMPRESSOR_ITEM =
			ITEMS.registerSimpleBlockItem("compressor", ModBlocksNeoForge.COMPRESSOR);
	public static final DeferredItem<BlockItem> GEOTHERMAL_GENERATOR_ITEM =
			ITEMS.registerSimpleBlockItem("geothermal_generator", ModBlocksNeoForge.GEOTHERMAL_GENERATOR);
	public static final DeferredItem<BlockItem> PUMP_ITEM =
			ITEMS.registerSimpleBlockItem("pump", ModBlocksNeoForge.PUMP);
	public static final DeferredItem<BlockItem> WATER_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("water_mill", ModBlocksNeoForge.WATER_MILL);
	public static final DeferredItem<BlockItem> WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("wind_mill", ModBlocksNeoForge.WIND_MILL);
	public static final DeferredItem<BlockItem> HIGH_ALTITUDE_WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("high_altitude_wind_mill", ModBlocksNeoForge.HIGH_ALTITUDE_WIND_MILL);
	public static final DeferredItem<BlockItem> STORM_WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("storm_wind_mill", ModBlocksNeoForge.STORM_WIND_MILL);
	public static final DeferredItem<BlockItem> COPPER_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("copper_cable", ModBlocksNeoForge.COPPER_CABLE);
	public static final DeferredItem<BlockItem> TIN_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("tin_cable", ModBlocksNeoForge.TIN_CABLE);
	public static final DeferredItem<BlockItem> INSULATED_COPPER_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("insulated_copper_cable", ModBlocksNeoForge.INSULATED_COPPER_CABLE);
	public static final DeferredItem<BlockItem> INSULATED_TIN_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("insulated_tin_cable", ModBlocksNeoForge.INSULATED_TIN_CABLE);
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
	public static final DeferredItem<BlockItem> URANIUM_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("uranium_ore", ModBlocksNeoForge.URANIUM_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_uranium_ore", ModBlocksNeoForge.DEEPSLATE_URANIUM_ORE);
	public static final DeferredItem<BlockItem> IRON_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("iron_chest", ModBlocksNeoForge.IRON_CHEST);
	public static final DeferredItem<BlockItem> TEMPERED_IRON_BLOCK_ITEM =
			ITEMS.registerSimpleBlockItem("tempered_iron_block", ModBlocksNeoForge.TEMPERED_IRON_BLOCK);

	private ModItemsNeoForge() {
	}

	/**
	 * Bind each item / block-item {@code DeferredItem} into the loader-neutral {@link ModContent} facade,
	 * mirroring {@code dev.alaindustrial.registry.ModItems#init()} on the Fabric side. A {@code DeferredItem}
	 * <b>is</b> a {@code Supplier} ({@code DeferredItem<T> extends DeferredHolder<Item, T> implements
	 * Supplier<T>}, verified against neoforge-26.2.0.8-beta), so it is assigned directly and resolves lazily
	 * after this register's {@code RegisterEvent} fires. Called from the {@code @Mod} constructor after
	 * {@code ITEMS.register(modBus)}.
	 */
	public static void init() {
		ModContent.ELECTRONIC_CIRCUIT = ELECTRONIC_CIRCUIT;
		ModContent.ALIGNMENT_CHIP_DAY = ALIGNMENT_CHIP_DAY;
		ModContent.ALIGNMENT_CHIP_NIGHT = ALIGNMENT_CHIP_NIGHT;
		ModContent.WINDMILL_ROTOR = WINDMILL_ROTOR;
		ModContent.WOODEN_GEAR = WOODEN_GEAR;
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
		ModContent.URANIUM_DUST = URANIUM_DUST;
		ModContent.RAW_URANIUM = RAW_URANIUM;
		ModContent.URANIUM_INGOT = URANIUM_INGOT;
		// NETWORK_ANALYZER is a DeferredItem<NetworkAnalyzerItem>; the slot is Supplier<Item>. Generics are
		// invariant, so bind via the (still-lazy) method reference — see ModBlocksNeoForge#init javadoc.
		ModContent.NETWORK_ANALYZER = NETWORK_ANALYZER::get;
		// Same invariant-generics story as NETWORK_ANALYZER above.
		ModContent.BATTERY_POUCH = BATTERY_POUCH::get;

		ModContent.GENERATOR_ITEM = GENERATOR_ITEM;
		ModContent.SOLAR_PANEL_ITEM = SOLAR_PANEL_ITEM;
		ModContent.MOONLIT_SOLAR_PANEL_ITEM = MOONLIT_SOLAR_PANEL_ITEM;
		ModContent.DAYLIGHT_SOLAR_PANEL_ITEM = DAYLIGHT_SOLAR_PANEL_ITEM;
		ModContent.MACERATOR_ITEM = MACERATOR_ITEM;
		ModContent.BATTERY_BOX_ITEM = BATTERY_BOX_ITEM;
		ModContent.ELECTRIC_FURNACE_ITEM = ELECTRIC_FURNACE_ITEM;
		ModContent.EXTRACTOR_ITEM = EXTRACTOR_ITEM;
		ModContent.COMPRESSOR_ITEM = COMPRESSOR_ITEM;
		ModContent.GEOTHERMAL_GENERATOR_ITEM = GEOTHERMAL_GENERATOR_ITEM;
		ModContent.PUMP_ITEM = PUMP_ITEM;
		ModContent.WATER_MILL_ITEM = WATER_MILL_ITEM;
		ModContent.WIND_MILL_ITEM = WIND_MILL_ITEM;
		ModContent.HIGH_ALTITUDE_WIND_MILL_ITEM = HIGH_ALTITUDE_WIND_MILL_ITEM;
		ModContent.STORM_WIND_MILL_ITEM = STORM_WIND_MILL_ITEM;
		ModContent.COPPER_CABLE_ITEM = COPPER_CABLE_ITEM;
		ModContent.TIN_CABLE_ITEM = TIN_CABLE_ITEM;
		ModContent.INSULATED_COPPER_CABLE_ITEM = INSULATED_COPPER_CABLE_ITEM;
		ModContent.INSULATED_TIN_CABLE_ITEM = INSULATED_TIN_CABLE_ITEM;
		ModContent.TIN_ORE_ITEM = TIN_ORE_ITEM;
		ModContent.DEEPSLATE_TIN_ORE_ITEM = DEEPSLATE_TIN_ORE_ITEM;
		ModContent.SILVER_ORE_ITEM = SILVER_ORE_ITEM;
		ModContent.DEEPSLATE_SILVER_ORE_ITEM = DEEPSLATE_SILVER_ORE_ITEM;
		ModContent.NICKEL_ORE_ITEM = NICKEL_ORE_ITEM;
		ModContent.DEEPSLATE_NICKEL_ORE_ITEM = DEEPSLATE_NICKEL_ORE_ITEM;
		ModContent.URANIUM_ORE_ITEM = URANIUM_ORE_ITEM;
		ModContent.DEEPSLATE_URANIUM_ORE_ITEM = DEEPSLATE_URANIUM_ORE_ITEM;
		ModContent.IRON_CHEST_ITEM = IRON_CHEST_ITEM;
		ModContent.TEMPERED_IRON_BLOCK_ITEM = TEMPERED_IRON_BLOCK_ITEM;
	}
}
