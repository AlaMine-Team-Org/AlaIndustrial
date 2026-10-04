package dev.alaindustrial.core.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A multi-source breadth-first distance field over a graph of positions {@code P} (MOD-715, CORE-4): seed
 * some nodes at a distance, flood, read every reached node's hop count. The networks' fields — the energy
 * network's producer, sink and machine fields (MOD-021, MOD-252, MOD-254) and the fluid network's
 * hungry-sink field (MOD-677) — are all this one walk.
 *
 * <p><b>Order is part of the contract (ADR-006).</b> The field never iterates a hash: distances live in
 * insertion order, so which node was reached first — and therefore how equal distances are listed — is
 * exactly the order of the seeds and of what {@code neighbours} returns. Where the field itself has to
 * break a tie between positions ({@link #sortByDistance}), it uses the comparator it was built with, which
 * is mandatory so that no caller can fall back to an order that follows absolute coordinates or the JVM
 * run. Minecraft-free: production passes {@code BlockPos} and a neighbour function, L1 passes integers.
 *
 * @param <P> the position type; must have value equality
 */
public final class DistanceField<P> {

	private final Map<P, Integer> distance = new LinkedHashMap<>();
	private final ArrayDeque<P> frontier = new ArrayDeque<>();
	private final Function<P, ? extends Iterable<P>> neighbours;
	private final Comparator<? super P> order;

	/**
	 * @param neighbours the nodes one hop from a node that the flood may enter, in a fixed order — the
	 *     caller filters out what is not part of the graph or not traversable from this node
	 * @param order how two positions at the same distance are ordered when the field sorts them
	 */
	public DistanceField(Function<P, ? extends Iterable<P>> neighbours, Comparator<? super P> order) {
		this.neighbours = Objects.requireNonNull(neighbours, "neighbours");
		this.order = Objects.requireNonNull(order, "order");
	}

	/** Forget every distance and every pending seed. */
	public void clear() {
		distance.clear();
		frontier.clear();
	}

	/**
	 * Seed {@code node} at {@code hops}, unless it already has a distance. Returns whether it was new. The
	 * flood starts from the seeds in the order they were given.
	 */
	public boolean seed(P node, int hops) {
		if (distance.putIfAbsent(node, hops) == null) {
			frontier.add(node);
			return true;
		}
		return false;
	}

	/** Flood from the pending seeds: every node first reached from a node at {@code d} gets {@code d + 1}. */
	public void flood() {
		while (!frontier.isEmpty()) {
			P current = frontier.poll();
			int next = distance.get(current) + 1;
			for (P neighbour : neighbours.apply(current)) {
				if (distance.putIfAbsent(neighbour, next) == null) {
					frontier.add(neighbour);
				}
			}
		}
	}

	/** The hop count of {@code node}, or {@code null} when the flood never reached it. */
	public Integer distanceOrNull(P node) {
		return distance.get(node);
	}

	public boolean contains(P node) {
		return distance.containsKey(node);
	}

	public boolean isEmpty() {
		return distance.isEmpty();
	}

	public int size() {
		return distance.size();
	}

	/** Every reached node with its distance, in the order the flood reached them. Read-only. */
	public Map<P, Integer> distances() {
		return Collections.unmodifiableMap(distance);
	}

	/**
	 * The reached nodes ordered by distance — nearest first when {@code ascending}, farthest first otherwise —
	 * with equal distances kept in the order the flood reached them (a stable sort, no comparator involved).
	 */
	public List<P> nodesByDistance(boolean ascending) {
		List<P> nodes = new ArrayList<>(distance.keySet());
		if (ascending) {
			nodes.sort((a, b) -> Integer.compare(distance.get(a), distance.get(b)));
		} else {
			nodes.sort((a, b) -> Integer.compare(distance.get(b), distance.get(a)));
		}
		return nodes;
	}

	/**
	 * {@code nodes}, every one of which the flood reached, sorted by distance — farthest first when
	 * {@code descending} — and equal distances broken by this field's order.
	 */
	public List<P> sortByDistance(Collection<P> nodes, boolean descending) {
		List<P> sorted = new ArrayList<>(nodes);
		sorted.sort((a, b) -> {
			int byDistance = descending ? Integer.compare(distance.get(b), distance.get(a))
					: Integer.compare(distance.get(a), distance.get(b));
			return byDistance != 0 ? byDistance : order.compare(a, b);
		});
		return sorted;
	}
}
