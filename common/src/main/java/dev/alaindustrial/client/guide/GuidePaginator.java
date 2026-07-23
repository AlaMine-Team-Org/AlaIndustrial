package dev.alaindustrial.client.guide;

/**
 * Page math for the Guide Book entry list (MOD-180). Pure integer arithmetic, no Minecraft on the
 * classpath, so it is covered directly on L1.
 *
 * <p>Pins the pagination-overlap fix: the list scrolls in whole pages of {@code perPage} rows, and a
 * page's first entry is always {@code page * perPage}. The old code clamped the raw scroll offset to
 * {@code count - perPage}, so the final page started mid-way and re-showed the tail of the previous
 * page (three solar panels + the battery box appeared on both pages of the Energy tab). Snapping the
 * offset to a page boundary removes the overlap: every entry belongs to exactly one page.
 */
public final class GuidePaginator {
	private GuidePaginator() {
	}

	/** Number of pages needed to show {@code count} entries at {@code perPage} rows each (>= 1). */
	public static int pageCount(int count, int perPage) {
		int pp = Math.max(1, perPage);
		return Math.max(1, (count + pp - 1) / pp);
	}

	/** Zero-based page the raw scroll {@code offset} lands on, clamped into range. */
	public static int currentPage(int offset, int perPage, int count) {
		int pp = Math.max(1, perPage);
		int page = Math.max(0, offset) / pp;
		return Math.max(0, Math.min(page, pageCount(count, pp) - 1));
	}

	/** Index of the first entry shown on the current page — always a multiple of {@code perPage}. */
	public static int pageStart(int offset, int perPage, int count) {
		return currentPage(offset, perPage, count) * Math.max(1, perPage);
	}

	/** How many entries the current page actually shows (the last page may be short). */
	public static int shownCount(int offset, int perPage, int count) {
		int pp = Math.max(1, perPage);
		int start = pageStart(offset, pp, count);
		return Math.max(0, Math.min(pp, count - start));
	}
}
