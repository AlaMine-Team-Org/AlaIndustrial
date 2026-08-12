package dev.alaindustrial.core.fluid;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.core.net.GraphNetworkManager;
import dev.alaindustrial.core.net.NetworkOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-level graph manager for fluid pipes (MOD-151). Since MOD-401 the bookkeeping lives in the
 * shared {@link GraphNetworkManager}; what remains here is what is genuinely fluid's own.
 *
 * <p><b>Connectivity is a PREDICATE, not raw adjacency.</b> A pipe face can be switched off, so
 * {@link FluidPipeBlock#shouldConnectTo} decides what is linked. That is why every entry point which
 * may have CREATED a link re-runs the union: the framework's re-partition can only ever split (its
 * traversal is bounded by the network's own node set). Skipping that is exactly MOD-282 — a
 * re-enabled joint left the two halves as separate networks forever, on a line that still looked
 * whole.
 *
 * <p><b>It has a tick budget from day one</b> ({@link Config#fluidNetworksPerTick}), copied from the
 * energy manager rather than the item one, which has none.
 *
 * <p><b>MOD-401 — the leak.</b> {@link #clear(ServerLevel)} and {@link #clearAll()} existed here from
 * the start and were called from nowhere: neither loader listed this manager in its unload / stop
 * handlers, so every {@code ServerLevel} that had ever carried a fluid pipe stayed reachable as a
 * map key for the life of the process. In single player that leaked a whole level — chunks and block
 * entities included — on every trip back to the main menu. The loaders no longer name managers at
 * all; they sweep {@code LevelStateRegistry}, which this manager joins by being constructed.
 */
public final class FluidNetworkManager {
	private FluidNetworkManager() {
	}

	private static final NetworkOps<ServerLevel, FluidNetwork, BlockPos> OPS =
			new NetworkOps<ServerLevel, FluidNetwork, BlockPos>() {
				@Override
				public FluidNetwork create(ServerLevel level) {
					return new FluidNetwork(level);
				}

				@Override
				public Set<BlockPos> nodes(FluidNetwork network) {
					return network.pipes();
				}

				@Override
				public void addNode(FluidNetwork network, BlockPos pos) {
					network.addPipe(pos);
				}

				@Override
				public void removeNode(FluidNetwork network, BlockPos pos) {
					network.removePipe(pos);
				}

				@Override
				public void absorb(FluidNetwork keep, FluidNetwork drop) {
					keep.absorb(drop);
				}

				@Override
				public void markDirty(FluidNetwork network) {
					network.markDirty();
				}

				@Override
				public boolean isAwake(FluidNetwork network) {
					return network.isAwake();
				}

				@Override
				public long tick(FluidNetwork network) {
					// A fluid network has no throughput counter of its own; the framework's telemetry
					// slot stays at zero and only the energy manager reads it.
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
						if (FluidPipeBlock.shouldConnectTo(level, pos, dir)) {
							result.add(pos.relative(dir).immutable());
						}
					}
					return result;
				}
			};

	private static final GraphNetworkManager<ServerLevel, FluidNetwork, BlockPos> GRAPH =
			new GraphNetworkManager<>("fluid", OPS, () -> Config.fluidNetworksPerTick,
					GraphNetworkManager.TickCursor.BY_WINDOW);

	public static void register(FluidPipeBlockEntity pipe) {
		if (!(pipe.getLevel() instanceof ServerLevel level)) {
			return;
		}
		GRAPH.register(level, pipe.getBlockPos().immutable());
	}

	public static void unregister(FluidPipeBlockEntity pipe) {
		if (!(pipe.getLevel() instanceof ServerLevel level)) {
			return;
		}
		GRAPH.unregister(level, pipe.getBlockPos());
	}

	/** Re-partition after a face-mode change: it can break a link, and it can restore one. */
	public static void topologyChanged(ServerLevel level, BlockPos pos) {
		GRAPH.retopologise(level, pos);
	}

	/** A neighbouring block changed: endpoints may differ, but pipe-to-pipe connectivity cannot. */
	public static void onNeighbourChanged(ServerLevel level, BlockPos pos) {
		GRAPH.markDirtyAt(level, pos);
	}

	/** Tick up to {@link Config#fluidNetworksPerTick} awake networks; round-robin the remainder. */
	public static void tickAll(ServerLevel level) {
		GRAPH.tickAll(level);
	}

	/** Network owning {@code pos}, or null. Exposed for tests; mirrors NetworkManager#networkAt. */
	public static FluidNetwork networkAt(ServerLevel level, BlockPos pos) {
		return GRAPH.networkAt(level, pos);
	}

	/** Drop one level's state (level unload), driven by the shared registry sweep. */
	public static void clear(ServerLevel level) {
		GRAPH.clearLevel(level);
	}

	/** Drop all per-level state (server stop), driven by the shared registry sweep. */
	public static void clearAll() {
		GRAPH.clearAll();
	}
}
