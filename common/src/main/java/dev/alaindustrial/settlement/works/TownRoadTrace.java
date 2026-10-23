package dev.alaindustrial.settlement.works;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The course of a town road (MOD-861; epic MOD-824, decision R114; owner's round 1 of 0.1.219, point R1-4): the cells
 * a road takes between two points of the town, round the town's buildings, each with the height its road block stands
 * at.
 *
 * <p><b>Walkable.</b> Two neighbouring cells of a course differ in height by one block at most, so people walk and
 * hop along it. Where the ground does more, the road cuts into it (at most {@link #MAX_CUT} blocks) or rises over it
 * on a fill (at most {@link #MAX_FILL}); earth moved costs {@link #EARTH_COST} steps a block, so the road keeps to the
 * ground where it can and goes round a hill when that is shorter than cutting through it. A column the survey refuses
 * — a building, a tree, water, something a player put — is never crossed.
 *
 * <p><b>Straight where it can.</b> The shortest walk over the allowed (column, height) states, a turn costing
 * {@link #TURN_COST} steps: a town's roads come out straight with few turns. The search stays in the box of its ends
 * widened by {@link #MARGIN}.
 *
 * <p><b>Level where it can (owner's round 3 of 0.1.219, R3-5).</b> A step up or down costs {@link #SLOPE_COST}, more
 * than a block of earth moved: a road keeps one level over a bump or a dip a few columns long — cut through the bump,
 * filled over the dip — and changes height only where the ground keeps the new level. On paving the road block takes
 * the paving block's place, flush with the street round it.
 *
 * <p><b>Flush with a doorstep.</b> A building's inlet sits one block over its floor, and the road block in front of it
 * stands on a road at floor level that wires it up from below (a riser, {@code TownRoadBlock}). So the column next to
 * an inlet's front ({@code risers}) is best one block lower than the front: the road meets the door level with the
 * floor. A road at the front's own height there is a step up at the door and costs a slope.
 *
 * <p><b>To a network.</b> {@link #routeToAny} goes from one cell to the nearest of many — any cell of a town's road
 * network ({@link TownRoadPlan}), each at its own height: the search ends on the first it reaches, and a column right
 * next to one of them costs {@link #NEAR_NETWORK_COST} more, so a new street meets the network instead of running
 * along it. A paved column — an old street of the town ({@link Ground#paved()}) — costs half a step (round 2 of
 * 0.1.219, R2-7): a road follows the town's old streets rather than winding between their slabs.
 *
 * <p>Minecraft-free on purpose, so the L1 lane can read it: a cell is three ints, the ground a function of two.
 */
public final class TownRoadTrace {

	/** How far around the box of its ends a road may go round a building or a hill. */
	public static final int MARGIN = 12;
	/** What a turn costs, in steps: a road bends only where it has to. */
	public static final int TURN_COST = 4;
	/** What a step up or down costs, in steps: dearer than a block of earth moved, so a road keeps level. */
	public static final int SLOPE_COST = 5;
	/** What one block of earth cut away or filled in costs, in steps. */
	public static final int EARTH_COST = 2;
	/** The deepest a road cuts into the ground, in blocks. */
	public static final int MAX_CUT = 3;
	/** The highest a road rises over the ground on a fill, in blocks. */
	public static final int MAX_FILL = 3;
	/** The longest course the search accepts, in columns. */
	public static final int MAX_COLUMNS = 512;
	/** What a column right next to a network cell costs on top, in steps: no two streets side by side. */
	public static final int NEAR_NETWORK_COST = 6;
	/**
	 * What stepping onto the cell in front of another building's inlet costs on top, in steps: a street does not run
	 * over a neighbour's doorstep, nor meet the network there, when the main street or another street is not far off.
	 */
	public static final int THRESHOLD_COST = 12;
	/** The widest search box, in columns along either axis: a network further out is not looked for. */
	public static final int MAX_BOX = 256;

	/** Heights a road may take in one column, counted from {@code MAX_CUT} below the ground's top. */
	private static final int SPAN = MAX_CUT + MAX_FILL + 1;
	/** The search counts in half steps: a paved column costs one, any other column two. */
	private static final int PAVED_STEP = 1;
	private static final int PLAIN_STEP = 2;
	private static final int UNIT = PLAIN_STEP;

	/** One column of the map. */
	public record Column(int x, int z) {
	}

	/** One cell of a course: where its road block stands. */
	public record Cell(int x, int y, int z) {
		public Column column() {
			return new Column(x, z);
		}
	}

	/**
	 * What one column offers a road.
	 *
	 * @param top     the ground's top block, which a road on the ground replaces
	 * @param lowest  the lowest height a road block may take there (a cut down to it is possible)
	 * @param highest the highest height a road block may take there (a fill up to it is possible)
	 * @param paved   whether the top is an old street of the town: half the price of a step
	 */
	public record Ground(int top, int lowest, int highest, boolean paved) {

		/** Unpaved ground. */
		public Ground(int top, int lowest, int highest) {
			this(top, lowest, highest, false);
		}
	}

	/** The ground of a column, or {@code null} when a road may not cross it. */
	@FunctionalInterface
	public interface Survey {
		@Nullable Ground at(int x, int z);
	}

	private TownRoadTrace() {
	}

	/**
	 * The cells from {@code start} to {@code end}, both included, each next to the one before and at most one block
	 * higher or lower; empty when no course within the search box keeps to that. The two ends are never surveyed: they
	 * stand in front of an inlet, at the inlet's height.
	 */
	public static Optional<List<Cell>> route(Cell start, Cell end, Survey survey) {
		return search(start, Map.of(end.column(), end.y()), survey, false, Set.of());
	}

	/**
	 * The cells from {@code start} to the cheapest-to-reach cell of {@code network} (a column and the height of its
	 * road block), both included, with the same steps as {@link #route}; empty when none is in reach. A column next to
	 * a network cell costs {@link #NEAR_NETWORK_COST} more. A {@code start} already in the network is a course of one.
	 */
	public static Optional<List<Cell>> routeToAny(Cell start, Map<Column, Integer> network, Survey survey) {
		return routeToAny(start, network, survey, Set.of());
	}

	/**
	 * {@link #routeToAny(Cell, Map, Survey)} where every column of {@code thresholds} but the start's — the cells in
	 * front of other buildings' inlets — costs {@link #THRESHOLD_COST} more to step on, network cell or not: dearer,
	 * never barred. The start and any network cell that is such a column is an inlet's front: the road next to it is
	 * best one block lower, level with the building's floor.
	 */
	public static Optional<List<Cell>> routeToAny(Cell start, Map<Column, Integer> network, Survey survey,
			Set<Column> thresholds) {
		if (network.isEmpty()) {
			return Optional.empty();
		}
		Integer here = network.get(start.column());
		if (here != null) {
			return Optional.of(List.of(new Cell(start.x(), here, start.z())));
		}
		return search(start, network, survey, true, thresholds);
	}

	private static Optional<List<Cell>> search(Cell start, Map<Column, Integer> targets, Survey survey,
			boolean nearPenalty, Set<Column> thresholds) {
		Optional<Box> fits = Box.around(start.column(), targets.keySet());
		if (fits.isEmpty()) {
			return Optional.empty();
		}
		Box box = fits.get();
		Grounds grounds = new Grounds(box, survey, start, targets, nearPenalty, thresholds);
		int states = box.width() * box.depth() * SPAN * 4;
		int[] from = new int[states];
		int found = dijkstra(box, grounds, box.cell(start.column()), from);
		if (found < 0) {
			return Optional.empty();
		}
		ArrayDeque<Cell> back = new ArrayDeque<>();
		for (int state = found; state >= 0; state = from[state]) {
			int cell = state / (SPAN * 4);
			int k = state / 4 % SPAN;
			Column c = box.column(cell);
			back.push(new Cell(c.x(), grounds.top(cell) + k - MAX_CUT, c.z()));
		}
		List<Cell> course = new ArrayList<>(back);
		return course.size() <= MAX_COLUMNS ? Optional.of(Collections.unmodifiableList(course)) : Optional.empty();
	}

	private static final int[] DX = {1, -1, 0, 0};
	private static final int[] DZ = {0, 0, 1, -1};

	/** The search box: the box of the ends widened by {@link #MARGIN}, columns numbered x-major. */
	private record Box(int minX, int minZ, int width, int depth) {
		static Optional<Box> around(Column start, Collection<Column> ends) {
			int minX = start.x();
			int maxX = start.x();
			int minZ = start.z();
			int maxZ = start.z();
			for (Column c : ends) {
				minX = Math.min(minX, c.x());
				maxX = Math.max(maxX, c.x());
				minZ = Math.min(minZ, c.z());
				maxZ = Math.max(maxZ, c.z());
			}
			int width = maxX - minX + 2 * MARGIN + 1;
			int depth = maxZ - minZ + 2 * MARGIN + 1;
			return width > MAX_BOX || depth > MAX_BOX ? Optional.empty()
					: Optional.of(new Box(minX - MARGIN, minZ - MARGIN, width, depth));
		}

		int cell(Column c) {
			return (c.x() - minX) * depth + (c.z() - minZ);
		}

		Column column(int cell) {
			return new Column(cell / depth + minX, cell % depth + minZ);
		}

		boolean contains(Column c) {
			return c.x() >= minX && c.x() < minX + width && c.z() >= minZ && c.z() < minZ + depth;
		}
	}

	/** The surveyed ground of the box's columns, asked once each and only when the search reaches them. */
	private static final class Grounds {
		private final Box box;
		private final Survey survey;
		private final boolean[] asked;
		private final int[] top;
		private final int[] lowK;
		private final int[] highK;
		private final int[] earth;
		private final int[] step;
		private final boolean[] target;
		private final boolean[] near;
		private final boolean[] threshold;
		/** An end in front of an inlet: its neighbour is best one block lower. */
		private final boolean[] riser;

		Grounds(Box box, Survey survey, Cell start, Map<Column, Integer> targets, boolean nearPenalty,
				Set<Column> thresholds) {
			this.box = box;
			this.survey = survey;
			int n = box.width() * box.depth();
			asked = new boolean[n];
			top = new int[n];
			lowK = new int[n];
			highK = new int[n];
			earth = new int[n];
			step = new int[n];
			target = new boolean[n];
			near = new boolean[n];
			threshold = new boolean[n];
			riser = new boolean[n];
			for (Column column : thresholds) {
				if (box.contains(column) && !column.equals(start.column())) {
					threshold[box.cell(column)] = true;
				}
			}
			fix(box.cell(start.column()), start.y());
			targets.forEach((column, y) -> {
				int cell = box.cell(column);
				fix(cell, y);
				target[cell] = true;
			});
			for (Column column : thresholds) {
				if (box.contains(column) && (column.equals(start.column()) || targets.containsKey(column))) {
					riser[box.cell(column)] = true;
				}
			}
			if (nearPenalty) {
				for (Column column : targets.keySet()) {
					for (int d = 0; d < 4; d++) {
						Column side = new Column(column.x() + DX[d], column.z() + DZ[d]);
						if (box.contains(side)) {
							near[box.cell(side)] = true;
						}
					}
				}
			}
		}

		/** An end: one height, its own, with no earthwork counted. */
		private void fix(int cell, int y) {
			asked[cell] = true;
			top[cell] = y;
			lowK[cell] = MAX_CUT;
			highK[cell] = MAX_CUT;
			earth[cell] = 0;
			step[cell] = PLAIN_STEP;
		}

		private void ask(int cell) {
			if (asked[cell]) {
				return;
			}
			asked[cell] = true;
			Column c = box.column(cell);
			Ground ground = survey.at(c.x(), c.z());
			if (ground == null) {
				lowK[cell] = 1;
				highK[cell] = 0; // no height at all
				return;
			}
			top[cell] = ground.top();
			lowK[cell] = Math.max(0, ground.lowest() - ground.top() + MAX_CUT);
			highK[cell] = Math.min(SPAN - 1, ground.highest() - ground.top() + MAX_CUT);
			earth[cell] = EARTH_COST * UNIT;
			step[cell] = ground.paved() ? PAVED_STEP : PLAIN_STEP;
		}

		int top(int cell) {
			ask(cell);
			return top[cell];
		}

		int lowK(int cell) {
			ask(cell);
			return lowK[cell];
		}

		int highK(int cell) {
			ask(cell);
			return highK[cell];
		}

		/** What a road at height step {@code k} of {@code cell} costs in earth moved. */
		int earthwork(int cell, int k) {
			ask(cell);
			return Math.abs(k - MAX_CUT) * earth[cell];
		}

		/** What stepping onto {@code cell} costs, before turns, slopes and earth. */
		int step(int cell) {
			ask(cell);
			return step[cell] + (near[cell] && !target[cell] ? NEAR_NETWORK_COST * UNIT : 0)
					+ (threshold[cell] ? THRESHOLD_COST * UNIT : 0);
		}

		boolean target(int cell) {
			return target[cell];
		}

		/**
		 * What going from {@code cell} at height {@code y} to {@code next} at {@code ny} costs in slope: a change of
		 * height, or — next to an inlet's front — not stepping down to the floor's level.
		 */
		int slope(int cell, int y, int next, int ny) {
			int wanted = (riser[cell] ? -1 : 0) + (riser[next] ? 1 : 0);
			return Math.abs(ny - y - wanted) * SLOPE_COST * UNIT;
		}
	}

	/**
	 * Dijkstra over (column, height, heading): the cheapest state on a target column, or -1; {@code from} gets each
	 * state's predecessor, -1 at the start. A state is {@code ((cell * SPAN) + k) * 4 + heading}.
	 */
	private static int dijkstra(Box box, Grounds grounds, int startCell, int[] from) {
		int[] cost = new int[from.length];
		Arrays.fill(cost, Integer.MAX_VALUE);
		PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0])
				: Integer.compare(a[1], b[1]));
		for (int d = 0; d < 4; d++) {
			int state = (startCell * SPAN + MAX_CUT) * 4 + d;
			cost[state] = 0;
			from[state] = -1;
			open.add(new int[] {0, state});
		}
		while (!open.isEmpty()) {
			int[] head = open.poll();
			int state = head[1];
			if (head[0] > cost[state]) {
				continue;
			}
			int cell = state / (SPAN * 4);
			int k = state / 4 % SPAN;
			int heading = state % 4;
			if (grounds.target(cell)) {
				return state;
			}
			int y = grounds.top(cell) + k - MAX_CUT;
			for (int d = 0; d < 4; d++) {
				int nx = cell / box.depth() + DX[d];
				int nz = cell % box.depth() + DZ[d];
				if (nx < 0 || nz < 0 || nx >= box.width() || nz >= box.depth()) {
					continue;
				}
				int next = nx * box.depth() + nz;
				int turn = cell != startCell && d != heading ? TURN_COST * UNIT : 0;
				for (int nk = grounds.lowK(next); nk <= grounds.highK(next); nk++) {
					int ny = grounds.top(next) + nk - MAX_CUT;
					if (Math.abs(ny - y) > 1) {
						continue;
					}
					int step = head[0] + grounds.step(next) + turn + grounds.earthwork(next, nk)
							+ grounds.slope(cell, y, next, ny);
					int nextState = (next * SPAN + nk) * 4 + d;
					if (step < cost[nextState]) {
						cost[nextState] = step;
						from[nextState] = state;
						open.add(new int[] {step, nextState});
					}
				}
			}
		}
		return -1;
	}
}
