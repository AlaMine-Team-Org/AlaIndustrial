package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.CompressorScreen;
import dev.alaindustrial.client.ElectricFurnaceScreen;
import dev.alaindustrial.client.EnergyPackHud;
import dev.alaindustrial.client.IronChestBlockEntityRenderer;
import dev.alaindustrial.client.MachineTooltips;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.client.SolarPanelScreen;
import dev.alaindustrial.client.WaterMillWheelBlockEntityRenderer;
import dev.alaindustrial.client.WindMillRotorBlockEntityRenderer;
import dev.alaindustrial.registry.ModBlockEntities;
import dev.alaindustrial.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.object.chest.ChestModel;

/**
 * Client entrypoint for Industrialization. Binds machine menus to their screens and registers the
 * hover-tooltip provider. The tooltip content itself is loader-neutral in
 * {@link MachineTooltips} (common); this only hooks it onto Fabric's {@code ItemTooltipCallback}.
 */
public class IndustrializationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AlaClientConfig.init(FabricLoader.getInstance().getConfigDir());

		MenuScreens.<dev.alaindustrial.menu.GeneratorMenu, dev.alaindustrial.client.GeneratorScreen>register(
				ModMenus.GENERATOR, dev.alaindustrial.client.GeneratorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.MaceratorMenu, dev.alaindustrial.client.MaceratorScreen>register(
				ModMenus.MACERATOR, dev.alaindustrial.client.MaceratorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.ElectricFurnaceMenu, ElectricFurnaceScreen>register(
				ModMenus.ELECTRIC_FURNACE, ElectricFurnaceScreen::new);
		MenuScreens.<dev.alaindustrial.menu.ExtractorMenu, dev.alaindustrial.client.ExtractorScreen>register(
				ModMenus.EXTRACTOR, dev.alaindustrial.client.ExtractorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.CompressorMenu, CompressorScreen>register(
				ModMenus.COMPRESSOR, CompressorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.SolarPanelMenu, SolarPanelScreen>register(
				ModMenus.SOLAR_PANEL, SolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.MoonlitSolarPanelMenu, dev.alaindustrial.client.MoonlitSolarPanelScreen>register(
				ModMenus.MOONLIT_SOLAR_PANEL, dev.alaindustrial.client.MoonlitSolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.BatteryBoxMenu, dev.alaindustrial.client.BatteryBoxScreen>register(
				ModMenus.BATTERY_BOX, dev.alaindustrial.client.BatteryBoxScreen::new);
		MenuScreens.<dev.alaindustrial.menu.DaylightSolarPanelMenu, dev.alaindustrial.client.DaylightSolarPanelScreen>register(
				ModMenus.DAYLIGHT_SOLAR_PANEL, dev.alaindustrial.client.DaylightSolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.GeothermalGeneratorMenu, dev.alaindustrial.client.GeothermalGeneratorScreen>register(
				ModMenus.GEOTHERMAL_GENERATOR, dev.alaindustrial.client.GeothermalGeneratorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.PumpMenu, dev.alaindustrial.client.PumpScreen>register(
				ModMenus.PUMP, dev.alaindustrial.client.PumpScreen::new);
		MenuScreens.<dev.alaindustrial.menu.WaterMillMenu, dev.alaindustrial.client.WaterMillScreen>register(
				ModMenus.WATER_MILL, dev.alaindustrial.client.WaterMillScreen::new);
		MenuScreens.<dev.alaindustrial.menu.WindMillMenu, dev.alaindustrial.client.WindMillScreen>register(
				ModMenus.WIND_MILL, dev.alaindustrial.client.WindMillScreen::new);
		MenuScreens.<dev.alaindustrial.menu.HighAltitudeWindMillMenu, dev.alaindustrial.client.HighAltitudeWindMillScreen>register(
				ModMenus.HIGH_ALTITUDE_WIND_MILL, dev.alaindustrial.client.HighAltitudeWindMillScreen::new);
		MenuScreens.<dev.alaindustrial.menu.StormWindMillMenu, dev.alaindustrial.client.StormWindMillScreen>register(
				ModMenus.STORM_WIND_MILL, dev.alaindustrial.client.StormWindMillScreen::new);
		MenuScreens.<dev.alaindustrial.menu.IronChestMenu, dev.alaindustrial.client.IronChestScreen>register(
				ModMenus.IRON_CHEST, dev.alaindustrial.client.IronChestScreen::new);

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
				MachineTooltips.append(stack, lines, Minecraft.getInstance().hasShiftDown()));
		// Battery Pouch bundle-style tooltip (MOD-052): map the neutral TooltipComponent to its renderer.
		net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(component ->
				component instanceof dev.alaindustrial.item.PouchTooltip pouch
						? new dev.alaindustrial.client.PouchClientTooltip(pouch)
						: null);
		// Energy Pack charge readout (MOD-065): the mod's first HUD element and first key mapping.
		// The drawing itself is loader-neutral (EnergyPackHud) — Fabric's HudElement and NeoForge's
		// GuiLayer take the same (GuiGraphicsExtractor, DeltaTracker) pair.
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.TOGGLE_ENERGY_HUD);
		ClientTickEvents.END_CLIENT_TICK.register(client -> ModKeyMappings.handleInput());
		HudElementRegistry.addLast(Industrialization.id("energy_pack_hud"), EnergyPackHud::render);

		dev.alaindustrial.client.NetworkVisualizationClient.init();
		dev.alaindustrial.client.CablePlacementPreview.init();
		dev.alaindustrial.client.sound.MachineHumClientHook.register();

		// Iron chest: 3D model + animated lid. Register the BlockEntityRenderer against the iron
		// chest BE type, and bake the chest model layer (vanilla single-body chest geometry).
		BlockEntityRendererRegistry.register(ModBlockEntities.IRON_CHEST, IronChestBlockEntityRenderer::new);
		ModelLayerRegistry.registerModelLayer(IronChestBlockEntityRenderer.IRON_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.WATER_MILL, WaterMillWheelBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.HIGH_ALTITUDE_WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.STORM_WIND_MILL, WindMillRotorBlockEntityRenderer::new);

		// Stock Display Frame (MOD-066): the mod's first entity renderer. Vanilla EntityRenderers.register
		// is the path Fabric's own docs recommend (their EntityRendererRegistry is a thin legacy wrapper).
		net.minecraft.client.renderer.entity.EntityRenderers.register(
				dev.alaindustrial.registry.ModEntities.STOCK_DISPLAY_FRAME,
				dev.alaindustrial.client.StockDisplayFrameRenderer::new);

		Industrialization.LOGGER.info("Industrialization client initialized.");
	}
}
