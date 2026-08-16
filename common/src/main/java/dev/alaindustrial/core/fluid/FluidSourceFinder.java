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
	 * Bounded by Manhattan distance {@code <= maxDistance} from the pump and by at most
	 * {@code maxVisited} visited blocks.
	 *
	 * <p><b>The search is NOT restricted to loaded chunks.</b> This javadoc used to claim it was, and
	 * the claim was never true: there is no {@code isLoaded} / {@code hasChunk} / {@code isAreaLoaded}
	 * call anywhere in this class, unlike {@code FluidNetwork}, which guards every world access that
	 * way. The distance cap makes reaching an unloaded chunk unlikely rather than impossible. Whether
	 * to add the real guard and drop the broad catch below is deliberately left to a follow-up — it is
	 * a behaviour change that needs to be reproduced in a dev client first, not a doc fix.
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
		java.util.Set<BlockPos> visited = new java.util.LinkedHashSet<>();

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
			// Still swallowed, so a scan gone wrong cannot crash the server tick -- but no longer in
			// silence. null is ALSO the normal "no source found" answer (see the return below), so
			// before this the pump could not tell "nothing to pump" from "the search blew up", and
			// neither could anyone reading the log: there was nothing in it. The player just saw a pump
			// that does not work, with no way to diagnose it.
			reportScanFailure(exception, pumpPos);
			return null;
		}
		return null;
	}

	/** Distinct failure signatures already logged, so a pump that throws every scan is logged once. */
	private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** Hard cap so a pathological stream of unique signatures cannot grow the set without bound. */
	private static final int WARNED_CAP = 64;

	/**
	 * Log a failed scan once per distinct failure site. A pump scans on a tick loop, so an unconditional
	 * log would flood the file and bury the very line worth reading.
	 *
	 * <p>Keying and capping follow {@code NetworkTickGuard#report}, deliberately including the reason:
	 * the key is the throwable class plus its top frame, NOT {@code getMessage()}, because a message
	 * here tends to embed block coordinates — message-keyed dedup would call every tick a fresh
	 * signature and defeat itself. The pattern is repeated rather than shared because generalizing that
	 * class is a refactor of the network core, which this change is not.
	 */
	private static void reportScanFailure(Exception exception, BlockPos pumpPos) {
		StackTraceElement[] frames = exception.getStackTrace();
		String top = (frames == null || frames.length == 0)
				? "?"
				: frames[0].getClassName() + '#' + frames[0].getMethodName();
		String signature = exception.getClass().getName() + '|' + top;
		if (WARNED.size() < WARNED_CAP && WARNED.add(signature)) {
			dev.alaindustrial.Industrialization.LOGGER.warn(
					"[alaindustrial] The fluid-source scan for the pump at {} threw and was treated as "
							+ "'no source found', so the pump will look idle rather than broken. Further "
							+ "identical failures are not logged.",
					pumpPos, exception);
		}
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
