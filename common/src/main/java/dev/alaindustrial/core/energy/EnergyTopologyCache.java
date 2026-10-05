package dev.alaindustrial.core.energy;

import dev.alaindustrial.core.net.DistanceField;
import dev.alaindustrial.core.net.NodeSet;
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
 * Topology + endpoint-discovery half of an {@link EnergyNetwork}. Extracted from {@code EnergyNetwork}
 * so the per-tick distribution kernel can stay small and (in its MC-free extracts) L1-testable.
 *
 * <p>Owns: the cable set, the cached producer/consumer endpoint lists with their cable-distances, the
 * independent BFS fields that produce those distances (producer-seeded for MOD-021 loss + the MOD-070
 * fallback; sink-seeded for the MOD-252 flow direction; machine-seeded for the MOD-254 fork tie-break,
 * which biases a split but never a path), and the propagation sweep order. All
 * mutation routes through {@link #addCable}/{@link #removeCable}/{@link #absorb}/{@link #markDirty},
 * which set the {@link NodeSet}'s dirty flag so the next read refreshes the cache. {@code EnergyNetwork}
 * wires a {@code Runnable onTopologyChanged} to those mutations so its own wake-state flag
 * ({@code lineFull}) stays consistent.
 *
 * <p>MC-coupled: looks up {@link EnergyLookup} and {@code level.getBlockEntity} for the endpoint
 * classification and the cable-buffer reads. The pure distribution math lives in
 * {@link EnergyLineDistributor} (and the existing {@link EnergyShare}/{@link EnergyServe}).
 *
 * <p>Package-private — part of the {@code EnergyNetwork} implementation; not a public API.
 */
final class EnergyTopologyCache {
	/**
	 * A cached neighbour storage endpoint: the position it lives at, the side the cable touches it, and
	 * the {@code host} whose energy it really is — {@code pos} itself for an ordinary block, the core of a
	 * multiblock for a cell that lends the core's port ({@link EnergyHostRedirect}, MOD-608). Resolved
	 * here, at refresh, so the per-tick pass counts each machine once without reading the world again.
	 */
	record Endpoint(BlockPos pos, Direction side, BlockPos host) {
	}

	/** Cached once — {@link Direction#values()} clones its array on every call (hot-path GC hygiene). */
	static final Direction[] DIRECTIONS = Direction.values();

	/**
	 * A stable geometric order for positions: see {@link PosOrder} for the rule, why it is x/y/z and
	 * emphatically NOT {@code BlockPos.asLong()}, and for the L1 tests that hold it in place.
	 */
	static final java.util.Comparator<BlockPos> BLOCK_POS_ORDER =
			(a, b) -> PosOrder.compare(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());

	private final ServerLevel level;
	/** The cables and the "refresh the endpoints" flag (MOD-715, batch 11). */
	private final NodeSet<BlockPos> nodes = new NodeSet<>();
	private final Set<BlockPos> cables = nodes.nodes();

	/** Cached endpoints, rebuilt on {@link #markDirty()} / any topology change. */
	private final List<Endpoint> producers = new ArrayList<>();
	private final List<Endpoint> consumers = new ArrayList<>();
	/** Cable-distance from each consumer position to its nearest producer, for per-consumer loss (MOD-021). */
	private final Map<BlockPos, Integer> consumerDistance = new LinkedHashMap<>();
	/**
	 * The three distance fields over this line's cables and what follows from them — the flow potential,
	 * the sweep order, the stranded cables ({@link FlowField}, MOD-715):
	 * <ul>
	 *   <li>the PRODUCER field is the source of {@link #consumerDistance}, i.e. the MOD-021 resistive loss —
	 *       "how far the EU travelled from a source" — and the fallback flow field while nothing wants energy
	 *       (MOD-070);</li>
	 *   <li>the SINK field, seeded from every endpoint that wants energy, directs the flow whenever anything
	 *       waits (MOD-252): a seam seeded from sinks drains toward both sides, one seeded from sources stalls
	 *       a whole stretch of bus behind it;</li>
	 *   <li>the MACHINE field is the sink field restricted to waiting machines — a fork tie-break, never a
	 *       path (MOD-254).</li>
	 * </ul>
	 * Seeded here, where the endpoints and their face ports are known; rebuilt only when a seed set changes.
	 */
	private final FlowField<BlockPos> flow = new FlowField<>(this::cableNeighbours, BLOCK_POS_ORDER);
	/**
	 * The strongest cable grade in the network, recomputed with the endpoint lists in {@link
	 * #refreshIfDirty()} (a cable's grade is fixed at construction, so it only changes when the cable set
	 * does). Cached so {@link #strongestCable()} — read once per tick for the packet cap AND the loss
	 * rate — is O(1) and does not rescan every cable's block entity every tick.
	 */
	private CableType cachedStrongestCable = CableType.COPPER;

	EnergyTopologyCache(ServerLevel level) {
		this.level = level;
	}

	ServerLevel level() {
		return level;
	}

	Set<BlockPos> cables() {
		return cables;
	}

	int size() {
		return cables.size();
	}

	boolean contains(BlockPos pos) {
		return cables.contains(pos);
	}

	boolean isEmpty() {
		return cables.isEmpty();
	}

	void addCable(BlockPos pos) {
		nodes.add(pos.immutable());
	}

	void removeCable(BlockPos pos) {
		nodes.remove(pos);
	}

	/** Absorb another topology's cables into this one (union-find merge). */
	void absorb(EnergyTopologyCache other) {
		nodes.absorb(other.nodes);
	}

	/** Force an endpoint recache on the next read (neighbour changed, cable added/removed). */
	void markDirty() {
		nodes.markDirty();
	}

	boolean endpointsDirty() {
		return nodes.isDirty();
	}

	/**
	 * Producers that actually held EU on the most recent tick (MOD-214). The distance field is seeded
	 * from THESE, not from every extraction-capable face: {@code producers} is a capability list, so an
	 * idle source — a moonlit solar panel in daylight, an unfuelled generator, a water mill in still
	 * water — used to seed distance 1 all along its stretch of bus. Two adjacent cables at equal distance
	 * cannot exchange, so the fill front died at that seam and everything past it (a Battery Box at the
	 * end of the run) stayed at 0 EU forever while the cables by the working generator read full.
	 * Empty means "no live supply known" — then the field falls back to all producers, which keeps a
	 * producer-only line filling exactly as before.
	 */
	private Set<BlockPos> supplyingProducers = Set.of();

	/**
	 * Endpoints that actually want energy this tick — the seeds of the sink field (MOD-252).
	 * Published by the façade: every endpoint with room, machines and storage sinks alike, because seeding
	 * decides which cables are REACHABLE at all, not who is served first (a cable above every
	 * source-adjacent potential has no filling path). MOD-009's class priority lives in the serve order
	 * instead. Empty means "nobody is waiting", which is what puts the network back on the producer-seeded
	 * fallback field.
	 */
	private Set<BlockPos> flowSinkSeeds = Set.of();

	/**
	 * The machine subset of {@link #flowSinkSeeds} — the seeds of the machine field (MOD-254).
	 * Always a subset, so it can only ever bias a split, never open or close a path.
	 */
	private Set<BlockPos> flowMachineSeeds = Set.of();

	/**
	 * Publish this tick's live supply and demand endpoints. Recomputes each distance field only when its
	 * own seed set actually changed — supply changes on day/night, fuel running out, a battery emptying;
	 * demand changes when a machine tops off or starts working. Both are rare next to every tick, and the
	 * BFS + sort behind them is O(cables · log cables).
	 *
	 * @param machineSeeds the machine subset of {@code sinkSeeds} (MOD-254); it seeds the fork tie-break
	 *     field only and never takes part in choosing the propagation order or the reachable set.
	 */
	/**
	 * The cables no downhill path can ever reach, farthest from the source first (MOD-318).
	 *
	 * <p>{@code propagateLineOneHop} only ever moves EU to a STRICTLY lower flow potential, and a cable is
	 * only ever charged directly if it touches a producer. In sink mode the potential is the distance to
	 * the nearest waiting sink, so the set of cables that can be filled at all is exactly "reachable from
	 * a supplying producer's own cable by strictly descending sink distance" — the union of the shortest
	 * paths from the generators to the demand. Everything else — a spur with no consumer on it, the run
	 * past the last consumer, a stretch behind the generators — sits above every cable that could feed it
	 * and stays at 0 EU for as long as anything on the network is asking for energy. That is MOD-318: the
	 * player sees a dead segment in the middle of a line whose far end is demonstrably powered.
	 *
	 * <p>The condition is topological, not geometric: a long line that simply runs from a generator to a
	 * consumer is entirely reachable however much it climbs and dives, and is not affected by any of this.
	 *
	 * <p>Empty in fallback mode by construction — with nothing waiting, the field is already seeded from
	 * the producers and spreads outward to every cable, which is the behaviour this list exists to restore
	 * for the sink-mode case.
	 */
	List<BlockPos> strandedFillOrder() {
		return flow.strandedOrder();
	}

	/**
	 * BFS distance from this cable to the nearest supplying producer, or {@code null} when it is off that
	 * field. Drives the stranded-fill hop direction (MOD-318) — outward from the source, the one direction
	 * that is well defined for a cable the demand-seeded field has abandoned.
	 *
	 * <p>Does not refresh the cache — see {@link #consumerDistance(BlockPos)} for why.
	 */
	Integer producerDistanceOrNull(BlockPos pos) {
		return flow.producerDistance(pos);
	}

	void updateLiveEndpoints(Set<BlockPos> supplying, Set<BlockPos> sinkSeeds, Set<BlockPos> machineSeeds) {
		refreshIfDirty();
		boolean changed = false;
		if (!supplyingProducers.equals(supplying)) {
			supplyingProducers = orderedCopy(supplying);
			computeProducerField();
			changed = true;
		}
		if (!flowSinkSeeds.equals(sinkSeeds)) {
			flowSinkSeeds = orderedCopy(sinkSeeds);
			computeSinkField();
			changed = true;
		}
		if (!flowMachineSeeds.equals(machineSeeds)) {
			flowMachineSeeds = orderedCopy(machineSeeds);
			computeMachineField();
		}
		if (changed) {
			rebuildFlowOrder();
		}
	}

	/**
	 * Defensive copy that KEEPS the caller's order (MOD-304 round 2).
	 *
	 * <p>This deliberately does not use {@code Set.copyOf}. That returns a
	 * {@code java.util.ImmutableCollections.SetN} whose iteration order is randomised by a per-JVM salt,
	 * so it threw away the order the caller had carefully built in a {@link LinkedHashSet} and replaced it
	 * with one that differs between runs of the same world. That mattered here precisely because of the
	 * other half of MOD-304: {@code sinkDistance} is now a {@link LinkedHashMap}, so its key order is the
	 * BFS insertion order, which is seeded from these sets, and {@code rebuildFlowOrder} then breaks
	 * equal-distance ties with a STABLE sort — i.e. by insertion order. A salted seed order therefore
	 * leaked all the way into "which of two equal donors fills the shared neighbour", which is the exact
	 * non-determinism this task exists to remove.
	 */
	private static Set<BlockPos> orderedCopy(Set<BlockPos> source) {
		return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(source));
	}

	List<Endpoint> producers() {
		refreshIfDirty();
		return producers;
	}

	List<Endpoint> consumers() {
		refreshIfDirty();
		return consumers;
	}

	/**
	 * MOD-021 loss distance for a consumer. Deliberately does NOT refresh the cache: it is called from
	 * inside the distributor's sweep over the live {@link #propagationOrder()} list, and a refresh there
	 * would {@code clear()+addAll()} that very list mid-iteration. The contract is that
	 * {@code EnergyNetwork.tick()} has already refreshed (it reads {@link #producers()},
	 * {@link #consumers()}, {@link #strongestCable()} and calls {@link #updateLiveEndpoints}) before any
	 * distributor exists.
	 */
	int consumerDistance(BlockPos pos) {
		return consumerDistance.getOrDefault(pos, 0);
	}

	/**
	 * The flow potential of a cable, or {@code null} if it is not on the active field. Energy is pulled
	 * from strictly HIGHER potential to lower, and the sweep visits cables in ascending potential — one
	 * rule for both modes (MOD-252):
	 * <ul>
	 *   <li>sink mode: potential = distance to the nearest waiting sink, so "downhill" is toward demand;</li>
	 *   <li>fallback: potential = <em>minus</em> the distance to the nearest supplying producer, so
	 *       "downhill" is away from the source — bit for bit the pre-MOD-252 behaviour (pulling from a
	 *       strictly smaller producer-distance is exactly pulling from a strictly larger negated one),
	 *       which is what keeps MOD-070's producer-only line fill unchanged.</li>
	 * </ul>
	 *
	 * <p>Does not refresh the cache — see {@link #consumerDistance(BlockPos)} for why.
	 */
	Integer flowPotentialOrNull(BlockPos pos) {
		return flow.flowPotential(pos);
	}

	/**
	 * Distance from a cable to the nearest waiting machine, or {@code null} when no machine is waiting (or
	 * the cable is off that field). Read only as a fork tie-break in the distribution kernel (MOD-254):
	 * a claimant whose machine distance is strictly below the donor's is carrying the unit toward a
	 * machine and outweighs one that is not. Null everywhere on a network with no hungry machine, which
	 * makes every claimant weigh the same and reproduces the plain proportional split.
	 *
	 * <p>Does not refresh the cache — see {@link #consumerDistance(BlockPos)} for why.
	 */
	Integer machinePotentialOrNull(BlockPos pos) {
		return flow.machinePotential(pos);
	}

	List<BlockPos> propagationOrder() {
		refreshIfDirty();
		return flow.propagationOrder();
	}

	/** True if the network has at least one non-storage-sink producer (a generator that fills the line). */
	boolean hasGenerator(java.util.function.Predicate<BlockPos> isStorageSink) {
		for (Endpoint ep : producers()) {
			if (!isStorageSink.test(ep.pos())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The strongest cable grade present in this network — primarily the one with the highest packet cap.
	 * Equal-cap bare/insulated variants are ordered conservatively so a bare segment governs the mixed
	 * run instead of leaving the result to {@code HashSet} iteration order (MOD-259). It governs BOTH the
	 * per-tick packet cap and the per-block loss rate of the whole line: splice one gold segment
	 * into a copper run and the entire network moves to gold's 128 EU/t cap <b>and</b> to gold's 0.03
	 * loss. That is the deliberate extension of the tier rule established in MOD-101 — taking the cap from
	 * the strongest cable but the loss from the weakest (or from copper always) would let a player buy a
	 * high cap at a cheap cable's loss with a single spliced segment.
	 *
	 * <p>An empty network falls back to {@link CableType#COPPER}, which reproduces the historical LV/32 +
	 * 0.02 behaviour exactly.
	 *
	 * <p>Returns the value cached by {@link #refreshIfDirty()} — the O(cables) {@code
	 * level.getBlockEntity} scan runs only when the cache rebuilds (on a topology change), not per tick.
	 * The grade of a {@link CableNode} is fixed at construction, so
	 * the result is stable between topology changes.
	 */
	CableType strongestCable() {
		refreshIfDirty();
		return cachedStrongestCable;
	}

	/** Rebuild the cached producer/consumer endpoint lists from the cables' non-cable neighbours. */
	private void refreshIfDirty() {
		if (!nodes.isDirty()) {
			return;
		}
		producers.clear();
		consumers.clear();
		// Starts null, NOT at COPPER: seeding with copper would make an all-tin network (whose packet cap,
		// 8, is below copper's 32) silently keep copper's numbers — the exact "recoloured copper" bug this
		// whole change exists to kill. CableType.strongerThan also resolves equal-cap insulation ties
		// deterministically. Only a network with no loadable cable at all falls back to copper.
		CableType strongest = null;
		Set<BlockPos> seenProducer = new LinkedHashSet<>();
		Set<BlockPos> seenConsumer = new LinkedHashSet<>();
		EnergyLookup lookup = EnergyLookup.get();
		// MOD-304 — walk the cables in GEOMETRIC order, not in set order.
		//
		// This loop's order fixes the order of producers/consumers, which in turn fixes the delivery
		// order and how ties at equal distance are broken. While a HashSet was iterated here, that order
		// was BlockPos.hashCode() over ABSOLUTE coordinates: the very same layout delivered energy
		// differently at x=100 and at x=5000, and the test rigs — which the world lays out at fresh
		// coordinates every run — were green alone and red in the full suite. A LinkedHashSet would drop
		// the hash dependency but keep a dependency on the order the network happened to be built in.
		// Sorting is translation-invariant, which is the property actually wanted here: the same layout
		// behaves the same wherever it stands. (See BLOCK_POS_ORDER for why the comparator is x/y/z and
		// emphatically NOT asLong.)
		List<BlockPos> orderedCables = new ArrayList<>(cables);
		orderedCables.sort(BLOCK_POS_ORDER);
		for (BlockPos cable : orderedCables) {
			if (level.getBlockEntity(cable) instanceof CableNode ce
					&& (strongest == null || ce.cableType().strongerThan(strongest))) {
				strongest = ce.cableType();
			}
			for (Direction dir : DIRECTIONS) {
				BlockPos np = cable.relative(dir);
				if (cables.contains(np)) {
					continue; // cable-to-cable link, not an endpoint
				}
				EnergyPort storage = lookup.find(level, np, dir.getOpposite());
				if (storage == null) {
					continue;
				}
				// The lookup already resolved the same host to find this port, so this is non-null here;
				// the fallback only keeps a racing block change from turning into a null key downstream.
				BlockPos host = EnergyHostRedirect.hostOf(level, np, dir.getOpposite());
				Endpoint endpoint = new Endpoint(np, dir.getOpposite(), host != null ? host : np);
				if (storage.supportsExtraction() && seenProducer.add(np)) {
					producers.add(endpoint);
				}
				if (storage.supportsInsertion() && seenConsumer.add(np)) {
					consumers.add(endpoint);
				}
			}
		}
		cachedStrongestCable = strongest != null ? strongest : CableType.COPPER;
		nodes.clearDirty();
		computeProducerField();
		computeSinkField();
		computeMachineField();
		rebuildFlowOrder();
	}

	/**
	 * Multi-source BFS over the cable graph: seed every cable touching a supplying producer at distance 1,
	 * flood the connected component, then record each consumer's distance as the minimum over its adjacent
	 * cables. Feeds the per-consumer cable loss in the distribution kernel (MOD-021), and the fallback flow
	 * field for a line with nothing to deliver to (MOD-070). Distance is to the <em>nearest</em> producer —
	 * an intentional approximation, since the round-robin pull may draw from any producer; for the common
	 * single-producer network it is exact.
	 */
	private void computeProducerField() {
		consumerDistance.clear();
		DistanceField<BlockPos> field = flow.producer();
		field.clear();
		// Producer distances are computed whenever there is a source — even with no consumer, so a
		// producer-only line still fills fully (the fallback field spreads the charge outward from the
		// source, not just into producer-adjacent cables).
		if (producers.isEmpty()) {
			return;
		}
		// MOD-214: seed from producers that actually supply. Falling back to all of them when nothing is
		// known keeps the very first tick (and a producer-only line) behaving exactly as before.
		for (Endpoint producer : producers) {
			if (!supplyingProducers.isEmpty() && !supplyingProducers.contains(producer.pos())) {
				continue;
			}
			for (Direction dir : DIRECTIONS) {
				BlockPos cable = producer.pos().relative(dir);
				if (cables.contains(cable)) {
					field.seed(cable, 1);
				}
			}
		}
		field.flood();
		for (Endpoint consumer : consumers) {
			int best = 0;
			for (Direction dir : DIRECTIONS) {
				Integer d = field.distanceOrNull(consumer.pos().relative(dir));
				if (d != null && (best == 0 || d < best)) {
					best = d;
				}
			}
			if (best > 0) {
				consumerDistance.put(consumer.pos(), best);
			}
		}
	}

	/**
	 * The sink field, seeded from {@link #flowSinkSeeds} — the endpoints that want energy this tick
	 * (MOD-252). A seed only injects through the faces that can actually ACCEPT energy: a dual-role Battery
	 * Box must not turn the cable on its output face into a "downhill" target it can never drink from. An
	 * empty seed set leaves the field empty, which is exactly the MOD-070 fallback case.
	 */
	private void computeSinkField() {
		floodFromSinks(flowSinkSeeds, flow.sink());
	}

	/**
	 * The same flood restricted to the machine seeds (MOD-254) — the fork tie-break field behind
	 * {@link #machinePotentialOrNull}. Never consulted for reachability or sweep order.
	 */
	private void computeMachineField() {
		floodFromSinks(flowMachineSeeds, flow.machine());
	}

	/** Seed {@code field} at distance 1 on every cable an accepting seed face touches, then flood. */
	private void floodFromSinks(Set<BlockPos> seeds, DistanceField<BlockPos> field) {
		field.clear();
		if (seeds.isEmpty()) {
			return;
		}
		EnergyLookup lookup = EnergyLookup.get();
		for (BlockPos seed : seeds) {
			for (Direction dir : DIRECTIONS) {
				// `dir` runs from the endpoint toward the cable, which is exactly the face key the lookup
				// wants (mirrors refreshIfDirty's find(level, np, dir.getOpposite())).
				BlockPos cable = seed.relative(dir);
				if (!cables.contains(cable)) {
					continue;
				}
				EnergyPort port = lookup.find(level, seed, dir);
				if (port == null || !port.supportsInsertion()) {
					continue;
				}
				field.seed(cable, 1);
			}
		}
		field.flood();
	}

	/** The cables one hop from {@code pos}, in {@link Direction} order — the graph the fields flood. */
	private List<BlockPos> cableNeighbours(BlockPos pos) {
		List<BlockPos> out = new ArrayList<>(DIRECTIONS.length);
		for (Direction dir : DIRECTIONS) {
			BlockPos np = pos.relative(dir);
			if (cables.contains(np)) {
				out.add(np);
			}
		}
		return out;
	}

	/** Re-derive the sweep order and the stranded cables after a field changed ({@link FlowField#rebuild}). */
	private void rebuildFlowOrder() {
		flow.rebuild(cables);
	}
}
