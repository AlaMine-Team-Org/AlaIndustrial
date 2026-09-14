package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.ReactorControllerScreen;
import dev.alaindustrial.core.structure.ReactorZone;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.network.ReactorZonePayload;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The reactor's «Core» tab (MOD-620): the core seen from above, one cell per vertical stack of columns, and what
 * the selected stack holds.
 *
 * <p><b>A cell reads like an inventory slot</b>, because that is what a player already knows how to read: rods
 * as pips, wear as the durability bar under an item, a count when the stack is more than one column tall. The
 * grid is sized to the room, so a 12 × 12 hall fits whole.
 *
 * <p><b>Nothing here is worked out on the client.</b> The stacks arrive folded, marked and summed from the server
 * ({@link ReactorZonePayload}); the page only picks which one to show in detail.
 */
public final class ZoneTabPage implements ReactorTabPage {

	private static final String KEY = "gui.alaindustrial.reactor_controller.";

	// ── Geometry, relative to the panel. Public so an L3 stand can crop a band from the same numbers. ──
	public static final int GRID_X = ConsoleTabPage.CONTENT_LEFT;
	public static final int GRID_Y = 22;
	public static final int GRID_SIZE = 100;
	private static final int GRID_INSET = 2;
	public static final int DETAIL_X = 116;
	public static final int DETAIL_Y = 22;
	public static final int DETAIL_BOTTOM = 122;
	public static final int SUMMARY_Y = 127;
	public static final int REPLACE_Y = 138;
	public static final int LEGEND_Y = 150;
	public static final int HINT_Y = 161;
	/** The largest cell drawn: a two-stack pile is not blown up to fill the grid. */
	private static final int MAX_CELL = 24;

	private static final int GRID_BACK = 0xFF1B2126;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_FILL = 0xFF8B8B8B;
	private static final int SLOT_EMPTY = 0xFF5A5F66;
	private static final int ROD_FUELLED = 0xFF6FD34A;
	private static final int ROD_SPENT = 0xFF3A3A3A;
	private static final int BAR_BACK = 0xFF000000;
	private static final int DENSE_MARK = 0xFFE23A2A;
	private static final int SELECTED = 0xFFFFD84A;
	private static final int DETAIL_EDGE = 0xFF555555;
	private static final int DETAIL_BACK = 0xFFB4B4B4;
	private static final int INK_AMBER = 0xFF8A5A00;
	private static final int COUNT_INK = 0xFFFFFFFF;
	private static final float SMALL = 0.75f;
	private static final int ROW_STEP = 9;
	private static final int TOOLTIP_WIDTH = 200;

	private final ReactorControllerScreen screen;
	/**
	 * The stack picked by a click, kept as an offset from the controller rather than from the zone's corner: a bare
	 * pile that loses its west-most rack moves the corner, and a pick kept by corner offset would jump to another stack.
	 */
	private boolean picked;
	private int pickedDx;
	private int pickedDz;
	/** Four rod pips and the three pixels between them — a cell narrower than this gets the one-colour drawing. */
	private static final int PIPS_MIN_INNER = 2 * FuelRodAssemblyBlock.MAX_RODS - 1;

	public ZoneTabPage(ReactorControllerScreen screen) {
		this.screen = screen;
	}

	@Override
	public Component title() {
		return Component.translatable(KEY + "tab.zone");
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(ModContent.FUEL_ROD_ASSEMBLY.get());
	}

	/** Amber while spent casings wait in a rack: the one thing on this tab a player has to go and do. */
	@Override
	public int badgeColour() {
		ReactorZonePayload zone = screen.getMenu().zone();
		if (zone != null) {
			for (ReactorZone.Stack stack : zone.stacks()) {
				if (stack.spentRods() > 0) {
					return ReactorPageText.FILL_AMBER;
				}
			}
		}
		return 0;
	}

	@Override
	public void init() {
	}

	@Override
	public void setShown(boolean shown) {
	}

	@Override
	public void tick() {
	}

	@Override
	public void mouseReleased(MouseButtonEvent event) {
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

	// ── Layout ───────────────────────────────────────────────────────────────────────────────────

	private record Grid(int cell, int left, int top) {
	}

	private static Grid grid(ReactorZonePayload zone) {
		int inner = GRID_SIZE - 2 * GRID_INSET;
		int span = Math.max(1, Math.max(zone.width(), zone.depth()));
		int cell = Math.max(1, Math.min(MAX_CELL, inner / span));
		return new Grid(cell, GRID_INSET + (inner - zone.width() * cell) / 2, GRID_INSET + (inner - zone.depth() * cell) / 2);
	}

	private static int indexAt(ReactorZonePayload zone, int x, int z) {
		for (int i = 0; i < zone.stacks().size(); i++) {
			ReactorZone.Stack stack = zone.stacks().get(i);
			if (stack.x() == x && stack.z() == z) {
				return i;
			}
		}
		return -1;
	}

	/** The stack shown in detail: the one clicked, or else the one to service first, the densest, the first. */
	private int selectedIndex(ReactorZonePayload zone) {
		int chosen = picked ? indexAt(zone, pickedDx - zone.originDx(), pickedDz - zone.originDz()) : -1;
		if (chosen >= 0) {
			return chosen;
		}
		int replace = ReactorZone.replaceFirst(zone.stacks());
		if (replace >= 0) {
			return replace;
		}
		int densest = ReactorZone.densest(zone.stacks());
		return densest >= 0 ? densest : zone.stacks().isEmpty() ? -1 : 0;
	}

	// ── Drawing ──────────────────────────────────────────────────────────────────────────────────

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorControllerMenu menu = screen.getMenu();
		ReactorZonePayload zone = menu.zone();
		int frameX = screen.left() + GRID_X;
		int frameY = screen.top() + GRID_Y;
		graphics.fill(frameX, frameY, frameX + GRID_SIZE, frameY + GRID_SIZE, ReactorPageText.PLATE_EDGE);
		graphics.fill(frameX + 1, frameY + 1, frameX + GRID_SIZE - 1, frameY + GRID_SIZE - 1, GRID_BACK);
		drawDetailFrame(graphics);

		if (zone == null || zone.width() <= 0 || zone.depth() <= 0) {
			String message = zone == null ? "zone.waiting"
					: menu.getStatus() == ReactorRoomStatus.FORMED ? "zone.empty" : "zone.not_built";
			centred(graphics, Component.translatable(KEY + message), frameX, frameY + GRID_SIZE / 2 - 4, GRID_SIZE);
			return;
		}

		int selected = selectedIndex(zone);
		drawGrid(graphics, zone, selected, frameX, frameY);
		if (selected >= 0) {
			drawDetail(graphics, zone.stacks().get(selected));
		} else {
			centred(graphics, Component.translatable(KEY + "zone.empty"), screen.left() + DETAIL_X,
					screen.top() + (DETAIL_Y + DETAIL_BOTTOM) / 2 - 4, ConsoleTabPage.CONTENT_RIGHT - DETAIL_X);
		}
		drawSummary(graphics, zone);
	}

	private void drawGrid(GuiGraphicsExtractor graphics, ReactorZonePayload zone, int selected, int frameX, int frameY) {
		Grid grid = grid(zone);
		int densest = ReactorZone.densest(zone.stacks());
		for (int z = 0; z < zone.depth(); z++) {
			for (int x = 0; x < zone.width(); x++) {
				int px = frameX + grid.left() + x * grid.cell();
				int py = frameY + grid.top() + z * grid.cell();
				int index = indexAt(zone, x, z);
				drawCell(graphics, px, py, grid.cell(), index >= 0 ? zone.stacks().get(index) : null, index == densest);
			}
		}
		if (selected >= 0) {
			// Two pixels of gold, not one of white: a slot's own bevel is a white pixel on its right and bottom edges,
			// and a one-pixel white outline read as just another slot (playtest, MOD-620).
			ReactorZone.Stack stack = zone.stacks().get(selected);
			int px = frameX + grid.left() + stack.x() * grid.cell();
			int py = frameY + grid.top() + stack.z() * grid.cell();
			int size = grid.cell() - (grid.cell() >= 6 ? 1 : 0);
			outline(graphics, px - 2, py - 2, size + 4, SELECTED);
			outline(graphics, px - 1, py - 1, size + 2, SELECTED);
		}
	}

	/**
	 * One cell as an inventory slot: the bevel, a pip per rod of an average column (fuelled bright, spent dark),
	 * the durability bar for the stack's mean wear, and the column count when the stack is more than one tall.
	 */
	private void drawCell(GuiGraphicsExtractor graphics, int px, int py, int cell, ReactorZone.@Nullable Stack stack,
			boolean densest) {
		int gap = cell >= 6 ? 1 : 0;
		int size = cell - gap;
		if (stack == null) {
			graphics.fill(px, py, px + size, py + size, SLOT_EMPTY);
			return;
		}
		graphics.fill(px, py, px + size, py + size, SLOT_SHADOW);
		graphics.fill(px + 1, py + 1, px + size, py + size, SLOT_LIGHT);
		graphics.fill(px + 1, py + 1, px + size - 1, py + size - 1, SLOT_FILL);

		int inner = size - 4;
		if (inner < PIPS_MIN_INNER && size >= 3) {
			// From a 9 × 9 zone up a cell has no room for four pips and their gaps, so it is one colour for its rods and
			// one line for its wear. Leaving it blank hid the whole reading in exactly the halls the tab is for, and
			// squeezing the pips in pushed the last one onto the bevel (review, MOD-620).
			int colour = stack.fuelledRods() > 0 ? ROD_FUELLED : stack.spentRods() > 0 ? ROD_SPENT : 0;
			if (colour != 0) {
				graphics.fill(px + 1, py + 1, px + size - 1, py + size - 2, colour);
			}
			if (stack.hasRods()) {
				graphics.fill(px + 1, py + size - 2, px + size - 1, py + size - 1, BAR_BACK);
				graphics.fill(px + 1, py + size - 2, px + 1 + (size - 2) * (1000 - stack.averageWearPermille()) / 1000,
						py + size - 1, durabilityColour(stack.averageWearPermille()));
			}
		}
		if (inner >= PIPS_MIN_INNER) {
			int slots = FuelRodAssemblyBlock.MAX_RODS;
			int fuelled = Math.round((float) stack.fuelledRods() / stack.columns());
			int spent = Math.min(slots - fuelled, Math.round((float) stack.spentRods() / stack.columns()));
			int pipW = Math.max(1, (inner - (slots - 1)) / slots);
			int pipH = Math.max(1, inner - 4);
			for (int i = 0; i < slots; i++) {
				int colour = i < fuelled ? ROD_FUELLED : i < fuelled + spent ? ROD_SPENT : 0;
				if (colour != 0) {
					int x0 = px + 2 + i * (pipW + 1);
					graphics.fill(x0, py + 2, x0 + pipW, py + 2 + pipH, colour);
				}
			}
			if (stack.hasRods()) {
				int barY = py + size - 4;
				graphics.fill(px + 2, barY, px + size - 2, barY + 2, BAR_BACK);
				int left = (size - 4) * (1000 - stack.averageWearPermille()) / 1000;
				graphics.fill(px + 2, barY, px + 2 + left, barY + 1, durabilityColour(stack.averageWearPermille()));
			}
		}
		if (stack.columns() > 1 && size >= 9) {
			ReactorPageText.scaled(graphics, screen.font(), Component.literal(Integer.toString(stack.columns())),
					px + 2, py + 2, 0.5f, COUNT_INK);
		}
		if (densest && size >= 8) {
			graphics.fill(px + size - 5, py + 1, px + size - 1, py + 5, ReactorPageText.PLATE_EDGE);
			graphics.fill(px + size - 4, py + 2, px + size - 2, py + 4, DENSE_MARK);
		} else if (densest && size >= 4) {
			graphics.fill(px + size - 3, py + 1, px + size - 1, py + 3, DENSE_MARK);
		}
	}

	private void drawDetailFrame(GuiGraphicsExtractor graphics) {
		int x0 = screen.left() + DETAIL_X;
		int y0 = screen.top() + DETAIL_Y;
		int x1 = screen.left() + ConsoleTabPage.CONTENT_RIGHT;
		int y1 = screen.top() + DETAIL_BOTTOM;
		graphics.fill(x0, y0, x1, y1, DETAIL_EDGE);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, DETAIL_BACK);
	}

	/** What the selected stack holds, one fact a line — the same facts the tooltip over a cell starts with. */
	private void drawDetail(GuiGraphicsExtractor graphics, ReactorZone.Stack stack) {
		Font font = screen.font();
		int x = screen.left() + DETAIL_X + 4;
		int right = screen.left() + ConsoleTabPage.CONTENT_RIGHT - 4;
		int y = screen.top() + DETAIL_Y + 3;
		ReactorPageText.scaledFit(graphics, font, Component.translatable(KEY + "zone.stack", stack.x() + 1, stack.z() + 1),
				x, y, right - x, GuiStyle.TEXT);
		y += 11;
		row(graphics, x, y, right, "zone.columns", Component.literal(Integer.toString(stack.columns())), GuiStyle.TEXT);
		y += ROW_STEP;
		row(graphics, x, y, right, "zone.rods",
				Component.literal(stack.fuelledRods() + " / " + stack.columns() * FuelRodAssemblyBlock.MAX_RODS), GuiStyle.TEXT);
		y += ROW_STEP;
		row(graphics, x, y, right, "zone.spent", Component.literal(Integer.toString(stack.spentRods())),
				stack.spentRods() > 0 ? INK_AMBER : GuiStyle.TEXT);
		y += ROW_STEP;
		row(graphics, x, y, right, "zone.wear", Component.translatable(KEY + "zone.wear_value",
				stack.averageWearPermille() / 10, stack.worstWearPermille() / 10), GuiStyle.TEXT);
		y += ROW_STEP;
		graphics.fill(x, y, right, y + 3, BAR_BACK);
		if (stack.hasRods()) {
			graphics.fill(x, y, x + (right - x) * (1000 - stack.averageWearPermille()) / 1000, y + 2,
					durabilityColour(stack.averageWearPermille()));
		}
		y += 6;
		row(graphics, x, y, right, "zone.energy",
				Component.translatable(KEY + "zone.energy_value", ReadoutFormat.compact(stack.remainingEu())), GuiStyle.TEXT);
		y += ROW_STEP;
		row(graphics, x, y, right, "zone.neighbours", Component.literal(Integer.toString(stack.neighbours())),
				GuiStyle.TEXT);
		y += ROW_STEP;
		row(graphics, x, y, right, "zone.water", Component.translatable(KEY + "percent", stack.waterPercent()),
				GuiStyle.TEXT);
		y += ROW_STEP;
		row(graphics, x, y, right, "zone.steam", Component.translatable(KEY + "percent", stack.steamPercent()),
				GuiStyle.TEXT);
	}

	/** The line under the grid, the stack to service first, the densest mark's legend and how to pick a stack. */
	private void drawSummary(GuiGraphicsExtractor graphics, ReactorZonePayload zone) {
		Font font = screen.font();
		int x = screen.left() + ConsoleTabPage.CONTENT_LEFT;
		int width = ConsoleTabPage.CONTENT_RIGHT - ConsoleTabPage.CONTENT_LEFT;
		int fuelled = 0;
		long wearSum = 0;
		int racked = 0;
		for (ReactorZone.Stack stack : zone.stacks()) {
			fuelled += stack.fuelledRods();
			int rods = stack.fuelledRods() + stack.spentRods();
			wearSum += (long) stack.averageWearPermille() * rods;
			racked += rods;
		}
		int averageWear = racked == 0 ? 0 : (int) (wearSum / racked / 10);
		ReactorPageText.scaledFit(graphics, font, Component.translatable(KEY + "zone.summary", zone.stacks().size(),
				fuelled, averageWear), x, screen.top() + SUMMARY_Y, width, GuiStyle.TEXT);

		int replace = ReactorZone.replaceFirst(zone.stacks());
		Component replaceLine = replace < 0
				? Component.translatable(KEY + "zone.replace_none")
				: Component.translatable(KEY + "zone.replace_first", zone.stacks().get(replace).x() + 1,
						zone.stacks().get(replace).z() + 1, zone.stacks().get(replace).worstWearPermille() / 10);
		ReactorPageText.scaledFit(graphics, font, replaceLine, x, screen.top() + REPLACE_Y, width,
				replace < 0 ? GuiStyle.TEXT_DIM : INK_AMBER);

		int legendY = screen.top() + LEGEND_Y;
		graphics.fill(x, legendY + 1, x + 5, legendY + 6, ReactorPageText.PLATE_EDGE);
		graphics.fill(x + 1, legendY + 2, x + 4, legendY + 5, DENSE_MARK);
		// Both lines at the small scale and shrunk to the panel: the Russian legend ran past its right edge at a fixed
		// scale (playtest, MOD-620).
		small(graphics, Component.translatable(KEY + "zone.densest"), x + 8, legendY, width - 8, GuiStyle.TEXT_DIM);
		small(graphics, Component.translatable(KEY + "zone.hint"), x, screen.top() + HINT_Y, width, GuiStyle.TEXT_DIM);
	}

	/**
	 * Label on the left, value on the right, both at the small scale. The label shrinks further only if it would
	 * not fit: at full size a short label ("Water") stood a size above its own value and above the long ones.
	 */
	private void row(GuiGraphicsExtractor graphics, int x, int y, int right, String labelKey, Component value,
			int valueColour) {
		Font font = screen.font();
		int valueW = Math.round(font.width(value) * SMALL);
		ReactorPageText.scaled(graphics, font, value, right - valueW, y, SMALL, valueColour);
		small(graphics, Component.translatable(KEY + labelKey), x, y, Math.max(1, right - valueW - 3 - x),
				GuiStyle.TEXT_DIM);
	}

	/** One line at the page's small scale, shrunk further only if it would not fit its width. */
	private void small(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int colour) {
		Font font = screen.font();
		float fit = (float) width / Math.max(1, font.width(text));
		ReactorPageText.scaled(graphics, font, text, x, y, Math.max(ReactorPageText.MIN_SCALE, Math.min(SMALL, fit)),
				colour);
	}

	private void centred(GuiGraphicsExtractor graphics, Component text, int x, int y, int width) {
		Font font = screen.font();
		int shown = Math.min(width - 8, Math.round(font.width(text) * SMALL));
		ReactorPageText.scaledFit(graphics, font, text, x + (width - shown) / 2, y, width - 8, GuiStyle.TEXT_DIM);
	}

	private static void outline(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
		graphics.fill(x, y, x + size, y + 1, colour);
		graphics.fill(x, y + size - 1, x + size, y + size, colour);
		graphics.fill(x, y, x + 1, y + size, colour);
		graphics.fill(x + size - 1, y, x + size, y + size, colour);
	}

	/** The item durability bar's colour: green when fresh, through yellow, to red when spent. */
	static int durabilityColour(int wearPermille) {
		float left = Math.max(0f, Math.min(1f, (1000 - wearPermille) / 1000f));
		int red = Math.round(255 * Math.min(1f, 2f - 2f * left));
		int green = Math.round(255 * Math.min(1f, 2f * left));
		return 0xFF000000 | red << 16 | green << 8;
	}

	// ── Input ────────────────────────────────────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		ReactorZonePayload zone = screen.getMenu().zone();
		int index = zone == null ? -1 : stackUnder(zone, event.x(), event.y());
		if (index < 0) {
			return false;
		}
		pick(zone.stacks().get(index).x(), zone.stacks().get(index).z());
		return true;
	}

	private int stackUnder(ReactorZonePayload zone, double mouseX, double mouseY) {
		if (zone.width() <= 0 || zone.depth() <= 0) {
			return -1;
		}
		Grid grid = grid(zone);
		double gx = mouseX - (screen.left() + GRID_X + grid.left());
		double gy = mouseY - (screen.top() + GRID_Y + grid.top());
		if (gx < 0 || gy < 0) {
			return -1;
		}
		int x = (int) (gx / grid.cell());
		int z = (int) (gy / grid.cell());
		return x < zone.width() && z < zone.depth() ? indexAt(zone, x, z) : -1;
	}

	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorZonePayload zone = screen.getMenu().zone();
		int index = zone == null ? -1 : stackUnder(zone, mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		ReactorZone.Stack stack = zone.stacks().get(index);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(KEY + "zone.stack", stack.x() + 1, stack.z() + 1));
		lines.add(Component.translatable(KEY + "zone.tooltip", stack.fuelledRods(),
				stack.columns() * FuelRodAssemblyBlock.MAX_RODS, stack.averageWearPermille() / 10));
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(screen.font().split(line, TOOLTIP_WIDTH));
		}
		graphics.setTooltipForNextFrame(screen.font(), wrapped, mouseX, mouseY);
		return true;
	}
}
