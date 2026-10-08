package dev.alaindustrial.core.energy;

import dev.alaindustrial.core.net.DistanceField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A reference circuit for {@link EnergyLineDistributorGoldenTest}: cables, endpoints, and a replica of
 * {@code EnergyNetwork.tick()} with the {@code EnergyTopologyCache} fields it feeds the kernel (MOD-715).
 *
 * <p>The replica follows the production rules step for step — endpoints discovered walking the cables in
 * geometric order, the producer field seeded from supplying producers, the sink and machine fields seeded
 * from every waiting endpoint, the stranded order handed over only while a generator supplies, the three
 * discharge channels and their sink exclusion — but it is a COPY, kept here so the kernel can be driven
 * without a world. Two rules are not copies: the replica calls {@code DischargePlan.setAside} for the sinks
 * that sit the tick out and those of them that draw the generators' surplus apart (MOD-731), and its fields,
 * sweep order and stranded cables are production's own {@link FlowField}
 * (MOD-730) — a copy of either rule would pin the golden to the old one. It is the input side of the golden; the real
 * cache and orchestration are pinned end to end by {@code EnergyFlowFieldGoldenScenarios} and
 * {@code DischargeChannelBoundaryScenarios}. Every face
 * accepts and emits, which is how a block whose faces are all role BOTH behaves.
 */
final class GoldenRig {
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final long PACKET_CAP = 32;
	private static final long CABLE_BUFFER = 12;
	private static final double LOSS = 0.02;
	private static final double FEED_RESERVE = 0.5;

	/** One endpoint: a block next to the cables, with its own per-tick production and consumption. */
	private record End(String name, BlockPos pos, EnergyBuffer buf, BlockPos host, boolean storage,
			boolean cascade, long feedRate, long produce, long drain) {
	}

	private final String name;
	private final Set<BlockPos> cables = new LinkedHashSet<>();
	private final Map<BlockPos, EnergyBuffer> buffers = new LinkedHashMap<>();
	private final List<End> ends = new ArrayList<>();
	/** Production's fields over this rig's cables: flow potential, sweep order, stranded cables (MOD-730). */
	private final FlowField<BlockPos> flow =
			new FlowField<>(this::cableNeighbours, EnergyTopologyCache.BLOCK_POS_ORDER);
	private final Map<BlockPos, Integer> consumerDistance = new LinkedHashMap<>();
	private final List<End> producers = new ArrayList<>();
	private final List<End> consumers = new ArrayList<>();
	private boolean endpointsKnown;
	private Set<BlockPos> supplying = Set.of();
	private Set<BlockPos> sinkSeeds = Set.of();
	private Set<BlockPos> machineSeeds = Set.of();
	private int cursor;
	private long lastMoved;
	private long lastDrawn;
	/** {@code EnergyNetwork.generatorSurplus}: generator injection beyond the machines' draw, last tick. */
	private long generatorSurplus;
	/** {@code EnergyNetwork.backupEpisode} and {@code sinkCredit}: the backup episode's ledger (MOD-756). */
	private boolean backupEpisode;
	private long sinkCredit;

	/** The blocks around the line as the discharge plan asks for them — production's {@code decide} reads them. */
	private final DischargePlan.Stores<BlockPos> planStores = new DischargePlan.Stores<>() {
		@Override
		public boolean acceptsCascade(BlockPos pos) {
			return byPos(pos).cascade();
		}

		@Override
		public long feedRate(BlockPos pos) {
			return byPos(pos).feedRate();
		}

		@Override
		public long inFlight() {
			long inFlight = 0;
			for (EnergyBuffer buf : buffers.values()) {
				inFlight += buf.getAmount();
			}
			return inFlight;
		}

		@Override
		public long generatorSurplus() {
			return generatorSurplus;
		}

		@Override
		public boolean backupEpisode() {
			return backupEpisode;
		}

		@Override
		public long sinkCredit() {
			return sinkCredit;
		}
	};

	GoldenRig(String name) {
		this.name = name;
	}

	void cable(BlockPos pos) {
		cables.add(pos);
		buffers.put(pos, new EnergyBuffer(CABLE_BUFFER, CABLE_BUFFER, CABLE_BUFFER, () -> { }));
	}

	/** A generator making {@code produce} EU/t into its own buffer, emitting up to {@code maxOut}. */
	EnergyBuffer generator(String id, BlockPos pos, long capacity, long maxOut, long produce) {
		EnergyBuffer buf = new EnergyBuffer(capacity, 0, maxOut, () -> { });
		ends.add(new End(id, pos, buf, pos, false, false, 0, produce, 0));
		return buf;
	}

	/** A second cell of a multiblock lending {@code host}'s buffer (MOD-608). */
	void cell(String id, BlockPos pos, EnergyBuffer shared, BlockPos host) {
		ends.add(new End(id, pos, shared, host, false, false, 0, 0, 0));
	}

	/** A machine taking up to {@code maxIn} EU/t and spending {@code drain} EU/t on its work. */
	void machine(String id, BlockPos pos, long capacity, long maxIn, long drain) {
		ends.add(new End(id, pos, new EnergyBuffer(capacity, maxIn, 0, () -> { }), pos, false, false, 0, 0,
				drain));
	}

	/** A store that both feeds and is fed, takes part in the cascade (a Battery Box). */
	void store(String id, BlockPos pos, long capacity, long maxIn, long maxOut, long initial) {
		EnergyBuffer buf = new EnergyBuffer(capacity, maxIn, maxOut, () -> { });
		buf.setAmountUntracked(initial);
		ends.add(new End(id, pos, buf, pos, true, true, 0, 0, 0));
	}

	/** A storage sink outside the cascade, fed at a flat rate (a Teleporter, MOD-353). */
	void fund(String id, BlockPos pos, long capacity, long maxIn, long feedRate) {
		ends.add(new End(id, pos, new EnergyBuffer(capacity, maxIn, 0, () -> { }), pos, true, false, feedRate,
				0, 0));
	}

	private End endAt(BlockPos pos) {
		for (End e : ends) {
			if (e.pos().equals(pos)) {
				return e;
			}
		}
		return null;
	}

	/** {@code EnergyTopologyCache.refreshIfDirty}: cables in geometric order, faces in enum order. */
	private void discoverEndpoints() {
		if (endpointsKnown) {
			return;
		}
		endpointsKnown = true;
		List<BlockPos> ordered = new ArrayList<>(cables);
		ordered.sort(EnergyTopologyCache.BLOCK_POS_ORDER);
		Set<BlockPos> seenProducer = new LinkedHashSet<>();
		Set<BlockPos> seenConsumer = new LinkedHashSet<>();
		for (BlockPos cable : ordered) {
			for (Direction dir : DIRECTIONS) {
				BlockPos np = cable.relative(dir);
				End e = cables.contains(np) ? null : endAt(np);
				if (e == null) {
					continue;
				}
				if (e.buf().supportsExtraction() && seenProducer.add(np)) {
					producers.add(e);
				}
				if (e.buf().supportsInsertion() && seenConsumer.add(np)) {
					consumers.add(e);
				}
			}
		}
		computeProducerField();
		floodFromSinks(sinkSeeds, flow.sink());
		floodFromSinks(machineSeeds, flow.machine());
		flow.rebuild(cables);
	}

	/** {@code EnergyTopologyCache.cableNeighbours}: the cables one hop away, in {@link Direction} order. */
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

	private void computeProducerField() {
		consumerDistance.clear();
		DistanceField<BlockPos> field = flow.producer();
		field.clear();
		for (End p : producers) {
			if (!supplying.isEmpty() && !supplying.contains(p.pos())) {
				continue;
			}
			for (Direction dir : DIRECTIONS) {
				BlockPos cable = p.pos().relative(dir);
				if (cables.contains(cable)) {
					field.seed(cable, 1);
				}
			}
		}
		field.flood();
		for (End c : consumers) {
			int best = 0;
			for (Direction dir : DIRECTIONS) {
				Integer d = flow.producerDistance(c.pos().relative(dir));
				if (d != null && (best == 0 || d < best)) {
					best = d;
				}
			}
			if (best > 0) {
				consumerDistance.put(c.pos(), best);
			}
		}
	}

	private void floodFromSinks(Set<BlockPos> seeds, DistanceField<BlockPos> field) {
		field.clear();
		for (BlockPos seed : seeds) {
			for (Direction dir : DIRECTIONS) {
				BlockPos cable = seed.relative(dir);
				if (cables.contains(cable)) {
					field.seed(cable, 1);
				}
			}
		}
		field.flood();
	}

	private void updateLiveEndpoints(Set<BlockPos> nowSupplying, Set<BlockPos> nowSinks, Set<BlockPos> nowMachines) {
		boolean changed = false;
		if (!supplying.equals(nowSupplying)) {
			supplying = new LinkedHashSet<>(nowSupplying);
			computeProducerField();
			changed = true;
		}
		if (!sinkSeeds.equals(nowSinks)) {
			sinkSeeds = new LinkedHashSet<>(nowSinks);
			floodFromSinks(sinkSeeds, flow.sink());
			changed = true;
		}
		if (!machineSeeds.equals(nowMachines)) {
			machineSeeds = new LinkedHashSet<>(nowMachines);
			floodFromSinks(machineSeeds, flow.machine());
		}
		if (changed) {
			flow.rebuild(cables);
		}
	}

	/** One network tick, preceded by every endpoint's own block-entity tick (production, then work). */
	void tick(EnergyPort.Txn txn) {
		for (End e : ends) {
			if (e.produce() > 0) {
				e.buf().produceInternal(e.produce());
			}
		}
		networkTick(txn);
		for (End e : ends) {
			if (e.drain() > 0) {
				e.buf().drainInternal(e.drain());
			}
		}
	}

	private void networkTick(EnergyPort.Txn txn) {
		lastMoved = 0;
		lastDrawn = 0;
		discoverEndpoints();
		if (producers.isEmpty()) {
			generatorSurplus = 0;
			return;
		}
		long genSupply = 0;
		Set<BlockPos> nowSupplying = new LinkedHashSet<>();
		List<EnergyLineDistributor.LiveProducer<BlockPos>> generators = new ArrayList<>();
		List<EnergyLineDistributor.LiveProducer<BlockPos>> stores = new ArrayList<>();
		Set<BlockPos> storePositions = new LinkedHashSet<>();
		Set<BlockPos> countedHosts = new LinkedHashSet<>();
		for (End p : producers) {
			if (p.storage()) {
				stores.add(new EnergyLineDistributor.LiveProducer<>(p.pos(), p.buf()));
				storePositions.add(p.pos());
				continue;
			}
			generators.add(new EnergyLineDistributor.LiveProducer<>(p.pos(), p.buf(), p.host()));
			long supply = Math.min(p.buf().getAmount(), p.buf().maxExtract);
			if (countedHosts.add(p.host())) {
				genSupply += supply;
			}
			if (supply > 0) {
				nowSupplying.add(p.pos());
			}
		}
		List<EnergyLineDistributor.LiveConsumer<BlockPos>> machines = new ArrayList<>();
		List<EnergyLineDistributor.LiveConsumer<BlockPos>> sinks = new ArrayList<>();
		long machineDemand = 0;
		for (End c : consumers) {
			long room = Math.min(c.buf().maxInsert, c.buf().getCapacity() - c.buf().getAmount());
			if (room <= 0) {
				continue;
			}
			EnergyLineDistributor.LiveConsumer<BlockPos> live =
					new EnergyLineDistributor.LiveConsumer<>(c.pos(), c.buf(), room);
			if (c.storage()) {
				sinks.add(live);
			} else {
				machines.add(live);
				machineDemand += room;
			}
		}
		// Which channel opens, which sinks sit the tick out and which of them draw apart: production's own
		// DischargePlan.decide over this rig's stores, not a copy of it.
		DischargePlan<BlockPos> plan = DischargePlan.decide(machineDemand, genSupply, stores, sinks, planStores,
				CABLE_BUFFER, PACKET_CAP, FEED_RESERVE);
		List<EnergyLineDistributor.LiveConsumer<BlockPos>> surplusTakers = storePositions.isEmpty()
				? List.of() : plan.setAside(sinks, storePositions, pos -> byPos(pos).cascade());
		Set<BlockPos> nowSinks = new LinkedHashSet<>();
		Set<BlockPos> nowMachines = new LinkedHashSet<>();
		for (EnergyLineDistributor.LiveConsumer<BlockPos> c : machines) {
			nowSinks.add(c.pos());
			nowMachines.add(c.pos());
		}
		for (EnergyLineDistributor.LiveConsumer<BlockPos> c : sinks) {
			nowSinks.add(c.pos());
		}
		updateLiveEndpoints(nowSupplying, nowSinks, nowMachines);
		boolean hasSupply = !nowSupplying.isEmpty();
		EnergyLineDistributor<BlockPos> kernel = new EnergyLineDistributor<>(new LineView<>(
				LineEndpoints.BLOCK_FACES, cables::contains, buffers::get,
				pos -> consumerDistance.getOrDefault(pos, 0), flow::flowPotential, flow::machinePotential,
				flow.propagationOrder(), (p, d) -> true, (p, d) -> true,
				hasSupply ? flow.strandedOrder() : List.of(), flow::producerDistance));
		lastMoved = kernel.serveConsumersFromLine(machines, PACKET_CAP, LOSS, txn, cursor);
		long machinesDrew = kernel.lastServeDrawn();
		lastMoved += kernel.serveConsumersFromLine(surplusTakers, PACKET_CAP, LOSS, txn, cursor,
				plan.surplusBudget());
		long apartDrew = kernel.lastServeDrawn();
		lastMoved += kernel.serveConsumersFromLine(sinks, PACKET_CAP, LOSS, txn, cursor);
		lastDrawn = kernel.chargeAndPropagateLine(generators, stores, plan, PACKET_CAP, txn, cursor);
		generatorSurplus = Math.max(0, kernel.generatorDrawn() - machinesDrew);
		backupEpisode = plan.cascadeAllowances().isEmpty() && plan.feedAllowances().isEmpty()
				&& (backupEpisode || plan.backupBudget() > 0);
		sinkCredit = backupEpisode ? Math.min(cables.size() * CABLE_BUFFER,
				sinkCredit + kernel.generatorDrawn() - machinesDrew - apartDrew) : 0;
		cursor = (cursor + 1) & Integer.MAX_VALUE;
	}

	private End byPos(BlockPos pos) {
		return endAt(pos);
	}

	/** One golden line: every cable in geometric order, then every endpoint, then the tick's two totals. */
	String snapshot(int tick) {
		List<BlockPos> ordered = new ArrayList<>(cables);
		ordered.sort(EnergyTopologyCache.BLOCK_POS_ORDER);
		StringBuilder line = new StringBuilder(name).append(" t=").append(tick).append(" c=");
		for (int i = 0; i < ordered.size(); i++) {
			line.append(i == 0 ? "" : ",").append(buffers.get(ordered.get(i)).getAmount());
		}
		line.append(" e=");
		for (int i = 0; i < ends.size(); i++) {
			line.append(i == 0 ? "" : ",").append(ends.get(i).name()).append(':').append(ends.get(i).buf().getAmount());
		}
		return line.append(" mv=").append(lastMoved).append(" dr=").append(lastDrawn).toString();
	}
}
