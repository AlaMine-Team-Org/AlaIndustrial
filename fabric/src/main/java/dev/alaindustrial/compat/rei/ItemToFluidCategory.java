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
import net.minecraft.world.level.ItemLike;

/**
 * REI category for an item → fluid family (MOD-924, the Honey Centrifuge): item → arrow → fluid (and the
 * returned container), with an "EU · time · volume" label. The same single-row layout as the other machine
 * categories.
 */
public class ItemToFluidCategory implements DisplayCategory<ItemToFluidDisplay> {
	private final Component title;
	private final ItemLike icon;

	public ItemToFluidCategory(ItemLike icon, Component title) {
		this.icon = icon;
		this.title = title;
	}

	@Override
	public CategoryIdentifier<? extends ItemToFluidDisplay> getCategoryIdentifier() {
		return ItemToFluidDisplay.CATEGORY;
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
	public int getDisplayWidth(ItemToFluidDisplay display) {
		return RecipeViewerLayout.WIDTH;
	}

	@Override
	public List<Widget> setupDisplay(ItemToFluidDisplay display, Rectangle bounds) {
		List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));

		widgets.add(Widgets.createArrow(new Point(
				bounds.getX() + RecipeViewerLayout.ARROW_X,
				bounds.getY() + RecipeViewerLayout.ARROW_Y)));
		Point input = new Point(bounds.getX() + RecipeViewerLayout.inputXs(1).getFirst(),
				bounds.getY() + RecipeViewerLayout.SLOT_Y);
		widgets.add(Widgets.createSlot(input).entries(display.getInputEntries().getFirst()).markInput());
		int outputs = display.getOutputEntries().size();
		List<Integer> outputXs = RecipeViewerLayout.outputXs(outputs);
		for (int i = 0; i < outputs; i++) {
			Point output = new Point(bounds.getX() + outputXs.get(i), bounds.getY() + RecipeViewerLayout.SLOT_Y);
			widgets.add(Widgets.createSlot(output).entries(display.getOutputEntries().get(i)).markOutput());
		}

		String cost = RecipeViewerLayout.fluidCostLabel(
				display.energy(), display.processingTicks(), display.amountMb());
		widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(),
				bounds.getY() + RecipeViewerLayout.LABEL_Y), Component.literal(cost))
				.noShadow().color(0xFF404040, 0xFFBBBBBB));

		return widgets;
	}
}
