package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.client.screen.MagnetScreen;
import dev.alaindustrial.item.tool.MagnetFilter;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * Drag an item from JEI onto a magnet filter cell (MOD-592). Only item ingredients, and only while a
 * filter is fitted — with no filter the cells are not there to drop onto.
 */
final class MagnetFilterGhostHandler implements IGhostIngredientHandler<MagnetScreen> {

	@Override
	public <I> List<Target<I>> getTargetsTyped(MagnetScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
		ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
		if (stack.isEmpty() || !screen.acceptsSamples()) {
			return List.of();
		}
		List<Target<I>> targets = new ArrayList<>(MagnetFilter.CELLS);
		for (int i = 0; i < MagnetFilter.CELLS; i++) {
			int cell = i;
			Rect2i area = screen.cellArea(i);
			targets.add(new Target<>() {
				@Override
				public Rect2i getArea() {
					return area;
				}

				@Override
				public void accept(I dropped) {
					MagnetScreen.sendSample(cell, stack);
				}
			});
		}
		return targets;
	}

	@Override
	public void onComplete() {
	}
}
