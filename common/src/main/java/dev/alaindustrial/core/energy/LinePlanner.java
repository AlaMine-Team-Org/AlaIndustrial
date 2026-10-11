package dev.alaindustrial.core.energy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// size-justified: one tick's plan — the cable index, the graph, the three stages of the rule and the even share —
// works on one graph and one set of sinks; split, every part would hand the same dozen arrays around (MOD-789).
/**
 * Plans one tick of a line as a prioritised flow (MOD-789, MOD-755, ADR-049) — the player-facing rule:
 *
 * <ol>
 *   <li><b>Machines eat first.</b> Each machine gets up to its per-tick need ({@link PerTickDemand}), as far as
 *       the cables can carry it; when there is not enough, the machine nearest the generators (fewest cables)
 *       first, machines at one distance alike.</li>
 *   <li><b>What the machines cannot take goes to the storages at once</b>, nearest first — a consumer that does
 *       not tell its need counts as a storage here — and only then do the machines fill their own buffers.</li>
 *   <li>Storages discharge back to the machines through the channels of {@link DischargePlan} (ADR-004); what
 *       they put into the cables reaches the machines through the line's stock ({@link LineStock}).</li>
 * </ol>
 *
 * <p>The graph ({@link FlowPlan}): a cable passes at most its segment buffer a tick (ADR-001: the buffer IS its
 * throughput), a hop and a receiver at most one packet, a generator host at most one packet. The plan routes the
 * generators' output; what the cables hold beyond it is the stock, carried a hop a tick by {@link LineStock}, not
 * planned. A machine's need is planned gross of the MOD-021 loss on its path. The sinks come in the order of the
 * rule, each class nearest first; a later sink never takes what an earlier one was planned
 * (successive augmenting paths end at their sink), and sinks at one distance that cannot all have what they want
 * share it evenly, the remainder from a rotating start.
 *
 * <p>Persistent per line: the graph's arrays and the cable index survive from tick to tick; the index is rebuilt
 * only when the cable list changes. Minecraft-free.
 *
 * @param <P> the position type of a cable or an endpoint
 */
final class LinePlanner<P> {

	/** The smallest group of sinks shared out along one search tree ({@link #share}). */
	private static final int TREE_GROUP = 4;
	private final FlowPlan graph = new FlowPlan();
	private final HopOrder hops = new HopOrder();
	private final LineStock<P> stock = new LineStock<>();
	private List<P> cables = List.of();
	private final Map<P, Integer> index = new LinkedHashMap<>();
	private int[] nbrStart = new int[1];
	private int[] nbr = new int[0];
	private int[] nbrEdge = new int[0];
	/** Per cable edge: the edge back (j to i for i to j), and what the machines' needs run along it. */
	private int[] reverse = new int[0];
	private long[] needHop = new long[0];
	private int[] throughEdge = new int[0];
	private int[] producerDistance = new int[0];
	private EnergyBuffer[] buffers = new EnergyBuffer[0];
	private final List<PlanEnds.Sink<P>> sinks = new ArrayList<>();
	private final List<PlanEnds.Source<P>> sources = new ArrayList<>();
	private int[] group = new int[8];
	private boolean[] stockHop = new boolean[0];
	private long[] pulled = new long[8];
	private long[] before = new long[8];
	private long[] roundBefore = new long[8];
	private boolean[] active = new boolean[8];
	private int source;
	private boolean planned;
	private int cursor;
	private long takerBudget;
	private long plannedTakerBudget;
	/** Whether this routing fixes the machines' needs' paths ({@link #recordNeeds}). */
	private boolean lockNeeds;
	private long[] amounts = new long[8];
	private boolean[] wanted = new boolean[8];
	/** Whether the latest {@link #greedy} pass gave every sink of its group all it wanted. */
	private boolean greedyAll;
	/** The generators' share found on the latest tick that searched one (MOD-252). */
	private long lastShare;

	FlowPlan graph() {
		return graph;
	}

	/** The line's stock carrier, kept with the planner so its arrays are reused. */
	LineStock<P> stock() {
		return stock;
	}

	/** The planned hops: the generators' output on its way. */
	HopOrder hops() {
		return hops;
	}

	List<PlanEnds.Sink<P>> sinks() {
		return sinks;
	}

	List<PlanEnds.Source<P>> sources() {
		return sources;
	}

	int cableCount() {
		return cables.size();
	}

	EnergyBuffer buffer(int cable) {
		return buffers[cable];
	}

	int producerDistance(int cable) {
		return producerDistance[cable];
	}

	/** What the plan runs through a cable this tick: what it hands on and gives its consumers. */
	long through(int cable) {
		return graph.flow(throughEdge[cable]);
	}

	int neighbourStart(int cable) {
		return nbrStart[cable];
	}

	int neighbourEnd(int cable) {
		return nbrStart[cable + 1];
	}

	int neighbour(int edge) {
		return nbr[edge];
	}

	/**
	 * Does the line hand energy from cable {@code from} to cable {@code to}, by the latest plan (MOD-665)? With
	 * nothing planned it fills outward from the generators (ADR-007).
	 */
	boolean handsOn(P from, P to) {
		Integer i = index.get(from);
		Integer j = index.get(to);
		if (i == null || j == null || i >= producerDistance.length) {
			return false;
		}
		if (!planned) {
			return producerDistance[j] > producerDistance[i] && producerDistance[j] < Integer.MAX_VALUE / 2;
		}
		for (int e = nbrStart[i]; e < nbrStart[i + 1]; e++) {
			if (nbr[e] == j) {
				return hops.net(e) > 0 || stockHop[e];
			}
		}
		return false;
	}

	/** Record that the stock moved a hop from cable edge {@code e} this tick, for {@link #handsOn}. */
	void stockHopped(int e) {
		stockHop[e] = true;
	}

	/** A scratch array, one slot per receiver of this tick's plan. */
	long[] pulledScratch() {
		if (pulled.length < sinks.size()) {
			pulled = new long[sinks.size() * 2];
		}
		return pulled;
	}

	/** Did this tick's plan move anything at all? With nothing planned the line fills outward (ADR-007). */
	boolean planned() {
		return planned;
	}

	/**
	 * Plan this tick. {@code machines}: the consumers that are not storages (an unknown need, 0, makes one a storage
	 * here); {@code stores}: the storage sinks; {@code takers}: the sinks that draw the generators' surplus apart, at
	 * most {@code takerBudget} between them, out of the plan only (MOD-731/756); {@code generators}: the sources whose
	 * output is planned. {@code cursor} rotates the start of every sharing.
	 */
	void plan(LineView<P> view, List<EnergyLineDistributor.LiveConsumer<P>> machines,
			List<EnergyLineDistributor.LiveConsumer<P>> stores, List<EnergyLineDistributor.LiveConsumer<P>> takers,
			List<EnergyLineDistributor.LiveProducer<P>> generators, long packetCap, long takerBudget, int cursor,
			double lossPerBlock) {
		this.cursor = cursor;
		this.takerBudget = takerBudget;
		this.plannedTakerBudget = takerBudget;
		index(view);
		int c = cables.size();
		for (int i = 0; i < c; i++) {
			buffers[i] = view.cableBufferAt().apply(cables.get(i));
			Integer d = view.producerDistance().apply(cables.get(i));
			producerDistance[i] = d == null ? Integer.MAX_VALUE / 2 : d;
		}
		sinks.clear();
		addSinks(view, machines, PlanEnds.MACHINE, packetCap, lossPerBlock);
		addSinks(view, stores, PlanEnds.STORE, packetCap, lossPerBlock);
		addSinks(view, takers, PlanEnds.TAKER, packetCap, lossPerBlock);
		sources.clear();
		addSources(view, generators, packetCap);
		route(packetCap);
		balanceSources(packetCap);
		for (int e = 0; e < nbr.length; e++) {
			hops.set(e, graph.flow(nbrEdge[e]));
			stockHop[e] = false;
		}
		boolean any = hops.settle();
		for (PlanEnds.Sink<P> s : sinks) {
			any |= s.planned > 0;
		}
		planned = any;
	}

	/**
	 * Route the plan's amounts (ADR-049). The needs and the stores are planned twice: free, then with the machines'
	 * needs on fixed paths (recordNeeds). The fixed plan is kept when it gives every receiver what the free one does
	 * — its paths stay the same tick after tick; where fixing the needs would cost a store, the free plan stands. The
	 * machines' buffers, the last class, are planned on the plan kept.
	 */
	private void route(long packetCap) {
		lockNeeds = false;
		planOnce(packetCap);
		if (amounts.length < sinks.size()) {
			amounts = new long[sinks.size() * 2];
		}
		boolean needs = false;
		for (int k = 0; k < sinks.size(); k++) {
			PlanEnds.Sink<P> s = sinks.get(k);
			amounts[k] = s.planned;
			needs |= s.machine() && Math.min(s.planned, s.need) > 0;
		}
		// With no need planned there is nothing to fix: the second routing would be the first.
		if (needs) {
			lockNeeds = true;
			planOnce(packetCap);
			boolean same = true;
			for (int k = 0; k < sinks.size(); k++) {
				same &= amounts[k] == sinks.get(k).planned;
			}
			if (!same) {
				lockNeeds = false;
				planOnce(packetCap);
			}
		}
		byDistance(source, PlanEnds.MACHINE, false, false);
		if (lockNeeds && slack()) {
			// The fixed paths cost the buffers what a generator still holds: plan it free as well and keep
			// whichever gives the receivers more in all (ADR-049: a generator does not idle while a receiver has
			// room and a path).
			long fixed = plannedTotal();
			lockNeeds = false;
			planOnce(packetCap);
			byDistance(source, PlanEnds.MACHINE, false, false);
			if (plannedTotal() <= fixed) {
				lockNeeds = true;
				planOnce(packetCap);
				byDistance(source, PlanEnds.MACHINE, false, false);
			}
		}
	}

	/** Does a generator still hold output while a machine's buffer has room it could be given? */
	private boolean slack() {
		boolean held = false;
		for (PlanEnds.Source<P> src : sources) {
			long used = 0L;
			boolean faceFree = false;
			for (int i = 0; i < src.cableCount; i++) {
				used += graph.flow(src.edges[i]);
				faceFree |= graph.flow(src.edges[i]) < graph.capacity(src.edges[i]);
			}
			held |= faceFree && used < src.available;
		}
		if (!held) {
			return false;
		}
		if (wanted.length < graph.nodes()) {
			wanted = new boolean[graph.nodes() * 2];
		}
		Arrays.fill(wanted, 0, graph.nodes(), false);
		boolean any = false;
		for (int k = 0; k < sinks.size(); k++) {
			PlanEnds.Sink<P> s = sinks.get(k);
			if (s.machine() && want(s, false) > 0) {
				wanted[node(k)] = true;
				any = true;
			}
		}
		// The paths fixed cost the buffers something only if, with them free, a path reaches a buffer with room.
		return any && graph.reachesUnlocked(source, wanted);
	}

	private long plannedTotal() {
		long t = 0L;
		for (PlanEnds.Sink<P> s : sinks) {
			t += s.planned;
		}
		return t;
	}

	private void planOnce(long packetCap) {
		for (PlanEnds.Sink<P> s : sinks) {
			s.planned = 0L;
		}
		takerBudget = plannedTakerBudget;
		build(packetCap, Long.MAX_VALUE);
		byDistance(source, PlanEnds.MACHINE, false, true);
		recordNeeds();
		byDistance(source, PlanEnds.STORE, true, false);
	}

	private void index(LineView<P> view) {
		List<P> list = view.cables();
		if (list == cables && list.size() == index.size()) {
			return;
		}
		cables = list;
		index.clear();
		for (int i = 0; i < list.size(); i++) {
			index.put(list.get(i), i);
		}
		int c = list.size();
		nbrStart = new int[c + 1];
		List<Integer> adj = new ArrayList<>(c * 2);
		for (int i = 0; i < c; i++) {
			nbrStart[i] = adj.size();
			for (int f = 0; f < view.faces().size(); f++) {
				Integer j = index.get(view.faces().get(f).apply(list.get(i)));
				if (j != null && j != i) {
					adj.add(j);
				}
			}
		}
		nbrStart[c] = adj.size();
		nbr = adj.stream().mapToInt(Integer::intValue).toArray();
		nbrEdge = new int[nbr.length];
		needHop = new long[nbr.length];
		reverse = new int[nbr.length];
		for (int i = 0; i < c; i++) {
			for (int e = nbrStart[i]; e < nbrStart[i + 1]; e++) {
				for (int b = nbrStart[nbr[e]]; b < nbrStart[nbr[e] + 1]; b++) {
					if (nbr[b] == i) {
						reverse[e] = b;
					}
				}
			}
		}
		throughEdge = new int[c];
		stockHop = new boolean[nbr.length];
		producerDistance = new int[c];
		buffers = new EnergyBuffer[c];
		hops.adjacency(nbrStart, nbr, c);
	}

	private void addSinks(LineView<P> view, List<EnergyLineDistributor.LiveConsumer<P>> list, int kind,
			long packetCap, double lossPerBlock) {
		for (EnergyLineDistributor.LiveConsumer<P> lc : list) {
			PlanEnds.Sink<P> s = sinkOf(lc.host());
			if (s == null) {
				long room = Math.min(lc.room(), packetCap);
				if (room <= 0) {
					continue;
				}
				int k = kind == PlanEnds.MACHINE && !lc.needKnown() ? PlanEnds.STORE : kind;
				// The need is planned gross: what the cables give up so the need arrives after the MOD-021 loss.
				long need = k == PlanEnds.MACHINE ? Math.min(EnergyShare.grossFor(lc.perTickNeed(), lossPerBlock,
						view.consumerDistance().apply(lc.pos())), room) : 0L;
				s = new PlanEnds.Sink<>(lc, k, room, need, kind != PlanEnds.MACHINE);
				sinks.add(s);
			}
			for (int f = 0; f < view.faces().size(); f++) {
				Integer j = index.get(view.faces().get(f).apply(lc.pos()));
				if (j != null && view.canDrawFace().test(lc.pos(), f)) {
					s.touch(j, producerDistance[j]);
				}
			}
		}
	}

	/** The receiver already planned for this host: a multiblock's cells are one (ADR-027). */
	private PlanEnds.Sink<P> sinkOf(P host) {
		for (PlanEnds.Sink<P> s : sinks) {
			if (s.consumer.host().equals(host)) {
				return s;
			}
		}
		return null;
	}

	private void addSources(LineView<P> view, List<EnergyLineDistributor.LiveProducer<P>> list, long packetCap) {
		for (EnergyLineDistributor.LiveProducer<P> lp : list) {
			PlanEnds.Source<P> s = null;
			for (PlanEnds.Source<P> other : sources) {
				if (other.producer.host().equals(lp.host())) {
					s = other; // MOD-608: one packet per host, whatever number of cells lends its port
					break;
				}
			}
			if (s == null) {
				s = new PlanEnds.Source<>(lp, Math.min(packetCap, Math.max(0L, lp.storage().getAmount())));
				sources.add(s);
			}
			int cell = s.cells.size();
			s.cells.add(lp);
			for (int f = 0; f < view.faces().size(); f++) {
				Integer j = index.get(view.faces().get(f).apply(lp.pos()));
				if (j != null && view.canFeedFace().test(lp.pos(), f)) {
					s.touch(j, f, cell);
				}
			}
		}
	}

	private void build(long packetCap, long sourceLevel) {
		int c = cables.size();
		int g = sources.size();
		source = 2 * c + g + sinks.size();
		graph.reset(source + 1);
		for (int i = 0; i < c; i++) {
			throughEdge[i] = graph.edge(2 * i, 2 * i + 1, buffers[i] == null ? 0L : buffers[i].getCapacity());
		}
		for (int i = 0; i < c; i++) {
			for (int e = nbrStart[i]; e < nbrStart[i + 1]; e++) {
				nbrEdge[e] = graph.edge(2 * i + 1, 2 * nbr[e], packetCap);
			}
		}
		for (int s = 0; s < g; s++) {
			PlanEnds.Source<P> src = sources.get(s);
			graph.edge(source, 2 * c + s, Math.min(src.available, sourceLevel));
			for (int i = 0; i < src.cableCount; i++) {
				long perFace = Math.min(packetCap, src.cells.get(src.cellOf[i]).perCall());
				src.edges[i] = graph.edge(2 * c + s, 2 * src.cables[i], perFace);
			}
		}
		for (int s = 0; s < sinks.size(); s++) {
			PlanEnds.Sink<P> sink = sinks.get(s);
			for (int i = 0; i < sink.cableCount; i++) {
				sink.edges[i] = graph.edge(2 * sink.cables[i] + 1, node(s), FlowPlan.UNLIMITED);
			}
		}
	}

	/**
	 * MOD-252: what the consumers get is fixed by the rule; which generator supplies it is not. Spread it as evenly
	 * as the cables allow — the smallest share per generator under which every consumer still gets what it was
	 * planned — so a generator behind another one is not left full while the near one runs dry (the wind-mill
	 * tower of MOD-252). Only when the generators have more than the lines take; the consumers' amounts do not move.
	 */
	private void balanceSources(long packetCap) {
		int g = sources.size();
		long total = 0L;
		long avail = 0L;
		long most = 0L;
		for (PlanEnds.Sink<P> s : sinks) {
			total += s.planned;
		}
		for (PlanEnds.Source<P> src : sources) {
			avail += src.available;
			most = Math.max(most, src.available);
		}
		if (g < 2 || total <= 0 || total >= avail) {
			return;
		}
		long lo = Math.max(1L, (total + g - 1) / g);
		long hi = most;
		// A steady line asks the same question tick after tick: try last tick's share first — two routings
		// instead of a whole search when it still is the smallest that works.
		if (lastShare >= lo && lastShare <= hi) {
			if (lastShare > lo && routes(packetCap, lastShare - 1)) {
				hi = lastShare - 1;
			} else if (routes(packetCap, lastShare)) {
				return;
			} else {
				lo = lastShare + 1;
			}
		}
		while (lo < hi) {
			long mid = (lo + hi) >>> 1;
			if (routes(packetCap, mid)) {
				hi = mid;
			} else {
				lo = mid + 1;
			}
		}
		lastShare = lo;
		if (!routes(packetCap, lo)) {
			// No level routes the plan with the needs locked first: keep the stage's own routing.
			lastShare = 0L;
			build(packetCap, Long.MAX_VALUE);
			for (PlanEnds.Sink<P> s : sinks) {
				s.planned = 0L;
			}
			takerBudget = plannedTakerBudget;
			stage(source, true);
		}
	}

	/**
	 * Rebuild the graph with every generator capped at {@code level}; can every consumer get what it was planned?
	 * The machines' needs first and locked, as in {@link #stage}, then the rest.
	 */
	private boolean routes(long packetCap, long level) {
		build(packetCap, level);
		boolean all = true;
		for (int k = 0; k < sinks.size(); k++) {
			PlanEnds.Sink<P> s = sinks.get(k);
			long want = s.machine() ? Math.min(s.planned, s.need) : 0L;
			if (want > 0) {
				all &= graph.augmentAll(source, node(k), want) == want;
			}
		}
		recordNeeds();
		for (int k = 0; k < sinks.size(); k++) {
			PlanEnds.Sink<P> s = sinks.get(k);
			long want = s.planned - (s.machine() ? Math.min(s.planned, s.need) : 0L);
			if (want > 0) {
				all &= graph.augmentAll(source, node(k), want) == want;
			}
		}
		return all;
	}

	/**
	 * The routing of the machines' needs alone, recorded right after they are planned: the line carries it out
	 * first — its draws and its hops — and the rest of the plan after ({@link #needTransfer},
	 * {@link #restTransfer}).
	 * Planned on the same graph tick after tick, it stays the same whatever the stores and the buffers take, so what
	 * a cable holds is what the needs draw from it (ADR-001: a unit travels a hop a tick). Carried out as one netted
	 * plan, the needs' paths moved with the buffers' room, which changes every tick, and a far machine's need
	 * reached a cable the last tick had not filled (the 32 x 32 sheet of the stand, E34).
	 */
	private void recordNeeds() {
		if (!lockNeeds) {
			Arrays.fill(needHop, 0L);
			for (PlanEnds.Sink<P> s : sinks) {
				Arrays.fill(s.needFlow, 0L);
			}
			return;
		}
		for (int e = 0; e < nbr.length; e++) {
			needHop[e] = graph.flow(nbrEdge[e]);
		}
		for (PlanEnds.Sink<P> s : sinks) {
			for (int i = 0; i < s.cableCount; i++) {
				s.needFlow[i] = graph.flow(s.edges[i]);
			}
		}
		graph.lock();
		for (int e = 0; e < nbr.length; e++) {
			if (needHop[e] > needHop[reverse[e]]) {
				int back = nbrEdge[reverse[e]];
				graph.capacity(back, graph.flow(back));
			}
		}
	}

	/** Does this tick's plan carry the machines' needs on fixed paths? Else {@link #needTransfer} is 0 everywhere. */
	boolean needsFixed() {
		return lockNeeds;
	}

	/** What the machines' needs move along cable edge {@code e} this tick, netted against the edge back. */
	long needTransfer(int e) {
		return Math.max(0L, needHop[e] - needHop[reverse[e]]);
	}

	/**
	 * What the rest of the plan moves along cable edge {@code e} this tick: the plan's flow on it less the needs'.
	 * Never negative — what was planned after the needs never reroutes them ({@link FlowPlan#lock}) — and not
	 * netted against the edge back, so it never runs against the needs' stream.
	 */
	long restTransfer(int e) {
		long all = hops.net(e) - hops.net(reverse[e]);
		long needs = needHop[e] - needHop[reverse[e]];
		return Math.max(0L, all - needs);
	}

	private int node(int sink) {
		return 2 * cables.size() + sources.size() + sink;
	}

	/** Rule 1, then rule 2, then the machines' buffers — out of one source node, each class nearest first. */
	private void stage(int from, boolean takersToo) {
		byDistance(from, PlanEnds.MACHINE, false, true);
		recordNeeds();
		byDistance(from, PlanEnds.STORE, takersToo, false);
		byDistance(from, PlanEnds.MACHINE, false, false);
	}

	private void byDistance(int from, int kind, boolean takersToo, boolean needOnly) {
		int last = Integer.MIN_VALUE;
		while (true) {
			int next = Integer.MAX_VALUE;
			for (PlanEnds.Sink<P> s : sinks) {
				if (matches(s, kind, takersToo) && s.distance > last && s.distance < next) {
					next = s.distance;
				}
			}
			if (next == Integer.MAX_VALUE) {
				return;
			}
			int n = collect(next, kind, takersToo, needOnly);
			if (kind == PlanEnds.MACHINE && !needOnly) {
				topUp(from, n);
			} else {
				share(from, n, needOnly);
			}
			last = next;
		}
	}

	private static boolean matches(PlanEnds.Sink<?> s, int kind, boolean takersToo) {
		return s.kind == kind || (takersToo && kind == PlanEnds.STORE && s.kind == PlanEnds.TAKER);
	}

	private long want(PlanEnds.Sink<P> s, boolean needOnly) {
		long w = Math.min((s.machine() && needOnly ? s.need : s.room) - s.planned, reach(s));
		return Math.max(0L, s.taker() ? Math.min(w, takerBudget) : w);
	}

	/**
	 * The most a sink can still be given this tick, whatever the routing: what its cables pass in all less what they
	 * already give it. Asking a path search for more costs one search that finds nothing — on a grid, one per sink.
	 */
	private long reach(PlanEnds.Sink<P> s) {
		long r = 0L;
		for (int i = 0; i < s.cableCount; i++) {
			r += graph.capacity(throughEdge[s.cables[i]]) - graph.flow(s.edges[i]);
		}
		return r;
	}

	/** The sinks of one kind at one distance that still want something, in {@link #group}; returns how many. */
	private int collect(int distance, int kind, boolean takersToo, boolean needOnly) {
		int n = 0;
		for (int i = 0; i < sinks.size(); i++) {
			PlanEnds.Sink<P> s = sinks.get(i);
			if (matches(s, kind, takersToo) && s.distance == distance && s.cableCount > 0 && want(s, needOnly) > 0) {
				if (n == group.length) {
					group = Arrays.copyOf(group, n * 2);
				}
				group[n++] = i;
			}
		}
		return n;
	}

	/**
	 * The machines' buffers, the last class: each takes what is left in turn, from a start that rotates tick by
	 * tick — no even share by rounds. A buffer is the machine's own reserve; sharing it out evenly every tick cost
	 * hundreds of path searches on a sheet of 64 machines (the stand's E34) for no rule of the player's.
	 */
	private void topUp(int from, int n) {
		int start = Math.floorMod(cursor, Math.max(1, n));
		for (int k = 0; k < n; k++) {
			PlanEnds.Sink<P> s = sinks.get(group[(start + k) % n]);
			long w = want(s, false);
			if (w > 0) {
				credit(s, graph.augmentAll(from, node(group[(start + k) % n]), w));
			}
		}
	}

	/** One pass over the group, sink after sink, each taking what it wants; returns the total ({@link #greedyAll}). */
	private long greedy(int from, int n, boolean needOnly) {
		greedyAll = true;
		long total = 0L;
		// One search serves the whole group while nothing saturates; a sink the tree cannot bring its want searches
		// on its own, and the next sink that wants starts a new tree. A sink the tree does not reach at all has no
		// path: a push only opens residual edges between nodes the tree already reached. A tree searches the whole
		// graph where a single search stops at its target: only a group of a few sinks is worth one.
		boolean fresh = n < TREE_GROUP;
		for (int k = 0; k < n; k++) {
			PlanEnds.Sink<P> s = sinks.get(group[k]);
			before[k] = s.planned;
			long w = want(s, needOnly);
			long got = 0L;
			if (w > 0 && n < TREE_GROUP) {
				got = graph.augmentAll(from, node(group[k]), w);
			} else if (w > 0) {
				if (!fresh) {
					graph.tree(from);
					fresh = true;
				}
				if (graph.treeReaches(node(group[k]))) {
					got = graph.pushTree(from, node(group[k]), w);
					if (got < w) {
						got += graph.augmentAll(from, node(group[k]), w - got);
						fresh = false;
					}
				}
			}
			credit(s, got);
			greedyAll &= got == w;
			total += got;
		}
		return total;
	}

	/**
	 * Give the group what it wants; if it cannot all have it, share it out evenly instead (water-filling): rounds in
	 * which every sink still wanting takes the same chunk, from a rotating start. A round in which one sink cannot
	 * have its chunk is taken back and tried with half the chunk; at a chunk of one unit it stands, and a sink that
	 * got nothing drops out. The result is that of handing out one unit a round — what a set of amounts can be
	 * routed depends on the amounts, not on the paths — at a few rounds instead of one per unit (MOD-789, review of
	 * Variant 1: one breadth-first search per EU cost 25 ms a tick on a 1024-cable HV grid).
	 */
	private void share(int from, int n, boolean needOnly) {
		if (n == 0) {
			return;
		}
		if (before.length < n) {
			before = new long[n * 2];
			roundBefore = new long[n * 2];
			active = new boolean[n * 2];
		}
		graph.save();
		long budget = takerBudget;
		long total = greedy(from, n, needOnly);
		boolean all = greedyAll;
		// One pass, sink after sink, reaches the group's most in all (the amounts a group can be routed form a
		// polymatroid: every maximal set of them has the same sum), so the even share hands out that same total.
		if (all || n == 1 || total == 0) {
			return;
		}
		graph.restore();
		takerBudget = budget;
		for (int k = 0; k < n; k++) {
			sinks.get(group[k]).planned = before[k];
			active[k] = true;
		}
		evenShare(from, n, needOnly, total);
		// The amounts are settled; route them again in the group's own order, not the rotating one, so the paths
		// stay the same tick after tick — a unit travels one hop a tick, and a path that changes with the rotation
		// leaves a cable empty where the plan draws (ADR-001).
		graph.restore();
		takerBudget = budget;
		for (int k = 0; k < n; k++) {
			PlanEnds.Sink<P> s = sinks.get(group[k]);
			long amount = s.planned - before[k];
			s.planned = before[k];
			credit(s, graph.augmentAll(from, node(group[k]), amount));
		}
	}

	/**
	 * Hand out {@code total} to the group in even rounds from a rotating start: a round in which a sink falls short
	 * of its chunk is taken back and tried with a smaller one; a round of one unit stands, and drops whoever got none.
	 */
	private void evenShare(int from, int n, boolean needOnly, long total) {
		int start = Math.floorMod(cursor, n);
		long remaining = total;
		long ceiling = Long.MAX_VALUE;
		int live;
		while (remaining > 0 && (live = live(n, needOnly)) > 0) {
			long chunk = Math.max(1L, Math.min(remaining / live, ceiling));
			graph.mark();
			long roundBudget = takerBudget;
			boolean whole = true;
			long given = 0L;
			long least = Long.MAX_VALUE;
			for (int k = 0; k < n && given < remaining; k++) {
				int at = (start + k) % n;
				if (!active[at]) {
					continue;
				}
				PlanEnds.Sink<P> s = sinks.get(group[at]);
				roundBefore[at] = s.planned;
				long c = Math.min(Math.min(chunk, want(s, needOnly)), remaining - given);
				// short: a taker the shared budget cut, or a sink the cables could not bring its chunk
				boolean full = c > 0 && c == Math.min(chunk, wantUncut(s, needOnly));
				long got = c > 0 ? credit(s, graph.augmentAll(from, node(group[at]), c)) : 0L;
				given += got;
				if (!full || got < c) {
					whole = false;
					least = Math.min(least, got);
					if (chunk == 1 && got == 0) {
						active[at] = false;
					}
				}
			}
			if (whole || chunk == 1) {
				remaining -= given;
				if (!whole) {
					ceiling = Long.MAX_VALUE; // a sink dropped out: the others may take more again
				}
			} else {
				graph.rollback();
				takerBudget = roundBudget;
				for (int k = 0; k < n; k++) {
					if (active[k]) {
						sinks.get(group[k]).planned = roundBefore[k];
					}
				}
				// next: what the shortest sink did get, else half — a chunk every sink can have
				ceiling = least > 0 && least < chunk ? least : Math.max(1L, chunk / 2);
			}
		}
	}

	/** Drop the sinks that want nothing more; how many are left? */
	private int live(int n, boolean needOnly) {
		int live = 0;
		for (int k = 0; k < n; k++) {
			if (active[k] && want(sinks.get(group[k]), needOnly) <= 0) {
				active[k] = false;
			}
			live += active[k] ? 1 : 0;
		}
		return live;
	}

	/** {@link #want} before the takers' shared budget cuts it. */
	private long wantUncut(PlanEnds.Sink<P> s, boolean needOnly) {
		return Math.max(0L, Math.min((s.machine() && needOnly ? s.need : s.room) - s.planned, reach(s)));
	}

	private long credit(PlanEnds.Sink<P> s, long got) {
		s.planned += got;
		if (s.taker()) {
			takerBudget -= got;
		}
		return got;
	}
}
