package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.BatteryBoxBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.EnrichedUraniumTorchBlock;
import dev.alaindustrial.block.EnrichedUraniumWallTorchBlock;
import dev.alaindustrial.block.CompressorBlock;
import dev.alaindustrial.block.SawmillBlock;
import dev.alaindustrial.block.DaylightSolarPanelBlock;
import dev.alaindustrial.block.ElectricFurnaceBlock;
import dev.alaindustrial.block.ExtractorBlock;
import dev.alaindustrial.block.GeneratorBlock;
import dev.alaindustrial.block.GeothermalGeneratorBlock;
import dev.alaindustrial.block.IronChestBlock;
import dev.alaindustrial.block.IronFurnaceBlock;
import dev.alaindustrial.block.MaceratorBlock;
import dev.alaindustrial.block.MoonlitSolarPanelBlock;
import dev.alaindustrial.block.PumpBlock;
import dev.alaindustrial.block.FluidTankBlock;
import dev.alaindustrial.block.SolarPanelBlock;
import dev.alaindustrial.block.WaterMillBlock;
import dev.alaindustrial.block.WindMillBlock;
import dev.alaindustrial.block.HighAltitudeWindMillBlock;
import dev.alaindustrial.block.StormWindMillBlock;
import dev.alaindustrial.block.SilverChestBlock;
import dev.alaindustrial.block.GoldChestBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Central registration for all Industrialization blocks: the Phase 1 energy-core set
 * (generator, copper cable, macerator, battery_box) and the machines built on it. No scattered init.
 *
 * <p><b>MOD-190.</b> The per-block {@code BlockBehaviour.Properties} chain (strength/sound/light) now
 * lives once in {@link ContentManifest#BLOCK_PROPS}; {@link #props} looks it up by the key's path and
 * layers the Fabric-only {@code setId}. The NeoForge {@code ModBlocksNeoForge} applies the same map
 * entries, so a value can no longer drift between loaders (the MOD-157 bug class).
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final ResourceKey<Block> GENERATOR_KEY = key("generator");
	public static final Block GENERATOR = register(GENERATOR_KEY, new GeneratorBlock(props(GENERATOR_KEY)));

	public static final ResourceKey<Block> SOLAR_PANEL_KEY = key("solar_panel");
	public static final Block SOLAR_PANEL = register(SOLAR_PANEL_KEY, new SolarPanelBlock(props(SOLAR_PANEL_KEY)));

	public static final ResourceKey<Block> MOONLIT_SOLAR_PANEL_KEY = key("moonlit_solar_panel");
	public static final Block MOONLIT_SOLAR_PANEL =
			register(MOONLIT_SOLAR_PANEL_KEY, new MoonlitSolarPanelBlock(props(MOONLIT_SOLAR_PANEL_KEY)));

	public static final ResourceKey<Block> DAYLIGHT_SOLAR_PANEL_KEY = key("daylight_solar_panel");
	public static final Block DAYLIGHT_SOLAR_PANEL =
			register(DAYLIGHT_SOLAR_PANEL_KEY, new DaylightSolarPanelBlock(props(DAYLIGHT_SOLAR_PANEL_KEY)));

	public static final ResourceKey<Block> GEOTHERMAL_GENERATOR_KEY = key("geothermal_generator");
	public static final Block GEOTHERMAL_GENERATOR =
			register(GEOTHERMAL_GENERATOR_KEY, new GeothermalGeneratorBlock(props(GEOTHERMAL_GENERATOR_KEY)));

	public static final ResourceKey<Block> WATER_MILL_KEY = key("water_mill");
	public static final Block WATER_MILL = register(WATER_MILL_KEY, new WaterMillBlock(props(WATER_MILL_KEY)));

	public static final ResourceKey<Block> WIND_MILL_KEY = key("wind_mill");
	public static final Block WIND_MILL = register(WIND_MILL_KEY, new WindMillBlock(props(WIND_MILL_KEY)));

	public static final ResourceKey<Block> HIGH_ALTITUDE_WIND_MILL_KEY = key("high_altitude_wind_mill");
	public static final Block HIGH_ALTITUDE_WIND_MILL =
			register(HIGH_ALTITUDE_WIND_MILL_KEY, new HighAltitudeWindMillBlock(props(HIGH_ALTITUDE_WIND_MILL_KEY)));

	public static final ResourceKey<Block> STORM_WIND_MILL_KEY = key("storm_wind_mill");
	public static final Block STORM_WIND_MILL =
			register(STORM_WIND_MILL_KEY, new StormWindMillBlock(props(STORM_WIND_MILL_KEY)));

	public static final ResourceKey<Block> PUMP_KEY = key("pump");
	public static final Block PUMP = register(PUMP_KEY, new PumpBlock(props(PUMP_KEY)));

	public static final ResourceKey<Block> FLUID_TANK_KEY = key("fluid_tank");
	public static final Block FLUID_TANK = register(FLUID_TANK_KEY, new FluidTankBlock(props(FLUID_TANK_KEY)));

	public static final ResourceKey<Block> COPPER_CABLE_KEY = key("copper_cable");
	public static final Block COPPER_CABLE = register(COPPER_CABLE_KEY, new CableBlock(props(COPPER_CABLE_KEY)));

	public static final ResourceKey<Block> TIN_CABLE_KEY = key("tin_cable");
	public static final Block TIN_CABLE = register(TIN_CABLE_KEY, new CableBlock(props(TIN_CABLE_KEY)));

	public static final ResourceKey<Block> INSULATED_COPPER_CABLE_KEY = key("insulated_copper_cable");
	public static final Block INSULATED_COPPER_CABLE =
			register(INSULATED_COPPER_CABLE_KEY, new CableBlock(props(INSULATED_COPPER_CABLE_KEY)));

	public static final ResourceKey<Block> INSULATED_TIN_CABLE_KEY = key("insulated_tin_cable");
	public static final Block INSULATED_TIN_CABLE =
			register(INSULATED_TIN_CABLE_KEY, new CableBlock(props(INSULATED_TIN_CABLE_KEY)));

	public static final ResourceKey<Block> ITEM_PIPE_KEY = key("item_pipe");
	public static final Block ITEM_PIPE = register(ITEM_PIPE_KEY, new ItemPipeBlock(props(ITEM_PIPE_KEY)));

	public static final ResourceKey<Block> MACERATOR_KEY = key("macerator");
	public static final Block MACERATOR = register(MACERATOR_KEY, new MaceratorBlock(props(MACERATOR_KEY)));

	public static final ResourceKey<Block> BATTERY_BOX_KEY = key("battery_box");
	public static final Block BATTERY_BOX = register(BATTERY_BOX_KEY, new BatteryBoxBlock(props(BATTERY_BOX_KEY)));

	// Teleporter station (MOD-091); visible since MOD-093 completed the feature.
	public static final ResourceKey<Block> TELEPORTER_KEY = key("teleporter");
	public static final Block TELEPORTER = register(TELEPORTER_KEY, new TeleporterBlock(props(TELEPORTER_KEY)));

	public static final ResourceKey<Block> ELECTRIC_FURNACE_KEY = key("electric_furnace");
	public static final Block ELECTRIC_FURNACE =
			register(ELECTRIC_FURNACE_KEY, new ElectricFurnaceBlock(props(ELECTRIC_FURNACE_KEY)));

	// Iron Furnace (MOD-115) — fuel-burning smelter between the stone and electric furnaces; the
	// lit blockstate glows (light 13) via the shared litLight helper.
	public static final ResourceKey<Block> IRON_FURNACE_KEY = key("iron_furnace");
	public static final Block IRON_FURNACE = register(IRON_FURNACE_KEY, new IronFurnaceBlock(props(IRON_FURNACE_KEY)));

	public static final ResourceKey<Block> EXTRACTOR_KEY = key("extractor");
	public static final Block EXTRACTOR = register(EXTRACTOR_KEY, new ExtractorBlock(props(EXTRACTOR_KEY)));

	public static final ResourceKey<Block> COMPRESSOR_KEY = key("compressor");
	public static final Block COMPRESSOR = register(COMPRESSOR_KEY, new CompressorBlock(props(COMPRESSOR_KEY)));

	public static final ResourceKey<Block> SAWMILL_KEY = key("sawmill");
	public static final Block SAWMILL = register(SAWMILL_KEY, new SawmillBlock(props(SAWMILL_KEY)));

	public static final ResourceKey<Block> TIN_ORE_KEY = key("tin_ore");
	public static final Block TIN_ORE = register(TIN_ORE_KEY, new Block(props(TIN_ORE_KEY)));

	public static final ResourceKey<Block> DEEPSLATE_TIN_ORE_KEY = key("deepslate_tin_ore");
	public static final Block DEEPSLATE_TIN_ORE = register(DEEPSLATE_TIN_ORE_KEY, new Block(props(DEEPSLATE_TIN_ORE_KEY)));

	public static final ResourceKey<Block> SILVER_ORE_KEY = key("silver_ore");
	public static final Block SILVER_ORE = register(SILVER_ORE_KEY, new Block(props(SILVER_ORE_KEY)));

	public static final ResourceKey<Block> DEEPSLATE_SILVER_ORE_KEY = key("deepslate_silver_ore");
	public static final Block DEEPSLATE_SILVER_ORE =
			register(DEEPSLATE_SILVER_ORE_KEY, new Block(props(DEEPSLATE_SILVER_ORE_KEY)));

	public static final ResourceKey<Block> NICKEL_ORE_KEY = key("nickel_ore");
	public static final Block NICKEL_ORE = register(NICKEL_ORE_KEY, new Block(props(NICKEL_ORE_KEY)));

	public static final ResourceKey<Block> DEEPSLATE_NICKEL_ORE_KEY = key("deepslate_nickel_ore");
	public static final Block DEEPSLATE_NICKEL_ORE =
			register(DEEPSLATE_NICKEL_ORE_KEY, new Block(props(DEEPSLATE_NICKEL_ORE_KEY)));

	public static final ResourceKey<Block> URANIUM_ORE_KEY = key("uranium_ore");
	public static final Block URANIUM_ORE = register(URANIUM_ORE_KEY, new Block(props(URANIUM_ORE_KEY)));

	public static final ResourceKey<Block> DEEPSLATE_URANIUM_ORE_KEY = key("deepslate_uranium_ore");
	public static final Block DEEPSLATE_URANIUM_ORE =
			register(DEEPSLATE_URANIUM_ORE_KEY, new Block(props(DEEPSLATE_URANIUM_ORE_KEY)));

	public static final ResourceKey<Block> IRON_CHEST_KEY = key("iron_chest");
	public static final Block IRON_CHEST = register(IRON_CHEST_KEY, new IronChestBlock(props(IRON_CHEST_KEY)));

	// Silver Chest (MOD-087) — the tier above the iron chest: 45 slots (5×9). Same block stats.
	public static final ResourceKey<Block> SILVER_CHEST_KEY = key("silver_chest");
	public static final Block SILVER_CHEST = register(SILVER_CHEST_KEY, new SilverChestBlock(props(SILVER_CHEST_KEY)));

	// Gold Chest (MOD-088) — the tier above the silver chest: 54 slots (6×9). Same block stats.
	public static final ResourceKey<Block> GOLD_CHEST_KEY = key("gold_chest");
	public static final Block GOLD_CHEST = register(GOLD_CHEST_KEY, new GoldChestBlock(props(GOLD_CHEST_KEY)));

	// Tempered Iron Block — "block of X" material block (9 ingots ↔ 1 block), like vanilla iron
	// block. Plain Block, cube_all model, single texture on all 6 faces. See docs/blocks/materials.
	public static final ResourceKey<Block> TEMPERED_IRON_BLOCK_KEY = key("tempered_iron_block");
	public static final Block TEMPERED_IRON_BLOCK =
			register(TEMPERED_IRON_BLOCK_KEY, new Block(props(TEMPERED_IRON_BLOCK_KEY)));

	// Industrial Workbench (MOD-062) — the Industrialist villager's job-site block. Plain decorative
	// full cube (canOcclude stays true), no BlockEntity; meaning comes from the PoiType/profession.
	public static final ResourceKey<Block> INDUSTRIAL_WORKBENCH_KEY = key("industrial_workbench");
	public static final Block INDUSTRIAL_WORKBENCH =
			register(INDUSTRIAL_WORKBENCH_KEY, new Block(props(INDUSTRIAL_WORKBENCH_KEY)));

	// Enriched Uranium Torch (MOD-085) — vanilla-behaviour torch, light 14, green flame. Its props entry
	// in ContentManifest#BLOCK_PROPS is the vanilla TORCH chain (no requiresCorrectToolForDrops), applied
	// by props() below just like every other block.
	public static final ResourceKey<Block> ENRICHED_URANIUM_TORCH_KEY = key("enriched_uranium_torch");
	public static final Block ENRICHED_URANIUM_TORCH = register(ENRICHED_URANIUM_TORCH_KEY,
			new EnrichedUraniumTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, props(ENRICHED_URANIUM_TORCH_KEY)));

	// Wall variant: mirrors the standing torch's loot table and display name (vanilla wallVariant). The
	// override is loader-specific (reads the just-registered standing torch), so it stays here on top of
	// the shared torch props. Registered after the standing torch (field order) so ENRICHED_URANIUM_TORCH
	// is resolved when its loot table / description id are read here.
	public static final ResourceKey<Block> ENRICHED_URANIUM_WALL_TORCH_KEY = key("enriched_uranium_wall_torch");
	public static final Block ENRICHED_URANIUM_WALL_TORCH = register(ENRICHED_URANIUM_WALL_TORCH_KEY,
			new EnrichedUraniumWallTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME,
					props(ENRICHED_URANIUM_WALL_TORCH_KEY)
							.overrideLootTable(ENRICHED_URANIUM_TORCH.getLootTable())
							.overrideDescription(ENRICHED_URANIUM_TORCH.getDescriptionId())));

	private static ResourceKey<Block> key(String path) {
		return ResourceKey.create(Registries.BLOCK, Industrialization.id(path));
	}

	/**
	 * The block's {@code Properties}: the shared per-block chain from {@link ContentManifest#BLOCK_PROPS}
	 * (looked up by the key's path) applied to a fresh base carrying the Fabric-only {@code setId}. The
	 * chain itself (strength/sound/light, {@code requiresCorrectToolForDrops} for non-torch blocks) is
	 * defined once in the manifest and shared with NeoForge, so the two loaders cannot drift (MOD-190).
	 */
	private static BlockBehaviour.Properties props(ResourceKey<Block> key) {
		return ContentManifest.blockProps(key.identifier().getPath())
				.apply(BlockBehaviour.Properties.of().setId(key));
	}

	private static <T extends Block> T register(ResourceKey<Block> key, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	/**
	 * Triggers class-loading so the static registrations above run during init, then publishes each
	 * eagerly-registered block into the loader-neutral {@link ModContent} facade so content classes
	 * (which read {@code ModContent.X.get()} at runtime) resolve the Fabric instance. Each handle wraps
	 * the already-registered value in a constant supplier ({@code () -> value}); NeoForge instead binds a
	 * lazy {@code DeferredHolder} into the same handle. See {@link ModContent}.
	 */
	public static void init() {
		ModContent.GENERATOR = () -> GENERATOR;
		ModContent.SOLAR_PANEL = () -> SOLAR_PANEL;
		ModContent.MOONLIT_SOLAR_PANEL = () -> MOONLIT_SOLAR_PANEL;
		ModContent.DAYLIGHT_SOLAR_PANEL = () -> DAYLIGHT_SOLAR_PANEL;
		ModContent.GEOTHERMAL_GENERATOR = () -> GEOTHERMAL_GENERATOR;
		ModContent.WATER_MILL = () -> WATER_MILL;
		ModContent.WIND_MILL = () -> WIND_MILL;
		ModContent.HIGH_ALTITUDE_WIND_MILL = () -> HIGH_ALTITUDE_WIND_MILL;
		ModContent.STORM_WIND_MILL = () -> STORM_WIND_MILL;
		ModContent.PUMP = () -> PUMP;
		ModContent.FLUID_TANK = () -> FLUID_TANK;
		ModContent.COPPER_CABLE = () -> COPPER_CABLE;
		ModContent.TIN_CABLE = () -> TIN_CABLE;
		ModContent.INSULATED_COPPER_CABLE = () -> INSULATED_COPPER_CABLE;
		ModContent.INSULATED_TIN_CABLE = () -> INSULATED_TIN_CABLE;
		ModContent.ITEM_PIPE = () -> ITEM_PIPE;
		ModContent.MACERATOR = () -> MACERATOR;
		ModContent.BATTERY_BOX = () -> BATTERY_BOX;
		ModContent.TELEPORTER = () -> TELEPORTER;
		ModContent.ELECTRIC_FURNACE = () -> ELECTRIC_FURNACE;
		ModContent.EXTRACTOR = () -> EXTRACTOR;
		ModContent.COMPRESSOR = () -> COMPRESSOR;
		ModContent.SAWMILL = () -> SAWMILL;
		ModContent.TIN_ORE = () -> TIN_ORE;
		ModContent.DEEPSLATE_TIN_ORE = () -> DEEPSLATE_TIN_ORE;
		ModContent.SILVER_ORE = () -> SILVER_ORE;
		ModContent.DEEPSLATE_SILVER_ORE = () -> DEEPSLATE_SILVER_ORE;
		ModContent.NICKEL_ORE = () -> NICKEL_ORE;
		ModContent.DEEPSLATE_NICKEL_ORE = () -> DEEPSLATE_NICKEL_ORE;
		ModContent.URANIUM_ORE = () -> URANIUM_ORE;
		ModContent.DEEPSLATE_URANIUM_ORE = () -> DEEPSLATE_URANIUM_ORE;
		ModContent.IRON_CHEST = () -> IRON_CHEST;
		ModContent.IRON_FURNACE = () -> IRON_FURNACE;
		ModContent.SILVER_CHEST = () -> SILVER_CHEST;
		ModContent.GOLD_CHEST = () -> GOLD_CHEST;
		ModContent.TEMPERED_IRON_BLOCK = () -> TEMPERED_IRON_BLOCK;
		ModContent.INDUSTRIAL_WORKBENCH = () -> INDUSTRIAL_WORKBENCH;
		ModContent.ENRICHED_URANIUM_TORCH = () -> ENRICHED_URANIUM_TORCH;
		ModContent.ENRICHED_URANIUM_WALL_TORCH = () -> ENRICHED_URANIUM_WALL_TORCH;
	}
}
