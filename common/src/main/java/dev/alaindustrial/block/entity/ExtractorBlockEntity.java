package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.ExtractorMenu;
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
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV extractor — extracts items (blaze rod → blaze powder, gravel → flint) spending EU over the
 * configured number of ticks. Recipes are real vanilla recipes ({@link ModRecipes#EXTRACTING})
 * loaded from {@code data/<ns>/recipe/extracting/*.json}; the input may be an item or a tag
 * (R-14/R-15).
 *
 * <p>Behaviour lives in {@link AbstractProcessingMachineBlockEntity}; this class only declares the
 * recipe source, default duration, buffer and menu.
 */
public final class ExtractorBlockEntity extends AbstractProcessingMachineBlockEntity implements MenuProvider {
	/** Input slot index — re-export of the shared processing-machine slot 0. */
	public static final int INPUT_SLOT = 0;
	/** Output slot index — re-export of the shared processing-machine slot 1. */
	public static final int OUTPUT_SLOT = 1;

	private final RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> recipeCheck = checkFor(ModRecipes.EXTRACTING);

	public ExtractorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.EXTRACTOR_BE.get(), pos, state, EnergyTier.LV, Config.machineBuffer, Config.extractorDuration);
	}

	@Override
	protected RecipeSolution resolveInput(ServerLevel level, ItemStack input) {
		AlaProcessingRecipe recipe = lookupKind(recipeCheck, level, input);
		return recipe != null ? RecipeSolution.of(recipe) : RecipeSolution.empty();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.extractor");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ExtractorMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
