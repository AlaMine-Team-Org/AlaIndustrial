package dev.alaindustrial.core.energy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * This tick's live endpoints of one energy network, resolved from the cached endpoint lists (MOD-715,
 * batch 6): who can supply and how much, who is waiting and how much. Taken out of {@code EnergyNetwork.tick}
 * so the tick keeps only the order of the steps; the rules each step applies are unchanged and are the ones
 * written down at each loop below.
 *
 * <p>Both halves dry-run the ports through {@link EnergyTransactions#simulate}, outside the committing
 * transaction the tick opens afterwards. Also the one place the kernel's numbered faces meet {@link Direction}
 * ({@link #BLOCK_FACES}, MOD-715 batch 8): the kernel is generic, and this class is its {@code BlockPos} side.
 */
final class LineEndpoints {

	private static final Direction[] DIRECTIONS = Direction.values();

	/**
	 * A block's six faces as the kernel's numbered steps ({@link LineView#faces()}): face {@code i} steps toward
	 * {@code Direction.values()[i]}, the order the kernel walked while it named the directions itself.
	 */
	static final List<UnaryOperator<BlockPos>> BLOCK_FACES = Arrays.stream(DIRECTIONS)
			.map(dir -> (UnaryOperator<BlockPos>) pos -> pos.relative(dir)).toList();

	/** The {@link Direction} of face {@code face} of {@link #BLOCK_FACES}. */
	static Direction direction(int face) {
		return DIRECTIONS[face];
	}

	private LineEndpoints() {
	}

	/**
	 * The supply side, split by source priority: pure generators vs storage sources (a dual-role Battery Box
	 * with a cabled OUT face). Generators feed the line and charge storage; a store discharges through one
	 * of three mutually exclusive channels — why three and not one is ADR-004.
	 *
	 * @param storagePositions the dual-role nodes, so the sink pass can tell a battery that is discharging
	 *     into this very line from an ordinary consumer (ADR-002: a node that donates must not also be
	 *     served, or it drinks its own discharge back out of the neighbouring cable)
	 * @param supplying the producers that actually HOLD EU this tick — the seeds of the flow field, not every
	 *     face capable of extraction (ADR-003, point 2)
	 * @param genSupply the generators' supply, each host counted once (MOD-608)
	 */
	record Supply(List<EnergyLineDistributor.LiveProducer<BlockPos>> generators,
			List<EnergyLineDistributor.LiveProducer<BlockPos>> storageSources, Set<BlockPos> storagePositions,
			Set<BlockPos> supplying, long genSupply) {

		/** No live producer at all this tick. */
		boolean isEmpty() {
			return generators.isEmpty() && storageSources.isEmpty();
		}
	}

	/**
	 * The demand side, partitioned: pure machines (served first, MOD-009) vs storage sinks (served after
	 * them, ADR-002). {@link #sinks()} is mutable: the discharge plan removes from it the stores that sit this
	 * tick out because they discharge into the line.
	 */
	record Demand(List<EnergyLineDistributor.LiveConsumer<BlockPos>> machines,
			List<EnergyLineDistributor.LiveConsumer<BlockPos>> sinks, long machineDemand) {

		/**
		 * Every endpoint that wants energy this tick — machines AND storage sinks — the seeds of the flow
		 * field. Seeding is about REACHABILITY, not priority: narrowing it to machines fences off the stretch
		 * of bus lying past the source, and a store out there never fills. Class priority lives in the serve
		 * order instead. Empty means nobody is waiting: the network falls back to the producer-seeded field
		 * (ADR-003, ADR-001).
		 */
		Set<BlockPos> flowSeeds() {
			Set<BlockPos> seeds = new LinkedHashSet<>();
			for (EnergyLineDistributor.LiveConsumer<BlockPos> c : machines) {
				seeds.add(c.pos());
			}
			for (EnergyLineDistributor.LiveConsumer<BlockPos> c : sinks) {
				seeds.add(c.pos());
			}
			return seeds;
		}

		/**
		 * The machine subset of {@link #flowSeeds()}: the fork tie-break seed set. It only weighs how a forked
		 * buffer is split, never who may be filled (ADR-003, point 3).
		 */
		Set<BlockPos> machineSeeds() {
			Set<BlockPos> seeds = new LinkedHashSet<>();
			for (EnergyLineDistributor.LiveConsumer<BlockPos> c : machines) {
				seeds.add(c.pos());
			}
			return seeds;
		}
	}

	/** Resolve the producers' live ports and dry-run what each can give this tick. */
	static Supply supply(List<EnergyTopologyCache.Endpoint> producers,
			Function<EnergyTopologyCache.Endpoint, EnergyPort> portAt, Predicate<BlockPos> isStorageSink) {
		long genSupply = 0;
		Set<BlockPos> supplying = new LinkedHashSet<>();
		List<EnergyLineDistributor.LiveProducer<BlockPos>> generators = new ArrayList<>(producers.size());
		List<EnergyLineDistributor.LiveProducer<BlockPos>> storageSources = new ArrayList<>();
		Set<BlockPos> storagePositions = new LinkedHashSet<>();
		// Hosts whose supply is already in genSupply (MOD-608): the cells of one multiblock are separate
		// endpoints over ONE buffer, and summing each of them would promise the storage stage energy that
		// exists once — batteries would then sit idle while machines go short.
		Set<BlockPos> countedHosts = new LinkedHashSet<>();
		for (EnergyTopologyCache.Endpoint ep : producers) {
			EnergyPort st = portAt.apply(ep);
			if (st == null || !st.supportsExtraction()) {
				continue;
			}
			if (isStorageSink.test(ep.pos())) {
				storageSources.add(new EnergyLineDistributor.LiveProducer<>(ep.pos(), st));
				storagePositions.add(ep.pos());
			} else {
				generators.add(new EnergyLineDistributor.LiveProducer<>(ep.pos(), st, ep.host()));
				long supply = EnergyTransactions.get().simulate(sim -> st.extract(Long.MAX_VALUE, sim));
				if (countedHosts.add(ep.host())) {
					genSupply += supply;
				}
				if (supply > 0) {
					supplying.add(ep.pos());
				}
			}
		}
		return new Supply(generators, storageSources, storagePositions, supplying, genSupply);
	}

	/**
	 * Resolve the consumers' live ports and dry-run how much each would take this tick. A consumer with no
	 * room is not waiting. No early-out when nobody waits: a producer-only network still fills its line to
	 * the buffer capacity — the wire holds and shows the energy with nowhere to deliver it (MOD-070).
	 */
	static Demand demand(List<EnergyTopologyCache.Endpoint> consumers,
			Function<EnergyTopologyCache.Endpoint, EnergyPort> portAt, Predicate<BlockPos> isStorageSink) {
		List<EnergyLineDistributor.LiveConsumer<BlockPos>> machines = new ArrayList<>(consumers.size());
		List<EnergyLineDistributor.LiveConsumer<BlockPos>> sinks = new ArrayList<>(consumers.size());
		long machineDemand = 0;
		for (EnergyTopologyCache.Endpoint ep : consumers) {
			EnergyPort st = portAt.apply(ep);
			if (st == null || !st.supportsInsertion()) {
				continue;
			}
			long r = EnergyTransactions.get().simulate(sim -> st.insert(Long.MAX_VALUE, sim));
			if (r <= 0) {
				continue;
			}
			EnergyLineDistributor.LiveConsumer<BlockPos> c = new EnergyLineDistributor.LiveConsumer<>(ep.pos(), st, r);
			if (isStorageSink.test(ep.pos())) {
				sinks.add(c);
			} else {
				machines.add(c);
				machineDemand += r;
			}
		}
		return new Demand(machines, sinks, machineDemand);
	}
}
