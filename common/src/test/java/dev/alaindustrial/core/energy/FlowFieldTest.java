package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link FlowField} — the energy line's three fields and what the kernel derives from them:
 * flow potential, sweep order and stranded cables (MOD-715, batch 7; MOD-070, MOD-252, MOD-254, MOD-318).
 * Integer cables on hand-drawn graphs.
 *
 * @implements MOD-715-FF01 — the flow field: fallback and sink mode, sweep order, stranded cables
 */
class FlowFieldTest {

	/** An undirected graph from an edge list; neighbours in ascending node order. */
	private static Function<Integer, List<Integer>> graph(Map<Integer, List<Integer>> edges) {
		return node -> edges.getOrDefault(node, List.of());
	}

	/** The path 0 - 1 - 2 - 3 - 4 - 5. */
	private static final Map<Integer, List<Integer>> PATH = Map.of(0, List.of(1), 1, List.of(0, 2), 2, List.of(1, 3),
			3, List.of(2, 4), 4, List.of(3, 5), 5, List.of(4));

	private static final List<Integer> PATH_CABLES = List.of(0, 1, 2, 3, 4, 5);

	@Test
	void withNothingWaitingTheFieldFallsBackToTheProducer() {
		FlowField<Integer> flow = new FlowField<>(graph(PATH), Comparator.naturalOrder());
		flow.producer().seed(0, 1);
		flow.producer().flood();
		flow.rebuild(PATH_CABLES);
		assertEquals(List.of(5, 4, 3, 2, 1, 0), flow.propagationOrder(), "farthest from the source first");
		assertEquals(-3, flow.flowPotential(2), "fallback potential is minus the producer distance");
		assertEquals(3, flow.producerDistance(2));
		assertTrue(flow.strandedOrder().isEmpty(), "the producer field reaches every cable");
		assertNull(flow.flowPotential(9));
	}

	@Test
	void aWaitingSinkDirectsTheFlowAndStrandsTheRunPastIt() {
		FlowField<Integer> flow = new FlowField<>(graph(PATH), Comparator.naturalOrder());
		flow.producer().seed(0, 1);
		flow.producer().flood();
		flow.sink().seed(3, 1);
		flow.sink().flood();
		flow.rebuild(PATH_CABLES);
		assertEquals(List.of(3, 2, 4, 1, 5, 0), flow.propagationOrder(),
				"ascending sink distance, reach order on ties");
		assertEquals(2, flow.flowPotential(2), "sink-mode potential is the sink distance");
		assertEquals(List.of(5, 4), flow.strandedOrder(), "past the sink, farthest from the source first");
	}

	@Test
	void strandedCablesAtOneDistanceAreOrderedByThePositionOrder() {
		// 0 - 1 - 2 with two spurs 3 and 4 off cable 1; the sink stands at 2, the producer at 0.
		Map<Integer, List<Integer>> star = Map.of(0, List.of(1), 1, List.of(0, 2, 3, 4), 2, List.of(1),
				3, List.of(1), 4, List.of(1));
		for (boolean natural : new boolean[] {true, false}) {
			Comparator<Integer> order = natural ? Comparator.naturalOrder() : Comparator.reverseOrder();
			FlowField<Integer> flow = new FlowField<>(graph(star), order);
			flow.producer().seed(0, 1);
			flow.producer().flood();
			flow.sink().seed(2, 1);
			flow.sink().flood();
			flow.rebuild(List.of(0, 1, 2, 3, 4));
			assertEquals(natural ? List.of(3, 4) : List.of(4, 3), flow.strandedOrder());
		}
	}

	@Test
	void aCableAtEqualPotentialIsNotReachable() {
		// Triangle 1-2-3 with a tail 0 on cable 1; producer on cable 1, sink on cable 2. Cables 1 and 3
		// are both one hop from the sink: equal potential, so the sweep never moves energy from 1 into 3,
		// and 3 is stranded — the strictness the sweep's own `<` rule has.
		Map<Integer, List<Integer>> triangle = Map.of(0, List.of(1), 1, List.of(0, 2, 3), 2, List.of(1, 3),
				3, List.of(1, 2));
		FlowField<Integer> flow = new FlowField<>(graph(triangle), Comparator.naturalOrder());
		flow.producer().seed(1, 1);
		flow.producer().flood();
		flow.sink().seed(2, 1);
		flow.sink().flood();
		flow.rebuild(List.of(0, 1, 2, 3));
		assertEquals(List.of(0, 3), flow.strandedOrder());
	}

	@Test
	void onlyCablesTouchingAProducerStartTheReachableWalk() {
		// The producer field seeded at distance 2 only: nothing is at distance 1, so no cable is reachable
		// from a source's own cable and every producer-field cable is stranded.
		FlowField<Integer> flow = new FlowField<>(graph(PATH), Comparator.naturalOrder());
		flow.producer().seed(0, 2);
		flow.producer().flood();
		flow.sink().seed(5, 1);
		flow.sink().flood();
		flow.rebuild(PATH_CABLES);
		assertEquals(List.of(5, 4, 3, 2, 1, 0), flow.strandedOrder());
	}

	@Test
	void theMachineFieldOnlyAnswersTheTieBreak() {
		FlowField<Integer> flow = new FlowField<>(graph(PATH), Comparator.naturalOrder());
		assertNull(flow.machinePotential(1), "no machine waiting: no tie-break anywhere");
		flow.machine().seed(1, 1);
		flow.machine().flood();
		flow.rebuild(PATH_CABLES);
		assertEquals(2, flow.machinePotential(2));
		assertTrue(flow.propagationOrder().isEmpty(), "the machine field never makes a sweep order");
	}

	@Test
	void rebuildRefillsTheSameLists() {
		FlowField<Integer> flow = new FlowField<>(graph(PATH), Comparator.naturalOrder());
		List<Integer> order = flow.propagationOrder();
		List<Integer> stranded = flow.strandedOrder();
		flow.producer().seed(0, 1);
		flow.producer().flood();
		flow.sink().seed(3, 1);
		flow.sink().flood();
		flow.rebuild(PATH_CABLES);
		flow.sink().clear();
		flow.rebuild(PATH_CABLES);
		assertSame(order, flow.propagationOrder(), "the kernel holds the list; it is refilled, not replaced");
		assertSame(stranded, flow.strandedOrder());
		assertEquals(List.of(5, 4, 3, 2, 1, 0), new ArrayList<>(order), "back in fallback after the sink left");
		assertEquals(Set.of(), Set.copyOf(stranded));
	}
}
