package dev.alaindustrial.network;

import dev.alaindustrial.core.energy.PosOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

	/**
	 * The geometric order every method here sorts by — see {@link PosOrder} for the rule and for why it
	 * is x/y/z rather than the packed {@code BlockPos.asLong()} (MOD-313).
	 *
	 * <p>It is what makes this class's output depend on the SHAPE of the network and on nothing else:
	 * not on the order the payload happened to list its positions in, not on which bucket a
	 * {@code HashSet} put an absolute {@code BlockPos} in, and not on where in the world the base was
	 * built. The overlay the player sees is therefore the same picture every time the same base is
	 * scanned.
	 *
	 * <p>Deliberately a second declaration rather than a reuse of {@code EnergyTopologyCache}'s
	 * package-private twin: both delegate to {@link PosOrder}, which is the single source of the RULE,
	 * and the alternative was widening a cache internal into public API for a two-line comparator.
	 */
	public static final Comparator<BlockPos> POSITION_ORDER =
			(a, b) -> PosOrder.compare(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());

	/** One undirected adjacency between two network positions, canonically ordered by
	 * {@link #POSITION_ORDER} so the same pair always compares equal regardless of discovery order —
	 * and so that translating a build leaves the pair oriented the same way (MOD-313; the previous
	 * {@code asLong()} canonicalisation swapped {@code a}/{@code b} across the y=0 and z=0 planes). */
	public record NetworkEdge(BlockPos a, BlockPos b) {
		public NetworkEdge {
			// PosOrder directly, not POSITION_ORDER: initialising a nested type does NOT initialise its
			// enclosing class, so a caller who builds an edge without ever touching NetworkTopology's
			// own members would read a null comparator field.
			if (PosOrder.compare(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()) > 0) {
				BlockPos tmp = a;
				a = b;
				b = tmp;
			}
		}
	}

	/** A directed hop for flow-animation purposes: energy moves from {@code from} toward {@code to}. */
	public record FlowEdge(BlockPos from, BlockPos to) {
	}

	/** Face mask with every face set — an endpoint wired on all six sides. */
	public static final int ALL_FACES = 0x3F;

	/**
	 * One endpoint's faces as one int ({@code 1 << Direction.ordinal()} each): bits 0-5 every face wired to
	 * a shown cable, bits 6-11 those that would take EU right now, bits 12-17 those that would give it now.
	 */
	public static int packFaces(int wired, int take, int emit) {
		return (wired & ALL_FACES) | (take & ALL_FACES) << 6 | (emit & ALL_FACES) << 12;
	}

	public static int wiredFaces(int packed) {
		return packed & ALL_FACES;
	}

	public static int takeFaces(int packed) {
		return packed >>> 6 & ALL_FACES;
	}

	public static int emitFaces(int packed) {
		return packed >>> 12 & ALL_FACES;
	}

	/**
	 * Which way energy moves along each wire, as the network itself moves it (MOD-665): a cable to the next
	 * cable along the faces the server read from the distribution kernel ({@code cableFlow}); a cable into
	 * an endpoint through a face that takes EU right now; an endpoint into a cable through a face that gives
	 * EU right now. Nothing is guessed on the client, so the sparks follow the same rule the energy does —
	 * every hungry machine fed, a full store skipped, two neighbours at equal potential left without an
	 * arrow rather than with two meeting head-on.
	 *
	 * @param packedFaces per endpoint, its {@link #packFaces wired, taking and giving faces}
	 * @param cableFlow   per cable, the faces it hands EU on through
	 */
	public static List<FlowEdge> flowFromNetwork(List<NetworkEdge> edges, Map<BlockPos, Integer> packedFaces,
			Map<BlockPos, Integer> cableFlow, Set<BlockPos> cables) {
		List<FlowEdge> flow = new ArrayList<>();
		for (NetworkEdge edge : edges) {
			addIfFlows(flow, edge.a(), edge.b(), packedFaces, cableFlow, cables);
			addIfFlows(flow, edge.b(), edge.a(), packedFaces, cableFlow, cables);
		}
		return List.copyOf(flow);
	}

	private static void addIfFlows(List<FlowEdge> flow, BlockPos from, BlockPos to, Map<BlockPos, Integer> packedFaces,
			Map<BlockPos, Integer> cableFlow, Set<BlockPos> cables) {
		boolean fromCable = cables.contains(from);
		boolean toCable = cables.contains(to);
		boolean flows;
		if (fromCable && toCable) {
			flows = (cableFlow.getOrDefault(from, 0) & faceBit(from, to)) != 0;
		} else if (fromCable) {
			flows = (takeFaces(packedFaces.getOrDefault(to, 0)) & faceBit(to, from)) != 0;
		} else if (toCable) {
			flows = (emitFaces(packedFaces.getOrDefault(from, 0)) & faceBit(from, to)) != 0;
		} else {
			flows = false;
		}
		if (flows) {
			flow.add(new FlowEdge(from, to));
		}
	}

	/**
	 * Hops from the nearest giving endpoint face to every position reached through cables - the trace's
	 * colour ramp (MOD-665). A position no giver reaches is absent.
	 */
	public static Map<BlockPos, Integer> distancesFromGivers(List<NetworkEdge> edges,
			Map<BlockPos, Integer> packedFaces, Set<BlockPos> cables) {
		Map<BlockPos, List<BlockPos>> adjacency = adjacency(edges);
		Map<BlockPos, Integer> distance = new LinkedHashMap<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		for (Map.Entry<BlockPos, Integer> e : packedFaces.entrySet()) {
			int emit = emitFaces(e.getValue());
			if (emit == 0) {
				continue;
			}
			distance.put(e.getKey(), 0);
			for (BlockPos n : adjacency.getOrDefault(e.getKey(), List.of())) {
				if (cables.contains(n) && (emit & faceBit(e.getKey(), n)) != 0 && !distance.containsKey(n)) {
					distance.put(n, 1);
					queue.add(n);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			int d = distance.get(current);
			for (BlockPos n : adjacency.getOrDefault(current, List.of())) {
				if (!distance.containsKey(n)) {
					distance.put(n, d + 1);
					if (cables.contains(n)) {
						queue.add(n);
					}
				}
			}
		}
		return distance;
	}

	private static Map<BlockPos, List<BlockPos>> adjacency(List<NetworkEdge> edges) {
		Map<BlockPos, List<BlockPos>> adjacency = new LinkedHashMap<>();
		for (NetworkEdge edge : edges) {
			adjacency.computeIfAbsent(edge.a(), k -> new ArrayList<>()).add(edge.b());
			adjacency.computeIfAbsent(edge.b(), k -> new ArrayList<>()).add(edge.a());
		}
		return adjacency;
	}

	/** The face bit of {@code from} that looks at the adjacent {@code to}. */
	private static int faceBit(BlockPos from, BlockPos to) {
		for (Direction dir : Direction.values()) {
			if (from.relative(dir).equals(to)) {
				return 1 << dir.ordinal();
			}
		}
		return 0;
	}

	/**
	 * The wires the network really has (MOD-665, D5): every pair of touching cables, plus one "leg" from
	 * an endpoint to each cable on a face listed in its mask ({@code 1 << Direction.ordinal()}, see
	 * {@code NetworkTraverser.endpointFaces}). Two endpoints are never joined to each other.
	 *
	 * <p>This replaced a purely geometric rule that joined ANY two touching positions. It drew wires the
	 * network does not have: between two machines standing side by side (the direct push between them
	 * is not the network), and from an endpoint to a cable its inert face merely touches (a generator's
	 * front, a Battery Box's side) — and, through such legs, turned a machine with cables on two sides
	 * into a pass-through the flow arrows walked across.
	 *
	 * <p>The returned list is in {@link #POSITION_ORDER} of the node the edge was discovered from, so it
	 * depends only on the network's shape — not on the order the inputs arrive in (MOD-313).
	 */
	public static List<NetworkEdge> connectedAdjacency(List<BlockPos> cables, Map<BlockPos, Integer> endpointFaces) {
		Set<BlockPos> cableSet = new LinkedHashSet<>(cables);
		Set<BlockPos> nodes = new LinkedHashSet<>(cableSet);
		nodes.addAll(endpointFaces.keySet());
		if (nodes.isEmpty()) {
			return List.of();
		}
		List<BlockPos> orderedNodes = new ArrayList<>(nodes);
		orderedNodes.sort(POSITION_ORDER);
		Set<NetworkEdge> edges = new LinkedHashSet<>();
		for (BlockPos pos : orderedNodes) {
			boolean isCable = cableSet.contains(pos);
			int mask = isCable ? 0 : endpointFaces.getOrDefault(pos, 0);
			for (Direction dir : Direction.values()) {
				BlockPos neighbour = pos.relative(dir);
				if (isCable) {
					if (cableSet.contains(neighbour)) {
						edges.add(new NetworkEdge(pos, neighbour));
					} else {
						Integer theirs = endpointFaces.get(neighbour);
						if (theirs != null && (theirs & (1 << dir.getOpposite().ordinal())) != 0) {
							edges.add(new NetworkEdge(pos, neighbour));
						}
					}
				} else if ((mask & (1 << dir.ordinal())) != 0 && cableSet.contains(neighbour)) {
					edges.add(new NetworkEdge(pos, neighbour));
				}
			}
		}
		return List.copyOf(edges);
	}

	/**
	 * Nodes where the tube should show a visible joint marker — dead ends, turns, and branches — as
	 * opposed to a plain straight-through hop (exactly two neighbours in exactly opposite directions),
	 * which should render as one seamless tube instead of a joint at every single cable block.
	 * {@code allNodes} must include every position that might need a marker (cables, producers,
	 * consumers) even ones with no edges at all — an isolated single-cable network still needs *a*
	 * marker, since nothing else indicates its position.
	 *
	 * <p>The returned set iterates in {@link #POSITION_ORDER} (MOD-313), so callers that walk it get the
	 * same sequence for the same shape wherever it stands.
	 */
	public static Set<BlockPos> jointNodes(List<BlockPos> allNodes, List<NetworkEdge> edges) {
		Map<BlockPos, List<BlockPos>> adjacency = new LinkedHashMap<>();
		for (NetworkEdge edge : edges) {
			adjacency.computeIfAbsent(edge.a(), k -> new ArrayList<>()).add(edge.b());
			adjacency.computeIfAbsent(edge.b(), k -> new ArrayList<>()).add(edge.a());
		}
		List<BlockPos> orderedNodes = new ArrayList<>(allNodes);
		orderedNodes.sort(POSITION_ORDER);
		Set<BlockPos> joints = new LinkedHashSet<>();
		for (BlockPos node : orderedNodes) {
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
}
