package dev.alaindustrial.compat.rei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * REI category for the Polymerizer (MOD-019): fluid → arrow → item, with an "EU · time · volume" label.
 * The same single-row layout as {@link AlaProcessingCategory}; it is a separate class only because its
 * display type differs (a fluid input instead of an item one) and the label carries the consumed volume.
 */
public class PolymerizingCategory implements DisplayCategory<PolymerizingDisplay> {
	private final Component title;
	private final Block icon;

	public PolymerizingCategory(Block icon, Component title) {
		this.icon = icon;
		this.title = title;
	}

	@Override
	public CategoryIdentifier<? extends PolymerizingDisplay> getCategoryIdentifier() {
		return PolymerizingDisplay.CATEGORY;
	}

	@Override
	public Component getTitle() {
		return title;
	}

	@Override
	public Renderer getIcon() {
		return EntryStacks.of(icon);
	}

	@Override
	public int getDisplayHeight() {
		return 48;
	}

	@Override
	public List<Widget> setupDisplay(PolymerizingDisplay display, Rectangle bounds) {
		List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));

		int centerX = bounds.getCenterX();
		int rowY = bounds.getY() + 6;
		Point input = new Point(centerX - 35, rowY);
		Point arrow = new Point(centerX - 12, rowY - 1);
		Point output = new Point(centerX + 19, rowY);

		widgets.add(Widgets.createArrow(arrow));
		if (!display.getInputEntries().isEmpty()) {
			widgets.add(Widgets.createSlot(input).entries(display.getInputEntries().get(0)).markInput());
		}
		if (!display.getOutputEntries().isEmpty()) {
			widgets.add(Widgets.createSlot(output).entries(display.getOutputEntries().get(0)).markOutput());
		}

		// EU cost, intrinsic time and the volume one operation drinks. Units (EU / s / mB) are symbols —
		// literal, no lang keys, as in every other machine category.
		String cost = display.energy() + " EU · " + formatSeconds(display.processingTicks())
				+ " · " + display.amountMb() + " mB";
		widgets.add(Widgets.createLabel(new Point(centerX, bounds.getMaxY() - 12), Component.literal(cost))
				.noShadow().color(0xFF404040, 0xFFBBBBBB));

		return widgets;
	}

	/** Ticks → "N.N s" (20 ticks = 1 s), trimming a trailing ".0". */
	private static String formatSeconds(int ticks) {
		String seconds = String.format(Locale.ROOT, "%.1f", ticks / 20.0);
		if (seconds.endsWith(".0")) {
			seconds = seconds.substring(0, seconds.length() - 2);
		}
		return seconds + " s";
	}
}
