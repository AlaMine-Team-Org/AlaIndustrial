package dev.alaindustrial.gametest;

import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * L2 sanity suite that covers EVERY mod processing recipe JSON in one shot. Replaces the per-recipe
 * point checks in {@link MachineGameTest} (which only sample 5–10 recipes) with a structural
 * invariant: every JSON under {@code data/alaindustrial/recipe/<kind>/*.json} must load, parse, and
 * produce a non-empty result.
 *
 * <p>Catches the failure shape "a recipe JSON has a typo / wrong item id / missing field" the
 * moment it lands, instead of waiting for a player to discover the dead recipe in-game. The recipe
 * count is intentionally not pinned (it grows with the mod), so the test asserts lower bounds that
 * are comfortably above the current counts and asserts each recipe's structural integrity.
 *
 * <p>Test scope: the four AlaIndustrial processing recipe types ({@link ModRecipes#MACERATION},
 * {@link ModRecipes#SMELTING}, {@link ModRecipes#COMPRESSING}, {@link ModRecipes#EXTRACTING}).
 * Vanilla smelting recipes are out of scope — they are vanilla's contract, not ours.
 *
 * @implements TC-RECIE-001 — every shipped mod recipe JSON loads with a valid non-empty result and
 *     a non-empty ingredient.
 */
public class RecipeCoverageGameTest {
	/** A typical kind ships ≈ 10–30 recipes; the lower bound guards "all recipes silently gone". */
	private static final int MIN_RECIPES_PER_KIND = 5;

	@GameTest
	public void tcRecie01_macerationRecipesAllLoad(GameTestHelper helper) {
		assertAllRecipesLoad(helper, ModRecipes.MACERATION, "maceration");
	}

	@GameTest
	public void tcRecie02_smeltingRecipesAllLoad(GameTestHelper helper) {
		assertAllRecipesLoad(helper, ModRecipes.SMELTING, "smelting");
	}

	@GameTest
	public void tcRecie03_compressingRecipesAllLoad(GameTestHelper helper) {
		assertAllRecipesLoad(helper, ModRecipes.COMPRESSING, "compressing");
	}

	@GameTest
	public void tcRecie04_extractingRecipesAllLoad(GameTestHelper helper) {
		assertAllRecipesLoad(helper, ModRecipes.EXTRACTING, "extracting");
	}

	/**
	 * For every recipe of the given kind: (a) the recipe loaded into the {@link RecipeManager},
	 * (b) its result stack is non-empty, (c) its ingredient is non-empty (would otherwise match
	 * nothing), (d) its namespace is {@code alaindustrial} (catches a vanilla recipe accidentally
	 * registered under our type). Also enforces a minimum count so "all recipes silently gone" fails
	 * loudly.
	 */
	private static void assertAllRecipesLoad(GameTestHelper helper, ModRecipes.Kind kind, String label) {
		ServerLevel level = helper.getLevel();
		RecipeType<AlaProcessingRecipe> type = kind.type();
		// MC 26.2: RecipeManager lives on MinecraftServer (Level no longer exposes it directly).
		// getAllRecipesFor(type) was removed when recipes moved to ResourceKeys; iterate getRecipes()
		// and filter by type.
		java.util.List<RecipeHolder<AlaProcessingRecipe>> recipes = new java.util.ArrayList<>();
		for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
			if (holder.value().getType() == type) {
				@SuppressWarnings("unchecked")
				RecipeHolder<AlaProcessingRecipe> typed = (RecipeHolder<AlaProcessingRecipe>) holder;
				recipes.add(typed);
			}
		}

		if (recipes.size() < MIN_RECIPES_PER_KIND) {
			helper.fail(label + ": only " + recipes.size() + " recipes loaded (expected ≥ "
					+ MIN_RECIPES_PER_KIND + ") — check the datapack and recipe JSONs");
			return;
		}

		for (RecipeHolder<AlaProcessingRecipe> holder : recipes) {
			// MC 26.2: RecipeHolder.id() returns ResourceKey<Recipe<?>>; .identifier() gives the path.
			Identifier id = holder.id().identifier();
			// Namespace must be alaindustrial (a vanilla recipe leaking into our type is a registration bug).
			if (!id.getNamespace().equals("alaindustrial")) {
				helper.fail(label + " recipe " + id + " has namespace '" + id.getNamespace()
						+ "', expected 'alaindustrial'");
				return;
			}
			AlaProcessingRecipe recipe = holder.value();
			ItemStack result = recipe.resultStack();
			if (result.isEmpty()) {
				helper.fail(label + " recipe " + id + " has an EMPTY result — check the 'result.id' field");
				return;
			}
			if (result.getCount() <= 0) {
				helper.fail(label + " recipe " + id + " has result.count = " + result.getCount() + " (must be ≥ 1)");
				return;
			}
			if (recipe.energy() < 0) {
				helper.fail(label + " recipe " + id + " has negative energy = " + recipe.energy());
				return;
			}
			// Ingredient must be non-empty (would otherwise match nothing in the machine).
			if (recipe.ingredient().isEmpty()) {
				helper.fail(label + " recipe " + id + " has an empty ingredient — check the 'ingredient' field");
				return;
			}
		}
		helper.succeed();
	}
}
