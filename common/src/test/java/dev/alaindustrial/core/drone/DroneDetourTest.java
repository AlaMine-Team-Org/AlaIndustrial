package dev.alaindustrial.core.drone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The way in to a walled-up cell (epic MOD-824, owner's round 5 of 0.1.219): the fewest blocks lifted for a while. */
class DroneDetourTest {

	private static final int MAX = 20_000;

	/**
	 * A floor cell at (0, 0, 0) shut in on every side: ground under it (y < 0), a wall over it at y = 1 for x in
	 * -1..1, the floor around it at y = 0. Over the wall (y >= 2) and beside it (y = 1, |x| >= 2) is air.
	 */
	private static DroneDetour.Kind shutIn(int x, int y, int z) {
		if (y >= 2 || y == 1 && Math.abs(x) >= 2) {
			return DroneDetour.Kind.OPEN;
		}
		return y >= 0 ? DroneDetour.Kind.LIFT : DroneDetour.Kind.CLOSED;
	}

	@Test
	void anOpenNeighbourNeedsNothingLifted() {
		int[] lifts = DroneDetour.find(0, 5, 0, 0, 0, 0, (x, y, z) -> y >= 1 ? DroneDetour.Kind.OPEN
				: DroneDetour.Kind.CLOSED, -4, -2, -4, 4, 6, 4, 2, MAX);
		assertNotNull(lifts);
		assertArrayEquals(new int[0], lifts);
	}

	/** The wall block right over the cell is the one block to lift: under it lies the cell, over it the air. */
	@Test
	void liftsTheOneBlockOverAShutInCell() {
		int[] lifts = DroneDetour.find(0, 5, 0, 0, 0, 0, DroneDetourTest::shutIn, -4, -2, -4, 4, 6, 4, 2, MAX);
		assertArrayEquals(new int[] {0, 1, 0}, lifts);
	}

	/** Two blocks deep: the outer one comes first, the one next to the target second. */
	@Test
	void liftsTwoInOrderFromTheDockSide() {
		DroneDetour.Cells cells = (x, y, z) -> {
			if (y >= 3) {
				return DroneDetour.Kind.OPEN;
			}
			if (x == 0 && z == 0 && (y == 1 || y == 2)) {
				return DroneDetour.Kind.LIFT;
			}
			return DroneDetour.Kind.CLOSED;
		};
		int[] lifts = DroneDetour.find(0, 6, 0, 0, 0, 0, cells, -4, -2, -4, 4, 8, 4, 2, MAX);
		assertArrayEquals(new int[] {0, 2, 0, 0, 1, 0}, lifts);
	}

	@Test
	void neverLiftsMoreThanAllowedNorAClosedBlock() {
		DroneDetour.Cells deep = (x, y, z) -> y >= 4 ? DroneDetour.Kind.OPEN
				: x == 0 && z == 0 && y >= 1 ? DroneDetour.Kind.LIFT : DroneDetour.Kind.CLOSED;
		assertNull(DroneDetour.find(0, 6, 0, 0, 0, 0, deep, -4, -2, -4, 4, 8, 4, 2, MAX));
		DroneDetour.Cells closed = (x, y, z) -> y >= 2 ? DroneDetour.Kind.OPEN : DroneDetour.Kind.CLOSED;
		assertNull(DroneDetour.find(0, 6, 0, 0, 0, 0, closed, -4, -2, -4, 4, 8, 4, 2, MAX));
	}
}
