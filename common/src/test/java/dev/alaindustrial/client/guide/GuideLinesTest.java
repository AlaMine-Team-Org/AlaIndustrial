package dev.alaindustrial.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link GuideLines}: the rendered lines of a guide-book paragraph never carry the
 * line feed they were broken on. Kept, it reached the font and showed as a boxed "LF" glyph after the
 * first three items of the "First steps" list in every language.
 *
 * @implements MOD-513-LF
 */
class GuideLinesTest {

	/** The "First steps" shape: a numbered list, one item per line. */
	@Test
	void listLinesLoseTheirLineFeeds() {
		String list = "1. Mine.\n2. Place.\n3. Run.\n4. Hook up.";
		assertEquals(List.of("1. Mine.", "2. Place.", "3. Run.", "4. Hook up."),
				GuideLines.slice(list, List.of(0, 9, 19, 27)));
	}

	/** A line broken on a space keeps what vanilla keeps; only the line feed is dropped. */
	@Test
	void onlyTheLineFeedIsDropped() {
		assertEquals(List.of("ab ", "cd"), GuideLines.slice("ab cd", List.of(0, 3)));
		assertEquals(List.of("tab\t", "x"), GuideLines.slice("tab\tx", List.of(0, 4)));
	}

	/** A line that is nothing but a line feed becomes empty; a paragraph's own trailing feed goes too. */
	@Test
	void edgesStayInPlace() {
		assertEquals(List.of("a", "", "b"), GuideLines.slice("a\n\nb", List.of(0, 2, 3)));
		assertEquals(List.of("one line"), GuideLines.slice("one line", List.of(0)));
		assertEquals(List.of(""), GuideLines.slice("", List.of(0)));
		assertEquals(List.of("ends"), GuideLines.slice("ends\n", List.of(0)));
	}
}
