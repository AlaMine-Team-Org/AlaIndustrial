package dev.alaindustrial.core;

import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
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

/** Per-level incremental graph manager for item pipes. Mirrors the proven energy NetworkManager. */
public final class ItemNetworkManager {
	private ItemNetworkManager() { }
	private static final class LevelState {
		final Map<BlockPos, ItemNetwork> byPos = new HashMap<>();
		final Set<ItemNetwork> networks = new HashSet<>();
		int tickCursor;
	}
	private static final Map<ServerLevel, LevelState> LEVELS = new IdentityHashMap<>();
	private static LevelState state(ServerLevel level) { return LEVELS.computeIfAbsent(level, l -> new LevelState()); }

	public static void register(ItemPipeBlockEntity pipe) {
		if (!(pipe.getLevel() instanceof ServerLevel level)) return;
		LevelState state = state(level);
		BlockPos pos = pipe.getBlockPos().immutable();
		if (state.byPos.containsKey(pos)) { state.byPos.get(pos).markDirty(); return; }
		ItemNetwork network = new ItemNetwork(level);
		network.addPipe(pos);
		state.byPos.put(pos, network);
		state.networks.add(network);
		for (Direction dir : Direction.values()) {
			ItemNetwork adjacent = state.byPos.get(pos.relative(dir));
			if (adjacent != null && adjacent != network && ItemPipeBlock.shouldConnectTo(level, pos, dir)) {
				network = merge(state, network, adjacent);
			}
		}
		network.markDirty();
	}

	private static ItemNetwork merge(LevelState state, ItemNetwork a, ItemNetwork b) {
		ItemNetwork keep = a.size() >= b.size() ? a : b;
		ItemNetwork drop = keep == a ? b : a;
		keep.absorb(drop);
		for (BlockPos pos : drop.pipes()) state.byPos.put(pos, keep);
		state.networks.remove(drop);
		return keep;
	}

	public static void unregister(ItemPipeBlockEntity pipe) {
		if (!(pipe.getLevel() instanceof ServerLevel level)) return;
		LevelState state = LEVELS.get(level);
		if (state == null) return;
		ItemNetwork network = state.byPos.remove(pipe.getBlockPos());
		if (network == null) return;
		network.removePipe(pipe.getBlockPos());
		if (network.isEmpty()) { state.networks.remove(network); return; }
		rebuild(state, network);
	}

	/** Rebuild after any removal/config change: face modes can split a component without a block removal. */
	public static void topologyChanged(ServerLevel level, BlockPos pos) {
		LevelState state = LEVELS.get(level);
		if (state == null) return;
		ItemNetwork network = state.byPos.get(pos);
		if (network != null) rebuild(state, network);
	}

	private static void rebuild(LevelState state, ItemNetwork old) {
		Set<BlockPos> remaining = new HashSet<>(old.pipes());
		state.networks.remove(old);
		List<Set<BlockPos>> components = GraphComponents.components(remaining,
				pos -> pipeNeighbours(old.level(), pos));
		boolean first = true;
		for (Set<BlockPos> component : components) {
			ItemNetwork network = first ? old : new ItemNetwork(old.level());
			if (first) { network.pipes().clear(); first = false; }
			for (BlockPos pipe : component) { network.addPipe(pipe); state.byPos.put(pipe, network); }
			network.markDirty();
			state.networks.add(network);
		}
	}

	private static List<BlockPos> pipeNeighbours(ServerLevel level, BlockPos pos) {
		List<BlockPos> result = new ArrayList<>(6);
		for (Direction dir : Direction.values()) {
			if (ItemPipeBlock.shouldConnectTo(level, pos, dir)) result.add(pos.relative(dir).immutable());
		}
		return result;
	}

	public static void onNeighbourChanged(ServerLevel level, BlockPos pos) {
		LevelState state = LEVELS.get(level);
		if (state != null && state.byPos.get(pos) != null) rebuild(state, state.byPos.get(pos));
	}

	public static void tickAll(ServerLevel level) {
		LevelState state = LEVELS.get(level);
		if (state == null || state.networks.isEmpty()) return;
		List<ItemNetwork> all = new ArrayList<>(state.networks);
		if (state.tickCursor >= all.size()) state.tickCursor = 0;
		for (int i = 0; i < all.size(); i++) {
			ItemNetwork network = all.get((state.tickCursor + i) % all.size());
			if (network.isAwake()) network.tick();
		}
		state.tickCursor = (state.tickCursor + 1) % all.size();
	}

	public static void clear(ServerLevel level) { LEVELS.remove(level); }
	public static void clearAll() { LEVELS.clear(); }
}
