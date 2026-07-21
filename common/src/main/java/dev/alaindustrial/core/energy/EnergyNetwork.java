package dev.alaindustrial.core.energy;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * A logical energy network: a connected set of cable {@link BlockPos} in one {@link ServerLevel},
 * with cached endpoint lists (adjacent producers and consumers, discovered via {@link EnergyLookup} on
 * the cables' non-cable neighbours).
 *
 * <p>Transport runs once per {@link #tick()}: gather supply from producers and demand from consumers,
 * serve machine consumers before storage sinks (so a BatteryBox can't starve a working machine), split
 * each class's allocation proportionally to room (capped at the tier {@code packetCap}), round-robin
 * the pull across producers — never pulling a storage sink from itself (no self-churn) — then commit
 * in one transaction. Cable transport is a throughput limit, not an EU-destroying toll (MOD-009).
 *
 * <p>Networks are transient — never persisted — and are rebuilt from cable block entities by
 * {@link NetworkManager} as chunks load.
 *
 * <p><b>Architecture.</b> The work is split across three collaborators so each piece stays readable
 * and (where possible) unit-testable:
 * <ul>
 *   <li>{@link EnergyTopologyCache} — endpoint discovery + cable-distance BFS + propagation order
 *       (MC-coupled; rebuilt lazily on topology change).</li>
 *   <li>{@link EnergyLineDistributor} — the per-tick distribution kernel (MC-free pure helpers; the
 *       runtime math is the L1/pitest-covered extract path).</li>
 *   <li>this class — the public façade: holds the per-network wake-state ({@link #lineFull}, the
 *       round-robin cursor, telemetry) and orchestrates one {@link #tick()} pass over the other two.</li>
 * </ul>
 * EnergyShare/EnergyServe remain the home of the delivery/loss arithmetic as before.
 *
 * <p>MOD-022 Phase 2: runs entirely on the neutral energy abstraction — {@link EnergyPort} ports,
 * {@link EnergyLookup} for per-face resolution and {@link EnergyTransactions} for open/commit/simulate.
 * No loader energy API is referenced here; the loader-bound lookup + transaction live behind those SPIs.
 */
public final class EnergyNetwork {
	private final EnergyTopologyCache topology;

	/**
	 * Cached "every cable buffer is at capacity" flag for the producer-only wake gate. The only writer
	 * of a cable's buffer amount is this network's own {@link #tick()}, so once {@link #lineHasRoom()}
	 * returns false it stays false until the topology changes (cable added/removed, neighbour block
	 * placed/broken) — and every such change already routes through {@link #markDirty()} /
	 * {@link #addCable} / {@link #removeCable} / {@link #absorb}, which clear this flag. Without it,
	 * {@link #isAwake()} would re-scan every cable every tick on a sleeping generator-only network,
	 * spending O(cables) work to keep answering "do nothing" (the audit's #20).
	 */
	private boolean lineFull;
	/**
	 * Round-robin rotation offset for the distributor's pull. Advanced once per {@link #tick()} so the
	 * pull does not always start at the same supply. NOTE (MOD-070): on the consumer→line path the supply
	 * list is the touched <em>cable buffers</em>, not the network's producers, so this rotates over cables
	 * there; it only rotates over real producers on the storage-sink paired path. Bounded by the live
	 * producer count, and every use site re-applies {@code % list.size()}, so it never indexes out of range.
	 */
	private int producerCursor;
	/** EU actually delivered by the most recent {@link #tick()} (0 if asleep/never ticked). */
	private long lastTickMoved;

	public EnergyNetwork(ServerLevel level) {
		this.topology = new EnergyTopologyCache(level);
	}

	public ServerLevel level() {
		return topology.level();
	}

	public Set<BlockPos> cables() {
		return topology.cables();
	}

	public int size() {
		return topology.size();
	}

	public boolean contains(BlockPos pos) {
		return topology.contains(pos);
	}

	public void addCable(BlockPos pos) {
		topology.addCable(pos);
		lineFull = false;
	}

	public void removeCable(BlockPos pos) {
		topology.removeCable(pos);
		lineFull = false;
	}

	/** Absorb another network's cables into this one (union-find merge). */
	public void absorb(EnergyNetwork other) {
		topology.absorb(other.topology);
		lineFull = false;
	}

	/** Force an endpoint recache on the next tick (neighbour changed, cable added/removed). */
	public void markDirty() {
		topology.markDirty();
		lineFull = false;
	}

	public boolean isEmpty() {
		return topology.isEmpty();
	}

	/**
	 * Awake = has a producer and there is work to do. With a consumer, that work is delivery. Even
	 * WITHOUT a consumer (MOD-070) a <em>generator</em> still fills the cable line up to its buffer
	 * capacity — a source connected to wires charges them so the buffer is visible and retained — so a
	 * generator-only network stays awake while any cable still has room, then sleeps once the line is
	 * full. A network whose only sources are <em>storage sources</em> (a dual-role BatteryBox) with no
	 * consumer sleeps immediately: storage discharges into the line only to cover a machine deficit
	 * ({@link EnergyLineDistributor#chargeAndPropagateLine}), so with no machine it would charge nothing
	 * — keeping it awake would spin a no-op tick (and an O(cables) {@link #lineHasRoom} scan) forever.
	 * A consumer-only or empty network can move nothing and is asleep until its neighbours change.
	 */
	public boolean isAwake() {
		List<EnergyTopologyCache.Endpoint> producers = topology.producers();
		if (producers.isEmpty()) {
			return false;
		}
		if (!topology.consumers().isEmpty()) {
			return true;
		}
		// No consumer: only a generator (not a storage source) actually fills the line, and only while
		// a cable still has room. Otherwise sleep — a storage-only source with no machine never charges.
		// The line-full check is memoized in `lineFull` (reset on any topology change, refreshed at the
		// end of tick()); without that a sleeping generator-only network re-scanned every cable every
		// tick to keep deciding "do nothing".
		if (!topology.hasGenerator(this::isStorageSink)) {
			return false;
		}
		if (lineFull) {
			return false;
		}
		return lineHasRoom();
	}

	/** True if any cable in this network still has free buffer space to fill (producer-only wake gate). */
	private boolean lineHasRoom() {
		for (BlockPos pos : topology.cables()) {
			EnergyBuffer buf = cableBufferAt(pos);
			if (buf != null && buf.amount < buf.getCapacity()) {
				return true;
			}
		}
		return false;
	}

	/** EU actually delivered by the most recent {@link #tick()} (0 if never ticked or asleep). */
	public long lastTickMoved() {
		return lastTickMoved;
	}

	/**
	 * Read-only diagnostics view for the Network Analyzer (MOD-016 / MOD-047) and tests. Returns a
	 * lightweight wrapper that exposes positions, supply/demand estimates and last-tick telemetry
	 * without polluting the network's tick-orchestrator API — see {@link EnergyNetworkDiagnostics}.
	 */
	public EnergyNetworkDiagnostics diagnostics() {
		return new EnergyNetworkDiagnostics(this);
	}

	/** Positions of this network's producer endpoints — package-private, used by {@link EnergyNetworkDiagnostics}. */
	List<BlockPos> producerPositions() {
		return topology.producers().stream().map(EnergyTopologyCache.Endpoint::pos).toList();
	}

	/** Positions of this network's consumer endpoints — package-private, used by {@link EnergyNetworkDiagnostics}. */
	List<BlockPos> consumerPositions() {
		return topology.consumers().stream().map(EnergyTopologyCache.Endpoint::pos).toList();
	}

	/**
	 * Dry-run sum of what producers could extract this instant (no commit) — the network's
	 * potential supply, not what actually moves once consumer demand and the tier packet cap are
	 * applied in {@link #tick()}. Package-private, surfaced via {@link EnergyNetworkDiagnostics}.
	 */
	long producerSupplyEstimate() {
		return dryRunSum(topology.producers(), topology.consumers(), true);
	}

	/**
	 * Dry-run sum of what consumers could accept this instant (no commit) — the network's potential
	 * demand. Package-private, surfaced via {@link EnergyNetworkDiagnostics}.
	 */
	long consumerDemandEstimate() {
		return dryRunSum(topology.consumers(), topology.producers(), false);
	}

	/**
	 * Shared dry-run helper for the two estimate methods above: sums {@code extract}/{@code insert}
	 * (no commit) across {@code from}, skipping any endpoint with no counterpart at a *different*
	 * position in {@code against} — mirroring the distributor's "no self-churn" rule (a storage node
	 * co-located as both producer and consumer can't trade with itself, so on its own it contributes
	 * nothing deliverable). Without this, a lone BatteryBox would report nonzero supply *and* nonzero
	 * demand even though {@link #tick()} can never move EU between it and itself.
	 */
	private long dryRunSum(List<EnergyTopologyCache.Endpoint> from, List<EnergyTopologyCache.Endpoint> against,
			boolean extracting) {
		return EnergyTransactions.get().simulate(sim -> {
			long total = 0;
			for (EnergyTopologyCache.Endpoint ep : from) {
				if (!hasOtherPosition(against, ep.pos())) {
					continue;
				}
				EnergyPort st = storageAt(ep);
				if (st == null) {
					continue;
				}
				if (extracting) {
					if (st.supportsExtraction()) {
						total += st.extract(Long.MAX_VALUE, sim);
					}
				} else if (st.supportsInsertion()) {
					total += st.insert(Long.MAX_VALUE, sim);
				}
			}
			return total;
		});
	}

	/** True if {@code endpoints} contains at least one position other than {@code pos}. */
	private static boolean hasOtherPosition(List<EnergyTopologyCache.Endpoint> endpoints, BlockPos pos) {
		for (EnergyTopologyCache.Endpoint ep : endpoints) {
			if (!ep.pos().equals(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Resolve a live storage for an endpoint, or null if it's gone (block changed/unloaded). */
	private EnergyPort storageAt(EnergyTopologyCache.Endpoint ep) {
		return EnergyLookup.get().find(topology.level(), ep.pos(), ep.side());
	}

	/** The live cable buffer at {@code pos}, or null if the block there is no longer a cable. */
	private EnergyBuffer cableBufferAt(BlockPos pos) {
		return topology.level().getBlockEntity(pos) instanceof CableBlockEntity cable ? cable.getEnergyStorage() : null;
	}

	/** True if the block at {@code pos} is a storage sink (e.g. BatteryBox) — served after machines. */
	private boolean isStorageSink(BlockPos pos) {
		return topology.level().getBlockEntity(pos) instanceof MachineBlockEntity mbe && mbe.isEnergyStorageSink();
	}

	/**
	 * Run one distribution pass. Returns the EU actually delivered to consumers this tick (0 when
	 * nothing moved). Safe to call on an asleep network (returns 0). All movement commits in a single
	 * outer transaction. The returned amount feeds the {@link NetworkManager} telemetry counters.
	 *
	 * <p>Delegates the per-tick work to {@link EnergyLineDistributor}; see that class for the
	 * segment-to-segment flow contract (MOD-070) and the producer/storage partitioning.
	 */
	public long tick() {
		List<EnergyTopologyCache.Endpoint> producers = topology.producers();
		if (producers.isEmpty()) {
			// No source at all — nothing to serve and nothing to charge the line with.
			lastTickMoved = 0L;
			return 0L;
		}
		List<EnergyTopologyCache.Endpoint> consumers = topology.consumers();

		// The cable-graph packet cap is the network's highest cable tier (so a future HV cable segment
		// lifts the whole line to HV throughput, while a network of all-LV cables stays at 32 EU/t — the
		// historical behaviour before this was derived from the cables rather than hardcoded as LV).
		long packetCap = topology.maxCableTier().maxVoltage();

		// --- dry-run supply, partitioned by source priority (MOD-070): pure generators vs storage
		// sources (dual-role BatteryBox with a cabled OUT face). Generators feed the line and charge
		// storage; storage only discharges into the line as backup (machine deficit), and NEVER sources
		// for another storage sink — so batteries can't wash energy through the wires or into each other. ---
		long genSupply = 0;
		List<EnergyLineDistributor.LiveProducer> generators = new ArrayList<>(producers.size());
		List<EnergyLineDistributor.LiveProducer> storageSources = new ArrayList<>();
		for (EnergyTopologyCache.Endpoint ep : producers) {
			EnergyPort st = storageAt(ep);
			if (st == null || !st.supportsExtraction()) {
				continue;
			}
			if (isStorageSink(ep.pos())) {
				storageSources.add(new EnergyLineDistributor.LiveProducer(ep.pos(), st));
			} else {
				generators.add(new EnergyLineDistributor.LiveProducer(ep.pos(), st));
				genSupply += EnergyTransactions.get().simulate(sim -> st.extract(Long.MAX_VALUE, sim));
			}
		}
		int liveProducerCount = generators.size() + storageSources.size();
		if (liveProducerCount == 0) {
			// No live producer this tick; the line may still hold energy, but with no producers the
			// network is asleep-shaped — nothing to serve deterministically. (Retained line energy is
			// delivered once a producer returns and the network wakes.)
			lastTickMoved = 0L;
			return 0L;
		}

		// --- dry-run demand (insertable), partitioned: pure machines (line path) vs storage sinks
		// (paired path). Machines get segment-to-segment inertia; sinks keep the direct, self-churn-safe
		// producer→sink route. ---
		List<EnergyLineDistributor.LiveConsumer> machines = new ArrayList<>(consumers.size());
		List<EnergyLineDistributor.LiveConsumer> sinks = new ArrayList<>(consumers.size());
		long machineDemand = 0;
		for (EnergyTopologyCache.Endpoint ep : consumers) {
			EnergyPort st = storageAt(ep);
			if (st == null || !st.supportsInsertion()) {
				continue;
			}
			long r = EnergyTransactions.get().simulate(sim -> st.insert(Long.MAX_VALUE, sim));
			if (r <= 0) {
				continue;
			}
			EnergyLineDistributor.LiveConsumer c = new EnergyLineDistributor.LiveConsumer(ep.pos(), st, r);
			if (isStorageSink(ep.pos())) {
				sinks.add(c);
			} else {
				machines.add(c);
				machineDemand += r;
			}
		}
		// Note: no early-out when machines and sinks are both empty. A producer-only network (a source
		// wired to cables with no consumer) still runs the charge stage below to fill the line to its
		// buffer capacity — the wire holds and shows the energy even with nowhere to deliver it (MOD-070).

		EnergyLineDistributor distributor = new EnergyLineDistributor(
				topology, this::cableBufferAt,
				topology::consumerDistance, topology::cableDistanceOrNull, topology.propagationOrder());
		long[] movedEu = {0L};
		long finalMachineDemand = machineDemand;
		long finalGenSupply = genSupply;
		EnergyTransactions.get().runCommitting(tx -> {
			// Serve ALL consumers from the line — machines first (MOD-009 priority), then storage sinks.
			// Both drain the cable buffers they touch, so a cable between a source and ANY consumer
			// (a machine OR a BatteryBox) genuinely carries and displays the energy in transit, instead
			// of the storage charge bypassing the wires.
			movedEu[0] += distributor.serveConsumersFromLine(machines, packetCap, tx, producerCursor);
			movedEu[0] += distributor.serveConsumersFromLine(sinks, packetCap, tx, producerCursor);
			// Replenish the line for next tick: generators fill it freely (inertia + a visible buffer);
			// a storage source discharges into the line ONLY to cover the machine demand generators fall
			// short of (backup power), never to hoard buffers or wash into another battery. With no
			// generator present, storage discharges nothing, so two batteries can't drain each other.
			distributor.chargeAndPropagateLine(generators, storageSources, finalMachineDemand, finalGenSupply, packetCap, tx);
		});
		// Advance the producer cursor so the next tick starts at a different producer, bounded by the
		// live producer count (its % use sites re-applies the modulus against their own list size).
		producerCursor = (producerCursor + 1) % liveProducerCount;
		lastTickMoved = movedEu[0];
		// Refresh the cached line-full flag so the next isAwake() on a producer-only network can skip
		// the O(cables) scan. Only meaningful on the no-consumer path (a consumer keeps the network
		// awake unconditionally), but the cost is one scan that has already happened inside this tick's
		// line fill, so we re-derive it cheaply rather than tracking it through every buffer write.
		lineFull = !lineHasRoom();
		return movedEu[0];
	}
}
