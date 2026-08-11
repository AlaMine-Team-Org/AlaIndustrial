package dev.alaindustrial;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.screen.CompressorScreen;
import dev.alaindustrial.client.screen.ElectricFurnaceScreen;
import dev.alaindustrial.client.hud.EnergyPackHud;
import dev.alaindustrial.client.render.ChestBlockEntityRenderer;
import dev.alaindustrial.client.tooltip.MachineTooltips;
import dev.alaindustrial.client.ModKeyMappings;
import dev.alaindustrial.client.screen.SolarPanelScreen;
import dev.alaindustrial.client.render.GardenDroneBlockEntityRenderer;
import dev.alaindustrial.client.render.EnergyCondenserBlockEntityRenderer;
import dev.alaindustrial.client.render.WaterMillWheelBlockEntityRenderer;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
import dev.alaindustrial.registry.ModBlockEntities;
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
		registerFluidRendering();
		// MOD-248: the submerged-in-oil look. Loader-neutral (a client mixin + a vanilla fog
		// environment in common/), so NeoForge calls the very same install().
		dev.alaindustrial.client.OilFogEnvironment.install();
		registerMenuScreens();
		registerTooltips();
		registerHudAndKeys();
		registerParticleProviders();
		registerBlockColors();
		registerClientHooks();
		registerBlockEntityRenderers();
		registerDevWindowTitle();
		// MOD-133: client dashboard reads the local player's synced stats attachment through the seam.
		dev.alaindustrial.stats.PlayerStatsClientCache.bind(() -> {
			net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
			return p == null ? dev.alaindustrial.stats.PlayerModStats.EMPTY
					: p.getAttachedOrElse(dev.alaindustrial.stats.fabric.FabricPlayerStats.TYPE,
							dev.alaindustrial.stats.PlayerModStats.EMPTY);
		});

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
	 * Registers oil's fluid model (MOD-238). The vanilla {@code FluidStateModelSet} hard-codes
	 * water/lava only, so a custom fluid supplies its own {@code FluidModel.Unbaked} per loader —
	 * here through Fabric's {@code FluidRenderingRegistry}; NeoForge uses
	 * {@code RegisterFluidModelsEvent}. Overlay and tint are {@code null} exactly like vanilla lava:
	 * the oil textures carry their colour themselves.
	 */
	private void registerFluidRendering() {
		registerFluidModel(dev.alaindustrial.registry.ModFluids.OIL,
				dev.alaindustrial.registry.ModFluids.FLOWING_OIL, "oil");
		// MOD-251: the two distillation fractions, same registration shape as oil.
		registerFluidModel(dev.alaindustrial.registry.ModFluids.DIESEL,
				dev.alaindustrial.registry.ModFluids.FLOWING_DIESEL, "diesel");
		registerFluidModel(dev.alaindustrial.registry.ModFluids.FUEL_OIL,
				dev.alaindustrial.registry.ModFluids.FLOWING_FUEL_OIL, "fuel_oil");
	}

	/** One still+flowing pair → its {@code FluidModel.Unbaked} built from {@code block/<name>_still|_flow}. */
	private static void registerFluidModel(net.minecraft.world.level.material.FlowingFluid still,
			net.minecraft.world.level.material.FlowingFluid flowing, String name) {
		net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.register(still, flowing,
				new net.minecraft.client.renderer.block.FluidModel.Unbaked(
						new net.minecraft.client.resources.model.sprite.Material(
								Industrialization.id("block/" + name + "_still")),
						new net.minecraft.client.resources.model.sprite.Material(
								Industrialization.id("block/" + name + "_flow")),
						null, null));
	}

	/**
	 * Initialises the client config screen state, the fluid-tank item tint source and the blueprint's
	 * product item-model type (MOD-275). Both render hooks add themselves to a vanilla late-bound
	 * registry, so they only have to be in place before the first resource reload parses the models.
	 */
	private void initClientConfig() {
		AlaClientConfig.init(FabricLoader.getInstance().getConfigDir());
		dev.alaindustrial.client.render.FluidTankItemTintSource.register();
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
	 * Registers the HUD elements (teleport fade, energy-pack + drill charge readouts), their key
	 * mappings, and the client-side teleport payload receivers that feed them.
	 */
	private void registerHudAndKeys() {
		// Energy Pack charge readout (MOD-065): the mod's first HUD element and first key mapping.
		// The drawing itself is loader-neutral (EnergyPackHud) — Fabric's HudElement and NeoForge's
		// GuiLayer take the same (GuiGraphicsExtractor, DeltaTracker) pair.
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.TOGGLE_ENERGY_HUD);
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.TOGGLE_DRILL_HUD);
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.OPEN_PROFILE);
		KeyMappingHelper.registerKeyMapping(ModKeyMappings.TOGGLE_STEP_ASSIST); // MOD-133 player dashboard
		ClientTickEvents.END_CLIENT_TICK.register(client -> ModKeyMappings.handleInput());
		// Jetpack thrust/glide (MOD-148) — player motion is client-authoritative, so the velocity
		// change lives in this end-of-tick step; the server burns the EU on its own input view.
		ClientTickEvents.END_CLIENT_TICK.register(client -> dev.alaindustrial.client.JetpackFlight.clientTick());
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

	/**
	 * Registers the mod's block tint sources — the incubator dome takes the colour of the glass it
	 * was built from (MOD-118), the mod's first block colour provider.
	 *
	 * <p>Verified against fabric-rendering-v1 25.2.0+2b0d8a229e (the module bundled in fabric-api
	 * {@code 0.153.0+26.2}): {@code BlockColorRegistry.register(List<BlockTintSource>, Block...)},
	 * where the list index is the model's {@code tintindex}. Note that 26.2 has no {@code BlockColor}
	 * / {@code ColorProviderRegistry} any more — a tint layer is a {@code BlockTintSource} and the
	 * in-world hook is {@code colorInWorld(state, level, pos)}, with no tint-index argument.
	 */
	private void registerBlockColors() {
		net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(
				java.util.List.of(dev.alaindustrial.client.render.IncubatorDomeTint.INSTANCE),
				dev.alaindustrial.registry.ModContent.INCUBATOR_DOME.get());
		net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(
				java.util.List.of(dev.alaindustrial.client.render.FluidPipeTint.INSTANCE),
				dev.alaindustrial.registry.ModContent.FLUID_PIPE.get());
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
		// MOD-391: the double-chest halves — 15-wide vanilla left/right bodies, per tier.
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.IRON_CHEST_LEFT_LAYER,
				ChestModel::createDoubleBodyLeftLayer);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.IRON_CHEST_RIGHT_LAYER,
				ChestModel::createDoubleBodyRightLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.SILVER_CHEST, ChestBlockEntityRenderer::silver);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.SILVER_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.SILVER_CHEST_LEFT_LAYER,
				ChestModel::createDoubleBodyLeftLayer);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.SILVER_CHEST_RIGHT_LAYER,
				ChestModel::createDoubleBodyRightLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.GOLD_CHEST, ChestBlockEntityRenderer::gold);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.GOLD_CHEST_LAYER,
				ChestModel::createSingleBodyLayer);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.GOLD_CHEST_LEFT_LAYER,
				ChestModel::createDoubleBodyLeftLayer);
		ModelLayerRegistry.registerModelLayer(ChestBlockEntityRenderer.GOLD_CHEST_RIGHT_LAYER,
				ChestModel::createDoubleBodyRightLayer);
		ModelLayerRegistry.registerModelLayer(WaterMillWheelBlockEntityRenderer.MODEL_LAYER,
				WaterMillWheelBlockEntityRenderer::createLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.WATER_MILL, WaterMillWheelBlockEntityRenderer::new);
		// MOD-393: the orb inside the condenser frame — its speed and glow are the block's gauge.
		BlockEntityRendererRegistry.register(ModBlockEntities.ENERGY_CONDENSER,
				EnergyCondenserBlockEntityRenderer::new);
		// Garden Drone (MOD-277): the drone is geometry this renderer places above its station, not an entity.
		ModelLayerRegistry.registerModelLayer(GardenDroneBlockEntityRenderer.MODEL_LAYER,
				GardenDroneBlockEntityRenderer::createLayer);
		BlockEntityRendererRegistry.register(ModBlockEntities.GARDEN_DRONE_STATION,
				GardenDroneBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.HIGH_ALTITUDE_WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.STORM_WIND_MILL, WindMillRotorBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.FLUID_TANK,
				dev.alaindustrial.client.render.FluidTankBlockEntityRenderer::new);
		// Incubator (MOD-118): bound to the base, draws into the dome chamber above it.
		BlockEntityRendererRegistry.register(ModBlockEntities.INCUBATOR,
				dev.alaindustrial.client.render.IncubatorBlockEntityRenderer::new);
		// Insulating stand under a bare cable (MOD-279). All cable grades share one BlockEntityType, so
		// this single registration covers every grade — and it is the first renderer bound to that type,
		// so nothing is being displaced.
		BlockEntityRendererRegistry.register(ModBlockEntities.COPPER_CABLE,
				dev.alaindustrial.client.render.CableAccessoryBlockEntityRenderer::new);

		// Stock Display Frame (MOD-066): the mod's first entity renderer. Vanilla EntityRenderers.register
		// is the path Fabric's own docs recommend (their EntityRendererRegistry is a thin legacy wrapper).
		net.minecraft.client.renderer.entity.EntityRenderers.register(
				dev.alaindustrial.registry.ModEntities.STOCK_DISPLAY_FRAME,
				dev.alaindustrial.client.render.StockDisplayFrameRenderer::new);
	}
}
