package dev.alaindustrial.core.item;

import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.net.GraphNetworkManager;
import dev.alaindustrial.core.net.NetworkOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-level graph manager for item pipes. Since MOD-401 the bookkeeping lives in the shared
 * {@link GraphNetworkManager} — this class used to be a hand copy of the energy manager, which is
 * how the three copies came to drift. What is genuinely item-specific stays here:
 * <ul>
 *   <li><b>connectivity is a predicate</b> ({@link ItemPipeBlock#shouldConnectTo}), so a face-mode
 *       change can restore a link as well as break one and the union must follow the re-partition
 *       (MOD-282);</li>
 *   <li><b>no tick budget</b> — every awake network ticks every tick, which is why the round-robin
 *       cursor advances by one instead of by the visited window: two item networks can feed the same
 *       chest, and rotating the order is what keeps them fair;</li>
 *   <li><b>a neighbour change re-partitions</b> rather than merely waking the network, unlike energy
 *       and fluid.</li>
 * </ul>
 */
public final class ItemNetworkManager {
	private ItemNetworkManager() { }

	private static final NetworkOps<ServerLevel, ItemNetwork, BlockPos> OPS =
			new NetworkOps<ServerLevel, ItemNetwork, BlockPos>() {
				@Override
				public ItemNetwork create(ServerLevel level) {
					return new ItemNetwork(level);
				}

				@Override
				public Set<BlockPos> nodes(ItemNetwork network) {
					return network.pipes();
				}

				@Override
				public void addNode(ItemNetwork network, BlockPos pos) {
					network.addPipe(pos);
				}

				@Override
				public void removeNode(ItemNetwork network, BlockPos pos) {
					network.removePipe(pos);
				}

				@Override
				public void absorb(ItemNetwork keep, ItemNetwork drop) {
					keep.absorb(drop);
				}

				@Override
				public void markDirty(ItemNetwork network) {
					network.markDirty();
				}

				@Override
				public boolean isAwake(ItemNetwork network) {
					return network.isAwake();
				}

				@Override
				public long tick(ItemNetwork network) {
					// An item network has no throughput counter the telemetry slot could carry; only the
					// energy manager reads that slot.
					network.tick();
					return 0L;
				}

				@Override
				public Iterable<BlockPos> candidates(BlockPos pos) {
					List<BlockPos> result = new ArrayList<>(6);
					for (Direction dir : Direction.values()) {
						result.add(pos.relative(dir).immutable());
					}
					return result;
				}

				@Override
				public Iterable<BlockPos> connected(ServerLevel level, BlockPos pos) {
					List<BlockPos> result = new ArrayList<>(6);
					for (Direction dir : Direction.values()) {
						if (ItemPipeBlock.shouldConnectTo(level, pos, dir)) {
							result.add(pos.relative(dir).immutable());
						}
					}
					return result;
				}
			};

	/**
	 * No budget: {@link Integer#MAX_VALUE} means "every awake network, every tick", which is what this
	 * manager has always done. The cursor therefore has to advance by one to rotate the serving order
	 * — see {@link GraphNetworkManager.TickCursor#BY_ONE}.
	 */
	private static final GraphNetworkManager<ServerLevel, ItemNetwork, BlockPos> GRAPH =
			new GraphNetworkManager<>("item", OPS, () -> Integer.MAX_VALUE,
					GraphNetworkManager.TickCursor.BY_ONE);

	public static void register(ItemPipeBlockEntity pipe) {
		if (!(pipe.getLevel() instanceof ServerLevel level)) return;
		GRAPH.register(level, pipe.getBlockPos().immutable());
	}

	public static void unregister(ItemPipeBlockEntity pipe) {
		if (!(pipe.getLevel() instanceof ServerLevel level)) return;
		GRAPH.unregister(level, pipe.getBlockPos());
	}

	/** Rebuild after a face-mode change: face modes can split a component without a block removal. */
	public static void topologyChanged(ServerLevel level, BlockPos pos) {
		GRAPH.retopologise(level, pos);
	}

	/** Network owning {@code pos}, or null. Exposed for tests; mirrors NetworkManager#networkAt. */
	public static ItemNetwork networkAt(ServerLevel level, BlockPos pos) {
		return GRAPH.networkAt(level, pos);
	}

	public static void onNeighbourChanged(ServerLevel level, BlockPos pos) {
		GRAPH.rebuildAt(level, pos);
	}

	public static void tickAll(ServerLevel level) {
		GRAPH.tickAll(level);
	}

	/** Drop one level's state (level unload), driven by the shared registry sweep. */
	public static void clear(ServerLevel level) { GRAPH.clearLevel(level); }

	/** Drop all per-level state (server stop), driven by the shared registry sweep. */
	public static void clearAll() { GRAPH.clearAll(); }
}
