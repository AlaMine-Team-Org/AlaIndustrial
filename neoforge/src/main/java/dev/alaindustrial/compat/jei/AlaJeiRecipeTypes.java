package dev.alaindustrial.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.CanningExchange;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.AlloyingRecipe;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;

final class AlaJeiRecipeTypes {
	static final IRecipeHolderType<AlaProcessingRecipe> MACERATION =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.MACERATION.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SMELTING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SMELTING.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> COMPRESSING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.COMPRESSING.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> EXTRACTING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.EXTRACTING.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> VULCANIZING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.VULCANIZING.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> GALVANIC_BATH =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.GALVANIC_BATH.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SAWING_PLANKS =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SAWING_PLANKS.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SAWING_STICKS =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SAWING_STICKS.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SAWING_SLABS =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SAWING_SLABS.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> SAWING_STAIRS =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.SAWING_STAIRS.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> MUTATION_TRANSFORM =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.MUTATION_TRANSFORM.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> MUTATION_DUPLICATE =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.MUTATION_DUPLICATE.id()));
	static final IRecipeHolderType<AlaProcessingRecipe> MUTATION_CREATE =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.MUTATION_CREATE.id()));

	/** The alloy smelter's multi-component family (MOD-064) — a different recipe class, so a separate type. */
	static final IRecipeHolderType<AlloyingRecipe> ALLOYING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.ALLOYING.id()));

	/** The Polymerizer's fluid → item family (MOD-019) — a different recipe class, so a separate type. */
	static final IRecipeHolderType<PolymerizingRecipe> POLYMERIZING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.POLYMERIZING.id()));
	/**
	 * Viewer type hook for MOD-251. Its category/catalyst are intentionally registered only when the
	 * real Distillation Column exists.
	 */
	static final IRecipeHolderType<FluidOutputRecipe> DISTILLING =
			IRecipeHolderType.create(Industrialization.id(ModRecipes.DISTILLING.id()));

	/**
	 * The Canning Machine (MOD-383). The one viewer type here that is NOT an {@link IRecipeHolderType}:
	 * the machine matches no JSON recipe, so there is no {@code Recipe} class to hold. Its cards are
	 * {@link CanningExchange.Card} records derived from the item registry, and
	 * {@link IRecipeType#create(net.minecraft.resources.Identifier, Class)} accepts any POJO.
	 */
	static final IRecipeType<CanningExchange.Card> CANNING =
			IRecipeType.create(Industrialization.id("canning"), CanningExchange.Card.class);

	private AlaJeiRecipeTypes() {
	}

	/** The fluid-fed families' viewer types by kind (MOD-251) — the FluidKind twin of {@link #byKind}. */
	static IRecipeHolderType<?> byFluidKind(ModRecipes.FluidKind<?> kind) {
		if (kind == ModRecipes.POLYMERIZING) {
			return POLYMERIZING;
		}
		if (kind == ModRecipes.DISTILLING) {
			return DISTILLING;
		}
		throw new IllegalArgumentException("Unknown Ala fluid recipe kind: " + kind.id());
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
		if (kind == ModRecipes.GALVANIC_BATH) {
			return GALVANIC_BATH;
		}
		if (kind == ModRecipes.VULCANIZING) {
			return VULCANIZING;
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
		if (kind == ModRecipes.MUTATION_TRANSFORM) {
			return MUTATION_TRANSFORM;
		}
		if (kind == ModRecipes.MUTATION_DUPLICATE) {
			return MUTATION_DUPLICATE;
		}
		if (kind == ModRecipes.MUTATION_CREATE) {
			return MUTATION_CREATE;
		}
		throw new IllegalArgumentException("Unknown Ala processing recipe kind: " + kind.id());
	}
}
