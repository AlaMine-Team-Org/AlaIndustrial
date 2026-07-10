package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the large decorative water wheel for the LV water mill.
 *
 * <p>The wheel is emitted procedurally each frame (no baked model): two segmented plank
 * rims, eight tilted paddle boards, eight stripped-log spokes per rim, an octagonal log
 * hub and a copper axle. Surfaces use vanilla block sprites resolved at runtime so the
 * wheel always matches the game's art style, and every quad is lit with the world light
 * at the wheel position plus the vanilla directional diffuse formula — this is what makes
 * the shape read as volumetric instead of flat.</p>
 */
public final class WaterMillWheelBlockEntityRenderer<T extends MachineBlockEntity>
		implements BlockEntityRenderer<T, WaterMillWheelBlockEntityRenderer.State> {
	// Vanilla sprites referenced at runtime (never copied): keeps the wheel in Minecraft's palette.
	private static final SpriteId PLANKS = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("spruce_planks");
	private static final SpriteId SPOKE_WOOD = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("stripped_spruce_log");
	private static final SpriteId HUB_SIDE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("spruce_log");
	private static final SpriteId HUB_END = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("spruce_log_top");
	private static final SpriteId AXLE_METAL = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("cut_copper");
	private static final RenderType RENDER_TYPE = PLANKS.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	private static final int RIM_SEGMENTS = 16;
	private static final float RIM_INNER = 0.98F;
	private static final float RIM_OUTER = 1.34F;
	// Closed drum shroud between the two rims — kills all see-through gaps in the wheel body.
	private static final float BAND_INNER = 1.00F;
	private static final float BAND_OUTER = 1.26F;
	private static final float BAND_HALF_Z = 0.305F;
	private static final int PADDLE_COUNT = 8;
	private static final float PADDLE_TILT_DEG = 25.0F;
	private static final int SPOKE_COUNT = 8;
	private static final float FRONT_Z0 = -0.44F;
	private static final float FRONT_Z1 = -0.31F;
	private static final float BACK_Z0 = 0.31F;
	private static final float BACK_Z1 = 0.44F;

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
		Level level = entity.getLevel();
		if (level != null) {
			// The wheel hangs one block in front of the (opaque) machine — sample light there,
			// not inside the machine block, or the wheel goes black.
			state.lightCoords = LightCoordsUtil.getLightCoords(level, entity.getBlockPos().relative(state.facing));
		}
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
		renderWheelGeometry(poseStack, submitNodeCollector, state);
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

	private void renderWheelGeometry(PoseStack poseStack, SubmitNodeCollector collector, State state) {
		TextureAtlasSprite planks = sprites.get(PLANKS);
		TextureAtlasSprite spokeWood = sprites.get(SPOKE_WOOD);
		TextureAtlasSprite hubSide = sprites.get(HUB_SIDE);
		TextureAtlasSprite hubEnd = sprites.get(HUB_END);
		TextureAtlasSprite axle = sprites.get(AXLE_METAL);
		int light = state.lightCoords;
		Shade wheelShade = new Shade(state.facing, state.angle);

		renderRing(poseStack, collector, planks, RIM_INNER, RIM_OUTER, FRONT_Z0, FRONT_Z1, light, wheelShade);
		renderRing(poseStack, collector, planks, RIM_INNER, RIM_OUTER, BACK_Z0, BACK_Z1, light, wheelShade);
		renderRing(poseStack, collector, planks, BAND_INNER, BAND_OUTER, -BAND_HALF_Z, BAND_HALF_Z, light, wheelShade);
		renderPaddles(poseStack, collector, planks, light, state);
		renderSpokes(poseStack, collector, spokeWood, light, state);
		renderOctagonalPrism(poseStack, collector, hubSide, hubEnd, 0.32F, -0.50F, 0.50F, light, wheelShade);
		renderOctagonalPrism(poseStack, collector, axle, axle, 0.14F, -0.62F, 0.70F, light, wheelShade);
	}

	/**
	 * One closed polygon ring: {@code RIM_SEGMENTS} plank segments joined flush (shared corner
	 * vertices, watertight). Plank separation is conveyed by the per-segment UV window jump,
	 * not by physical gaps — no see-through slits.
	 */
	private void renderRing(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite planks,
			float innerRadius, float outerRadius, float z0, float z1, int light, Shade shade) {
		float step = 360.0F / RIM_SEGMENTS;
		for (int i = 0; i < RIM_SEGMENTS; i++) {
			float start = (i - 0.5F) * step;
			float end = (i + 0.5F) * step;
			// Stagger the plank texture window per segment so the ring does not tile visibly.
			float uOff = (i % 3) * 0.19F;
			float vOff = (i % 2) * 0.5F;
			collector.submitCustomGeometry(poseStack, RENDER_TYPE,
					(pose, consumer) -> renderRingSegment(pose, consumer, planks,
							innerRadius, outerRadius, start, end, z0, z1, light, shade, uOff, vOff));
		}
	}

	/** Eight paddle boards, tilted off the radial direction so they read as scoops, not gear teeth. */
	private void renderPaddles(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite planks,
			int light, State state) {
		float stepDeg = 360.0F / PADDLE_COUNT;
		for (int i = 0; i < PADDLE_COUNT; i++) {
			float angleDeg = i * stepDeg;
			Shade shade = new Shade(state.facing,
					state.angle + (float) Math.toRadians(angleDeg + PADDLE_TILT_DEG));
			poseStack.pushPose();
			poseStack.mulPose(Axis.ZP.rotationDegrees(angleDeg));
			poseStack.translate(1.47F, 0.0F, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(PADDLE_TILT_DEG));
			// Inner edge (r≈1.17) is buried inside the drum shroud, so the joint is sealed.
			collector.submitCustomGeometry(poseStack, RENDER_TYPE,
					(pose, consumer) -> renderPlankBoard(pose, consumer, planks,
							-0.30F, -0.04F, -0.42F, 0.30F, 0.04F, 0.42F, light, shade));
			poseStack.popPose();
		}
	}

	/**
	 * Eight full-width stripped-log beams, aligned with the paddles they support. Each beam
	 * spans the whole wheel width (hub to drum, rim to rim) — one solid arm, no hollow middle.
	 * Slightly inset from the rim faces (±0.435 vs ±0.44) to avoid coplanar z-fighting.
	 */
	private void renderSpokes(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite spokeWood,
			int light, State state) {
		float stepDeg = 360.0F / SPOKE_COUNT;
		for (int i = 0; i < SPOKE_COUNT; i++) {
			float angleDeg = i * stepDeg;
			Shade shade = new Shade(state.facing, state.angle + (float) Math.toRadians(angleDeg));
			poseStack.pushPose();
			poseStack.mulPose(Axis.ZP.rotationDegrees(angleDeg));
			collector.submitCustomGeometry(poseStack, RENDER_TYPE,
					(pose, consumer) -> renderLogBeam(pose, consumer, spokeWood,
							0.24F, -0.06F, -0.435F, 1.10F, 0.06F, 0.435F, light, shade));
			poseStack.popPose();
		}
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

	/** Paddle board: box whose plank grain (sprite U) runs along Z, across the wheel width. */
	private static void renderPlankBoard(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			float x0, float y0, float z0, float x1, float y1, float z1, int light, Shade shade) {
		float dx = Math.min(1.0F, x1 - x0);
		float dy = Math.min(1.0F, y1 - y0);
		float dz = Math.min(1.0F, z1 - z0);
		// z-normal ends
		quad(pose, consumer, sprite, light, shade.at(0.0F, 0.0F, -1.0F), 0.0F, 0.0F, -1.0F,
				x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0,
				0.0F, dy, dx, dy, dx, 0.0F, 0.0F, 0.0F);
		quad(pose, consumer, sprite, light, shade.at(0.0F, 0.0F, 1.0F), 0.0F, 0.0F, 1.0F,
				x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1,
				0.0F, 0.0F, dx, 0.0F, dx, dy, 0.0F, dy);
		// x-normal sides: U along Z
		quad(pose, consumer, sprite, light, shade.at(-1.0F, 0.0F, 0.0F), -1.0F, 0.0F, 0.0F,
				x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1,
				dz, dy, 0.0F, dy, 0.0F, 0.0F, dz, 0.0F);
		quad(pose, consumer, sprite, light, shade.at(1.0F, 0.0F, 0.0F), 1.0F, 0.0F, 0.0F,
				x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1,
				dz, 0.0F, 0.0F, 0.0F, 0.0F, dy, dz, dy);
		// y-normal main faces: U along Z so the boards run across the wheel width
		quad(pose, consumer, sprite, light, shade.at(0.0F, 1.0F, 0.0F), 0.0F, 1.0F, 0.0F,
				x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
				0.0F, dx, 0.0F, 0.0F, dz, 0.0F, dz, dx);
		quad(pose, consumer, sprite, light, shade.at(0.0F, -1.0F, 0.0F), 0.0F, -1.0F, 0.0F,
				x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
				dz, dx, dz, 0.0F, 0.0F, 0.0F, 0.0F, dx);
	}

	/** Spoke beam along X: log grain (sprite V) runs along the beam length. */
	private static void renderLogBeam(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			float x0, float y0, float z0, float x1, float y1, float z1, int light, Shade shade) {
		float dx = Math.min(1.0F, x1 - x0);
		float dy = Math.min(1.0F, y1 - y0);
		float dz = Math.min(1.0F, z1 - z0);
		// z-normal faces: V along X
		quad(pose, consumer, sprite, light, shade.at(0.0F, 0.0F, -1.0F), 0.0F, 0.0F, -1.0F,
				x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0,
				0.0F, dx, 0.0F, 0.0F, dy, 0.0F, dy, dx);
		quad(pose, consumer, sprite, light, shade.at(0.0F, 0.0F, 1.0F), 0.0F, 0.0F, 1.0F,
				x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1,
				0.0F, dx, 0.0F, 0.0F, dy, 0.0F, dy, dx);
		// y-normal faces: V along X
		quad(pose, consumer, sprite, light, shade.at(0.0F, 1.0F, 0.0F), 0.0F, 1.0F, 0.0F,
				x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
				0.0F, dx, 0.0F, 0.0F, dz, 0.0F, dz, dx);
		quad(pose, consumer, sprite, light, shade.at(0.0F, -1.0F, 0.0F), 0.0F, -1.0F, 0.0F,
				x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
				dz, dx, dz, 0.0F, 0.0F, 0.0F, 0.0F, dx);
		// x-normal ends (small)
		quad(pose, consumer, sprite, light, shade.at(-1.0F, 0.0F, 0.0F), -1.0F, 0.0F, 0.0F,
				x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1,
				dz, dy, 0.0F, dy, 0.0F, 0.0F, dz, 0.0F);
		quad(pose, consumer, sprite, light, shade.at(1.0F, 0.0F, 0.0F), 1.0F, 0.0F, 0.0F,
				x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1,
				dz, 0.0F, 0.0F, 0.0F, 0.0F, dy, dz, dy);
	}

	/** One rim plank: a ring wedge with proportional UVs (U along the arc, V across it). */
	private static void renderRingSegment(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			float innerRadius, float outerRadius, float startDegrees, float endDegrees, float z0, float z1,
			int light, Shade shade, float uOff, float vOff) {
		float start = (float) Math.toRadians(startDegrees);
		float end = (float) Math.toRadians(endDegrees);
		float sx = (float) Math.cos(start);
		float sy = (float) Math.sin(start);
		float ex = (float) Math.cos(end);
		float ey = (float) Math.sin(end);
		float mx = (float) Math.cos((start + end) * 0.5F);
		float my = (float) Math.sin((start + end) * 0.5F);

		float x0 = innerRadius * sx;
		float y0 = innerRadius * sy;
		float x1 = outerRadius * sx;
		float y1 = outerRadius * sy;
		float x2 = outerRadius * ex;
		float y2 = outerRadius * ey;
		float x3 = innerRadius * ex;
		float y3 = innerRadius * ey;

		float radial = outerRadius - innerRadius;
		float arc = Math.min(1.0F, (innerRadius + outerRadius) * 0.5F * (end - start));
		float dz = z1 - z0;
		float ua = Math.min(uOff, 1.0F - arc);
		float va = Math.min(vOff, 1.0F - radial);

		// front / back faces
		quad(pose, consumer, sprite, light, shade.at(0.0F, 0.0F, -1.0F), 0.0F, 0.0F, -1.0F,
				x0, y0, z0, x1, y1, z0, x2, y2, z0, x3, y3, z0,
				ua, va + radial, ua, va, ua + arc, va, ua + arc, va + radial);
		quad(pose, consumer, sprite, light, shade.at(0.0F, 0.0F, 1.0F), 0.0F, 0.0F, 1.0F,
				x3, y3, z1, x2, y2, z1, x1, y1, z1, x0, y0, z1,
				ua + arc, va + radial, ua + arc, va, ua, va, ua, va + radial);

		// outer face (normal = mid-angle radial, out)
		quad(pose, consumer, sprite, light, shade.at(mx, my, 0.0F), mx, my, 0.0F,
				x1, y1, z0, x1, y1, z1, x2, y2, z1, x2, y2, z0,
				ua, va + dz, ua, va, ua + arc, va, ua + arc, va + dz);
		// inner face (normal = mid-angle radial, in)
		quad(pose, consumer, sprite, light, shade.at(-mx, -my, 0.0F), -mx, -my, 0.0F,
				x3, y3, z0, x3, y3, z1, x0, y0, z1, x0, y0, z0,
				ua, va + dz, ua, va, ua + arc, va, ua + arc, va + dz);
		// No angular end caps: rings are closed (adjacent segments share corner vertices),
		// so end faces would be interior coplanar quads — invisible at best, z-fighting at worst.
	}

	/**
	 * Octagonal prism along Z (hub / axle): 8 side faces (V along Z, grain of the log) and
	 * two octagonal end caps (fan of wedges, sprite centred on the axis).
	 */
	private void renderOctagonalPrism(PoseStack poseStack, SubmitNodeCollector collector,
			TextureAtlasSprite side, TextureAtlasSprite cap, float radius, float z0, float z1,
			int light, Shade shade) {
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) -> {
			float dz = Math.min(1.0F, z1 - z0);
			float width = Math.min(1.0F, 2.0F * radius * (float) Math.sin(Math.PI / 8.0));
			for (int k = 0; k < 8; k++) {
				float a0 = (float) (Math.PI / 8.0 + k * Math.PI / 4.0);
				float a1 = (float) (Math.PI / 8.0 + (k + 1) * Math.PI / 4.0);
				float am = (a0 + a1) * 0.5F;
				float px0 = radius * (float) Math.cos(a0);
				float py0 = radius * (float) Math.sin(a0);
				float px1 = radius * (float) Math.cos(a1);
				float py1 = radius * (float) Math.sin(a1);
				float nx = (float) Math.cos(am);
				float ny = (float) Math.sin(am);
				// side face
				quad(pose, consumer, side, light, shade.at(nx, ny, 0.0F), nx, ny, 0.0F,
						px0, py0, z0, px0, py0, z1, px1, py1, z1, px1, py1, z0,
						0.0F, dz, 0.0F, 0.0F, width, 0.0F, width, dz);
				// front cap wedge (normal -Z), sprite centred on the axis
				quad(pose, consumer, cap, light, shade.at(0.0F, 0.0F, -1.0F), 0.0F, 0.0F, -1.0F,
						0.0F, 0.0F, z0, px0, py0, z0, px1, py1, z0, px1, py1, z0,
						0.5F, 0.5F, 0.5F + px0, 0.5F + py0, 0.5F + px1, 0.5F + py1, 0.5F + px1, 0.5F + py1);
				// back cap wedge (normal +Z)
				quad(pose, consumer, cap, light, shade.at(0.0F, 0.0F, 1.0F), 0.0F, 0.0F, 1.0F,
						0.0F, 0.0F, z1, px1, py1, z1, px0, py0, z1, px0, py0, z1,
						0.5F, 0.5F, 0.5F + px1, 0.5F + py1, 0.5F + px0, 0.5F + py0, 0.5F + px0, 0.5F + py0);
			}
		});
	}

	/** Emits one quad; UV values are fractions (0..1) of the sprite. */
	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
			int light, float shade, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx2, float dy2, float dz2,
			float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		vertex(pose, consumer, ax, ay, az, sprite.getU(ua), sprite.getV(va), light, shade, nx, ny, nz);
		vertex(pose, consumer, bx, by, bz, sprite.getU(ub), sprite.getV(vb), light, shade, nx, ny, nz);
		vertex(pose, consumer, cx, cy, cz, sprite.getU(uc), sprite.getV(vc), light, shade, nx, ny, nz);
		vertex(pose, consumer, dx2, dy2, dz2, sprite.getU(ud), sprite.getV(vd), light, shade, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
			float u, float v, int light, float shade, float normalX, float normalY, float normalZ) {
		consumer.addVertex(pose, x, y, z)
				.setColor(shade, shade, shade, 1.0F)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, normalX, normalY, normalZ);
	}

	/**
	 * Vanilla directional diffuse for an element rotated by {@code angle} around the wheel
	 * axis and yawed to {@code facing}. Normals passed to {@link #at} are element-local;
	 * the result is the classic {@code 0.6x² + y²(3+y)/4 + 0.8z²} world-space shade.
	 */
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
			return Math.min(1.0F, 0.6F * ax * ax + wy * wy * (3.0F + wy) * 0.25F + 0.8F * az * az);
		}
	}

	public static final class State extends BlockEntityRenderState {
		private Direction facing = Direction.NORTH;
		private int production;
		private float angle;
	}
}
