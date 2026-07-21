package dev.alaindustrial.core.energy;

import dev.alaindustrial.Config;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The per-tick distribution kernel of an {@link EnergyNetwork}. Extracted from {@code EnergyNetwork}
 * so the segment-to-segment flow math (MOD-070) sits in a small, focused class rather than being
 * scattered across ~300 lines of the 760-line façade. The pure delivery/loss arithmetic itself
 * already lives in {@link EnergyShare}/{@link EnergyServe}; this class orchestrates WHO pulls from
 * WHERE, in what order, at what time.
 *
 * <p>A new instance is constructed per {@link EnergyNetwork#tick()} pass and bound to that network's
 * topology + buffer lookups. The two-state-mutating fields that survive across ticks (round-robin
 * cursor, line-full flag, telemetry) live in {@link EnergyNetwork}; the kernel itself is stateless
 * beyond what the constructor captures.
 *
 * <p>MC-coupled: reads the live cable buffer at a position through a {@link Function} supplied by
 * the façade (the actual {@code level.getBlockEntity} stays there). This keeps the kernel close to
 * the runtime path it has always run on, while letting the topology/BFS half live in
 * {@link EnergyTopologyCache}.
 *
 * <p>Package-private — part of the {@code EnergyNetwork} implementation; not a public API.
 */
final class EnergyLineDistributor {
	/** A live producer endpoint resolved for this tick: its pos (for self-churn checks) and storage. */
	record LiveProducer(BlockPos pos, EnergyPort storage) {
	}

	/** A live consumer endpoint resolved for this tick: its pos, storage and free room. */
	record LiveConsumer(BlockPos pos, EnergyPort storage, long room) {
	}

	private static final Direction[] DIRECTIONS = Direction.values();

	private final Predicate<BlockPos> isCable;
	private final Function<BlockPos, EnergyBuffer> cableBufferAt;
	private final Function<BlockPos, Integer> consumerDistance;
	private final Function<BlockPos, Integer> cableDistance;
	private final List<BlockPos> propagationOrder;

	EnergyLineDistributor(
			EnergyTopologyCache topology,
			Function<BlockPos, EnergyBuffer> cableBufferAt,
			Function<BlockPos, Integer> consumerDistance,
			Function<BlockPos, Integer> cableDistance,
			List<BlockPos> propagationOrder) {
		this(topology::contains, cableBufferAt, consumerDistance, cableDistance, propagationOrder);
	}

	/**
	 * Predicate-based constructor for tests and other callers that want to drive the kernel against a
	 * synthetic cable graph without spinning up a full {@link EnergyTopologyCache} (which requires a
	 * live {@link ServerLevel}). All behavioural methods read topology only through the captured
	 * {@code isCable} predicate, so a hand-rolled {@code Set::contains} over fake positions exercises
	 * the same code path.
	 */
	EnergyLineDistributor(
			Predicate<BlockPos> isCable,
			Function<BlockPos, EnergyBuffer> cableBufferAt,
			Function<BlockPos, Integer> consumerDistance,
			Function<BlockPos, Integer> cableDistance,
			List<BlockPos> propagationOrder) {
		this.isCable = isCable;
		this.cableBufferAt = cableBufferAt;
		this.consumerDistance = consumerDistance;
		this.cableDistance = cableDistance;
		this.propagationOrder = propagationOrder;
	}

	/**
	 * Serve a class of consumers (machines, or storage sinks) from the network's cable buffers (the
	 * "line"). Reuses {@link #serveClass} with the live cable buffers as the supply pool, so the
	 * proportional split, tier packet cap and — crucially — the per-consumer MOD-021 distance loss are
	 * exactly the same math as the old direct path; only the <em>source</em> of the EU is the line.
	 * Returns the EU delivered. Both machines and storage sinks route through here (MOD-070): all
	 * transfer flows through the wires, so any active cable shows its buffered energy.
	 *
	 * @param producerCursor the round-robin rotation offset for {@link #serveClass}'s pull across the
	 *     line supply pool; bounded by the pool size, re-applied modulo inside.
	 */
	long serveConsumersFromLine(List<LiveConsumer> consumers, long packetCap, EnergyPort.Txn tx, int producerCursor) {
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
				if (isCable.test(np)) {
					touched.add(np);
				}
			}
		}
		List<LiveProducer> lineSupply = new ArrayList<>();
		long[] lineTotal = {0L};
		for (BlockPos pos : touched) {
			EnergyBuffer buf = cableBufferAt.apply(pos);
			if (buf != null && buf.amount > 0) {
				lineSupply.add(new LiveProducer(pos, buf));
				lineTotal[0] += buf.amount;
			}
		}
		if (lineSupply.isEmpty()) {
			return 0L;
		}
		return serveClass(consumers, lineSupply, lineTotal, packetCap, tx, producerCursor);
	}

	/**
	 * Stage 2 of {@link EnergyNetwork#tick()}: push existing line energy one hop toward consumers,
	 * then charge the producer-adjacent cables from producers. Returns the EU actually drawn from
	 * producers into the line (so the caller can dock it from the supply left for storage sinks).
	 * Loss-free between cables — the resistive cost is charged once, per consumer, on delivery
	 * (stage 1); charging it again per hop would break the MOD-021 numbers ({@code floor(0.02·1)=0}
	 * per hop ≠ {@code floor(0.02·32·10)=6}).
	 */
	void chargeAndPropagateLine(List<LiveProducer> generators, List<LiveProducer> storageSources,
			long machineDemand, long genSupply, long packetCap, EnergyPort.Txn tx) {
		propagateLineOneHop(packetCap, tx);
		// Generators fill the line freely (free energy → inertia); only their draw is docked from the
		// supply left for storage sinks.
		chargeLineFrom(generators, packetCap, Long.MAX_VALUE, tx);
		// Storage discharges into the line ONLY to cover the machine demand generators fall short of
		// (backup power). When generators already cover it, storageBudget is 0 and no battery bleeds into
		// the wires — this closes the dual-role wash the audit flagged.
		long storageBudget = Math.max(0L, machineDemand - genSupply);
		if (storageBudget > 0 && !storageSources.isEmpty()) {
			chargeLineFrom(storageSources, packetCap, storageBudget, tx);
		}
	}

	/**
	 * Charge the source-adjacent cables from {@code sources}. Each source injects at most {@code packetCap}
	 * EU total this tick (the tier throughput limit — NOT {@code packetCap × adjacent-cables}, which the
	 * audit flagged as multiplying the per-source cap), and the whole call stops once it has drawn
	 * {@code totalBudget} EU. Returns the EU actually drawn. Loss-free (the resistive cost is charged
	 * once on delivery, stage 1).
	 */
	private long chargeLineFrom(List<LiveProducer> sources, long packetCap, long totalBudget, EnergyPort.Txn tx) {
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
				if (!isCable.test(np)) {
					continue;
				}
				EnergyBuffer buf = cableBufferAt.apply(np);
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
			EnergyBuffer to = cableBufferAt.apply(pos);
			if (to == null) {
				continue;
			}
			int dTo = cableDistance.apply(pos);
			for (Direction dir : DIRECTIONS) {
				long free = to.getCapacity() - to.amount;
				if (free <= 0) {
					break;
				}
				BlockPos np = pos.relative(dir);
				Integer dFrom = cableDistance.apply(np);
				if (dFrom == null || dFrom >= dTo) {
					continue; // only pull from strictly upstream (closer to a producer)
				}
				EnergyBuffer from = cableBufferAt.apply(np);
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

	/**
	 * Serve one consumer class (all machines, or all storage sinks) from the shared supply pool.
	 * Splits this class's allocation proportionally to room (capped at {@code packetCap}); pulls each
	 * consumer's share round-robin from producers, never from a producer co-located with the consumer
	 * (no storage self-churn). Decrements {@code remainingSupply[0]} by what actually moved.
	 *
	 * @param producerCursor the round-robin rotation offset; bounded by the producer pool size.
	 */
	private long serveClass(List<LiveConsumer> cls, List<LiveProducer> liveProducers,
			long[] remainingSupply, long packetCap, EnergyPort.Txn tx, int producerCursor) {
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
			long pulled = pullRoundRobin(liveProducers, want, c.pos(), tx, producerCursor);
			if (pulled <= 0) {
				continue;
			}
			// MOD-021: destroy floor(pulled × lossPerBlock × distance) EU in transit, per consumer.
			// Proportional to flow, so a small top-off packet floors to zero and the buffer still reaches
			// exact capacity (no MOD-009 regression). The lost EU is not returned to any producer.
			// The three lines below (deliver / surplus / consumed) are extracted into EnergyServe (MOD-144)
			// so the runtime loss-application kernel — the +→− / −→+ EU-creation/destruction mutants — is
			// covered by the L1 suite + pitest. The runtime math is the pure extract path; this kernel
			// (MC-coupled) is the consumer of those pure helpers.
			int distance = consumerDistance.apply(c.pos());
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

	/**
	 * Pull up to {@code want} EU from producers, starting at the round-robin cursor, skipping any
	 * producer co-located with {@code consumerPos} so a storage node never charges itself.
	 */
	private long pullRoundRobin(List<LiveProducer> liveProducers, long want, BlockPos consumerPos,
			EnergyPort.Txn tx, int producerCursor) {
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
	 * <p><b>Invariant (keeps this loss-free):</b> {@code surplus} is 0 in practice because {@link EnergyNetwork#tick}
	 * sizes each consumer's demand from a simulated {@code insert}, so {@code room} is already capped by
	 * the consumer's {@code maxInsert} and the kernel never pulls more than the consumer will take. This
	 * matters because producers can be generators ({@code maxInsert == 0}); a non-zero surplus landing on
	 * one would be <em>destroyed</em> here (the rate-capped {@code insert} returns 0), the same EU-loss
	 * shape fixed in {@code EnergyMover}. If a future change ever sizes the pull without that
	 * insert-simulate, restore surplus without the rate cap instead of via {@code insert}. Guarded by
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
}
