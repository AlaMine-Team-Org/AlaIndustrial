package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.SawmillBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ExtractorBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.core.fabric.PortAsEnergyStorage;
import dev.alaindustrial.core.fabric.TankAsFluidStorage;
// MOD-022 Phase 2: machines now expose a platform-neutral EnergyPort (MachineBlockEntity#energyPort).
// The Fabric SIDED capability binding is the per-loader seam: the neutral port is published through
// Team Reborn's EnergyStorage.SIDED via the PortAsEnergyStorage reverse adapter. NeoForge binds the same
// neutral port through RegisterCapabilitiesEvent.registerBlockEntity(...) with its own EnergyHandler
// adapter. MOD-028: fluid follows the identical pattern — the neutral FluidPort (MachineBlockEntity
// subclasses implementing FluidPortHost#fluidPort) is published through FluidStorage.SIDED via the
// TankAsFluidStorage reverse adapter; NeoForge binds the same neutral port through
// RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.Fluid.BLOCK, ...).
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import team.reborn.energy.api.EnergyStorage;

/**
 * Central registration for Industrialization {@link BlockEntityType}s, plus the Team Reborn Energy
 * {@code SIDED} lookup that publishes each machine's neutral energy buffer to the energy network.
 */
public final class ModBlockEntities {
	private ModBlockEntities() {
	}

	public static BlockEntityType<GeneratorBlockEntity> GENERATOR;
	public static BlockEntityType<GeothermalGeneratorBlockEntity> GEOTHERMAL_GENERATOR;
	public static BlockEntityType<SolarPanelBlockEntity> SOLAR_PANEL;
	public static BlockEntityType<MoonlitSolarPanelBlockEntity> MOONLIT_SOLAR_PANEL;
	public static BlockEntityType<DaylightSolarPanelBlockEntity> DAYLIGHT_SOLAR_PANEL;
	public static BlockEntityType<CableBlockEntity> COPPER_CABLE;
	public static BlockEntityType<ItemPipeBlockEntity> ITEM_PIPE;
	public static BlockEntityType<FluidPipeBlockEntity> FLUID_PIPE;
	public static BlockEntityType<MaceratorBlockEntity> MACERATOR;
	public static BlockEntityType<BatteryBoxBlockEntity> BATTERY_BOX;
	public static BlockEntityType<CesuBlockEntity> CESU;
	public static BlockEntityType<TeleporterBlockEntity> TELEPORTER;
	public static BlockEntityType<ElectricFurnaceBlockEntity> ELECTRIC_FURNACE;
	// Iron furnace is fuel-burning, not an EnergyPort — so no Team Reborn EnergyStorage.SIDED line below.
	public static BlockEntityType<IronFurnaceBlockEntity> IRON_FURNACE;
	public static BlockEntityType<ExtractorBlockEntity> EXTRACTOR;
	public static BlockEntityType<CompressorBlockEntity> COMPRESSOR;
	public static BlockEntityType<SawmillBlockEntity> SAWMILL;
	public static BlockEntityType<AssemblerBlockEntity> ASSEMBLER;
	public static BlockEntityType<PolymerizerBlockEntity> POLYMERIZER;
	public static BlockEntityType<VulcanizerBlockEntity> VULCANIZER;
	public static BlockEntityType<AlloySmelterBlockEntity> ALLOY_SMELTER;
	public static BlockEntityType<GalvanicBathBlockEntity> GALVANIC_BATH;
	public static BlockEntityType<ElectricHeaterBlockEntity> ELECTRIC_HEATER;
	public static BlockEntityType<ChargePadBlockEntity> CHARGE_PAD;
	public static BlockEntityType<IncubatorBlockEntity> INCUBATOR;
	public static BlockEntityType<PumpBlockEntity> PUMP;
	public static BlockEntityType<GardenDroneStationBlockEntity> GARDEN_DRONE_STATION;
	public static BlockEntityType<FluidTankBlockEntity> FLUID_TANK;
	public static BlockEntityType<WaterMillBlockEntity> WATER_MILL;
	public static BlockEntityType<WindMillBlockEntity> WIND_MILL;
	public static BlockEntityType<HighAltitudeWindMillBlockEntity> HIGH_ALTITUDE_WIND_MILL;
	public static BlockEntityType<StormWindMillBlockEntity> STORM_WIND_MILL;
	// Iron chest is a pure Container (no EnergyPort), so no Team Reborn EnergyStorage.SIDED line below.
	public static BlockEntityType<IronChestBlockEntity> IRON_CHEST;
	public static BlockEntityType<StorageModuleBlockEntity> STORAGE_MODULE;
	// Silver chest is likewise a pure Container (no EnergyPort) — no Team Reborn EnergyStorage.SIDED line.
	public static BlockEntityType<SilverChestBlockEntity> SILVER_CHEST;
	// Gold chest is likewise a pure Container (no EnergyPort) — no Team Reborn EnergyStorage.SIDED line.
	public static BlockEntityType<GoldChestBlockEntity> GOLD_CHEST;

	public static void init() {
		GENERATOR = register(ContentManifest.blockEntity("generator", GeneratorBlockEntity.class));
		GEOTHERMAL_GENERATOR = register(ContentManifest.blockEntity("geothermal_generator", GeothermalGeneratorBlockEntity.class));
		SOLAR_PANEL = register(ContentManifest.blockEntity("solar_panel", SolarPanelBlockEntity.class));
		MOONLIT_SOLAR_PANEL = register(ContentManifest.blockEntity("moonlit_solar_panel", MoonlitSolarPanelBlockEntity.class));
		DAYLIGHT_SOLAR_PANEL = register(ContentManifest.blockEntity("daylight_solar_panel", DaylightSolarPanelBlockEntity.class));
		COPPER_CABLE = register(ContentManifest.blockEntity("copper_cable", CableBlockEntity.class));
		ITEM_PIPE = register(ContentManifest.blockEntity("item_pipe", ItemPipeBlockEntity.class));
		FLUID_PIPE = register(ContentManifest.blockEntity("fluid_pipe", FluidPipeBlockEntity.class));
		MACERATOR = register(ContentManifest.blockEntity("macerator", MaceratorBlockEntity.class));
		BATTERY_BOX = register(ContentManifest.blockEntity("battery_box", BatteryBoxBlockEntity.class));
		CESU = register(ContentManifest.blockEntity("cesu", CesuBlockEntity.class));
		TELEPORTER = register(ContentManifest.blockEntity("teleporter", TeleporterBlockEntity.class));
		ELECTRIC_FURNACE = register(ContentManifest.blockEntity("electric_furnace", ElectricFurnaceBlockEntity.class));
		IRON_FURNACE = register(ContentManifest.blockEntity("iron_furnace", IronFurnaceBlockEntity.class));
		EXTRACTOR = register(ContentManifest.blockEntity("extractor", ExtractorBlockEntity.class));
		COMPRESSOR = register(ContentManifest.blockEntity("compressor", CompressorBlockEntity.class));
		SAWMILL = register(ContentManifest.blockEntity("sawmill", SawmillBlockEntity.class));
		ASSEMBLER = register(ContentManifest.blockEntity("assembler", AssemblerBlockEntity.class));
		POLYMERIZER = register(ContentManifest.blockEntity("polymerizer", PolymerizerBlockEntity.class));
		VULCANIZER = register(ContentManifest.blockEntity("vulcanizer", VulcanizerBlockEntity.class));
		ALLOY_SMELTER = register(ContentManifest.blockEntity("alloy_smelter", AlloySmelterBlockEntity.class));
		GALVANIC_BATH = register(ContentManifest.blockEntity("galvanic_bath", GalvanicBathBlockEntity.class));
		ELECTRIC_HEATER = register(ContentManifest.blockEntity("electric_heater", ElectricHeaterBlockEntity.class));
		CHARGE_PAD = register(ContentManifest.blockEntity("charge_pad", ChargePadBlockEntity.class));
		INCUBATOR = register(ContentManifest.blockEntity("incubator", IncubatorBlockEntity.class));
		PUMP = register(ContentManifest.blockEntity("pump", PumpBlockEntity.class));
		GARDEN_DRONE_STATION = register(ContentManifest.blockEntity("garden_drone_station", GardenDroneStationBlockEntity.class));
		FLUID_TANK = register(ContentManifest.blockEntity("fluid_tank", FluidTankBlockEntity.class));
		WATER_MILL = register(ContentManifest.blockEntity("water_mill", WaterMillBlockEntity.class));
		WIND_MILL = register(ContentManifest.blockEntity("wind_mill", WindMillBlockEntity.class));
		HIGH_ALTITUDE_WIND_MILL = register(ContentManifest.blockEntity("high_altitude_wind_mill", HighAltitudeWindMillBlockEntity.class));
		STORM_WIND_MILL = register(ContentManifest.blockEntity("storm_wind_mill", StormWindMillBlockEntity.class));
		IRON_CHEST = register(ContentManifest.blockEntity("iron_chest", IronChestBlockEntity.class));
		STORAGE_MODULE = register(ContentManifest.blockEntity("storage_module", StorageModuleBlockEntity.class));
		SILVER_CHEST = register(ContentManifest.blockEntity("silver_chest", SilverChestBlockEntity.class));
		GOLD_CHEST = register(ContentManifest.blockEntity("gold_chest", GoldChestBlockEntity.class));

			// EnergyStorage.SIDED registration: explicit per-block lines, one per energy-exposing block
			// entity. This is deliberately NOT driven from a shared loader-neutral list: such a list would
			// route through ModContent handles that are only bound by bindModContent() BELOW this point, so
			// reading them here would hit the Unbound placeholder. Fabric's static-init ordering forces the
			// use of the local already-registered BlockEntityType fields directly. (A loader-neutral list
			// was tried and reverted for exactly this ordering reason: the shared list's static init
			// read ModContent handles before the loaders bound them and crashed the runtime.)
			// When adding a powered block entity, add its line here AND in the NeoForge energy loop.
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),GENERATOR);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),GEOTHERMAL_GENERATOR);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),SOLAR_PANEL);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),MOONLIT_SOLAR_PANEL);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),DAYLIGHT_SOLAR_PANEL);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),COPPER_CABLE);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),MACERATOR);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),BATTERY_BOX);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),CESU);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),TELEPORTER);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),ELECTRIC_FURNACE);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),EXTRACTOR);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),COMPRESSOR);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),SAWMILL);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),ASSEMBLER);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),INCUBATOR);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),POLYMERIZER);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),VULCANIZER);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),ALLOY_SMELTER);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),GALVANIC_BATH);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),ELECTRIC_HEATER);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),CHARGE_PAD);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),PUMP);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),GARDEN_DRONE_STATION);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),WATER_MILL);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),WIND_MILL);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),HIGH_ALTITUDE_WIND_MILL);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),STORM_WIND_MILL);

		// MOD-022 registration facade: publish each eagerly-registered BlockEntityType into the
		// loader-neutral ModContent so content BE constructors (which read ModContent.X_BE.get() at
		// runtime) resolve the Fabric instance. NeoForge binds a lazy DeferredHolder into the same handle.
		ModContent.GENERATOR_BE = () -> GENERATOR;
		ModContent.GEOTHERMAL_GENERATOR_BE = () -> GEOTHERMAL_GENERATOR;
		ModContent.SOLAR_PANEL_BE = () -> SOLAR_PANEL;
		ModContent.MOONLIT_SOLAR_PANEL_BE = () -> MOONLIT_SOLAR_PANEL;
		ModContent.DAYLIGHT_SOLAR_PANEL_BE = () -> DAYLIGHT_SOLAR_PANEL;
		ModContent.COPPER_CABLE_BE = () -> COPPER_CABLE;
		ModContent.ITEM_PIPE_BE = () -> ITEM_PIPE;
		ModContent.FLUID_PIPE_BE = () -> FLUID_PIPE;
		ModContent.MACERATOR_BE = () -> MACERATOR;
		ModContent.BATTERY_BOX_BE = () -> BATTERY_BOX;
		ModContent.CESU_BE = () -> CESU;
		ModContent.TELEPORTER_BE = () -> TELEPORTER;
		ModContent.ELECTRIC_FURNACE_BE = () -> ELECTRIC_FURNACE;
		ModContent.IRON_FURNACE_BE = () -> IRON_FURNACE;
		ModContent.EXTRACTOR_BE = () -> EXTRACTOR;
		ModContent.COMPRESSOR_BE = () -> COMPRESSOR;
		ModContent.SAWMILL_BE = () -> SAWMILL;
		ModContent.ASSEMBLER_BE = () -> ASSEMBLER;
		ModContent.POLYMERIZER_BE = () -> POLYMERIZER;
		ModContent.VULCANIZER_BE = () -> VULCANIZER;
		ModContent.ALLOY_SMELTER_BE = () -> ALLOY_SMELTER;
		ModContent.GALVANIC_BATH_BE = () -> GALVANIC_BATH;
		ModContent.ELECTRIC_HEATER_BE = () -> ELECTRIC_HEATER;
		ModContent.CHARGE_PAD_BE = () -> CHARGE_PAD;
		ModContent.INCUBATOR_BE = () -> INCUBATOR;
		ModContent.PUMP_BE = () -> PUMP;
		ModContent.GARDEN_DRONE_STATION_BE = () -> GARDEN_DRONE_STATION;
		ModContent.FLUID_TANK_BE = () -> FLUID_TANK;
		ModContent.WATER_MILL_BE = () -> WATER_MILL;
		ModContent.WIND_MILL_BE = () -> WIND_MILL;
		ModContent.HIGH_ALTITUDE_WIND_MILL_BE = () -> HIGH_ALTITUDE_WIND_MILL;
		ModContent.STORM_WIND_MILL_BE = () -> STORM_WIND_MILL;
		ModContent.IRON_CHEST_BE = () -> IRON_CHEST;
		ModContent.STORAGE_MODULE_BE = () -> STORAGE_MODULE;
		ModContent.SILVER_CHEST_BE = () -> SILVER_CHEST;
		ModContent.GOLD_CHEST_BE = () -> GOLD_CHEST;

		// Fluid (lava) storages: the geothermal generator accepts lava, the pump exposes its tank. Both
		// publish their neutral FluidPort (via FluidPortHost#fluidPort) through the TankAsFluidStorage
		// reverse adapter (MOD-028), mirroring the energy PortAsEnergyStorage lines above.
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), GEOTHERMAL_GENERATOR);
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), PUMP);
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), FLUID_TANK);
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), FLUID_PIPE);
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), POLYMERIZER);
		FluidStorage.SIDED.registerForBlockEntity(
				(be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), GALVANIC_BATH);
	}

	/**
	 * Registers the {@code BlockEntityType} described by the shared manifest (MOD-307). The id, the
	 * factory and the valid-block set all come from {@link ContentManifest#BLOCK_ENTITIES} — before this,
	 * the block set was written out here AND in {@code ModBlockEntitiesNeoForge}, with only a Python
	 * parity script holding the two copies together (the MOD-191 defect: a block missing from one
	 * loader's set is not a type error, it is a silently absent block entity on that loader).
	 *
	 * <p>Fabric registers eagerly, so the blocks are resolved right here — safe because
	 * {@code ModBlocks.init()} runs before {@code ModBlockEntities.init()} in the entrypoint.
	 */
	private static <T extends BlockEntity> BlockEntityType<T> register(ContentManifest.BlockEntityDef<T> def) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Industrialization.id(def.id()),
				new BlockEntityType<>(def.factory(), def.blockSet()));
	}
}
