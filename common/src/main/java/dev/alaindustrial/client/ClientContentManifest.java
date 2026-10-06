package dev.alaindustrial.client;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.DiamondChestBlockEntity;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.ReactorDoorBlockEntity;
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.client.render.CableAccessoryBlockEntityRenderer;
import dev.alaindustrial.client.render.ChestBlockEntityRenderer;
import dev.alaindustrial.client.render.EnergyCondenserBlockEntityRenderer;
import dev.alaindustrial.client.render.MobWheelBlockEntityRenderer;
import dev.alaindustrial.client.render.RadiantSolarPanelBlockEntityRenderer;
import dev.alaindustrial.client.render.CableSleeveTint;
import dev.alaindustrial.client.render.FluidPipeTint;
import dev.alaindustrial.client.render.FluidTankBlockEntityRenderer;
import dev.alaindustrial.client.render.GardenDroneBlockEntityRenderer;
import dev.alaindustrial.client.render.IncubatorBlockEntityRenderer;
import dev.alaindustrial.client.render.IncubatorDomeTint;
import dev.alaindustrial.client.render.ReactorDoorBlockEntityRenderer;
import dev.alaindustrial.client.render.SprinklerHeadBlockEntityRenderer;
import dev.alaindustrial.client.render.TeleporterCapsuleDoorRenderer;
import dev.alaindustrial.client.render.ThermalCentrifugeBlockEntityRenderer;
import dev.alaindustrial.client.render.WaterMillWheelBlockEntityRenderer;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
import dev.alaindustrial.client.render.UpgradeTableBlockEntityRenderer;
import dev.alaindustrial.client.render.WorkstationBlockEntityRenderer;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModFluidsManifest;
import java.util.List;
import java.util.function.Supplier;
import dev.alaindustrial.client.hud.ElectricDrillHud;
import dev.alaindustrial.client.hud.EnergyPackHud;
import dev.alaindustrial.client.hud.TeleportFadeHud;
import dev.alaindustrial.client.render.ConcentratorSchematicRenderer;
import dev.alaindustrial.client.render.RootInspection;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.particle.NutrientSprayParticle;
import dev.alaindustrial.registry.ModParticles;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

// size-justified: a declarative table — seven client lists, one entry per line, each with its record and its
// one factory; splitting it by list would scatter the single place a client registration is declared, which
// is what MOD-403 and MOD-706 built it to be.
/**
 * Client-only content declared once for both loaders (MOD-403): block-entity renderers, the model layers
 * they bake, and the block tint sources. The counterpart of
 * {@link dev.alaindustrial.client.screen.MenuScreenManifest}, which MOD-190 already did for
 * menu&#8594;screen bindings.
 *
 * <p><b>Why this exists.</b> These three lists were written out TWICE — {@code IndustrializationClient}
 * (Fabric) and {@code IndustrializationNeoForgeClient} — in two different registration syntaxes around an
 * identical set of facts. Nothing but attention kept them in step, and the NeoForge client has no test
 * lane at all, so a line missing there is first noticed by a player looking at an unrendered block. The
 * comment that used to sit above the NeoForge renderer list said exactly that.
 *
 * <p><b>What stays per loader.</b> Only the registration API, which genuinely differs:
 * {@code BlockEntityRendererRegistry}/{@code ModelLayerRegistry}/{@code BlockColorRegistry} on Fabric
 * versus {@code EntityRenderersEvent.RegisterRenderers}/{@code .RegisterLayerDefinitions}/
 * {@code RegisterColorHandlersEvent.BlockTintSources} on NeoForge. Each client adapts one small
 * interface per list and loops.
 *
 * <p>MOD-706 added the key mappings ({@link #KEY_MAPPINGS}), the HUD layers ({@link #HUD_LAYERS}), the
 * fluid models ({@link #FLUID_MODELS}) and the particle providers ({@link #PARTICLE_PROVIDERS}), which both
 * client entry points used to list by hand — and which {@code loader_parity_check.py} could not see.
 *
 * <p><b>Deliberately NOT here</b> (it would cost more than it saves, and it is a single line): the
 * entity renderer for the Stock Display Frame — its handle ({@code ModEntities}/{@code ModEntitiesNeoForge})
 * is loader-specific and {@code ModContent}'s neutral slot is a wildcard {@code EntityType<?>}, so nothing
 * typed could be shared.
 *
 * <p>Lives in the {@code client} package so it is only ever class-loaded on the physical client.
 */
public final class ClientContentManifest {
	private ClientContentManifest() {
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Block-entity renderers
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * A loader's block-entity-renderer registration API, named as one generic method so the
	 * {@code BlockEntityType} and the renderer keep their types all the way to the call — the same shape
	 * as {@code MenuScreenManifest.ScreenRegistrar}, and for the same reason (no cast anywhere).
	 *
	 * <p>It is an interface with a generic <i>method</i>, not a generic interface, so
	 * {@link BlockEntityRendererDef#bindTo} can hand over a captured wildcard unchecked-free. A lambda
	 * cannot implement it (Java has no generic lambdas), so each loader passes an anonymous class.
	 */
	public interface RendererRegistrar {
		<T extends BlockEntity, S extends BlockEntityRenderState> void register(
				BlockEntityType<T> type, BlockEntityRendererProvider<T, S> provider);
	}

	/**
	 * One {@code BlockEntityType} &#8594; renderer binding.
	 *
	 * @param <T>  the block-entity class, shared by the type and the renderer
	 * @param <S>  the renderer's render-state class (26.2 splits it out of the renderer)
	 * @param type the manifest entry whose registered type this renderer draws; resolved lazily, because
	 *             on NeoForge the registry is only populated once its {@code RegisterEvent} has fired
	 * @param provider builds the renderer for that block entity
	 */
	public record BlockEntityRendererDef<T extends BlockEntity, S extends BlockEntityRenderState>(
			ContentManifest.BlockEntityDef<T> type, BlockEntityRendererProvider<T, S> provider) {

		/**
		 * Hands this pair to a loader's registration API. Called on a wildcard element of
		 * {@link #BLOCK_ENTITY_RENDERERS}; capture conversion re-binds {@code T} and {@code S} inside,
		 * which is why neither loader needs a cast.
		 */
		public void bindTo(RendererRegistrar registrar) {
			// Explicit type witness rather than inference: both parameters are then checked against
			// THIS def's T and S, so the pairing is verified at the one place it is made.
			registrar.<T, S>register(type.registeredType(), provider);
		}
	}

	/**
	 * Builds a {@link BlockEntityRendererDef}. {@code T} is inferred from <i>both</i> arguments, so a
	 * mismatched pair — {@code renderer(ContentManifest.blockEntity("water_mill", WaterMillBlockEntity.class),
	 * IncubatorBlockEntityRenderer::new)} — fails to compile instead of throwing a
	 * {@code ClassCastException} the first time the block comes into view (the MOD-198 trick, applied to
	 * renderers).
	 */
	private static <T extends BlockEntity, S extends BlockEntityRenderState> BlockEntityRendererDef<T, S>
			renderer(ContentManifest.BlockEntityDef<T> type, BlockEntityRendererProvider<T, S> provider) {
		return new BlockEntityRendererDef<>(type, provider);
	}

	/** Every block-entity renderer, in one shared order. See {@link BlockEntityRendererDef}. */
	public static final List<BlockEntityRendererDef<?, ?>> BLOCK_ENTITY_RENDERERS = List.of(
			// Storage chests: 3D model + animated lid, one shared renderer per tier texture.
			renderer(ContentManifest.blockEntity("iron_chest", IronChestBlockEntity.class),
					ChestBlockEntityRenderer::iron),
			renderer(ContentManifest.blockEntity("silver_chest", SilverChestBlockEntity.class),
					ChestBlockEntityRenderer::silver),
			renderer(ContentManifest.blockEntity("gold_chest", GoldChestBlockEntity.class),
					ChestBlockEntityRenderer::gold),
			renderer(ContentManifest.blockEntity("electrum_chest", ElectrumChestBlockEntity.class),
					ChestBlockEntityRenderer::electrum),
			// MOD-474 — the shielding chest: same chest geometry, its own lead-and-hazard texture.
			renderer(ContentManifest.blockEntity("shielding_chest", ShieldingChestBlockEntity.class),
					ChestBlockEntityRenderer::shielding),
			// MOD-599 — the diamond chest: same chest geometry, its own atlas.
			renderer(ContentManifest.blockEntity("diamond_chest", DiamondChestBlockEntity.class),
					ChestBlockEntityRenderer::diamond),
			renderer(ContentManifest.blockEntity("water_mill", WaterMillBlockEntity.class),
					WaterMillWheelBlockEntityRenderer::new),
			// MOD-546: the crystal inside the condenser frame — which of its three shapes is drawn says
			// which clot the block is holding.
			renderer(ContentManifest.blockEntity("energy_condenser", EnergyCondenserBlockEntity.class),
					EnergyCondenserBlockEntityRenderer::new),
			// MOD-602: the concentrator's two mirror wings, which fold over the collector when the
			// block cannot work. Everything else about it stands still and lives in the chunk mesh.
			renderer(ContentManifest.blockEntity("radiant_solar_panel", RadiantSolarPanelBlockEntity.class),
					RadiantSolarPanelBlockEntityRenderer::new),
			// MOD-483: the workstation's fans and the screens that fold out. One renderer for all
			// three parts of the block — the lower half draws the fans, the upper the screens, a
			// loose casing nothing.
			renderer(ContentManifest.blockEntity("workstation", WorkstationBlockEntity.class),
					WorkstationBlockEntityRenderer::new),
			// MOD-482: the Upgrade Table's press head, lowered onto the module while the table works.
			// Drawn by the upper half only; the rest of the bench is chunk geometry.
			renderer(ContentManifest.blockEntity("upgrade_table", UpgradeTableBlockEntity.class),
					UpgradeTableBlockEntityRenderer::new),
			// MOD-763: the assembled mob wheel — frame, running wheel and gate. Drawn by the drive; every
			// other member's formed model is empty.
			renderer(ContentManifest.blockEntity("mob_wheel_controller", MobWheelBlockEntity.class),
					MobWheelBlockEntityRenderer::new),
			// Garden Drone (MOD-277): the drone is geometry this renderer places above its station,
			// not an entity.
			renderer(ContentManifest.blockEntity("garden_drone_station", GardenDroneStationBlockEntity.class),
					GardenDroneBlockEntityRenderer::new),
			renderer(ContentManifest.blockEntity("wind_mill", WindMillBlockEntity.class),
					WindMillRotorBlockEntityRenderer::new),
			renderer(ContentManifest.blockEntity("high_altitude_wind_mill", HighAltitudeWindMillBlockEntity.class),
					WindMillRotorBlockEntityRenderer::new),
			renderer(ContentManifest.blockEntity("storm_wind_mill", StormWindMillBlockEntity.class),
					WindMillRotorBlockEntityRenderer::new),
			renderer(ContentManifest.blockEntity("fluid_tank", FluidTankBlockEntity.class),
					FluidTankBlockEntityRenderer::new),
			// Incubator (MOD-118): bound to the base, draws into the dome chamber above it.
			renderer(ContentManifest.blockEntity("incubator", IncubatorBlockEntity.class),
					IncubatorBlockEntityRenderer::new),
			// Thermal Centrifuge (MOD-424): the rotor turning inside the housing's open window — the only
			// way the redstone gate and the 400-tick spin-up are visible from outside the GUI.
			renderer(ContentManifest.blockEntity("thermal_centrifuge", ThermalCentrifugeBlockEntity.class),
					ThermalCentrifugeBlockEntityRenderer::new),
			// Sprinkler (MOD-525): the head is the block's only readout — it turns when there is
			// solution to spray — so it is geometry this renderer places above the mast.
			renderer(ContentManifest.blockEntity("sprinkler", SprinklerBlockEntity.class),
					SprinklerHeadBlockEntityRenderer::new),
			// Reactor airlock (MOD-493): the panel sliding down out of the doorway. The block model is
			// empty — everything the player sees of this door is drawn by the renderer.
			renderer(ContentManifest.blockEntity("reactor_door", ReactorDoorBlockEntity.class),
					ReactorDoorBlockEntityRenderer::new),
			// Teleporter capsule (MOD-112): the door sinking into the floor. Bound to the station, which
			// draws it across the two glass cells above; the rest of the capsule is chunk geometry.
			renderer(ContentManifest.blockEntity("teleporter", TeleporterBlockEntity.class),
					TeleporterCapsuleDoorRenderer::new),
			// Insulating stand under a bare cable (MOD-279). All cable grades share one BlockEntityType,
			// so this single registration covers every grade.
			renderer(ContentManifest.blockEntity("copper_cable", CableBlockEntity.class),
					CableAccessoryBlockEntityRenderer::new),
			// MOD-480 — the monitoring panel's face: the watched item and its count.
			renderer(ContentManifest.blockEntity("monitor_panel",
							dev.alaindustrial.block.entity.MonitorPanelBlockEntity.class),
					dev.alaindustrial.client.render.MonitorPanelBlockEntityRenderer::new));

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Model layers
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One baked model layer: where the renderer looks it up, and the geometry to bake.
	 *
	 * <p>No registrar interface here — both loaders take exactly {@code (ModelLayerLocation, () ->
	 * LayerDefinition)}; Fabric names that function {@code TexturedLayerDefinitionProvider} and NeoForge
	 * uses a plain {@link Supplier}, so each side passes {@code definition::get} / {@code definition}.
	 */
	public record ModelLayerDef(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
	}

	/** Every model layer the renderers above bake, in one shared order. */
	public static final List<ModelLayerDef> MODEL_LAYERS = List.of(
			// Vanilla single-body chest geometry, one layer per tier texture…
			new ModelLayerDef(ChestBlockEntityRenderer.IRON_CHEST_LAYER, ChestModel::createSingleBodyLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.SILVER_CHEST_LAYER, ChestModel::createSingleBodyLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.GOLD_CHEST_LAYER, ChestModel::createSingleBodyLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.ELECTRUM_CHEST_LAYER, ChestModel::createSingleBodyLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.SHIELDING_CHEST_LAYER, ChestModel::createSingleBodyLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.DIAMOND_CHEST_LAYER, ChestModel::createSingleBodyLayer),
			// …and MOD-391's double-chest halves: the 15-wide vanilla left/right bodies, per tier.
			new ModelLayerDef(ChestBlockEntityRenderer.IRON_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.IRON_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.SILVER_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.SILVER_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.GOLD_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.GOLD_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.ELECTRUM_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.ELECTRUM_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.SHIELDING_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.SHIELDING_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.DIAMOND_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.DIAMOND_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(WaterMillWheelBlockEntityRenderer.MODEL_LAYER,
					WaterMillWheelBlockEntityRenderer::createLayer),
			new ModelLayerDef(GardenDroneBlockEntityRenderer.MODEL_LAYER,
					GardenDroneBlockEntityRenderer::createLayer),
			new ModelLayerDef(ThermalCentrifugeBlockEntityRenderer.MODEL_LAYER,
					ThermalCentrifugeBlockEntityRenderer::createLayer),
			new ModelLayerDef(SprinklerHeadBlockEntityRenderer.MODEL_LAYER,
					SprinklerHeadBlockEntityRenderer::createLayer));

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Block tint sources
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One block's tint layers. The list index is the model's {@code tintindex}; 26.2 has no
	 * {@code BlockColor}/{@code ColorProviderRegistry} any more, a tint layer is a {@link BlockTintSource}
	 * whose in-world hook is {@code colorInWorld(state, level, pos)}.
	 *
	 * <p>Both loaders take the same {@code (List<BlockTintSource>, Block...)} shape — Fabric's
	 * {@code BlockColorRegistry.register} and NeoForge's {@code RegisterColorHandlersEvent.BlockTintSources
	 * #register} — so no registrar interface is needed, only a lazily-read block handle.
	 */
	public record BlockTintDef(List<BlockTintSource> sources, Supplier<Block> block) {
	}

	/** Every block tint source, in one shared order. */
	public static final List<BlockTintDef> BLOCK_TINTS = List.of(
			new BlockTintDef(List.of(dev.alaindustrial.client.render.RootSoilAppearance.INSTANCE), () -> ModContent.KOK_SAGYZ_ROOT.get()),
			// MOD-118: the incubator dome takes the colour of the glass it was built from.
			new BlockTintDef(List.of(IncubatorDomeTint.INSTANCE), () -> ModContent.INCUBATOR_DOME.get()),
			new BlockTintDef(List.of(FluidPipeTint.INSTANCE), () -> ModContent.FLUID_PIPE.get()),
			new BlockTintDef(List.of(FluidPipeTint.INSTANCE), () -> ModContent.FLUID_PIPE_ADVANCED.get()),
			new BlockTintDef(List.of(FluidPipeTint.INSTANCE), () -> ModContent.REINFORCED_FLUID_PIPE.get()),
			// MOD-662: the steam pipes show what they carry the same way.
			new BlockTintDef(List.of(FluidPipeTint.INSTANCE), () -> ModContent.STEAM_PIPE.get()),
			new BlockTintDef(List.of(FluidPipeTint.INSTANCE), () -> ModContent.REINFORCED_STEAM_PIPE.get()),
			// MOD-666: the rubber sleeve of an insulated cable takes its dye.
			new BlockTintDef(List.of(CableSleeveTint.INSTANCE), () -> ModContent.INSULATED_COPPER_CABLE.get()),
			new BlockTintDef(List.of(CableSleeveTint.INSTANCE), () -> ModContent.INSULATED_TIN_CABLE.get()),
			new BlockTintDef(List.of(CableSleeveTint.INSTANCE), () -> ModContent.INSULATED_GOLD_CABLE.get()),
			new BlockTintDef(List.of(CableSleeveTint.INSTANCE), () -> ModContent.INSULATED_ELECTRUM_CABLE.get()));

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Key mappings (MOD-706)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * Every key mapping of the mod, in the registration order both clients used: Fabric
	 * {@code KeyMappingHelper.registerKeyMapping}, NeoForge {@code RegisterKeyMappingsEvent.register}. The
	 * mappings themselves (key, category, defaults) are {@link ModKeyMappings}; only the category is NOT
	 * registered per loader — {@code KeyMapping.Category.register} already does that on both.
	 */
	public static final List<KeyMapping> KEY_MAPPINGS = List.of(
			ModKeyMappings.TOGGLE_ENERGY_HUD,
			ModKeyMappings.TOGGLE_DRILL_HUD,
			ModKeyMappings.OPEN_PROFILE,
			// MOD-127: the Fluxweave leggings' step assist.
			ModKeyMappings.TOGGLE_STEP_ASSIST,
			// MOD-482: the column bore on the held drill.
			ModKeyMappings.TOGGLE_DRILL_COLUMN);

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// HUD layers (MOD-706)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One HUD layer's drawing — the loader-neutral {@code (GuiGraphicsExtractor, DeltaTracker)} pair both
	 * loaders hand over (Fabric's {@code HudElement#extractRenderState}, NeoForge's {@code GuiLayer#render}).
	 */
	@FunctionalInterface
	public interface HudRenderer {
		void render(GuiGraphicsExtractor graphics, DeltaTracker delta);
	}

	/**
	 * Where a layer goes relative to what is already registered when it is added. The one value is the
	 * translation each client made by hand before MOD-706, and it is NOT the same spot on both:
	 * Fabric {@code HudElementRegistry.addLast} versus NeoForge {@code RegisterGuiLayersEvent.registerAboveAll}.
	 * Aligning the two is a product decision outside MOD-706; the field exists so a layer that needs
	 * another placement says so here, not in one loader.
	 */
	public enum HudPlacement {
		/** Fabric {@code addLast}, NeoForge {@code registerAboveAll}. */
		LAST
	}

	/**
	 * One HUD layer.
	 *
	 * @param id        path under the mod namespace
	 * @param renderer  what it draws
	 * @param placement how each loader places it
	 */
	public record HudLayerDef(String id, HudRenderer renderer, HudPlacement placement) {
		public HudLayerDef {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(renderer, "renderer");
			Objects.requireNonNull(placement, "placement");
		}
	}

	private static HudLayerDef hudLayer(String id, HudRenderer renderer, HudPlacement placement) {
		return new HudLayerDef(id, renderer, placement);
	}

	/**
	 * Every HUD layer, in registration order — which is drawing order: a later layer draws over an earlier
	 * one, so the charge readouts stay legible over the teleport fade.
	 */
	public static final List<HudLayerDef> HUD_LAYERS = List.of(
			// MOD-605: what the root under the crosshair is doing.
			hudLayer("root_inspection", RootInspection::renderHud, HudPlacement.LAST),
			// MOD-603: the concentrator's assembly hint.
			hudLayer("concentrator_assembly", ConcentratorSchematicRenderer::renderHud, HudPlacement.LAST),
			// MOD-106: the screen going dark as a jump lands.
			hudLayer("teleport_fade", TeleportFadeHud::render, HudPlacement.LAST),
			// MOD-065: the worn Energy Pack's charge readout.
			hudLayer("energy_pack_hud", EnergyPackHud::render, HudPlacement.LAST),
			// MOD-079: the held Electric Drill's charge readout, stacked below the pack's.
			hudLayer("electric_drill_hud", ElectricDrillHud::render, HudPlacement.LAST));

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Fluid models (MOD-706)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One fluid's model: the vanilla {@code FluidStateModelSet} hard-codes water and lava, so a custom
	 * fluid supplies its own {@code FluidModel.Unbaked} — Fabric through
	 * {@code FluidRenderingRegistry.register}, NeoForge through {@code RegisterFluidModelsEvent.register}.
	 *
	 * <p>The fluids are named by registry id and resolved when a loader registers the model; by then both
	 * have registered every fluid of {@code ModFluidsManifest.FLUIDS}.
	 *
	 * @param texture the {@code block/<texture>_still|_flow} sprites
	 * @param still   the still (or only) fluid's id
	 * @param flowing the flowing form's id, or {@code null} for a fluid that has none (steam)
	 */
	public record FluidModelDef(String texture, String still, @Nullable String flowing) {
		public FluidModelDef {
			Objects.requireNonNull(texture, "texture");
			Objects.requireNonNull(still, "still");
		}

		/** The model: still and flowing sprites, overlay and tint {@code null} exactly like vanilla lava. */
		public FluidModel.Unbaked model() {
			return new FluidModel.Unbaked(
					new Material(Industrialization.id("block/" + texture + "_still")),
					new Material(Industrialization.id("block/" + texture + "_flow")),
					null, null);
		}

		/** The still (or only) fluid, from the registry. */
		public Fluid stillFluid() {
			return fluid(still);
		}

		/** The flowing form, from the registry, or {@code null} when there is none. */
		public @Nullable Fluid flowingFluid() {
			return flowing == null ? null : fluid(flowing);
		}

		private static Fluid fluid(String id) {
			Identifier key = Industrialization.id(id);
			if (!BuiltInRegistries.FLUID.containsKey(key)) {
				throw new IllegalStateException("fluid model names " + key + ", which is not registered");
			}
			return BuiltInRegistries.FLUID.getValue(key);
		}
	}

	/**
	 * Every fluid model, in the order both clients registered them — one per entry of
	 * {@link ModFluidsManifest#FLUIDS} (MOD-708), its texture named after the fluid's id. A fluid without
	 * a flowing form (steam) registers its single fluid; without a model every tank and pipe holding it
	 * would draw the missing-texture sprite.
	 */
	public static final List<FluidModelDef> FLUID_MODELS = ModFluidsManifest.FLUIDS.stream()
			.map(def -> new FluidModelDef(def.id(), def.id(), def.flowingId()))
			.toList();

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Particle providers (MOD-706)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * A loader's sprite-set particle registration, as one generic method so the particle type and the
	 * provider keep their option type to the call (Fabric {@code ParticleProviderRegistry.register} with a
	 * {@code PendingParticleProvider}, NeoForge {@code RegisterParticleProvidersEvent.registerSpriteSet}).
	 */
	public interface ParticleRegistrar {
		<T extends ParticleOptions> void register(ParticleType<T> type,
				Function<SpriteSet, ParticleProvider<T>> factory);
	}

	/**
	 * One json-backed particle and the provider built from its sprite set.
	 *
	 * @param type    the registered particle type ({@code ModParticles} constant)
	 * @param factory the provider for the particle's sprites
	 */
	public record ParticleProviderDef<T extends ParticleOptions>(ParticleType<T> type,
			Function<SpriteSet, ParticleProvider<T>> factory) {

		/** Hands this pair to a loader's registrar, types intact. */
		public void bindTo(ParticleRegistrar registrar) {
			registrar.register(type, factory);
		}
	}

	private static <T extends ParticleOptions> ParticleProviderDef<T> particleProvider(ParticleType<T> type,
			Function<SpriteSet, ParticleProvider<T>> factory) {
		return new ParticleProviderDef<>(type, factory);
	}

	/** Every particle provider, in the order both clients registered them. */
	public static final List<ParticleProviderDef<?>> PARTICLE_PROVIDERS = List.of(
			// MOD-085: the Enriched Uranium Torch's green flame reuses the vanilla flame provider (like
			// soul_fire_flame); the colour comes entirely from the particle's own texture.
			particleProvider(ModParticles.ENRICHED_URANIUM_FLAME, FlameParticle.Provider::new),
			particleProvider(ModParticles.NUTRIENT_SPRAY, NutrientSprayParticle.Provider::new));
}
