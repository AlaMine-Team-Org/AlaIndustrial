package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.client.render.HallSchematicOverlay;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.SettlementCoreLayout;
import dev.alaindustrial.client.screen.TextFit;
import dev.alaindustrial.network.TownWorksPayload;
import dev.alaindustrial.settlement.works.ConstructionRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The «Construction» tab of the settlement core's screen (MOD-857, epic decision R88): the rows the server sent
 * ({@link TownWorksPayload}) — a heading wherever the section changes, each row's line with its status and percent at
 * the right, its second line under it — and, for the town's owner, the small controls at the right of a row (R95).
 * Everybody else sees the same rows without controls, and a line saying so at the end — all but the town hall's
 * schematic switch, which is visual and personal and goes to everyone ({@link HallSchematicOverlay}).
 *
 * <p>The tab knows no building or job: it draws rows. A later task's section (the plan sheets of MOD-859, the material
 * stages of MOD-860) appears here by itself once the server adds its rows.
 *
 * <p>Asked for when the tab opens and every two seconds while it stays open, like the shift workers' roster.
 */
public final class ConstructionTab {

	private static final int REFRESH_TICKS = 40;
	/** Most panel lines one row's second line wraps onto before it shrinks. */
	private static final int DETAIL_MAX_LINES = 4;

	/** What a control shows. */
	private static ItemStack icon(ConstructionRow.Action action) {
		return switch (action) {
			case RAISE_CLEAR -> new ItemStack(Items.IRON_SHOVEL);
			case RAISE_BUILD -> new ItemStack(Items.BRICKS);
			case CANCEL -> new ItemStack(Items.BARRIER);
			case START -> new ItemStack(Items.LEVER);
			case SCHEMATIC -> new ItemStack(Items.MAP);
		};
	}

	/** One line of the panel: a heading, a row's main line, its second line, or the read-only note. */
	private record Line(Kind kind, @Nullable ConstructionRow row, Component text) {
		enum Kind { HEADING, MAIN, DETAIL, NOTE }
	}

	private int scroll;
	private int ticks;

	/** The tab opened: asks for its rows and starts at the top. */
	public void opened() {
		scroll = 0;
		ticks = 0;
		TownWorksClient.request();
	}

	/** One client tick while the tab is open. */
	public void tick() {
		if (++ticks % REFRESH_TICKS == 0) {
			TownWorksClient.request();
		}
	}

	/** Draws the tab into the panel at ({@code left}, {@code top}); {@code unbound} — a core of no town. */
	public void draw(GuiGraphicsExtractor graphics, Font font, int left, int top, int right, boolean unbound,
			int mouseX, int mouseY) {
		List<Line> lines = lines();
		if (lines == null) {
			Component waiting = Component.translatable(unbound ? SettlementCoreLayout.STATUS_UNBOUND
					: SettlementCoreLayout.ROSTER_LOADING);
			cell(graphics, font, waiting, left + 2, top + 2, right - left - 4, GuiStyle.TEXT_DIM);
			return;
		}
		scroll = Math.min(scroll, SettlementCoreLayout.maxRosterScroll(lines.size()));
		int shown = Math.min(SettlementCoreLayout.ROSTER_ROWS, lines.size() - scroll);
		for (int i = 0; i < shown; i++) {
			Line line = lines.get(scroll + i);
			int y = top + i * SettlementCoreLayout.ROSTER_ROW;
			drawLine(graphics, font, line, left, right, y, mouseX, mouseY);
		}
	}

	private void drawLine(GuiGraphicsExtractor graphics, Font font, Line line, int left, int right, int y,
			int mouseX, int mouseY) {
		switch (line.kind()) {
			case HEADING -> {
				cell(graphics, font, line.text(), left + 2, y, right - left - 4, GuiStyle.LABEL);
				graphics.fill(left + 2, y + 9, right - 2, y + 10, GuiStyle.PANEL_LO);
			}
			case DETAIL -> cell(graphics, font, line.text(), left + SettlementCoreLayout.WORKS_DETAIL_INDENT, y,
					right - left - SettlementCoreLayout.WORKS_DETAIL_INDENT - 2,
					line.row() != null && line.row().tone() == ConstructionRow.Tone.BAD
							? GuiStyle.STATUS_BLOCKING : GuiStyle.TEXT_DIM);
			case NOTE -> cell(graphics, font, line.text(), left + 2, y, right - left - 4, GuiStyle.TEXT_DIM);
			case MAIN -> drawMain(graphics, font, line.row(), line.text(), left, y, mouseX, mouseY);
		}
	}

	private void drawMain(GuiGraphicsExtractor graphics, Font font, ConstructionRow row, Component text, int left,
			int y, int mouseX, int mouseY) {
		int colour = colour(row.tone());
		cell(graphics, font, text, left + 2, y, SettlementCoreLayout.WORKS_COL_STATUS - 4, colour);
		row.status().ifPresent(status -> cell(graphics, font, component(status),
				left + SettlementCoreLayout.WORKS_COL_STATUS, y,
				SettlementCoreLayout.WORKS_COL_PERCENT - SettlementCoreLayout.WORKS_COL_STATUS - 2, colour));
		if (row.percent() >= 0) {
			cell(graphics, font, Component.literal(row.percent() + "%"), left + SettlementCoreLayout.WORKS_COL_PERCENT,
					y, SettlementCoreLayout.WORKS_COL_ACTIONS - SettlementCoreLayout.WORKS_COL_PERCENT - 1, colour);
		}
		int x = left + SettlementCoreLayout.WORKS_COL_ACTIONS;
		for (ConstructionRow.Action action : ConstructionRow.Action.values()) {
			if (!row.allows(action)) {
				continue;
			}
			int size = SettlementCoreLayout.WORKS_ACTION_SIZE;
			boolean hovered = mouseX >= x && mouseX < x + size && mouseY >= y - 1 && mouseY < y - 1 + size;
			boolean on = action == ConstructionRow.Action.SCHEMATIC && schematicShown();
			graphics.fill(x, y - 1, x + size, y - 1 + size,
					hovered || on ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 0.5f, y - 0.5f);
			graphics.pose().scale(0.5f, 0.5f);
			graphics.item(icon(action), 0, 0);
			graphics.pose().popMatrix();
			x += size + 1;
		}
	}

	/** The control under the mouse: the row's target and the action, or empty. */
	private Optional<Hit> hit(int left, int top, double mouseX, double mouseY) {
		List<Line> lines = lines();
		if (lines == null || mouseY < top - 1) {
			return Optional.empty();
		}
		int index = (int) ((mouseY - (top - 1)) / SettlementCoreLayout.ROSTER_ROW);
		if (index >= SettlementCoreLayout.ROSTER_ROWS || scroll + index >= lines.size()) {
			return Optional.empty();
		}
		Line line = lines.get(scroll + index);
		if (line.kind() != Line.Kind.MAIN || line.row() == null) {
			return Optional.empty();
		}
		int x = left + SettlementCoreLayout.WORKS_COL_ACTIONS;
		int y = top + index * SettlementCoreLayout.ROSTER_ROW - 1;
		for (ConstructionRow.Action action : ConstructionRow.Action.values()) {
			if (!line.row().allows(action)) {
				continue;
			}
			int size = SettlementCoreLayout.WORKS_ACTION_SIZE;
			if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
				return Optional.of(new Hit(action, line.row().target()));
			}
			x += size + 1;
		}
		return Optional.empty();
	}

	private record Hit(ConstructionRow.Action action, int target) {
	}

	/** The tooltip of the control under the mouse, if any. */
	public Optional<Component> tooltip(int left, int top, double mouseX, double mouseY) {
		return hit(left, top, mouseX, mouseY).map(hit -> Component.translatable(
				hit.action() == ConstructionRow.Action.SCHEMATIC && schematicShown()
						? HallSchematicOverlay.HIDE_KEY : hit.action().key()));
	}

	/** A click: presses the control under the mouse. Whether it hit one. */
	public boolean clicked(int left, int top, double mouseX, double mouseY) {
		Optional<Hit> hit = hit(left, top, mouseX, mouseY);
		hit.ifPresent(h -> {
			TownWorksPayload payload = TownWorksClient.latest();
			if (h.action() == ConstructionRow.Action.SCHEMATIC) {
				if (payload != null) {
					HallSchematicOverlay.toggle(payload.core()); // visual and personal: asked for on its own
				}
			} else {
				TownWorksClient.press(h.action(), h.target());
			}
		});
		return hit.isPresent();
	}

	/** Whether the schematic of the hall of the town whose tab this is is shown. */
	private static boolean schematicShown() {
		TownWorksPayload payload = TownWorksClient.latest();
		return payload != null && HallSchematicOverlay.isShown(payload.core());
	}

	/** The wheel scrolls a longer list. Whether it did. */
	public boolean scrolled(double scrollY) {
		List<Line> lines = lines();
		if (lines == null || scrollY == 0.0) {
			return false;
		}
		int max = SettlementCoreLayout.maxRosterScroll(lines.size());
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		return true;
	}

	/** The panel's lines from the latest rows, or null before any arrived. */
	@Nullable
	private static List<Line> lines() {
		TownWorksPayload payload = TownWorksClient.latest();
		if (payload == null) {
			return null;
		}
		List<Line> lines = new ArrayList<>();
		String section = null;
		for (ConstructionRow row : payload.rows()) {
			if (!row.section().equals(section)) {
				section = row.section();
				lines.add(new Line(Line.Kind.HEADING, null, Component.translatable(section)));
			}
			lines.add(new Line(Line.Kind.MAIN, row, component(row.text())));
			row.detail().ifPresent(detail -> {
				for (String part : wrapped(component(detail).getString())) {
					lines.add(new Line(Line.Kind.DETAIL, row, Component.literal(part)));
				}
			});
		}
		if (!payload.manage() && !payload.rows().isEmpty()) {
			lines.add(new Line(Line.Kind.NOTE, null, Component.translatable(SettlementCoreLayout.WORKS_READ_ONLY)));
		}
		return lines;
	}

	/**
	 * A row's second line broken into as many panel lines as it takes at full size (owner's round 1: «missing: the roof
	 * over the hall, the door, the…» was cut at the panel's edge). Each part is a line of its own, so the click and
	 * tooltip arithmetic by line index holds.
	 */
	private static List<String> wrapped(String text) {
		Font font = Minecraft.getInstance().font;
		int width = SettlementCoreLayout.ROSTER_RIGHT - SettlementCoreLayout.ROSTER_LEFT
				- SettlementCoreLayout.WORKS_DETAIL_INDENT - 2;
		return TextFit.fit(text, width, DETAIL_MAX_LINES, font::width).lines();
	}

	/**
	 * A row's sentence: every argument shown as a translation, a joined list in the key's one placeholder. Public so
	 * another screen that shows the same server-worded lines (the dispatcher's, MOD-858) reads them the same way.
	 */
	public static Component component(ConstructionRow.Text text) {
		if (text.joined()) {
			MutableComponent list = Component.empty();
			for (int i = 0; i < text.args().size(); i++) {
				if (i > 0) {
					list.append(", ");
				}
				list.append(argument(text.args().get(i)));
			}
			return Component.translatable(text.key(), list);
		}
		Object[] args = new Object[text.args().size()];
		for (int i = 0; i < args.length; i++) {
			args[i] = argument(text.args().get(i));
		}
		return Component.translatable(text.key(), args);
	}

	/** One argument: «Glass ×6» for a counted one (MOD-860), else the translation of the argument itself. */
	private static Component argument(String argument) {
		return ConstructionRow.Text.counted(argument)
				.<Component>map(counted -> Component.translatable(ConstructionRow.Text.COUNTED,
						String.valueOf(counted.count()), Component.translatable(counted.key())))
				.orElseGet(() -> Component.translatable(argument));
	}

	private static int colour(ConstructionRow.Tone tone) {
		return switch (tone) {
			case DIM -> GuiStyle.TEXT_DIM;
			case GOOD -> GuiStyle.STATUS_GOOD;
			case BAD -> GuiStyle.STATUS_BLOCKING;
			case NORMAL -> GuiStyle.TEXT;
		};
	}

	/** One line in {@code width}, scaled down or cut when it must. */
	private static void cell(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width,
			int colour) {
		String plain = text.getString();
		TextFit.Fit fit = TextFit.fit(plain, width, 1, font::width);
		float scale = fit.fits() ? fit.scale() : TextFit.MIN_SCALE;
		String shown = fit.fits() ? plain : font.plainSubstrByWidth(plain, (int) (width / scale));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y + (1.0f - scale) * 4.0f);
		graphics.pose().scale(scale, scale);
		graphics.text(font, shown, 0, 0, colour, false);
		graphics.pose().popMatrix();
	}
}
