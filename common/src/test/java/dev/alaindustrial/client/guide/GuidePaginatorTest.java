package dev.alaindustrial.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link GuidePaginator} — the Guide Book entry-list page math (MOD-180). Pure ints,
 * no Minecraft on the classpath.
 *
 * <p>Pins the pagination-overlap bug: the Energy tab has 12 entries and, at a window height giving
 * {@code perPage == 8}, the old code clamped the scroll offset to {@code count - perPage == 4}, so
 * the second page started at entry 4 and re-showed entries 4–7 (three solar panels + the battery box)
 * that page one already listed. Page-aligned starts must tile the list with no overlap.
 */
class GuidePaginatorTest {

	@Test
	void pageCountRoundsUp() {
		assertEquals(1, GuidePaginator.pageCount(0, 8));
		assertEquals(1, GuidePaginator.pageCount(8, 8));
		assertEquals(2, GuidePaginator.pageCount(9, 8));
		assertEquals(2, GuidePaginator.pageCount(12, 8));
	}

	/** The exact reported case: 12 Energy entries at perPage 8 — the second page must start at 8, not 4. */
	@Test
	void energyTabSecondPageDoesNotOverlap() {
		int count = 12;
		int perPage = 8;
		// Page 1.
		assertEquals(0, GuidePaginator.pageStart(0, perPage, count));
		assertEquals(8, GuidePaginator.shownCount(0, perPage, count));
		// "Next" advances the raw offset by perPage; it must land on page 2 starting at entry 8.
		int offset = 0 + perPage; // 8
		assertEquals(8, GuidePaginator.pageStart(offset, perPage, count));
		assertEquals(4, GuidePaginator.shownCount(offset, perPage, count));
		// Two pages total, and page 2 is the last (no further Next).
		assertEquals(2, GuidePaginator.pageCount(count, perPage));
		assertEquals(1, GuidePaginator.currentPage(offset, perPage, count));
	}

	/** Whatever the count/perPage, walking pages by +perPage tiles every entry exactly once. */
	@Test
	void pagesTileWithoutOverlapOrGaps() {
		int[][] cases = {{12, 8}, {13, 6}, {12, 6}, {7, 3}, {1, 5}, {20, 7}};
		for (int[] c : cases) {
			int count = c[0];
			int perPage = c[1];
			Set<Integer> seen = new HashSet<>();
			int pages = GuidePaginator.pageCount(count, perPage);
			for (int p = 0; p < pages; p++) {
				int offset = p * perPage;
				int start = GuidePaginator.pageStart(offset, perPage, count);
				assertEquals(p * perPage, start, "page start must be page-aligned");
				int shown = GuidePaginator.shownCount(offset, perPage, count);
				for (int k = 0; k < shown; k++) {
					assertTrue(seen.add(start + k),
							"entry " + (start + k) + " shown twice for count=" + count + " perPage=" + perPage);
				}
			}
			assertEquals(count, seen.size(),
					"every entry shown exactly once for count=" + count + " perPage=" + perPage);
		}
	}

	/** A single-page tab (perPage >= count) has no second page to advance to. */
	@Test
	void singlePageHasNoNext() {
		assertEquals(1, GuidePaginator.pageCount(5, 8));
		assertEquals(0, GuidePaginator.currentPage(0, 8, 5));
		assertEquals(5, GuidePaginator.shownCount(0, 8, 5));
	}

	/** After a resize the stored offset may not be a page multiple — it snaps to the enclosing page. */
	@Test
	void offsetSnapsToPageBoundary() {
		assertEquals(0, GuidePaginator.pageStart(7, 8, 12));  // still page 0
		assertEquals(8, GuidePaginator.pageStart(9, 8, 12));  // rounds into page 1
		// An offset past the end clamps to the last page, never beyond it.
		assertEquals(8, GuidePaginator.pageStart(999, 8, 12));
		assertFalse(GuidePaginator.currentPage(999, 8, 12) >= GuidePaginator.pageCount(12, 8));
	}
}
