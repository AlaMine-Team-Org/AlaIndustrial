package dev.alaindustrial.core.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Breadth-first search for the closest fluid <em>source</em> block connected to a start position.
 * Extracted from {@code PumpBlockEntity} so the pure search stays unit-testable: the distance and
 * visited caps are passed in (wired from {@code Config} at the call site) rather than read here.
 */
public final class FluidSourceFinder {
	private FluidSourceFinder() {
	}

	/**
	 * Finds the closest source block of targetSourceFluid connected to startPos using Breadth-First Search (BFS).
	 * Restricts search to loaded chunks, Manhattan distance {@code <= maxDistance}, and visits at most
	 * {@code maxVisited} blocks.
	 *
	 * @param level              the level to search in
	 * @param pumpPos            the position of the pump block (used for distance constraint)
	 * @param startPos           the initial block position in front of the pump to start searching from
	 * @param targetSourceFluid the source representation of the fluid we are looking for
	 * @param maxDistance        max Manhattan distance from pumpPos a visited block may be
	 * @param maxVisited         max blocks the BFS may visit per scan
	 * @return the BlockPos of the closest source block, or null if none found
	 */
	public static BlockPos findClosestSource(Level level, BlockPos pumpPos, BlockPos startPos, Fluid targetSourceFluid,
			int maxDistance, int maxVisited) {
		java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();
		java.util.Set<BlockPos> visited = new java.util.HashSet<>();

		queue.add(startPos);
		visited.add(startPos);

		try {
			while (!queue.isEmpty()) {
				BlockPos current = queue.poll();
				FluidState currentState = level.getFluidState(current);
				if (currentState.isEmpty()) {
					continue;
				}

				Fluid currentFluid = currentState.getType();
				if (isSameFluid(currentFluid, targetSourceFluid)) {
					if (currentState.isSource()) {
						return current;
					}

					for (Direction dir : Direction.values()) {
						BlockPos next = current.relative(dir);
						if (!visited.contains(next)) {
							// Distance check: Manhattan distance to pumpPos must be <= maxDistance blocks
							if (next.distManhattan(pumpPos) <= maxDistance) {
								// Limit max visited blocks to avoid lag spikes
								if (visited.size() < maxVisited) {
									visited.add(next);
									queue.add(next);
								}
							}
						}
					}
				}
			}
		} catch (Exception exception) {
			// Catch any exceptions to prevent server crashes, returning null
			return null;
		}
		return null;
	}

	/**
	 * Helper to check if two fluids share the same source type.
	 *
	 * @param a first fluid to compare
	 * @param b second fluid to compare
	 * @return true if both fluids share the same source type
	 */
	private static boolean isSameFluid(Fluid a, Fluid b) {
		Fluid aSource = (a instanceof FlowingFluid flowing) ? flowing.getSource() : a;
		Fluid bSource = (b instanceof FlowingFluid flowing) ? flowing.getSource() : b;
		return aSource == bSource;
	}
}
