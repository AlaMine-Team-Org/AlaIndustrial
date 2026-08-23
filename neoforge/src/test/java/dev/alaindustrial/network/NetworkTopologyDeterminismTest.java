package dev.alaindustrial.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.network.NetworkTopology.NetworkEdge;
import dev.alaindustrial.network.NetworkTopology.TubeRun;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import net.minecraft.core.BlockPos;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * L1.5 regression coverage for the Network Analyzer overlay's geometry being a function of the network's
 * SHAPE and of nothing else (MOD-313).
 *
 * <p>The defect these pin is not "the numbers are wrong" — the overlay was always topologically correct.
 * It is that the same untouched base could be drawn with different tube runs from one scan to the next,
 * because the algorithms walked a {@code HashSet} of ABSOLUTE {@link BlockPos} and let bucket order pick
 * where each tube started. Two properties catch that, and neither is satisfied by "call it twice in one
 * JVM and compare" — a hash set is perfectly repeatable within a run:
 *
 * <ul>
 *   <li><b>Translation invariance.</b> The same shape built somewhere else must give the same relative
 *       answer. This is what actually varies in game, and it is also what {@code BlockPos.asLong()} gets
 *       wrong (see {@code PosOrder}), so the fixture deliberately moves the build ACROSS y=0 and z=0.</li>
 *   <li><b>Independence from the caller's iteration order.</b> {@code tubeRuns} is handed a joint set by
 *       the renderer, which builds it as a plain {@code HashSet}; feeding the same joints in the opposite
 *       order must not move a single {@code from}/{@code to}.</li>
 * </ul>
 *
 * <p>Lives in {@code :neoforge:test} rather than the L1 suite because every signature here is on
 * {@link BlockPos}, and {@code :common}'s test classpath has no Minecraft jar. Same pattern as
 * {@code EnergyLineDistributorTest}; no world is started, the provider is here for the classpath.
 *
 * <p>The traceability tags for MOD-313 sit on the METHODS below, per AUTOMATION-STANDARDS §3. Not here:
 * a tag in this class javadoc stands 70 lines above the first {@code @Test}, and
 * {@code gen_traceability.py} scans only 40 lines forward from a tag, so it produced a matrix row with
 * an empty method column instead.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class NetworkTopologyDeterminismTest {

	/**
	 * The fixture shape, as offsets: a straight run with a T-branch and a vertical stub off the far end.
	 * Chosen so the topology has all three joint kinds — a dead end, a turn and a 3-way branch — and so
	 * more than one tube run exists to get the order of wrong.
	 */
	private static final List<int[]> SHAPE = List.of(
			new int[] {0, 0, 0}, new int[] {1, 0, 0}, new int[] {2, 0, 0}, new int[] {3, 0, 0},
			new int[] {2, 0, 1}, new int[] {2, 0, 2},
			new int[] {0, 1, 0});

	/** Comfortably positive: every coordinate of the shape stays above zero. */
	private static final BlockPos FAR_FROM_ORIGIN = new BlockPos(120, 70, 340);

	/**
	 * Straddles the origin: with this base the shape spans y = -1..0 and z = -2..0, i.e. it crosses BOTH
	 * planes where the packed {@code asLong()} order inverts. A translation-invariant rule cannot tell
	 * this fixture from the one above; {@code asLong} can.
	 */
	private static final BlockPos ACROSS_ORIGIN = new BlockPos(-4, -1, -2);

	private static List<BlockPos> shapeAt(BlockPos origin) {
		List<BlockPos> out = new ArrayList<>();
		for (int[] offset : SHAPE) {
			out.add(origin.offset(offset[0], offset[1], offset[2]));
		}
		return out;
	}

	/** The same positions, re-expressed relative to their own origin, so two placements are comparable. */
	private static List<BlockPos> relative(List<BlockPos> positions, BlockPos origin) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : positions) {
			out.add(pos.subtract(origin));
		}
		return out;
	}

	private static List<String> edgesRelativeTo(BlockPos origin) {
		List<BlockPos> nodes = shapeAt(origin);
		// Split across the three input lists the way a real payload does: cables, then the endpoints.
		List<BlockPos> cables = nodes.subList(0, 5);
		List<BlockPos> producers = nodes.subList(5, 6);
		List<BlockPos> consumers = nodes.subList(6, 7);
		List<String> out = new ArrayList<>();
		for (NetworkEdge edge : NetworkTopology.fullAdjacency(cables, producers, consumers)) {
			out.add(edge.a().subtract(origin) + "->" + edge.b().subtract(origin));
		}
		return out;
	}

	private static List<String> tubeRunsRelativeTo(BlockPos origin, boolean jointsDescending) {
		List<BlockPos> nodes = shapeAt(origin);
		List<NetworkEdge> edges = NetworkTopology.fullAdjacency(nodes, List.of(), List.of());
		List<BlockPos> joints = new ArrayList<>(NetworkTopology.jointNodes(nodes, edges));
		joints.sort(jointsDescending
				? NetworkTopology.POSITION_ORDER.reversed()
				: NetworkTopology.POSITION_ORDER);
		// A LinkedHashSet so the ORDER the caller chose is what tubeRuns actually receives — with a plain
		// HashSet both directions would collapse to the same bucket order and the case would be vacuous.
		Set<BlockPos> jointSet = new LinkedHashSet<>(joints);
		List<String> out = new ArrayList<>();
		for (TubeRun run : NetworkTopology.tubeRuns(nodes, edges, jointSet)) {
			out.add(run.from().subtract(origin) + "=>" + run.to().subtract(origin));
		}
		return out;
	}

	/**
	 * @implements MOD-313 — the edge list of a shape does not depend on where the shape stands
	 */
	@Test
	void adjacencyIsTheSameWhereverTheBaseStands() {
		List<String> far = edgesRelativeTo(FAR_FROM_ORIGIN);
		List<String> across = edgesRelativeTo(ACROSS_ORIGIN);
		assertTrue(far.size() >= 6, "fixture must produce a non-trivial graph, got " + far);
		assertEquals(far, across,
				"the same network shape produced a different edge order after being moved across y=0/z=0 — "
						+ "the overlay's geometry is following the base's absolute coordinates again");
	}

	/**
	 * @implements MOD-313 — the edge list does not depend on the order the payload lists its nodes in
	 */
	@Test
	void adjacencyIgnoresTheOrderTheInputListsArriveIn() {
		List<BlockPos> nodes = shapeAt(FAR_FROM_ORIGIN);
		List<BlockPos> reversed = new ArrayList<>(nodes);
		java.util.Collections.reverse(reversed);
		assertEquals(NetworkTopology.fullAdjacency(nodes, List.of(), List.of()),
				NetworkTopology.fullAdjacency(reversed, List.of(), List.of()),
				"the edge order followed the order the payload happened to list its cables in — swapping "
						+ "the node set for an insertion-ordered one without also sorting would do this");
	}

	/**
	 * @implements MOD-313 — tube runs keep their from/to when the same shape is rebuilt elsewhere
	 */
	@Test
	void tubeRunsAreTheSameWhereverTheBaseStands() {
		List<String> far = tubeRunsRelativeTo(FAR_FROM_ORIGIN, false);
		List<String> across = tubeRunsRelativeTo(ACROSS_ORIGIN, false);
		assertTrue(far.size() >= 3, "fixture must produce several tube runs, got " + far);
		assertEquals(far, across,
				"TubeRun.from/to flipped when the same shape was rebuilt across y=0/z=0");
	}

	/**
	 * @implements MOD-313 — tube runs do not follow the order the renderer hands over its joint set
	 */
	@Test
	void tubeRunsIgnoreTheOrderTheCallerListsJointsIn() {
		List<String> ascending = tubeRunsRelativeTo(FAR_FROM_ORIGIN, false);
		List<String> descending = tubeRunsRelativeTo(FAR_FROM_ORIGIN, true);
		assertTrue(ascending.size() >= 3, "fixture must produce several tube runs, got " + ascending);
		assertEquals(ascending, descending,
				"reversing the joint set the renderer hands over changed TubeRun.from/to — the guarantee "
						+ "must live inside tubeRuns, not in the caller's choice of collection");
	}

	/**
	 * @implements MOD-313 — jointNodes iterates in geometric order, not in hash-bucket order
	 */
	@Test
	void jointNodesIterateInGeometricOrder() {
		List<BlockPos> nodes = shapeAt(ACROSS_ORIGIN);
		List<NetworkEdge> edges = NetworkTopology.fullAdjacency(nodes, List.of(), List.of());
		List<BlockPos> joints = new ArrayList<>(NetworkTopology.jointNodes(nodes, edges));
		assertTrue(joints.size() >= 4, "fixture must produce several joints, got " + joints);

		List<BlockPos> expected = new ArrayList<>(joints);
		expected.sort(NetworkTopology.POSITION_ORDER);
		assertEquals(relative(expected, ACROSS_ORIGIN), relative(joints, ACROSS_ORIGIN),
				"jointNodes returned a set whose iteration order is not the geometric one");
	}

	/**
	 * @implements MOD-313 — NetworkEdge canonicalises its pair translation-invariantly
	 */
	@Test
	void edgeEndpointsAreOrientedTheSameWhereverTheBaseStands() {
		// The record canonicalises its pair, and it used to do so by asLong() — which reverses across
		// y=0 and z=0. Pinned separately from the list order because it is a different mechanism: an
		// edge built from the SAME two blocks must come out with the same end in `a`.
		BlockPos lowZ = new BlockPos(0, 64, -1);
		BlockPos highZ = new BlockPos(0, 64, 0);
		NetworkEdge straddling = new NetworkEdge(highZ, lowZ);
		NetworkEdge shifted = new NetworkEdge(highZ.offset(0, 0, 1), lowZ.offset(0, 0, 1));
		assertEquals(straddling.a().subtract(lowZ), shifted.a().subtract(lowZ.offset(0, 0, 1)),
				"NetworkEdge put a different end first after a plain translation across z=0");
	}
}
