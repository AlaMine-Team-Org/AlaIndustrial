package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnSegmentBlockEntity;
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
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge {@code BlockEntityType} registration (MOD-022 registration-facade). Mirrors the Fabric
 * {@code dev.alaindustrial.registry.ModBlockEntities} set 1:1 (same ids, same {@code BlockEntity}
 * factories, same valid-block sets) over {@link Registries#BLOCK_ENTITY_TYPE} — the real BE types from
 * {@code common}, not stubs. The per-face energy capability for each type is bound separately in
 * {@code IndustrializationNeoForge#registerCapabilities}.
 *
 * <p><b>Geothermal generator and pump BE types (MOD-028).</b> {@code GeothermalGeneratorBlockEntity} and
 * {@code PumpBlockEntity} now live in {@code common} on the neutral {@code FluidPort}/{@code FluidTank}
 * abstraction, so their types are registered here like every other machine. Their per-face fluid
 * capability is bound separately in {@code IndustrializationNeoForge#registerCapabilities}, alongside
 * energy.
 *
 * <p><b>Split constraint (verified 26.2 API):</b> the {@code DeferredRegister} object and its
 * {@code register(modBus)} call must live on the {@code neoforge} side.
 *
 * <p><b>Verified 26.2 API (neoforge/minecraft 26.2.0.8-beta):</b> a {@code BlockEntityType} is built with
 * the varargs constructor {@code new BlockEntityType<>(factory, onlyOpCanSetNbt, validBlocks...)} — no
 * datafixer {@code Type}. The blocks are stored in a {@code Set} and only read at runtime
 * ({@code isValid}), never validated for registry membership at construction — so {@link #register} can
 * safely resolve the deferred blocks inside the type supplier (see its javadoc).
 */
public final class ModBlockEntitiesNeoForge {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Industrialization.MOD_ID);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR =
			register(ContentManifest.blockEntity("generator", GeneratorBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
			register(ContentManifest.blockEntity("solar_panel", SolarPanelBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoonlitSolarPanelBlockEntity>> MOONLIT_SOLAR_PANEL =
			register(ContentManifest.blockEntity("moonlit_solar_panel", MoonlitSolarPanelBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DaylightSolarPanelBlockEntity>> DAYLIGHT_SOLAR_PANEL =
			register(ContentManifest.blockEntity("daylight_solar_panel", DaylightSolarPanelBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> COPPER_CABLE =
			register(ContentManifest.blockEntity("copper_cable", CableBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemPipeBlockEntity>> ITEM_PIPE =
			register(ContentManifest.blockEntity("item_pipe", ItemPipeBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPipeBlockEntity>> FLUID_PIPE =
			register(ContentManifest.blockEntity("fluid_pipe", FluidPipeBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaceratorBlockEntity>> MACERATOR =
			register(ContentManifest.blockEntity("macerator", MaceratorBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBoxBlockEntity>> BATTERY_BOX =
			register(ContentManifest.blockEntity("battery_box", BatteryBoxBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CesuBlockEntity>> CESU =
			register(ContentManifest.blockEntity("cesu", CesuBlockEntity.class));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeleporterBlockEntity>> TELEPORTER =
			register(ContentManifest.blockEntity("teleporter", TeleporterBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
			register(ContentManifest.blockEntity("electric_furnace", ElectricFurnaceBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IronFurnaceBlockEntity>> IRON_FURNACE =
			register(ContentManifest.blockEntity("iron_furnace", IronFurnaceBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtractorBlockEntity>> EXTRACTOR =
			register(ContentManifest.blockEntity("extractor", ExtractorBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompressorBlockEntity>> COMPRESSOR =
			register(ContentManifest.blockEntity("compressor", CompressorBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CanningMachineBlockEntity>> CANNING_MACHINE =
			register(ContentManifest.blockEntity("canning_machine", CanningMachineBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SawmillBlockEntity>> SAWMILL =
			register(ContentManifest.blockEntity("sawmill", SawmillBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLER =
			register(ContentManifest.blockEntity("assembler", AssemblerBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PolymerizerBlockEntity>> POLYMERIZER =
			register(ContentManifest.blockEntity("polymerizer", PolymerizerBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistillationColumnBlockEntity>> DISTILLATION_COLUMN =
			register(ContentManifest.blockEntity("distillation_column", DistillationColumnBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistillationColumnSegmentBlockEntity>> DISTILLATION_COLUMN_SEGMENT =
			register(ContentManifest.blockEntity("distillation_column_segment", DistillationColumnSegmentBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VulcanizerBlockEntity>> VULCANIZER =
			register(ContentManifest.blockEntity("vulcanizer", VulcanizerBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlloySmelterBlockEntity>> ALLOY_SMELTER =
			register(ContentManifest.blockEntity("alloy_smelter", AlloySmelterBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GalvanicBathBlockEntity>>
			GALVANIC_BATH = register(ContentManifest.blockEntity("galvanic_bath", GalvanicBathBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricHeaterBlockEntity>> ELECTRIC_HEATER =
			register(ContentManifest.blockEntity("electric_heater", ElectricHeaterBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargePadBlockEntity>> CHARGE_PAD =
			register(ContentManifest.blockEntity("charge_pad", ChargePadBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyCondenserBlockEntity>> ENERGY_CONDENSER =
			register(ContentManifest.blockEntity("energy_condenser", EnergyCondenserBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IncubatorBlockEntity>> INCUBATOR =
			register(ContentManifest.blockEntity("incubator", IncubatorBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeothermalGeneratorBlockEntity>> GEOTHERMAL_GENERATOR =
			register(ContentManifest.blockEntity("geothermal_generator", GeothermalGeneratorBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> PUMP =
			register(ContentManifest.blockEntity("pump", PumpBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GardenDroneStationBlockEntity>>
			GARDEN_DRONE_STATION = register(ContentManifest.blockEntity("garden_drone_station", GardenDroneStationBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTankBlockEntity>> FLUID_TANK =
			register(ContentManifest.blockEntity("fluid_tank", FluidTankBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterMillBlockEntity>> WATER_MILL =
			register(ContentManifest.blockEntity("water_mill", WaterMillBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindMillBlockEntity>> WIND_MILL =
			register(ContentManifest.blockEntity("wind_mill", WindMillBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HighAltitudeWindMillBlockEntity>> HIGH_ALTITUDE_WIND_MILL =
			register(ContentManifest.blockEntity("high_altitude_wind_mill", HighAltitudeWindMillBlockEntity.class));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StormWindMillBlockEntity>> STORM_WIND_MILL =
			register(ContentManifest.blockEntity("storm_wind_mill", StormWindMillBlockEntity.class));
	// Pure container (no EnergyPort) — no capability binding in IndustrializationNeoForge#registerCapabilities.
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IronChestBlockEntity>> IRON_CHEST =
			register(ContentManifest.blockEntity("iron_chest", IronChestBlockEntity.class));
	// Pure container (no EnergyPort) — no capability binding. MOD-287 modular warehouse.
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageModuleBlockEntity>> STORAGE_MODULE =
			register(ContentManifest.blockEntity("storage_module", StorageModuleBlockEntity.class));
	// Pure container (no EnergyPort) — no capability binding. Silver chest = tier above the iron chest.
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SilverChestBlockEntity>> SILVER_CHEST =
			register(ContentManifest.blockEntity("silver_chest", SilverChestBlockEntity.class));
	// Pure container (no EnergyPort) — no capability binding. Gold chest = tier above the silver chest.
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GoldChestBlockEntity>> GOLD_CHEST =
			register(ContentManifest.blockEntity("gold_chest", GoldChestBlockEntity.class));
	// Electrum chest = tier above the gold chest; also a pure container, so also no capability binding.
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrumChestBlockEntity>> ELECTRUM_CHEST =
			register(ContentManifest.blockEntity("electrum_chest", ElectrumChestBlockEntity.class));

	private ModBlockEntitiesNeoForge() {
	}

	/**
	 * Class-load trigger for the {@code @Mod} constructor. Since MOD-403 the {@link ModContent} binding
	 * happens inside {@link #register} — each {@code BLOCK_ENTITIES} entry carries its own {@code bind} —
	 * so the 40 hand-written {@code ModContent.X_BE = X::get;} assignments that used to live here are gone,
	 * along with the failure mode where forgetting one showed up only as a {@code verifyAllBound()} crash.
	 */
	public static void init() {
		// Intentionally empty: touching this class runs the static registrations above, which bind
		// ModContent themselves. Kept because the entrypoint calls it in a fixed, documented order.
	}

	/**
	 * Registers the {@code BlockEntityType} described by the shared manifest (MOD-307): id, factory and
	 * valid-block set all come from {@link ContentManifest#BLOCK_ENTITIES}. Before this, the block set was
	 * spelled out here AND in the Fabric {@code ModBlockEntities}, held together only by a Python parity
	 * script — the MOD-191 defect, where a block missing from one loader's set is not a type error but a
	 * silently absent block entity on that loader.
	 *
	 * <p><b>Timing (the chicken-and-egg guard, unchanged).</b> On NeoForge a block only resolves after its
	 * {@code RegisterEvent} — later than this method is <i>called</i> (static init of this class). So the
	 * manifest ids are resolved <b>inside</b> the deferred type supplier, which the register invokes only
	 * when the block-entity {@code RegisterEvent} fires, by which point every block is registered. That is
	 * the same guarantee the previous {@code Supplier<Block>} handles relied on.
	 *
	 * <p><b>MOD-403.</b> The entry's {@link ModContent} slot is bound here too, via {@code holder::get}: a
	 * {@code DeferredHolder<_, BlockEntityType<X>>} is a {@code Supplier<BlockEntityType<X>>} while the
	 * slot is {@code Supplier<BlockEntityType<?>>} — generics are invariant, so the method reference
	 * bridges the wildcard while staying lazy. Binding at class load is legal for the same reason it was
	 * in {@code init()}: the holder is a handle, not the value.
	 */
	public static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(
			ContentManifest.BlockEntityDef<T> def) {
		DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> holder =
				BLOCK_ENTITIES.register(def.id(), () -> new BlockEntityType<>(def.factory(), def.blockSet()));
		def.bind().accept(holder::get);
		return holder;
	}
}
