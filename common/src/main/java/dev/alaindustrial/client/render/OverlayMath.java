package dev.alaindustrial.client.render;

/**
 * Minecraft-free arithmetic of the Network Analyzer overlay (MOD-665), so the L1 suite can pin it:
 * colour alpha, the flow-spark clock, the frame budget and how much of a tube run can be drawn at the
 * edge of the loaded world.
 */
public final class OverlayMath {
	/**
	 * Most flow sparks one frame builds (D4). Past it, further sparks are skipped: a spark is a comet of
	 * a few small boxes, and a base with thousands of animated edges must not cost thousands of them a frame.
	 */
	public static final int MAX_FRAME_PULSES = 512;

	private OverlayMath() {
	}

	/**
	 * {@code argb} with its alpha replaced by {@code alpha} (clamped to 0..255) — D12: the configured
	 * overlay opacity used to reach the tube only, so the role cubes and the sparks stayed opaque on a
	 * translucent trace.
	 */
	public static int withAlpha(int argb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
	}

	/**
	 * Where along its edge (0..1) spark {@code dot} of {@code dotsPerEdge} is, at {@code gameTicks} game
	 * ticks (whole ticks plus the frame's partial tick). D14: the phase used to follow the wall clock, so
	 * the sparks kept running while the game was paused; game time stops with the game.
	 */
	public static double flowPhase(double gameTicks, int dot, int dotsPerEdge, double edgesPerSecond) {
		double phase = (gameTicks / 20.0 * edgesPerSecond + (double) dot / dotsPerEdge) % 1.0;
		return phase < 0 ? phase + 1.0 : phase;
	}

	/**
	 * How far along the network a position sits, 0 at a producer and 1 at the farthest position a
	 * producer reaches — the overlay's blue-to-yellow ramp. A position no producer reaches (an unpowered
	 * island) and a network one hop deep both read 0.
	 */
	public static double pathFraction(Integer distance, int maxDistance) {
		if (distance == null || maxDistance <= 0) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, (double) distance / maxDistance));
	}

	/** Channel-wise blend of two ARGB colours, alpha included; {@code t} is clamped to 0..1. */
	public static int lerpColor(int from, int to, double t) {
		double k = Math.max(0.0, Math.min(1.0, t));
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int a = (from >>> shift) & 0xFF;
			int b = (to >>> shift) & 0xFF;
			out |= ((int) Math.round(a + (b - a) * k) & 0xFF) << shift;
		}
		return out;
	}
}
