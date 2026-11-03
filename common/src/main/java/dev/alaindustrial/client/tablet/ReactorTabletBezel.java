package dev.alaindustrial.client.tablet;

import dev.alaindustrial.block.entity.reactor.ReactorTabletLink;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The tablet's casing around the reactor panel (MOD-946), drawn with plain fills — no texture, no graphics backend
 * (ADR-015) — in the panel's own layout, so the screen's scale carries it.
 *
 * <p>Two passes: {@link #drawCasing} under the panel (body, grips, glass lip, link lamp, battery mark) and
 * {@link #drawOverGlass} over it (the alarm wash and the flicker of a weak signal). The casing is generic on purpose:
 * a later tier (MOD-947) changes its colours or marks without touching the hand pose or the screen's animation.
 */
public final class ReactorTabletBezel {

	/** Casing width left and right of the panel: room for the side tab strip, which sticks out 28 px. */
	public static final int SIDE = 32;
	/** Casing height above the panel: the label, the lamp and the battery mark. */
	public static final int TOP = 18;
	/** Casing height below the panel: the buttons and the speaker. */
	public static final int BOTTOM = 22;

	private static final int CASING = 0xFF2B3036;
	private static final int CASING_LIGHT = 0xFF4B525B;
	private static final int CASING_DARK = 0xFF15171A;
	private static final int GRIP = 0xFF1C1F23;
	private static final int GRIP_RIDGE = 0xFF33383F;
	private static final int GLASS_LIP = 0xFF0A0C0E;
	private static final int SCREW = 0xFF5A626B;
	private static final int LABEL = 0xFF8A949E;
	private static final int LAMP_GOOD = 0xFF5BD6A0;
	private static final int LAMP_WARN = 0xFFE8B04A;
	private static final int LAMP_ALARM = 0xFFE5483A;
	private static final int LAMP_OFF = 0xFF3A2422;
	private static final int BATTERY_INK = 0xFF9AA4AE;
	private static final int BATTERY_FILL = 0xFF5BD6A0;
	private static final int BATTERY_LOW = 0xFFE5483A;
	private static final int BUTTON = 0xFF3C424A;
	private static final int SIGNAL_DEAD = 0xFF3A4048;
	private static final int ALARM_RGB = 0xE5302A;
	private static final int WARN_RGB = 0xE8A030;

	private static final int BATTERY_SEGMENTS = 5;

	private ReactorTabletBezel() {
	}

	/**
	 * What the casing shows: the link and its reason from the menu, the console's verdict, the stack's charge
	 * (0 … 1).
	 */
	public record Status(boolean linked, ReactorTabletSignal.Alarm alarm, float charge, ReactorTabletLink.State link) {
	}

	/** The casing's left edge for a panel whose left edge is {@code panelLeft}. */
	public static int bodyX(int panelLeft) {
		return panelLeft - SIDE;
	}

	/** The casing's top edge for a panel whose top edge is {@code panelTop}. */
	public static int bodyY(int panelTop) {
		return panelTop - TOP;
	}

	public static int bodyWidth(int panelW) {
		return panelW + 2 * SIDE;
	}

	public static int bodyHeight(int panelH) {
		return panelH + TOP + BOTTOM;
	}

	/** The body, grips, glass lip and the marks of the top and bottom bezels — everything under the panel. */
	public static void drawCasing(GuiGraphicsExtractor g, Font font, Component label, int panelLeft, int panelTop,
			int panelW, int panelH, Status status, long ms) {
		int x0 = bodyX(panelLeft);
		int y0 = bodyY(panelTop);
		int x1 = x0 + bodyWidth(panelW);
		int y1 = y0 + bodyHeight(panelH);
		// Body with cut corners, lit from the top left.
		g.fill(x0 + 2, y0, x1 - 2, y1, CASING);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, CASING);
		g.fill(x0, y0 + 2, x1, y1 - 2, CASING);
		g.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 2, CASING_LIGHT);
		g.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 2, CASING_LIGHT);
		g.fill(x0 + 2, y1 - 2, x1 - 2, y1 - 1, CASING_DARK);
		g.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 2, CASING_DARK);
		// Rubber grips on the outer edges, where the hands hold it.
		for (int side = 0; side < 2; side++) {
			int gx = side == 0 ? x0 + 2 : x1 - 6;
			g.fill(gx, panelTop, gx + 4, panelTop + panelH, GRIP);
			for (int y = panelTop + 2; y < panelTop + panelH - 1; y += 3) {
				g.fill(gx, y, gx + 4, y + 1, GRIP_RIDGE);
			}
		}
		// Screws in the corners.
		g.fill(x0 + 5, y0 + 5, x0 + 7, y0 + 7, SCREW);
		g.fill(x1 - 7, y0 + 5, x1 - 5, y0 + 7, SCREW);
		g.fill(x0 + 5, y1 - 7, x0 + 7, y1 - 5, SCREW);
		g.fill(x1 - 7, y1 - 7, x1 - 5, y1 - 5, SCREW);
		// The glass lip the panel sits in.
		g.fill(panelLeft - 2, panelTop - 2, panelLeft + panelW + 2, panelTop + panelH + 2, GLASS_LIP);

		int textY = y0 + (TOP - font.lineHeight) / 2 + 1;
		g.text(font, label, panelLeft, textY, LABEL, false);
		// Right to left: battery, its percentage, the link lamp, the signal bars.
		int right = panelLeft + panelW;
		drawBattery(g, right - 17, y0 + 6, status.charge(), ms);
		String percent = Math.round(status.charge() * 100.0f) + "%";
		int percentX = right - 21 - font.width(percent);
		g.text(font, percent, percentX, textY,
				ReactorTabletSignal.isLow(status.charge()) ? BATTERY_LOW : LABEL, false);
		drawLamp(g, percentX - 10, y0 + 7, status, ms);
		drawSignalBars(g, percentX - 26, y0 + 5, status, ms);

		// Bottom bezel: three buttons in the middle, a speaker grille either side.
		int cx = panelLeft + panelW / 2;
		int by = panelTop + panelH + 8;
		for (int i = -1; i <= 1; i++) {
			g.fill(cx + i * 14 - 4, by, cx + i * 14 + 4, by + 5, BUTTON);
			g.fill(cx + i * 14 - 4, by + 5, cx + i * 14 + 4, by + 6, CASING_DARK);
		}
		for (int i = 0; i < 6; i++) {
			g.fill(panelLeft + 8 + i * 4, by + 2, panelLeft + 10 + i * 4, by + 4, CASING_DARK);
			g.fill(panelLeft + panelW - 10 - i * 4, by + 2, panelLeft + panelW - 8 - i * 4, by + 4, CASING_DARK);
		}
	}

	/** The alarm wash and the weak-signal flicker, over the panel and its lip. */
	public static void drawOverGlass(GuiGraphicsExtractor g, int panelLeft, int panelTop, int panelW, int panelH,
			Status status, long ms) {
		float wash = ReactorTabletSignal.wash(status.alarm(), ms);
		if (status.alarm() == ReactorTabletSignal.Alarm.ALARM) {
			g.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, argb(0x50 * wash, ALARM_RGB));
		}
		if (wash > 0.0f) {
			int lip = argb(0xC0 * wash, status.alarm() == ReactorTabletSignal.Alarm.ALARM ? ALARM_RGB : WARN_RGB);
			g.fill(panelLeft - 2, panelTop - 2, panelLeft + panelW + 2, panelTop, lip);
			g.fill(panelLeft - 2, panelTop + panelH, panelLeft + panelW + 2, panelTop + panelH + 2, lip);
			g.fill(panelLeft - 2, panelTop, panelLeft, panelTop + panelH, lip);
			g.fill(panelLeft + panelW, panelTop, panelLeft + panelW + 2, panelTop + panelH, lip);
		}
		float dropout = ReactorTabletSignal.dropout(status.linked(), status.alarm(), ms);
		if (dropout > 0.0f) {
			g.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, argb(0xFF * dropout, 0x000000));
		}
	}

	private static void drawLamp(GuiGraphicsExtractor g, int x, int y, Status status, long ms) {
		int colour;
		if (!ReactorTabletSignal.lampLit(status.linked(), status.alarm(), ms)) {
			colour = LAMP_OFF;
		} else if (!status.linked() || status.alarm() == ReactorTabletSignal.Alarm.ALARM) {
			colour = LAMP_ALARM;
		} else {
			colour = status.alarm() == ReactorTabletSignal.Alarm.WARN ? LAMP_WARN : LAMP_GOOD;
		}
		g.fill(x + 1, y, x + 4, y + 5, colour);
		g.fill(x, y + 1, x + 5, y + 4, colour);
	}

	/**
	 * Four rising bars: all lit while the tablet hears its reactor (coloured like the lamp), none and a red cross
	 * while it does not. The bars do not measure distance: the link is a yes or a no, and the reason is on the screen.
	 */
	private static void drawSignalBars(GuiGraphicsExtractor g, int x, int y, Status status, long ms) {
		int lit = status.linked() ? switch (status.alarm()) {
			case NONE -> LAMP_GOOD;
			case WARN -> LAMP_WARN;
			case ALARM -> LAMP_ALARM;
		} : SIGNAL_DEAD;
		for (int i = 0; i < 4; i++) {
			int h = 2 + i * 2;
			g.fill(x + i * 3, y + 8 - h, x + i * 3 + 2, y + 8, lit);
		}
		if (!status.linked() && ReactorTabletSignal.lampLit(false, status.alarm(), ms)) {
			for (int i = 0; i < 5; i++) {
				g.fill(x + 3 + i, y + i, x + 4 + i, y + i + 1, LAMP_ALARM);
				g.fill(x + 7 - i, y + i, x + 8 - i, y + i + 1, LAMP_ALARM);
			}
		}
	}

	/** A battery outline with five segments; red and blinking when low, an empty red outline when flat. */
	private static void drawBattery(GuiGraphicsExtractor g, int x, int y, float charge, long ms) {
		boolean low = ReactorTabletSignal.isLow(charge);
		if (!ReactorTabletSignal.batteryShown(charge, ms)) {
			return;
		}
		int ink = low ? BATTERY_LOW : BATTERY_INK;
		g.outline(x, y, 16, 7, ink);
		g.fill(x + 16, y + 2, x + 17, y + 5, ink);
		int lit = ReactorTabletSignal.litSegments(charge, BATTERY_SEGMENTS);
		for (int i = 0; i < lit; i++) {
			g.fill(x + 2 + i * 3, y + 2, x + 4 + i * 3, y + 5, low ? BATTERY_LOW : BATTERY_FILL);
		}
	}

	private static int argb(float alpha, int rgb) {
		return Math.clamp(Math.round(alpha), 0, 255) << 24 | rgb;
	}
}
