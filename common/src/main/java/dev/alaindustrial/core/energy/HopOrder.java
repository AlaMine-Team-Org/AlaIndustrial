package dev.alaindustrial.core.energy;

import java.util.Arrays;

/**
 * The planned hops between the cables of a line, as they are carried out (MOD-789, ADR-049): the net flow of every
 * cable-to-cable edge, with two-way pairs and directed cycles cancelled (they move nothing), and an order of the
 * cables in which every cable comes after all the cables it hands on to — the claimant before its donor, so each
 * unit moves one hop a tick (ADR-001). Edges are numbered as the planner's adjacency: the neighbours of cable
 * {@code c} are {@code nbr[start[c] .. start[c+1])}. Scratch arrays are kept between ticks. Minecraft-free.
 */
final class HopOrder {

	private long[] net = new long[0];
	private int[] order = new int[0];
	private int[] stackCable = new int[0];
	private int[] stackPos = new int[0];
	private byte[] color = new byte[0];
	private int[] start = new int[1];
	private int[] nbr = new int[0];
	private int cables;

	/** Adopt the planner's adjacency (a new array set whenever the cable list changes). */
	void adjacency(int[] start, int[] nbr, int cables) {
		this.start = start;
		this.nbr = nbr;
		this.cables = cables;
		if (net.length != nbr.length) {
			net = new long[nbr.length];
		}
		if (order.length < cables) {
			order = new int[cables];
			stackCable = new int[cables];
			stackPos = new int[cables];
			color = new byte[cables];
		}
	}

	/** Set the planned flow of edge {@code e}; then {@link #settle} before reading. */
	void set(int e, long flow) {
		net[e] = flow;
	}

	long net(int e) {
		return net[e];
	}

	/** The cables in the order they hand on: each after every cable it gives to. */
	int[] order() {
		return order;
	}

	/** Cancel two-way pairs and cycles and fix the order; returns whether any hop is left. */
	boolean settle() {
		for (int i = 0; i < cables; i++) {
			for (int e = start[i]; e < start[i + 1]; e++) {
				int j = nbr[e];
				if (j < i || net[e] == 0) {
					continue;
				}
				int back = edgeTo(j, i);
				long m = Math.min(net[e], net[back]);
				net[e] -= m;
				net[back] -= m;
			}
		}
		while (cancelOneCycle()) {
			// each pass removes at least one edge's flow
		}
		boolean any = false;
		for (int e = 0; e < start[cables]; e++) {
			any |= net[e] > 0;
		}
		claimantFirst();
		return any;
	}

	private int edgeTo(int from, int to) {
		for (int e = start[from]; e < start[from + 1]; e++) {
			if (nbr[e] == to) {
				return e;
			}
		}
		throw new IllegalStateException("no edge " + from + " -> " + to);
	}

	/** Find one directed cycle of positive net flow by depth-first search and take its smallest flow off it. */
	private boolean cancelOneCycle() {
		Arrays.fill(color, 0, cables, (byte) 0);
		for (int root = 0; root < cables; root++) {
			if (color[root] != 0) {
				continue;
			}
			int top = push(-1, root);
			while (top >= 0) {
				int u = stackCable[top];
				int e = stackPos[top];
				if (e >= start[u + 1]) {
					color[u] = 2;
					top--;
					continue;
				}
				stackPos[top] = e + 1;
				int v = nbr[e];
				if (net[e] <= 0) {
					continue;
				}
				if (color[v] == 1) {
					stackPos[top] = e; // the cycle closes through e
					cancel(v, top);
					return true;
				}
				if (color[v] == 0) {
					top = push(top, v);
				}
			}
		}
		return false;
	}

	private int push(int top, int cable) {
		int t = top + 1;
		stackCable[t] = cable;
		stackPos[t] = start[cable];
		color[cable] = 1;
		return t;
	}

	/** The cycle: the stack entries from {@code first}'s to the top, each down the edge it last took. */
	private void cancel(int first, int top) {
		int from = 0;
		while (stackCable[from] != first) {
			from++;
		}
		long m = Long.MAX_VALUE;
		for (int k = from; k <= top; k++) {
			m = Math.min(m, net[edgeAt(k, top)]);
		}
		for (int k = from; k <= top; k++) {
			net[edgeAt(k, top)] -= m;
		}
	}

	private int edgeAt(int k, int top) {
		return k == top ? stackPos[k] : stackPos[k] - 1;
	}

	/** Post-order of the hop graph: a cable is listed after every cable it hands on to. */
	private void claimantFirst() {
		Arrays.fill(color, 0, cables, (byte) 0);
		int n = 0;
		for (int root = 0; root < cables; root++) {
			if (color[root] != 0) {
				continue;
			}
			int top = push(-1, root);
			while (top >= 0) {
				int u = stackCable[top];
				int e = stackPos[top];
				if (e >= start[u + 1]) {
					color[u] = 2;
					order[n++] = u;
					top--;
					continue;
				}
				stackPos[top] = e + 1;
				int v = nbr[e];
				if (net[e] > 0 && color[v] == 0) {
					top = push(top, v);
				}
			}
		}
	}
}
