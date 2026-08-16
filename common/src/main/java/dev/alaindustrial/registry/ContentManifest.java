package dev.alaindustrial.registry;

import dev.alaindustrial.Config;
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
import dev.alaindustrial.block.MobRepellerBlock;
import dev.alaindustrial.block.MobRepellerHvBlock;
import dev.alaindustrial.block.MobRepellerMvBlock;
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
import dev.alaindustrial.block.StormWindMillBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.block.VulcanizerBlock;
import dev.alaindustrial.block.WaterMillBlock;
import dev.alaindustrial.block.WindMillBlock;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.core.food.CanningMath;
import dev.alaindustrial.menu.CanningMachineMenu;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnSegmentBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.ExtractorBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerHvBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerMvBlockEntity;
import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.Overclockable;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.SawmillBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.item.energy.BatteryItem;
import dev.alaindustrial.item.misc.DurableComponentItem;
import dev.alaindustrial.item.misc.HintItem;
import dev.alaindustrial.item.misc.MutationChipItem;
import dev.alaindustrial.item.misc.OverclockerChipItem;
import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.menu.EnergyCondenserMenu;
import dev.alaindustrial.menu.CesuMenu;
import dev.alaindustrial.menu.ChargePadMenu;
import dev.alaindustrial.menu.ElectricHeaterMenu;
import dev.alaindustrial.menu.ComponentRepairBenchMenu;
import dev.alaindustrial.menu.CompressorMenu;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
import dev.alaindustrial.menu.DistillationColumnMenu;
import dev.alaindustrial.menu.DoubleChestMenu;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.menu.ExtractorMenu;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.menu.ElectrumChestMenu;
import dev.alaindustrial.menu.GoldChestMenu;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import dev.alaindustrial.menu.IronChestMenu;
import dev.alaindustrial.menu.StorageMenu3;
import dev.alaindustrial.menu.StorageMenu6;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.menu.PolymerizerMenu;
import dev.alaindustrial.menu.GardenDroneStationMenu;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.menu.IncubatorMenu;
import dev.alaindustrial.menu.MobRepellerHvMenu;
import dev.alaindustrial.menu.MobRepellerMenu;
import dev.alaindustrial.menu.MobRepellerMvMenu;
import dev.alaindustrial.menu.SawmillMenu;
import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.menu.StormWindMillMenu;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.menu.TeleporterStationMenu;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.menu.AlloySmelterMenu;
import dev.alaindustrial.menu.VulcanizerMenu;
import dev.alaindustrial.menu.GalvanicBathMenu;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Loader-neutral content manifest (MOD-190). The single ordered list of the mod's registrable content,
 * declared once here in {@code common} with only vanilla types + common content classes — no Fabric or
 * NeoForge imports. Each loader replays this list through a thin adapter:
 * {@code ModMenus} (Fabric, eager {@code Registry.register}) and
 * {@code ModMenusNeoForge} (NeoForge, lazy {@code DeferredRegister}).
 *
 * <p>Adding a menu = one {@link #menu} entry here, plus its screen in
 * {@code dev.alaindustrial.client.screen.MenuScreenManifest}; both loaders pick it up automatically.
 *
 * <p><b>Ordered on purpose.</b> {@link #MENUS} is a {@link List} (insertion order), never a map, so the
 * registration order is identical on both loaders (MOD-190 gotcha #7). Order does not affect
 * correctness in 26.2 — registries sync by string id — but one shared order keeps the loaders consistent.
 */
public final class ContentManifest {
	private ContentManifest() {
	}

	/**
	 * The client menu constructor {@code (int syncId, Inventory) -> Menu}. Our own public functional
	 * interface — the vanilla {@code MenuType.MenuSupplier} is private, so it cannot be named here; each
	 * loader adapts this to its own factory type ({@code MenuType.MenuSupplier} on Fabric,
	 * {@code IContainerFactory} on NeoForge) by passing {@code factory::create}.
	 *
	 * @param <T> the menu class
	 */
	@FunctionalInterface
	public interface MenuFactory<T extends AbstractContainerMenu> {
		T create(int syncId, Inventory playerInventory);
	}

	/**
	 * One {@code MenuType} to register.
	 *
	 * @param <T>     the menu class
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory the client menu constructor, shared by both loaders
	 * @param bind    publishes the registered {@code MenuType} into its {@link ModContent} slot
	 */
	public record MenuDef<T extends AbstractContainerMenu>(String id, MenuFactory<T> factory,
			Consumer<Supplier<MenuType<T>>> bind) {
	}

	/**
	 * Builds a {@link MenuDef}, capturing the menu-class generic {@code T} from {@code factory} at the
	 * call site so the {@link #MENUS} list below stays free of explicit type witnesses.
	 *
	 * <p>{@code T} is fixed by {@code factory} (an exact method reference), and the {@code bind} target —
	 * a typed {@code ModContent} slot — is then <i>checked</i> against it (MOD-198). Pointing an entry at
	 * the wrong slot ({@code menu("sawmill", SawmillMenu::new, s -> ModContent.MACERATOR_MENU = s)}) is
	 * therefore a compile error, not a silent swap: {@code Supplier<MenuType<SawmillMenu>> cannot be
	 * converted to Supplier<MenuType<MaceratorMenu>>}. (In {@link
	 * dev.alaindustrial.client.screen.MenuScreenManifest#screen} the same guard shows up one step
	 * earlier, as a type-inference conflict rather than an assignment error.)
	 */
	private static <T extends AbstractContainerMenu> MenuDef<T> menu(String id, MenuFactory<T> factory,
			Consumer<Supplier<MenuType<T>>> bind) {
		return new MenuDef<>(id, factory, bind);
	}

	/** Every machine/chest menu, in one shared order. See {@link MenuDef}. */
	public static final List<MenuDef<?>> MENUS = List.of(
			menu("generator", GeneratorMenu::new, s -> ModContent.GENERATOR_MENU = s),
			menu("macerator", MaceratorMenu::new, s -> ModContent.MACERATOR_MENU = s),
			menu("solar_panel", SolarPanelMenu::new, s -> ModContent.SOLAR_PANEL_MENU = s),
			menu("moonlit_solar_panel", MoonlitSolarPanelMenu::new, s -> ModContent.MOONLIT_SOLAR_PANEL_MENU = s),
			menu("electric_furnace", ElectricFurnaceMenu::new, s -> ModContent.ELECTRIC_FURNACE_MENU = s),
			menu("extractor", ExtractorMenu::new, s -> ModContent.EXTRACTOR_MENU = s),
			menu("compressor", CompressorMenu::new, s -> ModContent.COMPRESSOR_MENU = s),
			menu("component_repair_bench", ComponentRepairBenchMenu::new,
					s -> ModContent.COMPONENT_REPAIR_BENCH_MENU = s),
			menu("canning_machine", CanningMachineMenu::new, s -> ModContent.CANNING_MACHINE_MENU = s),
			menu("sawmill", SawmillMenu::new, s -> ModContent.SAWMILL_MENU = s),
			// MOD-275 — the assembler: blueprint queue, ghost pattern grid, six-slot output.
			menu("assembler", AssemblerMenu::new, s -> ModContent.ASSEMBLER_MENU = s),
			menu("incubator", IncubatorMenu::new, s -> ModContent.INCUBATOR_MENU = s),
			menu("polymerizer", PolymerizerMenu::new, s -> ModContent.POLYMERIZER_MENU = s),
			// MOD-251 — the distillation column: three tank gauges, warm-up bar, status line.
			menu("distillation_column", DistillationColumnMenu::new,
					s -> ModContent.DISTILLATION_COLUMN_MENU = s),
			menu("vulcanizer", VulcanizerMenu::new, s -> ModContent.VULCANIZER_MENU = s),
			// MOD-418 — the heater under it: a slotless readout for the warm-up ramp it now has.
			menu("electric_heater", ElectricHeaterMenu::new, s -> ModContent.ELECTRIC_HEATER_MENU = s),
			// MOD-064 — the alloy smelter: three interchangeable component slots, one result slot.
			menu("alloy_smelter", AlloySmelterMenu::new, s -> ModContent.ALLOY_SMELTER_MENU = s),
			menu("galvanic_bath", GalvanicBathMenu::new, s -> ModContent.GALVANIC_BATH_MENU = s),
			menu("battery_box", BatteryBoxMenu::new, s -> ModContent.BATTERY_BOX_MENU = s),
			menu("energy_condenser", EnergyCondenserMenu::new, s -> ModContent.ENERGY_CONDENSER_MENU = s),
			menu("cesu", CesuMenu::new, s -> ModContent.CESU_MENU = s),
			// MOD-416 — the charging station's readout: the mod's only slotless machine menu.
			menu("charge_pad", ChargePadMenu::new, s -> ModContent.CHARGE_PAD_MENU = s),
			menu("teleporter_station", TeleporterStationMenu::new, s -> ModContent.TELEPORTER_STATION_MENU = s),
			menu("teleporter_remote", TeleporterRemoteMenu::new, s -> ModContent.TELEPORTER_REMOTE_MENU = s),
			menu("daylight_solar_panel", DaylightSolarPanelMenu::new, s -> ModContent.DAYLIGHT_SOLAR_PANEL_MENU = s),
			menu("geothermal_generator", GeothermalGeneratorMenu::new, s -> ModContent.GEOTHERMAL_GENERATOR_MENU = s),
			menu("pump", PumpMenu::new, s -> ModContent.PUMP_MENU = s),
			menu("garden_drone_station", GardenDroneStationMenu::new,
					s -> ModContent.GARDEN_DRONE_STATION_MENU = s),
			menu("water_mill", WaterMillMenu::new, s -> ModContent.WATER_MILL_MENU = s),
			menu("wind_mill", WindMillMenu::new, s -> ModContent.WIND_MILL_MENU = s),
			menu("high_altitude_wind_mill", HighAltitudeWindMillMenu::new,
					s -> ModContent.HIGH_ALTITUDE_WIND_MILL_MENU = s),
			menu("storm_wind_mill", StormWindMillMenu::new, s -> ModContent.STORM_WIND_MILL_MENU = s),
			menu("iron_chest", IronChestMenu::new, s -> ModContent.IRON_CHEST_MENU = s),
			// MOD-287 — the warehouse window, one registration per height (3/6/9/12 rows).
			menu("storage_module_3", StorageMenu3::new, s -> ModContent.STORAGE_MODULE_MENU_3 = s),
			menu("storage_module_6", StorageMenu6::new, s -> ModContent.STORAGE_MODULE_MENU_6 = s),
			menu("silver_chest", SilverChestMenu::new, s -> ModContent.SILVER_CHEST_MENU = s),
			menu("gold_chest", GoldChestMenu::new, s -> ModContent.GOLD_CHEST_MENU = s),
			// MOD-409 — the electrum tier: 81 slots behind the same 6-row scrolling window, so this
			// is a single chest wearing the warehouse/double-chest machinery rather than a taller panel.
			menu("electrum_chest", ElectrumChestMenu::new, s -> ModContent.ELECTRUM_CHEST_MENU = s),
			// MOD-391 — the double chest's 6-row scrolling window, one type for every tier.
			menu("double_chest", DoubleChestMenu::new, s -> ModContent.DOUBLE_CHEST_MENU = s),
			// MOD-278 — the guard field, one menu per tier (same class, tier-specific client factory:
			// a menu type must map to exactly one block for stillValid, and the tiers are three blocks).
			menu("mob_repeller", MobRepellerMenu::new, s -> ModContent.MOB_REPELLER_MENU = s),
			menu("mob_repeller_mv", MobRepellerMvMenu::new, s -> ModContent.MOB_REPELLER_MV_MENU = s),
			menu("mob_repeller_hv", MobRepellerHvMenu::new, s -> ModContent.MOB_REPELLER_HV_MENU = s));

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Blocks — the COMPOSITION, not just the definition (MOD-403)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One {@code Block} to register. MOD-190 moved the per-block {@code Properties} chain here
	 * ({@link #BLOCK_PROPS}); MOD-403 moves the <b>list itself</b>, which until then was kept by hand in
	 * two files ({@code ModBlocks} on Fabric, {@code ModBlocksNeoForge}) and guarded only by a Python
	 * set-comparison after the fact. A block declared here registers on BOTH loaders or on neither.
	 *
	 * <p><b>What each loader still does.</b> Only the registration <i>mechanism</i> stays loader-side,
	 * because the two genuinely differ: Fabric constructs the block eagerly and stamps the id itself
	 * ({@code Properties.of().setId(key)} → {@code Registry.register}), NeoForge hands the same factory to
	 * {@code DeferredRegister.Blocks#registerBlock}, which calls it later with a {@code Properties} whose id
	 * it derived from the deferred key. Both call {@link #blockProps} with {@link #id()} — a block can no
	 * longer be given another block's properties, because neither side chooses the id any more.
	 *
	 * <p><b>Order is load-bearing.</b> Both loaders replay {@link #BLOCKS} in list order, and two entries
	 * depend on an earlier one having registered: {@code enriched_uranium_wall_torch} reads the standing
	 * torch for its loot table / description (see {@link #BLOCK_PROPS}), and the three liquid blocks read
	 * their fluid from {@link ModContent}. Keep new entries appended rather than interleaved.
	 *
	 * @param <T>     the concrete block class, captured at the constant below so a loader's typed handle
	 *                ({@code DeferredBlock<GeneratorBlock>}) cannot be wired to the wrong entry
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory builds the block from the loader-supplied {@code Properties}
	 * @param bind    publishes the registered block into its {@link ModContent} slot
	 */
	public record BlockDef<T extends Block>(String id, Function<BlockBehaviour.Properties, T> factory,
			Consumer<Supplier<Block>> bind) {
	}

	/**
	 * Builds a {@link BlockDef}, capturing the block-class generic {@code T} from {@code factory} at the
	 * call site — the same trick as {@link #menu}. Each constant below is public so a loader's typed handle
	 * is derived from <i>this</i> entry ({@code handle(ContentManifest.GENERATOR)}) rather than from a
	 * string key: pointing a {@code DeferredBlock<SolarPanelBlock>} field at the generator entry is then a
	 * compile error, not a silent mismatch.
	 */
	private static <T extends Block> BlockDef<T> block(String id,
			Function<BlockBehaviour.Properties, T> factory, Consumer<Supplier<Block>> bind) {
		return new BlockDef<>(id, factory, bind);
	}

	public static final BlockDef<GeneratorBlock> GENERATOR =
			block("generator", GeneratorBlock::new, s -> ModContent.GENERATOR = s);
	public static final BlockDef<SolarPanelBlock> SOLAR_PANEL =
			block("solar_panel", SolarPanelBlock::new, s -> ModContent.SOLAR_PANEL = s);
	public static final BlockDef<MoonlitSolarPanelBlock> MOONLIT_SOLAR_PANEL =
			block("moonlit_solar_panel", MoonlitSolarPanelBlock::new, s -> ModContent.MOONLIT_SOLAR_PANEL = s);
	public static final BlockDef<DaylightSolarPanelBlock> DAYLIGHT_SOLAR_PANEL =
			block("daylight_solar_panel", DaylightSolarPanelBlock::new, s -> ModContent.DAYLIGHT_SOLAR_PANEL = s);
	public static final BlockDef<GeothermalGeneratorBlock> GEOTHERMAL_GENERATOR =
			block("geothermal_generator", GeothermalGeneratorBlock::new, s -> ModContent.GEOTHERMAL_GENERATOR = s);
	public static final BlockDef<WaterMillBlock> WATER_MILL =
			block("water_mill", WaterMillBlock::new, s -> ModContent.WATER_MILL = s);
	public static final BlockDef<WindMillBlock> WIND_MILL =
			block("wind_mill", WindMillBlock::new, s -> ModContent.WIND_MILL = s);
	public static final BlockDef<HighAltitudeWindMillBlock> HIGH_ALTITUDE_WIND_MILL =
			block("high_altitude_wind_mill", HighAltitudeWindMillBlock::new,
					s -> ModContent.HIGH_ALTITUDE_WIND_MILL = s);
	public static final BlockDef<StormWindMillBlock> STORM_WIND_MILL =
			block("storm_wind_mill", StormWindMillBlock::new, s -> ModContent.STORM_WIND_MILL = s);
	public static final BlockDef<PumpBlock> PUMP =
			block("pump", PumpBlock::new, s -> ModContent.PUMP = s);
	public static final BlockDef<GardenDroneStationBlock> GARDEN_DRONE_STATION =
			block("garden_drone_station", GardenDroneStationBlock::new, s -> ModContent.GARDEN_DRONE_STATION = s);
	public static final BlockDef<FluidTankBlock> FLUID_TANK =
			block("fluid_tank", FluidTankBlock::new, s -> ModContent.FLUID_TANK = s);
	// Cables (MOD-219 / MOD-259): each grade passes its CableType; rubber insulation keeps the
	// conductor's tier/cap/buffer and halves its attenuation.
	public static final BlockDef<CableBlock> COPPER_CABLE =
			block("copper_cable", p -> new CableBlock(CableType.COPPER, p), s -> ModContent.COPPER_CABLE = s);
	public static final BlockDef<CableBlock> TIN_CABLE =
			block("tin_cable", p -> new CableBlock(CableType.TIN, p), s -> ModContent.TIN_CABLE = s);
	public static final BlockDef<CableBlock> GOLD_CABLE =
			block("gold_cable", p -> new CableBlock(CableType.GOLD, p), s -> ModContent.GOLD_CABLE = s);
	public static final BlockDef<CableBlock> ELECTRUM_CABLE =
			block("electrum_cable", p -> new CableBlock(CableType.ELECTRUM, p), s -> ModContent.ELECTRUM_CABLE = s);
	public static final BlockDef<CableBlock> INSULATED_COPPER_CABLE =
			block("insulated_copper_cable", p -> new CableBlock(CableType.INSULATED_COPPER, p),
					s -> ModContent.INSULATED_COPPER_CABLE = s);
	public static final BlockDef<CableBlock> INSULATED_TIN_CABLE =
			block("insulated_tin_cable", p -> new CableBlock(CableType.INSULATED_TIN, p),
					s -> ModContent.INSULATED_TIN_CABLE = s);
	public static final BlockDef<CableBlock> INSULATED_GOLD_CABLE =
			block("insulated_gold_cable", p -> new CableBlock(CableType.INSULATED_GOLD, p),
					s -> ModContent.INSULATED_GOLD_CABLE = s);
	public static final BlockDef<CableBlock> INSULATED_ELECTRUM_CABLE =
			block("insulated_electrum_cable", p -> new CableBlock(CableType.INSULATED_ELECTRUM, p),
					s -> ModContent.INSULATED_ELECTRUM_CABLE = s);
	public static final BlockDef<ItemPipeBlock> ITEM_PIPE =
			block("item_pipe", ItemPipeBlock::new, s -> ModContent.ITEM_PIPE = s);
	public static final BlockDef<FluidPipeBlock> FLUID_PIPE =
			block("fluid_pipe", FluidPipeBlock::new, s -> ModContent.FLUID_PIPE = s);
	public static final BlockDef<MaceratorBlock> MACERATOR =
			block("macerator", MaceratorBlock::new, s -> ModContent.MACERATOR = s);
	public static final BlockDef<BatteryBoxBlock> BATTERY_BOX =
			block("battery_box", BatteryBoxBlock::new, s -> ModContent.BATTERY_BOX = s);
	public static final BlockDef<CesuBlock> CESU =
			block("cesu", CesuBlock::new, s -> ModContent.CESU = s);
	// Teleporter station (MOD-091); visible since MOD-093 completed the feature.
	public static final BlockDef<TeleporterBlock> TELEPORTER =
			block("teleporter", TeleporterBlock::new, s -> ModContent.TELEPORTER = s);
	public static final BlockDef<ElectricFurnaceBlock> ELECTRIC_FURNACE =
			block("electric_furnace", ElectricFurnaceBlock::new, s -> ModContent.ELECTRIC_FURNACE = s);
	// Iron Furnace (MOD-115) — fuel-burning smelter between the stone and electric furnaces.
	public static final BlockDef<IronFurnaceBlock> IRON_FURNACE =
			block("iron_furnace", IronFurnaceBlock::new, s -> ModContent.IRON_FURNACE = s);
	public static final BlockDef<ExtractorBlock> EXTRACTOR =
			block("extractor", ExtractorBlock::new, s -> ModContent.EXTRACTOR = s);
	public static final BlockDef<CompressorBlock> COMPRESSOR =
			block("compressor", CompressorBlock::new, s -> ModContent.COMPRESSOR = s);
	// Component Repair Bench (MOD-384) — restores worn rotors/wheels instead of recrafting them.
	public static final BlockDef<ComponentRepairBenchBlock> COMPONENT_REPAIR_BENCH =
			block("component_repair_bench", ComponentRepairBenchBlock::new,
					s -> ModContent.COMPONENT_REPAIR_BENCH = s);
	public static final BlockDef<CanningMachineBlock> CANNING_MACHINE =
			block("canning_machine", CanningMachineBlock::new, s -> ModContent.CANNING_MACHINE = s);
	public static final BlockDef<SawmillBlock> SAWMILL =
			block("sawmill", SawmillBlock::new, s -> ModContent.SAWMILL = s);
	// MOD-275 — the first MV machine; a blueprint-driven auto-crafter.
	public static final BlockDef<AssemblerBlock> ASSEMBLER =
			block("assembler", AssemblerBlock::new, s -> ModContent.ASSEMBLER = s);
	public static final BlockDef<PolymerizerBlock> POLYMERIZER =
			block("polymerizer", PolymerizerBlock::new, s -> ModContent.POLYMERIZER = s);
	// Distillation Column (MOD-251): the 1×1×3 tower. Only the base has a BlockItem; the two segment
	// blocks are placed by the base (setPlacedBy) and are never carried.
	public static final BlockDef<DistillationColumnBlock> DISTILLATION_COLUMN =
			block("distillation_column", DistillationColumnBlock::new, s -> ModContent.DISTILLATION_COLUMN = s);
	public static final BlockDef<DistillationColumnMiddleBlock> DISTILLATION_COLUMN_MIDDLE =
			block("distillation_column_middle", DistillationColumnMiddleBlock::new,
					s -> ModContent.DISTILLATION_COLUMN_MIDDLE = s);
	public static final BlockDef<DistillationColumnTopBlock> DISTILLATION_COLUMN_TOP =
			block("distillation_column_top", DistillationColumnTopBlock::new,
					s -> ModContent.DISTILLATION_COLUMN_TOP = s);
	// Rectification Section (MOD-251 round 2): the optional fourth storey, crafted and placed by hand.
	public static final BlockDef<RectificationSectionBlock> RECTIFICATION_SECTION =
			block("rectification_section", RectificationSectionBlock::new,
					s -> ModContent.RECTIFICATION_SECTION = s);
	public static final BlockDef<AlloySmelterBlock> ALLOY_SMELTER =
			block("alloy_smelter", AlloySmelterBlock::new, s -> ModContent.ALLOY_SMELTER = s);
	public static final BlockDef<VulcanizerBlock> VULCANIZER =
			block("vulcanizer", VulcanizerBlock::new, s -> ModContent.VULCANIZER = s);
	public static final BlockDef<GalvanicBathBlock> GALVANIC_BATH =
			block("galvanic_bath", GalvanicBathBlock::new, s -> ModContent.GALVANIC_BATH = s);
	public static final BlockDef<ElectricHeaterBlock> ELECTRIC_HEATER =
			block("electric_heater", ElectricHeaterBlock::new, s -> ModContent.ELECTRIC_HEATER = s);
	public static final BlockDef<ChargePadBlock> CHARGE_PAD =
			block("charge_pad", ChargePadBlock::new, s -> ModContent.CHARGE_PAD = s);
	/** Energy condenser (MOD-393): banks grid surplus into energy clots. */
	public static final BlockDef<EnergyCondenserBlock> ENERGY_CONDENSER =
			block("energy_condenser", EnergyCondenserBlock::new, s -> ModContent.ENERGY_CONDENSER = s);
	public static final BlockDef<MobRepellerBlock> MOB_REPELLER =
			block("mob_repeller", MobRepellerBlock::new, s -> ModContent.MOB_REPELLER = s);
	public static final BlockDef<MobRepellerMvBlock> MOB_REPELLER_MV =
			block("mob_repeller_mv", MobRepellerMvBlock::new, s -> ModContent.MOB_REPELLER_MV = s);
	public static final BlockDef<MobRepellerHvBlock> MOB_REPELLER_HV =
			block("mob_repeller_hv", MobRepellerHvBlock::new, s -> ModContent.MOB_REPELLER_HV = s);
	public static final BlockDef<IncubatorBlock> INCUBATOR =
			block("incubator", IncubatorBlock::new, s -> ModContent.INCUBATOR = s);
	public static final BlockDef<IncubatorDomeBlock> INCUBATOR_DOME =
			block("incubator_dome", IncubatorDomeBlock::new, s -> ModContent.INCUBATOR_DOME = s);
	// Cotton trellis (MOD-280) — the mod's first crop; a two-block plant support, not a machine.
	public static final BlockDef<TrellisBlock> TRELLIS =
			block("trellis", TrellisBlock::new, s -> ModContent.TRELLIS = s);
	// Ores: plain Block, harvest tier is tag-driven.
	public static final BlockDef<Block> TIN_ORE =
			block("tin_ore", Block::new, s -> ModContent.TIN_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_TIN_ORE =
			block("deepslate_tin_ore", Block::new, s -> ModContent.DEEPSLATE_TIN_ORE = s);
	public static final BlockDef<Block> SILVER_ORE =
			block("silver_ore", Block::new, s -> ModContent.SILVER_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_SILVER_ORE =
			block("deepslate_silver_ore", Block::new, s -> ModContent.DEEPSLATE_SILVER_ORE = s);
	public static final BlockDef<Block> NICKEL_ORE =
			block("nickel_ore", Block::new, s -> ModContent.NICKEL_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_NICKEL_ORE =
			block("deepslate_nickel_ore", Block::new, s -> ModContent.DEEPSLATE_NICKEL_ORE = s);
	public static final BlockDef<Block> SULFUR_ORE =
			block("sulfur_ore", Block::new, s -> ModContent.SULFUR_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_SULFUR_ORE =
			block("deepslate_sulfur_ore", Block::new, s -> ModContent.DEEPSLATE_SULFUR_ORE = s);
	public static final BlockDef<Block> URANIUM_ORE =
			block("uranium_ore", Block::new, s -> ModContent.URANIUM_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_URANIUM_ORE =
			block("deepslate_uranium_ore", Block::new, s -> ModContent.DEEPSLATE_URANIUM_ORE = s);
	// MOD-423 — the only Nether ore, hence the only one WITHOUT a deepslate twin: the host rock
	// there is netherrack/basalt/blackstone, and no deepslate strata exist to carry a second variant.
	public static final BlockDef<Block> PALLADIUM_ORE =
			block("palladium_ore", Block::new, s -> ModContent.PALLADIUM_ORE = s);
	public static final BlockDef<IronChestBlock> IRON_CHEST =
			block("iron_chest", IronChestBlock::new, s -> ModContent.IRON_CHEST = s);
	// MOD-287 — modular warehouse block; several face-adjacent ones share one inventory.
	public static final BlockDef<StorageModuleBlock> STORAGE_MODULE =
			block("storage_module", StorageModuleBlock::new, s -> ModContent.STORAGE_MODULE = s);
	// Silver Chest (MOD-087) / Gold Chest (MOD-088) — the tiers above the iron chest. Same block stats.
	public static final BlockDef<SilverChestBlock> SILVER_CHEST =
			block("silver_chest", SilverChestBlock::new, s -> ModContent.SILVER_CHEST = s);
	public static final BlockDef<GoldChestBlock> GOLD_CHEST =
			block("gold_chest", GoldChestBlock::new, s -> ModContent.GOLD_CHEST = s);
	// Electrum Chest (MOD-409) — the tier above gold. Same block stats; the difference is inside
	// (81 slots) and in the window (six rows + scrollbar instead of a taller panel).
	public static final BlockDef<ElectrumChestBlock> ELECTRUM_CHEST =
			block("electrum_chest", ElectrumChestBlock::new, s -> ModContent.ELECTRUM_CHEST = s);
	// Material / decorative full cubes: cube_all model, one texture per block.
	public static final BlockDef<Block> TEMPERED_IRON_BLOCK =
			block("tempered_iron_block", Block::new, s -> ModContent.TEMPERED_IRON_BLOCK = s);
	// MOD-225 machine casing (crafting base) + MOD-292 MV casing + two decorative plate blocks.
	public static final BlockDef<Block> MACHINE_CASING =
			block("machine_casing", Block::new, s -> ModContent.MACHINE_CASING = s);
	public static final BlockDef<Block> ADVANCED_MACHINE_CASING =
			block("advanced_machine_casing", Block::new, s -> ModContent.ADVANCED_MACHINE_CASING = s);
	public static final BlockDef<Block> SILVER_PLATE_BLOCK =
			block("silver_plate_block", Block::new, s -> ModContent.SILVER_PLATE_BLOCK = s);
	public static final BlockDef<Block> TEMPERED_IRON_PLATE_BLOCK =
			block("tempered_iron_plate_block", Block::new, s -> ModContent.TEMPERED_IRON_PLATE_BLOCK = s);
	// Industrial Workbench (MOD-062) — the Industrialist villager's job-site block.
	public static final BlockDef<Block> INDUSTRIAL_WORKBENCH =
			block("industrial_workbench", Block::new, s -> ModContent.INDUSTRIAL_WORKBENCH = s);
	// Enriched Uranium Torch (MOD-085) — vanilla-behaviour torch, light 14, green flame.
	// The WALL variant must stay directly after the standing one: its BLOCK_PROPS entry reads the
	// already-registered standing torch for its loot table and description.
	public static final BlockDef<EnrichedUraniumTorchBlock> ENRICHED_URANIUM_TORCH =
			block("enriched_uranium_torch",
					p -> new EnrichedUraniumTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, p),
					s -> ModContent.ENRICHED_URANIUM_TORCH = s);
	public static final BlockDef<EnrichedUraniumWallTorchBlock> ENRICHED_URANIUM_WALL_TORCH =
			block("enriched_uranium_wall_torch",
					p -> new EnrichedUraniumWallTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, p),
					s -> ModContent.ENRICHED_URANIUM_WALL_TORCH = s);
	// Oil (MOD-238) + the two distillation fractions (MOD-251): in-world liquid blocks, no BlockItem —
	// a liquid block is never held. The fluid comes from ModContent, which BOTH loaders bind before the
	// block factory runs (Fabric: ModFluids.init() ahead of the replay; NeoForge: the FLUID
	// RegisterEvent fires before BLOCK).
	public static final BlockDef<OilLiquidBlock> OIL =
			block("oil", p -> new OilLiquidBlock(ModContent.OIL.get(), p), s -> ModContent.OIL_BLOCK = s);
	// ModLiquidBlock, not LiquidBlock: the vanilla constructor is protected and `common` compiles
	// against the un-widened jar (see ModLiquidBlock's javadoc). No behaviour difference.
	public static final BlockDef<ModLiquidBlock> DIESEL =
			block("diesel", p -> new ModLiquidBlock(ModContent.DIESEL.get(), p), s -> ModContent.DIESEL_BLOCK = s);
	public static final BlockDef<ModLiquidBlock> FUEL_OIL =
			block("fuel_oil", p -> new ModLiquidBlock(ModContent.FUEL_OIL.get(), p),
					s -> ModContent.FUEL_OIL_BLOCK = s);

	/**
	 * Every block, in one shared registration order — the single source of the mod's block composition
	 * (MOD-403). Both loaders replay this list; see {@link BlockDef}.
	 *
	 * <p>Declared after the constants above on purpose: a {@code List.of(...)} initializer may only read
	 * fields already declared textually above it (otherwise "illegal forward reference").
	 */
	public static final List<BlockDef<?>> BLOCKS = List.of(
			GENERATOR, SOLAR_PANEL, MOONLIT_SOLAR_PANEL, DAYLIGHT_SOLAR_PANEL, GEOTHERMAL_GENERATOR,
			WATER_MILL, WIND_MILL, HIGH_ALTITUDE_WIND_MILL, STORM_WIND_MILL, PUMP, GARDEN_DRONE_STATION,
			FLUID_TANK, COPPER_CABLE, TIN_CABLE, GOLD_CABLE, ELECTRUM_CABLE, INSULATED_COPPER_CABLE,
			INSULATED_TIN_CABLE, INSULATED_GOLD_CABLE, INSULATED_ELECTRUM_CABLE, ITEM_PIPE, FLUID_PIPE,
			MACERATOR, BATTERY_BOX, CESU, TELEPORTER, ELECTRIC_FURNACE, IRON_FURNACE, EXTRACTOR,
			COMPRESSOR, COMPONENT_REPAIR_BENCH, CANNING_MACHINE, SAWMILL, ASSEMBLER, POLYMERIZER, DISTILLATION_COLUMN,
			DISTILLATION_COLUMN_MIDDLE, DISTILLATION_COLUMN_TOP, RECTIFICATION_SECTION, ALLOY_SMELTER,
			VULCANIZER, GALVANIC_BATH, ELECTRIC_HEATER, CHARGE_PAD, ENERGY_CONDENSER,
			MOB_REPELLER, MOB_REPELLER_MV, MOB_REPELLER_HV, INCUBATOR,
			INCUBATOR_DOME, TRELLIS, TIN_ORE, DEEPSLATE_TIN_ORE, SILVER_ORE, DEEPSLATE_SILVER_ORE,
			NICKEL_ORE, DEEPSLATE_NICKEL_ORE, SULFUR_ORE, DEEPSLATE_SULFUR_ORE, URANIUM_ORE,
			DEEPSLATE_URANIUM_ORE, PALLADIUM_ORE,
			IRON_CHEST, STORAGE_MODULE, SILVER_CHEST, GOLD_CHEST, ELECTRUM_CHEST,
			TEMPERED_IRON_BLOCK, MACHINE_CASING, ADVANCED_MACHINE_CASING, SILVER_PLATE_BLOCK,
			TEMPERED_IRON_PLATE_BLOCK, INDUSTRIAL_WORKBENCH, ENRICHED_URANIUM_TORCH,
			ENRICHED_URANIUM_WALL_TORCH, OIL, DIESEL, FUEL_OIL);

	/**
	 * Wraps a machine/ore/material block's {@code strength/sound/…} chain with the shared base every such
	 * block carries — {@code requiresCorrectToolForDrops()} (a pickaxe is needed to drop; harvest tier is
	 * tag-driven). Torch blocks skip this (they break by hand) and use {@link ModBlockProperties#applyTorch}
	 * directly. {@code setId} is layered by each loader (Fabric from its key; NeoForge from the deferred key).
	 */
	private static UnaryOperator<BlockBehaviour.Properties> machine(UnaryOperator<BlockBehaviour.Properties> chain) {
		return p -> chain.apply(p.requiresCorrectToolForDrops());
	}

	/**
	 * The single source of every block's {@code BlockBehaviour.Properties} chain (MOD-190), keyed by
	 * registry id. Both {@code ModBlocks} (Fabric) and {@code ModBlocksNeoForge} apply
	 * {@code BLOCK_PROPS.get(id)} to their loader base instead of inlining the chain twice — so a
	 * {@code strength}/{@code sound}/{@code lightLevel} value can no longer drift between loaders
	 * (the MOD-157 bug class). Contains only loader-neutral behaviour; {@code setId} and the wall-torch
	 * loot/description overrides stay loader-side.
	 */
	public static final Map<String, UnaryOperator<BlockBehaviour.Properties>> BLOCK_PROPS = Map.ofEntries(
			Map.entry("generator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			Map.entry("solar_panel", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion())),
			Map.entry("moonlit_solar_panel", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion())),
			Map.entry("daylight_solar_panel", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion())),
			Map.entry("geothermal_generator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			Map.entry("water_mill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("wind_mill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("high_altitude_wind_mill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("storm_wind_mill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("pump", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			// noOcclusion: the dock is a 4px plate, not a full cube — without it the faces below/around
			// it would be culled as if a solid block sat there.
			Map.entry("garden_drone_station",
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("fluid_tank", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("copper_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("tin_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("gold_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("electrum_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("insulated_copper_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("insulated_tin_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("insulated_gold_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("insulated_electrum_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("item_pipe", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("fluid_pipe", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("macerator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("battery_box", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.WOOD))),
			// Metal, and tougher than the LV box it is built from — this tier is a steel shell, not a crate.
			Map.entry("cesu", machine(p -> p.strength(4.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("teleporter", machine(p -> p.strength(5.0f, 12.0f).sound(SoundType.METAL))),
			Map.entry("electric_furnace", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("iron_furnace", machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			Map.entry("extractor", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("compressor", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("component_repair_bench", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("canning_machine", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("sawmill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			// MOD-064: the smelter glows through its crucible windows while melting, so it lights like
			// the other machines with a lit front texture.
			Map.entry("alloy_smelter", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			// MOD-275: the assembler has no lit model, so no lightLevel — a plain metal machine cube.
			Map.entry("assembler", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("polymerizer", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			// MOD-251 — the distillation tower: all three segments share the machine chain, glowing
			// windows while working (litLight). The base drops the item; the segments have no loot.
			// noOcclusion: the tower is a chamfered ~12px column (round-2 voxel models), not a full
			// cube — a non-full collision shape MUST not occlude (R-PHY-05).
			Map.entry("distillation_column", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			Map.entry("distillation_column_middle", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			Map.entry("distillation_column_top", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			Map.entry("rectification_section", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			Map.entry("galvanic_bath", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("vulcanizer", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			// MOD-418: the heater's glow is a four-rung thermometer, not the boolean litLight the rest of
			// the machine family uses — see HeaterGlow.
			Map.entry("electric_heater", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::heaterLight))),
			// noOcclusion for the same reason as the drone dock: a 4px plate would otherwise cull the
			// faces around it as if a solid cube sat there. The light is four-valued rather than lit/unlit
			// — see ChargePadState.
			Map.entry("charge_pad", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::chargePadLight))),
			// Energy condenser (MOD-393): an open frame, so noOcclusion — otherwise it culls its
			// neighbours' faces as if it were solid, and you would see through the world past the orb.
			// It glows while the bank holds anything, which is also the "it is working" signal.
			Map.entry("energy_condenser", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			// MOD-278 — the guard field. The soul emitter glows while the field is up, which is also the
			// "it is powered" signal; identical chain on all three tiers so only the trim differs.
			Map.entry("mob_repeller", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::repellerLight))),
			Map.entry("mob_repeller_mv", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::repellerLight))),
			Map.entry("mob_repeller_hv", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::repellerLight))),
			// The emitter ring lights the chamber while an operation runs, so the block emits too.
			Map.entry("incubator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			// The dome is see-through: noOcclusion keeps the chamber (and the item inside) visible.
			// A piston must not take it: the dome is half of a multiblock and its glass is remembered
			// by the base below, so moving it away from its base would strand both.
			Map.entry("incubator_dome", machine(p -> p.strength(1.0f, 2.0f).sound(SoundType.GLASS)
					.noOcclusion().pushReaction(PushReaction.BLOCK))),
			// Cotton trellis (MOD-280) — a plant, not a machine: no requiresCorrectToolForDrops (it comes
			// apart by hand), and randomTicks() is load-bearing rather than decoration — without it the
			// block never receives randomTick and the crop would simply never grow. Deliberately NOT
			// noCollision: the trellis is a structure the player builds, so it blocks movement like a fence
			// post rather than being walked through like wheat. A piston must not drag half a two-block
			// plant away from its other half.
			Map.entry("trellis", p -> p.strength(0.2f).sound(SoundType.GRASS)
					.noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY)),
			Map.entry("tin_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_tin_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("silver_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_silver_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("nickel_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_nickel_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("sulfur_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_sulfur_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("uranium_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_uranium_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			// MOD-423 — Nether ore. Tougher than the overworld ores (4.5 like the deepslate variants),
			// but nowhere near ancient debris' 30.0/1200.0: palladium is meant to be mined, not besieged.
			Map.entry("palladium_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.NETHER_ORE))),
			Map.entry("iron_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			// MOD-287 — plain full cube, no noOcclusion(): unlike the chests it has no 3D renderer.
			Map.entry("storage_module", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("silver_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("gold_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("electrum_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("tempered_iron_block", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			// MOD-225: machine casing (crafting base for machines) + two decorative plate blocks.
			Map.entry("machine_casing", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			// MOD-292: MV casing — tougher than the LV one, it is the tier-up part.
			Map.entry("advanced_machine_casing", machine(p -> p.strength(6.0f, 8.0f).sound(SoundType.METAL))),
			Map.entry("silver_plate_block", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("tempered_iron_plate_block", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("industrial_workbench", machine(p -> p.strength(2.5f, 6.0f).sound(SoundType.METAL))),
			Map.entry("enriched_uranium_torch", ModBlockProperties::applyTorch),
			// The wall variant adds the vanilla wallVariant mirroring (loot table + description of the
			// standing torch). MOD-403 moved that override off the two loader files into the shared
			// helper — see ModBlockProperties#applyWallTorch for the ordering it relies on.
			Map.entry("enriched_uranium_wall_torch", ModBlockProperties::applyWallTorch),
			// Distillation fractions (MOD-251): same vanilla liquid-block chain as oil, their own
			// map colours (diesel golden-yellow, fuel oil dark brown).
			Map.entry("diesel", p -> p.mapColor(MapColor.COLOR_YELLOW).replaceable().noCollision()
					.strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()
					.sound(SoundType.EMPTY)),
			Map.entry("fuel_oil", p -> p.mapColor(MapColor.TERRACOTTA_BROWN).replaceable().noCollision()
					.strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()
					.sound(SoundType.EMPTY)),
			// Oil (MOD-238): the vanilla liquid-block chain (see Blocks.WATER in 26.2), dark map colour.
			// Not machine(...) - a liquid needs no tool and has no drops.
			Map.entry("oil", p -> p.mapColor(MapColor.COLOR_BLACK).replaceable().noCollision()
					.strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()
					.sound(SoundType.EMPTY)));

	/** The shared {@code Properties} chain for {@code id} (see {@link #BLOCK_PROPS}); throws if unknown. */
	public static UnaryOperator<BlockBehaviour.Properties> blockProps(String id) {
		UnaryOperator<BlockBehaviour.Properties> op = BLOCK_PROPS.get(id);
		if (op == null) {
			throw new IllegalArgumentException("No BLOCK_PROPS entry for block id '" + id + "'");
		}
		return op;
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Items (MOD-305 / MOD-306)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * How an item is CONSTRUCTED, declared once for both loaders (MOD-306). Same shape as
	 * {@link #BLOCK_PROPS}: keyed by registry path, looked up by the per-loader registry class.
	 *
	 * <p><b>What this fixes.</b> {@code ModItems} (Fabric) and {@code ModItemsNeoForge} were
	 * line-for-line twins — same ids, same comments, two different registration syntaxes around an
	 * identical construction. Adding an item meant two edits in two files, and nothing but a Python
	 * validator running after the fact stopped the two from drifting apart.
	 *
	 * <p><b>Why a factory over {@code Item.Properties} and not a finished {@code Item}.</b> That is the
	 * one shape both loaders can consume, because they disagree on <i>when</i> and <i>with what</i> the
	 * properties appear:
	 * <ul>
	 *   <li>Fabric registers eagerly and must stamp the id itself — it calls the factory with
	 *       {@code new Item.Properties().setId(key)};</li>
	 *   <li>NeoForge registers lazily through {@code DeferredRegister.Items#registerItem}, which hands
	 *       the factory a {@code Properties} whose id it has already derived from the deferred key.</li>
	 * </ul>
	 * Neither loader can hold a constructed {@code Item} at this point, but both can hold the function
	 * that makes one. Everything else about the item — extra {@code Properties} steps such as
	 * {@code durability(...)}, and the concrete {@code Item} subclass — lives inside the factory, so the
	 * whole definition is one expression here.
	 *
	 * <p><b>Scope, deliberately.</b> Entries here are the items whose construction is genuinely
	 * loader-neutral. Block items are NOT here: their factory needs the loader's own block handle
	 * ({@code ModBlocks.X} is an eager {@code Block}, {@code ModBlocksNeoForge.X} a lazy holder), so a
	 * shared factory would have to close over a loader type — exactly what {@code common} must not do.
	 * The typed field per loader also stays: it is the handle 100+ call sites already use, and MOD-190
	 * settled that trade-off (menus collapse fully, blocks/items keep their field, definition is shared).
	 */
	public static final Map<String, Function<Item.Properties, ? extends Item>> ITEM_FACTORIES =
			buildItemFactories();

	/** The construction function for item {@code id} (see {@link #ITEM_FACTORIES}); throws if unknown. */
	public static Function<Item.Properties, ? extends Item> itemFactory(String id) {
		Function<Item.Properties, ? extends Item> factory = ITEM_FACTORIES.get(id);
		if (factory == null) {
			throw new IllegalArgumentException("No ITEM_FACTORIES entry for item id '" + id + "'");
		}
		return factory;
	}

	/**
	 * Two gray hint lines under the name, keyed {@code item.alaindustrial.<id>.hint} / {@code .hint2}.
	 * The key strings are derived from the id here rather than typed twice per loader.
	 */
	private static Function<Item.Properties, ? extends Item> hintItem(String id) {
		return p -> new HintItem(p, "item.alaindustrial." + id + ".hint",
				"item.alaindustrial." + id + ".hint2");
	}

	/** An overclocker chip of a fixed tier (MOD-393) — a hint item that also carries its step count. */
	private static Function<Item.Properties, ? extends Item> overclockerChip(String id, int tier) {
		return p -> new OverclockerChipItem(p, tier, "item.alaindustrial." + id + ".hint",
				"item.alaindustrial." + id + ".hint2");
	}

	/**
	 * A wearing machine component (MOD-189): {@code durability(max)} sets the vanilla {@code max_damage}
	 * component, so wear renders as the standard durability bar and the item becomes non-stackable.
	 * {@code max} is read from {@link Config} when the item is constructed — i.e. at registration, so a
	 * config change still needs a restart; the wear RATE is read live each tick in the block entity.
	 *
	 * <p>Note what {@code durability(max)} actually is since MOD-384: the item's DEFAULT ceiling, not a
	 * fixed one. {@code max_damage} is an ordinary stack component, so the repair bench lowers it on the
	 * individual stack it repairs and {@code ItemStack.getMaxDamage()} reads that override.
	 * {@link DurableComponentItem} carries the matching tooltip.
	 */
	private static Function<Item.Properties, ? extends Item> durableComponent(IntSupplier maxDamage) {
		return p -> new DurableComponentItem(p.durability(maxDamage.getAsInt()));
	}

	private static Map<String, Function<Item.Properties, ? extends Item>> buildItemFactories() {
		Map<String, Function<Item.Properties, ? extends Item>> defs = new LinkedHashMap<>();
		// Plain items: crafting components, dusts, plates, ingots, raw ores, by-products. Nothing but
		// `new Item(properties)` — the largest and most duplicated group.
		for (String id : List.of(
				"advanced_circuit", "alignment_chip_day", "alignment_chip_night",
				// MOD-064 alloys: the four products of the alloy smelter.
				"bronze_ingot", "cupronickel_ingot", "electrum_ingot", "invar_ingot",
				"coal_dust", "copper_coil", "copper_dust", "copper_plate", "cotton_fiber",
				"cotton_seeds", "depleted_uranium", "diamond_dust", "electronic_circuit",
				"emerald_dust", "empty_can", "flux_thread", "fluxweave_cloth", "garden_drone", "gold_dust",
				"gold_gear", "gold_plate", "iron_dust", "iron_gear", "iron_plate",
				"irradiated_diamond", "irradiated_slag", "lapis_dust", "mutagen_dust",
				"nickel_dust", "nickel_ingot", "nickel_plate",
				"palladium_dust", "palladium_ingot", "palladium_plate",
				"raw_nickel", "raw_palladium", "raw_rubber",
				"raw_silver", "raw_sulfur", "raw_tin", "raw_uranium", "resonant_shard", "rubber",
				"silver_dust", "silver_gear", "silver_ingot", "silver_plate", "stone_gear",
				"sulfur_dust", "tempered_iron", "tempered_iron_plate", "tin_dust", "tin_ingot",
				"tin_plate", "unstable_isotope", "uranium_dust", "uranium_ingot", "uranium_plate",
				"wooden_gear")) {
			defs.put(id, Item::new);
		}
		// Battery (MOD-083): the stackable EU carrier. Charge is per item, so the stack size is what
		// keeps stack transfers exact — see BatteryItem for why 16 and not 64.
		defs.put("battery", p -> new BatteryItem(p.stacksTo(BatteryItem.MAX_STACK)));
		// Canned Ration (MOD-383): the mod's first edible item, and the one place its food numbers are
		// declared. Its nutrition is fixed no matter what went into the machine — that is precisely
		// what lets every ration stack with every other one, which is the entire point of the machine
		// (a ration that remembered its source would carry a different component and never merge).
		// Its Consumable is built here rather than inherited from the source food, so effects like a
		// golden apple's regeneration have nowhere to travel.
		defs.put("canned_ration", p -> new Item(p.food(
				new FoodProperties.Builder()
						.nutrition(CanningMath.RATION_NUTRITION)
						.saturationModifier(CanningMath.RATION_SATURATION_MODIFIER)
						// Deliberately NOT alwaysEdible: a full player cannot eat this, exactly like every
						// ordinary food. Being edible at a full bar was tried and dropped — it let the
						// player top the hunger bar off at will, which is a power vanilla reserves for the
						// golden apple, and it is not what this machine is for.
						.build(),
				Consumable.builder()
						.consumeSeconds(CanningMath.RATION_CONSUME_SECONDS)
						// DRINK rather than EAT: the drink pose tips the item up to the mouth, which is
						// what eating straight out of a tin looks like — the eat pose holds it flat and
						// reads as biting a loaf. The SOUND stays the ordinary eating one, so it is chewed
						// like a steak while being held like a can.
						.animation(ItemUseAnimation.DRINK)
						.sound(SoundEvents.GENERIC_EAT)
						// Vanilla's drink preset turns particles off; kept on here so bits still fly and
						// the act reads as a meal rather than a swig.
						.hasConsumeParticles(true)
						// A metallic clink as the emptied tin is thrown away — the one cue that this was
						// a can and not a bowl.
						.soundAfterConsume(SoundEvents.ARMOR_EQUIP_IRON)
						.build())));
		// Upgrade chips (MOD-080): the blank and the mute upgrade, each with its hint lines.
		defs.put("empty_chip", hintItem("empty_chip"));
		defs.put("mute_chip", hintItem("mute_chip"));
		defs.put("overclocker_chip_i", overclockerChip("overclocker_chip_i", 1));
		defs.put("overclocker_chip_ii", overclockerChip("overclocker_chip_ii", 2));
		defs.put("overclocker_chip_iii", overclockerChip("overclocker_chip_iii", 3));
		// Energy clots (MOD-393): what the condenser packs surplus grid power into. Three tiers, told
		// apart by how much was banked when the player pulled it out.
		defs.put("energy_clot_i", hintItem("energy_clot_i"));
		defs.put("energy_clot_ii", hintItem("energy_clot_ii"));
		defs.put("energy_clot_iii", hintItem("energy_clot_iii"));
		// Cable breaker (MOD-276): clamps onto a laid cable and cuts the line for maintenance. A hint
		// item because the whole control scheme (install / throw / pry off) is gestures on the wire,
		// with no GUI anywhere to explain itself.
		defs.put("cable_breaker", hintItem("cable_breaker"));
		// Incubator mode chips (MOD-118) — the mode binding lives in the item.
		defs.put("mutation_chip_transform", p -> new MutationChipItem(p, IncubatorMode.TRANSFORM));
		defs.put("mutation_chip_duplicate", p -> new MutationChipItem(p, IncubatorMode.DUPLICATE));
		defs.put("mutation_chip_create", p -> new MutationChipItem(p, IncubatorMode.CREATE));
		// Wearing components (MOD-189).
		defs.put("windmill_rotor", durableComponent(() -> Config.windMillRotorMaxDamage));
		defs.put("water_mill_wheel", durableComponent(() -> Config.waterMillWheelMaxDamage));
		// MOD-385: the upper two grades of each component. Same factory — only the durability differs at
		// registration; output multiplier and wear rate are read live per tick from ComponentTier.
		defs.put("windmill_rotor_reinforced", durableComponent(() -> Config.windMillRotorReinforcedMaxDamage));
		defs.put("windmill_rotor_advanced", durableComponent(() -> Config.windMillRotorAdvancedMaxDamage));
		defs.put("water_mill_wheel_reinforced", durableComponent(() -> Config.waterMillWheelReinforcedMaxDamage));
		defs.put("water_mill_wheel_advanced", durableComponent(() -> Config.waterMillWheelAdvancedMaxDamage));
		// Soul Vessel (MOD-278): the repeller's upgrade currency. stacksTo(1) is not a balance knob —
		// the kill counter is a stack component, and stacks with different components never merge, so a
		// stackable vessel would only ever look broken.
		defs.put("soul_vessel", p -> new SoulVesselItem(p.stacksTo(1)));
		return Map.copyOf(defs);
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// BlockEntity types (MOD-307)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One {@code BlockEntityType} to register: its id, the {@code BlockEntity} class it produces, the
	 * factory, and the set of blocks it is valid for — <b>by registry id</b>.
	 *
	 * <p><b>Why the blocks are ids and not {@code Block} handles.</b> A handle would have to come from a
	 * loader registry ({@code ModBlocks.X} is an eager {@code Block}; {@code ModBlocksNeoForge.X} a lazy
	 * holder), so the valid-block set had to be written out twice — and the two copies were held together
	 * by nothing but a Python parity script. That is exactly the defect class MOD-191 filed: a block
	 * missing from one loader's set is not a type error, it is a silent "this block entity does not exist
	 * on that loader". With ids, the set is written once and each loader resolves it against the vanilla
	 * registry at its own registration moment.
	 *
	 * @param <T>     the block entity class
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param type    the block entity class, so a lookup can verify the caller's expected type
	 * @param factory the {@code BlockEntity} constructor, shared by both loaders
	 * @param bind    publishes the registered {@code BlockEntityType} into its {@link ModContent} slot
	 *                (MOD-403 — before that, each loader wrote out all 40 assignments by hand and a
	 *                forgotten line surfaced only as a {@code verifyAllBound()} crash at startup)
	 * @param blocks  registry ids of the blocks this type is valid for
	 */
	public record BlockEntityDef<T extends BlockEntity>(String id, Class<T> type,
			BlockEntityType.BlockEntitySupplier<T> factory,
			Consumer<Supplier<BlockEntityType<?>>> bind, List<String> blocks) {

		/**
		 * Resolves {@link #blocks} against the vanilla block registry. Called by each loader when it
		 * builds the {@code BlockEntityType}: on Fabric that is {@code ModBlockEntities.init()} (after
		 * {@code ModBlocks.init()}), on NeoForge it is inside the deferred type supplier — in both cases
		 * the blocks are already registered.
		 *
		 * <p>An unknown id throws instead of quietly resolving to {@code AIR}: a typo here would otherwise
		 * produce a block entity that never attaches to anything, which is precisely the silent failure
		 * this manifest exists to remove.
		 *
		 * <p><b>The returned set is UNMODIFIABLE (MOD-417), and that is load-bearing.</b> Both loaders
		 * hand this exact instance to {@code new BlockEntityType<>(factory, blockSet())}, and the vanilla
		 * constructor stores the reference as-is — it does not copy. {@code isValid(BlockState)} then reads
		 * that very set on every block-entity attach. Before the manifest, each loader passed
		 * {@code Set.of(...)}, so the field was unmodifiable by construction; building it here turned it
		 * into a live mutable collection aliased by a registered {@code BlockEntityType}, where a stray
		 * {@code add}/{@code remove} would silently change which blocks the type attaches to. That
		 * regression was accidental, undocumented and uncovered, so it is closed rather than kept.
		 *
		 * <p><b>Why {@code Collections.unmodifiableSet} and not {@code Set.copyOf}.</b> {@code Set.copyOf}
		 * is salted — its iteration order varies between JVM runs — and the resolution below depends on a
		 * stable order for its duplicate diagnostics and for reproducible error messages. The wrapper
		 * keeps insertion order (i.e. the order of {@link #blocks}) and adds immutability on top.
		 *
		 * <p><b>What this does NOT do.</b> It does not stop a NeoForge mod extending our types through
		 * {@code BlockEntityTypeAddBlocksEvent}: that event copies {@code getValidBlocks()} into a fresh
		 * {@code HashSet} and REPLACES the field through a mixin accessor, so it never touches the set we
		 * pass. Extensibility there is unchanged by this method, in either direction.
		 */
		public Set<Block> blockSet() {
			// The strictness below is not belt-and-braces: it replaces guarantees the vanilla/NeoForge
			// varargs constructors used to give and that the Set-taking one does not. An empty block set
			// used to throw; a duplicate block used to throw (Set.of). Losing both silently would leave a
			// BlockEntityType that is valid for nothing — the exact quiet failure this manifest exists to
			// remove.
			if (blocks.isEmpty()) {
				throw new IllegalStateException("BlockEntityDef '" + id
						+ "' lists no blocks — the type would be valid for nothing");
			}
			Set<Block> resolved = new LinkedHashSet<>();
			for (String blockId : blocks) {
				Identifier key = Industrialization.id(blockId);
				// getValue on a DefaultedRegistry substitutes AIR for an unknown key rather than
				// returning null, so AIR — not null — is what an unregistered/misspelt id looks like.
				Block block = BuiltInRegistries.BLOCK.getValue(key);
				if (block == Blocks.AIR) {
					throw new IllegalStateException("BlockEntityDef '" + id + "': block '" + key
							+ "' is not registered (yet) — cannot build its BlockEntityType");
				}
				if (!resolved.add(block)) {
					throw new IllegalStateException("BlockEntityDef '" + id + "': block '" + key
							+ "' listed twice");
				}
			}
			return Collections.unmodifiableSet(resolved);
		}

		/**
		 * The {@code BlockEntityType} this definition produced, typed on {@code T} — resolved from the
		 * vanilla registry rather than from a loader handle, so client code shared by both loaders can name
		 * it (MOD-403: the {@code BlockEntityRenderer} manifest).
		 *
		 * <p><b>Why the cast is safe.</b> {@link ModContent} keeps its block-entity slots as
		 * {@code Supplier<BlockEntityType<?>>}, so it cannot hand out a typed handle; the registry cannot
		 * either. What pins {@code T} is the call site: a {@code BlockEntityDef<T>} is only obtainable
		 * through {@link ContentManifest#blockEntity(String, Class)}, which throws unless this id's
		 * definition really produces {@code T} — and the loader built the registered type from THAT
		 * definition's factory. So the type parameter is checked, just one step earlier than the cast.
		 *
		 * <p>Callable only after the loader registered its block-entity types (Fabric:
		 * {@code ModBlockEntities.init()}; NeoForge: its {@code RegisterEvent}). Both renderer-registration
		 * hooks run far later than that.
		 */
		@SuppressWarnings("unchecked")
		public BlockEntityType<T> registeredType() {
			Identifier key = Industrialization.id(id);
			BlockEntityType<?> registered = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(key);
			if (registered == null) {
				throw new IllegalStateException("BlockEntityDef '" + id + "': '" + key
						+ "' is not registered (yet) — asked for its BlockEntityType too early");
			}
			return (BlockEntityType<T>) registered;
		}
	}

	private static <T extends BlockEntity> BlockEntityDef<T> blockEntity(String id, Class<T> type,
			BlockEntityType.BlockEntitySupplier<T> factory,
			Consumer<Supplier<BlockEntityType<?>>> bind, String... blocks) {
		return new BlockEntityDef<>(id, type, factory, bind, List.of(blocks));
	}

	/** Every {@code BlockEntityType}, declared once for both loaders. See {@link BlockEntityDef}. */
	public static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			blockEntity("generator", GeneratorBlockEntity.class, GeneratorBlockEntity::new, s -> ModContent.GENERATOR_BE = s, "generator"),
			blockEntity("geothermal_generator", GeothermalGeneratorBlockEntity.class, GeothermalGeneratorBlockEntity::new, s -> ModContent.GEOTHERMAL_GENERATOR_BE = s, "geothermal_generator"),
			blockEntity("solar_panel", SolarPanelBlockEntity.class, SolarPanelBlockEntity::new, s -> ModContent.SOLAR_PANEL_BE = s, "solar_panel"),
			blockEntity("moonlit_solar_panel", MoonlitSolarPanelBlockEntity.class, MoonlitSolarPanelBlockEntity::new, s -> ModContent.MOONLIT_SOLAR_PANEL_BE = s, "moonlit_solar_panel"),
			blockEntity("daylight_solar_panel", DaylightSolarPanelBlockEntity.class, DaylightSolarPanelBlockEntity::new, s -> ModContent.DAYLIGHT_SOLAR_PANEL_BE = s, "daylight_solar_panel"),
			blockEntity("copper_cable", CableBlockEntity.class, CableBlockEntity::new, s -> ModContent.COPPER_CABLE_BE = s, "copper_cable", "tin_cable", "gold_cable", "electrum_cable", "insulated_copper_cable", "insulated_tin_cable", "insulated_gold_cable", "insulated_electrum_cable"),
			blockEntity("item_pipe", ItemPipeBlockEntity.class, ItemPipeBlockEntity::new, s -> ModContent.ITEM_PIPE_BE = s, "item_pipe"),
			blockEntity("fluid_pipe", FluidPipeBlockEntity.class, FluidPipeBlockEntity::new, s -> ModContent.FLUID_PIPE_BE = s, "fluid_pipe"),
			blockEntity("macerator", MaceratorBlockEntity.class, MaceratorBlockEntity::new, s -> ModContent.MACERATOR_BE = s, "macerator"),
			blockEntity("component_repair_bench", ComponentRepairBenchBlockEntity.class, ComponentRepairBenchBlockEntity::new, s -> ModContent.COMPONENT_REPAIR_BENCH_BE = s, "component_repair_bench"),
			blockEntity("battery_box", BatteryBoxBlockEntity.class, BatteryBoxBlockEntity::new, s -> ModContent.BATTERY_BOX_BE = s, "battery_box"),
			blockEntity("cesu", CesuBlockEntity.class, CesuBlockEntity::new, s -> ModContent.CESU_BE = s, "cesu"),
			blockEntity("teleporter", TeleporterBlockEntity.class, TeleporterBlockEntity::new, s -> ModContent.TELEPORTER_BE = s, "teleporter"),
			blockEntity("electric_furnace", ElectricFurnaceBlockEntity.class, ElectricFurnaceBlockEntity::new, s -> ModContent.ELECTRIC_FURNACE_BE = s, "electric_furnace"),
			blockEntity("iron_furnace", IronFurnaceBlockEntity.class, IronFurnaceBlockEntity::new, s -> ModContent.IRON_FURNACE_BE = s, "iron_furnace"),
			blockEntity("extractor", ExtractorBlockEntity.class, ExtractorBlockEntity::new, s -> ModContent.EXTRACTOR_BE = s, "extractor"),
			blockEntity("compressor", CompressorBlockEntity.class, CompressorBlockEntity::new, s -> ModContent.COMPRESSOR_BE = s, "compressor"),
			blockEntity("canning_machine", CanningMachineBlockEntity.class, CanningMachineBlockEntity::new,
					s -> ModContent.CANNING_MACHINE_BE = s, "canning_machine"),
			blockEntity("sawmill", SawmillBlockEntity.class, SawmillBlockEntity::new, s -> ModContent.SAWMILL_BE = s, "sawmill"),
			blockEntity("assembler", AssemblerBlockEntity.class, AssemblerBlockEntity::new, s -> ModContent.ASSEMBLER_BE = s, "assembler"),
			blockEntity("polymerizer", PolymerizerBlockEntity.class, PolymerizerBlockEntity::new, s -> ModContent.POLYMERIZER_BE = s, "polymerizer"),
			// MOD-251 — the tower: master BE on the base, one shared proxy type on both segments.
			blockEntity("distillation_column", DistillationColumnBlockEntity.class,
					DistillationColumnBlockEntity::new, s -> ModContent.DISTILLATION_COLUMN_BE = s,
					"distillation_column"),
			blockEntity("distillation_column_segment", DistillationColumnSegmentBlockEntity.class,
					DistillationColumnSegmentBlockEntity::new,
					s -> ModContent.DISTILLATION_COLUMN_SEGMENT_BE = s,
					"distillation_column_middle", "distillation_column_top"),
			blockEntity("vulcanizer", VulcanizerBlockEntity.class, VulcanizerBlockEntity::new, s -> ModContent.VULCANIZER_BE = s, "vulcanizer"),
			blockEntity("alloy_smelter", AlloySmelterBlockEntity.class, AlloySmelterBlockEntity::new, s -> ModContent.ALLOY_SMELTER_BE = s, "alloy_smelter"),
			blockEntity("galvanic_bath", GalvanicBathBlockEntity.class, GalvanicBathBlockEntity::new, s -> ModContent.GALVANIC_BATH_BE = s, "galvanic_bath"),
			blockEntity("electric_heater", ElectricHeaterBlockEntity.class, ElectricHeaterBlockEntity::new, s -> ModContent.ELECTRIC_HEATER_BE = s, "electric_heater"),
			blockEntity("charge_pad", ChargePadBlockEntity.class, ChargePadBlockEntity::new, s -> ModContent.CHARGE_PAD_BE = s, "charge_pad"),
			blockEntity("energy_condenser", EnergyCondenserBlockEntity.class,
					EnergyCondenserBlockEntity::new, s -> ModContent.ENERGY_CONDENSER_BE = s, "energy_condenser"),
			blockEntity("incubator", IncubatorBlockEntity.class, IncubatorBlockEntity::new, s -> ModContent.INCUBATOR_BE = s, "incubator"),
			blockEntity("pump", PumpBlockEntity.class, PumpBlockEntity::new, s -> ModContent.PUMP_BE = s, "pump"),
			blockEntity("garden_drone_station", GardenDroneStationBlockEntity.class, GardenDroneStationBlockEntity::new, s -> ModContent.GARDEN_DRONE_STATION_BE = s, "garden_drone_station"),
			blockEntity("fluid_tank", FluidTankBlockEntity.class, FluidTankBlockEntity::new, s -> ModContent.FLUID_TANK_BE = s, "fluid_tank"),
			blockEntity("water_mill", WaterMillBlockEntity.class, WaterMillBlockEntity::new, s -> ModContent.WATER_MILL_BE = s, "water_mill"),
			blockEntity("wind_mill", WindMillBlockEntity.class, WindMillBlockEntity::new, s -> ModContent.WIND_MILL_BE = s, "wind_mill"),
			blockEntity("high_altitude_wind_mill", HighAltitudeWindMillBlockEntity.class, HighAltitudeWindMillBlockEntity::new, s -> ModContent.HIGH_ALTITUDE_WIND_MILL_BE = s, "high_altitude_wind_mill"),
			blockEntity("storm_wind_mill", StormWindMillBlockEntity.class, StormWindMillBlockEntity::new, s -> ModContent.STORM_WIND_MILL_BE = s, "storm_wind_mill"),
			blockEntity("iron_chest", IronChestBlockEntity.class, IronChestBlockEntity::new, s -> ModContent.IRON_CHEST_BE = s, "iron_chest"),
			blockEntity("storage_module", StorageModuleBlockEntity.class, StorageModuleBlockEntity::new, s -> ModContent.STORAGE_MODULE_BE = s, "storage_module"),
			blockEntity("silver_chest", SilverChestBlockEntity.class, SilverChestBlockEntity::new, s -> ModContent.SILVER_CHEST_BE = s, "silver_chest"),
			blockEntity("gold_chest", GoldChestBlockEntity.class, GoldChestBlockEntity::new, s -> ModContent.GOLD_CHEST_BE = s, "gold_chest"),
			blockEntity("electrum_chest", ElectrumChestBlockEntity.class, ElectrumChestBlockEntity::new, s -> ModContent.ELECTRUM_CHEST_BE = s, "electrum_chest"),
			blockEntity("mob_repeller", MobRepellerBlockEntity.class, MobRepellerBlockEntity::new, s -> ModContent.MOB_REPELLER_BE = s, "mob_repeller"),
			blockEntity("mob_repeller_mv", MobRepellerMvBlockEntity.class, MobRepellerMvBlockEntity::new, s -> ModContent.MOB_REPELLER_MV_BE = s, "mob_repeller_mv"),
			blockEntity("mob_repeller_hv", MobRepellerHvBlockEntity.class, MobRepellerHvBlockEntity::new, s -> ModContent.MOB_REPELLER_HV_BE = s, "mob_repeller_hv"));

	/**
	 * The definition for block-entity {@code id}, checked against the type the caller expects.
	 *
	 * <p>The {@code Class} argument is what keeps the loader's typed field honest: asking for
	 * {@code blockEntity("macerator", SawmillBlockEntity.class)} fails loudly here instead of producing a
	 * {@code BlockEntityType} whose generic parameter lies about what it creates.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends BlockEntity> BlockEntityDef<T> blockEntity(String id, Class<T> type) {
		for (BlockEntityDef<?> def : BLOCK_ENTITIES) {
			if (def.id().equals(id)) {
				if (def.type() != type) {
					throw new IllegalArgumentException("BlockEntityDef '" + id + "' produces "
							+ def.type().getSimpleName() + ", not " + type.getSimpleName());
				}
				return (BlockEntityDef<T>) def;
			}
		}
		throw new IllegalArgumentException("No BLOCK_ENTITIES entry for block-entity id '" + id + "'");
	}

	/**
	 * Does an overclocker chip do anything in {@code block}? Answered from the block alone, so the
	 * upgrade panel can refuse the chip on the CLIENT, where the menu is backed by a dummy container
	 * with no block entity to ask (MOD-392: a generator used to accept the chip and then ignore it).
	 *
	 * <p>Derived from {@link Overclockable} on the block entity class rather than from a second,
	 * hand-written list of blocks: the manifest already maps blocks to their BE class, so there is
	 * exactly one place to declare that a machine overclocks, and the slot cannot drift from the effect.
	 * Blocks with no block entity at all — every plain building block — answer {@code false}.
	 */
	public static boolean isOverclockable(Block block) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(block);
		if (!Industrialization.MOD_ID.equals(key.getNamespace())) {
			return false;
		}
		String path = key.getPath();
		for (BlockEntityDef<?> def : BLOCK_ENTITIES) {
			if (def.blocks().contains(path)) {
				return Overclockable.class.isAssignableFrom(def.type());
			}
		}
		return false;
	}
}
