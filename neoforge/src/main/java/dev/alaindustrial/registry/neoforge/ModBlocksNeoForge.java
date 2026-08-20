package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.AlloySmelterBlock;
import dev.alaindustrial.block.AssemblerBlock;
import dev.alaindustrial.block.BatteryBoxBlock;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.CanningMachineBlock;
import dev.alaindustrial.block.CesuBlock;
import dev.alaindustrial.block.ChargePadBlock;
import dev.alaindustrial.block.ComponentRepairBenchBlock;
import dev.alaindustrial.block.CompressorBlock;
import dev.alaindustrial.block.DaylightSolarPanelBlock;
import dev.alaindustrial.block.DistillationColumnBlock;
import dev.alaindustrial.block.DistillationColumnMiddleBlock;
import dev.alaindustrial.block.DistillationColumnTopBlock;
import dev.alaindustrial.block.ElectricFurnaceBlock;
import dev.alaindustrial.block.ElectricHeaterBlock;
import dev.alaindustrial.block.EnergyCondenserBlock;
import dev.alaindustrial.block.MobRepellerBlock;
import dev.alaindustrial.block.MobRepellerHvBlock;
import dev.alaindustrial.block.MobRepellerMvBlock;
import dev.alaindustrial.block.EnrichedUraniumTorchBlock;
import dev.alaindustrial.block.EnrichedUraniumWallTorchBlock;
import dev.alaindustrial.block.ExtractorBlock;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.FluidTankBlock;
import dev.alaindustrial.block.GalvanicBathBlock;
import dev.alaindustrial.block.GardenDroneStationBlock;
import dev.alaindustrial.block.GeneratorBlock;
import dev.alaindustrial.block.GeothermalGeneratorBlock;
import dev.alaindustrial.block.ElectrumChestBlock;
import dev.alaindustrial.block.GoldChestBlock;
import dev.alaindustrial.block.HighAltitudeWindMillBlock;
import dev.alaindustrial.block.IncubatorBlock;
import dev.alaindustrial.block.IncubatorDomeBlock;
import dev.alaindustrial.block.IronChestBlock;
import dev.alaindustrial.block.IronFurnaceBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.MaceratorBlock;
import dev.alaindustrial.block.MoonlitSolarPanelBlock;
import dev.alaindustrial.block.ModLiquidBlock;
import dev.alaindustrial.block.OilLiquidBlock;
import dev.alaindustrial.block.PolymerizerBlock;
import dev.alaindustrial.block.PumpBlock;
import dev.alaindustrial.block.RectificationSectionBlock;
import dev.alaindustrial.block.SawmillBlock;
import dev.alaindustrial.block.SilverChestBlock;
import dev.alaindustrial.block.SolarPanelBlock;
import dev.alaindustrial.block.StorageModuleBlock;
import dev.alaindustrial.block.LightningRodGeneratorBlock;
import dev.alaindustrial.block.StormWindMillBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.ReactorLampBlock;
import dev.alaindustrial.block.ReactorOutletBlock;
import dev.alaindustrial.block.ReactorPortBlock;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.ReactorButtonBlock;
import dev.alaindustrial.block.ReactorShellBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.ReactorControllerBlock;
import dev.alaindustrial.block.ThermalCentrifugeBlock;
import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.block.VulcanizerBlock;
import dev.alaindustrial.block.WaterMillBlock;
import dev.alaindustrial.block.WindMillBlock;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge block registration: a replay of the shared {@link ContentManifest#BLOCKS} list, plus the typed
 * {@link DeferredBlock} handles the rest of the NeoForge source set reads.
 *
 * <p><b>MOD-190 → MOD-403.</b> MOD-190 moved each block's {@code BlockBehaviour.Properties} chain into
 * {@link ContentManifest#BLOCK_PROPS}. MOD-403 moved the <b>list</b>: which blocks exist, in what order,
 * built by which factory, bound to which {@link ModContent} handle. This file used to mirror the Fabric
 * {@code ModBlocks} "1:1 by convention", held together by a Python parity script — a block added on one
 * loader and forgotten here compiled fine and simply did not exist on NeoForge.
 *
 * <p><b>What is left here is the NeoForge registration MECHANISM, and only that:</b> the
 * {@link DeferredRegister} (which must live on this side) and its lazy {@code registerBlock}, whose
 * properties {@link Supplier} is invoked during the block {@code RegisterEvent} — that lateness is what
 * lets manifest entries read earlier ones ({@code enriched_uranium_wall_torch} → the standing torch, the
 * liquid blocks → their fluid, whose {@code RegisterEvent} fires first).
 *
 * <p><b>The typed fields below are handles, not registrations.</b> They exist because
 * {@code ModItemsNeoForge} (block items), the JEI plugin and {@code ModProfessionsNeoForge} read
 * {@code ModBlocksNeoForge.X}; adding a block does NOT require adding one. Each is derived from its
 * manifest entry, so the entry's block class — not a string — is what fixes the field's type parameter.
 */
public final class ModBlocksNeoForge {
	public static final DeferredRegister.Blocks BLOCKS =
			DeferredRegister.createBlocks(Industrialization.MOD_ID);

	/**
	 * Every manifest entry, queued on {@link #BLOCKS} the moment this class loads. Declared right after
	 * the register on purpose: static fields initialise in textual order, so the register exists here and
	 * the handles below can read the result.
	 */
	private static final Map<String, DeferredBlock<?>> REGISTERED = registerAll();

	public static final DeferredBlock<GeneratorBlock> GENERATOR = handle(ContentManifest.GENERATOR);
	public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = handle(ContentManifest.SOLAR_PANEL);
	public static final DeferredBlock<MoonlitSolarPanelBlock> MOONLIT_SOLAR_PANEL =
			handle(ContentManifest.MOONLIT_SOLAR_PANEL);
	public static final DeferredBlock<DaylightSolarPanelBlock> DAYLIGHT_SOLAR_PANEL =
			handle(ContentManifest.DAYLIGHT_SOLAR_PANEL);
	public static final DeferredBlock<GeothermalGeneratorBlock> GEOTHERMAL_GENERATOR =
			handle(ContentManifest.GEOTHERMAL_GENERATOR);
	public static final DeferredBlock<WaterMillBlock> WATER_MILL = handle(ContentManifest.WATER_MILL);
	public static final DeferredBlock<WindMillBlock> WIND_MILL = handle(ContentManifest.WIND_MILL);
	public static final DeferredBlock<HighAltitudeWindMillBlock> HIGH_ALTITUDE_WIND_MILL =
			handle(ContentManifest.HIGH_ALTITUDE_WIND_MILL);
	public static final DeferredBlock<StormWindMillBlock> STORM_WIND_MILL =
			handle(ContentManifest.STORM_WIND_MILL);
	public static final DeferredBlock<LightningRodGeneratorBlock> LIGHTNING_ROD_GENERATOR =
			handle(ContentManifest.LIGHTNING_ROD_GENERATOR);
	public static final DeferredBlock<PumpBlock> PUMP = handle(ContentManifest.PUMP);
	public static final DeferredBlock<GardenDroneStationBlock> GARDEN_DRONE_STATION =
			handle(ContentManifest.GARDEN_DRONE_STATION);
	public static final DeferredBlock<FluidTankBlock> FLUID_TANK = handle(ContentManifest.FLUID_TANK);
	public static final DeferredBlock<CableBlock> COPPER_CABLE = handle(ContentManifest.COPPER_CABLE);
	public static final DeferredBlock<CableBlock> TIN_CABLE = handle(ContentManifest.TIN_CABLE);
	public static final DeferredBlock<CableBlock> GOLD_CABLE = handle(ContentManifest.GOLD_CABLE);
	public static final DeferredBlock<CableBlock> ELECTRUM_CABLE = handle(ContentManifest.ELECTRUM_CABLE);
	public static final DeferredBlock<CableBlock> INSULATED_COPPER_CABLE =
			handle(ContentManifest.INSULATED_COPPER_CABLE);
	public static final DeferredBlock<CableBlock> INSULATED_TIN_CABLE =
			handle(ContentManifest.INSULATED_TIN_CABLE);
	public static final DeferredBlock<CableBlock> INSULATED_GOLD_CABLE =
			handle(ContentManifest.INSULATED_GOLD_CABLE);
	public static final DeferredBlock<CableBlock> INSULATED_ELECTRUM_CABLE =
			handle(ContentManifest.INSULATED_ELECTRUM_CABLE);
	public static final DeferredBlock<ItemPipeBlock> ITEM_PIPE = handle(ContentManifest.ITEM_PIPE);
	public static final DeferredBlock<FluidPipeBlock> FLUID_PIPE = handle(ContentManifest.FLUID_PIPE);
	public static final DeferredBlock<MaceratorBlock> MACERATOR = handle(ContentManifest.MACERATOR);
	public static final DeferredBlock<BatteryBoxBlock> BATTERY_BOX = handle(ContentManifest.BATTERY_BOX);
	public static final DeferredBlock<CesuBlock> CESU = handle(ContentManifest.CESU);
	public static final DeferredBlock<TeleporterBlock> TELEPORTER = handle(ContentManifest.TELEPORTER);
	public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE =
			handle(ContentManifest.ELECTRIC_FURNACE);
	public static final DeferredBlock<IronFurnaceBlock> IRON_FURNACE = handle(ContentManifest.IRON_FURNACE);
	public static final DeferredBlock<ExtractorBlock> EXTRACTOR = handle(ContentManifest.EXTRACTOR);
	public static final DeferredBlock<CompressorBlock> COMPRESSOR = handle(ContentManifest.COMPRESSOR);
	public static final DeferredBlock<ComponentRepairBenchBlock> COMPONENT_REPAIR_BENCH =
			handle(ContentManifest.COMPONENT_REPAIR_BENCH);
	public static final DeferredBlock<CanningMachineBlock> CANNING_MACHINE =
			handle(ContentManifest.CANNING_MACHINE);
	public static final DeferredBlock<SawmillBlock> SAWMILL = handle(ContentManifest.SAWMILL);
	public static final DeferredBlock<AssemblerBlock> ASSEMBLER = handle(ContentManifest.ASSEMBLER);
	public static final DeferredBlock<PolymerizerBlock> POLYMERIZER = handle(ContentManifest.POLYMERIZER);
	public static final DeferredBlock<DistillationColumnBlock> DISTILLATION_COLUMN =
			handle(ContentManifest.DISTILLATION_COLUMN);
	public static final DeferredBlock<DistillationColumnMiddleBlock> DISTILLATION_COLUMN_MIDDLE =
			handle(ContentManifest.DISTILLATION_COLUMN_MIDDLE);
	public static final DeferredBlock<DistillationColumnTopBlock> DISTILLATION_COLUMN_TOP =
			handle(ContentManifest.DISTILLATION_COLUMN_TOP);
	public static final DeferredBlock<RectificationSectionBlock> RECTIFICATION_SECTION =
			handle(ContentManifest.RECTIFICATION_SECTION);
	public static final DeferredBlock<AlloySmelterBlock> ALLOY_SMELTER = handle(ContentManifest.ALLOY_SMELTER);
	public static final DeferredBlock<VulcanizerBlock> VULCANIZER = handle(ContentManifest.VULCANIZER);
	public static final DeferredBlock<GalvanicBathBlock> GALVANIC_BATH = handle(ContentManifest.GALVANIC_BATH);
	public static final DeferredBlock<ThermalCentrifugeBlock> THERMAL_CENTRIFUGE =
			handle(ContentManifest.THERMAL_CENTRIFUGE);

	// MOD-468, stage 1 — the reactor room's shell.
	public static final DeferredBlock<ReactorShellBlock> REACTOR_CASING =
			handle(ContentManifest.REACTOR_CASING);
	public static final DeferredBlock<ReactorShellBlock> REACTOR_GLASS =
			handle(ContentManifest.REACTOR_GLASS);
	public static final DeferredBlock<ReactorPortBlock> REACTOR_PORT =
			handle(ContentManifest.REACTOR_PORT);
	public static final DeferredBlock<ReactorDoorBlock> REACTOR_DOOR =
			handle(ContentManifest.REACTOR_DOOR);
	public static final DeferredBlock<ReactorControllerBlock> REACTOR_CONTROLLER =
			handle(ContentManifest.REACTOR_CONTROLLER);
	public static final DeferredBlock<ReactorLampBlock> REACTOR_LAMP =
			handle(ContentManifest.REACTOR_LAMP);
	public static final DeferredBlock<SteamNozzleBlock> STEAM_NOZZLE =
			handle(ContentManifest.STEAM_NOZZLE);
	public static final DeferredBlock<ReactorOutletBlock> REACTOR_OUTLET =
			handle(ContentManifest.REACTOR_OUTLET);
	public static final DeferredBlock<ReactorButtonBlock> REACTOR_BUTTON =
			handle(ContentManifest.REACTOR_BUTTON);
	public static final DeferredBlock<FuelRodAssemblyBlock> FUEL_ROD_ASSEMBLY =
			handle(ContentManifest.FUEL_ROD_ASSEMBLY);
	public static final DeferredBlock<ElectricHeaterBlock> ELECTRIC_HEATER =
			handle(ContentManifest.ELECTRIC_HEATER);
	public static final DeferredBlock<ChargePadBlock> CHARGE_PAD = handle(ContentManifest.CHARGE_PAD);
	/** Energy condenser (MOD-393): banks grid surplus into energy clots. */
	public static final DeferredBlock<EnergyCondenserBlock> ENERGY_CONDENSER =
			handle(ContentManifest.ENERGY_CONDENSER);
	public static final DeferredBlock<MobRepellerBlock> MOB_REPELLER =
			handle(ContentManifest.MOB_REPELLER);
	public static final DeferredBlock<MobRepellerMvBlock> MOB_REPELLER_MV =
			handle(ContentManifest.MOB_REPELLER_MV);
	public static final DeferredBlock<MobRepellerHvBlock> MOB_REPELLER_HV =
			handle(ContentManifest.MOB_REPELLER_HV);
	public static final DeferredBlock<IncubatorBlock> INCUBATOR = handle(ContentManifest.INCUBATOR);
	public static final DeferredBlock<IncubatorDomeBlock> INCUBATOR_DOME = handle(ContentManifest.INCUBATOR_DOME);
	public static final DeferredBlock<TrellisBlock> TRELLIS = handle(ContentManifest.TRELLIS);
	public static final DeferredBlock<Block> TIN_ORE = handle(ContentManifest.TIN_ORE);
	public static final DeferredBlock<Block> DEEPSLATE_TIN_ORE = handle(ContentManifest.DEEPSLATE_TIN_ORE);
	public static final DeferredBlock<Block> SILVER_ORE = handle(ContentManifest.SILVER_ORE);
	public static final DeferredBlock<Block> DEEPSLATE_SILVER_ORE = handle(ContentManifest.DEEPSLATE_SILVER_ORE);
	public static final DeferredBlock<Block> NICKEL_ORE = handle(ContentManifest.NICKEL_ORE);
	public static final DeferredBlock<Block> DEEPSLATE_NICKEL_ORE = handle(ContentManifest.DEEPSLATE_NICKEL_ORE);
	public static final DeferredBlock<Block> SULFUR_ORE = handle(ContentManifest.SULFUR_ORE);
	public static final DeferredBlock<Block> DEEPSLATE_SULFUR_ORE = handle(ContentManifest.DEEPSLATE_SULFUR_ORE);
	public static final DeferredBlock<Block> URANIUM_ORE = handle(ContentManifest.URANIUM_ORE);
	public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE = handle(ContentManifest.DEEPSLATE_URANIUM_ORE);
	public static final DeferredBlock<Block> PALLADIUM_ORE = handle(ContentManifest.PALLADIUM_ORE);
	public static final DeferredBlock<IronChestBlock> IRON_CHEST = handle(ContentManifest.IRON_CHEST);
	public static final DeferredBlock<StorageModuleBlock> STORAGE_MODULE = handle(ContentManifest.STORAGE_MODULE);
	public static final DeferredBlock<SilverChestBlock> SILVER_CHEST = handle(ContentManifest.SILVER_CHEST);
	public static final DeferredBlock<GoldChestBlock> GOLD_CHEST = handle(ContentManifest.GOLD_CHEST);
	public static final DeferredBlock<ElectrumChestBlock> ELECTRUM_CHEST = handle(ContentManifest.ELECTRUM_CHEST);
	public static final DeferredBlock<Block> TEMPERED_IRON_BLOCK = handle(ContentManifest.TEMPERED_IRON_BLOCK);
	public static final DeferredBlock<Block> MACHINE_CASING = handle(ContentManifest.MACHINE_CASING);
	public static final DeferredBlock<Block> ADVANCED_MACHINE_CASING =
			handle(ContentManifest.ADVANCED_MACHINE_CASING);
	public static final DeferredBlock<Block> SILVER_PLATE_BLOCK = handle(ContentManifest.SILVER_PLATE_BLOCK);
	public static final DeferredBlock<Block> TEMPERED_IRON_PLATE_BLOCK =
			handle(ContentManifest.TEMPERED_IRON_PLATE_BLOCK);
	public static final DeferredBlock<Block> INDUSTRIAL_WORKBENCH = handle(ContentManifest.INDUSTRIAL_WORKBENCH);
	public static final DeferredBlock<EnrichedUraniumTorchBlock> ENRICHED_URANIUM_TORCH =
			handle(ContentManifest.ENRICHED_URANIUM_TORCH);
	public static final DeferredBlock<EnrichedUraniumWallTorchBlock> ENRICHED_URANIUM_WALL_TORCH =
			handle(ContentManifest.ENRICHED_URANIUM_WALL_TORCH);
	public static final DeferredBlock<OilLiquidBlock> OIL = handle(ContentManifest.OIL);
	public static final DeferredBlock<ModLiquidBlock> DIESEL = handle(ContentManifest.DIESEL);
	public static final DeferredBlock<ModLiquidBlock> FUEL_OIL = handle(ContentManifest.FUEL_OIL);

	private ModBlocksNeoForge() {
	}

	/** Queues every {@link ContentManifest#BLOCKS} entry, in list order, and binds each into ModContent. */
	private static Map<String, DeferredBlock<?>> registerAll() {
		Map<String, DeferredBlock<?>> registered = new LinkedHashMap<>();
		for (ContentManifest.BlockDef<?> def : ContentManifest.BLOCKS) {
			if (registered.put(def.id(), register(def)) != null) {
				throw new IllegalStateException(
						"ContentManifest.BLOCKS declares block id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	/**
	 * One manifest entry, the NeoForge way: {@code registerBlock} applies {@code setId} from the deferred
	 * key and calls the factory with the properties this supplier builds, when the block
	 * {@code RegisterEvent} fires.
	 *
	 * <p>The {@link ModContent} slot is bound HERE, at class load, with {@code holder::get} — a lazy
	 * handle, so binding before the event is legal and is exactly what {@code ModBlocksNeoForge.init()}
	 * used to do line by line for all 72 blocks.
	 */
	private static <T extends Block> DeferredBlock<T> register(ContentManifest.BlockDef<T> def) {
		DeferredBlock<T> holder = BLOCKS.registerBlock(def.id(), def.factory(), props(def.id()));
		def.bind().accept(holder::get);
		return holder;
	}

	/**
	 * The queued holder for a manifest entry, typed on the entry's block class.
	 *
	 * <p>The cast is unchecked but cannot lie: {@code def} is the very entry {@link #register} passed to
	 * {@code registerBlock}, so the holder it produced is a {@code DeferredBlock<T>} for that same
	 * {@code T}. What the compiler DOES check is the field: a
	 * {@code DeferredBlock<SolarPanelBlock> GENERATOR = handle(ContentManifest.GENERATOR)} does not
	 * compile, because {@code T} comes from the entry rather than from a string key.
	 */
	@SuppressWarnings("unchecked")
	private static <T extends Block> DeferredBlock<T> handle(ContentManifest.BlockDef<T> def) {
		DeferredBlock<?> holder = REGISTERED.get(def.id());
		if (holder == null) {
			throw new IllegalStateException("ModBlocksNeoForge handle for '" + def.id()
					+ "' has no queued block — is the entry missing from ContentManifest.BLOCKS?");
		}
		return (DeferredBlock<T>) holder;
	}

	/**
	 * The property {@link Supplier} {@code registerBlock} wants: the shared per-block chain from
	 * {@link ContentManifest#BLOCK_PROPS} applied to a bare {@code Properties.of()}. {@code setId} is
	 * applied by {@code registerBlock} from the deferred key; the machine map entries already carry
	 * {@code requiresCorrectToolForDrops}, and torch entries deliberately do not.
	 */
	private static Supplier<BlockBehaviour.Properties> props(String id) {
		return () -> ContentManifest.blockProps(id).apply(BlockBehaviour.Properties.of());
	}

	/**
	 * Class-load trigger for the {@code @Mod} constructor. Queueing and {@link ModContent} binding both
	 * happen in the static initializer above, so this only has to touch the class — and it checks, cheaply,
	 * that the replay covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED.size() != ContentManifest.BLOCKS.size()) {
			throw new IllegalStateException("ModBlocksNeoForge registered " + REGISTERED.size() + " of "
					+ ContentManifest.BLOCKS.size() + " manifest blocks");
		}
	}
}
