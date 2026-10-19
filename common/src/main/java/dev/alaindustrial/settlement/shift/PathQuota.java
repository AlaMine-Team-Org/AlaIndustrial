package dev.alaindustrial.settlement.shift;

/**
 * The path dispatcher of one town (MOD-832, ADR-050 point 11): no goal of a shift worker plans a path on its own;
 * it asks here first, and the town grants at most {@code perTick} new paths in one game tick. A denied worker
 * simply tries again on a later tick, so ten workers who all want to walk at once spread their path searches
 * over a few ticks instead of paying for them in one.
 *
 * <p>The first slice keeps it this simple on purpose — one entry point from the first day, so a smarter dispatcher
 * (priorities, a shared path cache, road nodes) replaces the body without touching the goals.
 *
 * <p>Server thread only, not saved. Minecraft-free: the L1 lane tests it ({@code PathQuotaTest}).
 */
public final class PathQuota {

	private long tick = Long.MIN_VALUE;
	private int granted;

	/**
	 * Asks for one new path on game tick {@code gameTime}; true if granted. The count starts again on every new
	 * tick, so an unused grant is not saved up for later.
	 *
	 * @param perTick paths the town grants per tick (the knob; below 1 counts as 1, or no worker could ever walk)
	 */
	public boolean tryAcquire(long gameTime, int perTick) {
		if (gameTime != tick) {
			tick = gameTime;
			granted = 0;
		}
		if (granted >= Math.max(1, perTick)) {
			return false;
		}
		granted++;
		return true;
	}
}
