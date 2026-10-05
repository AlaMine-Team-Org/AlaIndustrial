package dev.alaindustrial.client.screen;

import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.network.MachineStatsPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The text inside the statistics panel (MOD-125): the readout rows, or the line that explains why there are
 * none yet. Split out of {@link StatsPanelController} by MOD-716 so the controller holds the panel and its
 * input, and this class the one frame of text it draws; one instance per frame.
 */
final class StatsPanelReadout {
	private static final int PANEL_W = StatsPanelController.PANEL_W;
	private static final int PANEL_H = StatsPanelController.PANEL_H;

	/** Row pitch inside the statistics panel: 8px glyphs with a 3px gutter, the vanilla readout rhythm. */
	private static final int STAT_ROW_H = 11;

	/** Inner width available to panel text: the box minus the same gutter on both sides. */
	private static final int STAT_TEXT_W = PANEL_W - 18;

	private final Font font;
	private final MachineMenu menu;
	private final GuiGraphicsExtractor graphics;
	private final int mouseX;
	private final int mouseY;

	StatsPanelReadout(Font font, MachineMenu menu, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		this.font = font;
		this.menu = menu;
		this.graphics = graphics;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
	}

	/** The panel's text, from the frame's top-left corner. */
	void draw(int px, int py) {
		MachineStatsPayload stats = menu.stats();
		if (stats == null) {
			// Two different silences, and telling them apart is the difference between "wait a moment" and
			// "this machine will never show you anything until you fit a chip".
			boolean chipFitted = menu.hasStatsChipInPanel();
			int wrapY = wrappedText(Component.translatable(chipFitted
					? "gui.alaindustrial.stats.waiting" : "gui.alaindustrial.stats.no_chip"), px, py + 26);
			if (!chipFitted) {
				wrappedText(Component.translatable("gui.alaindustrial.stats.no_chip_hint"),
						px, wrapY + 2);
			}
			return;
		}

		drawRows(px, py, stats);
	}

	/** The readout rows under the title: work done, energy, rates, connections, and the live/idle line. */
	private void drawRows(int px, int py, MachineStatsPayload stats) {
		int pw = PANEL_W;
		int ph = PANEL_H;
		int y = py + 26;
		long[] parts = ReadoutFormat.durationParts(stats.activeTicks());
		y = statRow(px, y, "gui.alaindustrial.stats.uptime",
				Component.translatable("gui.alaindustrial.stats.duration",
						parts[0], ReadoutFormat.clock(parts[1], parts[2])).getString(),
				stats.activeTicks(), false);
		// Processed count sits with the working time: both answer "what has this machine actually done",
		// as opposed to the energy block below, which answers "at what cost". Shown only where it can be
		// non-zero — a generator processes nothing and would just carry a permanent 0.
		if (stats.itemsProcessed() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.processed",
					ReadoutFormat.compact(stats.itemsProcessed()),
					stats.itemsProcessed(), false);
		}

		y = drawEnergyRows(px, y, stats);

		y += 3;
		graphics.fill(px + 7, y, px + pw - 7, y + 1, GuiStyle.PANEL_LO);
		y += 4;

		boolean stale = menu.statsAreStale();
		y = statRow(px, y, "gui.alaindustrial.stats.now",
				stats.euRate() + " EU/t", stats.euRate(), false, stale);
		if (stats.peakEuRate() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.peak",
					stats.peakEuRate() + " EU/t", stats.peakEuRate(), false);
		}

		y += 3;
		graphics.fill(px + 7, y, px + pw - 7, y + 1, GuiStyle.PANEL_LO);
		y += 4;

		y = statRow(px, y, "gui.alaindustrial.stats.connections",
				stats.sources() + " / " + stats.sinks(), 0, false);

		graphics.text(font, Component.translatable(stale
						? "gui.alaindustrial.stats.idle" : "gui.alaindustrial.stats.live"),
				px + 9, py + ph - 14, GuiStyle.TEXT_DIM, false);
	}

	/** The energy block: only the rows this block can answer, each with its exact figure as a tooltip. */
	private int drawEnergyRows(int px, int y, MachineStatsPayload stats) {
		// Only the rows this block can actually answer. A generator has no "received", a consumer has no
		// "generated", and a row of zeroes is worse than no row: it invites the player to look for a fault.
		if (stats.energyGenerated() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.generated",
					ReadoutFormat.compact(stats.energyGenerated()) + " EU",
					stats.energyGenerated(), true);
		}
		if (stats.energyOut() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.sent",
					ReadoutFormat.compact(stats.energyOut()) + " EU",
					stats.energyOut(), true);
		}
		if (stats.energyIn() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.received",
					ReadoutFormat.compact(stats.energyIn()) + " EU",
					stats.energyIn(), true);
		}
		if (stats.energyConsumed() > 0) {
			y = statRow(px, y, "gui.alaindustrial.stats.spent",
					ReadoutFormat.compact(stats.energyConsumed()) + " EU",
					stats.energyConsumed(), true);
		}
		return y;
	}

	/**
	 * Draw a line that may not fit, wrapped at the panel's inner width, and return the y below it.
	 *
	 * <p>Needed because the panel is a fixed 159px while its strings are translated into twenty
	 * languages: the Russian hint for fitting a chip already overflowed it, and German and Turkish
	 * run longer still. Wrapping is measured, not guessed at authoring time.
	 */
	private int wrappedText(Component text, int px, int y) {
		for (FormattedCharSequence line : font.split(text, STAT_TEXT_W)) {
			graphics.text(font, line, px + 9, y, GuiStyle.TEXT_DIM, false);
			y += 10;
		}
		return y;
	}

	private int statRow(int px, int y, String labelKey, String value, long exact, boolean tooltip) {
		return statRow(px, y, labelKey, value, exact, tooltip, false);
	}

	/**
	 * One label/value line. The value is right-aligned so the column of numbers can be scanned vertically,
	 * and an abbreviated figure carries its exact value in a tooltip — the panel has room for "1.2M EU",
	 * the player checking a build needs the digits.
	 */
	private int statRow(int px, int y, String labelKey, String value, long exact, boolean tooltip,
			boolean dim) {
		int pw = PANEL_W;
		int color = dim ? GuiStyle.TEXT_DIM : GuiStyle.TEXT;
		int valueW = font.width(value);
		int valueX = px + pw - 9 - valueW;
		// The value is the number the player came for, so the LABEL is what gives way when a translation
		// is too long for the row: it is ellipsised to whatever space the value leaves, instead of the two
		// running into each other.
		int labelRoom = Math.max(0, STAT_TEXT_W - valueW - 4);
		FormattedCharSequence label = font.split(Component.translatable(labelKey), labelRoom)
				.stream().findFirst().orElse(FormattedCharSequence.EMPTY);
		graphics.text(font, label, px + 9, y, color, false);
		graphics.text(font, Component.literal(value), valueX, y, color, false);
		if (tooltip && mouseY >= y - 1 && mouseY < y + STAT_ROW_H - 1 && mouseX >= px + 7 && mouseX < px + pw - 7) {
			graphics.setTooltipForNextFrame(font,
					Component.literal(ReadoutFormat.exact(exact) + " EU"), mouseX, mouseY);
		}
		return y + STAT_ROW_H;
	}
}
