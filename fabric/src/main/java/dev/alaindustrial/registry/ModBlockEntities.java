package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ExtractorBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
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
	public static BlockEntityType<MaceratorBlockEntity> MACERATOR;
	public static BlockEntityType<BatteryBoxBlockEntity> BATTERY_BOX;
	public static BlockEntityType<ElectricFurnaceBlockEntity> ELECTRIC_FURNACE;
	public static BlockEntityType<ExtractorBlockEntity> EXTRACTOR;
	public static BlockEntityType<CompressorBlockEntity> COMPRESSOR;
	public static BlockEntityType<PumpBlockEntity> PUMP;
	public static BlockEntityType<WaterMillBlockEntity> WATER_MILL;
	public static BlockEntityType<WindMillBlockEntity> WIND_MILL;
	// Iron chest is a pure Container (no EnergyPort), so no Team Reborn EnergyStorage.SIDED line below.
	public static BlockEntityType<IronChestBlockEntity> IRON_CHEST;

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
		MACERATOR = register("macerator",
				new BlockEntityType<>(MaceratorBlockEntity::new, Set.of(ModBlocks.MACERATOR)));
		BATTERY_BOX = register("battery_box",
				new BlockEntityType<>(BatteryBoxBlockEntity::new, Set.of(ModBlocks.BATTERY_BOX)));
		ELECTRIC_FURNACE = register("electric_furnace",
				new BlockEntityType<>(ElectricFurnaceBlockEntity::new, Set.of(ModBlocks.ELECTRIC_FURNACE)));
		EXTRACTOR = register("extractor",
				new BlockEntityType<>(ExtractorBlockEntity::new, Set.of(ModBlocks.EXTRACTOR)));
		COMPRESSOR = register("compressor",
				new BlockEntityType<>(CompressorBlockEntity::new, Set.of(ModBlocks.COMPRESSOR)));
		PUMP = register("pump",
				new BlockEntityType<>(PumpBlockEntity::new, Set.of(ModBlocks.PUMP)));
		WATER_MILL = register("water_mill",
				new BlockEntityType<>(WaterMillBlockEntity::new, Set.of(ModBlocks.WATER_MILL)));
		WIND_MILL = register("wind_mill",
				new BlockEntityType<>(WindMillBlockEntity::new, Set.of(ModBlocks.WIND_MILL)));
		IRON_CHEST = register("iron_chest",
				new BlockEntityType<>(IronChestBlockEntity::new, Set.of(ModBlocks.IRON_CHEST)));

		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),GENERATOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),GEOTHERMAL_GENERATOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),SOLAR_PANEL);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),MOONLIT_SOLAR_PANEL);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),DAYLIGHT_SOLAR_PANEL);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),COPPER_CABLE);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),MACERATOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),BATTERY_BOX);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),ELECTRIC_FURNACE);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),EXTRACTOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),COMPRESSOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),PUMP);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),WATER_MILL);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)),WIND_MILL);

		// MOD-022 registration facade: publish each eagerly-registered BlockEntityType into the
		// loader-neutral ModContent so content BE constructors (which read ModContent.X_BE.get() at
		// runtime) resolve the Fabric instance. NeoForge binds a lazy DeferredHolder into the same handle.
		ModContent.GENERATOR_BE = () -> GENERATOR;
		ModContent.GEOTHERMAL_GENERATOR_BE = () -> GEOTHERMAL_GENERATOR;
		ModContent.SOLAR_PANEL_BE = () -> SOLAR_PANEL;
		ModContent.MOONLIT_SOLAR_PANEL_BE = () -> MOONLIT_SOLAR_PANEL;
		ModContent.DAYLIGHT_SOLAR_PANEL_BE = () -> DAYLIGHT_SOLAR_PANEL;
		ModContent.COPPER_CABLE_BE = () -> COPPER_CABLE;
		ModContent.MACERATOR_BE = () -> MACERATOR;
		ModContent.BATTERY_BOX_BE = () -> BATTERY_BOX;
		ModContent.ELECTRIC_FURNACE_BE = () -> ELECTRIC_FURNACE;
		ModContent.EXTRACTOR_BE = () -> EXTRACTOR;
		ModContent.COMPRESSOR_BE = () -> COMPRESSOR;
		ModContent.PUMP_BE = () -> PUMP;
		ModContent.WATER_MILL_BE = () -> WATER_MILL;
		ModContent.WIND_MILL_BE = () -> WIND_MILL;
		ModContent.IRON_CHEST_BE = () -> IRON_CHEST;

		// Fluid (lava) storages: the geothermal generator accepts lava, the pump exposes its tank. Both
		// publish their neutral FluidPort (via FluidPortHost#fluidPort) through the TankAsFluidStorage
		// reverse adapter (MOD-028), mirroring the energy PortAsEnergyStorage lines above.
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), GEOTHERMAL_GENERATOR);
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), PUMP);
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String path, BlockEntityType<T> type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Industrialization.id(path), type);
	}
}
