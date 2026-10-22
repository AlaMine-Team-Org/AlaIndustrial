package dev.alaindustrial.settlement.drone;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Who works in a town's construction, for this run of the server (MOD-858; epic decisions R91, R93): which dispatcher
 * of a town is the one, which of its nests may fly, and which cell a drone is busy with. Never saved — every block
 * reports itself while it ticks ({@link #dispatcherCheckIn}, {@link #nestCheckIn}), and a block that has not reported
 * for {@link #ALIVE_TICKS} ticks (broken, or its chunk unloaded) drops out.
 *
 * <p><b>The first to come works.</b> Of a town's dispatchers the one that reported first and is still alive works; a
 * second one stands idle and says why. Of its nests the first {@code limit} alive ones work, in the order they first
 * reported. After a restart the order is the order of the first ticks — the spec's rule («the one that started
 * working earlier») for one run of the server.
 *
 * <p><b>Two drones never take the same cell.</b> {@link #reserve} hands a cell to one taker until it gives it back
 * ({@link #release}); the work queue already keeps one drone to a building (R91), and this is the safety net for
 * anything that ever lets two of them near one cell.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks it ({@code TownDroneRosterTest}). Server thread only. Kept by the
 * towns' registry for the server's lifetime ({@code SettlementRegistry#drones}).
 */
public final class TownDroneRoster {

	/** Ticks after which a block that stopped reporting no longer counts. */
	public static final long ALIVE_TICKS = 100L;

	/** One reporting block: when it last reported and whether its drone is busy. */
	private static final class Entry {
		long lastSeen;
		boolean busy;

		Entry(long lastSeen) {
			this.lastSeen = lastSeen;
		}
	}

	private final Map<Integer, LinkedHashMap<Long, Entry>> dispatchers = new HashMap<>();
	private final Map<Integer, LinkedHashMap<Long, Entry>> nests = new HashMap<>();
	private final Map<Long, String> cells = new HashMap<>();

	/**
	 * Dispatcher at {@code pos} of town {@code town} reports at {@code gameTime}; returns whether it is the town's
	 * working one.
	 */
	public boolean dispatcherCheckIn(int town, long pos, long gameTime) {
		LinkedHashMap<Long, Entry> list = report(dispatchers, town, pos, gameTime);
		return list.keySet().iterator().next() == pos;
	}

	/** The working dispatcher of {@code town} at {@code gameTime}, if one is alive. */
	public OptionalLong dispatcherOf(int town, long gameTime) {
		LinkedHashMap<Long, Entry> list = dispatchers.get(town);
		if (list == null) {
			return OptionalLong.empty();
		}
		prune(list, gameTime);
		return list.isEmpty() ? OptionalLong.empty() : OptionalLong.of(list.keySet().iterator().next());
	}

	/**
	 * Nest at {@code pos} of town {@code town} reports at {@code gameTime}, its drone {@code busy} or not; returns its
	 * rank among the town's alive nests (0 the first) — it works while the rank is below the town's limit.
	 */
	public int nestCheckIn(int town, long pos, long gameTime, boolean busy) {
		LinkedHashMap<Long, Entry> list = report(nests, town, pos, gameTime);
		list.get(pos).busy = busy;
		int rank = 0;
		for (long key : list.keySet()) {
			if (key == pos) {
				return rank;
			}
			rank++;
		}
		return rank;
	}

	/** How many alive nests of {@code town} have a busy drone at {@code gameTime}, among the first {@code limit}. */
	public int busyNests(int town, long gameTime, int limit) {
		LinkedHashMap<Long, Entry> list = nests.get(town);
		if (list == null) {
			return 0;
		}
		prune(list, gameTime);
		int busy = 0;
		int rank = 0;
		for (Entry entry : list.values()) {
			if (rank++ >= limit) {
				break;
			}
			if (entry.busy) {
				busy++;
			}
		}
		return busy;
	}

	/** How many nests of {@code town} are alive at {@code gameTime}. */
	public int nests(int town, long gameTime) {
		LinkedHashMap<Long, Entry> list = nests.get(town);
		if (list == null) {
			return 0;
		}
		prune(list, gameTime);
		return list.size();
	}

	/** The block at {@code pos} left town {@code town} — broken, or moved to another town. */
	public void forget(int town, long pos) {
		LinkedHashMap<Long, Entry> list = dispatchers.get(town);
		if (list != null) {
			list.remove(pos);
		}
		list = nests.get(town);
		if (list != null) {
			list.remove(pos);
		}
	}

	/** Hands {@code cell} to {@code taker}; false when another taker holds it. Asking again for one's own is true. */
	public boolean reserve(long cell, String taker) {
		String holder = cells.putIfAbsent(cell, taker);
		return holder == null || holder.equals(taker);
	}

	/** Who holds {@code cell}, or null. */
	public String holder(long cell) {
		return cells.get(cell);
	}

	/** {@code taker} gives back every cell it holds. */
	public void release(String taker) {
		cells.values().removeIf(taker::equals);
	}

	private static LinkedHashMap<Long, Entry> report(Map<Integer, LinkedHashMap<Long, Entry>> byTown, int town,
			long pos, long gameTime) {
		LinkedHashMap<Long, Entry> list = byTown.computeIfAbsent(town, ignored -> new LinkedHashMap<>());
		prune(list, gameTime);
		Entry entry = list.get(pos);
		if (entry == null) {
			list.put(pos, new Entry(gameTime));
		} else {
			entry.lastSeen = gameTime;
		}
		return list;
	}

	private static void prune(LinkedHashMap<Long, Entry> list, long gameTime) {
		Iterator<Entry> it = list.values().iterator();
		while (it.hasNext()) {
			Entry entry = it.next();
			if (gameTime - entry.lastSeen > ALIVE_TICKS) {
				it.remove();
			}
		}
	}
}
