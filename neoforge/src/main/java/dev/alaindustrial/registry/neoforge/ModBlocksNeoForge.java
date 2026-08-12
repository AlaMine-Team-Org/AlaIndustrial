package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.AssemblerBlock;
import dev.alaindustrial.block.BatteryBoxBlock;
import dev.alaindustrial.block.CesuBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.ChargePadBlock;
import dev.alaindustrial.block.EnergyCondenserBlock;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.CanningMachineBlock;
import dev.alaindustrial.block.CompressorBlock;
import dev.alaindustrial.block.IncubatorBlock;
import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.block.IncubatorDomeBlock;
import dev.alaindustrial.block.SawmillBlock;
import dev.alaindustrial.block.DaylightSolarPanelBlock;
import dev.alaindustrial.block.ElectricFurnaceBlock;
import dev.alaindustrial.block.EnrichedUraniumTorchBlock;
import dev.alaindustrial.block.EnrichedUraniumWallTorchBlock;
import dev.alaindustrial.block.ExtractorBlock;
import dev.alaindustrial.block.GeneratorBlock;
import dev.alaindustrial.block.GeothermalGeneratorBlock;
import dev.alaindustrial.block.IronChestBlock;
import dev.alaindustrial.block.StorageModuleBlock;
import dev.alaindustrial.block.IronFurnaceBlock;
import dev.alaindustrial.block.MaceratorBlock;
import dev.alaindustrial.block.SilverChestBlock;
import dev.alaindustrial.block.GoldChestBlock;
import dev.alaindustrial.block.MoonlitSolarPanelBlock;
import dev.alaindustrial.block.PolymerizerBlock;
import dev.alaindustrial.block.AlloySmelterBlock;
import dev.alaindustrial.block.VulcanizerBlock;
import dev.alaindustrial.block.GalvanicBathBlock;
import dev.alaindustrial.block.ElectricHeaterBlock;
import dev.alaindustrial.block.GardenDroneStationBlock;
import dev.alaindustrial.block.PumpBlock;
import dev.alaindustrial.block.FluidTankBlock;
import dev.alaindustrial.block.SolarPanelBlock;
import dev.alaindustrial.block.OilLiquidBlock;
import dev.alaindustrial.block.WaterMillBlock;
import dev.alaindustrial.block.WindMillBlock;
import dev.alaindustrial.block.HighAltitudeWindMillBlock;
import dev.alaindustrial.block.StormWindMillBlock;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModParticles;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge block registration (MOD-022 registration-facade). Mirrors the Fabric
 * {@code dev.alaindustrial.registry.ModBlocks} set 1:1 (same ids, same block subclasses) using
 * NeoForge's {@link DeferredRegister.Blocks} with the real content classes from {@code common}.
 *
 * <p><b>MOD-190.</b> The per-block {@code BlockBehaviour.Properties} chain now lives once in
 * {@link ContentManifest#BLOCK_PROPS}; {@link #props} looks it up by id and wraps it in the property
 * {@code Supplier} {@code registerBlock} wants. The Fabric side applies the same map entries, so the
 * two loaders cannot drift (the MOD-157 bug class). {@code setId} is applied by {@code registerBlock}
 * from the deferred key; {@code requiresCorrectToolForDrops} is baked into the machine map entries.
 *
 * <p><b>Split constraint (verified 26.2 API):</b> the {@code DeferredRegister} object and its
 * {@code register(modBus)} call must live on the {@code neoforge} side.
 */
public final class ModBlocksNeoForge {
	public static final DeferredRegister.Blocks BLOCKS =
			DeferredRegister.createBlocks(Industrialization.MOD_ID);

	// --- Machines (energy core + processing) ---
	public static final DeferredBlock<GeneratorBlock> GENERATOR =
			BLOCKS.registerBlock("generator", GeneratorBlock::new, props("generator"));
	public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL =
			BLOCKS.registerBlock("solar_panel", SolarPanelBlock::new, props("solar_panel"));
	public static final DeferredBlock<MoonlitSolarPanelBlock> MOONLIT_SOLAR_PANEL =
			BLOCKS.registerBlock("moonlit_solar_panel", MoonlitSolarPanelBlock::new, props("moonlit_solar_panel"));
	public static final DeferredBlock<DaylightSolarPanelBlock> DAYLIGHT_SOLAR_PANEL =
			BLOCKS.registerBlock("daylight_solar_panel", DaylightSolarPanelBlock::new, props("daylight_solar_panel"));
	public static final DeferredBlock<MaceratorBlock> MACERATOR =
			BLOCKS.registerBlock("macerator", MaceratorBlock::new, props("macerator"));
	public static final DeferredBlock<BatteryBoxBlock> BATTERY_BOX =
			BLOCKS.registerBlock("battery_box", BatteryBoxBlock::new, props("battery_box"));
	public static final DeferredBlock<CesuBlock> CESU =
			BLOCKS.registerBlock("cesu", CesuBlock::new, props("cesu"));

	// Teleporter station (MOD-091); visible since MOD-093.
	public static final DeferredBlock<TeleporterBlock> TELEPORTER =
			BLOCKS.registerBlock("teleporter", TeleporterBlock::new, props("teleporter"));
	public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE =
			BLOCKS.registerBlock("electric_furnace", ElectricFurnaceBlock::new, props("electric_furnace"));
	// Iron Furnace (MOD-115) — fuel-burning smelter between stone and electric; lit glows (light 13).
	public static final DeferredBlock<IronFurnaceBlock> IRON_FURNACE =
			BLOCKS.registerBlock("iron_furnace", IronFurnaceBlock::new, props("iron_furnace"));
	public static final DeferredBlock<ExtractorBlock> EXTRACTOR =
			BLOCKS.registerBlock("extractor", ExtractorBlock::new, props("extractor"));
	public static final DeferredBlock<CompressorBlock> COMPRESSOR =
			BLOCKS.registerBlock("compressor", CompressorBlock::new, props("compressor"));
	public static final DeferredBlock<CanningMachineBlock> CANNING_MACHINE =
			BLOCKS.registerBlock("canning_machine", CanningMachineBlock::new, props("canning_machine"));
	public static final DeferredBlock<SawmillBlock> SAWMILL =
			BLOCKS.registerBlock("sawmill", SawmillBlock::new, props("sawmill"));
	// MOD-275 — the first MV machine; a blueprint-driven auto-crafter.
	public static final DeferredBlock<AssemblerBlock> ASSEMBLER =
			BLOCKS.registerBlock("assembler", AssemblerBlock::new, props("assembler"));
	public static final DeferredBlock<PolymerizerBlock> POLYMERIZER =
			BLOCKS.registerBlock("polymerizer", PolymerizerBlock::new, props("polymerizer"));
	// Distillation Column (MOD-251): the 1×1×3 tower — base with item, two segment blocks without.
	public static final DeferredBlock<dev.alaindustrial.block.DistillationColumnBlock> DISTILLATION_COLUMN =
			BLOCKS.registerBlock("distillation_column",
					dev.alaindustrial.block.DistillationColumnBlock::new, props("distillation_column"));
	public static final DeferredBlock<dev.alaindustrial.block.DistillationColumnMiddleBlock> DISTILLATION_COLUMN_MIDDLE =
			BLOCKS.registerBlock("distillation_column_middle",
					dev.alaindustrial.block.DistillationColumnMiddleBlock::new, props("distillation_column_middle"));
	public static final DeferredBlock<dev.alaindustrial.block.DistillationColumnTopBlock> DISTILLATION_COLUMN_TOP =
			BLOCKS.registerBlock("distillation_column_top",
					dev.alaindustrial.block.DistillationColumnTopBlock::new, props("distillation_column_top"));
	public static final DeferredBlock<dev.alaindustrial.block.RectificationSectionBlock> RECTIFICATION_SECTION =
			BLOCKS.registerBlock("rectification_section",
					dev.alaindustrial.block.RectificationSectionBlock::new, props("rectification_section"));
	public static final DeferredBlock<AlloySmelterBlock> ALLOY_SMELTER =
			BLOCKS.registerBlock("alloy_smelter", AlloySmelterBlock::new, props("alloy_smelter"));
	public static final DeferredBlock<VulcanizerBlock> VULCANIZER =
			BLOCKS.registerBlock("vulcanizer", VulcanizerBlock::new, props("vulcanizer"));
	public static final DeferredBlock<GalvanicBathBlock> GALVANIC_BATH =
			BLOCKS.registerBlock("galvanic_bath", GalvanicBathBlock::new, props("galvanic_bath"));
	public static final DeferredBlock<ElectricHeaterBlock> ELECTRIC_HEATER =
			BLOCKS.registerBlock("electric_heater", ElectricHeaterBlock::new, props("electric_heater"));
	public static final DeferredBlock<ChargePadBlock> CHARGE_PAD =
			BLOCKS.registerBlock("charge_pad", ChargePadBlock::new, props("charge_pad"));
	/** Energy condenser (MOD-393): banks grid surplus into energy clots. */
	public static final DeferredBlock<EnergyCondenserBlock> ENERGY_CONDENSER =
			BLOCKS.registerBlock("energy_condenser", EnergyCondenserBlock::new, props("energy_condenser"));
	public static final DeferredBlock<IncubatorBlock> INCUBATOR =
			BLOCKS.registerBlock("incubator", IncubatorBlock::new, props("incubator"));
	public static final DeferredBlock<IncubatorDomeBlock> INCUBATOR_DOME =
			BLOCKS.registerBlock("incubator_dome", IncubatorDomeBlock::new, props("incubator_dome"));
	// Cotton trellis (MOD-280) — the mod's first crop; a two-block plant support, not a machine.
	public static final DeferredBlock<TrellisBlock> TRELLIS =
			BLOCKS.registerBlock("trellis", TrellisBlock::new, props("trellis"));
	public static final DeferredBlock<GeothermalGeneratorBlock> GEOTHERMAL_GENERATOR =
			BLOCKS.registerBlock("geothermal_generator", GeothermalGeneratorBlock::new, props("geothermal_generator"));
	public static final DeferredBlock<PumpBlock> PUMP =
			BLOCKS.registerBlock("pump", PumpBlock::new, props("pump"));
	public static final DeferredBlock<GardenDroneStationBlock> GARDEN_DRONE_STATION =
			BLOCKS.registerBlock("garden_drone_station", GardenDroneStationBlock::new,
					props("garden_drone_station"));
	public static final DeferredBlock<FluidTankBlock> FLUID_TANK =
			BLOCKS.registerBlock("fluid_tank", FluidTankBlock::new, props("fluid_tank"));
	public static final DeferredBlock<WaterMillBlock> WATER_MILL =
			BLOCKS.registerBlock("water_mill", WaterMillBlock::new, props("water_mill"));
	public static final DeferredBlock<WindMillBlock> WIND_MILL =
			BLOCKS.registerBlock("wind_mill", WindMillBlock::new, props("wind_mill"));
	public static final DeferredBlock<HighAltitudeWindMillBlock> HIGH_ALTITUDE_WIND_MILL =
			BLOCKS.registerBlock("high_altitude_wind_mill", HighAltitudeWindMillBlock::new, props("high_altitude_wind_mill"));
	public static final DeferredBlock<StormWindMillBlock> STORM_WIND_MILL =
			BLOCKS.registerBlock("storm_wind_mill", StormWindMillBlock::new, props("storm_wind_mill"));

	// --- Cables ---
	// Each grade passes its CableType (MOD-219/MOD-259) — the mirror of the Fabric side, which
	// loader_parity_check enforces. Rubber insulation keeps cap/buffer but halves attenuation.
	public static final DeferredBlock<CableBlock> COPPER_CABLE =
			BLOCKS.registerBlock("copper_cable", p -> new CableBlock(CableType.COPPER, p), props("copper_cable"));
	public static final DeferredBlock<CableBlock> TIN_CABLE =
			BLOCKS.registerBlock("tin_cable", p -> new CableBlock(CableType.TIN, p), props("tin_cable"));
	public static final DeferredBlock<CableBlock> GOLD_CABLE =
			BLOCKS.registerBlock("gold_cable", p -> new CableBlock(CableType.GOLD, p), props("gold_cable"));
	public static final DeferredBlock<CableBlock> ELECTRUM_CABLE =
			BLOCKS.registerBlock("electrum_cable", p -> new CableBlock(CableType.ELECTRUM, p),
					props("electrum_cable"));
	public static final DeferredBlock<CableBlock> INSULATED_COPPER_CABLE =
			BLOCKS.registerBlock("insulated_copper_cable", p -> new CableBlock(CableType.INSULATED_COPPER, p),
					props("insulated_copper_cable"));
	public static final DeferredBlock<CableBlock> INSULATED_TIN_CABLE =
			BLOCKS.registerBlock("insulated_tin_cable", p -> new CableBlock(CableType.INSULATED_TIN, p),
					props("insulated_tin_cable"));
	public static final DeferredBlock<CableBlock> INSULATED_GOLD_CABLE =
			BLOCKS.registerBlock("insulated_gold_cable", p -> new CableBlock(CableType.INSULATED_GOLD, p),
					props("insulated_gold_cable"));
	public static final DeferredBlock<CableBlock> INSULATED_ELECTRUM_CABLE =
			BLOCKS.registerBlock("insulated_electrum_cable", p -> new CableBlock(CableType.INSULATED_ELECTRUM, p),
					props("insulated_electrum_cable"));
	public static final DeferredBlock<ItemPipeBlock> ITEM_PIPE =
			BLOCKS.registerBlock("item_pipe", ItemPipeBlock::new, props("item_pipe"));
	public static final DeferredBlock<FluidPipeBlock> FLUID_PIPE =
			BLOCKS.registerBlock("fluid_pipe", FluidPipeBlock::new, props("fluid_pipe"));

	// --- Ores (plain Block, harvest tier is tag-driven) ---
	public static final DeferredBlock<Block> TIN_ORE =
			BLOCKS.registerBlock("tin_ore", Block::new, props("tin_ore"));
	public static final DeferredBlock<Block> DEEPSLATE_TIN_ORE =
			BLOCKS.registerBlock("deepslate_tin_ore", Block::new, props("deepslate_tin_ore"));
	public static final DeferredBlock<Block> SILVER_ORE =
			BLOCKS.registerBlock("silver_ore", Block::new, props("silver_ore"));
	public static final DeferredBlock<Block> DEEPSLATE_SILVER_ORE =
			BLOCKS.registerBlock("deepslate_silver_ore", Block::new, props("deepslate_silver_ore"));
	public static final DeferredBlock<Block> NICKEL_ORE =
			BLOCKS.registerBlock("nickel_ore", Block::new, props("nickel_ore"));
	public static final DeferredBlock<Block> DEEPSLATE_NICKEL_ORE =
			BLOCKS.registerBlock("deepslate_nickel_ore", Block::new, props("deepslate_nickel_ore"));
	public static final DeferredBlock<Block> SULFUR_ORE =
			BLOCKS.registerBlock("sulfur_ore", Block::new, props("sulfur_ore"));
	public static final DeferredBlock<Block> DEEPSLATE_SULFUR_ORE =
			BLOCKS.registerBlock("deepslate_sulfur_ore", Block::new, props("deepslate_sulfur_ore"));
	public static final DeferredBlock<Block> URANIUM_ORE =
			BLOCKS.registerBlock("uranium_ore", Block::new, props("uranium_ore"));
	public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE =
			BLOCKS.registerBlock("deepslate_uranium_ore", Block::new, props("deepslate_uranium_ore"));

	// --- Storage (pure container, no energy) ---
	public static final DeferredBlock<IronChestBlock> IRON_CHEST =
			BLOCKS.registerBlock("iron_chest", IronChestBlock::new, props("iron_chest"));
	// MOD-287 — modular warehouse block; several face-adjacent ones share one inventory.
	public static final DeferredBlock<StorageModuleBlock> STORAGE_MODULE =
			BLOCKS.registerBlock("storage_module", StorageModuleBlock::new, props("storage_module"));
	public static final DeferredBlock<SilverChestBlock> SILVER_CHEST =
			BLOCKS.registerBlock("silver_chest", SilverChestBlock::new, props("silver_chest"));
	public static final DeferredBlock<GoldChestBlock> GOLD_CHEST =
			BLOCKS.registerBlock("gold_chest", GoldChestBlock::new, props("gold_chest"));

	// Tempered Iron Block — "block of X" material block; plain Block, cube_all, single texture.
	public static final DeferredBlock<Block> TEMPERED_IRON_BLOCK =
			BLOCKS.registerBlock("tempered_iron_block", Block::new, props("tempered_iron_block"));
	// MOD-225 — machine casing + two decorative plate blocks (plain full cubes).
	public static final DeferredBlock<Block> MACHINE_CASING =
			BLOCKS.registerBlock("machine_casing", Block::new, props("machine_casing"));
	// MOD-292 — MV casing, the tier-up crafting base.
	public static final DeferredBlock<Block> ADVANCED_MACHINE_CASING =
			BLOCKS.registerBlock("advanced_machine_casing", Block::new, props("advanced_machine_casing"));
	public static final DeferredBlock<Block> SILVER_PLATE_BLOCK =
			BLOCKS.registerBlock("silver_plate_block", Block::new, props("silver_plate_block"));
	public static final DeferredBlock<Block> TEMPERED_IRON_PLATE_BLOCK =
			BLOCKS.registerBlock("tempered_iron_plate_block", Block::new, props("tempered_iron_plate_block"));

	// Industrial Workbench (MOD-062) — the Industrialist villager's job-site block; plain decorative cube.
	public static final DeferredBlock<Block> INDUSTRIAL_WORKBENCH =
			BLOCKS.registerBlock("industrial_workbench", Block::new, props("industrial_workbench"));

	// Enriched Uranium Torch (MOD-085) — vanilla-behaviour torch, light 14, green flame. Its props entry
	// is the vanilla TORCH chain (no requiresCorrectToolForDrops), applied by props() like every block.
	public static final DeferredBlock<EnrichedUraniumTorchBlock> ENRICHED_URANIUM_TORCH =
			BLOCKS.registerBlock("enriched_uranium_torch",
					p -> new EnrichedUraniumTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, p),
					props("enriched_uranium_torch"));

	// Wall variant: drops/names from the standing torch (vanilla wallVariant). The override is
	// loader-specific (reads the just-registered standing torch), so it layers on top of the shared torch
	// props here. The property supplier runs during the block RegisterEvent, by which point the earlier
	// ENRICHED_URANIUM_TORCH entry is resolved.
	public static final DeferredBlock<EnrichedUraniumWallTorchBlock> ENRICHED_URANIUM_WALL_TORCH =
			BLOCKS.registerBlock("enriched_uranium_wall_torch",
					p -> new EnrichedUraniumWallTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, p),
					() -> ContentManifest.blockProps("enriched_uranium_wall_torch")
							.apply(BlockBehaviour.Properties.of())
							.overrideLootTable(ENRICHED_URANIUM_TORCH.get().getLootTable())
							.overrideDescription(ENRICHED_URANIUM_TORCH.get().getDescriptionId()));

	// Oil (MOD-238): the in-world liquid block. The factory resolves the still fluid eagerly — safe,
	// because the FLUID RegisterEvent fires before BLOCK (vanilla registration order; see
	// ModFluidsNeoForge). NO BlockItem — the hand-carried form is the oil bucket.
	public static final DeferredBlock<OilLiquidBlock> OIL =
			BLOCKS.registerBlock("oil", p -> new OilLiquidBlock(ModFluidsNeoForge.OIL.get(), p), props("oil"));

	// Distillation fractions (MOD-251): plain vanilla LiquidBlocks (water-like, no burning subclass).
	public static final DeferredBlock<net.minecraft.world.level.block.LiquidBlock> DIESEL =
			BLOCKS.registerBlock("diesel",
					p -> new net.minecraft.world.level.block.LiquidBlock(ModFluidsNeoForge.DIESEL.get(), p),
					props("diesel"));
	public static final DeferredBlock<net.minecraft.world.level.block.LiquidBlock> FUEL_OIL =
			BLOCKS.registerBlock("fuel_oil",
					p -> new net.minecraft.world.level.block.LiquidBlock(ModFluidsNeoForge.FUEL_OIL.get(), p),
					props("fuel_oil"));

	private ModBlocksNeoForge() {
	}

	/**
	 * The property {@link Supplier} {@code registerBlock} wants: the shared per-block chain from
	 * {@link ContentManifest#BLOCK_PROPS} (looked up by id) applied to a bare {@code Properties.of()}.
	 * {@code setId} is applied by {@code registerBlock} from the deferred key; the machine map entries
	 * already carry {@code requiresCorrectToolForDrops}, and torch entries deliberately do not.
	 */
	private static Supplier<BlockBehaviour.Properties> props(String id) {
		return () -> ContentManifest.blockProps(id).apply(BlockBehaviour.Properties.of());
	}

	/**
	 * Bind each block {@code DeferredBlock} into the loader-neutral {@link ModContent} facade, mirroring
	 * {@code dev.alaindustrial.registry.ModBlocks#init()} on the Fabric side. A {@code DeferredBlock} is a
	 * {@code Supplier<Block>}, so it is bound via {@code HOLDER::get} (widening the subtype to the slot's
	 * {@code Supplier<Block>}); it resolves lazily after this register's {@code RegisterEvent}. Called from
	 * the {@code @Mod} constructor after {@code BLOCKS.register(modBus)}.
	 */
	public static void init() {
		ModContent.GENERATOR = GENERATOR::get;
		ModContent.SOLAR_PANEL = SOLAR_PANEL::get;
		ModContent.MOONLIT_SOLAR_PANEL = MOONLIT_SOLAR_PANEL::get;
		ModContent.DAYLIGHT_SOLAR_PANEL = DAYLIGHT_SOLAR_PANEL::get;
		ModContent.MACERATOR = MACERATOR::get;
		ModContent.BATTERY_BOX = BATTERY_BOX::get;
		ModContent.CESU = CESU::get;
		ModContent.TELEPORTER = TELEPORTER::get;
		ModContent.ELECTRIC_FURNACE = ELECTRIC_FURNACE::get;
		ModContent.IRON_FURNACE = IRON_FURNACE::get;
		ModContent.EXTRACTOR = EXTRACTOR::get;
		ModContent.COMPRESSOR = COMPRESSOR::get;
		ModContent.CANNING_MACHINE = CANNING_MACHINE::get;
		ModContent.SAWMILL = SAWMILL::get;
		ModContent.ASSEMBLER = ASSEMBLER::get;
		ModContent.POLYMERIZER = POLYMERIZER::get;
		ModContent.DISTILLATION_COLUMN = DISTILLATION_COLUMN::get;
		ModContent.DISTILLATION_COLUMN_MIDDLE = DISTILLATION_COLUMN_MIDDLE::get;
		ModContent.DISTILLATION_COLUMN_TOP = DISTILLATION_COLUMN_TOP::get;
		ModContent.RECTIFICATION_SECTION = RECTIFICATION_SECTION::get;
		ModContent.VULCANIZER = VULCANIZER::get;
		ModContent.ALLOY_SMELTER = ALLOY_SMELTER::get;
		ModContent.GALVANIC_BATH = GALVANIC_BATH::get;
		ModContent.ELECTRIC_HEATER = ELECTRIC_HEATER::get;
		ModContent.CHARGE_PAD = CHARGE_PAD::get;
		ModContent.ENERGY_CONDENSER = ENERGY_CONDENSER::get;
		ModContent.INCUBATOR = INCUBATOR::get;
		ModContent.INCUBATOR_DOME = INCUBATOR_DOME::get;
		ModContent.TRELLIS = TRELLIS::get;
		ModContent.GEOTHERMAL_GENERATOR = GEOTHERMAL_GENERATOR::get;
		ModContent.PUMP = PUMP::get;
		ModContent.GARDEN_DRONE_STATION = GARDEN_DRONE_STATION::get;
		ModContent.FLUID_TANK = FLUID_TANK::get;
		ModContent.WATER_MILL = WATER_MILL::get;
		ModContent.WIND_MILL = WIND_MILL::get;
		ModContent.HIGH_ALTITUDE_WIND_MILL = HIGH_ALTITUDE_WIND_MILL::get;
		ModContent.STORM_WIND_MILL = STORM_WIND_MILL::get;
		ModContent.COPPER_CABLE = COPPER_CABLE::get;
		ModContent.TIN_CABLE = TIN_CABLE::get;
		ModContent.GOLD_CABLE = GOLD_CABLE::get;
		ModContent.ELECTRUM_CABLE = ELECTRUM_CABLE::get;
		ModContent.INSULATED_COPPER_CABLE = INSULATED_COPPER_CABLE::get;
		ModContent.INSULATED_TIN_CABLE = INSULATED_TIN_CABLE::get;
		ModContent.INSULATED_GOLD_CABLE = INSULATED_GOLD_CABLE::get;
		ModContent.INSULATED_ELECTRUM_CABLE = INSULATED_ELECTRUM_CABLE::get;
		ModContent.ITEM_PIPE = ITEM_PIPE::get;
		ModContent.FLUID_PIPE = FLUID_PIPE::get;
		ModContent.TIN_ORE = TIN_ORE::get;
		ModContent.DEEPSLATE_TIN_ORE = DEEPSLATE_TIN_ORE::get;
		ModContent.SILVER_ORE = SILVER_ORE::get;
		ModContent.DEEPSLATE_SILVER_ORE = DEEPSLATE_SILVER_ORE::get;
		ModContent.NICKEL_ORE = NICKEL_ORE::get;
		ModContent.DEEPSLATE_NICKEL_ORE = DEEPSLATE_NICKEL_ORE::get;
		ModContent.SULFUR_ORE = SULFUR_ORE::get;
		ModContent.DEEPSLATE_SULFUR_ORE = DEEPSLATE_SULFUR_ORE::get;
		ModContent.URANIUM_ORE = URANIUM_ORE::get;
		ModContent.DEEPSLATE_URANIUM_ORE = DEEPSLATE_URANIUM_ORE::get;
		ModContent.IRON_CHEST = IRON_CHEST::get;
		ModContent.STORAGE_MODULE = STORAGE_MODULE::get;
		ModContent.SILVER_CHEST = SILVER_CHEST::get;
		ModContent.GOLD_CHEST = GOLD_CHEST::get;
		ModContent.TEMPERED_IRON_BLOCK = TEMPERED_IRON_BLOCK::get;
		ModContent.MACHINE_CASING = MACHINE_CASING::get;
		ModContent.ADVANCED_MACHINE_CASING = ADVANCED_MACHINE_CASING::get;
		ModContent.SILVER_PLATE_BLOCK = SILVER_PLATE_BLOCK::get;
		ModContent.TEMPERED_IRON_PLATE_BLOCK = TEMPERED_IRON_PLATE_BLOCK::get;
		ModContent.INDUSTRIAL_WORKBENCH = INDUSTRIAL_WORKBENCH::get;
		ModContent.ENRICHED_URANIUM_TORCH = ENRICHED_URANIUM_TORCH::get;
		ModContent.ENRICHED_URANIUM_WALL_TORCH = ENRICHED_URANIUM_WALL_TORCH::get;
		ModContent.OIL_BLOCK = OIL::get;
		ModContent.DIESEL_BLOCK = DIESEL::get;
		ModContent.FUEL_OIL_BLOCK = FUEL_OIL::get;
	}
}
