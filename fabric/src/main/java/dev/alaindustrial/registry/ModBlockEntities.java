package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ExtractorBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.core.fabric.PortAsEnergyStorage;
import dev.alaindustrial.core.fabric.TankAsFluidStorage;
import java.util.Set;
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
	public static BlockEntityType<MaceratorBlockEntity> MACERATOR;
	public static BlockEntityType<BatteryBoxBlockEntity> BATTERY_BOX;
	public static BlockEntityType<TeleporterBlockEntity> TELEPORTER;
	public static BlockEntityType<ElectricFurnaceBlockEntity> ELECTRIC_FURNACE;
	// Iron furnace is fuel-burning, not an EnergyPort — so no Team Reborn EnergyStorage.SIDED line below.
	public static BlockEntityType<IronFurnaceBlockEntity> IRON_FURNACE;
	public static BlockEntityType<ExtractorBlockEntity> EXTRACTOR;
	public static BlockEntityType<CompressorBlockEntity> COMPRESSOR;
	public static BlockEntityType<PumpBlockEntity> PUMP;
	public static BlockEntityType<FluidTankBlockEntity> FLUID_TANK;
	public static BlockEntityType<WaterMillBlockEntity> WATER_MILL;
	public static BlockEntityType<WindMillBlockEntity> WIND_MILL;
	public static BlockEntityType<HighAltitudeWindMillBlockEntity> HIGH_ALTITUDE_WIND_MILL;
	public static BlockEntityType<StormWindMillBlockEntity> STORM_WIND_MILL;
	// Iron chest is a pure Container (no EnergyPort), so no Team Reborn EnergyStorage.SIDED line below.
	public static BlockEntityType<IronChestBlockEntity> IRON_CHEST;
	// Silver chest is likewise a pure Container (no EnergyPort) — no Team Reborn EnergyStorage.SIDED line.
	public static BlockEntityType<SilverChestBlockEntity> SILVER_CHEST;
	// Gold chest is likewise a pure Container (no EnergyPort) — no Team Reborn EnergyStorage.SIDED line.
	public static BlockEntityType<GoldChestBlockEntity> GOLD_CHEST;

	public static void init() {
		GENERATOR = register("generator",
				new BlockEntityType<>(GeneratorBlockEntity::new, Set.of(ModBlocks.GENERATOR)));
		GEOTHERMAL_GENERATOR = register("geothermal_generator",
				new BlockEntityType<>(GeothermalGeneratorBlockEntity::new, Set.of(ModBlocks.GEOTHERMAL_GENERATOR)));
		SOLAR_PANEL = register("solar_panel",
				new BlockEntityType<>(SolarPanelBlockEntity::new, Set.of(ModBlocks.SOLAR_PANEL)));
		MOONLIT_SOLAR_PANEL = register("moonlit_solar_panel",
				new BlockEntityType<>(MoonlitSolarPanelBlockEntity::new, Set.of(ModBlocks.MOONLIT_SOLAR_PANEL)));
		DAYLIGHT_SOLAR_PANEL = register("daylight_solar_panel",
				new BlockEntityType<>(DaylightSolarPanelBlockEntity::new, Set.of(ModBlocks.DAYLIGHT_SOLAR_PANEL)));
		COPPER_CABLE = register("copper_cable",
				new BlockEntityType<>(CableBlockEntity::new, Set.of(ModBlocks.COPPER_CABLE,
						ModBlocks.TIN_CABLE, ModBlocks.INSULATED_COPPER_CABLE, ModBlocks.INSULATED_TIN_CABLE)));
		ITEM_PIPE = register("item_pipe", new BlockEntityType<>(ItemPipeBlockEntity::new, Set.of(ModBlocks.ITEM_PIPE)));
		MACERATOR = register("macerator",
				new BlockEntityType<>(MaceratorBlockEntity::new, Set.of(ModBlocks.MACERATOR)));
		BATTERY_BOX = register("battery_box",
				new BlockEntityType<>(BatteryBoxBlockEntity::new, Set.of(ModBlocks.BATTERY_BOX)));
		TELEPORTER = register("teleporter",
				new BlockEntityType<>(TeleporterBlockEntity::new, Set.of(ModBlocks.TELEPORTER)));
		ELECTRIC_FURNACE = register("electric_furnace",
				new BlockEntityType<>(ElectricFurnaceBlockEntity::new, Set.of(ModBlocks.ELECTRIC_FURNACE)));
		IRON_FURNACE = register("iron_furnace",
				new BlockEntityType<>(IronFurnaceBlockEntity::new, Set.of(ModBlocks.IRON_FURNACE)));
		EXTRACTOR = register("extractor",
				new BlockEntityType<>(ExtractorBlockEntity::new, Set.of(ModBlocks.EXTRACTOR)));
		COMPRESSOR = register("compressor",
				new BlockEntityType<>(CompressorBlockEntity::new, Set.of(ModBlocks.COMPRESSOR)));
		PUMP = register("pump",
				new BlockEntityType<>(PumpBlockEntity::new, Set.of(ModBlocks.PUMP)));
		FLUID_TANK = register("fluid_tank",
				new BlockEntityType<>(FluidTankBlockEntity::new, Set.of(ModBlocks.FLUID_TANK)));
		WATER_MILL = register("water_mill",
				new BlockEntityType<>(WaterMillBlockEntity::new, Set.of(ModBlocks.WATER_MILL)));
		WIND_MILL = register("wind_mill",
				new BlockEntityType<>(WindMillBlockEntity::new, Set.of(ModBlocks.WIND_MILL)));
		HIGH_ALTITUDE_WIND_MILL = register("high_altitude_wind_mill",
				new BlockEntityType<>(HighAltitudeWindMillBlockEntity::new, Set.of(ModBlocks.HIGH_ALTITUDE_WIND_MILL)));
		STORM_WIND_MILL = register("storm_wind_mill",
				new BlockEntityType<>(StormWindMillBlockEntity::new, Set.of(ModBlocks.STORM_WIND_MILL)));
		IRON_CHEST = register("iron_chest",
				new BlockEntityType<>(IronChestBlockEntity::new, Set.of(ModBlocks.IRON_CHEST)));
		SILVER_CHEST = register("silver_chest",
				new BlockEntityType<>(SilverChestBlockEntity::new, Set.of(ModBlocks.SILVER_CHEST)));
		GOLD_CHEST = register("gold_chest",
				new BlockEntityType<>(GoldChestBlockEntity::new, Set.of(ModBlocks.GOLD_CHEST)));

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
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),TELEPORTER);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),ELECTRIC_FURNACE);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),EXTRACTOR);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),COMPRESSOR);
			EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),PUMP);
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
		ModContent.MACERATOR_BE = () -> MACERATOR;
		ModContent.BATTERY_BOX_BE = () -> BATTERY_BOX;
		ModContent.TELEPORTER_BE = () -> TELEPORTER;
		ModContent.ELECTRIC_FURNACE_BE = () -> ELECTRIC_FURNACE;
		ModContent.IRON_FURNACE_BE = () -> IRON_FURNACE;
		ModContent.EXTRACTOR_BE = () -> EXTRACTOR;
		ModContent.COMPRESSOR_BE = () -> COMPRESSOR;
		ModContent.PUMP_BE = () -> PUMP;
		ModContent.FLUID_TANK_BE = () -> FLUID_TANK;
		ModContent.WATER_MILL_BE = () -> WATER_MILL;
		ModContent.WIND_MILL_BE = () -> WIND_MILL;
		ModContent.HIGH_ALTITUDE_WIND_MILL_BE = () -> HIGH_ALTITUDE_WIND_MILL;
		ModContent.STORM_WIND_MILL_BE = () -> STORM_WIND_MILL;
		ModContent.IRON_CHEST_BE = () -> IRON_CHEST;
		ModContent.SILVER_CHEST_BE = () -> SILVER_CHEST;
		ModContent.GOLD_CHEST_BE = () -> GOLD_CHEST;

		// Fluid (lava) storages: the geothermal generator accepts lava, the pump exposes its tank. Both
		// publish their neutral FluidPort (via FluidPortHost#fluidPort) through the TankAsFluidStorage
		// reverse adapter (MOD-028), mirroring the energy PortAsEnergyStorage lines above.
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), GEOTHERMAL_GENERATOR);
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), PUMP);
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), FLUID_TANK);
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String path, BlockEntityType<T> type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Industrialization.id(path), type);
	}
}
