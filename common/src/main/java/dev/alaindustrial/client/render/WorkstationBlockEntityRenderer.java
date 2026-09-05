package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.WorkstationPart;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.core.machine.RotorSpin;
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
 * The Workstation's moving parts (MOD-483): three fans in the tower and the screens that fold out.
 *
 * <p>Everything that stands still is an ordinary block model and is baked into the chunk mesh, where
 * it costs nothing per frame. Only what turns is here, and the generator cuts exactly those cubes out
 * of the two half models — otherwise a motionless copy would sit inside the spinning one.
 *
 * <p><b>One renderer, both halves.</b> A block entity type gets one renderer, and all three parts of
 * the workstation share a type. The lower half draws its fans, the upper half its screens, and a
 * loose casing draws nothing at all.
 *
 * <p><b>Where the motion comes from.</b> The fans are a pure function of the game clock, the way the
 * mill and the centrifuge rotors are — nothing is stored, so two players looking at the same machine
 * see the same blades and a chunk reload changes nothing. The fold-out is the reactor airlock's
 * trick instead: the block entity notices the {@code lit} edge on its own client tick and remembers
 * when. Both halves read the LOWER half's clock, or the seam between them would tear on the frame
 * where the block update lands between two ticks.
 *
 * <p>Hand-written vertices rather than {@code ModelPart}, for the reason the condenser documents: the
 * designer's texture is a palette addressed with per-face UVs, and {@code CubeListBuilder} can only
 * lay out the fixed box unwrap.
 */
public final class WorkstationBlockEntityRenderer
		implements BlockEntityRenderer<WorkstationBlockEntity,
				WorkstationBlockEntityRenderer.State> {

	private static final SpriteId SPRITE_ON =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("workstation_body"));
	private static final SpriteId SPRITE_OFF =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("workstation_body_off"));
	private static final RenderType TYPE_ON =
			SPRITE_ON.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	private static final RenderType TYPE_OFF =
			SPRITE_OFF.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	/**
	 * Four turns a second, the rate the designer keyframed.
	 *
	 * <p>Fast, and it can be: each fan is four evenly spaced blades, so the eye has a 90° period to
	 * alias against and 24° a frame at 60 fps stays well inside it. Below about 32 fps the step passes
	 * 45° and the blades start to read as turning backwards — the same trade the centrifuge's rotor
	 * documents, accepted here because the alternative is a machine that looks idle while it works.
	 */
	private static final float FAN_RADIANS_PER_TICK = (float) (Math.PI * 2.0 * 4.0 / 20.0);

	/** How far the screens tilt out, straight from the designer's keyframes. */
	private static final float MONITOR_OPEN_DEGREES = 12.5F;

	private static final float PIXEL = 1.0F / 16.0F;

	private static final CubeMesh[] FANS = buildFans();
	private static final CubeMesh MONITORS = new CubeMesh(WorkstationGeometry.MONITORS, 0.0F);

	private static CubeMesh[] buildFans() {
		CubeMesh[] meshes = new CubeMesh[WorkstationGeometry.FANS.length];
		for (int i = 0; i < meshes.length; i++) {
			// 0 offset: the parts are exported in the block's own pixel space already, unlike the
			// condenser's crystal, which is modelled around zero and has to be moved to the middle.
			meshes[i] = new CubeMesh(WorkstationGeometry.FANS[i], 0.0F);
		}
		return meshes;
	}

	private final SpriteGetter sprites;

	public WorkstationBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		/** Nothing to draw: a loose casing has no moving parts. */
		boolean skip;
		/** Which half this is — the lower one owns the fans, the upper one the screens. */
		boolean lower;
		boolean lit;
		/** Model yaw for the block's facing, in the blockstate's own clockwise convention. */
		float yaw;
		float fanAngle;
		float tiltDegrees;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WorkstationBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition,
				breakProgress);
		BlockState block = entity.getBlockState();
		WorkstationPart part = block.hasProperty(WorkstationBlock.PART)
				? block.getValue(WorkstationBlock.PART) : WorkstationPart.SINGLE;
		state.skip = !part.assembled();
		if (state.skip) {
			return;
		}
		state.lower = part == WorkstationPart.LOWER;
		state.lit = block.getValue(WorkstationBlock.LIT);
		state.yaw = yawOf(block.getValue(WorkstationBlock.FACING));

		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
		float open = entity.animationClock().openProgress(gameTime, partialTicks);
		state.tiltDegrees = MONITOR_OPEN_DEGREES * open;
		// floorMod before the float: a world tens of millions of ticks old cannot hold consecutive
		// ticks in a float, and the blades would step and then stop.
		float time = Math.floorMod(gameTime, RotorSpin.TIME_WRAP) + partialTicks;
		// Rate scaled by the same progress, so the fans wind up with the screens instead of snapping
		// to full speed. Scaling the rate jumps the phase at each step, which is invisible only
		// because the blades are evenly spaced — the condition the centrifuge's rotor spells out.
		state.fanAngle = time * FAN_RADIANS_PER_TICK * open;
	}

	/**
	 * The blockstate's own mapping: the models are drawn facing north, and the variants turn them
	 * clockwise from there. The renderer draws in the block's unrotated space, so it has to repeat
	 * that turn itself.
	 */
	private static float yawOf(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0F;
			case SOUTH -> 180.0F;
			case WEST -> 270.0F;
			default -> 0.0F;
		};
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (state.skip) {
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(state.lit ? SPRITE_ON : SPRITE_OFF);
		RenderType type = state.lit ? TYPE_ON : TYPE_OFF;

		poseStack.pushPose();
		// Negated: a blockstate `y` turns the model clockwise seen from above, a positive turn about
		// +Y goes the other way.
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
		poseStack.translate(-0.5F, 0.0F, -0.5F);

		if (state.lower) {
			for (int i = 0; i < FANS.length; i++) {
				float[] pivot = WorkstationGeometry.FAN_PIVOTS[i];
				poseStack.pushPose();
				poseStack.translate(pivot[0] * PIXEL, pivot[1] * PIXEL, pivot[2] * PIXEL);
				poseStack.mulPose(Axis.ZP.rotation(
						state.fanAngle * WorkstationGeometry.FAN_DIRECTION[i]));
				poseStack.translate(-pivot[0] * PIXEL, -pivot[1] * PIXEL, -pivot[2] * PIXEL);
				submit(collector, poseStack, type, FANS[i], sprite, state.lightCoords);
				poseStack.popPose();
			}
		} else {
			float[] pivot = WorkstationGeometry.MONITORS_PIVOT;
			poseStack.pushPose();
			poseStack.translate(pivot[0] * PIXEL, pivot[1] * PIXEL, pivot[2] * PIXEL);
			poseStack.mulPose(Axis.XP.rotationDegrees(state.tiltDegrees));
			poseStack.translate(-pivot[0] * PIXEL, -pivot[1] * PIXEL, -pivot[2] * PIXEL);
			submit(collector, poseStack, type, MONITORS, sprite, state.lightCoords);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type,
			CubeMesh mesh, TextureAtlasSprite sprite, int light) {
		if (mesh.isEmpty()) {
			return;
		}
		// The block's own light, not full bright: these are painted panels and fan blades, not a
		// light source — the condenser's crystal is the exception, not the rule.
		collector.submitCustomGeometry(poseStack, type,
				(pose, consumer) -> mesh.emit(pose, consumer, sprite, light));
	}
}
