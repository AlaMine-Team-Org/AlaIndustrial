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
	public static final int GRID_X = StackGrid.X;
	public static final int GRID_Y = StackGrid.Y;
	public static final int GRID_SIZE = StackGrid.SIZE;
	public static final int DETAIL_X = StackGrid.DETAIL_X;
	public static final int DETAIL_Y = StackGrid.DETAIL_Y;
	public static final int DETAIL_BOTTOM = StackGrid.DETAIL_BOTTOM;
	public static final int SUMMARY_Y = 127;
	public static final int REPLACE_Y = 138;
	public static final int LEGEND_Y = 150;
	public static final int HINT_Y = 161;

	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_FILL = 0xFF8B8B8B;
	private static final int SLOT_EMPTY = 0xFF5A5F66;
	private static final int ROD_FUELLED = 0xFF6FD34A;
	private static final int ROD_SPENT = 0xFF3A3A3A;
	private static final int BAR_BACK = 0xFF000000;
	private static final int DENSE_MARK = 0xFFE23A2A;
	private static final int INK_AMBER = 0xFF8A5A00;
	private static final int COUNT_INK = 0xFFFFFFFF;
	private static final int ROW_STEP = 9;
	private static final int TOOLTIP_WIDTH = 200;
	/** Four rod pips and the three pixels between them — a cell narrower than this gets the one-colour drawing. */
	private static final int PIPS_MIN_INNER = 2 * FuelRodAssemblyBlock.MAX_RODS - 1;

	private final ReactorControllerScreen screen;

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
		screen.stackGrid().pick(x, z);
	}

	/** The stack shown in detail: the one clicked, or else the one to service first, the densest, the first. */
	private int selectedIndex(ReactorZonePayload zone) {
		int chosen = screen.stackGrid().pickedIndex(zone);
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
		StackGrid grid = screen.stackGrid();
		grid.drawFrame(graphics);
		grid.drawDetailFrame(graphics);

		if (zone == null || zone.width() <= 0 || zone.depth() <= 0) {
			String message = zone == null ? "zone.waiting"
					: menu.getStatus() == ReactorRoomStatus.FORMED ? "zone.empty" : "zone.not_built";
			ReactorPageText.centred(graphics, screen.font(), Component.translatable(KEY + message),
					screen.left() + GRID_X, screen.top() + GRID_Y + GRID_SIZE / 2 - 4, GRID_SIZE);
			return;
		}

		int selected = selectedIndex(zone);
		drawGrid(graphics, zone, selected);
		if (selected >= 0) {
			drawDetail(graphics, zone.stacks().get(selected));
		} else {
			ReactorPageText.centred(graphics, screen.font(), Component.translatable(KEY + "zone.empty"),
					screen.left() + DETAIL_X, screen.top() + (DETAIL_Y + DETAIL_BOTTOM) / 2 - 4,
					ConsoleTabPage.CONTENT_RIGHT - DETAIL_X);
		}
		drawSummary(graphics, zone);
	}

	private void drawGrid(GuiGraphicsExtractor graphics, ReactorZonePayload zone, int selected) {
		StackGrid grid = screen.stackGrid();
		StackGrid.Layout layout = StackGrid.layout(zone);
		int densest = ReactorZone.densest(zone.stacks());
		for (int z = 0; z < zone.depth(); z++) {
			for (int x = 0; x < zone.width(); x++) {
				int index = StackGrid.indexAt(zone, x, z);
				drawCell(graphics, grid.cellX(layout, x), grid.cellY(layout, z), layout.size(),
						index >= 0 ? zone.stacks().get(index) : null, index == densest);
			}
		}
		if (selected >= 0) {
			grid.drawSelection(graphics, zone, selected);
		}
	}

	/**
	 * One cell as an inventory slot: the bevel, a pip per rod of an average column (fuelled bright, spent dark),
	 * the durability bar for the stack's mean wear, and the column count when the stack is more than one tall.
	 */
	private void drawCell(GuiGraphicsExtractor graphics, int px, int py, int size, ReactorZone.@Nullable Stack stack,
			boolean densest) {
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
		ReactorPageText.small(graphics, font, Component.translatable(KEY + "zone.densest"), x + 8, legendY, width - 8,
				GuiStyle.TEXT_DIM);
		ReactorPageText.small(graphics, font, Component.translatable(KEY + "zone.hint"), x, screen.top() + HINT_Y, width,
				GuiStyle.TEXT_DIM);
	}

	private void row(GuiGraphicsExtractor graphics, int x, int y, int right, String labelKey, Component value,
			int valueColour) {
		ReactorPageText.row(graphics, screen.font(), x, y, right, Component.translatable(KEY + labelKey), value,
				valueColour);
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
		return zone != null && screen.stackGrid().click(zone, event.x(), event.y());
	}

	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorZonePayload zone = screen.getMenu().zone();
		int index = zone == null ? -1 : screen.stackGrid().stackUnder(zone, mouseX, mouseY);
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
