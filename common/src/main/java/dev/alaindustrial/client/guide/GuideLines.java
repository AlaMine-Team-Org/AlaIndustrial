package dev.alaindustrial.client.guide;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a guide-book paragraph into the text of its rendered lines, given where each line starts.
 *
 * <p>A line ends where the next one starts, and when the break was a newline that newline is the
 * last character of the slice. Vanilla's splitter drops the character it breaks on; {@link GuideText}
 * slices the paragraph itself (MOD-607), so it has to drop the newline too — kept, it reaches the
 * font, which draws it as a boxed "LF" glyph at the end of every line of a list.
 *
 * <p>Minecraft-free, so L1 covers it; {@link GuideText#split} turns the strings into rendered text.
 */
public final class GuideLines {

	private GuideLines() {
	}

	/** The text of each line of {@code paragraph}, the lines starting at {@code starts}, without line feeds. */
	public static List<String> slice(String paragraph, List<Integer> starts) {
		List<String> lines = new ArrayList<>(starts.size());
		for (int i = 0; i < starts.size(); i++) {
			int from = starts.get(i);
			int to = i + 1 < starts.size() ? starts.get(i + 1) : paragraph.length();
			if (to > from && paragraph.charAt(to - 1) == '\n') {
				to--;
			}
			lines.add(paragraph.substring(from, to));
		}
		return lines;
	}
}
