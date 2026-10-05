package dev.alaindustrial.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link DistanceField} — the multi-source BFS behind the energy and fluid networks' fields
 * (MOD-715, batch 7). Integer nodes on hand-drawn graphs: the tested code is the production code.
 *
 * @implements MOD-715-DF01 — the distance field: hop counts, seed priority, order of equal distances
 */
class DistanceFieldTest {

	/** A path 0 - 1 - 2 - ... - (n-1). */
	private static Function<Integer, List<Integer>> path(int n) {
		return node -> {
			List<Integer> out = new ArrayList<>();
			if (node > 0) {
				out.add(node - 1);
			}
			if (node < n - 1) {
				out.add(node + 1);
			}
			return out;
		};
	}

	private static DistanceField<Integer> field(Function<Integer, List<Integer>> neighbours) {
		return new DistanceField<>(neighbours, Comparator.naturalOrder());
	}

	@Test
	void floodCountsHopsFromTheSeed() {
		DistanceField<Integer> f = field(path(5));
		assertTrue(f.seed(0, 1));
		f.flood();
		assertEquals(Map.of(0, 1, 1, 2, 2, 3, 3, 4, 4, 5), f.distances());
		assertEquals(5, f.size());
	}

	@Test
	void nearestSeedWinsAndAZeroSeedIsAllowed() {
		DistanceField<Integer> f = field(path(7));
		f.seed(0, 0);
		f.seed(6, 0);
		f.flood();
		List<Integer> hops = new ArrayList<>();
		for (int node = 0; node < 7; node++) {
			hops.add(f.distanceOrNull(node));
		}
		assertEquals(List.of(0, 1, 2, 3, 2, 1, 0), hops);
	}

	@Test
	void aSecondSeedOfAKnownNodeIsIgnored() {
		DistanceField<Integer> f = field(path(3));
		assertTrue(f.seed(1, 1));
		assertFalse(f.seed(1, 7), "the first distance stands");
		f.flood();
		assertEquals(1, f.distanceOrNull(1));
		assertEquals(2, f.distanceOrNull(0));
		assertEquals(2, f.distanceOrNull(2));
	}

	@Test
	void unreachedNodesHaveNoDistance() {
		DistanceField<Integer> f = field(node -> node == 0 ? List.of(1) : List.of());
		f.seed(0, 1);
		f.flood();
		assertNull(f.distanceOrNull(5));
		assertFalse(f.contains(5));
		assertTrue(f.contains(1));
	}

	@Test
	void ringsCloseWithoutRevisiting() {
		// 0-1-2-3-0: the far node is two hops away by either way round, reached once.
		Function<Integer, List<Integer>> ring = node -> List.of((node + 3) % 4, (node + 1) % 4);
		DistanceField<Integer> f = field(ring);
		f.seed(0, 1);
		f.flood();
		assertEquals(3, f.distanceOrNull(2));
		assertEquals(4, f.size());
	}

	@Test
	void reachOrderFollowsSeedsThenNeighbours() {
		// A star 0 -> {3, 1, 2} in that neighbour order, seeded at 0: the flood lists 0, 3, 1, 2.
		DistanceField<Integer> f = field(node -> node == 0 ? List.of(3, 1, 2) : List.of());
		f.seed(0, 1);
		f.flood();
		assertEquals(List.of(0, 3, 1, 2), new ArrayList<>(f.distances().keySet()));
		assertEquals(List.of(0, 3, 1, 2), f.nodesByDistance(true), "equal distances keep the reach order");
		assertEquals(List.of(3, 1, 2, 0), f.nodesByDistance(false));
	}

	@Test
	void sortByDistanceBreaksTiesByTheFieldsOrder() {
		DistanceField<Integer> f = new DistanceField<>(node -> node == 0 ? List.of(3, 1, 2) : List.of(),
				Comparator.reverseOrder());
		f.seed(0, 1);
		f.flood();
		assertEquals(List.of(3, 2, 1, 0), f.sortByDistance(List.of(1, 0, 2, 3), true));
		assertEquals(List.of(0, 3, 2, 1), f.sortByDistance(List.of(1, 0, 2, 3), false));
	}

	@Test
	void clearForgetsDistancesAndPendingSeeds() {
		DistanceField<Integer> f = field(path(3));
		f.seed(0, 1);
		f.clear();
		f.flood();
		assertTrue(f.isEmpty());
		f.seed(2, 1);
		f.flood();
		assertEquals(Set.of(0, 1, 2), f.distances().keySet());
		assertEquals(3, f.distanceOrNull(0));
	}

	@Test
	void theDistanceMapIsReadOnly() {
		DistanceField<Integer> f = field(path(2));
		f.seed(0, 1);
		f.flood();
		assertThrows(UnsupportedOperationException.class, () -> f.distances().put(9, 9));
	}

	@Test
	void anOrderIsMandatory() {
		assertThrows(NullPointerException.class, () -> new DistanceField<Integer>(path(2), null));
		assertThrows(NullPointerException.class, () -> new DistanceField<Integer>(null, Comparator.naturalOrder()));
	}
}
