package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.screen.AlaConfigScreen;
import dev.alaindustrial.client.screen.BatteryBoxScreen;
import dev.alaindustrial.client.screen.CompressorScreen;
import dev.alaindustrial.client.screen.DaylightSolarPanelScreen;
import dev.alaindustrial.client.screen.ElectricFurnaceScreen;
import dev.alaindustrial.client.hud.EnergyPackHud;
import dev.alaindustrial.client.screen.ExtractorScreen;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.client.screen.GeneratorScreen;
import dev.alaindustrial.client.screen.GeothermalGeneratorScreen;
import dev.alaindustrial.client.render.ChestBlockEntityRenderer;
import dev.alaindustrial.client.screen.MaceratorScreen;
import dev.alaindustrial.client.screen.PumpScreen;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.client.screen.MoonlitSolarPanelScreen;
import dev.alaindustrial.client.screen.SolarPanelScreen;
import dev.alaindustrial.client.screen.WaterMillScreen;
import dev.alaindustrial.client.render.WaterMillWheelBlockEntityRenderer;
import dev.alaindustrial.client.screen.WindMillScreen;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
import dev.alaindustrial.client.neoforge.NeoForgeCableGhost;
import dev.alaindustrial.client.neoforge.NeoForgeNetworkVisualization;
import dev.alaindustrial.registry.neoforge.ModBlockEntitiesNeoForge;
import dev.alaindustrial.registry.neoforge.ModMenusNeoForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.object.chest.ChestModel;
import net.neoforged.fml.ModContainer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * NeoForge client entrypoint (MOD-022 Phase 3). A {@code dist = Dist.CLIENT} companion to
 * {@link IndustrializationNeoForge} — NeoForge instantiates it only on the physical client and
 * injects the mod event bus, mirroring the Fabric {@code IndustrializationClient} client initializer.
 *
 * <p>Its job is the machine screen binding: the {@link RegisterMenuScreensEvent} listener is the
 * NeoForge counterpart to the Fabric {@code MenuScreens.register(menuType, Screen::new)} calls. The
 * bindings themselves land in Phase 4 alongside the common menu/screen content (see the listener
 * body); this class wires the verified 26.2 event now so the migration only drops the
 * {@code event.register(...)} lines in.
 */
@Mod(value = Industrialization.MOD_ID, dist = {Dist.CLIENT})
public final class IndustrializationNeoForgeClient {

	/**
	 * MOD-137: the constructor is a table of contents. {@link #registerClientEvents} keeps every
	 * listener registration in one method rather than splitting mod-bus from game-bus, because the
	 * original order interleaves the two buses and reordering across that boundary is avoided.
	 */
	public IndustrializationNeoForgeClient(IEventBus modBus, ModContainer container) {
		initClientConfig(container);
		registerClientEvents(modBus);
		// MOD-133: client dashboard reads the local player's synced stats attachment through the seam.
		dev.alaindustrial.stats.PlayerStatsClientCache.bind(() -> {
			net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
			return p == null ? dev.alaindustrial.stats.PlayerModStats.EMPTY
					: p.getData(dev.alaindustrial.registry.neoforge.ModAttachmentsNeoForge.PLAYER_STATS);
		});

		Industrialization.LOGGER.info("Industrialization (NeoForge client) initialized.");
	}

	/** Initialises the client config screen state, the fluid-tank item tint source, and the config-screen factory. */
	private void initClientConfig(ModContainer container) {
		AlaClientConfig.init(FMLPaths.CONFIGDIR.get());
		dev.alaindustrial.client.render.FluidTankItemTintSource.register();
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(modContainer, parent) -> new AlaConfigScreen(parent));
	}

	/**
	 * Registers every client-side listener in the original order. The order interleaves mod-bus
	 * registrations (menu screens, particle providers, tooltip factories, renderers, layer definitions,
	 * key mappings, GUI layers) with game-bus registrations (item tooltips, world overlays, client tick,
	 * disconnect cleanup) and the two static client hooks (machine hum, tooltip keys); it is kept as one
	 * method to preserve that order (MOD-137).
	 */
	private void registerClientEvents(IEventBus modBus) {
		modBus.addListener(this::registerMenuScreens);
		// MOD-085: green flame particle provider for the Enriched Uranium Torch. registerSpriteSet =
		// json-backed particle (assets/alaindustrial/particles/enriched_uranium_flame.json); reuses the
		// vanilla FlameParticle provider (like soul_fire_flame), colour comes from the particle texture.
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) ->
				event.registerSpriteSet(dev.alaindustrial.registry.ModParticles.ENRICHED_URANIUM_FLAME,
						net.minecraft.client.particle.FlameParticle.Provider::new));
		// Battery Pouch bundle-style tooltip (MOD-052) — NeoForge counterpart to the Fabric
		// ClientTooltipComponentCallback mapping in IndustrializationClient.
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) ->
				event.register(dev.alaindustrial.item.PouchTooltip.class,
						dev.alaindustrial.client.tooltip.PouchClientTooltip::new));
		// Iron chest: 3D model + animated lid. Register the BlockEntityRenderer + bake the chest
		// model layer (vanilla single-body chest geometry), the NeoForge counterpart to the Fabric
		// BlockEntityRendererRegistry + ModelLayerRegistry calls in IndustrializationClient.
		modBus.addListener(this::registerRenderers);
		modBus.addListener(this::registerLayerDefinitions);
		// Install the client-side machine-hum manager (looping ambient sound). Counterpart to the Fabric
		// IndustrializationClient call; this @Mod class is dist=CLIENT, so it runs only on the physical client.
		dev.alaindustrial.client.sound.MachineHumClientHook.register();
		// MOD-108: answers "is Shift held" for item tooltips (the pipe shows its numbers behind Shift).
		dev.alaindustrial.client.tooltip.TooltipKeysClientHook.register();
		// Hover tooltips for machine block items + the Network Analyzer. Counterpart to the Fabric
		// ItemTooltipCallback in IndustrializationClient; the content is loader-neutral in MachineTooltips.
		// ItemTooltipEvent fires on the game bus (client only), so it goes on NeoForge.EVENT_BUS.
		NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event) ->
				MachineTooltips.append(event.getItemStack(), event.getToolTip(), Minecraft.getInstance().hasShiftDown()));
		// World overlays (counterparts to the Fabric NetworkVisualizationClient + CablePlacementPreview).
		// The analyzer overlay submits per-frame custom geometry: SubmitCustomGeometryEvent exposes the
		// render-time SubmitNodeCollector (RenderLevelStageEvent does not), firing at the same frame point
		// as the Fabric AFTER_TRANSLUCENT_FEATURES hook — full visual parity via the common
		// NetworkOverlayRenderer (MOD-033/MOD-060). The cable ghost stays on the vanilla per-tick gizmo
		// API: a static block-shaped preview gains nothing from per-frame submission.
		NeoForge.EVENT_BUS.addListener(NeoForgeNetworkVisualization::onSubmitCustomGeometry);
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> NeoForgeCableGhost.tick());
		// Energy Pack charge readout (MOD-065) — counterpart to the Fabric KeyMappingHelper +
		// HudElementRegistry + ClientTickEvents trio. The drawing is loader-neutral (EnergyPackHud):
		// NeoForge's GuiLayer and Fabric's HudElement take the same (GuiGraphicsExtractor, DeltaTracker).
		// Only the mapping — NOT the category. ModKeyMappings builds the category with the vanilla
		// KeyMapping.Category.register, which already appends it to the sort order on both loaders;
		// calling event.registerCategory here as well would list it twice on NeoForge (harmless today,
		// since lookups take the first match, but a real loader asymmetry).
		modBus.addListener((RegisterKeyMappingsEvent event) -> {
			event.register(ModKeyMappings.TOGGLE_ENERGY_HUD);
			event.register(ModKeyMappings.TOGGLE_DRILL_HUD);
			event.register(ModKeyMappings.OPEN_PROFILE); // MOD-133 player dashboard
		});
		modBus.addListener((RegisterGuiLayersEvent event) -> {
			// Teleport screen fade (MOD-106) — counterpart to the Fabric HudElementRegistry entry; the
			// drawing itself is loader-neutral (TeleportFadeHud). Registered before the readouts so they
			// stay legible over it.
			event.registerAboveAll(Industrialization.id("teleport_fade"),
					dev.alaindustrial.client.hud.TeleportFadeHud::render);
			event.registerAboveAll(Industrialization.id("energy_pack_hud"), EnergyPackHud::render);
			// Electric Drill charge readout (MOD-079) — same toggle/key as the pack, stacks below it.
			event.registerAboveAll(Industrialization.id("electric_drill_hud"),
					dev.alaindustrial.client.hud.ElectricDrillHud::render);
		});
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ModKeyMappings.handleInput());
		// MOD-133: add the profile button to the survival inventory screen (creative is a different screen
		// class, excluded by this instanceof). No injected mixin — a NeoForge screen-init event.
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) -> {
			if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
				event.addListener(dev.alaindustrial.client.dashboard.InventoryProfileButton.install(event.getScreen()));
			}
		});
		// Leaving a world drops any fade in flight, so it cannot bleed into the next one (MOD-106) —
		// the Fabric counterpart hangs off ClientPlayConnectionEvents.DISCONNECT.
		NeoForge.EVENT_BUS.addListener(
				(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
						dev.alaindustrial.client.hud.TeleportFadeHud.reset());
	}

	/**
	 * Binds each machine {@code MenuType} to its {@code Screen}. Verified pattern (neoforge-26.2.0.8-beta):
	 * {@code event.register(menuType, MyScreen::new)} where the constructor matches
	 * {@code MenuScreens.ScreenConstructor<M, U>} — i.e. {@code (M menu, Inventory, Component)}, exactly the
	 * common {@code Screen} constructors. This is the NeoForge counterpart to the Fabric
	 * {@code MenuScreens.register(menuType, Screen::new)} calls in {@code IndustrializationClient}.
	 */
	private void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenusNeoForge.GENERATOR.get(), GeneratorScreen::new);
		event.register(ModMenusNeoForge.MACERATOR.get(), MaceratorScreen::new);
		event.register(ModMenusNeoForge.SOLAR_PANEL.get(), SolarPanelScreen::new);
		event.register(ModMenusNeoForge.MOONLIT_SOLAR_PANEL.get(), MoonlitSolarPanelScreen::new);
		event.register(ModMenusNeoForge.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
		event.register(ModMenusNeoForge.EXTRACTOR.get(), ExtractorScreen::new);
		event.register(ModMenusNeoForge.COMPRESSOR.get(), CompressorScreen::new);
		event.register(ModMenusNeoForge.BATTERY_BOX.get(), BatteryBoxScreen::new);
		event.register(ModMenusNeoForge.TELEPORTER_STATION.get(),
				dev.alaindustrial.client.screen.TeleporterStationScreen::new);
		event.register(ModMenusNeoForge.TELEPORTER_REMOTE.get(),
				dev.alaindustrial.client.screen.TeleporterRemoteScreen::new);
		event.register(ModMenusNeoForge.DAYLIGHT_SOLAR_PANEL.get(), DaylightSolarPanelScreen::new);
		event.register(ModMenusNeoForge.GEOTHERMAL_GENERATOR.get(), GeothermalGeneratorScreen::new);
		event.register(ModMenusNeoForge.PUMP.get(), PumpScreen::new);
		event.register(ModMenusNeoForge.WATER_MILL.get(), WaterMillScreen::new);
		event.register(ModMenusNeoForge.WIND_MILL.get(), WindMillScreen::new);
		event.register(ModMenusNeoForge.HIGH_ALTITUDE_WIND_MILL.get(), dev.alaindustrial.client.screen.HighAltitudeWindMillScreen::new);
		event.register(ModMenusNeoForge.STORM_WIND_MILL.get(), dev.alaindustrial.client.screen.StormWindMillScreen::new);
		event.register(ModMenusNeoForge.IRON_CHEST.get(), dev.alaindustrial.client.screen.IronChestScreen::new);
		event.register(ModMenusNeoForge.SILVER_CHEST.get(), dev.alaindustrial.client.screen.SilverChestScreen::new);
		event.register(ModMenusNeoForge.GOLD_CHEST.get(), dev.alaindustrial.client.screen.GoldChestScreen::new);
	}

	/**
	 * Binds the iron chest's 3D model + animated-lid renderer to its BlockEntity type. Verified
	 * pattern (neoforge 26.2.0.8-beta): {@code event.registerBlockEntityRenderer(type, factory)}
	 * where {@code factory} is a {@code BlockEntityRendererProvider<T, S>}. This is the NeoForge
	 * counterpart to the Fabric {@code BlockEntityRendererRegistry.register} call.
	 */
	private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.IRON_CHEST.get(),
				ChestBlockEntityRenderer::iron);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.SILVER_CHEST.get(),
				ChestBlockEntityRenderer::silver);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.GOLD_CHEST.get(),
				ChestBlockEntityRenderer::gold);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.WATER_MILL.get(),
				WaterMillWheelBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.WIND_MILL.get(),
				WindMillRotorBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.HIGH_ALTITUDE_WIND_MILL.get(),
				WindMillRotorBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.STORM_WIND_MILL.get(),
				WindMillRotorBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.FLUID_TANK.get(),
				dev.alaindustrial.client.render.FluidTankBlockEntityRenderer::new);
		// Stock Display Frame (MOD-066): the mod's first entity renderer — NeoForge counterpart to
		// the Fabric EntityRenderers.register call in IndustrializationClient.
		event.registerEntityRenderer(
				dev.alaindustrial.registry.neoforge.ModEntitiesNeoForge.STOCK_DISPLAY_FRAME.get(),
				dev.alaindustrial.client.render.StockDisplayFrameRenderer::new);
	}

	/**
	 * Bakes the iron chest model layer (vanilla single-body chest geometry) so the renderer can
	 * resolve its {@code ModelPart} via {@code EntityModelSet#bakeLayer}. NeoForge counterpart to
	 * the Fabric {@code ModelLayerRegistry.registerModelLayer} call.
	 */
	private void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(ChestBlockEntityRenderer.IRON_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		event.registerLayerDefinition(ChestBlockEntityRenderer.SILVER_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		event.registerLayerDefinition(ChestBlockEntityRenderer.GOLD_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		event.registerLayerDefinition(WaterMillWheelBlockEntityRenderer.MODEL_LAYER,
				WaterMillWheelBlockEntityRenderer::createLayer);
	}
}
