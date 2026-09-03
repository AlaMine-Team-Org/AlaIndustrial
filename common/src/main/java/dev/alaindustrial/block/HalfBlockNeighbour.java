package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The one place that decides whether a neighbour is a "half-block" — low enough that a transport
 * line joining it sideways must drop its sleeve instead of meeting thin air above its surface.
 *
 * <p>Cables answered this question first (MOD-042) and pipes ask exactly the same one (MOD-540).
 * The threshold lives here rather than in each block because MOD-195 is the standing lesson of this
 * mod's connection geometry: shared logic with per-block copies of its numbers drifts, and the drift
 * shows up as drawn-but-unclickable geometry that only an audit finds.
 *
 * <p>The test is deliberately generic — it reads the neighbour's own shape, never a list of block
 * types — so a half-block added years from now connects correctly without touching this file.
 */
public final class HalfBlockNeighbour {
	/** Neighbours whose shape tops out at or below this height (in blocks) are half-blocks. */
	public static final double LOW_NEIGHBOUR_THRESHOLD = 0.5;

	private HalfBlockNeighbour() {
	}

	/**
	 * Whether the block at {@code neighborPos} is low enough to take a dropped arm. Empty shapes
	 * (air, fluids) are not low: there is nothing there to hug. {@link LevelReader} extends
	 * {@link BlockGetter}, so it satisfies {@link BlockState#getShape(BlockGetter, BlockPos)}.
	 */
	public static boolean isLow(LevelReader level, BlockPos neighborPos) {
		VoxelShape shape = level.getBlockState(neighborPos).getShape(level, neighborPos);
		return !shape.isEmpty() && shape.bounds().maxY <= LOW_NEIGHBOUR_THRESHOLD;
	}
}
