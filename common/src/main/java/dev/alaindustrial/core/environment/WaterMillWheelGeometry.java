package dev.alaindustrial.core.environment;

/**
 * Minecraft-free wheel-vs-wheel AABB geometry for the water mill (MOD-175/MOD-179). Extracted from
 * {@link WaterMillInterference} into its own class so it can be unit-tested at L1: the common test
 * classpath has no Minecraft jar, and the JVM refuses to link a class whose other methods reference
 * Minecraft types — so the pure maths must live in a class with zero Minecraft imports (the same
 * reason {@link WaterMillOutput} is standalone). {@code WaterMillInterference.hasInterference}
 * evaluates exactly {@link #wheelsOverlap} per candidate neighbour; the constants here are the single
 * source of truth for the wheel box.
 */
public final class WaterMillWheelGeometry {
	/** Half-extent of the wheel in its rotation plane — the rim outer radius {@code RIM_OUTER}. */
	public static final double DISC_HALF_SIZE = 1.32;
	/** Half-thickness of the wheel box along {@code FACING} — the rim depth {@code RIM_BACK}. */
	public static final double DISC_HALF_DEPTH = 0.4375;
	/** Distance from the mill's block centre to the wheel centre along {@code FACING} (renderer push). */
	public static final double DISC_PUSH = 1.02;
	/** Interval-overlap slack so wheels meeting exactly edge-to-edge do not count as overlapping. */
	static final double EPSILON = 1.0E-4;

	private WaterMillWheelGeometry() {
	}

	/**
	 * Whether the wheel of a mill at block {@code (ax, ay, az)} facing {@code (afx, afy, afz)} overlaps
	 * the wheel of a mill at {@code (bx, by, bz)} facing {@code (bfx, bfy, bfz)}. Each facing is its
	 * unit step vector ({@code Direction#getStepX} etc.). Overlap requires positive volume on all three
	 * axes — touching edge-to-edge (within {@link #EPSILON}) is not overlap.
	 */
	public static boolean wheelsOverlap(int ax, int ay, int az, int afx, int afy, int afz,
			int bx, int by, int bz, int bfx, int bfy, int bfz) {
		return boxesOverlap(discBox(ax, ay, az, afx, afy, afz), discBox(bx, by, bz, bfx, bfy, bfz));
	}

	/**
	 * Axis-aligned box of the wheel for a mill at block {@code (x, y, z)} whose facing has unit step
	 * vector {@code (fx, fy, fz)}, as {@code {minX, minY, minZ, maxX, maxY, maxZ}}: wheel centre
	 * pushed {@link #DISC_PUSH} from the block centre along the facing, ±{@link #DISC_HALF_DEPTH}
	 * along the facing axis (the axis whose step is non-zero) and ±{@link #DISC_HALF_SIZE} along the
	 * two plane axes.
	 */
	private static double[] discBox(int x, int y, int z, int fx, int fy, int fz) {
		double cx = x + 0.5 + fx * DISC_PUSH;
		double cy = y + 0.5 + fy * DISC_PUSH;
		double cz = z + 0.5 + fz * DISC_PUSH;
		double hx = fx != 0 ? DISC_HALF_DEPTH : DISC_HALF_SIZE;
		double hy = fy != 0 ? DISC_HALF_DEPTH : DISC_HALF_SIZE;
		double hz = fz != 0 ? DISC_HALF_DEPTH : DISC_HALF_SIZE;
		return new double[] {cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz};
	}

	/** Positive-volume overlap on all three axes; edge contact (within {@link #EPSILON}) is not overlap. */
	private static boolean boxesOverlap(double[] a, double[] b) {
		return a[0] < b[3] - EPSILON && b[0] < a[3] - EPSILON
				&& a[1] < b[4] - EPSILON && b[1] < a[4] - EPSILON
				&& a[2] < b[5] - EPSILON && b[2] < a[5] - EPSILON;
	}
}
