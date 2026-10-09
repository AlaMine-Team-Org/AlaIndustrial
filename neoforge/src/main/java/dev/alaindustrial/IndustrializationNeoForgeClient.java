package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.ClientContentManifest;
import dev.alaindustrial.client.screen.AlaConfigScreen;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.client.neoforge.NeoForgeCableGhost;
import dev.alaindustrial.client.neoforge.NeoForgeNetworkVisualization;
import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.registry.neoforge.ModAttachmentsNeoForge;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModContainer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
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
		// MOD-133/MOD-483: the dashboard and the skill screen read the local player's synced attachments
		// through their client caches — one loop over the shared list (MOD-708).
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def
				: ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			bindClientCache(def);
		}

		Industrialization.LOGGER.info("Industrialization (NeoForge client) initialized.");
	}

	/**
	 * Initialises the client config screen state, the item tint sources, the blueprint's
	 * product item-model type (MOD-275) and the config-screen factory. The two render hooks add
	 * themselves to vanilla late-bound registries, so the call sites are identical on both loaders.
	 */
	private void initClientConfig(ModContainer container) {
		AlaClientConfig.init(FMLPaths.CONFIGDIR.get());
		dev.alaindustrial.client.render.ModItemTintSources.register();
		dev.alaindustrial.client.render.BlueprintResultItemModel.register();
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(modContainer, parent) -> new AlaConfigScreen(parent));
	}

	/**
	 * Registers every client-side listener in the original order. The order interleaves mod-bus
	 * registrations (menu screens, particle providers, tooltip factories, renderers, layer definitions,
	 * key mappings, GUI layers) with game-bus registrations (item tooltips, world overlays, client tick,
	 * disconnect cleanup) and the two static client hooks (machine hum, tooltip keys). MOD-137 kept it as
	 * one method to preserve that order; MOD-706 cut it into three consecutive slices called in that same
	 * order, so no registration moved across another.
	 */
	private void registerClientEvents(IEventBus modBus) {
		registerContentListeners(modBus);
		registerClientHooks();
		registerInputAndHud(modBus);
	}

	/**
	 * The first slice of {@link #registerClientEvents}: menu screens, root-soil models, the oil fog, fluid
	 * models and particles, block tints, the pouch tooltip, block-entity renderers and model layers.
	 */
	private void registerContentListeners(IEventBus modBus) {
		modBus.addListener(this::registerMenuScreens);
		dev.alaindustrial.client.neoforge.NeoForgeRootSoilModels.init();
		modBus.addListener(dev.alaindustrial.client.neoforge.NeoForgeRootSoilModels::onBake);
		modBus.addListener(dev.alaindustrial.client.neoforge.NeoForgePiezoPlateModels::onBake);
		// MOD-248: the submerged-in-oil look (screen overlay + near-black fog). Both halves live in
		// common/ behind a client mixin, because Fabric has no fog/screen-effect API and one
		// implementation must serve both loaders. NeoForge's IClientFluidTypeExtensions overlay hook
		// IS invoked since 26.2.0.67 but is deliberately left unregistered — see OilScreenEffects.
		dev.alaindustrial.client.OilFogEnvironment.install();
		registerFluidModelsAndParticles(modBus);
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
	}

	/**
	 * Fluid models and particle providers from the shared {@link ClientContentManifest} (MOD-238, MOD-706) —
	 * the same lists the Fabric client replays. A fluid model is registered on the fluids the registry holds
	 * under the entry's ids; the {@code RegisterFluidModelsEvent} fires after every fluid is registered.
	 */
	private void registerFluidModelsAndParticles(IEventBus modBus) {
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) -> {
			for (ClientContentManifest.FluidModelDef def : ClientContentManifest.FLUID_MODELS) {
				net.minecraft.world.level.material.Fluid flowing = def.flowingFluid();
				if (flowing == null) {
					event.register(def.model(), def.stillFluid());
				} else {
					event.register(def.model(), def.stillFluid(), flowing);
				}
			}
		});
		modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) -> {
			ClientContentManifest.ParticleRegistrar registrar = new ClientContentManifest.ParticleRegistrar() {
				@Override
				public <T extends net.minecraft.core.particles.ParticleOptions> void register(
						net.minecraft.core.particles.ParticleType<T> type,
						java.util.function.Function<net.minecraft.client.particle.SpriteSet,
								net.minecraft.client.particle.ParticleProvider<T>> factory) {
					event.registerSpriteSet(type, factory::apply);
				}
			};
			for (ClientContentManifest.ParticleProviderDef<?> def : ClientContentManifest.PARTICLE_PROVIDERS) {
				def.bindTo(registrar);
			}
		});
	}

	/** The second slice of {@link #registerClientEvents}: client hooks, item tooltips and world overlays. */
	private void registerClientHooks() {
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
		// MOD-778: the shared block outlines. LOWEST so every other listener has had its say (and added its
		// custom renderers) before the outline is decided.
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, IndustrializationNeoForgeClient::replaceBlockOutline);
	}

	/**
	 * Replays the shared {@link ClientContentManifest#BLOCK_OUTLINES} (MOD-778): the first entry that answers
	 * for the hovered block becomes the extracted outline's shape. The event fires before vanilla builds the
	 * render state, and cancelling it leaves the field to whoever set it — so the state is built here with
	 * the event's own translucency, contrast and custom renderers, and vanilla draws it with its usual
	 * styling. The Fabric client swaps the shape of the extracted state the same way.
	 */
	private static void replaceBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
		for (ClientContentManifest.BlockOutline def : ClientContentManifest.BLOCK_OUTLINES) {
			net.minecraft.world.phys.shapes.VoxelShape shape =
					def.shape(event.getLevel(), event.getBlockPos(), event.getBlockState());
			if (shape != null) {
				event.getLevelRenderState().blockOutlineRenderState =
						new net.minecraft.client.renderer.state.level.BlockOutlineRenderState(event.getBlockPos(),
								event.isInTranslucentPass(), event.isHighContrast(), shape, event.getCustomRenderers());
				event.setCanceled(true);
				return;
			}
		}
	}

	/**
	 * The last slice of {@link #registerClientEvents}: key mappings and HUD layers (shared lists, MOD-706),
	 * the client-tick input steps, the profile button and the world-leave reset.
	 */
	private void registerInputAndHud(IEventBus modBus) {
		// MOD-706: the key mappings and the HUD layers are ClientContentManifest lists, the same ones the Fabric
		// client replays. Only the mappings are registered here, NOT their category: ModKeyMappings builds it
		// with the vanilla KeyMapping.Category.register, which already appends it to the sort order on both
		// loaders; event.registerCategory as well would list it twice on NeoForge.
		modBus.addListener((RegisterKeyMappingsEvent event) -> {
			for (net.minecraft.client.KeyMapping mapping : ClientContentManifest.KEY_MAPPINGS) {
				event.register(mapping);
			}
		});
		// In list order, which is drawing order: the readouts stay legible over the teleport fade. The
		// drawing is loader-neutral — NeoForge's GuiLayer and Fabric's HudElement take the same pair.
		modBus.addListener((RegisterGuiLayersEvent event) -> {
			for (ClientContentManifest.HudLayerDef def : ClientContentManifest.HUD_LAYERS) {
				switch (def.placement()) {
					case LAST -> event.registerAboveAll(Industrialization.id(def.id()), def.renderer()::render);
				}
			}
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
		// Leaving a world drops the client state that belongs to it (MOD-106 fade, MOD-513 archive record,
		// MOD-665 analyzer trace) — one shared list; the Fabric counterpart hangs off
		// ClientPlayConnectionEvents.DISCONNECT.
		NeoForge.EVENT_BUS.addListener(
				(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
						dev.alaindustrial.client.ClientDisconnectReset.run());
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
		// Chest boats (MOD-785) — counterpart to the Fabric registration in IndustrializationClient.
		for (dev.alaindustrial.entity.ChestBoatVariants.Variant variant
				: dev.alaindustrial.entity.ChestBoatVariants.ALL) {
			event.registerEntityRenderer(
					dev.alaindustrial.registry.neoforge.ModEntitiesNeoForge.CHEST_BOATS.get(variant.id()).get(),
					context -> dev.alaindustrial.client.render.ModChestBoatRenderer.create(context, variant));
		}
		// The mobs (MOD-767), replayed from the shared ClientContentManifest.MOB_RENDERERS.
		ClientContentManifest.EntityRendererRegistrar mobs = new ClientContentManifest.EntityRendererRegistrar() {
			@Override
			public <T extends net.minecraft.world.entity.Entity> void register(
					net.minecraft.world.entity.EntityType<T> type,
					net.minecraft.client.renderer.entity.EntityRendererProvider<T> provider) {
				event.registerEntityRenderer(type, provider);
			}
		};
		for (ClientContentManifest.MobRendererDef<?> def : ClientContentManifest.MOB_RENDERERS) {
			def.bindTo(mobs);
		}
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

	/** Binds one attachment's client cache; the client reads its synced copy with {@code getData}, as before. */
	private static <T> void bindClientCache(ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		var type = ModAttachmentsNeoForge.holder(def);
		def.bindClientCache().accept(() -> {
			net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
			return p == null ? def.empty() : p.getData(type);
		});
	}
}
