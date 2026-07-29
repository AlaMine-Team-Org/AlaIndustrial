package dev.alaindustrial.compat.rei;

import dev.alaindustrial.client.compat.RecipeViewerLayout;
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
		return RecipeViewerLayout.HEIGHT;
	}

	@Override
	public int getDisplayWidth(PolymerizingDisplay display) {
		return RecipeViewerLayout.WIDTH;
	}

	@Override
	public List<Widget> setupDisplay(PolymerizingDisplay display, Rectangle bounds) {
		List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));

		widgets.add(Widgets.createArrow(new Point(
				bounds.getX() + RecipeViewerLayout.ARROW_X,
				bounds.getY() + RecipeViewerLayout.ARROW_Y)));
		Point input = new Point(bounds.getX() + RecipeViewerLayout.inputXs(1).getFirst(),
				bounds.getY() + RecipeViewerLayout.SLOT_Y);
		widgets.add(Widgets.createSlot(input).entries(display.getInputEntries().getFirst()).markInput());
		Point output = new Point(bounds.getX() + RecipeViewerLayout.outputXs(1).getFirst(),
				bounds.getY() + RecipeViewerLayout.SLOT_Y);
		widgets.add(Widgets.createSlot(output).entries(display.getOutputEntries().getFirst()).markOutput());

		// EU cost, intrinsic time and the volume one operation drinks. Units (EU / s / mB) are symbols —
		// literal, no lang keys, as in every other machine category.
		String cost = RecipeViewerLayout.fluidCostLabel(
				display.energy(), display.processingTicks(), display.amountMb());
		widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(),
				bounds.getY() + RecipeViewerLayout.LABEL_Y), Component.literal(cost))
				.noShadow().color(0xFF404040, 0xFFBBBBBB));

		return widgets;
	}
}
