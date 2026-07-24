package dev.alaindustrial.core.energy;

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
 * Topology + endpoint-discovery half of an {@link EnergyNetwork}. Extracted from {@code EnergyNetwork}
 * so the per-tick distribution kernel can stay small and (in its MC-free extracts) L1-testable.
 *
 * <p>Owns: the cable set, the cached producer/consumer endpoint lists with their cable-distances,
 * the BFS that produces those distances, and the descending-distance propagation sweep order. All
 * mutation routes through {@link #addCable}/{@link #removeCable}/{@link #absorb}/{@link #markDirty},
 * which set {@link #endpointsDirty} so the next read refreshes the cache. {@code EnergyNetwork}
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
	/** A cached neighbour storage endpoint: the position it lives at and the side the cable touches it. */
	record Endpoint(BlockPos pos, Direction side) {
	}

	/** Cached once — {@link Direction#values()} clones its array on every call (hot-path GC hygiene). */
	static final Direction[] DIRECTIONS = Direction.values();

	private final ServerLevel level;
	private final Set<BlockPos> cables = new HashSet<>();

	/** Cached endpoints, rebuilt on {@link #markDirty()} / any topology change. */
	private final List<Endpoint> producers = new ArrayList<>();
	private final List<Endpoint> consumers = new ArrayList<>();
	/** Cable-distance from each consumer position to its nearest producer, for per-consumer loss (MOD-021). */
	private final Map<BlockPos, Integer> consumerDistance = new HashMap<>();
	/**
	 * Cable-distance from each cable position to the nearest producer (BFS over the cable graph, MOD-070).
	 * Drives the per-tick line propagation ordering: energy flows from lower-distance cables to
	 * higher-distance ones (away from producers, toward consumers).
	 */
	private final Map<BlockPos, Integer> cableDistance = new HashMap<>();
	/**
	 * Cables in descending {@link #cableDistance} order — the fixed per-tick propagation sweep order
	 * (MOD-070). Rebuilt only on topology change (in {@link #computeConsumerDistances}); iterating it
	 * avoids re-sorting {@code cableDistance} every tick (a hot-path allocation + boxing the audit flagged).
	 */
	private final List<BlockPos> propagationOrder = new ArrayList<>();
	/**
	 * The strongest cable grade in the network, recomputed with the endpoint lists in {@link
	 * #refreshIfDirty()} (a cable's grade is fixed at construction, so it only changes when the cable set
	 * does). Cached so {@link #strongestCable()} — read once per tick for the packet cap AND the loss
	 * rate — is O(1) and does not rescan every cable's block entity every tick.
	 */
	private CableType cachedStrongestCable = CableType.COPPER;
	private boolean endpointsDirty = true;

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
		if (cables.add(pos.immutable())) {
			endpointsDirty = true;
		}
	}

	void removeCable(BlockPos pos) {
		if (cables.remove(pos)) {
			endpointsDirty = true;
		}
	}

	/** Absorb another topology's cables into this one (union-find merge). */
	void absorb(EnergyTopologyCache other) {
		cables.addAll(other.cables);
		endpointsDirty = true;
	}

	/** Force an endpoint recache on the next read (neighbour changed, cable added/removed). */
	void markDirty() {
		endpointsDirty = true;
	}

	boolean endpointsDirty() {
		return endpointsDirty;
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
	 * Publish this tick's live-supply set. Recomputes the distance field only when the set actually
	 * changed — it changes on day/night, fuel running out, a battery emptying: rare next to every tick.
	 */
	void updateSupplyingProducers(Set<BlockPos> supplying) {
		refreshIfDirty();
		if (supplyingProducers.equals(supplying)) {
			return;
		}
		supplyingProducers = Set.copyOf(supplying);
		computeConsumerDistances();
	}

	List<Endpoint> producers() {
		refreshIfDirty();
		return producers;
	}

	List<Endpoint> consumers() {
		refreshIfDirty();
		return consumers;
	}

	int consumerDistance(BlockPos pos) {
		refreshIfDirty();
		return consumerDistance.getOrDefault(pos, 0);
	}

	Integer cableDistanceOrNull(BlockPos pos) {
		refreshIfDirty();
		return cableDistance.get(pos);
	}

	List<BlockPos> propagationOrder() {
		refreshIfDirty();
		return propagationOrder;
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
	 * The strongest cable grade present in this network — the one with the highest packet cap. It governs
	 * BOTH the per-tick packet cap and the per-block loss rate of the whole line: splice one gold segment
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
	 * The grade of a {@link dev.alaindustrial.block.entity.CableBlockEntity} is fixed at construction, so
	 * the result is stable between topology changes.
	 */
	CableType strongestCable() {
		refreshIfDirty();
		return cachedStrongestCable;
	}

	/** Rebuild the cached producer/consumer endpoint lists from the cables' non-cable neighbours. */
	private void refreshIfDirty() {
		if (!endpointsDirty) {
			return;
		}
		producers.clear();
		consumers.clear();
		// Starts null, NOT at COPPER: seeding with copper would make an all-tin network (whose packet cap,
		// 8, is below copper's 32) silently keep copper's numbers — the exact "recoloured copper" bug this
		// whole change exists to kill. Only a network with no loadable cable at all falls back to copper.
		CableType strongest = null;
		Set<BlockPos> seenProducer = new HashSet<>();
		Set<BlockPos> seenConsumer = new HashSet<>();
		EnergyLookup lookup = EnergyLookup.get();
		for (BlockPos cable : cables) {
			if (level.getBlockEntity(cable) instanceof dev.alaindustrial.block.entity.CableBlockEntity ce
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
				if (storage.supportsExtraction() && seenProducer.add(np)) {
					producers.add(new Endpoint(np, dir.getOpposite()));
				}
				if (storage.supportsInsertion() && seenConsumer.add(np)) {
					consumers.add(new Endpoint(np, dir.getOpposite()));
				}
			}
		}
		cachedStrongestCable = strongest != null ? strongest : CableType.COPPER;
		endpointsDirty = false;
		computeConsumerDistances();
	}

	/**
	 * Multi-source BFS over the cable graph: seed every cable touching a producer at distance 1, flood
	 * the connected component, then record each consumer's distance as the minimum over its adjacent
	 * cables. Feeds the per-consumer cable loss in the distribution kernel (MOD-021). Distance is to
	 * the <em>nearest</em> producer — an intentional approximation, since the round-robin pull may draw
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
		// MOD-214: seed from producers that actually supply. Falling back to all of them when nothing is
		// known keeps the very first tick (and a producer-only line) behaving exactly as before.
		List<Endpoint> seeds = producers.stream()
				.filter(p -> supplyingProducers.isEmpty() || supplyingProducers.contains(p.pos()))
				.toList();
		for (Endpoint producer : seeds) {
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
}
