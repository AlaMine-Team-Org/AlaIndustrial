package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.UpgradeTableBlock;
import dev.alaindustrial.block.WorkstationPart;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Upgrade Table's press head: while the table works, it comes down onto the module lying on the
 * drill and lifts again, over and over.
 *
 * <p>Everything else on the bench stands still and is baked into the chunk mesh; only the head is
 * here, and it is its own group in the model source, so the static halves do not carry a second copy.
 * Only the upper half draws; the lower half and a loose casing draw nothing.
 *
 * <p>The stroke is a function of the game clock, so two players see the same press. Its depth is
 * scaled by the lower half's eased on/off clock, so the press winds up when the table starts and
 * parks raised when it stops instead of freezing mid-stroke.
 */
public final class UpgradeTableBlockEntityRenderer
		implements BlockEntityRenderer<UpgradeTableBlockEntity, UpgradeTableBlockEntityRenderer.State> {

	private static final SpriteId SPRITE_ON =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("upgrade_table_body"));
	private static final SpriteId SPRITE_OFF =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("upgrade_table_body_off"));
	private static final RenderType TYPE_ON =
			SPRITE_ON.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	private static final RenderType TYPE_OFF =
			SPRITE_OFF.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	/** One press every two seconds. */
	private static final int CYCLE_TICKS = 40;
	/** Share of the cycle spent coming down, then held on the module; the rest is the slower lift. */
	private static final float DOWN_END = 0.3F;
	private static final float HOLD_END = 0.45F;

	private static final float PIXEL = 1.0F / 16.0F;

	private static final CubeMesh HEAD = new CubeMesh(UpgradeTableGeometry.HEAD, 0.0F);

	private final SpriteGetter sprites;

	public UpgradeTableBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		boolean skip;
		boolean lit;
		float yaw;
		/** How far down the head is, in pixels. */
		float drop;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(UpgradeTableBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition,
				breakProgress);
		BlockState block = entity.getBlockState();
		state.skip = !block.hasProperty(UpgradeTableBlock.PART)
				|| block.getValue(UpgradeTableBlock.PART) != WorkstationPart.UPPER;
		if (state.skip) {
			return;
		}
		state.lit = block.getValue(UpgradeTableBlock.LIT);
		state.yaw = yawOf(block.getValue(UpgradeTableBlock.FACING));

		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
		float amplitude = entity.animationClock().pressAmplitude(gameTime, partialTicks);
		// floorMod before the float, as the workstation's fans do: an old world's tick count does not fit.
		float time = Math.floorMod(gameTime, RotorSpin.TIME_WRAP) + partialTicks;
		float phase = (time % CYCLE_TICKS) / CYCLE_TICKS;
		state.drop = UpgradeTableGeometry.PRESS_STROKE_PX * amplitude * stroke(phase);
	}

	/** 0 raised, 1 on the module: down quickly, a beat on the module, then a slower lift. */
	static float stroke(float phase) {
		if (phase < DOWN_END) {
			return smooth(phase / DOWN_END);
		}
		if (phase < HOLD_END) {
			return 1.0F;
		}
		return 1.0F - smooth((phase - HOLD_END) / (1.0F - HOLD_END));
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/** The blockstate's clockwise turn from north, which the renderer has to repeat itself. */
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
		// Negated: a blockstate `y` turns the model clockwise seen from above, a positive turn about +Y
		// goes the other way.
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		poseStack.translate(-0.5F, -state.drop * PIXEL, -0.5F);
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, type,
				(pose, consumer) -> HEAD.emit(pose, consumer, sprite, light));
		poseStack.popPose();
	}
}
