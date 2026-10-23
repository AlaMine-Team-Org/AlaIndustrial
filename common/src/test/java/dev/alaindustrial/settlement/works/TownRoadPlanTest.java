package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.TownRoadPlan.Hall;
import dev.alaindustrial.settlement.works.TownRoadPlan.Node;
import dev.alaindustrial.settlement.works.TownRoadPlan.Site;
import dev.alaindustrial.settlement.works.TownRoadTrace.Cell;
import dev.alaindustrial.settlement.works.TownRoadTrace.Column;
import dev.alaindustrial.settlement.works.TownRoadTrace.Ground;
import dev.alaindustrial.settlement.works.TownRoadTrace.Survey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 for the town's road network (round 2 of 0.1.219, R2-7; owner's answers Q7–Q12): one main street along the
 * town's long axis, T junctions to every inlet, no two streets side by side, shorter than a separate road to every
 * house, one block of height at most between neighbours, a second house joining the first one's street, old paved
 * streets followed, and laid cells kept.
 */
class TownRoadPlanTest {

	private static final Hall HALL = new Hall(new Cell(0, 0, 0), 0, 1);
	/** Four houses on both sides of the way the hall faces, further and further out. */
	private static final List<Site> HOUSES = List.of(
			new Site(1, new Cell(6, 0, 8)), new Site(2, new Cell(-6, 0, 14)),
			new Site(3, new Cell(6, 0, 20)), new Site(4, new Cell(-6, 0, 26)));

	private static Ground ground(int top) {
		return new Ground(top, top - TownRoadTrace.MAX_CUT, top + TownRoadTrace.MAX_FILL);
	}

	private static final Survey FLAT = (x, z) -> ground(0);

	@Test
	void anOpenTownGetsOneMainStreetWithTJunctionsToItsHouses() {
		TownRoadPlan plan = new TownRoadPlan();
		assertTrue(plan.grow(HALL, HOUSES, FLAT).isEmpty(), "every house joins");
		assertTree(plan);
		for (Site house : HOUSES) {
			List<Cell> course = plan.course(house.building()).orElseThrow();
			assertEquals(HALL.front(), course.getFirst(), "a course starts in front of the hall");
			assertEquals(house.front(), course.getLast(), "and ends in front of the house");
			assertWalkable(course);
		}
		// The main street: straight out of the hall along Z, as far as the last junction.
		int street = 0;
		for (int z = 0; plan.at(new Column(0, z)).isPresent(); z++) {
			street++;
		}
		assertTrue(street >= 26, "a straight main street from the hall to the farthest house: " + street);
		assertTrue(junctions(plan) >= 3, "branches leave the main street at T junctions: " + plan.nodes());
		int separate = 0;
		for (Site house : HOUSES) {
			separate += TownRoadTrace.route(HALL.front(), house.front(), FLAT).orElseThrow().size();
		}
		assertTrue(plan.nodes().size() < separate,
				"one network is shorter than a road to every house: " + plan.nodes().size() + " >= " + separate);
	}

	@Test
	void noTwoStreetsRunSideBySide() {
		TownRoadPlan plan = new TownRoadPlan();
		plan.grow(HALL, HOUSES, FLAT);
		Map<Column, Node> byColumn = new HashMap<>();
		plan.nodes().forEach(node -> byColumn.put(node.column(), node));
		for (Node node : plan.nodes()) {
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				Node side = byColumn.get(new Column(node.cell().x() + d[0], node.cell().z() + d[1]));
				if (side != null) {
					assertTrue(node.column().equals(side.parent()) || side.column().equals(node.parent()),
							"two cells side by side that are not one street: " + node + " / " + side);
				}
			}
		}
	}

	@Test
	void unevenGroundKeepsEveryStepToOneBlock() {
		// The ground rises a block every two along Z and steps up by two past x = 3: cuts and fills are needed.
		Survey hills = (x, z) -> ground(z / 2 + (x > 3 ? 2 : 0));
		Hall hall = new Hall(new Cell(0, 0, 0), 0, 1);
		List<Site> houses = List.of(new Site(1, new Cell(8, 7, 10)), new Site(2, new Cell(-6, 7, 14)));
		TownRoadPlan plan = new TownRoadPlan();
		assertTrue(plan.grow(hall, houses, hills).isEmpty(), "both houses join");
		assertTree(plan);
		for (Site house : houses) {
			assertWalkable(plan.course(house.building()).orElseThrow());
		}
	}

	@Test
	void aSecondHouseJoinsTheStreetOfTheFirst() {
		TownRoadPlan plan = new TownRoadPlan();
		Site first = new Site(1, new Cell(12, 0, 4));
		plan.grow(HALL, List.of(first), FLAT);
		Set<Column> before = new HashSet<>();
		plan.nodes().forEach(node -> before.add(node.column()));
		Site second = new Site(2, new Cell(16, 0, 10));
		assertTrue(plan.grow(HALL, List.of(first, second), FLAT).isEmpty());
		assertTree(plan);
		List<Cell> course = plan.course(2).orElseThrow();
		int shared = 0;
		while (shared < course.size() && before.contains(course.get(shared).column())) {
			shared++;
		}
		assertTrue(shared > 3, "the second house meets the network beyond the hall's door: " + course);
		int added = plan.nodes().size() - before.size();
		assertTrue(added < TownRoadTrace.route(HALL.front(), second.front(), FLAT).orElseThrow().size() / 2,
				"the second house adds a short branch, not a road of its own: " + added);
		assertEquals(plan.course(1).orElseThrow().size(),
				new TownRoadPlan(plan.nodes(), plan.fronts()).course(1).orElseThrow().size(),
				"the plan reads back as saved");
	}

	@Test
	void aStreetKeepsOffAnotherHousesDoorstep() {
		TownRoadPlan plan = new TownRoadPlan();
		Site first = new Site(1, new Cell(8, 0, 10));
		plan.grow(HALL, List.of(first), FLAT);
		// The second door is three blocks from the first one's doorstep, nearer to it than to any other cell.
		Site second = new Site(2, new Cell(10, 0, 11));
		assertTrue(plan.grow(HALL, List.of(first, second), FLAT).isEmpty());
		assertTree(plan);
		Column doorstep = first.front().column();
		assertTrue(plan.nodes().stream().noneMatch(node -> doorstep.equals(node.parent())),
				"the second house's street must not start on the first one's doorstep: " + plan.course(2));
		assertFalse(plan.course(2).orElseThrow().stream().anyMatch(cell -> cell.column().equals(doorstep)),
				"nor run over it: " + plan.course(2));
	}

	@Test
	void growingAgainChangesNothing() {
		TownRoadPlan plan = new TownRoadPlan();
		plan.grow(HALL, HOUSES, FLAT);
		List<Node> once = plan.nodes();
		plan.grow(HALL, HOUSES, FLAT);
		assertEquals(once, plan.nodes(), "a laid-out network is not traced again");
	}

	@Test
	void anOldPavedStreetIsFollowed() {
		// Two equal ways round from (0, 0) to (10, 2): the one along the paved row z = 2 is cheaper.
		Survey paved = (x, z) -> new Ground(0, -TownRoadTrace.MAX_CUT, TownRoadTrace.MAX_FILL, z == 2);
		List<Cell> course = TownRoadTrace.route(new Cell(0, 0, 0), new Cell(10, 0, 2), paved).orElseThrow();
		for (int x = 1; x < 10; x++) {
			int at = x;
			assertTrue(course.stream().anyMatch(c -> c.x() == at && c.z() == 2), "off the paved row: " + course);
		}
	}

	@Test
	void aHouseLeavingTakesItsBranchButNotWhatIsLaid() {
		TownRoadPlan plan = new TownRoadPlan();
		plan.grow(HALL, HOUSES, FLAT);
		Column third = HOUSES.get(2).front().column();
		plan.detach(3);
		assertFalse(plan.at(third).isPresent(), "the branch of a house that left goes");
		assertTrue(plan.course(4).isPresent(), "the others keep theirs");
		Column first = HOUSES.getFirst().front().column();
		assertTrue(plan.markBuilt(first));
		plan.detach(1);
		assertTrue(plan.at(first).isPresent(), "a laid cell stays");
		assertTree(plan);
	}

	/** Every cell but the hall's hangs on a neighbour in the tree, at most one block higher or lower. */
	private static void assertTree(TownRoadPlan plan) {
		Map<Column, Node> byColumn = new HashMap<>();
		plan.nodes().forEach(node -> byColumn.put(node.column(), node));
		int roots = 0;
		for (Node node : plan.nodes()) {
			if (node.parent() == null) {
				roots++;
				continue;
			}
			Node parent = byColumn.get(node.parent());
			assertTrue(parent != null, "a cell hangs on nothing: " + node);
			int apart = Math.abs(parent.cell().x() - node.cell().x()) + Math.abs(parent.cell().z() - node.cell().z());
			assertEquals(1, apart, "a cell hangs on a cell not next to it: " + node);
			assertTrue(Math.abs(parent.cell().y() - node.cell().y()) <= 1, "a step of more than one block: " + node);
		}
		assertEquals(1, roots, "one root, in front of the hall");
		assertNotEquals(0, plan.nodes().size());
	}

	/** Cells that carry two or more streets on: the T and cross junctions. */
	private static int junctions(TownRoadPlan plan) {
		Map<Column, Integer> children = new HashMap<>();
		for (Node node : plan.nodes()) {
			if (node.parent() != null) {
				children.merge(node.parent(), 1, Integer::sum);
			}
		}
		return (int) children.values().stream().filter(n -> n >= 2).count();
	}

	private static void assertWalkable(List<Cell> course) {
		for (int i = 1; i < course.size(); i++) {
			Cell a = course.get(i - 1);
			Cell b = course.get(i);
			assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()), "cells " + a + " and " + b);
			assertTrue(Math.abs(a.y() - b.y()) <= 1, "a step of more than one block: " + a + " -> " + b);
		}
	}
}
