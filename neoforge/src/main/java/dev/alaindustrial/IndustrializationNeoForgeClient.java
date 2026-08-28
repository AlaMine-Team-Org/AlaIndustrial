package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.ClientContentManifest;
import dev.alaindustrial.client.screen.AlaConfigScreen;
import dev.alaindustrial.client.hud.EnergyPackHud;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.client.neoforge.NeoForgeCableGhost;
import dev.alaindustrial.client.neoforge.NeoForgeNetworkVisualization;
import net.minecraft.client.Minecraft;
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

	/**
	 * Initialises the client config screen state, the fluid-tank item tint source, the blueprint's
	 * product item-model type (MOD-275) and the config-screen factory. The two render hooks add
	 * themselves to vanilla late-bound registries, so the call sites are identical on both loaders.
	 */
	private void initClientConfig(ModContainer container) {
		AlaClientConfig.init(FMLPaths.CONFIGDIR.get());
		dev.alaindustrial.client.render.FluidTankItemTintSource.register();
		dev.alaindustrial.client.render.BlueprintResultItemModel.register();
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
		// MOD-248: the submerged-in-oil look (screen overlay + near-black fog). Both halves live in
		// common/ behind a client mixin, because Fabric has no fog/screen-effect API and one
		// implementation must serve both loaders. NeoForge's IClientFluidTypeExtensions overlay hook
		// IS invoked since 26.2.0.67 but is deliberately left unregistered — see OilScreenEffects.
		dev.alaindustrial.client.OilFogEnvironment.install();
		// MOD-238: oil's fluid model — the vanilla FluidStateModelSet hard-codes water/lava only, so a
		// custom fluid registers its own FluidModel.Unbaked. NeoForge counterpart to the Fabric
		// FluidRenderingRegistry call in IndustrializationClient; one model shared by still + flowing,
		// overlay/tint null exactly like vanilla lava (the textures carry their colour).
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) -> {
			event.register(fluidModel("oil"),
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.OIL,
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.FLOWING_OIL);
			// MOD-251: the two distillation fractions, same registration shape as oil.
			event.register(fluidModel("diesel"),
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.DIESEL,
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.FLOWING_DIESEL);
			event.register(fluidModel("fuel_oil"),
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.FUEL_OIL,
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.FLOWING_FUEL_OIL);
			// MOD-146/MOD-525: the organic chain's two fluids, same registration shape.
			event.register(fluidModel("biofuel"),
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.BIOFUEL,
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.FLOWING_BIOFUEL);
			event.register(fluidModel("nutrient_solution"),
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.NUTRIENT_SOLUTION,
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.FLOWING_NUTRIENT_SOLUTION);
			// MOD-468: steam, through the single-fluid overload — it has no flowing form, and with no
			// model every tank and pipe holding it would draw the missing-texture sprite.
			event.register(fluidModel("steam"),
					dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.STEAM);
		});
		// MOD-085: green flame particle provider for the Enriched Uranium Torch. registerSpriteSet =
		// json-backed particle (assets/alaindustrial/particles/enriched_uranium_flame.json); reuses the
		// vanilla FlameParticle provider (like soul_fire_flame), colour comes from the particle texture.
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) -> {
			event.registerSpriteSet(dev.alaindustrial.registry.ModParticles.ENRICHED_URANIUM_FLAME,
					net.minecraft.client.particle.FlameParticle.Provider::new);
			event.registerSpriteSet(dev.alaindustrial.registry.ModParticles.NUTRIENT_SPRAY,
					dev.alaindustrial.client.particle.NutrientSprayParticle.Provider::new);
		});
		// MOD-118: the incubator dome takes the colour of the glass it was built from — the mod's
		// first block colour provider. Verified pattern (neoforge-26.2.0.67):
		// RegisterColorHandlersEvent.BlockTintSources#register(List<BlockTintSource>, Block...), the
		// same signature as the Fabric BlockColorRegistry call in IndustrializationClient, with the
		// list index being the model's tintindex. 26.2 dropped BlockColor: a tint layer is a
		// BlockTintSource and the in-world hook is colorInWorld(state, level, pos).
		// MOD-403: the pairs themselves come from the shared ClientContentManifest, so this list cannot
		// drift from the Fabric one any more; only the event call stays loader-specific.
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.BlockTintSources event) -> {
			for (ClientContentManifest.BlockTintDef def : ClientContentManifest.BLOCK_TINTS) {
				event.register(def.sources(), def.block().get());
			}
		});
		// Battery Pouch bundle-style tooltip (MOD-052) — NeoForge counterpart to the Fabric
		// ClientTooltipComponentCallback mapping in IndustrializationClient.
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) ->
				event.register(dev.alaindustrial.item.energy.PouchTooltip.class,
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
			event.register(ModKeyMappings.OPEN_PROFILE);
			event.register(ModKeyMappings.TOGGLE_STEP_ASSIST); // MOD-133 player dashboard
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
		// Jetpack thrust/glide (MOD-148) — counterpart of the Fabric END_CLIENT_TICK registration.
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> dev.alaindustrial.client.JetpackFlight.clientTick());
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

	/** One fluid's {@code FluidModel.Unbaked} built from {@code block/<name>_still|_flow} (MOD-251). */
	private static net.minecraft.client.renderer.block.FluidModel.Unbaked fluidModel(String name) {
		return new net.minecraft.client.renderer.block.FluidModel.Unbaked(
				new net.minecraft.client.resources.model.sprite.Material(
						Industrialization.id("block/" + name + "_still")),
				new net.minecraft.client.resources.model.sprite.Material(
						Industrialization.id("block/" + name + "_flow")),
				null, null);
	}

	/**
	 * Binds each machine {@code MenuType} to its {@code Screen} (MOD-190: from the shared manifest).
	 * Verified pattern (neoforge-26.2.0.67): {@code event.register(menuType, screen::create)} where
	 * the screen constructor matches {@code MenuScreens.ScreenConstructor<M, U>} — i.e.
	 * {@code (M menu, Inventory, Component)}, exactly the common {@code Screen} constructors. The pair
	 * stays typed end to end through {@code ScreenRegistrar}, so no cast is involved (MOD-198). This is
	 * the NeoForge counterpart to {@code registerMenuScreens} in {@code IndustrializationClient}.
	 */
	private void registerMenuScreens(RegisterMenuScreensEvent event) {
		dev.alaindustrial.client.screen.MenuScreenManifest.ScreenRegistrar registrar =
				new dev.alaindustrial.client.screen.MenuScreenManifest.ScreenRegistrar() {
					@Override
					public <M extends net.minecraft.world.inventory.AbstractContainerMenu,
							U extends net.minecraft.client.gui.screens.Screen
								& net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void register(
							net.minecraft.world.inventory.MenuType<M> menuType,
							dev.alaindustrial.client.screen.MenuScreenManifest.ScreenFactory<M, U> screen) {
						event.register(menuType, screen::create);
					}
				};
		for (dev.alaindustrial.client.screen.MenuScreenManifest.ScreenDef<?, ?> def
				: dev.alaindustrial.client.screen.MenuScreenManifest.SCREENS) {
			def.bindTo(registrar);
		}
	}

	/**
	 * Binds every block-entity renderer from the shared {@link ClientContentManifest} (MOD-403) — the same
	 * list the Fabric client plays, so a renderer can no longer exist on one loader only. That mattered
	 * most here: NeoForge has no client test lane, and a line missing from this method used to be noticed
	 * first by a player looking at an unrendered block.
	 *
	 * <p>Verified pattern (neoforge 26.2.0.67): {@code event.registerBlockEntityRenderer(type,
	 * factory)} where {@code factory} is a {@code BlockEntityRendererProvider<T, S>}. The pair stays typed
	 * end to end through {@code RendererRegistrar}, so no cast is involved.
	 *
	 * <p>The entity renderer below stays here: its type handle is loader-specific
	 * ({@code ModEntitiesNeoForge}) and the neutral {@code ModContent} slot is a wildcard, so there is
	 * nothing typed to share for one registration.
	 */
	private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		ClientContentManifest.RendererRegistrar registrar = new ClientContentManifest.RendererRegistrar() {
			@Override
			public <T extends net.minecraft.world.level.block.entity.BlockEntity,
					S extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState>
					void register(net.minecraft.world.level.block.entity.BlockEntityType<T> type,
							net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider<T, S> provider) {
				// NeoForge declares registerBlockEntityRenderer(BlockEntityType<? extends T>,
				// BlockEntityRendererProvider<T, S>); the witness pins both to this method's own
				// parameters rather than letting them be inferred through the wildcard.
				event.<T, S>registerBlockEntityRenderer(type, provider);
			}
		};
		for (ClientContentManifest.BlockEntityRendererDef<?, ?> def
				: ClientContentManifest.BLOCK_ENTITY_RENDERERS) {
			def.bindTo(registrar);
		}
		// Stock Display Frame (MOD-066): the mod's first entity renderer — NeoForge counterpart to
		// the Fabric EntityRenderers.register call in IndustrializationClient.
		event.registerEntityRenderer(
				dev.alaindustrial.registry.neoforge.ModEntitiesNeoForge.STOCK_DISPLAY_FRAME.get(),
				dev.alaindustrial.client.render.StockDisplayFrameRenderer::new);
	}

	/**
	 * Bakes every model layer from the shared {@link ClientContentManifest} (MOD-403) so the renderers can
	 * resolve their {@code ModelPart}s via {@code EntityModelSet#bakeLayer}. NeoForge counterpart to the
	 * Fabric {@code ModelLayerRegistry.registerModelLayer} loop; both take the same
	 * {@code (ModelLayerLocation, () -> LayerDefinition)} pair, which is why the manifest needs no
	 * registrar interface for this one.
	 */
	private void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		for (ClientContentManifest.ModelLayerDef def : ClientContentManifest.MODEL_LAYERS) {
			event.registerLayerDefinition(def.location(), def.definition());
		}
	}
}
