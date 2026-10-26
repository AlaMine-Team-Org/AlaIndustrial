package dev.alaindustrial.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 for the rows of the core's «Chronicle» tab (MOD-890): a row is as tall as its sentence, so the hover covers every
 * line of it and the next row starts under it; the view shows whole rows; the last row can be scrolled to.
 */
class ChronicleRowsTest {

	@Test
	void aRowIsAsTallAsItsLines() {
		int one = ChronicleRows.height(1, 1.0f);
		int two = ChronicleRows.height(2, 1.0f);
		int threeSmall = ChronicleRows.height(3, 0.7f);
		assertEquals(SettlementCoreLayout.LINE_STEP + SettlementCoreLayout.CHRONICLE_GAP, one);
		assertEquals(2 * SettlementCoreLayout.LINE_STEP + SettlementCoreLayout.CHRONICLE_GAP, two);
		assertTrue(threeSmall >= 3 * 7 + SettlementCoreLayout.CHRONICLE_GAP,
				"three lines at 0.7 take " + threeSmall + " px");
		// The case of the owner's screenshot: a three-line entry must not run into the next row.
		List<ChronicleRows.Row> rows = ChronicleRows.visible(2, 0, 200, i -> i == 0 ? threeSmall : two);
		assertEquals(rows.get(0).top() + rows.get(0).height(), rows.get(1).top());
		for (int y = rows.get(0).top(); y < rows.get(0).top() + threeSmall; y++) {
			assertEquals(0, ChronicleRows.rowAt(rows, y), "y " + y + " of the first entry");
		}
	}

	@Test
	void theViewShowsWholeRowsAndScrollsToTheLast() {
		int view = SettlementCoreLayout.CHRONICLE_VIEW;
		int h = ChronicleRows.height(2, 1.0f);
		int fit = view / h;
		List<ChronicleRows.Row> rows = ChronicleRows.visible(50, 0, view, i -> h);
		assertEquals(fit, rows.size());
		assertTrue(rows.getLast().top() + rows.getLast().height() <= view);
		int max = ChronicleRows.maxScroll(50, view, i -> h);
		assertEquals(50 - fit, max);
		assertEquals(49, ChronicleRows.visible(50, max, view, i -> h).getLast().index());
		assertEquals(0, ChronicleRows.maxScroll(3, view, i -> h), "a short chronicle does not scroll");
		assertEquals(0, ChronicleRows.maxScroll(0, view, i -> h));
		assertEquals(-1, ChronicleRows.rowAt(rows, view + 5));
	}
}
