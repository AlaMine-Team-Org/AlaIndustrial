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
		throw new IllegalArgumentException("Unknown Ala processing recipe kind: " + kind.id());
	}
}
