package dev.alaindustrial.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.client.render.NetworkOverlayState;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.item.tool.NetworkAnalyzerItem;
import dev.alaindustrial.network.NetworkTopology.FlowEdge;
import dev.alaindustrial.network.NetworkTopology.NetworkEdge;
import dev.alaindustrial.network.NetworkTraverser.TraversalResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * L1.5 regression coverage for the Network Analyzer overlay fixes of MOD-665 whose inputs are
 * {@link BlockPos} and friends — which is why they sit on {@code :neoforge:test}: {@code :common}'s test
 * classpath has no Minecraft jar. No world is started; these are pure functions of their arguments.
 */
class NetworkAnalyzerOverlayTest {

	private static final BlockPos O = new BlockPos(100, 64, -40);

	private static int face(Direction... dirs) {
		int mask = 0;
		for (Direction d : dirs) {
			mask |= 1 << d.ordinal();
		}
		return mask;
	}

	private static Set<NetworkEdge> edgeSet(List<NetworkEdge> edges) {
		return new HashSet<>(edges);
	}

	// ── D5: only the wires the network has ───────────────────────────────────────────────────────────

	/**
	 * Two machines side by side are not wired to each other, and an endpoint gets a leg only on the faces
	 * the server listed.
	 *
	 * @implements MOD-665-D5 — adjacency follows real connections: no machine–machine wire, no leg to a
	 *     cable an inert face merely touches
	 * @covers MOD-665
	 */
	@Test
	void adjacencyFollowsRealConnections() {
		BlockPos machineA = O;
		BlockPos machineB = O.east();                 // touches A — the direct push is not the network
		BlockPos cableWest = O.west();                // wired to A's west face
		BlockPos cableAbove = O.above();              // touches A's top, which is inert
		Map<BlockPos, Integer> endpoints = new LinkedHashMap<>();
		endpoints.put(machineA, face(Direction.WEST));
		endpoints.put(machineB, 0);

		Set<NetworkEdge> edges = edgeSet(NetworkTopology.connectedAdjacency(List.of(cableWest, cableAbove), endpoints));
		assertTrue(edges.contains(new NetworkEdge(machineA, cableWest)), "the wired face must get its leg");
		assertFalse(edges.contains(new NetworkEdge(machineA, machineB)), "two touching machines got a wire");
		assertFalse(edges.contains(new NetworkEdge(machineA, cableAbove)), "an inert face got a leg");
	}

	/**
	 * The sparks run the way the network moves energy, as the server read it (MOD-665): cable to cable
	 * along the faces it sent, out of an endpoint only through a face giving right now, into one only
	 * through a face taking right now — and never through a machine.
	 *
	 * @implements MOD-665-D5 — flow follows the network's own direction, not a guess from positions
	 * @covers MOD-665
	 */
	@Test
	void flowFollowsTheNetwork() {
		// generator — c1 — c2 — MACHINE — c3, the machine wired on both sides, taking on both.
		BlockPos gen = O;
		BlockPos c1 = O.east(1);
		BlockPos c2 = O.east(2);
		BlockPos machine = O.east(3);
		BlockPos c3 = O.east(4);
		Map<BlockPos, Integer> packed = new LinkedHashMap<>();
		packed.put(gen, NetworkTopology.packFaces(face(Direction.EAST), 0, face(Direction.EAST)));
		packed.put(machine, NetworkTopology.packFaces(face(Direction.WEST, Direction.EAST),
				face(Direction.WEST, Direction.EAST), 0));
		Map<BlockPos, Integer> wired = new LinkedHashMap<>();
		packed.forEach((p, f) -> wired.put(p, NetworkTopology.wiredFaces(f)));
		List<BlockPos> cables = List.of(c1, c2, c3);
		List<NetworkEdge> edges = NetworkTopology.connectedAdjacency(cables, wired);
		Map<BlockPos, Integer> cableFlow = Map.of(c1, face(Direction.EAST));
		List<FlowEdge> flow = NetworkTopology.flowFromNetwork(edges, packed, cableFlow, new HashSet<>(cables));

		assertTrue(flow.contains(new FlowEdge(gen, c1)), "the generator gives into its cable");
		assertTrue(flow.contains(new FlowEdge(c1, c2)), "the cable hands on the way the network said");
		assertTrue(flow.contains(new FlowEdge(c2, machine)), "the machine takes from its cable");
		assertTrue(flow.contains(new FlowEdge(c3, machine)), "and from the far side, where it takes too");
		assertFalse(flow.contains(new FlowEdge(c2, c1)), "no arrow against the network's direction");
		for (FlowEdge e : flow) {
			assertFalse(e.from().equals(machine), "an arrow leaves a machine that gives nothing: " + e);
		}
	}

	// ── D4: the payload is capped, nearest first ─────────────────────────────────────────────────────

	/**
	 * @implements MOD-665-D4-PAYLOAD — a network larger than the ceiling is cut to the positions nearest
	 *     the clicked cable and flagged as truncated
	 * @covers MOD-665
	 */
	@Test
	void payloadIsCappedAroundTheClick() {
		Set<BlockPos> cables = new LinkedHashSet<>();
		for (int i = 0; i < 40; i++) {
			cables.add(O.east(i));
		}
		BlockPos producer = O.west();
		BlockPos clicked = O.east(10);
		TraversalResult result = new TraversalResult(cables, Set.of(producer), Set.of(), Set.of(), 5, 5, 5, false,
				Map.of(producer, NetworkTopology.packFaces(face(Direction.EAST), 0, face(Direction.EAST))),
				Map.of(clicked, face(Direction.EAST)));

		NetworkAnalyzerPayload capped = NetworkAnalyzerPayload.of(Level.OVERWORLD, result, AnalyzerMode.TRAVERSE,
				clicked, 11);
		assertTrue(capped.truncated(), "a network over the ceiling must be flagged");
		assertEquals(10, capped.cables().size(), "one slot went to the producer, ten are left for cables");
		assertTrue(capped.cables().contains(clicked), "the clicked cable must survive the cut");
		assertFalse(capped.cables().contains(O.east(39)), "the far end should be the part cut away");
		assertEquals(3 + 10, capped.endpointFaces().length, "three bytes for the producer, one per kept cable");
		assertEquals(face(Direction.EAST), capped.faceMask(0));
		assertEquals(face(Direction.EAST), NetworkTopology.emitFaces(capped.packedFaces(0)));
		assertEquals(face(Direction.EAST), capped.cableFlowFaces(capped.cables().indexOf(clicked)),
				"a cable's flow byte travels with that cable through the cut");

		NetworkAnalyzerPayload whole = NetworkAnalyzerPayload.of(Level.OVERWORLD, result, AnalyzerMode.TRAVERSE,
				clicked, PayloadBudget.MAX_POSITIONS);
		assertFalse(whole.truncated());
		assertEquals(List.copyOf(cables), whole.cables(), "under the ceiling the list goes out untouched");
	}

	// ── D7: one actionbar line ───────────────────────────────────────────────────────────────────────

	/**
	 * @implements MOD-665-D7 — the limit and truncation notes ride on the statistics line instead of
	 *     replacing it in the actionbar
	 * @covers MOD-665
	 */
	@Test
	void readoutIsOneLineWithTheNotes() {
		TraversalResult hit = new TraversalResult(Set.of(O), Set.of(), Set.of(), Set.of(), 1, 2, 3, true, Map.of(),
				Map.of());
		Component line = NetworkAnalyzerItem.readout(hit, true, 32);
		assertEquals("gui.alaindustrial.network_analyzer.stats",
				((TranslatableContents) line.getContents()).getKey(), "the statistics must lead the line");
		List<String> keys = new ArrayList<>();
		for (Component sibling : line.getSiblings()) {
			if (sibling.getContents() instanceof TranslatableContents t) {
				keys.add(t.getKey());
			}
		}
		assertTrue(keys.contains("gui.alaindustrial.network_analyzer.note.limit"), "limit note missing: " + keys);
		assertTrue(keys.contains("gui.alaindustrial.network_analyzer.note.truncated"),
				"truncation note missing: " + keys);

		TraversalResult quiet = new TraversalResult(Set.of(O), Set.of(), Set.of(), Set.of(), 1, 2, 3, false,
				Map.of(), Map.of());
		assertTrue(NetworkAnalyzerItem.readout(quiet, false, 32).getSiblings().isEmpty(),
				"no notes when nothing was cut");
	}

	// ── D1: the trace belongs to one world and one dimension ────────────────────────────────────────

	private static NetworkAnalyzerPayload payloadIn(net.minecraft.resources.ResourceKey<Level> dimension) {
		return new NetworkAnalyzerPayload(dimension, List.of(O, O.east()), List.of(O.west()), List.of(), List.of(),
				new byte[] {(byte) face(Direction.EAST)}, AnalyzerMode.TRAVERSE, 0, 0, 0, false);
	}

	/**
	 * @implements MOD-665-D1 — clearing forgets the trace completely: nothing left to draw, and the very
	 *     same payload arriving again (a new world, same scan) is taken, not ignored as "already shown"
	 * @covers MOD-665
	 */
	@Test
	void clearForgetsTheTrace() {
		NetworkOverlayState state = new NetworkOverlayState();
		NetworkAnalyzerPayload payload = payloadIn(Level.OVERWORLD);
		assertTrue(state.update(payload));
		assertFalse(state.isEmpty());
		assertFalse(state.update(payload), "the same payload twice recomputes nothing");

		state.clear();
		assertTrue(state.isEmpty(), "clear must leave nothing to draw");
		assertTrue(state.update(payload), "after clear the same payload must be taken again");
	}

	/**
	 * @implements MOD-665-D1 — a trace from one dimension is dropped, not merely hidden, once the player
	 *     is elsewhere: coming back does not bring it back
	 * @covers MOD-665
	 */
	@Test
	void changingDimensionDropsTheTrace() {
		NetworkOverlayState state = new NetworkOverlayState();
		state.update(payloadIn(Level.OVERWORLD));
		assertTrue(state.retainFor(Level.OVERWORLD));
		assertFalse(state.retainFor(Level.NETHER), "nothing to draw in the Nether");
		assertFalse(state.retainFor(Level.OVERWORLD), "back in the Overworld the old trace must not return");
		assertTrue(state.isEmpty());
	}
}
