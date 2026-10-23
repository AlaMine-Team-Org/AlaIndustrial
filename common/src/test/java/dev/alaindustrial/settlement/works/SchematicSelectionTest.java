package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * L1 for the cells one town hall schematic reply carries (MOD-857, owner's round 2, point 7): all of them under the
 * cap, the nearest to the viewer over it, always in the plan's own order.
 */
class SchematicSelectionTest {

	@Test
	void underTheCapEveryCellGoesInPlanOrder() {
		int[] x = {5, 0, 9};
		int[] y = {0, 1, 2};
		int[] z = {0, 0, 0};
		assertArrayEquals(new int[] {0, 1, 2}, SchematicSelection.pick(x, y, z, 100, 100, 100, 3));
	}

	@Test
	void overTheCapTheNearestGoAndKeepPlanOrder() {
		// Five cells on a line; the viewer stands by cell x = 40.
		int[] x = {0, 10, 20, 30, 40};
		int[] y = new int[5];
		int[] z = new int[5];
		int[] picked = SchematicSelection.pick(x, y, z, 40.5, 0.5, 0.5, 2);
		assertArrayEquals(new int[] {3, 4}, picked, "the two cells nearest the viewer, ascending");
	}

	@Test
	void anEqualDistanceTieKeepsTheEarlierCell() {
		int[] x = {-1, 1, 5};
		int[] y = new int[3];
		int[] z = new int[3];
		// Cells -1 and 1 are equally near the viewer at 0.5 (centres -0.5 and 1.5); the earlier one wins.
		assertArrayEquals(new int[] {0}, SchematicSelection.pick(x, y, z, 0.5, 0.5, 0.5, 1));
	}

	@Test
	void noCapOrNoCellsSendsNothing() {
		assertEquals(0, SchematicSelection.pick(new int[] {1}, new int[] {1}, new int[] {1}, 0, 0, 0, 0).length);
		assertEquals(0, SchematicSelection.pick(new int[0], new int[0], new int[0], 0, 0, 0, 10).length);
	}

	@Test
	void mismatchedArraysAreRefused() {
		assertThrows(IllegalArgumentException.class,
				() -> SchematicSelection.pick(new int[2], new int[1], new int[2], 0, 0, 0, 5));
	}

	@Test
	void theSchematicSwitchIsTheLastAction() {
		// The tab's request packet carries an action's ordinal; the order controls of round 2 of 0.1.219 replaced the
		// two «raise» ones, so client and server must be one build.
		ConstructionRow.Action[] all = ConstructionRow.Action.values();
		assertEquals(ConstructionRow.Action.SCHEMATIC, all[all.length - 1]);
		assertEquals(6, ConstructionRow.Action.START.ordinal());
		assertEquals("settlement.alaindustrial.works.action.schematic", ConstructionRow.Action.SCHEMATIC.key());
	}
}
