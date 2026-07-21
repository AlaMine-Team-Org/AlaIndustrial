package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
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

/** Renders the visible wooden rotor in front of wind mill blocks. */
public final class WindMillRotorBlockEntityRenderer<T extends MachineBlockEntity>
		implements BlockEntityRenderer<T, WindMillRotorBlockEntityRenderer.State> {
	private static final SpriteId SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("wind_mill_rotor_blades_3d"));
	private static final RenderType RENDER_TYPE = SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	private static final float HALF_SIZE = 1.0F;

	private final SpriteGetter sprites;

	public WindMillRotorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
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
		// Interference (MOD-051): when a neighbouring mill's rotor disc overlaps this one's, both
		// mills stall and hide their blades — rendering two overlapping coplanar quads would clip
		// and z-fight. Channel 3 is the synced mode code shared by the whole wind mill family.
		boolean interfered = entity.getDataAccess().get(3) == WindMillBlockEntity.MODE_INTERFERENCE;
		state.visible = !interfered
				&& (!(entity instanceof WindMillBlockEntity windMill)
						|| !windMill.getItem(WindMillBlockEntity.ROTOR_SLOT).isEmpty());

		int production = entity.getDataAccess().get(2);
		state.angle = production <= 0 ? 0.0F : rotationAngle(entity, partialTicks, production);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera) {
		if (!state.visible) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		rotateToFacing(poseStack, state.facing);
		poseStack.translate(0.0F, 0.0F, -0.58F);
		poseStack.mulPose(Axis.ZP.rotation(state.angle));
		TextureAtlasSprite sprite = sprites.get(SPRITE);
		// The rotor is a decorative overhang drawn as a flat cutout quad in front of the block. It
		// spans 2×2 blocks (HALF_SIZE) and floats off the face, so positional block light at the BE's
		// own coords leaves its corners dark — and the NeoForge custom-geometry pipeline shades
		// cutoutBlockItemSheet darker than Fabric's for the same light value. Forcing FULL_BRIGHT
		// (the vanilla pattern for flags/skulls/shields) makes the blades render at full brightness,
		// identically on both loaders and regardless of where the block sits.
		submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE,
				(pose, consumer) -> renderRotorQuad(pose, consumer, sprite, LightCoordsUtil.FULL_BRIGHT));
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

	private static float rotationAngle(MachineBlockEntity entity, float partialTicks, int production) {
		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
		float radiansPerTick = 0.08F + Math.min(production, 16) * 0.035F;
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

	private static void renderRotorQuad(PoseStack.Pose pose, VertexConsumer consumer,
			TextureAtlasSprite sprite, int light) {
		float u0 = sprite.getU0();
		float u1 = sprite.getU1();
		float v0 = sprite.getV0();
		float v1 = sprite.getV1();
		// Front face: one complete 64x64 sprite, not a cuboid UV unwrap. This keeps the rotor
		// proportional and prevents transparent atlas regions from appearing on side faces.
		vertex(pose, consumer, -HALF_SIZE, -HALF_SIZE, 0.0F, u0, v1, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, HALF_SIZE, -HALF_SIZE, 0.0F, u1, v1, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, HALF_SIZE, HALF_SIZE, 0.0F, u1, v0, light, 0.0F, 0.0F, -1.0F);
		vertex(pose, consumer, -HALF_SIZE, HALF_SIZE, 0.0F, u0, v0, light, 0.0F, 0.0F, -1.0F);
		// Back face so the rotor still looks correct when viewed from an angle or behind.
		vertex(pose, consumer, -HALF_SIZE, HALF_SIZE, 0.0F, u0, v0, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, HALF_SIZE, HALF_SIZE, 0.0F, u1, v0, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, HALF_SIZE, -HALF_SIZE, 0.0F, u1, v1, light, 0.0F, 0.0F, 1.0F);
		vertex(pose, consumer, -HALF_SIZE, -HALF_SIZE, 0.0F, u0, v1, light, 0.0F, 0.0F, 1.0F);
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
		private boolean visible;
		private float angle;
	}
}
