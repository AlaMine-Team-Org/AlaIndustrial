package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.quarry.QuarryFrameLayout;
import dev.alaindustrial.block.entity.quarry.QuarryFramePlacements;
import dev.alaindustrial.compat.client.Poses;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The formed quarry frame (MOD-950), drawn from the controller's block entity in the designer's model: four towers,
 * the two paths, the bridge with its saddles and the cart with its drill.
 *
 * <p>Once the frame forms every cell of it is invisible ({@code QuarryFramePart}), so the whole frame is this
 * renderer's geometry ({@link QuarryGeometry}, generated from the designer's {@code .bbmodel}). A loose frame keeps its
 * own block models and this renderer draws nothing. Where the parts stand comes from the controller's
 * {@link QuarryFrameLayout}, which reaches the client in the block entity's update tag.
 *
 * <p><b>Placing a part.</b> Where each part stands and how it turns is {@link QuarryFramePlacements}, the list the
 * frame's collision is cut from as well; this renderer applies it as
 * {@code translate(centre) · R_Y(yaw) · translate(-local)}.
 *
 * <p><b>Texture.</b> The designer's 512 x 512 atlas under {@code textures/entity/quarry/}, stitched into the block
 * atlas by a directory source in {@code atlases/blocks.json}, as the mob wheel's sheets are.
 */
public final class QuarryFrameRenderer
		implements BlockEntityRenderer<QuarryControllerBlockEntity, QuarryFrameRenderer.State> {

	private static final float PIXEL = 1.0F / 16.0F;

	private static final AffineCubeMesh TOWER = new AffineCubeMesh(QuarryGeometry.TOWER);
	private static final AffineCubeMesh PATH = new AffineCubeMesh(QuarryGeometry.PATH);
	private static final AffineCubeMesh SADDLE = new AffineCubeMesh(QuarryGeometry.SADDLE);
	private static final AffineCubeMesh BRIDGE = new AffineCubeMesh(QuarryGeometry.BRIDGE);
	private static final AffineCubeMesh CART = new AffineCubeMesh(QuarryGeometry.CART);
	private static final AffineCubeMesh DRILL = new AffineCubeMesh(QuarryGeometry.DRILL);
	/** By {@link QuarryFramePlacements.Part} ordinal. */
	private static final AffineCubeMesh[] MESHES = {TOWER, PATH, SADDLE, BRIDGE, CART, DRILL};


	private static final SpriteId SPRITE = Sheets.BLOCK_ENTITIES_MAPPER.apply(Industrialization.id("quarry/frame"));
	/** The stub of cable into the socket foundation ({@link QuarryCableJoint}). */
	private static final SpriteId CABLE = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("copper_cable"));
	/**
	 * Back faces culled: where two parts meet, their end faces lie in one plane facing each other, and a sheet that
	 * drew both flickered between them at every joint of the bridge and the paths.
	 */
	private static final RenderType TYPE = RenderTypes.entityCutoutCull(Sheets.BLOCKS_MAPPER.sheet());

	private final SpriteGetter sprites;

	public QuarryFrameRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** One part where it stands: which mesh, its centre relative to the controller block, its turn and light. */
	record Placement(int mesh, float x, float y, float z, float yaw, int light) {
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		final List<Placement> placements = new ArrayList<>();
		/** Faces of the socket foundation a cable touches, its height from the controller, and its light. */
		int cableFaces;
		/** Whether a drill bit is in the slot: without one the cart hangs no drill (owner, 2026-11-05). */
		boolean drill;
		float socketY;
		int socketLight;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(QuarryControllerBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		state.placements.clear();
		state.cableFaces = 0;
		state.drill = entity.hasDrillBit();
		QuarryFrameLayout layout = entity.layout();
		Level level = entity.getLevel();
		if (layout == null || level == null) {
			return;
		}
		place(layout, entity.getBlockPos(), level, state.placements);
		BlockPos socket = new BlockPos(entity.getBlockPos().getX(), layout.baseY(), entity.getBlockPos().getZ());
		state.cableFaces = QuarryCableJoint.cabledFaces(level, socket);
		state.socketY = layout.baseY() - entity.getBlockPos().getY();
		state.socketLight = lightAbove(level, socket);
	}

	/** Every part of the frame ({@link QuarryFramePlacements}), relative to the controller block and lit. */
	static void place(QuarryFrameLayout layout, BlockPos controller, Level level, List<Placement> out) {
		float baseY = layout.baseY() - controller.getY();
		for (QuarryFramePlacements.Placement part : QuarryFramePlacements.of(layout)) {
			int light = lightAbove(level, BlockPos.containing(part.x(), part.lightY(), part.z()));
			out.add(new Placement(part.part().ordinal(), part.x() - controller.getX(), baseY,
					part.z() - controller.getZ(), part.yaw(), light));
		}
	}

	/** How many cells above a part's light cell are read: a player's block on the frame must not black the model. */
	private static final int LIGHT_CELLS = 4;

	/**
	 * The brightest light of the open cells above a part: its own light cell and the few over it, sky and block light
	 * each at their brightest. One cell alone went dark the moment a player set a block on the frame, and the model
	 * under it was drawn black.
	 */
	static int lightAbove(Level level, BlockPos cell) {
		int light = 0;
		for (int up = 0; up < LIGHT_CELLS; up++) {
			light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, cell.above(up)));
		}
		return light;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.placements.isEmpty()) {
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(SPRITE);
		for (Placement placement : state.placements) {
			AffineCubeMesh mesh = MESHES[placement.mesh()];
			if (mesh.isEmpty()) {
				continue;
			}
			QuarryFramePlacements.Part part = QuarryFramePlacements.Part.values()[placement.mesh()];
			if (part == QuarryFramePlacements.Part.DRILL && !state.drill) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(placement.x(), placement.y(), placement.z());
			Poses.rotate(poseStack, Axis.YP.rotationDegrees(placement.yaw()));
			poseStack.translate(-part.localX() * PIXEL, 0.0F, -part.localZ() * PIXEL);
			int light = placement.light();
			collector.submitCustomGeometry(poseStack, TYPE,
					(pose, consumer) -> mesh.emit(pose, consumer, sprite, light));
			poseStack.popPose();
		}
		if (state.cableFaces != 0) {
			TextureAtlasSprite cable = this.sprites.get(CABLE);
			int faces = state.cableFaces;
			int light = state.socketLight;
			poseStack.pushPose();
			poseStack.translate(0.0F, state.socketY, 0.0F);
			collector.submitCustomGeometry(poseStack, TYPE,
					(pose, consumer) -> QuarryCableJoint.emit(pose, consumer, cable, light, faces));
			poseStack.popPose();
		}
	}

	/** The frame stands up to fifteen blocks from the controller in every direction it is built. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer (MOD-633): the whole frame, its
	 * heads' beams and the cart's drill included. A loose controller draws nothing, so its own block is enough.
	 *
	 * <p>No {@code @Override}: only NeoForge's {@code BlockEntityRenderer} declares this method; Fabric never tests a
	 * block entity against the frustum. {@code OffScreenRendererBoxTest} on the NeoForge lane fails if it stops
	 * overriding.
	 */
	public AABB getRenderBoundingBox(QuarryControllerBlockEntity blockEntity) {
		QuarryFrameLayout layout = blockEntity.layout();
		if (layout == null) {
			return new AABB(blockEntity.getBlockPos());
		}
		return new AABB(layout.minX() - 1, layout.baseY(), layout.minZ() - 1, layout.maxX() + 2, layout.headY() + 4,
				layout.maxZ() + 2);
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
