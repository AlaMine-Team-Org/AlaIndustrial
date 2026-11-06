package dev.alaindustrial.core.quarry;

/**
 * The quarry's heat (MOD-952; spec {@code docs/blocks/mining/quarry.md}, "water and heat"): every block drilled
 * heats the drill, water takes the heat away, and a drill that reaches the top of the scale stops until it has
 * cooled to half. Nothing explodes and nothing breaks: without water the quarry works in bursts.
 *
 * <p>Minecraft-free: the numbers are {@code Config} knobs handed in as a record, the water is a count of millibuckets
 * the caller drains, and the clock is the caller's ticks.
 *
 * @param perBlock        heat one drilled block adds
 * @param max             the top of the scale: the drill stops here
 * @param coolPerSecond   heat lost a second with no water
 * @param heatPerMb       heat one millibucket of water takes away
 */
public record QuarryHeat(int perBlock, int max, int coolPerSecond, int heatPerMb) {

	private static final int TICKS_PER_SECOND = 20;

	/** The state of one drill: its heat, whether it is resting after reaching the top, and the tick of the second. */
	public static final class Gauge {
		private int heat;
		private boolean overheated;
		private int tick;

		public int heat() {
			return heat;
		}

		/** Whether the drill stands to cool: from reaching the top of the scale until it has cooled to half. */
		public boolean overheated() {
			return overheated;
		}

		public void restore(int heat, boolean overheated) {
			this.heat = Math.max(0, heat);
			this.overheated = overheated;
		}
	}

	/** A drilled block heats the drill; at the top of the scale it stops to cool. */
	public void drilled(Gauge gauge) {
		gauge.heat = Math.min(max, gauge.heat + perBlock);
		if (gauge.heat >= max) {
			gauge.overheated = true;
		}
	}

	/**
	 * One tick of cooling: water first, as much of {@code water} millibuckets as the heat needs, then once a second
	 * the drill's own cooling. Returns the millibuckets used, for the caller to drain.
	 */
	public int cool(Gauge gauge, int water) {
		int used = 0;
		if (gauge.heat > 0 && water > 0 && heatPerMb > 0) {
			used = Math.min(water, (gauge.heat + heatPerMb - 1) / heatPerMb);
			gauge.heat = Math.max(0, gauge.heat - used * heatPerMb);
		}
		if (++gauge.tick >= TICKS_PER_SECOND) {
			gauge.tick = 0;
			gauge.heat = Math.max(0, gauge.heat - coolPerSecond);
		}
		if (gauge.overheated && gauge.heat <= max / 2) {
			gauge.overheated = false;
		}
		return used;
	}
}
