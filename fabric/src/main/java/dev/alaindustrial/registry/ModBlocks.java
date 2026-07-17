package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.BatteryBoxBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.EnrichedUraniumTorchBlock;
import dev.alaindustrial.block.EnrichedUraniumWallTorchBlock;
import dev.alaindustrial.block.CompressorBlock;
import dev.alaindustrial.block.DaylightSolarPanelBlock;
import dev.alaindustrial.block.ElectricFurnaceBlock;
import dev.alaindustrial.block.ExtractorBlock;
import dev.alaindustrial.block.GeneratorBlock;
import dev.alaindustrial.block.GeothermalGeneratorBlock;
import dev.alaindustrial.block.IronChestBlock;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;

/**
 * Central registration for all Industrialization blocks: the Phase 1 energy-core set
 * (generator, copper cable, macerator, battery_box) and the machines built on it. No scattered init.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final ResourceKey<Block> GENERATOR_KEY = key("generator");
	public static final Block GENERATOR = register(GENERATOR_KEY,
			new GeneratorBlock(props(GENERATOR_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlocks::litLight)));

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
			new GeothermalGeneratorBlock(props(GEOTHERMAL_GENERATOR_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlocks::litLight)));

	public static final ResourceKey<Block> WATER_MILL_KEY = key("water_mill");
	public static final Block WATER_MILL = register(WATER_MILL_KEY,
			new WaterMillBlock(props(WATER_MILL_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> WIND_MILL_KEY = key("wind_mill");
	public static final Block WIND_MILL = register(WIND_MILL_KEY,
			new WindMillBlock(props(WIND_MILL_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> HIGH_ALTITUDE_WIND_MILL_KEY = key("high_altitude_wind_mill");
	public static final Block HIGH_ALTITUDE_WIND_MILL = register(HIGH_ALTITUDE_WIND_MILL_KEY,
			new HighAltitudeWindMillBlock(props(HIGH_ALTITUDE_WIND_MILL_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> STORM_WIND_MILL_KEY = key("storm_wind_mill");
	public static final Block STORM_WIND_MILL = register(STORM_WIND_MILL_KEY,
			new StormWindMillBlock(props(STORM_WIND_MILL_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> PUMP_KEY = key("pump");
	public static final Block PUMP = register(PUMP_KEY,
			new PumpBlock(props(PUMP_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> FLUID_TANK_KEY = key("fluid_tank");
	public static final Block FLUID_TANK = register(FLUID_TANK_KEY,
			new FluidTankBlock(props(FLUID_TANK_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));

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

	public static final ResourceKey<Block> ITEM_PIPE_KEY = key("item_pipe");
	public static final Block ITEM_PIPE = register(ITEM_PIPE_KEY,
			new ItemPipeBlock(props(ITEM_PIPE_KEY).strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()));

	public static final ResourceKey<Block> MACERATOR_KEY = key("macerator");
	public static final Block MACERATOR = register(MACERATOR_KEY,
			new MaceratorBlock(props(MACERATOR_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL)));

	public static final ResourceKey<Block> BATTERY_BOX_KEY = key("battery_box");
	public static final Block BATTERY_BOX = register(BATTERY_BOX_KEY,
			new BatteryBoxBlock(props(BATTERY_BOX_KEY).strength(3.0f, 6.0f).sound(SoundType.WOOD)));

	// Teleporter station (MOD-091); visible since MOD-093 completed the feature.
	public static final ResourceKey<Block> TELEPORTER_KEY = key("teleporter");
	public static final Block TELEPORTER = register(TELEPORTER_KEY,
			new TeleporterBlock(props(TELEPORTER_KEY).strength(5.0f, 12.0f).sound(SoundType.METAL)));

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

	public static final ResourceKey<Block> IRON_CHEST_KEY = key("iron_chest");
	public static final Block IRON_CHEST = register(IRON_CHEST_KEY,
			new IronChestBlock(props(IRON_CHEST_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));

	// Silver Chest (MOD-087) — the tier above the iron chest: 45 slots (5×9). Same block stats as the
	// iron chest (strength 3.0/6.0, METAL, noOcclusion) since it is the same chest shape.
	public static final ResourceKey<Block> SILVER_CHEST_KEY = key("silver_chest");
	public static final Block SILVER_CHEST = register(SILVER_CHEST_KEY,
			new SilverChestBlock(props(SILVER_CHEST_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));

	// Gold Chest (MOD-088) — the tier above the silver chest: 54 slots (6×9). Same block stats.
	public static final ResourceKey<Block> GOLD_CHEST_KEY = key("gold_chest");
	public static final Block GOLD_CHEST = register(GOLD_CHEST_KEY,
			new GoldChestBlock(props(GOLD_CHEST_KEY).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));

	// Tempered Iron Block — "block of X" material block (9 ingots ↔ 1 block), like vanilla iron
	// block. Plain Block, cube_all model, single texture on all 6 faces. Strength/sound mirror
	// vanilla iron_block (5.0 / 6.0, METAL). See docs/blocks/materials/tempered_iron_block.md.
	public static final ResourceKey<Block> TEMPERED_IRON_BLOCK_KEY = key("tempered_iron_block");
	public static final Block TEMPERED_IRON_BLOCK = register(TEMPERED_IRON_BLOCK_KEY,
			new Block(props(TEMPERED_IRON_BLOCK_KEY).strength(5.0f, 6.0f).sound(SoundType.METAL)));

	// Enriched Uranium Torch (MOD-085) — vanilla-behaviour torch, light 15, green flame. NOT built via
	// props() (that adds requiresCorrectToolForDrops, non-vanilla for a torch): torchProps() mirrors the
	// vanilla TORCH property chain (no collision, instabreak, WOOD, DESTROY push reaction) exactly.
	public static final ResourceKey<Block> ENRICHED_URANIUM_TORCH_KEY = key("enriched_uranium_torch");
	public static final Block ENRICHED_URANIUM_TORCH = register(ENRICHED_URANIUM_TORCH_KEY,
			new EnrichedUraniumTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, torchProps(ENRICHED_URANIUM_TORCH_KEY)));

	// Wall variant: mirrors the standing torch's loot table and display name (vanilla wallVariant), so it
	// needs neither its own loot table nor its own lang key. Registered after the standing torch (field
	// order) so ENRICHED_URANIUM_TORCH is resolved when its loot table / description id are read here.
	public static final ResourceKey<Block> ENRICHED_URANIUM_WALL_TORCH_KEY = key("enriched_uranium_wall_torch");
	public static final Block ENRICHED_URANIUM_WALL_TORCH = register(ENRICHED_URANIUM_WALL_TORCH_KEY,
			new EnrichedUraniumWallTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME,
					torchProps(ENRICHED_URANIUM_WALL_TORCH_KEY)
							.overrideLootTable(ENRICHED_URANIUM_TORCH.getLootTable())
							.overrideDescription(ENRICHED_URANIUM_TORCH.getDescriptionId())));

	private static ResourceKey<Block> key(String path) {
		return ResourceKey.create(Registries.BLOCK, Industrialization.id(path));
	}

	/**
	 * Per-state light emission for fuel-burning generators (MOD-013): a working generator
	 * ({@code lit=true}) glows at light level 13 like a lit vanilla furnace; idle ({@code lit=false})
	 * emits none. Applied only to {@code generator} and {@code geothermal_generator} — the EU-powered
	 * processing machines (macerator/furnace/extractor/compressor) share the {@code lit} state but do
	 * not burn fuel, so they stay dark.
	 */
	private static int litLight(BlockState state) {
		return state.getValue(BlockStateProperties.LIT) ? 13 : 0;
	}

	/**
	 * Vanilla torch property chain (MOD-085), mirroring {@code Blocks.TORCH} exactly: no collision, instant
	 * break (breaks by hand, no tool gate — so the torch is NOT in {@code minecraft:mineable/pickaxe} and NOT
	 * {@code requiresCorrectToolForDrops}, unlike the {@link #props} machines/ores), light level 14 (same as
	 * the vanilla torch — kept identical to avoid any behavioural surprises; the torch's "enriched" identity
	 * comes from the green flame, richer particles and underwater burning, not from +1 light), WOOD sound,
	 * and {@code DESTROY} push reaction (a piston breaks it). {@code noOcclusion()} keeps the block-standards
	 * occlusion invariant happy (non-full-cube ⇒ must not occlude).
	 */
	private static BlockBehaviour.Properties torchProps(ResourceKey<Block> key) {
		return BlockBehaviour.Properties.of().setId(key).noCollision().instabreak()
				.lightLevel(state -> 14).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY).noOcclusion();
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
		ModContent.TIN_ORE = () -> TIN_ORE;
		ModContent.DEEPSLATE_TIN_ORE = () -> DEEPSLATE_TIN_ORE;
		ModContent.SILVER_ORE = () -> SILVER_ORE;
		ModContent.DEEPSLATE_SILVER_ORE = () -> DEEPSLATE_SILVER_ORE;
		ModContent.NICKEL_ORE = () -> NICKEL_ORE;
		ModContent.DEEPSLATE_NICKEL_ORE = () -> DEEPSLATE_NICKEL_ORE;
		ModContent.URANIUM_ORE = () -> URANIUM_ORE;
		ModContent.DEEPSLATE_URANIUM_ORE = () -> DEEPSLATE_URANIUM_ORE;
		ModContent.IRON_CHEST = () -> IRON_CHEST;
		ModContent.SILVER_CHEST = () -> SILVER_CHEST;
		ModContent.GOLD_CHEST = () -> GOLD_CHEST;
		ModContent.TEMPERED_IRON_BLOCK = () -> TEMPERED_IRON_BLOCK;
		ModContent.ENRICHED_URANIUM_TORCH = () -> ENRICHED_URANIUM_TORCH;
		ModContent.ENRICHED_URANIUM_WALL_TORCH = () -> ENRICHED_URANIUM_WALL_TORCH;
	}
}
