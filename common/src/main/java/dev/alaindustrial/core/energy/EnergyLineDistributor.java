package dev.alaindustrial.core.energy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-tick distribution kernel of an {@link EnergyNetwork} (MOD-070): who draws from which cable, which cable
 * hands on to which, what the generators put in — by the plan {@link LinePlanner} makes for this tick (MOD-789,
 * MOD-755, ADR-049), then carried out on the cable buffers so every unit really travels the wires one hop a tick
 * (ADR-001). The delivery and loss arithmetic is {@link EnergyShare}/{@link EnergyServe}'s.
 *
 * <p>One instance per tick, bound to the line's {@link LineView}; what survives between ticks (the planner's
 * arrays, the rotation cursor, the telemetry) lives in {@link EnergyNetwork} or the view. Minecraft-free and
 * generic over the position type {@code P} (MOD-715): the network runs it on {@code BlockPos}, an L1 test on a toy
 * grid. Package-private — part of the {@code EnergyNetwork} implementation.
 *
 * @param <P> the position type of a cable or an endpoint
 */
final class EnergyLineDistributor<P> {
	/**
	 * A live producer endpoint resolved for this tick: its pos (for self-churn checks), its storage, and the host
	 * whose buffer that storage is — its own pos unless it is a multiblock cell lending a core's port (MOD-608), in
	 * which case the per-source packet cap is shared with every cell of that host.
	 */
	record LiveProducer<P>(P pos, EnergyPort storage, P host, long perCall) {
		/** An ordinary producer: its own host. Cable buffers and storage sources are always this. */
		LiveProducer(P pos, EnergyPort storage) {
			this(pos, storage, pos);
		}

		/** A producer whose port gives whatever it holds in one call. */
		LiveProducer(P pos, EnergyPort storage, P host) {
			this(pos, storage, host, Long.MAX_VALUE);
		}
	}

	/**
	 * A live consumer endpoint resolved for this tick: its pos, storage, free room and per-tick need — what it
	 * draws per tick while it works ({@link PerTickDemand}, MOD-789). A store's need is its room; {@code need <= 0}
	 * means the consumer cannot tell (no {@link PerTickDemand} — a foreign mod's block): its need is unknown, and
	 * the plan treats it as a storage (ADR-049, rule 2). {@code host}: the block whose buffer it fills — a
	 * multiblock's cells share one (ADR-027), and the plan makes them one receiver by it, never by the port object,
	 * which a loader hands out new on every lookup.
	 */
	record LiveConsumer<P>(P pos, EnergyPort storage, long room, long need, P host) {
		/** A consumer whose need is its whole room: a store. */
		LiveConsumer(P pos, EnergyPort storage, long room) {
			this(pos, storage, room, room);
		}

		/** A consumer that is its own host: not a cell of a multiblock (ADR-027). */
		LiveConsumer(P pos, EnergyPort storage, long room, long need) {
			this(pos, storage, room, need, pos);
		}

		/** Does it know its per-tick need? A consumer that does not is served as a storage (ADR-049). */
		boolean needKnown() {
			return need > 0;
		}

		/** What it takes first this tick: its need, at most its room; 0 when the need is unknown. */
		long perTickNeed() {
			return needKnown() ? Math.min(need, room) : 0L;
		}
	}

	/**
	 * What one {@link #distribute} moved: delivered in all, of it into storage sinks, what the machines and the
	 * surplus takers drew out of the cables (delivered plus loss — MOD-731, MOD-756), and what all the consumers drew.
	 */
	record Moved(long delivered, long intoStorage, long machinesDrew, long takersDrew, long drew) {
	}

	private final LineView<P> view;
	private final LinePlanner<P> planner;
	/** Producers that put EU into a cable this tick — what the Network Analyzer draws its sparks from (MOD-665). */
	private final Set<P> fed = new LinkedHashSet<>();
	/** Per cable, what it may still hand on this tick: what it held when the hops began. */
	private long[] budget = new long[0];
	/** Per receiver, its first draw of the tick (the machines' needs). */
	private long[] firstPull = new long[0];
	/** EU the generators put into the cables in the latest {@link #distribute} (MOD-731). */
	private long generatorDrawn;

	/**
	 * A kernel bound to one tick of one line ({@link LineView}): the network's own view, or a synthetic graph in a
	 * test — every behavioural method reads the topology only through the view.
	 */
	EnergyLineDistributor(LineView<P> view) {
		this.view = view;
		this.planner = view.planner();
	}

	Set<P> fed() {
		return fed;
	}

	/** EU the generators put into the cables in the latest {@link #distribute} (MOD-731). */
	long generatorDrawn() {
		return generatorDrawn;
	}

	/**
	 * One tick of the line (ADR-049): plan, then the consumers draw their planned amounts (machines first), the
	 * cables hand on theirs (the claimant before its donor), the generators charge theirs and fill what room is
	 * left beside them; with nothing planned at all the line fills outward from the generators (ADR-007).
	 *
	 * @param takers the sinks that draw the generators' surplus apart this tick (MOD-731/756), at most
	 *     {@code takerBudget} between them
	 * @param rotation the network's per-tick rotation offset: who starts a sharing, a fill (MOD-254)
	 */
	Moved distribute(List<LiveConsumer<P>> machines, List<LiveConsumer<P>> stores, List<LiveConsumer<P>> takers,
			long takerBudget, List<LiveProducer<P>> generators, long packetCap, double lossPerBlock,
			EnergyPort.Txn tx, int rotation) {
		planner.plan(view, machines, stores, takers, generators, packetCap, takerBudget, rotation, lossPerBlock);
		List<PlanEnds.Sink<P>> sinks = planner.sinks();
		LineStock<P> stock = planner.stock();
		stock.measure(planner);
		long[] pulled = planner.pulledScratch();
		// The plan first — the generators' output on its way — then the stock the plan leaves, one hop a tick: a
		// unit of the stock never takes a packet the plan routes through the same cable (ADR-049). What each cable
		// may give this tick — draws and hops alike — is what it held when the tick began: a unit
		// moves one hop a tick (ADR-001). The machines' needs first, their draws and their hops; the rest after.
		int c = planner.cableCount();
		if (budget.length < c) {
			budget = new long[c];
		}
		for (int i = 0; i < c; i++) {
			EnergyBuffer b = planner.buffer(i);
			budget[i] = b == null ? 0L : b.getAmount();
		}
		// Each draw is delivered before anything moves into the cables again: what a receiver refuses goes back into
		// the cables it was just drawn out of, where its room is still free — after the hops it would have nowhere
		// to go.
		long[] moved = new long[5];
		if (firstPull.length < sinks.size()) {
			firstPull = new long[sinks.size() * 2];
		}
		for (int k = 0; k < sinks.size(); k++) {
			pulled[k] = pull(sinks.get(k), true, tx);
			firstPull[k] = pulled[k];
			deliver(sinks.get(k), pulled[k], 0L, lossPerBlock, tx, moved);
		}
		if (planner.needsFixed()) {
			handOn(0, packetCap, tx);
		}
		for (int k = 0; k < sinks.size(); k++) {
			pulled[k] += pull(sinks.get(k), false, tx);
		}
		stock.draw(planner, pulled, rotation, tx);
		for (int k = 0; k < sinks.size(); k++) {
			deliver(sinks.get(k), pulled[k] - firstPull[k], firstPull[k], lossPerBlock, tx, moved);
		}
		handOn(1, packetCap, tx);
		stock.move(planner, packetCap, rotation, tx);
		long[] drawnByHost = new long[planner.sources().size()];
		generatorDrawn = chargePlanned(packetCap, tx, drawnByHost, rotation);
		// With a consumer still wanting, output the plan could not route stays in the generator: in the cables it
		// would be stock that slips toward that consumer whenever a planned packet leaves room.
		if (!stock.wanting() && outputLeft(drawnByHost)) {
			fillOutward(packetCap, tx);
			generatorDrawn += fillBeside(packetCap, tx, rotation, drawnByHost);
		}
		return new Moved(moved[0], moved[1], moved[2], moved[3], moved[4]);
	}

	/** One receiver's planned draw out of its cables; returns what it pulled. */
	/**
	 * One receiver's planned draw out of its cables, within what each cable held when the tick began; returns what
	 * it pulled. {@code needs}: a machine's need part only (the first draw of the tick); else the rest of the plan.
	 */
	private long pull(PlanEnds.Sink<P> s, boolean needs, EnergyPort.Txn tx) {
		long pulled = 0L;
		FlowPlan graph = planner.graph();
		long restLeft = s.planned;
		for (int i = 0; i < s.cableCount; i++) {
			restLeft -= needs ? 0L : s.needFlow[i];
		}
		for (int i = 0; i < s.cableCount; i++) {
			long f;
			if (needs) {
				s.taken[i] = 0L;
				f = s.needFlow[i];
			} else {
				f = Math.min(Math.max(0L, graph.flow(s.edges[i]) - s.needFlow[i]), Math.max(0L, restLeft));
				restLeft -= f;
			}
			int cable = s.cables[i];
			EnergyBuffer b = planner.buffer(cable);
			if (f > 0 && b != null) {
				long got = b.extract(Math.min(f, Math.min(budget[cable], b.getAmount())), tx);
				budget[cable] -= got;
				s.taken[i] += got;
				pulled += got;
			}
		}
		return pulled;
	}

	/**
	 * One receiver's draw, the MOD-021 loss, the insert; what it refuses goes back to its cables. {@code earlier}:
	 * what it drew before this tick, delivered already — the loss is rounded once on the tick's whole draw.
	 */
	private void deliver(PlanEnds.Sink<P> s, long pulled, long earlier, double lossPerBlock, EnergyPort.Txn tx,
			long[] moved) {
		if (pulled <= 0) {
			return;
		}
		int distance = view.consumerDistance().apply(s.consumer.pos());
		long loss = EnergyShare.cableLoss(earlier + pulled, lossPerBlock, distance)
				- EnergyShare.cableLoss(earlier, lossPerBlock, distance);
		long toDeliver = EnergyServe.deliverAfterLoss(pulled, loss);
		long inserted = toDeliver > 0 ? s.consumer.storage().insert(toDeliver, tx) : 0L;
		long surplus = EnergyServe.surplus(toDeliver, inserted);
		for (int i = 0; i < s.cableCount && surplus > 0; i++) {
			EnergyBuffer b = planner.buffer(s.cables[i]);
			if (b != null) {
				surplus -= b.insert(Math.min(surplus, b.getCapacity() - b.getAmount()), tx);
			}
		}
		long drew = EnergyServe.consumed(inserted, loss);
		moved[0] += inserted;
		moved[4] += drew;
		if (s.storage) {
			moved[1] += inserted;
		}
		if (s.machine()) {
			moved[2] += drew;
		}
		if (s.taker()) {
			moved[3] += drew;
		}
	}

	/**
	 * One pass of the planned hops, each cable after every cable it gives to, within what it held when the tick
	 * began (ADR-001). Pass 0 hands on the machines' needs ({@link LinePlanner#needHop}), pass 1 the rest, so a
	 * cable short of what the plan takes out of it, or a receiver short of room, serves the needs first.
	 */
	private void handOn(int pass, long packetCap, EnergyPort.Txn tx) {
		HopOrder hops = planner.hops();
		int[] order = hops.order();
		for (int k = 0; k < planner.cableCount(); k++) {
			int from = order[k];
			EnergyBuffer donor = planner.buffer(from);
			for (int e = planner.neighbourStart(from); e < planner.neighbourEnd(from) && budget[from] > 0; e++) {
				long f = pass == 0 ? planner.needTransfer(e) : planner.restTransfer(e);
				EnergyBuffer to = planner.buffer(planner.neighbour(e));
				if (f <= 0 || to == null || donor == null) {
					continue;
				}
				long give = Math.min(Math.min(f, budget[from]), Math.min(to.getCapacity() - to.getAmount(), packetCap));
				if (give > 0) {
					long got = donor.extract(give, tx);
					to.insert(got, tx);
					budget[from] -= got;
				}
			}
		}
	}

	/**
	 * The generators put in what the plan routes; returns the EU drawn. What the plan routes into one cable may come
	 * from any generator beside that cable — one turn after another (MOD-254: a generator on a full line is never
	 * left out for good) — but a generator never gives away what the plan routes through its other cables.
	 */
	private long chargePlanned(long packetCap, EnergyPort.Txn tx, long[] drawnByHost, int rotation) {
		long drawn = 0L;
		FlowPlan graph = planner.graph();
		List<PlanEnds.Source<P>> sources = planner.sources();
		int n = sources.size();
		long[] spare = new long[n];
		for (int s = 0; s < n; s++) {
			PlanEnds.Source<P> src = sources.get(s);
			long planned = 0L;
			for (int i = 0; i < src.cableCount; i++) {
				planned += graph.flow(src.edges[i]);
			}
			spare[s] = Math.max(0L, src.available - planned);
		}
		for (int s0 = 0; s0 < n; s0++) {
			PlanEnds.Source<P> owner = sources.get(s0);
			for (int i0 = 0; i0 < owner.cableCount; i0++) {
				long due = graph.flow(owner.edges[i0]);
				int cable = owner.cables[i0];
				for (int j = 0; j < n && due > 0; j++) {
					int s = Math.floorMod(rotation + j, n);
					PlanEnds.Source<P> src = sources.get(s);
					int i = src.slotOf(cable);
					if (i < 0) {
						continue;
					}
					long own = s == s0 ? due : 0L;
					long got = inject(src, i, Math.min(due, Math.min(own + spare[s], packetCap - drawnByHost[s])), tx);
					spare[s] -= Math.max(0L, got - own);
					drawnByHost[s] += got;
					drawn += got;
					due -= got;
				}
			}
		}
		return drawn;
	}

	/** Put up to {@code want} out of a source into its {@code i}-th cable, never past the cable's room. */
	private long inject(PlanEnds.Source<P> src, int i, long want, EnergyPort.Txn tx) {
		EnergyBuffer buf = planner.buffer(src.cables[i]);
		if (buf == null || want <= 0) {
			return 0L;
		}
		long room = buf.getCapacity() - buf.getAmount();
		if (room <= 0) {
			return 0L;
		}
		LiveProducer<P> cell = src.cells.get(src.cellOf[i]);
		long got = cell.storage().extract(Math.min(want, room), tx);
		if (got > 0) {
			buf.insert(got, tx);
			fed.add(cell.pos());
		}
		return got;
	}

	/**
	 * What a generator could not route still fills the cables beside it (ADR-007: a generator fills the wire, and
	 * only then does the network sleep), one packet per host in all, starting at the rotation (MOD-254).
	 */
	private long fillBeside(long packetCap, EnergyPort.Txn tx, int rotation, long[] drawnByHost) {
		long drawn = 0L;
		List<PlanEnds.Source<P>> sources = planner.sources();
		int n = sources.size();
		for (int k = 0; k < n; k++) {
			int s = Math.floorMod(rotation + k, n);
			PlanEnds.Source<P> src = sources.get(s);
			for (int j = 0; j < src.cableCount && drawnByHost[s] < packetCap; j++) {
				int i = Math.floorMod(rotation + j, src.cableCount);
				long got = inject(src, i, packetCap - drawnByHost[s], tx);
				drawnByHost[s] += got;
				drawn += got;
			}
		}
		return drawn;
	}

	/** Has a generator output the plan could not route — energy nobody can take this tick? */
	private boolean outputLeft(long[] drawnByHost) {
		List<PlanEnds.Source<P>> sources = planner.sources();
		for (int s = 0; s < sources.size(); s++) {
			if (sources.get(s).available > drawnByHost[s]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * While a generator has output nobody can take, the line fills outward from the generators (MOD-070, MOD-318,
	 * ADR-007): a cable hands what it holds beyond what the plan runs through it to a neighbour farther from the
	 * generators, the farthest cables first, so a unit moves one hop a tick — a spur or the run past the last consumer
	 * shows the energy in it. What the plan runs through a cable is never handed out this way.
	 */
	private void fillOutward(long packetCap, EnergyPort.Txn tx) {
		int c = planner.cableCount();
		Integer[] order = new Integer[c];
		for (int i = 0; i < c; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Integer.compare(planner.producerDistance(b), planner.producerDistance(a)));
		for (int idx : order) {
			EnergyBuffer donor = planner.buffer(idx);
			int d = planner.producerDistance(idx);
			if (donor == null || d >= Integer.MAX_VALUE / 2) {
				continue;
			}
			for (int e = planner.neighbourStart(idx); e < planner.neighbourEnd(idx); e++) {
				int n = planner.neighbour(e);
				EnergyBuffer to = planner.buffer(n);
				long spare = donor.getAmount() - planner.through(idx);
				if (spare <= 0 || to == null || planner.producerDistance(n) <= d
						|| planner.producerDistance(n) >= Integer.MAX_VALUE / 2) {
					continue;
				}
				long give = Math.min(spare, Math.min(to.getCapacity() - to.getAmount(), packetCap));
				if (give > 0) {
					to.insert(donor.extract(give, tx), tx);
				}
			}
		}
	}

	/**
	 * Does this line hand energy from one cable to its neighbour, by the last plan (MOD-665)? With nothing
	 * planned, outward from the generators.
	 */
	boolean handsOn(P from, P to) {
		return planner.handsOn(from, to);
	}

	/**
	 * The storage stage of a tick, after {@link #distribute}: a store discharges into the line through exactly one
	 * of three channels, never two (ADR-004), in the order of {@code plan} — backup power (the machines are short),
	 * the cascade (MOD-314) and the feed (MOD-353). What a store puts in reaches the machines on the next tick's
	 * stock ({@link LineStock}). Returns the EU drawn out of storage (MOD-665).
	 */
	long dischargeStorage(List<LiveProducer<P>> storageSources, DischargePlan<P> plan, long packetCap,
			EnergyPort.Txn tx, int rotation) {
		long storageBudget = plan.backupBudget();
		if (storageBudget > 0 && !storageSources.isEmpty()) {
			return chargeLineFrom(storageSources, packetCap, storageBudget, tx, rotation);
		}
		if (!plan.cascadeAllowances().isEmpty()) {
			return chargeLineByAllowance(storageSources, plan.cascadeAllowances(), packetCap, tx, rotation);
		}
		return chargeLineByAllowance(storageSources, plan.feedAllowances(), packetCap, tx, rotation);
	}

	/**
	 * Let each donor in {@code storageSources} charge the line with at most its own allowance — the cascade's and
	 * the feed's per-donor budgets (MOD-314, MOD-353). Iterates the sources, not the map, so the order the topology
	 * fixed is kept (MOD-304). Returns the EU drawn.
	 */
	private long chargeLineByAllowance(List<LiveProducer<P>> storageSources, Map<P, Long> allowances,
			long packetCap, EnergyPort.Txn tx, int rotation) {
		long drawn = 0;
		for (LiveProducer<P> donor : storageSources) {
			Long allowance = allowances.get(donor.pos());
			if (allowance != null && allowance > 0) {
				drawn += chargeLineFrom(List.of(donor), packetCap, allowance, tx, rotation);
			}
		}
		return drawn;
	}

	/**
	 * Charge the source-adjacent cables from storage {@code sources}: each host at most {@code packetCap} this tick
	 * (MOD-608), the whole call at most {@code totalBudget}; only through faces that can emit (MOD-255), the sweep
	 * starting at {@code rotation} (MOD-254). Returns the EU drawn.
	 */
	private long chargeLineFrom(List<LiveProducer<P>> sources, long packetCap, long totalBudget,
			EnergyPort.Txn tx, int rotation) {
		int sourceCount = sources.size();
		long drawn = 0;
		Map<P, Long> drawnByHost = new LinkedHashMap<>();
		int faceCount = view.faces().size();
		for (int s = 0; s < sourceCount && drawn < totalBudget; s++) {
			LiveProducer<P> prod = sources.get(Math.floorMod(rotation + s, sourceCount));
			long fromThis = drawnByHost.getOrDefault(prod.host(), 0L);
			for (int d = 0; d < faceCount && fromThis < packetCap && drawn < totalBudget; d++) {
				int face = Math.floorMod(rotation + d, faceCount);
				P np = view.faces().get(face).apply(prod.pos());
				if (!view.isCable().test(np) || !view.canFeedFace().test(prod.pos(), face)) {
					continue;
				}
				EnergyBuffer buf = view.cableBufferAt().apply(np);
				long room = buf == null ? 0L : buf.getCapacity() - buf.getAmount();
				if (room <= 0) {
					continue;
				}
				long got = prod.storage().extract(Math.min(Math.min(room, packetCap - fromThis), totalBudget - drawn),
						tx);
				if (got > 0) {
					buf.insert(got, tx);
					fromThis += got;
					drawn += got;
					fed.add(prod.pos());
				}
			}
			drawnByHost.put(prod.host(), fromThis);
		}
		return drawn;
	}
}
