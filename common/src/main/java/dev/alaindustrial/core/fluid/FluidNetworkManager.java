package dev.alaindustrial.core.fluid;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.core.energy.GraphComponents;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-level incremental graph manager for fluid pipes (MOD-151). Mirrors the energy
 * {@code NetworkManager} and the item {@code ItemNetworkManager}, with two deliberate differences.
 *
 * <p><b>It merges as well as splits.</b> Like the item pipe, connectivity here is a PREDICATE (a face
 * can be switched off), not raw adjacency — and {@link #rebuild} can only ever split, because its
 * traversal is bounded by the network's own node set. Every entry point that may have CREATED a link
 * therefore runs {@link #unionWithNeighbours} afterwards. Skipping that is exactly MOD-282: a
 * re-enabled joint left the two halves as separate networks forever, on a line that still looked whole.
 *
 * <p><b>It has a tick budget from day one</b> ({@link Config#fluidNetworksPerTick}), copied from the
 * energy manager rather than the item one, which has none.
 */
public final class FluidNetworkManager {
	private FluidNetworkManager() {
	}

	private static final class LevelState {
		final Map<BlockPos, FluidNetwork> byPos = new LinkedHashMap<>();
		final Set<FluidNetwork> networks = new LinkedHashSet<>();
		int tickCursor;
	}

	private static final Map<ServerLevel, LevelState> LEVELS = new IdentityHashMap<>();

	private static LevelState state(ServerLevel level) {
		return LEVELS.computeIfAbsent(level, l -> new LevelState());
	}

	public static void register(FluidPipeBlockEntity pipe) {
		if (!(pipe.getLevel() instanceof ServerLevel level)) {
			return;
		}
		LevelState st = state(level);
		BlockPos pos = pipe.getBlockPos().immutable();
		if (st.byPos.containsKey(pos)) {
			st.byPos.get(pos).markDirty();
			return;
		}
		FluidNetwork net = new FluidNetwork(level);
		net.addPipe(pos);
		st.byPos.put(pos, net);
		st.networks.add(net);
		unionWithNeighbours(st, level, pos);
	}

	/**
	 * Merge the network owning {@code pos} with the network of every pipe it currently connects to.
	 * The ONLY place networks are joined — see the class doc for why every connectivity-creating call
	 * site must run it.
	 */
	private static void unionWithNeighbours(LevelState st, ServerLevel level, BlockPos pos) {
		FluidNetwork net = st.byPos.get(pos);
		if (net == null) {
			return;
		}
		for (Direction dir : Direction.values()) {
			FluidNetwork adjacent = st.byPos.get(pos.relative(dir));
			if (adjacent != null && adjacent != net && FluidPipeBlock.shouldConnectTo(level, pos, dir)) {
				net = merge(st, net, adjacent);
			}
		}
		net.markDirty();
	}

	private static FluidNetwork merge(LevelState st, FluidNetwork a, FluidNetwork b) {
		FluidNetwork keep = a.size() >= b.size() ? a : b;
		FluidNetwork drop = keep == a ? b : a;
		keep.absorb(drop);
		for (BlockPos p : drop.pipes()) {
			st.byPos.put(p, keep);
		}
		st.networks.remove(drop);
		keep.markDirty();
		return keep;
	}

	public static void unregister(FluidPipeBlockEntity pipe) {
		if (!(pipe.getLevel() instanceof ServerLevel level)) {
			return;
		}
		LevelState st = LEVELS.get(level);
		if (st == null) {
			return;
		}
		BlockPos pos = pipe.getBlockPos();
		FluidNetwork net = st.byPos.remove(pos);
		if (net == null) {
			return;
		}
		net.removePipe(pos);
		if (net.isEmpty()) {
			st.networks.remove(net);
			return;
		}
		int survivingNeighbours = 0;
		for (Direction dir : Direction.values()) {
			if (net.contains(pos.relative(dir))) {
				survivingNeighbours++;
			}
		}
		if (survivingNeighbours < 2) {
			// At most one branch survives — removal cannot have split anything.
			net.markDirty();
			return;
		}
		rebuild(st, net);
	}

	/** Re-partition after a face-mode change: it can break a link, and it can restore one. */
	public static void topologyChanged(ServerLevel level, BlockPos pos) {
		LevelState st = LEVELS.get(level);
		if (st == null) {
			return;
		}
		FluidNetwork net = st.byPos.get(pos);
		if (net == null) {
			return;
		}
		rebuild(st, net);
		unionWithNeighbours(st, level, pos);
	}

	/** A neighbouring block changed: endpoints may differ, but pipe-to-pipe connectivity cannot. */
	public static void onNeighbourChanged(ServerLevel level, BlockPos pos) {
		LevelState st = LEVELS.get(level);
		if (st == null) {
			return;
		}
		FluidNetwork net = st.byPos.get(pos);
		if (net != null) {
			net.markDirty();
		}
	}

	private static void rebuild(LevelState st, FluidNetwork old) {
		ServerLevel level = old.level();
		Set<BlockPos> remaining = new LinkedHashSet<>(old.pipes());
		st.networks.remove(old);
		List<Set<BlockPos>> components =
				GraphComponents.components(remaining, p -> pipeNeighbours(level, p));
		boolean first = true;
		for (Set<BlockPos> component : components) {
			FluidNetwork net;
			if (first) {
				net = old;
				net.pipes().clear();
				first = false;
			} else {
				net = new FluidNetwork(level);
			}
			for (BlockPos p : component) {
				net.addPipe(p);
				st.byPos.put(p, net);
			}
			st.networks.add(net);
			net.markDirty();
		}
	}

	private static List<BlockPos> pipeNeighbours(ServerLevel level, BlockPos pos) {
		List<BlockPos> result = new ArrayList<>(6);
		for (Direction dir : Direction.values()) {
			if (FluidPipeBlock.shouldConnectTo(level, pos, dir)) {
				result.add(pos.relative(dir).immutable());
			}
		}
		return result;
	}

	/** Tick up to {@link Config#fluidNetworksPerTick} awake networks; round-robin the remainder. */
	public static void tickAll(ServerLevel level) {
		LevelState st = LEVELS.get(level);
		if (st == null || st.networks.isEmpty()) {
			return;
		}
		List<FluidNetwork> all = new ArrayList<>(st.networks);
		int n = all.size();
		int budget = Math.min(Config.fluidNetworksPerTick, n);
		if (st.tickCursor >= n) {
			st.tickCursor = 0;
		}
		int processed = 0;
		int i = 0;
		while (processed < n && budget > 0) {
			FluidNetwork net = all.get((st.tickCursor + i) % n);
			i++;
			processed++;
			if (net.isAwake()) {
				// A neighbouring mod's fluid capability throwing must not crash the server tick (MOD-186).
				dev.alaindustrial.core.NetworkTickGuard.runIsolated("fluid", net::tick);
				budget--;
			}
		}
		st.tickCursor = n == 0 ? 0 : (st.tickCursor + i) % n;
	}

	/** Network owning {@code pos}, or null. Exposed for tests; mirrors NetworkManager#networkAt. */
	public static FluidNetwork networkAt(ServerLevel level, BlockPos pos) {
		LevelState st = LEVELS.get(level);
		return st == null ? null : st.byPos.get(pos);
	}

	public static void clear(ServerLevel level) {
		LEVELS.remove(level);
	}

	public static void clearAll() {
		LEVELS.clear();
	}
}
