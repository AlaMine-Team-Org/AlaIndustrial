package dev.alaindustrial.core.energy;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
	/**
	 * Game time of the last tick on which a GENERATOR actually held EU to give (MOD-318). Read by
	 * {@link dev.alaindustrial.block.entity.CableBlockEntity#isEnergizedForShock()} to answer "is this
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
	 * True if the store at {@code pos} may RECEIVE from the storage→storage cascade (MOD-314). Strictly
	 * narrower than {@link #isStorageSink}: the Teleporter is a storage sink too, with 25× a Battery Box's
	 * buffer, so balancing by fill fraction would drain a full box into it unbidden.
	 */
	private boolean acceptsCascade(BlockPos pos) {
		return topology.level().getBlockEntity(pos) instanceof MachineBlockEntity mbe && mbe.acceptsCascade();
	}

	/**
	 * How much each storage source may push into the line as a cascade donor this tick (MOD-314).
	 *
	 * <p>Per (donor, sink) pair, keeping only the single most-favourable target per donor: the line
	 * decides where the EU actually lands (proportionally to room, {@code EnergyShare.split}), so this
	 * only has to answer "is there a target worth opening the tap for, and how wide". Sizing off the
	 * emptiest eligible target is what makes the allowance shrink as the pair levels out.
	 *
	 * <p>The deadband is the network's segment buffer, taken from its strongest cable grade — not the
	 * tier packet cap and not the global {@code Config.cableBuffer}. MOD-070 established the segment
	 * buffer as the real per-tick throughput between two points, so it is the unit a "too small to
	 * bother" threshold has to be measured in; MOD-219 made it per grade, so reading the global would
	 * under-set the deadband fourfold on a gold line and weaken the anti-oscillation guarantee exactly
	 * where packets are largest.
	 *
	 * <p>Only self is excluded as a target — deliberately not "every other storage source". A node is a
	 * source by face role alone, charge not consulted, so excluding the whole set would make an empty
	 * mid-bus box permanently ineligible (see the guard in {@code tick()}). Two nodes cannot donate to
	 * each other anyway: the gradient is strict, and {@code fill(a) > fill(b)} and {@code fill(b) >
	 * fill(a)} cannot both hold. A chain (c → a → b) is legitimate, and the guard keeps whoever is
	 * actually donating out of the serve pass.
	 */
	private Map<BlockPos, Long> computeCascadeAllowances(List<EnergyLineDistributor.LiveProducer> donors,
			List<EnergyLineDistributor.LiveConsumer> sinks, long deadband, long packetCap) {
		Map<BlockPos, Long> allowances = new LinkedHashMap<>();
		// EU that has already left a donor and is still travelling: the line advances one hop per tick, so
		// on an N-cable run up to N × segmentBuffer EU is in transit and absent from every stored amount
		// the comparison below can see. Ignoring it makes the donor keep giving for as many ticks as the
		// line is long, and it lands PAST level — measured at 77 EU over ten copper cables, which is the
		// first half of an oscillation on any layout where the receiver can donate back. Counting it as
		// already delivered removes the blind spot. Attributing it per (donor, sink) pair is not possible
		// — a cable buffer does not record where its EU came from or is going — so the whole line counts
		// against every sink, which biases toward refusing a transfer rather than overshooting one.
		long inFlight = 0L;
		for (BlockPos pos : topology.cables()) {
			EnergyBuffer buf = cableBufferAt(pos);
			if (buf != null) {
				inFlight += buf.amount;
			}
		}
		for (EnergyLineDistributor.LiveProducer donor : donors) {
			if (!acceptsCascade(donor.pos())) {
				continue;
			}
			long donorAmount = donor.storage().getAmount();
			long donorCapacity = donor.storage().getCapacity();
			long best = 0L;
			for (EnergyLineDistributor.LiveConsumer sink : sinks) {
				if (sink.pos().equals(donor.pos()) || !acceptsCascade(sink.pos())) {
					continue;
				}
				long sinkCapacity = sink.storage().getCapacity();
				long sinkAmount = Math.min(sinkCapacity, sink.storage().getAmount() + inFlight);
				long allowance = CascadeShare.allowance(donorAmount, donorCapacity,
						sinkAmount, sinkCapacity, deadband, packetCap);
				if (allowance > best) {
					best = allowance;
				}
			}
			if (best > 0) {
				allowances.put(donor.pos(), best);
			}
		}
		return allowances;
	}

	/**
	 * MOD-353 — how much each donor store may release to sinks that are OUTSIDE the cascade
	 * (Teleporter, Charging Station), when nothing else on the segment wants power.
	 *
	 * <p>Deliberately a separate aggregate from {@code machineDemand}: folding these sinks into machine
	 * demand would have been two lines, but it also feeds the MOD-255 self-serve guard, the cascade's
	 * mutual exclusion and {@code storageBudget} — so a Teleporter with room would silently close the
	 * box↔box cascade, which is the MOD-314 regression this must not cause.
	 *
	 * <p>Unlike the cascade this compares nothing proportionally. Each donor offers a flat rate out of
	 * what it holds above its own reserve, so a 500 000 EU fund cannot pull harder than a 20 000 EU box
	 * would — that asymmetry is precisely why the cascade refuses these blocks in the first place.
	 */
	private Map<BlockPos, Long> computeFeedAllowances(List<EnergyLineDistributor.LiveProducer> donors,
			List<EnergyLineDistributor.LiveConsumer> sinks, long packetCap) {
		Map<BlockPos, Long> allowances = new LinkedHashMap<>();
		for (EnergyLineDistributor.LiveProducer donor : donors) {
			// A donor that would itself accept this feed is a store, not a fund: those level out through
			// the cascade, and letting them use this channel too would give one pair two ways to move EU
			// in the same tick.
			if (feedRate(donor.pos()) > 0) {
				continue;
			}
			long donorAmount = donor.storage().getAmount();
			long donorCapacity = donor.storage().getCapacity();
			long best = 0L;
			for (EnergyLineDistributor.LiveConsumer sink : sinks) {
				if (sink.pos().equals(donor.pos())) {
					continue;
				}
				long rate = feedRate(sink.pos());
				if (rate <= 0) {
					continue;
				}
				long allowance = StorageFeedShare.feedAllowance(donorAmount, donorCapacity,
						dev.alaindustrial.Config.storageFeedReserveFraction, sink.room(), rate, packetCap);
				if (allowance > best) {
					best = allowance;
				}
			}
			if (best > 0) {
				allowances.put(donor.pos(), best);
			}
		}
		return allowances;
	}

	/** {@code storageFeedRate()} of the block at {@code pos}, or 0 when it is not a mod machine. */
	private long feedRate(BlockPos pos) {
		return topology.level().getBlockEntity(pos) instanceof MachineBlockEntity mbe
				? mbe.storageFeedRate() : 0L;
	}

	/**
	 * Can the block at {@code pos} ACCEPT energy through the face pointing in {@code face} (MOD-255)? The
	 * distributor finds an endpoint's cables by adjacency, which says nothing about the role of the face
	 * they touch; this is the per-face permission behind that adjacency. Neutral on purpose — resolved
	 * through {@link EnergyLookup}, so it reads the same role on both loaders (Fabric registers the port
	 * per face, NeoForge asks the {@link EnergyPortHost}); a foreign block that exposes one undifferentiated
	 * handler answers "yes", which is the pre-MOD-255 behaviour for everything that is not ours.
	 */
	private boolean faceAccepts(BlockPos pos, Direction face) {
		EnergyPort port = EnergyLookup.get().find(topology.level(), pos, face);
		return port != null && port.supportsInsertion();
	}

	/** Can the block at {@code pos} EMIT energy through {@code face}? Mirror of {@link #faceAccepts}. */
	private boolean faceEmits(BlockPos pos, Direction face) {
		EnergyPort port = EnergyLookup.get().find(topology.level(), pos, face);
		return port != null && port.supportsExtraction();
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

		// Both transport numbers come from the network's strongest cable grade (MOD-219): the packet cap
		// (a gold segment lifts the whole line to 128 EU/t) and the resistive loss rate (that same segment
		// also moves the line to gold's higher 0.03 loss). An all-copper network resolves to exactly the
		// historical 32 EU/t + 0.02 pair. Note the grade's OTHER number — its segment buffer, i.e. the real
		// throughput — is not read here: it lives per-segment in each cable's own EnergyBuffer.
		CableType strongestCable = topology.strongestCable();
		long packetCap = strongestCable.packetCap();
		double lossPerBlock = strongestCable.lossPerBlock();

		// --- dry-run supply, partitioned by source priority (MOD-070): pure generators vs storage
		// sources (dual-role BatteryBox with a cabled OUT face). Generators feed the line and charge
		// storage; storage discharges into the line as backup power (machine deficit) and — since
		// MOD-314 — as a cascade donor once machines are covered.
		//
		// This comment used to end "and NEVER sources for another storage sink — so batteries can't wash
		// energy through the wires or into each other". That blanket ban was too wide: it did stop washing,
		// but it also stopped the legitimate case of a full box topping up an empty one, so a player could
		// not extend their bank by placing a second Battery Box. Washing is now excluded by construction
		// instead — a donor only feeds a store that is proportionally emptier, only by half the gap, only
		// above a deadband, and never one that is itself donating this tick. See CascadeShare. ---
		long genSupply = 0;
		java.util.Set<BlockPos> supplyingProducers = new java.util.LinkedHashSet<>();
		List<EnergyLineDistributor.LiveProducer> generators = new ArrayList<>(producers.size());
		List<EnergyLineDistributor.LiveProducer> storageSources = new ArrayList<>();
		// MOD-255: the positions of the dual-role nodes, so the sink pass below can tell a battery that is
		// discharging into this very line from an ordinary consumer.
		Set<BlockPos> storageSourcePositions = new LinkedHashSet<>();
		for (EnergyTopologyCache.Endpoint ep : producers) {
			EnergyPort st = storageAt(ep);
			if (st == null || !st.supportsExtraction()) {
				continue;
			}
			if (isStorageSink(ep.pos())) {
				storageSources.add(new EnergyLineDistributor.LiveProducer(ep.pos(), st));
				storageSourcePositions.add(ep.pos());
			} else {
				generators.add(new EnergyLineDistributor.LiveProducer(ep.pos(), st));
				long supply = EnergyTransactions.get().simulate(sim -> st.extract(Long.MAX_VALUE, sim));
				genSupply += supply;
				// MOD-214: which producers actually hold EU this tick, decided HERE — outside the
				// committing transaction opened below. The line kernel needs it to tell a cable that is
				// genuinely starved from one that is simply about to be refilled by its generator.
				if (supply > 0) {
					supplyingProducers.add(ep.pos());
				}
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

		// MOD-255: a storage node that discharges into this line THIS tick must not also be served from it.
		// The old no-self-churn rule compared positions, which is dead on the line path (the supply pool is
		// cable buffers, and a cable is never at the battery's position), so a Battery Box wired on both its
		// IN and its OUT face drank straight back the EU it had just pushed into the wire: the charge
		// oscillated between the box and its neighbouring cables and never advanced down the line, leaving
		// the machines at the far end on 0 EU next to a full battery.
		//
		// Discharging is exactly `storageBudget > 0` (see EnergyLineDistributor#chargeAndPropagateLine), so
		// the two decisions read the same number from the same helper. When generators cover the machines —
		// or there is no machine at all — the budget is 0, nothing is dropped, and a dual-role box charges
		// from its input face as before (MOD-214's cabled-output-face layout). The consequence to know: while
		// machines are hungry and generators fall short, a dual-role box only feeds and does not fill. That
		// is MOD-009's "machines before storage" rule, now also applied to the battery's own second role.
		long storageDischarge = EnergyLineDistributor.storageBudget(machineDemand, genSupply);

		// MOD-314: the storage→storage cascade — a fuller Battery Box topping up an emptier one, so
		// "extend the bank by placing a second box" works. Computed HERE, before the guard below, because
		// the guard has to know whether a node is discharging for ANY reason this tick.
		//
		// Only when `storageDischarge == 0`, i.e. machines are absent or already covered by generators.
		// That is MOD-009's "machines before storage" rule (a battery must not top up another battery
		// while a machine starves) and it also makes the two storage stages mutually exclusive, which is
		// what keeps one source from injecting 2 × packetCap in a tick — see chargeAndPropagateLine.
		//
		// Targets exclude every dual-role position: a node that donates this tick must not also receive,
		// or two boxes wired on both faces would hand the same EU back and forth, paying MOD-021 loss on
		// every leg — a slow drain, strictly worse than the bug being fixed. Restricting the RECEIVER to
		// a pure sink makes that ping-pong structurally impossible rather than merely unlikely.
		Map<BlockPos, Long> cascadeAllowances = new LinkedHashMap<>();
		if (storageDischarge == 0 && !storageSources.isEmpty() && !sinks.isEmpty()) {
			cascadeAllowances = computeCascadeAllowances(storageSources, sinks, strongestCable.segmentBuffer(),
					packetCap);
		}

		// MOD-353: the third and last way a store may discharge — into a sink that the cascade refuses.
		// Computed only when BOTH earlier stages are closed, which is what keeps the three mutually
		// exclusive: backup power (machine demand), cascade (fill fractions), feed (flat rate + reserve).
		Map<BlockPos, Long> feedAllowances = new LinkedHashMap<>();
		if (storageDischarge == 0 && cascadeAllowances.isEmpty()
				&& !storageSources.isEmpty() && !sinks.isEmpty()) {
			feedAllowances = computeFeedAllowances(storageSources, sinks, packetCap);
		}

		// MOD-255 + MOD-314: a storage node that discharges into this line THIS tick — as backup power OR
		// as a cascade donor — must not also be served from it, or it drinks its own discharge back out of
		// the neighbouring cable. Before MOD-314 this read `storageDischarge > 0` alone, which is zero in
		// exactly the scenario the cascade runs in (no machines), so the guard would have been inert
		// precisely when it was needed.
		//
		// Membership of `storageSources` is NOT the right criterion, and using it re-created the very bug
		// this task exists to fix. That list is built from `supportsExtraction()`, a pure face-role test
		// that never looks at the buffer — so an EMPTY box with a cable on its OUT face counts as a
		// "source". Excluding all of them would drop every box wired mid-bus (cable–box–cable, the layout
		// MOD-255 documents as ordinary) out of `sinks`, and it would never charge from its full neighbour:
		// the original symptom, preserved for the more common wiring. Backup discharge does draw from every
		// storage source, so there the whole set is right; the cascade draws only from donors that actually
		// got an allowance, so only those are excluded.
		if (!storageSourcePositions.isEmpty()) {
			if (storageDischarge > 0) {
				sinks.removeIf(c -> storageSourcePositions.contains(c.pos()));
			} else if (!cascadeAllowances.isEmpty()) {
				Map<BlockPos, Long> discharging = cascadeAllowances;
				sinks.removeIf(c -> discharging.containsKey(c.pos()));
			} else if (!feedAllowances.isEmpty()) {
				// Same rule for the feed stage (MOD-353): a store that pushes into the line this tick must
				// not be served from it, or it drinks its own discharge back out of the neighbouring cable.
				Map<BlockPos, Long> feeding = feedAllowances;
				sinks.removeIf(c -> feeding.containsKey(c.pos()));
			}
		}

		// MOD-252: the flow direction is seeded from the endpoints that actually WANT energy this tick, so
		// EU moves toward demand instead of away from whichever source happens to be nearest.
		//
		// EVERY waiting endpoint seeds it — machines AND storage sinks. Seeding is about REACHABILITY, not
		// about priority: a cable is only ever filled from a source-adjacent cable, and the pull rule is
		// strictly downhill, so a cable whose potential is above every source-adjacent one has no filling
		// path at all. Letting machines alone seed the field therefore fenced off the whole stretch of bus
		// lying past the source relative to the machine: a Battery Box out there read 0/20000 next to dead
		// cable for as long as any machine anywhere on the net had room — the original MOD-252 symptom,
		// simply relocated to the far side of the source. MOD-009's class priority is enforced where it
		// belongs, in the SERVE order below (machines drink from the line before storage sinks do), not by
		// bending the geometry.
		//
		// An empty set means nobody is waiting, which puts the network on the producer-seeded fallback
		// field and reproduces MOD-070's producer-only line fill exactly.
		Set<BlockPos> sinkSeeds = new LinkedHashSet<>();
		Set<BlockPos> machineSeeds = new LinkedHashSet<>();
		for (EnergyLineDistributor.LiveConsumer c : machines) {
			sinkSeeds.add(c.pos());
			machineSeeds.add(c.pos());
		}
		for (EnergyLineDistributor.LiveConsumer c : sinks) {
			sinkSeeds.add(c.pos());
		}
		// MOD-214: the producer field must describe distance from a producer that actually supplies, not
		// from any face capable of extraction. Both fields are published before the distributor reads
		// propagationOrder / the flow potential, and outside the committing transaction opened below.
		// MOD-254: the machine subset rides along as the fork tie-break seed set. It biases how a forked
		// cable buffer is split (machines before storage, geometrically) and nothing else — the flow field
		// above stays seeded from every waiting endpoint, so reachability is untouched.
		topology.updateLiveEndpoints(supplyingProducers, sinkSeeds, machineSeeds);
		// MOD-318: stamp "a generator had EU this tick" for the shock rule. Set here rather than at the
		// end of the method so it lands on the same tick the cables are actually being fed, and only from
		// `supplyingProducers`, which holds generators only — a Battery Box sitting on an isolated stretch
		// must not keep that stretch reading as a live hazard.
		boolean hasSupply = !supplyingProducers.isEmpty();
		if (hasSupply) {
			lastSupplyTick = topology.level().getGameTime();
		}

		EnergyLineDistributor distributor = new EnergyLineDistributor(
				topology, this::cableBufferAt,
				topology::consumerDistance, topology::flowPotentialOrNull, topology::machinePotentialOrNull,
				topology.propagationOrder(), this::faceAccepts, this::faceEmits,
				// MOD-318: the segments the downhill rule cannot reach. Handed over only while a GENERATOR
				// is actually supplying — the pass moves EU that is already in the wires, and without the
				// gate a lone Battery Box's backup discharge would be dragged out into dead-end spurs it
				// can only get back slowly (MOD-070's "a lone storage source does not fill the line").
				hasSupply ? topology.strandedFillOrder() : List.of(), topology::producerDistanceOrNull);
		long[] movedEu = {0L};
		long finalMachineDemand = machineDemand;
		long finalGenSupply = genSupply;
		Map<BlockPos, Long> finalCascadeAllowances = cascadeAllowances;
		Map<BlockPos, Long> finalFeedAllowances = feedAllowances;
		EnergyTransactions.get().runCommitting(tx -> {
			// Serve ALL consumers from the line — machines first (MOD-009 priority), then storage sinks.
			// Both drain the cable buffers they touch, so a cable between a source and ANY consumer
			// (a machine OR a BatteryBox) genuinely carries and displays the energy in transit, instead
			// of the storage charge bypassing the wires.
			movedEu[0] += distributor.serveConsumersFromLine(machines, packetCap, lossPerBlock, tx, producerCursor);
			movedEu[0] += distributor.serveConsumersFromLine(sinks, packetCap, lossPerBlock, tx, producerCursor);
			// Replenish the line for next tick: generators fill it freely (inertia + a visible buffer);
			// a storage source discharges into the line ONLY to cover the machine demand generators fall
			// short of (backup power), never to hoard buffers or wash into another battery. With no
			// generator present, storage discharges nothing, so two batteries can't drain each other.
			distributor.chargeAndPropagateLine(generators, storageSources, finalMachineDemand, finalGenSupply,
					packetCap, tx, producerCursor, finalCascadeAllowances, finalFeedAllowances);
		});
		// Advance the rotation cursor so the next tick starts at a different producer / face. Monotonic and
		// masked non-negative (MOD-254); every use site re-applies the modulus against its own list size.
		producerCursor = (producerCursor + 1) & Integer.MAX_VALUE;
		lastTickMoved = movedEu[0];
		// Refresh the cached line-full flag so the next isAwake() on a producer-only network can skip
		// the O(cables) scan. Only meaningful on the no-consumer path (a consumer keeps the network
		// awake unconditionally), but the cost is one scan that has already happened inside this tick's
		// line fill, so we re-derive it cheaply rather than tracking it through every buffer write.
		lineFull = !lineHasRoom();
		return movedEu[0];
	}
}
