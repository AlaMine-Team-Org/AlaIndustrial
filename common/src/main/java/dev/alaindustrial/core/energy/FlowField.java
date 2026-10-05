package dev.alaindustrial.core.energy;

import dev.alaindustrial.core.net.DistanceField;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

/**
 * The three distance fields of an energy line and what the distribution kernel derives from them (MOD-715,
 * batch 7): the potential energy flows down, the sweep order, and the cables the downhill rule cannot reach.
 * Extracted from {@link EnergyTopologyCache}, which stays the adapter to the world — it decides which cables
 * the fields are seeded from (that needs the endpoints and their face ports) and calls {@link #rebuild}.
 *
 * <ul>
 *   <li>{@link #producer()} — distance from the supplying producers: the MOD-021 loss distance and the
 *       MOD-070 fallback field while nothing waits.</li>
 *   <li>{@link #sink()} — distance from every endpoint that wants energy: the direction of flow (MOD-252).</li>
 *   <li>{@link #machine()} — distance from the waiting machines only: a fork tie-break, never a path
 *       (MOD-254).</li>
 * </ul>
 *
 * <p>Minecraft-free: the position type is a parameter, and so is the order that breaks ties between
 * positions (ADR-006) — production passes {@code BlockPos} and {@link EnergyTopologyCache#BLOCK_POS_ORDER}.
 *
 * @param <P> the position type of a cable
 */
final class FlowField<P> {

	private final Function<P, ? extends Iterable<P>> cableNeighbours;
	private final DistanceField<P> producer;
	private final DistanceField<P> sink;
	private final DistanceField<P> machine;
	/** True while the sink field is non-empty: the flow is directed toward demand (MOD-252). */
	private boolean sinkMode;
	/**
	 * Cables in ascending flow potential — the per-tick sweep order (MOD-070); one list, refilled in place.
	 * Equal potentials: corridor before stranded cables (MOD-730), within each in the order the flood found them.
	 */
	private final List<P> propagationOrder = new ArrayList<>();
	/** Cables no downhill path reaches, farthest from the source first (MOD-318); refilled in place. */
	private final List<P> strandedOrder = new ArrayList<>();

	/**
	 * @param cableNeighbours the cables one hop from a cable, in a fixed order
	 * @param order how two cables at the same distance are ordered where a tie has to be broken (ADR-006)
	 */
	FlowField(Function<P, ? extends Iterable<P>> cableNeighbours, Comparator<? super P> order) {
		this.cableNeighbours = cableNeighbours;
		this.producer = new DistanceField<>(cableNeighbours, order);
		this.sink = new DistanceField<>(cableNeighbours, order);
		this.machine = new DistanceField<>(cableNeighbours, order);
	}

	DistanceField<P> producer() {
		return producer;
	}

	DistanceField<P> sink() {
		return sink;
	}

	DistanceField<P> machine() {
		return machine;
	}

	/**
	 * The flow potential of a cable, or {@code null} off the active field: the sink distance while anything
	 * waits, otherwise MINUS the producer distance — so "pull from strictly higher potential" is the same
	 * rule in both modes, and the fallback is bit for bit the pre-MOD-252 sweep.
	 */
	Integer flowPotential(P cable) {
		if (sinkMode) {
			return sink.distanceOrNull(cable);
		}
		Integer d = producer.distanceOrNull(cable);
		return d == null ? null : -d;
	}

	Integer producerDistance(P cable) {
		return producer.distanceOrNull(cable);
	}

	Integer machinePotential(P cable) {
		return machine.distanceOrNull(cable);
	}

	List<P> propagationOrder() {
		return propagationOrder;
	}

	List<P> strandedOrder() {
		return strandedOrder;
	}

	/**
	 * Re-derive the sweep order and the stranded cables from the current fields: ascending sink distance in
	 * sink mode, descending producer distance in fallback. Equal distances keep the order the flood reached
	 * them, except that in sink mode a corridor cable goes before a stranded one (MOD-730). {@code cables} is
	 * the line's cable set in its own order; the stranded list is collected from it.
	 */
	void rebuild(Iterable<P> cables) {
		sinkMode = !sink.isEmpty();
		propagationOrder.clear();
		propagationOrder.addAll(sinkMode ? sink.nodesByDistance(true) : producer.nodesByDistance(false));
		rebuildStrandedOrder(cables);
		yieldStrandedToCorridor();
	}

	/**
	 * At equal sink distance, the corridor sweeps before the dead ends (MOD-730). A spur cable is a legal
	 * downhill donor of the junction it hangs off, and the flood may list it before the corridor cable that
	 * feeds the same junction — whether it does depends only on the face order, i.e. on how the build is
	 * turned. Swept first, the spur takes the room the junction just made, the corridor stalls a tick, and a
	 * machine downstream gets a packet only every other tick. Corridor first, the spur fills only the room
	 * the corridor left: what it gives back is exactly the MOD-318 return when demand outgrows the source.
	 * A stable sort, so ties within the corridor and within the stranded cables keep the flood's order.
	 */
	private void yieldStrandedToCorridor() {
		if (strandedOrder.isEmpty()) {
			return;
		}
		Set<P> stranded = new LinkedHashSet<>(strandedOrder);
		propagationOrder.sort(Comparator.<P>comparingInt(sink::distanceOrNull).thenComparing(stranded::contains));
	}

	/**
	 * Which cables the downhill sweep can never fill (MOD-318): walked exactly the way the sweep moves energy
	 * — from the cables a supplying producer touches (producer distance 1) to strictly lower sink potential —
	 * so the complement really is "cannot be filled". Sorted by descending producer distance, so a stranded
	 * cable is visited before the cable that feeds it; equal distances by the field's position order, never
	 * by set order (MOD-304). Empty in fallback mode, where the producer field already reaches every cable.
	 */
	private void rebuildStrandedOrder(Iterable<P> cables) {
		strandedOrder.clear();
		if (!sinkMode || producer.isEmpty()) {
			return;
		}
		Set<P> reachable = new LinkedHashSet<>();
		Queue<P> queue = new ArrayDeque<>();
		for (Map.Entry<P, Integer> entry : producer.distances().entrySet()) {
			if (entry.getValue() == 1 && sink.contains(entry.getKey()) && reachable.add(entry.getKey())) {
				queue.add(entry.getKey());
			}
		}
		while (!queue.isEmpty()) {
			P current = queue.poll();
			int potential = sink.distanceOrNull(current);
			for (P next : cableNeighbours.apply(current)) {
				Integer nextPotential = sink.distanceOrNull(next);
				if (nextPotential != null && nextPotential < potential && reachable.add(next)) {
					queue.add(next);
				}
			}
		}
		List<P> stranded = new ArrayList<>();
		for (P cable : cables) {
			if (!reachable.contains(cable) && producer.contains(cable)) {
				stranded.add(cable);
			}
		}
		strandedOrder.addAll(producer.sortByDistance(stranded, true));
	}
}
