package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link CraterWaves} (MOD-736): the crater's size, its lava, and the planner that decides where
 * the cascade's waves go off. Every expectation is a literal counted by hand, not the formula written twice.
 */
class CraterWavesTest {

	@Test
	void radiusGrowsWithTheRodsAndStopsAtTheCeiling() {
		assertEquals(6, CraterWaves.radius(0, 6, 2, 28), "a core with no rods");
		assertEquals(9, CraterWaves.radius(16, 6, 2, 28), "a bare pile of four racks: 6 + 32/10, truncated");
		assertEquals(15, CraterWaves.radius(48, 6, 2, 28), "twelve racks in a room");
		assertEquals(26, CraterWaves.radius(100, 6, 2, 28), "a hundred-rod station");
		assertEquals(28, CraterWaves.radius(200, 6, 2, 28), "the ceiling holds");
		assertEquals(46, CraterWaves.radius(200, 6, 2, 0), "a ceiling of zero means none");
		assertEquals(6, CraterWaves.radius(-5, 6, -2, 28), "negative inputs count as zero");
	}

	@Test
	void lavaGrowsWithTheCrater() {
		assertEquals(6, CraterWaves.lavaCells(6, 3, 0));
		assertEquals(84, CraterWaves.lavaCells(6, 3, 26));
		assertEquals(0, CraterWaves.lavaCells(-1, -3, 10));
	}

	@Test
	void aWaveIsPlannedOnlyWhereItStaysInsideTheCrater() {
		assertEquals(0, CraterWaves.plan(6, 6).size(), "the nearest node would carve past a radius of 6");
		assertEquals(6, CraterWaves.plan(9, 6).size(), "only the six face neighbours fit in 9");
		assertEquals(32, CraterWaves.plan(15, 6).size(), "6 + 12 + 8 + 6 nodes with |k|² ≤ 4");
		assertEquals(250, CraterWaves.plan(26, 6).size());
		assertEquals(0, CraterWaves.plan(0, 6).size());
		assertEquals(0, CraterWaves.plan(10, 0).size());
	}

	@Test
	void wavesGoOffNearestFirstInAFixedOrder() {
		List<CraterWaves.Target> plan = CraterWaves.plan(15, 6);
		List<CraterWaves.Cell> firstSix = plan.subList(0, 6).stream().map(CraterWaves.Target::at).toList();
		assertEquals(List.of(new CraterWaves.Cell(-6, 0, 0), new CraterWaves.Cell(0, -6, 0),
				new CraterWaves.Cell(0, 0, -6), new CraterWaves.Cell(0, 0, 6), new CraterWaves.Cell(0, 6, 0),
				new CraterWaves.Cell(6, 0, 0)), firstSix);
		assertEquals(new CraterWaves.Cell(-6, -6, -6), plan.get(18).at(), "after the 6 faces and 12 edges, a corner");
		assertEquals(new CraterWaves.Cell(-12, 0, 0), plan.get(26).at(),
				"after the 8 corners, the first node two spacings out");
		assertEquals(new CraterWaves.Cell(-2, 0, 0), plan.get(26).node());
	}

	@Test
	void aWaveGoesOffOnlyWhereSomethingWasDestroyed() {
		CraterWaves.Field field = new CraterWaves.Field(6);
		CraterWaves.Target east = CraterWaves.plan(9, 6).get(5);
		assertEquals(new CraterWaves.Cell(6, 0, 0), east.at());
		assertNull(field.centreFor(east), "nothing destroyed yet: the wave has nothing to grow from");

		field.offer(2, 0, 0);
		assertNull(field.centreFor(east), "x = 2 is nearer the epicentre's node than the eastern one");
		field.offer(3, 1, 0);
		assertEquals(new CraterWaves.Cell(3, 1, 0), field.centreFor(east), "x = 3 rounds up into the eastern bucket");
		field.offer(5, 0, 1);
		assertEquals(new CraterWaves.Cell(5, 0, 1), field.centreFor(east), "the cell nearest the node wins");
		field.offer(7, 0, 0);
		assertEquals(new CraterWaves.Cell(7, 0, 0), field.centreFor(east), "1 block from the node beats √2");
		field.offer(6, 1, 0);
		assertEquals(new CraterWaves.Cell(6, 1, 0), field.centreFor(east), "a tie at 1 goes to the smaller x");
		field.offer(6, 0, -1);
		assertEquals(new CraterWaves.Cell(6, 0, -1), field.centreFor(east), "then to the smaller y");
		field.offer(6, 0, 1);
		assertEquals(new CraterWaves.Cell(6, 0, -1), field.centreFor(east), "then to the smaller z");
	}

	@Test
	void bucketsAreSymmetricAboutTheEpicentre() {
		CraterWaves.Field field = new CraterWaves.Field(2);
		List<CraterWaves.Target> faces = CraterWaves.plan(3, 2);
		assertEquals(new CraterWaves.Cell(-2, 0, 0), faces.get(0).at());
		assertEquals(new CraterWaves.Cell(2, 0, 0), faces.get(5).at());
		field.offer(-1, 0, 0);
		field.offer(1, 0, 0);
		assertEquals(new CraterWaves.Cell(-1, 0, 0), field.centreFor(faces.get(0)), "half a spacing west rounds west");
		assertEquals(new CraterWaves.Cell(1, 0, 0), field.centreFor(faces.get(5)), "half a spacing east rounds east");
	}

	@Test
	void theChoiceDoesNotDependOnTheOrderCellsArrive() {
		CraterWaves.Field forward = new CraterWaves.Field(6);
		CraterWaves.Field backward = new CraterWaves.Field(6);
		int[][] cells = {{7, 0, 0}, {5, 0, 1}, {6, 1, 0}, {5, 0, -1}, {4, 2, 2}};
		for (int[] c : cells) {
			forward.offer(c[0], c[1], c[2]);
		}
		for (int i = cells.length - 1; i >= 0; i--) {
			backward.offer(cells[i][0], cells[i][1], cells[i][2]);
		}
		CraterWaves.Target east = CraterWaves.plan(9, 6).get(5);
		assertEquals(forward.centreFor(east), backward.centreFor(east));
		assertTrue(forward.centreFor(east) != null);
	}
}
