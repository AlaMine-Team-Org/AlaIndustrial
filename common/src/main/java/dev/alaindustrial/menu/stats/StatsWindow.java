package dev.alaindustrial.menu.stats;

/**
 * One viewer's statistics window (MOD-125, MOD-722): when the next packet is due, and how much EU went
 * through the block over how many game ticks since the previous one.
 *
 * <p>The window carries raw EU and its real length, not a rate. An integer EU/t rounded every average
 * under 1 EU/t down to zero, so a Garden Drone Station paying 8 EU per flight of 26+ ticks read "0 EU/t"
 * while it worked; the client divides and formats the fraction ({@code ReadoutFormat.rate}).
 *
 * <p>Timed by game time, not by calls: the server calls {@code broadcastChanges} once per tick for an open
 * menu and again on every click and button press in it, so a count of calls would close the window early
 * while the client still divided by a full one.
 *
 * <p>Minecraft-free on purpose, so the L1 lane can pin the arithmetic without a server.
 */
public final class StatsWindow {

	/** Game ticks between two statistics packets for one open screen. Two seconds: fast enough to read as live. */
	public static final int INTERVAL_TICKS = 40;

	/** EU produced plus consumed over {@code ticks} game ticks. */
	public record Sample(long eu, int ticks) {}

	/** Game time the current packet interval opened at; {@link Long#MIN_VALUE} before the first. */
	private long intervalStart = Long.MIN_VALUE;

	/** Game time of the previous throughput sample; negative before the first one or after a reset. */
	private long sampleTime = -1L;

	/** {@code energyGenerated + energyConsumed} at the previous sample. */
	private long lastThroughput;

	/**
	 * {@code true} on the first call and then once {@link #INTERVAL_TICKS} of game time have passed since the
	 * last {@code true}; any number of calls within those ticks answer {@code false}.
	 */
	public boolean due(long gameTime) {
		if (intervalStart != Long.MIN_VALUE && gameTime - intervalStart < INTERVAL_TICKS) {
			return false;
		}
		intervalStart = gameTime;
		return true;
	}

	/**
	 * Forget the throughput sample — the chip left the panel. The next sample starts over from the block's
	 * instantaneous rate instead of averaging a span the counters partly did not run through.
	 */
	public void reset() {
		sampleTime = -1L;
	}

	/**
	 * EU that went through the block since the previous sample and the game ticks that took; remembers
	 * {@code throughput} for the next.
	 *
	 * <p>Throughput (produced + consumed) is monotonic, so the difference is the true total over the window,
	 * ticks the block spent asleep included — a per-tick sample would miss those. The first sample has no
	 * predecessor and reports the block's instantaneous rate over one interval, rather than an average over
	 * an unknown span. A total that went backwards reads as nothing moved; a window is at least one tick.
	 *
	 * @param gameTime the level's game time now
	 * @param throughput the block's lifetime {@code energyGenerated + energyConsumed} now
	 * @param instantRate the block's current EU/t, used only for the first sample
	 */
	public Sample sample(long gameTime, long throughput, int instantRate) {
		Sample sample = sampleTime < 0
				? new Sample((long) Math.max(0, instantRate) * INTERVAL_TICKS, INTERVAL_TICKS)
				: new Sample(Math.max(0L, throughput - lastThroughput),
						(int) Math.min(Integer.MAX_VALUE, Math.max(1L, gameTime - sampleTime)));
		sampleTime = gameTime;
		lastThroughput = throughput;
		return sample;
	}
}
