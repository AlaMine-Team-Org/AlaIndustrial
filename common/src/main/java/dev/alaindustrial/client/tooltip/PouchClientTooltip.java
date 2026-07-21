package dev.alaindustrial.client.tooltip;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.PouchContents;
import dev.alaindustrial.item.PouchTooltip;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Bundle-style visual tooltip for the Battery Pouch (MOD-052, player request): a grid of the stored
 * stacks (top-of-pouch first — what right-click takes out next) plus a fullness bar with the
 * weight numbers, mirroring the vanilla bundle tooltip's item grid + progress bar. Drawn with
 * plain fills and the item renderer — no vanilla sprites are referenced or copied.
 *
 * <p>EVERY stored stack gets a cell (player feedback: no "+N" counter — the player wants to see
 * exactly what is inside): up to 4 columns for small loads, 8 columns beyond, rows as needed.
 * The weight numbers ({@code "64 / 128"}) render centered inside the bar; the fill percent
 * ({@code "50%"}) renders just to the RIGHT of the bar, outside it (MOD-124 — inside the bar the
 * {@code "(100%)"} spilled past the bar edge at 100 % and looked broken). The percent is the same
 * weight/capacity ratio the bar is drawn from, so they can never disagree. The old redundant text
 * line in {@link MachineTooltips} was removed (MOD-119).
 */
public class PouchClientTooltip implements ClientTooltipComponent {
	private static final int CELL = 18;
	private static final int NARROW_COLUMNS = 4;
	private static final int WIDE_COLUMNS = 8;
	private static final int BAR_HEIGHT = 12;
	private static final int GAP = 2;

	private static final int CELL_BG = 0x44000000;
	private static final int BAR_BORDER = 0xFF373737;
	private static final int BAR_BG = 0xFF1D1D21;
	private static final int BAR_FILL = 0xFF6D6AFE; // bundle-like violet-blue, distinct from the gold EU bar
	private static final int BAR_TEXT = 0xFFFFFFFF;
	private static final int PERCENT_TEXT = 0xFFAAAAAA; // secondary grey, sits outside the bar

	private final PouchContents contents;

	public PouchClientTooltip(PouchTooltip tooltip) {
		this.contents = tooltip.contents();
	}

	private int cells() {
		return contents.items().size();
	}

	private int columns() {
		int cells = Math.max(cells(), 1);
		return cells <= 2 * NARROW_COLUMNS ? Math.min(cells, NARROW_COLUMNS) : WIDE_COLUMNS;
	}

	private int rows() {
		return (cells() + columns() - 1) / columns();
	}

	private int gridWidth() {
		return Math.max(columns() * CELL, NARROW_COLUMNS * CELL); // keep the bar readable even for one item
	}

	private int capacity() {
		return Math.max(1, Config.lvPouchCapacity);
	}

	private String percentLabel() {
		return (contents.weight() * 100 / capacity()) + "%";
	}

	@Override
	public int getWidth(Font font) {
		// Grid/bar width plus the percent label that sits to the RIGHT of the bar, outside it (MOD-124).
		return gridWidth() + GAP + font.width(percentLabel());
	}

	@Override
	public int getHeight(Font font) {
		return rows() * CELL + GAP + BAR_HEIGHT + GAP;
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
		List<ItemStack> items = contents.items();
		int columns = columns();
		for (int i = 0; i < items.size(); i++) {
			int cx = x + (i % columns) * CELL;
			int cy = y + (i / columns) * CELL;
			graphics.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, CELL_BG);
			ItemStack stack = items.get(items.size() - 1 - i); // top of the pouch first
			graphics.item(stack, cx, cy);
			graphics.itemDecorations(font, stack, cx, cy);
		}
		int barY = y + rows() * CELL + GAP;
		int barWidth = gridWidth();
		graphics.fill(x, barY, x + barWidth, barY + BAR_HEIGHT, BAR_BORDER);
		graphics.fill(x + 1, barY + 1, x + barWidth - 1, barY + BAR_HEIGHT - 1, BAR_BG);
		int capacity = capacity();
		int weight = contents.weight();
		int fill = Math.min(barWidth - 2, (barWidth - 2) * weight / capacity);
		if (fill > 0) {
			graphics.fill(x + 1, barY + 1, x + 1 + fill, barY + BAR_HEIGHT - 1, BAR_FILL);
		}
		// Weight numbers centered INSIDE the bar.
		graphics.centeredText(font, weight + " / " + capacity, x + barWidth / 2, barY + 2, BAR_TEXT);
		// Fill percent just to the RIGHT of the bar, outside it, so it can never overflow the bar
		// (MOD-124 — the old in-bar "(100%)" spilled past the edge at 100 %). Same weight/capacity
		// ratio the bar is drawn from, so the number can never disagree with the fill.
		String percent = weight * 100 / capacity + "%";
		graphics.centeredText(font, percent, x + barWidth + GAP + font.width(percent) / 2, barY + 2, PERCENT_TEXT);
	}
}
