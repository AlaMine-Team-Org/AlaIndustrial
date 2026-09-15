package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.ReactorControllerScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text and the dark message box the reactor screen's pages share (MOD-619).
 *
 * <p>The «Console» tab's advice and the «Room» tab's diagnosis are the same object — a title in the state's
 * tone, a badge that carries the tone in a shape as well as a colour, and a paragraph that shrinks as a whole
 * rather than being cut mid-sentence — so they are drawn by one method, and a fix to one is a fix to both.
 */
final class ReactorPageText {

	static final int FILL_GREEN = 0xFF4E9E52;
	static final int FILL_AMBER = 0xFFD9A33A;
	static final int FILL_RED = 0xFFD63A2A;
	static final int FILL_IDLE = 0xFF6B7178;

	/** Below this a translated line is no longer readable at GUI scale 2, so it is clipped instead. */
	static final float MIN_SCALE = dev.alaindustrial.client.screen.tabs.PageText.MIN_SCALE;
	static final float BODY_SCALE = 0.75f;
	static final int LINE_H = 9;

	private static final int BOX_BACK = 0xFF2A2D33;
	private static final int BOX_TEXT = 0xFFD7DBE0;
	/** The tone badge in front of a box's title: a 10×10 plate with an 8×8 pixel glyph. */
	private static final int BADGE_SIZE = 10;
	static final int PLATE_EDGE = 0xFF111316;
	static final int GLYPH = 0xFFFFFFFF;

	private ReactorPageText() {
	}

	/** One line at a scale — the shared {@code PageText.scaled} since the teleporter remote needed it too (MOD-628). */
	static void scaled(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float scale,
			int colour) {
		dev.alaindustrial.client.screen.tabs.PageText.scaled(graphics, font, text, x, y, scale, colour);
	}

	/** One line, shrunk to fit a width — but never below {@link #MIN_SCALE}. */
	static void scaledFit(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width,
			int colour) {
		dev.alaindustrial.client.screen.tabs.PageText.scaledFit(graphics, font, text, x, y, width, colour);
	}

	/** The scale the stack tabs set their rows in. */
	static final float SMALL = 0.75f;

	/** One line at {@link #SMALL}, shrunk further only if it would not fit its width. */
	static void small(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int colour) {
		float fit = (float) width / Math.max(1, font.width(text));
		scaled(graphics, font, text, x, y, Math.max(MIN_SCALE, Math.min(SMALL, fit)), colour);
	}

	/**
	 * Label on the left, value on the right, both at {@link #SMALL}. The label shrinks further only if it would not
	 * fit: at full size a short label ("Water") stood a size above its own value and above the long ones (MOD-620).
	 */
	static void row(GuiGraphicsExtractor graphics, Font font, int x, int y, int right, Component label,
			Component value, int valueColour) {
		int valueW = Math.round(font.width(value) * SMALL);
		scaled(graphics, font, value, right - valueW, y, SMALL, valueColour);
		small(graphics, font, label, x, y, Math.max(1, right - valueW - 3 - x), GuiStyle.TEXT_DIM);
	}

	/** One dim line centred in a width, for a panel with nothing to show yet. */
	static void centred(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width) {
		int shown = Math.min(width - 8, Math.round(font.width(text) * SMALL));
		scaledFit(graphics, font, text, x + (width - shown) / 2, y, width - 8, GuiStyle.TEXT_DIM);
	}

	/**
	 * The dark box: a tone badge, a title in the tone's colour, and paragraphs wrapped under it — shrunk
	 * together if a translation runs long, and clipped only once they reach {@link #MIN_SCALE}.
	 */
	static void messageBox(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
			ReactorConsole.Tone tone, Component title, List<Component> paragraphs) {
		graphics.fill(x, y, x + width, y + height, BOX_BACK);
		toneBadge(graphics, x + 5, y + 3, tone);

		int titleX = x + 5 + BADGE_SIZE + 4;
		scaledFit(graphics, font, title, titleX, y + 4, x + width - 4 - titleX, ReactorControllerScreen.toneColour(tone));

		int textX = x + 6;
		int textW = width - 10;
		int top = y + 16;
		int room = y + height - 2 - top;
		float scale = BODY_SCALE;
		List<FormattedCharSequence> lines = split(font, paragraphs, textW, scale);
		while (lines.size() * LINE_H * scale > room && scale > MIN_SCALE) {
			scale = Math.max(MIN_SCALE, scale - 0.05f);
			lines = split(font, paragraphs, textW, scale);
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(textX, top);
		graphics.pose().scale(scale, scale);
		for (int i = 0; i < lines.size() && (i + 1) * LINE_H * scale <= room + 0.5f; i++) {
			graphics.text(font, lines.get(i), 0, i * LINE_H, BOX_TEXT, false);
		}
		graphics.pose().popMatrix();
	}

	/**
	 * The tone as a small plate with a glyph: a tick when all is well, an exclamation mark for a warning or an
	 * alarm, a pause sign for an idle reactor. It replaced a two-pixel stripe down the box's left edge that the
	 * owner found untidy, and it carries the tone in a shape as well as a colour.
	 */
	static void toneBadge(GuiGraphicsExtractor graphics, int x, int y, ReactorConsole.Tone tone) {
		int plate = switch (tone) {
			case GOOD -> FILL_GREEN;
			case WARN -> FILL_AMBER;
			case ALARM -> FILL_RED;
			case IDLE -> FILL_IDLE;
		};
		String[] glyph = switch (tone) {
			case GOOD -> GLYPH_TICK;
			case WARN, ALARM -> GLYPH_EXCLAMATION;
			case IDLE -> GLYPH_PAUSE;
		};
		graphics.fill(x, y, x + BADGE_SIZE, y + BADGE_SIZE, PLATE_EDGE);
		graphics.fill(x + 1, y + 1, x + BADGE_SIZE - 1, y + BADGE_SIZE - 1, plate);
		glyph(graphics, x + 1, y + 1, glyph, GLYPH);
	}

	/** A pixel glyph: one GUI pixel per {@code #}. */
	static void glyph(GuiGraphicsExtractor graphics, int x, int y, String[] rows, int colour) {
		for (int row = 0; row < rows.length; row++) {
			for (int col = 0; col < rows[row].length(); col++) {
				if (rows[row].charAt(col) == '#') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, colour);
				}
			}
		}
	}

	private static List<FormattedCharSequence> split(Font font, List<Component> paragraphs, int width, float scale) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Component paragraph : paragraphs) {
			lines.addAll(font.split(paragraph, (int) (width / scale)));
		}
		return lines;
	}

	private static final String[] GLYPH_TICK = {
			"........",
			".......#",
			"......##",
			"#....##.",
			"##..##..",
			".####...",
			"..##....",
			"........",
	};
	private static final String[] GLYPH_EXCLAMATION = {
			"...##...",
			"...##...",
			"...##...",
			"...##...",
			"...##...",
			"........",
			"...##...",
			"........",
	};
	private static final String[] GLYPH_PAUSE = {
			"........",
			".##..##.",
			".##..##.",
			".##..##.",
			".##..##.",
			".##..##.",
			".##..##.",
			"........",
	};
}
