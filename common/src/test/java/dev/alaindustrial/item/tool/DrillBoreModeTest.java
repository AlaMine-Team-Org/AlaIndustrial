package dev.alaindustrial.item.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** L1 — the bore mode table and the key's cycle for a drill with both modules (MOD-821). */
class DrillBoreModeTest {

	/**
	 * @implements MOD-821 — the key walks cross, column, row, single and back to cross
	 * @covers MOD-821
	 */
	@Test
	void keyCyclesCrossColumnRowSingle() {
		assertEquals(DrillBoreMode.COLUMN, DrillBoreMode.CROSS.next());
		assertEquals(DrillBoreMode.ROW, DrillBoreMode.COLUMN.next());
		assertEquals(DrillBoreMode.SINGLE, DrillBoreMode.ROW.next());
		assertEquals(DrillBoreMode.CROSS, DrillBoreMode.SINGLE.next());
	}

	/** @implements MOD-821 — the two switches map onto the four modes, and back */
	@Test
	void switchesMapOntoModes() {
		for (DrillBoreMode mode : DrillBoreMode.values()) {
			assertEquals(mode, DrillBoreMode.of(mode.column(), mode.row()));
		}
		assertTrue(DrillBoreMode.CROSS.column() && DrillBoreMode.CROSS.row());
		assertTrue(DrillBoreMode.COLUMN.column() && !DrillBoreMode.COLUMN.row());
		assertTrue(!DrillBoreMode.ROW.column() && DrillBoreMode.ROW.row());
		assertFalse(DrillBoreMode.SINGLE.column() || DrillBoreMode.SINGLE.row());
	}

	/** @implements MOD-821 — each mode names its own action-bar message key, all distinct */
	@Test
	void messageKeysAreDistinct() {
		assertEquals(4, java.util.Arrays.stream(DrillBoreMode.values()).map(DrillBoreMode::key).distinct().count());
	}
}
