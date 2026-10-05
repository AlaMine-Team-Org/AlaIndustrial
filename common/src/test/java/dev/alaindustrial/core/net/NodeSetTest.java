package dev.alaindustrial.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link NodeSet} — the nodes and the dirty flag the four networks share (MOD-715, batch 11).
 *
 * @implements MOD-715-NS01 — the node set: insertion order, a flag set only by a change, merge, live view
 */
class NodeSetTest {

	/** A node set with its first refresh already done, so the flag reads clean. */
	private static NodeSet<Integer> refreshed(Integer... nodes) {
		NodeSet<Integer> set = new NodeSet<>();
		for (Integer node : nodes) {
			set.add(node);
		}
		set.clearDirty();
		return set;
	}

	@Test
	void aNewSetAsksForItsFirstRefresh() {
		NodeSet<Integer> set = new NodeSet<>();
		assertTrue(set.isDirty(), "a new network reads its endpoints on the first tick");
		assertTrue(set.isEmpty());
		set.clearDirty();
		assertFalse(set.isDirty());
	}

	@Test
	void addingANewNodeSetsTheFlagAndAKnownOneDoesNot() {
		NodeSet<Integer> set = refreshed(1);
		assertFalse(set.add(1), "already there: no change");
		assertFalse(set.isDirty(), "nothing changed, nothing to refresh");
		assertTrue(set.add(2));
		assertTrue(set.isDirty());
		assertEquals(2, set.size());
		assertTrue(set.contains(2));
	}

	@Test
	void removingAKnownNodeSetsTheFlagAndAnUnknownOneDoesNot() {
		NodeSet<Integer> set = refreshed(1, 2);
		assertFalse(set.remove(9));
		assertFalse(set.isDirty());
		assertTrue(set.remove(1));
		assertTrue(set.isDirty());
		assertFalse(set.contains(1));
	}

	@Test
	void absorbingAlwaysSetsTheFlagAndKeepsTheOrder() {
		NodeSet<Integer> keep = refreshed(3, 1);
		NodeSet<Integer> drop = refreshed(2, 1);
		keep.absorb(drop);
		assertTrue(keep.isDirty(), "a merge always asks for a refresh");
		assertEquals(List.of(3, 1, 2), new ArrayList<>(keep.nodes()), "insertion order, the merged nodes last");
		assertEquals(List.of(2, 1), new ArrayList<>(drop.nodes()), "the dropped set is left as it was");

		NodeSet<Integer> nothingNew = refreshed(1);
		nothingNew.absorb(refreshed(1));
		assertTrue(nothingNew.isDirty(), "even a merge that adds nothing");
	}

	@Test
	void markDirtyAsksForARefreshWithoutAChange() {
		NodeSet<Integer> set = refreshed(1);
		set.markDirty();
		assertTrue(set.isDirty());
		assertEquals(1, set.size());
	}

	@Test
	void theNodeViewIsLive() {
		NodeSet<Integer> set = refreshed(1);
		assertSame(set.nodes(), set.nodes(), "the frame holds the set itself, not a copy");
		set.nodes().clear(); // what GraphNetworkManager does when it re-partitions
		assertTrue(set.isEmpty());
		set.add(5);
		assertEquals(List.of(5), new ArrayList<>(set.nodes()));
	}
}
