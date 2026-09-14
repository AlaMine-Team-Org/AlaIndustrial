package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.client.screen.ReactorControllerScreen;
import dev.alaindustrial.core.structure.ReactorZone;
import dev.alaindustrial.network.ReactorZonePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The top-down grid of stacks two tabs share (MOD-621): the «Core» tab draws rods and wear in its cells, the «Coolant»
 * tab water and steam.
 *
 * <p>Geometry, hit-testing and the picked stack live here once, so a stack picked on one tab is the stack shown on the
 * other — a player who found the worn rack on «Core» and switches to «Coolant» is looking at the same rack.
 */
public final class StackGrid {

	// ── Geometry, relative to the panel. Public so an L3 stand can crop a band from the same numbers. ──
	public static final int X = ConsoleTabPage.CONTENT_LEFT;
	public static final int Y = 22;
	public static final int SIZE = 100;
	private static final int INSET = 2;
	public static final int DETAIL_X = 116;
	public static final int DETAIL_Y = 22;
	public static final int DETAIL_BOTTOM = 122;
	/** The largest cell drawn: a two-stack pile is not blown up to fill the grid. */
	private static final int MAX_CELL = 24;

	private static final int BACK = 0xFF1B2126;
	private static final int SELECTED = 0xFFFFD84A;
	private static final int DETAIL_EDGE = 0xFF555555;
	private static final int DETAIL_BACK = 0xFFB4B4B4;

	private final ReactorControllerScreen screen;
	/**
	 * The stack picked by a click, kept as an offset from the controller rather than from the zone's corner: a bare
	 * pile that loses its west-most rack moves the corner, and a pick kept by corner offset would jump to another stack.
	 */
	private boolean picked;
	private int pickedDx;
	private int pickedDz;

	public StackGrid(ReactorControllerScreen screen) {
		this.screen = screen;
	}

	/** Cell size and where the first cell sits inside the grid frame. */
	public record Layout(int cell, int left, int top) {

		/** A cell's drawn size: a one-pixel gap between cells once they are big enough to spare it. */
		public int size() {
			return cell - (cell >= 6 ? 1 : 0);
		}
	}

	public static Layout layout(ReactorZonePayload zone) {
		int inner = SIZE - 2 * INSET;
		int span = Math.max(1, Math.max(zone.width(), zone.depth()));
		int cell = Math.max(1, Math.min(MAX_CELL, inner / span));
		return new Layout(cell, INSET + (inner - zone.width() * cell) / 2, INSET + (inner - zone.depth() * cell) / 2);
	}

	/** The index of the stack at offset ({@code x}, {@code z}), or -1 for an empty cell. */
	public static int indexAt(ReactorZonePayload zone, int x, int z) {
		for (int i = 0; i < zone.stacks().size(); i++) {
			ReactorZone.Stack stack = zone.stacks().get(i);
			if (stack.x() == x && stack.z() == z) {
				return i;
			}
		}
		return -1;
	}

	/** Picks the stack at offset ({@code x}, {@code z}) of the zone now shown — for a click, and for a stand. */
	public void pick(int x, int z) {
		ReactorZonePayload zone = screen.getMenu().zone();
		if (zone != null) {
			picked = true;
			pickedDx = zone.originDx() + x;
			pickedDz = zone.originDz() + z;
		}
	}

	/** The picked stack's index in this zone, or -1 while nothing is picked or the picked stack is gone. */
	public int pickedIndex(ReactorZonePayload zone) {
		return picked ? indexAt(zone, pickedDx - zone.originDx(), pickedDz - zone.originDz()) : -1;
	}

	/** Picks the stack under the mouse; whether there was one. */
	public boolean click(ReactorZonePayload zone, double mouseX, double mouseY) {
		int index = stackUnder(zone, mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		pick(zone.stacks().get(index).x(), zone.stacks().get(index).z());
		return true;
	}

	/** The index of the stack under the mouse, or -1. */
	public int stackUnder(ReactorZonePayload zone, double mouseX, double mouseY) {
		if (zone.width() <= 0 || zone.depth() <= 0) {
			return -1;
		}
		Layout layout = layout(zone);
		double gx = mouseX - (screen.left() + X + layout.left());
		double gy = mouseY - (screen.top() + Y + layout.top());
		if (gx < 0 || gy < 0) {
			return -1;
		}
		int x = (int) (gx / layout.cell());
		int z = (int) (gy / layout.cell());
		return x < zone.width() && z < zone.depth() ? indexAt(zone, x, z) : -1;
	}

	/** Screen x of the cell in column {@code x}. */
	public int cellX(Layout layout, int x) {
		return screen.left() + X + layout.left() + x * layout.cell();
	}

	/** Screen y of the cell in row {@code z}. */
	public int cellY(Layout layout, int z) {
		return screen.top() + Y + layout.top() + z * layout.cell();
	}

	/** The grid's frame and dark back. */
	public void drawFrame(GuiGraphicsExtractor graphics) {
		int x = screen.left() + X;
		int y = screen.top() + Y;
		graphics.fill(x, y, x + SIZE, y + SIZE, ReactorPageText.PLATE_EDGE);
		graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, BACK);
	}

	/** The light panel the selected stack's details are written on. */
	public void drawDetailFrame(GuiGraphicsExtractor graphics) {
		int x0 = screen.left() + DETAIL_X;
		int y0 = screen.top() + DETAIL_Y;
		int x1 = screen.left() + ConsoleTabPage.CONTENT_RIGHT;
		int y1 = screen.top() + DETAIL_BOTTOM;
		graphics.fill(x0, y0, x1, y1, DETAIL_EDGE);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, DETAIL_BACK);
	}

	/**
	 * Two pixels of gold round the stack at {@code index}, not one of white: a slot's own bevel is a white pixel on
	 * its right and bottom edges, and a one-pixel white outline read as just another slot (playtest, MOD-620).
	 */
	public void drawSelection(GuiGraphicsExtractor graphics, ReactorZonePayload zone, int index) {
		Layout layout = layout(zone);
		ReactorZone.Stack stack = zone.stacks().get(index);
		int px = cellX(layout, stack.x());
		int py = cellY(layout, stack.z());
		int size = layout.size();
		outline(graphics, px - 2, py - 2, size + 4, SELECTED);
		outline(graphics, px - 1, py - 1, size + 2, SELECTED);
	}

	private static void outline(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
		graphics.fill(x, y, x + size, y + 1, colour);
		graphics.fill(x, y + size - 1, x + size, y + size, colour);
		graphics.fill(x, y, x + 1, y + size, colour);
		graphics.fill(x + size - 1, y, x + size, y + size, colour);
	}
}
