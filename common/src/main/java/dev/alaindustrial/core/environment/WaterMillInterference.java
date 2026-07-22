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
 * (rim radius {@value WaterMillWheelGeometry#DISC_HALF_SIZE} vs 1.0), so two mills placed close
 * together look broken: the wheels intersect and z-fight (the caged screenshot).
 * {@code WaterMillBlockEntity} handles the solid-block case separately via {@code WaterMillClearance}
 * (MOD-179) — the neighbour's wheel is a client-side render, not a block.
 *
 * <p>The AABB model and the overlap predicate live in {@link WaterMillWheelGeometry} (Minecraft-free,
 * unit-tested at L1). Two mills interfere when their wheel boxes overlap with positive volume —
 * touching edge-to-edge is <b>not</b> interference. Interference is symmetric, so <b>both</b> mills
 * stall (no tie-break). Because the wheel is larger, mills need a wider gap than wind mills: two
 * empty blocks side-by-side or face-to-face. Face-to-face with NO gap is geometrically invisible to
 * this test (each wheel box sits inside the other mill's solid casing) and is caught by
 * {@code WaterMillClearance} instead.
 *
 * <p>A neighbour counts only when it has a wheel installed (slot {@code WHEEL_SLOT} = 0 of its block
 * entity): a bare mill renders no wheel. Even a stalled neighbour's wheel counts — a static wheel
 * overlaps visually just the same. A neighbour whose block entity is not available (unloaded chunk)
 * reads as wheel-less; the next scan after the chunk loads corrects the state.
 */
public final class WaterMillInterference {
	/**
	 * Chebyshev scan radius around the mill's own position. Each wheel box reaches at most
	 * {@code DISC_HALF_SIZE} ≈ 1.32 in its plane and {@code DISC_PUSH + DISC_HALF_DEPTH} ≈ 1.46 along
	 * {@code FACING}, so two mills whose centres are more than ~3 blocks apart on every axis cannot
	 * overlap; radius 3 covers every reachable candidate.
	 */
	private static final int SCAN_RADIUS = 3;

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
					if (wheelsOverlap(pos, facing, cursor, state.getValue(HorizontalMachineBlock.FACING))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Wheel-vs-wheel geometry: whether the wheel of a mill at {@code a} facing {@code aFacing}
	 * overlaps the wheel of a mill at {@code b} facing {@code bFacing}. Thin adapter over the
	 * L1-tested {@link WaterMillWheelGeometry#wheelsOverlap} — the scan above evaluates exactly this
	 * predicate per candidate neighbour.
	 */
	public static boolean wheelsOverlap(BlockPos a, Direction aFacing, BlockPos b, Direction bFacing) {
		return WaterMillWheelGeometry.wheelsOverlap(
				a.getX(), a.getY(), a.getZ(), aFacing.getStepX(), aFacing.getStepY(), aFacing.getStepZ(),
				b.getX(), b.getY(), b.getZ(), bFacing.getStepX(), bFacing.getStepY(), bFacing.getStepZ());
	}

	/**
	 * True when the mill at {@code at} has a wheel in slot 0 ({@code WHEEL_SLOT}). No block entity
	 * (unloaded chunk, race during placement) reads as "no wheel": no wheel is rendered from an
	 * unloaded chunk anyway.
	 */
	private static boolean hasWheelInstalled(Level level, BlockPos at) {
		return level.getBlockEntity(at) instanceof Container container && !container.getItem(0).isEmpty();
	}
}
