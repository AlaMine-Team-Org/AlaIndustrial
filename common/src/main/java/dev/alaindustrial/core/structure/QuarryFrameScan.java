package dev.alaindustrial.core.structure;

// size-justified: one shape and its seven rules — the kinds, the limits, the result and the walks the rules share;
// split, the tower walk, the path walk and the bridge walk would each need the frame's corners passed around again.
/**
 * MC-free geometry of the quarry's portal frame (MOD-950, stage A of MOD-637): four massive towers, two rail paths,
 * a bridge across them and a cart on the bridge. Like {@link RoomScan} it names no Minecraft type, which is what makes
 * every rule an L1 JUnit target; the world binding lives outside {@code core} (ADR-039).
 *
 * <p><b>Why not {@link RoomScan} or {@link RoomFill}.</b> Both look for an enclosed volume. A portal frame is open on
 * every side and has no floor — the pit is dug under it — so neither can see it; the frame is found by following its
 * own parts instead.
 *
 * <p><b>The shape</b> (spec {@code docs/blocks/mining/quarry.md}, owner round 7 of MOD-681). A tower stands on the
 * designer's {@value #TOWER} × {@value #TOWER} footprint: two layers of foundations, four layers of scaffolding and
 * {@value #HEAD_LAYERS} layers of heads, each over the whole footprint — the blocks fill the height of the designer's
 * model, which hides them once the frame forms (owner, 2026-11-04: a frame as tall as the model, so breaking what the
 * player sees breaks the frame). The four towers stand at one height in the corners of a rectangle; one cell of the
 * bottom head layer of one tower, any but the middle one, is the controller. Two paths of rail beams, as wide as a head
 * and two layers tall, run between the heads along one axis, each beam lying along its path. The bridge is one row of
 * bridge beams in the upper path layer, lying across from path to path between them, and its two saddles are
 * {@value #SADDLE_CELLS} more beams of the same row one layer up at each end — over the whole path and one cell into
 * the pit (owner, 2026-11-05: the designer's saddles stand on blocks too); the cart stands on the bridge between them.
 * The <em>span</em> is the number of cells between two towers along an axis — the width of the pit.
 *
 * <p><b>Order of checks is a UX decision</b>, the same as the reactor room's: the controller's own tower, the other
 * towers, size, paths, bridge, cart, a rival controller. Each answer is one thing to fix, and the player fixes them
 * one at a time instead of being handed a list.
 */
public final class QuarryFrameScan {

	/** A tower's footprint edge, in cells. */
	public static final int TOWER = 3;

	/** Layers of foundations at the bottom of a tower. */
	public static final int FOUNDATION_LAYERS = 2;

	/** Layers of scaffolding between the foundations and the head. */
	public static final int SCAFFOLD_LAYERS = 4;

	/** How far above the bottom foundation layer the bottom head layer — the controller's — is. */
	public static final int HEAD_HEIGHT = FOUNDATION_LAYERS + SCAFFOLD_LAYERS;

	/** Layers of heads at the top of a tower, as tall as the designer's head. */
	public static final int HEAD_LAYERS = 3;

	/** Layers of rail beams in a path, as tall as the designer's path. */
	public static final int PATH_LAYERS = 2;

	/** Bridge beams of one saddle: over the path's {@value #TOWER} rows and one cell into the pit. */
	public static final int SADDLE_CELLS = TOWER + 1;

	/** Smallest span, in cells between two towers, the spec allows on either axis. */
	public static final int DEFAULT_MIN_SPAN = 5;


	private QuarryFrameScan() {
	}

	/**
	 * What the world holds at one position, from the frame's point of view. A beam's axis is part of its kind, because
	 * a beam lying across its path is a gap in that path. Everything that is no part of a frame — air, stone, a
	 * machine — is {@link #OTHER}.
	 */
	public enum Kind {
		FOUNDATION_1,
		FOUNDATION_2,
		FOUNDATION_3,
		SCAFFOLD,
		HEAD,
		CONTROLLER,
		RAIL_X,
		RAIL_Z,
		BRIDGE_X,
		BRIDGE_Z,
		CART,
		OTHER;

		/** The foundation's tier, 1 to 3, or 0 for anything that is not a foundation. */
		public int foundationTier() {
			return switch (this) {
				case FOUNDATION_1 -> 1;
				case FOUNDATION_2 -> 2;
				case FOUNDATION_3 -> 3;
				default -> 0;
			};
		}

		/** Whether a head layer may hold this block — a plain head or the controller. */
		public boolean isHead() {
			return this == HEAD || this == CONTROLLER;
		}

		boolean isBridge() {
			return this == BRIDGE_X || this == BRIDGE_Z;
		}
	}

	/** Reads the world for {@link #scan}. Coordinates are absolute block positions. */
	@FunctionalInterface
	public interface Probe {
		/** @return what stands at the position; never {@code null} — use {@link Kind#OTHER}. */
		Kind kindAt(int x, int y, int z);
	}

	/**
	 * The size rules: the smallest span, and the largest one each foundation tier allows.
	 *
	 * @param maxSpanByTier the largest span for tiers 1, 2 and 3, in that order
	 */
	public record Limits(int minSpan, int[] maxSpanByTier) {
		public Limits {
			if (maxSpanByTier.length != 3) {
				throw new IllegalArgumentException("three tiers, got " + maxSpanByTier.length);
			}
			maxSpanByTier = maxSpanByTier.clone();
		}

		/** The largest span a frame of this tier may have. */
		public int maxSpan(int tier) {
			return maxSpanByTier[Math.clamp(tier, 1, 3) - 1];
		}

		/** How far past a tower another tower is looked for: one past the largest span of any tier. */
		int reach() {
			int widest = 0;
			for (int span : maxSpanByTier) {
				widest = Math.max(widest, span);
			}
			return widest + 1;
		}
	}

	/** Why the frame is (not) formed. Every non-{@link #FORMED} constant carries a position in the {@link Result}. */
	public enum Status {
		/** No scan has run yet — the controller was just placed or loaded. Never returned by {@link #scan}. */
		UNSCANNED,
		/** Four towers, two whole paths, one bridge, one cart, one controller, within the size limits. */
		FORMED,
		/**
		 * The controller is not in the bottom head layer of a whole tower: the reported cell is the first one of its
		 * tower — head, scaffolding or foundation — that is missing or wrong, or the controller itself when a head
		 * stands under it.
		 */
		CONTROLLER_NOT_ON_TOWER,
		/** The controller is the middle cell of its head, where no cable can reach the socket under it. */
		CONTROLLER_NOT_ON_EDGE,
		/**
		 * The other towers were not found: no head stands within reach on both axes, or the corner opposite the
		 * controller's tower is empty. The position is that corner when it is known, the controller otherwise.
		 */
		TOWERS_NOT_FOUND,
		/** One of the other three towers is incomplete; the reported cell is its first missing or wrong block. */
		TOWER_INCOMPLETE,
		/** A span is below the minimum. */
		TOO_SMALL,
		/** A span is above what the weakest foundation allows. */
		TOO_LARGE,
		/** A path between two heads has a gap, a foreign block or a beam lying across it at the reported cell. */
		PATH_BROKEN,
		/** No bridge beam lies on the paths. */
		NO_BRIDGE,
		/** The bridge row does not reach from path to path, or a beam in it lies along the paths. */
		BRIDGE_BROKEN,
		/** A second bridge lies on the paths. */
		SECOND_BRIDGE,
		/** No cart stands on the bridge. */
		NO_CART,
		/** A second cart stands on the bridge. */
		SECOND_CART,
		/** A second controller sits in one of the heads; a quarry has exactly one brain. */
		SECOND_CONTROLLER
	}

	/**
	 * Outcome of a scan. On {@link Status#FORMED} the bounds, spans and tier describe the frame; on every other status
	 * {@code x/y/z} points at the block to fix and the rest holds whatever was measured so far (useful for "19 × 9 —
	 * a tier 1 frame spans at most 9").
	 *
	 * @param tier        the weakest foundation's tier, 0 until all four towers were read
	 * @param limit       the largest span that tier allows, 0 until then
	 * @param spanX       cells between the towers along X, 0 until the towers were found
	 * @param spanZ       cells between the towers along Z, 0 until then
	 * @param minX        the lower X of the frame's outer bounds
	 * @param minZ        the lower Z of the frame's outer bounds
	 * @param maxX        the upper X of the frame's outer bounds
	 * @param maxZ        the upper Z of the frame's outer bounds
	 * @param pathsAlongX whether the paths run along X (the bridge then lies along Z)
	 * @param bridgeAt    on {@link Status#FORMED}, the bridge row's coordinate along the paths (X when they run
	 *                    along X); 0 otherwise
	 * @param cartAt      on {@link Status#FORMED}, the cart's coordinate across the paths; 0 otherwise
	 */
	public record Result(Status status, int x, int y, int z, int tier, int limit, int spanX, int spanZ,
			int minX, int minZ, int maxX, int maxZ, boolean pathsAlongX, int bridgeAt, int cartAt) {

		public boolean formed() {
			return status == Status.FORMED;
		}

		/** The placeholder a controller holds until its first scan. */
		public static Result unscanned() {
			return at(Status.UNSCANNED, 0, 0, 0);
		}

		static Result at(Status status, int x, int y, int z) {
			return new Result(status, x, y, z, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0);
		}
	}

	/** A cell, for the scan's internal answers: the first wrong block, or {@code null} when there is none. */
	private record Cell(int x, int y, int z) {
	}

	/**
	 * The four towers by their north-west corners: the controller's at {@code (x0, z0)}, its neighbour along X at
	 * {@code (ox, z0)}, along Z at {@code (x0, oz)}, the opposite one at {@code (ox, oz)}; {@code hy} is head height.
	 */
	private record Frame(int x0, int z0, int ox, int oz, int hy, int tier) {
		int lowX() {
			return Math.min(x0, ox) + TOWER;
		}

		int highX() {
			return Math.max(x0, ox) - 1;
		}

		int lowZ() {
			return Math.min(z0, oz) + TOWER;
		}

		int highZ() {
			return Math.max(z0, oz) - 1;
		}

		int spanX() {
			return highX() - lowX() + 1;
		}

		int spanZ() {
			return highZ() - lowZ() + 1;
		}

		int[][] towers() {
			return new int[][] {{x0, z0}, {ox, z0}, {x0, oz}, {ox, oz}};
		}

		Result result(Status status, int x, int y, int z, int limit, boolean alongX) {
			return formed(status, x, y, z, limit, alongX, 0, 0);
		}

		Result formed(Status status, int x, int y, int z, int limit, boolean alongX, int bridgeAt, int cartAt) {
			return new Result(status, x, y, z, tier, limit, spanX(), spanZ(),
					Math.min(x0, ox), Math.min(z0, oz), Math.max(x0, ox) + TOWER - 1, Math.max(z0, oz) + TOWER - 1,
					alongX, bridgeAt, cartAt);
		}

		Result result(Status status, Cell cell, int limit, boolean alongX) {
			return result(status, cell.x(), cell.y(), cell.z(), limit, alongX);
		}
	}

	/**
	 * Reads the frame around the controller at {@code (cx, cy, cz)} — a cell of its own tower's bottom head layer.
	 *
	 * <p>size-justified: the seven rules in the order the player is told about them, numbered in place; split
	 * across methods, the order that is the whole point of the class would have to be reassembled by the reader.
	 */
	public static Result scan(Probe probe, int cx, int cy, int cz, Limits limits) {
		// 1. The controller's own tower, and the controller on its edge.
		int x0 = headEdge(probe, cx, cy, cz, -1, 0);
		int z0 = headEdge(probe, cx, cy, cz, 0, -1);
		boolean wholeRow = headEdge(probe, cx, cy, cz, 1, 0) - x0 == TOWER - 1;
		boolean wholeColumn = headEdge(probe, cx, cy, cz, 0, 1) - z0 == TOWER - 1;
		if (!wholeRow || !wholeColumn || probe.kindAt(cx, cy - 1, cz).isHead()) {
			return Result.at(Status.CONTROLLER_NOT_ON_TOWER, cx, cy, cz);
		}
		Cell bad = towerFault(probe, x0, cy, z0);
		if (bad != null) {
			return Result.at(Status.CONTROLLER_NOT_ON_TOWER, bad.x(), bad.y(), bad.z());
		}
		boolean onEdge = cx == x0 || cx == x0 + TOWER - 1 || cz == z0 || cz == z0 + TOWER - 1;
		if (!onEdge) {
			return Result.at(Status.CONTROLLER_NOT_ON_EDGE, cx, cy, cz);
		}

		// 2. The other three towers: the nearest head along X and along Z, and the corner opposite.
		Frame frame = null;
		Cell missing = new Cell(cx, cy, cz);
		boolean knowsMissing = false;
		for (int[] pair : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
			Integer ox = neighbourTower(probe, x0, cy, z0, pair[0], 0, limits.reach());
			Integer oz = neighbourTower(probe, x0, cy, z0, 0, pair[1], limits.reach());
			if (ox == null || oz == null) {
				continue;
			}
			if (probe.kindAt(ox, cy, oz).isHead()) {
				frame = new Frame(x0, z0, ox, oz, cy, 0);
				break;
			}
			if (!knowsMissing) {
				missing = new Cell(ox, cy, oz);
				knowsMissing = true;
			}
		}
		if (frame == null) {
			return Result.at(Status.TOWERS_NOT_FOUND, missing.x(), missing.y(), missing.z());
		}
		int[][] towers = frame.towers();
		for (int i = 1; i < towers.length; i++) {
			Cell fault = towerFault(probe, towers[i][0], cy, towers[i][1]);
			if (fault != null) {
				return frame.result(Status.TOWER_INCOMPLETE, fault, 0, false);
			}
		}
		int tier = 3;
		for (int[] tower : towers) {
			tier = Math.min(tier, towerTier(probe, tower[0], cy, tower[1]));
		}
		frame = new Frame(frame.x0, frame.z0, frame.ox, frame.oz, cy, tier);

		// 3. Size: the smallest span first, then what the weakest foundation allows.
		int limit = limits.maxSpan(tier);
		if (Math.min(frame.spanX(), frame.spanZ()) < limits.minSpan()) {
			return frame.result(Status.TOO_SMALL, cx, cy, cz, limit, false);
		}
		if (Math.max(frame.spanX(), frame.spanZ()) > limit) {
			return frame.result(Status.TOO_LARGE, cx, cy, cz, limit, false);
		}

		// 4. The paths. Their axis is the one more of the cells between the heads already agree with, so a path with
		// one beam turned the wrong way is reported at that beam instead of as a frame with no paths at all.
		boolean alongX = railsAlong(probe, frame, true) >= railsAlong(probe, frame, false);
		Cell gap = firstPathGap(probe, frame, alongX);
		if (gap != null) {
			return frame.result(Status.PATH_BROKEN, gap, limit, alongX);
		}

		// 5. The bridge, and 6. the cart on it.
		Result bridgeAndCart = bridgeAndCart(probe, frame, alongX, limit);
		if (!bridgeAndCart.formed()) {
			return bridgeAndCart;
		}

		// 7. One brain.
		for (int[] tower : towers) {
			for (int dx = 0; dx < TOWER; dx++) {
				for (int dz = 0; dz < TOWER; dz++) {
					int x = tower[0] + dx;
					int z = tower[1] + dz;
					if ((x != cx || z != cz) && probe.kindAt(x, cy, z) == Kind.CONTROLLER) {
						return frame.result(Status.SECOND_CONTROLLER, x, cy, z, limit, alongX);
					}
				}
			}
		}
		return frame.formed(Status.FORMED, cx, cy, cz, limit, alongX, bridgeAndCart.bridgeAt(),
				bridgeAndCart.cartAt());
	}

	/**
	 * The last head cell from {@code (cx, cz)} in the direction {@code (sx, sz)}, at most {@link #TOWER} − 1 steps — so
	 * on a whole head the two edges along an axis are exactly {@code TOWER - 1} apart.
	 */
	private static int headEdge(Probe probe, int cx, int cy, int cz, int sx, int sz) {
		int steps = 0;
		while (steps < TOWER && probe.kindAt(cx + sx * (steps + 1), cy, cz + sz * (steps + 1)).isHead()) {
			steps++;
		}
		return sx != 0 ? cx + sx * steps : cz + sz * steps;
	}

	/**
	 * The first missing or wrong block of the tower whose bottom head layer starts at {@code (x0, hy, z0)}, top down:
	 * the head layers, then the scaffolding, then the two foundation layers; {@code null} for a whole tower.
	 */
	private static Cell towerFault(Probe probe, int x0, int hy, int z0) {
		for (int y = hy + HEAD_LAYERS - 1; y >= hy; y--) {
			for (int dz = 0; dz < TOWER; dz++) {
				for (int dx = 0; dx < TOWER; dx++) {
					if (!probe.kindAt(x0 + dx, y, z0 + dz).isHead()) {
						return new Cell(x0 + dx, y, z0 + dz);
					}
				}
			}
		}
		for (int layer = 1; layer <= SCAFFOLD_LAYERS; layer++) {
			for (int dz = 0; dz < TOWER; dz++) {
				for (int dx = 0; dx < TOWER; dx++) {
					if (probe.kindAt(x0 + dx, hy - layer, z0 + dz) != Kind.SCAFFOLD) {
						return new Cell(x0 + dx, hy - layer, z0 + dz);
					}
				}
			}
		}
		for (int layer = SCAFFOLD_LAYERS + 1; layer <= HEAD_HEIGHT; layer++) {
			for (int dz = 0; dz < TOWER; dz++) {
				for (int dx = 0; dx < TOWER; dx++) {
					if (probe.kindAt(x0 + dx, hy - layer, z0 + dz).foundationTier() == 0) {
						return new Cell(x0 + dx, hy - layer, z0 + dz);
					}
				}
			}
		}
		return null;
	}

	/** The weakest foundation of a whole tower. */
	private static int towerTier(Probe probe, int x0, int hy, int z0) {
		int tier = 3;
		for (int layer = SCAFFOLD_LAYERS + 1; layer <= HEAD_HEIGHT; layer++) {
			for (int dz = 0; dz < TOWER; dz++) {
				for (int dx = 0; dx < TOWER; dx++) {
					tier = Math.min(tier, probe.kindAt(x0 + dx, hy - layer, z0 + dz).foundationTier());
				}
			}
		}
		return tier;
	}

	/**
	 * The near corner coordinate (X for {@code sx}, Z for {@code sz}) of the next tower past the one at
	 * {@code (x0, z0)} in that direction, or {@code null} when no head stands within reach. Looked for along the
	 * tower's own edge row, at head height.
	 */
	private static Integer neighbourTower(Probe probe, int x0, int hy, int z0, int sx, int sz, int reach) {
		boolean ahead = sx > 0 || sz > 0;
		for (int step = 0; step < reach; step++) {
			int offset = ahead ? TOWER + step : -1 - step;
			int x = sx != 0 ? x0 + offset : x0;
			int z = sz != 0 ? z0 + offset : z0;
			if (probe.kindAt(x, hy, z).isHead()) {
				int edge = (sx != 0 ? x0 : z0) + offset;
				return ahead ? edge : edge - (TOWER - 1);
			}
		}
		return null;
	}

	/** The cells of both paths along X (or along Z), the controller's path first, as {@code {x, z}}. */
	private static int[][] pathCells(Frame frame, boolean alongX) {
		int low = alongX ? frame.lowX() : frame.lowZ();
		int high = alongX ? frame.highX() : frame.highZ();
		int near = alongX ? frame.z0 : frame.x0;
		int far = alongX ? frame.oz : frame.ox;
		int[] rows = pathRows(near, far);
		int[][] cells = new int[rows.length * Math.max(0, high - low + 1)][];
		int i = 0;
		for (int row : rows) {
			for (int t = low; t <= high; t++) {
				cells[i++] = alongX ? new int[] {t, row} : new int[] {row, t};
			}
		}
		return cells;
	}

	/** The rows a path covers, the near path's first: the whole width of the heads it joins. */
	private static int[] pathRows(int near, int far) {
		int[] rows = new int[2 * TOWER];
		for (int i = 0; i < TOWER; i++) {
			rows[i] = near + i;
			rows[TOWER + i] = far + i;
		}
		return rows;
	}

	/** How many cells of both paths already hold a beam lying along X (or along Z). */
	private static int railsAlong(Probe probe, Frame frame, boolean alongX) {
		Kind wanted = alongX ? Kind.RAIL_X : Kind.RAIL_Z;
		int count = 0;
		for (int[] cell : pathCells(frame, alongX)) {
			if (probe.kindAt(cell[0], frame.hy, cell[1]) == wanted) {
				count++;
			}
		}
		return count;
	}

	/** The first cell of either path, lower layer first, that is not a beam lying along the path, or {@code null}. */
	private static Cell firstPathGap(Probe probe, Frame frame, boolean alongX) {
		Kind wanted = alongX ? Kind.RAIL_X : Kind.RAIL_Z;
		for (int y = frame.hy; y < frame.hy + PATH_LAYERS; y++) {
			for (int[] cell : pathCells(frame, alongX)) {
				if (probe.kindAt(cell[0], y, cell[1]) != wanted) {
					return new Cell(cell[0], y, cell[1]);
				}
			}
		}
		return null;
	}

	/**
	 * Rules 5 and 6: exactly one bridge — a row of bridge beams in the upper path layer, lying across from the inner
	 * row of one path to the inner row of the other — and exactly one cart on it. When both hold, a
	 * {@link Status#FORMED} result carrying the bridge row and the cart's place; the first broken rule otherwise.
	 */
	private static Result bridgeAndCart(Probe probe, Frame frame, boolean alongX, int limit) {
		int low = alongX ? frame.lowX() : frame.lowZ();
		int high = alongX ? frame.highX() : frame.highZ();
		int acrossLow = Math.min(alongX ? frame.z0 : frame.x0, alongX ? frame.oz : frame.ox) + TOWER;
		int acrossHigh = Math.max(alongX ? frame.z0 : frame.x0, alongX ? frame.oz : frame.ox) - 1;
		int y = frame.hy + PATH_LAYERS - 1;
		int row = Integer.MIN_VALUE;
		for (int t = low; t <= high; t++) {
			Cell found = firstBridgeBetweenPaths(probe, alongX, t, y, acrossLow, acrossHigh);
			if (found == null) {
				continue;
			}
			if (row != Integer.MIN_VALUE) {
				return frame.result(Status.SECOND_BRIDGE, found, limit, alongX);
			}
			row = t;
		}
		if (row == Integer.MIN_VALUE) {
			return frame.result(Status.NO_BRIDGE, frame.x0, frame.hy, frame.z0, limit, alongX);
		}
		Kind wanted = alongX ? Kind.BRIDGE_Z : Kind.BRIDGE_X;
		for (int u = acrossLow; u <= acrossHigh; u++) {
			int x = alongX ? row : u;
			int z = alongX ? u : row;
			if (probe.kindAt(x, y, z) != wanted) {
				return frame.result(Status.BRIDGE_BROKEN, x, y, z, limit, alongX);
			}
		}
		// The saddles: the same row one layer up, over each path and one cell into the pit.
		for (int u = acrossLow - TOWER; u <= acrossHigh + TOWER; u++) {
			if (u > acrossLow && u < acrossHigh) {
				continue;
			}
			int x = alongX ? row : u;
			int z = alongX ? u : row;
			if (probe.kindAt(x, y + 1, z) != wanted) {
				return frame.result(Status.BRIDGE_BROKEN, x, y + 1, z, limit, alongX);
			}
		}
		int carts = 0;
		int cartAt = 0;
		for (int u = acrossLow + 1; u < acrossHigh; u++) {
			int x = alongX ? row : u;
			int z = alongX ? u : row;
			if (probe.kindAt(x, y + 1, z) == Kind.CART) {
				if (++carts == 2) {
					return frame.result(Status.SECOND_CART, x, y + 1, z, limit, alongX);
				}
				cartAt = u;
			}
		}
		if (carts == 0) {
			int middle = (acrossLow + acrossHigh) / 2;
			return frame.result(Status.NO_CART, alongX ? row : middle, y + 1, alongX ? middle : row, limit, alongX);
		}
		return frame.formed(Status.FORMED, frame.x0, frame.hy, frame.z0, limit, alongX, row, cartAt);
	}

	/** The first bridge beam between the paths at step {@code t} along them, or {@code null}. */
	private static Cell firstBridgeBetweenPaths(Probe probe, boolean alongX, int t, int y, int acrossLow,
			int acrossHigh) {
		for (int u = acrossLow; u <= acrossHigh; u++) {
			int x = alongX ? t : u;
			int z = alongX ? u : t;
			if (probe.kindAt(x, y, z).isBridge()) {
				return new Cell(x, y, z);
			}
		}
		return null;
	}
}
