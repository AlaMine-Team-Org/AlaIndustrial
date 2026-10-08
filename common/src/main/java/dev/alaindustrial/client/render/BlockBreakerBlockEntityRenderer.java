package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.BlockBreakerBlock;
import dev.alaindustrial.block.entity.BlockBreakerBlockEntity;
import dev.alaindustrial.compat.client.Poses;
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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Block Breaker's auger (MOD-787): the stone-bladed bit at the front, turning while the machine breaks.
 *
 * <p>The housing is the block model; only the auger is here, from {@link BlockBreakerGeometry}, which the
 * model source generates together with the housing. It is turned to the block's facing the same way the
 * blockstate turns the housing — {@code x} first, then {@code y} — and spins about the front axis only
 * while the machine is actually breaking (the block's {@code lit} state, which every client receives with
 * the block). Standing, unpowered or without a tool, it rests where the model put it.
 *
 * <p>The block lets light through (see {@code BlockBreakerBlock#getLightDampening}), so its own cell is lit
 * and the auger takes that light; while the block still blocked light, the auger came out black.
 */
public final class BlockBreakerBlockEntityRenderer
		implements BlockEntityRenderer<BlockBreakerBlockEntity, BlockBreakerBlockEntityRenderer.State> {

	private static final SpriteId SPRITE = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("block_breaker_body"));
	private static final RenderType TYPE = SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	private static final float PIXEL = 1.0F / 16.0F;

	private static final CubeMesh BIT = new CubeMesh(BlockBreakerGeometry.BIT, 0.0F);

	private final SpriteGetter sprites;

	public BlockBreakerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		float xRot;
		float yRot;
		/** The auger's turn, in degrees. */
		float angle;
		int light;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BlockBreakerBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		BlockState block = entity.getBlockState();
		Direction facing = block.hasProperty(BlockBreakerBlock.FACING)
				? block.getValue(BlockBreakerBlock.FACING) : Direction.NORTH;
		state.xRot = xRotOf(facing);
		state.yRot = yRotOf(facing);
		boolean working = block.hasProperty(BlockBreakerBlock.LIT) && block.getValue(BlockBreakerBlock.LIT);
		Level level = entity.getLevel();
		if (working && level != null) {
			// floorMod before the float: an old world's tick count does not fit a float's mantissa.
			float seconds = (Math.floorMod(level.getGameTime(), RotorSpin.TIME_WRAP) + partialTicks) / 20.0F;
			state.angle = seconds * BlockBreakerGeometry.TURNS_PER_SECOND * 360.0F;
		} else {
			state.angle = 0.0F;
		}
		state.light = level == null
				? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(level, entity.getBlockPos());
	}

	/** The blockstate's {@code x}: the front (north) turned up or down. */
	private static float xRotOf(Direction facing) {
		return switch (facing) {
			case UP -> 270.0F;
			case DOWN -> 90.0F;
			default -> 0.0F;
		};
	}

	/** The blockstate's {@code y}, clockwise from north seen from above. */
	private static float yRotOf(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0F;
			case SOUTH -> 180.0F;
			case WEST -> 270.0F;
			default -> 0.0F;
		};
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		TextureAtlasSprite sprite = this.sprites.get(SPRITE);
		int light = state.light;
		poseStack.pushPose();
		// Same turn as the blockstate: a model rotation of x then y, both clockwise, hence negated.
		poseStack.translate(0.5F, 0.5F, 0.5F);
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(-state.yRot));
		Poses.rotate(poseStack, Axis.XP.rotationDegrees(-state.xRot));
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		// The spin, about the front axis through the auger's hub.
		poseStack.translate(BlockBreakerGeometry.PIVOT_X * PIXEL, BlockBreakerGeometry.PIVOT_Y * PIXEL,
				BlockBreakerGeometry.PIVOT_Z * PIXEL);
		Poses.rotate(poseStack, Axis.ZP.rotationDegrees(state.angle));
		poseStack.translate(-BlockBreakerGeometry.PIVOT_X * PIXEL, -BlockBreakerGeometry.PIVOT_Y * PIXEL,
				-BlockBreakerGeometry.PIVOT_Z * PIXEL);
		collector.submitCustomGeometry(poseStack, TYPE, (pose, consumer) -> BIT.emit(pose, consumer, sprite, light));
		poseStack.popPose();
	}
}
