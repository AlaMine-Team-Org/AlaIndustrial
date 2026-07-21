package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.screen.CompressorScreen;
import dev.alaindustrial.client.screen.ElectricFurnaceScreen;
import dev.alaindustrial.client.hud.EnergyPackHud;
import dev.alaindustrial.client.render.ChestBlockEntityRenderer;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.client.screen.SolarPanelScreen;
import dev.alaindustrial.client.render.WaterMillWheelBlockEntityRenderer;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
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
 *
 * <p>MOD-137: {@code onInitializeClient()} is a table of contents — each step is a named private
 * method, called in the same order the statements ran before.
 */
public class IndustrializationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		initClientConfig();
		registerMenuScreens();
		registerTooltips();
		registerHudAndKeys();
		registerParticleProviders();
		registerClientHooks();
		registerBlockEntityRenderers();
		// MOD-133: client dashboard reads the local player's synced stats attachment through the seam.
		dev.alaindustrial.stats.PlayerStatsClientCache.bind(() -> {
			net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
			return p == null ? dev.alaindustrial.stats.PlayerModStats.EMPTY
					: p.getAttachedOrElse(dev.alaindustrial.stats.fabric.FabricPlayerStats.TYPE,
							dev.alaindustrial.stats.PlayerModStats.EMPTY);
		});

		Industrialization.LOGGER.info("Industrialization client initialized.");
	}

	/** Initialises the client config screen state and the fluid-tank item tint source. */
	private void initClientConfig() {
		AlaClientConfig.init(FabricLoader.getInstance().getConfigDir());
		dev.alaindustrial.client.render.FluidTankItemTintSource.register();
	}

	/** Binds each machine {@code MenuType} to its {@code Screen}. */
	private void registerMenuScreens() {
		MenuScreens.<dev.alaindustrial.menu.GeneratorMenu, dev.alaindustrial.client.screen.GeneratorScreen>register(
				ModMenus.GENERATOR, dev.alaindustrial.client.screen.GeneratorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.MaceratorMenu, dev.alaindustrial.client.screen.MaceratorScreen>register(
				ModMenus.MACERATOR, dev.alaindustrial.client.screen.MaceratorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.ElectricFurnaceMenu, ElectricFurnaceScreen>register(
				ModMenus.ELECTRIC_FURNACE, ElectricFurnaceScreen::new);
		MenuScreens.<dev.alaindustrial.menu.ExtractorMenu, dev.alaindustrial.client.screen.ExtractorScreen>register(
				ModMenus.EXTRACTOR, dev.alaindustrial.client.screen.ExtractorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.CompressorMenu, CompressorScreen>register(
				ModMenus.COMPRESSOR, CompressorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.SolarPanelMenu, SolarPanelScreen>register(
				ModMenus.SOLAR_PANEL, SolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.MoonlitSolarPanelMenu, dev.alaindustrial.client.screen.MoonlitSolarPanelScreen>register(
				ModMenus.MOONLIT_SOLAR_PANEL, dev.alaindustrial.client.screen.MoonlitSolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.BatteryBoxMenu, dev.alaindustrial.client.screen.BatteryBoxScreen>register(
				ModMenus.BATTERY_BOX, dev.alaindustrial.client.screen.BatteryBoxScreen::new);
		MenuScreens.<dev.alaindustrial.menu.TeleporterStationMenu,
				dev.alaindustrial.client.screen.TeleporterStationScreen>register(
				ModMenus.TELEPORTER_STATION, dev.alaindustrial.client.screen.TeleporterStationScreen::new);
		MenuScreens.<dev.alaindustrial.menu.TeleporterRemoteMenu,
				dev.alaindustrial.client.screen.TeleporterRemoteScreen>register(
				ModMenus.TELEPORTER_REMOTE, dev.alaindustrial.client.screen.TeleporterRemoteScreen::new);
		MenuScreens.<dev.alaindustrial.menu.DaylightSolarPanelMenu, dev.alaindustrial.client.screen.DaylightSolarPanelScreen>register(
				ModMenus.DAYLIGHT_SOLAR_PANEL, dev.alaindustrial.client.screen.DaylightSolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.GeothermalGeneratorMenu, dev.alaindustrial.client.screen.GeothermalGeneratorScreen>register(
				ModMenus.GEOTHERMAL_GENERATOR, dev.alaindustrial.client.screen.GeothermalGeneratorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.PumpMenu, dev.alaindustrial.client.screen.PumpScreen>register(
				ModMenus.PUMP, dev.alaindustrial.client.screen.PumpScreen::new);
		MenuScreens.<dev.alaindustrial.menu.WaterMillMenu, dev.alaindustrial.client.screen.WaterMillScreen>register(
				ModMenus.WATER_MILL, dev.alaindustrial.client.screen.WaterMillScreen::new);
		MenuScreens.<dev.alaindustrial.menu.WindMillMenu, dev.alaindustrial.client.screen.WindMillScreen>register(
				ModMenus.WIND_MILL, dev.alaindustrial.client.screen.WindMillScreen::new);
		MenuScreens.<dev.alaindustrial.menu.HighAltitudeWindMillMenu, dev.alaindustrial.client.screen.HighAltitudeWindMillScreen>register(
				ModMenus.HIGH_ALTITUDE_WIND_MILL, dev.alaindustrial.client.screen.HighAltitudeWindMillScreen::new);
		MenuScreens.<dev.alaindustrial.menu.StormWindMillMenu, dev.alaindustrial.client.screen.StormWindMillScreen>register(
				ModMenus.STORM_WIND_MILL, dev.alaindustrial.client.screen.StormWindMillScreen::new);
		MenuScreens.<dev.alaindustrial.menu.IronChestMenu, dev.alaindustrial.client.screen.IronChestScreen>register(
				ModMenus.IRON_CHEST, dev.alaindustrial.client.screen.IronChestScreen::new);
		MenuScreens.<dev.alaindustrial.menu.SilverChestMenu, dev.alaindustrial.client.screen.SilverChestScreen>register(
				ModMenus.SILVER_CHEST, dev.alaindustrial.client.screen.SilverChestScreen::new);
		MenuScreens.<dev.alaindustrial.menu.GoldChestMenu, dev.alaindustrial.client.screen.GoldChestScreen>register(
				ModMenus.GOLD_CHEST, dev.alaindustrial.client.screen.GoldChestScreen::new);
	}

	/** Registers the machine hover-tooltip provider and the Battery Pouch bundle-style tooltip renderer. */
	private void registerTooltips() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
				MachineTooltips.append(stack, lines, Minecraft.getInstance().hasShiftDown()));
		// Battery Pouch bundle-style tooltip (MOD-052): map the neutral TooltipComponent to its renderer.
		net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(component ->
				component instanceof dev.alaindustrial.item.PouchTooltip pouch
						? new dev.alaindustrial.client.tooltip.PouchClientTooltip(pouch)
						: null);
	}

	/**
	 * Registers the HUD elements (teleport fade, energy-pack + drill charge readouts), their key
	 * mappings, and the client-side teleport payload receivers that feed them.
	 */
	private void registerHudAndKeys() {
		// Energy Pack charge readout (MOD-065): the mod's first HUD element and first key mapping.
		// The drawing itself is loader-neutral (EnergyPackHud) — Fabric's HudElement and NeoForge's
		// GuiLayer take the same (GuiGraphicsExtractor, DeltaTracker) pair.
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.TOGGLE_ENERGY_HUD);
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.TOGGLE_DRILL_HUD);
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.OPEN_PROFILE); // MOD-133 player dashboard
		ClientTickEvents.END_CLIENT_TICK.register(client -> ModKeyMappings.handleInput());
		// Teleport screen fade (MOD-106). Registered first so the readouts below stay legible over it —
		// and addLast keeps it under vanilla's own overlays, which a jump has no business hiding.
		HudElementRegistry.addLast(Industrialization.id("teleport_fade"),
				dev.alaindustrial.client.hud.TeleportFadeHud::render);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.alaindustrial.network.TeleportFadePayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> dev.alaindustrial.client.hud.TeleportFadeHud.receive(payload.strength())));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> dev.alaindustrial.client.hud.TeleportFadeHud.reset());
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.alaindustrial.network.TeleportNoticePayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> dev.alaindustrial.client.hud.TeleportNotice.receive(payload.message())));

		HudElementRegistry.addLast(Industrialization.id("energy_pack_hud"), EnergyPackHud::render);
		// Electric Drill charge readout (MOD-079) — same toggle/key as the pack, stacks below it.
		HudElementRegistry.addLast(Industrialization.id("electric_drill_hud"),
				dev.alaindustrial.client.hud.ElectricDrillHud::render);
	}

	/** Registers the green-flame particle provider for the Enriched Uranium Torch (MOD-085). */
	private void registerParticleProviders() {
		// MOD-085: green flame particle for the Enriched Uranium Torch. Reuses the vanilla FlameParticle
		// provider (like soul_fire_flame) — the green colour comes entirely from the particle's own texture
		// (assets/alaindustrial/particles/enriched_uranium_flame.json), no custom particle class or tint.
		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(
				dev.alaindustrial.registry.ModParticles.ENRICHED_URANIUM_FLAME,
				net.minecraft.client.particle.FlameParticle.Provider::new);
	}

	/** Installs the world-overlay / client-hook singletons (network viz, cable preview, hum, tooltip keys). */
	private void registerClientHooks() {
		dev.alaindustrial.client.NetworkVisualizationClient.init();
		dev.alaindustrial.client.CablePlacementPreview.init();
		dev.alaindustrial.client.sound.MachineHumClientHook.register();
		// MOD-108: answers "is Shift held" for item tooltips (the pipe shows its numbers behind Shift).
		dev.alaindustrial.client.tooltip.TooltipKeysClientHook.register();
		// MOD-133: add the profile button to the survival inventory screen (creative uses a different
		// screen class, so this instanceof already excludes it). No injected mixin — a Fabric screen event.
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
				net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen)
						.add(dev.alaindustrial.client.dashboard.InventoryProfileButton.install(screen));
			}
		});
	}

	/** Registers the block-entity / entity renderers and bakes their model layers. */
	private void registerBlockEntityRenderers() {
		// Storage chests: 3D model + animated lid, one shared renderer per tier texture. Register the
		// BlockEntityRenderer against each chest BE type, and bake the chest model layer (vanilla
		// single-body chest geometry).
		BlockEntityRendererRegistry.register(ModBlockEntities.IRON_CHEST, ChestBlockEntityRenderer::iron);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.IRON_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.SILVER_CHEST, ChestBlockEntityRenderer::silver);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.SILVER_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.GOLD_CHEST, ChestBlockEntityRenderer::gold);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.GOLD_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		ModelLayerRegistry.registerModelLayer(WaterMillWheelBlockEntityRenderer.MODEL_LAYER,
				WaterMillWheelBlockEntityRenderer::createLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.WATER_MILL, WaterMillWheelBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.HIGH_ALTITUDE_WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.STORM_WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.FLUID_TANK,
				dev.alaindustrial.client.render.FluidTankBlockEntityRenderer::new);

		// Stock Display Frame (MOD-066): the mod's first entity renderer. Vanilla EntityRenderers.register
		// is the path Fabric's own docs recommend (their EntityRendererRegistry is a thin legacy wrapper).
		net.minecraft.client.renderer.entity.EntityRenderers.register(
				dev.alaindustrial.registry.ModEntities.STOCK_DISPLAY_FRAME,
				dev.alaindustrial.client.render.StockDisplayFrameRenderer::new);
	}
}
