package dev.alaindustrial.core;

import dev.alaindustrial.Config;
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

	private final ServerLevel level;
	private final Set<BlockPos> cables = new HashSet<>();

	/** Cached endpoints, rebuilt on {@link #markDirty()} / any topology change. */
	private final List<Endpoint> producers = new ArrayList<>();
	private final List<Endpoint> consumers = new ArrayList<>();
	/** Cable-distance from each consumer position to its nearest producer, for per-consumer loss (MOD-021). */
	private final Map<BlockPos, Integer> consumerDistance = new HashMap<>();
	private boolean endpointsDirty = true;
	/** Round-robin start index so producers are pulled fairly across ticks. */
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
	 * Awake = has at least one producer and one consumer. A producer-only or consumer-only network
	 * can move nothing, so it is skipped (asleep) until its neighbours change.
	 */
	public boolean isAwake() {
		if (endpointsDirty) {
			refreshEndpoints();
		}
		return !producers.isEmpty() && !consumers.isEmpty();
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
			for (Direction dir : Direction.values()) {
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
		if (producers.isEmpty() || consumers.isEmpty()) {
			return;
		}
		Map<BlockPos, Integer> cableDist = new HashMap<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		for (Endpoint producer : producers) {
			for (Direction dir : Direction.values()) {
				BlockPos cable = producer.pos().relative(dir);
				if (cables.contains(cable) && cableDist.putIfAbsent(cable, 1) == null) {
					queue.add(cable);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos cur = queue.poll();
			int next = cableDist.get(cur) + 1;
			for (Direction dir : Direction.values()) {
				BlockPos np = cur.relative(dir);
				if (cables.contains(np) && cableDist.putIfAbsent(np, next) == null) {
					queue.add(np);
				}
			}
		}
		for (Endpoint consumer : consumers) {
			int best = 0;
			for (Direction dir : Direction.values()) {
				Integer d = cableDist.get(consumer.pos().relative(dir));
				if (d != null && (best == 0 || d < best)) {
					best = d;
				}
			}
			if (best > 0) {
				consumerDistance.put(consumer.pos(), best);
			}
		}
	}

	/** Resolve a live storage for an endpoint, or null if it's gone (block changed/unloaded). */
	private EnergyPort storageAt(Endpoint ep) {
		return EnergyLookup.get().find(level, ep.pos(), ep.side());
	}

	/**
	 * Run one distribution pass. Returns the EU actually delivered to consumers this tick (0 when
	 * nothing moved). Safe to call on an asleep network (returns 0). All movement commits in a single
	 * outer transaction. The returned amount feeds the {@link NetworkManager} telemetry counters.
	 */
	public long tick() {
		if (endpointsDirty) {
			refreshEndpoints();
		}
		if (producers.isEmpty() || consumers.isEmpty()) {
			lastTickMoved = 0L;
			return 0L;
		}

		long packetCap = EnergyTier.LV.maxVoltage();

		// --- dry-run supply (extractable), keeping each producer's pos for self-churn exclusion ---
		long supply = 0;
		List<LiveProducer> liveProducers = new ArrayList<>(producers.size());
		for (Endpoint ep : producers) {
			EnergyPort st = storageAt(ep);
			if (st == null || !st.supportsExtraction()) {
				continue;
			}
			liveProducers.add(new LiveProducer(ep.pos(), st));
			supply += EnergyTransactions.get().simulate(sim -> st.extract(Long.MAX_VALUE, sim));
		}
		if (supply <= 0 || liveProducers.isEmpty()) {
			lastTickMoved = 0L;
			return 0L;
		}

		// --- dry-run demand (insertable), partitioned: machines first, storage sinks last (MOD-009) ---
		List<LiveConsumer> machines = new ArrayList<>(consumers.size());
		List<LiveConsumer> sinks = new ArrayList<>(consumers.size());
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
			(isStorageSink(ep.pos()) ? sinks : machines).add(c);
		}
		if (machines.isEmpty() && sinks.isEmpty()) {
			lastTickMoved = 0L;
			return 0L;
		}

		// --- commit: serve machines from the supply pool first, then storage sinks from the remainder ---
		long[] remainingSupply = {supply};
		long[] movedEu = {0L};
		EnergyTransactions.get().runCommitting(tx -> {
			movedEu[0] += serveClass(machines, liveProducers, remainingSupply, packetCap, tx);
			movedEu[0] += serveClass(sinks, liveProducers, remainingSupply, packetCap, tx);
		});
		// Advance the producer cursor so the next tick starts at a different producer. Step it in
		// lockstep with liveProducers — the list pullRoundRobin actually pulls from — so producers
		// filtered out as dead this tick don't bias which live producer leads the rotation.
		// (liveProducers is non-empty here: a zero/empty supply already returned above.)
		producerCursor = (producerCursor + 1) % liveProducers.size();
		lastTickMoved = movedEu[0];
		return movedEu[0];
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
			int distance = consumerDistance.getOrDefault(c.pos(), 0);
			long loss = EnergyShare.cableLoss(pulled, Config.copperCableLossPerBlock, distance);
			long toDeliver = pulled - loss;
			long inserted = toDeliver > 0 ? c.storage().insert(toDeliver, tx) : 0;
			long surplus = toDeliver - inserted;
			if (surplus > 0) {
				// Consumer took less than we offered; push only the surplus back (loss stays destroyed).
				returnRoundRobin(liveProducers, surplus, c.pos(), tx);
			}
			moved += inserted;
			consumed += inserted + loss;
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
