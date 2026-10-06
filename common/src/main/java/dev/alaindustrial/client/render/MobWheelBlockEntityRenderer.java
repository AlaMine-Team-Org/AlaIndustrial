package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.MobWheelControllerBlock;
import dev.alaindustrial.block.MobWheelStructure;
import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.compat.client.Poses;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The assembled mob wheel (MOD-763): frame, running wheel and gate, drawn from the drive's block entity.
 *
 * <p>Once the 3×3×3 box is formed every member's block model is empty, so the whole structure is this
 * renderer's geometry ({@link MobWheelGeometry}, generated from the approved sketch). Loose parts keep their
 * own block models and this renderer draws nothing.
 *
 * <p><b>Transform.</b> The geometry is canonical (gate side +Z, drive in {@code CONTROLLER_CELL}); the pose
 * turns it about the drive's block centre by the structure's facing — {@code world = controller + c +
 * R_Y(yaw) · (v − CONTROLLER_CELL − c)}, {@code c = (0.5, 0, 0.5)}. The wheel turns about the Z axle
 * through {@code ROTOR_PIVOT} by the block entity's integrated client angle, the gate leaf about Y through
 * {@code GATE_PIVOT} by its eased openness.
 *
 * <p><b>Texture.</b> One 128×16 sheet per wood family under {@code textures/entity/mob_wheel/}, stitched into
 * the block atlas by a directory source in {@code atlases/blocks.json} — the way vanilla stitches the conduit
 * — and read as an atlas sprite through {@link Sheets#BLOCK_ENTITIES_MAPPER}. The atlas path keeps the
 * block sheet's cutout, depth and mipmaps; a standalone texture would bleed a white edge.
 */
public final class MobWheelBlockEntityRenderer
		implements BlockEntityRenderer<MobWheelBlockEntity, MobWheelBlockEntityRenderer.State> {

	private static final float PIXEL = 1.0F / 16.0F;

	private static final CubeMesh STATIC = new CubeMesh(MobWheelGeometry.STATIC, 0.0F);
	private static final CubeMesh ROTOR = new CubeMesh(MobWheelGeometry.ROTOR, 0.0F);
	private static final CubeMesh GATE = new CubeMesh(MobWheelGeometry.GATE, 0.0F);

	private static final Map<String, SpriteId> SPRITES = buildSprites();
	private static final SpriteId DEFAULT_SPRITE = SPRITES.get(MobWheelGeometry.WOODS[0]);
	/** Every wood sheet lives in the block atlas, so one render type serves all of them. */
	private static final RenderType TYPE = DEFAULT_SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	private static Map<String, SpriteId> buildSprites() {
		Map<String, SpriteId> sprites = new HashMap<>();
		String folder = MobWheelGeometry.TEXTURE_PREFIX.substring("textures/entity/".length());
		for (String wood : MobWheelGeometry.WOODS) {
			sprites.put(wood, Sheets.BLOCK_ENTITIES_MAPPER.apply(Industrialization.id(folder + wood)));
		}
		return sprites;
	}

	private final SpriteGetter sprites;

	public MobWheelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		boolean skip;
		float yaw;
		float wheelAngle;
		float gateDegrees;
		SpriteId sprite = DEFAULT_SPRITE;
		int gateLight;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MobWheelBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		BlockState block = entity.getBlockState();
		state.skip = !block.hasProperty(MobWheelStructure.FORMED) || !block.getValue(MobWheelStructure.FORMED);
		if (state.skip) {
			return;
		}
		Direction facing = block.getValue(MobWheelControllerBlock.FACING);
		state.yaw = yawOf(facing);
		state.wheelAngle = entity.animation().wheelAngle(partialTicks);
		state.gateDegrees = MobWheelGeometry.GATE_OPEN_ANGLE * entity.animation().gateOpenness(partialTicks);
		state.sprite = SPRITES.getOrDefault(entity.wood(), DEFAULT_SPRITE);
		Level level = entity.getLevel();
		if (level != null) {
			BlockPos controller = entity.getBlockPos();
			// The drive's own cell sits in a corner against the frame; the middle of the wheel is the fairer
			// sample for a three-block object. The gate takes its own cell's light, since it swings outside.
			state.lightCoords = LightCoordsUtil.getLightCoords(level,
					MobWheelStructure.at(controller, facing, MobWheelStructure.ROTOR));
			state.gateLight = LightCoordsUtil.getLightCoords(level,
					MobWheelStructure.at(controller, facing, MobWheelStructure.GATE));
		} else {
			state.gateLight = state.lightCoords;
		}
	}

	/** Canonical +Z (the gate side) turned onto {@code facing}, right-handed about +Y. */
	private static float yawOf(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0F;
			case NORTH -> 180.0F;
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
		TextureAtlasSprite sprite = this.sprites.get(state.sprite);
		int[] cell = MobWheelGeometry.CONTROLLER_CELL;

		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(state.yaw));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		poseStack.translate(-cell[0], -cell[1], -cell[2]);

		submit(collector, poseStack, STATIC, sprite, state.lightCoords);

		float[] rotor = MobWheelGeometry.ROTOR_PIVOT;
		poseStack.pushPose();
		poseStack.translate(rotor[0] * PIXEL, rotor[1] * PIXEL, rotor[2] * PIXEL);
		Poses.rotate(poseStack, Axis.ZP.rotation(state.wheelAngle));
		poseStack.translate(-rotor[0] * PIXEL, -rotor[1] * PIXEL, -rotor[2] * PIXEL);
		submit(collector, poseStack, ROTOR, sprite, state.lightCoords);
		poseStack.popPose();

		float[] gate = MobWheelGeometry.GATE_PIVOT;
		poseStack.pushPose();
		poseStack.translate(gate[0] * PIXEL, gate[1] * PIXEL, gate[2] * PIXEL);
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(state.gateDegrees));
		poseStack.translate(-gate[0] * PIXEL, -gate[1] * PIXEL, -gate[2] * PIXEL);
		submit(collector, poseStack, GATE, sprite, state.gateLight);
		poseStack.popPose();

		poseStack.popPose();
	}

	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, CubeMesh mesh,
			TextureAtlasSprite sprite, int light) {
		if (mesh.isEmpty()) {
			return;
		}
		collector.submitCustomGeometry(poseStack, TYPE, (pose, consumer) -> mesh.emit(pose, consumer, sprite, light));
	}

	/** The structure reaches two blocks past the drive and the open gate swings out in front of it. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer (MOD-633): the whole
	 * structure at every wheel and gate angle, {@code RENDER_MIN..RENDER_MAX} turned into the world. A loose
	 * drive draws nothing, so its own block is enough.
	 *
	 * <p>No {@code @Override}: only NeoForge's {@code BlockEntityRenderer} declares this method, and vanilla —
	 * so Fabric — never tests a block entity against the frustum. {@code OffScreenRendererBoxTest} on the
	 * NeoForge lane fails if it stops overriding.
	 */
	public AABB getRenderBoundingBox(MobWheelBlockEntity blockEntity) {
		BlockPos controller = blockEntity.getBlockPos();
		BlockState block = blockEntity.getBlockState();
		if (!block.hasProperty(MobWheelStructure.FORMED) || !block.getValue(MobWheelStructure.FORMED)) {
			return new AABB(controller);
		}
		return worldBox(controller, block.getValue(MobWheelControllerBlock.FACING));
	}

	/** {@code RENDER_MIN..RENDER_MAX} through the renderer's own transform, as an axis-aligned world box. */
	static AABB worldBox(BlockPos controller, Direction facing) {
		double yaw = Math.toRadians(yawOf(facing));
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		float[] min = MobWheelGeometry.RENDER_MIN;
		float[] max = MobWheelGeometry.RENDER_MAX;
		int[] cell = MobWheelGeometry.CONTROLLER_CELL;
		double minX = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (int corner = 0; corner < 4; corner++) {
			double x = ((corner & 1) == 0 ? min[0] : max[0]) * PIXEL - cell[0] - 0.5;
			double z = ((corner & 2) == 0 ? min[2] : max[2]) * PIXEL - cell[2] - 0.5;
			double wx = x * cos + z * sin + 0.5;
			double wz = -x * sin + z * cos + 0.5;
			minX = Math.min(minX, wx);
			maxX = Math.max(maxX, wx);
			minZ = Math.min(minZ, wz);
			maxZ = Math.max(maxZ, wz);
		}
		return new AABB(controller.getX() + minX, controller.getY() + min[1] * PIXEL - cell[1],
				controller.getZ() + minZ, controller.getX() + maxX, controller.getY() + max[1] * PIXEL - cell[1],
				controller.getZ() + maxZ);
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
