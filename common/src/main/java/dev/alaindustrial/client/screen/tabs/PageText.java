package dev.alaindustrial.client.screen.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Text at a scale, for the pages of a tabbed screen (MOD-619, shared since MOD-628).
 *
 * <p>A tab page sets its rows smaller than the vanilla font, and a translated line that runs long shrinks
 * rather than being cut mid-word — down to {@link #MIN_SCALE}, below which it would stop being readable.
 */
public final class PageText {

	/** Below this a translated line is no longer readable at GUI scale 2, so it is clipped instead. */
	public static final float MIN_SCALE = 0.6f;

	private PageText() {
	}

	/** One line at a scale, centred on the line the full-size glyphs would sit on. */
	public static void scaled(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float scale,
			int colour) {
		if (scale >= 1.0f) {
			graphics.text(font, text, x, y, colour, false);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y + (font.lineHeight - 1) * (1.0f - scale) / 2.0f);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, 0, 0, colour, false);
		graphics.pose().popMatrix();
	}

	/**
	 * The width to wrap a text at. Right-to-left script is measured before it is shaped, and Arabic's joined letter forms
	 * draw wider than the isolated ones the measure counted — a hint that "fit" on one line ran past the panel's edge
	 * (MOD-629). Such text wraps a tenth early; left-to-right text wraps at the width it is given.
	 */
	public static int wrapWidth(Component text, int width) {
		String plain = text.getString();
		for (int i = 0; i < plain.length(); ) {
			int codePoint = plain.codePointAt(i);
			byte direction = Character.getDirectionality(codePoint);
			if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
					|| direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
				return Math.round(width * 0.9f);
			}
			i += Character.charCount(codePoint);
		}
		return width;
	}

	/** One line, shrunk to fit a width — but never below {@link #MIN_SCALE}. */
	public static void scaledFit(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width,
			int colour) {
		int textW = font.width(text);
		float scale = textW > width ? Math.max(MIN_SCALE, (float) width / textW) : 1.0f;
		scaled(graphics, font, text, x, y, scale, colour);
	}
}
