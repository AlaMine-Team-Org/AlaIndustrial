package dev.alaindustrial.recipe;

import dev.alaindustrial.core.fluid.FluidHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * What a fluid-fed machine offers its recipe lookup (MOD-019): the fluid its tank holds and how much of
 * it, in mB. The vanilla counterpart for an item-fed machine is
 * {@link net.minecraft.world.item.crafting.SingleRecipeInput}; there is no vanilla fluid equivalent
 * because no vanilla machine consumes a fluid.
 *
 * <p>{@link RecipeInput} is an <em>item</em> contract (it exists so the recipe book and the crafting
 * grid can read a recipe's slots), so this input reports zero item slots. Nothing in the mod places a
 * fluid recipe in a grid — {@link PolymerizingRecipe#placementInfo()} answers
 * {@link net.minecraft.world.item.crafting.PlacementInfo#NOT_PLACEABLE} — so the empty item view is
 * never read, and being a {@code RecipeInput} is what lets these recipes ride the vanilla
 * {@link net.minecraft.world.item.crafting.RecipeManager} (datapack loading, {@code /reload}, tags)
 * instead of a hand-rolled loader.
 *
 * @param fluid  the fluid in the machine's tank ({@link FluidHolder#EMPTY} when it is empty)
 * @param amount how much of it is available, in mB
 */
public record FluidRecipeInput(FluidHolder fluid, long amount) implements RecipeInput {

	@Override
	public ItemStack getItem(int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		// The default implementation derives emptiness from the (always zero) item slots, which would
		// report every fluid input as empty. "Empty" here means the tank has nothing usable in it.
		return fluid.isEmpty() || amount <= 0;
	}
}
