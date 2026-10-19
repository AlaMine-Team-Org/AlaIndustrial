package dev.alaindustrial.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Fits a translated line of text into a box of a screen: wrapped onto at most {@code maxLines} lines and, when
 * that is not enough, scaled down in steps to {@link #MIN_SCALE} (MOD-842).
 *
 * <p><b>Why not just scale, as the status rows do.</b> A single row scaled to 0.7 holds about 190 px of text in a
 * 134 px column; the settlement core's longest translations run past 280 px, and the playtest showed one of them
 * crossing the frame. Wrapping first keeps the text at full size wherever a second line fits.
 *
 * <p>Minecraft-free: the width of a string comes in as a function, {@code font::width} on the screen and the
 * vanilla font's advance table in the L1 test, so the test checks every language of the mod with the very
 * algorithm the screen draws with.
 */
public final class TextFit {

	/** The steps tried, largest first; 0.7 is the status row's floor ({@code MachineScreen.MIN_STATUS_SCALE}). */
	static final float[] SCALES = {1.0f, 0.9f, 0.8f, 0.7f};
	public static final float MIN_SCALE = 0.7f;

	private TextFit() {
	}

	/**
	 * The lines of a fitted text and the scale they are drawn at.
	 *
	 * @param lines the text of each line, in order
	 * @param scale the scale every line is drawn at
	 * @param fits  whether every line is at most the box width at that scale and there are at most
	 *              {@code maxLines} of them; false only when even {@link #MIN_SCALE} is not enough
	 */
	public record Fit(List<String> lines, float scale, boolean fits) {
	}

	/** {@code text} fitted into {@code width} pixels on at most {@code maxLines} lines. */
	public static Fit fit(String text, int width, int maxLines, ToIntFunction<String> measure) {
		List<String> lines = List.of();
		for (float scale : SCALES) {
			int room = (int) Math.floor(width / scale + 1.0e-4);
			lines = wrap(text, room, measure);
			if (lines.size() <= maxLines && widest(lines, measure) <= room) {
				return new Fit(lines, scale, true);
			}
		}
		return new Fit(lines, MIN_SCALE, false);
	}

	/**
	 * {@code text} broken into lines no wider than {@code room}: at spaces where it can, inside a word only when
	 * the word alone is wider than a line (scripts written without spaces), and never in front of a combining
	 * mark, which stays with the letter it belongs to.
	 */
	static List<String> wrap(String text, int room, ToIntFunction<String> measure) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ", -1)) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (measure.applyAsInt(candidate) <= room) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
				line.setLength(0);
			}
			String rest = word;
			while (measure.applyAsInt(rest) > room) {
				int cut = cutPoint(rest, room, measure);
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

	/** Where to break a word wider than {@code room}: as far as fits, moved back off a combining mark. */
	private static int cutPoint(String word, int room, ToIntFunction<String> measure) {
		int first = word.offsetByCodePoints(0, 1);
		int cut = first;
		while (cut < word.length()) {
			int after = word.offsetByCodePoints(cut, 1);
			if (measure.applyAsInt(word.substring(0, after)) > room) {
				break;
			}
			cut = after;
		}
		while (cut > first && cut < word.length() && isMark(word.codePointAt(cut))) {
			cut = word.offsetByCodePoints(cut, -1);
		}
		return cut;
	}

	private static boolean isMark(int codePoint) {
		int type = Character.getType(codePoint);
		return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
				|| type == Character.ENCLOSING_MARK;
	}

	private static int widest(List<String> lines, ToIntFunction<String> measure) {
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, measure.applyAsInt(line));
		}
		return widest;
	}
}
