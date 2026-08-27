package dev.alaindustrial.core.structure;

/**
 * MC-free geometry of a room of <em>any</em> closed shape (MOD-505) — a flood fill of the air
 * inside, rather than {@link RoomScan}'s six rays and rectangular perimeter.
 *
 * <p><b>Why a second algorithm instead of extending the first.</b> {@link RoomScan} is deliberately
 * rectangular: it casts rays to find a box and then walks that box's perimeter, which is what lets
 * it say "one block too wide" as distinct from "there is a hole". That precision is worth having for
 * a reactor, whose containment is a box by design. A greenhouse is the opposite — the whole appeal
 * is building a dome, a stepped pyramid, a lean-to — and the perimeter walk rejects every one of
 * those as a breach. The two structures want different answers, so they get different scanners over
 * the same {@link RoomScan.ShellProbe}.
 *
 * <p><b>What is given up, honestly.</b> RoomScan's own docs argue a plain flood fill "cannot tell
 * 'this room is too large' from 'this room leaks' — both simply run until the cap", and that is
 * true. This class does not pretend otherwise: both land on {@link Status#UNSEALED}, and the
 * position it reports is the cell where the fill ran past its limit — not the missing block, but a
 * pointer in its direction, which is still better than a shrug. In exchange the player may build any
 * shape that holds air.
 *
 * <p><b>Shell membership is a 26-neighbourhood, classification a 6-neighbourhood.</b> The fill only
 * ever touches cells that share a FACE with the interior, so a box's edges and corners — which touch
 * it only diagonally — would be missed entirely, and a finished room would be painted with its
 * framing left out. Every cell within one step of the interior, diagonals included, is therefore
 * collected; a cell that faces the interior directly is a wall, and one that does not is
 * {@linkplain #isEdge framing}. That definition needs no notion of a box, so it generalises to a
 * dome as readily as to a cube.
 */
public final class RoomFill {

	/** Why the room is (not) sealed. */
	public enum Status {
		/** A closed volume within the size limits, with exactly one controller in its shell. */
		SEALED,
		/** The cell the controller faces is itself a shell block — it is buried or facing the wrong way. */
		CONTROLLER_NOT_IN_WALL,
		/** The fill ran past its cell budget or its reach: the room leaks, or it is simply too big. */
		UNSEALED,
		/** Fewer interior cells than the minimum — this is a cupboard, not a greenhouse. */
		TOO_SMALL,
		/** A second controller stands in the shell; a room has exactly one brain. */
		SECOND_CONTROLLER
	}

	/**
	 * Outcome of a fill. On {@link Status#SEALED} the cell lists are the answer; otherwise
	 * {@code x/y/z} points at the offending or limiting block and the lists are empty.
	 *
	 * <p>Cells are flat {@code x, y, z} triples rather than objects: a 12-block dome has some
	 * eighteen hundred of them and this runs every couple of seconds, so an allocation per cell is a
	 * cost with nothing to show for it.
	 */
	public record Result(Status status, int x, int y, int z,
			int[] interior, int[] shell, boolean[] shellIsEdge,
			int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

		public boolean sealed() {
			return status == Status.SEALED;
		}

		/** Number of interior cells — the room's volume in blocks. */
		public int volume() {
			return interior.length / 3;
		}

		/** Number of shell cells, walls and framing together. */
		public int shellSize() {
			return shell.length / 3;
		}

		private static Result failure(Status status, int x, int y, int z) {
			return new Result(status, x, y, z, EMPTY, EMPTY, NO_EDGES, 0, 0, 0, -1, -1, -1);
		}
	}

	/** Hard ceiling on the fill's cell budget, whatever the config says. A 32-block cube. */
	private static final int MAX_CELL_BUDGET = 32768;

	private static final int[] EMPTY = new int[0];
	private static final boolean[] NO_EDGES = new boolean[0];

	private RoomFill() {
	}

	/**
	 * Floods the room a controller belongs to.
	 *
	 * @param probe    reads block kinds from the world
	 * @param cx       controller position
	 * @param cy       controller position
	 * @param cz       controller position
	 * @param inX      unit step from the controller towards the interior — the opposite of its facing
	 * @param inY      see {@code inX}
	 * @param inZ      see {@code inX}
	 * @param minCells smallest interior volume that counts as a room
	 * @param maxCells largest interior volume; the fill gives up past this
	 * @param maxSpan  how far from the controller the fill may reach, in blocks. A second, harder
	 *                 limit than {@code maxCells}: it is what keeps a leaking room from reading blocks
	 *                 chunks away, which is both slow and a way to touch unloaded terrain.
	 */
	public static Result fill(RoomScan.ShellProbe probe, int cx, int cy, int cz,
			int inX, int inY, int inZ, int minCells, int maxCells, int maxSpan) {
		int seedX = cx + inX;
		int seedY = cy + inY;
		int seedZ = cz + inZ;
		if (probe.kindAt(seedX, seedY, seedZ).isShell()) {
			return Result.failure(Status.CONTROLLER_NOT_IN_WALL, seedX, seedY, seedZ);
		}

		// Clamped before it is multiplied: a config holding a nonsensical cap would otherwise overflow
		// the set's capacity arithmetic into a negative array size (found by audit). No greenhouse
		// wants more than this, and the leash bounds the fill long before the budget does.
		int budget = Math.min(Math.max(1, maxCells), MAX_CELL_BUDGET);
		CellSet visited = new CellSet(budget * 2 + 64);
		IntBag interior = new IntBag(budget * 3);
		int[] queue = new int[Math.max(48, budget * 3)];
		int head = 0;
		int tail = 0;
		queue[tail++] = seedX;
		queue[tail++] = seedY;
		queue[tail++] = seedZ;
		visited.add(seedX, seedY, seedZ);

		int minX = seedX;
		int minY = seedY;
		int minZ = seedZ;
		int maxX = seedX;
		int maxY = seedY;
		int maxZ = seedZ;

		while (head < tail) {
			int x = queue[head++];
			int y = queue[head++];
			int z = queue[head++];
			interior.add(x, y, z);
			if (interior.size() / 3 > budget) {
				return Result.failure(Status.UNSEALED, x, y, z);
			}
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);

			for (int face = 0; face < 6; face++) {
				int nx = x + FACE_X[face];
				int ny = y + FACE_Y[face];
				int nz = z + FACE_Z[face];
				if (probe.kindAt(nx, ny, nz).isShell()) {
					continue; // a wall — the fill stops here, and this cell is collected later
				}
				// Open, and outside the leash: the room is not closed, and THIS is the way out.
				if (Math.abs(nx - cx) > maxSpan || Math.abs(ny - cy) > maxSpan
						|| Math.abs(nz - cz) > maxSpan) {
					return Result.failure(Status.UNSEALED, nx, ny, nz);
				}
				if (!visited.add(nx, ny, nz)) {
					continue;
				}
				if (tail + 3 > queue.length) {
					queue = grow(queue);
				}
				queue[tail++] = nx;
				queue[tail++] = ny;
				queue[tail++] = nz;
			}
		}

		if (interior.size() / 3 < minCells) {
			return Result.failure(Status.TOO_SMALL, seedX, seedY, seedZ);
		}

		// Every cell within one step of the interior, diagonals included — see the class docs for why
		// the fill's own face-neighbours are not enough.
		CellSet shellSeen = new CellSet(interior.size() + 64);
		IntBag shell = new IntBag(interior.size());
		int[] cells = interior.data();
		for (int i = 0; i < interior.size(); i += 3) {
			int x = cells[i];
			int y = cells[i + 1];
			int z = cells[i + 2];
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						int nx = x + dx;
						int ny = y + dy;
						int nz = z + dz;
						if (visited.contains(nx, ny, nz) || !shellSeen.add(nx, ny, nz)) {
							continue;
						}
						if (probe.kindAt(nx, ny, nz).isShell()) {
							shell.add(nx, ny, nz);
						}
					}
				}
			}
		}

		int[] shellCells = shell.toArray();
		boolean[] isEdge = new boolean[shellCells.length / 3];
		boolean selfSeen = false;
		for (int i = 0; i < shellCells.length; i += 3) {
			int x = shellCells[i];
			int y = shellCells[i + 1];
			int z = shellCells[i + 2];
			isEdge[i / 3] = !facesInterior(visited, x, y, z);
			if (probe.kindAt(x, y, z) == RoomScan.ShellKind.CONTROLLER) {
				if (x == cx && y == cy && z == cz) {
					selfSeen = true;
				} else {
					return Result.failure(Status.SECOND_CONTROLLER, x, y, z);
				}
			}
		}
		if (!selfSeen) {
			// The seed was open air, the fill closed, but this controller is not part of that shell —
			// it is looking into somebody else's room, or into the outdoors.
			return Result.failure(Status.CONTROLLER_NOT_IN_WALL, cx, cy, cz);
		}

		return new Result(Status.SEALED, cx, cy, cz, interior.toArray(), shellCells, isEdge,
				minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** Whether any of the six faces of this cell opens onto the interior — a wall rather than framing. */
	private static boolean facesInterior(CellSet interior, int x, int y, int z) {
		for (int face = 0; face < 6; face++) {
			if (interior.contains(x + FACE_X[face], y + FACE_Y[face], z + FACE_Z[face])) {
				return true;
			}
		}
		return false;
	}

	private static final int[] FACE_X = { -1, 1, 0, 0, 0, 0 };
	private static final int[] FACE_Y = { 0, 0, -1, 1, 0, 0 };
	private static final int[] FACE_Z = { 0, 0, 0, 0, -1, 1 };

	private static int[] grow(int[] array) {
		int[] bigger = new int[array.length * 2];
		System.arraycopy(array, 0, bigger, 0, array.length);
		return bigger;
	}

	/** A growable flat {@code x, y, z} buffer. */
	private static final class IntBag {
		private int[] data;
		private int size;

		IntBag(int capacity) {
			this.data = new int[Math.max(24, capacity)];
		}

		void add(int x, int y, int z) {
			if (size + 3 > data.length) {
				data = grow(data);
			}
			data[size++] = x;
			data[size++] = y;
			data[size++] = z;
		}

		int size() {
			return size;
		}

		int[] data() {
			return data;
		}

		int[] toArray() {
			int[] out = new int[size];
			System.arraycopy(data, 0, out, 0, size);
			return out;
		}
	}

	/**
	 * An open-addressed set of packed cell coordinates.
	 *
	 * <p>A {@code HashSet<Long>} would box every one of a couple of thousand cells twice per scan,
	 * every couple of seconds, per greenhouse. It would also iterate in an order that varies between
	 * runs, and this mod has been bitten before by a HashMap's order making a gametest pass alone and
	 * fail in a full run — nothing here iterates the set, and keeping it that way is deliberate.
	 */
	private static final class CellSet {
		private long[] keys;
		private boolean[] used;
		private int mask;
		private int size;

		CellSet(int expected) {
			int capacity = Integer.highestOneBit(Math.max(16, expected)) * 4;
			this.keys = new long[capacity];
			this.used = new boolean[capacity];
			this.mask = capacity - 1;
		}

		/**
		 * Packs a cell into one long, the way vanilla's own {@code BlockPos.asLong} lays it out:
		 * 26 bits of X, 26 of Z, 12 of Y, filling the word exactly.
		 *
		 * <p>Masking rather than offsetting is what makes negative coordinates work — and the widths
		 * are not decoration: X and Z shifted by anything wider would run off the top of the long and
		 * silently fold two distant cells onto one key. Two cells can only collide if they differ by
		 * 2^26 blocks horizontally or 4096 vertically, neither of which fits inside one room.
		 */
		private static long key(int x, int y, int z) {
			return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
		}

		private static int spread(long key) {
			return (int) (key * 0x9E3779B97F4A7C15L >>> 32);
		}

		/** @return {@code true} if the cell was not already present */
		boolean add(int x, int y, int z) {
			long k = key(x, y, z);
			int i = spread(k) & mask;
			while (used[i]) {
				if (keys[i] == k) {
					return false;
				}
				i = i + 1 & mask;
			}
			used[i] = true;
			keys[i] = k;
			size++;
			// Grown well before full: an open-addressed table that fills completely turns its probe
			// loop into an infinite one, and this runs on the server thread.
			if (size * 3 > keys.length * 2) {
				rehash();
			}
			return true;
		}

		boolean contains(int x, int y, int z) {
			long k = key(x, y, z);
			int i = spread(k) & mask;
			while (used[i]) {
				if (keys[i] == k) {
					return true;
				}
				i = i + 1 & mask;
			}
			return false;
		}

		private void rehash() {
			long[] oldKeys = keys;
			boolean[] oldUsed = used;
			keys = new long[oldKeys.length * 2];
			used = new boolean[oldUsed.length * 2];
			mask = keys.length - 1;
			for (int j = 0; j < oldKeys.length; j++) {
				if (!oldUsed[j]) {
					continue;
				}
				int i = spread(oldKeys[j]) & mask;
				while (used[i]) {
					i = i + 1 & mask;
				}
				used[i] = true;
				keys[i] = oldKeys[j];
			}
		}
	}
}
