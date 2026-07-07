package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link GraphComponents} — the BFS connected-components used by
 * {@link NetworkManager} to split a cable network when a cable is removed. Tested here on plain
 * {@code Integer} line-graphs (MC-free), so cable split/merge correctness no longer depends only on
 * the slow L2 gametests.
 *
 * @implements energy-network cable split (connected components / BFS)
 */
class GraphComponentsTest {

	/** Line-graph adjacency: each node is connected to its ±1 neighbours (filtered by the node set). */
	private static List<Integer> lineNeighbours(int n) {
		return List.of(n - 1, n + 1);
	}

	@Test
	void connectedLine_isOneComponent() {
		Set<Integer> nodes = setOf(1, 2, 3, 4);
		List<Set<Integer>> comps = GraphComponents.components(nodes, GraphComponentsTest::lineNeighbours);
		assertEquals(1, comps.size());
		assertEquals(nodes, comps.get(0));
	}

	@Test
	void gapSplitsIntoTwoComponents() {
		// 1-2 . . 5-6 : removing the cable at 3/4 leaves two disconnected runs.
		Set<Integer> nodes = setOf(1, 2, 5, 6);
		List<Set<Integer>> comps = GraphComponents.components(nodes, GraphComponentsTest::lineNeighbours);
		assertEquals(2, comps.size());
		assertTrue(comps.contains(setOf(1, 2)));
		assertTrue(comps.contains(setOf(5, 6)));
	}

	@Test
	void allIsolated_isThreeComponents() {
		Set<Integer> nodes = setOf(1, 3, 5); // no two are adjacent
		List<Set<Integer>> comps = GraphComponents.components(nodes, GraphComponentsTest::lineNeighbours);
		assertEquals(3, comps.size());
	}

	@Test
	void singleNode_isOneComponent() {
		List<Set<Integer>> comps = GraphComponents.components(setOf(7), GraphComponentsTest::lineNeighbours);
		assertEquals(1, comps.size());
		assertEquals(setOf(7), comps.get(0));
	}

	@Test
	void emptyGraph_hasNoComponents() {
		assertEquals(0, GraphComponents.components(new HashSet<Integer>(), GraphComponentsTest::lineNeighbours).size());
	}

	@Test
	void componentsArePartition_disjointAndCoverAllNodes() {
		Set<Integer> nodes = setOf(1, 2, 3, 10, 11, 20);
		List<Set<Integer>> comps = GraphComponents.components(nodes, GraphComponentsTest::lineNeighbours);
		Set<Integer> union = new HashSet<>();
		int total = 0;
		for (Set<Integer> c : comps) {
			union.addAll(c);
			total += c.size();
		}
		assertEquals(nodes, union, "union of components = all nodes");
		assertEquals(nodes.size(), total, "components are disjoint (no node counted twice)");
	}

	private static Set<Integer> setOf(Integer... values) {
		return new HashSet<>(List.of(values));
	}
}
