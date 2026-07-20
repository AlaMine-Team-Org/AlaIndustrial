package dev.alaindustrial.core;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
 * <p>MOD-022 Phase 2: runs entirely on the neutral energy abstraction — {@link EnergyPort} ports,
 * {@link EnergyLookup} for per-face resolution and {@link EnergyTransactions} for open/commit/simulate.
 * No loader energy API is referenced here; the loader-bound lookup + transaction live behind those SPIs.
 */
public final class EnergyNetwork {
	/** A cached neighbour storage endpoint: the position it lives at and the side the cable touches it. */
	private record Endpoint(BlockPos pos, Direction side) {
	}

	/** Cached once — {@link Direction#values()} clones its array on every call (hot-path GC hygiene). */
	private static final Direction[] DIRECTIONS = Direction.values();

	private final ServerLevel level;
	private final Set<BlockPos> cables = new HashSet<>();

	/** Cached endpoints, rebuilt on {@link #markDirty()} / any topology change. */
	private final List<Endpoint> producers = new ArrayList<>();
	private final List<Endpoint> consumers = new ArrayList<>();
	/** Cable-distance from each consumer position to its nearest producer, for per-consumer loss (MOD-021). */
	private final Map<BlockPos, Integer> consumerDistance = new HashMap<>();
	/**
	 * Cable-distance from each cable position to the nearest producer (BFS over the cable graph, MOD-070).
	 * Drives the per-tick line propagation ordering ({@link #propagateLineOneHop}: energy flows from
	 * lower-distance cables to higher-distance ones, i.e. away from producers, toward consumers).
	 */
	private final Map<BlockPos, Integer> cableDistance = new HashMap<>();
	/**
	 * Cables in descending {@link #cableDistance} order — the fixed per-tick propagation sweep order
	 * (MOD-070). Rebuilt only on topology change (in {@link #computeConsumerDistances}); iterating it
	 * avoids re-sorting {@code cableDistance} every tick (a hot-path allocation + boxing the audit flagged).
	 */
	private final List<BlockPos> propagationOrder = new ArrayList<>();
	private boolean endpointsDirty = true;
	/**
	 * Round-robin rotation offset for {@link #pullRoundRobin}. Advanced once per {@link #tick()} so the
	 * pull does not always start at the same supply. NOTE (MOD-070): on the consumer→line path the supply
	 * list is the touched <em>cable buffers</em>, not the network's producers, so this rotates over cables
	 * there; it only rotates over real producers on the storage-sink paired path. Bounded by the live
	 * producer count, and every use site re-applies {@code % list.size()}, so it never indexes out of range.
	 */
	private int producerCursor;
	/** EU actually delivered by the most recent {@link #tick()} (0 if asleep/never ticked). */
	private long lastTickMoved;

	public EnergyNetwork(ServerLevel level) {
		this.level = level;
	}

	public ServerLevel level() {
		return level;
	}

	public Set<BlockPos> cables() {
		return cables;
	}

	public int size() {
		return cables.size();
	}

	public boolean contains(BlockPos pos) {
		return cables.contains(pos);
	}

	public void addCable(BlockPos pos) {
		if (cables.add(pos.immutable())) {
			endpointsDirty = true;
		}
	}

	public void removeCable(BlockPos pos) {
		if (cables.remove(pos)) {
			endpointsDirty = true;
		}
	}

	/** Absorb another network's cables into this one (union-find merge). */
	public void absorb(EnergyNetwork other) {
		cables.addAll(other.cables);
		endpointsDirty = true;
	}

	/** Force an endpoint recache on the next tick (neighbour changed, cable added/removed). */
	public void markDirty() {
		endpointsDirty = true;
	}

	public boolean isEmpty() {
		return cables.isEmpty();
	}

	/**
	 * Awake = has a producer and there is work to do. With a consumer, that work is delivery. Even
	 * WITHOUT a consumer (MOD-070) a <em>generator</em> still fills the cable line up to its buffer
	 * capacity — a source connected to wires charges them so the buffer is visible and retained — so a
	 * generator-only network stays awake while any cable still has room, then sleeps once the line is
	 * full. A network whose only sources are <em>storage sources</em> (a dual-role BatteryBox) with no
	 * consumer sleeps immediately: storage discharges into the line only to cover a machine deficit
	 * ({@link #chargeAndPropagateLine}), so with no machine it would charge nothing — keeping it awake
	 * would spin a no-op tick (and an O(cables) {@link #lineHasRoom} scan) forever. A consumer-only or
	 * empty network can move nothing and is asleep until its neighbours change.
	 */
	public boolean isAwake() {
		if (endpointsDirty) {
			refreshEndpoints();
		}
		if (producers.isEmpty()) {
			return false;
		}
		if (!consumers.isEmpty()) {
			return true;
		}
		// No consumer: only a generator (not a storage source) actually fills the line, and only while
		// a cable still has room. Otherwise sleep — a storage-only source with no machine never charges.
		return hasGenerator() && lineHasRoom();
	}

	/** True if this network has at least one non-storage-sink producer (a generator that fills the line). */
	private boolean hasGenerator() {
		for (Endpoint ep : producers) {
			if (!isStorageSink(ep.pos())) {
				return true;
			}
		}
		return false;
	}

	/** True if any cable in this network still has free buffer space to fill (producer-only wake gate). */
	private boolean lineHasRoom() {
		for (BlockPos pos : cables) {
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

	/** Positions of this network's producer endpoints (read-only introspection, e.g. MOD-016). */
	public List<BlockPos> producerPositions() {
		if (endpointsDirty) {
			refreshEndpoints();
		}
		return producers.stream().map(Endpoint::pos).toList();
	}

	/** Positions of this network's consumer endpoints (read-only introspection, e.g. MOD-016). */
	public List<BlockPos> consumerPositions() {
		if (endpointsDirty) {
			refreshEndpoints();
		}
		return consumers.stream().map(Endpoint::pos).toList();
	}

	/**
	 * Dry-run sum of what producers could extract this instant (no commit) — the network's
	 * potential supply, not what actually moves once consumer demand and the tier packet cap are
	 * applied in {@link #tick()}. For diagnostics only (MOD-016).
	 */
	public long producerSupplyEstimate() {
		if (endpointsDirty) {
			refreshEndpoints();
		}
		return dryRunSum(producers, consumers, true);
	}

	/**
	 * Dry-run sum of what consumers could accept this instant (no commit) — the network's
	 * potential demand. For diagnostics only (MOD-016).
	 */
	public long consumerDemandEstimate() {
		if (endpointsDirty) {
			refreshEndpoints();
		}
		return dryRunSum(consumers, producers, false);
	}

	/**
	 * Shared dry-run helper for the two estimate methods above: sums {@code extract}/{@code insert}
	 * (no commit) across {@code from}, skipping any endpoint with no counterpart at a *different*
	 * position in {@code against} — mirroring {@link #pullRoundRobin}'s "no self-churn" rule (a
	 * storage node co-located as both producer and consumer can't trade with itself, so on its own
	 * it contributes nothing deliverable). Without this, a lone BatteryBox would report nonzero supply
	 * *and* nonzero demand even though {@link #tick()} can never move EU between it and itself.
	 */
	private long dryRunSum(List<Endpoint> from, List<Endpoint> against, boolean extracting) {
		return EnergyTransactions.get().simulate(sim -> {
			long total = 0;
			for (Endpoint ep : from) {
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
	private static boolean hasOtherPosition(List<Endpoint> endpoints, BlockPos pos) {
		for (Endpoint ep : endpoints) {
			if (!ep.pos().equals(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Rebuild the cached producer/consumer endpoint lists from the cables' non-cable neighbours. */
	private void refreshEndpoints() {
		producers.clear();
		consumers.clear();
		Set<BlockPos> seenProducer = new HashSet<>();
		Set<BlockPos> seenConsumer = new HashSet<>();
		EnergyLookup lookup = EnergyLookup.get();
		for (BlockPos cable : cables) {
			for (Direction dir : DIRECTIONS) {
				BlockPos np = cable.relative(dir);
				if (cables.contains(np)) {
					continue; // cable-to-cable link, not an endpoint
				}
				EnergyPort storage = lookup.find(level, np, dir.getOpposite());
				if (storage == null) {
					continue;
				}
				if (storage.supportsExtraction() && seenProducer.add(np)) {
					producers.add(new Endpoint(np, dir.getOpposite()));
				}
				if (storage.supportsInsertion() && seenConsumer.add(np)) {
					consumers.add(new Endpoint(np, dir.getOpposite()));
				}
			}
		}
		endpointsDirty = false;
		// producerCursor is a rotation offset reduced modulo liveProducers.size() at every use site
		// (the tick() advance and pullRoundRobin); this clamp only keeps it bounded after the cached
		// producer set shrinks. Never index producers/liveProducers with it without re-applying the mod.
		if (producers.isEmpty()) {
			producerCursor = 0;
		} else {
			producerCursor %= producers.size();
		}
		computeConsumerDistances();
	}

	/**
	 * Multi-source BFS over the cable graph: seed every cable touching a producer at distance 1, flood
	 * the connected component, then record each consumer's distance as the minimum over its adjacent
	 * cables. Feeds the per-consumer cable loss in {@link #serveClass} (MOD-021). Distance is to the
	 * <em>nearest</em> producer — an intentional approximation, since {@link #pullRoundRobin} may draw
	 * from any producer; for the common single-producer network it is exact.
	 */
	private void computeConsumerDistances() {
		consumerDistance.clear();
		cableDistance.clear();
		propagationOrder.clear();
		// Cable distances are seeded from producers, so they (and the propagation order) are computed
		// whenever there is a source — even with no consumer, so a producer-only line still fills fully
		// (propagation spreads the charge outward from the source, not just into producer-adjacent cables).
		if (producers.isEmpty()) {
			return;
		}
		Map<BlockPos, Integer> cableDist = cableDistance;
		Queue<BlockPos> queue = new ArrayDeque<>();
		for (Endpoint producer : producers) {
			for (Direction dir : DIRECTIONS) {
				BlockPos cable = producer.pos().relative(dir);
				if (cables.contains(cable) && cableDist.putIfAbsent(cable, 1) == null) {
					queue.add(cable);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos cur = queue.poll();
			int next = cableDist.get(cur) + 1;
			for (Direction dir : DIRECTIONS) {
				BlockPos np = cur.relative(dir);
				if (cables.contains(np) && cableDist.putIfAbsent(np, next) == null) {
					queue.add(np);
				}
			}
		}
		for (Endpoint consumer : consumers) {
			int best = 0;
			for (Direction dir : DIRECTIONS) {
				Integer d = cableDist.get(consumer.pos().relative(dir));
				if (d != null && (best == 0 || d < best)) {
					best = d;
				}
			}
			if (best > 0) {
				consumerDistance.put(consumer.pos(), best);
			}
		}
		// Cache the descending-distance sweep order once per topology change (MOD-070): propagation
		// pushes energy from lower- to higher-distance cables, so processing far cables first makes each
		// unit advance exactly one hop per tick. Avoids re-sorting cableDistance every tick.
		propagationOrder.addAll(cableDist.keySet());
		propagationOrder.sort((a, b) -> Integer.compare(cableDist.get(b), cableDist.get(a)));
	}

	/** Resolve a live storage for an endpoint, or null if it's gone (block changed/unloaded). */
	private EnergyPort storageAt(Endpoint ep) {
		return EnergyLookup.get().find(level, ep.pos(), ep.side());
	}

	/**
	 * Run one distribution pass. Returns the EU actually delivered to consumers this tick (0 when
	 * nothing moved). Safe to call on an asleep network (returns 0). All movement commits in a single
	 * outer transaction. The returned amount feeds the {@link NetworkManager} telemetry counters.
	 *
	 * <p><b>Segment-to-segment flow (MOD-070).</b> Cables are no longer bypassed: each cable carries a
	 * small live buffer ({@link Config#cableBuffer}) and <em>all</em> transfer flows through them with
	 * inertia rather than teleporting producer→consumer, so any active cable genuinely holds (and
	 * displays) the energy passing through it. The pass has two stages, both in one commit:
	 * <ol>
	 *   <li><b>Serve consumers from the line</b> — machines first (MOD-009 priority), then storage sinks
	 *       (BatteryBox). Each pulls from the cable buffers it touches, with the per-consumer MOD-021
	 *       distance loss ({@link #serveClass} over the touched cable buffers as the supply, so the loss
	 *       model is numerically unchanged). Storage routes through the line too, so a source→cable→
	 *       BatteryBox link fills the cables in transit.</li>
	 *   <li><b>Replenish the line</b> — propagate existing line energy one hop toward consumers
	 *       ({@link #propagateLineOneHop}), then charge the source-adjacent cables. <em>Generators</em>
	 *       fill the line freely (free energy → inertia + a visible buffer); a dual-role <em>storage
	 *       source</em> (BatteryBox with a cabled OUT face) discharges into the line only to cover the
	 *       machine demand generators fall short of (backup power) — so when generators suffice no battery
	 *       washes energy into the wires, and with no generator present storage discharges nothing, so two
	 *       batteries can't drain each other.</li>
	 * </ol>
	 * On a line break the source-side cables keep whatever they buffered (the split-off half has no
	 * consumer, so it goes to sleep and its buffers are simply retained + persisted).
	 */
	public long tick() {
		if (endpointsDirty) {
			refreshEndpoints();
		}
		if (producers.isEmpty()) {
			// No source at all — nothing to serve and nothing to charge the line with.
			lastTickMoved = 0L;
			return 0L;
		}

		long packetCap = EnergyTier.LV.maxVoltage();

		// --- dry-run supply, partitioned by source priority (MOD-070): pure generators vs storage
		// sources (dual-role BatteryBox with a cabled OUT face). Generators feed the line and charge
		// storage; storage only discharges into the line as backup (machine deficit), and NEVER sources
		// for another storage sink — so batteries can't wash energy through the wires or into each other. ---
		long genSupply = 0;
		List<LiveProducer> generators = new ArrayList<>(producers.size());
		List<LiveProducer> storageSources = new ArrayList<>();
		for (Endpoint ep : producers) {
			EnergyPort st = storageAt(ep);
			if (st == null || !st.supportsExtraction()) {
				continue;
			}
			if (isStorageSink(ep.pos())) {
				storageSources.add(new LiveProducer(ep.pos(), st));
			} else {
				generators.add(new LiveProducer(ep.pos(), st));
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
		List<LiveConsumer> machines = new ArrayList<>(consumers.size());
		List<LiveConsumer> sinks = new ArrayList<>(consumers.size());
		long machineDemand = 0;
		for (Endpoint ep : consumers) {
			EnergyPort st = storageAt(ep);
			if (st == null || !st.supportsInsertion()) {
				continue;
			}
			long r = EnergyTransactions.get().simulate(sim -> st.insert(Long.MAX_VALUE, sim));
			if (r <= 0) {
				continue;
			}
			LiveConsumer c = new LiveConsumer(ep.pos(), st, r);
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

		long[] movedEu = {0L};
		long finalMachineDemand = machineDemand;
		long finalGenSupply = genSupply;
		EnergyTransactions.get().runCommitting(tx -> {
			// Serve ALL consumers from the line — machines first (MOD-009 priority), then storage sinks.
			// Both drain the cable buffers they touch, so a cable between a source and ANY consumer
			// (a machine OR a BatteryBox) genuinely carries and displays the energy in transit, instead
			// of the storage charge bypassing the wires.
			movedEu[0] += serveConsumersFromLine(machines, packetCap, tx);
			movedEu[0] += serveConsumersFromLine(sinks, packetCap, tx);
			// Replenish the line for next tick: generators fill it freely (inertia + a visible buffer);
			// a storage source discharges into the line ONLY to cover the machine demand generators fall
			// short of (backup power), never to hoard buffers or wash into another battery. With no
			// generator present, storage discharges nothing, so two batteries can't drain each other.
			chargeAndPropagateLine(generators, storageSources, finalMachineDemand, finalGenSupply, packetCap, tx);
		});
		// Advance the producer cursor so the next tick starts at a different producer, bounded by the
		// live producer count (its % use sites re-apply the modulus against their own list size).
		producerCursor = (producerCursor + 1) % liveProducerCount;
		lastTickMoved = movedEu[0];
		return movedEu[0];
	}

	/**
	 * Serve a class of consumers (machines, or storage sinks) from the network's cable buffers (the
	 * "line"). Reuses {@link #serveClass} with the live cable buffers as the supply pool, so the
	 * proportional split, tier packet cap and — crucially — the per-consumer MOD-021 distance loss are
	 * exactly the same math as the old direct path; only the <em>source</em> of the EU is the line.
	 * Returns the EU delivered. Both machines and storage sinks route through here (MOD-070): all
	 * transfer flows through the wires, so any active cable shows its buffered energy.
	 */
	private long serveConsumersFromLine(List<LiveConsumer> consumers, long packetCap, EnergyPort.Txn tx) {
		if (consumers.isEmpty()) {
			return 0L;
		}
		// Locality: a consumer draws only from the cable segments it physically touches, NOT the whole
		// network. This is what forces energy to propagate along the line to reach a distant consumer
		// (so intermediate cables genuinely buffer it) instead of teleporting from anywhere in the graph.
		// serveClass still splits a shared touched-cable pool proportionally, so two consumers on one cable
		// share it fairly, and it applies the unchanged per-consumer distance loss (MOD-021).
		Set<BlockPos> touched = new HashSet<>();
		for (LiveConsumer m : consumers) {
			for (Direction dir : DIRECTIONS) {
				BlockPos np = m.pos().relative(dir);
				if (cables.contains(np)) {
					touched.add(np);
				}
			}
		}
		List<LiveProducer> lineSupply = new ArrayList<>();
		long[] lineTotal = {0L};
		for (BlockPos pos : touched) {
			EnergyBuffer buf = cableBufferAt(pos);
			if (buf != null && buf.amount > 0) {
				lineSupply.add(new LiveProducer(pos, buf));
				lineTotal[0] += buf.amount;
			}
		}
		if (lineSupply.isEmpty()) {
			return 0L;
		}
		return serveClass(consumers, lineSupply, lineTotal, packetCap, tx);
	}

	/**
	 * Stage 2 of {@link #tick()}: push existing line energy one hop toward consumers, then charge the
	 * producer-adjacent cables from producers. Returns the EU actually drawn from producers into the
	 * line (so {@link #tick()} can dock it from the supply left for storage sinks). Loss-free between
	 * cables — the resistive cost is charged once, per consumer, on delivery (stage 1); charging it
	 * again per hop would break the MOD-021 numbers ({@code floor(0.02·1)=0} per hop ≠
	 * {@code floor(0.02·32·10)=6}).
	 */
	private long chargeAndPropagateLine(List<LiveProducer> generators, List<LiveProducer> storageSources,
			long machineDemand, long genSupply, long packetCap, EnergyPort.Txn tx) {
		propagateLineOneHop(packetCap, tx);
		// Generators fill the line freely (free energy → inertia); only their draw is docked from the
		// supply left for storage sinks.
		long genDrawn = chargeLineFrom(generators, packetCap, Long.MAX_VALUE, tx);
		// Storage discharges into the line ONLY to cover the machine demand generators fall short of
		// (backup power). When generators already cover it, storageBudget is 0 and no battery bleeds into
		// the wires — this closes the dual-role wash the audit flagged.
		long storageBudget = Math.max(0L, machineDemand - genSupply);
		if (storageBudget > 0 && !storageSources.isEmpty()) {
			chargeLineFrom(storageSources, packetCap, storageBudget, tx);
		}
		return genDrawn;
	}

	/**
	 * Charge the source-adjacent cables from {@code sources}. Each source injects at most {@code packetCap}
	 * EU total this tick (the tier throughput limit — NOT {@code packetCap × adjacent-cables}, which the
	 * audit flagged as multiplying the per-source cap), and the whole call stops once it has drawn
	 * {@code totalBudget} EU. Returns the EU actually drawn. Loss-free (the resistive cost is charged once
	 * on delivery, stage 1 / stage 3).
	 */
	private long chargeLineFrom(List<LiveProducer> sources, long packetCap, long totalBudget,
			EnergyPort.Txn tx) {
		long drawn = 0;
		for (LiveProducer prod : sources) {
			if (drawn >= totalBudget) {
				break;
			}
			long fromThis = 0; // per-source throughput this tick, capped at packetCap
			for (Direction dir : DIRECTIONS) {
				if (fromThis >= packetCap || drawn >= totalBudget) {
					break;
				}
				BlockPos np = prod.pos().relative(dir);
				if (!cables.contains(np)) {
					continue;
				}
				EnergyBuffer buf = cableBufferAt(np);
				if (buf == null) {
					continue;
				}
				long room = buf.getCapacity() - buf.amount;
				if (room <= 0) {
					continue;
				}
				long want = Math.min(Math.min(room, packetCap - fromThis), totalBudget - drawn);
				long got = prod.storage().extract(want, tx);
				if (got > 0) {
					buf.insert(got, tx);
					fromThis += got;
					drawn += got;
				}
			}
		}
		return drawn;
	}

	/**
	 * Move line energy one cable-hop from lower-distance cables to higher-distance ones (away from
	 * producers, toward consumers). Processing cables in <em>descending</em> distance and pulling from
	 * strictly-upstream neighbours makes each unit advance at most one hop per tick — the "fill front"
	 * that gives the line its inertia. Bounded by the tier throughput ({@code packetCap}) and each
	 * cable's free space.
	 */
	private void propagateLineOneHop(long packetCap, EnergyPort.Txn tx) {
		if (propagationOrder.isEmpty()) {
			return;
		}
		for (BlockPos pos : propagationOrder) {
			EnergyBuffer to = cableBufferAt(pos);
			if (to == null) {
				continue;
			}
			int dTo = cableDistance.get(pos);
			for (Direction dir : DIRECTIONS) {
				long free = to.getCapacity() - to.amount;
				if (free <= 0) {
					break;
				}
				BlockPos np = pos.relative(dir);
				Integer dFrom = cableDistance.get(np);
				if (dFrom == null || dFrom >= dTo) {
					continue; // only pull from strictly upstream (closer to a producer)
				}
				EnergyBuffer from = cableBufferAt(np);
				if (from == null || from.amount <= 0) {
					continue;
				}
				long move = Math.min(Math.min(free, from.amount), packetCap);
				if (move > 0) {
					from.extract(move, tx);
					to.insert(move, tx);
				}
			}
		}
	}

	/** The live cable buffer at {@code pos}, or null if the block there is no longer a cable. */
	private EnergyBuffer cableBufferAt(BlockPos pos) {
		return level.getBlockEntity(pos) instanceof CableBlockEntity cable ? cable.getEnergyStorage() : null;
	}

	/**
	 * Serve one consumer class (all machines, or all storage sinks) from the shared supply pool.
	 * Splits this class's allocation proportionally to room (capped at {@code packetCap}); pulls each
	 * consumer's share round-robin from producers, never from a producer co-located with the consumer
	 * (no storage self-churn). Decrements {@code remainingSupply[0]} by what actually moved.
	 */
	private long serveClass(List<LiveConsumer> cls, List<LiveProducer> liveProducers,
			long[] remainingSupply, long packetCap, EnergyPort.Txn tx) {
		if (cls.isEmpty() || remainingSupply[0] <= 0) {
			return 0L;
		}
		long[] room = new long[cls.size()];
		long demand = 0;
		for (int i = 0; i < cls.size(); i++) {
			room[i] = cls.get(i).room();
			demand += room[i];
		}
		long moveTotal = EnergyShare.deliverable(remainingSupply[0], demand);
		long[] share = EnergyShare.split(moveTotal, room, demand, packetCap);

		long moved = 0;
		long consumed = 0; // EU drawn from producers and not returned = delivered + cable loss
		for (int i = 0; i < cls.size(); i++) {
			long want = share[i];
			if (want <= 0) {
				continue;
			}
			LiveConsumer c = cls.get(i);
			long pulled = pullRoundRobin(liveProducers, want, c.pos(), tx);
			if (pulled <= 0) {
				continue;
			}
			// MOD-021: destroy floor(pulled × lossPerBlock × distance) EU in transit, per consumer.
			// Proportional to flow, so a small top-off packet floors to zero and the buffer still reaches
			// exact capacity (no MOD-009 regression). The lost EU is not returned to any producer.
			// The three lines below (deliver / surplus / consumed) are extracted into EnergyServe (MOD-144)
			// so the runtime loss-application kernel — the +→− / −→+ EU-creation/destruction mutants — is
			// covered by the L1 suite + pitest. EnergyNetwork itself is excluded from pitest as MC-coupled;
			// pure extracts like this are the only way the runtime math becomes L1-testable.
			int distance = consumerDistance.getOrDefault(c.pos(), 0);
			long loss = EnergyShare.cableLoss(pulled, Config.copperCableLossPerBlock, distance);
			long toDeliver = EnergyServe.deliverAfterLoss(pulled, loss);
			long inserted = toDeliver > 0 ? c.storage().insert(toDeliver, tx) : 0;
			long surplus = EnergyServe.surplus(toDeliver, inserted);
			if (surplus > 0) {
				// Consumer took less than we offered; push only the surplus back (loss stays destroyed).
				returnRoundRobin(liveProducers, surplus, c.pos(), tx);
			}
			moved += inserted;
			consumed += EnergyServe.consumed(inserted, loss);
		}
		remainingSupply[0] -= consumed;
		return moved;
	}

	/** True if the block at {@code pos} is a storage sink (e.g. BatteryBox) — served after machines. */
	private boolean isStorageSink(BlockPos pos) {
		return level.getBlockEntity(pos) instanceof MachineBlockEntity mbe && mbe.isEnergyStorageSink();
	}

	/**
	 * Pull up to {@code want} EU from producers, starting at the round-robin cursor, skipping any
	 * producer co-located with {@code consumerPos} so a storage node never charges itself.
	 */
	private long pullRoundRobin(List<LiveProducer> liveProducers, long want, BlockPos consumerPos,
			EnergyPort.Txn tx) {
		long pulled = 0;
		int n = liveProducers.size();
		for (int k = 0; k < n && pulled < want; k++) {
			LiveProducer prod = liveProducers.get((producerCursor + k) % n);
			if (prod.pos().equals(consumerPos)) {
				continue; // no self-churn: a storage sink must not pull from itself
			}
			pulled += prod.storage().extract(want - pulled, tx);
		}
		return pulled;
	}

	/**
	 * Return surplus EU back into producers (a consumer accepted less than was pulled), skipping self.
	 *
	 * <p><b>Invariant (keeps this loss-free):</b> {@code surplus} is 0 in practice because {@link #tick}
	 * sizes each consumer's demand from a simulated {@code insert} (line ~325), so {@code room} is already
	 * capped by the consumer's {@code maxInsert} and {@code serveClass} never pulls more than the consumer
	 * will take. This matters because producers can be generators ({@code maxInsert == 0}); a non-zero
	 * surplus landing on one would be <em>destroyed</em> here (the rate-capped {@code insert} returns 0),
	 * the same EU-loss shape fixed in {@code EnergyMover}. If a future change ever sizes the pull without
	 * that insert-simulate, restore surplus without the rate cap instead of via {@code insert}. Guarded by
	 * gametest {@code NetworkGameTest#tcCable001Nrg03_generatorNotDrainedByPartialConsumer}.
	 */
	private void returnRoundRobin(List<LiveProducer> liveProducers, long surplus, BlockPos consumerPos,
			EnergyPort.Txn tx) {
		for (LiveProducer prod : liveProducers) {
			if (surplus <= 0) {
				break;
			}
			if (prod.pos().equals(consumerPos)) {
				continue;
			}
			surplus -= prod.storage().insert(surplus, tx);
		}
	}

	/** A live producer endpoint resolved for this tick: its pos (for self-churn checks) and storage. */
	private record LiveProducer(BlockPos pos, EnergyPort storage) {
	}

	/** A live consumer endpoint resolved for this tick: its pos, storage and free room. */
	private record LiveConsumer(BlockPos pos, EnergyPort storage, long room) {
	}
}
