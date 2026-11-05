package dev.alaindustrial.core.quarry;

/**
 * What one quarry operation costs in energy and time (MOD-951; spec {@code docs/blocks/mining/quarry.md}, "energy"
 * and "speed"): drilling by the block's hardness with a floor on both, the bridge and the cart travelling to the
 * cell, and the drill's rope going down to it and back up.
 *
 * <p>The travel is paid in stage B already, though nothing moves on screen yet (owner, 2026-11-05): speed and price
 * stay the same when the animation arrives. The bridge and the cart travel at once, so the travel takes as long as
 * the longer of the two and costs both.
 *
 * <p>Minecraft-free; the numbers are {@code Config} knobs handed in as a record, so a test sets its own.
 *
 * @param euPerHardness     drilling EU per unit of hardness
 * @param minDrillEu        drilling EU of the softest block
 * @param stoneDrillTicks   drilling ticks of stone (hardness {@value #STONE_HARDNESS}); others in proportion
 * @param minDrillTicks     drilling ticks of the softest block
 * @param travelPerSecond   blocks the bridge or the cart travel in a second
 * @param travelEuPerBlock  EU per block either of them travels
 * @param ropePerSecond     blocks of rope a second, down or up
 * @param ropeEuPerBlock    EU per block of rope
 */
public record QuarryRates(int euPerHardness, int minDrillEu, int stoneDrillTicks, int minDrillTicks,
		int travelPerSecond, int travelEuPerBlock, int ropePerSecond, int ropeEuPerBlock) {

	/** The hardness the drilling time is stated for: stone's. */
	public static final float STONE_HARDNESS = 1.5F;

	private static final int TICKS_PER_SECOND = 20;

	/** EU to drill one block of {@code hardness}. */
	public long drillEu(float hardness) {
		return Math.max(minDrillEu, Math.round(euPerHardness * (double) hardness));
	}

	/** Ticks to drill one block of {@code hardness}. */
	public int drillTicks(float hardness) {
		return Math.max(minDrillTicks, Math.round(stoneDrillTicks * hardness / STONE_HARDNESS));
	}

	/** Ticks for the bridge to move {@code alongPaths} blocks and the cart {@code alongBridge}, at once. */
	public int travelTicks(int alongPaths, int alongBridge) {
		int longest = Math.max(Math.abs(alongPaths), Math.abs(alongBridge));
		return ceilTicks(longest, travelPerSecond);
	}

	/** EU for that travel: every block either of them moves. */
	public long travelEu(int alongPaths, int alongBridge) {
		return (long) travelEuPerBlock * (Math.abs(alongPaths) + Math.abs(alongBridge));
	}

	/** Ticks for {@code blocks} of rope, down and up together. */
	public int ropeTicks(int blocks) {
		return ceilTicks(Math.abs(blocks), ropePerSecond);
	}

	/** EU for {@code blocks} of rope. */
	public long ropeEu(int blocks) {
		return (long) ropeEuPerBlock * Math.abs(blocks);
	}

	private static int ceilTicks(int blocks, int perSecond) {
		if (blocks == 0) {
			return 0;
		}
		return (int) Math.ceil(blocks * (double) TICKS_PER_SECOND / Math.max(1, perSecond));
	}
}
