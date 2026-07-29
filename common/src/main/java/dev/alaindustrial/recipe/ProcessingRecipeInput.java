package dev.alaindustrial.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * The ordered item slots offered to an {@link AlaProcessingRecipe}.
 *
 * <p>Existing processing machines use the one-stack constructor. Machines with a genuinely
 * two-component recipe use the list constructor; the recipe matches the two slots positionally.
 */
public record ProcessingRecipeInput(List<ItemStack> items) implements RecipeInput {

	public ProcessingRecipeInput {
		items = List.copyOf(items);
		if (items.isEmpty() || items.size() > 2) {
			throw new IllegalArgumentException("processing recipe input must contain 1 or 2 item stacks");
		}
	}

	public ProcessingRecipeInput(ItemStack item) {
		this(List.of(item));
	}

	public ProcessingRecipeInput(ItemStack first, ItemStack second) {
		this(List.of(first, second));
	}

	@Override
	public ItemStack getItem(int index) {
		return items.get(index);
	}

	@Override
	public int size() {
		return items.size();
	}
}
