package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.menu.CompressorMenu;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.menu.ExtractorMenu;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
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
	}

	private static <T extends AbstractContainerMenu> MenuType<T> register(String path, MenuType<T> type) {
		return Registry.register(BuiltInRegistries.MENU, Industrialization.id(path), type);
	}
}
