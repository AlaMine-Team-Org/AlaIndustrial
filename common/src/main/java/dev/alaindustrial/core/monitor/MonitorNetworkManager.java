package dev.alaindustrial.core.monitor;

import dev.alaindustrial.core.net.GraphNetworkManager;
import dev.alaindustrial.core.net.NetworkOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-level graph manager for monitoring systems (MOD-480) — the fourth client of the shared
 * {@link GraphNetworkManager} after energy, fluid and items.
 *
 * <p>Wires, the core and the panels all live in ONE graph on purpose. Splitting a wall in half and
 * cutting a wire run are the same event as far as connectivity goes, so they get the same machinery:
 * the framework re-partitions the component, and each half that ends up without a core reports it
 * on its panels.
 */
public final class MonitorNetworkManager {

	private MonitorNetworkManager() {
	}

	private static final NetworkOps<ServerLevel, MonitorNetwork, BlockPos> OPS =
			new NetworkOps<ServerLevel, MonitorNetwork, BlockPos>() {
				@Override
				public MonitorNetwork create(ServerLevel level) {
					return new MonitorNetwork(level);
				}

				@Override
				public Set<BlockPos> nodes(MonitorNetwork network) {
					return network.nodes();
				}

				@Override
				public void addNode(MonitorNetwork network, BlockPos pos) {
					network.addNode(pos);
				}

				@Override
				public void removeNode(MonitorNetwork network, BlockPos pos) {
					network.removeNode(pos);
				}

				@Override
				public void absorb(MonitorNetwork keep, MonitorNetwork drop) {
					keep.absorb(drop);
				}

				@Override
				public void markDirty(MonitorNetwork network) {
					network.markDirty();
				}

				@Override
				public boolean isAwake(MonitorNetwork network) {
					return network.isAwake();
				}

				@Override
				public long tick(MonitorNetwork network) {
					// Nothing is transported, so there is no throughput for the telemetry slot to carry.
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
						BlockPos target = pos.relative(dir).immutable();
						if (MonitorNetwork.connects(level, pos, target)) {
							result.add(target);
						}
					}
					return result;
				}
			};

	/**
	 * Every awake system every tick. The work is already gated behind each network's own scan
	 * interval, so the budget would only add a second, coarser clock on top of it.
	 */
	private static final GraphNetworkManager<ServerLevel, MonitorNetwork, BlockPos> GRAPH =
			new GraphNetworkManager<>("monitor", OPS, () -> Integer.MAX_VALUE,
					GraphNetworkManager.TickCursor.BY_ONE);

	public static void register(ServerLevel level, BlockPos pos) {
		GRAPH.register(level, pos.immutable());
	}

	public static void unregister(ServerLevel level, BlockPos pos) {
		GRAPH.unregister(level, pos);
	}

	/** A filter or a card changed: the topology is intact, but the next scan must be re-planned. */
	public static void demandChanged(ServerLevel level, BlockPos pos) {
		MonitorNetwork network = GRAPH.networkAt(level, pos);
		if (network != null) {
			network.markDirty();
		}
	}

	public static void onNeighbourChanged(ServerLevel level, BlockPos pos) {
		GRAPH.rebuildAt(level, pos);
	}

	public static MonitorNetwork networkAt(ServerLevel level, BlockPos pos) {
		return GRAPH.networkAt(level, pos);
	}

	public static void tickAll(ServerLevel level) {
		GRAPH.tickAll(level);
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
