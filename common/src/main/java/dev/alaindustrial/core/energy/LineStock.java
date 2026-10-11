package dev.alaindustrial.core.energy;

import java.util.Arrays;
import java.util.List;

/**
 * Stage B of a line tick (MOD-789, ADR-049): the energy the cables already hold beyond what the plan runs through
 * them — what a store discharged into the line, what a line kept overnight, what a branch held when the demand
 * moved. It is carried as a bucket brigade, one hop a tick (ADR-001): the consumers draw it from their own cables
 * in the order of the rule (the machines' unmet need, then the storages nearest first, then the machines'
 * buffers), and what is left moves one cable down a field toward the consumers that still want something — toward
 * a machine whose need is unmet wherever one can be reached, toward the other consumers otherwise. It moves only
 * out of the stock: what the plan runs through a cable is never touched.
 *
 * <p>Persistent per line, its arrays reused; Minecraft-free.
 *
 * @param <P> the position type of a cable or an endpoint
 */
final class LineStock<P> {

	/** The potential of a cable reached only by the field of the non-machine wants: past every machine's. */
	private static final int STORE_FIELD = Integer.MAX_VALUE / 4;
	private static final int OFF = Integer.MAX_VALUE;

	private long[] stock = new long[0];
	private long[] leftNeed = new long[8];
	private long[] leftRoom = new long[8];
	private int[] pot = new int[0];
	private int[] queue = new int[0];
	private int[] order = new int[0];
	private boolean[] roomBefore = new boolean[8];
	private boolean wanting;
	/** While the stores and the buffers draw: skip the stock held for a machine whose need is unmet. */
	private boolean reserve;
	/** The cables the field of the latest {@link #draw} reached, for {@link #move}. */
	private int fieldCount;

	/**
	 * Per cable, what it holds now beyond what the plan takes out of it this tick (its planned hops and draws):
	 * the stock. Call once per tick, before the plan is carried out.
	 */
	long[] measure(LinePlanner<P> plan) {
		int c = plan.cableCount();
		if (stock.length < c) {
			stock = new long[c];
			pot = new int[c];
			queue = new int[c];
			order = new int[c];
		}
		HopOrder hops = plan.hops();
		for (int i = 0; i < c; i++) {
			long out = 0L;
			for (int e = plan.neighbourStart(i); e < plan.neighbourEnd(i); e++) {
				out += Math.max(0L, hops.net(e));
			}
			EnergyBuffer b = plan.buffer(i);
			stock[i] = b == null ? 0L : b.getAmount() - out;
		}
		FlowPlan graph = plan.graph();
		for (PlanEnds.Sink<P> s : plan.sinks()) {
			for (int k = 0; k < s.cableCount; k++) {
				stock[s.cables[k]] -= graph.flow(s.edges[k]);
			}
		}
		for (int i = 0; i < c; i++) {
			stock[i] = Math.max(0L, stock[i]);
		}
		return stock;
	}

	/** Does a consumer still want something after this tick's draws? Then the line does not fill outward. */
	boolean wanting() {
		return wanting;
	}

	/**
	 * The consumers' draws out of the stock in their own cables, in the order of the rule, added to
	 * {@code pulled}. Call after the plan's own draws; {@link #move} carries the rest on.
	 */
	void draw(LinePlanner<P> plan, long[] pulled, int rotation, EnergyPort.Txn tx) {
		List<PlanEnds.Sink<P>> sinks = plan.sinks();
		int n = sinks.size();
		if (leftNeed.length < n) {
			leftNeed = new long[n * 2];
			leftRoom = new long[n * 2];
		}
		for (int k = 0; k < n; k++) {
			PlanEnds.Sink<P> s = sinks.get(k);
			// What the plan's draws actually brought, not what was planned: a draw held to what its cable had when
			// the tick began can fall short, and the stock makes that up.
			leftNeed[k] = s.machine() ? Math.max(0L, s.need - pulled[k]) : 0L;
			leftRoom[k] = s.taker() ? 0L : Math.max(0L, s.room - pulled[k]);
		}
		// The stores' part of the field is laid from their room before the draws: a store that drinks its whole room
		// out of the stock this tick wants again the next, and the stock behind it must keep coming one hop at a
		// time. The machines' part is laid after their needs are drawn: the stock a fed machine's cable still holds
		// goes on toward the machine that is not fed yet.
		if (roomBefore.length < n) {
			roomBefore = new boolean[n * 2];
		}
		for (int k = 0; k < n; k++) {
			roomBefore[k] = leftRoom[k] > 0;
		}
		byDistance(plan, pulled, tx, PlanEnds.MACHINE, true, rotation);
		fieldCount = field(plan);
		// The stock a machine whose need is still unmet can reach is held for it: neither a store nor a buffer takes
		// it, it moves on toward that machine (rule 1 before rules 2 and 3, ADR-049).
		reserve = true;
		byDistance(plan, pulled, tx, PlanEnds.STORE, false, rotation);
		byDistance(plan, pulled, tx, PlanEnds.MACHINE, false, rotation);
		reserve = false;
		wanting = false;
		for (int k = 0; k < n; k++) {
			wanting |= leftRoom[k] > 0;
		}
	}

	/** Then, after the plan's own hops, the stock moves one hop toward whoever still wants ({@link #draw} first). */
	void move(LinePlanner<P> plan, long packetCap, int rotation, EnergyPort.Txn tx) {
		handOn(plan, fieldCount, packetCap, rotation, tx);
	}

	/** The consumers of one kind, nearest first; those at one distance take a unit each in turn (ADR-049). */
	private void byDistance(LinePlanner<P> plan, long[] pulled, EnergyPort.Txn tx, int kind, boolean needOnly,
			int rotation) {
		List<PlanEnds.Sink<P>> sinks = plan.sinks();
		int n = sinks.size();
		int last = Integer.MIN_VALUE;
		while (true) {
			int next = Integer.MAX_VALUE;
			for (PlanEnds.Sink<P> s : sinks) {
				if (s.kind == kind && s.distance > last && s.distance < next) {
					next = s.distance;
				}
			}
			if (next == Integer.MAX_VALUE) {
				return;
			}
			for (boolean progress = true; progress;) {
				progress = false;
				for (int j = 0; j < n; j++) {
					int k = Math.floorMod(rotation + j, n);
					PlanEnds.Sink<P> s = sinks.get(k);
					if (s.kind == kind && s.distance == next && (needOnly ? leftNeed[k] : leftRoom[k]) > 0) {
						progress |= take(plan, s, k, 1L, pulled, tx) > 0;
					}
				}
			}
			last = next;
		}
	}

	private long take(LinePlanner<P> plan, PlanEnds.Sink<P> s, int k, long want, long[] pulled, EnergyPort.Txn tx) {
		long took = 0L;
		for (int i = 0; i < s.cableCount && took < want; i++) {
			int c = s.cables[i];
			EnergyBuffer b = plan.buffer(c);
			if (b == null || reserve && pot[c] < STORE_FIELD) {
				continue;
			}
			long t = Math.min(Math.min(want - took, stock[c]), Math.min(b.getAmount(), leftRoom[k]));
			if (t <= 0) {
				continue;
			}
			long got = b.extract(t, tx);
			stock[c] -= got;
			took += got;
			pulled[k] += got;
			leftRoom[k] -= got;
			leftNeed[k] = Math.max(0L, leftNeed[k] - got);
		}
		return took;
	}

	/**
	 * The field the stock moves down: distance to the nearest cable of a machine whose need is unmet, or — only
	 * where no such machine can be reached — {@link #STORE_FIELD} plus the distance to the nearest cable of any
	 * consumer that still has room. Leaves the cables in ascending potential in {@link #order}; returns how many.
	 */
	private int field(LinePlanner<P> plan) {
		int c = plan.cableCount();
		Arrays.fill(pot, 0, c, OFF);
		int count = flood(plan, true, 0, 0);
		return flood(plan, false, STORE_FIELD, count);
	}

	private int flood(LinePlanner<P> plan, boolean machines, int base, int count) {
		List<PlanEnds.Sink<P>> sinks = plan.sinks();
		int head = count;
		int tail = count;
		for (int k = 0; k < sinks.size(); k++) {
			PlanEnds.Sink<P> s = sinks.get(k);
			boolean seed = machines ? leftNeed[k] > 0 : roomBefore[k];
			for (int i = 0; seed && i < s.cableCount; i++) {
				int cb = s.cables[i];
				if (pot[cb] == OFF) {
					pot[cb] = base;
					order[tail++] = cb;
				}
			}
		}
		while (head < tail) {
			int u = order[head++];
			for (int e = plan.neighbourStart(u); e < plan.neighbourEnd(u); e++) {
				int v = plan.neighbour(e);
				if (pot[v] == OFF) {
					pot[v] = pot[u] + 1;
					order[tail++] = v;
				}
			}
		}
		return tail;
	}

	/** Each cable, nearest the wants first, hands its stock to the neighbours strictly downhill, one hop. */
	private void handOn(LinePlanner<P> plan, int count, long packetCap, int rotation, EnergyPort.Txn tx) {
		for (int k = 0; k < count; k++) {
			int c = order[k];
			EnergyBuffer donor = plan.buffer(c);
			int first = plan.neighbourStart(c);
			int deg = plan.neighbourEnd(c) - first;
			for (int j = 0; j < deg && donor != null && stock[c] > 0; j++) {
				int e = first + Math.floorMod(rotation + j, deg);
				int v = plan.neighbour(e);
				EnergyBuffer to = plan.buffer(v);
				if (to == null || pot[v] >= pot[c]) {
					continue;
				}
				long give = Math.min(Math.min(stock[c], donor.getAmount()),
						Math.min(to.getCapacity() - to.getAmount(), packetCap));
				if (give > 0) {
					long got = donor.extract(give, tx);
					to.insert(got, tx);
					stock[c] -= got;
					plan.stockHopped(e);
				}
			}
		}
	}
}
