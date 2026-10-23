package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.TownRoadPlan.Hall;
import dev.alaindustrial.settlement.works.TownRoadPlan.Node;
import dev.alaindustrial.settlement.works.TownRoadPlan.Site;
import dev.alaindustrial.settlement.works.TownRoadTrace.Cell;
import dev.alaindustrial.settlement.works.TownRoadTrace.Column;
import dev.alaindustrial.settlement.works.TownRoadTrace.Ground;
import dev.alaindustrial.settlement.works.TownRoadTrace.Survey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 for the level of a town road (owner's round 3 of 0.1.219, R3-5): one level over a bump or a dip a few columns
 * long, one step where the ground keeps a new level, the road at a door one block under the inlet's front — flush with
 * the floor — and, on the ground of a real town, no cell standing up or down on its own between two neighbours. A plan
 * traced on ground that has moved since is traced again, its laid cells kept.
 */
class TownRoadLevelTest {

	private static Ground ground(int top) {
		return new Ground(top, top - TownRoadTrace.MAX_CUT, top + TownRoadTrace.MAX_FILL);
	}

	@Test
	void aBumpAndADipAreLevelledNotFollowed() {
		// A ridge one block high at x = 3 and a ditch one block deep at x = 6, across the whole box: no way round.
		Survey uneven = (x, z) -> ground(x == 3 ? 1 : x == 6 ? -1 : 0);
		List<Cell> course = TownRoadTrace.route(new Cell(0, 0, 0), new Cell(9, 0, 0), uneven).orElseThrow();
		assertTrue(course.stream().allMatch(c -> c.y() == 0), "the road keeps one level: " + course);
	}

	@Test
	void aRiseThatStaysIsClimbedOnce() {
		// The ground goes up by one at x = 5 and stays there.
		Survey terrace = (x, z) -> ground(x >= 5 ? 1 : 0);
		List<Cell> course = TownRoadTrace.route(new Cell(0, 0, 0), new Cell(10, 1, 0), terrace).orElseThrow();
		assertEquals(1, changes(course), "one step up, no more: " + course);
	}

	@Test
	void theRoadAtADoorLiesLevelWithTheFloor() {
		// An inlet's front stands a block over the floor, which lies level with the ground (height 0).
		Cell front = new Cell(0, 1, 0);
		Map<Column, Integer> network = Map.of(new Column(8, 0), 0);
		Survey flat = (x, z) -> ground(0);
		List<Cell> course = TownRoadTrace.routeToAny(front, network, flat, Set.of(front.column())).orElseThrow();
		assertEquals(0, course.get(1).y(), "the road next to the door is the floor's level, not a step up: " + course);
		// Ground a block over the floor in front of the door is cut down to it rather than left as a step.
		Survey bank = (x, z) -> ground(x <= 2 ? 1 : 0);
		List<Cell> cut = TownRoadTrace.routeToAny(front, network, bank, Set.of(front.column())).orElseThrow();
		assertEquals(0, cut.get(1).y(), "a step at the door is cut away: " + cut);
	}

	/**
	 * The ground round the hall of the owner's town of round 3 (x 1456..1482, z 310..341): a
	 * letter per column, its top at 90 + the letter's place in the alphabet, a capital for paving, '#' and '?' where a
	 * road may not go (a building, a wall, ground out of the range).
	 */
	private static final int X0 = 1456;
	private static final int Z0 = 310;
	private static final String[] OWNER_TOWN = {
		"??afghhh###II#Mggggjkklmqqq",
		"??afghhh###Ig##hhhijkklmqqq",
		"?abfghhh##M#MM#hhiikkklmqqq",
		"?abghhhhhhhhhhhhhiikkllm###",
		"?acghhhhhhhhhhhhhijlllmm###",
		"?acghihhhhhhhhhhiimmmqqq###",
		"?ADKLLLLKJIIIIIIL##PQqqq###",
		"??KlMmMmMMMMiMMMMMNmQqqq###",
		"??LLMmMmMMMmMmMMMMMmqqqq###",
		"??LmMMMMMMMMMMmMMMMOQqqqqqq",
		"??LmMm###########MMmqqqqqqq",
		"??LMMM###########MMmqqqq###",
		"??LMMM###########MmmPqqq###",
		"??LMMM###########MMmPqqq###",
		"?aLmMM###########MMNP######",
		"??LMMM###########MMmOpqq###",
		"?KlMMM###########MmmOppq###",
		"?KLMMM###########mMNopppppp",
		"?KlMMM###########mMmOoppppo",
		"??LMMM###########MMmnooo###",
		"??LLMM###########MMmNoon###",
		"???LMM###########mMNNnnn###",
		"???lMM###########MMMN######",
		"???kLMMMMmmMMMMMlMMMMnnn###",
		"????lLLMMMMMMMMMMMMMmmmn###",
		"?????EFLLMMMMmMMMmMMMmmmmmm",
		"?????eFFFFfLLLLLLmMMMllllll",
		"####?effffffffffflllllll###",
		"?????ef########fffllllll###",
		"?????ef#e######ffgllllll###",
		"?????ef########ff##ll######",
		"????def########fkllllll####",
	};

	private static final Survey OWNER_GROUND = (x, z) -> {
		int row = z - Z0;
		int col = x - X0;
		if (row < 0 || row >= OWNER_TOWN.length || col < 0 || col >= OWNER_TOWN[row].length()) {
			return null;
		}
		char c = OWNER_TOWN[row].charAt(col);
		if (c == '#' || c == '?') {
			return null;
		}
		int top = 90 + Character.toLowerCase(c) - 'a';
		return new Ground(top, top - TownRoadTrace.MAX_CUT, top + TownRoadTrace.MAX_FILL, Character.isUpperCase(c));
	};

	@Test
	void theOwnersTownGetsLevelStreets() {
		// The hall's inlet faces west; four houses east of it, each inlet a block over its floor.
		Hall hall = new Hall(new Cell(1461, 103, 324), -1, 0);
		List<Site> houses = List.of(new Site(4, new Cell(1479, 102, 338)), new Site(5, new Cell(1479, 107, 322)),
				new Site(6, new Cell(1479, 107, 314)), new Site(7, new Cell(1479, 104, 330)));
		TownRoadPlan plan = new TownRoadPlan();
		assertTrue(plan.grow(hall, houses, OWNER_GROUND).isEmpty(), "every house joins");
		for (Site house : houses) {
			List<Cell> course = plan.course(house.building()).orElseThrow();
			for (int i = 1; i < course.size(); i++) {
				assertTrue(Math.abs(course.get(i).y() - course.get(i - 1).y()) <= 1, "a step over one: " + course);
			}
			for (int i = 2; i < course.size() - 2; i++) {
				int y = course.get(i).y();
				int before = course.get(i - 1).y();
				int after = course.get(i + 1).y();
				assertTrue(!(y > before && y > after) && !(y < before && y < after),
						"a cell up or down on its own at " + course.get(i) + ": " + course);
			}
			assertEquals(course.getLast().y() - 1, course.get(course.size() - 2).y(),
					"the road at house " + house.building() + "'s door lies level with its floor: " + course);
			assertEquals(hall.front().y() - 1, course.get(1).y(), "and at the hall's: " + course);
		}
	}

	@Test
	void whatIsNotLaidIsTracedAgainOnTheGroundOfTheDay() {
		// The inlets stand a block over floors level with the ground (height 0).
		Hall hall = new Hall(new Cell(0, 1, 0), 0, 1);
		Site house = new Site(1, new Cell(6, 1, 12));
		// Rubble a block high lay over the rows z = 3..10 when the plan was traced; the road climbed it.
		Survey rubble = (x, z) -> ground(z >= 3 && z <= 10 ? 1 : 0);
		TownRoadPlan plan = new TownRoadPlan();
		plan.grow(hall, List.of(house), rubble);
		assertTrue(plan.nodes().stream().anyMatch(node -> node.cell().y() == 1 && node.cell().z() > 2
				&& node.cell().z() < 11), "the plan rose over the rubble: " + plan.nodes());
		Column laid = plan.course(1).orElseThrow().get(1).column();
		plan.markBuilt(laid);
		// A clearing took the rubble away.
		plan.groundMoved();
		assertTrue(plan.stale());
		plan.retraceUnlaid();
		Survey flat = (x, z) -> ground(0);
		assertTrue(plan.grow(hall, List.of(house), flat).isEmpty(), "the house joins again");
		Set<Column> doors = Set.of(hall.front().column(), house.front().column());
		assertTrue(plan.nodes().stream().allMatch(node -> node.cell().y() == 0 || doors.contains(node.column())),
				"level on today's ground: " + plan.nodes());
		assertTrue(plan.at(laid).map(Node::built).orElse(false), "the laid cell stays");
	}

	private static int changes(List<Cell> course) {
		int n = 0;
		for (int i = 1; i < course.size(); i++) {
			if (course.get(i).y() != course.get(i - 1).y()) {
				n++;
			}
		}
		return n;
	}
}
