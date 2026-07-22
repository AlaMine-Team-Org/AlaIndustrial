package dev.alaindustrial.core.energy;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.CableBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-{@link ServerLevel} registry of transient {@link EnergyNetwork}s. Networks are never
 * persisted: they are rebuilt from cable block entities as chunks load
 * ({@link #register(CableBlockEntity)}) and pruned as cables are removed/unloaded
 * ({@link #unregister(CableBlockEntity)}).
 *
 * <p>Topology is maintained incrementally:
 * <ul>
 *   <li><b>register</b> — add the cable to a new singleton network, then union (merge) it with the
 *       networks of any already-registered adjacent cables.</li>
 *   <li><b>unregister</b> — remove the cable; if it had ≥2 cable neighbours, BFS from each surviving
 *       neighbour to detect a split and rebuild the affected network(s). Empty networks are dropped,
 *       which prevents the chunk-unload leak.</li>
 * </ul>
 *
 * <p>{@link #tickAll(ServerLevel)} processes up to {@link Config#networksPerTick} awake networks per
 * tick, round-robining the remainder across subsequent ticks.
 */
public final class NetworkManager {
	private NetworkManager() {
	}

	/** Per-level state: pos→network index, the live networks, a round-robin cursor, and telemetry. */
	private static final class LevelState {
		final Map<BlockPos, EnergyNetwork> byPos = new HashMap<>();
		final Set<EnergyNetwork> networks = new HashSet<>();
		int tickCursor;
		// --- telemetry (R-13): last server tick + cumulative since load ---
		int lastTicked;
		long lastEuMoved;
		long totalEuMoved;
	}

	/**
	 * A point-in-time telemetry snapshot for one level (see {@code docs/research/metrics-telemetry.md}).
	 * {@code networks}/{@code awake}/{@code cables} are computed on demand; {@code tickedLastTick} and
	 * {@code euMovedLastTick} reflect the most recent {@link #tickAll}; {@code euMovedTotal} is
	 * cumulative since the level loaded.
	 */
	public record Stats(int networks, int awake, int asleep, int cables,
			int tickedLastTick, long euMovedLastTick, long euMovedTotal) {
	}

	private static final Map<ServerLevel, LevelState> LEVELS = new IdentityHashMap<>();

	private static LevelState state(ServerLevel level) {
		return LEVELS.computeIfAbsent(level, l -> new LevelState());
	}

	/** Register a cable block entity on load/place. Idempotent. */
	public static void register(CableBlockEntity cable) {
		ServerLevel level = (ServerLevel) cable.getLevel();
		if (level == null) {
			return;
		}
		LevelState st = state(level);
		BlockPos pos = cable.getBlockPos().immutable();
		if (st.byPos.containsKey(pos)) {
			st.byPos.get(pos).markDirty();
			return;
		}

		EnergyNetwork net = new EnergyNetwork(level);
		net.addCable(pos);
		st.networks.add(net);
		st.byPos.put(pos, net);

		// Union with any already-registered adjacent cable networks (merge into the largest).
		for (Direction dir : Direction.values()) {
			BlockPos np = pos.relative(dir);
			EnergyNetwork adj = st.byPos.get(np);
			if (adj != null && adj != net) {
				net = merge(st, net, adj);
			}
		}
		net.markDirty();
	}

	/** Merge {@code b} into {@code a} (keeping the larger as the survivor), updating the index. */
	private static EnergyNetwork merge(LevelState st, EnergyNetwork a, EnergyNetwork b) {
		EnergyNetwork keep = a.size() >= b.size() ? a : b;
		EnergyNetwork drop = keep == a ? b : a;
		keep.absorb(drop);
		for (BlockPos p : drop.cables()) {
			st.byPos.put(p, keep);
		}
		st.networks.remove(drop);
		keep.markDirty();
		return keep;
	}

	/** Unregister a cable on removal/unload. Splits the network if removal disconnects it. */
	public static void unregister(CableBlockEntity cable) {
		ServerLevel level = (ServerLevel) cable.getLevel();
		if (level == null) {
			return;
		}
		LevelState st = LEVELS.get(level);
		if (st == null) {
			return;
		}
		BlockPos pos = cable.getBlockPos();
		EnergyNetwork net = st.byPos.remove(pos);
		if (net == null) {
			return;
		}
		net.removeCable(pos);

		// Surviving cable neighbours that were part of this network.
		List<BlockPos> neighbours = new ArrayList<>(6);
		for (Direction dir : Direction.values()) {
			BlockPos np = pos.relative(dir);
			if (net.contains(np)) {
				neighbours.add(np.immutable());
			}
		}

		if (net.isEmpty()) {
			st.networks.remove(net); // drop empty network (prevents chunk-unload leak)
			return;
		}

		if (neighbours.size() < 2) {
			// At most one branch survives — no possible split, just keep the (smaller) network.
			net.markDirty();
			return;
		}

		// ≥2 neighbours: removal may have split the network. Rebuild connected components by BFS.
		rebuildComponents(st, net);
	}

	/**
	 * Re-partition {@code old}'s remaining cables into connected components and re-index them.
	 * Connectivity is computed by {@link GraphComponents} (unit-tested, MC-free); the first component
	 * reuses the {@code old} network object, the rest become fresh networks.
	 */
	private static void rebuildComponents(LevelState st, EnergyNetwork old) {
		ServerLevel level = old.level();
		Set<BlockPos> remaining = new HashSet<>(old.cables());
		st.networks.remove(old);

		List<Set<BlockPos>> comps = GraphComponents.components(remaining, NetworkManager::cableNeighbours);
		boolean first = true;
		for (Set<BlockPos> nodes : comps) {
			EnergyNetwork comp;
			if (first) {
				comp = old;
				comp.cables().clear();
				first = false;
			} else {
				comp = new EnergyNetwork(level);
			}
			for (BlockPos p : nodes) {
				comp.addCable(p);
				st.byPos.put(p, comp);
			}
			st.networks.add(comp);
			comp.markDirty();
		}
	}

	/** The six axis-neighbours of a cable position (the caller filters to the live cable set). */
	private static List<BlockPos> cableNeighbours(BlockPos pos) {
		List<BlockPos> ns = new ArrayList<>(6);
		for (Direction dir : Direction.values()) {
			ns.add(pos.relative(dir).immutable());
		}
		return ns;
	}

	/** Wake the network owning {@code pos} (a neighbour changed). Called on neighbour updates. */
	public static void onNeighbourChanged(ServerLevel level, BlockPos pos) {
		LevelState st = LEVELS.get(level);
		if (st == null) {
			return;
		}
		EnergyNetwork net = st.byPos.get(pos);
		if (net != null) {
			net.markDirty();
		}
	}

	/** Tick up to {@link Config#networksPerTick} awake networks; round-robin the remainder. */
	public static void tickAll(ServerLevel level) {
		LevelState st = LEVELS.get(level);
		if (st == null || st.networks.isEmpty()) {
			return;
		}
		List<EnergyNetwork> all = new ArrayList<>(st.networks);
		int n = all.size();
		int budget = Math.min(Config.networksPerTick, n);
		if (st.tickCursor >= n) {
			st.tickCursor = 0;
		}
		int processed = 0;
		int i = 0;
		int ticked = 0;
		long movedEu = 0;
		while (processed < n && budget > 0) {
			EnergyNetwork net = all.get((st.tickCursor + i) % n);
			i++;
			processed++;
			if (net.isAwake()) {
				// Isolate the tick: a neighbouring mod's capability throwing must not crash the server tick
				// (MOD-186). On a throw the network is skipped this tick (0 EU) and retried next tick.
				movedEu += dev.alaindustrial.core.NetworkTickGuard.tickIsolated("energy", net::tick);
				ticked++;
				budget--;
			}
		}
		st.tickCursor = (st.tickCursor + i) % n;
		st.lastTicked = ticked;
		st.lastEuMoved = movedEu;
		st.totalEuMoved += movedEu;
	}

	/** Drop all per-level state (call on server stop to avoid leaks across world reloads). */
	public static void clearAll() {
		LEVELS.clear();
	}

	/** Drop one level's state (e.g. on level unload). */
	public static void clear(ServerLevel level) {
		LEVELS.remove(level);
	}

	// --- test / introspection helpers ---

	/** The network owning {@code pos}, or null. Visible for the self-test. */
	public static EnergyNetwork networkAt(ServerLevel level, BlockPos pos) {
		LevelState st = LEVELS.get(level);
		return st == null ? null : st.byPos.get(pos);
	}

	/** Total live network count in a level. Visible for the self-test. */
	public static int networkCount(ServerLevel level) {
		LevelState st = LEVELS.get(level);
		return st == null ? 0 : st.networks.size();
	}

	/**
	 * Telemetry snapshot for one level (R-13): network/awake/cable counts plus last-tick and
	 * cumulative EU throughput. Scans the level's networks once, so it is meant for the {@code /ala
	 * net} command, not the hot tick path.
	 */
	public static Stats stats(ServerLevel level) {
		LevelState st = LEVELS.get(level);
		if (st == null) {
			return new Stats(0, 0, 0, 0, 0, 0L, 0L);
		}
		int awake = 0;
		int cables = 0;
		for (EnergyNetwork net : st.networks) {
			cables += net.size();
			if (net.isAwake()) {
				awake++;
			}
		}
		int total = st.networks.size();
		return new Stats(total, awake, total - awake, cables,
				st.lastTicked, st.lastEuMoved, st.totalEuMoved);
	}
}
