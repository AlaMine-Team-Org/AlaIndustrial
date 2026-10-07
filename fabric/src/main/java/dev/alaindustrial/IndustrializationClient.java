package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.ClientContentManifest;
import dev.alaindustrial.client.ClientPayloadManifest;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.registry.ModAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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
		dev.alaindustrial.client.RootSoilModels.init();
		dev.alaindustrial.client.PiezoPlateModels.init();
		initClientConfig();
		registerFluidRendering();
		// MOD-248: the submerged-in-oil look. Loader-neutral (a client mixin + a vanilla fog
		// environment in common/), so NeoForge calls the very same install().
		dev.alaindustrial.client.OilFogEnvironment.install();
		registerMenuScreens();
		registerTooltips();
		registerHudAndKeys();
		registerPayloadReceivers();
		registerParticleProviders();
		registerBlockColors();
		registerClientHooks();
		registerBlockOutlines();
		registerBlockEntityRenderers();
		registerDevWindowTitle();
		// MOD-133/MOD-483: the dashboard and the skill screen read the local player's synced attachments
		// through their client caches — one loop over the shared list (MOD-708).
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def
				: ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			bindClientCache(def);
		}

		Industrialization.LOGGER.info("Industrialization client initialized.");
	}

	/**
	 * Dev-only tester hint: when {@code -Dalaindustrial.devtitle=...} is set (wired from the
	 * {@code -Pdevtitle} Gradle hook in {@code fabric/build.gradle}), stamp the tested task onto the
	 * game window title — so the tester can tell at a glance which task this client is for.
	 *
	 * <p>No-op in production: the property is never set for the shipped jar, so the release client
	 * keeps the vanilla title. Mirrors the {@code alaindustrial.guionly} dev gate.
	 */
	private void registerDevWindowTitle() {
		String tag = System.getProperty("alaindustrial.devtitle");
		if (tag == null || tag.isBlank()) {
			return;
		}
		final String title = "AlaIndustrial DEV — " + tag.trim();
		// Re-apply each client tick: vanilla rewrites the window title on world load / screen change,
		// so a one-shot set would not survive. setTitle is a cheap GLFW call and the gate above keeps
		// this off entirely in production.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.getWindow() != null) {
				client.getWindow().setTitle(title);
			}
		});
		Industrialization.LOGGER.info("Dev window title set: {}", title);
	}

	/**
	 * Registers every fluid model of the shared {@link ClientContentManifest#FLUID_MODELS} (MOD-238,
	 * MOD-706) through Fabric's {@code FluidRenderingRegistry}; NeoForge replays the same list on its
	 * {@code RegisterFluidModelsEvent}. A fluid with a flowing form registers the pair, one without (steam)
	 * the single-fluid overload.
	 */
	/** Binds one attachment's client cache: a read that never installs the default (getAttachedOrElse). */
	private static <T> void bindClientCache(ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		AttachmentType<T> type = ModAttachments.type(def);
		def.bindClientCache().accept(() -> {
			net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
			return p == null ? def.empty() : p.getAttachedOrElse(type, def.empty());
		});
	}

	private void registerFluidRendering() {
		for (ClientContentManifest.FluidModelDef def : ClientContentManifest.FLUID_MODELS) {
			net.minecraft.world.level.material.Fluid flowing = def.flowingFluid();
			if (flowing == null) {
				net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.register(
						def.stillFluid(), def.model());
			} else {
				net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.register(
						def.stillFluid(), flowing, def.model());
			}
		}
	}

	/**
	 * Initialises the client config screen state, the item tint sources and the blueprint's
	 * product item-model type (MOD-275). Both render hooks add themselves to a vanilla late-bound
	 * registry, so they only have to be in place before the first resource reload parses the models.
	 */
	private void initClientConfig() {
		AlaClientConfig.init(FabricLoader.getInstance().getConfigDir());
		dev.alaindustrial.client.render.ModItemTintSources.register();
		dev.alaindustrial.client.render.BlueprintResultItemModel.register();
	}

	/**
	 * Binds each machine {@code MenuType} to its {@code Screen} (MOD-190: from the shared manifest).
	 * The pair stays typed end to end through {@code ScreenRegistrar}, so no cast is involved (MOD-198).
	 */
	private void registerMenuScreens() {
		dev.alaindustrial.client.screen.MenuScreenManifest.ScreenRegistrar registrar =
				new dev.alaindustrial.client.screen.MenuScreenManifest.ScreenRegistrar() {
					@Override
					public <M extends net.minecraft.world.inventory.AbstractContainerMenu,
							U extends net.minecraft.client.gui.screens.Screen
								& net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void register(
							net.minecraft.world.inventory.MenuType<M> menuType,
							dev.alaindustrial.client.screen.MenuScreenManifest.ScreenFactory<M, U> screen) {
						MenuScreens.register(menuType, screen::create);
					}
				};
		for (dev.alaindustrial.client.screen.MenuScreenManifest.ScreenDef<?, ?> def
				: dev.alaindustrial.client.screen.MenuScreenManifest.SCREENS) {
			def.bindTo(registrar);
		}
	}

	/** Registers the machine hover-tooltip provider and the Battery Pouch bundle-style tooltip renderer. */
	private void registerTooltips() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
				MachineTooltips.append(stack, lines, Minecraft.getInstance().hasShiftDown()));
		// Battery Pouch bundle-style tooltip (MOD-052): map the neutral TooltipComponent to its renderer.
		net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(component ->
				component instanceof dev.alaindustrial.item.energy.PouchTooltip pouch
						? new dev.alaindustrial.client.tooltip.PouchClientTooltip(pouch)
						: null);
	}

	/**
	 * Registers the key mappings and the HUD layers from the shared {@link ClientContentManifest}
	 * (MOD-706), plus the client-tick input steps and the world-leave reset. The payloads that feed the
	 * HUD arrive through {@link #registerPayloadReceivers}.
	 */
	private void registerHudAndKeys() {
		// MOD-706: the key mappings and the HUD layers are ClientContentManifest lists, the same ones the
		// NeoForge client replays. The drawing is loader-neutral — Fabric's HudElement and NeoForge's
		// GuiLayer take the same (GuiGraphicsExtractor, DeltaTracker) pair.
		for (net.minecraft.client.KeyMapping mapping : ClientContentManifest.KEY_MAPPINGS) {
			KeyMappingHelper.registerKeyMapping(mapping);
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> ModKeyMappings.handleInput());
		// Jetpack thrust/glide (MOD-148) — player motion is client-authoritative, so the velocity
		// change lives in this end-of-tick step; the server burns the EU on its own input view.
		ClientTickEvents.END_CLIENT_TICK.register(client -> dev.alaindustrial.client.JetpackFlight.clientTick());
		// Leaving a world drops the client state that belongs to it (MOD-106 fade, MOD-513 archive record,
		// MOD-665 analyzer trace) — one shared list, the NeoForge counterpart hangs off LoggingOut.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> dev.alaindustrial.client.ClientDisconnectReset.run());
		// In list order, which is drawing order: the readouts stay legible over the teleport fade.
		for (ClientContentManifest.HudLayerDef def : ClientContentManifest.HUD_LAYERS) {
			switch (def.placement()) {
				case LAST -> HudElementRegistry.addLast(Industrialization.id(def.id()), def.renderer()::render);
			}
		}
	}

	/**
	 * Registers one {@code ClientPlayNetworking} receiver per entry of the shared
	 * {@link ClientPayloadManifest#HANDLERS} (MOD-706) — the client half of every clientbound payload;
	 * NeoForge reaches the same list from its payload handler. Each entry keeps the hand-off its
	 * hand-written receiver had: {@code QUEUED} through {@code client().execute}, {@code INLINE} straight
	 * from the receiver (Fabric already calls it on the client thread; see the manifest's class doc).
	 */
	private void registerPayloadReceivers() {
		ClientPayloadManifest.Registrar registrar = new ClientPayloadManifest.Registrar() {
			@Override
			public <T extends CustomPacketPayload> void receiver(ClientPayloadManifest.ClientHandlerDef<T> def) {
				ClientPlayNetworking.registerGlobalReceiver(def.type(), (payload, context) -> {
					if (def.dispatch() == ClientPayloadManifest.Dispatch.QUEUED) {
						context.client().execute(() -> def.receive(payload));
					} else {
						def.receive(payload);
					}
				});
			}
		};
		for (ClientPayloadManifest.ClientHandlerDef<?> def : ClientPayloadManifest.HANDLERS) {
			def.bindTo(registrar);
		}
	}

	/**
	 * Registers every particle provider of the shared {@link ClientContentManifest#PARTICLE_PROVIDERS}
	 * (MOD-085, MOD-706); NeoForge replays the same list on its {@code RegisterParticleProvidersEvent}.
	 */
	private void registerParticleProviders() {
		ClientContentManifest.ParticleRegistrar registrar = new ClientContentManifest.ParticleRegistrar() {
			@Override
			public <T extends net.minecraft.core.particles.ParticleOptions> void register(
					net.minecraft.core.particles.ParticleType<T> type,
					java.util.function.Function<net.minecraft.client.particle.SpriteSet,
							net.minecraft.client.particle.ParticleProvider<T>> factory) {
				net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(
						type, factory::apply);
			}
		};
		for (ClientContentManifest.ParticleProviderDef<?> def : ClientContentManifest.PARTICLE_PROVIDERS) {
			def.bindTo(registrar);
		}
	}

	/**
	 * Registers the mod's block tint sources from the shared {@link ClientContentManifest} (MOD-403) —
	 * the incubator dome takes the colour of the glass it was built from (MOD-118), the fluid pipe the
	 * colour of what flows through it.
	 *
	 * <p>Verified against fabric-rendering-v1 25.2.0+2b0d8a229e (the module bundled in fabric-api
	 * {@code 0.153.0+26.2}): {@code BlockColorRegistry.register(List<BlockTintSource>, Block...)},
	 * where the list index is the model's {@code tintindex}. Note that 26.2 has no {@code BlockColor}
	 * / {@code ColorProviderRegistry} any more — a tint layer is a {@code BlockTintSource} and the
	 * in-world hook is {@code colorInWorld(state, level, pos)}, with no tint-index argument.
	 */
	private void registerBlockColors() {
		for (ClientContentManifest.BlockTintDef def : ClientContentManifest.BLOCK_TINTS) {
			net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(
					def.sources(), def.block().get());
		}
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

	/**
	 * Replays the shared {@link ClientContentManifest#BLOCK_OUTLINES} (MOD-778): once vanilla has extracted
	 * the hovered block's outline, the first entry that answers swaps the drawn shape and keeps the rest of
	 * the extracted state — position, translucency, contrast — so the outline is styled exactly as vanilla
	 * draws it. NeoForge does the same on its {@code ExtractBlockOutlineRenderStateEvent}.
	 */
	private void registerBlockOutlines() {
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION
				.register((context, hit) -> {
					net.minecraft.client.renderer.state.level.LevelRenderState levelState = context.levelState();
					net.minecraft.client.renderer.state.level.BlockOutlineRenderState outline =
							levelState.blockOutlineRenderState;
					if (outline == null) {
						return;
					}
					net.minecraft.world.level.block.state.BlockState state =
							context.level().getBlockState(outline.pos());
					for (ClientContentManifest.BlockOutline def : ClientContentManifest.BLOCK_OUTLINES) {
						net.minecraft.world.phys.shapes.VoxelShape shape =
								def.shape(context.level(), outline.pos(), state);
						if (shape != null) {
							levelState.blockOutlineRenderState =
									new net.minecraft.client.renderer.state.level.BlockOutlineRenderState(
											outline.pos(), outline.isTranslucent(), outline.highContrast(), shape,
											outline.collisionShape(), outline.occlusionShape(),
											outline.interactionShape());
							return;
						}
					}
				});
	}

	/**
	 * Registers the block-entity renderers and bakes their model layers, both replayed from the shared
	 * {@link ClientContentManifest} (MOD-403) — the same set the NeoForge client plays, so a renderer can
	 * no longer exist on one loader only. The pair stays typed end to end through {@code RendererRegistrar},
	 * so no cast is involved.
	 *
	 * <p>The entity renderer below stays here: its type handle is loader-specific ({@code ModEntities} vs
	 * {@code ModEntitiesNeoForge}) and the neutral {@code ModContent} slot is a wildcard, so there is
	 * nothing typed to share for one registration.
	 */
	private void registerBlockEntityRenderers() {
		ClientContentManifest.RendererRegistrar registrar = new ClientContentManifest.RendererRegistrar() {
			@Override
			public <T extends net.minecraft.world.level.block.entity.BlockEntity,
					S extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState>
					void register(net.minecraft.world.level.block.entity.BlockEntityType<T> type,
							net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider<T, S> provider) {
				// MOD-498 — vanilla's own registry, not Fabric's BlockEntityRendererRegistry wrapper,
				// which Fabric has deprecated. `BlockEntityRenderers.register` is private in vanilla;
				// fabric-transitive-access-wideners-v1 opens it (`transitive-accessible method
				// net/minecraft/client/renderer/blockentity/BlockEntityRenderers register`, line 42 of
				// fabric-transitive-access-wideners-v1.classtweaker). Line 45 of that same file widens
				// EntityRenderers#register, which the entity renderer further down already ships through —
				// a different entry, so it is strong evidence rather than proof that this one resolves at
				// runtime; the client gametest lane booting is what actually exercises it.
				// Not a pure rename: the wrapper buffered registrations in its own map and let a mixin on
				// BlockEntityRenderers.<clinit> flush them, so calling vanilla directly forces that clinit
				// here, during client init, instead of later. The resulting provider map — and therefore
				// what is rendered — is the same either way; what moves is when those classes load.
				// The witness pins both variables to this method's own parameters rather than letting
				// the render state be inferred from the `BlockEntityType<? extends T>` bound.
				net.minecraft.client.renderer.blockentity.BlockEntityRenderers.<T, S>register(type, provider);
			}
		};
		for (ClientContentManifest.BlockEntityRendererDef<?, ?> def
				: ClientContentManifest.BLOCK_ENTITY_RENDERERS) {
			def.bindTo(registrar);
		}
		for (ClientContentManifest.ModelLayerDef def : ClientContentManifest.MODEL_LAYERS) {
			ModelLayerRegistry.registerModelLayer(def.location(), def.definition()::get);
		}

		// Stock Display Frame (MOD-066): the mod's first entity renderer. Vanilla EntityRenderers.register
		// is the path Fabric's own docs recommend (their EntityRendererRegistry is a thin legacy wrapper).
		net.minecraft.client.renderer.entity.EntityRenderers.register(
				dev.alaindustrial.registry.ModEntities.STOCK_DISPLAY_FRAME,
				dev.alaindustrial.client.render.StockDisplayFrameRenderer::new);
	}
}
