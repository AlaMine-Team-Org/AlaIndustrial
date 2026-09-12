package dev.alaindustrial.client.guide;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Wrapping the guide book's prose without cutting a letter in half (MOD-607).
 *
 * <p>Vanilla's line breaker looks at exactly two codepoints — newline and space
 * ({@code StringSplitter.LineBreakFinder.accept}). When neither is within the line's width it breaks
 * on whatever character the width ran out on, and in a script that writes without spaces that
 * character is frequently a combining mark: a Thai vowel sign or tone mark renders on the consonant
 * before it, so a break between the two leaves the mark alone at the start of the next line, attached
 * to nothing.
 *
 * <p><b>Measured, not suspected.</b> At the guide book's normal column (356 px on a 1280×720 window
 * at GUI scale 2) the Thai book breaks 215 times and not one of them is bad — there is almost always
 * a space within reach. Squeeze the column to what the narrowest window gives (144 px) and the same
 * text breaks 732 times, 127 of them mid-word and <b>29 inside a grapheme cluster</b>. So the book
 * reads correctly on a comfortable window and is broken on a small one — the kind of defect nobody
 * reports because nobody with a large monitor can see it.
 *
 * <p><b>What this fixes and what it deliberately does not.</b> It moves a break back to the nearest
 * grapheme boundary, so a mark is never separated from its base. It does NOT teach the splitter where
 * Thai words end: that needs a dictionary, and vanilla's splitter has none. Breaking mid-word is ugly;
 * breaking mid-letter is wrong, and only the second one is fixed here.
 *
 * <p>Language-neutral on purpose: the boundary comes from {@link BreakIterator#getCharacterInstance()},
 * which knows Devanagari, Hangul, Arabic and emoji sequences too, so the same code protects every
 * locale the book ships in rather than special-casing the one that exposed the bug.
 *
 * <p><b>One oracle.</b> {@link #lineStarts} is where the decision lives; {@link #split} builds the
 * rendered lines from it and the audit stand reads the same method. A stand that re-derived the
 * positions from vanilla's splitter would be auditing a layout the screen no longer uses — the first
 * draft did exactly that and reported the unfixed numbers after the fix had landed.
 */
public final class GuideText {

	private GuideText() {
	}

	/**
	 * Where each rendered line of {@code paragraph} starts, at {@code width}.
	 *
	 * <p>Re-splits the remainder after every break rather than snapping vanilla's whole answer in one
	 * pass: moving a break earlier makes the NEXT line longer, and a line adjusted once could then
	 * overflow the column by the cluster it inherited. Paragraphs are a few hundred characters and the
	 * rows are built once per opened entry, not per frame, so the extra passes cost nothing a player
	 * can feel.
	 */
	public static List<Integer> lineStarts(Font font, String paragraph, int width) {
		List<Integer> starts = new ArrayList<>();
		starts.add(0);
		if (paragraph.isEmpty()) {
			return starts;
		}
		BreakIterator graphemes = BreakIterator.getCharacterInstance();
		graphemes.setText(paragraph);

		int position = 0;
		while (position < paragraph.length()) {
			String rest = paragraph.substring(position);
			List<Integer> raw = new ArrayList<>();
			font.getSplitter().splitLines(rest, width, Style.EMPTY, false,
					(style, from, to) -> raw.add(from));
			if (raw.size() <= 1) {
				break;                              // what is left fits on one line
			}
			int at = position + raw.get(1);
			int snapped = snapBack(graphemes, at, position);
			if (snapped <= position) {
				snapped = at;                        // a cluster wider than the whole column
			}
			if (snapped >= paragraph.length()) {
				break;
			}
			starts.add(snapped);
			position = snapped;
		}
		return starts;
	}

	/** The paragraph as rendered lines — {@link #lineStarts} turned into text. */
	public static List<FormattedCharSequence> split(Font font, String paragraph, int width) {
		List<Integer> starts = lineStarts(font, paragraph, width);
		List<FormattedCharSequence> lines = new ArrayList<>(starts.size());
		for (int i = 0; i < starts.size(); i++) {
			int from = starts.get(i);
			int to = i + 1 < starts.size() ? starts.get(i + 1) : paragraph.length();
			lines.add(Component.literal(paragraph.substring(from, to)).getVisualOrderText());
		}
		return lines;
	}

	/** The start of the grapheme {@code at} falls inside — or {@code at} itself when that is the line start. */
	private static int snapBack(BreakIterator graphemes, int at, int lineStart) {
		if (graphemes.isBoundary(at)) {
			return at;
		}
		int previous = graphemes.preceding(at);
		return previous > lineStart ? previous : at;
	}
}
