package dev.alaindustrial.settlement;

/**
 * The shell that shows a settlement's border to the player who asked for it (MOD-843, MOD-848): walls exactly on the
 * edge of the territory and a dome over them, the same shape from inside and from outside (epic decision R60).
 *
 * <p><b>The walls are the territory.</b> {@link SettlementTerritory.Area} holds the columns between its bounds, both
 * included, so the walls stand on the outer faces of the edge blocks: {@code minX} and {@code maxX + 1}, and the same
 * along z. A block touching a wall from inside is in the town, one touching it from outside is not.
 *
 * <p><b>Floor and walls' top are a choice, not the territory.</b> The territory is a column through the whole world;
 * drawn to the build limits it would be two walls a kilometre tall with nothing to stand on. The shell closes it
 * {@link #FLOOR_DEPTH} blocks under the core (the laboratory under the town hall, R29); the walls end
 * {@link #CEILING_HEIGHT} above it (the drone station on the roof, R21), where the dome starts.
 *
 * <p><b>The dome.</b> A cloister vault over the walls: every horizontal section is the territory's rectangle scaled
 * towards its centre, every vertical section a quarter ellipse rising {@link #DOME_RISE} of the shorter half-side. It
 * meets the walls with no seam, reads as one shell, and costs the same rings of quads as a flat lid cut into pieces;
 * a hemisphere would have needed a circle laid over a rectangle.
 *
 * <p><b>Inside and outside</b> (R60). From outside the shell is translucent glass with a grid: walls, floor and dome.
 * From inside, under the dome and within the walls ({@link Box#encloses}), only the grid of the walls and the dome:
 * no fill and no floor, which inside a town would lie over every room and street and hide where the town ends.
 *
 * <p>Every face is cut into pieces no longer than {@link #PIECE} blocks: the gizmo pass orders translucent quads by
 * distance, and one wall-long quad would be ordered wrongly against the rest (MOD-665). Minecraft-free: points in,
 * quads and lines out through a {@link Sink}, so the L1 lane checks the shape directly.
 */
public final class SettlementShell {

	/** Blocks under the core where the floor lies. */
	public static final int FLOOR_DEPTH = 16;
	/** Blocks above the top of the core where the walls end and the dome starts. */
	public static final int CEILING_HEIGHT = 16;
	/** The longest edge of one quad, in blocks; also the spacing of the grid lines. */
	public static final int PIECE = 8;
	/** The fewest rings the dome is built from, foot to apex; a large dome takes more ({@link #domeRings}). */
	public static final int DOME_RINGS = 8;
	/** The dome's height as a share of the territory's shorter half-side. */
	public static final double DOME_RISE = 0.5;

	private SettlementShell() {
	}

	/** A point in world space. */
	public record Point(double x, double y, double z) {
	}

	/** Where the geometry goes: the client turns it into gizmos, the L1 test counts and checks it. */
	public interface Sink {
		/** One flat piece of the shell, corners in order around it; either side may face the viewer. */
		void quad(Point a, Point b, Point c, Point d);

		/** One edge of the grid. */
		void line(Point a, Point b);
	}

	/**
	 * The box the shell's walls enclose; the dome stands on its top.
	 *
	 * @param minX west wall; {@code maxX} east wall, one past the last block of the territory
	 * @param minY floor
	 * @param maxY the top of the walls, the foot of the dome
	 */
	public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

		/**
		 * Whether ({@code x}, {@code y}, {@code z}) is inside the shell, faces included: within the walls, above the
		 * floor and under the dome. A point under the dome lies on the base rectangle scaled by {@code s} towards the
		 * centre, and the dome stands {@code rise * sqrt(1 - s^2)} over the walls' top there.
		 */
		public boolean encloses(double x, double y, double z) {
			if (x < minX || x > maxX || z < minZ || z > maxZ || y < minY) {
				return false;
			}
			if (y <= maxY) {
				return true;
			}
			double s = Math.max(Math.abs(x - centreX()) / halfX(), Math.abs(z - centreZ()) / halfZ());
			return y <= maxY + domeRise() * Math.sqrt(Math.max(0.0, 1.0 - s * s));
		}

		double centreX() {
			return (minX + maxX) / 2.0;
		}

		double centreZ() {
			return (minZ + maxZ) / 2.0;
		}

		double halfX() {
			return (maxX - minX) / 2.0;
		}

		double halfZ() {
			return (maxZ - minZ) / 2.0;
		}

		/** The height of the dome's apex above the walls' top. */
		public double domeRise() {
			return Math.min(halfX(), halfZ()) * DOME_RISE;
		}
	}

	/** The shell of {@code territory}, at the height of the core standing at {@code coreY}. */
	public static Box box(SettlementTerritory.Area territory, int coreY) {
		return new Box(territory.minX(), coreY - FLOOR_DEPTH, territory.minZ(),
				territory.maxX() + 1.0, coreY + 1.0 + CEILING_HEIGHT, territory.maxZ() + 1.0);
	}

	/**
	 * Emit the shell as seen from ({@code eyeX}, {@code eyeY}, {@code eyeZ}): from inside it ({@link Box#encloses})
	 * the grid lines of the walls and the dome alone; from anywhere else the walls, the floor and the dome, filled and
	 * with their grid.
	 */
	public static void emit(Box box, double eyeX, double eyeY, double eyeZ, Sink sink) {
		boolean outside = !box.encloses(eyeX, eyeY, eyeZ);
		walls(box, outside, sink);
		if (outside) {
			lid(box, box.minY(), sink);
		}
		dome(box, outside, sink);
	}

	/** The four walls, each cut into a grid of pieces ({@code fill}), and the grid's lines. */
	private static void walls(Box box, boolean fill, Sink sink) {
		double[] ys = cuts(box.minY(), box.maxY());
		double[][] corners = perimeter(box);
		for (int side = 0; side < 4; side++) {
			double[] from = corners[side];
			double[] to = corners[(side + 1) % 4];
			double[] ts = cuts(0.0, 1.0, Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1]));
			for (int i = 0; i + 1 < ts.length; i++) {
				double x0 = lerp(from[0], to[0], ts[i]);
				double z0 = lerp(from[1], to[1], ts[i]);
				double x1 = lerp(from[0], to[0], ts[i + 1]);
				double z1 = lerp(from[1], to[1], ts[i + 1]);
				for (int j = 0; fill && j + 1 < ys.length; j++) {
					sink.quad(new Point(x0, ys[j], z0), new Point(x1, ys[j], z1),
							new Point(x1, ys[j + 1], z1), new Point(x0, ys[j + 1], z0));
				}
				// The vertical at the start of each piece; the side's end is the next side's start.
				sink.line(new Point(x0, box.minY(), z0), new Point(x0, box.maxY(), z0));
			}
			for (double y : ys) {
				sink.line(new Point(from[0], y, from[1]), new Point(to[0], y, to[1]));
			}
		}
	}

	/** A flat lid at {@code y} — the floor — cut into pieces, with its inner grid lines. */
	private static void lid(Box box, double y, Sink sink) {
		double[] xs = cuts(box.minX(), box.maxX());
		double[] zs = cuts(box.minZ(), box.maxZ());
		for (int i = 0; i + 1 < xs.length; i++) {
			for (int j = 0; j + 1 < zs.length; j++) {
				sink.quad(new Point(xs[i], y, zs[j]), new Point(xs[i + 1], y, zs[j]),
						new Point(xs[i + 1], y, zs[j + 1]), new Point(xs[i], y, zs[j + 1]));
			}
		}
		for (int i = 1; i + 1 < xs.length; i++) {
			sink.line(new Point(xs[i], y, box.minZ()), new Point(xs[i], y, box.maxZ()));
		}
		for (int j = 1; j + 1 < zs.length; j++) {
			sink.line(new Point(box.minX(), y, zs[j]), new Point(box.maxX(), y, zs[j]));
		}
	}

	/**
	 * The cloister vault over the walls: {@link #domeRings} rings, ring {@code k} the base rectangle scaled by
	 * {@code cos θ} towards the centre at {@code rise · sin θ} above the walls' top, {@code θ = k · 90° / rings}.
	 * Each piece of the perimeter climbs as a strip of quads ({@code fill}); its lower edges are the meridian lines.
	 */
	private static void dome(Box box, boolean fill, Sink sink) {
		double cx = box.centreX();
		double cz = box.centreZ();
		int rings = domeRings(box);
		// Every ring's scale and height once, not four times per quad: the trig was most of the dome's cost.
		double[] scale = new double[rings + 1];
		double[] height = new double[rings + 1];
		for (int k = 0; k <= rings; k++) {
			Point unit = ring(0.0, 0.0, box.maxY(), box.domeRise(), 1.0, 0.0, k, rings);
			scale[k] = unit.x();
			height[k] = unit.y();
		}
		double[][] corners = perimeter(box);
		for (int side = 0; side < 4; side++) {
			double[] from = corners[side];
			double[] to = corners[(side + 1) % 4];
			double[] ts = cuts(0.0, 1.0, Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1]));
			Point[] colA = column(cx, cz, lerp(from[0], to[0], ts[0]), lerp(from[1], to[1], ts[0]), scale, height);
			for (int i = 0; i + 1 < ts.length; i++) {
				Point[] colB = column(cx, cz, lerp(from[0], to[0], ts[i + 1]), lerp(from[1], to[1], ts[i + 1]),
						scale, height);
				for (int k = 0; k < rings; k++) {
					if (fill) {
						sink.quad(colA[k], colB[k], colB[k + 1], colA[k + 1]);
					}
					sink.line(colA[k], colA[k + 1]);
					if (k > 0) {
						sink.line(colA[k], colB[k]);
					}
				}
				colA = colB;
			}
		}
	}

	/** The dome's points above the base point ({@code x}, {@code z}), foot to apex. */
	private static Point[] column(double cx, double cz, double x, double z, double[] scale, double[] height) {
		Point[] out = new Point[scale.length];
		for (int k = 0; k < scale.length; k++) {
			out[k] = new Point(cx + (x - cx) * scale[k], height[k], cz + (z - cz) * scale[k]);
		}
		return out;
	}

	/**
	 * Rings enough that no step of the longest meridian — the one from a corner, a quarter ellipse with semi-axes the
	 * corner's distance from the centre and the rise — is longer than a {@link #PIECE}: a step at angle θ is at most
	 * {@code (90° / rings) · max(a, b)} long.
	 */
	public static int domeRings(Box box) {
		double longest = Math.max(Math.hypot(box.halfX(), box.halfZ()), box.domeRise());
		return Math.max(DOME_RINGS, (int) Math.ceil(Math.PI / 2.0 * longest / PIECE - 1.0e-9));
	}

	/** The point of ring {@code k} of {@code rings} above the base point ({@code x}, {@code z}). */
	static Point ring(double cx, double cz, double foot, double rise, double x, double z, int k, int rings) {
		double theta = Math.PI / 2.0 * k / rings;
		double scale = k == rings ? 0.0 : Math.cos(theta);
		return new Point(cx + (x - cx) * scale, foot + rise * Math.sin(theta), cz + (z - cz) * scale);
	}

	/** The four corners of the base rectangle, in order around it, as (x, z). */
	private static double[][] perimeter(Box box) {
		return new double[][] {
				{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.maxX(), box.maxZ()}, {box.minX(), box.maxZ()}};
	}

	/** {@code from}, {@code to} and the points between them, at most {@link #PIECE} apart and evenly spaced. */
	static double[] cuts(double from, double to) {
		return cuts(from, to, to - from);
	}

	/** {@code from}..{@code to} split into the pieces a run of {@code length} blocks needs. */
	private static double[] cuts(double from, double to, double length) {
		int pieces = Math.max(1, (int) Math.ceil(length / PIECE - 1.0e-9));
		double[] out = new double[pieces + 1];
		for (int i = 0; i <= pieces; i++) {
			out[i] = i == pieces ? to : from + (to - from) * i / pieces;
		}
		return out;
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
