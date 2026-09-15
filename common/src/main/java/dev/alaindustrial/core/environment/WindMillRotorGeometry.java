package dev.alaindustrial.core.environment;

/**
 * Minecraft-free rotor-disc geometry for the wind mill family (MOD-634): the one set of numbers read by
 * both {@code WindMillRotorBlockEntityRenderer}, which draws the disc, and {@link WindMillInterference},
 * which keeps two discs apart. While each held its own, the check placed the disc half a block further
 * out than it is drawn — it took 1.08, the disc centre measured from the block's CORNER on a south- or
 * east-facing mill, and added it to the block's CENTRE. Kept free of Minecraft types for the same reason
 * as {@link WaterMillWheelGeometry}: the L1 test classpath has no Minecraft jar, and the JVM refuses to
 * link a class whose other methods reference one.
 */
public final class WindMillRotorGeometry {
	/** Half-extent of the rotor quad in its rotation plane: the quad spans 2×2 blocks. */
	public static final double DISC_HALF_SIZE = 1.0;
	/**
	 * Distance from the mill's block CENTRE to the disc centre along {@code FACING}. The face is at 0.5,
	 * so the drawn quad hangs 0.08 in front of it, inside the first slice of the front neighbour's cell.
	 */
	public static final double DISC_PUSH = 0.58;
	/** Half-thickness of the disc box along {@code FACING} — the quad is flat, this is tolerance. */
	public static final double DISC_HALF_DEPTH = 0.1;
	/** Interval-overlap slack so discs meeting exactly edge-to-edge do not count as overlapping. */
	static final double EPSILON = 1.0E-4;

	private WindMillRotorGeometry() {
	}

	/**
	 * Whether the disc of a mill at block {@code (ax, ay, az)} facing {@code (afx, afy, afz)} overlaps the
	 * disc of a mill at {@code (bx, by, bz)} facing {@code (bfx, bfy, bfz)}. Each facing is its unit step
	 * vector ({@code Direction#getStepX} etc.). Overlap requires positive volume on all three axes —
	 * touching edge-to-edge (within {@link #EPSILON}) is not overlap.
	 */
	public static boolean discsOverlap(int ax, int ay, int az, int afx, int afy, int afz,
			int bx, int by, int bz, int bfx, int bfy, int bfz) {
		return boxesOverlap(discBox(ax, ay, az, afx, afy, afz), discBox(bx, by, bz, bfx, bfy, bfz));
	}

	/**
	 * Axis-aligned box of the disc for a mill at block {@code (x, y, z)} whose facing has unit step vector
	 * {@code (fx, fy, fz)}, as {@code {minX, minY, minZ, maxX, maxY, maxZ}}: centre pushed
	 * {@link #DISC_PUSH} from the block centre along the facing, ±{@link #DISC_HALF_DEPTH} along the facing
	 * axis and ±{@link #DISC_HALF_SIZE} along the two plane axes.
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
