package dev.alaindustrial.core.world;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Lets go of a drop a puller cannot bring in (MOD-592, MOD-802).
 *
 * <p>A drop behind a wall, in a hole or snagged on a block edge gets the same shove at every pull and never
 * arrives, and each shove costs energy. The rule: a drop that has not come any closer for {@code stallPulls}
 * pulls is released for {@code releaseTicks} game ticks, during which the puller neither moves it nor pays
 * for it; then it is tried again, in case the way opened.
 *
 * <p>One tracker per puller kind, so the electromagnet and the vacuum collector, which measure distance to
 * different targets, never read each other's history. Server thread only. Keys are weak, so a drop that is
 * picked up or despawns drops out of the map on its own.
 */
public final class StuckDropTracker {

	/** A pull that brings the drop at least this much closer (blocks) counts as progress. */
	private static final double PROGRESS = 0.05;

	private final int stallPulls;
	private final int releaseTicks;
	private final Map<Entity, Track> tracks = new WeakHashMap<>();

	private static final class Track {
		double lastDistance = Double.MAX_VALUE;
		int stalls;
		long releasedUntil = Long.MIN_VALUE;
	}

	/**
	 * @param stallPulls consecutive pulls without getting closer before the drop is released
	 * @param releaseTicks how long a released drop is left alone, in game ticks
	 */
	public StuckDropTracker(int stallPulls, int releaseTicks) {
		this.stallPulls = stallPulls;
		this.releaseTicks = releaseTicks;
	}

	/** Whether this puller has let go of {@code drop} for now. */
	public boolean isReleased(Entity drop, long gameTime) {
		Track track = tracks.get(drop);
		return track != null && gameTime < track.releasedUntil;
	}

	/**
	 * Record one pull on {@code drop}, made while it was {@code distance} blocks from the target. Returns
	 * whether this pull released it.
	 */
	public boolean recordPull(Entity drop, double distance, long gameTime) {
		Track track = tracks.computeIfAbsent(drop, d -> new Track());
		if (distance < track.lastDistance - PROGRESS) {
			track.stalls = 0;
		} else if (++track.stalls >= stallPulls) {
			track.releasedUntil = gameTime + releaseTicks;
			track.stalls = 0;
			track.lastDistance = Double.MAX_VALUE;
			return true;
		}
		track.lastDistance = Math.min(track.lastDistance, distance);
		return false;
	}

	/** Forget {@code drop} — for tests that reuse one detached entity across scenarios. */
	public void forget(Entity drop) {
		tracks.remove(drop);
	}
}
