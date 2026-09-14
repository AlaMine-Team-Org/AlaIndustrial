package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.ReactorControllerScreen;
import dev.alaindustrial.core.structure.ReactorZone;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.network.ReactorZonePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The reactor's «Coolant» tab (MOD-621): the loop seen from above, one cell per vertical stack of columns with its
 * water and its steam, the stack picked in detail, and what to fix.
 *
 * <p><b>A grid, not a bar chart</b> — the owner's choice. A 12 × 12 hall gives each bar of a chart a pixel and a half,
 * while the grid the «Core» tab already draws fits it whole and shows where the dry stack stands, which is where the
 * player has to walk.
 *
 * <p><b>Two bars per cell, side by side:</b> water on the left, steam on the right, each rising from the floor of the
 * cell. They are two tanks of their own, not one vessel filling up, so stacking them in one column would hide a full
 * steam tank behind a full water tank — exactly the blocked exhaust this tab is for.
 */
public final class CoolantTabPage implements ReactorTabPage {

	private static final String KEY = "gui.alaindustrial.reactor_controller.";

	// ── Geometry, relative to the panel. Public so an L3 stand can crop a band from the same numbers. ──
	public static final int SUMMARY_Y = 127;
	public static final int ADVICE_Y = 138;
	public static final int ADVICE_BOTTOM = 178;

	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_FILL = 0xFF8B8B8B;
	private static final int SLOT_EMPTY = 0xFF5A5F66;
	/** The «Console» tab's coolant teal. */
	private static final int FILL_WATER = 0xFF3E8FA8;
	/** Steam, light enough to read on the slot's grey — the «Console» tab's darker steam vanished on it. */
	private static final int FILL_STEAM = 0xFFE3E8EC;
	private static final int BAR_BACK = 0xFF000000;
	private static final int INK_AMBER = 0xFF8A5A00;
	private static final int INK_RED = 0xFFAA2A1A;
	private static final int INK_GREEN = 0xFF2F6B33;
	private static final int ROW_STEP = 9;
	private static final int TOOLTIP_WIDTH = 200;

	private final ReactorControllerScreen screen;

	public CoolantTabPage(ReactorControllerScreen screen) {
		this.screen = screen;
	}

	@Override
	public Component title() {
		return Component.translatable(KEY + "tab.coolant");
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.WATER_BUCKET);
	}

	/** Red while a dry or blocked stack is already leaving heat behind, amber while one waits to. */
	@Override
	public int badgeColour() {
		ReactorControllerMenu menu = screen.getMenu();
		ReactorZonePayload zone = menu.zone();
		if (zone == null || menu.getStatus() != ReactorRoomStatus.FORMED) {
			return 0;
		}
		return switch (ReactorCoolant.badge(zone.stacks(), ConsoleTabPage.readout(menu).coolantShare())) {
			case ALARM -> ReactorPageText.FILL_RED;
			case WARN -> ReactorPageText.FILL_AMBER;
			case GOOD, IDLE -> 0;
		};
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

	/** The stack shown in detail: the one picked on either stack tab, or else the first blocked, the first dry. */
	private int selectedIndex(ReactorZonePayload zone) {
		int chosen = screen.stackGrid().pickedIndex(zone);
		if (chosen >= 0) {
			return chosen;
		}
		ReactorCoolant.Survey survey = ReactorCoolant.survey(zone.stacks());
		if (survey.firstBlocked() >= 0) {
			return survey.firstBlocked();
		}
		if (survey.firstDry() >= 0) {
			return survey.firstDry();
		}
		return zone.stacks().isEmpty() ? -1 : 0;
	}

	// ── Drawing ──────────────────────────────────────────────────────────────────────────────────

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorControllerMenu menu = screen.getMenu();
		ReactorZonePayload zone = menu.zone();
		StackGrid grid = screen.stackGrid();
		grid.drawFrame(graphics);
		grid.drawDetailFrame(graphics);

		if (zone == null) {
			gridMessage(graphics, "zone.waiting");
			return;
		}
		boolean formed = menu.getStatus() == ReactorRoomStatus.FORMED;
		ReactorCoolant.Advice advice = ReactorCoolant.advice(formed, menu.isBare(), zone.stacks(),
				ConsoleTabPage.readout(menu).coolantShare());
		// No loop to draw: a bare pile boils nothing, and a room still being built has no loop yet. The box under the
		// grid says why rather than leaving an empty frame to be read as a broken tab.
		if (!formed || zone.width() <= 0 || zone.depth() <= 0) {
			gridMessage(graphics, menu.isBare() ? "coolant.no_loop" : "coolant.not_built");
			drawAdvice(graphics, advice);
			return;
		}

		int selected = selectedIndex(zone);
		StackGrid.Layout layout = StackGrid.layout(zone);
		for (int z = 0; z < zone.depth(); z++) {
			for (int x = 0; x < zone.width(); x++) {
				int index = StackGrid.indexAt(zone, x, z);
				drawCell(graphics, grid.cellX(layout, x), grid.cellY(layout, z), layout.size(),
						index >= 0 ? zone.stacks().get(index) : null);
			}
		}
		if (selected >= 0) {
			grid.drawSelection(graphics, zone, selected);
			drawDetail(graphics, zone.stacks().get(selected));
		} else {
			ReactorPageText.centred(graphics, screen.font(), Component.translatable(KEY + "zone.empty"),
					screen.left() + StackGrid.DETAIL_X, screen.top() + (StackGrid.DETAIL_Y + StackGrid.DETAIL_BOTTOM) / 2 - 4,
					ConsoleTabPage.CONTENT_RIGHT - StackGrid.DETAIL_X);
		}

		ReactorCoolant.Survey survey = ReactorCoolant.survey(zone.stacks());
		ReactorPageText.scaledFit(graphics, screen.font(), Component.translatable(KEY + "coolant.summary", survey.dry(),
				survey.blocked(), menu.getWaterRate()), screen.left() + ConsoleTabPage.CONTENT_LEFT,
				screen.top() + SUMMARY_Y, ConsoleTabPage.CONTENT_RIGHT - ConsoleTabPage.CONTENT_LEFT,
				survey.faulty() ? INK_AMBER : GuiStyle.TEXT);
		drawAdvice(graphics, advice);
	}

	private void gridMessage(GuiGraphicsExtractor graphics, String key) {
		ReactorPageText.centred(graphics, screen.font(), Component.translatable(KEY + key), screen.left() + StackGrid.X,
				screen.top() + StackGrid.Y + StackGrid.SIZE / 2 - 4, StackGrid.SIZE);
	}

	/**
	 * One cell: the slot's bevel, water on the left and steam on the right rising from the floor, the steam amber once
	 * its exhaust counts as blocked, and a red floor under the water of a stack with none.
	 */
	private void drawCell(GuiGraphicsExtractor graphics, int px, int py, int size, ReactorZone.@Nullable Stack stack) {
		if (stack == null) {
			graphics.fill(px, py, px + size, py + size, SLOT_EMPTY);
			return;
		}
		graphics.fill(px, py, px + size, py + size, SLOT_SHADOW);
		graphics.fill(px + 1, py + 1, px + size, py + size, SLOT_LIGHT);
		graphics.fill(px + 1, py + 1, px + size - 1, py + size - 1, SLOT_FILL);
		int inner = size - 2;
		if (inner <= 0) {
			return;
		}
		ReactorZone.Coolant coolant = stack.coolant();
		int left = px + 1;
		int floor = py + size - 1;
		int waterW = (inner + 1) / 2;
		int waterH = level(coolant.water(), coolant.waterCapacity(), inner);
		if (waterH > 0) {
			graphics.fill(left, floor - waterH, left + waterW, floor, FILL_WATER);
		}
		int steamH = level(coolant.steam(), coolant.steamCapacity(), inner);
		if (steamH > 0 && inner > waterW) {
			graphics.fill(left + waterW, floor - steamH, left + inner, floor,
					coolant.blocked() ? ReactorPageText.FILL_AMBER : FILL_STEAM);
		}
		if (coolant.dry()) {
			graphics.fill(left, floor - Math.max(1, inner / 6), left + waterW, floor, ReactorPageText.FILL_RED);
		}
		// The corner mark only where the cell can spare its corner: in 8- to 11-pixel cells the plate covered the whole
		// steam bar and the water bar's corner, and there the bars' own amber and red already say it (review, MOD-621).
		if (size >= 12 && (coolant.blocked() || coolant.dry())) {
			int mark = coolant.blocked() ? ReactorPageText.FILL_AMBER : ReactorPageText.FILL_RED;
			graphics.fill(px + size - 5, py + 1, px + size - 1, py + 5, ReactorPageText.PLATE_EDGE);
			graphics.fill(px + size - 4, py + 2, px + size - 2, py + 4, mark);
		}
	}

	/**
	 * Pixels for a share of a height, rounded to the nearest. Any amount at all shows at least one pixel, so a trickle is
	 * not drawn as empty, and only a tank within a percent of full reaches the top: rounding down drew a pipe-full column
	 * a pixel short — 4 of 5 in a hall's cell reads as 80 % — and rounding up alone would draw 90 % steam as full.
	 */
	static int level(long amount, long capacity, int height) {
		if (amount <= 0 || capacity <= 0 || height <= 0) {
			return 0;
		}
		if (amount * 100 >= capacity * 99) {
			return height;
		}
		long nearest = (amount * height * 2 + capacity) / (capacity * 2);
		return (int) Math.max(1, Math.min(height - 1, nearest));
	}

	/** What the picked stack holds: its water and steam in millibuckets, with bars, and what is wrong with it. */
	private void drawDetail(GuiGraphicsExtractor graphics, ReactorZone.Stack stack) {
		Font font = screen.font();
		int x = screen.left() + StackGrid.DETAIL_X + 4;
		int right = screen.left() + ConsoleTabPage.CONTENT_RIGHT - 4;
		int y = screen.top() + StackGrid.DETAIL_Y + 3;
		ReactorZone.Coolant coolant = stack.coolant();
		ReactorPageText.scaledFit(graphics, font, Component.translatable(KEY + "zone.stack", stack.x() + 1, stack.z() + 1),
				x, y, right - x, GuiStyle.TEXT);
		y += 11;
		row(graphics, x, y, right, "zone.columns", Component.literal(Integer.toString(stack.columns())), GuiStyle.TEXT);
		y += ROW_STEP + 3;
		row(graphics, x, y, right, "zone.water", amount(coolant.water(), coolant.waterCapacity()),
				coolant.dry() ? INK_RED : GuiStyle.TEXT);
		y += ROW_STEP;
		bar(graphics, x, y, right, coolant.water(), coolant.waterCapacity(), FILL_WATER);
		y += 7;
		row(graphics, x, y, right, "zone.steam", amount(coolant.steam(), coolant.steamCapacity()),
				coolant.blocked() ? INK_AMBER : GuiStyle.TEXT);
		y += ROW_STEP;
		bar(graphics, x, y, right, coolant.steam(), coolant.steamCapacity(),
				coolant.blocked() ? ReactorPageText.FILL_AMBER : FILL_STEAM);
		y += 10;
		row(graphics, x, y, right, "coolant.state", Component.translatable(KEY + stateKey(coolant)), stateInk(coolant));
	}

	private void bar(GuiGraphicsExtractor graphics, int x, int y, int right, long amount, long capacity, int colour) {
		graphics.fill(x, y, right, y + 3, BAR_BACK);
		int filled = level(amount, capacity, right - x);
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + 2, colour);
		}
	}

	private static Component amount(long amount, long capacity) {
		return Component.translatable(KEY + "coolant.amount", amount, capacity);
	}

	/** A blocked exhaust is named before dryness, in the order the advice fixes them. */
	private static String stateKey(ReactorZone.Coolant coolant) {
		return coolant.blocked() ? "coolant.state.blocked" : coolant.dry() ? "coolant.state.dry" : "coolant.state.ok";
	}

	private static int stateInk(ReactorZone.Coolant coolant) {
		return coolant.blocked() ? INK_AMBER : coolant.dry() ? INK_RED : INK_GREEN;
	}

	private void drawAdvice(GuiGraphicsExtractor graphics, ReactorCoolant.Advice advice) {
		ReactorPageText.messageBox(graphics, screen.font(), screen.left() + ConsoleTabPage.CONTENT_LEFT,
				screen.top() + ADVICE_Y, ConsoleTabPage.CONTENT_RIGHT - ConsoleTabPage.CONTENT_LEFT, ADVICE_BOTTOM - ADVICE_Y,
				advice.tone(), Component.translatable(advice.titleKey()),
				List.of(Component.translatable(advice.bodyKey(), advice.args().toArray())));
	}

	private void row(GuiGraphicsExtractor graphics, int x, int y, int right, String labelKey, Component value,
			int valueColour) {
		ReactorPageText.row(graphics, screen.font(), x, y, right, Component.translatable(KEY + labelKey), value,
				valueColour);
	}

	// ── Input ────────────────────────────────────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		ReactorControllerMenu menu = screen.getMenu();
		ReactorZonePayload zone = menu.zone();
		return zone != null && menu.getStatus() == ReactorRoomStatus.FORMED
				&& screen.stackGrid().click(zone, event.x(), event.y());
	}

	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorControllerMenu menu = screen.getMenu();
		ReactorZonePayload zone = menu.zone();
		if (zone == null || menu.getStatus() != ReactorRoomStatus.FORMED) {
			return false;
		}
		int index = screen.stackGrid().stackUnder(zone, mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		ReactorZone.Stack stack = zone.stacks().get(index);
		ReactorZone.Coolant coolant = stack.coolant();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(KEY + "zone.stack", stack.x() + 1, stack.z() + 1));
		lines.add(Component.translatable(KEY + "coolant.tooltip", coolant.water(), coolant.waterCapacity(),
				coolant.steam(), coolant.steamCapacity()));
		lines.add(Component.translatable(KEY + stateKey(coolant)));
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(screen.font().split(line, TOOLTIP_WIDTH));
		}
		graphics.setTooltipForNextFrame(screen.font(), wrapped, mouseX, mouseY);
		return true;
	}
}
