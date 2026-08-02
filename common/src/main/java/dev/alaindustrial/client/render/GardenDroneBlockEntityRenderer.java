package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Draws the Garden Drone above its station (MOD-277).
 *
 * <p>The drone is <b>not an entity</b> — it is geometry this renderer places, so it cannot be lost,
 * killed or left behind in an unloaded chunk. The server sends each leg of the flight <em>once</em>
 * (target, length, start time) and this class works out where the drone is from the client's own
 * clock; a per-tick position packet would make this the chattiest machine in the mod for no gain.
 *
 * <p>Two details are easy to get wrong and were both wrong once here:
 * <ul>
 * <li><b>Each rotor spins on its own axis.</b> Rotating the whole rotor group about the model origin
 *     makes the four blades orbit the hull like a carousel instead of turning in place — which is
 *     what it looked like before every rotor became its own {@link ModelPart} with its own pivot.</li>
 * <li><b>The flight arcs.</b> A straight line between two points reads as a rail, not as flying, so
 *     the drone climbs out of the dock, crests mid-leg and settles onto the target.</li>
 * </ul>
 */
public final class GardenDroneBlockEntityRenderer<T extends GardenDroneStationBlockEntity>
		implements BlockEntityRenderer<T, GardenDroneBlockEntityRenderer.State> {

	public static final ModelLayerLocation MODEL_LAYER =
			new ModelLayerLocation(Industrialization.id("garden_drone"), "main");

	private static final SpriteId HULL = Sheets.BLOCKS_MAPPER.apply(Industrialization.id("machine_casing"));
	private static final SpriteId TRIM = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("cut_copper");
	private static final SpriteId BLADES = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("iron_block");

	private static final int ROTOR_COUNT = 4;
	/** Distance from the hull centre to each rotor mast, in model pixels. */
	private static final float ARM = 4.6F;
	/** Height of the rotor plane above the hull centre, in model pixels. */
	private static final float ROTOR_Y = 1.7F;

	/**
	 * Size of the drone relative to its authored pixel geometry. The model is drawn around a squat
	 * 7-pixel hull; at 1:1 that is under half a block and reads as a speck from any real distance.
	 * The drone is the thing the player actually watches, so it is scaled until it looks like a
	 * machine — roughly a block and a half across.
	 */
	// 1.0 renders the authored model at its natural size: the airframe spans ~17 pixels, i.e. just
	// over one block. A garden drone should read as a machine that fits over a single tile, so it sits
	// a little under that — big enough to watch, small enough not to swallow the farm.
	private static final float DRONE_SCALE = 0.85F;
	/**
	 * The model's lowest point (the landing pads), in authored pixels below its centre.
	 *
	 * <p><b>{@link ModelPart} geometry is already in block units:</b> vanilla's {@code Cube} divides
	 * every authored coordinate by 16 when it bakes. So one authored pixel is {@code 1/16} of a block
	 * <em>before</em> {@link #DRONE_SCALE} is applied, and dividing by 16 again here — which this class
	 * did at first — shrank the drone sixteenfold while the landing height was computed from the size
	 * it was supposed to be. That is why it floated a block and a half over its own pad.
	 */
	private static final float MODEL_BOTTOM_PX = 4.1F;
	/** How far the model reaches below its centre once scaled, in blocks. */
	private static final float MODEL_BOTTOM = MODEL_BOTTOM_PX / 16.0F * DRONE_SCALE;
	/** Top face of the dock plate (a 4-pixel slab), so a parked drone stands on it rather than in it. */
	private static final float DOCK_SURFACE = 4.0F / 16.0F;
	/** Clearance kept under the feet out in the field, where the drone lands on the tile it works. */
	private static final float FIELD_CLEARANCE = 0.02F;
	/** Rotor speed while a leg is in the air, radians per tick. */
	private static final float BLADE_SPEED = 1.1F;
	/**
	 * Rotor speed while parked. Deliberately non-zero: a powered machine sitting with dead blades
	 * reads as broken, while a slow idle turn reads as standing by.
	 */
	private static final float BLADE_IDLE_SPEED = 0.15F;
	/** Amplitude of the idle bob, in blocks — enough to read as hovering, not as bouncing. */
	private static final float BOB_AMPLITUDE = 0.05F;
	/** How high the drone climbs over the midpoint of a leg, in blocks. */
	private static final float ARC_HEIGHT = 1.6F;
	/** How far the drone leans into its flight direction, in radians at full tilt. */
	private static final float MAX_TILT = 0.30F;
	/** Sideways drift amplitude mid-leg, in blocks — keeps the path off a dead-straight rail. */
	private static final float SWAY_AMPLITUDE = 0.25F;

	private final Model.Simple hullModel;
	private final Model.Simple trimModel;
	private final Model.Simple rotorModel;
	private final ModelPart[] rotorParts = new ModelPart[ROTOR_COUNT];
	private final SpriteGetter sprites;

	public GardenDroneBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(MODEL_LAYER);
		this.hullModel = new Model.Simple(root.getChild("hull"),
				ignored -> HULL.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.trimModel = new Model.Simple(root.getChild("trim"),
				ignored -> TRIM.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		ModelPart rotors = root.getChild("rotors");
		this.rotorModel = new Model.Simple(rotors,
				ignored -> BLADES.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		for (int i = 0; i < ROTOR_COUNT; i++) {
			this.rotorParts[i] = rotors.getChild("rotor_" + i);
		}
		this.sprites = context.sprites();
	}

	/**
	 * The "Combine" silhouette, in model pixels ({@code 16 == one block}) around the drone's centre:
	 * a squat hull, the hopper on its back that reads as "the harvest goes in here", a collection
	 * scoop at the front, and four rotors on outriggers.
	 *
	 * <p>The rotors are children with their own {@link PartPose#offset} pivots and blades authored
	 * around the origin, so setting {@code yRot} on one turns that rotor in place. Authoring them at
	 * absolute coordinates instead would make the group orbit the hull when rotated.
	 */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition trim = root.addOrReplaceChild("trim", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rotors = root.addOrReplaceChild("rotors", CubeListBuilder.create(), PartPose.ZERO);

		// Hull: a tapered two-tier body rather than one slab, so it catches light on more than one plane.
		hull.addOrReplaceChild("belly",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.2F, -3.0F, 6.0F, 1.0F, 6.0F),
				PartPose.ZERO);
		hull.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.8F, -1.2F, -3.8F, 7.6F, 2.4F, 7.6F),
				PartPose.ZERO);
		trim.addOrReplaceChild("belt",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.9F, -0.3F, -3.9F, 7.8F, 0.6F, 7.8F),
				PartPose.ZERO);
		// Hopper: three narrowing steps, the shape that says "the harvest goes in here".
		hull.addOrReplaceChild("hopper_lower",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2.8F, 1.2F, -2.8F, 5.6F, 1.6F, 5.6F),
				PartPose.ZERO);
		hull.addOrReplaceChild("hopper_upper",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 2.8F, -2.0F, 4.0F, 1.2F, 4.0F),
				PartPose.ZERO);
		trim.addOrReplaceChild("hopper_neck",
				CubeListBuilder.create().texOffs(0, 0).addBox(-1.4F, 4.0F, -1.4F, 2.8F, 0.8F, 2.8F),
				PartPose.ZERO);
		// Collection scoop at the front, with a copper lip and a chin fairing under it.
		trim.addOrReplaceChild("scoop_lip",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2.8F, -2.6F, -6.0F, 5.6F, 1.2F, 2.2F),
				PartPose.ZERO);
		hull.addOrReplaceChild("scoop_neck",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2.2F, -2.0F, -4.2F, 4.4F, 1.2F, 1.6F),
				PartPose.ZERO);
		// Sensor pod and mast, so the drone has a readable "front" and a bit of silhouette on top.
		trim.addOrReplaceChild("sensor",
				CubeListBuilder.create().texOffs(0, 0).addBox(-1.2F, -0.9F, -4.6F, 2.4F, 1.6F, 1.0F),
				PartPose.ZERO);
		hull.addOrReplaceChild("mast",
				CubeListBuilder.create().texOffs(0, 0).addBox(-0.3F, 4.8F, -0.3F, 0.6F, 1.8F, 0.6F),
				PartPose.ZERO);
		trim.addOrReplaceChild("beacon",
				CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, 6.6F, -0.5F, 1.0F, 1.0F, 1.0F),
				PartPose.ZERO);

		for (int i = 0; i < ROTOR_COUNT; i++) {
			float x = (i == 0 || i == 3) ? ARM : -ARM;
			float z = (i < 2) ? ARM : -ARM;
			// Outrigger: a boom out to the mast plus the mast itself, so rotors sit clear of the hull.
			hull.addOrReplaceChild("arm_" + i,
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(x - 1.1F, -0.6F, z - 1.1F, 2.2F, 1.2F, 2.2F),
					PartPose.ZERO);
			hull.addOrReplaceChild("mast_" + i,
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(x - 0.6F, 0.4F, z - 0.6F, 1.2F, 1.2F, 1.2F),
					PartPose.ZERO);
			trim.addOrReplaceChild("motor_" + i,
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(x - 1.4F, 1.0F, z - 1.4F, 2.8F, 0.9F, 2.8F),
					PartPose.ZERO);
			// Guard ring: four short bars around each rotor — the detail that reads as a real airframe.
			for (int side = 0; side < 4; side++) {
				float gx = x + (side == 0 ? 4.0F : side == 1 ? -4.0F : 0.0F);
				float gz = z + (side == 2 ? 4.0F : side == 3 ? -4.0F : 0.0F);
				float w = (side < 2) ? 0.5F : 8.5F;
				float d = (side < 2) ? 8.5F : 0.5F;
				trim.addOrReplaceChild("guard_" + i + "_" + side,
						CubeListBuilder.create().texOffs(0, 0)
								.addBox(gx - w / 2, 1.9F, gz - d / 2, w, 0.5F, d),
						PartPose.ZERO);
			}
			// Blades authored around (0,0,0) and hung on a pivot at the mast: a two-bladed cross that
			// turns on its own axis when this part gets a yRot.
			rotors.addOrReplaceChild("rotor_" + i,
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(-3.7F, 0.0F, -0.35F, 7.4F, 0.4F, 0.7F)
							.addBox(-0.35F, 0.0F, -3.7F, 0.7F, 0.4F, 7.4F),
					PartPose.offset(x, ROTOR_Y, z));
		}

		// Landing legs, so the parked drone visibly stands on its dock.
		for (int i = 0; i < ROTOR_COUNT; i++) {
			float x = (i == 0 || i == 3) ? 2.4F : -2.4F;
			float z = (i < 2) ? 2.4F : -2.4F;
			hull.addOrReplaceChild("leg_" + i,
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(x - 0.45F, -3.6F, z - 0.45F, 0.9F, 1.5F, 0.9F),
					PartPose.ZERO);
			trim.addOrReplaceChild("foot_" + i,
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(x - 0.8F, -4.1F, z - 0.8F, 1.6F, 0.5F, 1.6F),
					PartPose.ZERO);
		}
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
		Level level = entity.getLevel();
		BlockPos station = entity.getBlockPos();
		BlockPos target = entity.droneTarget();
		// No drone installed means nothing to draw: the dock stands empty until the player supplies one.
		state.present = entity.hasDrone();
		state.flying = target != null;

		long gameTime = level == null ? 0L : level.getGameTime();
		float time = gameTime + partialTicks;

		if (target != null) {
			// The whole flight is computed here, from the leg the server sent once. Reading a synced
			// per-tick counter instead would freeze the drone on its dock between packets.
			float t = accelerate(entity.flightProgress(time));
			// On the way home the same leg runs backwards, so one interpolation covers both directions.
			float travel = entity.isReturning() ? 1.0F - t : t;
			float dx = target.getX() - station.getX();
			float dy = target.getY() - station.getY();
			float dz = target.getZ() - station.getZ();
			state.offsetX = dx * travel;
			state.offsetY = dy * travel;
			state.offsetZ = dz * travel;
			// Land on the target cell's actual collision top, not on an assumption about it. The four
			// jobs aim at different things — tilling at the ground block, the other three at the plant's
			// own cell — and a "is it a full cube?" test still buried the drone in farmland, which is
			// 15/16 of a block tall. Reading the collision shape covers every case with one rule: dirt
			// and grass give 1.0, farmland 0.9375, air and crops are empty so the drone stands on the
			// cell floor (i.e. on whatever soil is underneath).
			float surface = 0.0F;
			if (level != null) {
				VoxelShape collision = level.getBlockState(target).getCollisionShape(level, target);
				if (!collision.isEmpty()) {
					surface = (float) collision.max(Direction.Axis.Y);
				}
			}
			state.restHeight = MODEL_BOTTOM + surface + Mth.lerp(travel, DOCK_SURFACE, FIELD_CLEARANCE);

			// Climb out and settle back down. The arc is what makes the drone land ON the tile it works:
			// at both ends of the leg it is zero, so the drone is at REST_HEIGHT over the dock at the
			// start and REST_HEIGHT over the target at the finish — it descends all the way instead of
			// hovering a block up and reaching down.
			float arc = (float) Math.sin(t * Math.PI);
			state.offsetY += arc * ARC_HEIGHT;

			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 0.0001) {
				// Drift sideways across the leg so no two crossings trace the same line.
				float sway = (float) Math.sin(time * 0.07F) * arc * SWAY_AMPLITUDE;
				state.offsetX += (float) (-dz / len) * sway;
				state.offsetZ += (float) (dx / len) * sway;

				// Lean into the direction of travel; the sign flips on the way back, and the lean eases
				// out at both ends so the drone is level when it does its work.
				float dir = entity.isReturning() ? -1.0F : 1.0F;
				state.tiltX = (float) (dz / len) * dir * MAX_TILT * arc;
				state.tiltZ = (float) (-dx / len) * dir * MAX_TILT * arc;
			}
			// Nose-up on the climb, nose-down on the descent.
			state.tiltX += (float) Math.cos(t * Math.PI) * 0.12F;
		}

		// Blades never stop outright — they idle slowly on the dock and spin up in flight.
		state.bladeAngle = time * (state.flying ? BLADE_SPEED : BLADE_IDLE_SPEED);
		// Bobbing belongs to a drone in the air; on the pad it would push the legs through the plate.
		state.bob = state.flying ? (float) Math.sin(time * 0.15F) * BOB_AMPLITUDE : 0.0F;
		if (level != null) {
			state.lightCoords = LightCoordsUtil.getLightCoords(level, station.above());
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (!state.present) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F + state.offsetX, state.restHeight + state.offsetY + state.bob,
				0.5F + state.offsetZ);
		poseStack.mulPose(Axis.XP.rotation(state.tiltX));
		poseStack.mulPose(Axis.ZP.rotation(state.tiltZ));
		poseStack.scale(DRONE_SCALE, DRONE_SCALE, DRONE_SCALE);

		collector.submitModel(hullModel, Unit.INSTANCE, poseStack, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, HULL, sprites, 0, state.breakProgress);
		collector.submitModel(trimModel, Unit.INSTANCE, poseStack, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, TRIM, sprites, 0, state.breakProgress);

		// Each rotor turns on its own pivot. Alternating direction between diagonals is what a real
		// quadcopter does to cancel torque, and it stops the four rotors from reading as one texture.
		for (int i = 0; i < ROTOR_COUNT; i++) {
			rotorParts[i].yRot = (i % 2 == 0) ? state.bladeAngle : -state.bladeAngle;
		}
		collector.submitModel(rotorModel, Unit.INSTANCE, poseStack, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, BLADES, sprites, 0, state.breakProgress);

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

	/**
	 * The speed curve: slow off the pad, fast across the middle, braking onto the target.
	 *
	 * <p>Smootherstep (the quintic one) rather than plain smoothstep — over a leg as short as sixteen
	 * ticks the cubic curve still reads as near-constant speed, and the whole point of the easing is
	 * that the player can see the drone accelerate and brake the way a real one does.
	 */
	private static float accelerate(float t) {
		float c = Math.clamp(t, 0.0F, 1.0F);
		return c * c * c * (c * (c * 6.0F - 15.0F) + 10.0F);
	}

	public static final class State extends BlockEntityRenderState {
		private boolean present;
		/** Height of the drone's centre over whatever it is standing on; parked, that is the dock plate. */
		private float restHeight = MODEL_BOTTOM + DOCK_SURFACE;
		private boolean flying;
		private float offsetX;
		private float offsetY;
		private float offsetZ;
		private float tiltX;
		private float tiltZ;
		private float bladeAngle;
		private float bob;
	}
}
