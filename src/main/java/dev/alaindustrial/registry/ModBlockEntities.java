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
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import team.reborn.energy.api.EnergyStorage;

/**
 * Central registration for Industrialization {@link BlockEntityType}s, plus the Team Reborn Energy
 * {@code SIDED} lookup that exposes each machine's buffer to the energy network.
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

		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), GENERATOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), GEOTHERMAL_GENERATOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), SOLAR_PANEL);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), MOONLIT_SOLAR_PANEL);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), DAYLIGHT_SOLAR_PANEL);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), COPPER_CABLE);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), MACERATOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), BATTERY_BOX);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), ELECTRIC_FURNACE);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), EXTRACTOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), COMPRESSOR);
		EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> be.energyPort(dir), PUMP);

		// Fluid (lava) storages: the geothermal generator accepts lava, the pump exposes its tank.
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> be.fluidTank, GEOTHERMAL_GENERATOR);
		FluidStorage.SIDED.registerForBlockEntity((be, dir) -> be.fluidTank, PUMP);
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String path, BlockEntityType<T> type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Industrialization.id(path), type);
	}
}
