package dev.alaindustrial.core.drone;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * The route a drone flies from its dock to the cell next to the block it works (MOD-858, owner's round 2): through
 * open cells only, never through a solid block. Minecraft-free on purpose — the caller answers «is this cell open»
 * ({@link Cells}) and the L1 lane checks the search directly ({@code DroneRouteTest}).
 *
 * <p><b>The search.</b> A* over the cells of a bounded box around the dock and the target, a step to each of a cell's
 * six face-neighbours (so it never slips across a corner), with the distance on the grid as its estimate. It stops
 * after {@code maxNodes} expanded cells, so its cost is bounded whatever the world looks like. The goal
 * is any open face-neighbour of the target, the one above preferred; the target cell itself is never entered — the
 * block the drone puts there would otherwise stand in its way home.
 *
 * <p><b>The smoothing.</b> The cell path is pulled straight where a drone-wide line stays in open cells
 * ({@link #lineOpen}), so a flight reads as a few straight legs — diagonals included — not as a staircase.
 *
 * <p><b>The fallback.</b> When the search finds nothing in its budget, the drone tries the plain way over: straight up
 * above the obstacles, across, straight down. When that is blocked too there is no route ({@link #found()} false), and
 * the caller skips the cell and tries it again later.
 */
public final class DroneRoute {

	/** Whether a cell is open for a drone: no collision worth the name, loaded, not lava. Answered by the caller. */
	@FunctionalInterface
	public interface Cells {
		boolean open(int x, int y, int z);
	}

	/** How the route was found. */
	public enum Kind {
		/** The A* search found it. */
		SEARCH,
		/** The search did not; the plain way over did. */
		OVER,
		/** No route. */
		NONE
	}

	/** Half the drone's width in blocks: a straight leg keeps this far from any closed cell sideways. */
	public static final double HALF_WIDTH = 0.3;
	/** Step along a leg at which {@link #lineOpen} looks at the cells under it, in blocks. */
	private static final double LINE_STEP = 0.25;
	private static final int[][] FACES = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

	private final int[] points;
	private final int expanded;
	private final Kind kind;

	private DroneRoute(int[] points, int expanded, Kind kind) {
		this.points = points;
		this.expanded = expanded;
		this.kind = kind;
	}

	/** The route's corners, dock first and goal last, as x, y, z triplets; empty when there is no route. */
	public int[] points() {
		return points.clone();
	}

	/** Cells the search expanded: its cost. */
	public int expanded() {
		return expanded;
	}

	public Kind kind() {
		return kind;
	}

	public boolean found() {
		return kind != Kind.NONE;
	}

	/** The route's length in blocks, corner to corner. */
	public double length() {
		return length(points);
	}

	/** The length of a polyline of x, y, z triplets, in blocks. */
	public static double length(int[] points) {
		double total = 0.0;
		for (int i = 3; i + 2 < points.length; i += 3) {
			double dx = points[i] - points[i - 3];
			double dy = points[i + 1] - points[i - 2];
			double dz = points[i + 2] - points[i - 1];
			total += Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
		return total;
	}

	/**
	 * Finds the route from the dock {@code (sx, sy, sz)} to an open face-neighbour of the target {@code (tx, ty, tz)},
	 * inside the box {@code [minX..maxX] x [minY..maxY] x [minZ..maxZ]}, expanding at most {@code maxNodes} cells. The
	 * dock is always open (the drone stands in it), the target never.
	 */
	public static DroneRoute find(int sx, int sy, int sz, int tx, int ty, int tz, Cells cells, int minX, int minY,
			int minZ, int maxX, int maxY, int maxZ, int maxNodes) {
		Planner planner = plan(sx, sy, sz, tx, ty, tz, cells, minX, minY, minZ, maxX, maxY, maxZ, maxNodes);
		planner.advance(Integer.MAX_VALUE);
		return planner.result();
	}

	/**
	 * The same search as {@link #find}, to be carried out a few cells at a time ({@link Planner#advance}): a caller
	 * with a budget per tick spreads one search over several ticks.
	 */
	public static Planner plan(int sx, int sy, int sz, int tx, int ty, int tz, Cells cells, int minX, int minY,
			int minZ, int maxX, int maxY, int maxZ, int maxNodes) {
		Cells open = (x, y, z) -> x == sx && y == sy && z == sz
				|| !(x == tx && y == ty && z == tz) && cells.open(x, y, z);
		return new Planner(new Search(open, minX, minY, minZ, maxX, maxY, maxZ, sx, sy, sz, tx, ty, tz), maxNodes);
	}

	/** One route search in progress: advanced by a number of cells at a time, its route read once it is done. */
	public static final class Planner {
		private final Search search;
		private final int maxNodes;
		private @Nullable DroneRoute result;

		private Planner(Search search, int maxNodes) {
			this.search = search;
			this.maxNodes = maxNodes;
		}

		/** Expands up to {@code nodes} more cells; true once the search is done and {@link #result} is ready. */
		public boolean advance(int nodes) {
			if (result != null) {
				return true;
			}
			long until = Math.min((long) maxNodes, (long) search.expanded + Math.max(1, nodes));
			Boolean found = search.step((int) until);
			if (found == null && search.expanded < maxNodes) {
				return false;
			}
			result = finish(Boolean.TRUE.equals(found) ? search.trace() : null);
			return true;
		}

		/** The route; only once {@link #advance} said the search is done. */
		public DroneRoute result() {
			if (result == null) {
				throw new IllegalStateException("the route search is not done yet");
			}
			return result;
		}

		/** Cells expanded so far. */
		public int expanded() {
			return search.expanded;
		}

		private DroneRoute finish(int @Nullable [] path) {
			// The smoothing and the way over ask the search's own answers: each cell of the box is looked at once.
			Cells cached = search::open;
			if (path != null) {
				return new DroneRoute(smooth(path, cached), search.expanded, Kind.SEARCH);
			}
			int[] over = over(search.sx, search.sy, search.sz, search.tx, search.ty, search.tz, cached, search.maxY());
			if (over != null) {
				return new DroneRoute(over, search.expanded, Kind.OVER);
			}
			return new DroneRoute(new int[0], search.expanded, Kind.NONE);
		}
	}

	/**
	 * The plain way over: up from the dock to the lowest height the whole crossing is open at, across, and down to the
	 * goal — the cell over the target if open, otherwise its first open face-neighbour. Null when nothing up to
	 * {@code maxY} works.
	 */
	static int[] over(int sx, int sy, int sz, int tx, int ty, int tz, Cells open, int maxY) {
		for (int[] face : FACES) {
			int gx = tx + face[0];
			int gy = ty + face[1];
			int gz = tz + face[2];
			if (!open.open(gx, gy, gz)) {
				continue;
			}
			for (int cruise = Math.max(sy, gy); cruise <= maxY; cruise++) {
				int[] route = compact(new int[] {sx, sy, sz, sx, cruise, sz, gx, cruise, gz, gx, gy, gz});
				if (polylineOpen(route, open)) {
					return route;
				}
			}
		}
		return null;
	}

	/** Drops repeated corners (a zero climb or a zero crossing). */
	private static int[] compact(int[] route) {
		int[] out = new int[route.length];
		int n = 0;
		for (int i = 0; i < route.length; i += 3) {
			if (n >= 3 && out[n - 3] == route[i] && out[n - 2] == route[i + 1] && out[n - 1] == route[i + 2]) {
				continue;
			}
			out[n++] = route[i];
			out[n++] = route[i + 1];
			out[n++] = route[i + 2];
		}
		return Arrays.copyOf(out, n);
	}

	private static boolean polylineOpen(int[] route, Cells open) {
		for (int i = 3; i < route.length; i += 3) {
			if (!lineOpen(route[i - 3], route[i - 2], route[i - 1], route[i], route[i + 1], route[i + 2], open)) {
				return false;
			}
		}
		return true;
	}

	/** Pulls the cell path straight: from each kept corner, the farthest later cell a straight leg reaches. */
	static int[] smooth(int[] path, Cells open) {
		int count = path.length / 3;
		int[] out = new int[path.length];
		int n = 0;
		int at = 0;
		out[n++] = path[0];
		out[n++] = path[1];
		out[n++] = path[2];
		while (at < count - 1) {
			int next = at + 1;
			for (int j = count - 1; j > at + 1; j--) {
				if (lineOpen(path[at * 3], path[at * 3 + 1], path[at * 3 + 2], path[j * 3], path[j * 3 + 1],
						path[j * 3 + 2], open)) {
					next = j;
					break;
				}
			}
			out[n++] = path[next * 3];
			out[n++] = path[next * 3 + 1];
			out[n++] = path[next * 3 + 2];
			at = next;
		}
		return Arrays.copyOf(out, n);
	}

	/**
	 * Whether a drone flying straight between the centres of two cells stays in open cells: every {@link #LINE_STEP}
	 * along the leg, the cell under its centre and under its four sideways edges ({@link #HALF_WIDTH}) is open.
	 */
	public static boolean lineOpen(int ax, int ay, int az, int bx, int by, int bz, Cells open) {
		double dx = bx - ax;
		double dy = by - ay;
		double dz = bz - az;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int steps = Math.max(1, (int) Math.ceil(len / LINE_STEP));
		for (int s = 0; s <= steps; s++) {
			double f = (double) s / steps;
			double x = ax + 0.5 + dx * f;
			double y = ay + 0.5 + dy * f;
			double z = az + 0.5 + dz * f;
			for (int k = 0; k < 5; k++) {
				double ox = k == 1 ? HALF_WIDTH : k == 2 ? -HALF_WIDTH : 0.0;
				double oz = k == 3 ? HALF_WIDTH : k == 4 ? -HALF_WIDTH : 0.0;
				if (!open.open((int) Math.floor(x + ox), (int) Math.floor(y), (int) Math.floor(z + oz))) {
					return false;
				}
			}
		}
		return true;
	}

	/** One A* search over the box; arrays sized to the box, allocated once per search. */
	private static final class Search {
		private static final byte UNKNOWN = 0;
		private static final byte OPEN = 1;
		private static final byte CLOSED = 2;
		/** Extra blocks a landing beside or under the target counts as, against the one on top of it. */
		private static final float SIDE_LANDING = 3.0F;
		/** Breaks ties between equally good cells towards the target, so open air is not searched cell by cell. */
		private static final float TIE = 1.001F;

		private final Cells cells;
		private final int minX;
		private final int minY;
		private final int minZ;
		private final int sizeX;
		private final int sizeY;
		private final int sizeZ;
		private final byte[] passable;
		private final float[] cost;
		private final int[] parent;
		private final boolean[] done;
		private final CellHeap heap;
		final int sx;
		final int sy;
		final int sz;
		final int tx;
		final int ty;
		final int tz;
		private int found = -1;
		int expanded;

		Search(Cells cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int sx, int sy, int sz, int tx,
				int ty, int tz) {
			this.cells = cells;
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.tx = tx;
			this.ty = ty;
			this.tz = tz;
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.sizeX = Math.max(1, maxX - minX + 1);
			this.sizeY = Math.max(1, maxY - minY + 1);
			this.sizeZ = Math.max(1, maxZ - minZ + 1);
			int volume = sizeX * sizeY * sizeZ;
			this.passable = new byte[volume];
			this.cost = new float[volume];
			this.parent = new int[volume];
			this.done = new boolean[volume];
			Arrays.fill(cost, Float.POSITIVE_INFINITY);
			this.heap = new CellHeap(Math.min(volume, 4096));
			int start = index(sx, sy, sz);
			if (start >= 0) {
				cost[start] = 0.0F;
				parent[start] = -1;
				heap.push(start, heuristic(sx, sy, sz, tx, ty, tz));
			}
		}

		int maxY() {
			return minY + sizeY - 1;
		}

		private int index(int x, int y, int z) {
			int lx = x - minX;
			int ly = y - minY;
			int lz = z - minZ;
			if (lx < 0 || ly < 0 || lz < 0 || lx >= sizeX || ly >= sizeY || lz >= sizeZ) {
				return -1;
			}
			return (lx * sizeY + ly) * sizeZ + lz;
		}

		boolean open(int x, int y, int z) {
			int i = index(x, y, z);
			if (i < 0) {
				return false;
			}
			if (passable[i] == UNKNOWN) {
				passable[i] = cells.open(x, y, z) ? OPEN : CLOSED;
			}
			return passable[i] == OPEN;
		}

		private int x(int i) {
			return i / (sizeY * sizeZ) + minX;
		}

		private int y(int i) {
			return i / sizeZ % sizeY + minY;
		}

		private int z(int i) {
			return i % sizeZ + minZ;
		}

		/**
		 * Expands cells till {@code expanded} reaches {@code until}: true once the goal is reached, false once nothing
		 * is left to expand, null while the search goes on.
		 */
		@Nullable
		Boolean step(int until) {
			if (found >= 0) {
				return Boolean.TRUE;
			}
			while (expanded < until) {
				if (heap.isEmpty()) {
					return Boolean.FALSE;
				}
				int at = heap.pop();
				if (done[at]) {
					continue;
				}
				done[at] = true;
				expanded++;
				int x = x(at);
				int y = y(at);
				int z = z(at);
				if (goal(x, y, z, tx, ty, tz)) {
					found = at;
					return Boolean.TRUE;
				}
				for (int[] face : FACES) {
					relax(at, x, y, z, face[0], face[1], face[2], tx, ty, tz);
				}
			}
			return heap.isEmpty() ? Boolean.FALSE : null;
		}

		/** An open face-neighbour of the target; the target itself is closed by {@link DroneRoute#find}. */
		private static boolean goal(int x, int y, int z, int tx, int ty, int tz) {
			return Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz) == 1;
		}

		private void relax(int at, int x, int y, int z, int dx, int dy, int dz, int tx, int ty, int tz) {
			int nx = x + dx;
			int ny = y + dy;
			int nz = z + dz;
			int next = index(nx, ny, nz);
			if (next < 0 || done[next] || !open(nx, ny, nz)) {
				return;
			}
			float g = cost[at] + 1.0F;
			if (g < cost[next]) {
				cost[next] = g;
				parent[next] = at;
				// The cell above the target is the landing the drone stands on the block from; another face-neighbour
				// is taken only when reaching the top costs more than SIDE_LANDING blocks more.
				boolean side = goal(nx, ny, nz, tx, ty, tz) && !(nx == tx && nz == tz && ny == ty + 1);
				heap.push(next, g + TIE * heuristic(nx, ny, nz, tx, ty, tz) + (side ? SIDE_LANDING : 0.0F));
			}
		}

		/** Steps on the grid to the nearest face-neighbour of the target: never more than it takes. */
		private static float heuristic(int x, int y, int z, int tx, int ty, int tz) {
			return Math.max(0, Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz) - 1);
		}

		int[] trace() {
			int end = found;
			int length = 0;
			for (int i = end; i >= 0; i = parent[i]) {
				length++;
			}
			int[] out = new int[length * 3];
			int n = out.length;
			for (int i = end; i >= 0; i = parent[i]) {
				out[--n] = z(i);
				out[--n] = y(i);
				out[--n] = x(i);
			}
			return out;
		}
	}
}
