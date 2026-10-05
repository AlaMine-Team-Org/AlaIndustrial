package dev.alaindustrial.client.compat;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * The page forms' card (MOD-716, batch 12e): the numbers and the height rule the JEI and the REI info
 * categories share — the twin of {@link RecipeViewerLayout} for the text pages of {@link RecipeViewerInfo}.
 * What each viewer still decides for itself is where the title and body sit inside the card (JEI leaves a
 * slot for the page's item, REI a title row) and its padding.
 */
public final class InfoPageLayout {

	/** Width of a page card. */
	public static final int WIDTH = 160;

	/** One body line, and the title row. */
	public static final int LINE_HEIGHT = 10;

	/**
	 * Floor for the reserved body lines, not a ceiling (MOD-422).
	 *
	 * <p>It used to be the ceiling, and that silently truncated nothing — it overflowed instead: the draw
	 * loop emits one line per visual line however many there are, and the panel neither clips nor scrolls,
	 * so everything past the reserved height was painted outside the background. English fit in eight lines,
	 * which is why the defect was invisible on the development language; the Russian "mutation grades" page
	 * measured nineteen. Height is derived from the real text ({@link #bodyLines}), and this constant only
	 * keeps short pages from collapsing.
	 */
	public static final int MIN_BODY_LINES = 8;

	/**
	 * Hard ceiling, derived from REI's own layout maths — not a taste call.
	 *
	 * <p>REI's {@code DefaultDisplayViewingScreen} sizes its window as
	 * {@code 36 + (displayHeight + 4) * (displaysPerPage + 1)}, so the screen a category demands grows with
	 * its height. Minecraft's smallest supported GUI is 240 logical pixels tall, which leaves
	 * {@code 240 - 36 - 4 = 200} for the card, i.e. seventeen body lines; a JEI card taller than that stops
	 * fitting the same window. Nothing shipped comes close (the longest page is eleven lines after MOD-422
	 * trimmed the mutation-grade text), so this is a backstop against a future edit, not a live constraint.
	 */
	public static final int MAX_BODY_LINES = 17;

	/** The page title — slightly darker than the body. */
	public static final int TITLE_COLOR = 0xFF303030;

	/** The page body. */
	public static final int BODY_COLOR = 0xFF404040;

	private InfoPageLayout() {
	}

	/**
	 * Body lines to reserve: the tallest of {@code pages}, wrapped at {@code textWidth} with the same splitter
	 * a category draws with, so the reservation cannot disagree with the drawing. Between
	 * {@link #MIN_BODY_LINES} and {@link #MAX_BODY_LINES}.
	 *
	 * <p>Measured live, not cached: the wrap depends on the active language and on the balance values
	 * interpolated into the lines, both of which change without the category being rebuilt. Falls back to
	 * the floor while the font is not up — a viewer can ask before the client finishes loading, and a crash
	 * there would take the whole recipe screen with it.
	 */
	public static int bodyLines(List<RecipeViewerInfo.Entry> pages, int textWidth) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.font == null) {
			return MIN_BODY_LINES;
		}
		StringSplitter splitter = client.font.getSplitter();
		int longest = MIN_BODY_LINES;
		for (RecipeViewerInfo.Entry page : pages) {
			int lines = 0;
			for (Component line : RecipeViewerInfo.buildLines(page)) {
				lines += splitter.splitLines(line, textWidth, Style.EMPTY).size();
			}
			longest = Math.max(longest, lines);
		}
		return Math.min(longest, MAX_BODY_LINES);
	}
}
