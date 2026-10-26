package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.client.render.HallSchematicOverlay;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.SettlementCoreLayout;
import dev.alaindustrial.client.screen.TextFit;
import dev.alaindustrial.client.screen.works.WorksModel.Control;
import dev.alaindustrial.client.screen.works.WorksModel.Line;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.works.BuildingTile;
import dev.alaindustrial.settlement.works.ConstructionRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The drawing primitives of the core's «Construction» tab (owner's round 3 of 0.1.219, R3-3): a card's face, its chip
 * and bar, a button with an item, a plan slot, a line of words fitted to its width.
 */
final class WorksPaint {

	private static final int LINE = 10;

	private WorksPaint() {
	}

	/** What a control shows. */
	static ItemStack icon(ConstructionRow.Action action) {
		return switch (action) {
			case ORDER_RESTORE, ORDER_BUILD -> new ItemStack(Items.BRICKS);
			case ORDER_RESUME -> new ItemStack(Items.SCAFFOLDING);
			case ORDER_REPAIR -> new ItemStack(Items.ANVIL);
			case ORDER_CANCEL, CANCEL -> new ItemStack(Items.BARRIER);
			case START -> new ItemStack(Items.LEVER);
			case SCHEMATIC -> new ItemStack(Items.MAP);
		};
	}

	/** The first {@code max} lines, the last one cut with an ellipsis: the tooltip shows the whole text. */
	static List<String> cut(Font font, List<String> lines, int width, int max) {
		List<String> out = new ArrayList<>(lines.subList(0, Math.min(max, lines.size())));
		int last = out.size() - 1;
		if (last >= 0 && lines.size() > max) {
			int room = (int) (width / TextFit.MIN_SCALE) - font.width("…");
			out.set(last, font.plainSubstrByWidth(out.get(last), room) + "…");
		}
		return out;
	}

	static void cardFace(GuiGraphicsExtractor graphics, int left, int right, int y, int height, boolean open,
			boolean hovered) {
		graphics.fill(left, y, right, y + height, open ? GuiStyle.CARD_OPEN_EDGE : GuiStyle.PANEL_LO);
		graphics.fill(left + 1, y + 1, right - 1, y + height - 1, GuiStyle.PANEL_HI);
		graphics.fill(left + 1, y + 2, right - 1, y + height - 1, GuiStyle.CARD);
		if (hovered) {
			graphics.fill(left + 1, y + 1, right - 1, y + height - 1, GuiStyle.HOVER_WASH);
		}
	}

	/** A slot with an item: bright when {@code has}, washed out with a red corner when not; a number at its corner. */
	static void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, boolean has,
			String number) {
		int size = SettlementCoreLayout.WORKS_SLOT;
		graphics.fill(x, y, x + size, y + size, has ? GuiStyle.STATUS_GOOD : GuiStyle.SLOT_EDGE);
		graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, GuiStyle.SLOT);
		graphics.item(stack, x + 1, y + 1);
		if (!has) {
			graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, GuiStyle.CLOSED_WASH);
		}
		Font font = Minecraft.getInstance().font;
		graphics.text(font, number, x + size - font.width(number), y + size - 8, GuiStyle.CHIP_TEXT, true);
	}

	/** The chip of a card: its word on its colour; returns the chip's right edge. */
	static int drawChip(GuiGraphicsExtractor graphics, Font font, BuildingTile.Chip chip, int x, int y) {
		String word = Component.translatable(chip.key()).getString();
		TextFit.Fit fit = TextFit.fit(word, SettlementCoreLayout.WORKS_CHIP_MAX - 6, 1, font::width);
		float scale = fit.fits() ? fit.scale() : TextFit.MIN_SCALE;
		String shown = fit.fits() ? word : font.plainSubstrByWidth(word, (int) ((SettlementCoreLayout.WORKS_CHIP_MAX
				- 6) / scale));
		int width = Math.round(font.width(shown) * scale) + 6;
		graphics.fill(x, y, x + width, y + 10, chipColour(chip));
		scaled(graphics, font, shown, x + 3, y + 1 + Math.round((1.0f - scale) * 4.0f), scale, GuiStyle.CHIP_TEXT);
		return x + width;
	}

	static int chipColour(BuildingTile.Chip chip) {
		return switch (chip) {
			case RUIN -> GuiStyle.CHIP_RUIN;
			case DAMAGED -> GuiStyle.CHIP_DAMAGED;
			case NO_PLAN, LOCKED -> GuiStyle.CHIP_MUTED;
			case WORKING -> GuiStyle.CHIP_WORKING;
			case WAITING -> GuiStyle.STATUS_BLOCKING;
			case DONE -> GuiStyle.STATUS_GOOD;
		};
	}

	static int barColour(BuildingTile.Chip chip) {
		return switch (chip) {
			case DONE -> GuiStyle.STATUS_GOOD_TICK;
			case WORKING -> GuiStyle.LEVEL_FILL;
			case WAITING -> GuiStyle.STATUS_BLOCKING;
			default -> GuiStyle.COPPER;
		};
	}

	/** A thin bar from {@code x} to {@code right}: its track and {@code percent} of it filled. */
	static void bar(GuiGraphicsExtractor graphics, int x, int y, int right, int percent, int colour) {
		graphics.fill(x, y, right, y + 3, GuiStyle.LEVEL_TRACK);
		int filled = (right - x) * Math.max(0, Math.min(100, percent)) / 100;
		graphics.fill(x, y, x + filled, y + 3, colour);
	}

	static void button(GuiGraphicsExtractor graphics, ItemStack icon, int x, int y, int size, boolean hovered,
			boolean on) {
		graphics.fill(x, y, x + size, y + size, hovered || on ? GuiStyle.BUTTON_ACTIVE_EDGE : GuiStyle.SLOT_EDGE);
		graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, hovered || on ? GuiStyle.BUTTON_ACTIVE
				: GuiStyle.BUTTON);
		graphics.item(icon, x + (size - 16) / 2, y + (size - 16) / 2);
	}

	/** What a block-in-the-way line's «show it» button shows (MOD-890): the spyglass of «Find» (R142). */
	static ItemStack spotIcon() {
		return new ItemStack(Items.SPYGLASS);
	}

	/**
	 * The picture of a small button whose top-left is ({@code x}, {@code y}): half size, centred on a whole pixel
	 * (MOD-890: it sat one pixel off the button's top and left edges and two off its bottom and right).
	 */
	static void smallButtonIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		int inset = SettlementCoreLayout.smallIconInset(SettlementCoreLayout.WORKS_SMALL_BUTTON);
		smallIcon(graphics, stack, x + inset, y + inset);
	}

	/** An item at half size; a whole-pixel offset, so it samples no neighbour in the atlas (round 2, R2-2). */
	static void smallIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(0.5f, 0.5f);
		graphics.item(stack, 0, 0);
		graphics.pose().popMatrix();
	}

	static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	/** Whether {@code text} shows whole in {@code width}, scaled down if need be, as {@link #cell} draws it. */
	static boolean fits(Font font, Component text, int width) {
		return TextFit.fit(text.getString(), width, 1, font::width).fits();
	}

	/** One line in {@code width}, scaled down or cut when it must. */
	static void cell(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width,
			int colour) {
		String plain = text.getString();
		TextFit.Fit fit = TextFit.fit(plain, width, 1, font::width);
		float scale = fit.fits() ? fit.scale() : TextFit.MIN_SCALE;
		String shown = fit.fits() ? plain : font.plainSubstrByWidth(plain, (int) (width / scale) - font.width("…"))
				+ "…";
		scaled(graphics, font, shown, x, Math.round(y + (1.0f - scale) * 4.0f), scale, colour);
	}

	static void scaled(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, float scale,
			int colour) {
		if (scale >= 1.0f) {
			graphics.text(font, text, x, y, colour, false);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, 0, 0, colour, false);
		graphics.pose().popMatrix();
	}

	/** The dark bar at the top: a copper «!» badge and the next step in two lines at most. */
	static void drawHint(GuiGraphicsExtractor graphics, Font font, Optional<ConstructionRow.Text> hint, int x0,
			int y0) {
		int left = x0 + SettlementCoreLayout.ROSTER_LEFT;
		int top = y0 + SettlementCoreLayout.WORKS_HINT_TOP;
		int bottom = top + SettlementCoreLayout.WORKS_HINT_HEIGHT;
		graphics.fill(left, top, x0 + SettlementCoreLayout.ROSTER_RIGHT, bottom, GuiStyle.HEADER_BAR);
		int badge = top + (SettlementCoreLayout.WORKS_HINT_HEIGHT - 12) / 2;
		graphics.fill(left + 5, badge, left + 17, badge + 12, GuiStyle.COPPER);
		graphics.text(font, "!", left + 11 - font.width("!") / 2, badge + 2, GuiStyle.CHIP_TEXT, false);
		if (hint.isEmpty()) {
			return;
		}
		String text = ConstructionTab.component(hint.get()).getString();
		int width = SettlementCoreLayout.worksHintWidth();
		TextFit.Fit fit = TextFit.fit(text, width, SettlementCoreLayout.WORKS_HINT_MAX_LINES, font::width);
		List<String> lines = fit.fits() ? fit.lines()
				: cut(font, fit.lines(), width, SettlementCoreLayout.WORKS_HINT_MAX_LINES);
		float scale = fit.fits() ? fit.scale() : TextFit.MIN_SCALE;
		int height = Math.round(lines.size() * LINE * scale);
		int y = top + (SettlementCoreLayout.WORKS_HINT_HEIGHT - height) / 2 + 1;
		for (int i = 0; i < lines.size(); i++) {
			scaled(graphics, font, lines.get(i), x0 + SettlementCoreLayout.ROSTER_LEFT
					+ SettlementCoreLayout.WORKS_HINT_TEXT, Math.round(y + i * LINE * scale), scale,
					GuiStyle.HEADER_TEXT);
		}
	}

	static void drawOpenLines(GuiGraphicsExtractor graphics, Font font, List<Line> lines, int left, int right,
			int y, int mouseX, int mouseY) {
		if (lines.isEmpty()) {
			return;
		}
		int indent = SettlementCoreLayout.WORKS_OPEN_INDENT;
		graphics.fill(left + indent - 2, y, right - indent + 2, y + 1, GuiStyle.PANEL_LO);
		int x = left + SettlementCoreLayout.WORKS_OPEN_INDENT;
		int width = right - left - 2 * SettlementCoreLayout.WORKS_OPEN_INDENT;
		int at = y + 3;
		for (Line line : lines) {
			Control control = line.control();
			if (control != null && control.action() == ConstructionRow.Action.SCHEMATIC) {
				boolean shown = ConstructionTab.schematicShown(control.target());
				Component words = Component.translatable(shown ? HallSchematicOverlay.HIDE_KEY
						: ConstructionRow.Action.SCHEMATIC.key());
				int w = Math.min(width, font.width(words) + SettlementCoreLayout.WORKS_SMALL_BUTTON + 6);
				boolean hovered = inside(mouseX, mouseY, x, at - 1, w, SettlementCoreLayout.WORKS_SMALL_BUTTON);
				graphics.fill(x, at - 1, x + w, at - 1 + SettlementCoreLayout.WORKS_SMALL_BUTTON,
						hovered || shown ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
				smallButtonIcon(graphics, icon(ConstructionRow.Action.SCHEMATIC), x, at - 1);
				cell(graphics, font, words, x + SettlementCoreLayout.WORKS_SMALL_BUTTON + 2, at + 1,
						w - SettlementCoreLayout.WORKS_SMALL_BUTTON - 4, GuiStyle.HEADER_TEXT);
			} else {
				boolean button = control != null || line.spot() != null;
				int textWidth = button ? width - SettlementCoreLayout.WORKS_SMALL_BUTTON - 3 : width;
				cell(graphics, font, line.text(), x, at, textWidth, line.colour());
				if (button) {
					int bx = x + width - SettlementCoreLayout.WORKS_SMALL_BUTTON;
					boolean hovered = inside(mouseX, mouseY, bx, at - 1, SettlementCoreLayout.WORKS_SMALL_BUTTON,
							SettlementCoreLayout.WORKS_SMALL_BUTTON);
					graphics.fill(bx, at - 1, bx + SettlementCoreLayout.WORKS_SMALL_BUTTON,
							at - 1 + SettlementCoreLayout.WORKS_SMALL_BUTTON,
							hovered ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
					smallButtonIcon(graphics, control != null ? icon(control.action()) : spotIcon(), bx, at - 1);
				}
			}
			if (line.barPercent() >= 0) {
				bar(graphics, x, at + 9, x + width, line.barPercent(), GuiStyle.COPPER);
				at += 3;
			}
			at += LINE;
		}
	}

	static void drawNetwork(GuiGraphicsExtractor graphics, Font font, ConstructionRow row, Component name,
			int left, int right, int y, int mouseX, int mouseY) {
		int height = 24;
		boolean schematic = row.allows(ConstructionRow.Action.SCHEMATIC);
		int bx = right - 18 - 3;
		boolean onButton = schematic && inside(mouseX, mouseY, bx, y + 3, 18, 18);
		cardFace(graphics, left, right, y, height, false, false);
		graphics.item(new ItemStack(ModContent.TOWN_ROAD_ITEM.get()), left + 5, y + 4);
		cell(graphics, font, name, left + 26, y + 8, bx - left - 30, GuiStyle.TEXT);
		if (schematic) {
			button(graphics, icon(ConstructionRow.Action.SCHEMATIC), bx, y + 3, 18, onButton,
					ConstructionTab.schematicShown(row.target()));
		}
	}

	static void drawSheets(GuiGraphicsExtractor graphics, Font font, List<ConstructionRow> sheets, int left,
			int y) {
		cell(graphics, font, Component.translatable(BuildingTile.Group.HALL.key()), left + 2, y + 7,
				SettlementCoreLayout.WORKS_PLAN_LABEL - 4, GuiStyle.TEXT);
		int found = 0;
		int x = left + SettlementCoreLayout.WORKS_PLAN_LABEL;
		for (ConstructionRow sheet : sheets) {
			boolean has = sheet.tone() == ConstructionRow.Tone.GOOD;
			found += has ? 1 : 0;
			slot(graphics, new ItemStack(ModContent.TOWN_HALL_PLAN_SHEET.get()), x, y + 2, has,
					String.valueOf(sheet.target()));
			x += SettlementCoreLayout.WORKS_SLOT + 2;
		}
		Component count = Component.translatable(SettlementCoreLayout.WORKS_SHEETS_COUNT, found, sheets.size());
		graphics.text(font, count, x + 4, y + 7, found == sheets.size() ? GuiStyle.STATUS_GOOD : GuiStyle.TEXT_DIM,
				false);
	}

	static void drawHousePlans(GuiGraphicsExtractor graphics, Font font, List<ConstructionRow> plans,
			int left, int y) {
		cell(graphics, font, Component.translatable(BuildingTile.Group.HOUSE.key()), left + 2, y + 7,
				SettlementCoreLayout.WORKS_PLAN_LABEL - 4, GuiStyle.TEXT);
		int x = left + SettlementCoreLayout.WORKS_PLAN_LABEL;
		for (ConstructionRow plan : plans) {
			String type = plan.text().args().isEmpty() ? "?" : plan.text().args().get(0);
			slot(graphics, new ItemStack(ModContent.HOUSE_PLAN.get()), x, y + 2,
					plan.tone() == ConstructionRow.Tone.GOOD, type);
			x += SettlementCoreLayout.WORKS_SLOT + 2;
		}
	}
}
