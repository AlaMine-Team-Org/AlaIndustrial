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
 * REI category for one processing machine. All four machines share this class — they differ only by
 * their {@link CategoryIdentifier}, title and icon (the machine block). Layout is a single
 * input → arrow → output row with an "EU · time" label underneath.
 */
public class AlaProcessingCategory implements DisplayCategory<AlaProcessingDisplay> {
	private final CategoryIdentifier<AlaProcessingDisplay> identifier;
	private final Component title;
	private final Block icon;

	public AlaProcessingCategory(CategoryIdentifier<AlaProcessingDisplay> identifier, Block icon,
			Component title) {
		this.identifier = identifier;
		this.icon = icon;
		// Composed by the caller from already-localised parts (block name + mode) — no new lang keys.
		this.title = title;
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
		return RecipeViewerLayout.HEIGHT;
	}

	@Override
	public int getDisplayWidth(AlaProcessingDisplay display) {
		return RecipeViewerLayout.WIDTH;
	}

	@Override
	public List<Widget> setupDisplay(AlaProcessingDisplay display, Rectangle bounds) {
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
		List<Integer> outputXs = RecipeViewerLayout.outputXs(display.getOutputEntries().size());
		for (int i = 0; i < outputXs.size(); i++) {
			Point point = new Point(bounds.getX() + outputXs.get(i), bounds.getY() + RecipeViewerLayout.SLOT_Y);
			widgets.add(Widgets.createSlot(point).entries(display.getOutputEntries().get(i)).markOutput());
		}

		// EU cost + intrinsic time, and the success chance where the operation is a gamble (the
		// incubator). Units (EU / s / %) are symbols — literal, no lang keys.
		String cost = RecipeViewerLayout.withChance(
				RecipeViewerLayout.costLabel(display.energy(), display.processingTicks()), display.chance());
		widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(),
				bounds.getY() + RecipeViewerLayout.LABEL_Y), Component.literal(cost))
				.noShadow().color(0xFF404040, 0xFFBBBBBB));

		return widgets;
	}
}
