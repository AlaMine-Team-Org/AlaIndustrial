package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.NetworkAnalyzerItem;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
	public static final Item ALIGNMENT_CHIP_DAY = item("alignment_chip_day");
	public static final Item ALIGNMENT_CHIP_NIGHT = item("alignment_chip_night");
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
	public static final BlockItem TIN_ORE_ITEM = blockItem("tin_ore", ModBlocks.TIN_ORE);
	public static final BlockItem DEEPSLATE_TIN_ORE_ITEM = blockItem("deepslate_tin_ore", ModBlocks.DEEPSLATE_TIN_ORE);
	public static final BlockItem SILVER_ORE_ITEM = blockItem("silver_ore", ModBlocks.SILVER_ORE);
	public static final BlockItem DEEPSLATE_SILVER_ORE_ITEM = blockItem("deepslate_silver_ore", ModBlocks.DEEPSLATE_SILVER_ORE);
	public static final BlockItem NICKEL_ORE_ITEM = blockItem("nickel_ore", ModBlocks.NICKEL_ORE);
	public static final BlockItem DEEPSLATE_NICKEL_ORE_ITEM = blockItem("deepslate_nickel_ore", ModBlocks.DEEPSLATE_NICKEL_ORE);
	public static final BlockItem URANIUM_ORE_ITEM = blockItem("uranium_ore", ModBlocks.URANIUM_ORE);
	public static final BlockItem DEEPSLATE_URANIUM_ORE_ITEM = blockItem("deepslate_uranium_ore", ModBlocks.DEEPSLATE_URANIUM_ORE);

	private static Item item(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	private static Item networkAnalyzer(String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new NetworkAnalyzerItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	private static BlockItem blockItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(path));
		BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.alaindustrial"))
				.icon(() -> new ItemStack(MACERATOR_ITEM))
				.displayItems((params, output) -> {
					// NB: pump + non-copper cables (tin, insulated copper/tin) are registered but
					// deliberately hidden for the v1.0 release (no tab entry, recipe removed). Re-add
					// their output.accept(...) + recipe JSON to bring them back. See task MOD-010.
					// Generators
					output.accept(SOLAR_PANEL_ITEM);
					output.accept(DAYLIGHT_SOLAR_PANEL_ITEM);
					output.accept(MOONLIT_SOLAR_PANEL_ITEM);
					output.accept(GENERATOR_ITEM);
					output.accept(GEOTHERMAL_GENERATOR_ITEM);
					output.accept(WATER_MILL_ITEM);
					output.accept(WIND_MILL_ITEM);
					// Machines
					output.accept(MACERATOR_ITEM);
					output.accept(ELECTRIC_FURNACE_ITEM);
					output.accept(EXTRACTOR_ITEM);
					output.accept(COMPRESSOR_ITEM);
					// Storage + cables
					output.accept(BATTERY_BOX_ITEM);
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
					output.accept(ALIGNMENT_CHIP_DAY);
					output.accept(ALIGNMENT_CHIP_NIGHT);
					output.accept(IRON_DUST);
					output.accept(COPPER_DUST);
					output.accept(GOLD_DUST);
					output.accept(COAL_DUST);
					output.accept(DIAMOND_DUST);
					output.accept(EMERALD_DUST);
					output.accept(LAPIS_DUST);
					output.accept(NETWORK_ANALYZER);
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, tab);

		bindModContent();
	}

	/**
	 * Publish each eagerly-registered item / block item into the loader-neutral {@link ModContent}
	 * facade so content classes (which read {@code ModContent.X.get()} at runtime) resolve the Fabric
	 * instance. Each handle wraps the already-registered value in a constant supplier; NeoForge binds a
	 * lazy {@code DeferredHolder} into the same handle. See {@link ModContent}.
	 */
	private static void bindModContent() {
		ModContent.ELECTRONIC_CIRCUIT = () -> ELECTRONIC_CIRCUIT;
		ModContent.ALIGNMENT_CHIP_DAY = () -> ALIGNMENT_CHIP_DAY;
		ModContent.ALIGNMENT_CHIP_NIGHT = () -> ALIGNMENT_CHIP_NIGHT;
		ModContent.IRON_DUST = () -> IRON_DUST;
		ModContent.COPPER_DUST = () -> COPPER_DUST;
		ModContent.GOLD_DUST = () -> GOLD_DUST;
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

		ModContent.GENERATOR_ITEM = () -> GENERATOR_ITEM;
		ModContent.GEOTHERMAL_GENERATOR_ITEM = () -> GEOTHERMAL_GENERATOR_ITEM;
		ModContent.WATER_MILL_ITEM = () -> WATER_MILL_ITEM;
		ModContent.WIND_MILL_ITEM = () -> WIND_MILL_ITEM;
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
	}
}
