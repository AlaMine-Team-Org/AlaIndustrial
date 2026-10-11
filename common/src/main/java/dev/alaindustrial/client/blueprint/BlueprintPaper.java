package dev.alaindustrial.client.blueprint;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The look of the blueprint sheet (MOD-797): its colours and the few drawing primitives a technical
 * drawing is made of — grid, double frame, dashed leader, numbered balloon, title block. Kept apart from
 * {@link BlueprintScreen} so the screen reads as layout and behaviour only.
 */
final class BlueprintPaper {

	/** Cyanotype blue of the sheet. */
	static final int PAPER = 0xFF14457F;
	/** Darker blue for boxes laid on the sheet. */
	static final int PAPER_DARK = 0xF0103A6C;
	/** White drawing ink. */
	static final int INK = 0xFFFFFFFF;
	/** Faint ink for the grid and secondary rules. */
	static final int INK_FAINT = 0x40FFFFFF;
	/** Grid ink, fainter still. */
	static final int GRID = 0x16FFFFFF;
	/** Red pencil for a wrong arrangement. */
	static final int RED = 0xFFFF7A6A;
	/** The mod's energy orange, for the flow dots. */
	static final int ENERGY = 0xFFFF8A1E;

	private static final int CELL = 16;
	private static final int DASH = 3;
	private static final int GAP = 2;

	private BlueprintPaper() {
	}

	/** Fill the screen with the sheet: blue, a fine grid with every fourth line stronger, a double frame. */
	static void sheet(GuiGraphicsExtractor g, int width, int height) {
		g.fill(0, 0, width, height, PAPER);
		for (int x = 0, i = 0; x < width; x += CELL, i++) {
			g.fill(x, 0, x + 1, height, i % 4 == 0 ? 0x20FFFFFF : GRID);
		}
		for (int y = 0, i = 0; y < height; y += CELL, i++) {
			g.fill(0, y, width, y + 1, i % 4 == 0 ? 0x20FFFFFF : GRID);
		}
		g.outline(6, 6, width - 12, height - 12, INK);
		g.outline(9, 9, width - 18, height - 18, INK_FAINT);
	}

	/** A dashed vertical rule from {@code top} to {@code bottom}. */
	static void dashedV(GuiGraphicsExtractor g, int x, int top, int bottom) {
		for (int y = top; y < bottom; y += DASH + GAP) {
			g.fill(x, y, x + 1, Math.min(bottom, y + DASH), INK);
		}
	}

	/** A dashed horizontal rule from {@code left} to {@code right}. */
	static void dashedH(GuiGraphicsExtractor g, int left, int right, int y) {
		for (int x = left; x < right; x += DASH + GAP) {
			g.fill(x, y, Math.min(right, x + DASH), y + 1, INK);
		}
	}

	/** The item-number balloon of a drawing, centred on ({@code x}, {@code y}). */
	static void balloon(GuiGraphicsExtractor g, Font font, int x, int y, int number) {
		g.fill(x - 7, y - 7, x + 8, y + 8, PAPER);
		g.outline(x - 7, y - 7, 15, 15, INK);
		g.centeredText(font, Integer.toString(number), x + 1, y - 3, INK);
	}

	/** The title block in the lower right corner: the mod's name and the sheet number. */
	static void titleBlock(GuiGraphicsExtractor g, Font font, int width, int bottom, Component sheetOf) {
		int w = 150;
		int h = 26;
		int x = width - 14 - w;
		int y = bottom - h;
		g.fill(x, y, x + w, y + h, PAPER_DARK);
		g.outline(x, y, w, h, INK);
		g.fill(x, y + 13, x + w, y + 14, INK_FAINT);
		g.text(font, Component.translatable("blueprint.alaindustrial.ui.title_block"), x + 5, y + 3, INK, false);
		g.text(font, sheetOf, x + 5, y + 16, INK, false);
	}
}
