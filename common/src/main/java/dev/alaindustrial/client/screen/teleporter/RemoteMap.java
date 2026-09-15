package dev.alaindustrial.client.screen.teleporter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The «Map» tab's geometry (MOD-629), kept free of Minecraft so L1 runs every rule of it: the radar's logarithmic
 * scale, stations merging into clusters, arrows parked on the rim for far stations, what a click lands on, the order
 * the wheel walks, compass points and the price the screen shows.
 *
 * <p>The numbers are the approved mockup's (variant 6), taken from its generator rather than off the picture. Screen
 * axes are the map's: north up, so world +X runs right and world +Z runs down. Lengths are GUI pixels inside a radar
 * {@link #SIZE} across, with the origin at its top-left corner.
 */
public final class RemoteMap {

	/** The radar's side. */
	public static final int SIZE = 122;
	/** The player's position on the radar: the corner between the four middle pixels. */
	public static final double CENTRE = SIZE / 2;
	/** Kept free for the player's arrow, so a station 20 m away is not hidden under it. */
	public static final double INNER = 7.0;
	/** Where the last ring lies. */
	public static final double OUTER = SIZE / 2.0 - 6.0;
	/** The last ring's distance; anything farther becomes an arrow on the rim. */
	public static final double LAST_RING_BLOCKS = 5000.0;
	/** The distance the scale is linear up to — below it a few blocks still move a dot. */
	private static final double KNEE_BLOCKS = 40.0;
	/** Dots closer than this merge into one numbered plate. */
	public static final double MERGE_PX = 7.0;
	/** Half the side of the widest marker (a 9×9 plate), plus a pixel of slack for the hand. */
	private static final double HIT_HALF = 5.0;

	/** The player's arrow and a rim arrow, as points (u right, v forward) in pixels. */
	private static final double[][] PLAYER_ARROW = {{0, 4.5}, {-3.6, -3.6}, {0, -1.2}, {3.6, -3.6}};
	private static final double[][] RIM_ARROW = {{0, 3.0}, {-3.2, -2.2}, {3.2, -2.2}};

	private RemoteMap() {
	}

	/** Radius on the radar for a distance in blocks: 7 px at the player, 55 px at 5 000 blocks, logarithmic between. */
	public static double radius(double distance) {
		return INNER + (OUTER - INNER) * Math.log1p(Math.max(0.0, distance) / KNEE_BLOCKS)
				/ Math.log1p(LAST_RING_BLOCKS / KNEE_BLOCKS);
	}

	/**
	 * The price on screen: rounded UP to a hundred, so the number shown is never less than the one charged. Rounded to
	 * the nearest hundred instead, «needs 20,000 EU» over a charge of 20,010 would promise a jump costing 20,025 that
	 * the server refuses.
	 */
	public static long shownPrice(long exact) {
		return exact <= 0 ? 0 : (exact + 99) / 100 * 100;
	}

	/** Compass point from the player towards a station: 0 north, 1 north-east … 7 north-west. */
	public static int compassPoint(double dx, double dz) {
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		degrees = ((degrees % 360.0) + 360.0) % 360.0;
		return (int) Math.floor((degrees + 22.5) / 45.0) % 8;
	}

	/** A bound station in the player's dimension: its index in the remote's list and its offset in blocks. */
	public record Placed(int index, double dx, double dz) {

		public double distance() {
			return Math.hypot(dx, dz);
		}
	}

	/**
	 * One thing drawn on the radar: a lamp, a numbered cluster, or an arrow on the rim.
	 *
	 * @param x radar-relative centre
	 * @param dirX unit direction from the player, for a rim arrow
	 * @param far past the last ring: drawn as an arrow, and never merged
	 * @param members the stations it stands for, in the order they were placed
	 */
	public record Marker(double x, double y, double dirX, double dirY, boolean far, List<Integer> members) {

		public Marker {
			members = List.copyOf(members);
		}

		public boolean isCluster() {
			return members.size() > 1;
		}

		/** The pixel the marker is drawn around. */
		public int pixelX() {
			return (int) Math.rint(x);
		}

		public int pixelY() {
			return (int) Math.rint(y);
		}
	}

	/**
	 * Places the stations on the radar in the order given. A dot within {@link #MERGE_PX} of an earlier dot joins it,
	 * and the plate moves to the average of its members; a station past the last ring is an arrow on the rim.
	 */
	public static List<Marker> markers(List<Placed> stations) {
		List<double[]> positions = new ArrayList<>();
		List<List<Integer>> members = new ArrayList<>();
		List<Boolean> far = new ArrayList<>();
		for (Placed station : stations) {
			double distance = station.distance();
			double ux = distance == 0 ? 0 : station.dx() / distance;
			double uy = distance == 0 ? 0 : station.dz() / distance;
			if (distance > LAST_RING_BLOCKS) {
				positions.add(new double[] {CENTRE + ux * (OUTER + 0.5), CENTRE + uy * (OUTER + 0.5), ux, uy});
				members.add(new ArrayList<>(List.of(station.index())));
				far.add(true);
				continue;
			}
			double r = radius(distance);
			double px = CENTRE + r * ux;
			double py = CENTRE + r * uy;
			int into = -1;
			for (int m = 0; m < positions.size(); m++) {
				if (!far.get(m) && Math.hypot(positions.get(m)[0] - px, positions.get(m)[1] - py) < MERGE_PX) {
					into = m;
					break;
				}
			}
			if (into < 0) {
				positions.add(new double[] {px, py, ux, uy});
				members.add(new ArrayList<>(List.of(station.index())));
				far.add(false);
			} else {
				List<Integer> group = members.get(into);
				group.add(station.index());
				double[] at = positions.get(into);
				at[0] += (px - at[0]) / group.size();
				at[1] += (py - at[1]) / group.size();
			}
		}
		List<Marker> out = new ArrayList<>(positions.size());
		for (int m = 0; m < positions.size(); m++) {
			double[] at = positions.get(m);
			out.add(new Marker(at[0], at[1], at[2], at[3], far.get(m), members.get(m)));
		}
		return out;
	}

	/** The marker under a radar-relative point, or -1. The last drawn wins, as it lies on top. */
	public static int markerAt(List<Marker> markers, double x, double y) {
		for (int m = markers.size() - 1; m >= 0; m--) {
			Marker marker = markers.get(m);
			if (Math.abs(x - (marker.pixelX() + 0.5)) <= HIT_HALF && Math.abs(y - (marker.pixelY() + 0.5)) <= HIT_HALF) {
				return m;
			}
		}
		return -1;
	}

	/** A click on a cluster picks its first station, and the next one each time it is clicked again. */
	public static int nextInCluster(List<Integer> members, int selected) {
		int at = members.indexOf(selected);
		return at < 0 ? members.get(0) : members.get((at + 1) % members.size());
	}

	/** The stations' indices, nearest first; equal distances keep the remote's order. */
	public static List<Integer> byDistance(List<Placed> stations) {
		return stations.stream()
				.sorted(Comparator.comparingDouble(Placed::distance).thenComparingInt(Placed::index))
				.map(Placed::index)
				.toList();
	}

	/**
	 * One step through an order from the current selection, wrapping at both ends. A selection outside the order starts
	 * from its first entry going forward and from its last going back.
	 */
	public static int step(List<Integer> order, int current, int step) {
		if (order.isEmpty()) {
			return current;
		}
		int at = order.indexOf(current);
		if (at < 0) {
			return step >= 0 ? order.get(0) : order.get(order.size() - 1);
		}
		return order.get(Math.floorMod(at + Integer.signum(step), order.size()));
	}

	/**
	 * Whether a pixel of the random-jump zone is dotted: inside the ring between the two radii, on a diagonal lattice of
	 * every third pixel. Coordinates are the panel's, so the lattice sits where the mockup put it.
	 */
	public static boolean zoneDot(int panelX, int panelY, int radarLeft, int radarTop, double innerPx, double outerPx) {
		double d = Math.hypot(panelX + 0.5 - (radarLeft + CENTRE), panelY + 0.5 - (radarTop + CENTRE));
		return d >= innerPx && d <= outerPx && Math.floorMod(panelX + panelY, 3) == 0
				&& Math.floorMod(panelX - panelY, 3) == 0;
	}

	/** The pixels of a ring at a radius, radar-relative, each once. */
	public static List<int[]> ringPixels(double r) {
		Set<Long> seen = new LinkedHashSet<>();
		for (int t = 0; t < 720; t++) {
			double a = Math.toRadians(t / 2.0);
			seen.add(pack((int) Math.rint(CENTRE + r * Math.cos(a) - 0.5), (int) Math.rint(CENTRE + r * Math.sin(a) - 0.5)));
		}
		return unpack(seen);
	}

	/** A drawn arrow: its body and the one-pixel outline around it, radar-relative. */
	public record ArrowPixels(List<int[]> body, List<int[]> edge) {
	}

	/** The player's arrow at the centre, pointing along a unit screen direction. */
	public static ArrowPixels playerArrow(double forwardX, double forwardY) {
		return arrow(CENTRE, CENTRE, forwardX, forwardY, PLAYER_ARROW);
	}

	/** A rim arrow for a far station, pointing away from the player. */
	public static ArrowPixels rimArrow(Marker marker) {
		return arrow(marker.x(), marker.y(), marker.dirX(), marker.dirY(), RIM_ARROW);
	}

	/**
	 * The screen direction the player faces, from Minecraft's yaw: 0° faces south (+Z, down the map) and 90° faces west
	 * (−X, left).
	 */
	public static double[] facing(float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return new double[] {-Math.sin(yaw), Math.cos(yaw)};
	}

	private static ArrowPixels arrow(double cx, double cy, double fx, double fy, double[][] shape) {
		double rx = -fy;
		double ry = fx;
		double[][] points = new double[shape.length][];
		for (int i = 0; i < shape.length; i++) {
			double u = shape[i][0];
			double v = shape[i][1];
			points[i] = new double[] {cx + u * rx + v * fx, cy + u * ry + v * fy};
		}
		Set<Long> body = rasterise(points);
		Set<Long> edge = new LinkedHashSet<>();
		for (long packed : body) {
			int x = (int) (packed >> 32);
			int y = (int) packed;
			for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				long neighbour = pack(x + n[0], y + n[1]);
				if (!body.contains(neighbour)) {
					edge.add(neighbour);
				}
			}
		}
		return new ArrowPixels(unpack(body), unpack(edge));
	}

	/** The pixels whose centres fall inside a polygon, by the even-odd rule — pixel art, not an anti-aliased shape. */
	private static Set<Long> rasterise(double[][] points) {
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (double[] p : points) {
			minX = Math.min(minX, p[0]);
			maxX = Math.max(maxX, p[0]);
			minY = Math.min(minY, p[1]);
			maxY = Math.max(maxY, p[1]);
		}
		Set<Long> out = new LinkedHashSet<>();
		for (int iy = (int) Math.floor(minY); iy <= (int) Math.ceil(maxY); iy++) {
			for (int ix = (int) Math.floor(minX); ix <= (int) Math.ceil(maxX); ix++) {
				double px = ix + 0.5;
				double py = iy + 0.5;
				boolean inside = false;
				for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
					double xi = points[i][0], yi = points[i][1], xj = points[j][0], yj = points[j][1];
					if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
						inside = !inside;
					}
				}
				if (inside) {
					out.add(pack(ix, iy));
				}
			}
		}
		return out;
	}

	private static long pack(int x, int y) {
		return ((long) x << 32) | (y & 0xFFFFFFFFL);
	}

	private static List<int[]> unpack(Set<Long> packed) {
		List<int[]> out = new ArrayList<>(packed.size());
		for (long p : packed) {
			out.add(new int[] {(int) (p >> 32), (int) p});
		}
		return out;
	}
}
