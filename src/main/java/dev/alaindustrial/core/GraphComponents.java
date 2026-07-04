package dev.alaindustrial.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Generic connected-components (BFS) over an undirected graph — extracted from
 * {@link NetworkManager} so the cable split/merge topology logic can be unit-tested without a
 * Minecraft runtime (L1). Production passes {@code BlockPos} nodes plus a 6-neighbour function;
 * tests pass plain {@code Integer}s, so the tested code is the production code.
 *
 * <p>No Minecraft imports: a pure function of {@code (nodes, neighbours)}.
 */
public final class GraphComponents {
	private GraphComponents() {
	}

	/**
	 * Partition {@code nodes} into connected components. The {@code neighbours} function returns the
	 * adjacent nodes of a given node; any neighbour not present in {@code nodes} is ignored, so the
	 * caller can return all candidate neighbours without pre-filtering.
	 *
	 * @return one {@link Set} per component; their union equals {@code nodes}, and they are disjoint
	 */
	public static <N> List<Set<N>> components(Set<N> nodes, Function<N, ? extends Iterable<N>> neighbours) {
		List<Set<N>> result = new ArrayList<>();
		Set<N> visited = new HashSet<>();
		for (N start : nodes) {
			if (!visited.add(start)) {
				continue;
			}
			Set<N> component = new HashSet<>();
			Deque<N> queue = new ArrayDeque<>();
			queue.add(start);
			while (!queue.isEmpty()) {
				N cur = queue.poll();
				component.add(cur);
				for (N next : neighbours.apply(cur)) {
					if (nodes.contains(next) && visited.add(next)) {
						queue.add(next);
					}
				}
			}
			result.add(component);
		}
		return result;
	}
}
