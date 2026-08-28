package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerHvBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerMvBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnSegmentBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorDoorBlockEntity;
import dev.alaindustrial.block.entity.ReactorOutletBlockEntity;
import dev.alaindustrial.block.entity.ReactorPortBlockEntity;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.block.entity.CrystalFarmControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
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
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.fabric.PortAsEnergyStorage;
import dev.alaindustrial.core.fabric.TankAsFluidStorage;
import dev.alaindustrial.core.fluid.FluidPortHost;
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
	// Iron furnace is fuel-burning, not an EnergyPortHost — so the roster gives it no energy capability.
	public static BlockEntityType<IronFurnaceBlockEntity> IRON_FURNACE;
	public static BlockEntityType<ExtractorBlockEntity> EXTRACTOR;
	public static BlockEntityType<CompressorBlockEntity> COMPRESSOR;
	public static BlockEntityType<ComponentRepairBenchBlockEntity> COMPONENT_REPAIR_BENCH;
	public static BlockEntityType<CanningMachineBlockEntity> CANNING_MACHINE;
	public static BlockEntityType<SawmillBlockEntity> SAWMILL;
	public static BlockEntityType<AssemblerBlockEntity> ASSEMBLER;
	public static BlockEntityType<PolymerizerBlockEntity> POLYMERIZER;
	public static BlockEntityType<DistillationColumnBlockEntity> DISTILLATION_COLUMN;
	public static BlockEntityType<DistillationColumnSegmentBlockEntity> DISTILLATION_COLUMN_SEGMENT;
	public static BlockEntityType<VulcanizerBlockEntity> VULCANIZER;
	public static BlockEntityType<AlloySmelterBlockEntity> ALLOY_SMELTER;
	public static BlockEntityType<GalvanicBathBlockEntity> GALVANIC_BATH;
	public static BlockEntityType<FermenterBlockEntity> FERMENTER;
	public static BlockEntityType<SprinklerBlockEntity> SPRINKLER;
	public static BlockEntityType<ThermalCentrifugeBlockEntity> THERMAL_CENTRIFUGE;
	/** MOD-468, stage 1 — the reactor room's brain. */
	public static BlockEntityType<ReactorControllerBlockEntity> REACTOR_CONTROLLER;
	/** MOD-505 — the crystal greenhouse's brain, and the only ticker a farm has. */
	public static BlockEntityType<CrystalFarmControllerBlockEntity> CRYSTAL_FARM_CONTROLLER;
	public static BlockEntityType<FuelRodAssemblyBlockEntity> FUEL_ROD_ASSEMBLY;
	public static BlockEntityType<ReactorPortBlockEntity> REACTOR_PORT;
	public static BlockEntityType<SteamNozzleBlockEntity> STEAM_NOZZLE;
	public static BlockEntityType<ReactorOutletBlockEntity> REACTOR_OUTLET;
	/** MOD-493 — the airlock panel's travel clock; holds no game state. */
	public static BlockEntityType<ReactorDoorBlockEntity> REACTOR_DOOR;
	public static BlockEntityType<ElectricHeaterBlockEntity> ELECTRIC_HEATER;
	public static BlockEntityType<ChargePadBlockEntity> CHARGE_PAD;
	public static BlockEntityType<EnergyCondenserBlockEntity> ENERGY_CONDENSER;
	public static BlockEntityType<MobRepellerBlockEntity> MOB_REPELLER;
	public static BlockEntityType<MobRepellerMvBlockEntity> MOB_REPELLER_MV;
	public static BlockEntityType<MobRepellerHvBlockEntity> MOB_REPELLER_HV;
	public static BlockEntityType<IncubatorBlockEntity> INCUBATOR;
	public static BlockEntityType<PumpBlockEntity> PUMP;
	public static BlockEntityType<GardenDroneStationBlockEntity> GARDEN_DRONE_STATION;
	public static BlockEntityType<FluidTankBlockEntity> FLUID_TANK;
	public static BlockEntityType<WaterMillBlockEntity> WATER_MILL;
	public static BlockEntityType<WindMillBlockEntity> WIND_MILL;
	public static BlockEntityType<HighAltitudeWindMillBlockEntity> HIGH_ALTITUDE_WIND_MILL;
	public static BlockEntityType<StormWindMillBlockEntity> STORM_WIND_MILL;
	public static BlockEntityType<LightningRodGeneratorBlockEntity> LIGHTNING_ROD_GENERATOR;
	public static BlockEntityType<CreativeEnergySourceBlockEntity> CREATIVE_ENERGY_SOURCE;
	// Iron chest is a pure Container (no EnergyPortHost), so the roster gives it no energy capability.
	public static BlockEntityType<IronChestBlockEntity> IRON_CHEST;
	public static BlockEntityType<StorageModuleBlockEntity> STORAGE_MODULE;
	// Silver chest is likewise a pure Container (no EnergyPortHost) — no energy capability.
	public static BlockEntityType<SilverChestBlockEntity> SILVER_CHEST;
	// Gold chest is likewise a pure Container (no EnergyPortHost) — no energy capability.
	public static BlockEntityType<GoldChestBlockEntity> GOLD_CHEST;
	public static BlockEntityType<ElectrumChestBlockEntity> ELECTRUM_CHEST;
	// MOD-474 — a pure Container as well; its shielding is a radiation rule, not a capability.
	public static BlockEntityType<ShieldingChestBlockEntity> SHIELDING_CHEST;

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
		COMPONENT_REPAIR_BENCH = register(ContentManifest.blockEntity("component_repair_bench", ComponentRepairBenchBlockEntity.class));
		CANNING_MACHINE = register(ContentManifest.blockEntity("canning_machine", CanningMachineBlockEntity.class));
		SAWMILL = register(ContentManifest.blockEntity("sawmill", SawmillBlockEntity.class));
		ASSEMBLER = register(ContentManifest.blockEntity("assembler", AssemblerBlockEntity.class));
		POLYMERIZER = register(ContentManifest.blockEntity("polymerizer", PolymerizerBlockEntity.class));
		DISTILLATION_COLUMN = register(ContentManifest.blockEntity("distillation_column", DistillationColumnBlockEntity.class));
		DISTILLATION_COLUMN_SEGMENT = register(ContentManifest.blockEntity("distillation_column_segment", DistillationColumnSegmentBlockEntity.class));
		VULCANIZER = register(ContentManifest.blockEntity("vulcanizer", VulcanizerBlockEntity.class));
		ALLOY_SMELTER = register(ContentManifest.blockEntity("alloy_smelter", AlloySmelterBlockEntity.class));
		GALVANIC_BATH = register(ContentManifest.blockEntity("galvanic_bath", GalvanicBathBlockEntity.class));
		FERMENTER = register(ContentManifest.blockEntity("fermenter", FermenterBlockEntity.class));
		SPRINKLER = register(ContentManifest.blockEntity("sprinkler", SprinklerBlockEntity.class));
		THERMAL_CENTRIFUGE = register(ContentManifest.blockEntity("thermal_centrifuge", ThermalCentrifugeBlockEntity.class));
		REACTOR_CONTROLLER =
				register(ContentManifest.blockEntity("reactor_controller", ReactorControllerBlockEntity.class));
		CRYSTAL_FARM_CONTROLLER = register(
				ContentManifest.blockEntity("crystal_farm_controller", CrystalFarmControllerBlockEntity.class));
		FUEL_ROD_ASSEMBLY =
				register(ContentManifest.blockEntity("fuel_rod_assembly", FuelRodAssemblyBlockEntity.class));
		REACTOR_PORT =
				register(ContentManifest.blockEntity("reactor_port", ReactorPortBlockEntity.class));
		STEAM_NOZZLE =
				register(ContentManifest.blockEntity("steam_nozzle", SteamNozzleBlockEntity.class));
		REACTOR_OUTLET =
				register(ContentManifest.blockEntity("reactor_outlet", ReactorOutletBlockEntity.class));
		REACTOR_DOOR =
				register(ContentManifest.blockEntity("reactor_door", ReactorDoorBlockEntity.class));
		ELECTRIC_HEATER = register(ContentManifest.blockEntity("electric_heater", ElectricHeaterBlockEntity.class));
		CHARGE_PAD = register(ContentManifest.blockEntity("charge_pad", ChargePadBlockEntity.class));
		ENERGY_CONDENSER = register(ContentManifest.blockEntity("energy_condenser", EnergyCondenserBlockEntity.class));
		MOB_REPELLER = register(ContentManifest.blockEntity("mob_repeller", MobRepellerBlockEntity.class));
		MOB_REPELLER_MV = register(ContentManifest.blockEntity("mob_repeller_mv", MobRepellerMvBlockEntity.class));
		MOB_REPELLER_HV = register(ContentManifest.blockEntity("mob_repeller_hv", MobRepellerHvBlockEntity.class));
		INCUBATOR = register(ContentManifest.blockEntity("incubator", IncubatorBlockEntity.class));
		PUMP = register(ContentManifest.blockEntity("pump", PumpBlockEntity.class));
		GARDEN_DRONE_STATION = register(ContentManifest.blockEntity("garden_drone_station", GardenDroneStationBlockEntity.class));
		FLUID_TANK = register(ContentManifest.blockEntity("fluid_tank", FluidTankBlockEntity.class));
		WATER_MILL = register(ContentManifest.blockEntity("water_mill", WaterMillBlockEntity.class));
		WIND_MILL = register(ContentManifest.blockEntity("wind_mill", WindMillBlockEntity.class));
		HIGH_ALTITUDE_WIND_MILL = register(ContentManifest.blockEntity("high_altitude_wind_mill", HighAltitudeWindMillBlockEntity.class));
		STORM_WIND_MILL = register(ContentManifest.blockEntity("storm_wind_mill", StormWindMillBlockEntity.class));
		LIGHTNING_ROD_GENERATOR = register(ContentManifest.blockEntity("lightning_rod_generator",
				LightningRodGeneratorBlockEntity.class));
		CREATIVE_ENERGY_SOURCE = register(ContentManifest.blockEntity("creative_energy_source",
				CreativeEnergySourceBlockEntity.class));
		IRON_CHEST = register(ContentManifest.blockEntity("iron_chest", IronChestBlockEntity.class));
		STORAGE_MODULE = register(ContentManifest.blockEntity("storage_module", StorageModuleBlockEntity.class));
		SILVER_CHEST = register(ContentManifest.blockEntity("silver_chest", SilverChestBlockEntity.class));
		GOLD_CHEST = register(ContentManifest.blockEntity("gold_chest", GoldChestBlockEntity.class));
		ELECTRUM_CHEST = register(ContentManifest.blockEntity("electrum_chest", ElectrumChestBlockEntity.class));
		SHIELDING_CHEST = register(ContentManifest.blockEntity("shielding_chest", ShieldingChestBlockEntity.class));

		// MOD-403: the 40 `ModContent.X_BE = () -> X;` lines that used to sit here are gone — each
		// BLOCK_ENTITIES entry carries its own `bind`, applied by register() below, so a handle can no
		// longer be left on its throwing placeholder because someone forgot a line.

		// MOD-433: capabilities are derived from the manifest by INTERFACE, not named per block. Every
		// block entity that implements EnergyPortHost publishes its neutral EnergyPort through Team
		// Reborn's EnergyStorage.SIDED via the PortAsEnergyStorage reverse adapter (minus the two pipes —
		// see BlockCapabilityRoster.NO_ENERGY_CAPABILITY); every FluidPortHost publishes its FluidPort
		// through FluidStorage.SIDED via TankAsFluidStorage. NeoForge replays the same rosters through
		// RegisterCapabilitiesEvent. Before this, 36 + 8 hand-written lines lived here and 35 + 8 more on
		// NeoForge, and they had already drifted (the CESU was missing from the NeoForge energy list).
		//
		// This is safe where the earlier "shared list" attempt was not: the roster reads only the
		// manifest's Class objects, and def.registeredType() resolves the live BlockEntityType from the
		// vanilla registry — populated by the register(...) calls above — never a ModContent handle.
		// Item storage needs no line on Fabric: ItemStorage.SIDED wraps any Container through its
		// global fallback (the chests' combined view is the one explicit provider, in the entrypoint).
		for (ContentManifest.BlockEntityDef<?> def : BlockCapabilityRoster.energyHosts()) {
			bindEnergy(def);
		}
		for (ContentManifest.BlockEntityDef<?> def : BlockCapabilityRoster.fluidHosts()) {
			bindFluid(def);
		}
	}

	/**
	 * Publishes {@code def}'s neutral energy port through {@code EnergyStorage.SIDED}; the roster guarantees the cast.
	 *
	 * <p>MOD-448: {@code dir} is nullable here — {@code BlockApiLookup#find} lets a caller ask without naming
	 * a side, and viewer mods (Jade) do exactly that on every block under the crosshair. The nullable case
	 * is answered by {@code energyPortForLookup}, which documents the contract for both loaders; handing the
	 * null to {@code energyPort} threw inside the implementation (NPE in
	 * {@code EnergyCondenserBlockEntity.energyRoleForFace}) — a defect that predates the manifest derivation
	 * and lived in the per-block registrations before it.
	 */
	private static <T extends BlockEntity> void bindEnergy(ContentManifest.BlockEntityDef<T> def) {
		EnergyStorage.SIDED.registerForBlockEntity(
				(be, dir) -> PortAsEnergyStorage.of(((EnergyPortHost) be).energyPortForLookup(dir)),
				def.registeredType());
	}

	/**
	 * Publishes {@code def}'s neutral fluid port through {@code FluidStorage.SIDED}; the roster guarantees the cast.
	 * The side-less query goes through {@code fluidPortForLookup} for the reason given on {@link #bindEnergy} (MOD-448).
	 */
	private static <T extends BlockEntity> void bindFluid(ContentManifest.BlockEntityDef<T> def) {
		FluidStorage.SIDED.registerForBlockEntity(
				(be, dir) -> TankAsFluidStorage.of(((FluidPortHost) be).fluidPortForLookup(dir)),
				def.registeredType());
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
	 *
	 * <p><b>MOD-403.</b> The registered type is also published into the entry's {@link ModContent} slot
	 * here, via the {@code bind} the manifest carries. That replaces 40 hand-written
	 * {@code ModContent.X_BE = () -> X;} lines per loader whose only guard was a startup crash.
	 */
	private static <T extends BlockEntity> BlockEntityType<T> register(ContentManifest.BlockEntityDef<T> def) {
		BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
				Industrialization.id(def.id()), new BlockEntityType<>(def.factory(), def.blockSet()));
		def.bind().accept(() -> type);
		return type;
	}
}
