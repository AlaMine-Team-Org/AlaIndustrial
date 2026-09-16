package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.core.environment.WaterMillWheelGeometry;
import dev.alaindustrial.core.machine.ComponentTier;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Open, baked 3D water-wheel model for the LV water mill.
 *
 * <p>The wheel combines a continuous annular prism with solid cuboids baked once through
 * Minecraft's {@link ModelPart} pipeline. Full-depth spokes connect the hub to the rim, and
 * compact L-shaped buckets stay inside the round silhouette. The pieces overlap only inside
 * their joints, hiding sub-pixel cracks without coplanar exterior faces. Per frame, only the
 * root pose rotates.</p>
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
	/**
	 * Material sprites per wheel grade (MOD-385), so an upgraded mill reads as upgraded from across the
	 * river rather than only in its GUI. Unlike the wind mill's blades — a single flat quad with a
	 * dedicated mod texture — the wheel is built from model parts textured with whole-block sprites, so
	 * a grade is expressed by swapping which block it is MADE of: tempered iron for the reinforced wheel,
	 * a dedicated dark circuit-board texture with gold traces for the advanced one, matching its item
	 * icon. Vanilla {@code emerald_block} was tried first and rejected on sight: it is one of the most
	 * saturated greens in the game and read as "a wheel made of emeralds" rather than as electronics.
	 * Its own {@code water_mill_wheel_3d.png} plays no part here — that file is a documentation render,
	 * and since MOD-506 it lives in {@code tools/renders_refs/} rather than in the shipped assets.
	 */
	private static final SpriteId REINFORCED_BODY =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("tempered_iron_block"));
	private static final SpriteId REINFORCED_SPOKE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("tempered_iron_plate_block"));
	private static final SpriteId ADVANCED_BODY =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("circuit_board_block"));
	private static final SpriteId ADVANCED_SPOKE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("circuit_board_block"));
	private static final SpriteId ADVANCED_AXLE =
			Sheets.BLOCKS_MAPPER.defaultNamespaceApply("gold_block");

	private static final int RIM_SEGMENTS = 24;
	private static final int SPOKE_COUNT = 8;
	private static final int PADDLE_COUNT = 12;
	/**
	 * Inner radius of the annular rim prism — how wide the ring is, not how far the wheel hangs off
	 * the block. It only coincidentally shares its numeric value with {@link #WHEEL_PUSH} today, so
	 * unlike the constants below it stays a local literal rather than a read of
	 * {@link WaterMillWheelGeometry#DISC_PUSH}: collapsing the two would assert a relationship
	 * between ring width and push distance that does not actually exist (MOD-635).
	 */
	private static final float RIM_INNER = 1.02F;
	/** Outer radius of the rim, shared with the interference/clearance checks (MOD-635). */
	private static final float RIM_OUTER = (float) WaterMillWheelGeometry.DISC_HALF_SIZE;
	private static final float RIM_FRONT = -(float) WaterMillWheelGeometry.DISC_HALF_DEPTH;
	private static final float RIM_BACK = (float) WaterMillWheelGeometry.DISC_HALF_DEPTH;
	private static final float PADDLE_TILT = (float) Math.toRadians(8.0);
	/**
	 * How far in front of the mill's centre the axle sits, along its facing, in blocks. Read from the
	 * class the interference and clearance checks read too: while each kept its own number, the wind
	 * mill's rotor had exactly this drift and stalled mills whose drawn discs never met (MOD-634); this
	 * keeps the water mill from repeating it (MOD-635).
	 */
	private static final float WHEEL_PUSH = (float) WaterMillWheelGeometry.DISC_PUSH;
	private static final RenderType RENDER_TYPE =
			PLANKS.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	private final Model.Simple planksModel;
	private final Model.Simple timberModel;
	private final Model.Simple axleModel;
	private final SpriteGetter sprites;
	/** Farthest any drawn corner gets from the axle, in blocks — the paddle lips, not the rim. */
	private final float wheelReach;
	/** Farthest any drawn corner gets from the wheel's middle plane, in blocks — the axle's ends. */
	private final float wheelDepth;

	public WaterMillWheelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(MODEL_LAYER);
		this.planksModel = new Model.Simple(root.getChild("planks"),
				ignored -> PLANKS.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.timberModel = new Model.Simple(root.getChild("timbers"),
				ignored -> SPOKE_WOOD.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.axleModel = new Model.Simple(root.getChild("axle"),
				ignored -> AXLE_METAL.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.sprites = context.sprites();
		// Measured off the baked model rather than restated: the paddles and the axle both stick out past
		// the rim, and a hand-kept number would go stale the day either is resized. The rim is drawn by
		// hand, outside the model, so it seeds the measurement.
		float[] extent = {RIM_OUTER, RIM_BACK};
		root.getExtentsForGui(new PoseStack(), corner -> {
			extent[0] = Math.max(extent[0], (float) Math.hypot(corner.x(), corner.y()));
			extent[1] = Math.max(extent[1], Math.abs(corner.z()));
		});
		this.wheelReach = extent[0];
		this.wheelDepth = extent[1];
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
		state.facing = facing(entity.getBlockState());
		state.production = entity.getDataAccess().get(2);
		net.minecraft.world.item.ItemStack wheel = entity.getItem(WaterMillBlockEntity.WHEEL_SLOT);
		state.installed = !wheel.isEmpty();
		// Grade → material sprites (MOD-385). Read straight off the slot: the machine's inventory is part
		// of getUpdateTag (saveWithoutMetadata), so every watching client already has it — no packet needed.
		ComponentTier tier = state.installed
				? ComponentTier.forItemPath(BuiltInRegistries.ITEM.getKey(wheel.getItem()).getPath())
				: null;
		if (tier == ComponentTier.WATER_MILL_WHEEL_REINFORCED) {
			state.body = REINFORCED_BODY;
			state.spoke = REINFORCED_SPOKE;
			state.axle = AXLE_METAL;
		} else if (tier == ComponentTier.WATER_MILL_WHEEL_ADVANCED) {
			state.body = ADVANCED_BODY;
			state.spoke = ADVANCED_SPOKE;
			state.axle = ADVANCED_AXLE;
		} else {
			state.body = PLANKS;
			state.spoke = SPOKE_WOOD;
			state.axle = AXLE_METAL;
		}
		// MOD-175/MOD-179: when this mill's wheel overlaps a neighbour's (interference) or would clip
		// through a solid block (obstruction), hide it entirely instead of drawing a broken wheel. The
		// mode rides the maxProgress sync channel (slot 3). MODE_NO_WATER does NOT hide the wheel — a
		// dry wheel stands still but stays rendered.
		int mode = entity.getDataAccess().get(3);
		state.interfered = mode == WaterMillBlockEntity.MODE_INTERFERENCE
				|| mode == WaterMillBlockEntity.MODE_OBSTRUCTED;
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
		if (!state.installed || state.interfered) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		rotateToFacing(poseStack, state.facing);
		// Keep the axle exactly on the machine block's centre, both vertically and horizontally.
		poseStack.translate(0.0F, 0.0F, -WHEEL_PUSH);
		poseStack.mulPose(Axis.ZP.rotation(state.angle));

		renderContinuousRim(poseStack, collector, sprites.get(state.body), state);
		collector.submitModel(planksModel, Unit.INSTANCE, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				state.body, sprites, 0, state.breakProgress);
		collector.submitModel(timberModel, Unit.INSTANCE, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				state.spoke, sprites, 0, state.breakProgress);
		collector.submitModel(axleModel, Unit.INSTANCE, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				state.axle, sprites, 0, state.breakProgress);
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

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer, ahead of
	 * {@link #shouldRenderOffScreen} (MOD-633). Its default is the mill's own block, so a wheel reaching
	 * into view while that block was past the edge of the screen was culled whole. The box is the wheel:
	 * {@link #wheelReach} across the plane it turns in, {@link #wheelDepth} along the axle.
	 *
	 * <p>No {@code @Override}: only NeoForge's {@code BlockEntityRenderer} declares this method, and vanilla
	 * — so Fabric — never tests a block entity against the frustum. {@code OffScreenRendererBoxTest} on the
	 * NeoForge lane fails if it stops overriding.
	 */
	public AABB getRenderBoundingBox(T blockEntity) {
		BlockPos pos = blockEntity.getBlockPos();
		Direction facing = facing(blockEntity.getBlockState());
		double cx = pos.getX() + 0.5 + facing.getStepX() * WHEEL_PUSH;
		double cy = pos.getY() + 0.5;
		double cz = pos.getZ() + 0.5 + facing.getStepZ() * WHEEL_PUSH;
		double hx = facing.getAxis() == Direction.Axis.X ? wheelDepth : wheelReach;
		double hz = facing.getAxis() == Direction.Axis.Z ? wheelDepth : wheelReach;
		return new AABB(cx - hx, cy - wheelReach, cz - hz, cx + hx, cy + wheelReach, cz + hz);
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	private static Direction facing(BlockState blockState) {
		return blockState.hasProperty(HorizontalMachineBlock.FACING)
				? blockState.getValue(HorizontalMachineBlock.FACING)
				: Direction.NORTH;
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
		private boolean interfered;
		private float angle;
		private boolean installed;
		/** Material sprites of the installed wheel grade (MOD-385); default to the wooden wheel's. */
		private SpriteId body = PLANKS;
		private SpriteId spoke = SPOKE_WOOD;
		private SpriteId axle = AXLE_METAL;
	}
}
