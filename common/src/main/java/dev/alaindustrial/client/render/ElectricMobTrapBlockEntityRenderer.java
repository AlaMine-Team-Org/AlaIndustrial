package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.block.entity.ElectricMobTrapBlockEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The discharge of the Electric Mob Trap (MOD-818, animation E3 of the concept): for a few ticks after a shock
 * the node ball flashes and four zigzag arcs run down the open sides, between the insulator strings, with a
 * short jumper from each ring joint to the node.
 *
 * <p><b>Why a renderer and not a model state.</b> The quiet pulse is a texture animation of the lit model; a
 * discharge that lasts 0.4 s and changes shape every two ticks is neither a blockstate (it would be a block
 * update per frame) nor a texture. The server sends one vanilla block event per shocking pulse; the block
 * entity notes the client game time it arrived ({@code shockedAt}) and this renderer reads it. Nothing is
 * stored in the world and nothing travels beyond the block event.
 *
 * <p><b>Shape.</b> Each arc is a chain of {@link #SEGMENTS} thin ribbons from the ring's underside to the
 * base, wandering sideways by up to {@link #WANDER} pixels; every ribbon is two crossed quads, so it shows
 * from any side. The wander is a hash of the block position, the arc and the {@code age / }{@link #STEP_TICKS}
 * step, so the bolt re-forms every two ticks, differently on every trap, and a frame is a pure function of its
 * inputs (no random state, no flicker between a frame's two passes). The geometry stays inside the block: the
 * arcs lie in the open sides' planes at one to two pixels from the edge, between the corner columns.
 *
 * <p><b>Light.</b> Everything is drawn full bright on the flat white-concrete sprite and coloured by the
 * vertex, the way the centrifuge draws its glow: a discharge is a light source, the block around it lights
 * normally. Only client types appear here, and only through the vertex API ({@code QuadEmitter}), never a
 * backend package.
 */
public final class ElectricMobTrapBlockEntityRenderer
		implements BlockEntityRenderer<ElectricMobTrapBlockEntity, ElectricMobTrapBlockEntityRenderer.State> {

	/** A flat, near-white sprite: the vertex colour is what is seen. */
	private static final SpriteId FLAT = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("white_concrete");
	private static final RenderType TYPE = FLAT.renderType(ignored -> Sheets.cutoutBlockItemSheet());

	/** How long a discharge lasts, in ticks: 0.4 s. */
	static final float LIFETIME_TICKS = 8.0F;
	/** The node flashes for the first ticks only; the arcs last the whole lifetime. */
	private static final float FLASH_TICKS = 3.0F;
	/** The bolt re-forms this often: every two ticks. */
	private static final int STEP_TICKS = 2;
	private static final int SEGMENTS = 6;
	/** Sideways wander of an arc's inner points, in model pixels. */
	private static final float WANDER = 1.8F;
	/** Half-width of a ribbon, in model pixels: a bolt one pixel wide. */
	private static final float HALF_WIDTH = 0.5F;
	private static final float PIXEL = 1.0F / 16.0F;

	/** The arc's plane lies this deep inside its side, in pixels: clear of the ring's 2 px rim, in the open. */
	private static final float ARC_DEPTH = 1.1F;
	/** From the underside of the ring to the top of the base belt, in pixels. */
	private static final float ARC_TOP = 10.0F;
	private static final float ARC_BOTTOM = 3.0F;

	/** Bolt colours: hot core at the start, the lit cyan of the model at the end. */
	private static final int HOT = 0xFFE2FAFF;
	private static final int COOL = 0xFF5CCBF0;
	private static final int FLASH = 0xFFFFFFFF;

	private final SpriteGetter sprites;

	public ElectricMobTrapBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		/** Ticks since the last shock, or a negative number when there is nothing to draw. */
		float age = -1.0F;
		/** The block position, hashed: so two traps never strike in the same shape. */
		long seed;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ElectricMobTrapBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		state.age = -1.0F;
		if (entity.getLevel() == null || !entity.hasShocked()) {
			return;
		}
		float age = (entity.getLevel().getGameTime() - entity.shockedAt()) + partialTicks;
		if (age >= 0.0F && age < LIFETIME_TICKS) {
			state.age = age;
			state.seed = entity.getBlockPos().asLong();
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.age < 0.0F) {
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(FLAT);
		float fade = 1.0F - state.age / LIFETIME_TICKS;
		int step = (int) (state.age / STEP_TICKS);
		int colour = lerp(COOL, HOT, fade);
		collector.submitCustomGeometry(poseStack, TYPE, (pose, consumer) -> {
			QuadEmitter quads = new QuadEmitter(pose, consumer, LightCoordsUtil.FULL_BRIGHT).sprite(sprite);
			quads.color(colour);
			for (int side = 0; side < 4; side++) {
				arc(quads, state.seed, step, side);
				jumper(quads, state.seed, step, side);
			}
			if (state.age < FLASH_TICKS) {
				quads.color(lerp(COOL, FLASH, 1.0F - state.age / FLASH_TICKS));
				node(quads);
			}
		});
	}

	// --- geometry, in model pixels; every point goes through side() to the four sides ---

	/**
	 * The arc down one open side: from the ring's underside at the side's middle to the base, wandering in
	 * the side's plane ({@code u}) and a little in depth ({@code v}); both ends are fixed.
	 */
	private static void arc(QuadEmitter quads, long seed, int step, int side) {
		float u = 8.0F;
		float v = ARC_DEPTH;
		float y = ARC_TOP;
		float dy = (ARC_TOP - ARC_BOTTOM) / SEGMENTS;
		for (int i = 1; i <= SEGMENTS; i++) {
			boolean last = i == SEGMENTS;
			float nu = last ? 8.0F : 8.0F + WANDER * noise(seed, step, side, i, 0);
			float nv = last ? ARC_DEPTH : ARC_DEPTH + 0.5F * noise(seed, step, side, i, 1);
			float ny = y - dy;
			ribbon(quads, side, u, y, v, nu, ny, nv);
			u = nu;
			v = nv;
			y = ny;
		}
	}

	/** A short jagged jumper from the ring joint's inner end to the node ball, two bends. */
	private static void jumper(QuadEmitter quads, long seed, int step, int side) {
		float[] us = {8.0F, 8.0F + 0.9F * noise(seed, step, side, 20, 0), 8.0F + 0.9F * noise(seed, step, side, 21, 0),
				8.0F};
		float[] ys = {10.5F, 10.9F, 10.0F, 9.6F};
		float[] vs = {5.0F, 5.6F, 6.0F, 6.4F};
		for (int i = 0; i < 3; i++) {
			ribbon(quads, side, us[i], ys[i], vs[i], us[i + 1], ys[i + 1], vs[i + 1]);
		}
	}

	/** The node's flash: a cube a little larger than the ball (6..10, 8..11, 6..10) round it. */
	private static void node(QuadEmitter quads) {
		float x0 = 5.6F * PIXEL, x1 = 10.4F * PIXEL;
		float y0 = 7.6F * PIXEL, y1 = 11.4F * PIXEL;
		for (Direction face : Direction.values()) {
			quads.cubeFace(face, x0, y0, x0, x1, y1, x1, 0.0F, 0.0F, 1.0F, 1.0F);
		}
	}

	/** One bolt segment, as two crossed ribbons so it shows from any direction. */
	private static void ribbon(QuadEmitter quads, int side, float u0, float y0, float v0, float u1, float y1,
			float v1) {
		float du = u1 - u0;
		float dy = y1 - y0;
		float length = Mth.sqrt(du * du + dy * dy);
		if (length < 1.0E-4F) {
			return;
		}
		// The in-plane perpendicular of the segment, and the depth axis: the two ribbons' widths.
		float pu = -dy / length * HALF_WIDTH;
		float py = du / length * HALF_WIDTH;
		quad(quads, side, u0 - pu, y0 - py, v0, u0 + pu, y0 + py, v0, u1 + pu, y1 + py, v1, u1 - pu, y1 - py, v1);
		quad(quads, side, u0, y0, v0 - HALF_WIDTH, u0, y0, v0 + HALF_WIDTH, u1, y1, v1 + HALF_WIDTH, u1, y1,
				v1 - HALF_WIDTH);
	}

	/** One quad in a side's own coordinates, both windings, mapped to the block. */
	private static void quad(QuadEmitter quads, int side, float u0, float y0, float v0, float u1, float y1, float v1,
			float u2, float y2, float v2, float u3, float y3, float v3) {
		float[] a = side(side, u0, y0, v0);
		float[] b = side(side, u1, y1, v1);
		float[] c = side(side, u2, y2, v2);
		float[] d = side(side, u3, y3, v3);
		quads.quadBothSides(0.0F, 1.0F, 0.0F,
				a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2],
				0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F);
	}

	/**
	 * A side's own coordinates to the block's, in blocks: {@code u} along the side (0..16), {@code y} up and
	 * {@code v} the depth in from the side's edge. Side 0 is the north face, then east, south, west.
	 */
	static float[] side(int side, float u, float y, float v) {
		float x;
		float z;
		switch (side) {
			case 0 -> {
				x = u;
				z = v;
			}
			case 1 -> {
				x = 16.0F - v;
				z = u;
			}
			case 2 -> {
				x = 16.0F - u;
				z = 16.0F - v;
			}
			default -> {
				x = v;
				z = 16.0F - u;
			}
		}
		return new float[] {x * PIXEL, y * PIXEL, z * PIXEL};
	}

	// --- shape and colour ---

	/** A pure, well-mixed value in {@code [-1, 1]} from the arguments: the same inputs, the same bolt. */
	static float noise(long seed, int step, int side, int index, int axis) {
		long h = seed * 0x9E3779B97F4A7C15L + step * 0xC2B2AE3D27D4EB4FL + side * 0x165667B19E3779F9L
				+ index * 0x27D4EB2F165667C5L + axis * 0x94D049BB133111EBL;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return ((h >>> 11) & 0xFFFFFL) / (float) 0x7FFFF - 1.0F;
	}

	/** Linear blend of two opaque ARGB colours: {@code t = 0} is {@code from}, {@code t = 1} is {@code to}. */
	private static int lerp(int from, int to, float t) {
		float k = Mth.clamp(t, 0.0F, 1.0F);
		int r = Math.round(Mth.lerp(k, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(k, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
