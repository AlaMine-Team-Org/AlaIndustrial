package dev.alaindustrial.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blade-clearance check for the wind mill family. The rotor is a flat 2×2-block quad
 * ({@code HALF_SIZE = 1.0} in {@code WindMillRotorBlockEntityRenderer}) that floats ~0.58 block in
 * front of the mill's {@code FACING} face and spins around the axis pointing along {@code FACING}.
 *
 * <p>Because the 0.58 push moves the quad past the block boundary (0.5 + 0.58 &gt; 1.0, the centre lands
 * at ~1.08 from the mill's origin), the entire spinning disc lives in the <b>front neighbour's</b>
 * block space — the block at {@code pos.relative(facing)} — not in the mill's own block. The blade
 * tips reach a radius of √2 ≈ 1.41 from that front centre, so as the rotor turns it sweeps through:
 *
 * <ul>
 *   <li><b>Front</b> — the block the disc lives in ({@code pos.relative(facing)}): the rotor physically
 *       occupies it, so it must be air.</li>
 *   <li><b>Front sides</b> — one block left/right of the front block
 *       ({@code front.relative(clockWise)} / {@code counterClockWise()}): the horizontal blade tips.</li>
 *   <li><b>Front pit</b> — the three blocks under the front block (centre + both sides) so the lower
 *       blade arc has air to dip into.</li>
 * </ul>
 *
 * <p>The top neighbour of the front block is <b>not</b> checked: a roof above is already caught by
 * {@link SolarSky#classify} ({@code openSky} returns {@code CLEAR} only with an open column), and
 * duplicating it would create two sources of truth for the same condition.
 *
 * <p>A position is considered <b>free</b> when its block state reports
 * {@link BlockState#canBeReplaced()} — air, tall grass, flowers, saplings, snow layers and other
 * "filler" the player can walk through and overwrite. Anything with real collision (stone, glass,
 * leaves, fences, slabs, the mill's own pole) blocks the blades and stalls the mill.
 */
public final class WindMillClearance {
	private WindMillClearance() {
	}

	/**
	 * True when any block the spinning blades would sweep through is solid enough to stop them. All
	 * clearance positions are measured from the <b>front neighbour</b> ({@code pos.relative(facing)}),
	 * because that is the block the rotor disc physically occupies (the renderer pushes the quad 0.58
	 * blocks forward, past the mill's own boundary).
	 *
	 * @param level  the mill's level
	 * @param pos    the mill's block position
	 * @param facing the mill's {@code FACING} (the rotor/working face)
	 * @return {@code true} if at least one clearance position is occupied by a non-replaceable block
	 */
	public static boolean hasObstruction(Level level, BlockPos pos, Direction facing) {
		Direction cw = facing.getClockWise();
		Direction ccw = facing.getCounterClockWise();
		BlockPos front = pos.relative(facing);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		// The block the rotor disc lives in: it must be air or the quad clips straight into a wall.
		if (blocks(level, cursor, front)) {
			return true;
		}
		// Horizontal blade tips reach one block left/right of the front block.
		if (blocks(level, cursor, front.relative(cw))) {
			return true;
		}
		if (blocks(level, cursor, front.relative(ccw))) {
			return true;
		}
		// Pit below the front block (centre + both sides): the lower blade arc dips into it.
		BlockPos frontBelow = front.below();
		if (blocks(level, cursor, frontBelow)) {
			return true;
		}
		if (blocks(level, cursor, frontBelow.relative(cw))) {
			return true;
		}
		return blocks(level, cursor, frontBelow.relative(ccw));
	}

	/** True when the block at {@code at} cannot be replaced (solid enough to stall the blades). */
	private static boolean blocks(Level level, BlockPos.MutableBlockPos cursor, BlockPos at) {
		cursor.set(at.getX(), at.getY(), at.getZ());
		return !level.getBlockState(cursor).canBeReplaced();
	}
}
