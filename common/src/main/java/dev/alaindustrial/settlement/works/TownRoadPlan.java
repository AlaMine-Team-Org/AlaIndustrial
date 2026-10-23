package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.works.TownRoadTrace.Cell;
import dev.alaindustrial.settlement.works.TownRoadTrace.Column;
import dev.alaindustrial.settlement.works.TownRoadTrace.Ground;
import dev.alaindustrial.settlement.works.TownRoadTrace.Survey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The road network of one ALA town (round 2 of 0.1.219, R2-7; epic MOD-824, owner's answers Q7–Q12): one tree of road
 * cells rooted in front of the town hall's inlet, with a branch to the inlet of every other building — the town's
 * general plan of streets, laid by the drones bit by bit, each building's road job taking the cells between its inlet
 * and the hall that are not laid yet.
 *
 * <p><b>The shape.</b> A main street first: straight from the hall's inlet along the town's long axis (the way the
 * hall faces when that is along it; else a few blocks out in front of the hall and both ways along the axis), as far
 * as the farthest inlet reaches along it. Then every building is joined, the one nearest to the network first, by the
 * cheapest course from its inlet to <em>any</em> cell of the network ({@link TownRoadTrace#routeToAny}): it meets the
 * network at a T, keeps off the cells beside it, and follows old paved streets — Prim's greedy rule for a Steiner tree
 * (Takahashi–Matsuyama). Last, the dead ends that lead to no inlet are trimmed back. Every two neighbouring cells of
 * the tree differ by one block of height at most, as on any town road.
 *
 * <p><b>Laid stays.</b> A cell whose road is laid is marked {@link Node#built()}: trimming never takes it, and nothing
 * already in the tree is traced again — a new building only adds its branch (Q9). No rings (Q12): every cell has one
 * way to the hall.
 *
 * <p><b>What is not laid is only a plan (owner's round 3 of 0.1.219, R3-5).</b> The cells were traced on the ground of
 * their day: rubble a clearing has taken away since, or a block a player broke, would leave the road a block up in the
 * air. So the plan is {@link #stale()} after it is read from disk and whenever the town says its ground moved
 * ({@link #groundMoved()}), and the network then {@linkplain #retraceUnlaid() drops} every cell no laid one hangs
 * on: the next {@link #grow} traces those ways again on today's ground.
 *
 * <p>Minecraft-free on purpose, so the L1 lane can read it. Server thread only; every change calls the hook the town
 * installs.
 */
public final class TownRoadPlan {

	/** How far out in front of the hall a main street that runs across the hall's face starts, in blocks. */
	public static final int SETBACK = 3;
	/** The shortest main street worth laying, in blocks: a town smaller than that gets branches only. */
	public static final int MIN_MAIN_STREET = 4;

	/**
	 * One cell of the network.
	 *
	 * @param cell   where its road block stands
	 * @param parent the next cell towards the hall; {@code null} for the cell in front of the hall's inlet
	 * @param built  whether its road is laid: such a cell is never trimmed
	 */
	public record Node(Cell cell, @Nullable Column parent, boolean built) {

		public Column column() {
			return cell.column();
		}

		Node asBuilt() {
			return new Node(cell, parent, true);
		}
	}

	/** A building to join: its index and the cell in front of its inlet. */
	public record Site(int building, Cell front) {
	}

	/**
	 * The hall's inlet the network grows from.
	 *
	 * @param front    the cell in front of it
	 * @param outwardX the way it faces along X: -1, 0 or 1
	 * @param outwardZ the way it faces along Z: -1, 0 or 1
	 */
	public record Hall(Cell front, int outwardX, int outwardZ) {
	}

	private final Map<Column, Node> nodes = new LinkedHashMap<>();
	private final Map<Integer, Column> fronts = new TreeMap<>();
	/** The cells in front of every building's inlet while the plan grows: dear to cross; never saved. */
	private final Set<Column> thresholds = new HashSet<>();
	private Runnable onChange = () -> { };
	/** Whether the ground may have moved under the unlaid cells since they were traced. Not saved: a read plan is. */
	private boolean stale = true;

	/** An empty plan: no hall, no street. */
	public TownRoadPlan() {
	}

	/** A plan as saved: its cells in any order and the column each building's inlet joins it at. */
	public TownRoadPlan(List<Node> saved, Map<Integer, Column> joined) {
		for (Node node : saved) {
			nodes.putIfAbsent(node.column(), node);
		}
		joined.forEach((building, column) -> {
			if (nodes.containsKey(column)) {
				fronts.put(building, column);
			}
		});
	}

	void onChange(Runnable hook) {
		this.onChange = hook;
	}

	/** Whether the plan holds nothing. */
	public boolean isEmpty() {
		return nodes.isEmpty();
	}

	/** Every cell, in the order they joined: the hall's first. */
	public List<Node> nodes() {
		return List.copyOf(nodes.values());
	}

	/** The column each building's inlet joins the network at, by building. */
	public Map<Integer, Column> fronts() {
		return Collections.unmodifiableMap(fronts);
	}

	/** The cell in front of the hall's inlet, if the network has grown. */
	public Optional<Node> root() {
		for (Node node : nodes.values()) {
			if (node.parent() == null) {
				return Optional.of(node);
			}
		}
		return Optional.empty();
	}

	/** The network's cell in column {@code column}, if it has one. */
	public Optional<Node> at(Column column) {
		return Optional.ofNullable(nodes.get(column));
	}

	/** Whether building {@code building}'s inlet is joined to the network. */
	public boolean joined(int building) {
		return fronts.containsKey(building);
	}

	/**
	 * The cells between the hall's inlet and building {@code building}'s, the hall's end first: the course its road
	 * job lays, laid cells included.
	 */
	public Optional<List<Cell>> course(int building) {
		Column at = fronts.get(building);
		if (at == null) {
			return Optional.empty();
		}
		ArrayDeque<Cell> back = new ArrayDeque<>();
		for (int guard = 0; at != null && guard <= nodes.size(); guard++) {
			Node node = nodes.get(at);
			if (node == null) {
				return Optional.empty();
			}
			back.push(node.cell());
			at = node.parent();
		}
		return at == null ? Optional.of(List.copyOf(back)) : Optional.empty();
	}

	/** Marks the cell of column {@code column} laid; returns whether it was not yet. */
	public boolean markBuilt(Column column) {
		Node node = nodes.get(column);
		if (node == null || node.built()) {
			return false;
		}
		nodes.put(column, node.asBuilt());
		onChange.run();
		return true;
	}

	/** Whether the ground may have moved under the cells not laid yet since they were traced. */
	public boolean stale() {
		return stale;
	}

	/** The town's ground moved — a clearing took terrain or rubble away: the unlaid cells may stand wrong now. */
	public void groundMoved() {
		stale = true;
	}

	/**
	 * Drops every cell not laid that no laid cell hangs on, and forgets the inlets whose way lost a cell: the next
	 * {@link #grow} joins them again on the ground as it is now. Laid cells and the way from them to the hall stay.
	 */
	public void retraceUnlaid() {
		stale = false;
		Set<Column> keep = new HashSet<>();
		for (Node node : nodes.values()) {
			for (Column at = node.built() ? node.column() : null; at != null && keep.add(at); ) {
				Node up = nodes.get(at);
				at = up == null ? null : up.parent();
			}
		}
		if (keep.size() == nodes.size()) {
			return;
		}
		nodes.keySet().retainAll(keep);
		fronts.entrySet().removeIf(entry -> !nodes.containsKey(entry.getValue())
				|| course(entry.getKey()).isEmpty());
		onChange.run();
	}

	/** Building {@code building} leaves the network: its branch goes, as far as it is not laid or shared. */
	public void detach(int building) {
		if (fronts.remove(building) != null) {
			trim();
			onChange.run();
		}
	}

	/** Forgets the whole plan: the hall's inlet moved. */
	public void clear() {
		if (!nodes.isEmpty() || !fronts.isEmpty()) {
			nodes.clear();
			fronts.clear();
			onChange.run();
		}
	}

	/**
	 * Grows the network over {@code sites}: plants it at {@code hall} (a plan rooted elsewhere is started over), lays
	 * out the main street when the plan is new, joins every site not joined yet — nearest to the network first — and
	 * trims the dead ends. A site whose inlet moved is joined again; a plan no inlet is joined to and nothing is laid
	 * on yet is laid out afresh. Returns the buildings that could not be joined.
	 */
	public Set<Integer> grow(Hall hall, List<Site> sites, Survey survey) {
		Optional<Node> root = root();
		boolean nothingHeld = fronts.isEmpty() && nodes.values().stream().noneMatch(Node::built);
		if (root.isPresent() && !root.get().cell().equals(hall.front()) || nothingHeld) {
			nodes.clear(); // a plan nothing hangs on yet — its main street is laid out again with every inlet known
			fronts.clear();
		}
		thresholds.clear(); // the doorsteps of every building: a street keeps off them where it can (R2-7)
		thresholds.add(hall.front().column());
		sites.forEach(site -> thresholds.add(site.front().column()));
		boolean fresh = nodes.isEmpty();
		if (fresh) {
			nodes.put(hall.front().column(), new Node(hall.front(), null, false));
			mainStreet(hall, sites, survey);
		}
		List<Site> waiting = new ArrayList<>();
		for (Site site : sites) {
			Column joined = fronts.get(site.building());
			if (joined == null || !joined.equals(site.front().column())) {
				fronts.remove(site.building());
				waiting.add(site);
			}
		}
		Set<Integer> failed = new TreeSet<>();
		while (!waiting.isEmpty()) {
			Site next = nearest(waiting);
			waiting.remove(next);
			if (!join(next.front(), survey)) {
				failed.add(next.building());
				continue;
			}
			fronts.put(next.building(), next.front().column());
		}
		trim();
		onChange.run();
		return failed;
	}

	/** The site whose inlet is the fewest blocks from any cell of the network. */
	private Site nearest(List<Site> sites) {
		Site best = sites.getFirst();
		int bestDistance = Integer.MAX_VALUE;
		for (Site site : sites) {
			Column front = site.front().column();
			for (Column column : nodes.keySet()) {
				int distance = Math.abs(column.x() - front.x()) + Math.abs(column.z() - front.z());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = site;
				}
			}
		}
		return best;
	}

	/**
	 * The main street: along the long axis of the box of the hall's inlet and every site's, as far as the farthest
	 * inlet along it — straight on from the hall when the hall faces along the axis, else from {@link #SETBACK} blocks
	 * in front of the hall both ways. Shorter than {@link #MIN_MAIN_STREET}: none.
	 */
	private void mainStreet(Hall hall, List<Site> sites, Survey survey) {
		if (sites.isEmpty()) {
			return;
		}
		Cell root = hall.front();
		int minX = root.x();
		int maxX = root.x();
		int minZ = root.z();
		int maxZ = root.z();
		for (Site site : sites) {
			minX = Math.min(minX, site.front().x());
			maxX = Math.max(maxX, site.front().x());
			minZ = Math.min(minZ, site.front().z());
			maxZ = Math.max(maxZ, site.front().z());
		}
		boolean alongX = maxX - minX > maxZ - minZ || maxX - minX == maxZ - minZ && hall.outwardX() != 0;
		int ax = alongX ? 1 : 0;
		int az = alongX ? 0 : 1;
		boolean facesAlong = alongX ? hall.outwardX() != 0 : hall.outwardZ() != 0;
		Cell base = root;
		if (!facesAlong) {
			for (int d = SETBACK; d >= 1; d--) {
				Optional<Cell> out = onGround(root.x() + hall.outwardX() * d, root.z() + hall.outwardZ() * d, survey);
				if (out.isPresent()) {
					if (join(out.get(), survey)) {
						base = out.get();
					}
					break;
				}
			}
		}
		List<Integer> sides = facesAlong ? List.of(alongX ? hall.outwardX() : hall.outwardZ()) : List.of(1, -1);
		for (int side : sides) {
			int reach = 0;
			for (Site site : sites) {
				int along = alongX ? site.front().x() - base.x() : site.front().z() - base.z();
				reach = Math.max(reach, along * side);
			}
			for (int d = reach; d >= MIN_MAIN_STREET; d--) {
				Optional<Cell> end = onGround(base.x() + ax * side * d, base.z() + az * side * d, survey);
				if (end.isPresent()) {
					join(end.get(), survey);
					break;
				}
			}
		}
	}

	/** The road cell on the ground of column ({@code x}, {@code z}), if a road may stand there. */
	private static Optional<Cell> onGround(int x, int z, Survey survey) {
		Ground ground = survey.at(x, z);
		return ground == null ? Optional.empty() : Optional.of(new Cell(x, ground.top(), z));
	}

	/** Joins {@code start} to the network by its cheapest course; returns whether it is joined. */
	private boolean join(Cell start, Survey survey) {
		Map<Column, Integer> network = new HashMap<>();
		nodes.forEach((column, node) -> network.put(column, node.cell().y()));
		Optional<List<Cell>> course = TownRoadTrace.routeToAny(start, network, survey, thresholds);
		if (course.isEmpty()) {
			return false;
		}
		List<Cell> cells = course.get();
		for (int i = cells.size() - 2; i >= 0; i--) {
			Cell cell = cells.get(i);
			nodes.putIfAbsent(cell.column(), new Node(cell, cells.get(i + 1).column(), false));
		}
		return true;
	}

	/** Takes away, over and over, every cell that is not laid, leads to no inlet and has nothing hanging on it. */
	private void trim() {
		Map<Column, Integer> children = new HashMap<>();
		for (Node node : nodes.values()) {
			if (node.parent() != null) {
				children.merge(node.parent(), 1, Integer::sum);
			}
		}
		Set<Column> held = Set.copyOf(fronts.values());
		ArrayDeque<Column> leaves = new ArrayDeque<>();
		for (Node node : nodes.values()) {
			if (children.getOrDefault(node.column(), 0) == 0) {
				leaves.add(node.column());
			}
		}
		while (!leaves.isEmpty()) {
			Column column = leaves.poll();
			Node node = nodes.get(column);
			if (node == null || node.parent() == null || node.built() || held.contains(column)
					|| children.getOrDefault(column, 0) > 0) {
				continue;
			}
			nodes.remove(column);
			int left = children.merge(node.parent(), -1, Integer::sum);
			if (left == 0) {
				leaves.add(node.parent());
			}
		}
	}
}
