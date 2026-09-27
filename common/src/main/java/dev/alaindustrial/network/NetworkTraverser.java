package dev.alaindustrial.network;

import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.EnergyNetworkDiagnostics;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.energy.NetworkManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Read-only multi-network traversal for the Network Analyzer's Traverse mode (MOD-047).
 *
 * <p>Background: {@link EnergyNetwork} builds connectivity only from cable↔cable adjacency
 * ({@link NetworkManager#register}), so a storage sink such as BatteryBox — which is an endpoint,
 * not a cable — never "stitches" two cable segments together. A layout like
 * {@code [Generator]─cable─[BatteryBox]─cable─[Machine]} is therefore two distinct
 * {@code EnergyNetwork} instances, and the original analyzer (MOD-016) only highlighted the clicked
 * half, leaving everything beyond the BatteryBox invisible.
 *
 * <p>This class performs a breadth-first walk that treats a storage sink as a bridge: starting from
 * the clicked cable's network, it finds every storage-sink endpoint, looks through each of its faces
 * that is a live energy port, and — if the cable there belongs to a not-yet-visited network — crosses
 * into it and repeats. The result is the union of cables, producers, consumers and storage sinks
 * across all connected networks, which the analyzer payload ships to the client as one picture.
 *
 * <p>{@link dev.alaindustrial.item.tool.AnalyzerMode#STOP_AT_STORAGE} mode is just the single clicked
 * network (the original MOD-016 behaviour) — no bridging.
 *
 * <p>Pure read-only: never mutates networks or touches {@link EnergyNetwork#tick()}. The traversal
 * is guarded by {@code maxNetworks} so an absurdly large factory with dozens of BatteryBoxes can't
 * explode the scan; when the cap is reached {@link TraversalResult#hitLimit()} flips true and the
 * caller surfaces a warning.
 */
public final class NetworkTraverser {
	private NetworkTraverser() {
	}

	/**
	 * Walk the network(s) reachable from {@code start} according to {@code mode}, capped at
	 * {@code maxNetworks} visited networks. The start network is always included even if the cap is 1.
	 */
	public static TraversalResult traverse(ServerLevel level, EnergyNetwork start,
			dev.alaindustrial.item.tool.AnalyzerMode mode, int maxNetworks) {
		if (mode == dev.alaindustrial.item.tool.AnalyzerMode.STOP_AT_STORAGE) {
			// Original MOD-016 behaviour: one network, no bridging.
			return collectSingle(level, start);
		}
		return traverseThrough(level, start, Math.max(1, maxNetworks));
	}

	/**
	 * One-network snapshot for STOP_AT_STORAGE: cables + producers + consumers. A node that both feeds
	 * and draws from this very network (a Battery Box in the middle of a bus, IN and OUT faces both
	 * cabled) is listed once, as storage — listed in both roles it was drawn as two cubes in one cell (D6).
	 */
	private static TraversalResult collectSingle(ServerLevel level, EnergyNetwork net) {
		EnergyNetworkDiagnostics d = net.diagnostics();
		Set<BlockPos> cables = new LinkedHashSet<>(net.cables());
		Set<BlockPos> producers = new LinkedHashSet<>(d.producerPositions());
		Set<BlockPos> consumers = new LinkedHashSet<>(d.consumerPositions());
		Set<BlockPos> storage = new LinkedHashSet<>();
		Set<BlockPos> giving = givingEndpoints(level, net, d);
		separateDualRole(producers, consumers, storage);
		return new TraversalResult(
				cables,
				producers,
				consumers,
				storage,
				d.producerSupplyEstimate(),
				d.consumerDemandEstimate(),
				AnalyzerTotals.fresh(d.lastTickMoved(), d.lastTickAt(), level.getGameTime()),
				false,
				endpointFaces(level, cables, producers, consumers, storage, giving),
				d.cableFlowFaces());
	}

	/**
	 * Multi-network BFS: seed with the clicked network, then cross through every storage sink into
	 * adjacent cable networks until none remain or the cap is hit.
	 */
	private static TraversalResult traverseThrough(ServerLevel level, EnergyNetwork start, int maxNetworks) {
		// LinkedHashSet, although this set is never iterated: EnergyNetwork has no equals/hashCode of its
		// own, so a HashSet here buckets by IDENTITY hash — a value that differs on every JVM start
		// (MOD-313). Keeping it ordered means the collection can never quietly start leaking that.
		Set<EnergyNetwork> visited = new LinkedHashSet<>();
		Queue<EnergyNetwork> queue = new ArrayDeque<>();
		visited.add(start);
		queue.add(start);

		Set<BlockPos> cables = new LinkedHashSet<>();
		Set<BlockPos> producers = new LinkedHashSet<>();
		Set<BlockPos> consumers = new LinkedHashSet<>();
		Set<BlockPos> storageSinks = new LinkedHashSet<>();
		long supply = 0;
		long demand = 0;
		List<long[]> flows = new ArrayList<>(); // per network: moved, of it into storage, out of storage
		long now = level.getGameTime();
		boolean hitLimit = false;
		Set<BlockPos> giving = new LinkedHashSet<>();
		Map<BlockPos, Integer> cableFlow = new LinkedHashMap<>();

		while (!queue.isEmpty()) {
			EnergyNetwork net = queue.poll();
			EnergyNetworkDiagnostics d = net.diagnostics();
			cables.addAll(net.cables());
			giving.addAll(givingEndpoints(level, net, d));
			d.cableFlowFaces().forEach((pos, mask) -> cableFlow.merge(pos, mask, (x, y) -> x | y));
			supply += d.producerSupplyEstimate();
			demand += d.consumerDemandEstimate();
			// D10: a network that slept or was skipped reports nothing, not its last delivery.
			flows.add(new long[] {
					AnalyzerTotals.fresh(d.lastTickMoved(), d.lastTickAt(), now),
					AnalyzerTotals.fresh(d.lastTickToStorage(), d.lastTickAt(), now),
					AnalyzerTotals.fresh(d.lastTickFromStorage(), d.lastTickAt(), now)});

			// Partition this network's endpoints: storage sinks go to their own bucket (they render as
			// bridge nodes), everything else stays a producer/consumer.
			Set<BlockPos> netSinks = new LinkedHashSet<>();
			for (BlockPos pos : d.producerPositions()) {
				if (isStorageSink(level, pos)) {
					storageSinks.add(pos);
					netSinks.add(pos);
				} else {
					producers.add(pos);
				}
			}
			for (BlockPos pos : d.consumerPositions()) {
				if (isStorageSink(level, pos)) {
					storageSinks.add(pos);
					netSinks.add(pos);
				} else {
					consumers.add(pos);
				}
			}

			// Once the cap has refused a network nothing new is accepted — but the networks already
			// accepted are still collected. (D2: the queue used to be cleared at this point, dropping
			// every network that had passed the cap check but was not yet walked.)
			if (hitLimit) {
				continue;
			}
			// Bridge step: from each storage sink in this network, look through each face that is a live
			// energy port. D3: a face with no port, or with a port that neither takes nor gives (a Battery
			// Box side), is not a connection — a cable of another network merely touching it is no bridge.
			//
			// The sinks are walked in geometric order (MOD-313). It decides the order neighbour networks
			// are queued in, and therefore both the order their cables land in the result and — once
			// `maxNetworks` is reached — which of them was already collected and which is dropped. Sorted
			// here rather than left to the set: `netSinks` is fed from the network's own endpoint lists,
			// so without this the answer would follow whatever order those arrive in.
			List<BlockPos> orderedSinks = new ArrayList<>(netSinks);
			orderedSinks.sort(NetworkTopology.POSITION_ORDER);
			for (BlockPos sinkPos : orderedSinks) {
				for (Direction dir : Direction.values()) {
					if (!isLiveEnergyFace(level, sinkPos, dir)) {
						continue;
					}
					EnergyNetwork adj = NetworkManager.networkAt(level, sinkPos.relative(dir));
					if (adj == null || visited.contains(adj)) {
						continue;
					}
					if (visited.size() >= maxNetworks) {
						hitLimit = true;
						break;
					}
					visited.add(adj);
					queue.add(adj);
				}
				if (hitLimit) {
					break;
				}
			}
		}

		separateDualRole(producers, consumers, storageSinks);
		long[] moved = new long[flows.size()];
		long[] toStorage = new long[flows.size()];
		long[] fromStorage = new long[flows.size()];
		for (int i = 0; i < flows.size(); i++) {
			moved[i] = flows.get(i)[0];
			toStorage[i] = flows.get(i)[1];
			fromStorage[i] = flows.get(i)[2];
		}
		return new TraversalResult(cables, producers, consumers, storageSinks, supply, demand,
				AnalyzerTotals.deliveredAcross(moved, toStorage, fromStorage), hitLimit,
				endpointFaces(level, cables, producers, consumers, storageSinks, giving), cableFlow);
	}

	/**
	 * The endpoints putting EU into {@code net} right now (MOD-665): the ones that fed a cable on its last
	 * tick, provided that tick is recent. Not a dry-run extract: the network empties a generator's buffer
	 * every tick, and a reactor outlet is topped up from outside between ticks, so either reads empty at
	 * any moment it is looked at while feeding the line all the time.
	 */
	private static Set<BlockPos> givingEndpoints(ServerLevel level, EnergyNetwork net, EnergyNetworkDiagnostics d) {
		if (AnalyzerTotals.fresh(1, d.lastTickAt(), level.getGameTime()) == 0) {
			return Set.of();
		}
		return new LinkedHashSet<>(d.lastTickFed());
	}

	/**
	 * Gives every position exactly one role (D6): one that is both a producer and a consumer moves to
	 * {@code storage}, and anything already in {@code storage} leaves the other two.
	 */
	private static void separateDualRole(Set<BlockPos> producers, Set<BlockPos> consumers, Set<BlockPos> storage) {
		for (BlockPos pos : producers) {
			if (consumers.contains(pos)) {
				storage.add(pos);
			}
		}
		producers.removeAll(storage);
		consumers.removeAll(storage);
	}

	/**
	 * Whether {@code face} of the block at {@code pos} is a live energy port — the same test the
	 * network's own endpoint discovery applies ({@code EnergyTopologyCache}): a port exists there and it
	 * either takes or gives EU. A Battery Box's four side faces answer no.
	 */
	private static boolean isLiveEnergyFace(ServerLevel level, BlockPos pos, Direction face) {
		EnergyPort port = EnergyLookup.get().find(level, pos, face);
		return port != null && (port.supportsInsertion() || port.supportsExtraction());
	}

	/**
	 * For every endpoint, the faces through which it is really wired to a shown cable, packed by
	 * {@link NetworkTopology#packFaces}: all of them, and of those the ones that would take EU right now
	 * and the ones that give it now (see {@link #givingEndpoints}). A face counts as wired when the neighbour on
	 * that side is in {@code cables} and the face is a live energy port (D5); the live split is what lets
	 * the overlay run its sparks from what is giving to what is taking (MOD-665). The overlay draws an endpoint's "legs" only along these faces, so an
	 * inert face touching a cable, or two machines side by side, no longer get a wire the network does
	 * not have.
	 */
	private static Map<BlockPos, Integer> endpointFaces(ServerLevel level, Set<BlockPos> cables,
			Set<BlockPos> producers, Set<BlockPos> consumers, Set<BlockPos> storage, Set<BlockPos> giving) {
		Map<BlockPos, Integer> faces = new LinkedHashMap<>();
		for (Set<BlockPos> group : List.of(producers, consumers, storage)) {
			for (BlockPos pos : group) {
				int wired = 0;
				int take = 0;
				int emit = 0;
				for (Direction dir : Direction.values()) {
					if (!cables.contains(pos.relative(dir))) {
						continue;
					}
					EnergyPort port = EnergyLookup.get().find(level, pos, dir);
					if (port == null || !(port.supportsInsertion() || port.supportsExtraction())) {
						continue;
					}
					int bit = 1 << dir.ordinal();
					wired |= bit;
					// Live, not declared: a full store or a clogged machine takes nothing now, an empty store
					// or a panel at night gives nothing. Dry runs, so nothing moves.
					if (port.supportsInsertion()
							&& EnergyTransactions.get().simulate(sim -> port.insert(Long.MAX_VALUE, sim)) > 0) {
						take |= bit;
					}
					if (port.supportsExtraction() && giving.contains(pos)) {
						emit |= bit;
					}
				}
				faces.put(pos, NetworkTopology.packFaces(wired, take, emit));
			}
		}
		return Collections.unmodifiableMap(faces);
	}

	/**
	 * Mirror of {@link EnergyNetwork}'s private isStorageSink — true for BatteryBox-like blocks.
	 *
	 * <p>Tests against {@link EnergyBlockEntity}, not {@code MachineBlockEntity} (MOD-400): the
	 * predicate lives on the energy base, and narrowing the check to the machine subclass would
	 * answer "not a sink" for anything transport-shaped without ever asking it.
	 */
	private static boolean isStorageSink(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof EnergyBlockEntity be && be.isEnergyStorageSink();
	}

	/**
	 * Immutable result of a traversal. Position lists are de-duplicated across all visited networks and
	 * every position is in exactly one of the three endpoint sets.
	 *
	 * @param cables        union of cable positions across visited networks
	 * @param producers     producer endpoints that are NOT storage sinks
	 * @param consumers     consumer endpoints that are NOT storage sinks
	 * @param storageSinks  storage-sink endpoints (e.g. BatteryBox) — drawn as bridge nodes; in
	 *                      STOP_AT_STORAGE mode only a node that both feeds and draws from the network
	 * @param supply        summed producer supply estimate (EU/t) across visited networks
	 * @param demand        summed consumer demand estimate (EU/t) across visited networks
	 * @param moved         EU delivered on the last tick, 0 when that tick is stale (D10); across several
	 *                      networks a store's pass-through counts once ({@link AnalyzerTotals#deliveredAcross})
	 * @param hitLimit      true if the traversal stopped early because the network cap was reached
	 * @param endpointFaces per endpoint, the faces really wired to a shown cable (bit {@code 1 << ordinal})
	 */
	public record TraversalResult(Set<BlockPos> cables, Set<BlockPos> producers, Set<BlockPos> consumers,
			Set<BlockPos> storageSinks, long supply, long demand, long moved, boolean hitLimit,
			Map<BlockPos, Integer> endpointFaces, Map<BlockPos, Integer> cableFlow) {

		public int cableCount() {
			return cables.size();
		}

		public List<BlockPos> cableList() {
			return List.copyOf(cables);
		}

		public List<BlockPos> producerList() {
			return List.copyOf(producers);
		}

		public List<BlockPos> consumerList() {
			return List.copyOf(consumers);
		}

		public List<BlockPos> storageList() {
			return List.copyOf(storageSinks);
		}
	}
}
