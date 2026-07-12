package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.AlaConfigScreen;
import dev.alaindustrial.client.BatteryBoxScreen;
import dev.alaindustrial.client.CompressorScreen;
import dev.alaindustrial.client.DaylightSolarPanelScreen;
import dev.alaindustrial.client.ElectricFurnaceScreen;
import dev.alaindustrial.client.ExtractorScreen;
import dev.alaindustrial.client.GeneratorScreen;
import dev.alaindustrial.client.GeothermalGeneratorScreen;
import dev.alaindustrial.client.IronChestBlockEntityRenderer;
import dev.alaindustrial.client.MaceratorScreen;
import dev.alaindustrial.client.PumpScreen;
import dev.alaindustrial.client.MachineTooltips;
import dev.alaindustrial.client.MoonlitSolarPanelScreen;
import dev.alaindustrial.client.SolarPanelScreen;
import dev.alaindustrial.client.WaterMillScreen;
import dev.alaindustrial.client.WaterMillWheelBlockEntityRenderer;
import dev.alaindustrial.client.WindMillScreen;
import dev.alaindustrial.client.WindMillRotorBlockEntityRenderer;
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

	public IndustrializationNeoForgeClient(IEventBus modBus, ModContainer container) {
		AlaClientConfig.init(FMLPaths.CONFIGDIR.get());
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(modContainer, parent) -> new AlaConfigScreen(parent));
		modBus.addListener(this::registerMenuScreens);
		// Battery Pouch bundle-style tooltip (MOD-052) — NeoForge counterpart to the Fabric
		// ClientTooltipComponentCallback mapping in IndustrializationClient.
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) ->
				event.register(dev.alaindustrial.item.PouchTooltip.class,
						dev.alaindustrial.client.PouchClientTooltip::new));
		// Iron chest: 3D model + animated lid. Register the BlockEntityRenderer + bake the chest
		// model layer (vanilla single-body chest geometry), the NeoForge counterpart to the Fabric
		// BlockEntityRendererRegistry + ModelLayerRegistry calls in IndustrializationClient.
		modBus.addListener(this::registerRenderers);
		modBus.addListener(this::registerLayerDefinitions);
		// Install the client-side machine-hum manager (looping ambient sound). Counterpart to the Fabric
		// IndustrializationClient call; this @Mod class is dist=CLIENT, so it runs only on the physical client.
		dev.alaindustrial.client.sound.MachineHumClientHook.register();
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
		Industrialization.LOGGER.info("Industrialization (NeoForge client) initialized.");
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
		event.register(ModMenusNeoForge.DAYLIGHT_SOLAR_PANEL.get(), DaylightSolarPanelScreen::new);
		event.register(ModMenusNeoForge.GEOTHERMAL_GENERATOR.get(), GeothermalGeneratorScreen::new);
		event.register(ModMenusNeoForge.PUMP.get(), PumpScreen::new);
		event.register(ModMenusNeoForge.WATER_MILL.get(), WaterMillScreen::new);
		event.register(ModMenusNeoForge.WIND_MILL.get(), WindMillScreen::new);
		event.register(ModMenusNeoForge.HIGH_ALTITUDE_WIND_MILL.get(), dev.alaindustrial.client.HighAltitudeWindMillScreen::new);
		event.register(ModMenusNeoForge.STORM_WIND_MILL.get(), dev.alaindustrial.client.StormWindMillScreen::new);
		event.register(ModMenusNeoForge.IRON_CHEST.get(), dev.alaindustrial.client.IronChestScreen::new);
	}

	/**
	 * Binds the iron chest's 3D model + animated-lid renderer to its BlockEntity type. Verified
	 * pattern (neoforge 26.2.0.8-beta): {@code event.registerBlockEntityRenderer(type, factory)}
	 * where {@code factory} is a {@code BlockEntityRendererProvider<T, S>}. This is the NeoForge
	 * counterpart to the Fabric {@code BlockEntityRendererRegistry.register} call.
	 */
	private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.IRON_CHEST.get(),
				IronChestBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.WATER_MILL.get(),
				WaterMillWheelBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.WIND_MILL.get(),
				WindMillRotorBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.HIGH_ALTITUDE_WIND_MILL.get(),
				WindMillRotorBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntitiesNeoForge.STORM_WIND_MILL.get(),
				WindMillRotorBlockEntityRenderer::new);
	}

	/**
	 * Bakes the iron chest model layer (vanilla single-body chest geometry) so the renderer can
	 * resolve its {@code ModelPart} via {@code EntityModelSet#bakeLayer}. NeoForge counterpart to
	 * the Fabric {@code ModelLayerRegistry.registerModelLayer} call.
	 */
	private void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(IronChestBlockEntityRenderer.IRON_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
	}
}
