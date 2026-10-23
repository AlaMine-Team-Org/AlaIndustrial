package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.compat.client.TranslucentTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the town hall's schematic as real, translucent block models (MOD-857, owner's round 3, point 6), from the
 * settlement core's block entity — the one place on 26.3 where a mod's geometry with a texture reaches the frame. A
 * submit from a world-render hook does not draw there ({@link FrameGizmos}, MOD-665), and frame gizmos have colour
 * only, which is why round 2 drew tinted boxes. A block entity renderer's custom geometry is drawn, textured, in the
 * block-entity feature phase.
 *
 * <p>The same renderer draws the house a restorer is laying in the town ({@link HousePlacementPreview}, MOD-877)
 * when no hall schematic is shown.
 *
 * <p>The ghosts come from {@link HallSchematicOverlay} (toggle, server replies, refresh) as a baked
 * {@link HallSchematicGhosts}; this renderer only writes them, in one buffer of
 * {@link TranslucentTypes#blockSheetNoDepthWrite()}: depth-tested, so a real block in front of a ghost hides it, and
 * never written to depth, so a ghost never hides a real block or the water behind it (the type sorts its quads back to
 * front on upload). A ghost drawn with its block-entity model (a chest) goes into one more buffer of the same kind per
 * texture it is drawn from, {@link TranslucentTypes#sheetNoDepthWrite} (MOD-824, R1-3).
 *
 * <p>The hall stands away from the core, so the renderer draws off its block: {@link #shouldRenderOffScreen} puts the
 * core on the global list, and {@link #getRenderBoundingBox} gives NeoForge the ghosts' box, which it tests against the
 * view before anything else (MOD-633). With no schematic shown both answer for the core block alone, and the renderer
 * draws nothing.
 */
public final class HallSchematicRenderer
		implements BlockEntityRenderer<SettlementCoreBlockEntity, HallSchematicRenderer.State> {

	/** Farther than this from the core, the renderer is not asked at all; the ghosts' own limit is shorter. */
	private static final int VIEW_DISTANCE = 256;

	public HallSchematicRenderer(BlockEntityRendererProvider.Context context) {
	}

	/** Render state: the baked ghosts of this core's schematic, or none. */
	public static final class State extends BlockEntityRenderState {
		@Nullable
		HallSchematicGhosts ghosts;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SettlementCoreBlockEntity blockEntity, State state, float partialTick,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
		HallSchematicGhosts ghosts = ghosts(blockEntity);
		state.ghosts = ghosts.isEmpty() ? null : ghosts;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		HallSchematicGhosts ghosts = state.ghosts;
		Vec3 eye = camera.pos;
		if (ghosts == null || eye == null) {
			return;
		}
		collector.submitCustomGeometry(poseStack, TranslucentTypes.blockSheetNoDepthWrite(),
				(pose, consumer) -> ghosts.emit(pose, consumer, eye));
		for (Identifier texture : ghosts.specialTextures()) {
			collector.submitCustomGeometry(poseStack, TranslucentTypes.sheetNoDepthWrite(texture),
					(pose, consumer) -> ghosts.emitSpecial(texture, pose, consumer, eye));
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer (MOD-633): the core block and
	 * every ghost of its schematic. No {@code @Override}: only NeoForge's {@code BlockEntityRenderer} declares it
	 * ({@code OffScreenRendererBoxTest}).
	 */
	public AABB getRenderBoundingBox(SettlementCoreBlockEntity blockEntity) {
		AABB core = new AABB(blockEntity.getBlockPos());
		AABB ghosts = ghosts(blockEntity).bounds();
		return ghosts == null ? core : core.minmax(ghosts);
	}

	/** The hall's schematic, or else the house being laid in the core's town (MOD-877). */
	private static HallSchematicGhosts ghosts(SettlementCoreBlockEntity blockEntity) {
		HallSchematicGhosts hall = HallSchematicOverlay.ghostsAt(blockEntity.getBlockPos());
		return hall.isEmpty() ? HousePlacementPreview.ghostsAt(blockEntity.getBlockPos()) : hall;
	}

	@Override
	public int getViewDistance() {
		return VIEW_DISTANCE;
	}
}
