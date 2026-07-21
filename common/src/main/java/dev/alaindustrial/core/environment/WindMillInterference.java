package dev.alaindustrial.core.environment;

import dev.alaindustrial.block.HighAltitudeWindMillBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.StormWindMillBlock;
import dev.alaindustrial.block.WindMillBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rotor-interference check for the wind mill family (MOD-051). Each mill with an installed rotor
 * renders a flat 2×2-block disc in front of its {@code FACING} face (centre ~1.08 blocks from the
 * mill's own centre along {@code FACING} — the 0.5 half-block plus the renderer's 0.58 push, see
 * {@code WindMillRotorBlockEntityRenderer}). Two mills placed close enough that their discs
 * intersect look broken in-world: the blades overlap and, where the quads are coplanar (same push
 * depth), z-fight and flicker. {@link WindMillClearance} cannot catch this — the neighbour's rotor
 * is a client-side render, not a block.
 *
 * <p>The check models each disc as an axis-aligned box: ±{@link #DISC_HALF_SIZE} along the two axes
 * of the rotation plane, ±{@link #DISC_HALF_DEPTH} along {@code FACING}. Two mills interfere when
 * their disc boxes overlap with positive volume — touching edge-to-edge (e.g. mills two blocks
 * apart, discs meeting exactly at the shared boundary) is <b>not</b> interference. One box rule
 * covers every layout: side-by-side coplanar discs, face-to-face mills across a one-block gap, and
 * perpendicular discs slicing through each other. Interference is symmetric by construction, so
 * <b>both</b> mills stall — there is no tie-break.
 *
 * <p>A neighbour counts only when it has a rotor installed (slot 0 of its block entity): a bare
 * mill renders no disc, so there is nothing to clash with. Even a stalled neighbour's rotor counts —
 * static blades overlap visually just the same. A neighbour whose block entity is not available
 * (e.g. its chunk is not loaded at sample time) is treated as rotor-less; the next sample after the
 * chunk loads corrects the state.
 *
 * <p>Mills facing each other in <b>directly adjacent</b> blocks are not this check's problem: each
 * disc then sits inside the other mill's solid block, which {@link WindMillClearance} already
 * reports as an obstruction (higher priority).
 */
public final class WindMillInterference {
	/** Half-extent of the rotor disc in its rotation plane ({@code HALF_SIZE} in the renderer). */
	private static final double DISC_HALF_SIZE = 1.0;
	/** Half-thickness of the disc box along {@code FACING} — the quad is flat, this is tolerance. */
	private static final double DISC_HALF_DEPTH = 0.1;
	/** Distance from the mill's block centre to the disc centre along {@code FACING} (0.5 + 0.58). */
	private static final double DISC_PUSH = 1.08;
	/**
	 * Chebyshev scan radius around the mill's own position. Each disc box reaches at most
	 * {@code DISC_PUSH + DISC_HALF_DEPTH} ≈ 1.18 from its mill's centre along one axis, so two mills
	 * whose centres are further than ~3.4 blocks apart on every axis cannot overlap; radius 3 covers
	 * every reachable candidate.
	 */
	private static final int SCAN_RADIUS = 3;
	/** Interval-overlap slack so discs meeting exactly edge-to-edge do not count as overlapping. */
	private static final double EPSILON = 1.0E-4;

	private WindMillInterference() {
	}

	/**
	 * True when another wind-mill-family block with an installed rotor sits close enough that the two
	 * rotor discs intersect. Called on the sampling cadence (not every tick): the scan visits a
	 * {@code 7×7×7} box and only touches block entities of actual wind mills inside it.
	 *
	 * @param level  the mill's level
	 * @param pos    the mill's block position
	 * @param facing the mill's {@code FACING} (the rotor face)
	 * @return {@code true} if at least one neighbouring rotor disc overlaps this mill's disc
	 */
	public static boolean hasInterference(Level level, BlockPos pos, Direction facing) {
		double[] own = discBox(pos, facing);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
			for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
				for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (!isWindMill(state.getBlock()) || !state.hasProperty(HorizontalMachineBlock.FACING)) {
						continue;
					}
					if (!hasRotorInstalled(level, cursor)) {
						continue;
					}
					double[] other = discBox(cursor, state.getValue(HorizontalMachineBlock.FACING));
					if (boxesOverlap(own, other)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The wind mill family: T1 plus both T2 evolutions. All render the same 2×2 rotor disc. */
	private static boolean isWindMill(Block block) {
		return block instanceof WindMillBlock
				|| block instanceof StormWindMillBlock
				|| block instanceof HighAltitudeWindMillBlock;
	}

	/**
	 * True when the mill at {@code at} has a rotor in slot 0 — the shared {@code ROTOR_SLOT} of all
	 * three wind mill block entities. No block entity (unloaded chunk, race during placement) reads
	 * as "no rotor": no disc is rendered from an unloaded chunk anyway.
	 */
	private static boolean hasRotorInstalled(Level level, BlockPos at) {
		return level.getBlockEntity(at) instanceof Container container && !container.getItem(0).isEmpty();
	}

	/**
	 * Axis-aligned box of the rotor disc for a mill at {@code pos} facing {@code facing}, as
	 * {@code {minX, minY, minZ, maxX, maxY, maxZ}}: disc centre pushed {@link #DISC_PUSH} from the
	 * block centre along {@code facing}, ±{@link #DISC_HALF_DEPTH} along the facing axis and
	 * ±{@link #DISC_HALF_SIZE} along the two plane axes.
	 */
	private static double[] discBox(BlockPos pos, Direction facing) {
		double cx = pos.getX() + 0.5 + facing.getStepX() * DISC_PUSH;
		double cy = pos.getY() + 0.5 + facing.getStepY() * DISC_PUSH;
		double cz = pos.getZ() + 0.5 + facing.getStepZ() * DISC_PUSH;
		double hx = facing.getAxis() == Direction.Axis.X ? DISC_HALF_DEPTH : DISC_HALF_SIZE;
		double hy = facing.getAxis() == Direction.Axis.Y ? DISC_HALF_DEPTH : DISC_HALF_SIZE;
		double hz = facing.getAxis() == Direction.Axis.Z ? DISC_HALF_DEPTH : DISC_HALF_SIZE;
		return new double[] {cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz};
	}

	/** Positive-volume overlap on all three axes; edge contact (within {@link #EPSILON}) is not overlap. */
	private static boolean boxesOverlap(double[] a, double[] b) {
		return a[0] < b[3] - EPSILON && b[0] < a[3] - EPSILON
				&& a[1] < b[4] - EPSILON && b[1] < a[4] - EPSILON
				&& a[2] < b[5] - EPSILON && b[2] < a[5] - EPSILON;
	}
}
