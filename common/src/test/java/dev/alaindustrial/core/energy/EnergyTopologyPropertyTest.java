package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based coverage of the two Minecraft-free layers under the energy network: the arithmetic
 * chained along a line, and the graph partition that decides what counts as one network.
 *
 * <p><b>What this class does NOT do, stated plainly.</b> The live composition lives in
 * {@code EnergyLineDistributor}, which is Minecraft-coupled and cannot run at L1 — so {@code runLine}
 * below is a re-implementation of that chain written here, in the test. It therefore catches a broken
 * {@code split}/{@code cableLoss}, but it cannot catch {@code EnergyLineDistributor} applying them in
 * the wrong order or to the wrong operand. That gap is covered by the world gametests, not here, and
 * pretending otherwise would be worse than the gap itself.
 *
 * <p>The line properties overlap with {@link EnergySharePropertyTest} on purpose — they exercise the
 * same functions in composition rather than in isolation — so the value they add over it is modest.
 * The graph properties are where this class carries its weight: {@code GraphComponentsTest} draws a
 * handful of shapes by hand, and randomised graphs plus an explicit two-clique case cover both
 * directions of the partition, including the one that a "return everything as one component"
 * implementation would otherwise satisfy.
 */
class EnergyTopologyPropertyTest {

	// ── generators ───────────────────────────────────────────────────────────────────────────────

	@Provide
	Arbitrary<long[]> consumerRooms() {
		return Arbitraries.longs().between(0L, 1L << 20).list().ofMinSize(1).ofMaxSize(8)
				.map(list -> list.stream().mapToLong(Long::longValue).toArray());
	}

	@Provide
	Arbitrary<Long> supply() {
		return Arbitraries.longs().between(0L, 1L << 24);
	}

	@Provide
	Arbitrary<Integer> hops() {
		return Arbitraries.integers().between(1, 24);
	}

	@Provide
	Arbitrary<Double> lossRate() {
		return Arbitraries.doubles().between(0.0, 0.2).ofScale(4);
	}

	@Provide
	Arbitrary<Long> packetCap() {
		return Arbitraries.longs().between(1L, 1L << 16);
	}

	// ── a whole line: split, then per-consumer transit loss ──────────────────────────────────────

	/**
	 * Delivers {@code supply} over a line of {@code hops} cable blocks to {@code room.length}
	 * consumers, exactly as the network does: cap the movable total at the demand, split it
	 * proportionally to free room, then destroy the distance loss per consumer.
	 *
	 * @return {@code [totalDelivered, totalLost, totalMoved]}
	 */
	private static long[] runLine(long supply, long[] room, int hops, double lossPerBlock,
			long cap) {
		long demand = 0;
		for (long r : room) {
			demand += r;
		}
		long moveTotal = EnergyShare.deliverable(supply, demand);
		long[] shares = EnergyShare.split(moveTotal, room, demand, cap, 0);
		long delivered = 0;
		long lost = 0;
		long moved = 0;
		for (long gross : shares) {
			long loss = EnergyShare.cableLoss(gross, lossPerBlock, hops);
			delivered += gross - loss;
			lost += loss;
			moved += gross;
		}
		return new long[] {delivered, lost, moved};
	}

	/**
	 * Nothing is created and nothing evaporates unaccounted: what the producers gave up equals what
	 * arrived plus what the cable destroyed, and neither exceeds the supply.
	 *
	 * <p>This is the invariant the "energy washed through the wires" class of bug violates. At the
	 * level of a single function it is trivially true; along a chain it is where an off-by-one clamp
	 * or a loss applied to the wrong operand shows up.
	 */
	@Property
	void line_neverMovesMoreThanSupplyOrDemand(@ForAll("supply") long supply,
			@ForAll("consumerRooms") long[] room, @ForAll("hops") int hops,
			@ForAll("lossRate") double lossPerBlock, @ForAll("packetCap") long cap) {
		long demand = 0;
		for (long r : room) {
			demand += r;
		}
		long[] result = runLine(supply, room, hops, lossPerBlock, cap);
		long delivered = result[0];
		long lost = result[1];
		long moved = result[2];

		// NB deliberately NOT asserting `moved == delivered + lost`. That identity is computed from the
		// same three accumulators a few lines above and holds for ANY value cableLoss returns —
		// negative, larger than gross, constant. An assertion that cannot fail is worse than no
		// assertion, because it reads like conservation is checked when nothing is.
		assertTrue(moved <= supply, "the line moved more EU than the producers had: " + moved
				+ " > " + supply);
		assertTrue(moved <= demand, "the line moved more EU than the consumers asked for: " + moved
				+ " > " + demand);
		assertTrue(lost <= moved, "more EU was destroyed in transit than ever entered the line");
		assertTrue(delivered >= 0 && lost >= 0, "negative delivery or loss is never valid");
		if (moved > 0) {
			assertTrue(delivered > 0,
					"a line that moved " + moved + " EU delivered nothing: transit ate all of it, "
							+ "but cableLoss is clamped to leave at least 1 EU per consumer");
		}
	}

	/**
	 * No consumer ever receives more than its free room: overfilling is the buffer-corruption failure
	 * mode, and it must hold for every consumer on the line, not just the first.
	 */
	@Property
	void line_neverOverfillsAnyConsumer(@ForAll("supply") long supply,
			@ForAll("consumerRooms") long[] room, @ForAll("packetCap") long cap) {
		long demand = 0;
		for (long r : room) {
			demand += r;
		}
		long[] shares = EnergyShare.split(EnergyShare.deliverable(supply, demand), room, demand, cap, 0);
		for (int i = 0; i < room.length; i++) {
			assertTrue(shares[i] <= room[i],
					"consumer " + i + " was handed " + shares[i] + " EU into " + room[i] + " of room");
		}
	}

	/**
	 * Stretching the line never helps. A longer cable run delivers no more than a shorter one with
	 * everything else held equal — the monotonicity that makes distance a cost rather than a lottery,
	 * and the property a sign flip inside the attenuation chain breaks immediately.
	 */
	@Property
	void line_longerNeverDeliversMore(@ForAll("supply") long supply,
			@ForAll("consumerRooms") long[] room, @ForAll("hops") int hops,
			@ForAll("lossRate") double lossPerBlock, @ForAll("packetCap") long cap) {
		long shortLine = runLine(supply, room, hops, lossPerBlock, cap)[0];
		long longLine = runLine(supply, room, hops + 1, lossPerBlock, cap)[0];
		assertTrue(longLine <= shortLine, "a " + (hops + 1) + "-block line delivered " + longLine
				+ " EU, more than the " + hops + "-block line's " + shortLine);
	}

	// ── the cable graph partition ────────────────────────────────────────────────────────────────

	/**
	 * A random undirected graph: {@code n} nodes plus a random subset of the possible edges.
	 *
	 * <p><b>Edge probability is deliberately low.</b> The obvious way to symmetrise — OR the two
	 * halves of the matrix — turns a fair coin into P(edge) = 0.75, and at that density essentially
	 * every generated graph is connected: a measurement over 200 000 draws found more than one
	 * component in 4 % of cases, and almost never above n = 4. A partition test that never sees a
	 * partition is decoration. Only the lower triangle is drawn here, then mirrored, so the coin is
	 * fair, and it is biased further toward "no edge" so that disconnected shapes are common.
	 */
	@Provide
	Arbitrary<int[][]> randomGraph() {
		return Arbitraries.integers().between(1, 10).flatMap(n ->
				// 0..3 with only 0 meaning "edge" → P(edge) = 1/4 per unordered pair.
				Arbitraries.integers().between(0, 3).list()
						.ofSize(n * n)
						.map(bits -> {
							int[][] adjacency = new int[n][n];
							for (int i = 0; i < n; i++) {
								for (int j = 0; j < i; j++) {
									// Lower triangle only, then mirrored: the cable graph is
									// undirected, and OR-ing both halves would double the density.
									int bit = bits.get(i * n + j) == 0 ? 1 : 0;
									adjacency[i][j] = bit;
									adjacency[j][i] = bit;
								}
							}
							return adjacency;
						}));
	}

	private static List<Set<Integer>> partition(int[][] adjacency) {
		Set<Integer> nodes = new LinkedHashSet<>();
		for (int i = 0; i < adjacency.length; i++) {
			nodes.add(i);
		}
		return GraphComponents.components(nodes, node -> {
			List<Integer> out = new ArrayList<>();
			for (int j = 0; j < adjacency.length; j++) {
				if (adjacency[node][j] == 1) {
					out.add(j);
				}
			}
			return out;
		});
	}

	/**
	 * The result is a partition: every node appears in exactly one component, and the union is the
	 * whole node set. A node that lands in two components would be a cable served by two networks —
	 * the double-delivery bug; a node in none would be a cable that silently stops conducting.
	 */
	@Property
	void components_arePartition(@ForAll("randomGraph") int[][] adjacency) {
		List<Set<Integer>> components = partition(adjacency);
		Set<Integer> union = new LinkedHashSet<>();
		int total = 0;
		for (Set<Integer> component : components) {
			total += component.size();
			union.addAll(component);
		}
		assertEquals(adjacency.length, union.size(), "the components do not cover every node");
		assertEquals(adjacency.length, total, "some node appears in more than one component");
	}

	/**
	 * <b>The direction the other two properties do not test.</b> Nodes that are NOT connected must end
	 * up in different components.
	 *
	 * <p>Without this, the whole graph half of this class passes on an implementation that simply
	 * returns every node in one component — both {@code components_arePartition} (union is complete,
	 * nothing duplicated) and {@code components_keepNeighboursTogether} (everything is together) are
	 * satisfied by that. And "one component" is exactly the failure that matters here: it is a network
	 * that refuses to split when a cable is broken.
	 *
	 * <p>Construction avoids relying on the random generator producing a disconnected shape: two
	 * cliques are built explicitly with no edge between them, so the assertion always has something to
	 * assert.
	 */
	@Property
	void components_separateDisconnectedHalves(
			@ForAll("cliqueSize") int leftSize, @ForAll("cliqueSize") int rightSize) {
		int n = leftSize + rightSize;
		int[][] adjacency = new int[n][n];
		for (int i = 0; i < leftSize; i++) {
			for (int j = 0; j < leftSize; j++) {
				adjacency[i][j] = i == j ? 0 : 1;
			}
		}
		for (int i = leftSize; i < n; i++) {
			for (int j = leftSize; j < n; j++) {
				adjacency[i][j] = i == j ? 0 : 1;
			}
		}
		List<Set<Integer>> components = partition(adjacency);
		assertEquals(2, components.size(),
				"two cliques with no edge between them must be two networks, got "
						+ components.size());
		for (Set<Integer> component : components) {
			boolean allLeft = component.stream().allMatch(node -> node < leftSize);
			boolean allRight = component.stream().allMatch(node -> node >= leftSize);
			assertTrue(allLeft || allRight, "a component straddles the gap between the two cliques");
		}
	}

	/**
	 * Neighbours reported outside the node set must be ignored.
	 *
	 * <p>In production the neighbour function returns all six adjacent positions, most of which are not
	 * cables at all — the {@code nodes.contains(next)} filter inside {@link GraphComponents} is what
	 * keeps them out. Every other property here feeds a neighbour function that only ever returns
	 * members, so that filter is never executed: removing it would not fail a single one of them.
	 */
	@Property
	void components_ignoreNeighboursOutsideNodeSet(@ForAll("cliqueSize") int size) {
		Set<Integer> nodes = new LinkedHashSet<>();
		for (int i = 0; i < size; i++) {
			nodes.add(i);
		}
		// Every node claims a neighbour far outside the set, plus its in-set successor.
		List<Set<Integer>> components = GraphComponents.components(nodes, node -> {
			List<Integer> out = new ArrayList<>();
			out.add(1_000_000 + node);
			if (node + 1 < size) {
				out.add(node + 1);
			}
			return out;
		});
		int total = components.stream().mapToInt(Set::size).sum();
		assertEquals(size, total,
				"phantom neighbours outside the node set leaked into the components");
		assertEquals(1, components.size(), "the in-set chain must still form one component");
	}

	@Provide
	Arbitrary<Integer> cliqueSize() {
		return Arbitraries.integers().between(1, 6);
	}

	/**
	 * Adjacency implies co-membership: two directly connected cables are always in the same network.
	 * This is the ring-merge invariant (MOD-025) stated as a property rather than as one drawn shape.
	 */
	@Property
	void components_keepNeighboursTogether(@ForAll("randomGraph") int[][] adjacency) {
		List<Set<Integer>> components = partition(adjacency);
		for (int i = 0; i < adjacency.length; i++) {
			for (int j = 0; j < adjacency.length; j++) {
				if (adjacency[i][j] != 1) {
					continue;
				}
				final int left = i;
				final int right = j;
				boolean together = components.stream()
						.anyMatch(component -> component.contains(left) && component.contains(right));
				assertTrue(together, "connected nodes " + left + " and " + right
						+ " ended up in different networks");
			}
		}
	}
}
