package dev.alaindustrial.client.render;

import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.NetworkTopology;
import dev.alaindustrial.network.NetworkTopology.FlowEdge;
import dev.alaindustrial.network.NetworkTopology.NetworkEdge;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * What the Network Analyzer overlay currently shows (MOD-665): the last payload and the topology derived
 * from it once, on arrival. Split out of {@link NetworkOverlayRenderer} so it holds no client-only type
 * and its lifecycle can be tested on the server test classpath.
 *
 * <p>D1: the state used to live forever — nothing cleared it on leaving a world, and a return to a
 * dimension brought the old trace back. {@link #clear()} is now called on disconnect by both loaders
 * ({@code ClientDisconnectReset}) and by {@link #retainFor} whenever the player's dimension no longer
 * matches the one the trace was taken in.
 */
public final class NetworkOverlayState {
	private NetworkAnalyzerPayload payload;
	private ResourceKey<Level> dimension;
	private List<BlockPos> cables = List.of();
	private List<BlockPos> producers = List.of();
	private List<BlockPos> consumers = List.of();
	private List<BlockPos> storage = List.of();
	private List<NetworkEdge> edges = List.of();
	private List<FlowEdge> flowEdges = List.of();
	private Set<BlockPos> jointNodes = Set.of();
	private Map<BlockPos, Integer> producerDistance = Map.of();
	private int maxDistance;
	private Set<BlockPos> endpointPositions = Set.of();
	private boolean truncated;

	/**
	 * Adopts {@code next} and recomputes the topology, unless it is the payload already shown — payloads
	 * are immutable and replaced wholesale, so reference identity is the change test.
	 *
	 * @return whether anything changed
	 */
	public boolean update(NetworkAnalyzerPayload next) {
		if (next == null || next == payload) {
			return false;
		}
		payload = next;
		dimension = next.dimension();
		cables = next.cables();
		producers = next.producers();
		consumers = next.consumers();
		storage = next.storage();
		truncated = next.truncated();
		// Faces per endpoint, in the payload's producers ++ consumers ++ storage order (D5).
		Map<BlockPos, Integer> faces = new LinkedHashMap<>();
		int index = 0;
		for (List<BlockPos> group : List.of(producers, consumers, storage)) {
			for (BlockPos pos : group) {
				faces.merge(pos, next.packedFaces(index++), (a, b) -> a | b);
			}
		}
		Map<BlockPos, Integer> wired = new LinkedHashMap<>();
		faces.forEach((pos, packed) -> wired.put(pos, NetworkTopology.wiredFaces(packed)));
		edges = NetworkTopology.connectedAdjacency(cables, wired);
		Set<BlockPos> cableSet = new HashSet<>(cables);
		// The direction the network itself moves EU in (MOD-665), sent by the server.
		Map<BlockPos, Integer> cableFlow = new LinkedHashMap<>();
		for (int i = 0; i < cables.size(); i++) {
			cableFlow.put(cables.get(i), next.cableFlowFaces(i));
		}
		flowEdges = NetworkTopology.flowFromNetwork(edges, faces, cableFlow, cableSet);
		producerDistance = NetworkTopology.distancesFromGivers(edges, faces, cableSet);
		maxDistance = 0;
		for (int d : producerDistance.values()) {
			maxDistance = Math.max(maxDistance, d);
		}
		List<BlockPos> allEndpoints = new ArrayList<>(wired.keySet());
		List<BlockPos> allNodes = new ArrayList<>(cables.size() + allEndpoints.size());
		allNodes.addAll(cables);
		allNodes.addAll(allEndpoints);
		endpointPositions = new HashSet<>(allEndpoints);
		// Endpoints are always joints (MOD-059): without this union a producer/consumer squeezed between
		// two collinear cables counts as a plain straight-through hop — no marker, and the tube run would
		// pass level through a half-block panel's cell instead of dropping to it.
		Set<BlockPos> joints = new HashSet<>(NetworkTopology.jointNodes(allNodes, edges));
		joints.addAll(endpointPositions);
		jointNodes = joints;
		return true;
	}

	/** Forgets everything; the next {@link #update}, even with the same payload, starts afresh. */
	public void clear() {
		payload = null;
		dimension = null;
		cables = List.of();
		producers = List.of();
		consumers = List.of();
		storage = List.of();
		edges = List.of();
		flowEdges = List.of();
		jointNodes = Set.of();
		producerDistance = Map.of();
		maxDistance = 0;
		endpointPositions = Set.of();
		truncated = false;
	}

	/**
	 * Keeps the trace only while the player is in the dimension it was taken in; otherwise clears it
	 * (D1 — a trace from the Overworld used to come back after a trip to the Nether).
	 *
	 * @return whether there is still something to draw in {@code current}
	 */
	public boolean retainFor(ResourceKey<Level> current) {
		if (isEmpty()) {
			return false;
		}
		if (current == null || !current.equals(dimension)) {
			clear();
			return false;
		}
		return true;
	}

	public boolean isEmpty() {
		return cables.isEmpty() && producers.isEmpty() && consumers.isEmpty() && storage.isEmpty();
	}

	public List<BlockPos> cables() {
		return cables;
	}

	public List<BlockPos> producers() {
		return producers;
	}

	public List<BlockPos> consumers() {
		return consumers;
	}

	public List<BlockPos> storage() {
		return storage;
	}

	public List<NetworkEdge> edges() {
		return edges;
	}

	public List<FlowEdge> flowEdges() {
		return flowEdges;
	}

	public Set<BlockPos> jointNodes() {
		return jointNodes;
	}

	/**
	 * Where along the network {@code pos} sits, 0 at a producer and 1 at the farthest position a producer
	 * reaches — the trace's colour ramp (MOD-665).
	 */
	public double pathFraction(BlockPos pos) {
		return OverlayMath.pathFraction(producerDistance.get(pos), maxDistance);
	}

	public boolean isEndpoint(BlockPos pos) {
		return endpointPositions.contains(pos);
	}

	public boolean truncated() {
		return truncated;
	}
}
