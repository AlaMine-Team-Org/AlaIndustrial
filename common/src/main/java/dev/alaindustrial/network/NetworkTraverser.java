package dev.alaindustrial.network;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.EnergyNetworkDiagnostics;
import dev.alaindustrial.core.energy.NetworkManager;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
 * the clicked cable's network, it finds every storage-sink endpoint, looks at the 6 neighbours of
 * each, and — if a neighbour cable belongs to a not-yet-visited network — crosses into it and
 * repeats. The result is the union of cables, producers, consumers and storage sinks across all
 * connected networks, which the analyzer payload ships to the client as one picture.
 *
 * <p>{@link dev.alaindustrial.item.AnalyzerMode#STOP_AT_STORAGE} mode is just the single clicked
 * network (the original MOD-016 behaviour) — no bridging, no separate storage list.
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
			dev.alaindustrial.item.AnalyzerMode mode, int maxNetworks) {
		if (mode == dev.alaindustrial.item.AnalyzerMode.STOP_AT_STORAGE) {
			// Original MOD-016 behaviour: one network, no storage highlight, no bridging.
			return collectSingle(start);
		}
		return traverseThrough(level, start, Math.max(1, maxNetworks));
	}

	/** One-network snapshot for STOP_AT_STORAGE: cables + producers + consumers, sinks stay as consumers. */
	private static TraversalResult collectSingle(EnergyNetwork net) {
		EnergyNetworkDiagnostics d = net.diagnostics();
		Set<BlockPos> cables = new LinkedHashSet<>(net.cables());
		Set<BlockPos> producers = new LinkedHashSet<>(d.producerPositions());
		Set<BlockPos> consumers = new LinkedHashSet<>(d.consumerPositions());
		return new TraversalResult(
				cables,
				producers,
				consumers,
				Set.of(), // no separate storage list in stop-at-storage mode
				d.producerSupplyEstimate(),
				d.consumerDemandEstimate(),
				d.lastTickMoved(),
				false);
	}

	/**
	 * Multi-network BFS: seed with the clicked network, then cross through every storage sink into
	 * adjacent cable networks until none remain or the cap is hit.
	 */
	private static TraversalResult traverseThrough(ServerLevel level, EnergyNetwork start, int maxNetworks) {
		Set<EnergyNetwork> visited = new HashSet<>();
		Queue<EnergyNetwork> queue = new ArrayDeque<>();
		visited.add(start);
		queue.add(start);

		Set<BlockPos> cables = new LinkedHashSet<>();
		Set<BlockPos> producers = new LinkedHashSet<>();
		Set<BlockPos> consumers = new LinkedHashSet<>();
		Set<BlockPos> storageSinks = new LinkedHashSet<>();
		long supply = 0;
		long demand = 0;
		long moved = 0;
		boolean hitLimit = false;

		while (!queue.isEmpty()) {
			EnergyNetwork net = queue.poll();
			EnergyNetworkDiagnostics d = net.diagnostics();
			cables.addAll(net.cables());
			supply += d.producerSupplyEstimate();
			demand += d.consumerDemandEstimate();
			moved += d.lastTickMoved();

			// Partition this network's endpoints: storage sinks go to their own bucket (they render as
			// bridge nodes), everything else stays a producer/consumer.
			Set<BlockPos> netSinks = new HashSet<>();
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

			// Bridge step: from each storage sink in this network, peek at the 6 neighbours. A neighbour
			// cable that belongs to a not-yet-visited network is a bridge into it.
			for (BlockPos sinkPos : netSinks) {
				for (Direction dir : Direction.values()) {
					EnergyNetwork adj = NetworkManager.networkAt(level, sinkPos.relative(dir));
					if (adj != null && visited.add(adj)) {
						if (visited.size() > maxNetworks) {
							// Cap reached: roll back this addition so visited stays within the limit, and flag.
							visited.remove(adj);
							hitLimit = true;
							break;
						}
						queue.add(adj);
					}
				}
				if (hitLimit) {
					break;
				}
			}
			if (hitLimit) {
				queue.clear(); // stop expanding
			}
		}

		return new TraversalResult(cables, producers, consumers, storageSinks, supply, demand, moved, hitLimit);
	}

	/** Mirror of {@link EnergyNetwork}'s private isStorageSink — true for BatteryBox-like blocks. */
	private static boolean isStorageSink(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof MachineBlockEntity mbe && mbe.isEnergyStorageSink();
	}

	/**
	 * Immutable result of a traversal. Position lists are de-duplicated across all visited networks.
	 *
	 * @param cables       union of cable positions across visited networks
	 * @param producers    producer endpoints that are NOT storage sinks
	 * @param consumers    consumer endpoints that are NOT storage sinks
	 * @param storageSinks storage-sink endpoints (e.g. BatteryBox) — drawn as bridge nodes; empty in
	 *                     STOP_AT_STORAGE mode
	 * @param supply       summed producer supply estimate (EU/t) across visited networks
	 * @param demand       summed consumer demand estimate (EU/t) across visited networks
	 * @param moved        summed EU delivered on the last tick across visited networks
	 * @param hitLimit     true if the traversal stopped early because the network cap was reached
	 */
	public record TraversalResult(Set<BlockPos> cables, Set<BlockPos> producers, Set<BlockPos> consumers,
			Set<BlockPos> storageSinks, long supply, long demand, long moved, boolean hitLimit) {

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
