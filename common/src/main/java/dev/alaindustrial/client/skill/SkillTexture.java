package dev.alaindustrial.client.skill;

import dev.alaindustrial.Industrialization;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The skill screen's texture atlas and how to stretch it (MOD-483).
 *
 * <p>The screen has to fit the window: at a large GUI scale the virtual screen can be under 280
 * pixels tall, and a panel of fixed height ran off both edges. So the atlas holds <b>parts</b> rather
 * than a finished screen, and this class assembles them at whatever size the screen asks for.
 *
 * <p>{@link #nineSlice} is the whole trick: corners are blitted at their own size, edges are repeated
 * along each side, and the middle is tiled. That keeps a one-pixel bevel crisp at any size — scaling
 * the whole thing instead would smear the highlight into a grey smudge, which is exactly what a
 * Minecraft frame must not look like.
 *
 * <p>Coordinates mirror {@code tools/gen_skill_tree_gui.py}. Change one, change both.
 */
public final class SkillTexture {

	public static final Identifier ATLAS = Industrialization.id("textures/gui/skill_tree.png");

	/** Atlas side. Both {@code blit} calls need it to turn pixel coordinates into UVs. */
	public static final int SIZE = 512;

	// --- nine-slice pieces: u, v, source side, border ---
	public static final int PANEL_U = 0;
	public static final int BOARD_U = 48;
	public static final int CARD_U = 96;
	public static final int STAT_U = 144;
	public static final int AVATAR_U = 192;
	public static final int PIECE = 48;
	public static final int PANEL_BORDER = 8;
	public static final int PIECE_BORDER = 4;
	public static final int STAT_H = 16;
	public static final int STAT_BORDER = 3;

	/** Header strip: repeated horizontally, its copper thread already in the bottom row. */
	public static final int HEADER_U = 240;
	public static final int HEADER_W = 16;
	public static final int HEADER_H = 22;

	/** Node sprites, four states in a row: taken, available, locked, shut by a fork. */
	public static final int NODES_V = 64;
	public static final int NODE = 20;

	/** The plate at the centre of the wheel — its own art, not a borrowed card. */
	public static final int HUB_U = 96;
	public static final int HUB = 28;

	/** Skill icons: one row per branch, one column per slot. */
	public static final int ICONS_V = 96;
	public static final int ICON = 16;

	private SkillTexture() {
	}

	/** Straight blit of one atlas region. */
	public static void piece(GuiGraphicsExtractor g, int u, int v, int w, int h, int x, int y) {
		g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, (float) u, (float) v, w, h, SIZE, SIZE);
	}

	/** Blit one atlas region scaled into a target rectangle. */
	private static void stretched(GuiGraphicsExtractor g, int u, int v, int su, int sv,
			int x, int y, int w, int h) {
		if (w <= 0 || h <= 0) {
			return;
		}
		// Argument order verified against the jar, not guessed:
		//   blit(pipeline, id, x, y, float u, float v, w, h, regionW, regionH, texW, texH)
		// Getting it wrong is silent — every parameter here is a number and int widens to float, so a
		// swapped pair compiles cleanly and simply draws the wrong part of the atlas.
		g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, (float) u, (float) v, w, h, su, sv, SIZE, SIZE);
	}

	/**
	 * Draw a nine-slice piece into {@code (x, y, w, h)}.
	 *
	 * <p>{@code side} is the square the piece occupies in the atlas, {@code border} how many pixels of
	 * it are corner. Everything between the corners is stretched, so the frame keeps its bevel exactly
	 * one pixel wide however large the panel gets.
	 */
	public static void nineSlice(GuiGraphicsExtractor g, int u, int v, int side, int border,
			int x, int y, int w, int h) {
		int inner = side - border * 2;
		int midW = w - border * 2;
		int midH = h - border * 2;

		// corners
		piece(g, u, v, border, border, x, y);
		piece(g, u + side - border, v, border, border, x + w - border, y);
		piece(g, u, v + side - border, border, border, x, y + h - border);
		piece(g, u + side - border, v + side - border, border, border, x + w - border, y + h - border);

		// edges
		stretched(g, u + border, v, inner, border, x + border, y, midW, border);
		stretched(g, u + border, v + side - border, inner, border,
				x + border, y + h - border, midW, border);
		stretched(g, u, v + border, border, inner, x, y + border, border, midH);
		stretched(g, u + side - border, v + border, border, inner,
				x + w - border, y + border, border, midH);

		// middle
		stretched(g, u + border, v + border, inner, inner, x + border, y + border, midW, midH);
	}

	/** Nine-slice with a non-square source — used by the short stat recess. */
	public static void nineSliceH(GuiGraphicsExtractor g, int u, int v, int sw, int sh, int border,
			int x, int y, int w, int h) {
		int innerW = sw - border * 2;
		int innerH = sh - border * 2;
		int midW = w - border * 2;
		int midH = h - border * 2;

		piece(g, u, v, border, border, x, y);
		piece(g, u + sw - border, v, border, border, x + w - border, y);
		piece(g, u, v + sh - border, border, border, x, y + h - border);
		piece(g, u + sw - border, v + sh - border, border, border, x + w - border, y + h - border);

		stretched(g, u + border, v, innerW, border, x + border, y, midW, border);
		stretched(g, u + border, v + sh - border, innerW, border,
				x + border, y + h - border, midW, border);
		stretched(g, u, v + border, border, innerH, x, y + border, border, midH);
		stretched(g, u + sw - border, v + border, border, innerH,
				x + w - border, y + border, border, midH);
		stretched(g, u + border, v + border, innerW, innerH, x + border, y + border, midW, midH);
	}

	/** The header strip, repeated across the panel's width. */
	public static void header(GuiGraphicsExtractor g, int x, int y, int w) {
		stretched(g, HEADER_U, 0, HEADER_W, HEADER_H, x, y, w, HEADER_H);
	}

	/**
	 * One node sprite, scaled to {@code size}.
	 *
	 * @param state 0 taken, 1 available, 2 locked, 3 shut by a fork
	 */
	public static void node(GuiGraphicsExtractor g, int state, int cx, int cy, int size) {
		stretched(g, state * NODE, NODES_V, NODE, NODE,
				cx - size / 2, cy - size / 2, size, size);
	}

	/** The wheel's hub plate, scaled to {@code size} and centred on {@code (cx, cy)}. */
	public static void hub(GuiGraphicsExtractor g, int cx, int cy, int size) {
		stretched(g, HUB_U, NODES_V, HUB, HUB, cx - size / 2, cy - size / 2, size, size);
	}

	/** One skill icon, scaled to {@code size}, centred on {@code (cx, cy)}. */
	public static void icon(GuiGraphicsExtractor g, int branch, int slot, int cx, int cy, int size) {
		stretched(g, slot * ICON, ICONS_V + branch * ICON, ICON, ICON,
				cx - size / 2, cy - size / 2, size, size);
	}
}
