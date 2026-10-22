package dev.alaindustrial.core.drone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The builder drone's route through open cells (MOD-858, owner's round 2): never through a block, bounded in cost. */
class DroneRouteTest {

	private static final int MAX = 6000;

	/** Every sample of every leg of {@code route} lies in an open cell — the drone never enters a solid one. */
	private static void assertStaysInTheAir(DroneRoute route, DroneRoute.Cells open, int sx, int sy, int sz) {
		int[] p = route.points();
		assertEquals(sx, p[0]);
		assertEquals(sy, p[1]);
		assertEquals(sz, p[2]);
		DroneRoute.Cells withDock = (x, y, z) -> x == sx && y == sy && z == sz || open.open(x, y, z);
		for (int i = 3; i < p.length; i += 3) {
			assertTrue(DroneRoute.lineOpen(p[i - 3], p[i - 2], p[i - 1], p[i], p[i + 1], p[i + 2], withDock),
					"leg " + (i / 3) + " goes through a closed cell");
		}
	}

	private static int[] last(DroneRoute route) {
		int[] p = route.points();
		return new int[] {p[p.length - 3], p[p.length - 2], p[p.length - 1]};
	}

	/** A wall five high between the dock and the target: the route goes over it, not through it. */
	@Test
	void goesOverAWall() {
		DroneRoute.Cells open = (x, y, z) -> y >= 0 && !(x == 5 && y <= 4);
		DroneRoute route = DroneRoute.find(0, 0, 0, 10, 0, 0, open, -4, 0, -4, 14, 10, 4, MAX);
		assertTrue(route.found());
		assertEquals(DroneRoute.Kind.SEARCH, route.kind());
		assertStaysInTheAir(route, open, 0, 0, 0);
		assertTrue(route.length() > 10.0, "the route must be longer than the straight line, it is " + route.length());
		assertTrue(route.expanded() <= MAX);
	}

	/** The goal is a face-neighbour of the target — the one above when it is open — and the target is never entered. */
	@Test
	void landsOnTopAndNeverEntersTheTarget() {
		DroneRoute.Cells open = (x, y, z) -> y >= 0;
		DroneRoute route = DroneRoute.find(0, 3, 0, 6, 0, 2, open, -4, 0, -4, 10, 8, 6, MAX);
		assertTrue(route.found());
		assertEquals(6, last(route)[0]);
		assertEquals(1, last(route)[1]);
		assertEquals(2, last(route)[2]);
		int[] p = route.points();
		for (int i = 0; i < p.length; i += 3) {
			assertFalse(p[i] == 6 && p[i + 1] == 0 && p[i + 2] == 2, "the route must not enter the target");
		}
	}

	/** A target walled in on every side has no route, and the search stays within its budget. */
	@Test
	void walledInTargetHasNoRoute() {
		DroneRoute.Cells open = (x, y, z) -> !(Math.abs(x - 6) <= 1 && Math.abs(y - 3) <= 1 && Math.abs(z) <= 1);
		DroneRoute route = DroneRoute.find(0, 3, 0, 6, 3, 0, open, -4, 0, -4, 10, 8, 4, MAX);
		assertFalse(route.found());
		assertEquals(0, route.points().length);
		assertTrue(route.expanded() <= MAX);
	}

	/** Out of budget: the plain way over is taken, and it is still in the air. */
	@Test
	void fallsBackToTheWayOver() {
		DroneRoute.Cells open = (x, y, z) -> y >= 0 && !(x == 5 && y <= 4);
		DroneRoute route = DroneRoute.find(0, 0, 0, 10, 0, 0, open, -4, 0, -4, 14, 10, 4, 3);
		assertEquals(DroneRoute.Kind.OVER, route.kind());
		assertStaysInTheAir(route, open, 0, 0, 0);
		assertTrue(route.expanded() <= 3);
	}

	/** Two blocks touching at an edge leave no gap a diagonal step may squeeze through. */
	@Test
	void neverCutsACorner() {
		// Floor y = 0, ceiling y = 2: one layer of air. Blocks at (1,1,0) and (0,1,1) close the corner between the
		// dock (0,1,0) and (1,1,1) — the only way out of the dock's corner of the box is through it.
		DroneRoute.Cells open = (x, y, z) -> y == 1 && !(x == 1 && z == 0) && !(x == 0 && z == 1);
		DroneRoute route = DroneRoute.find(0, 1, 0, 3, 1, 3, open, 0, 0, 0, 4, 2, 4, MAX);
		assertFalse(route.found(), "the route must not slip between two blocks touching at an edge");
	}

	/** A doorway one cell wide: the route goes through it. */
	@Test
	void goesThroughADoorway() {
		// A wall at x = 5 from y = 0 to 9 with one hole at (5, 1, 0); the box top is y = 9, so there is no way over.
		DroneRoute.Cells open = (x, y, z) -> y >= 0 && !(x == 5 && !(y == 1 && z == 0));
		DroneRoute route = DroneRoute.find(0, 1, 0, 9, 1, 0, open, -2, 0, -3, 12, 9, 3, MAX);
		assertTrue(route.found());
		assertStaysInTheAir(route, open, 0, 1, 0);
	}
}
