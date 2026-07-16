package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.menu.CompressorMenu;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.menu.ExtractorMenu;
import dev.alaindustrial.menu.IronChestMenu;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.menu.GoldChestMenu;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import dev.alaindustrial.menu.StormWindMillMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Central registration for Industrialization {@link MenuType}s (machine GUIs). */
public final class ModMenus {
	private ModMenus() {
	}

	public static MenuType<GeneratorMenu> GENERATOR;
	public static MenuType<MaceratorMenu> MACERATOR;
	public static MenuType<SolarPanelMenu> SOLAR_PANEL;
	public static MenuType<MoonlitSolarPanelMenu> MOONLIT_SOLAR_PANEL;
	public static MenuType<ElectricFurnaceMenu> ELECTRIC_FURNACE;
	public static MenuType<ExtractorMenu> EXTRACTOR;
	public static MenuType<CompressorMenu> COMPRESSOR;
	public static MenuType<BatteryBoxMenu> BATTERY_BOX;
	public static MenuType<DaylightSolarPanelMenu> DAYLIGHT_SOLAR_PANEL;
	public static MenuType<GeothermalGeneratorMenu> GEOTHERMAL_GENERATOR;
	public static MenuType<PumpMenu> PUMP;
	public static MenuType<WaterMillMenu> WATER_MILL;
	public static MenuType<WindMillMenu> WIND_MILL;
	public static MenuType<HighAltitudeWindMillMenu> HIGH_ALTITUDE_WIND_MILL;
	public static MenuType<StormWindMillMenu> STORM_WIND_MILL;
	public static MenuType<IronChestMenu> IRON_CHEST;
	public static MenuType<SilverChestMenu> SILVER_CHEST;
	public static MenuType<GoldChestMenu> GOLD_CHEST;

	public static void init() {
		GENERATOR = register("generator", new MenuType<>(GeneratorMenu::new, FeatureFlags.VANILLA_SET));
		MACERATOR = register("macerator",
				new MenuType<>(MaceratorMenu::new, FeatureFlags.VANILLA_SET));
		SOLAR_PANEL = register("solar_panel",
				new MenuType<>(SolarPanelMenu::new, FeatureFlags.VANILLA_SET));
		MOONLIT_SOLAR_PANEL = register("moonlit_solar_panel",
				new MenuType<>(MoonlitSolarPanelMenu::new, FeatureFlags.VANILLA_SET));
		ELECTRIC_FURNACE = register("electric_furnace",
				new MenuType<>(ElectricFurnaceMenu::new, FeatureFlags.VANILLA_SET));
		EXTRACTOR = register("extractor",
				new MenuType<>(ExtractorMenu::new, FeatureFlags.VANILLA_SET));
		COMPRESSOR = register("compressor",
				new MenuType<>(CompressorMenu::new, FeatureFlags.VANILLA_SET));
		BATTERY_BOX = register("battery_box",
				new MenuType<>(BatteryBoxMenu::new, FeatureFlags.VANILLA_SET));
		DAYLIGHT_SOLAR_PANEL = register("daylight_solar_panel",
				new MenuType<>(DaylightSolarPanelMenu::new, FeatureFlags.VANILLA_SET));
		GEOTHERMAL_GENERATOR = register("geothermal_generator",
				new MenuType<>(GeothermalGeneratorMenu::new, FeatureFlags.VANILLA_SET));
		PUMP = register("pump", new MenuType<>(PumpMenu::new, FeatureFlags.VANILLA_SET));
		WATER_MILL = register("water_mill",
				new MenuType<>(WaterMillMenu::new, FeatureFlags.VANILLA_SET));
		WIND_MILL = register("wind_mill",
				new MenuType<>(WindMillMenu::new, FeatureFlags.VANILLA_SET));
		HIGH_ALTITUDE_WIND_MILL = register("high_altitude_wind_mill",
				new MenuType<>(HighAltitudeWindMillMenu::new, FeatureFlags.VANILLA_SET));
		STORM_WIND_MILL = register("storm_wind_mill",
				new MenuType<>(StormWindMillMenu::new, FeatureFlags.VANILLA_SET));
		IRON_CHEST = register("iron_chest",
				new MenuType<>(IronChestMenu::new, FeatureFlags.VANILLA_SET));
		SILVER_CHEST = register("silver_chest",
				new MenuType<>(SilverChestMenu::new, FeatureFlags.VANILLA_SET));
		GOLD_CHEST = register("gold_chest",
				new MenuType<>(GoldChestMenu::new, FeatureFlags.VANILLA_SET));

		// MOD-022 registration facade: publish each eagerly-registered MenuType into the loader-neutral
		// ModContent so content Menu constructors (which read ModContent.X_MENU.get() at runtime) resolve
		// the Fabric instance. NeoForge binds a lazy DeferredHolder into the same handle. See ModContent.
		ModContent.GENERATOR_MENU = () -> GENERATOR;
		ModContent.MACERATOR_MENU = () -> MACERATOR;
		ModContent.SOLAR_PANEL_MENU = () -> SOLAR_PANEL;
		ModContent.MOONLIT_SOLAR_PANEL_MENU = () -> MOONLIT_SOLAR_PANEL;
		ModContent.ELECTRIC_FURNACE_MENU = () -> ELECTRIC_FURNACE;
		ModContent.EXTRACTOR_MENU = () -> EXTRACTOR;
		ModContent.COMPRESSOR_MENU = () -> COMPRESSOR;
		ModContent.BATTERY_BOX_MENU = () -> BATTERY_BOX;
		ModContent.DAYLIGHT_SOLAR_PANEL_MENU = () -> DAYLIGHT_SOLAR_PANEL;
		ModContent.GEOTHERMAL_GENERATOR_MENU = () -> GEOTHERMAL_GENERATOR;
		ModContent.PUMP_MENU = () -> PUMP;
		ModContent.WATER_MILL_MENU = () -> WATER_MILL;
		ModContent.WIND_MILL_MENU = () -> WIND_MILL;
		ModContent.HIGH_ALTITUDE_WIND_MILL_MENU = () -> HIGH_ALTITUDE_WIND_MILL;
		ModContent.STORM_WIND_MILL_MENU = () -> STORM_WIND_MILL;
		ModContent.IRON_CHEST_MENU = () -> IRON_CHEST;
		ModContent.SILVER_CHEST_MENU = () -> SILVER_CHEST;
		ModContent.GOLD_CHEST_MENU = () -> GOLD_CHEST;
	}

	private static <T extends AbstractContainerMenu> MenuType<T> register(String path, MenuType<T> type) {
		return Registry.register(BuiltInRegistries.MENU, Industrialization.id(path), type);
	}
}
