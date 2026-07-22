package dev.alaindustrial.core.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wheel-clearance check for the water mill (MOD-179), the water-side analogue of
 * {@link WindMillClearance}. The wheel is an open disc of rim radius ~1.32 blocks whose centre sits
 * 1.02 blocks in front of the mill along {@code FACING} (the renderer's {@code translate(0, 0, -1.02)}),
 * so the whole disc lives in the <b>front neighbour's</b> block column. The rim reaches ~0.82 block
 * into each in-plane neighbour of that front cell, so a solid block in the front cell or beside/above
 * it makes the wheel visibly clip straight through it — the broken look MOD-175/MOD-179 exists to
 * prevent. This check also closes the interference blind spot: two mills placed face-to-face with
 * <b>no</b> gap put each wheel's hub inside the other mill's solid front cell, where the AABB-overlap
 * test of {@link WaterMillInterference} cannot see it (boxes overlap only at centre distances
 * 1.165–2.915).
 *
 * <p>Checked cells, all relative to {@code pos.relative(facing)} (the front cell the wheel lives in) —
 * the wheel needs a clear vertical slot, open on top and both sides:
 *
 * <ul>
 *   <li><b>Front</b> — the hub cell: it must be free.</li>
 *   <li><b>Front-above</b> — the upper rim arc.</li>
 *   <li><b>Front-left / Front-right</b> — the horizontal rim arcs (the sides the player sees the rim
 *       sweep through).</li>
 * </ul>
 *
 * <p>The cell <b>below</b> the front cell is the one exemption: the lower arc of a water wheel is meant
 * to dip into the river — into water, or visually into a shallow river bed — so a solid block there
 * does not stall the mill. Requiring the bottom cell to be clear would forbid every riverbed placement.
 *
 * <p>A position is free when its state reports {@link BlockState#canBeReplaced()} — air, water, tall
 * grass and other filler. Anything with real collision (stone, another mill's casing, glass) blocks
 * the wheel.
 */
public final class WaterMillClearance {
	private WaterMillClearance() {
	}

	/**
	 * True when a non-replaceable block occupies the front cell (hub) or the cell above / to either
	 * side of it (the rim arcs) — the wheel would clip through it and must stall. The cell below the
	 * front cell is exempt (the wheel dips into the river/riverbed).
	 *
	 * @param level  the mill's level
	 * @param pos    the mill's block position
	 * @param facing the mill's {@code FACING} (the wheel face)
	 * @return {@code true} if the hub cell or one of its top/side neighbours is a non-replaceable block
	 */
	public static boolean hasObstruction(Level level, BlockPos pos, Direction facing) {
		BlockPos front = pos.relative(facing);
		return blocks(level, front)
				|| blocks(level, front.above())
				|| blocks(level, front.relative(facing.getClockWise()))
				|| blocks(level, front.relative(facing.getCounterClockWise()));
	}

	/** True when the block at {@code at} cannot be replaced (solid enough to stall the wheel). */
	private static boolean blocks(Level level, BlockPos at) {
		return !level.getBlockState(at).canBeReplaced();
	}
}
