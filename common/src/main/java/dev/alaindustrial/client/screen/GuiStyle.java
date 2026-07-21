package dev.alaindustrial.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared visual language for Industrialization machine screens, built on the 26.2 render-state
 * model ({@link GuiGraphicsExtractor#fill}). The look is a light control-panel face with a bevel
 * and a single copper accent strip under the header (the signature element), recessed slots, and
 * an amber energy bar. Text stays dark for legibility on the light panel (matching vanilla labels).
 */
public final class GuiStyle {
	private GuiStyle() {
	}

	public static final int PANEL = 0xFFC6C6C6;
	public static final int PANEL_HI = 0xFFFDFDFD;
	public static final int PANEL_LO = 0xFF555555;
	public static final int SLOT = 0xFF8B8B8B;
	public static final int SLOT_EDGE = 0xFF373737;
	public static final int TRACK = 0xFF373737;
	public static final int ENERGY = 0xFFFF8A1E;
	public static final int ENERGY_HI = 0xFFFFC15A;
	public static final int COPPER = 0xFFB87333;
	public static final int TEXT = 0xFF3F3F3F;
	public static final int TEXT_DIM = 0xFF6B6B6B;

	/** Panel face with bevel border and a copper accent strip under the header. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL);
		g.fill(x, y, x + w, y + 1, PANEL_HI);
		g.fill(x, y, x + 1, y + h, PANEL_HI);
		g.fill(x, y + h - 1, x + w, y + h, PANEL_LO);
		g.fill(x + w - 1, y, x + w, y + h, PANEL_LO);
		g.fill(x + 5, y + 14, x + w - 5, y + 15, COPPER);
	}

	/** Recessed 18×18 slot background. Pass the slot's top-left (slot.x-1, slot.y-1). */
	public static void slot(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, SLOT_EDGE);
		g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
	}

	/** Vertical energy bar with border + sheen, filled by stored/capacity. */
	public static void energyBar(GuiGraphicsExtractor g, int x, int y, int w, int h, int stored, int capacity) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TRACK);
		int filled = capacity > 0 ? (int) ((long) stored * h / capacity) : 0;
		g.fill(x, y + h - filled, x + w, y + h, ENERGY);
		if (filled > 0) {
			g.fill(x, y + h - filled, x + 1, y + h, ENERGY_HI);
		}
	}

	/** Horizontal progress arrow track + fill (progress/maxProgress). */
	public static void progress(GuiGraphicsExtractor g, int x, int y, int w, int progress, int maxProgress) {
		g.fill(x, y, x + w, y + 6, TRACK);
		int filled = maxProgress > 0 ? progress * w / maxProgress : 0;
		g.fill(x, y, x + filled, y + 6, PANEL_HI);
	}
}
