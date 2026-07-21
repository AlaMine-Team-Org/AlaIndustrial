package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV electric furnace — works exactly like a vanilla furnace (processes all vanilla smelting
 * recipes) but consumes EU instead of fuel, and is faster. Additionally accepts mod-specific
 * {@code alaindustrial:smelting} recipes (e.g. dusts → ingots) which take priority over vanilla
 * when both would match the same input.
 *
 * <p>Behaviour lives in {@link AbstractProcessingMachineBlockEntity}; this class only declares the
 * recipe source (mod-smelting first, then vanilla furnace fallback), the default duration, buffer
 * and menu.
 */
public final class ElectricFurnaceBlockEntity extends AbstractProcessingMachineBlockEntity implements MenuProvider {
	/** Input slot index — re-export of the shared processing-machine slot 0. */
	public static final int INPUT_SLOT = 0;
	/** Output slot index — re-export of the shared processing-machine slot 1. */
	public static final int OUTPUT_SLOT = 1;

	/** Mod-specific smelting recipes (priority over vanilla). */
	private final RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> modRecipeCheck = checkFor(ModRecipes.SMELTING);
	/** Vanilla furnace recipes — used as fallback when no mod recipe matches. */
	private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> vanillaRecipeCheck =
			RecipeManager.createCheck(RecipeType.SMELTING);

	public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ELECTRIC_FURNACE_BE.get(), pos, state, EnergyTier.LV, Config.machineBuffer, Config.electricFurnaceDuration);
	}

	@Override
	protected RecipeSolution resolveInput(ServerLevel level, ItemStack input) {
		// Mod recipes take priority — they may override a vanilla match with a different result/cost.
		AlaProcessingRecipe modRecipe = ModRecipes.lookup(modRecipeCheck, level, input);
		if (modRecipe != null) {
			return RecipeSolution.of(modRecipe);
		}
		// Vanilla fallback: a furnace recipe carries no EU cost, so the default duration applies.
		if (!input.isEmpty()) {
			SmeltingRecipe vanilla = vanillaRecipeCheck.getRecipeFor(new SingleRecipeInput(input), level)
					.map(RecipeHolder::value).orElse(null);
			if (vanilla != null) {
				ItemStack result = vanilla.assemble(new SingleRecipeInput(input));
				// energy = 0 → base class falls back to the machine's default duration.
				return new RecipeSolution(0, result);
			}
		}
		return RecipeSolution.empty();
	}

	@Override
	protected boolean canPlaceInput(ItemStack stack) {
		// Accept if a mod recipe OR a vanilla smelting recipe matches — re-checked against the live
		// level (resolveInput already does this, but the GUI calls canPlaceItem on the client side too,
		// so we keep the explicit server-side check here for the same reason the original did).
		if (stack.isEmpty() || !(level instanceof ServerLevel sl)) {
			return false;
		}
		if (ModRecipes.lookup(modRecipeCheck, sl, stack) != null) {
			return true;
		}
		return vanillaRecipeCheck.getRecipeFor(new SingleRecipeInput(stack), sl).isPresent();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.electric_furnace");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ElectricFurnaceMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
