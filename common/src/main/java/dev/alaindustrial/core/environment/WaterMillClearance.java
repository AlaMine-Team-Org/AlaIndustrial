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
 * <p><b>Checked cells: the whole 3×3 of the wheel's plane</b> (MOD-355) — the front column and the one
 * on each side, each at the level of the front cell, one above and one below. That is what the geometry
 * demands: the disc's half-size is {@value WaterMillWheelGeometry#DISC_HALF_SIZE} about a centre sitting
 * at (0.5, 0.5) of the front cell, so its box covers all nine cells. The diagonals are not a rounding
 * artefact either — a diagonal cell's nearest corner is 0.707 away while the disc reaches 1.433 along
 * that diagonal, so the rim enters it by nearly half a block and is plainly visible cutting through.
 *
 * <p><b>The bottom row used to be exempt, and is not any more (MOD-355).</b> The exemption existed so a
 * mill could stand on a river bed, but it let players sink the lower third of the wheel into solid stone
 * and keep generating — the exact "broken wheel" look MOD-175/MOD-179 exist to prevent. It was never
 * needed: a cell counts as free when it is {@link BlockState#canBeReplaced()}, and water is replaceable,
 * so a wheel hanging over a channel passes just as before. Since MOD-352 the cell below the front one is
 * also the undershot drive cell, so in any working build it holds water rather than rock.
 *
 * <p>A position is free when its state reports {@link BlockState#canBeReplaced()} — air, water, tall
 * grass and other filler. Anything with real collision (stone, another mill's casing, glass) blocks
 * the wheel.
 */
public final class WaterMillClearance {
	private WaterMillClearance() {
	}

	/**
	 * True when a non-replaceable block occupies any of the nine cells of the wheel's plane — the
	 * wheel would visibly clip through it and must stall. Nine reads a tick is still cheap enough to
	 * run unconditionally, like the wind mill's blade clearance.
	 *
	 * @param level  the mill's level
	 * @param pos    the mill's block position
	 * @param facing the mill's {@code FACING} (the wheel face)
	 * @return {@code true} if any cell of the wheel's 3×3 plane holds a non-replaceable block
	 */
	public static boolean hasObstruction(Level level, BlockPos pos, Direction facing) {
		BlockPos front = pos.relative(facing);
		BlockPos[] columns = {
			front,
			front.relative(facing.getClockWise()),
			front.relative(facing.getCounterClockWise()),
		};
		for (BlockPos column : columns) {
			if (blocks(level, column) || blocks(level, column.above()) || blocks(level, column.below())) {
				return true;
			}
		}
		return false;
	}

	/** True when the block at {@code at} cannot be replaced (solid enough to stall the wheel). */
	private static boolean blocks(Level level, BlockPos at) {
		return !level.getBlockState(at).canBeReplaced();
	}
}
