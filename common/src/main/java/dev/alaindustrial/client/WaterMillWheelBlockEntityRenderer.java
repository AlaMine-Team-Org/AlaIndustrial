package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
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
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Closed, baked 3D water-wheel model for the LV water mill.
 *
 * <p>The wheel is a hierarchy of solid cuboids baked once through Minecraft's
 * {@link ModelPart} pipeline. A single wide rim replaces the old pair of disconnected
 * side rings, each spoke is one full-depth timber, and every paddle is a thick closed
 * board sunk into the rim. The pieces overlap by one model unit at their joints, hiding
 * sub-pixel cracks without coplanar exterior faces. Per frame, only the root pose rotates.</p>
 */
public final class WaterMillWheelBlockEntityRenderer<T extends WaterMillBlockEntity>
		implements BlockEntityRenderer<T, WaterMillWheelBlockEntityRenderer.State> {
	public static final ModelLayerLocation MODEL_LAYER =
			new ModelLayerLocation(Industrialization.id("water_mill_wheel"), "main");

	private static final SpriteId PLANKS = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("oak_planks");
	private static final SpriteId SPOKE_WOOD =
			Sheets.BLOCKS_MAPPER.defaultNamespaceApply("stripped_oak_log");
	private static final SpriteId AXLE_METAL =
			Sheets.BLOCKS_MAPPER.defaultNamespaceApply("cut_copper");

	private static final int RIM_SEGMENTS = 24;
	private static final int SPOKE_COUNT = 8;
	private static final int PADDLE_COUNT = 12;
	private static final float RIM_INNER = 1.02F;
	private static final float RIM_OUTER = 1.32F;
	private static final float RIM_FRONT = -0.4375F;
	private static final float RIM_BACK = 0.4375F;
	private static final float PADDLE_TILT = (float) Math.toRadians(8.0);
	private static final RenderType RENDER_TYPE =
			PLANKS.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	private final Model.Simple planksModel;
	private final Model.Simple timberModel;
	private final Model.Simple axleModel;
	private final SpriteGetter sprites;

	public WaterMillWheelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(MODEL_LAYER);
		this.planksModel = new Model.Simple(root.getChild("planks"),
				ignored -> PLANKS.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.timberModel = new Model.Simple(root.getChild("timbers"),
				ignored -> SPOKE_WOOD.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.axleModel = new Model.Simple(root.getChild("axle"),
				ignored -> AXLE_METAL.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.sprites = context.sprites();
	}

	/**
	 * Original cuboid construction for the wheel. Geometry coordinates are model pixels
	 * ({@code 16 == one block}); every cube receives all six faces from vanilla's model baker.
	 */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition planks = root.addOrReplaceChild("planks", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition timbers = root.addOrReplaceChild("timbers", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition axle = root.addOrReplaceChild("axle", CubeListBuilder.create(), PartPose.ZERO);

		float spokeStep = (float) (Math.PI * 2.0 / SPOKE_COUNT);
		for (int i = 0; i < SPOKE_COUNT; i++) {
			float angle = i * spokeStep;
			// 3.5..17.2: half a model unit under the hub and almost one unit under the rim.
			timbers.addOrReplaceChild("spoke_" + i,
					CubeListBuilder.create().texOffs(0, 0).addBox(
							3.5F, -1.55F, -5.25F, 13.7F, 3.1F, 10.5F),
					PartPose.rotation(0.0F, 0.0F, angle));
		}

		float paddleStep = (float) (Math.PI * 2.0 / PADDLE_COUNT);
		for (int i = 0; i < PADDLE_COUNT; i++) {
			float radialAngle = i * paddleStep;
			float boardAngle = radialAngle + PADDLE_TILT;
			// A compact L-shaped bucket: a broad radial floor and a short outer lip. Both are
			// closed cuboids, inset from the ring faces and overlapping only inside the joint.
			PartDefinition paddle = planks.addOrReplaceChild("paddle_" + i,
					CubeListBuilder.create().texOffs(0, 0).addBox(
							-3.2F, -1.25F, -6.0F, 6.4F, 2.5F, 12.0F),
					PartPose.offsetAndRotation(
							18.7F * (float) Math.cos(radialAngle),
							18.7F * (float) Math.sin(radialAngle),
							0.0F, 0.0F, 0.0F, boardAngle));
			paddle.addOrReplaceChild("lip",
					CubeListBuilder.create().texOffs(0, 0).addBox(
							-0.8F, -1.25F, -5.8F, 1.6F, 4.6F, 11.6F),
					// At x=3.9 the lip's innermost rotated corner is outside the 21.12px
					// rim radius. It still overlaps the floor by 0.1px, but never intersects
					// the rim skin, removing the depth-order pop while the wheel rotates.
					PartPose.offset(3.9F, 0.0F, 0.0F));
		}

		// A massive closed timber hub covers every inner spoke joint.
		timbers.addOrReplaceChild("hub",
				CubeListBuilder.create().texOffs(0, 0).addBox(
						-5.0F, -5.0F, -6.0F, 10.0F, 10.0F, 12.0F),
				PartPose.rotation(0.0F, 0.0F, (float) (Math.PI / 4.0)));
		// The copper axle passes completely through the hub and machine-side bearing.
		axle.addOrReplaceChild("shaft",
				CubeListBuilder.create().texOffs(0, 0).addBox(
						-2.0F, -2.0F, -10.5F, 4.0F, 4.0F, 21.0F),
				PartPose.rotation(0.0F, 0.0F, (float) (Math.PI / 4.0)));

		return LayerDefinition.create(mesh, 64, 64);
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
		state.installed = !entity.getItem(WaterMillBlockEntity.WHEEL_SLOT).isEmpty();
		state.angle = state.production <= 0 ? 0.0F : rotationAngle(entity, partialTicks, state.production);
		Level level = entity.getLevel();
		if (level != null) {
			state.lightCoords = LightCoordsUtil.getLightCoords(
					level, entity.getBlockPos().relative(state.facing));
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (!state.installed) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		rotateToFacing(poseStack, state.facing);
		// Keep the axle exactly on the machine block's centre, both vertically and horizontally.
		poseStack.translate(0.0F, 0.0F, -1.02F);
		poseStack.mulPose(Axis.ZP.rotation(state.angle));

		renderContinuousRim(poseStack, collector, sprites.get(PLANKS), state);
		collector.submitModel(planksModel, Unit.INSTANCE, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				PLANKS, sprites, 0, state.breakProgress);
		collector.submitModel(timberModel, Unit.INSTANCE, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				SPOKE_WOOD, sprites, 0, state.breakProgress);
		collector.submitModel(axleModel, Unit.INSTANCE, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				AXLE_METAL, sprites, 0, state.breakProgress);
		poseStack.popPose();
	}

	/**
	 * One continuous annular prism. Adjacent sectors share their boundary vertices exactly;
	 * unlike overlapping tangent cuboids, there are no coincident front/back faces to z-fight.
	 */
	private void renderContinuousRim(PoseStack poseStack, SubmitNodeCollector collector,
			TextureAtlasSprite sprite, State state) {
		Shade shade = new Shade(state.facing, state.angle);
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) -> {
			float step = 360.0F / RIM_SEGMENTS;
			for (int i = 0; i < RIM_SEGMENTS; i++) {
				float start = (i - 0.5F) * step;
				float end = (i + 0.5F) * step;
				renderRingSegment(pose, consumer, sprite, start, end, state.lightCoords, shade);
			}
		});
	}

	private static void renderRingSegment(PoseStack.Pose pose, VertexConsumer consumer,
			TextureAtlasSprite sprite, float startDegrees, float endDegrees, int light, Shade shade) {
		float start = (float) Math.toRadians(startDegrees);
		float end = (float) Math.toRadians(endDegrees);
		float sx = (float) Math.cos(start);
		float sy = (float) Math.sin(start);
		float ex = (float) Math.cos(end);
		float ey = (float) Math.sin(end);
		float mx = (float) Math.cos((start + end) * 0.5F);
		float my = (float) Math.sin((start + end) * 0.5F);

		float x0 = RIM_INNER * sx;
		float y0 = RIM_INNER * sy;
		float x1 = RIM_OUTER * sx;
		float y1 = RIM_OUTER * sy;
		float x2 = RIM_OUTER * ex;
		float y2 = RIM_OUTER * ey;
		float x3 = RIM_INNER * ex;
		float y3 = RIM_INNER * ey;
		float radial = RIM_OUTER - RIM_INNER;
		float arc = Math.min(1.0F, (RIM_INNER + RIM_OUTER) * 0.5F * (end - start));
		float depth = RIM_BACK - RIM_FRONT;

		quad(pose, consumer, sprite, light, shade.at(0.0F, 0.0F, -1.0F), 0.0F, 0.0F, -1.0F,
				x0, y0, RIM_FRONT, x1, y1, RIM_FRONT, x2, y2, RIM_FRONT, x3, y3, RIM_FRONT,
				0.0F, radial, 0.0F, 0.0F, arc, 0.0F, arc, radial);
		quad(pose, consumer, sprite, light, shade.at(0.0F, 0.0F, 1.0F), 0.0F, 0.0F, 1.0F,
				x3, y3, RIM_BACK, x2, y2, RIM_BACK, x1, y1, RIM_BACK, x0, y0, RIM_BACK,
				arc, radial, arc, 0.0F, 0.0F, 0.0F, 0.0F, radial);
		quad(pose, consumer, sprite, light, shade.at(mx, my, 0.0F), mx, my, 0.0F,
				x1, y1, RIM_FRONT, x1, y1, RIM_BACK, x2, y2, RIM_BACK, x2, y2, RIM_FRONT,
				0.0F, depth, 0.0F, 0.0F, arc, 0.0F, arc, depth);
		quad(pose, consumer, sprite, light, shade.at(-mx, -my, 0.0F), -mx, -my, 0.0F,
				x3, y3, RIM_FRONT, x3, y3, RIM_BACK, x0, y0, RIM_BACK, x0, y0, RIM_FRONT,
				0.0F, depth, 0.0F, 0.0F, arc, 0.0F, arc, depth);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			int light, float shade, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		// Exterior winding used by Minecraft's culled item pipeline.
		vertex(pose, consumer, sprite, light, shade, nx, ny, nz, ax, ay, az, ua, va);
		vertex(pose, consumer, sprite, light, shade, nx, ny, nz, bx, by, bz, ub, vb);
		vertex(pose, consumer, sprite, light, shade, nx, ny, nz, cx, cy, cz, uc, vc);
		vertex(pose, consumer, sprite, light, shade, nx, ny, nz, dx, dy, dz, ud, vd);
		// Reverse winding guarantees that loader/facing transforms cannot make the solid ring
		// disappear. Culling means exactly one copy is visible from either side, so the coincident
		// vertices do not z-fight.
		vertex(pose, consumer, sprite, light, shade, -nx, -ny, -nz, dx, dy, dz, ud, vd);
		vertex(pose, consumer, sprite, light, shade, -nx, -ny, -nz, cx, cy, cz, uc, vc);
		vertex(pose, consumer, sprite, light, shade, -nx, -ny, -nz, bx, by, bz, ub, vb);
		vertex(pose, consumer, sprite, light, shade, -nx, -ny, -nz, ax, ay, az, ua, va);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			int light, float shade, float nx, float ny, float nz,
			float x, float y, float z, float u, float v) {
		consumer.addVertex(pose, x, y, z)
				.setColor(shade, shade, shade, 1.0F)
				.setUv(sprite.getU(u), sprite.getV(v))
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	private static float rotationAngle(WaterMillBlockEntity entity, float partialTicks, int production) {
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

	private static final class Shade {
		private final Direction facing;
		private final float cos;
		private final float sin;

		Shade(Direction facing, float angle) {
			this.facing = facing;
			this.cos = (float) Math.cos(angle);
			this.sin = (float) Math.sin(angle);
		}

		float at(float nx, float ny, float nz) {
			float wx = nx * cos - ny * sin;
			float wy = nx * sin + ny * cos;
			float ax;
			float az;
			switch (facing) {
				case SOUTH -> {
					ax = -wx;
					az = -nz;
				}
				case WEST -> {
					ax = nz;
					az = -wx;
				}
				case EAST -> {
					ax = -nz;
					az = wx;
				}
				default -> {
					ax = wx;
					az = nz;
				}
			}
			return Math.min(1.0F,
					0.6F * ax * ax + wy * wy * (3.0F + wy) * 0.25F + 0.8F * az * az);
		}
	}

	public static final class State extends BlockEntityRenderState {
		private Direction facing = Direction.NORTH;
		private int production;
		private float angle;
		private boolean installed;
	}
}
