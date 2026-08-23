package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.entity.ReactorDoorBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Draws the reactor airlock's panel wherever its travel has got to (MOD-493).
 *
 * <p><b>Why the block model is empty.</b> A baked model can only ever show one of two poses, and the
 * {@code open} state it would key on flips in a single tick — so the panel would jump. Everything the
 * player sees of this door is therefore drawn here, and {@code block/reactor_door_hidden} exists only
 * to give the block a particle texture. That is the same bargain every animated block in this mod
 * makes, and it costs the two things a chunk mesh gives for free: ambient occlusion, and the crumbling
 * overlay while the door is being mined.
 *
 * <p><b>Each half draws its own slice.</b> The panel is two blocks tall but is rendered as two
 * one-block windows, one per {@link DoubleBlockHalf}, rather than as a single tall mesh hanging off
 * the lower block. Two reasons, both visible: a two-block mesh leaves its own section box and needs
 * {@code shouldRenderOffScreen}, and — the one that actually shows — it would light the whole panel
 * from one cell, so a door standing between a lit corridor and a dark room would be uniformly one or
 * the other. Both halves read one clock (the lower half's) so the slice edges cannot drift apart.
 *
 * <p><b>The texture slides with the panel, it does not stretch.</b> As the door sinks, each half shows
 * a different window onto the same two-tile-tall artwork: the V range is derived from where the panel's
 * top edge currently is, so the metal appears to move past the doorway instead of being squashed into
 * it. Getting this wrong looks like a texture being scaled, which is the tell of a fake sliding door.
 */
public final class ReactorDoorBlockEntityRenderer
		implements BlockEntityRenderer<ReactorDoorBlockEntity, ReactorDoorBlockEntityRenderer.State> {

	/** The upper tile of the two-block panel — the one with the semi-transparent vision slit. */
	private static final SpriteId PANEL_TOP =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("reactor_door_top"));
	private static final SpriteId PANEL_BOTTOM =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("reactor_door_bottom"));
	/**
	 * Translucent for the top tile, cutout for the bottom — not one shared sheet. The top texture is
	 * 22 pixels of alpha 105 (the slit) against a solid plate; on a cutout sheet those pixels come out
	 * fully opaque and the window disappears, which is exactly what {@code force_translucent} in the
	 * old baked model was there to prevent.
	 */
	private static final RenderType TOP_TYPE =
			PANEL_TOP.renderType(ignored -> Sheets.translucentBlockItemSheet());
	private static final RenderType BOTTOM_TYPE =
			PANEL_BOTTOM.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	/** Height of the whole panel, in blocks. Two, and the doorway it fills is the same. */
	private static final float PANEL_HEIGHT = 2.0f;
	/** Below this a slice is thinner than a pixel and only costs vertices. */
	private static final float EPSILON = 1.0e-4f;

	private final SpriteGetter sprites;

	public ReactorDoorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ReactorDoorBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = entity.getBlockState();
		Level level = entity.getLevel();
		if (level == null || !(blockState.getBlock() instanceof ReactorDoorBlock)) {
			state.visible = false;
			return;
		}
		state.visible = true;
		// Which one-block window of the two-block panel this block entity is responsible for.
		state.windowBase = blockState.getValue(ReactorDoorBlock.HALF) == DoubleBlockHalf.UPPER ? 1.0f : 0.0f;

		// One clock for the pair — see the class doc. `slideProgress` takes the tick count and the
		// fraction apart because a long-lived world's game time no longer fits a float precisely.
		float retracted = entity.animationClock().slideProgress(level.getGameTime(), partialTicks);
		state.panelTop = PANEL_HEIGHT * (1.0f - retracted);

		// The panel fills exactly the volume the closed door collides in, read off the block itself so
		// the two cannot disagree about where the door is.
		VoxelShape slab = ReactorDoorBlock.closedSlab(blockState.getValue(ReactorDoorBlock.FACING));
		state.minX = (float) slab.min(Direction.Axis.X);
		state.maxX = (float) slab.max(Direction.Axis.X);
		state.minZ = (float) slab.min(Direction.Axis.Z);
		state.maxZ = (float) slab.max(Direction.Axis.Z);

		state.lightCoords = LightCoordsUtil.getLightCoords(level, entity.getBlockPos());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (!state.visible) {
			return;
		}
		// This block's window onto the panel, in door space (0 at the lower half's floor, 2 at the
		// closed panel's top). Everything above `panelTop` has sunk out of the doorway already.
		float windowBottom = state.windowBase;
		float windowTop = Math.min(state.windowBase + 1.0f, state.panelTop);
		if (windowTop - windowBottom <= EPSILON) {
			return;
		}
		// Where the two texture tiles meet, in door space: one block down from the panel's top edge.
		float seam = state.panelTop - 1.0f;
		submitTile(state, poseStack, collector, PANEL_TOP, TOP_TYPE, 0.0f,
				Math.max(windowBottom, seam), windowTop);
		submitTile(state, poseStack, collector, PANEL_BOTTOM, BOTTOM_TYPE, 1.0f,
				windowBottom, Math.min(windowTop, seam));
	}

	/**
	 * One texture tile's contribution to this window.
	 *
	 * @param tileIndex which tile of the panel this is, counted down from the top — the amount to
	 *                  subtract from "distance below the panel's top edge" to land back inside a
	 *                  single sprite's 0..1 V range
	 */
	private void submitTile(State state, PoseStack poseStack, SubmitNodeCollector collector,
			SpriteId spriteId, RenderType renderType, float tileIndex, float doorBottom, float doorTop) {
		if (doorTop - doorBottom <= EPSILON) {
			return;
		}
		// V runs from the sprite's top edge downward, so the panel's own top edge is V=0 of tile 0.
		float vTop = Mth.clamp(state.panelTop - doorTop - tileIndex, 0.0f, 1.0f);
		float vBottom = Mth.clamp(state.panelTop - doorBottom - tileIndex, 0.0f, 1.0f);
		float y0 = doorBottom - state.windowBase;
		float y1 = doorTop - state.windowBase;
		TextureAtlasSprite sprite = sprites.get(spriteId);
		collector.submitCustomGeometry(poseStack, renderType,
				(pose, consumer) -> slice(pose, consumer, sprite, state, y0, y1, vTop, vBottom));
	}

	/**
	 * The visible faces of one slice of the panel: both broad sides, the top edge and the two narrow
	 * jambs. No bottom face — it is either flush with the floor or already under it.
	 *
	 * <p>Every face is wound both ways, the convention the incubator's ring set here: both sheets cull
	 * back faces, and a panel that vanishes when walked around is a worse bug than the vertices this
	 * costs.
	 */
	private static void slice(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
			State state, float y0, float y1, float vTop, float vBottom) {
		float x0 = state.minX;
		float x1 = state.maxX;
		float z0 = state.minZ;
		float z1 = state.maxZ;
		float u0 = sprite.getU0();
		float u1 = sprite.getU1();
		float v0 = Mth.lerp(vTop, sprite.getV0(), sprite.getV1());
		float v1 = Mth.lerp(vBottom, sprite.getV0(), sprite.getV1());
		int light = state.lightCoords;
		// Which way the slab is thin follows the facing: Shapes.rotateHorizontal turns the plate with
		// the direction, so a north/south door is thin along Z and an east/west one along X. The box
		// comes out right either way — the UV does not. Assuming Z here (as this did at first) gave
		// every east/west door's broad faces the sliver of texture meant for its edges, and its edges
		// the whole plate: a stretched smear on exactly half the possible orientations, and one the
		// demo stand cannot show because it only ever places the door facing south.
		boolean thinAlongZ = (z1 - z0) <= (x1 - x0);
		float thickness = thinAlongZ ? z1 - z0 : x1 - x0;
		// The edges show a strip of plate as deep as the panel is thick, rather than the whole sprite
		// squeezed into 3/16 of a block.
		float uEdge = Mth.lerp(thickness, u0, u1);
		float vEdge = Mth.lerp(thickness, v0, v1);

		if (thinAlongZ) {
			// Broad faces span X, at the slab's two Z walls.
			face(pose, out, light,
					x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, u0, v1, u1, v0, 0.0f, 0.0f, -1.0f);
			face(pose, out, light,
					x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, u0, v1, u1, v0, 0.0f, 0.0f, 1.0f);
			// Jambs span Z — only as wide as the panel is thick.
			face(pose, out, light,
					x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, u0, v1, uEdge, v0, -1.0f, 0.0f, 0.0f);
			face(pose, out, light,
					x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, u0, v1, uEdge, v0, 1.0f, 0.0f, 0.0f);
			// The leading edge — the face the player actually watches descend. U runs along X (full),
			// V along Z (thin).
			face(pose, out, light,
					x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, vEdge, u1, v0, 0.0f, 1.0f, 0.0f);
		} else {
			// Broad faces span Z, at the slab's two X walls.
			face(pose, out, light,
					x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, u0, v1, u1, v0, -1.0f, 0.0f, 0.0f);
			face(pose, out, light,
					x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, u0, v1, u1, v0, 1.0f, 0.0f, 0.0f);
			// Jambs span X — only as wide as the panel is thick.
			face(pose, out, light,
					x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, u0, v1, uEdge, v0, 0.0f, 0.0f, -1.0f);
			face(pose, out, light,
					x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, u0, v1, uEdge, v0, 0.0f, 0.0f, 1.0f);
			// Leading edge, mirrored: here U runs along X (thin) and V along Z (full).
			face(pose, out, light,
					x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v0, uEdge, v1, 0.0f, 1.0f, 0.0f);
		}
	}

	/** One quad, emitted with both windings so neither sheet's back-face culling can hide it. */
	private static void face(PoseStack.Pose pose, VertexConsumer out, int light,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float uMin, float vMin, float uMax, float vMax,
			float normalX, float normalY, float normalZ) {
		vertex(pose, out, ax, ay, az, uMin, vMin, light, normalX, normalY, normalZ);
		vertex(pose, out, bx, by, bz, uMax, vMin, light, normalX, normalY, normalZ);
		vertex(pose, out, cx, cy, cz, uMax, vMax, light, normalX, normalY, normalZ);
		vertex(pose, out, dx, dy, dz, uMin, vMax, light, normalX, normalY, normalZ);

		vertex(pose, out, dx, dy, dz, uMin, vMax, light, -normalX, -normalY, -normalZ);
		vertex(pose, out, cx, cy, cz, uMax, vMax, light, -normalX, -normalY, -normalZ);
		vertex(pose, out, bx, by, bz, uMax, vMin, light, -normalX, -normalY, -normalZ);
		vertex(pose, out, ax, ay, az, uMin, vMin, light, -normalX, -normalY, -normalZ);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
			float u, float v, int light, float normalX, float normalY, float normalZ) {
		out.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, normalX, normalY, normalZ);
	}

	public static final class State extends BlockEntityRenderState {
		private boolean visible;
		/** 0 for the lower half, 1 for the upper: this block's floor in door space. */
		private float windowBase;
		/** Door-space height of the panel's top edge: 2 when shut, 0 when fully sunk. */
		private float panelTop = PANEL_HEIGHT;
		private float minX;
		private float maxX;
		private float minZ;
		private float maxZ;
	}
}
