package dev.alaindustrial.core;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** One connected component of item-pipe segments, ticked once by {@link ItemNetworkManager}. */
public final class ItemNetwork {
	private record Endpoint(BlockPos pos, Direction side) { }

	private final ServerLevel level;
	private final Set<BlockPos> pipes = new HashSet<>();
	private final List<Endpoint> sources = new ArrayList<>();
	private final List<Endpoint> targets = new ArrayList<>();
	private boolean endpointsDirty = true;
	private int sourceCursor;
	private int targetCursor;
	/**
	 * Ticks left before this network may move items again (MOD-108). Starts at 0 so a freshly built or
	 * newly fed pipe reacts on its first tick instead of idling for a second — the wait is between
	 * transfers, not before the first one.
	 */
	private int transferCooldown;

	ItemNetwork(ServerLevel level) {
		this.level = level;
	}

	ServerLevel level() { return level; }
	Set<BlockPos> pipes() { return pipes; }
	int size() { return pipes.size(); }
	boolean contains(BlockPos pos) { return pipes.contains(pos); }
	boolean isEmpty() { return pipes.isEmpty(); }
	void addPipe(BlockPos pos) { if (pipes.add(pos.immutable())) endpointsDirty = true; }
	void removePipe(BlockPos pos) { if (pipes.remove(pos)) endpointsDirty = true; }
	void absorb(ItemNetwork other) { pipes.addAll(other.pipes); endpointsDirty = true; }
	void markDirty() { endpointsDirty = true; }

	boolean isAwake() {
		if (endpointsDirty) refreshEndpoints();
		return !sources.isEmpty() && !targets.isEmpty();
	}

	/**
	 * Moves at most {@link Config#itemPipeItemsPerTransfer} items, and only once every
	 * {@link Config#itemPipeTransferIntervalTicks} ticks (MOD-108). Before that the network moved a batch
	 * every single tick, which made the passive starter pipe 8× a hopper — see {@code Config} for the
	 * balance rationale.
	 *
	 * <p>The cooldown is only spent when something actually moved: a network whose source is empty or
	 * whose target is full keeps checking every tick, so it resumes the instant the situation changes
	 * rather than sitting out the rest of an interval it never used.
	 */
	int tick() {
		if (endpointsDirty) refreshEndpoints();
		if (sources.isEmpty() || targets.isEmpty()) return 0;
		if (transferCooldown > 0) {
			transferCooldown--;
			return 0;
		}
		int sourceCount = sources.size();
		int targetCount = targets.size();
		for (int s = 0; s < sourceCount; s++) {
			Endpoint source = sources.get((sourceCursor + s) % sourceCount);
			ItemPort from = ItemLookup.get().find(level, source.pos(), source.side());
			if (from == null) continue;
			for (int t = 0; t < targetCount; t++) {
				Endpoint target = targets.get((targetCursor + t) % targetCount);
				if (source.pos().equals(target.pos())) continue; // never churn an endpoint into itself
				ItemPort to = ItemLookup.get().find(level, target.pos(), target.side());
				int moved = ItemMover.move(from, to, Config.itemPipeItemsPerTransfer);
				if (moved > 0) {
					sourceCursor = (sourceCursor + s + 1) % sourceCount;
					targetCursor = (targetCursor + t + 1) % targetCount;
					transferCooldown = Math.max(0, Config.itemPipeTransferIntervalTicks - 1);
					return moved;
				}
			}
		}
		return 0;
	}

	private void refreshEndpoints() {
		sources.clear();
		targets.clear();
		Set<Endpoint> sourceSeen = new HashSet<>();
		Set<Endpoint> targetSeen = new HashSet<>();
		for (BlockPos pipe : pipes) {
			if (!(level.getBlockEntity(pipe) instanceof ItemPipeBlockEntity entity)) continue;
			for (Direction direction : Direction.values()) {
				if (!ItemPipeBlock.shouldConnectTo(level, pipe, direction)) continue;
				BlockPos neighbour = pipe.relative(direction);
				if (pipes.contains(neighbour)) continue;
				PipeFaceMode mode = entity.faceMode(direction);
				if (mode == PipeFaceMode.DISABLED) continue;
				Endpoint endpoint = new Endpoint(neighbour.immutable(), direction.getOpposite());
				if (mode == PipeFaceMode.NEUTRAL || mode == PipeFaceMode.EXTRACT) sourceSeen.add(endpoint);
				if (mode == PipeFaceMode.NEUTRAL || mode == PipeFaceMode.INSERT) targetSeen.add(endpoint);
			}
		}
		sources.addAll(sourceSeen);
		targets.addAll(targetSeen);
		endpointsDirty = false;
		if (!sources.isEmpty()) sourceCursor %= sources.size(); else sourceCursor = 0;
		if (!targets.isEmpty()) targetCursor %= targets.size(); else targetCursor = 0;
	}
}
