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
 * renders a flat 2×2-block disc just in front of its {@code FACING} face: its centre sits
 * {@link WindMillRotorGeometry#DISC_PUSH} = 0.58 from the mill's block centre, 0.08 past the face.
 * The renderer and this check read those numbers from {@link WindMillRotorGeometry} (MOD-634 — before
 * that the check placed the disc half a block further out and stalled mills whose drawn blades never
 * met). Two mills placed close enough that their discs intersect look broken in-world: the blades
 * overlap and, where the quads are coplanar (same push depth), z-fight and flicker.
 * {@link WindMillClearance} cannot catch this — the neighbour's rotor is a client-side render, not a
 * block.
 *
 * <p>The check models each disc as an axis-aligned box (see {@link WindMillRotorGeometry}). Two mills
 * interfere when their disc boxes overlap with positive volume — touching edge-to-edge (e.g. mills two
 * blocks apart, discs meeting exactly at the shared boundary) is <b>not</b> interference. One box rule
 * covers every layout: side-by-side coplanar discs, perpendicular discs slicing through each other, and
 * the parallel discs of mills facing each other diagonally, one block ahead and one block up or down.
 * Interference is symmetric by construction, so <b>both</b> mills stall — there is no tie-break.
 *
 * <p>A neighbour counts only when it has a rotor installed (slot 0 of its block entity): a bare
 * mill renders no disc, so there is nothing to clash with. Even a stalled neighbour's rotor counts —
 * static blades overlap visually just the same. A neighbour whose block entity is not available
 * (e.g. its chunk is not loaded at sample time) is treated as rotor-less; the next sample after the
 * chunk loads corrects the state.
 *
 * <p>Mills facing each other in <b>directly adjacent</b> blocks do have overlapping discs, but this
 * check never reaches them: each disc sits inside the other mill's solid block, which
 * {@link WindMillClearance} reports as an obstruction first (higher priority). Across one air block the
 * two discs are 0.84 apart and both mills run.
 */
public final class WindMillInterference {
	/**
	 * Chebyshev scan radius around the mill's own position. A disc box stays within
	 * {@link WindMillRotorGeometry#DISC_HALF_SIZE} = 1.0 of its mill's centre on every axis (along
	 * {@code FACING} it reaches only {@code DISC_PUSH + DISC_HALF_DEPTH} = 0.68), so two mills can overlap
	 * only when their centres are under 2 blocks apart on every axis; radius 3 covers every such candidate.
	 */
	private static final int SCAN_RADIUS = 3;

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
					Direction other = state.getValue(HorizontalMachineBlock.FACING);
					if (WindMillRotorGeometry.discsOverlap(
							pos.getX(), pos.getY(), pos.getZ(), facing.getStepX(), facing.getStepY(), facing.getStepZ(),
							cursor.getX(), cursor.getY(), cursor.getZ(),
							other.getStepX(), other.getStepY(), other.getStepZ())) {
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
}
