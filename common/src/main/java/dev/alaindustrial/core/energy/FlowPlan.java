package dev.alaindustrial.core.energy;

import java.util.Arrays;

/**
 * The residual graph a line's tick is planned on (MOD-789, MOD-755, ADR-049): nodes and paired edges by index,
 * successive augmenting paths found breadth-first. Minecraft-free and position-free — {@link LinePlanner} maps a
 * line onto it. One instance per network, reused tick after tick: every array grows and is never handed out, so a
 * steady tick allocates nothing here.
 *
 * <p>Edge {@code e} and its reverse {@code e ^ 1} are added together; the flow on a forward edge is its original
 * capacity minus its residual one. An augmenting path may run backwards along an edge that carries flow — that
 * reroutes energy already planned, never reduces what a sink planned earlier was given, because a path ends at
 * its target sink and never passes on beyond one.
 */
final class FlowPlan {

	/** A capacity that never binds: a consumer's edge from its cable (its own cap sits on the cable side). */
	static final long UNLIMITED = Long.MAX_VALUE / 4;

	private int nodes;
	private int edgeCount;
	private int[] head = new int[64];
	private int[] next = new int[128];
	private int[] to = new int[128];
	private long[] cap = new long[128];
	private long[] orig = new long[128];
	/** Per edge, the residual {@link #lock} and a lowered {@link #capacity} took away: {@link #reachesUnlocked}. */
	private long[] relax = new long[128];
	/** Every push since {@link #reset}, edge and amount: {@link #restore} and {@link #rollback} undo them. */
	private int[] journalEdge = new int[256];
	private long[] journalPush = new long[256];
	private int journal;
	private int savedAt;
	private int markedAt;
	private int[] parentEdge = new int[64];
	private int[] queue = new int[64];
	private int[] seen = new int[64];
	private int stamp;
	/** The stamp of the latest {@link #tree}; any later search moves {@link #stamp} on and so retires the tree. */
	private int treeStamp = -1;
	/** Path searches run so far — what a tick costs, counted (MOD-789 perf gate). */
	private long searches;

	/** Start an empty graph of {@code nodeCount} nodes. */
	void reset(int nodeCount) {
		nodes = nodeCount;
		edgeCount = 0;
		journal = 0;
		savedAt = 0;
		markedAt = 0;
		if (head.length < nodeCount) {
			int n = Math.max(nodeCount, head.length * 2);
			head = new int[n];
			parentEdge = new int[n];
			queue = new int[n];
			seen = new int[n];
			stamp = 0;
		}
		Arrays.fill(head, 0, nodeCount, -1);
	}

	long searches() {
		return searches;
	}

	int nodes() {
		return nodes;
	}

	/** Add {@code u -> v} with capacity {@code c} (and its reverse, capacity 0); returns the forward edge. */
	int edge(int u, int v, long c) {
		if (edgeCount + 2 > to.length) {
			int n = to.length * 2;
			next = Arrays.copyOf(next, n);
			to = Arrays.copyOf(to, n);
			cap = Arrays.copyOf(cap, n);
			orig = Arrays.copyOf(orig, n);
			relax = Arrays.copyOf(relax, n);
		}
		int e = edgeCount;
		link(e, u, v, c);
		link(e + 1, v, u, 0L);
		edgeCount += 2;
		return e;
	}

	private void link(int e, int u, int v, long c) {
		to[e] = v;
		cap[e] = c;
		orig[e] = c;
		relax[e] = 0L;
		next[e] = head[u];
		head[u] = e;
	}

	/** Set the capacity of a forward edge that carries no flow yet (a source's edge before its stage). */
	void capacity(int e, long c) {
		long flow = orig[e] - cap[e];
		relax[e] += Math.max(0L, orig[e] - c);
		orig[e] = c;
		cap[e] = c - flow;
	}

	/** The flow planned on forward edge {@code e}. */
	long flow(int e) {
		return orig[e] - cap[e];
	}

	/**
	 * Fix the flow planned so far: no later path may reroute it by running back along an edge that carries it.
	 * A path may still add to that edge. Called between the stages of a plan, outside a checkpoint.
	 */
	void lock() {
		for (int e = 0; e < edgeCount; e += 2) {
			long f = orig[e] - cap[e];
			if (f > 0) {
				cap[e ^ 1] -= f;
				relax[e ^ 1] += f;
			}
		}
	}

	/** The capacity forward edge {@code e} was given. */
	long capacity(int e) {
		return orig[e];
	}

	/** The capacity left on edge {@code e} (forward or reverse). */
	long residual(int e) {
		return cap[e];
	}

	/**
	 * Remember the residual graph, so a greedy attempt can be taken back ({@link #restore}, as often as needed). A
	 * checkpoint is a place in the journal of pushes, so it costs the pushes made since, not the whole graph.
	 */
	void save() {
		savedAt = journal;
	}

	void restore() {
		undoTo(savedAt);
	}

	/** A second, inner checkpoint ({@link #rollback}), after {@link #save}: one round of a sharing. */
	void mark() {
		markedAt = journal;
	}

	void rollback() {
		undoTo(markedAt);
	}

	private void undoTo(int at) {
		while (journal > at) {
			journal--;
			int e = journalEdge[journal];
			long push = journalPush[journal];
			cap[e] += push;
			cap[e ^ 1] -= push;
		}
	}

	/**
	 * Push up to {@code limit} along one shortest residual path from {@code source} to {@code target}; returns what
	 * was pushed, 0 when no path is left. Breadth-first in the order the edges were added, so the same graph always
	 * gives the same path.
	 */
	long augment(int source, int target, long limit) {
		if (limit <= 0 || source == target || dry(source)) {
			return 0L;
		}
		searches++;
		stamp++;
		if (stamp == Integer.MAX_VALUE) {
			Arrays.fill(seen, 0);
			stamp = 1;
		}
		int qh = 0;
		int qt = 0;
		queue[qt++] = source;
		seen[source] = stamp;
		boolean found = false;
		while (qh < qt && !found) {
			int u = queue[qh++];
			for (int e = head[u]; e >= 0; e = next[e]) {
				int v = to[e];
				if (cap[e] > 0 && seen[v] != stamp) {
					seen[v] = stamp;
					parentEdge[v] = e;
					if (v == target) {
						found = true;
						break;
					}
					queue[qt++] = v;
				}
			}
		}
		if (!found) {
			return 0L;
		}
		long push = limit;
		for (int v = target; v != source; v = to[parentEdge[v] ^ 1]) {
			push = Math.min(push, cap[parentEdge[v]]);
		}
		for (int v = target; v != source; v = to[parentEdge[v] ^ 1]) {
			int e = parentEdge[v];
			cap[e] -= push;
			cap[e ^ 1] += push;
			if (journal == journalEdge.length) {
				journalEdge = Arrays.copyOf(journalEdge, journal * 2);
				journalPush = Arrays.copyOf(journalPush, journal * 2);
			}
			journalEdge[journal] = e;
			journalPush[journal++] = push;
		}
		return push;
	}

	/**
	 * One breadth-first search from {@code source} as if nothing were locked ({@link #lock}, a lowered
	 * {@link #capacity}): does it reach a node {@code wanted} marks? A plan with fixed paths asks it before
	 * planning the same tick again free: no such path, and the free plan could not give those nodes more.
	 */
	boolean reachesUnlocked(int source, boolean[] wanted) {
		searches++;
		stamp++;
		if (stamp == Integer.MAX_VALUE) {
			Arrays.fill(seen, 0);
			stamp = 1;
		}
		int qh = 0;
		int qt = 0;
		queue[qt++] = source;
		seen[source] = stamp;
		while (qh < qt) {
			int u = queue[qh++];
			for (int e = head[u]; e >= 0; e = next[e]) {
				int v = to[e];
				if (cap[e] + relax[e] > 0 && seen[v] != stamp) {
					if (wanted[v]) {
						return true;
					}
					seen[v] = stamp;
					queue[qt++] = v;
				}
			}
		}
		return false;
	}

	/** Has {@code source} nothing left to give — no edge out of it with capacity? Then no search can find a path. */
	private boolean dry(int source) {
		for (int e = head[source]; e >= 0; e = next[e]) {
			if (cap[e] > 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * One breadth-first search from {@code source} over the whole residual graph: the tree {@link #pushTree} sends
	 * along — a group of sinks served by one search instead of one each. The paths are those {@link #augment}
	 * finds, as long as nothing saturates in between.
	 */
	void tree(int source) {
		searches++;
		stamp++;
		if (stamp == Integer.MAX_VALUE) {
			Arrays.fill(seen, 0);
			stamp = 1;
		}
		treeStamp = stamp;
		int qh = 0;
		int qt = 0;
		queue[qt++] = source;
		seen[source] = stamp;
		while (qh < qt) {
			int u = queue[qh++];
			for (int e = head[u]; e >= 0; e = next[e]) {
				int v = to[e];
				if (cap[e] > 0 && seen[v] != stamp) {
					seen[v] = stamp;
					parentEdge[v] = e;
					queue[qt++] = v;
				}
			}
		}
	}

	/**
	 * Does the latest {@link #tree}, still current, reach {@code target}? A node it did not reach stays out of reach
	 * whatever is pushed after it: a push opens residual edges only between nodes the tree reached.
	 */
	boolean treeReaches(int target) {
		return treeStamp == stamp && seen[target] == stamp;
	}

	/**
	 * Push up to {@code limit} to {@code target} along the latest {@link #tree}; returns what was pushed — 0 when
	 * the tree does not reach it, or a later search has replaced the tree.
	 */
	long pushTree(int source, int target, long limit) {
		if (limit <= 0 || treeStamp != stamp || seen[target] != stamp) {
			return 0L;
		}
		long push = limit;
		for (int v = target; v != source; v = to[parentEdge[v] ^ 1]) {
			push = Math.min(push, cap[parentEdge[v]]);
		}
		if (push <= 0) {
			return 0L;
		}
		for (int v = target; v != source; v = to[parentEdge[v] ^ 1]) {
			int e = parentEdge[v];
			cap[e] -= push;
			cap[e ^ 1] += push;
			if (journal == journalEdge.length) {
				journalEdge = Arrays.copyOf(journalEdge, journal * 2);
				journalPush = Arrays.copyOf(journalPush, journal * 2);
			}
			journalEdge[journal] = e;
			journalPush[journal++] = push;
		}
		return push;
	}

	/** Push as much as the residual graph lets through, up to {@code limit}, path after path. */
	long augmentAll(int source, int target, long limit) {
		long pushed = 0L;
		while (pushed < limit) {
			long p = augment(source, target, limit - pushed);
			if (p <= 0) {
				break;
			}
			pushed += p;
		}
		return pushed;
	}
}
