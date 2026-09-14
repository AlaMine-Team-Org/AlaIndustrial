package dev.alaindustrial.core.structure;

import java.util.Arrays;

/**
 * MC-free geometry of the reactor room (MOD-468, stage 1) — the first <em>volumetric</em> multiblock
 * check in the mod. The two existing multiblocks scan fixed vertical offsets ({@code
 * DistillationColumnBlock} 1×1×3, {@code IncubatorBlock} 1×2) and neither generalises to a room, so
 * the shape logic lives here instead of inside a block class: no Minecraft type is referenced, which
 * is exactly what makes it an L1 JUnit target (the lesson of {@code TankMath} — a class that touches
 * {@code net.minecraft..} cannot be exercised by {@code :common:test}).
 *
 * <p><b>The shape.</b> A hollow axis-aligned box. The <em>interior</em> is what the size limits talk
 * about: {@code minInner}…{@code maxInner} blocks along every axis (3…12 by default, so the built
 * shell runs 5×5×5…14×14×14). Every cell of the six faces enclosing that interior must be a shell
 * block — {@link ShellKind#isShell()}. L-shapes, stepped rooms and domes are out of scope: the
 * perimeter walk in phase B rejects them as a breach, which is the honest answer (there is a
 * non-shell block where the shell has to be).
 *
 * <p><b>Two phases, and why.</b> A plain flood fill cannot tell "this room is too large" from "this
 * room leaks" — both simply run until the cap. So:
 * <ol>
 *   <li><b>A — find the six walls</b> from the interior seed (±X, ±Y, ±Z), at most {@code maxInner} + 1
 *       steps out. Each wall is found by a vote of up to nine parallel rays — the seed's and its
 *       neighbours' across the direction — and the wall is where most of them stop. No ray at all
 *       reaching one means the room is unbounded in that direction (too large, or a side knocked out).</li>
 *   <li><b>B — walk the whole perimeter</b> implied by the six walls. This is what finds a one-block
 *       hole anywhere in a 14³ shell, with coordinates — searching that by hand is frustration, not
 *       gameplay, which is why the controller reports the spot. Once a hole is found the walk goes on
 *       counting holes, so a player who has three to fill is shown three.</li>
 * </ol>
 *
 * <p><b>Why a vote and not one ray (MOD-619).</b> One ray per direction was stopped by whatever stood on its
 * single column. A hole there sent it out into the open, and two holes in the middle of a ceiling reported a
 * room with no ceiling at all; a stray casing block inside the room stopped it short, and the box shrank under
 * the real ceiling. The neighbouring rays outvote both. A tie goes to the seed's own ray, then to the nearer
 * wall.
 *
 * <p><b>Order of checks is a UX decision</b>, not an implementation detail: controller placement →
 * bounds → breach → door. Each answer is actionable on its own, and the player fixes one thing at a
 * time rather than being handed a list.
 */
public final class RoomScan {

	/** Smallest interior edge the room may have, in blocks (shell 5×5×5). */
	public static final int DEFAULT_MIN_INNER = 3;

	/** Largest interior edge the room may have, in blocks (shell 14×14×14, 1016 shell blocks). */
	public static final int DEFAULT_MAX_INNER = 12;

	/** Largest share of the shell, in percent, that may be glass. */
	public static final int DEFAULT_MAX_GLASS_PERCENT = 30;

	/**
	 * Most holes a breach lists by position (MOD-619). The count goes on past it: a face knocked out of a 14³
	 * shell is two hundred holes, and no map draws two hundred markers usefully.
	 */
	public static final int MAX_LISTED_HOLES = 12;

	private static final int[] NO_HOLES = {};

	private RoomScan() {
	}

	/**
	 * What the world holds at one position, from the room's point of view. Anything the shell may be
	 * built from is a distinct constant; everything else — air, machines, fuel rods, the player's
	 * chest — is {@link #OTHER} and is legal <em>inside</em> the room but never as part of the shell.
	 */
	public enum ShellKind {
		CASING,
		GLASS,
		DOOR,
		PORT,
		CONTROLLER,
		OTHER;

		/** Whether a cell of this kind may stand in the shell (everything but {@link #OTHER}). */
		public boolean isShell() {
			return this != OTHER;
		}
	}

	/** Reads the world for {@link #scan}. Coordinates are absolute block positions. */
	@FunctionalInterface
	public interface ShellProbe {
		/** @return what stands at the position; never {@code null} — use {@link ShellKind#OTHER}. */
		ShellKind kindAt(int x, int y, int z);
	}

	/**
	 * Why the room is (not) formed. Every non-{@link #FORMED} constant carries a position in the
	 * {@link Result} so the controller can name it and spawn particles there.
	 */
	public enum Status {
		/** The room is a sealed box within the size limits, with a controller; a door is optional. */
		FORMED,
		/**
		 * The block behind the controller's face is itself a shell block, so the controller is not
		 * looking into an interior — it sits in the floor, the ceiling, an edge, or faces the wrong way.
		 */
		CONTROLLER_NOT_IN_WALL,
		/** No ray in some direction found shell within {@code maxInner} + 1 steps. */
		ROOM_UNBOUNDED,
		/** Interior edge below {@code minInner} along some axis. */
		TOO_SMALL,
		/** Interior edge above {@code maxInner} along some axis. */
		TOO_LARGE,
		/** A perimeter cell is not a shell block — the first hole is at the reported position. */
		BREACH,
		/**
		 * A door exists but no doorway does: no wall column carries door cells at both the floor level
		 * and the level above it. A door lying in the floor or ceiling lands here too.
		 */
		NO_DOORWAY,
		/** A second controller stands in the shell; a room has exactly one brain. */
		SECOND_CONTROLLER,
		/**
		 * More of the shell is glass than the structure tolerates. Glass is shell, but a room walled
		 * mostly in windows is a viewing gallery, not containment — the cap is what keeps the cheap,
		 * pretty option from being the strictly better one.
		 */
		TOO_MUCH_GLASS
	}

	/**
	 * Outcome of a scan. On {@link Status#FORMED} the interior box is the answer; on every other
	 * status {@code x/y/z} points at the offending block and the box holds whatever was measured so
	 * far (useful for "12 × 4 × 7 — too large along X").
	 *
	 * @param holeCount on {@link Status#BREACH}, every hole the walk found; zero otherwise
	 * @param holes     the first {@link #MAX_LISTED_HOLES} of them as {@code x, y, z} triples, in walk order
	 */
	public record Result(
			Status status,
			int x, int y, int z,
			int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ,
			int holeCount, int[] holes) {

		/** A verdict with no holes. */
		public Result(Status status, int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY,
				int maxZ) {
			this(status, x, y, z, minX, minY, minZ, maxX, maxY, maxZ, 0, NO_HOLES);
		}

		public boolean formed() {
			return status == Status.FORMED;
		}

		/** Interior extent along X, in blocks. */
		public int sizeX() {
			return maxX - minX + 1;
		}

		/** Interior extent along Y, in blocks. */
		public int sizeY() {
			return maxY - minY + 1;
		}

		/** Interior extent along Z, in blocks. */
		public int sizeZ() {
			return maxZ - minZ + 1;
		}

		/** How many holes are listed by position — at most {@link #MAX_LISTED_HOLES}. */
		public int listedHoles() {
			return holes.length / 3;
		}

		public int holeX(int index) {
			return holes[3 * index];
		}

		public int holeY(int index) {
			return holes[3 * index + 1];
		}

		public int holeZ(int index) {
			return holes[3 * index + 2];
		}

		/**
		 * A verdict reached before the rays measured anything: the box is left empty ({@code max < min}). Every
		 * verdict found once they have — a size, a hole, a misplaced controller or door, the glass — carries the
		 * box it was found in, so a screen can draw the walls around the fault (MOD-619).
		 */
		private static Result failure(Status status, int x, int y, int z) {
			return new Result(status, x, y, z, 0, 0, 0, -1, -1, -1);
		}
	}

	/**
	 * Scans the room a controller belongs to.
	 *
	 * @param probe    reads block kinds from the world
	 * @param cx       controller position
	 * @param cy       controller position
	 * @param cz       controller position
	 * @param inX      unit step from the controller towards the room interior — the opposite of the
	 *                 controller's facing (the face carrying the screen looks outwards)
	 * @param inY      see {@code inX}
	 * @param inZ      see {@code inX}
	 * @param minInner smallest legal interior edge, in blocks
	 * @param maxInner largest legal interior edge, in blocks
	 */
	public static Result scan(ShellProbe probe, int cx, int cy, int cz,
			int inX, int inY, int inZ, int minInner, int maxInner) {
		return scan(probe, cx, cy, cz, inX, inY, inZ, minInner, maxInner, DEFAULT_MAX_GLASS_PERCENT);
	}

	/** As {@link #scan(ShellProbe, int, int, int, int, int, int, int, int)}, with the glass cap given. */
	public static Result scan(ShellProbe probe, int cx, int cy, int cz,
			int inX, int inY, int inZ, int minInner, int maxInner, int maxGlassPercent) {
		int seedX = cx + inX;
		int seedY = cy + inY;
		int seedZ = cz + inZ;

		// A controller in a wall looks at open interior. Anything shell-shaped behind its face means it
		// is buried, sits in an edge, or was placed facing the wrong way.
		if (probe.kindAt(seedX, seedY, seedZ).isShell()) {
			return Result.failure(Status.CONTROLLER_NOT_IN_WALL, seedX, seedY, seedZ);
		}

		// Phase A — the six walls. Rays reach one block PAST the limit on purpose: a ray capped at exactly
		// maxInner can never see the far wall of a room that is one block too big, so every oversized room
		// would report ROOM_UNBOUNDED and TOO_LARGE would be dead code. With the extra step, "you built it one
		// too wide" and "there is no wall there" stay distinct.
		int reach = maxInner + 1;
		int minX = wall(probe, seedX, seedY, seedZ, -1, 0, 0, reach);
		if (minX == RAY_MISS) {
			return Result.failure(Status.ROOM_UNBOUNDED, seedX - reach, seedY, seedZ);
		}
		int maxX = wall(probe, seedX, seedY, seedZ, 1, 0, 0, reach);
		if (maxX == RAY_MISS) {
			return Result.failure(Status.ROOM_UNBOUNDED, seedX + reach, seedY, seedZ);
		}
		int minY = wall(probe, seedX, seedY, seedZ, 0, -1, 0, reach);
		if (minY == RAY_MISS) {
			return Result.failure(Status.ROOM_UNBOUNDED, seedX, seedY - reach, seedZ);
		}
		int maxY = wall(probe, seedX, seedY, seedZ, 0, 1, 0, reach);
		if (maxY == RAY_MISS) {
			return Result.failure(Status.ROOM_UNBOUNDED, seedX, seedY + reach, seedZ);
		}
		int minZ = wall(probe, seedX, seedY, seedZ, 0, 0, -1, reach);
		if (minZ == RAY_MISS) {
			return Result.failure(Status.ROOM_UNBOUNDED, seedX, seedY, seedZ - reach);
		}
		int maxZ = wall(probe, seedX, seedY, seedZ, 0, 0, 1, reach);
		if (maxZ == RAY_MISS) {
			return Result.failure(Status.ROOM_UNBOUNDED, seedX, seedY, seedZ + reach);
		}

		// Interior extents, derived from where the walls stand (the hit is the shell cell itself).
		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;

		int smallest = Math.min(sizeX, Math.min(sizeY, sizeZ));
		if (smallest < minInner) {
			return new Result(Status.TOO_SMALL, seedX, seedY, seedZ, minX, minY, minZ, maxX, maxY, maxZ);
		}
		int largest = Math.max(sizeX, Math.max(sizeY, sizeZ));
		if (largest > maxInner) {
			return new Result(Status.TOO_LARGE, seedX, seedY, seedZ, minX, minY, minZ, maxX, maxY, maxZ);
		}

		// Phase B — the whole perimeter. Also counts controllers and remembers door cells for the
		// doorway test; one walk answers all three questions.
		boolean selfSeen = false;
		int foreignControllerX = 0;
		int foreignControllerY = 0;
		int foreignControllerZ = 0;
		boolean foreignController = false;
		boolean anyDoor = false;
		boolean doorway = false;
		int shellCells = 0;
		int glassCells = 0;
		int firstGlassX = 0;
		int firstGlassY = 0;
		int firstGlassZ = 0;
		int holeCount = 0;
		int[] holes = new int[3 * MAX_LISTED_HOLES];

		for (int y = minY - 1; y <= maxY + 1; y++) {
			for (int z = minZ - 1; z <= maxZ + 1; z++) {
				for (int x = minX - 1; x <= maxX + 1; x++) {
					int outside = 0;
					if (x < minX || x > maxX) {
						outside++;
					}
					if (y < minY || y > maxY) {
						outside++;
					}
					if (z < minZ || z > maxZ) {
						outside++;
					}
					if (outside == 0) {
						continue; // interior — the player may build whatever they like in here
					}

					ShellKind kind = probe.kindAt(x, y, z);
					if (!kind.isShell()) {
						if (holeCount < MAX_LISTED_HOLES) {
							holes[3 * holeCount] = x;
							holes[3 * holeCount + 1] = y;
							holes[3 * holeCount + 2] = z;
						}
						holeCount++;
						continue;
					}
					if (holeCount > 0) {
						continue; // the shell leaks: the rest of the walk only looks for more holes
					}
					shellCells++;
					if (kind == ShellKind.GLASS) {
						if (glassCells == 0) {
							firstGlassX = x;
							firstGlassY = y;
							firstGlassZ = z;
						}
						glassCells++;
					}
					// Edges and corners hold the box together but are not "walls": a controller or door
					// there would have no interior to face. They only have to be *some* shell block.
					if (outside > 1) {
						continue;
					}
					if (kind == ShellKind.CONTROLLER) {
						if (x == cx && y == cy && z == cz) {
							// A vertical wall only: the controller is a panel the player reads and wires,
							// so the floor and the ceiling are out even though the geometry would allow them.
							if (y < minY || y > maxY) {
								return new Result(Status.CONTROLLER_NOT_IN_WALL, cx, cy, cz,
										minX, minY, minZ, maxX, maxY, maxZ);
							}
							selfSeen = true;
						} else {
							foreignController = true;
							foreignControllerX = x;
							foreignControllerY = y;
							foreignControllerZ = z;
						}
					} else if (kind == ShellKind.DOOR) {
						anyDoor = true;
						// A doorway is two door cells stacked at floor level, in a vertical wall.
						// Doors lying in the floor or ceiling (outside on Y) never satisfy this.
						if (y == minY && probe.kindAt(x, y + 1, z) == ShellKind.DOOR) {
							doorway = true;
						}
					}
				}
			}
		}

		if (holeCount > 0) {
			return new Result(Status.BREACH, holes[0], holes[1], holes[2], minX, minY, minZ, maxX, maxY, maxZ,
					holeCount, Arrays.copyOf(holes, 3 * Math.min(holeCount, MAX_LISTED_HOLES)));
		}
		// The scanning controller must itself have been walked as a *wall* cell. If it was not, it sits
		// in an edge or a corner: the seed happened to be open (it was simply outside the room), the
		// rays found a box, but this controller is not part of any of its walls.
		if (!selfSeen) {
			return new Result(Status.CONTROLLER_NOT_IN_WALL, cx, cy, cz, minX, minY, minZ, maxX, maxY, maxZ);
		}
		if (foreignController) {
			return new Result(Status.SECOND_CONTROLLER,
					foreignControllerX, foreignControllerY, foreignControllerZ, minX, minY, minZ, maxX, maxY, maxZ);
		}
		// A door is OPTIONAL (player request, 2026-08-20). Walling yourself in and mining back out is a
		// legitimate way to run a reactor — cheaper than an airlock, and the player who chooses it has
		// already accepted the inconvenience. The shell only has to be SEALED; how you get inside is
		// your business. A door that IS present still has to form a real doorway, because a door lying
		// in the floor is a mistake rather than a choice.
		if (anyDoor && !doorway) {
			return new Result(Status.NO_DOORWAY, cx, cy, cz, minX, minY, minZ, maxX, maxY, maxZ);
		}
		// Integer arithmetic on purpose: glassCells * 100 cannot overflow for a shell of at most 1016
		// cells, and comparing scaled integers avoids a floating-point boundary that would make the cap
		// behave differently on the two sides of an exact percentage.
		if (shellCells > 0 && glassCells * 100 > maxGlassPercent * shellCells) {
			return new Result(Status.TOO_MUCH_GLASS, firstGlassX, firstGlassY, firstGlassZ,
					minX, minY, minZ, maxX, maxY, maxZ);
		}

		return new Result(Status.FORMED, cx, cy, cz, minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** Convenience overload using {@link #DEFAULT_MIN_INNER}/{@link #DEFAULT_MAX_INNER}. */
	public static Result scan(ShellProbe probe, int cx, int cy, int cz, int inX, int inY, int inZ) {
		return scan(probe, cx, cy, cz, inX, inY, inZ, DEFAULT_MIN_INNER, DEFAULT_MAX_INNER);
	}

	/** Returned by {@link #castRay} and {@link #wall} when no shell block stands within reach. */
	private static final int RAY_MISS = Integer.MIN_VALUE;

	/**
	 * The interior bound in one direction, by a vote of up to nine parallel rays: the seed's own and those of
	 * the eight cells around it in the plane across the direction. A neighbour that is itself a shell block —
	 * the controller's wall, the floor under the seed — has no room to cast from and does not vote.
	 *
	 * @return the bound most rays agree on (a tie goes to the seed's own ray, then to the nearer wall), or
	 * 		{@link #RAY_MISS} if no ray reached shell
	 */
	private static int wall(ShellProbe probe, int x, int y, int z, int dx, int dy, int dz, int reach) {
		int[] hits = new int[9];
		int count = 0;
		int seedHit = RAY_MISS;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				// The two offsets span the plane the ray does not travel along.
				int sx = x + (dx != 0 ? 0 : a);
				int sy = y + (dy != 0 ? 0 : dx != 0 ? a : b);
				int sz = z + (dz != 0 ? 0 : b);
				boolean seed = a == 0 && b == 0;
				if (!seed && probe.kindAt(sx, sy, sz).isShell()) {
					continue;
				}
				int hit = castRay(probe, sx, sy, sz, dx, dy, dz, reach);
				if (seed) {
					seedHit = hit;
				}
				if (hit != RAY_MISS) {
					hits[count++] = hit;
				}
			}
		}
		int direction = dx + dy + dz;
		int best = RAY_MISS;
		int bestVotes = 0;
		for (int i = 0; i < count; i++) {
			int votes = 0;
			for (int j = 0; j < count; j++) {
				if (hits[j] == hits[i]) {
					votes++;
				}
			}
			boolean tieWon = votes == bestVotes && hits[i] != best && best != seedHit
					&& (hits[i] == seedHit || (direction > 0 ? hits[i] < best : hits[i] > best));
			if (votes > bestVotes || tieWon) {
				best = hits[i];
				bestVotes = votes;
			}
		}
		return best;
	}

	/**
	 * Steps from the seed until a shell block is hit, at most {@code maxSteps} times.
	 *
	 * @return the interior coordinate along the ray's axis of the last open cell before the shell —
	 * 		that is, the interior bound on that side — or {@link #RAY_MISS} if nothing was hit
	 */
	private static int castRay(ShellProbe probe, int x, int y, int z,
			int dx, int dy, int dz, int maxSteps) {
		int cx = x;
		int cy = y;
		int cz = z;
		for (int step = 0; step < maxSteps; step++) {
			int nx = cx + dx;
			int ny = cy + dy;
			int nz = cz + dz;
			if (probe.kindAt(nx, ny, nz).isShell()) {
				return dx != 0 ? cx : dy != 0 ? cy : cz;
			}
			cx = nx;
			cy = ny;
			cz = nz;
		}
		return RAY_MISS;
	}
}
