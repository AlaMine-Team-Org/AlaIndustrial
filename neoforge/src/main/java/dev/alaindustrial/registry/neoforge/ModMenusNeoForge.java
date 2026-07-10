package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.menu.CompressorMenu;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.menu.ExtractorMenu;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.menu.IronChestMenu;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import dev.alaindustrial.menu.StormWindMillMenu;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge {@code MenuType} registration (MOD-022 registration-facade). Mirrors the Fabric
 * {@code dev.alaindustrial.registry.ModMenus} set over {@link Registries#MENU} using the real menu
 * classes from {@code common}, not stubs.
 *
 * <p><b>Geothermal generator menu (MOD-028).</b> {@code GeothermalGeneratorBlockEntity} now lives in
 * {@code common} and its block is registered on NeoForge (see {@code ModBlocksNeoForge}), so its menu is
 * registered here like every other machine.
 *
 * <p><b>Split constraint (verified 26.2 API):</b> the {@code DeferredRegister} object and its
 * {@code register(modBus)} call must live on the {@code neoforge} side. NeoForge builds a networked
 * {@code MenuType} via {@code IMenuTypeExtension.create(IContainerFactory)} (the loader replacement for
 * the vanilla {@code new MenuType<>(factory, FeatureFlags)} the Fabric side uses). Verified against
 * neoforge-26.2.0.8-beta: {@code IContainerFactory<T> extends MenuType.MenuSupplier<T>} and provides a
 * default {@code create(int, Inventory)} that ignores the extra buffer — so the menu's 2-arg client
 * constructor {@code (int syncId, Inventory playerInventory)} plugs straight in.
 */
public final class ModMenusNeoForge {
	public static final DeferredRegister<MenuType<?>> MENUS =
			DeferredRegister.create(Registries.MENU, Industrialization.MOD_ID);

	public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR =
			register("generator", (id, inv, buf) -> new GeneratorMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<MaceratorMenu>> MACERATOR =
			register("macerator", (id, inv, buf) -> new MaceratorMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<SolarPanelMenu>> SOLAR_PANEL =
			register("solar_panel", (id, inv, buf) -> new SolarPanelMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<MoonlitSolarPanelMenu>> MOONLIT_SOLAR_PANEL =
			register("moonlit_solar_panel", (id, inv, buf) -> new MoonlitSolarPanelMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE =
			register("electric_furnace", (id, inv, buf) -> new ElectricFurnaceMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<ExtractorMenu>> EXTRACTOR =
			register("extractor", (id, inv, buf) -> new ExtractorMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<CompressorMenu>> COMPRESSOR =
			register("compressor", (id, inv, buf) -> new CompressorMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<BatteryBoxMenu>> BATTERY_BOX =
			register("battery_box", (id, inv, buf) -> new BatteryBoxMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<DaylightSolarPanelMenu>> DAYLIGHT_SOLAR_PANEL =
			register("daylight_solar_panel", (id, inv, buf) -> new DaylightSolarPanelMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<GeothermalGeneratorMenu>> GEOTHERMAL_GENERATOR =
			register("geothermal_generator", (id, inv, buf) -> new GeothermalGeneratorMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<PumpMenu>> PUMP =
			register("pump", (id, inv, buf) -> new PumpMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<WaterMillMenu>> WATER_MILL =
			register("water_mill", (id, inv, buf) -> new WaterMillMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<WindMillMenu>> WIND_MILL =
			register("wind_mill", (id, inv, buf) -> new WindMillMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<HighAltitudeWindMillMenu>> HIGH_ALTITUDE_WIND_MILL =
			register("high_altitude_wind_mill", (id, inv, buf) -> new HighAltitudeWindMillMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<StormWindMillMenu>> STORM_WIND_MILL =
			register("storm_wind_mill", (id, inv, buf) -> new StormWindMillMenu(id, inv));
	public static final DeferredHolder<MenuType<?>, MenuType<IronChestMenu>> IRON_CHEST =
			register("iron_chest", (id, inv, buf) -> new IronChestMenu(id, inv));

	private ModMenusNeoForge() {
	}

	/**
	 * Bind each {@code MenuType} {@code DeferredHolder} into the loader-neutral {@code ModContent} facade,
	 * mirroring the {@code ModContent.X_MENU = () -> X} assignments in
	 * {@code dev.alaindustrial.registry.ModMenus#init()} on the Fabric side. Assigned directly (a
	 * {@code DeferredHolder} is a {@code Supplier}); resolves lazily after the {@code RegisterEvent}.
	 */
	 // Bound via HOLDER::get: a DeferredHolder<_, MenuType<X>> is a Supplier<MenuType<X>>, but the slot is
	 // Supplier<MenuType<?>>; generics are invariant, so the (still-lazy) method reference bridges the
	 // wildcard (see ModBlocksNeoForge#init).
	public static void init() {
		ModContent.GENERATOR_MENU = GENERATOR::get;
		ModContent.MACERATOR_MENU = MACERATOR::get;
		ModContent.SOLAR_PANEL_MENU = SOLAR_PANEL::get;
		ModContent.MOONLIT_SOLAR_PANEL_MENU = MOONLIT_SOLAR_PANEL::get;
		ModContent.ELECTRIC_FURNACE_MENU = ELECTRIC_FURNACE::get;
		ModContent.EXTRACTOR_MENU = EXTRACTOR::get;
		ModContent.COMPRESSOR_MENU = COMPRESSOR::get;
		ModContent.BATTERY_BOX_MENU = BATTERY_BOX::get;
		ModContent.DAYLIGHT_SOLAR_PANEL_MENU = DAYLIGHT_SOLAR_PANEL::get;
		ModContent.GEOTHERMAL_GENERATOR_MENU = GEOTHERMAL_GENERATOR::get;
		ModContent.PUMP_MENU = PUMP::get;
		ModContent.WATER_MILL_MENU = WATER_MILL::get;
		ModContent.WIND_MILL_MENU = WIND_MILL::get;
		ModContent.HIGH_ALTITUDE_WIND_MILL_MENU = HIGH_ALTITUDE_WIND_MILL::get;
		ModContent.STORM_WIND_MILL_MENU = STORM_WIND_MILL::get;
		ModContent.IRON_CHEST_MENU = IRON_CHEST::get;
	}

	/**
	 * Registers one machine {@code MenuType} from its container factory. Verified: NeoForge builds a
	 * networked {@code MenuType} via {@code IMenuTypeExtension.create(IContainerFactory)}
	 * (neoforge-26.2.0.8-beta).
	 */
	public static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> register(
			String name, IContainerFactory<T> factory) {
		return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
	}
}
