package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.RadiantSolarPanelBlock;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Mirror Concentrator's two wings, which fold over the collector when the block cannot work
 * (MOD-602).
 *
 * <p>Everything that stands still — chassis, mast, collector, lens — is an ordinary block model
 * baked into the chunk mesh, where it costs nothing per frame. Only the two wings are here, and the
 * generator cuts exactly those cubes out of the static model, or a motionless copy would sit inside
 * the folding one.
 *
 * <p><b>Where the motion comes from.</b> Nothing extra crosses the network. Whether the optics
 * should be out is a pure function of world state the client already has, so the block entity
 * samples it once a tick and keeps its own eased clock — the same trick the reactor airlock and the
 * workstation's screens use. A solar farm therefore costs no block updates at dawn.
 *
 * <p><b>Why the wing rises before it turns.</b> The hinge sits inside the collector bed, so a pure
 * rotation drags the wing through the gold rail. The lift is measured, not styled: see the fold
 * stages in {@link RadiantSolarPanelGeometry}, which are read out of the designer's keyframes.
 *
 * <p>Hand-written vertices rather than {@code ModelPart}, for the reason the condenser documents:
 * the texture is a palette addressed with per-face UVs, and {@code CubeListBuilder} can only lay out
 * the fixed box unwrap.
 */
public final class RadiantSolarPanelBlockEntityRenderer
		implements BlockEntityRenderer<RadiantSolarPanelBlockEntity,
				RadiantSolarPanelBlockEntityRenderer.State> {

	private static final SpriteId SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("radiant_solar_panel_body"));
	private static final RenderType TYPE = SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	private static final float PIXEL = 1.0F / 16.0F;

	/** How much bigger the assembled structure is than the one-block machine, on every axis. */
	private static final float STRUCTURE_SCALE = 2.0F;

	private static final CubeMesh[] WINGS = buildWings();

	private static CubeMesh[] buildWings() {
		CubeMesh[] meshes = new CubeMesh[RadiantSolarPanelGeometry.WINGS.length];
		for (int i = 0; i < meshes.length; i++) {
			// 0 offset: the wings are exported in the block's own pixel space already, unlike the
			// condenser's crystal, which is modelled around zero and has to be moved to the middle.
			meshes[i] = new CubeMesh(RadiantSolarPanelGeometry.WINGS[i], 0.0F);
		}
		return meshes;
	}

	private final SpriteGetter sprites;

	public RadiantSolarPanelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		/** Wing angle in degrees, before the per-wing direction sign. */
		float angleDegrees;
		/** How far the wings have risen on their hinges, in block pixels. */
		float lift;
		/** Blockstate {@code y} of the assembled structure, or 0 for the one-block machine. */
		int structureYaw;
		/** Middle of the structure in blocks from this block's origin — depends on the facing. */
		float centreX;
		float centreZ;
		/** Whether this block is the core of an assembled structure and draws it at double size. */
		boolean assembled;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RadiantSolarPanelBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition,
				breakProgress);
		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
		// Picked here so submit stays a pure draw.
		float folded = entity.foldProgress(gameTime, partialTicks);
		float seconds = folded * RadiantSolarPanelGeometry.FOLD_SECONDS;
		state.angleDegrees = sample(seconds, 1);
		state.lift = sample(seconds, 2);
		BlockState blockState = entity.getBlockState();
		state.assembled = blockState.getValue(RadiantSolarPanelBlock.ASSEMBLED);
		if (state.assembled) {
			Direction facing = blockState.getValue(RadiantSolarPanelBlock.FACING);
			state.structureYaw = ConcentratorPart.modelYaw(facing);
			float[] centre = ConcentratorPart.structureCentre(facing);
			state.centreX = centre[0];
			state.centreZ = centre[1];
		} else {
			state.structureYaw = 0;
			state.centreX = 0.0F;
			state.centreZ = 0.0F;
		}
	}

	/**
	 * Linear read of one column of the fold stages at {@code seconds}.
	 *
	 * <p>Linear between stages on purpose: that is what the keyframes say, and the easing already
	 * happened once, on the progress the block entity handed over. Easing twice would make the wings
	 * crawl at both ends.
	 */
	private static float sample(float seconds, int column) {
		float[][] stages = RadiantSolarPanelGeometry.FOLD_STAGES;
		if (seconds <= stages[0][0]) {
			return stages[0][column];
		}
		for (int i = 1; i < stages.length; i++) {
			if (seconds <= stages[i][0]) {
				float span = stages[i][0] - stages[i - 1][0];
				float t = span <= 0.0F ? 1.0F : (seconds - stages[i - 1][0]) / span;
				return stages[i - 1][column] + (stages[i][column] - stages[i - 1][column]) * t;
			}
		}
		return stages[stages.length - 1][column];
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (!state.assembled) {
			// The one-block form is a plain slab with no optics on it. Mirrors that folded over a
			// panel the size of a doorstep read as debris floating beside it, which is exactly what
			// they looked like in game.
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(SPRITE);
		poseStack.pushPose();
		if (state.assembled) {
			// The structure is the one-block model at double size, so the wings scale with it, and the
			// turn happens about the STRUCTURE's middle — the point the blockstate rotation of the
			// eight static pieces is equivalent to. That middle moves with the facing (see
			// ConcentratorPart#structureCentre): the structure grows to one side of the core, and
			// which side depends on which way it faces.
			poseStack.translate(state.centreX, 0.0F, state.centreZ);
			poseStack.mulPose(Axis.YP.rotationDegrees(-state.structureYaw));
			// CANONICAL centre out, WORLD centre in — see ConcentratorPart#canonicalToWorld. The wing
			// rows are canonical coordinates, so the turn has to leave that frame before the world
			// offset is added; subtracting the world centre here instead cancels only facing north.
			poseStack.translate(-ConcentratorPart.CANONICAL_CENTRE, 0.0F,
					-ConcentratorPart.CANONICAL_CENTRE);
			poseStack.scale(STRUCTURE_SCALE, STRUCTURE_SCALE, STRUCTURE_SCALE);
		}
		for (int i = 0; i < WINGS.length; i++) {
			float[] pivot = RadiantSolarPanelGeometry.WING_PIVOTS[i];
			float angle = state.angleDegrees * RadiantSolarPanelGeometry.WING_DIRECTION[i];
			poseStack.pushPose();
			// Lift first, then the turn about the hinge: the keyframes move the whole group in world
			// space, so the rise must not be dragged round by the rotation.
			poseStack.translate(0.0F, state.lift * PIXEL, 0.0F);
			poseStack.translate(pivot[0] * PIXEL, pivot[1] * PIXEL, pivot[2] * PIXEL);
			poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
			poseStack.translate(-pivot[0] * PIXEL, -pivot[1] * PIXEL, -pivot[2] * PIXEL);
			submit(collector, poseStack, WINGS[i], sprite, state.lightCoords);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/**
	 * Assembled, the wings sweep well outside the core block, so the renderer must keep drawing while
	 * that block itself is off screen — otherwise the mirrors vanish as the player walks past.
	 */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, CubeMesh mesh,
			TextureAtlasSprite sprite, int light) {
		if (mesh.isEmpty()) {
			return;
		}
		// The block's own light, not full bright: these are mirrors and painted metal, not a light
		// source — the condenser's crystal is the exception, not the rule.
		collector.submitCustomGeometry(poseStack, TYPE,
				(pose, consumer) -> mesh.emit(pose, consumer, sprite, light));
	}
}
