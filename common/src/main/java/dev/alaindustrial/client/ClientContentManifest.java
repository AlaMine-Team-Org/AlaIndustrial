package dev.alaindustrial.client;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.ReactorDoorBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.client.render.CableAccessoryBlockEntityRenderer;
import dev.alaindustrial.client.render.ChestBlockEntityRenderer;
import dev.alaindustrial.client.render.EnergyCondenserBlockEntityRenderer;
import dev.alaindustrial.client.render.FluidPipeTint;
import dev.alaindustrial.client.render.FluidTankBlockEntityRenderer;
import dev.alaindustrial.client.render.GardenDroneBlockEntityRenderer;
import dev.alaindustrial.client.render.IncubatorBlockEntityRenderer;
import dev.alaindustrial.client.render.IncubatorDomeTint;
import dev.alaindustrial.client.render.ReactorDoorBlockEntityRenderer;
import dev.alaindustrial.client.render.ThermalCentrifugeBlockEntityRenderer;
import dev.alaindustrial.client.render.WaterMillWheelBlockEntityRenderer;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

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
 * <p><b>Deliberately NOT here</b> (each would cost more than it saves, and each is a single line):
 * the entity renderer for the Stock Display Frame — its handle
 * ({@code ModEntities}/{@code ModEntitiesNeoForge}) is loader-specific and {@code ModContent}'s neutral
 * slot is a wildcard {@code EntityType<?>}, so nothing typed could be shared; the particle provider,
 * where Fabric takes a {@code ParticleProvider} and NeoForge a sprite-set factory; and fluid model
 * registration, which is a different event with a different argument order on each side.
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
			renderer(ContentManifest.blockEntity("water_mill", WaterMillBlockEntity.class),
					WaterMillWheelBlockEntityRenderer::new),
			// MOD-393: the orb inside the condenser frame — its speed and glow are the block's gauge.
			renderer(ContentManifest.blockEntity("energy_condenser", EnergyCondenserBlockEntity.class),
					EnergyCondenserBlockEntityRenderer::new),
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
			// Reactor airlock (MOD-493): the panel sliding down out of the doorway. The block model is
			// empty — everything the player sees of this door is drawn by the renderer.
			renderer(ContentManifest.blockEntity("reactor_door", ReactorDoorBlockEntity.class),
					ReactorDoorBlockEntityRenderer::new),
			// Insulating stand under a bare cable (MOD-279). All cable grades share one BlockEntityType,
			// so this single registration covers every grade.
			renderer(ContentManifest.blockEntity("copper_cable", CableBlockEntity.class),
					CableAccessoryBlockEntityRenderer::new));

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
			// …and MOD-391's double-chest halves: the 15-wide vanilla left/right bodies, per tier.
			new ModelLayerDef(ChestBlockEntityRenderer.IRON_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.IRON_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.SILVER_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.SILVER_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.GOLD_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.GOLD_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.ELECTRUM_CHEST_LEFT_LAYER, ChestModel::createDoubleBodyLeftLayer),
			new ModelLayerDef(ChestBlockEntityRenderer.ELECTRUM_CHEST_RIGHT_LAYER, ChestModel::createDoubleBodyRightLayer),
			new ModelLayerDef(WaterMillWheelBlockEntityRenderer.MODEL_LAYER,
					WaterMillWheelBlockEntityRenderer::createLayer),
			new ModelLayerDef(GardenDroneBlockEntityRenderer.MODEL_LAYER,
					GardenDroneBlockEntityRenderer::createLayer),
			new ModelLayerDef(ThermalCentrifugeBlockEntityRenderer.MODEL_LAYER,
					ThermalCentrifugeBlockEntityRenderer::createLayer));

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
			// MOD-118: the incubator dome takes the colour of the glass it was built from.
			new BlockTintDef(List.of(IncubatorDomeTint.INSTANCE), () -> ModContent.INCUBATOR_DOME.get()),
			new BlockTintDef(List.of(FluidPipeTint.INSTANCE), () -> ModContent.FLUID_PIPE.get()));
}
