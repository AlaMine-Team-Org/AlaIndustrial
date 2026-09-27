package dev.alaindustrial.item.tool;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rate limit for Network Analyzer scans (MOD-665, D4). Minecraft-free so the L1 suite can pin it.
 *
 * <p>Holding the use key repeats {@code useOn} every four ticks, and each repeat used to run a full
 * traversal and send a full payload — five complete scans a second of a base that had not changed. Now
 * a player gets at most one scan per {@link #MIN_INTERVAL_TICKS}, and the same cable is not rescanned
 * within {@link #SAME_TARGET_TICKS}; a refused scan keeps the picture already shown.
 *
 * <p>Game time is the clock, so a world with an earlier clock (another save, a new server) never finds
 * itself throttled by a scan from the previous one.
 */
public final class ScanThrottle {
	/** Shortest gap between two scans by one player: half a second. */
	public static final int MIN_INTERVAL_TICKS = 10;
	/** Shortest gap before the same cable is scanned again by the same player: two seconds. */
	public static final int SAME_TARGET_TICKS = 40;
	/** Past this many remembered players, entries older than the longest window are dropped. */
	private static final int PRUNE_ABOVE = 64;

	private record Last(long gameTime, long target) {
	}

	private final Map<UUID, Last> last = new LinkedHashMap<>();

	/**
	 * Records and allows the scan, or refuses it.
	 *
	 * @param target the clicked position, packed ({@code BlockPos.asLong()})
	 * @return true when the scan should run
	 */
	public synchronized boolean tryScan(UUID player, long target, long gameTime) {
		Last previous = last.get(player);
		if (previous != null) {
			long age = gameTime - previous.gameTime();
			if (age >= 0 && (age < MIN_INTERVAL_TICKS || (target == previous.target() && age < SAME_TARGET_TICKS))) {
				return false;
			}
		}
		last.put(player, new Last(gameTime, target));
		if (last.size() > PRUNE_ABOVE) {
			for (Iterator<Last> it = last.values().iterator(); it.hasNext(); ) {
				long age = gameTime - it.next().gameTime();
				if (age < 0 || age >= SAME_TARGET_TICKS) {
					it.remove();
				}
			}
		}
		return true;
	}

	/** Forgets every player — for tests. */
	public synchronized void reset() {
		last.clear();
	}
}
