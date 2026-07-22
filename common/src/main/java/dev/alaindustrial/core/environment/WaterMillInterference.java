package dev.alaindustrial.core.environment;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.WaterMillBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wheel-interference check for the water mill (MOD-175), the water-side analogue of
 * {@link WindMillInterference}. A water mill with an installed wheel renders a large open 3D wheel in
 * front of its {@code FACING} face (centre ~1.02 blocks from the mill's own centre along
 * {@code FACING} — the renderer's {@code translate(0, 0, -1.02)}, see
 * {@code WaterMillWheelBlockEntityRenderer}). The wheel is markedly bigger than the wind-mill rotor
 * (rim radius {@value #DISC_HALF_SIZE} vs 1.0), so two mills placed close together look broken: the
 * wheels intersect and z-fight (the caged screenshot). {@code WaterMillBlockEntity} has no {@code lit}
 * or clearance concept for this — the neighbour's wheel is a client-side render, not a block.
 *
 * <p>The check models each wheel as an axis-aligned box: ±{@link #DISC_HALF_SIZE} along the two axes
 * of the rotation plane, ±{@link #DISC_HALF_DEPTH} along {@code FACING}. Two mills interfere when
 * their boxes overlap with positive volume — touching edge-to-edge is <b>not</b> interference.
 * Interference is symmetric, so <b>both</b> mills stall (no tie-break). Because the wheel is larger,
 * mills need a wider gap than wind mills: two empty blocks side-by-side or face-to-face.
 *
 * <p>A neighbour counts only when it has a wheel installed (slot {@code WHEEL_SLOT} = 0 of its block
 * entity): a bare mill renders no wheel. Even a stalled neighbour's wheel counts — a static wheel
 * overlaps visually just the same. A neighbour whose block entity is not available (unloaded chunk)
 * reads as wheel-less; the next scan after the chunk loads corrects the state.
 */
public final class WaterMillInterference {
	/** Half-extent of the wheel in its rotation plane — the rim outer radius {@code RIM_OUTER}. */
	private static final double DISC_HALF_SIZE = 1.32;
	/** Half-thickness of the wheel box along {@code FACING} — the rim depth {@code RIM_BACK}. */
	private static final double DISC_HALF_DEPTH = 0.4375;
	/** Distance from the mill's block centre to the wheel centre along {@code FACING} (renderer push). */
	private static final double DISC_PUSH = 1.02;
	/**
	 * Chebyshev scan radius around the mill's own position. Each wheel box reaches at most
	 * {@code DISC_HALF_SIZE} ≈ 1.32 in its plane and {@code DISC_PUSH + DISC_HALF_DEPTH} ≈ 1.46 along
	 * {@code FACING}, so two mills whose centres are more than ~3 blocks apart on every axis cannot
	 * overlap; radius 3 covers every reachable candidate.
	 */
	private static final int SCAN_RADIUS = 3;
	/** Interval-overlap slack so wheels meeting exactly edge-to-edge do not count as overlapping. */
	private static final double EPSILON = 1.0E-4;

	private WaterMillInterference() {
	}

	/**
	 * True when another water mill with an installed wheel sits close enough that the two wheels
	 * intersect. Scans a {@code 7×7×7} box and only touches block entities of actual water mills.
	 *
	 * @param level  the mill's level
	 * @param pos    the mill's block position
	 * @param facing the mill's {@code FACING} (the wheel face)
	 * @return {@code true} if at least one neighbouring wheel overlaps this mill's wheel
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
					if (!(state.getBlock() instanceof WaterMillBlock)
							|| !state.hasProperty(HorizontalMachineBlock.FACING)) {
						continue;
					}
					if (!hasWheelInstalled(level, cursor)) {
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

	/**
	 * True when the mill at {@code at} has a wheel in slot 0 ({@code WHEEL_SLOT}). No block entity
	 * (unloaded chunk, race during placement) reads as "no wheel": no wheel is rendered from an
	 * unloaded chunk anyway.
	 */
	private static boolean hasWheelInstalled(Level level, BlockPos at) {
		return level.getBlockEntity(at) instanceof Container container && !container.getItem(0).isEmpty();
	}

	/**
	 * Axis-aligned box of the wheel for a mill at {@code pos} facing {@code facing}, as
	 * {@code {minX, minY, minZ, maxX, maxY, maxZ}}: wheel centre pushed {@link #DISC_PUSH} from the
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
