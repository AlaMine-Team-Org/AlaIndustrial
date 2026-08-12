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
 * REI category for the Canning Machine (MOD-383): food + empty can → arrow → canned ration, with the
 * shared "EU · time" label.
 *
 * <p>The same fixed two-in / one-out row as {@link AlaProcessingCategory}; a separate class only
 * because its display type differs and carries no recipe.
 */
public class CanningCategory implements DisplayCategory<CanningDisplay> {
	private final Component title;
	private final Block icon;

	public CanningCategory(Block icon, Component title) {
		this.icon = icon;
		this.title = title;
	}

	@Override
	public CategoryIdentifier<? extends CanningDisplay> getCategoryIdentifier() {
		return CanningDisplay.CATEGORY;
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
	public int getDisplayWidth(CanningDisplay display) {
		return RecipeViewerLayout.WIDTH;
	}

	@Override
	public List<Widget> setupDisplay(CanningDisplay display, Rectangle bounds) {
		List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));

		widgets.add(Widgets.createArrow(new Point(
				bounds.getX() + RecipeViewerLayout.ARROW_X,
				bounds.getY() + RecipeViewerLayout.ARROW_Y)));

		List<Integer> inputXs = RecipeViewerLayout.inputXs(display.getInputEntries().size());
		for (int i = 0; i < inputXs.size(); i++) {
			Point point = new Point(bounds.getX() + inputXs.get(i), bounds.getY() + RecipeViewerLayout.SLOT_Y);
			widgets.add(Widgets.createSlot(point).entries(display.getInputEntries().get(i)).markInput());
		}
		Point output = new Point(bounds.getX() + RecipeViewerLayout.outputXs(1).getFirst(),
				bounds.getY() + RecipeViewerLayout.SLOT_Y);
		widgets.add(Widgets.createSlot(output).entries(display.getOutputEntries().getFirst()).markOutput());

		// EU cost and time, in the same units every other machine category prints.
		String cost = RecipeViewerLayout.costLabel(display.energy(), display.processingTicks());
		widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(),
				bounds.getY() + RecipeViewerLayout.LABEL_Y), Component.literal(cost))
				.noShadow().color(0xFF404040, 0xFFBBBBBB));

		return widgets;
	}
}
