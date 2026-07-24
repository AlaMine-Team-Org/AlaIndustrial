package dev.alaindustrial.client.screen;

import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Client-only menu&#8594;screen bindings for every machine GUI (MOD-190). Shared by both loaders' client
 * entrypoints ({@code IndustrializationClient} on Fabric, {@code IndustrializationNeoForgeClient} on
 * NeoForge), which each loop over {@link #SCREENS} and call their loader's screen-registration API
 * ({@code MenuScreens.register} / {@code RegisterMenuScreensEvent#register}) with {@code factory::create}.
 *
 * <p>Lives in the {@code client.screen} package so the screen classes need no import, and so it is only
 * ever classloaded on the physical client (never on a dedicated server).
 */
public final class MenuScreenManifest {
	private MenuScreenManifest() {
	}

	/**
	 * The screen constructor {@code (menu, Inventory, Component) -> Screen}. Our own public functional
	 * interface — vanilla's {@code MenuScreens.ScreenConstructor} is private, so it cannot be named here;
	 * each loader passes {@code factory::create} to its screen-registration API.
	 *
	 * @param <M> the menu class
	 * @param <U> the screen class
	 */
	@FunctionalInterface
	public interface ScreenFactory<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> {
		U create(M menu, Inventory playerInventory, Component title);
	}

	/**
	 * A loader's screen-registration API, named as one generic method so the menu/screen pair keeps its
	 * types all the way to the call (MOD-198). Fabric implements it with {@code MenuScreens.register},
	 * NeoForge with {@code RegisterMenuScreensEvent#register}.
	 *
	 * <p><b>Not quite the vanilla signature.</b> Both loader APIs take {@code MenuType<? extends M>};
	 * this declares the invariant {@code MenuType<M>} on purpose, because {@link ScreenDef} pins the
	 * {@code MenuType} and the screen to the same {@code M} — which is the whole point of MOD-198.
	 * Narrowing a parameter is safe (every {@code MenuType<M>} is a {@code MenuType<? extends M>}); the
	 * cost is that one screen written against a <i>supertype</i> menu — say a single chest screen over
	 * {@code AbstractChestMenu} serving all three chests — would not fit. Widening here <i>and</i> in
	 * {@link #screen} would be the change to make if that day comes.
	 *
	 * <p>It is an interface with a generic <i>method</i> — not a generic interface — because that is what
	 * lets {@link ScreenDef#bindTo} hand over a captured wildcard without an unchecked cast. A lambda
	 * cannot implement it (Java has no generic lambdas), so loaders pass an anonymous class.
	 */
	public interface ScreenRegistrar {
		<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(
				MenuType<M> menuType, ScreenFactory<M, U> screen);
	}

	/**
	 * One menu&#8594;screen binding. {@code menuType} reads the loader-populated {@link ModContent} slot
	 * lazily; {@code screen} builds the screen. Both sides share {@code M}, so the pair cannot be
	 * mismatched (MOD-198).
	 *
	 * @param <M>      the menu class, shared by the {@code MenuType} and the screen
	 * @param <U>      the screen class
	 * @param menuType reads the loader-populated {@link ModContent} slot lazily
	 * @param screen   builds the screen for that menu
	 */
	public record ScreenDef<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>>(
			Supplier<MenuType<M>> menuType, ScreenFactory<M, U> screen) {

		/**
		 * Hands this pair to a loader's registration API. Called on a wildcard element of
		 * {@link #SCREENS}; capture conversion re-binds {@code M} and {@code U} inside, which is why
		 * neither loader needs a cast.
		 */
		public void bindTo(ScreenRegistrar registrar) {
			registrar.register(menuType.get(), screen);
		}
	}

	/**
	 * Builds a {@link ScreenDef}. {@code M} is inferred from <i>both</i> arguments, so a mismatched pair
	 * ({@code screen(() -> ModContent.SAWMILL_MENU.get(), MaceratorScreen::new)}) fails to compile
	 * instead of throwing {@code ClassCastException} when the player opens the GUI (MOD-198).
	 */
	private static <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> ScreenDef<M, U> screen(
			Supplier<MenuType<M>> menuType, ScreenFactory<M, U> screen) {
		return new ScreenDef<>(menuType, screen);
	}

	/** Every machine/chest menu&#8594;screen pair, in the same shared order as {@code ContentManifest.MENUS}. */
	public static final List<ScreenDef<?, ?>> SCREENS = List.of(
			screen(() -> ModContent.GENERATOR_MENU.get(), GeneratorScreen::new),
			screen(() -> ModContent.MACERATOR_MENU.get(), MaceratorScreen::new),
			screen(() -> ModContent.SOLAR_PANEL_MENU.get(), SolarPanelScreen::new),
			screen(() -> ModContent.MOONLIT_SOLAR_PANEL_MENU.get(), MoonlitSolarPanelScreen::new),
			screen(() -> ModContent.ELECTRIC_FURNACE_MENU.get(), ElectricFurnaceScreen::new),
			screen(() -> ModContent.EXTRACTOR_MENU.get(), ExtractorScreen::new),
			screen(() -> ModContent.COMPRESSOR_MENU.get(), CompressorScreen::new),
			screen(() -> ModContent.SAWMILL_MENU.get(), SawmillScreen::new),
			screen(() -> ModContent.BATTERY_BOX_MENU.get(), BatteryBoxScreen::new),
			screen(() -> ModContent.TELEPORTER_STATION_MENU.get(), TeleporterStationScreen::new),
			screen(() -> ModContent.TELEPORTER_REMOTE_MENU.get(), TeleporterRemoteScreen::new),
			screen(() -> ModContent.DAYLIGHT_SOLAR_PANEL_MENU.get(), DaylightSolarPanelScreen::new),
			screen(() -> ModContent.GEOTHERMAL_GENERATOR_MENU.get(), GeothermalGeneratorScreen::new),
			screen(() -> ModContent.PUMP_MENU.get(), PumpScreen::new),
			screen(() -> ModContent.WATER_MILL_MENU.get(), WaterMillScreen::new),
			screen(() -> ModContent.WIND_MILL_MENU.get(), WindMillScreen::new),
			screen(() -> ModContent.HIGH_ALTITUDE_WIND_MILL_MENU.get(), HighAltitudeWindMillScreen::new),
			screen(() -> ModContent.STORM_WIND_MILL_MENU.get(), StormWindMillScreen::new),
			screen(() -> ModContent.IRON_CHEST_MENU.get(), IronChestScreen::new),
			screen(() -> ModContent.SILVER_CHEST_MENU.get(), SilverChestScreen::new),
			screen(() -> ModContent.GOLD_CHEST_MENU.get(), GoldChestScreen::new));
}
