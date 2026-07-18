package dev.alaindustrial.core;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** One connected component of item-pipe segments, ticked once by {@link ItemNetworkManager}. */
public final class ItemNetwork {
	private record Endpoint(BlockPos pos, Direction side) { }

	/**
	 * Stable order for {@link #sources}/{@link #targets} (MOD-115). The endpoints are collected into a
	 * {@code HashSet}, whose iteration order shifts when the set's composition changes; a numeric
	 * round-robin cursor over that shifting order would occasionally skip or double-serve an endpoint.
	 * Sorting by {@code (BlockPos, side)} gives the cursor a stable meaning across rebuilds.
	 */
	private static final Comparator<Endpoint> ENDPOINT_ORDER =
			Comparator.comparingLong((Endpoint e) -> e.pos().asLong()).thenComparingInt(e -> e.side().ordinal());

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
	 * Once every {@link Config#itemPipeTransferIntervalTicks} ticks (MOD-108 balance gate), serves
	 * <b>every</b> target endpoint with up to {@link Config#itemPipeItemsPerTransfer} items — parallel
	 * distribution (MOD-115). One chest feeding several furnaces fills them together, instead of the old
	 * behaviour that served a single endpoint per interval and made every extra machine slower.
	 *
	 * <p>Both cursors rotate one step per interval and independently, over the sorted (stable) endpoint
	 * lists, so that when a source runs short no single endpoint is permanently favoured (fair N→M, no
	 * lockstep). The cooldown is only spent when something actually moved: a network whose source is
	 * empty or whose targets are all full keeps checking every tick and resumes the instant that changes.
	 */
	int tick() {
		if (endpointsDirty) refreshEndpoints();
		if (sources.isEmpty() || targets.isEmpty()) return 0;
		if (transferCooldown > 0) {
			transferCooldown--;
			return 0;
		}
		int per = Config.itemPipeItemsPerTransfer;
		int sourceCount = sources.size();
		int targetCount = targets.size();
		int totalMoved = 0;
		for (int t = 0; t < targetCount; t++) {
			Endpoint target = targets.get((targetCursor + t) % targetCount);
			ItemPort to = ItemLookup.get().find(level, target.pos(), target.side());
			if (to == null) continue;
			for (int s = 0; s < sourceCount; s++) {
				Endpoint source = sources.get((sourceCursor + s) % sourceCount);
				if (source.pos().equals(target.pos())) continue; // never churn an endpoint into itself
				ItemPort from = ItemLookup.get().find(level, source.pos(), source.side());
				if (from == null) continue;
				int moved = ItemMover.move(from, to, per);
				if (moved > 0) {
					totalMoved += moved;
					break; // this target is served for the interval; move on to the next target
				}
			}
		}
		if (totalMoved > 0) {
			sourceCursor = (sourceCursor + 1) % sourceCount;
			targetCursor = (targetCursor + 1) % targetCount;
			transferCooldown = Math.max(0, Config.itemPipeTransferIntervalTicks - 1);
		}
		return totalMoved;
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
		sources.sort(ENDPOINT_ORDER);
		targets.sort(ENDPOINT_ORDER);
		endpointsDirty = false;
		if (!sources.isEmpty()) sourceCursor %= sources.size(); else sourceCursor = 0;
		if (!targets.isEmpty()) targetCursor %= targets.size(); else targetCursor = 0;
	}
}
