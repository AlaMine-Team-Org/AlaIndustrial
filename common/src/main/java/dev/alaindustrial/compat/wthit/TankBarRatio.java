package dev.alaindustrial.compat.wthit;

/**
 * How full WTHIT's fluid bar is drawn for one of our tanks (MOD-938).
 *
 * <p>WTHIT 20.0.0's {@code SpriteBarComponent} is 100 px wide and clips its fill to {@code fill - 2}
 * pixels ({@code enableScissor(x + 1, …, x + fill - 1, …)}). A fill of 1 or 2 px — a tank 1–3 % full —
 * gives an empty scissor, and the 26.x render pass throws {@code "Scissor size must be >0"}, taking
 * the game down. The fix is upstream (badasintended/wthit#376) but not released.
 *
 * <p>So a tank that is not empty is never drawn below 3 %: its bar is at most 2 px longer than the
 * truth, only when it is nearly empty, and the {@code stored/capacity} text beside it stays exact.
 * A ratio of 0 still draws nothing — WTHIT skips a zero fill on its own.
 *
 * <p>Minecraft-free and WTHIT-free on purpose, so the L1 lane can pin the band.
 */
public final class TankBarRatio {
	/** The smallest fill WTHIT can clip without an empty scissor: 3 px of its 100 px bar. */
	static final float MIN_VISIBLE = 0.03F;

	private TankBarRatio() {
	}

	/** The bar fill for {@code amount} of {@code capacity}, in [0, 1]. */
	public static float of(long amount, long capacity) {
		if (amount <= 0 || capacity <= 0) {
			return 0F;
		}
		float ratio = Math.min(1F, (float) amount / capacity);
		return Math.max(MIN_VISIBLE, ratio);
	}
}
