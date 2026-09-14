package dev.alaindustrial.client.screen.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** L1 coverage for {@link RoomMapLayout} (MOD-619): every room the scanner can measure fits its map box. */
class RoomMapLayoutTest {

	private static final int BOX = 100;
	private static final int[] NONE = {};

	/** The largest box the scanner measures is one past the limit (TOO_LARGE at 13): its shell is 15 cells. */
	@Test
	void theLargestMeasurableShellFitsWithTheControllerOnIt() {
		RoomMapLayout map = RoomMapLayout.of(true, 1, -6, 13, 13, NONE, BOX, BOX);
		assertEquals(15, map.cols());
		assertEquals(15, map.rows());
		assertTrue(map.cellLeft(0) >= 0 && map.cellLeft(14) + map.cell() <= BOX, "the far wall is clipped");
		assertTrue(map.cellTop(-7) >= 0 && map.cellTop(7) + map.cell() <= BOX, "a wall is clipped");
	}

	@Test
	void aSmallRoomIsNotDrawnAsAPostageStamp() {
		RoomMapLayout map = RoomMapLayout.of(true, 1, -1, 3, 3, NONE, BOX, BOX);
		assertEquals(RoomMapLayout.MAX_CELL, map.cell());
		assertEquals((BOX - 5 * RoomMapLayout.MAX_CELL) / 2, map.left(), "the map is centred");
	}

	/** North is up and east is right: the controller on the west wall is the left-most column. */
	@Test
	void northIsUpAndEastIsRight() {
		RoomMapLayout map = RoomMapLayout.of(true, 1, -2, 4, 4, NONE, BOX, BOX);
		assertEquals(map.left(), map.cellLeft(0));
		assertTrue(map.cellLeft(5) > map.cellLeft(0));
		assertTrue(map.cellTop(-3) < map.cellTop(2));
	}

	/** A controller in an edge can sit outside the shell's walls; the map must still show it. */
	@Test
	void aControllerOffTheFootprintIsStillOnTheMap() {
		RoomMapLayout map = RoomMapLayout.of(true, 3, 2, 3, 3, NONE, BOX, BOX);
		assertEquals(0, map.minX());
		assertEquals(0, map.minZ());
	}

	@Test
	void withNoBoxTheProblemAroundTheControllerIsShown() {
		RoomMapLayout map = RoomMapLayout.of(false, 0, 0, 0, 0, new int[] {1, 0}, BOX, BOX);
		assertEquals(RoomMapLayout.UNMEASURED_SPAN, map.cols());
		assertTrue(map.cellLeft(1) + map.cell() <= BOX);
		RoomMapLayout far = RoomMapLayout.of(false, 0, 0, 0, 0, new int[] {0, 9}, BOX, BOX);
		assertTrue(far.cellTop(9) + far.cell() <= BOX, "a problem outside the small field grows it");
	}

	/** Every hole is on the map, not only the first — the playtest found three walls' worth missing. */
	@Test
	void everyProblemCellIsOnTheMap() {
		int[] holes = {-6, 0, 8, -1, 3, 12};
		RoomMapLayout map = RoomMapLayout.of(true, 1, -3, 7, 7, holes, BOX, BOX);
		for (int i = 0; i < holes.length; i += 2) {
			int x = holes[i];
			int z = holes[i + 1];
			assertTrue(map.cellLeft(x) >= 0 && map.cellLeft(x) + map.cell() <= BOX, () -> "hole x " + x + " is clipped");
			assertTrue(map.cellTop(z) >= 0 && map.cellTop(z) + map.cell() <= BOX, () -> "hole z " + z + " is clipped");
		}
	}

	@Test
	void theShellIsTheRingAroundTheInterior() {
		assertTrue(RoomMapLayout.isShell(0, 0, 1, -1, 3, 3), "the controller's cell is shell");
		assertTrue(RoomMapLayout.isShell(4, 2, 1, -1, 3, 3), "far corner");
		assertFalse(RoomMapLayout.isShell(2, 0, 1, -1, 3, 3), "interior");
		assertTrue(RoomMapLayout.isInterior(2, 0, 1, -1, 3, 3));
		assertFalse(RoomMapLayout.isShell(5, 0, 1, -1, 3, 3), "outside");
		assertFalse(RoomMapLayout.isInterior(5, 0, 1, -1, 3, 3));
	}
}
