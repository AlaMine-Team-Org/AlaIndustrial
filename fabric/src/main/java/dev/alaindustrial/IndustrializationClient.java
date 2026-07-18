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
		dev.alaindustrial.client.FluidTankItemTintSource.register();

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
		MenuScreens.<dev.alaindustrial.menu.TeleporterStationMenu,
				dev.alaindustrial.client.TeleporterStationScreen>register(
				ModMenus.TELEPORTER_STATION, dev.alaindustrial.client.TeleporterStationScreen::new);
		MenuScreens.<dev.alaindustrial.menu.TeleporterRemoteMenu,
				dev.alaindustrial.client.TeleporterRemoteScreen>register(
				ModMenus.TELEPORTER_REMOTE, dev.alaindustrial.client.TeleporterRemoteScreen::new);
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
		MenuScreens.<dev.alaindustrial.menu.SilverChestMenu, dev.alaindustrial.client.SilverChestScreen>register(
				ModMenus.SILVER_CHEST, dev.alaindustrial.client.SilverChestScreen::new);
		MenuScreens.<dev.alaindustrial.menu.GoldChestMenu, dev.alaindustrial.client.GoldChestScreen>register(
				ModMenus.GOLD_CHEST, dev.alaindustrial.client.GoldChestScreen::new);

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
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.TOGGLE_DRILL_HUD);
		ClientTickEvents.END_CLIENT_TICK.register(client -> ModKeyMappings.handleInput());
		// Teleport screen fade (MOD-106). Registered first so the readouts below stay legible over it —
		// and addLast keeps it under vanilla's own overlays, which a jump has no business hiding.
		HudElementRegistry.addLast(Industrialization.id("teleport_fade"),
				dev.alaindustrial.client.TeleportFadeHud::render);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.alaindustrial.network.TeleportFadePayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> dev.alaindustrial.client.TeleportFadeHud.receive(payload.strength())));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> dev.alaindustrial.client.TeleportFadeHud.reset());
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.alaindustrial.network.TeleportNoticePayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> dev.alaindustrial.client.TeleportNotice.receive(payload.message())));

		HudElementRegistry.addLast(Industrialization.id("energy_pack_hud"), EnergyPackHud::render);
		// Electric Drill charge readout (MOD-079) — same toggle/key as the pack, stacks below it.
		HudElementRegistry.addLast(Industrialization.id("electric_drill_hud"),
				dev.alaindustrial.client.ElectricDrillHud::render);

		// MOD-085: green flame particle for the Enriched Uranium Torch. Reuses the vanilla FlameParticle
		// provider (like soul_fire_flame) — the green colour comes entirely from the particle's own texture
		// (assets/alaindustrial/particles/enriched_uranium_flame.json), no custom particle class or tint.
		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(
				dev.alaindustrial.registry.ModParticles.ENRICHED_URANIUM_FLAME,
				net.minecraft.client.particle.FlameParticle.Provider::new);

		dev.alaindustrial.client.NetworkVisualizationClient.init();
		dev.alaindustrial.client.CablePlacementPreview.init();
		dev.alaindustrial.client.sound.MachineHumClientHook.register();
		// MOD-108: answers "is Shift held" for item tooltips (the pipe shows its numbers behind Shift).
		dev.alaindustrial.client.TooltipKeysClientHook.register();

		// Iron chest: 3D model + animated lid. Register the BlockEntityRenderer against the iron
		// chest BE type, and bake the chest model layer (vanilla single-body chest geometry).
		BlockEntityRendererRegistry.register(ModBlockEntities.IRON_CHEST, IronChestBlockEntityRenderer::new);
		ModelLayerRegistry.registerModelLayer(IronChestBlockEntityRenderer.IRON_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		// Silver chest (MOD-087): same 3D chest model + animated lid, textured with entity/chest/silver.
		BlockEntityRendererRegistry.register(ModBlockEntities.SILVER_CHEST, dev.alaindustrial.client.SilverChestBlockEntityRenderer::new);
		ModelLayerRegistry.registerModelLayer(dev.alaindustrial.client.SilverChestBlockEntityRenderer.SILVER_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		// Gold chest (MOD-088): same 3D chest model + animated lid, textured with entity/chest/gold.
		BlockEntityRendererRegistry.register(ModBlockEntities.GOLD_CHEST, dev.alaindustrial.client.GoldChestBlockEntityRenderer::new);
		ModelLayerRegistry.registerModelLayer(dev.alaindustrial.client.GoldChestBlockEntityRenderer.GOLD_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		ModelLayerRegistry.registerModelLayer(WaterMillWheelBlockEntityRenderer.MODEL_LAYER,
				WaterMillWheelBlockEntityRenderer::createLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.WATER_MILL, WaterMillWheelBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.HIGH_ALTITUDE_WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.STORM_WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.FLUID_TANK,
				dev.alaindustrial.client.FluidTankBlockEntityRenderer::new);

		// Stock Display Frame (MOD-066): the mod's first entity renderer. Vanilla EntityRenderers.register
		// is the path Fabric's own docs recommend (their EntityRendererRegistry is a thin legacy wrapper).
		net.minecraft.client.renderer.entity.EntityRenderers.register(
				dev.alaindustrial.registry.ModEntities.STOCK_DISPLAY_FRAME,
				dev.alaindustrial.client.StockDisplayFrameRenderer::new);

		Industrialization.LOGGER.info("Industrialization client initialized.");
	}
}
