package dev.alaindustrial.client.tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Breaks a long hover-tooltip line into several, the way vanilla's own long descriptions read (owner's round 4 of
 * 0.1.219, R4-3): a tooltip one sentence wide stretches the box across half the screen.
 *
 * <p>The width is counted in narrow-glyph units, not pixels: an item's {@code appendHoverText} is common code and may
 * not reach the client's font. A letter counts one unit, a wide glyph (the CJK scripts, full-width forms) two, so a
 * Japanese line wraps about where a Russian one does.
 *
 * <p>A wrapped line keeps the colour of the line it came from, the style of its root. A line whose translation key
 * the running language does not know is left whole: wrapping the bare key — what a dedicated server or a test
 * sees — would only cut a key into pieces.
 */
public final class TooltipWrap {

	/** The widest a tooltip line runs, in units: about the width of vanilla's long item descriptions. */
	public static final int MAX_UNITS = 44;

	private TooltipWrap() {
	}

	/** {@code line} as it is shown: itself when it fits, or its text in several lines of its colour. */
	public static List<Component> wrap(Component line) {
		if (line.getContents() instanceof TranslatableContents translatable
				&& !Language.getInstance().has(translatable.getKey())) {
			return List.of(line);
		}
		String text = line.getString();
		if (units(text) <= MAX_UNITS) {
			return List.of(line);
		}
		List<Component> out = new ArrayList<>();
		for (String part : balanced(text, MAX_UNITS)) {
			out.add(Component.literal(part).setStyle(line.getStyle()));
		}
		return out;
	}

	/** Hands {@code line} to {@code adder} wrapped: the hook an item's {@code appendHoverText} writes through. */
	public static void add(Consumer<Component> adder, Component line) {
		wrap(line).forEach(adder);
	}

	/**
	 * {@code text} in as few lines of at most {@code max} units as it needs, the lines as even as the words allow: a
	 * line of 47 units reads as two of about 24, not as 44 and a lone word.
	 */
	static List<String> balanced(String text, int max) {
		int lines = split(text, max).size();
		int total = units(text);
		for (int width = (total + lines - 1) / lines; width < max; width++) {
			List<String> even = split(text, width);
			if (even.size() <= lines) {
				return even;
			}
		}
		return split(text, max);
	}

	/**
	 * {@code text} broken into lines of at most {@code max} units: at spaces where it can, inside a word only when the
	 * word alone is wider than a line (scripts written without spaces).
	 */
	static List<String> split(String text, int max) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!line.isEmpty() && units(line) + 1 + units(word) <= max) {
				line.append(' ').append(word);
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
				line.setLength(0);
			}
			String rest = word;
			while (units(rest) > max) {
				int cut = cutPoint(rest, max);
				lines.add(rest.substring(0, cut));
				rest = rest.substring(cut);
			}
			line.append(rest);
		}
		if (!line.isEmpty() || lines.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** Where to cut a word wider than {@code max}: as many whole code points as fit, at least one. */
	private static int cutPoint(String word, int max) {
		int used = 0;
		int index = 0;
		while (index < word.length()) {
			int codePoint = word.codePointAt(index);
			int width = width(codePoint);
			if (index > 0 && used + width > max) {
				break;
			}
			used += width;
			index += Character.charCount(codePoint);
		}
		return index;
	}

	/** The width of {@code text} in units. */
	static int units(CharSequence text) {
		return text.codePoints().map(TooltipWrap::width).sum();
	}

	private static int width(int codePoint) {
		Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
		boolean wide = script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
				|| script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
				|| codePoint >= 0xFF00 && codePoint <= 0xFFEF || codePoint >= 0x3000 && codePoint <= 0x303F;
		return wide ? 2 : 1;
	}
}
