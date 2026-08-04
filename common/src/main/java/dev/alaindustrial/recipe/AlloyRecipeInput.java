package dev.alaindustrial.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * The three input slots offered to an {@link AlloyingRecipe}.
 *
 * <p>Unlike {@link ProcessingRecipeInput}, position carries no meaning here: the recipe matches the
 * slots as a multiset, so "copper, copper, tin" and "tin, copper, copper" are the same input. Empty
 * slots are kept in place rather than compacted out — {@link #filledSlots()} is what the strictness
 * rule reads, and a machine that compacted its slots could not tell "two components, spare slot empty"
 * from "two components, spare slot holds junk".
 */
public record AlloyRecipeInput(List<ItemStack> items) implements RecipeInput {

	/** Input slots on the alloy smelter. */
	public static final int SLOT_COUNT = 3;

	public AlloyRecipeInput {
		items = List.copyOf(items);
		if (items.size() != SLOT_COUNT) {
			throw new IllegalArgumentException("alloy recipe input must contain exactly "
					+ SLOT_COUNT + " item stacks");
		}
	}

	public AlloyRecipeInput(ItemStack first, ItemStack second, ItemStack third) {
		this(List.of(first, second, third));
	}

	@Override
	public ItemStack getItem(int index) {
		return items.get(index);
	}

	@Override
	public int size() {
		return items.size();
	}

	/** How many input slots hold something. The strictness rule compares this to the component count. */
	public int filledSlots() {
		int filled = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				filled++;
			}
		}
		return filled;
	}

	@Override
	public boolean isEmpty() {
		return filledSlots() == 0;
	}
}
