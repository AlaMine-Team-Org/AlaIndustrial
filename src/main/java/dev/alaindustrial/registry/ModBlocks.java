package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.BatteryBoxBlock;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.CompressorBlock;
import dev.alaindustrial.block.DaylightSolarPanelBlock;
import dev.alaindustrial.block.ElectricFurnaceBlock;
import dev.alaindustrial.block.ExtractorBlock;
import dev.alaindustrial.block.GeneratorBlock;
import dev.alaindustrial.block.GeothermalGeneratorBlock;
import dev.alaindustrial.block.MaceratorBlock;
import dev.alaindustrial.block.MoonlitSolarPanelBlock;
import dev.alaindustrial.block.PumpBlock;
import dev.alaindustrial.block.SolarPanelBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Central registration for all Industrialization blocks: the Phase 1 energy-core set
 * (generator, copper cable, macerator, battery_box) and the machines built on it. No scattered init.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final ResourceKey<Block> GENERATOR_KEY = key("generator");
	public static final Block GENERATOR = register(GENERATOR_KEY,
			new GeneratorBlock(props(GENERATOR_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> SOLAR_PANEL_KEY = key("solar_panel");
	public static final Block SOLAR_PANEL = register(SOLAR_PANEL_KEY,
			new SolarPanelBlock(props(SOLAR_PANEL_KEY).strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()));

	public static final ResourceKey<Block> MOONLIT_SOLAR_PANEL_KEY = key("moonlit_solar_panel");
	public static final Block MOONLIT_SOLAR_PANEL = register(MOONLIT_SOLAR_PANEL_KEY,
			new MoonlitSolarPanelBlock(props(MOONLIT_SOLAR_PANEL_KEY).strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()));

	public static final ResourceKey<Block> DAYLIGHT_SOLAR_PANEL_KEY = key("daylight_solar_panel");
	public static final Block DAYLIGHT_SOLAR_PANEL = register(DAYLIGHT_SOLAR_PANEL_KEY,
			new DaylightSolarPanelBlock(props(DAYLIGHT_SOLAR_PANEL_KEY).strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()));

	public static final ResourceKey<Block> GEOTHERMAL_GENERATOR_KEY = key("geothermal_generator");
	public static final Block GEOTHERMAL_GENERATOR = register(GEOTHERMAL_GENERATOR_KEY,
			new GeothermalGeneratorBlock(props(GEOTHERMAL_GENERATOR_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> PUMP_KEY = key("pump");
	public static final Block PUMP = register(PUMP_KEY,
			new PumpBlock(props(PUMP_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> COPPER_CABLE_KEY = key("copper_cable");
	public static final Block COPPER_CABLE = register(COPPER_CABLE_KEY,
			new CableBlock(props(COPPER_CABLE_KEY).strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()));

	public static final ResourceKey<Block> TIN_CABLE_KEY = key("tin_cable");
	public static final Block TIN_CABLE = register(TIN_CABLE_KEY,
			new CableBlock(props(TIN_CABLE_KEY).strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()));

	public static final ResourceKey<Block> INSULATED_COPPER_CABLE_KEY = key("insulated_copper_cable");
	public static final Block INSULATED_COPPER_CABLE = register(INSULATED_COPPER_CABLE_KEY,
			new CableBlock(props(INSULATED_COPPER_CABLE_KEY).strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion()));

	public static final ResourceKey<Block> INSULATED_TIN_CABLE_KEY = key("insulated_tin_cable");
	public static final Block INSULATED_TIN_CABLE = register(INSULATED_TIN_CABLE_KEY,
			new CableBlock(props(INSULATED_TIN_CABLE_KEY).strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion()));

	public static final ResourceKey<Block> MACERATOR_KEY = key("macerator");
	public static final Block MACERATOR = register(MACERATOR_KEY,
			new MaceratorBlock(props(MACERATOR_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> BATTERY_BOX_KEY = key("battery_box");
	public static final Block BATTERY_BOX = register(BATTERY_BOX_KEY,
			new BatteryBoxBlock(props(BATTERY_BOX_KEY).strength(3.0f, 6.0f).sound(SoundType.WOOD)));

	public static final ResourceKey<Block> ELECTRIC_FURNACE_KEY = key("electric_furnace");
	public static final Block ELECTRIC_FURNACE = register(ELECTRIC_FURNACE_KEY,
			new ElectricFurnaceBlock(props(ELECTRIC_FURNACE_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> EXTRACTOR_KEY = key("extractor");
	public static final Block EXTRACTOR = register(EXTRACTOR_KEY,
			new ExtractorBlock(props(EXTRACTOR_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> COMPRESSOR_KEY = key("compressor");
	public static final Block COMPRESSOR = register(COMPRESSOR_KEY,
			new CompressorBlock(props(COMPRESSOR_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> TIN_ORE_KEY = key("tin_ore");
	public static final Block TIN_ORE = register(TIN_ORE_KEY,
			new Block(props(TIN_ORE_KEY).strength(3.0f, 3.0f).sound(SoundType.STONE)));

	public static final ResourceKey<Block> DEEPSLATE_TIN_ORE_KEY = key("deepslate_tin_ore");
	public static final Block DEEPSLATE_TIN_ORE = register(DEEPSLATE_TIN_ORE_KEY,
			new Block(props(DEEPSLATE_TIN_ORE_KEY).strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)));

	public static final ResourceKey<Block> SILVER_ORE_KEY = key("silver_ore");
	public static final Block SILVER_ORE = register(SILVER_ORE_KEY,
			new Block(props(SILVER_ORE_KEY).strength(3.0f, 3.0f).sound(SoundType.STONE)));

	public static final ResourceKey<Block> DEEPSLATE_SILVER_ORE_KEY = key("deepslate_silver_ore");
	public static final Block DEEPSLATE_SILVER_ORE = register(DEEPSLATE_SILVER_ORE_KEY,
			new Block(props(DEEPSLATE_SILVER_ORE_KEY).strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)));

	public static final ResourceKey<Block> NICKEL_ORE_KEY = key("nickel_ore");
	public static final Block NICKEL_ORE = register(NICKEL_ORE_KEY,
			new Block(props(NICKEL_ORE_KEY).strength(3.0f, 3.0f).sound(SoundType.STONE)));

	public static final ResourceKey<Block> DEEPSLATE_NICKEL_ORE_KEY = key("deepslate_nickel_ore");
	public static final Block DEEPSLATE_NICKEL_ORE = register(DEEPSLATE_NICKEL_ORE_KEY,
			new Block(props(DEEPSLATE_NICKEL_ORE_KEY).strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)));

	public static final ResourceKey<Block> URANIUM_ORE_KEY = key("uranium_ore");
	public static final Block URANIUM_ORE = register(URANIUM_ORE_KEY,
			new Block(props(URANIUM_ORE_KEY).strength(3.0f, 3.0f).sound(SoundType.STONE)));

	public static final ResourceKey<Block> DEEPSLATE_URANIUM_ORE_KEY = key("deepslate_uranium_ore");
	public static final Block DEEPSLATE_URANIUM_ORE = register(DEEPSLATE_URANIUM_ORE_KEY,
			new Block(props(DEEPSLATE_URANIUM_ORE_KEY).strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)));

	private static ResourceKey<Block> key(String path) {
		return ResourceKey.create(Registries.BLOCK, Industrialization.id(path));
	}

	private static BlockBehaviour.Properties props(ResourceKey<Block> key) {
		// requiresCorrectToolForDrops + the minecraft:mineable/pickaxe tag → a pickaxe is needed to get
		// a drop; bare hand yields none (R-BRK-02). Functional/machine blocks have no tier gate (any
		// pickaxe works). The ore blocks add a harvest-tier gate purely via the vanilla data tags
		// minecraft:needs_stone_tool (tin/silver/nickel) and minecraft:needs_iron_tool (uranium) — a
		// too-low pickaxe drops nothing and mines much slower (R-BRK-09). Tier gating in 1.21+ is
		// tag-driven, not a Block.Properties field, so nothing is set here per-ore.
		return BlockBehaviour.Properties.of().setId(key).requiresCorrectToolForDrops();
	}

	private static <T extends Block> T register(ResourceKey<Block> key, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	/** Triggers class-loading so the static registrations above run during init. */
	public static void init() {
	}
}
