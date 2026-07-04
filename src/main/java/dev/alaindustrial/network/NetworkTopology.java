package dev.alaindustrial.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Pure graph algorithms behind the Network Analyzer's client-side highlight (MOD-016): the full
 * (undirected) adjacency between cables, producers and consumers, and the flow direction along each
 * edge derived from a multi-source BFS distance from every producer. No client/rendering dependency
 * — plain {@link BlockPos} data in, data out, so it can be exercised without a running game.
 */
public final class NetworkTopology {
	private NetworkTopology() {
	}

	/** One undirected adjacency between two network positions, canonically ordered by {@link BlockPos#asLong()}
	 * so the same pair always compares equal regardless of discovery order. */
	public record NetworkEdge(BlockPos a, BlockPos b) {
		public NetworkEdge {
			if (a.asLong() > b.asLong()) {
				BlockPos tmp = a;
				a = b;
				b = tmp;
			}
		}
	}

	/** A directed hop for flow-animation purposes: energy moves from {@code from} toward {@code to}. */
	public record FlowEdge(BlockPos from, BlockPos to) {
	}

	/** A maximal straight stretch of tube between two {@link #jointNodes} — see {@link #tubeRuns}.
	 * {@code positions} lists every node the run passes through, {@code from} and {@code to} inclusive,
	 * in walk order; used to check every one is loaded before rendering the run as a single tube. */
	public record TubeRun(BlockPos from, BlockPos to, List<BlockPos> positions) {
	}

	/**
	 * Every adjacent pair across cables, producers and consumers combined, each pair kept once. Producers and
	 * consumers are included as graph nodes (not just cables) so the "last mile" between a generator/machine and
	 * its cable is drawn too, instead of leaving those endpoints visually disconnected from the network.
	 */
	public static List<NetworkEdge> fullAdjacency(List<BlockPos> cables, List<BlockPos> producers,
			List<BlockPos> consumers) {
		Set<BlockPos> nodes = new HashSet<>(cables);
		nodes.addAll(producers);
		nodes.addAll(consumers);
		if (nodes.isEmpty()) {
			return List.of();
		}
		Set<NetworkEdge> edges = new LinkedHashSet<>();
		for (BlockPos pos : nodes) {
			for (Direction dir : Direction.values()) {
				BlockPos neighbour = pos.relative(dir);
				if (nodes.contains(neighbour)) {
					edges.add(new NetworkEdge(pos, neighbour));
				}
			}
		}
		return List.copyOf(edges);
	}

	/**
	 * Orients every edge whose endpoints sit at a different multi-source BFS distance from {@code producers}
	 * toward the farther endpoint — flow leaves a generator and travels outward. This works across cycles and
	 * redundant cable paths (unlike a spanning tree): every edge in {@code edges} is considered independently
	 * against the distance gradient, so a loop's "far" side still gets an outward-pointing direction instead of
	 * being silently dropped.
	 *
	 * <p>Edges with no producer-reachable endpoint (unpowered island) or with both endpoints equidistant
	 * (ambiguous — e.g. two producers directly adjacent to each other) are left un-oriented; they still render
	 * as static lines via {@link #fullAdjacency}, they simply carry no flow animation.
	 */
	public static List<FlowEdge> flowDirections(List<NetworkEdge> edges, List<BlockPos> producers) {
		if (edges.isEmpty() || producers.isEmpty()) {
			return List.of();
		}
		Map<BlockPos, List<BlockPos>> adjacency = new HashMap<>();
		for (NetworkEdge edge : edges) {
			adjacency.computeIfAbsent(edge.a(), k -> new ArrayList<>()).add(edge.b());
			adjacency.computeIfAbsent(edge.b(), k -> new ArrayList<>()).add(edge.a());
		}

		Map<BlockPos, Integer> distance = new HashMap<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos producer : producers) {
			if (distance.putIfAbsent(producer, 0) == null) {
				queue.add(producer);
			}
		}
		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			int nextDistance = distance.get(current) + 1;
			for (BlockPos neighbour : adjacency.getOrDefault(current, List.of())) {
				if (!distance.containsKey(neighbour)) {
					distance.put(neighbour, nextDistance);
					queue.add(neighbour);
				}
			}
		}

		List<FlowEdge> flow = new ArrayList<>();
		for (NetworkEdge edge : edges) {
			Integer distanceA = distance.get(edge.a());
			Integer distanceB = distance.get(edge.b());
			if (distanceA == null || distanceB == null || distanceA.equals(distanceB)) {
				continue;
			}
			flow.add(distanceA < distanceB ? new FlowEdge(edge.a(), edge.b()) : new FlowEdge(edge.b(), edge.a()));
		}
		return List.copyOf(flow);
	}

	/**
	 * Nodes where the tube should show a visible joint marker — dead ends, turns, and branches — as
	 * opposed to a plain straight-through hop (exactly two neighbours in exactly opposite directions),
	 * which should render as one seamless tube instead of a joint at every single cable block.
	 * {@code allNodes} must include every position that might need a marker (cables, producers,
	 * consumers) even ones with no edges at all — an isolated single-cable network still needs *a*
	 * marker, since nothing else indicates its position.
	 */
	public static Set<BlockPos> jointNodes(List<BlockPos> allNodes, List<NetworkEdge> edges) {
		Map<BlockPos, List<BlockPos>> adjacency = new HashMap<>();
		for (NetworkEdge edge : edges) {
			adjacency.computeIfAbsent(edge.a(), k -> new ArrayList<>()).add(edge.b());
			adjacency.computeIfAbsent(edge.b(), k -> new ArrayList<>()).add(edge.a());
		}
		Set<BlockPos> joints = new HashSet<>();
		for (BlockPos node : allNodes) {
			List<BlockPos> neighbours = adjacency.getOrDefault(node, List.of());
			if (neighbours.size() != 2 || !isStraightThrough(node, neighbours.get(0), neighbours.get(1))) {
				joints.add(node);
			}
		}
		return joints;
	}

	/** Whether {@code n1 - node} and {@code node - n2} point the same way — i.e. {@code n1}, {@code node},
	 * {@code n2} lie on one straight axis-aligned line, so a tube through {@code node} needs no joint. */
	private static boolean isStraightThrough(BlockPos node, BlockPos n1, BlockPos n2) {
		return node.getX() - n1.getX() == n2.getX() - node.getX()
				&& node.getY() - n1.getY() == n2.getY() - node.getY()
				&& node.getZ() - n1.getZ() == n2.getZ() - node.getZ();
	}

	/**
	 * Merges every maximal chain of straight-through hops into one {@link TubeRun} between the
	 * {@link #jointNodes} at each end, skipping the intermediate positions for rendering purposes. Even
	 * when every 1-block segment along a straight run is individually correct and bit-identical at its
	 * shared edges, drawing it as many separate quads in a row can still show a faint seam per segment
	 * under the renderer's lighting/antialiasing — this removes the seams by not having them: one quad
	 * spans the whole straight stretch instead of one per block.
	 *
	 * <p>Only the tube's static outline uses this — {@link #flowDirections}' BFS distance gradient and
	 * the flow-dot animation keep working off the original block-by-block {@code edges}, since the
	 * flow animation's per-block phase timing depends on that granularity.
	 */
	public static List<TubeRun> tubeRuns(List<BlockPos> allNodes, List<NetworkEdge> edges, Set<BlockPos> jointNodes) {
		Map<BlockPos, List<BlockPos>> adjacency = new HashMap<>();
		for (NetworkEdge edge : edges) {
			adjacency.computeIfAbsent(edge.a(), k -> new ArrayList<>()).add(edge.b());
			adjacency.computeIfAbsent(edge.b(), k -> new ArrayList<>()).add(edge.a());
		}

		Set<NetworkEdge> visitedHops = new HashSet<>();
		List<TubeRun> runs = new ArrayList<>();
		int maxSteps = allNodes.size() + 1;

		for (BlockPos joint : jointNodes) {
			for (BlockPos neighbour : adjacency.getOrDefault(joint, List.of())) {
				if (!visitedHops.add(new NetworkEdge(joint, neighbour))) {
					continue;
				}
				List<BlockPos> positions = new ArrayList<>();
				positions.add(joint);
				positions.add(neighbour);

				BlockPos previous = joint;
				BlockPos current = neighbour;
				int steps = 0;
				while (!jointNodes.contains(current) && steps < maxSteps) {
					List<BlockPos> currentNeighbours = adjacency.get(current);
					BlockPos next = currentNeighbours.get(0).equals(previous) ? currentNeighbours.get(1)
							: currentNeighbours.get(0);
					visitedHops.add(new NetworkEdge(current, next));
					positions.add(next);
					previous = current;
					current = next;
					steps++;
				}
				runs.add(new TubeRun(joint, current, List.copyOf(positions)));
			}
		}
		return List.copyOf(runs);
	}
}
