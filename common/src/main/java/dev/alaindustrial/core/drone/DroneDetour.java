package dev.alaindustrial.core.drone;

import java.util.ArrayDeque;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * The way in to a cell a drone cannot reach through the air (epic MOD-824, owner's round 5 of 0.1.219): the fewest
 * blocks it would have to lift out of its way for a while — at most a few — so it reaches an open face-neighbour of
 * the target, as {@link DroneRoute} would. The drone takes them away, does its work and puts them back.
 *
 * <p>A 0-1 breadth-first search over the cells of a bounded box: stepping into an open cell costs nothing, into a cell
 * the caller lets the drone lift ({@link Kind#LIFT}) costs one, a closed cell is never entered, and neither is the
 * target. The dock is always open. It stops after {@code maxNodes} cells, so its cost is bounded whatever the world.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks it ({@code DroneDetourTest}).
 */
public final class DroneDetour {

	/** What a cell is to the search. */
	public enum Kind {
		/** The drone flies through it. */
		OPEN,
		/** Closed now, but the drone may lift its block out of the way for a while. */
		LIFT,
		/** Never entered. */
		CLOSED
	}

	/** Answers what a cell is; asked at most once per cell. */
	@FunctionalInterface
	public interface Cells {
		Kind kind(int x, int y, int z);
	}

	private static final int[][] FACES = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

	private DroneDetour() {
	}

	/**
	 * The cells to lift, in the order the drone lifts them — from the dock's side towards the target, as x, y, z
	 * triplets — so that the target gets an open face-neighbour the dock reaches through open cells; empty when it
	 * has one already, null when no way in lifts {@code maxLifts} cells or fewer inside the box
	 * {@code [minX..maxX] x [minY..maxY] x [minZ..maxZ]} within {@code maxNodes} cells.
	 */
	public static int @Nullable [] find(int sx, int sy, int sz, int tx, int ty, int tz, Cells cells, int minX,
			int minY, int minZ, int maxX, int maxY, int maxZ, int maxLifts, int maxNodes) {
		int dx = maxX - minX + 1;
		int dy = maxY - minY + 1;
		int dz = maxZ - minZ + 1;
		if (dx <= 0 || dy <= 0 || dz <= 0 || !inBox(sx, sy, sz, minX, minY, minZ, maxX, maxY, maxZ)) {
			return null;
		}
		int size = dx * dy * dz;
		byte[] kinds = new byte[size];
		int[] lifts = new int[size];
		int[] parent = new int[size];
		Arrays.fill(lifts, Integer.MAX_VALUE);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		int start = ((sx - minX) * dy + (sy - minY)) * dz + (sz - minZ);
		lifts[start] = 0;
		parent[start] = -1;
		queue.add(start);
		int expanded = 0;
		while (!queue.isEmpty() && expanded < maxNodes) {
			int at = queue.pollFirst();
			expanded++;
			int x = at / (dy * dz) + minX;
			int y = at / dz % dy + minY;
			int z = at % dz + minZ;
			if (Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz) == 1) {
				return trace(at, parent, kinds, dy, dz, minX, minY, minZ);
			}
			for (int[] face : FACES) {
				int nx = x + face[0];
				int ny = y + face[1];
				int nz = z + face[2];
				if (nx == tx && ny == ty && nz == tz || !inBox(nx, ny, nz, minX, minY, minZ, maxX, maxY, maxZ)) {
					continue;
				}
				int next = ((nx - minX) * dy + (ny - minY)) * dz + (nz - minZ);
				if (kinds[next] == 0) {
					kinds[next] = (byte) (cells.kind(nx, ny, nz).ordinal() + 1);
				}
				int cost = kinds[next] == Kind.OPEN.ordinal() + 1 ? 0 : kinds[next] == Kind.LIFT.ordinal() + 1 ? 1 : -1;
				int total = lifts[at] + cost;
				if (cost < 0 || total > maxLifts || total >= lifts[next]) {
					continue;
				}
				lifts[next] = total;
				parent[next] = at;
				if (cost == 0) {
					queue.addFirst(next);
				} else {
					queue.addLast(next);
				}
			}
		}
		return null;
	}

	/** The lifted cells on the way from the dock to {@code end}, dock side first. */
	private static int[] trace(int end, int[] parent, byte[] kinds, int dy, int dz, int minX, int minY, int minZ) {
		int count = 0;
		for (int at = end; at >= 0; at = parent[at]) {
			if (kinds[at] == Kind.LIFT.ordinal() + 1) {
				count++;
			}
		}
		int[] out = new int[count * 3];
		int n = count;
		for (int at = end; at >= 0; at = parent[at]) {
			if (kinds[at] == Kind.LIFT.ordinal() + 1) {
				n--;
				out[n * 3] = at / (dy * dz) + minX;
				out[n * 3 + 1] = at / dz % dy + minY;
				out[n * 3 + 2] = at % dz + minZ;
			}
		}
		return out;
	}

	private static boolean inBox(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}
}
