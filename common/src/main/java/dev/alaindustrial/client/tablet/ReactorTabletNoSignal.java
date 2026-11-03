package dev.alaindustrial.client.tablet;

import dev.alaindustrial.block.entity.reactor.ReactorTabletLink;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the tablet's screen shows while it does not hear its reactor (MOD-946): one card per reason, with an icon, the
 * reason as a title and one short line on what to do, over a dim crawl of interference.
 *
 * <p>The card is opaque and covers the whole panel, so nothing of the reactor's panel shows through: no tab title,
 * no page buttons, no greyed numbers. The screen draws it in the render pass, after the page widgets, so it covers them
 * too; the screen also stops passing the mouse to the panel, so nothing hidden answers a hover or a click.
 *
 * <p>The reasons are {@link ReactorTabletLink.State}, read from the tablet's menu, the one source of the link.
 */
public final class ReactorTabletNoSignal {

	/** Side of one interference cell, in panel pixels. */
	private static final int NOISE_CELL = 4;
	/** How often the interference changes, in milliseconds. */
	private static final long NOISE_FRAME_MS = 60L;
	private static final int SCREEN_BACK = 0xFF0C1114;
	private static final int CARD_BACK = 0xF0141B20;
	private static final int CARD_EDGE = 0xFF2E3A42;
	private static final int ICON_PLATE = 0xFF1E272D;
	private static final int HEADER_INK = 0xFFE5483A;
	private static final int TITLE_INK = 0xFFE8B04A;
	private static final int HINT_INK = 0xFFC9D1D8;
	private static final int CARD_W = 184;
	private static final int ICON_SCALE = 2;

	private ReactorTabletNoSignal() {
	}

	/** The lang key of the line that says what to do about {@code state}. */
	public static String hintKey(ReactorTabletLink.State state) {
		return "gui.alaindustrial.reactor_tablet.hint." + state.name().toLowerCase(Locale.ROOT);
	}

	/** The picture of the reason: the thing the player has to deal with. */
	static ItemStack icon(ReactorTabletLink.State state) {
		return switch (state) {
			case UNBOUND, NO_ANTENNA -> new ItemStack(ModContent.REACTOR_ANTENNA.get());
			case DISCHARGED -> new ItemStack(ModContent.CHARGE_PAD.get());
			case ANTENNA_UNPOWERED -> new ItemStack(ModContent.COPPER_CABLE.get());
			case OUT_OF_RANGE -> new ItemStack(Items.COMPASS);
			case OTHER_DIMENSION -> new ItemStack(Items.ENDER_EYE);
			case NOT_RESPONDING, LINKED -> new ItemStack(ModContent.REACTOR_CONTROLLER.get());
		};
	}

	/** The whole panel area: interference, then the card for {@code state} in the middle. */
	public static void draw(GuiGraphicsExtractor g, Font font, int left, int top, int width, int height,
			ReactorTabletLink.State state, long ms) {
		g.fill(left, top, left + width, top + height, SCREEN_BACK);
		drawInterference(g, left, top, width, height, ms);

		Component header = Component.translatable("gui.alaindustrial.reactor_tablet.no_signal");
		Component title = Component.translatable(state.translationKey());
		List<FormattedCharSequence> hint = font.split(Component.translatable(hintKey(state)), CARD_W - 16);
		int iconSize = 16 * ICON_SCALE;
		int cardH = 10 + font.lineHeight + 8 + iconSize + 8 + font.lineHeight + 6 + hint.size() * (font.lineHeight + 1)
				+ 8;
		int cardL = left + (width - CARD_W) / 2;
		int cardT = top + (height - cardH) / 2;
		g.fill(cardL - 1, cardT - 1, cardL + CARD_W + 1, cardT + cardH + 1, CARD_EDGE);
		g.fill(cardL, cardT, cardL + CARD_W, cardT + cardH, CARD_BACK);

		int y = cardT + 10;
		drawCentred(g, font, header, cardL, y, HEADER_INK);
		y += font.lineHeight + 8;
		int iconL = cardL + (CARD_W - iconSize) / 2;
		g.fill(iconL - 3, y - 3, iconL + iconSize + 3, y + iconSize + 3, ICON_PLATE);
		g.pose().pushMatrix();
		g.pose().translate(iconL, y);
		g.pose().scale(ICON_SCALE, ICON_SCALE);
		g.item(icon(state), 0, 0);
		g.pose().popMatrix();
		y += iconSize + 8;
		drawCentred(g, font, title, cardL, y, TITLE_INK);
		y += font.lineHeight + 6;
		for (FormattedCharSequence line : hint) {
			g.text(font, line, cardL + (CARD_W - font.width(line)) / 2, y, HINT_INK, false);
			y += font.lineHeight + 1;
		}
	}

	private static void drawCentred(GuiGraphicsExtractor g, Font font, Component text, int cardL, int y, int ink) {
		g.text(font, text, cardL + (CARD_W - font.width(text)) / 2, y, ink, false);
	}

	/** A dim, slow crawl of grey cells and one rolling scan line: the look of a lost carrier, kept quiet. */
	private static void drawInterference(GuiGraphicsExtractor g, int left, int top, int width, int height, long ms) {
		long frame = ms / NOISE_FRAME_MS;
		for (int y = 0; y < height; y += NOISE_CELL) {
			for (int x = 0; x < width; x += NOISE_CELL) {
				int grey = 0x14 + (int) (hash(x, y, frame) & 0x1F);
				g.fill(left + x, top + y, left + Math.min(x + NOISE_CELL, width),
						top + Math.min(y + NOISE_CELL, height), 0xFF000000 | grey << 16 | grey << 8 | grey);
			}
		}
		int scan = (int) (frame % (height / 2)) * 2;
		g.fill(left, top + scan, left + width, top + scan + 1, 0x40FFFFFF);
	}

	/** A cheap, stable hash of a cell and a frame: the same cell in the same frame always draws the same grey. */
	private static long hash(int x, int y, long frame) {
		long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ frame * 0x165667B19E3779F9L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ h >>> 32;
	}
}
