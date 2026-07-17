package dev.alaindustrial.registry.neoforge;

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
import dev.alaindustrial.registry.ModContent;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
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
			register("generator", GeneratorBlockEntity::new, ModBlocksNeoForge.GENERATOR);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
			register("solar_panel", SolarPanelBlockEntity::new, ModBlocksNeoForge.SOLAR_PANEL);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoonlitSolarPanelBlockEntity>> MOONLIT_SOLAR_PANEL =
			register("moonlit_solar_panel", MoonlitSolarPanelBlockEntity::new, ModBlocksNeoForge.MOONLIT_SOLAR_PANEL);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DaylightSolarPanelBlockEntity>> DAYLIGHT_SOLAR_PANEL =
			register("daylight_solar_panel", DaylightSolarPanelBlockEntity::new, ModBlocksNeoForge.DAYLIGHT_SOLAR_PANEL);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> COPPER_CABLE =
			register("copper_cable", CableBlockEntity::new,
					ModBlocksNeoForge.COPPER_CABLE, ModBlocksNeoForge.TIN_CABLE,
					ModBlocksNeoForge.INSULATED_COPPER_CABLE, ModBlocksNeoForge.INSULATED_TIN_CABLE);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemPipeBlockEntity>> ITEM_PIPE =
			register("item_pipe", ItemPipeBlockEntity::new, ModBlocksNeoForge.ITEM_PIPE);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaceratorBlockEntity>> MACERATOR =
			register("macerator", MaceratorBlockEntity::new, ModBlocksNeoForge.MACERATOR);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBoxBlockEntity>> BATTERY_BOX =
			register("battery_box", BatteryBoxBlockEntity::new, ModBlocksNeoForge.BATTERY_BOX);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeleporterBlockEntity>> TELEPORTER =
			register("teleporter", TeleporterBlockEntity::new, ModBlocksNeoForge.TELEPORTER);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
			register("electric_furnace", ElectricFurnaceBlockEntity::new, ModBlocksNeoForge.ELECTRIC_FURNACE);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtractorBlockEntity>> EXTRACTOR =
			register("extractor", ExtractorBlockEntity::new, ModBlocksNeoForge.EXTRACTOR);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompressorBlockEntity>> COMPRESSOR =
			register("compressor", CompressorBlockEntity::new, ModBlocksNeoForge.COMPRESSOR);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeothermalGeneratorBlockEntity>> GEOTHERMAL_GENERATOR =
			register("geothermal_generator", GeothermalGeneratorBlockEntity::new, ModBlocksNeoForge.GEOTHERMAL_GENERATOR);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> PUMP =
			register("pump", PumpBlockEntity::new, ModBlocksNeoForge.PUMP);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTankBlockEntity>> FLUID_TANK =
			register("fluid_tank", FluidTankBlockEntity::new, ModBlocksNeoForge.FLUID_TANK);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterMillBlockEntity>> WATER_MILL =
			register("water_mill", WaterMillBlockEntity::new, ModBlocksNeoForge.WATER_MILL);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindMillBlockEntity>> WIND_MILL =
			register("wind_mill", WindMillBlockEntity::new, ModBlocksNeoForge.WIND_MILL);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HighAltitudeWindMillBlockEntity>> HIGH_ALTITUDE_WIND_MILL =
			register("high_altitude_wind_mill", HighAltitudeWindMillBlockEntity::new, ModBlocksNeoForge.HIGH_ALTITUDE_WIND_MILL);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StormWindMillBlockEntity>> STORM_WIND_MILL =
			register("storm_wind_mill", StormWindMillBlockEntity::new, ModBlocksNeoForge.STORM_WIND_MILL);
	// Pure container (no EnergyPort) — no capability binding in IndustrializationNeoForge#registerCapabilities.
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IronChestBlockEntity>> IRON_CHEST =
			register("iron_chest", IronChestBlockEntity::new, ModBlocksNeoForge.IRON_CHEST);
	// Pure container (no EnergyPort) — no capability binding. Silver chest = tier above the iron chest.
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SilverChestBlockEntity>> SILVER_CHEST =
			register("silver_chest", SilverChestBlockEntity::new, ModBlocksNeoForge.SILVER_CHEST);
	// Pure container (no EnergyPort) — no capability binding. Gold chest = tier above the silver chest.
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GoldChestBlockEntity>> GOLD_CHEST =
			register("gold_chest", GoldChestBlockEntity::new, ModBlocksNeoForge.GOLD_CHEST);

	private ModBlockEntitiesNeoForge() {
	}

	/**
	 * Bind each {@code BlockEntityType} {@code DeferredHolder} into the loader-neutral {@link ModContent}
	 * facade, mirroring the {@code ModContent.X_BE = () -> X} assignments in
	 * {@code dev.alaindustrial.registry.ModBlockEntities#init()} on the Fabric side. Assigned directly
	 * (a {@code DeferredHolder} is a {@code Supplier}); resolves lazily after the {@code RegisterEvent}.
	 *
	 * <p>Bound via {@code HOLDER::get}: a {@code DeferredHolder<_, BlockEntityType<X>>} is a
	 * {@code Supplier<BlockEntityType<X>>}, but the slot is {@code Supplier<BlockEntityType<?>>} — generics
	 * are invariant, so the method reference bridges the wildcard while staying lazy (see
	 * {@code ModBlocksNeoForge#init}).
	 */
	public static void init() {
		ModContent.GENERATOR_BE = GENERATOR::get;
		ModContent.SOLAR_PANEL_BE = SOLAR_PANEL::get;
		ModContent.MOONLIT_SOLAR_PANEL_BE = MOONLIT_SOLAR_PANEL::get;
		ModContent.DAYLIGHT_SOLAR_PANEL_BE = DAYLIGHT_SOLAR_PANEL::get;
		ModContent.COPPER_CABLE_BE = COPPER_CABLE::get;
		ModContent.ITEM_PIPE_BE = ITEM_PIPE::get;
		ModContent.MACERATOR_BE = MACERATOR::get;
		ModContent.BATTERY_BOX_BE = BATTERY_BOX::get;
		ModContent.TELEPORTER_BE = TELEPORTER::get;
		ModContent.ELECTRIC_FURNACE_BE = ELECTRIC_FURNACE::get;
		ModContent.EXTRACTOR_BE = EXTRACTOR::get;
		ModContent.COMPRESSOR_BE = COMPRESSOR::get;
		ModContent.GEOTHERMAL_GENERATOR_BE = GEOTHERMAL_GENERATOR::get;
		ModContent.PUMP_BE = PUMP::get;
		ModContent.FLUID_TANK_BE = FLUID_TANK::get;
		ModContent.WATER_MILL_BE = WATER_MILL::get;
		ModContent.WIND_MILL_BE = WIND_MILL::get;
		ModContent.HIGH_ALTITUDE_WIND_MILL_BE = HIGH_ALTITUDE_WIND_MILL::get;
		ModContent.STORM_WIND_MILL_BE = STORM_WIND_MILL::get;
		ModContent.IRON_CHEST_BE = IRON_CHEST::get;
		ModContent.SILVER_CHEST_BE = SILVER_CHEST::get;
		ModContent.GOLD_CHEST_BE = GOLD_CHEST::get;
	}

	/**
	 * Registers one machine {@code BlockEntityType} against its block(s), taking the blocks as
	 * <b>deferred handles</b>.
	 *
	 * <p><b>Timing (the chicken-and-egg guard).</b> On NeoForge a block is a {@code DeferredBlock} that only
	 * resolves after its {@code RegisterEvent} fires — later than when this method is <i>called</i> (static
	 * init of this class, which runs while the {@code @Mod} constructor touches it). Passing resolved
	 * {@code Block}s here would force the caller to {@code deferredBlock.get()} too early and crash. So this
	 * takes {@code Supplier<Block>} handles (a {@code DeferredBlock} is one) and resolves them <b>inside</b>
	 * the deferred {@code BlockEntityType} supplier — which the register only invokes when the block-entity
	 * {@code RegisterEvent} fires, by which point every block is resolved. Verified against
	 * neoforge/minecraft 26.2.0.8-beta: {@code new BlockEntityType<>(factory, boolean, Block...)} stores the
	 * blocks in a {@code Set} and only reads them at runtime — deferring construction to the event is both
	 * necessary and sufficient.
	 */
	@SafeVarargs
	public static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(
			String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block>... validBlocks) {
		return BLOCK_ENTITIES.register(name, () -> {
			Block[] blocks = new Block[validBlocks.length];
			for (int i = 0; i < validBlocks.length; i++) {
				blocks[i] = validBlocks[i].get();
			}
			return new BlockEntityType<>(factory, false, blocks);
		});
	}
}
