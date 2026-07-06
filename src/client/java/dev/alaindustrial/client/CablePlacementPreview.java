package dev.alaindustrial.client;

import dev.alaindustrial.block.CableBlock;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ghost preview for cable placement (MOD-001): while the player holds one of the mod's cables and
 * aims at a block, draws a translucent hologram of the cable in the exact cell it would be placed
 * into, with the exact same connection arms it will have once placed. Client-only — nothing is
 * written to the world before the click.
 *
 * <p><b>Why this is correct without a separate connection calculation.</b> The ghost's
 * {@link BlockState} comes from {@link CableBlock#getStateForPlacement(BlockPlaceContext)} — the very
 * method the game itself calls on the real placement — so the acceptance criterion "connections in
 * the preview match the ones after placement" is satisfied by construction, not by a parallel copy of
 * the logic that could drift. The context is rebuilt from the live hit result every frame, so the
 * preview follows the cursor and vanishes the instant the player looks away or swaps the item, with
 * no state to clear (hence no lingering-artifact bug).
 *
 * <p><b>Rendering path.</b> Reuses the confirmed-working gizmo pipeline from
 * {@link NetworkVisualizationClient}: geometry is fed to a {@link DrawableGizmoPrimitives} in
 * <em>absolute world coordinates</em> (the gizmo renderer subtracts the camera position itself — see
 * {@code GizmoFeatureRenderer.buildQuads}, which is why no manual camera math is done here) and
 * submitted once per frame. The ghost is drawn as filled boxes matching the cable's collision shape
 * (a 6px core plus an arm toward each connection, identical to {@code CableBlock}'s
 * {@code VoxelShape}). A colour with alpha &lt; {@code 0xFF} is routed by
 * {@code DrawableGizmoPrimitives.getGroup} into the translucent group, and the underlying
 * {@code debugFilledBox} render type blends with {@code BlendFunction.TRANSLUCENT} — so the fill is
 * genuinely see-through, not a solid block.
 */
public final class CablePlacementPreview {

	/**
	 * Faint translucent fill for the box interiors — deliberately low alpha (0x33 ≈ 20%) so the middle
	 * of the ghost reads as a soft haze rather than a solid block; the shape is carried mostly by the
	 * edge outline below. Alpha &lt; 0xFF routes it into the translucent gizmo group so it blends.
	 */
	private static final int FILL_COLOR = 0x3380E5FF;
	/**
	 * Bright, near-opaque light-blue outline drawn along every box edge — this is what gives the ghost
	 * its crisp "wireframe" silhouette and clearly reads the connection arms. Higher alpha (0xE6) and a
	 * lighter tint than {@link #FILL_COLOR} so the outline pops against the faint fill.
	 */
	private static final int EDGE_COLOR = 0xE6D8F5FF;
	/** Line width for the edge outline. A hairline (~1px) is nearly invisible at play distance, so the
	 * outline is drawn a little thicker to stay legible. */
	private static final float EDGE_WIDTH = 2.5f;

	/** Cable core in block-local space (0..1), matching {@code CableBlock.CORE} = box(5,5,5,11,11,11). */
	private static final AABB CORE = new AABB(5 / 16.0, 5 / 16.0, 5 / 16.0, 11 / 16.0, 11 / 16.0, 11 / 16.0);

	/** Per-direction arm boxes in block-local space, matching {@code CableBlock.ARMS} exactly. */
	private static final Map<Direction, AABB> ARMS = new EnumMap<>(Direction.class);
	static {
		ARMS.put(Direction.DOWN, new AABB(5 / 16.0, 0, 5 / 16.0, 11 / 16.0, 5 / 16.0, 11 / 16.0));
		ARMS.put(Direction.UP, new AABB(5 / 16.0, 11 / 16.0, 5 / 16.0, 11 / 16.0, 1.0, 11 / 16.0));
		ARMS.put(Direction.NORTH, new AABB(5 / 16.0, 5 / 16.0, 0, 11 / 16.0, 11 / 16.0, 5 / 16.0));
		ARMS.put(Direction.SOUTH, new AABB(5 / 16.0, 5 / 16.0, 11 / 16.0, 11 / 16.0, 11 / 16.0, 1.0));
		ARMS.put(Direction.WEST, new AABB(0, 5 / 16.0, 5 / 16.0, 5 / 16.0, 11 / 16.0, 11 / 16.0));
		ARMS.put(Direction.EAST, new AABB(11 / 16.0, 5 / 16.0, 5 / 16.0, 1.0, 11 / 16.0, 11 / 16.0));
	}

	private CablePlacementPreview() {
	}

	public static void init() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(CablePlacementPreview::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null || player.isSpectator()) {
			return;
		}
		// Only a solid block hit gives a placement cell; a MISS BlockHitResult would place nothing.
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		// Find a cable BlockItem in either hand (main takes priority, as vanilla placement does).
		CableBlock cable = null;
		InteractionHand hand = null;
		for (InteractionHand h : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(h);
			if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CableBlock c) {
				cable = c;
				hand = h;
				break;
			}
		}
		if (cable == null) {
			return;
		}

		// Reuse the real placement pipeline so the ghost's connections cannot drift from reality.
		BlockPlaceContext ctx = new BlockPlaceContext(player, hand, player.getItemInHand(hand), hit);
		if (!ctx.canPlace()) {
			return;
		}
		BlockState ghost = cable.getStateForPlacement(ctx);
		if (ghost == null) {
			return;
		}
		BlockPos pos = ctx.getClickedPos();

		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
		addBox(gizmos, CORE, pos);
		for (Direction dir : Direction.values()) {
			BooleanProperty prop = PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
			if (ghost.getValue(prop)) {
				addBox(gizmos, ARMS.get(dir), pos);
			}
		}
		// The submit flag only affects line primitives (GizmoFeatureRenderer.buildLines); filled quads
		// always depth-test via the debugFilledBox pipeline, so the value is irrelevant for this
		// quad-only geometry — pass false.
		gizmos.submit(context.submitNodeCollector(), context.levelState().cameraRenderState, false);
	}

	/**
	 * Adds one ghost box in absolute world space — {@code local} is the block-local shape, translated
	 * by the target cell {@code pos}. The gizmo renderer expects world coordinates and applies the
	 * camera offset itself, so no camera-relative math happens here.
	 *
	 * <p>Drawn as two layers from a single set of eight corners: six faint translucent quads
	 * ({@link #FILL_COLOR}) for a soft interior haze, plus twelve bright edge lines ({@link #EDGE_COLOR},
	 * {@link #EDGE_WIDTH}) that outline the box for a crisp wireframe silhouette. The faint fill keeps
	 * the middle unobtrusive while the outline carries the shape and connection arms.
	 */
	private static void addBox(DrawableGizmoPrimitives gizmos, AABB local, BlockPos pos) {
		double x0 = pos.getX() + local.minX;
		double y0 = pos.getY() + local.minY;
		double z0 = pos.getZ() + local.minZ;
		double x1 = pos.getX() + local.maxX;
		double y1 = pos.getY() + local.maxY;
		double z1 = pos.getZ() + local.maxZ;

		Vec3 p000 = new Vec3(x0, y0, z0);
		Vec3 p100 = new Vec3(x1, y0, z0);
		Vec3 p010 = new Vec3(x0, y1, z0);
		Vec3 p110 = new Vec3(x1, y1, z0);
		Vec3 p001 = new Vec3(x0, y0, z1);
		Vec3 p101 = new Vec3(x1, y0, z1);
		Vec3 p011 = new Vec3(x0, y1, z1);
		Vec3 p111 = new Vec3(x1, y1, z1);

		// Faint interior fill — six faces.
		gizmos.addQuad(p000, p100, p110, p010, FILL_COLOR); // -Z (north)
		gizmos.addQuad(p101, p001, p011, p111, FILL_COLOR); // +Z (south)
		gizmos.addQuad(p001, p000, p010, p011, FILL_COLOR); // -X (west)
		gizmos.addQuad(p100, p101, p111, p110, FILL_COLOR); // +X (east)
		gizmos.addQuad(p001, p101, p100, p000, FILL_COLOR); // -Y (bottom)
		gizmos.addQuad(p010, p110, p111, p011, FILL_COLOR); // +Y (top)

		// Bright outline — twelve edges (4 bottom + 4 top + 4 vertical).
		gizmos.addLine(p000, p100, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p100, p101, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p101, p001, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p001, p000, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p010, p110, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p110, p111, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p111, p011, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p011, p010, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p000, p010, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p100, p110, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p101, p111, EDGE_COLOR, EDGE_WIDTH);
		gizmos.addLine(p001, p011, EDGE_COLOR, EDGE_WIDTH);
	}
}
