package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Property check of the line tick on random small topologies (MOD-789, ADR-049 — Variant 1) over production's
 * core, against an oracle of its own ({@link FlowOracle}: a max flow written apart from the planner) — the Java twin
 * of {@code docs/tasks/MOD-789-.../sim/vcheck.py}, seeded and deterministic.
 *
 * <p>Topologies: a cable set grown from one cell (the grid closes rings by itself), 5–20 cables; 1–3 generators
 * of 4, 8 or 12 EU/t on free cells whose cables touch no other generator; 1–4 machines working 2 or 4 EU/t, at
 * most 8 EU/t in all; 0–3 boxes. On a quarter of the seeds one machine works 16 or 24 EU/t, above what one copper
 * cable carries; on a third, a consumer of unknown need ({@link LineRig#foreign}); on a quarter, a tin packet of 8.
 * Every invariant runs twice, without loss and with copper's 2 % a block (MOD-021) — there in gross EU, what the
 * cables give up. Over windows of {@value #K} ticks after {@value #WARM}, with a
 * tolerance of {@code max(K/2, 3 %)} (the oracle is a steady state; the line fills a few ticks behind it):
 * <ul>
 *   <li>I1 conservation, every tick ({@link LineRig#tick}) and over the run: injected = cables + endpoints +
 *       work spent + loss.</li>
 *   <li>I2 no waste: while a generator held output back on every tick, the consumers together got at least what
 *       the oracle says the line could bring them (every consumer's room) — a generator never idles while a sink
 *       with room has a path with spare capacity.</li>
 *   <li>I3 storages only what the machines cannot take: whenever a box gained, the machines got the oracle's most
 *       for their needs.</li>
 *   <li>I4 quiet run: the machines stop at t = 300, the boxes are small; once no consumer has room the cables
 *       stop changing (the network goes still — ADR-007).</li>
 *   <li>I5 nearest first: for every machine distance (cables from the nearest generator), the machines up to it
 *       got the oracle's most for their needs; the storage class the same after the machines; and on a single line
 *       of machines of one work rate, no farther machine gains on a tick on which a nearer one got less than its
 *       need.</li>
 *   <li>I6 a consumer of unknown need never blocks a machine: it gained while the machines got less than I3's
 *       most.</li>
 *   <li>I7 no consumer takes more than one packet in a tick.</li>
 *   <li>I9 no oscillation: over the last 100 ticks the per-tick deliveries repeat with some period up to 24, but
 *       for at most 5 ticks (a buffer that fills up changes the pattern once).</li>
 * </ul>
 *
 * <p>{@value #SEEDS} seeds by default (a few seconds in every build); the full set of 200 runs with
 * {@code ALAMOD_FLOW_SEEDS=200 ./gradlew :common:test --tests dev.alaindustrial.core.energy.FlowPropertyTest --rerun}
 * ({@code --rerun}: the variable is no task input, so Gradle would call the default run up to date).
 *
 * @implements MOD-789-FW05 — on random topologies no machine starves while a store gains or a generator idles
 */
class FlowPropertyTest {

	private static final int SEEDS = 40;
	private static final int TICKS = 600;
	private static final int K = 100;
	private static final int WARM = 200;
	private static final int[][] DIRS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

	/**
	 * One random network: cables, generators (cell, output), machines (cell, work), boxes, consumers of unknown need
	 * (cell, use, capacity), and the packet cap.
	 */
	record Topology(List<LineRig.Cell> cables, List<long[]> gens, List<long[]> machines, List<LineRig.Cell> boxes,
			List<long[]> foreign, long packet) {
	}

	private static LineRig.Cell cell(int x, int z) {
		return new LineRig.Cell(x, z);
	}

	private static Set<LineRig.Cell> touch(LineRig.Cell p, Set<LineRig.Cell> cables) {
		Set<LineRig.Cell> out = new LinkedHashSet<>();
		for (int[] d : DIRS) {
			LineRig.Cell n = cell(p.x() + d[0], p.z() + d[1]);
			if (cables.contains(n)) {
				out.add(n);
			}
		}
		return out;
	}

	static Topology topology(long seed) {
		Random rnd = new Random(seed);
		int n = 5 + rnd.nextInt(16);
		List<LineRig.Cell> cells = new ArrayList<>(List.of(cell(0, 0)));
		Set<LineRig.Cell> set = new LinkedHashSet<>(cells);
		while (cells.size() < n) {
			LineRig.Cell c = cells.get(rnd.nextInt(cells.size()));
			int[] d = DIRS[rnd.nextInt(4)];
			LineRig.Cell next = cell(c.x() + d[0], c.z() + d[1]);
			if (set.add(next)) {
				cells.add(next);
			}
		}
		TreeSet<LineRig.Cell> around = new TreeSet<>(Comparator.comparingInt(LineRig.Cell::x)
				.thenComparingInt(LineRig.Cell::z));
		for (LineRig.Cell c : cells) {
			for (int[] d : DIRS) {
				LineRig.Cell next = cell(c.x() + d[0], c.z() + d[1]);
				if (!set.contains(next)) {
					around.add(next);
				}
			}
		}
		List<LineRig.Cell> free = new ArrayList<>(around);
		Collections.shuffle(free, rnd);
		List<long[]> gens = new ArrayList<>();
		Set<LineRig.Cell> used = new LinkedHashSet<>();
		for (int g = 1 + rnd.nextInt(3); g > 0; g--) {
			for (int i = 0; i < free.size(); i++) {
				Set<LineRig.Cell> t = touch(free.get(i), set);
				if (Collections.disjoint(t, used)) {
					used.addAll(t);
					LineRig.Cell p = free.remove(i);
					gens.add(new long[] {p.x(), p.z(), 4L * (1 + rnd.nextInt(3))});
					break;
				}
			}
		}
		List<long[]> machines = new ArrayList<>();
		long work = 0;
		for (int m = Math.min(free.size(), 1 + rnd.nextInt(4)); m > 0; m--) {
			long w = rnd.nextBoolean() ? 2 : 4;
			if (work + w > 8) {
				break;
			}
			LineRig.Cell p = free.remove(free.size() - 1);
			machines.add(new long[] {p.x(), p.z(), w});
			work += w;
		}
		List<LineRig.Cell> boxes = new ArrayList<>();
		for (int b = Math.min(free.size(), rnd.nextInt(4)); b > 0; b--) {
			boxes.add(free.remove(free.size() - 1));
		}
		// Review round 3: what the generator above never drew. Drawn after the original choices, so a seed keeps
		// the network it always had and only gains these.
		Random more = new Random(seed * 31 + 7);
		if (!machines.isEmpty() && more.nextInt(4) == 0) {
			machines.get(0)[2] = more.nextBoolean() ? 16 : 24; // above one copper cable's 12 EU a tick
		}
		List<long[]> foreign = new ArrayList<>();
		if (!free.isEmpty() && more.nextInt(3) == 0) {
			LineRig.Cell p = free.remove(free.size() - 1);
			foreign.add(new long[] {p.x(), p.z(), new long[] {0, 4, 16}[more.nextInt(3)],
				more.nextBoolean() ? 2_000 : 1_000_000});
		}
		long packet = more.nextInt(4) == 0 ? 8 : LineRig.PACKET;
		return new Topology(cells, gens, machines, boxes, foreign, packet);
	}

	static Topology line(long seed) {
		Random rnd = new Random(seed ^ 0x5DEECE66DL);
		int n = 4 + rnd.nextInt(9);
		List<LineRig.Cell> cells = new ArrayList<>();
		for (int x = 1; x <= n; x++) {
			cells.add(cell(x, 0));
		}
		long use = new long[] {4, 8, 14}[rnd.nextInt(3)];
		List<Integer> xs = new ArrayList<>();
		for (int x = 1; x <= n; x++) {
			xs.add(x);
		}
		Collections.shuffle(xs, rnd);
		List<Integer> picked = new ArrayList<>(xs.subList(0, 2 + rnd.nextInt(Math.min(4, n) - 1)));
		Collections.sort(picked);
		List<long[]> machines = new ArrayList<>();
		for (int x : picked) {
			machines.add(new long[] {x, 1, use});
		}
		List<long[]> gens = new ArrayList<>();
		gens.add(new long[] {0, 0, 4L * (1 + rnd.nextInt(3))});
		return new Topology(cells, gens, machines, List.of(), List.of(), LineRig.PACKET);
	}

	static LineRig build(String name, Topology t, double loss, long boxCapacity) {
		LineRig r = new LineRig(name, loss).packet(t.packet());
		for (LineRig.Cell c : t.cables()) {
			r.cable(c.x(), c.z());
		}
		for (long[] g : t.gens()) {
			r.source((int) g[0], (int) g[1], g[2]);
		}
		for (int i = 0; i < t.machines().size(); i++) {
			long[] m = t.machines().get(i);
			r.machine("M" + i, (int) m[0], (int) m[1], m[2]);
		}
		for (int i = 0; i < t.boxes().size(); i++) {
			r.box("B" + i, t.boxes().get(i).x(), t.boxes().get(i).z(), boxCapacity);
		}
		for (int i = 0; i < t.foreign().size(); i++) {
			long[] f = t.foreign().get(i);
			r.foreign("F" + i, (int) f[0], (int) f[1], f[2], f[3], 32);
		}
		return r;
	}

	/**
	 * One tick of the window: what the machines and the storage class (boxes, consumers of unknown need) got, what
	 * the consumers of unknown need got, did a generator hold output back, and the oracle's most for this tick's
	 * rooms and needs — in all, for the machines' needs, and cumulative by distance (machines; then the storage
	 * class after the machines).
	 */
	private record Tick(long machines, long machinesNet, long stores, long foreign, long[] machinesUpTo,
			long[] storesUpTo, boolean held, long oracleAll, long oracleMachines, long[] oracleMachinesUpTo,
			long[] oracleStoresUpTo) {
	}

	/** Run a topology; returns the invariants it broke. */
	static Set<String> run(String name, Topology t, int ticks, boolean isLine, int stopAt, double loss) {
		LineRig r = build(name, t, loss, stopAt > 0 ? 300 : 20_000);
		FlowOracle oracle = new FlowOracle(r);
		Set<String> fails = new LinkedHashSet<>();
		int machines = t.machines().size();
		List<LineRig.End> ms = r.ends.subList(0, machines);
		List<LineRig.End> stores = r.ends.subList(machines, r.ends.size());
		int[] mDist = distances(oracle, ms);
		int[] sDist = distances(oracle, stores);
		ArrayDeque<Tick> window = new ArrayDeque<>();
		List<List<Long>> history = new ArrayList<>();
		List<Boolean> roomHistory = new ArrayList<>();
		List<List<Long>> gains = new ArrayList<>();
		long used = 0;
		boolean stopped = false;
		for (int tick = 1; tick <= ticks; tick++) {
			stopped |= stopAt > 0 && tick >= stopAt;
			Map<LineRig.End, Long> room = new LinkedHashMap<>();
			Map<LineRig.End, Long> need = new LinkedHashMap<>();
			for (LineRig.End e : r.ends) {
				room.put(e, Math.max(0L, Math.min(Math.min(e.buf().maxInsert, t.packet()),
						e.buf().getCapacity() - e.buf().getAmount())));
			}
			for (int i = 0; i < machines; i++) {
				need.put(ms.get(i), stopped ? 0L : Math.min(room.get(ms.get(i)), t.machines().get(i)[2]));
			}
			r.tick();
			for (long g : r.gain.values()) {
				if (g > t.packet()) {
					fails.add("I7");
				}
			}
			gains.add(new ArrayList<>(r.gain.values()));
			if (!stopped) {
				for (long w : r.work().values()) {
					used += w;
				}
			}
			if (tick > WARM && stopAt <= 0 && !isLine) {
				window.addLast(tickOf(r, oracle, ms, stores, mDist, sDist, room, need, loss));
				if (window.size() > K) {
					window.removeFirst();
				}
				if (window.size() == K) {
					checkWindow(window, fails);
				}
			}
			if (isLine && tick > WARM) {
				lineOrder(r, ms, need, fails);
			}
			List<Long> state = new ArrayList<>();
			for (EnergyBuffer b : r.buffers.values()) {
				state.add(b.getAmount());
			}
			history.add(state);
			boolean anyRoom = false;
			for (LineRig.End e : r.ends) {
				anyRoom |= e.buf().getAmount() < e.buf().getCapacity();
			}
			roomHistory.add(anyRoom);
		}
		assertEquals(r.injected, r.inCables() + r.inEnds() + used + r.lost, name + ": I1 over the run");
		if (stopAt > 0) {
			int from = history.size() - 20;
			boolean still = !roomHistory.subList(from, history.size()).contains(true);
			if (still && !history.subList(from, history.size()).stream().allMatch(history.get(from)::equals)) {
				fails.add("I4");
			}
		} else if (!aPattern(gains)) {
			fails.add("I9");
		}
		return fails;
	}

	private static int[] distances(FlowOracle oracle, List<LineRig.End> ends) {
		int[] d = new int[ends.size()];
		for (int i = 0; i < d.length; i++) {
			d[i] = oracle.distance(ends.get(i));
		}
		return d;
	}

	/** The distinct distances of {@code d}, ascending. */
	private static int[] levels(int[] d) {
		return java.util.Arrays.stream(d).distinct().sorted().toArray();
	}

	private static Tick tickOf(LineRig r, FlowOracle oracle, List<LineRig.End> ms, List<LineRig.End> stores,
			int[] mDist, int[] sDist, Map<LineRig.End, Long> room, Map<LineRig.End, Long> netNeed, double loss) {
		// With the MOD-021 loss on, the oracle's caps and the consumers' gains are in what the cables give up: a
		// need is the gross that brings it, a gain the gross that brought it (the plan works in gross, ADR-049).
		Map<LineRig.End, Long> need = new LinkedHashMap<>();
		for (int i = 0; i < ms.size(); i++) {
			LineRig.End e = ms.get(i);
			need.put(e, Math.min(room.get(e), EnergyShare.grossFor(netNeed.get(e), loss, mDist[i])));
		}
		long m = 0;
		long st = 0;
		long mNet = 0;
		long foreign = 0;
		int[] ml = levels(mDist);
		int[] sl = levels(sDist);
		long[] mUp = new long[ml.length];
		long[] sUp = new long[sl.length];
		long[] omUp = new long[ml.length];
		long[] osUp = new long[sl.length];
		for (int i = 0; i < ms.size(); i++) {
			long net = r.gain.get(ms.get(i).name());
			m += EnergyShare.grossFor(net, loss, mDist[i]);
			mNet += net;
			for (int l = 0; l < ml.length; l++) {
				mUp[l] += mDist[i] <= ml[l] ? net : 0;
			}
		}
		for (int i = 0; i < stores.size(); i++) {
			long g = EnergyShare.grossFor(r.gain.get(stores.get(i).name()), loss, sDist[i]);
			st += g;
			foreign += stores.get(i).name().startsWith("F") ? g : 0;
			for (int l = 0; l < sl.length; l++) {
				sUp[l] += sDist[i] <= sl[l] ? g : 0;
			}
		}
		// What the machines get is checked in net EU, what arrives: the oracle's gross most, scaled by what of the
		// gross needs arrives — a machine short by the loss on its path is short (MOD-789, review of Variant 1).
		for (int l = 0; l < ml.length; l++) {
			Map<LineRig.End, Long> caps = new LinkedHashMap<>();
			for (int i = 0; i < ms.size(); i++) {
				if (mDist[i] <= ml[l]) {
					caps.put(ms.get(i), need.get(ms.get(i)));
				}
			}
			omUp[l] = netOf(oracle.maxFlow(caps), caps, netNeed);
		}
		for (int l = 0; l < sl.length; l++) {
			Map<LineRig.End, Long> caps = new LinkedHashMap<>(need);
			for (int i = 0; i < stores.size(); i++) {
				if (sDist[i] <= sl[l]) {
					caps.put(stores.get(i), room.get(stores.get(i)));
				}
			}
			osUp[l] = oracle.maxFlow(caps);
		}
		long supply = 0;
		for (LineRig.Source s : r.sources) {
			supply += Math.min(s.out(), r.packet);
		}
		long all = Math.min(supply, oracle.maxFlow(room));
		return new Tick(m, mNet, st, foreign, mUp, sUp, r.held, all, netOf(oracle.maxFlow(need), need, netNeed), omUp,
				osUp);
	}

	/** {@code gross} of the machines in {@code grossNeed}, in net EU: scaled by their net needs over their gross. */
	private static long netOf(long gross, Map<LineRig.End, Long> grossNeed, Map<LineRig.End, Long> netNeed) {
		long g = 0;
		long n = 0;
		for (Map.Entry<LineRig.End, Long> e : grossNeed.entrySet()) {
			g += e.getValue();
			n += Math.min(e.getValue(), netNeed.get(e.getKey()));
		}
		return g <= 0 ? gross : gross * n / g;
	}

	private static long tolerance(long want) {
		return Math.max(K / 2, want * 3 / 100);
	}

	private static void checkWindow(ArrayDeque<Tick> window, Set<String> fails) {
		long m = 0;
		long mNet = 0;
		long st = 0;
		long foreign = 0;
		long all = 0;
		long om = 0;
		boolean held = true;
		Tick first = window.getFirst();
		long[] mUp = new long[first.machinesUpTo().length];
		long[] omUp = new long[mUp.length];
		long[] sUp = new long[first.storesUpTo().length];
		long[] osUp = new long[sUp.length];
		for (Tick w : window) {
			m += w.machines();
			mNet += w.machinesNet();
			st += w.stores();
			foreign += w.foreign();
			all += w.oracleAll();
			om += w.oracleMachines();
			held &= w.held();
			for (int l = 0; l < mUp.length; l++) {
				mUp[l] += w.machinesUpTo()[l];
				omUp[l] += w.oracleMachinesUpTo()[l];
			}
			for (int l = 0; l < sUp.length; l++) {
				sUp[l] += w.storesUpTo()[l];
				osUp[l] += w.oracleStoresUpTo()[l];
			}
		}
		if (held && m + st < all - tolerance(all)) {
			fails.add("I2");
		}
		if (st > 0 && mNet < om - tolerance(om)) {
			fails.add(foreign > 0 ? "I6" : "I3");
		}
		for (int l = 0; l < mUp.length; l++) {
			if (mUp[l] < omUp[l] - tolerance(omUp[l])) {
				fails.add("I5");
			}
		}
		for (int l = 0; l < sUp.length; l++) {
			if (m + sUp[l] < osUp[l] - tolerance(osUp[l])) {
				fails.add("I5");
			}
		}
	}

	/** A single line, one work rate: a farther machine gains nothing on a tick a nearer one got less than its need. */
	private static void lineOrder(LineRig r, List<LineRig.End> ms, Map<LineRig.End, Long> need, Set<String> fails) {
		for (int a = 0; a < ms.size(); a++) {
			if (r.gain.get(ms.get(a).name()) < need.get(ms.get(a))) {
				for (int b = a + 1; b < ms.size(); b++) {
					if (r.gain.get(ms.get(b).name()) > 0) {
						fails.add("I5");
					}
				}
			}
		}
	}

	/** I9: the last 100 ticks repeat with some period up to 24, but for at most 5 ticks. */
	private static boolean aPattern(List<List<Long>> gains) {
		int n = gains.size();
		for (int p = 1; p <= 24; p++) {
			int off = 0;
			for (int i = n - 100; i < n; i++) {
				off += gains.get(i).equals(gains.get(i - p)) ? 0 : 1;
			}
			if (off <= 5) {
				return true;
			}
		}
		return false;
	}

	/**
	 * With {@code ALAMOD_FLOW_DUMP=<file>} set, append the topology of a seed as one {@code DUMP} line — what
	 * {@code sim/seeds_all.py} and {@code sim/jcheck.py} replay on the replica of {@code main} and of the models.
	 */
	private static void dump(long seed, Topology t) {
		String file = System.getenv("ALAMOD_FLOW_DUMP");
		if (file == null || file.isBlank()) {
			return;
		}
		StringBuilder b = new StringBuilder("DUMP ").append(seed).append(" cables=");
		t.cables().forEach(c -> b.append(c.x()).append(',').append(c.z()).append(';'));
		b.append(" gens=");
		t.gens().forEach(g -> b.append(g[0]).append(',').append(g[1]).append(',').append(g[2]).append(';'));
		b.append(" machines=");
		t.machines().forEach(m -> b.append(m[0]).append(',').append(m[1]).append(',').append(m[2]).append(';'));
		b.append(" boxes=");
		t.boxes().forEach(c -> b.append(c.x()).append(',').append(c.z()).append(';'));
		b.append(" foreign=");
		t.foreign().forEach(f -> b.append(f[0]).append(',').append(f[1]).append(',').append(f[2]).append(',')
				.append(f[3]).append(';'));
		b.append(" packet=").append(t.packet()).append('\n');
		try {
			Files.writeString(Path.of(file), b, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	void noRandomNetworkBreaksAnInvariant() {
		Map<String, List<Long>> bad = new LinkedHashMap<>();
		String full = System.getenv("ALAMOD_FLOW_SEEDS");
		int seeds = full != null ? Integer.parseInt(full.trim()) : SEEDS;
		for (long seed = 0; seed < seeds; seed++) {
			Topology t = topology(seed);
			dump(seed, t);
			for (String f : run("net" + seed, t, TICKS, false, 0, 0.0)) {
				bad.computeIfAbsent(f, k -> new ArrayList<>()).add(seed);
			}
			for (String f : run("loss" + seed, t, TICKS, false, 0, 0.02)) {
				bad.computeIfAbsent(f + " (loss)", k -> new ArrayList<>()).add(seed);
			}
			for (String f : run("quiet" + seed, t, 2400, false, 300, 0.0)) {
				bad.computeIfAbsent(f, k -> new ArrayList<>()).add(seed);
			}
			for (String f : run("line" + seed, line(seed), TICKS, true, 0, 0.0)) {
				bad.computeIfAbsent(f + " (line)", k -> new ArrayList<>()).add(seed);
			}
		}
		assertTrue(bad.isEmpty(), "invariants broken, by seed: " + bad);
	}
}
