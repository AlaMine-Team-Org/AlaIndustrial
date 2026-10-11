package dev.alaindustrial.core.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The independent oracle of {@link FlowPropertyTest} (MOD-789, ADR-049): the most a {@link LineRig} can deliver in
 * a steady tick to a set of consumers with given caps — a plain Edmonds–Karp max flow written apart from the
 * production planner. A cable passes at most its buffer a tick, a hop and a receiver at most one packet, a generator
 * at most its output and one packet; the cables' contents are not counted (the steady state). Results are memoised
 * per cap set: a steady run asks the same question every tick.
 */
final class FlowOracle {

	private final LineRig rig;
	private final Map<LineRig.Cell, Integer> cable = new LinkedHashMap<>();
	private final Map<String, Long> memo = new HashMap<>();
	private final Map<LineRig.Cell, Integer> distance = new HashMap<>();

	FlowOracle(LineRig rig) {
		this.rig = rig;
		for (LineRig.Cell c : rig.cables) {
			cable.put(c, cable.size());
		}
		ArrayDeque<LineRig.Cell> q = new ArrayDeque<>();
		for (LineRig.Source s : rig.sources) {
			for (LineRig.Cell n : rig.neighbours(s.pos())) {
				if (distance.putIfAbsent(n, 1) == null) {
					q.add(n);
				}
			}
		}
		while (!q.isEmpty()) {
			LineRig.Cell c = q.poll();
			for (LineRig.Cell n : rig.neighbours(c)) {
				if (distance.putIfAbsent(n, distance.get(c) + 1) == null) {
					q.add(n);
				}
			}
		}
	}

	/** Cables between the nearest generator and an endpoint (its nearest cable), or a large number off the field. */
	int distance(LineRig.End e) {
		int best = Integer.MAX_VALUE / 2;
		for (LineRig.Cell n : rig.neighbours(e.pos())) {
			Integer d = distance.get(n);
			if (d != null) {
				best = Math.min(best, d);
			}
		}
		return best;
	}

	/** The most the generators can deliver in a steady tick to the ends with these caps (absent: 0). */
	long maxFlow(Map<LineRig.End, Long> caps) {
		StringBuilder key = new StringBuilder();
		for (LineRig.End e : rig.ends) {
			key.append(caps.getOrDefault(e, 0L)).append(',');
		}
		return memo.computeIfAbsent(key.toString(), k -> compute(caps));
	}

	private long compute(Map<LineRig.End, Long> caps) {
		int c = cable.size();
		int g = rig.sources.size();
		int k = rig.ends.size();
		int s = 2 * c + g + k;
		int t = s + 1;
		List<Map<Integer, Long>> cap = new ArrayList<>();
		for (int i = 0; i <= t; i++) {
			cap.add(new LinkedHashMap<>());
		}
		for (Map.Entry<LineRig.Cell, Integer> en : cable.entrySet()) {
			int i = en.getValue();
			add(cap, 2 * i, 2 * i + 1, rig.buffers.get(en.getKey()).getCapacity());
			for (LineRig.Cell n : rig.neighbours(en.getKey())) {
				add(cap, 2 * i + 1, 2 * cable.get(n), rig.packet);
			}
		}
		for (int j = 0; j < g; j++) {
			LineRig.Source src = rig.sources.get(j);
			add(cap, s, 2 * c + j, Math.min(src.out(), rig.packet));
			for (LineRig.Cell n : rig.neighbours(src.pos())) {
				add(cap, 2 * c + j, 2 * cable.get(n), rig.packet);
			}
		}
		for (int j = 0; j < k; j++) {
			LineRig.End e = rig.ends.get(j);
			long cp = caps.getOrDefault(e, 0L);
			if (cp <= 0) {
				continue;
			}
			for (LineRig.Cell n : rig.neighbours(e.pos())) {
				add(cap, 2 * cable.get(n) + 1, 2 * c + g + j, cp);
			}
			add(cap, 2 * c + g + j, t, cp);
		}
		long total = 0;
		while (true) {
			int[] prev = new int[t + 1];
			java.util.Arrays.fill(prev, -1);
			prev[s] = s;
			ArrayDeque<Integer> q = new ArrayDeque<>(List.of(s));
			while (!q.isEmpty() && prev[t] < 0) {
				int u = q.poll();
				for (Map.Entry<Integer, Long> e : cap.get(u).entrySet()) {
					if (e.getValue() > 0 && prev[e.getKey()] < 0) {
						prev[e.getKey()] = u;
						q.add(e.getKey());
					}
				}
			}
			if (prev[t] < 0) {
				return total;
			}
			long b = Long.MAX_VALUE;
			for (int v = t; v != s; v = prev[v]) {
				b = Math.min(b, cap.get(prev[v]).get(v));
			}
			for (int v = t; v != s; v = prev[v]) {
				cap.get(prev[v]).merge(v, -b, Long::sum);
				cap.get(v).merge(prev[v], b, Long::sum);
			}
			total += b;
		}
	}

	private static void add(List<Map<Integer, Long>> cap, int u, int v, long c) {
		cap.get(u).merge(v, c, Long::sum);
		cap.get(v).putIfAbsent(u, 0L);
	}
}
