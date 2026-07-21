package dev.alaindustrial.core.environment;

/**
 * Pure production arithmetic for the water mill — extracted so it can be unit-tested without a
 * Minecraft runtime (L1, see {@code docs/testing/AUTOMATION-STANDARDS.md}). No Minecraft imports,
 * no side effects. {@code WaterMillBlockEntity.produce()} calls this so the tested code <em>is</em>
 * the production code.
 */
public final class WaterMillOutput {
	private WaterMillOutput() {
	}

	/**
	 * EU/t = {@code perSide × waterSides}, with {@code waterSides} clamped to the four horizontal faces
	 * (0..4), so the fully-surrounded case never exceeds {@code perSide × 4}.
	 *
	 * @param waterSides count of horizontal faces touching water (any int; clamped to 0..4)
	 * @param perSide    EU/t produced per water face ({@code Config.waterMillEuPerTick})
	 */
	public static int euFor(int waterSides, int perSide) {
		int sides = Math.max(0, Math.min(4, waterSides));
		return perSide * sides;
	}
}
