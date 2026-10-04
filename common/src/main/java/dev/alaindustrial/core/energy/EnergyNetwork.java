package dev.alaindustrial.core.energy;

import dev.alaindustrial.core.net.GraphNetwork;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
public final class EnergyNetwork implements GraphNetwork<EnergyNetwork, BlockPos> {
	private final EnergyTopologyCache topology;

	/**
	 * Cached "every cable buffer is at capacity" flag for the producer-only wake gate. The only writer
	 * of a cable's buffer amount is this network's own {@link #tick()}, so once {@link #lineHasRoom()}
	 * returns false it stays false until the topology changes (cable added/removed, neighbour block
	 * placed/broken) — and every such change already routes through {@link #markDirty()} /
	 * {@link #addNode} / {@link #removeNode} / {@link #absorb}, which clear this flag. Without it,
	 * {@link #isAwake()} would re-scan every cable every tick on a sleeping generator-only network,
	 * spending O(cables) work to keep answering "do nothing" (the audit's #20).
	 */
	private boolean lineFull;
	/**
	 * Round-robin rotation offset for the distributor's pull. Advanced once per {@link #tick()} so the
	 * pull does not always start at the same supply. NOTE (MOD-070): on the consumer→line path the supply
	 * list is the touched <em>cable buffers</em>, not the network's producers, so this rotates over cables
	 * there; it only rotates over real producers on the storage-sink paired path. Every use site reduces it
	 * with {@link Math#floorMod} against its own list size — plain {@code %} is NOT enough here, because the
	 * counter runs free (see below) and {@code cursor + k} can overflow to a negative near
	 * {@link Integer#MAX_VALUE}, which {@code %} would pass straight through to {@code List#get}.
	 *
	 * <p>MOD-254: a plain monotonic tick counter, NOT {@code % liveProducerCount} as it was. Taken modulo
	 * the producer count it was pinned to 0 forever on a single-producer network — which is most of them —
	 * so every list it is supposed to rotate was walked in the same order every tick and the later entries
	 * starved. The counter now rotates the line-charge sweep too (source order and face order), where that
	 * pinning is what left a source next to a saturated cable at a full buffer for good.
	 */
	private int producerCursor;
	/** EU actually delivered by the most recent {@link #tick()} (0 if asleep/never ticked). */
	private long lastTickMoved;
	/** The part of {@link #lastTickMoved} that went into storage sinks (MOD-665). */
	private long lastTickToStorage;
	/** EU storage sources discharged into the line on the most recent {@link #tick()} (MOD-665). */
	private long lastTickFromStorage;
	/**
	 * Game time of the most recent {@link #tick()} call, whatever it moved (MOD-665). A network that
	 * went to sleep or was skipped by the per-tick budget keeps its last numbers; this stamp is what lets
	 * a reader tell a current reading from a stale one instead of reporting yesterday's flow as today's.
	 */
	private long lastTickAt = Long.MIN_VALUE;
	/** Producers that fed a cable on the last tick (MOD-665), see {@link EnergyLineDistributor#fed()}. */
	private Set<BlockPos> lastTickFed = Set.of();
	/**
	 * Game time of the last tick on which a GENERATOR actually held EU to give (MOD-318). Read by
	 * {@code CableBlockEntity.isEnergizedForShock()} to answer "is this
	 * wire's grid live", so a bare segment that merely holds a retained buffer is a hazard only while
	 * something is still powering the grid it belongs to.
	 *
	 * <p>A timestamp rather than a boolean, and deliberately so: networks are split and merged as cables
	 * come and go, and a surviving instance would otherwise carry a stale {@code true} into a component
	 * that no longer has a source — which is exactly the isolated line an open breaker creates, and the
	 * one case where reporting "live" would be a safety defect rather than a cosmetic one. A stamp goes
	 * stale on its own after one tick without supply, so the flag cannot outlive the fact it records.
	 */
	private long lastSupplyTick = Long.MIN_VALUE;

	/**
	 * What the discharge plan asks of the blocks around this line: the cascade and feed predicates of
	 * {@link StorageEndpoint}, and the EU travelling in the cables right now (MOD-314's in-flight
	 * correction, summed only when a cascade is actually sized).
	 */
	private final DischargePlan.Stores<BlockPos> stores = new DischargePlan.Stores<>() {
		@Override
		public boolean acceptsCascade(BlockPos pos) {
			return EnergyNetwork.this.acceptsCascade(pos);
		}

		@Override
		public long feedRate(BlockPos pos) {
			return EnergyNetwork.this.feedRate(pos);
		}

		@Override
		public long inFlight() {
			long inFlight = 0L;
			for (BlockPos pos : topology.cables()) {
				EnergyBuffer buf = cableBufferAt(pos);
				if (buf != null) {
					inFlight += buf.getAmount();
				}
			}
			return inFlight;
		}
	};

	public EnergyNetwork(ServerLevel level) {
		this.topology = new EnergyTopologyCache(level);
	}

	public ServerLevel level() {
		return topology.level();
	}

	public Set<BlockPos> cables() {
		return topology.cables();
	}

	@Override
	public Set<BlockPos> nodes() {
		return topology.cables();
	}

	public int size() {
		return topology.size();
	}

	public boolean contains(BlockPos pos) {
		return topology.contains(pos);
	}

	@Override
	public void addNode(BlockPos pos) {
		topology.addCable(pos);
		lineFull = false;
	}

	@Override
	public void removeNode(BlockPos pos) {
		topology.removeCable(pos);
		lineFull = false;
	}

	/** Absorb another network's cables into this one (union-find merge). */
	@Override
	public void absorb(EnergyNetwork other) {
		topology.absorb(other.topology);
		lineFull = false;
	}

	/** Force an endpoint recache on the next tick (neighbour changed, cable added/removed). */
	@Override
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
	 * full. A network whose only sources are <em>storage sources</em> (a dual-role BatteryBox) and that
	 * has NO consumer at all sleeps immediately: with nothing to serve, storage charges nothing, and
	 * keeping it awake would spin a no-op tick (plus an O(cables) {@link #lineHasRoom} scan) forever.
	 * A consumer-only or empty network can move nothing and is asleep until its neighbours change.
	 *
	 * <p>Note the precise condition, because MOD-314 narrowed what "storage charges nothing" means. It
	 * used to hold because storage discharged only to cover a machine deficit; a cascade donor now also
	 * discharges toward an emptier store. That does not change this method: the early return above fires
	 * only when {@code consumers()} is empty, and a cascade needs a storage sink on the network, which
	 * would be a consumer. So the sleep gate is still correct — but it is correct because there is no
	 * consumer, not because storage never discharges without machines.
	 */
	@Override
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
			if (buf != null && buf.getAmount() < buf.getCapacity()) {
				return true;
			}
		}
		return false;
	}

	/** EU actually delivered by the most recent {@link #tick()} (0 if never ticked or asleep). */
	public long lastTickMoved() {
		return lastTickMoved;
	}

	/** The part of {@link #lastTickMoved()} delivered into storage sinks (MOD-665). */
	public long lastTickToStorage() {
		return lastTickToStorage;
	}

	/** EU drawn out of storage sources into the line on the most recent tick (MOD-665). */
	public long lastTickFromStorage() {
		return lastTickFromStorage;
	}

	/** Game time of the most recent {@link #tick()} call, or {@link Long#MIN_VALUE} if never (MOD-665). */
	public long lastTickAt() {
		return lastTickAt;
	}

	/**
	 * Was a generator holding EU to give on the current or immediately preceding tick (MOD-318)? The
	 * one-tick grace is the same one {@code isEnergizedForShock} uses, and for the same reason: the
	 * cable and the network are ticked by different systems, so an exact-equality test would flicker
	 * with collision order. Storage sources deliberately do NOT count — {@code supplyingProducers} is
	 * filled from generators only — so a line left holding charge next to a Battery Box reads as dead,
	 * which is what makes an isolated segment safe to work on.
	 */
	public boolean hasLiveSupply(long gameTime) {
		if (lastSupplyTick == Long.MIN_VALUE) {
			return false;
		}
		long age = gameTime - lastSupplyTick;
		return age >= 0 && age <= 1;
	}

	/**
	 * Read-only diagnostics view for the Network Analyzer (MOD-016 / MOD-047) and tests. Returns a
	 * lightweight wrapper that exposes positions, supply/demand estimates and last-tick telemetry
	 * without polluting the network's tick-orchestrator API — see {@link EnergyNetworkDiagnostics}.
	 */
	public EnergyNetworkDiagnostics diagnostics() {
		return new EnergyNetworkDiagnostics(this);
	}

	/** The kernel's flow potential of a cable, see {@link EnergyTopologyCache#flowPotentialOrNull} (MOD-665). */
	Integer flowPotentialAt(BlockPos pos) {
		return topology.flowPotentialOrNull(pos);
	}

	/** Hop distance of a cable from the nearest supplying producer, or null (MOD-665). */
	Integer producerDistanceAt(BlockPos pos) {
		return topology.producerDistanceOrNull(pos);
	}

	/** Cables the downhill rule cannot reach, filled outward from the source instead (MOD-318, MOD-665). */
	List<BlockPos> strandedCables() {
		return topology.strandedFillOrder();
	}

	/** Producers that fed a cable on the last tick (MOD-665). */
	Set<BlockPos> lastTickFed() {
		return lastTickFed;
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
	 *
	 * <p>Each host is summed once (MOD-608), the same rule {@link #tick()} applies to its generator
	 * supply: the cells of a multiblock are several endpoints over one buffer.
	 */
	private long dryRunSum(List<EnergyTopologyCache.Endpoint> from, List<EnergyTopologyCache.Endpoint> against,
			boolean extracting) {
		return EnergyTransactions.get().simulate(sim -> {
			long total = 0;
			Set<BlockPos> countedHosts = new LinkedHashSet<>();
			for (EnergyTopologyCache.Endpoint ep : from) {
				if (!hasOtherPosition(against, ep.pos()) || !countedHosts.add(ep.host())) {
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
		return topology.level().getBlockEntity(pos) instanceof CableNode cable ? cable.lineBuffer() : null;
	}

	/**
	 * True if the block at {@code pos} is a storage sink (e.g. BatteryBox) — served after machines.
	 *
	 * <p>Asked of {@link StorageEndpoint}, where the predicate is declared, and so read the same way as the
	 * analyzer's {@code NetworkTraverser} and the direct push (MOD-691). Narrowed to the machine subclass it
	 * answered "not a store" for any block extending the energy base directly, whatever that block declared.
	 */
	private boolean isStorageSink(BlockPos pos) {
		return topology.level().getBlockEntity(pos) instanceof StorageEndpoint be && be.isEnergyStorageSink();
	}

	/**
	 * True if the store at {@code pos} may RECEIVE from the storage→storage cascade (MOD-314). Strictly
	 * narrower than {@link #isStorageSink}: the Teleporter is a storage sink too, with 25× a Battery Box's
	 * buffer, so balancing by fill fraction would drain a full box into it unbidden.
	 */
	private boolean acceptsCascade(BlockPos pos) {
		return topology.level().getBlockEntity(pos) instanceof StorageEndpoint be && be.acceptsCascade();
	}

	/** {@code storageFeedRate()} of the block at {@code pos}, or 0 when it is not a mod energy block. */
	private long feedRate(BlockPos pos) {
		return topology.level().getBlockEntity(pos) instanceof StorageEndpoint be
				? be.storageFeedRate() : 0L;
	}

	/**
	 * Can the block at {@code pos} ACCEPT energy through face {@code face} (MOD-255, numbered as in
	 * {@link LineEndpoints#BLOCK_FACES})? The
	 * distributor finds an endpoint's cables by adjacency, which says nothing about the role of the face
	 * they touch; this is the per-face permission behind that adjacency. Neutral on purpose — resolved
	 * through {@link EnergyLookup}, so it reads the same role on both loaders (Fabric registers the port
	 * per face, NeoForge asks the {@link EnergyPortHost}); a foreign block that exposes one undifferentiated
	 * handler answers "yes", which is the pre-MOD-255 behaviour for everything that is not ours.
	 */
	private boolean faceAccepts(BlockPos pos, int face) {
		EnergyPort port = EnergyLookup.get().find(topology.level(), pos, LineEndpoints.direction(face));
		return port != null && port.supportsInsertion();
	}

	/** Can the block at {@code pos} EMIT energy through {@code face}? Mirror of {@link #faceAccepts}. */
	private boolean faceEmits(BlockPos pos, int face) {
		EnergyPort port = EnergyLookup.get().find(topology.level(), pos, LineEndpoints.direction(face));
		return port != null && port.supportsExtraction();
	}

	/**
	 * The kernel's view of this line for this tick ({@link LineView}). MOD-318: the stranded segments are
	 * handed over only while a GENERATOR is actually supplying — the pass moves EU that is already in the
	 * wires, and without the gate a lone Battery Box's backup discharge would be dragged out into dead-end
	 * spurs it can only get back slowly (MOD-070's "a lone storage source does not fill the line").
	 */
	private LineView<BlockPos> lineView(boolean hasSupply) {
		return new LineView<>(LineEndpoints.BLOCK_FACES, topology::contains, this::cableBufferAt,
				topology::consumerDistance, topology::flowPotentialOrNull, topology::machinePotentialOrNull,
				topology.propagationOrder(), this::faceAccepts, this::faceEmits,
				hasSupply ? topology.strandedFillOrder() : List.of(), topology::producerDistanceOrNull);
	}

	/**
	 * The committing half of a tick: serve the consumers from the line, recharge the line, then advance the
	 * rotation cursor and publish the telemetry (MOD-665) and the line-full flag. Returns the EU delivered.
	 */
	private long moveAndRecord(EnergyLineDistributor<BlockPos> distributor, LineEndpoints.Supply supply,
			LineEndpoints.Demand demand, DischargePlan<BlockPos> plan, long packetCap, double lossPerBlock) {
		// [0] delivered in total, [1] of which into storage sinks, [2] drawn out of storage (MOD-665).
		long[] movedEu = {0L, 0L, 0L};
		EnergyTransactions.get().runCommitting(tx -> {
			// Serve ALL consumers from the line — machines first (MOD-009 priority), then storage sinks.
			// Both drain the cable buffers they touch, so a cable between a source and ANY consumer
			// (a machine OR a BatteryBox) genuinely carries and displays the energy in transit, instead
			// of the storage charge bypassing the wires.
			movedEu[0] += distributor.serveConsumersFromLine(demand.machines(), packetCap, lossPerBlock, tx,
					producerCursor);
			long intoStorage = distributor.serveConsumersFromLine(demand.sinks(), packetCap, lossPerBlock, tx,
					producerCursor);
			movedEu[0] += intoStorage;
			movedEu[1] = intoStorage;
			// Replenish the line for next tick: generators fill it freely (inertia + a visible buffer);
			// a storage source discharges into the line ONLY to cover the machine demand generators fall
			// short of (backup power), never to hoard buffers or wash into another battery. With no
			// generator present, storage discharges nothing, so two batteries can't drain each other.
			movedEu[2] = distributor.chargeAndPropagateLine(supply.generators(), supply.storageSources(), plan,
					packetCap, tx, producerCursor);
		});
		// Advance the rotation cursor so the next tick starts at a different producer / face. Monotonic and
		// masked non-negative (MOD-254); every use site re-applies the modulus against its own list size.
		producerCursor = (producerCursor + 1) & Integer.MAX_VALUE;
		lastTickMoved = movedEu[0];
		lastTickToStorage = movedEu[1];
		lastTickFromStorage = movedEu[2];
		lastTickFed = Collections.unmodifiableSet(new LinkedHashSet<>(distributor.fed()));
		// Refresh the cached line-full flag so the next isAwake() on a producer-only network can skip
		// the O(cables) scan. Only meaningful on the no-consumer path (a consumer keeps the network
		// awake unconditionally), but the cost is one scan that has already happened inside this tick's
		// line fill, so we re-derive it cheaply rather than tracking it through every buffer write.
		lineFull = !lineHasRoom();
		return movedEu[0];
	}

	/** {@link #tick(NetworkBalance)} on the balance as it is now: the frame's tick shape, and the rigs'. */
	@Override
	public long tick() {
		return tick(NetworkManager.balance());
	}

	/**
	 * Run one distribution pass. Returns the EU actually delivered to consumers this tick (0 when
	 * nothing moved). Safe to call on an asleep network (returns 0). All movement commits in a single
	 * outer transaction. The returned amount feeds the {@link NetworkManager} telemetry counters.
	 *
	 * <p>Delegates the per-tick work to {@link EnergyLineDistributor}; see that class for the
	 * flow contract (MOD-070). The knobs read inside the pass come in {@code balance} (CORE-10).
	 */
	public long tick(NetworkBalance balance) {
		lastTickAt = topology.level().getGameTime();
		lastTickToStorage = 0L;
		lastTickFromStorage = 0L;
		lastTickFed = Set.of();
		List<EnergyTopologyCache.Endpoint> producers = topology.producers();
		if (producers.isEmpty()) {
			// No source at all — nothing to serve and nothing to charge the line with.
			lastTickMoved = 0L;
			return 0L;
		}
		List<EnergyTopologyCache.Endpoint> consumers = topology.consumers();

		// Packet cap and loss rate come from the strongest grade (MOD-219; all copper = 32 EU/t + 0.02); its
		// segment buffer — the real throughput — lives in each cable's own EnergyBuffer.
		CableType strongestCable = topology.strongestCable();
		long packetCap = strongestCable.packetCap();
		double lossPerBlock = strongestCable.lossPerBlock();

		LineEndpoints.Supply supply = LineEndpoints.supply(producers, this::storageAt, this::isStorageSink);
		if (supply.isEmpty()) {
			// No live producer: nothing to serve deterministically. Energy still in the line is delivered
			// once a producer returns and the network wakes.
			lastTickMoved = 0L;
			return 0L;
		}
		LineEndpoints.Demand demand = LineEndpoints.demand(consumers, this::storageAt, this::isStorageSink);
		List<EnergyLineDistributor.LiveConsumer<BlockPos>> sinks = demand.sinks();

		// The three storage discharge channels — backup, cascade, feed — and which stores must sit this tick
		// out of the serve pass because they discharge into the line (ADR-002, ADR-004): one decision, made
		// by DischargePlan. The deadband is the strongest grade's segment buffer (MOD-219).
		DischargePlan<BlockPos> plan = DischargePlan.decide(demand.machineDemand(), supply.genSupply(),
				supply.storageSources(), sinks, stores, strongestCable.segmentBuffer(), packetCap,
				balance.storageFeedReserveFraction());
		if (!supply.storagePositions().isEmpty()) {
			sinks.removeIf(c -> plan.excludes(c.pos(), supply.storagePositions()));
		}

		// Published before the distributor reads propagationOrder / the flow potential, and outside the
		// committing transaction opened below (ADR-003).
		topology.updateLiveEndpoints(supply.supplying(), demand.flowSeeds(), demand.machineSeeds());
		// Stamp "a generator had EU this tick" for the shock rule — here rather than at the end of the
		// method, so it lands on the same tick the cables are actually fed. Generators only: a Battery Box
		// on an isolated stretch must not keep it reading as a live hazard (ADR-007).
		boolean hasSupply = !supply.supplying().isEmpty();
		if (hasSupply) {
			lastSupplyTick = topology.level().getGameTime();
		}
		EnergyLineDistributor<BlockPos> distributor = new EnergyLineDistributor<>(lineView(hasSupply));
		return moveAndRecord(distributor, supply, demand, plan, packetCap, lossPerBlock);
	}
}
