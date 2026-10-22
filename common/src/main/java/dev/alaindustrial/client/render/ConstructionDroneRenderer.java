package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.DroneNestBlockEntity;
import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.core.drone.DroneFlightPath;
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
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the tier-1 builder drone on its nest and in flight (MOD-858). The drone is the nest's state, not an entity
 * (as the garden drone, MOD-277): the server sends each leg once — target, length, start and the route's corners — and
 * this renderer works out where the drone is from the client's own clock, along the route the server found through
 * open cells only (owner's round 2: it never flies through a block). The speed curve is the shared
 * {@link DroneFlight#accelerate}, so it leaves and lands with no jolt, and the route's corners are rounded off
 * ({@link DroneFlightPath#rounded}) so a turn reads as a curve.
 *
 * <p><b>It flies like a drone.</b> The airframe leans the way it is going and the harder it speeds up, brakes or turns
 * — the lean is the drone's velocity and acceleration, both taken from the drawn path itself a moment before and after
 * now, so a turn banks into it and a stop sits it back. It bobs a little in the air and the rotors spin up in flight.
 * On a cell it stands on top of it — on the block it takes away, or over the empty cell it fills.
 */
public final class ConstructionDroneRenderer
		implements BlockEntityRenderer<DroneNestBlockEntity, ConstructionDroneRenderer.State> {

	private static final CubeMesh BODY = new CubeMesh(ConstructionDroneGeometry.BODY, 0.0F);
	private static final CubeMesh[] ROTORS = {
			new CubeMesh(ConstructionDroneGeometry.ROTOR_0, 0.0F),
			new CubeMesh(ConstructionDroneGeometry.ROTOR_1, 0.0F),
			new CubeMesh(ConstructionDroneGeometry.ROTOR_2, 0.0F),
			new CubeMesh(ConstructionDroneGeometry.ROTOR_3, 0.0F)};
	private static final SpriteId SPRITE = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("drone_nest"));
	private static final RenderType TYPE = SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	private static final float PIXEL = 1.0F / 16.0F;
	/** How far the drone's feet sit above the nest block's floor, docked: lifted to a cell's top in the field. */
	private static final float FEET = ConstructionDroneGeometry.BOTTOM_PX * PIXEL;
	/** The point the airframe leans about: the middle of its body. */
	private static final float PIVOT_Y = (ConstructionDroneGeometry.BOTTOM_PX + ConstructionDroneGeometry.TOP_PX) * 0.5F
			* PIXEL;
	private static final float BLADE_SPEED = 1.2F;
	private static final float BLADE_IDLE_SPEED = 0.12F;
	/** Ticks either side of now the lean reads the path at. */
	private static final float LOOK = 1.5F;
	/** Lean per block-per-tick of speed, and per block-per-tick-squared of acceleration, in radians. */
	private static final float LEAN_PER_SPEED = 0.35F;
	private static final float LEAN_PER_ACCEL = 0.9F;
	/** The steepest lean, in radians. */
	private static final float MAX_LEAN = 0.42F;
	/** The hover bob in the air, in blocks, faded in over the first block of a leg and out over the last. */
	private static final float BOB = 0.04F;
	/** Sideways room past the route's corners, and headroom over its highest one, in blocks. */
	private static final double MARGIN = 1.0;

	private final SpriteGetter sprites;

	public ConstructionDroneRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives. */
	public static final class State extends BlockEntityRenderState {
		float x;
		float y;
		float z;
		float leanX;
		float leanZ;
		float blades;
		/** Whether a drone is docked or flying: an empty nest draws only its grate (the block model). */
		boolean drone;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DroneNestBlockEntity entity, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		Level level = entity.getLevel();
		float time = (level == null ? 0L : level.getGameTime()) + partialTicks;
		BlockPos nest = entity.getBlockPos();
		BlockPos target = entity.droneTarget();
		state.drone = entity.hasDrone();
		state.blades = time * (target != null ? BLADE_SPEED : BLADE_IDLE_SPEED);
		if (target != null) {
			DroneFlightPath path = new DroneFlightPath(offsets(nest, target, entity.routeCorners()));
			float[] now = new float[3];
			float[] before = new float[3];
			float[] after = new float[3];
			float travel = travel(entity, time);
			path.rounded(travel, now);
			path.rounded(travel(entity, time - LOOK), before);
			path.rounded(travel(entity, time + LOOK), after);
			lean(state, before, now, after);
			float flown = Math.min(travel, 1.0F - travel) * path.length();
			float bob = (float) Math.sin(time * 0.2F) * BOB * Math.min(1.0F, flown);
			state.x = now[0];
			state.y = now[1] + bob;
			state.z = now[2];
		}
		if (level != null) {
			state.lightCoords = LightCoordsUtil.getLightCoords(level, nest.offset(Math.round(state.x),
					(int) Math.floor(state.y + PIVOT_Y), Math.round(state.z)));
		}
	}

	/** How far along the route the drone is at {@code time}: eased, run backwards on the way home. */
	private static float travel(DroneNestBlockEntity entity, float time) {
		float t = DroneFlight.accelerate(entity.flightProgress(time));
		return entity.isReturning() ? 1.0F - t : t;
	}

	/**
	 * The route's corners as offsets from the nest block, in the drone's own frame (its feet at {@link #FEET} over a
	 * cell's floor): a goal over the target sits it on the target's top instead.
	 */
	static float[] offsets(BlockPos nest, BlockPos target, int[] corners) {
		float[] out = new float[corners.length];
		for (int i = 0; i + 2 < corners.length; i += 3) {
			out[i] = corners[i] - nest.getX();
			out[i + 1] = corners[i + 1] - nest.getY();
			out[i + 2] = corners[i + 2] - nest.getZ();
		}
		int last = corners.length - 3;
		if (last > 0 && corners[last] == target.getX() && corners[last + 1] == target.getY() + 1
				&& corners[last + 2] == target.getZ()) {
			out[last + 1] -= FEET;
		}
		return out;
	}

	/**
	 * Leans the airframe the way the drone moves and the way it is pushed: its velocity and acceleration from three
	 * points of the drawn path {@link #LOOK} ticks apart. A quadcopter tilts into its thrust, so this banks it into a
	 * turn and sits it back on braking; level on the ground, where it does not move.
	 */
	private static void lean(State state, float[] before, float[] now, float[] after) {
		float vx = (after[0] - before[0]) / (2.0F * LOOK);
		float vz = (after[2] - before[2]) / (2.0F * LOOK);
		float ax = (after[0] - 2.0F * now[0] + before[0]) / (LOOK * LOOK);
		float az = (after[2] - 2.0F * now[2] + before[2]) / (LOOK * LOOK);
		float lx = LEAN_PER_SPEED * vx + LEAN_PER_ACCEL * ax;
		float lz = LEAN_PER_SPEED * vz + LEAN_PER_ACCEL * az;
		float size = (float) Math.sqrt(lx * lx + lz * lz);
		if (size > MAX_LEAN) {
			lx *= MAX_LEAN / size;
			lz *= MAX_LEAN / size;
		}
		// About X a positive angle tips the top towards +Z; about Z, towards -X (as the garden drone leans).
		state.leanX = lz;
		state.leanZ = -lx;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.drone) {
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(SPRITE);
		poseStack.pushPose();
		poseStack.translate(state.x, state.y, state.z);
		if (state.leanX != 0.0F || state.leanZ != 0.0F) {
			poseStack.translate(0.5F, PIVOT_Y, 0.5F);
			Poses.rotate(poseStack, Axis.XP.rotation(state.leanX));
			Poses.rotate(poseStack, Axis.ZP.rotation(state.leanZ));
			poseStack.translate(-0.5F, -PIVOT_Y, -0.5F);
		}
		submit(collector, poseStack, BODY, sprite, state.lightCoords);
		for (int i = 0; i < ROTORS.length; i++) {
			float[] pivot = ConstructionDroneGeometry.ROTOR_PIVOTS[i];
			poseStack.pushPose();
			poseStack.translate(pivot[0] * PIXEL, pivot[1] * PIXEL, pivot[2] * PIXEL);
			Poses.rotate(poseStack, Axis.YP.rotation(i % 2 == 0 ? state.blades : -state.blades));
			poseStack.translate(-pivot[0] * PIXEL, -pivot[1] * PIXEL, -pivot[2] * PIXEL);
			submit(collector, poseStack, ROTORS[i], sprite, state.lightCoords);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, CubeMesh mesh,
			TextureAtlasSprite sprite, int light) {
		if (!mesh.isEmpty()) {
			collector.submitCustomGeometry(poseStack, TYPE, (pose, consumer) -> mesh.emit(pose, consumer, sprite,
					light));
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer (MOD-633): the nest alone while
	 * the drone is docked; in the air, every corner of the leg's route and a block over it, widened by {@link #MARGIN}
	 * — the rounded path stays between the corners, the lean and the bob within the margin. No {@code @Override}: only
	 * NeoForge's {@code BlockEntityRenderer} declares it ({@code OffScreenRendererBoxTest}).
	 */
	public AABB getRenderBoundingBox(DroneNestBlockEntity blockEntity) {
		BlockPos nest = blockEntity.getBlockPos();
		int[] corners = blockEntity.routeCorners();
		if (corners.length == 0) {
			return new AABB(nest);
		}
		float[] box = DroneFlightPath.bounds(toFloats(corners));
		return new AABB(nest).minmax(new AABB(box[0], box[1], box[2], box[3] + 1.0, box[4] + 1.0, box[5] + 1.0))
				.inflate(MARGIN, 0.0, MARGIN).expandTowards(0.0, 1.0, 0.0);
	}

	private static float[] toFloats(int[] values) {
		float[] out = new float[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = values[i];
		}
		return out;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
