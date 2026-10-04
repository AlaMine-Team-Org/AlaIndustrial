package dev.alaindustrial.core.energy;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.net.GraphNetworkManager;
import dev.alaindustrial.core.net.GraphNetworkOps;
import dev.alaindustrial.core.net.NetworkOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-{@link ServerLevel} registry of transient {@link EnergyNetwork}s. Networks are never
 * persisted: they are rebuilt from cable block entities as chunks load
 * ({@link #register(CableNode)}) and pruned as cables are removed/unloaded
 * ({@link #unregister(CableNode)}).
 *
 * <p>Since MOD-401 the bookkeeping — the position index, the incremental union on register, the BFS
 * re-partition on removal, the round-robin tick and the level sweep — lives in the shared
 * {@link GraphNetworkManager}; this class is the cable-shaped face of it. What stays here is what is
 * genuinely energy's own:
 * <ul>
 *   <li><b>connectivity is raw adjacency</b> — a cable connects to every cable it touches, with no
 *       per-face switch, so {@link NetworkOps#candidates} and {@link NetworkOps#connected} are the
 *       same six positions;</li>
 *   <li><b>the tick budget</b> is {@link Config#networksPerTick};</li>
 *   <li><b>the balance</b> the algorithms read ({@link NetworkBalance}) is read from {@link Config} here,
 *       once per {@link #tickAll} pass, and handed to every network it ticks (MOD-710, CORE-10);</li>
 *   <li><b>telemetry</b> — {@link Stats} and the EU counters behind {@code /ala net}. The framework
 *       only accumulates the number each tick returns; what it means is decided here.</li>
 * </ul>
 *
 * <p>The public entry points below keep their old signatures on purpose: cable block entities, the
 * {@code /ala net} command and a large L2 gametest suite call them.
 */
public final class NetworkManager {
	private NetworkManager() {
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

	/**
	 * The frame's view of an energy network: every operation is the network's own ({@link GraphNetworkOps},
	 * MOD-715). No per-face switch on a cable: everything it touches is connected, so the candidates and the
	 * connected positions are the same six.
	 */
	private static final NetworkOps<ServerLevel, EnergyNetwork, BlockPos> OPS = new GraphNetworkOps<>(
			EnergyNetwork::new, NetworkManager::axisNeighbours, (level, pos) -> axisNeighbours(pos));

	private static final GraphNetworkManager<ServerLevel, EnergyNetwork, BlockPos> GRAPH =
			new GraphNetworkManager<>("energy", OPS, () -> Config.networksPerTick,
					GraphNetworkManager.TickCursor.BY_WINDOW);

	/** The six axis-neighbours of a cable position (the caller filters to the live cable set). */
	private static List<BlockPos> axisNeighbours(BlockPos pos) {
		List<BlockPos> ns = new ArrayList<>(6);
		for (Direction dir : Direction.values()) {
			ns.add(pos.relative(dir).immutable());
		}
		return ns;
	}

	/** Register a cable block entity on load/place. Idempotent. */
	public static void register(CableNode cable) {
		ServerLevel level = (ServerLevel) cable.getLevel();
		if (level == null) {
			return;
		}
		GRAPH.register(level, cable.getBlockPos().immutable());
	}

	/** Unregister a cable on removal/unload. Splits the network if removal disconnects it. */
	public static void unregister(CableNode cable) {
		ServerLevel level = (ServerLevel) cable.getLevel();
		if (level == null) {
			return;
		}
		GRAPH.unregister(level, cable.getBlockPos());
	}

	/** Wake the network owning {@code pos} (a neighbour changed). Called on neighbour updates. */
	public static void onNeighbourChanged(ServerLevel level, BlockPos pos) {
		GRAPH.markDirtyAt(level, pos);
	}

	/**
	 * Tick up to {@link Config#networksPerTick} awake networks; round-robin the remainder. The balance is
	 * read once for the whole pass, so every network of it ticks on the same numbers.
	 */
	public static void tickAll(ServerLevel level) {
		NetworkBalance balance = balance();
		GRAPH.tickAll(level, network -> network.tick(balance));
	}

	/**
	 * The knobs the energy algorithms read, as they are now — the one place in the energy core that reads
	 * them from {@link Config} (CORE-10). {@link #tickAll} builds it once per pass; the direct cable-less push
	 * and a lone {@link EnergyNetwork#tick()} take it at the moment they run, as they always read it.
	 */
	public static NetworkBalance balance() {
		return new NetworkBalance(Config.cableBuffer, Config.storageFeedReserveFraction);
	}

	/**
	 * Drop all per-level state (server stop). Both loaders reach it through
	 * {@code LevelStateRegistry.clearAll()} rather than by name; kept public for the gametests.
	 */
	public static void clearAll() {
		GRAPH.clearAll();
	}

	/** Drop one level's state (level unload), likewise driven by the shared registry. */
	public static void clear(ServerLevel level) {
		GRAPH.clearLevel(level);
	}

	// --- test / introspection helpers ---

	/** The network owning {@code pos}, or null. Visible for the self-test. */
	public static EnergyNetwork networkAt(ServerLevel level, BlockPos pos) {
		return GRAPH.networkAt(level, pos);
	}

	/** Total live network count in a level. Visible for the self-test. */
	public static int networkCount(ServerLevel level) {
		return GRAPH.networkCount(level);
	}

	/**
	 * Telemetry snapshot for one level (R-13): network/awake/cable counts plus last-tick and
	 * cumulative EU throughput. Scans the level's networks once, so it is meant for the {@code /ala
	 * net} command, not the hot tick path.
	 */
	public static Stats stats(ServerLevel level) {
		Collection<EnergyNetwork> nets = GRAPH.networks(level);
		int awake = 0;
		int cables = 0;
		for (EnergyNetwork net : nets) {
			cables += net.size();
			if (net.isAwake()) {
				awake++;
			}
		}
		int total = nets.size();
		GraphNetworkManager.Telemetry tick = GRAPH.telemetry(level);
		return new Stats(total, awake, total - awake, cables,
				tick.ticked(), tick.movedLastTick(), tick.movedTotal());
	}
}
