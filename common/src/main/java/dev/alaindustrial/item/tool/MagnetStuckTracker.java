package dev.alaindustrial.item.tool;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Lets go of a drop the magnet cannot bring in (MOD-592).
 *
 * <p>The magnet pays per pull, and it pulls every scan. A drop behind a wall, in a hole or snagged on a
 * block edge gets the same shove every tick and never arrives — and every one of those shoves costs
 * EU, so a handful of such drops drains the buffer while nothing reaches the player. The owner's rule:
 * a drop that has not come any closer for {@link #STALL_PULLS} pulls is released for
 * {@link #RELEASE_TICKS} ticks, during which the magnet neither moves it nor pays for it; then it is
 * tried again, in case the player moved.
 *
 * <p>Deliberately NOT about a full inventory: the owner decided a magnet keeps pulling (and paying)
 * when the player has no room, so the drops trail after the player. A drop that reaches the player
 * sits inside the pickup snap distance, where the magnet already stops pulling and stops paying.
 *
 * <p>Server thread only (the pull runs in {@code inventoryTick} and in gametests on the server). Keys
 * are weak, so a drop that is picked up or despawns drops out of the map on its own.
 */
public final class MagnetStuckTracker {

	/** Consecutive pulls without getting closer before the drop is released. At one scan a tick: 1 s. */
	public static final int STALL_PULLS = 20;
	/** How long a released drop is left alone, in game ticks: 2 s. */
	public static final int RELEASE_TICKS = 40;
	/** A pull that brings the drop at least this much closer (blocks) counts as progress. */
	private static final double PROGRESS = 0.05;

	private static final Map<Entity, Track> TRACKS = new WeakHashMap<>();

	private static final class Track {
		double lastDistance = Double.MAX_VALUE;
		int stalls;
		long releasedUntil = Long.MIN_VALUE;
	}

	private MagnetStuckTracker() {
	}

	/** Whether the magnet has let go of {@code drop} for now. */
	public static boolean isReleased(Entity drop, long gameTime) {
		Track track = TRACKS.get(drop);
		return track != null && gameTime < track.releasedUntil;
	}

	/**
	 * Record one pull on {@code drop}, made while it was {@code distance} blocks from the player. Returns
	 * whether this pull released it.
	 */
	public static boolean recordPull(Entity drop, double distance, long gameTime) {
		Track track = TRACKS.computeIfAbsent(drop, d -> new Track());
		if (distance < track.lastDistance - PROGRESS) {
			track.stalls = 0;
		} else if (++track.stalls >= STALL_PULLS) {
			track.releasedUntil = gameTime + RELEASE_TICKS;
			track.stalls = 0;
			track.lastDistance = Double.MAX_VALUE;
			return true;
		}
		track.lastDistance = Math.min(track.lastDistance, distance);
		return false;
	}

	/** Forget {@code drop} — for tests that reuse one detached entity across scenarios. */
	public static void forget(Entity drop) {
		TRACKS.remove(drop);
	}
}
