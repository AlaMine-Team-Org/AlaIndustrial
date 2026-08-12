package dev.alaindustrial.core.net;

import dev.alaindustrial.core.NetworkTickGuard;
import dev.alaindustrial.core.energy.GraphComponents;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * The per-level graph bookkeeping shared by every network kind (MOD-401): the position index, the
 * incremental union on register, the BFS re-partition on removal, the budgeted round-robin tick, and
 * the level sweep.
 *
 * <p><b>Why it exists.</b> Energy, fluid and item pipes ran three copies of this algorithm — the
 * item one still says "Mirrors the proven energy NetworkManager" in its javadoc. Copies drift: the
 * fluid copy was taken before the energy original grew its level cleanup, so its {@code clear} /
 * {@code clearAll} were never wired into either loader and every unloaded world leaked. One
 * implementation removes the drift and, because construction enrols the manager in
 * {@link LevelStateRegistry}, removes the possibility of the next copy leaking the same way.
 *
 * <p><b>What the three kinds still own</b> — all of it through {@link NetworkOps} or a constructor
 * argument, none of it duplicated here: what a node is and what counts as connected (raw adjacency
 * for cables, a face predicate for pipes), the per-tick budget, the cursor policy, and — for energy
 * alone — the meaning of the number a tick returns.
 *
 * <p><b>Three type parameters, and the level key is one of them on purpose.</b> {@code common}'s L1
 * suite runs without the Minecraft jar. Parameterising the level key is what lets the whole
 * lifecycle — enrolment, unload, server stop — be exercised against a stand-in level in
 * {@code GraphNetworkManagerTest}, instead of only inside the L2 gametests where the leak this class
 * fixes went unnoticed for months.
 *
 * @param <L> the level key (identity-compared, exactly as the hand-written managers compared it)
 * @param <N> the network object
 * @param <P> the node position
 */
public final class GraphNetworkManager<L, N, P> implements LevelStateHolder {

	/**
	 * How far the round-robin cursor moves after a tick. A real difference between the kinds, kept
	 * explicit here rather than hidden in three copies of the loop.
	 */
	public enum TickCursor {
		/**
		 * Advance past the networks this tick visited. The budgeted managers (energy, fluid) use it:
		 * the next tick resumes where the budget ran out, so a base with more networks than budget
		 * still serves all of them over consecutive ticks.
		 */
		BY_WINDOW,
		/**
		 * Advance by one. Item pipes have no budget, so every tick visits every network and
		 * {@link #BY_WINDOW} would leave the cursor exactly where it was — freezing the order in
		 * which networks are served. Two item networks can feed the same chest, so that order is
		 * observable, and rotating it is what keeps them fair.
		 */
		BY_ONE;

		/** The cursor for the next tick. {@code size} is the live network count, never negative. */
		int next(int cursor, int visited, int size) {
			if (size <= 0) {
				return 0;
			}
			return (cursor + (this == BY_ONE ? 1 : visited)) % size;
		}
	}

	/**
	 * What the last {@link #tickAll} did in one level: how many networks it ticked and what their
	 * tick bodies reported, plus the running total since the level loaded.
	 *
	 * <p>Only the energy manager reads this (it becomes the EU figures behind {@code /ala net}), and
	 * the wording here is deliberately neutral — the framework never interprets the number. It is
	 * kept in the framework's own per-level state rather than in a second map on the energy side
	 * <b>because a second map keyed by level is exactly the defect this class was written to close</b>.
	 */
	public record Telemetry(int ticked, long movedLastTick, long movedTotal) {
		static final Telemetry EMPTY = new Telemetry(0, 0L, 0L);
	}

	/** Per-level state: pos→network index, the live networks, the round-robin cursor, telemetry. */
	private static final class LevelState<N, P> {
		final Map<P, N> byPos = new LinkedHashMap<>();
		final Set<N> networks = new LinkedHashSet<>();
		int tickCursor;
		int lastTicked;
		long lastMoved;
		long totalMoved;
	}

	private final String kind;
	private final NetworkOps<L, N, P> ops;
	private final IntSupplier tickBudget;
	private final TickCursor cursorPolicy;
	/** Identity-keyed, matching the {@code IdentityHashMap} each hand-written manager used. */
	private final Map<L, LevelState<N, P>> levels = new IdentityHashMap<>();

	/**
	 * @param kind        short label — the {@link NetworkTickGuard} log tag and the {@link #stateId()}
	 * @param ops         what a node is, what connects to what, how a network ticks
	 * @param tickBudget  how many awake networks may tick per level tick; read on every tick because
	 *                    the balance config is reloadable. {@link Integer#MAX_VALUE} means "all".
	 * @param cursorPolicy how the round-robin cursor advances
	 */
	public GraphNetworkManager(String kind, NetworkOps<L, N, P> ops, IntSupplier tickBudget,
			TickCursor cursorPolicy) {
		this.kind = kind;
		this.ops = ops;
		this.tickBudget = tickBudget;
		this.cursorPolicy = cursorPolicy;
		// Enrolment lives here so that "a network manager exists" and "its state gets cleared" are the
		// same fact. Moving this into the loaders (where it used to live, by name) is what leaked.
		LevelStateRegistry.register(this);
	}

	private LevelState<N, P> state(L level) {
		return levels.computeIfAbsent(level, l -> new LevelState<>());
	}

	/** Register a node on load/place. Idempotent: a known position is only woken. */
	public void register(L level, P pos) {
		LevelState<N, P> st = state(level);
		N known = st.byPos.get(pos);
		if (known != null) {
			ops.markDirty(known);
			return;
		}
		N net = ops.create(level);
		ops.addNode(net, pos);
		st.byPos.put(pos, net);
		st.networks.add(net);
		unionWithNeighbours(st, level, pos);
	}

	/**
	 * Merge the network owning {@code pos} with the network of everything it currently connects to.
	 * The ONLY place networks are joined: {@link #rebuild} can only split, because its traversal is
	 * bounded by the network's own node set. So every entry point that may have CREATED a link runs
	 * this afterwards — skipping it is MOD-282, where a re-enabled joint left the two halves as
	 * separate networks forever on a line that still looked whole.
	 */
	private void unionWithNeighbours(LevelState<N, P> st, L level, P pos) {
		N net = st.byPos.get(pos);
		if (net == null) {
			return;
		}
		for (P neighbour : ops.connected(level, pos)) {
			N adjacent = st.byPos.get(neighbour);
			if (adjacent != null && adjacent != net) {
				net = merge(st, net, adjacent);
			}
		}
		ops.markDirty(net);
	}

	/** Merge {@code b} into {@code a}, keeping the larger as the survivor, and re-index the loser. */
	private N merge(LevelState<N, P> st, N a, N b) {
		N keep = ops.nodes(a).size() >= ops.nodes(b).size() ? a : b;
		N drop = keep == a ? b : a;
		ops.absorb(keep, drop);
		for (P pos : ops.nodes(drop)) {
			st.byPos.put(pos, keep);
		}
		st.networks.remove(drop);
		ops.markDirty(keep);
		return keep;
	}

	/** Unregister a node on removal/unload. Splits the network if the removal disconnected it. */
	public void unregister(L level, P pos) {
		LevelState<N, P> st = levels.get(level);
		if (st == null) {
			return;
		}
		N net = st.byPos.remove(pos);
		if (net == null) {
			return;
		}
		ops.removeNode(net, pos);
		if (ops.nodes(net).isEmpty()) {
			// Drop the empty network: this is what stops a chunk unload from leaking one per cable run.
			st.networks.remove(net);
			return;
		}
		// Grid adjacency, NOT connectivity: the block is already gone from the world, so asking it what
		// it connects to would answer "nothing" and every removal would look like a leaf.
		int survivors = 0;
		for (P neighbour : ops.candidates(pos)) {
			if (ops.nodes(net).contains(neighbour)) {
				survivors++;
			}
		}
		if (survivors < 2) {
			// A node with at most one neighbour left in the set cannot have been a cut vertex, so the
			// remainder is still one component and the BFS would only rediscover it.
			ops.markDirty(net);
			return;
		}
		rebuild(st, level, net);
	}

	/**
	 * A face mode changed at {@code pos}: re-partition, then re-union. Both halves are needed because
	 * a face mode can break a link and can restore one, and {@link #rebuild} only ever splits.
	 */
	public void retopologise(L level, P pos) {
		LevelState<N, P> st = levels.get(level);
		if (st == null) {
			return;
		}
		N net = st.byPos.get(pos);
		if (net == null) {
			return;
		}
		rebuild(st, level, net);
		unionWithNeighbours(st, level, pos);
	}

	/** Re-partition the network owning {@code pos} without re-unioning. */
	public void rebuildAt(L level, P pos) {
		LevelState<N, P> st = levels.get(level);
		if (st == null) {
			return;
		}
		N net = st.byPos.get(pos);
		if (net == null) {
			return;
		}
		rebuild(st, level, net);
	}

	/** Wake the network owning {@code pos} — its endpoints may have changed, its topology cannot. */
	public void markDirtyAt(L level, P pos) {
		LevelState<N, P> st = levels.get(level);
		if (st == null) {
			return;
		}
		N net = st.byPos.get(pos);
		if (net != null) {
			ops.markDirty(net);
		}
	}

	/**
	 * Re-partition {@code old}'s nodes into connected components and re-index them. Connectivity comes
	 * from {@link GraphComponents} (unit-tested, Minecraft-free); the first component reuses the
	 * {@code old} object so callers holding a reference keep a live network, the rest become fresh
	 * ones.
	 */
	private void rebuild(LevelState<N, P> st, L level, N old) {
		Set<P> remaining = new LinkedHashSet<>(ops.nodes(old));
		st.networks.remove(old);
		List<Set<P>> components = GraphComponents.components(remaining, p -> ops.connected(level, p));
		boolean first = true;
		for (Set<P> component : components) {
			N net;
			if (first) {
				net = old;
				ops.nodes(net).clear();
				first = false;
			} else {
				net = ops.create(level);
			}
			for (P pos : component) {
				ops.addNode(net, pos);
				st.byPos.put(pos, net);
			}
			st.networks.add(net);
			ops.markDirty(net);
		}
	}

	/**
	 * Tick up to the configured budget of awake networks, round-robining the rest to later ticks.
	 * Asleep networks are visited but cost no budget.
	 *
	 * <p>Every tick body runs inside {@link NetworkTickGuard}: a neighbouring mod's capability
	 * throwing must not unwind into the server tick handler and crash the world with our package at
	 * the top of the stack (MOD-186). Isolating here rather than in each manager means a new network
	 * kind gets it whether or not its author knew about that failure mode.
	 */
	public void tickAll(L level) {
		LevelState<N, P> st = levels.get(level);
		if (st == null || st.networks.isEmpty()) {
			return;
		}
		List<N> all = new ArrayList<>(st.networks);
		int size = all.size();
		int budget = Math.min(tickBudget.getAsInt(), size);
		int visited = 0;
		int ticked = 0;
		long moved = 0L;
		// No cursor clamp before the loop: the index and the next cursor are both taken modulo `size`,
		// so a cursor left over from a larger network count is already harmless.
		while (visited < size && budget > 0) {
			N net = all.get((st.tickCursor + visited) % size);
			visited++;
			if (ops.isAwake(net)) {
				moved += NetworkTickGuard.tickIsolated(kind, () -> ops.tick(net));
				ticked++;
				budget--;
			}
		}
		st.tickCursor = cursorPolicy.next(st.tickCursor, visited, size);
		st.lastTicked = ticked;
		st.lastMoved = moved;
		st.totalMoved += moved;
	}

	/** The network owning {@code pos}, or null. */
	public N networkAt(L level, P pos) {
		LevelState<N, P> st = levels.get(level);
		return st == null ? null : st.byPos.get(pos);
	}

	/** Live network count in one level. */
	public int networkCount(L level) {
		LevelState<N, P> st = levels.get(level);
		return st == null ? 0 : st.networks.size();
	}

	/** Snapshot of one level's networks, for the telemetry command. Not for the tick path. */
	public Collection<N> networks(L level) {
		LevelState<N, P> st = levels.get(level);
		return st == null ? List.of() : List.copyOf(st.networks);
	}

	/**
	 * Last-tick and cumulative tick figures for one level, or zeros for a level with no state. A tick
	 * on an empty level leaves the previous figures alone, exactly as the hand-written energy loop
	 * did — {@code /ala net} keeps showing the last real throughput instead of blanking to 0.
	 */
	public Telemetry telemetry(L level) {
		LevelState<N, P> st = levels.get(level);
		return st == null ? Telemetry.EMPTY : new Telemetry(st.lastTicked, st.lastMoved, st.totalMoved);
	}

	// --- LevelStateHolder: the cleanup the loaders drive through LevelStateRegistry ---

	@Override
	public String stateId() {
		return kind;
	}

	@Override
	public void clearLevel(Object level) {
		levels.remove(level);
	}

	@Override
	public void clearAll() {
		levels.clear();
	}

	@Override
	public int retained() {
		return levels.size();
	}
}
