package dev.alaindustrial.client.screen.shiftworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.client.screen.SettlementCoreLayout;
import org.junit.jupiter.api.Test;

/**
 * L1: the shift worker's card (MOD-854) keeps its modules inside the panel — the record module grows by a row for a
 * dead worker, and the stats module under it still ends above the panel's bottom margin — and the screen's «+»
 * buttons land on the rows the page draws, because both read {@link ShiftWorkerCardLayout}.
 */
class ShiftWorkerCardLayoutTest {

	private static final int STATS = 4;

	@Test
	void aDeadWorkerHasOneRecordRowMore() {
		assertEquals(ShiftWorkerCardLayout.ROW, ShiftWorkerCardLayout.recordHeight(false)
				- ShiftWorkerCardLayout.recordHeight(true));
		assertEquals(51, ShiftWorkerCardLayout.recordHeight(true));
	}

	@Test
	void modulesFollowEachOtherWithOneGap() {
		for (boolean alive : new boolean[] {true, false}) {
			assertEquals(ShiftWorkerCardLayout.TOP + ShiftWorkerCardLayout.recordHeight(alive)
					+ ShiftWorkerCardLayout.GAP, ShiftWorkerCardLayout.rankTop(alive));
			assertEquals(ShiftWorkerCardLayout.rankTop(alive) + ShiftWorkerCardLayout.RANK_H
					+ ShiftWorkerCardLayout.GAP, ShiftWorkerCardLayout.statsTop(alive));
		}
	}

	@Test
	void theLastStatRowEndsAboveThePanelsBottomMargin() {
		for (boolean alive : new boolean[] {true, false}) {
			int lastRowBottom = ShiftWorkerCardLayout.statRowY(alive, STATS - 1) + ShiftWorkerCardLayout.STAT_ROW;
			assertTrue(lastRowBottom + 3 <= ShiftWorkerCardLayout.PANEL_H - ShiftWorkerCardLayout.MARGIN,
					"stats end at " + lastRowBottom + " for alive=" + alive);
		}
		assertEquals(ShiftWorkerCardLayout.statsTop(true) + ShiftWorkerCardLayout.STAT_FIRST
				+ 2 * ShiftWorkerCardLayout.STAT_ROW, ShiftWorkerCardLayout.statRowY(true, 2));
	}

	@Test
	void theRightColumnEndsAtTheMargin() {
		assertEquals(ShiftWorkerCardLayout.PANEL_W - ShiftWorkerCardLayout.MARGIN,
				ShiftWorkerCardLayout.RIGHT_X + ShiftWorkerCardLayout.rightWidth());
		assertTrue(ShiftWorkerCardLayout.LEFT_X + ShiftWorkerCardLayout.LEFT_W < ShiftWorkerCardLayout.RIGHT_X);
	}

	@Test
	void aRosterScrollsOnlyPastAFullPanel() {
		assertEquals(0, SettlementCoreLayout.maxRosterScroll(0));
		assertEquals(0, SettlementCoreLayout.maxRosterScroll(SettlementCoreLayout.ROSTER_ROWS));
		assertEquals(3, SettlementCoreLayout.maxRosterScroll(SettlementCoreLayout.ROSTER_ROWS + 3));
	}
}
