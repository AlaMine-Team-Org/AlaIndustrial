package dev.alaindustrial.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import mezz.jei.api.recipe.types.IRecipeHolderType;

final class AlaJeiRecipeTypes {
	static final IRecipeHolderType<AlaProcessingRecipe> MACERATION =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.MACERATION.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SMELTING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SMELTING.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> COMPRESSING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.COMPRESSING.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> EXTRACTING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.EXTRACTING.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SAWING_PLANKS =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SAWING_PLANKS.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SAWING_STICKS =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SAWING_STICKS.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SAWING_SLABS =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SAWING_SLABS.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SAWING_STAIRS =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SAWING_STAIRS.id()));

	private AlaJeiRecipeTypes() {
	}

	static IRecipeHolderType<AlaProcessingRecipe> byKind(ModRecipes.Kind kind) {
		if (kind == ModRecipes.MACERATION) {
			return MACERATION;
		}
		if (kind == ModRecipes.SMELTING) {
			return SMELTING;
		}
		if (kind == ModRecipes.COMPRESSING) {
			return COMPRESSING;
		}
		if (kind == ModRecipes.EXTRACTING) {
			return EXTRACTING;
		}
		if (kind == ModRecipes.SAWING_PLANKS) {
			return SAWING_PLANKS;
		}
		if (kind == ModRecipes.SAWING_STICKS) {
			return SAWING_STICKS;
		}
		if (kind == ModRecipes.SAWING_SLABS) {
			return SAWING_SLABS;
		}
		if (kind == ModRecipes.SAWING_STAIRS) {
			return SAWING_STAIRS;
		}
		throw new IllegalArgumentException("Unknown Ala processing recipe kind: " + kind.id());
	}
}
