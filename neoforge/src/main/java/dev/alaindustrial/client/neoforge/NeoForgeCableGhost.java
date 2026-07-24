package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.block.CableBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
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

/**
 * NeoForge cable-placement ghost (MOD-022, the NeoForge counterpart to the Fabric
 * {@code CablePlacementPreview}). While the player holds one of the mod's cables and aims at a block,
 * draws a translucent hologram of the cable in the exact cell it would be placed into, with the exact
 * connection arms it will have once placed. Client-only; nothing is written to the world before the click.
 *
 * <p><b>Rendering path (verified against 26.2).</b> Fabric feeds a {@code DrawableGizmoPrimitives} to the
 * render-time {@code SubmitNodeCollector} exposed by its {@code LevelRenderContext}; NeoForge's
 * {@code RenderLevelStageEvent} exposes no such collector, so this uses the vanilla per-tick gizmo API
 * instead — {@link Minecraft#collectPerTickGizmos()} opens the ambient {@code GizmoCollector} and
 * {@link Gizmos#cuboid} submits axis-aligned boxes for the cable core + each connection arm. Because the
 * ghost is nothing but axis-aligned boxes, this is visually faithful to the Fabric version (unlike the
 * network overlay, whose custom oriented-tube quads can only be approximated by this API). Submitted once
 * per client tick from {@code IndustrializationNeoForgeClient}; a per-tick refresh follows the cursor.
 *
 * <p><b>Correctness without a separate connection calc.</b> The ghost's {@link BlockState} comes from
 * {@link CableBlock#getStateForPlacement(BlockPlaceContext)} — the same method the real placement uses —
 * so preview connections match post-placement by construction.
 */
public final class NeoForgeCableGhost {

	/** Faint translucent light-blue fill; bright light-blue stroke outline — mirrors the Fabric ghost colours. */
	private static final int FILL_COLOR = 0x3380E5FF;
	private static final int EDGE_COLOR = 0xE6D8F5FF;
	private static final float EDGE_WIDTH = 2.5f;
	private static final GizmoStyle STYLE = GizmoStyle.strokeAndFill(EDGE_COLOR, EDGE_WIDTH, FILL_COLOR);

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

	/**
	 * Low-arm boxes for a horizontal connection to a half-block neighbour, matching
	 * {@code CableBlock.ARMS_LOW} exactly (two boxes each: the bridge at cable height plus the drop that
	 * hugs the slab's side). Without these the ghost drew the centred arm floating over a solar panel and
	 * the block then snapped to the low shape once placed — the very mismatch MOD-195 fixed in the models.
	 */
	private static final Map<Direction, AABB[]> ARMS_LOW = new EnumMap<>(Direction.class);
	static {
		ARMS_LOW.put(Direction.NORTH, new AABB[] {
			new AABB(5 / 16.0, 5 / 16.0, 2 / 16.0, 11 / 16.0, 11 / 16.0, 5 / 16.0),
			new AABB(5 / 16.0, 2 / 16.0, 0, 11 / 16.0, 8 / 16.0, 2 / 16.0)});
		ARMS_LOW.put(Direction.SOUTH, new AABB[] {
			new AABB(5 / 16.0, 5 / 16.0, 11 / 16.0, 11 / 16.0, 11 / 16.0, 14 / 16.0),
			new AABB(5 / 16.0, 2 / 16.0, 14 / 16.0, 11 / 16.0, 8 / 16.0, 1.0)});
		ARMS_LOW.put(Direction.WEST, new AABB[] {
			new AABB(2 / 16.0, 5 / 16.0, 5 / 16.0, 5 / 16.0, 11 / 16.0, 11 / 16.0),
			new AABB(0, 2 / 16.0, 5 / 16.0, 2 / 16.0, 8 / 16.0, 11 / 16.0)});
		ARMS_LOW.put(Direction.EAST, new AABB[] {
			new AABB(11 / 16.0, 5 / 16.0, 5 / 16.0, 14 / 16.0, 11 / 16.0, 11 / 16.0),
			new AABB(14 / 16.0, 2 / 16.0, 5 / 16.0, 1.0, 8 / 16.0, 11 / 16.0)});
	}

	private NeoForgeCableGhost() {
	}

	/** Submit the ghost for this client tick, if the player is holding a cable aimed at a placeable cell. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null || player.isSpectator()) {
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

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

		BlockPlaceContext ctx = new BlockPlaceContext(player, hand, player.getItemInHand(hand), hit);
		if (!ctx.canPlace()) {
			return;
		}
		BlockState ghost = cable.getStateForPlacement(ctx);
		if (ghost == null) {
			return;
		}
		var pos = ctx.getClickedPos();

		try (Gizmos.TemporaryCollection collection = mc.collectPerTickGizmos()) {
			Gizmos.cuboid(CORE.move(pos), STYLE);
			for (Direction dir : Direction.values()) {
				BooleanProperty prop = PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
				if (ghost.getValue(prop)) {
					// Mirror the block's own shape choice: a horizontal arm toward a half-block neighbour
					// drops to ARMS_LOW, so the ghost must too (MOD-195/MOD-219).
					BooleanProperty lowProp = CableBlock.lowFlagFor(dir);
					if (lowProp != null && ghost.getValue(lowProp)) {
						for (AABB low : ARMS_LOW.get(dir)) {
							Gizmos.cuboid(low.move(pos), STYLE);
						}
					} else {
						Gizmos.cuboid(ARMS.get(dir).move(pos), STYLE);
					}
				}
			}
		}
	}
}
