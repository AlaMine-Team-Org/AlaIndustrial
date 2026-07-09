package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Renders the large decorative water wheel, chute and water stream for the LV water mill. */
public final class WaterMillWheelBlockEntityRenderer<T extends MachineBlockEntity>
		implements BlockEntityRenderer<T, WaterMillWheelBlockEntityRenderer.State> {
	private static final SpriteId RIM =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("water_mill_wheel_rim_3d"));
	private static final SpriteId SPOKES =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("water_mill_wheel_spokes_3d"));
	private static final SpriteId BUCKETS =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("water_mill_wheel_buckets_3d"));
	private static final RenderType RENDER_TYPE = RIM.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	private static final int RIM_SEGMENTS = 16;
	private static final int BUCKET_COUNT = 12;
	private static final int SPOKES_COUNT = 8;
	private static final float FRONT_Z0 = -0.42F;
	private static final float FRONT_Z1 = -0.30F;
	private static final float BACK_Z0 = 0.30F;
	private static final float BACK_Z1 = 0.42F;

	private final SpriteGetter sprites;

	public WaterMillWheelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		state.facing = entity.getBlockState().hasProperty(HorizontalMachineBlock.FACING)
				? entity.getBlockState().getValue(HorizontalMachineBlock.FACING)
				: Direction.NORTH;
		state.production = entity.getDataAccess().get(2);
		state.angle = state.production <= 0 ? 0.0F : rotationAngle(entity, partialTicks, state.production);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		rotateToFacing(poseStack, state.facing);

		poseStack.pushPose();
		poseStack.translate(0.0F, -0.42F, -1.02F);
		poseStack.mulPose(Axis.ZP.rotation(state.angle));
		renderWheelGeometry(poseStack, submitNodeCollector);
		poseStack.popPose();

		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	private void renderWheelGeometry(PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
		TextureAtlasSprite rim = sprites.get(RIM);
		TextureAtlasSprite spokes = sprites.get(SPOKES);
		TextureAtlasSprite buckets = sprites.get(BUCKETS);
		renderSideRing(poseStack, submitNodeCollector, rim, FRONT_Z0, FRONT_Z1);
		renderSideRing(poseStack, submitNodeCollector, rim, BACK_Z0, BACK_Z1);
		renderBucketRing(poseStack, submitNodeCollector, buckets);
		renderSpokes(poseStack, submitNodeCollector, spokes, FRONT_Z0, FRONT_Z1);
		renderSpokes(poseStack, submitNodeCollector, spokes, BACK_Z0, BACK_Z1);
		renderHub(poseStack, submitNodeCollector, spokes, rim);
	}

	private void renderSideRing(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			TextureAtlasSprite rim, float z0, float z1) {
		for (int i = 0; i < RIM_SEGMENTS; i++) {
			float step = 360.0F / RIM_SEGMENTS;
			float start = i * step - step * 0.48F;
			float end = i * step + step * 0.48F;
			submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE,
					(pose, consumer) -> renderRingSegment(pose, consumer, rim,
							0.98F, 1.33F, start, end, z0, z1, LightCoordsUtil.FULL_BRIGHT));
		}
	}

	private void renderBucketRing(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			TextureAtlasSprite buckets) {
		for (int i = 0; i < BUCKET_COUNT; i++) {
			float angle = i * (360.0F / BUCKET_COUNT) + 360.0F / BUCKET_COUNT / 2.0F;
			poseStack.pushPose();
			poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
			submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE,
					(pose, consumer) -> renderBox(pose, consumer, buckets,
							1.12F, -0.10F, -0.36F, 1.42F, 0.10F, 0.36F, LightCoordsUtil.FULL_BRIGHT));
			poseStack.popPose();
		}
	}

	private void renderSpokes(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			TextureAtlasSprite spokes, float z0, float z1) {
		for (int i = 0; i < SPOKES_COUNT; i++) {
			float angle = i * (360.0F / SPOKES_COUNT);
			poseStack.pushPose();
			poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
			submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE,
					(pose, consumer) -> renderBox(pose, consumer, spokes,
							0.24F, -0.045F, z0, 1.02F, 0.045F, z1, LightCoordsUtil.FULL_BRIGHT));
			poseStack.popPose();
		}
	}

	private void renderHub(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			TextureAtlasSprite spokes, TextureAtlasSprite rim) {
		submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE,
				(pose, consumer) -> renderBox(pose, consumer, spokes,
						-0.25F, -0.25F, FRONT_Z0, 0.25F, 0.25F, BACK_Z1, LightCoordsUtil.FULL_BRIGHT));
		submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE,
				(pose, consumer) -> renderBox(pose, consumer, rim,
						-0.11F, -0.11F, -0.62F, 0.11F, 0.11F, 0.62F, LightCoordsUtil.FULL_BRIGHT));
	}

	private static float rotationAngle(MachineBlockEntity entity, float partialTicks, int production) {
		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
		float radiansPerTick = 0.045F + Math.min(production, 4) * 0.055F;
		return (gameTime + partialTicks) * radiansPerTick;
	}

	private static void rotateToFacing(PoseStack poseStack, Direction facing) {
		switch (facing) {
			case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
			case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
			case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
			default -> {
			}
		}
	}

	private static void renderBox(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			float x0, float y0, float z0, float x1, float y1, float z1, int light) {
		float u0 = sprite.getU0();
		float u1 = sprite.getU1();
		float v0 = sprite.getV0();
		float v1 = sprite.getV1();
		// north/front
		vertex(pose, consumer, x0, y0, z0, u0, v1, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, x1, y0, z0, u1, v1, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, x1, y1, z0, u1, v0, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, x0, y1, z0, u0, v0, light, 0.0F, 0.0F, -1.0F);
		// south/back
		vertex(pose, consumer, x0, y1, z1, u0, v0, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, x1, y1, z1, u1, v0, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, x1, y0, z1, u1, v1, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, x0, y0, z1, u0, v1, light, 0.0F, 0.0F, 1.0F);
		// west
		vertex(pose, consumer, x0, y0, z1, u0, v1, light, -1.0F, 0.0F, 0.0F);
		vertex(pose, consumer, x0, y0, z0, u1, v1, light, -1.0F, 0.0F, 0.0F);
		vertex(pose, consumer, x0, y1, z0, u1, v0, light, -1.0F, 0.0F, 0.0F);
		vertex(pose, consumer, x0, y1, z1, u0, v0, light, -1.0F, 0.0F, 0.0F);
		// east
		vertex(pose, consumer, x1, y1, z1, u0, v0, light, 1.0F, 0.0F, 0.0F);
		vertex(pose, consumer, x1, y1, z0, u1, v0, light, 1.0F, 0.0F, 0.0F);
		vertex(pose, consumer, x1, y0, z0, u1, v1, light, 1.0F, 0.0F, 0.0F);
		vertex(pose, consumer, x1, y0, z1, u0, v1, light, 1.0F, 0.0F, 0.0F);
		// top
		vertex(pose, consumer, x0, y1, z0, u0, v1, light, 0.0F, 1.0F, 0.0F);
		vertex(pose, consumer, x1, y1, z0, u1, v1, light, 0.0F, 1.0F, 0.0F);
		vertex(pose, consumer, x1, y1, z1, u1, v0, light, 0.0F, 1.0F, 0.0F);
		vertex(pose, consumer, x0, y1, z1, u0, v0, light, 0.0F, 1.0F, 0.0F);
		// bottom
		vertex(pose, consumer, x0, y0, z1, u0, v0, light, 0.0F, -1.0F, 0.0F);
		vertex(pose, consumer, x1, y0, z1, u1, v0, light, 0.0F, -1.0F, 0.0F);
		vertex(pose, consumer, x1, y0, z0, u1, v1, light, 0.0F, -1.0F, 0.0F);
		vertex(pose, consumer, x0, y0, z0, u0, v1, light, 0.0F, -1.0F, 0.0F);
	}

	private static void renderRingSegment(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			float innerRadius, float outerRadius, float startDegrees, float endDegrees, float z0, float z1, int light) {
		float start = (float) Math.toRadians(startDegrees);
		float end = (float) Math.toRadians(endDegrees);
		float sx = (float) Math.cos(start);
		float sy = (float) Math.sin(start);
		float ex = (float) Math.cos(end);
		float ey = (float) Math.sin(end);

		float x0 = innerRadius * sx;
		float y0 = innerRadius * sy;
		float x1 = outerRadius * sx;
		float y1 = outerRadius * sy;
		float x2 = outerRadius * ex;
		float y2 = outerRadius * ey;
		float x3 = innerRadius * ex;
		float y3 = innerRadius * ey;

		float u0 = sprite.getU0();
		float u1 = sprite.getU1();
		float v0 = sprite.getV0();
		float v1 = sprite.getV1();

		vertex(pose, consumer, x0, y0, z0, u0, v1, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, x1, y1, z0, u1, v1, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, x2, y2, z0, u1, v0, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, x3, y3, z0, u0, v0, light, 0.0F, 0.0F, -1.0F);

		vertex(pose, consumer, x3, y3, z1, u0, v0, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, x2, y2, z1, u1, v0, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, x1, y1, z1, u1, v1, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, x0, y0, z1, u0, v1, light, 0.0F, 0.0F, 1.0F);

		vertex(pose, consumer, x1, y1, z0, u0, v1, light, sx, sy, 0.0F);
		vertex(pose, consumer, x1, y1, z1, u1, v1, light, sx, sy, 0.0F);
		vertex(pose, consumer, x2, y2, z1, u1, v0, light, ex, ey, 0.0F);
		vertex(pose, consumer, x2, y2, z0, u0, v0, light, ex, ey, 0.0F);

		vertex(pose, consumer, x3, y3, z0, u0, v1, light, -sx, -sy, 0.0F);
		vertex(pose, consumer, x3, y3, z1, u1, v1, light, -sx, -sy, 0.0F);
		vertex(pose, consumer, x0, y0, z1, u1, v0, light, -ex, -ey, 0.0F);
		vertex(pose, consumer, x0, y0, z0, u0, v0, light, -ex, -ey, 0.0F);

		vertex(pose, consumer, x0, y0, z0, u0, v1, light, sx, sy, 0.0F);
		vertex(pose, consumer, x0, y0, z1, u1, v1, light, sx, sy, 0.0F);
		vertex(pose, consumer, x1, y1, z1, u1, v0, light, sx, sy, 0.0F);
		vertex(pose, consumer, x1, y1, z0, u0, v0, light, sx, sy, 0.0F);

		vertex(pose, consumer, x2, y2, z0, u0, v1, light, ex, ey, 0.0F);
		vertex(pose, consumer, x2, y2, z1, u1, v1, light, ex, ey, 0.0F);
		vertex(pose, consumer, x3, y3, z1, u1, v0, light, ex, ey, 0.0F);
		vertex(pose, consumer, x3, y3, z0, u0, v0, light, ex, ey, 0.0F);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
			float u, float v, int light, float normalX, float normalY, float normalZ) {
		consumer.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, normalX, normalY, normalZ);
	}

	public static final class State extends BlockEntityRenderState {
		private Direction facing = Direction.NORTH;
		private int production;
		private float angle;
	}
}
