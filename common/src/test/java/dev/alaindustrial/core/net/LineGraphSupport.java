package dev.alaindustrial.core.net;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minecraft-free stand-ins for a level, a network and the {@link NetworkOps} of one network kind, so
 * {@link GraphNetworkManager} can be exercised in the L1 lane ({@code common} has no Minecraft jar on
 * its test classpath by design).
 *
 * <p>The graph is a line: node {@code n} touches {@code n-1} and {@code n+1}. That is enough to model
 * both connectivity flavours the production managers use — raw adjacency (cables) is the line with no
 * cuts, and a face predicate (fluid/item pipes) is the line with {@link LineOps#cut} applied. Cuts are
 * symmetric, like a pipe joint: both ends stop connecting.
 */
final class LineGraphSupport {
	private LineGraphSupport() {
	}

	/** Stand-in for a {@code ServerLevel}: the framework only ever compares it by identity. */
	static final class Level {
		private final String name;

		Level(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/** Stand-in for an {@code EnergyNetwork} / {@code FluidNetwork} / {@code ItemNetwork}. */
	static final class Net {
		final Level level;
		final Set<Integer> nodes = new LinkedHashSet<>();
		/** Whether {@code tickAll} should tick it — the stand-in for "has a source and a sink". */
		boolean awake = true;
		/** What one tick reports as moved, i.e. what the telemetry slot accumulates. */
		long movesPerTick;
		/** If set, one tick throws it — the stand-in for a foreign mod's capability blowing up. */
		RuntimeException failure;
		int ticks;
		int dirtied;
		/** How many times a node was added. Non-zero growth after a removal means a BFS re-partition ran. */
		int added;

		Net(Level level) {
			this.level = level;
		}

		@Override
		public String toString() {
			return "Net" + nodes;
		}
	}

	/** {@link NetworkOps} over the line graph, with switchable links and a few call counters. */
	static final class LineOps implements NetworkOps<Level, Net, Integer> {
		/** Links switched off, stored as both ordered pairs so connectivity stays symmetric. */
		private final Set<String> cuts = new LinkedHashSet<>();
		/** Networks created — a rebuild that splits shows up here, a shortcut does not. */
		int created;
		/** The networks ticked, in order, across all calls: the round-robin cursor is observable here. */
		final List<Net> tickOrder = new ArrayList<>();

		/** Switch the link between two adjacent nodes off (a pipe face closed). */
		void cut(int a, int b) {
			cuts.add(key(a, b));
			cuts.add(key(b, a));
		}

		/** Switch it back on (the face re-opened) — the move that only a union can act on. */
		void restore(int a, int b) {
			cuts.remove(key(a, b));
			cuts.remove(key(b, a));
		}

		private static String key(int a, int b) {
			return a + "|" + b;
		}

		@Override
		public Net create(Level level) {
			created++;
			return new Net(level);
		}

		@Override
		public Set<Integer> nodes(Net network) {
			return network.nodes;
		}

		@Override
		public void addNode(Net network, Integer pos) {
			network.nodes.add(pos);
			network.added++;
		}

		@Override
		public void removeNode(Net network, Integer pos) {
			network.nodes.remove(pos);
		}

		@Override
		public void absorb(Net keep, Net drop) {
			keep.nodes.addAll(drop.nodes);
		}

		@Override
		public void markDirty(Net network) {
			network.dirtied++;
		}

		@Override
		public boolean isAwake(Net network) {
			return network.awake;
		}

		@Override
		public long tick(Net network) {
			tickOrder.add(network);
			network.ticks++;
			if (network.failure != null) {
				throw network.failure;
			}
			return network.movesPerTick;
		}

		@Override
		public Iterable<Integer> candidates(Integer pos) {
			return List.of(pos - 1, pos + 1);
		}

		@Override
		public Iterable<Integer> connected(Level level, Integer pos) {
			List<Integer> result = new ArrayList<>(2);
			for (int neighbour : candidates(pos)) {
				if (!cuts.contains(key(pos, neighbour))) {
					result.add(neighbour);
				}
			}
			return result;
		}
	}
}
