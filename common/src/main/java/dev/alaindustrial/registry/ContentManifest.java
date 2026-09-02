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
import dev.alaindustrial.block.CreativeEnergySourceBlock;
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
import dev.alaindustrial.block.FermenterBlock;
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
import dev.alaindustrial.block.IrradiatedSoilBlock;
import dev.alaindustrial.block.IronChestBlock;
import dev.alaindustrial.block.IronFurnaceBlock;
import dev.alaindustrial.block.KokSagyzBlock;
import dev.alaindustrial.block.KokSagyzRootBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.MaceratorBlock;
import dev.alaindustrial.block.MoonlitSolarPanelBlock;
import dev.alaindustrial.block.ModLiquidBlock;
import dev.alaindustrial.block.OilLiquidBlock;
import dev.alaindustrial.block.PolymerizerBlock;
import dev.alaindustrial.block.PumpBlock;
import dev.alaindustrial.block.RectificationSectionBlock;
import dev.alaindustrial.block.SawmillBlock;
import dev.alaindustrial.block.ShieldingChestBlock;
import dev.alaindustrial.block.SilverChestBlock;
import dev.alaindustrial.block.SolarPanelBlock;
import dev.alaindustrial.block.StorageModuleBlock;
import dev.alaindustrial.block.LightningRodGeneratorBlock;
import dev.alaindustrial.block.StormWindMillBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.CrystalFarmControllerBlock;
import dev.alaindustrial.block.CrystalFarmDoorBlock;
import dev.alaindustrial.block.CrystalFarmShellBlock;
import dev.alaindustrial.block.CrystalSeedbedBlock;
import dev.alaindustrial.block.ReactorShellBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.ReactorLeverBlock;
import dev.alaindustrial.block.ReactorOutletBlock;
import dev.alaindustrial.block.ReactorPortBlock;
import dev.alaindustrial.block.ReactorLampBlock;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.ReactorButtonBlock;
import dev.alaindustrial.block.ReactorControllerBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.ThermalCentrifugeBlock;
import dev.alaindustrial.block.SprinklerBlock;
import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.block.VulcanizerBlock;
import dev.alaindustrial.block.WaterMillBlock;
import dev.alaindustrial.block.WindMillBlock;
import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.core.food.CanningMath;
import dev.alaindustrial.menu.CanningMachineMenu;
import dev.alaindustrial.menu.CreativeEnergySourceMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
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
import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
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
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.CrystalFarmControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorDoorBlockEntity;
import dev.alaindustrial.block.entity.ReactorOutletBlockEntity;
import dev.alaindustrial.core.structure.FuelRodMath;
import dev.alaindustrial.block.entity.ReactorPortBlockEntity;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.item.energy.BatteryItem;
import dev.alaindustrial.item.energy.CrystalBlankItem;
import dev.alaindustrial.item.energy.CrystalTier;
import dev.alaindustrial.item.misc.DurableComponentItem;
import dev.alaindustrial.item.misc.HintItem;
import dev.alaindustrial.item.misc.MutationChipItem;
import dev.alaindustrial.item.misc.OverclockerChipItem;
import dev.alaindustrial.item.misc.ShieldingPouchItem;
import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.item.teleport.RtpChipItem;
import dev.alaindustrial.entity.StockDisplayFrameEntity;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.fluid.FilledCapsuleItem;
import dev.alaindustrial.item.fluid.FluidTankBlockItem;
import dev.alaindustrial.item.fluid.VacuumCapsuleItem;
import dev.alaindustrial.item.material.ModArmorMaterials;
import dev.alaindustrial.item.material.ModToolMaterials;
import dev.alaindustrial.item.material.TemperedIronToolStats;
import dev.alaindustrial.item.misc.FluidPipeBlockItem;
import dev.alaindustrial.item.misc.GuideBookItem;
import dev.alaindustrial.item.misc.ItemPipeBlockItem;
import dev.alaindustrial.item.misc.StockDisplayFrameItem;
import dev.alaindustrial.item.teleport.TeleporterRemoteItem;
import dev.alaindustrial.item.tool.ElectricChainsawDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricChainsawItem;
import dev.alaindustrial.item.tool.ElectricDrillDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import dev.alaindustrial.item.tool.ElectricDrillNetheriteTipItem;
import dev.alaindustrial.item.tool.ElectricSaberItem;
import dev.alaindustrial.item.tool.MagnetItem;
import dev.alaindustrial.item.tool.NetworkAnalyzerItem;
import dev.alaindustrial.item.tool.ScytheItem;
import dev.alaindustrial.item.tool.ScytheTier;
import dev.alaindustrial.item.tool.ScytheTiers;
import dev.alaindustrial.item.tool.WindGaugeItem;
import dev.alaindustrial.item.tool.WrenchItem;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.item.wearable.FluxweaveArmorItem;
import dev.alaindustrial.item.wearable.JetpackItem;
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
import dev.alaindustrial.menu.ShieldingChestMenu;
import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.menu.LightningRodGeneratorMenu;
import dev.alaindustrial.menu.StormWindMillMenu;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.menu.TeleporterStationMenu;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.menu.ThermalCentrifugeMenu;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.menu.AlloySmelterMenu;
import dev.alaindustrial.menu.VulcanizerMenu;
import dev.alaindustrial.menu.FermenterMenu;
import dev.alaindustrial.menu.SprinklerMenu;
import dev.alaindustrial.menu.GalvanicBathMenu;
import java.util.Collections;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;
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
			// MOD-146 — the fermenter: one input slot, two tank gauges, two container pairs.
			menu("fermenter", FermenterMenu::new, s -> ModContent.FERMENTER_MENU = s),
			// MOD-525 — the sprinkler: one gauge and a container pair, the mod's smallest machine menu.
			menu("sprinkler", SprinklerMenu::new, s -> ModContent.SPRINKLER_MENU = s),
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
			// MOD-474 — the shielding chest's window: the iron chest's four rows, its own menu type.
			menu("shielding_chest", ShieldingChestMenu::new, s -> ModContent.SHIELDING_CHEST_MENU = s),
			// MOD-391 — the double chest's 6-row scrolling window, one type for every tier.
			menu("double_chest", DoubleChestMenu::new, s -> ModContent.DOUBLE_CHEST_MENU = s),
			// MOD-278 — the guard field, one menu per tier (same class, tier-specific client factory:
			// a menu type must map to exactly one block for stillValid, and the tiers are three blocks).
			menu("mob_repeller", MobRepellerMenu::new, s -> ModContent.MOB_REPELLER_MENU = s),
			menu("mob_repeller_mv", MobRepellerMvMenu::new, s -> ModContent.MOB_REPELLER_MV_MENU = s),
			menu("mob_repeller_hv", MobRepellerHvMenu::new, s -> ModContent.MOB_REPELLER_HV_MENU = s),
			// MOD-424 — the thermal centrifuge: rotor gauge + status line over the usual two slots.
			menu("thermal_centrifuge", ThermalCentrifugeMenu::new, s -> ModContent.THERMAL_CENTRIFUGE_MENU = s),
			// MOD-386 — the lightning rod: conductor-tip slot, capacitor gauge, status line.
			menu("lightning_rod_generator", LightningRodGeneratorMenu::new,
					s -> ModContent.LIGHTNING_ROD_GENERATOR_MENU = s),
			// MOD-468 — the reactor controller: a slotless diagnostic readout for the room multiblock.
			menu("reactor_controller", ReactorControllerMenu::new,
					s -> ModContent.REACTOR_CONTROLLER_MENU = s),
			// MOD-479 — the creative energy source: switch, output presets, fine slider, charge slot.
			menu("creative_energy_source", CreativeEnergySourceMenu::new,
					s -> ModContent.CREATIVE_ENERGY_SOURCE_MENU = s));

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
	public static final BlockDef<LightningRodGeneratorBlock> LIGHTNING_ROD_GENERATOR =
			block("lightning_rod_generator", LightningRodGeneratorBlock::new,
					s -> ModContent.LIGHTNING_ROD_GENERATOR = s);
	/**
	 * MOD-479 — a technical block, not survival content: an inexhaustible EU source for test
	 * stands. Filed with the generators because that is what it is to the energy network.
	 */
	public static final BlockDef<CreativeEnergySourceBlock> CREATIVE_ENERGY_SOURCE =
			block("creative_energy_source", CreativeEnergySourceBlock::new,
					s -> ModContent.CREATIVE_ENERGY_SOURCE = s);
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
	// Thermal Centrifuge (MOD-424): redstone-started, heated from below; doubles a dust a second time.
	public static final BlockDef<ThermalCentrifugeBlock> THERMAL_CENTRIFUGE =
			block("thermal_centrifuge", ThermalCentrifugeBlock::new, s -> ModContent.THERMAL_CENTRIFUGE = s);

	// ── MOD-468, stage 1: the reactor room's shell. Four inert building blocks and one brain. ──
	/** Wall, floor and ceiling of a reactor room — the only material its shell may be built from. */
	public static final BlockDef<ReactorShellBlock> REACTOR_CASING =
			block("reactor_casing", ReactorShellBlock::new, s -> ModContent.REACTOR_CASING = s);
	/** A window that still counts as shell; capped by share, so a room cannot be all windows. */
	public static final BlockDef<ReactorShellBlock> REACTOR_GLASS =
			block("reactor_glass", ReactorShellBlock::new, s -> ModContent.REACTOR_GLASS = s);
	/** Feedthrough: pipes and cables cross the shell here instead of breaking it (live in stage 3). */
	public static final BlockDef<ReactorPortBlock> REACTOR_PORT =
			block("reactor_port", ReactorPortBlock::new, s -> ModContent.REACTOR_PORT = s);

	public static final BlockDef<ReactorOutletBlock> REACTOR_OUTLET =
			block("reactor_outlet", ReactorOutletBlock::new, s -> ModContent.REACTOR_OUTLET = s);
	/** Shell block that lights the inside of the room — and only once the room is sealed. */
	public static final BlockDef<ReactorLampBlock> REACTOR_LAMP =
			block("reactor_lamp", ReactorLampBlock::new, s -> ModContent.REACTOR_LAMP = s);
	/** The way out: a shielded button that survives what the room is built to contain. */
	public static final BlockDef<ReactorButtonBlock> REACTOR_BUTTON =
			block("reactor_button", ReactorButtonBlock::new, s -> ModContent.REACTOR_BUTTON = s);
	/** The button's twin for a signal that stays on: scram switch, throttle, any held redstone. */
	public static final BlockDef<ReactorLeverBlock> REACTOR_LEVER =
			block("reactor_lever", ReactorLeverBlock::new, s -> ModContent.REACTOR_LEVER = s);

	public static final BlockDef<SteamNozzleBlock> STEAM_NOZZLE =
			block("steam_nozzle", SteamNozzleBlock::new, s -> ModContent.STEAM_NOZZLE = s);
	/** MOD-468 stage 2 — the fuel rack: stands on the floor, fills with rods, shows its level. */
	public static final BlockDef<FuelRodAssemblyBlock> FUEL_ROD_ASSEMBLY =
			block("fuel_rod_assembly", FuelRodAssemblyBlock::new, s -> ModContent.FUEL_ROD_ASSEMBLY = s);
	/** The airlock — pulse-only, self-closing; a room without one cannot be entered and does not form. */
	public static final BlockDef<ReactorDoorBlock> REACTOR_DOOR =
			block("reactor_door", ReactorDoorBlock::new, s -> ModContent.REACTOR_DOOR = s);
	/** The room's brain: scans the shell, reports what is wrong and where. */
	public static final BlockDef<ReactorControllerBlock> REACTOR_CONTROLLER =
			block("reactor_controller", ReactorControllerBlock::new, s -> ModContent.REACTOR_CONTROLLER = s);
	/**
	 * MOD-471 — ground a reactor accident poisoned. No block item: it is left behind, never placed.
	 */
	public static final BlockDef<IrradiatedSoilBlock> IRRADIATED_SOIL =
			block("irradiated_soil", IrradiatedSoilBlock::new, s -> ModContent.IRRADIATED_SOIL = s);
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
	// MOD-537 — kok sagyz, the rubber dandelion. Two blocks, three states of one plant: the flower
	// the player plants, and the root column it grows downward (tip=true is the harvestable end).
	// The root has no BlockItem of its own: it is dug, never placed — its plain item is the harvest.
	public static final BlockDef<KokSagyzBlock> KOK_SAGYZ =
			block("kok_sagyz", KokSagyzBlock::new, s -> ModContent.KOK_SAGYZ = s);
	public static final BlockDef<KokSagyzRootBlock> KOK_SAGYZ_ROOT =
			block("kok_sagyz_root", KokSagyzRootBlock::new, s -> ModContent.KOK_SAGYZ_ROOT = s);

	// ── MOD-505: the crystal greenhouse. Glass and door come from tags, and what grows is vanilla
	// amethyst, so the mod adds only the deck, the brain and the bed. ──
	/** The deck a greenhouse stands on; wears the sealed look once the room passes its scan. */
	public static final BlockDef<CrystalFarmShellBlock> CRYSTAL_FARM_FLOOR =
			block("crystal_farm_floor", CrystalFarmShellBlock::new,
					s -> ModContent.CRYSTAL_FARM_FLOOR = s);
	/** The glazing above it — same class, same sealed look, so the dome closes with the floor. */
	public static final BlockDef<CrystalFarmShellBlock> CRYSTAL_FARM_GLASS =
			block("crystal_farm_glass", CrystalFarmShellBlock::new,
					s -> ModContent.CRYSTAL_FARM_GLASS = s);
	/** The way in: a glazed door in the same frame, so the shell is not broken by a wooden one. */
	public static final BlockDef<CrystalFarmDoorBlock> CRYSTAL_FARM_DOOR =
			block("crystal_farm_door", CrystalFarmDoorBlock::new,
					s -> ModContent.CRYSTAL_FARM_DOOR = s);
	/** The room's brain: seals the greenhouse, then grows every seedbed inside it. */
	public static final BlockDef<CrystalFarmControllerBlock> CRYSTAL_FARM_CONTROLLER =
			block("crystal_farm_controller", CrystalFarmControllerBlock::new,
					s -> ModContent.CRYSTAL_FARM_CONTROLLER = s);
	/** Dead until fed amethyst, then buds real vanilla clusters until its charge runs out. */
	public static final BlockDef<CrystalSeedbedBlock> CRYSTAL_SEEDBED =
			block("crystal_seedbed", CrystalSeedbedBlock::new, s -> ModContent.CRYSTAL_SEEDBED = s);
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
	// Shielding Chest (MOD-474) — NOT a rung of the storage ladder above: it holds the same 36 slots
	// as the iron chest and is bought for what it stops, not for what it fits. It is the only place
	// radioactive material can sit without irradiating everything around it.
	public static final BlockDef<ShieldingChestBlock> SHIELDING_CHEST =
			block("shielding_chest", ShieldingChestBlock::new, s -> ModContent.SHIELDING_CHEST = s);
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
	// The organic chain (MOD-146/MOD-525): the machine that brews waste into biofuel, and the block
	// that sprays what the column cracks out of it.
	public static final BlockDef<FermenterBlock> FERMENTER =
			block("fermenter", FermenterBlock::new, s -> ModContent.FERMENTER = s);
	public static final BlockDef<SprinklerBlock> SPRINKLER =
			block("sprinkler", SprinklerBlock::new, s -> ModContent.SPRINKLER = s);
	// The two fluids' liquid blocks. Same treatment as the fractions above — pourable, never held.
	public static final BlockDef<ModLiquidBlock> BIOFUEL =
			block("biofuel", p -> new ModLiquidBlock(ModContent.BIOFUEL.get(), p),
					s -> ModContent.BIOFUEL_BLOCK = s);
	public static final BlockDef<ModLiquidBlock> NUTRIENT_SOLUTION =
			block("nutrient_solution", p -> new ModLiquidBlock(ModContent.NUTRIENT_SOLUTION.get(), p),
					s -> ModContent.NUTRIENT_SOLUTION_BLOCK = s);

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
			INCUBATOR_DOME, TRELLIS,
			// MOD-537 — kok sagyz: flower first, then its root, so the root's factory can read the
			// flower's registered entry (and its loot/props ordering stays next to the plant group).
			KOK_SAGYZ, KOK_SAGYZ_ROOT,
			TIN_ORE, DEEPSLATE_TIN_ORE, SILVER_ORE, DEEPSLATE_SILVER_ORE,
			NICKEL_ORE, DEEPSLATE_NICKEL_ORE, SULFUR_ORE, DEEPSLATE_SULFUR_ORE, URANIUM_ORE,
			DEEPSLATE_URANIUM_ORE, PALLADIUM_ORE,
			IRON_CHEST, STORAGE_MODULE, SILVER_CHEST, GOLD_CHEST, ELECTRUM_CHEST, SHIELDING_CHEST,
			TEMPERED_IRON_BLOCK, MACHINE_CASING, ADVANCED_MACHINE_CASING, SILVER_PLATE_BLOCK,
			TEMPERED_IRON_PLATE_BLOCK, INDUSTRIAL_WORKBENCH, ENRICHED_URANIUM_TORCH,
			ENRICHED_URANIUM_WALL_TORCH, OIL, DIESEL, FUEL_OIL,
			// MOD-424 — appended rather than filed next to VULCANIZER, per the ordering note above.
			THERMAL_CENTRIFUGE,
			// MOD-386 — likewise appended, not filed with the other generators.
			LIGHTNING_ROD_GENERATOR,
			// MOD-468 — the reactor room's shell, appended as one group.
			REACTOR_CASING, REACTOR_GLASS, REACTOR_PORT, REACTOR_DOOR, REACTOR_CONTROLLER,
			REACTOR_LAMP, REACTOR_BUTTON, FUEL_ROD_ASSEMBLY, REACTOR_OUTLET, IRRADIATED_SOIL,
			// Stage 3 — the exhaust. Outside the shell, so it is not part of the group above.
			STEAM_NOZZLE,
			// MOD-479 — appended at the tail like every block since MOD-403; replay order is load-bearing.
			CREATIVE_ENERGY_SOURCE,
			// MOD-505 — the crystal greenhouse, appended as one group for the same reason.
			CRYSTAL_FARM_FLOOR, CRYSTAL_FARM_GLASS, CRYSTAL_FARM_DOOR, CRYSTAL_FARM_CONTROLLER,
			CRYSTAL_SEEDBED,
			// MOD-146/MOD-525 — the organic chain: the fermenter that brews waste into biofuel, the
			// sprinkler that sprays the solution cracked from it, and the two liquid blocks.
			FERMENTER, SPRINKLER, BIOFUEL, NUTRIENT_SOLUTION,
			// MOD-514 — the reactor room's held-signal switch, appended at the tail like everything
			// since MOD-403 rather than filed with the MOD-468 group above.
			REACTOR_LEVER);

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
			// MOD-386: not a full cube (casing plate + mast), hence noOcclusion — R-PHY-05.
			Map.entry("lightning_rod_generator", machine(p -> p.strength(3.0f, 6.0f)
					.sound(SoundType.METAL).noOcclusion())),
			// MOD-479: an ordinary machine block — breakable, explodable, drops itself.
			Map.entry("creative_energy_source",
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
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
			// MOD-146: an ordinary machine cube, lit while a batch brews.
			Map.entry("fermenter", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			// MOD-525: base plus mast, so the shape is far from a full cube — noOcclusion is mandatory
			// or it would cull its neighbours' faces as if a solid block stood there (R-PHY-05).
			Map.entry("sprinkler", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("vulcanizer", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			// MOD-424 — the centrifuge housing is an open frame around the rotor, so its getShape is inset
			// (1..15) and noOcclusion is mandatory: a non-full shape must not cull its neighbours (R-PHY-05).
			// It glows through those openings while the rotor is doing work, hence litLight.
			Map.entry("thermal_centrifuge", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			// ── MOD-468, stage 1: the reactor room's shell. Tougher than a machine casing (5.0) and far
			// harder to blow up (30.0): the room is what stands between a meltdown and the world, so a
			// creeper must not be able to open it. Glass and door are the same material, hence the same
			// numbers — a window is not a weak point, it just costs the same palladium as a wall.
			Map.entry("reactor_casing", machine(p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL))),
			Map.entry("reactor_glass", machine(p -> p.strength(5.0f, 30.0f).sound(SoundType.GLASS)
					.noOcclusion())),
			Map.entry("reactor_outlet", machine(p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL))),
			Map.entry("reactor_port", machine(p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL))),
			Map.entry("reactor_door", machine(p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL)
					.noOcclusion().pushReaction(PushReaction.DESTROY))),
			Map.entry("reactor_controller", machine(p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL))),
			// MOD-471 — fallout. Soft as the dirt it replaces and no tool requirement: the scar is meant
			// to be shovelled away by a player who would rather not wait for it to fade. randomTicks()
			// is load-bearing — without it the decay in IrradiatedSoilBlock would never run and the
			// contamination would be permanent.
			Map.entry("irradiated_soil", p -> p.strength(0.6f).sound(SoundType.GRAVEL).randomTicks()),
			// The lamp glows only while its shell passes the scan — light is the room's "done" signal.
			Map.entry("reactor_lamp", machine(p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL)
					.lightLevel(ReactorLampBlock::lightLevel))),
			// A button is not a wall: no tool requirement, no collision, and it must not resist an
			// explosion the way the shell does, or it would survive a blast that took the wall with it.
			Map.entry("reactor_button", p -> p.strength(0.5f).sound(SoundType.METAL).noCollision()
					.pushReaction(PushReaction.DESTROY)),
			// The lever is the button's twin down to the numbers: vanilla's own lever is
			// noCollision + strength 0.5 + PushReaction.DESTROY, and ours differs only in the sound
			// family. It must not out-live the wall it hangs on either.
			Map.entry("reactor_lever", p -> p.strength(0.5f).sound(SoundType.METAL).noCollision()
					.pushReaction(PushReaction.DESTROY)),
			// Bolted to the outside of the shell: the shell's toughness, none of its bulk.
			Map.entry("steam_nozzle", machine(p -> p.strength(4.0f, 20.0f).sound(SoundType.METAL)
					.noOcclusion())),
			// A rack, not armour: it lives inside the shell, so it needs none of the shell's toughness.
			// noOcclusion because the casing is transparent and the rods inside have to be drawn.
			Map.entry("fuel_rod_assembly", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion())),
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
			// MOD-537 — kok sagyz. A vanilla-flower block: instabreak, walked through, and randomTicks()
			// is load-bearing (the plant advances on the random tick, like the trellis). A piston must
			// not drag the flower away from the root column it owns.
			Map.entry("kok_sagyz", p -> p.instabreak().sound(SoundType.GRASS)
					.noCollision().randomTicks().pushReaction(PushReaction.DESTROY)),
			// The root is a full dirt-strength cube and ticks never: growth is driven from the flower
			// above, so a random tick here would be work nothing reads.
			Map.entry("kok_sagyz_root", p -> p.strength(0.6f).sound(SoundType.ROOTED_DIRT)),
			// ── MOD-505: the crystal greenhouse. Ordinary machine-grade blocks — the room contains
			// nothing more dangerous than a growing crystal, so none of the reactor's toughness.
			// Deliberately NO randomTicks() anywhere here: growth is driven by the controller's tick,
			// so a random tick would be work the farm never reads.
			// pushReaction BLOCK on all three: they carry the sealed look (and the seedbed its "tended"
			// flag), and a piston shoving one clear of the room's footprint would strand it wearing a
			// state nothing owns any more — the sweep only reaches the box it remembers (found by audit).
			// The incubator's dome is pinned for the same class of reason.
			Map.entry("crystal_farm_floor", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.pushReaction(PushReaction.BLOCK))),
			// noOcclusion is mandatory on the glazing: a transparent full cube that occludes would cull
			// the room away behind it and the greenhouse would show nothing (the reactor glass note).
			Map.entry("crystal_farm_glass", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.GLASS)
					.noOcclusion().pushReaction(PushReaction.BLOCK))),
			// A door is never a full cube, so noOcclusion is mandatory; pushReaction DESTROY keeps a
			// piston from tearing one half of a two-block door away from the other.
			Map.entry("crystal_farm_door", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.COPPER)
					.noOcclusion().pushReaction(PushReaction.DESTROY))),
			Map.entry("crystal_farm_controller", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			// The bed is a block of amethyst that happens to be machinery, so it sounds like the stone
			// it is made of rather than like metal — the cue that it is the thing crystals come out of.
			Map.entry("crystal_seedbed", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.AMETHYST)
					.pushReaction(PushReaction.BLOCK))),
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
			// MOD-423/MOD-511 — Nether ore. The two halves of strength() are set from different
			// arguments and must not be read as one "toughness" number.
			// destroyTime 4.5 — same as the deepslate variants: a diamond pickaxe clears a vein in
			// seconds. Ancient debris' 30.0 is deliberately NOT copied; digging speed is not the point.
			// explosionResistance 1200.0 — copied from ancient debris exactly (MOD-511). Palladium is
			// the mod's only Nether ore and sits in the layer where creepers, ghasts, beds and respawn
			// anchors go off by accident; at the old 3.0 a stray blast erased the vein along with the
			// netherrack around it. ServerExplosion drains (resistance + 0.3) * 0.3 per 0.3-block step,
			// so 1200.0 burns ~360 power per step and stops every vanilla blast (TNT 4, creeper 3/6,
			// ghast 1, bed/anchor 5, wither skull 1, wither spawn 7) at the first block it touches.
			// This buys immunity to EXPLOSIONS only: a wither's body-charge destruction is gated by
			// #minecraft:wither_immune, not by resistance, so it still breaks palladium — exactly as
			// it breaks ancient debris. Behavioural parity with debris is the goal, not invulnerability.
			Map.entry("palladium_ore", machine(p -> p.strength(4.5f, 1200.0f).sound(SoundType.NETHER_ORE))),
			Map.entry("iron_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			// MOD-287 — plain full cube, no noOcclusion(): unlike the chests it has no 3D renderer.
			Map.entry("storage_module", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("silver_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("gold_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("electrum_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			// MOD-474 — same stats as the storage chests: the shielding is a radiation rule, not armour.
			Map.entry("shielding_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
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
			// The organic chain's two fluids (MOD-146/MOD-525): same vanilla liquid-block chain, their
			// own map colours — biofuel olive, nutrient solution a brighter green.
			Map.entry("biofuel", p -> p.mapColor(MapColor.COLOR_GREEN).replaceable().noCollision()
					.strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()
					.sound(SoundType.EMPTY)),
			Map.entry("nutrient_solution", p -> p.mapColor(MapColor.EMERALD).replaceable().noCollision()
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
	// Items — the COMPOSITION, not just the construction (MOD-305 / MOD-306 / MOD-554)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One {@code Item} to register. MOD-306 moved the per-item CONSTRUCTION here; MOD-554 moves the
	 * <b>list itself</b>, which until then was kept by hand in two files ({@code ModItems} on Fabric,
	 * {@code ModItemsNeoForge}) — 273 registrations plus 273 {@code ModContent} bindings in each,
	 * guarded only by a Python set-comparison after the fact. An item declared here registers on BOTH
	 * loaders or on neither.
	 *
	 * <p><b>What each loader still does.</b> Only the registration <i>mechanism</i>, because the two
	 * genuinely differ: Fabric constructs the item eagerly and stamps the id itself
	 * ({@code new Item.Properties().setId(key)} → {@code Registry.register}), NeoForge hands the same
	 * factory to {@code DeferredRegister.Items#registerItem}, which calls it later with a
	 * {@code Properties} whose id it derived from the deferred key. Neither side chooses the id any more.
	 *
	 * <p><b>Why the factory closes over registry IDS, not handles.</b> A block item needs its block, a
	 * bucket its fluid, the display frame its entity type — all of which are per-loader objects
	 * ({@code Block} vs {@code DeferredBlock}). Resolving them by id from the vanilla registry INSIDE the
	 * factory works on both loaders for the same reason {@link BlockEntityDef#blockSet()} does: the
	 * factory runs after those registries are populated (Fabric registers blocks/fluids/entities before
	 * items in its entrypoint; on NeoForge the {@code RegisterEvent} order does it). The comment that
	 * used to sit here — "a shared factory would have to close over a loader type" — was disproved by
	 * this manifest's own liquid blocks, which have closed over {@code ModContent} inside their factory
	 * since MOD-403.
	 *
	 * <p><b>Order is load-bearing.</b> Both loaders replay {@link #ITEMS} in list order, and one entry
	 * depends on an earlier one: {@code filled_vacuum_capsule} takes the empty capsule as its
	 * craft-remainder. Keep new entries appended rather than interleaved.
	 *
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory builds the item from the loader-supplied {@code Properties}, or {@code null} for an
	 *                entry whose CLASS differs per loader — see {@link #loaderItem}
	 * @param bind    publishes the registered item into its {@link ModContent} slot
	 */
	public record ItemDef(String id, @Nullable Function<Item.Properties, ? extends Item> factory,
			Consumer<Supplier<Item>> bind) {
	}

	/** An item with a hand-written construction. */
	private static ItemDef item(String id, Function<Item.Properties, ? extends Item> factory,
			Consumer<Supplier<Item>> bind) {
		return new ItemDef(id, factory, bind);
	}

	/**
	 * A plain crafting component: nothing but {@code new Item(properties)} — dusts, plates, ingots, raw
	 * ores, by-products. The largest group by far.
	 */
	private static ItemDef plain(String id, Consumer<Supplier<Item>> bind) {
		return item(id, Item::new, bind);
	}

	/**
	 * Two gray hint lines under the name, keyed {@code item.alaindustrial.<id>.hint} / {@code .hint2}.
	 * The key strings are derived from the id here rather than typed twice per loader.
	 */
	private static ItemDef hint(String id, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new HintItem(p, "item.alaindustrial." + id + ".hint",
				"item.alaindustrial." + id + ".hint2"), bind);
	}

	/** An overclocker chip of a fixed tier (MOD-393) — a hint item that also carries its step count. */
	private static ItemDef overclockerChip(String id, int tier, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new OverclockerChipItem(p, tier, "item.alaindustrial." + id + ".hint",
				"item.alaindustrial." + id + ".hint2"), bind);
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
	private static ItemDef durableComponent(String id, IntSupplier maxDamage, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new DurableComponentItem(p.durability(maxDamage.getAsInt())), bind);
	}

	/**
	 * An armour piece (MOD-056/466/470). {@code humanoidArmor(material, type)} wires durability,
	 * attributes, enchantability, the {@code EQUIPPABLE} component (equip sound + asset id from the
	 * material) and the repair tag in one call — exactly how vanilla {@code Items.IRON_HELMET} is built.
	 */
	private static ItemDef armor(String id, ArmorMaterial material, ArmorType type,
			Consumer<Supplier<Item>> bind) {
		return item(id, p -> new Item(p.humanoidArmor(material, type)), bind);
	}

	/**
	 * A scythe tier (MOD-068/168). The id and every stat come from the loader-neutral
	 * {@link ScytheTiers} catalogue, so a balance tweak cannot drift between the loaders.
	 * {@code .hoe(...)} attaches the data-driven tool component exactly like a vanilla hoe, but the
	 * instance is a {@link ScytheItem}: right-click clears an area instead of tilling.
	 */
	private static ItemDef scythe(ScytheTier tier, Consumer<Supplier<Item>> bind) {
		return item(tier.id(), p -> {
			Item.Properties props = p.hoe(tier.material(), tier.attackDamage(), -1.0f);
			return new ScytheItem(tier.profile(), tier.fireResistant() ? props.fireResistant() : props);
		}, bind);
	}

	/**
	 * A filled mod-fluid bucket (MOD-238 oil, MOD-251 diesel/fuel oil, MOD-146/525 the organic pair) —
	 * built exactly like vanilla {@code Items.WATER_BUCKET}. The still fluid is resolved by id: on both
	 * loaders the fluid registry is populated before the item factory runs (Fabric calls
	 * {@code ModFluids.init()} first; on NeoForge the FLUID {@code RegisterEvent} fires before ITEM).
	 */
	private static ItemDef bucket(String id, String fluidId, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new BucketItem(registeredFluid(fluidId),
				p.craftRemainder(Items.BUCKET).stacksTo(1)), bind);
	}

	/**
	 * An item whose CLASS differs per loader — the manifest owns its id, its place in the order and its
	 * {@link ModContent} slot, and the loader supplies the constructor through its own override map
	 * (see {@code ModItems.LOADER_ITEMS} / {@code ModItemsNeoForge.LOADER_ITEMS}).
	 *
	 * <p>There are five, and each is a loader-API seam rather than a difference of content: the forge
	 * hammer routes a craft-remainder hook whose signature differs on the two loaders, and the four
	 * electric hoe/shovel tiers must declare NeoForge {@code ItemAbility}s that Fabric has no concept of
	 * (MOD-378/MOD-379 — without them NeoForge's patched {@code HoeItem}/{@code ShovelItem} refuse to
	 * till and make no paths).
	 *
	 * <p><b>There is deliberately no shared default.</b> A default would let a loader that forgot its
	 * override ship the wrong class silently — which is exactly the defect MOD-378 and MOD-379 each
	 * fixed once. With {@code factory == null} the replay throws at startup instead.
	 */
	private static ItemDef loaderItem(String id, Consumer<Supplier<Item>> bind) {
		return new ItemDef(id, null, bind);
	}

	/** A block item whose registry id equals its block's ({@code alaindustrial:macerator} → the block). */
	private static ItemDef blockItem(String id, Consumer<Supplier<BlockItem>> bind) {
		return blockItem(id, id, bind);
	}

	/** A block item whose id differs from the block's ({@code kok_sagyz_seeds} places {@code kok_sagyz}). */
	private static ItemDef blockItem(String id, String blockId, Consumer<Supplier<BlockItem>> bind) {
		return blockItem(id, blockId, UnaryOperator.identity(), bind);
	}

	/**
	 * A block item with extra shared {@code Properties} (MOD-479: rarity + tooltip style). The extras
	 * live here rather than per loader because {@code loader_parity_check} compares the SETS of item
	 * ids, never their properties — a {@code .rarity(...)} written once per loader would drift the first
	 * time somebody edited one of them, with every gate still green.
	 */
	private static ItemDef blockItem(String id, String blockId, UnaryOperator<Item.Properties> extra,
			Consumer<Supplier<BlockItem>> bind) {
		return blockItem(id, p -> new BlockItem(registeredBlock(blockId), extra.apply(p)), bind);
	}

	/** A block item under its own {@code BlockItem} subclass (pipes, the tank, the torch). */
	private static ItemDef blockItem(String id, Function<Item.Properties, ? extends BlockItem> factory,
			Consumer<Supplier<BlockItem>> bind) {
		return new ItemDef(id, p -> factory.apply(p.useBlockDescriptionPrefix()), blockItemSlot(bind));
	}

	/**
	 * Adapts a {@code Supplier<BlockItem>} {@link ModContent} slot to the {@code Supplier<Item>} the
	 * replay hands out. The cast cannot lie: only {@link #blockItem} reaches this, and every one of its
	 * factories returns a {@code BlockItem}. Keeping the slot type in the helper signature is what makes
	 * pointing a block item at a plain-item slot (or the reverse) a compile error.
	 */
	private static Consumer<Supplier<Item>> blockItemSlot(Consumer<Supplier<BlockItem>> bind) {
		return s -> bind.accept(() -> (BlockItem) s.get());
	}

	/**
	 * Presentation for a block item a player cannot obtain (MOD-479): the light-purple name vanilla puts
	 * on the dragon egg, the barrier and the command block — technical blocks, exactly like this one —
	 * plus this mod's own tooltip frame.
	 *
	 * <p><b>{@code EPIC} is the ceiling.</b> NeoForge marks {@code Rarity} extensible and Fabric does
	 * not, so inventing a "legendary" tier would be an asymmetry by construction.
	 *
	 * <p>The tooltip style is an id, not a sprite: {@code TooltipRenderUtil} expands it into
	 * {@code alaindustrial:tooltip/creative_background} and {@code …_frame}. BOTH must exist — a custom
	 * style replaces the vanilla background instead of falling back to it, so shipping only the frame
	 * leaves the text sitting on a missing texture. Nothing in the repo checks that; see the task.
	 */
	public static final UnaryOperator<Item.Properties> CREATIVE_ONLY_ITEM = props -> props
			.rarity(Rarity.EPIC)
			.component(DataComponents.TOOLTIP_STYLE, Industrialization.id("creative"));

	/**
	 * The factory a loader must call for {@code def}: its own override when the entry is
	 * {@linkplain #loaderItem loader-specific}, the manifest's own otherwise. Both mismatches throw
	 * rather than picking a side — an override for a shared entry would shadow the shared definition
	 * on one loader only, which is the drift this manifest exists to remove.
	 *
	 * @param def      the manifest entry being replayed
	 * @param override this loader's entry from its {@code LOADER_ITEMS} map, or {@code null}
	 */
	public static Function<Item.Properties, ? extends Item> itemFactory(ItemDef def,
			@Nullable Function<Item.Properties, ? extends Item> override) {
		if (def.factory() == null) {
			if (override == null) {
				throw new IllegalStateException("ItemDef '" + def.id() + "' is declared loader-specific "
						+ "(no shared factory), but this loader supplied no override for it");
			}
			return override;
		}
		if (override != null) {
			throw new IllegalStateException("ItemDef '" + def.id() + "' has a shared factory, so this "
					+ "loader's override for it would silently shadow the shared definition");
		}
		return def.factory();
	}

	/**
	 * The registered block for {@code blockId}, for a block item's factory.
	 *
	 * <p>Resolved from the vanilla registry rather than from a loader handle, for the same reason
	 * {@link BlockEntityDef#blockSet()} does it: the id is the one name both loaders share. An
	 * unregistered id throws instead of quietly resolving to {@code AIR} — {@code getValue} on a
	 * defaulted registry substitutes AIR, so AIR is what a typo looks like, and a {@code BlockItem} over
	 * AIR would place nothing while looking perfectly registered.
	 */
	private static Block registeredBlock(String blockId) {
		Identifier key = Industrialization.id(blockId);
		Block block = BuiltInRegistries.BLOCK.getValue(key);
		if (block == Blocks.AIR) {
			throw new IllegalStateException("ItemDef: block '" + key + "' is not registered (yet) — "
					+ "its block item cannot be built");
		}
		return block;
	}

	/** The registered still fluid for {@code fluidId}, for a bucket's factory. See {@link #registeredBlock}. */
	private static FlowingFluid registeredFluid(String fluidId) {
		Identifier key = Industrialization.id(fluidId);
		Fluid fluid = BuiltInRegistries.FLUID.getValue(key);
		if (!(fluid instanceof FlowingFluid flowing)) {
			throw new IllegalStateException("ItemDef: fluid '" + key + "' is not a registered FlowingFluid "
					+ "(got " + fluid + ") — its bucket cannot be built");
		}
		return flowing;
	}

	/** The registered item for {@code itemId} — only an EARLIER entry of {@link #ITEMS} can be asked for. */
	private static Item registeredItem(String itemId) {
		Identifier key = Industrialization.id(itemId);
		Item item = BuiltInRegistries.ITEM.getValue(key);
		if (item == Items.AIR) {
			throw new IllegalStateException("ItemDef: item '" + key + "' is not registered (yet) — an "
					+ "entry may only reference an item declared EARLIER in ContentManifest.ITEMS");
		}
		return item;
	}

	/**
	 * The stock display frame's entity type (MOD-066), typed for {@code StockDisplayFrameItem}.
	 *
	 * <p>The cast is unchecked because the registry is heterogeneous, and safe because
	 * {@code alaindustrial:stock_display_frame} is registered from exactly one place on each loader with
	 * exactly this entity class. The same cast, for the same reason, is in
	 * {@code StockDisplayFrameScenarios}.
	 */
	@SuppressWarnings("unchecked")
	private static EntityType<StockDisplayFrameEntity> stockDisplayFrameType() {
		Identifier key = Industrialization.id("stock_display_frame");
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key);
		if (type == null) {
			throw new IllegalStateException("ItemDef: entity type '" + key + "' is not registered (yet) — "
					+ "the display-frame item cannot be built");
		}
		return (EntityType<StockDisplayFrameEntity>) type;
	}

	/**
	 * Every item, in one shared registration order — the single source of the mod's item composition
	 * (MOD-554). Both loaders replay this list; see {@link ItemDef}.
	 */
	public static final List<ItemDef> ITEMS = List.of(
			// Crafting components (referenced by MaceratorBlockEntity recipes and crafting recipes).
			plain("electronic_circuit", s -> ModContent.ELECTRONIC_CIRCUIT = s),
			// MOD-299 — the MV circuit: electronic circuit + gold plates + rubber. Gates the advanced casing.
			plain("advanced_circuit", s -> ModContent.ADVANCED_CIRCUIT = s),
			item("assembly_blueprint", p -> new AssemblyBlueprintItem(p.stacksTo(AssemblyBlueprintItem.BLANK_STACK_SIZE)), s -> ModContent.ASSEMBLY_BLUEPRINT = s),
			// Copper Coil — crafting component (copper cable + tin), gates the Electric Drill.
			plain("copper_coil", s -> ModContent.COPPER_COIL = s),
			// Resonance chain (MOD-116): spatial stock -> the coil above the copper one -> the station's chip.
			plain("spatial_crystal", s -> ModContent.SPATIAL_CRYSTAL = s),
			plain("resonance_coil", s -> ModContent.RESONANCE_COIL = s),
			// Random Jump Chip (MOD-116): the teleporter station's one permanent upgrade. Its own class
			// rather than a hintItem because fitting it is an interaction, not just a tooltip.
			item("rtp_chip", p -> new RtpChipItem(p, "item.alaindustrial.rtp_chip.hint",
				"item.alaindustrial.rtp_chip.hint2"), s -> ModContent.RTP_CHIP = s),
			plain("alignment_chip_day", s -> ModContent.ALIGNMENT_CHIP_DAY = s),
			plain("alignment_chip_night", s -> ModContent.ALIGNMENT_CHIP_NIGHT = s),
			// Upgrade chips (MOD-080): empty blank + the mute upgrade. Each shows a gray hint line.
			hint("empty_chip", s -> ModContent.EMPTY_CHIP = s),
			// Incubator (MOD-118): mode chips, by-products and the tier-1 evolution materials.
			item("mutation_chip_transform", p -> new MutationChipItem(p, IncubatorMode.TRANSFORM), s -> ModContent.MUTATION_CHIP_TRANSFORM = s),
			item("mutation_chip_duplicate", p -> new MutationChipItem(p, IncubatorMode.DUPLICATE), s -> ModContent.MUTATION_CHIP_DUPLICATE = s),
			item("mutation_chip_create", p -> new MutationChipItem(p, IncubatorMode.CREATE), s -> ModContent.MUTATION_CHIP_CREATE = s),
			plain("irradiated_slag", s -> ModContent.IRRADIATED_SLAG = s),
			plain("irradiated_diamond", s -> ModContent.IRRADIATED_DIAMOND = s),
			plain("resonant_shard", s -> ModContent.RESONANT_SHARD = s),
			plain("mutagen_dust", s -> ModContent.MUTAGEN_DUST = s),
			// Oil → rubber chain: the polymerizer's product and the vulcanizer's cured output.
			plain("raw_rubber", s -> ModContent.RAW_RUBBER = s),
			// Organic chain (MOD-146): the fermenter's solid leftover, stock for a later task.
			plain("biomass", s -> ModContent.BIOMASS = s),
			plain("rubber", s -> ModContent.RUBBER = s),
			// Cotton (MOD-280): the seed is planted onto a trellis by right-click (the block handles it, so this
			// stays a plain Item — no BlockItem/ItemNameBlockItem), the fibre is the harvest.
			plain("cotton_seeds", s -> ModContent.COTTON_SEEDS = s),
			plain("cotton_fiber", s -> ModContent.COTTON_FIBER = s),
			// Kok sagyz (MOD-537): the dug root macerates into raw rubber; inulin rides along as the
			// by-product. The seeds are a BlockItem — see the blockItem block below.
			plain("kok_sagyz_root", s -> ModContent.KOK_SAGYZ_ROOT_ITEM = s),
			// MOD-537 — the kok sagyz harvest and its by-product: the dug root (macerable into
			// raw rubber + inulin) and the inulin itself.
			plain("inulin", s -> ModContent.INULIN = s),
			// Fluxweave chain (MOD-127): silver-plated fibre, then the woven sheet. Both are plain crafting
			// components — the EU buffer lives on the armor, not on the material.
			plain("flux_thread", s -> ModContent.FLUX_THREAD = s),
			plain("fluxweave_cloth", s -> ModContent.FLUXWEAVE_CLOTH = s),
			plain("unstable_isotope", s -> ModContent.UNSTABLE_ISOTOPE = s),
			hint("mute_chip", s -> ModContent.MUTE_CHIP = s),
			hint("stats_chip", s -> ModContent.STATS_CHIP = s),
			// Soul Vessel (MOD-278): the repeller's upgrade currency. stacksTo(1) is not a balance knob —
			// the kill counter is a stack component, and stacks with different components never merge, so a
			// stackable vessel would only ever look broken.
			// Soul Vessel (MOD-278): the Mob Repeller upgrade currency.
			item("soul_vessel", p -> new SoulVesselItem(p.stacksTo(1)), s -> ModContent.SOUL_VESSEL = s),
			// Overclocker chips (MOD-392/393): three tiers trading energy for machine speed.
			overclockerChip("overclocker_chip_i", 1, s -> ModContent.OVERCLOCKER_CHIP_I = s),
			overclockerChip("overclocker_chip_ii", 2, s -> ModContent.OVERCLOCKER_CHIP_II = s),
			overclockerChip("overclocker_chip_iii", 3, s -> ModContent.OVERCLOCKER_CHIP_III = s),
			// Energy clots (MOD-393): surplus grid power packed into an item by the energy condenser.
			hint("energy_clot_i", s -> ModContent.ENERGY_CLOT_I = s),
			hint("energy_clot_ii", s -> ModContent.ENERGY_CLOT_II = s),
			hint("energy_clot_iii", s -> ModContent.ENERGY_CLOT_III = s),
			// Cable breaker (MOD-276): clamps onto a laid cable and cuts the line for maintenance. A hint
			// item because the whole control scheme (install / throw / pry off) is gestures on the wire,
			// with no GUI anywhere to explain itself.
			// Cable breaker (MOD-276): clamps onto a laid cable to cut the line for maintenance.
			hint("cable_breaker", s -> ModContent.CABLE_BREAKER = s),
			// Rotor / wheel (MOD-189): durability components — wear shows as a vanilla durability bar and, being
			// damageable, they are automatically non-stackable. maxDamage from Config (registration-time).
			durableComponent("windmill_rotor", () -> Config.windMillRotorMaxDamage, s -> ModContent.WINDMILL_ROTOR = s),
			durableComponent("water_mill_wheel", () -> Config.waterMillWheelMaxDamage, s -> ModContent.WATER_MILL_WHEEL = s),
			// MOD-385: upper grades — richer craft, higher output, longer life. See core.machine.ComponentTier.
			durableComponent("windmill_rotor_reinforced", () -> Config.windMillRotorReinforcedMaxDamage, s -> ModContent.WINDMILL_ROTOR_REINFORCED = s),
			durableComponent("windmill_rotor_advanced", () -> Config.windMillRotorAdvancedMaxDamage, s -> ModContent.WINDMILL_ROTOR_ADVANCED = s),
			// MOD-386: the lightning rod's conductor tips.
			durableComponent("lightning_rod_conductor_tip", () -> Config.lightningRodTipMaxDamage, s -> ModContent.LIGHTNING_ROD_CONDUCTOR_TIP = s),
			durableComponent("lightning_rod_conductor_tip_reinforced", () -> Config.lightningRodTipReinforcedMaxDamage, s -> ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_REINFORCED = s),
			durableComponent("lightning_rod_conductor_tip_advanced", () -> Config.lightningRodTipAdvancedMaxDamage, s -> ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_ADVANCED = s),
			durableComponent("water_mill_wheel_reinforced", () -> Config.waterMillWheelReinforcedMaxDamage, s -> ModContent.WATER_MILL_WHEEL_REINFORCED = s),
			durableComponent("water_mill_wheel_advanced", () -> Config.waterMillWheelAdvancedMaxDamage, s -> ModContent.WATER_MILL_WHEEL_ADVANCED = s),
			plain("wooden_gear", s -> ModContent.WOODEN_GEAR = s),
			// Metal gears (MOD-105): crafting components for machinery still to come.
			plain("stone_gear", s -> ModContent.STONE_GEAR = s),
			plain("iron_gear", s -> ModContent.IRON_GEAR = s),
			plain("gold_gear", s -> ModContent.GOLD_GEAR = s),
			plain("silver_gear", s -> ModContent.SILVER_GEAR = s),
			// MOD-534: electrum's first use as a gear — the netherite tip's head drive.
			plain("electrum_gear", s -> ModContent.ELECTRUM_GEAR = s),
			// MOD-534: the assembled drill bit and the smithing template that gates it.
			plain("netherite_drill_head", s -> ModContent.NETHERITE_DRILL_HEAD = s),
			// Netherite Drill Upgrade smithing template (MOD-534) — the mod's first smithing template, and the
			// gate on its top drill tier. Built on vanilla's own SmithingTemplateItem rather than a plain Item
			// so the smithing screen shows what goes in which slot and greys the empty slots with the right
			// sprites, exactly as it does for the vanilla netherite upgrade. Constructor argument order was
			// read off the bytecode of Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE's factory (project rule 1):
			// appliesTo, ingredients, baseSlotDescription, additionsSlotDescription, then the two icon lists.
			item("netherite_drill_upgrade_smithing_template", p -> new SmithingTemplateItem(
				Component.translatable("item.alaindustrial.netherite_drill_upgrade_smithing_template.applies_to")
						.withStyle(ChatFormatting.BLUE),
				Component.translatable("item.alaindustrial.netherite_drill_upgrade_smithing_template.ingredients")
						.withStyle(ChatFormatting.BLUE),
				Component.translatable("item.alaindustrial.netherite_drill_upgrade_smithing_template.base_slot_description"),
				Component.translatable("item.alaindustrial.netherite_drill_upgrade_smithing_template.additions_slot_description"),
				List.of(Identifier.withDefaultNamespace("container/slot/pickaxe")),
				List.of(Identifier.withDefaultNamespace("container/slot/ingot")),
				p), s -> ModContent.NETHERITE_DRILL_UPGRADE_SMITHING_TEMPLATE = s),
			plain("tempered_iron", s -> ModContent.TEMPERED_IRON = s),
			item("tempered_iron_pickaxe", p -> new Item(p.pickaxe(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.PICKAXE.attackDamage(), TemperedIronToolStats.PICKAXE.attackSpeed())), s -> ModContent.TEMPERED_IRON_PICKAXE = s),
			// Axe/Hoe/Shovel extend their vanilla subclasses so useOn (stripping/tilling/path) works —
			// in 26.2 these subclasses still exist and carry that behavior; PickaxeItem/SwordItem were
			// removed, so pickaxe/sword stay plain Item with .pickaxe()/.sword().
			item("tempered_iron_axe", p -> new AxeItem(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.AXE.attackDamage(), TemperedIronToolStats.AXE.attackSpeed(), p), s -> ModContent.TEMPERED_IRON_AXE = s),
			item("tempered_iron_hoe", p -> new HoeItem(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.HOE.attackDamage(), TemperedIronToolStats.HOE.attackSpeed(), p), s -> ModContent.TEMPERED_IRON_HOE = s),
			item("tempered_iron_shovel", p -> new ShovelItem(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.SHOVEL.attackDamage(), TemperedIronToolStats.SHOVEL.attackSpeed(), p), s -> ModContent.TEMPERED_IRON_SHOVEL = s),
			item("tempered_iron_sword", p -> new Item(p.sword(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.SWORD.attackDamage(), TemperedIronToolStats.SWORD.attackSpeed())), s -> ModContent.TEMPERED_IRON_SWORD = s),
			// Tempered-iron armor (MOD-056). MC 26.2 has no ArmorItem: each piece is a plain Item whose
			// equipment properties are attached via Item.Properties.humanoidArmor(ArmorMaterial, ArmorType).
			// That helper chains durability, attributes, enchantability, the EQUIPPABLE component (with the
			// material's asset id + equip sound) and the repair tag in one go (javap-verified).
			armor("tempered_iron_helmet", ModArmorMaterials.TEMPERED_IRON, ArmorType.HELMET, s -> ModContent.TEMPERED_IRON_HELMET = s),
			armor("tempered_iron_chestplate", ModArmorMaterials.TEMPERED_IRON, ArmorType.CHESTPLATE, s -> ModContent.TEMPERED_IRON_CHESTPLATE = s),
			armor("tempered_iron_leggings", ModArmorMaterials.TEMPERED_IRON, ArmorType.LEGGINGS, s -> ModContent.TEMPERED_IRON_LEGGINGS = s),
			armor("tempered_iron_boots", ModArmorMaterials.TEMPERED_IRON, ArmorType.BOOTS, s -> ModContent.TEMPERED_IRON_BOOTS = s),
			// Fluxweave armour (MOD-127): humanoidArmor gives it ordinary armour stats; FluxweaveArmorItem
			// layers the charge-driven worn asset and bonus attributes on top of that.
			item("fluxweave_helmet", p -> new FluxweaveArmorItem(
					FluxweaveArmorItem.equipmentProperties(p, ArmorType.HELMET), ArmorType.HELMET), s -> ModContent.FLUXWEAVE_HELMET = s),
			item("fluxweave_chestplate", p -> new FluxweaveArmorItem(
					FluxweaveArmorItem.equipmentProperties(p, ArmorType.CHESTPLATE), ArmorType.CHESTPLATE), s -> ModContent.FLUXWEAVE_CHESTPLATE = s),
			item("fluxweave_leggings", p -> new FluxweaveArmorItem(
					FluxweaveArmorItem.equipmentProperties(p, ArmorType.LEGGINGS), ArmorType.LEGGINGS), s -> ModContent.FLUXWEAVE_LEGGINGS = s),
			item("fluxweave_boots", p -> new FluxweaveArmorItem(
					FluxweaveArmorItem.equipmentProperties(p, ArmorType.BOOTS), ArmorType.BOOTS), s -> ModContent.FLUXWEAVE_BOOTS = s),
			// Shielding suit (MOD-470): ordinary armour items; the shielding lives in the item tag.
			armor("shielding_helmet", ModArmorMaterials.SHIELDING, ArmorType.HELMET, s -> ModContent.SHIELDING_HELMET = s),
			armor("shielding_chestplate", ModArmorMaterials.SHIELDING, ArmorType.CHESTPLATE, s -> ModContent.SHIELDING_CHESTPLATE = s),
			armor("shielding_leggings", ModArmorMaterials.SHIELDING, ArmorType.LEGGINGS, s -> ModContent.SHIELDING_LEGGINGS = s),
			armor("shielding_boots", ModArmorMaterials.SHIELDING, ArmorType.BOOTS, s -> ModContent.SHIELDING_BOOTS = s),
			// Insulated set (MOD-466): ordinary armour items; the insulation lives in the item tag.
			armor("insulated_helmet", ModArmorMaterials.INSULATED, ArmorType.HELMET, s -> ModContent.INSULATED_HELMET = s),
			armor("insulated_chestplate", ModArmorMaterials.INSULATED, ArmorType.CHESTPLATE, s -> ModContent.INSULATED_CHESTPLATE = s),
			armor("insulated_leggings", ModArmorMaterials.INSULATED, ArmorType.LEGGINGS, s -> ModContent.INSULATED_LEGGINGS = s),
			armor("insulated_boots", ModArmorMaterials.INSULATED, ArmorType.BOOTS, s -> ModContent.INSULATED_BOOTS = s),
			plain("iron_dust", s -> ModContent.IRON_DUST = s),
			plain("copper_dust", s -> ModContent.COPPER_DUST = s),
			plain("gold_dust", s -> ModContent.GOLD_DUST = s),
			plain("coal_dust", s -> ModContent.COAL_DUST = s),
			plain("diamond_dust", s -> ModContent.DIAMOND_DUST = s),
			plain("emerald_dust", s -> ModContent.EMERALD_DUST = s),
			plain("empty_can", s -> ModContent.EMPTY_CAN = s),
			// Canned Ration (MOD-383): the mod's first edible item, and the one place its food numbers are
			// declared. Its nutrition is fixed no matter what went into the machine — that is precisely
			// what lets every ration stack with every other one, which is the entire point of the machine
			// (a ration that remembered its source would carry a different component and never merge).
			// Its Consumable is built here rather than inherited from the source food, so effects like a
			// golden apple's regeneration have nowhere to travel.
			item("canned_ration", p -> new Item(p.food(
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
						.build())), s -> ModContent.CANNED_RATION = s),
			plain("lapis_dust", s -> ModContent.LAPIS_DUST = s),
			plain("tin_dust", s -> ModContent.TIN_DUST = s),
			plain("raw_tin", s -> ModContent.RAW_TIN = s),
			plain("tin_ingot", s -> ModContent.TIN_INGOT = s),
			plain("silver_dust", s -> ModContent.SILVER_DUST = s),
			plain("raw_silver", s -> ModContent.RAW_SILVER = s),
			plain("silver_ingot", s -> ModContent.SILVER_INGOT = s),
			plain("nickel_dust", s -> ModContent.NICKEL_DUST = s),
			plain("raw_nickel", s -> ModContent.RAW_NICKEL = s),
			plain("nickel_ingot", s -> ModContent.NICKEL_INGOT = s),
			// MOD-064 alloys.
			plain("bronze_ingot", s -> ModContent.BRONZE_INGOT = s),
			plain("invar_ingot", s -> ModContent.INVAR_INGOT = s),
			plain("cupronickel_ingot", s -> ModContent.CUPRONICKEL_INGOT = s),
			plain("electrum_ingot", s -> ModContent.ELECTRUM_INGOT = s),
			// MOD-534: the alloy smelter's endgame product, built on a vanilla netherite ingot.
			plain("netherite_alloy_ingot", s -> ModContent.NETHERITE_ALLOY_INGOT = s),
			plain("sulfur_dust", s -> ModContent.SULFUR_DUST = s),
			plain("raw_sulfur", s -> ModContent.RAW_SULFUR = s),
			plain("uranium_dust", s -> ModContent.URANIUM_DUST = s),
			plain("raw_uranium", s -> ModContent.RAW_URANIUM = s),
			plain("uranium_ingot", s -> ModContent.URANIUM_INGOT = s),
			// MOD-424: the centrifuge's product, and what smelting it yields.
			plain("uranium_shavings", s -> ModContent.URANIUM_SHAVINGS = s),
			// MOD-424: the centrifuge's product and what smelting it yields.
			plain("refined_uranium", s -> ModContent.REFINED_URANIUM = s),
			// MOD-468, stage 1 — the shielding chain and the controller's parts.
			plain("shielding_alloy_ingot", s -> ModContent.SHIELDING_ALLOY_INGOT = s),
			plain("shielding_alloy_plate", s -> ModContent.SHIELDING_ALLOY_PLATE = s),
			plain("shielding_alloy_reinforced_plate", s -> ModContent.SHIELDING_ALLOY_REINFORCED_PLATE = s),
			plain("reactor_circuit", s -> ModContent.REACTOR_CIRCUIT = s),
			plain("control_rod_drive", s -> ModContent.CONTROL_ROD_DRIVE = s),
			// Uranium Fuel Rod (MOD-468 stage 4). Durability IS its remaining charge: the column wears the
			// rod down as the reactor draws on it, so a half-spent rod shows a half-empty bar in the hand
			// and can be pulled out and put back without losing what is left. Before this it was a plain
			// item and a rod taken out mid-burn was simply destroyed.
			item("uranium_fuel_rod", p -> new Item(p.durability(FuelRodMath.ROD_DURABILITY)), s -> ModContent.URANIUM_FUEL_ROD = s),
			// MOD-468 stage 4: the empty casing. Crafted 3x3, filled with one refined uranium, and
			// handed back by the column when its charge is spent — refuelling a reactor is topping up
			// casings you already own, not building rods from scratch every time.
			plain("empty_fuel_rod", s -> ModContent.EMPTY_FUEL_ROD = s),
			plain("depleted_uranium", s -> ModContent.DEPLETED_URANIUM = s),
			plain("palladium_dust", s -> ModContent.PALLADIUM_DUST = s),
			plain("raw_palladium", s -> ModContent.RAW_PALLADIUM = s),
			plain("palladium_ingot", s -> ModContent.PALLADIUM_INGOT = s),
			item("network_analyzer", p -> new NetworkAnalyzerItem(p.stacksTo(1)), s -> ModContent.NETWORK_ANALYZER = s),
			item("wind_gauge", p -> new WindGaugeItem(p.stacksTo(1)), s -> ModContent.WIND_GAUGE = s),
			item("wrench", p -> new WrenchItem(p.stacksTo(1)), s -> ModContent.WRENCH = s),
			item("guide_book", p -> new GuideBookItem(p.stacksTo(1)), s -> ModContent.GUIDE_BOOK = s),
			// Teleporter Remote (MOD-092): registered but kept out of the creative tab + no recipe until
			// MOD-093 finishes the feature (same treatment as the station — see CreativeTabContent).
			item("teleporter_remote", p -> new TeleporterRemoteItem(p.stacksTo(1)), s -> ModContent.TELEPORTER_REMOTE = s),
			item("battery_pouch", p -> new PouchItem(p.stacksTo(1)), s -> ModContent.BATTERY_POUCH = s),
			// Shielding Pouch (MOD-545): the same pouch handling with no electricity, and the one
			// carried container the radiation sweep skips — see RadiationSources.countTagged.
			item("shielding_pouch", p -> new ShieldingPouchItem(p.stacksTo(1)),
					s -> ModContent.SHIELDING_POUCH = s),
			// Energy Pack (MOD-065): worn LV buffer + the inert battery cell it is crafted from.
			item("battery", p -> new BatteryItem(p.stacksTo(BatteryItem.MAX_STACK)), s -> ModContent.BATTERY = s),
			// EU crystals (MOD-504). Two items per tier: the blank carries the buffer and is stacksTo(1)
			// (energy moved into a stack must divide by count, and at these buffer sizes any stack would
			// start rounding EU away); the finished crystal is an ordinary crafting material that stacks
			// normally, because it holds no energy at all.
			// Written out id by id rather than looped over CrystalTier.values(): every gate that knows
			// which items exist (arch_check, loader_parity_check, graph_data) reads the id LITERALS of
			// this list. A loop registers correctly at runtime and is invisible to all three, which
			// would leave the looped ids unguarded — and the six crystals out of the item catalogue.
			// EU crystals (MOD-504): a chargeable blank per tier, and the finished crystal it becomes at 100 %.
			// Only the blanks have an EU buffer; the finished three are ordinary crafting materials.
			item("energy_crystal_blank", p -> new CrystalBlankItem(p.stacksTo(1), CrystalTier.ENERGY), s -> ModContent.ENERGY_CRYSTAL_BLANK = s),
			item("energy_crystal", Item::new, s -> ModContent.ENERGY_CRYSTAL = s),
			item("lapotron_crystal_blank", p -> new CrystalBlankItem(p.stacksTo(1), CrystalTier.LAPOTRON), s -> ModContent.LAPOTRON_CRYSTAL_BLANK = s),
			item("lapotron_crystal", Item::new, s -> ModContent.LAPOTRON_CRYSTAL = s),
			item("resonant_crystal_blank", p -> new CrystalBlankItem(p.stacksTo(1), CrystalTier.RESONANT), s -> ModContent.RESONANT_CRYSTAL_BLANK = s),
			item("resonant_crystal", Item::new, s -> ModContent.RESONANT_CRYSTAL = s),
			item("energy_pack", p -> new EnergyPackItem(EnergyPackItem.equipmentProperties(p)), s -> ModContent.ENERGY_PACK = s),
			// Electric Drill (MOD-079): first powered hand tool — a diamond-tier pickaxe that runs on EU.
			item("electric_drill", p -> new ElectricDrillItem(ElectricDrillItem.electricDrillProperties(p)), s -> ModContent.ELECTRIC_DRILL = s),
			// Diamond-Tipped Electric Drill (MOD-321): the drill's upgrade tier — faster, switchable Silk Touch.
			item("electric_drill_diamond_tip", p -> new ElectricDrillDiamondTipItem(
					ElectricDrillDiamondTipItem.electricDrillDiamondTipProperties(p)), s -> ModContent.ELECTRIC_DRILL_DIAMOND_TIP = s),
			// Netherite-Tipped Electric Drill (MOD-534): the drill's third tier — faster still, harder hitting,
			// and the one tier with a bigger EU buffer of its own.
			item("electric_drill_netherite_tip", p -> new ElectricDrillNetheriteTipItem(
					ElectricDrillNetheriteTipItem.electricDrillNetheriteTipProperties(p)), s -> ModContent.ELECTRIC_DRILL_NETHERITE_TIP = s),
			// Electric Chainsaw (MOD-337): the drill's wood-side counterpart — an EU axe for logs and leaves.
			item("electric_chainsaw", p -> new ElectricChainsawItem(ElectricChainsawItem.electricChainsawProperties(p)), s -> ModContent.ELECTRIC_CHAINSAW = s),
			// Diamond-Tipped Electric Chainsaw (MOD-374): the chainsaw's upgrade tier — faster, with a
			// switchable Silk Touch mode that drops leaves as blocks.
			item("electric_chainsaw_diamond_tip", p -> new ElectricChainsawDiamondTipItem(
					ElectricChainsawDiamondTipItem.electricChainsawDiamondTipProperties(p)), s -> ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP = s),
			// Electric Shovel (MOD-338): the earth-side member of the same line — an EU shovel for loose ground.
			loaderItem("electric_shovel", s -> ModContent.ELECTRIC_SHOVEL = s),
			// Diamond-Tipped Electric Shovel (MOD-481): the shovel's upgrade tier — faster, and its drops switch
			// between normal and Silk Touch on the fly.
			loaderItem("electric_shovel_diamond_tip", s -> ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP = s),
			// Electric Hoe (MOD-342): the farming member of the same line — an EU hoe that tills for free.
			loaderItem("electric_hoe", s -> ModContent.ELECTRIC_HOE = s),
			// Diamond-Tipped Electric Hoe (MOD-378): the hoe's upgrade tier — faster, and the plots it tills
			// come out already watered.
			loaderItem("electric_hoe_diamond_tip", s -> ModContent.ELECTRIC_HOE_DIAMOND_TIP = s),
			// Electric Saber (MOD-149): the line's first weapon — EU per hit, plain sword when flat or off.
			item("electric_saber", p -> new ElectricSaberItem(ElectricSaberItem.electricSaberProperties(p)), s -> ModContent.ELECTRIC_SABER = s),
			// Electromagnet (MOD-132): EU item in any inventory slot that pulls loose drops toward the carrier.
			item("electromagnet", p -> new MagnetItem(p.stacksTo(1)), s -> ModContent.ELECTROMAGNET = s),
			// Jetpack (MOD-148): worn EU flight — thrust on held jump, powerless glide when drained.
			item("jetpack", p -> new JetpackItem(JetpackItem.equipmentProperties(p)), s -> ModContent.JETPACK = s),
			// Vacuum Capsule (MOD-063): empty (×64) + filled (×16, fluid in the capsule_fluid component).
			item("vacuum_capsule", VacuumCapsuleItem::new, s -> ModContent.VACUUM_CAPSULE = s),
			item("filled_vacuum_capsule", p -> new FilledCapsuleItem(p.stacksTo(FilledCapsuleItem.STACK_SIZE)
					.craftRemainder(registeredItem("vacuum_capsule"))), s -> ModContent.FILLED_VACUUM_CAPSULE = s),
			// Stock Display Frame (MOD-066). The entity type is resolved by id inside the factory, so it is
			// read when the item is built, never at class-init: Fabric registers entity types before items
			// in its entrypoint, and on NeoForge the ENTITY_TYPE RegisterEvent fires before ITEM.
			item("stock_display_frame", p -> new StockDisplayFrameItem(stockDisplayFrameType(), p), s -> ModContent.STOCK_DISPLAY_FRAME_ITEM = s),
			// Scythe (MOD-068): six material tiers, each an AOE foliage clearer. Registered like a hoe
			// (.hoe(material, attackDamage, -1.0f) attaches the tool component + enchantability) but as
			// ScytheItem, not HoeItem — the scythe must not till dirt on right-click, it clears its area
			// instead. The eight tiers (material + AOE profile + attack bias) are declared once in the
			// loader-neutral dev.alaindustrial.item.tool.ScytheTiers — both loaders register from the same list,
			// so a balance tweak cannot drift between Fabric and NeoForge.
			scythe(ScytheTiers.WOOD, s -> ModContent.SCYTHE_WOOD = s),
			scythe(ScytheTiers.STONE, s -> ModContent.SCYTHE_STONE = s),
			scythe(ScytheTiers.COPPER, s -> ModContent.SCYTHE_COPPER = s),
			scythe(ScytheTiers.IRON, s -> ModContent.SCYTHE_IRON = s),
			scythe(ScytheTiers.GOLD, s -> ModContent.SCYTHE_GOLD = s),
			scythe(ScytheTiers.TEMPERED_IRON, s -> ModContent.SCYTHE_TEMPERED_IRON = s),
			scythe(ScytheTiers.DIAMOND, s -> ModContent.SCYTHE_DIAMOND = s),
			scythe(ScytheTiers.NETHERITE, s -> ModContent.SCYTHE_NETHERITE = s),
			// Metal plates (MOD-078): plain ingredient items, ingot form. Made by the Forge Hammer (by hand)
			// or the Compressor; recycled back to dust by the Macerator (except tempered_iron — no dust).
			plain("copper_plate", s -> ModContent.COPPER_PLATE = s),
			plain("gold_plate", s -> ModContent.GOLD_PLATE = s),
			plain("iron_plate", s -> ModContent.IRON_PLATE = s),
			plain("tin_plate", s -> ModContent.TIN_PLATE = s),
			plain("silver_plate", s -> ModContent.SILVER_PLATE = s),
			plain("nickel_plate", s -> ModContent.NICKEL_PLATE = s),
			plain("uranium_plate", s -> ModContent.URANIUM_PLATE = s),
			plain("palladium_plate", s -> ModContent.PALLADIUM_PLATE = s),
			plain("tempered_iron_plate", s -> ModContent.TEMPERED_IRON_PLATE = s),
			// Alloy plates + reinforced tier (MOD-460): same hammer/compressor path, no dust to recycle to.
			plain("bronze_plate", s -> ModContent.BRONZE_PLATE = s),
			plain("invar_plate", s -> ModContent.INVAR_PLATE = s),
			plain("cupronickel_plate", s -> ModContent.CUPRONICKEL_PLATE = s),
			plain("electrum_plate", s -> ModContent.ELECTRUM_PLATE = s),
			plain("bronze_reinforced_plate", s -> ModContent.BRONZE_REINFORCED_PLATE = s),
			plain("invar_reinforced_plate", s -> ModContent.INVAR_REINFORCED_PLATE = s),
			plain("cupronickel_reinforced_plate", s -> ModContent.CUPRONICKEL_REINFORCED_PLATE = s),
			plain("electrum_reinforced_plate", s -> ModContent.ELECTRUM_REINFORCED_PLATE = s),
			// Forge Hammer (MOD-078): pre-machine hand tool — ingot + hammer on the grid → plate; the hammer
			// stays and loses 1 durability per plate. The craft-remainder hook has a different signature on
			// each loader, so the CLASS is loader-supplied (HammerItemFabric / HammerItemNeoForge) while the
			// durability and the anvil repair stay shared, in HammerItem#hammerProperties.
			loaderItem("forge_hammer", s -> ModContent.FORGE_HAMMER = s),
			// Oil Bucket (MOD-238): the vanilla WATER_BUCKET pattern — BucketItem(fluid, props with
			// craftRemainder(BUCKET).stacksTo(1)); the still fluid is resolved by id (see bucket()).
			bucket("oil_bucket", "oil", s -> ModContent.OIL_BUCKET = s),
			// Distillation fraction buckets (MOD-251) — same pattern, filled by the column's output tanks.
			bucket("diesel_bucket", "diesel", s -> ModContent.DIESEL_BUCKET = s),
			bucket("fuel_oil_bucket", "fuel_oil", s -> ModContent.FUEL_OIL_BUCKET = s),
			// The organic chain (MOD-146/MOD-525) — same vanilla BucketItem pattern.
			bucket("biofuel_bucket", "biofuel", s -> ModContent.BIOFUEL_BUCKET = s),
			bucket("nutrient_solution_bucket", "nutrient_solution", s -> ModContent.NUTRIENT_SOLUTION_BUCKET = s),
			// Block items.
			blockItem("generator", s -> ModContent.GENERATOR_ITEM = s),
			blockItem("geothermal_generator", s -> ModContent.GEOTHERMAL_GENERATOR_ITEM = s),
			blockItem("solar_panel", s -> ModContent.SOLAR_PANEL_ITEM = s),
			blockItem("moonlit_solar_panel", s -> ModContent.MOONLIT_SOLAR_PANEL_ITEM = s),
			blockItem("daylight_solar_panel", s -> ModContent.DAYLIGHT_SOLAR_PANEL_ITEM = s),
			blockItem("copper_cable", s -> ModContent.COPPER_CABLE_ITEM = s),
			blockItem("tin_cable", s -> ModContent.TIN_CABLE_ITEM = s),
			blockItem("gold_cable", s -> ModContent.GOLD_CABLE_ITEM = s),
			blockItem("electrum_cable", s -> ModContent.ELECTRUM_CABLE_ITEM = s),
			blockItem("insulated_copper_cable", s -> ModContent.INSULATED_COPPER_CABLE_ITEM = s),
			blockItem("insulated_tin_cable", s -> ModContent.INSULATED_TIN_CABLE_ITEM = s),
			blockItem("insulated_gold_cable", s -> ModContent.INSULATED_GOLD_CABLE_ITEM = s),
			blockItem("insulated_electrum_cable", s -> ModContent.INSULATED_ELECTRUM_CABLE_ITEM = s),
			// MOD-108: its own BlockItem subclass so the pipe can carry a tooltip (plain hint + Shift for the
			// throughput numbers) — a plain blockItem() has none.
			blockItem("item_pipe", p -> new ItemPipeBlockItem(registeredBlock("item_pipe"),
					p.useBlockDescriptionPrefix()), s -> ModContent.ITEM_PIPE_ITEM = s),
			blockItem("fluid_pipe", p -> new FluidPipeBlockItem(registeredBlock("fluid_pipe"),
					p.useBlockDescriptionPrefix()), s -> ModContent.FLUID_PIPE_ITEM = s),
			blockItem("macerator", s -> ModContent.MACERATOR_ITEM = s),
			blockItem("battery_box", s -> ModContent.BATTERY_BOX_ITEM = s),
			blockItem("cesu", s -> ModContent.CESU_ITEM = s),
			blockItem("teleporter", s -> ModContent.TELEPORTER_ITEM = s),
			blockItem("electric_furnace", s -> ModContent.ELECTRIC_FURNACE_ITEM = s),
			blockItem("extractor", s -> ModContent.EXTRACTOR_ITEM = s),
			blockItem("compressor", s -> ModContent.COMPRESSOR_ITEM = s),
			blockItem("component_repair_bench", s -> ModContent.COMPONENT_REPAIR_BENCH_ITEM = s),
			blockItem("canning_machine", s -> ModContent.CANNING_MACHINE_ITEM = s),
			blockItem("sawmill", s -> ModContent.SAWMILL_ITEM = s),
			blockItem("assembler", s -> ModContent.ASSEMBLER_ITEM = s),
			blockItem("polymerizer", s -> ModContent.POLYMERIZER_ITEM = s),
			// Distillation Column (MOD-251): one item raises the whole 1×1×3 tower; segments have no items.
			blockItem("distillation_column", s -> ModContent.DISTILLATION_COLUMN_ITEM = s),
			blockItem("rectification_section", s -> ModContent.RECTIFICATION_SECTION_ITEM = s),
			blockItem("vulcanizer", s -> ModContent.VULCANIZER_ITEM = s),
			blockItem("alloy_smelter", s -> ModContent.ALLOY_SMELTER_ITEM = s),
			blockItem("galvanic_bath", s -> ModContent.GALVANIC_BATH_ITEM = s),
			// The organic chain (MOD-146/MOD-525).
			blockItem("fermenter", s -> ModContent.FERMENTER_ITEM = s),
			blockItem("sprinkler", s -> ModContent.SPRINKLER_ITEM = s),
			blockItem("thermal_centrifuge", s -> ModContent.THERMAL_CENTRIFUGE_ITEM = s),
			// MOD-468, stage 1 — block items for the reactor shell.
			blockItem("reactor_casing", s -> ModContent.REACTOR_CASING_ITEM = s),
			blockItem("irradiated_soil", s -> ModContent.IRRADIATED_SOIL_ITEM = s),
			blockItem("reactor_glass", s -> ModContent.REACTOR_GLASS_ITEM = s),
			blockItem("reactor_port", s -> ModContent.REACTOR_PORT_ITEM = s),
			blockItem("reactor_door", s -> ModContent.REACTOR_DOOR_ITEM = s),
			blockItem("reactor_controller", s -> ModContent.REACTOR_CONTROLLER_ITEM = s),
			blockItem("reactor_lamp", s -> ModContent.REACTOR_LAMP_ITEM = s),
			blockItem("steam_nozzle", s -> ModContent.STEAM_NOZZLE_ITEM = s),
			blockItem("reactor_outlet", s -> ModContent.REACTOR_OUTLET_ITEM = s),
			blockItem("reactor_button", s -> ModContent.REACTOR_BUTTON_ITEM = s),
			blockItem("reactor_lever", s -> ModContent.REACTOR_LEVER_ITEM = s),
			blockItem("fuel_rod_assembly", s -> ModContent.FUEL_ROD_ASSEMBLY_ITEM = s),
			blockItem("electric_heater", s -> ModContent.ELECTRIC_HEATER_ITEM = s),
			blockItem("charge_pad", s -> ModContent.CHARGE_PAD_ITEM = s),
			blockItem("energy_condenser", s -> ModContent.ENERGY_CONDENSER_ITEM = s),
			blockItem("mob_repeller", s -> ModContent.MOB_REPELLER_ITEM = s),
			blockItem("mob_repeller_mv", s -> ModContent.MOB_REPELLER_MV_ITEM = s),
			blockItem("mob_repeller_hv", s -> ModContent.MOB_REPELLER_HV_ITEM = s),
			blockItem("incubator", s -> ModContent.INCUBATOR_ITEM = s),
			blockItem("trellis", s -> ModContent.TRELLIS_ITEM = s),
			// MOD-537 — the seeds carry the flower's id-in-name-only ("kok_sagyz_seeds"): planting is just
			// placing the block, so a BlockItem is exactly right. The root has NO block item: it is dug,
			// never placed — the plain "kok_sagyz_root" item above is the harvest.
			blockItem("kok_sagyz_seeds", "kok_sagyz", s -> ModContent.KOK_SAGYZ_SEEDS = s),
			// MOD-505 — the greenhouse. The bud has no item: it is grown, never placed.
			blockItem("crystal_farm_floor", s -> ModContent.CRYSTAL_FARM_FLOOR_ITEM = s),
			blockItem("crystal_farm_glass", s -> ModContent.CRYSTAL_FARM_GLASS_ITEM = s),
			blockItem("crystal_farm_door", s -> ModContent.CRYSTAL_FARM_DOOR_ITEM = s),
			blockItem("crystal_farm_controller", s -> ModContent.CRYSTAL_FARM_CONTROLLER_ITEM = s),
			blockItem("crystal_seedbed", s -> ModContent.CRYSTAL_SEEDBED_ITEM = s),
			blockItem("pump", s -> ModContent.PUMP_ITEM = s),
			blockItem("garden_drone_station", s -> ModContent.GARDEN_DRONE_STATION_ITEM = s),
			plain("garden_drone", s -> ModContent.GARDEN_DRONE = s),
			blockItem("fluid_tank", p -> new FluidTankBlockItem(registeredBlock("fluid_tank"),
					p.useBlockDescriptionPrefix()), s -> ModContent.FLUID_TANK_ITEM = s),
			blockItem("water_mill", s -> ModContent.WATER_MILL_ITEM = s),
			blockItem("wind_mill", s -> ModContent.WIND_MILL_ITEM = s),
			blockItem("high_altitude_wind_mill", s -> ModContent.HIGH_ALTITUDE_WIND_MILL_ITEM = s),
			blockItem("storm_wind_mill", s -> ModContent.STORM_WIND_MILL_ITEM = s),
			blockItem("lightning_rod_generator", s -> ModContent.LIGHTNING_ROD_GENERATOR_ITEM = s),
			blockItem("creative_energy_source", "creative_energy_source", CREATIVE_ONLY_ITEM, s -> ModContent.CREATIVE_ENERGY_SOURCE_ITEM = s),
			blockItem("tin_ore", s -> ModContent.TIN_ORE_ITEM = s),
			blockItem("deepslate_tin_ore", s -> ModContent.DEEPSLATE_TIN_ORE_ITEM = s),
			blockItem("silver_ore", s -> ModContent.SILVER_ORE_ITEM = s),
			blockItem("deepslate_silver_ore", s -> ModContent.DEEPSLATE_SILVER_ORE_ITEM = s),
			blockItem("nickel_ore", s -> ModContent.NICKEL_ORE_ITEM = s),
			blockItem("deepslate_nickel_ore", s -> ModContent.DEEPSLATE_NICKEL_ORE_ITEM = s),
			blockItem("sulfur_ore", s -> ModContent.SULFUR_ORE_ITEM = s),
			blockItem("deepslate_sulfur_ore", s -> ModContent.DEEPSLATE_SULFUR_ORE_ITEM = s),
			blockItem("uranium_ore", s -> ModContent.URANIUM_ORE_ITEM = s),
			blockItem("deepslate_uranium_ore", s -> ModContent.DEEPSLATE_URANIUM_ORE_ITEM = s),
			blockItem("palladium_ore", s -> ModContent.PALLADIUM_ORE_ITEM = s),
			blockItem("iron_chest", s -> ModContent.IRON_CHEST_ITEM = s),
			blockItem("storage_module", s -> ModContent.STORAGE_MODULE_ITEM = s),
			blockItem("iron_furnace", s -> ModContent.IRON_FURNACE_ITEM = s),
			blockItem("silver_chest", s -> ModContent.SILVER_CHEST_ITEM = s),
			blockItem("gold_chest", s -> ModContent.GOLD_CHEST_ITEM = s),
			blockItem("electrum_chest", s -> ModContent.ELECTRUM_CHEST_ITEM = s),
			blockItem("shielding_chest", s -> ModContent.SHIELDING_CHEST_ITEM = s),
			blockItem("tempered_iron_block", s -> ModContent.TEMPERED_IRON_BLOCK_ITEM = s),
			// MOD-225 block-items.
			blockItem("machine_casing", s -> ModContent.MACHINE_CASING_ITEM = s),
			blockItem("advanced_machine_casing", s -> ModContent.ADVANCED_MACHINE_CASING_ITEM = s),
			blockItem("silver_plate_block", s -> ModContent.SILVER_PLATE_BLOCK_ITEM = s),
			blockItem("tempered_iron_plate_block", s -> ModContent.TEMPERED_IRON_PLATE_BLOCK_ITEM = s),
			blockItem("industrial_workbench", s -> ModContent.INDUSTRIAL_WORKBENCH_ITEM = s),
			// Enriched Uranium Torch (MOD-085): a StandingAndWallBlockItem (like vanilla Items.TORCH) so using it
			// on a wall places the wall variant and on the floor the standing variant. The wall block has no item
			// of its own — this item maps to both blocks (StandingAndWallBlockItem#registerBlocks).
			blockItem("enriched_uranium_torch", p -> new StandingAndWallBlockItem(registeredBlock("enriched_uranium_torch"),
					registeredBlock("enriched_uranium_wall_torch"), Direction.DOWN,
					p.useBlockDescriptionPrefix()), s -> ModContent.ENRICHED_URANIUM_TORCH_ITEM = s));

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
			blockEntity("shielding_chest", ShieldingChestBlockEntity.class, ShieldingChestBlockEntity::new, s -> ModContent.SHIELDING_CHEST_BE = s, "shielding_chest"),
			blockEntity("mob_repeller", MobRepellerBlockEntity.class, MobRepellerBlockEntity::new, s -> ModContent.MOB_REPELLER_BE = s, "mob_repeller"),
			blockEntity("mob_repeller_mv", MobRepellerMvBlockEntity.class, MobRepellerMvBlockEntity::new, s -> ModContent.MOB_REPELLER_MV_BE = s, "mob_repeller_mv"),
			blockEntity("mob_repeller_hv", MobRepellerHvBlockEntity.class, MobRepellerHvBlockEntity::new, s -> ModContent.MOB_REPELLER_HV_BE = s, "mob_repeller_hv"),
			blockEntity("thermal_centrifuge", ThermalCentrifugeBlockEntity.class,
					ThermalCentrifugeBlockEntity::new, s -> ModContent.THERMAL_CENTRIFUGE_BE = s,
					"thermal_centrifuge"),
			blockEntity("lightning_rod_generator", LightningRodGeneratorBlockEntity.class,
					LightningRodGeneratorBlockEntity::new, s -> ModContent.LIGHTNING_ROD_GENERATOR_BE = s,
					"lightning_rod_generator"),
			blockEntity("reactor_controller", ReactorControllerBlockEntity.class,
					ReactorControllerBlockEntity::new, s -> ModContent.REACTOR_CONTROLLER_BE = s,
					"reactor_controller"),
			blockEntity("fuel_rod_assembly", FuelRodAssemblyBlockEntity.class,
					FuelRodAssemblyBlockEntity::new, s -> ModContent.FUEL_ROD_ASSEMBLY_BE = s,
					"fuel_rod_assembly"),
			blockEntity("reactor_port", ReactorPortBlockEntity.class,
					ReactorPortBlockEntity::new, s -> ModContent.REACTOR_PORT_BE = s,
					"reactor_port"),
			blockEntity("steam_nozzle", SteamNozzleBlockEntity.class,
					SteamNozzleBlockEntity::new, s -> ModContent.STEAM_NOZZLE_BE = s,
					"steam_nozzle"),
			blockEntity("reactor_outlet", ReactorOutletBlockEntity.class,
					ReactorOutletBlockEntity::new, s -> ModContent.REACTOR_OUTLET_BE = s,
					"reactor_outlet"),
			// MOD-493: the airlock's panel slides rather than swings, and a block state cannot hold a
			// position between two ticks. This block entity stores no game state at all — it is the
			// clock the client times that travel by.
			blockEntity("reactor_door", ReactorDoorBlockEntity.class,
					ReactorDoorBlockEntity::new, s -> ModContent.REACTOR_DOOR_BE = s,
					"reactor_door"),
			blockEntity("creative_energy_source", CreativeEnergySourceBlockEntity.class,
					CreativeEnergySourceBlockEntity::new, s -> ModContent.CREATIVE_ENERGY_SOURCE_BE = s,
					"creative_energy_source"),
			// MOD-505: the greenhouse's only ticking object. The seedbeds and buds it drives have none,
			// which is what lets one room hold a hundred of them without a hundred tickers.
			blockEntity("crystal_farm_controller", CrystalFarmControllerBlockEntity.class,
					CrystalFarmControllerBlockEntity::new, s -> ModContent.CRYSTAL_FARM_CONTROLLER_BE = s,
					"crystal_farm_controller"),
			// MOD-146/MOD-525: the organic chain's two ticking blocks.
			blockEntity("fermenter", FermenterBlockEntity.class, FermenterBlockEntity::new,
					s -> ModContent.FERMENTER_BE = s, "fermenter"),
			blockEntity("sprinkler", SprinklerBlockEntity.class, SprinklerBlockEntity::new,
					s -> ModContent.SPRINKLER_BE = s, "sprinkler"));

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
