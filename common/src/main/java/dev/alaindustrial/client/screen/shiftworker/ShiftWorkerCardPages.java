package dev.alaindustrial.client.screen.shiftworker;

import static dev.alaindustrial.client.screen.shiftworker.ShiftWorkerCardLayout.*;

import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.TextFit;
import dev.alaindustrial.network.ShiftWorkerCardPayload;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Draws the contents of a shift worker's card (MOD-854) — the left column under the model and the right column of
 * each tab — for one frame. Positions come from {@link ShiftWorkerCardLayout}, wording from
 * {@link ShiftWorkerCardText}; buttons belong to the screen and sit where these rows are.
 */
final class ShiftWorkerCardPages {

	private static final String UI = ShiftWorkerCardText.UI;

	private final Font font;
	private final GuiGraphicsExtractor g;
	private final ShiftWorkerCardPayload card;
	private final int left;
	private final int top;

	ShiftWorkerCardPages(Font font, GuiGraphicsExtractor g, ShiftWorkerCardPayload card, int left, int top) {
		this.font = font;
		this.g = g;
		this.card = card;
		this.left = left;
		this.top = top;
	}

	// ── left column ─────────────────────────────────────────────────────────────────────────────

	/** The name row (unless the name is being edited), the battery, the health and, alive, how to charge it. */
	void drawLeftColumn(boolean editing, int nameWidth) {
		int x = left + LEFT_X;
		if (!editing) {
			Component name = card.name().isEmpty() ? Component.literal(card.number())
					: Component.literal(card.number() + "  " + card.name());
			line(name, x, top + NAME_Y + 3, nameWidth, GuiStyle.TEXT);
		}
		long capacity = Math.max(1L, card.capacity());
		long charge = Math.max(0L, Math.min(card.charge(), capacity));
		int percent = (int) (charge * 100L / capacity);
		line(Component.translatable(UI + "battery"), x, top + BATTERY_LABEL_Y, LEFT_W - 30, GuiStyle.TEXT_DIM);
		String pct = percent + " %";
		g.text(font, pct, x + LEFT_W - font.width(pct), top + BATTERY_LABEL_Y, GuiStyle.TEXT, false);
		int barY = top + BATTERY_BAR_Y;
		g.fill(x, barY, x + LEFT_W, barY + BATTERY_BAR_H, GuiStyle.TRACK);
		int filled = (int) ((LEFT_W - 2) * charge / capacity);
		if (filled > 0) {
			g.fill(x + 1, barY + 1, x + 1 + filled, barY + BATTERY_BAR_H - 1, GuiStyle.ENERGY);
			g.fill(x + 1, barY + 1, x + 1 + Math.min(filled, 2), barY + BATTERY_BAR_H - 1, GuiStyle.ENERGY_HI);
		}
		line(Component.translatable(UI + "battery.charge", ShiftWorkerCardText.amount(charge),
				ShiftWorkerCardText.amount(card.capacity())), x, top + BATTERY_TEXT_Y, LEFT_W, GuiStyle.TEXT);
		line(Component.translatable(UI + "health", ShiftWorkerCardText.health(card.health()),
				ShiftWorkerCardText.health(card.maxHealth())), x, top + HEALTH_Y, LEFT_W, GuiStyle.TEXT);
		if (card.alive()) {
			drawChargeHint(x, top + CHARGE_HINT_Y);
		}
	}

	/** The charge hint in two lines at most; a language that needs more gets one line, scaled down. */
	private void drawChargeHint(int x, int y) {
		Component hint = Component.translatable(UI + "charge_hint");
		List<FormattedCharSequence> wrapped = font.split(hint, LEFT_W);
		if (wrapped.size() > CHARGE_HINT_LINES) {
			line(hint, x, y, LEFT_W, GuiStyle.TEXT_DIM);
			return;
		}
		for (FormattedCharSequence part : wrapped) {
			g.text(font, part, x, y, GuiStyle.TEXT_DIM, false);
			y += 10;
		}
	}

	// ── «Card» tab ──────────────────────────────────────────────────────────────────────────────

	void drawCardTab() {
		boolean alive = card.alive();
		int x = left + RIGHT_X;
		int width = rightWidth();
		drawRecord(x, top + TOP, width, alive);
		drawRank(x, top + rankTop(alive), width);
		drawStats(x, top + statsTop(alive), width, alive);
	}

	private void drawRecord(int x, int y, int width, boolean alive) {
		module(x, y, width, recordHeight(alive), Component.translatable(UI + "record"));
		int inX = x + 8;
		int inW = width - 16;
		int row = y + MODULE_FIRST_ROW;
		line(ShiftWorkerCardText.town(card), inX, row, inW, GuiStyle.TEXT);
		row += ROW;
		line(Component.translatable(UI + "status", ShiftWorkerCardText.status(card.status())), inX, row, inW,
				alive ? GuiStyle.TEXT : GuiStyle.STATUS_BLOCKING);
		row += ROW;
		line(ShiftWorkerCardText.assignment(card), inX, row, inW, GuiStyle.TEXT);
		if (!alive) {
			row += ROW;
			Component deathLine = ShiftWorkerCardText.death(card).orElse(Component.empty());
			if (card.status() == ShiftWorkerRecord.Status.AWAITING_RETURN) {
				deathLine = Component.empty().append(deathLine).append("  ").append(Component.translatable(
						UI + "return_price", ShiftWorkerCardText.amount(card.returnPrice())));
			}
			line(deathLine, inX, row, inW, GuiStyle.TEXT_DIM);
		}
	}

	private void drawRank(int x, int y, int width) {
		module(x, y, width, RANK_H, ShiftWorkerCardText.rankAndLevel(card));
		int inX = x + 8;
		int inRight = x + width - 8;
		int barY = y + RANK_BAR;
		g.fill(inX, barY, inRight, barY + 8, GuiStyle.LEVEL_TRACK);
		boolean maxed = card.xpForNext() <= 0;
		double progress = maxed ? 1.0 : Math.min(1.0, card.xpIntoLevel() / (double) card.xpForNext());
		int filled = (int) Math.round(progress * (inRight - inX - 2));
		if (filled > 0) {
			g.fill(inX + 1, barY + 1, inX + 1 + filled, barY + 7, GuiStyle.LEVEL_FILL);
			g.fill(inX + 1, barY + 1, inX + 1 + Math.min(filled, 2), barY + 7, GuiStyle.LEVEL_FILL_HI);
		}
		Component xp = maxed ? Component.translatable("gui.alaindustrial.dashboard.maxed")
				: Component.translatable(UI + "xp", ShiftWorkerCardText.amount(card.xpIntoLevel()),
						ShiftWorkerCardText.amount(card.xpForNext()));
		line(xp, inX, y + RANK_XP, inRight - inX, GuiStyle.TEXT_DIM);
		line(Component.translatable(UI + "drained", ShiftWorkerCardText.amount(card.euDrained())), inX,
				y + RANK_DRAINED, inRight - inX, GuiStyle.TEXT);
		line(Component.translatable(UI + "free_points", card.freePoints()), inX, y + RANK_FREE, inRight - inX,
				card.freePoints() > 0 ? GuiStyle.COPPER : GuiStyle.TEXT);
	}

	private void drawStats(int x, int y, int width, boolean alive) {
		int height = STAT_FIRST + ShiftWorkerProfile.Stat.values().length * STAT_ROW + 3;
		module(x, y, width, height, Component.translatable(UI + "stats"));
		int cap = Math.max(1, card.statCap());
		for (ShiftWorkerProfile.Stat stat : ShiftWorkerProfile.Stat.values()) {
			int rowY = top + statRowY(alive, stat.ordinal());
			boolean active = card.active(stat);
			int colour = active ? GuiStyle.TEXT : GuiStyle.TEXT_DIM;
			line(Component.translatable(stat.langKey()), x + 8, rowY + 2, STAT_NAME_W, colour);
			int count = card.count(stat);
			int pipW = Math.max(2, (STAT_PIP * 10) / cap);
			for (int i = 0; i < cap; i++) {
				int px = x + STAT_PIPS + i * (pipW + 1);
				int pipColour = i < count ? (active ? GuiStyle.COPPER : GuiStyle.TEXT_DIM) : GuiStyle.TRACK;
				g.fill(px, rowY + 3, px + pipW, rowY + 9, pipColour);
			}
			g.text(font, count + "/" + cap, x + STAT_COUNT, rowY + 2, colour, false);
			if (!active) {
				line(Component.translatable(UI + "stat.inactive"), x + STAT_CAPTION, rowY + 2,
						width - STAT_CAPTION - 4, GuiStyle.TEXT_DIM);
			}
		}
	}

	// ── «Personal file» tab ─────────────────────────────────────────────────────────────────────

	void drawFileTab(int bottom) {
		int x = left + RIGHT_X;
		int width = rightWidth();
		int y = top + TOP;
		card(x, y, width, bottom - y);
		int inX = x + 8;
		int inW = width - 16;
		int row = y + MODULE_TITLE;
		g.text(font, Component.translatable(UI + "file.title", card.number()), inX, row, GuiStyle.COPPER, false);
		row += 13;
		if (card.biography().isEmpty()) {
			line(Component.translatable(UI + "file.empty"), inX, row, inW, GuiStyle.TEXT_DIM);
			row += ROW;
		}
		for (Component bio : card.biography()) {
			for (FormattedCharSequence wrapped : font.split(bio, inW)) {
				g.text(font, wrapped, inX, row, GuiStyle.TEXT, false);
				row += 10;
			}
		}
		row += 5;
		g.text(font, Component.translatable(UI + "marks"), inX, row, GuiStyle.COPPER, false);
		row += 13;
		List<ShiftWorkerProfile.ServiceMark> marks = card.marks();
		if (marks.isEmpty()) {
			line(Component.translatable(UI + "marks.empty"), inX, row, inW, GuiStyle.TEXT_DIM);
			return;
		}
		int room = Math.max(0, (bottom - row - 4) / ROW);
		int dayW = 44;
		for (ShiftWorkerProfile.ServiceMark mark : marks.subList(Math.max(0, marks.size() - room), marks.size())) {
			line(ShiftWorkerCardText.dayLabel(mark.gameTime()), inX, row, dayW - 4, GuiStyle.TEXT_DIM);
			line(ShiftWorkerCardText.mark(mark), inX + dayW, row, inW - dayW, GuiStyle.TEXT);
			row += ROW;
		}
	}

	// ── «Number» tab ────────────────────────────────────────────────────────────────────────────

	void drawNumberTab(int bottom) {
		int x = left + RIGHT_X;
		int width = rightWidth();
		int y = top + TOP;
		card(x, y, width, bottom - y);
		int inX = x + 8;
		int inW = width - 16;
		int row = y + MODULE_TITLE;
		g.text(font, Component.translatable(UI + "number.title", card.number()), inX, row, GuiStyle.COPPER, false);
		row += 15;
		Component issued = card.issuedGameTime() < 0 ? Component.translatable(UI + "number.issued_unknown")
				: Component.translatable(UI + "number.issued", ShiftWorkerCardText.day(card.issuedGameTime()));
		line(issued, inX, row, inW, GuiStyle.TEXT);
		row += ROW + 6;
		g.text(font, Component.translatable(UI + "number.former"), inX, row, GuiStyle.COPPER, false);
		row += 13;
		for (Component former : formerLines()) {
			if (row > bottom - 40) {
				break;
			}
			line(former, inX, row, inW, GuiStyle.TEXT);
			row += ROW;
		}
		row += 6;
		for (FormattedCharSequence wrapped : font.split(Component.translatable(UI + "number.holders_note"), inW)) {
			g.text(font, wrapped, inX, row, GuiStyle.TEXT_DIM, false);
			row += 10;
		}
	}

	private List<Component> formerLines() {
		List<Component> lines = new ArrayList<>();
		for (ShiftWorkerCardPayload.FormerNumber former : card.formerNumbers()) {
			lines.add(former.gameTime() < 0
					? Component.translatable(UI + "number.former_entry_unknown", former.number())
					: Component.translatable(UI + "number.former_entry", former.number(),
							ShiftWorkerCardText.day(former.gameTime())));
		}
		if (lines.isEmpty()) {
			lines.add(Component.translatable(UI + "number.former_none"));
		}
		return lines;
	}

	// ── stamp ───────────────────────────────────────────────────────────────────────────────────

	/** The stamp across the model of a dead record: closed for good, or waiting to be brought back. */
	Optional<Component> stamp() {
		return switch (card.status()) {
			case DEAD -> Optional.of(Component.translatable(UI + "stamp.closed"));
			case AWAITING_RETURN -> Optional.of(Component.translatable(UI + "stamp.awaiting"));
			case ALIVE -> Optional.empty();
		};
	}

	/** Draws {@code text} as a stamp centred on ({@code cx}, {@code cy}), at most {@code maxWidth} wide. */
	void drawStamp(Component text, int cx, int cy, int maxWidth) {
		int colour = card.status() == ShiftWorkerRecord.Status.DEAD ? GuiStyle.STATUS_BLOCKING : GuiStyle.COPPER;
		int textW = Math.min(font.width(text), maxWidth - 10);
		int x0 = cx - textW / 2 - 5;
		int x1 = cx + textW / 2 + 5;
		int y0 = cy - 9;
		int y1 = cy + 9;
		g.fill(x0, y0, x1, y1, colour);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GuiStyle.PANEL);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, colour);
		g.fill(x0 + 3, y0 + 3, x1 - 3, y1 - 3, GuiStyle.PANEL);
		line(text, cx - textW / 2, cy - 4, textW, colour);
	}

	// ── drawing helpers ─────────────────────────────────────────────────────────────────────────

	private void module(int x, int y, int width, int height, Component title) {
		card(x, y, width, height);
		line(title, x + 8, y + MODULE_TITLE, width - 16, GuiStyle.COPPER);
	}

	/** A recessed module card (light fill, dark top-left edge, light bottom-right edge). */
	private void card(int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, GuiStyle.CARD);
		g.fill(x, y, x + w, y + 1, GuiStyle.PANEL_LO);
		g.fill(x, y, x + 1, y + h, GuiStyle.PANEL_LO);
		g.fill(x, y + h - 1, x + w, y + h, GuiStyle.PANEL_HI);
		g.fill(x + w - 1, y, x + w, y + h, GuiStyle.PANEL_HI);
	}

	/** One line of {@code text} in {@code width}: scaled down when it must, cut only past the smallest scale. */
	void line(Component text, int x, int y, int width, int colour) {
		String plain = text.getString();
		TextFit.Fit fit = TextFit.fit(plain, width, 1, font::width);
		if (fit.fits() && fit.scale() >= 1.0f) {
			g.text(font, plain, x, y, colour, false);
			return;
		}
		float scale = fit.fits() ? fit.scale() : TextFit.MIN_SCALE;
		String shown = fit.fits() ? plain : font.plainSubstrByWidth(plain, (int) (width / scale));
		g.pose().pushMatrix();
		g.pose().translate(x, y + (1.0f - scale) * 4.0f);
		g.pose().scale(scale, scale);
		g.text(font, shown, 0, 0, colour, false);
		g.pose().popMatrix();
	}
}
