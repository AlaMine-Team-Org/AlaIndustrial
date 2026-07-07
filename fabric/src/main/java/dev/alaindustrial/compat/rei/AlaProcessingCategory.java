package dev.alaindustrial.compat.rei;

import java.util.ArrayList;
import java.util.List;
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
 * REI category for one processing machine. All four machines share this class — they differ only by
 * their {@link CategoryIdentifier}, title and icon (the machine block). Layout is a single
 * input → arrow → output row with an "EU · time" label underneath.
 */
public class AlaProcessingCategory implements DisplayCategory<AlaProcessingDisplay> {
	private final CategoryIdentifier<AlaProcessingDisplay> identifier;
	private final Component title;
	private final Block icon;

	public AlaProcessingCategory(CategoryIdentifier<AlaProcessingDisplay> identifier, Block icon) {
		this.identifier = identifier;
		this.icon = icon;
		// Reuse the machine block's own (already-localised) name — no new lang keys needed.
		this.title = icon.getName();
	}

	@Override
	public CategoryIdentifier<? extends AlaProcessingDisplay> getCategoryIdentifier() {
		return identifier;
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
	public List<Widget> setupDisplay(AlaProcessingDisplay display, Rectangle bounds) {
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

		// EU cost + intrinsic time. Units (EU / s) are symbols — literal, no lang keys.
		String cost = display.energy() + " EU · " + formatSeconds(display.processingTicks());
		widgets.add(Widgets.createLabel(new Point(centerX, bounds.getMaxY() - 12), Component.literal(cost))
				.noShadow().color(0xFF404040, 0xFFBBBBBB));

		return widgets;
	}

	/** Ticks → "N.N s" (20 ticks = 1 s), trimming a trailing ".0". */
	private static String formatSeconds(int ticks) {
		double seconds = ticks / 20.0;
		String s = String.format(java.util.Locale.ROOT, "%.1f", seconds);
		if (s.endsWith(".0")) {
			s = s.substring(0, s.length() - 2);
		}
		return s + " s";
	}
}
