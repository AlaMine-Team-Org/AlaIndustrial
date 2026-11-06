package dev.alaindustrial.gametest;

import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluids;

/**
 * Recipes written by a pack author (MOD-953): the machine recipes are plain data-pack JSON, so a pack that ships
 * {@code data/<ns>/recipe/*.json} with one of our types must be read by our serializers without any code of ours
 * knowing the pack. The fixtures live in the gametest resources under their own namespace
 * ({@code data/alaindustrial_gametest/recipe/}), one copy per loader: the gametest source set is its own mod there.
 */
public final class RecipeDatapackScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RecipeDatapackScenarios::packMacerationIsLoadedAndMatches,
						"recipe_datapack_maceration_loaded_and_matches"),
				RosterEntry.of(RecipeDatapackScenarios::packSecondaryResultIsRead,
						"recipe_datapack_secondary_result_read"),
				RosterEntry.of(RecipeDatapackScenarios::packTwoInputCountsAreRead,
						"recipe_datapack_input_counts_read"),
				RosterEntry.of(RecipeDatapackScenarios::packFluidFamilyIsRead,
						"recipe_datapack_polymerizing_read"),
				RosterEntry.of(RecipeDatapackScenarios::falseLoadConditionKeepsARecipeOut,
						"recipe_datapack_false_condition_not_loaded"));

		private Roster() {}
	}

	private RecipeDatapackScenarios() {
	}

	private static ResourceKey<Recipe<?>> fixture(String path) {
		return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("alaindustrial_gametest", path));
	}

	private static Optional<RecipeHolder<?>> loaded(GameTestHelper helper, String path) {
		RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
		Optional<RecipeHolder<?>> holder = manager.byKey(fixture(path));
		if (holder.isEmpty()) {
			helper.fail("the pack recipe alaindustrial_gametest:" + path + " was not loaded");
		}
		return holder;
	}

	/** The fixture recipe as a processing recipe of {@code kind}, or a failed test and {@code null}. */
	private static AlaProcessingRecipe processing(GameTestHelper helper, String path, ModRecipes.Kind kind) {
		Optional<RecipeHolder<?>> holder = loaded(helper, path);
		if (holder.isEmpty()) {
			return null;
		}
		if (!(holder.get().value() instanceof AlaProcessingRecipe recipe) || recipe.kind() != kind) {
			helper.fail("alaindustrial_gametest:" + path + " loaded as " + holder.get().value()
					+ " instead of a " + kind.id() + " recipe");
			return null;
		}
		return recipe;
	}

	private static boolean resultIs(GameTestHelper helper, String what, ItemStackTemplate result, Item item,
			int count) {
		if (!result.item().value().equals(item) || result.count() != count) {
			helper.fail(what + ": expected " + count + " x " + item + ", got " + result.count() + " x "
					+ result.item().value());
			return false;
		}
		return true;
	}

	/**
	 * A one-input maceration recipe from a pack is in the manager with its output and energy, and the macerator's own
	 * cached lookup finds it for the input stack.
	 *
	 * @implements MOD-953 — a pack's alaindustrial:maceration JSON is read by our serializer.
	 */
	public static void packMacerationIsLoadedAndMatches(GameTestHelper helper) {
		AlaProcessingRecipe recipe = processing(helper, "maceration_basic", ModRecipes.MACERATION);
		if (recipe == null) {
			return;
		}
		if (!resultIs(helper, "maceration_basic", recipe.result(), Items.DIAMOND, 9)) {
			return;
		}
		if (recipe.energy() != 321) {
			helper.fail("the pack energy 321 must survive loading, got " + recipe.energy());
			return;
		}
		ServerLevel level = helper.getLevel();
		AlaProcessingRecipe found = ModRecipes.lookup(ModRecipes.MACERATION.newCheck(), level,
				new ItemStack(Items.DIAMOND_BLOCK));
		if (found == null || found.energy() != 321) {
			helper.fail("the macerator lookup must find the pack recipe for a diamond block, got " + found);
			return;
		}
		helper.succeed();
	}

	/**
	 * {@code secondary_result} with {@code count} and {@code chance} survives loading unchanged.
	 *
	 * @implements MOD-953 — the optional bonus output is readable from pack JSON.
	 */
	public static void packSecondaryResultIsRead(GameTestHelper helper) {
		AlaProcessingRecipe recipe = processing(helper, "maceration_secondary", ModRecipes.MACERATION);
		if (recipe == null) {
			return;
		}
		if (!resultIs(helper, "maceration_secondary", recipe.result(), Items.EMERALD, 9)) {
			return;
		}
		AlaProcessingRecipe.SecondaryOutput secondary = recipe.secondaryResult().orElse(null);
		if (secondary == null) {
			helper.fail("secondary_result was dropped while loading");
			return;
		}
		if (!resultIs(helper, "secondary_result", secondary.stack(), Items.GRAVEL, 2)) {
			return;
		}
		if (secondary.chance() != 0.25) {
			helper.fail("secondary chance 0.25 must survive loading, got " + secondary.chance());
			return;
		}
		helper.succeed();
	}

	/**
	 * Two {@code ingredients} with {@code input_counts} load as two inputs with their counts, in order.
	 *
	 * @implements MOD-953 — the two-input shape with per-input counts is readable from pack JSON.
	 */
	public static void packTwoInputCountsAreRead(GameTestHelper helper) {
		AlaProcessingRecipe recipe = processing(helper, "vulcanizing_counts", ModRecipes.VULCANIZING);
		if (recipe == null) {
			return;
		}
		if (recipe.ingredients().size() != 2 || !recipe.inputCounts().equals(List.of(3, 2))) {
			helper.fail("expected 2 inputs with counts [3, 2], got " + recipe.ingredients().size()
					+ " inputs with counts " + recipe.inputCounts());
			return;
		}
		ProcessingRecipeInput input = new ProcessingRecipeInput(
				new ItemStack(Items.CLAY_BALL, 3), new ItemStack(Items.GUNPOWDER, 2));
		if (!recipe.matches(input, helper.getLevel()) || !recipe.hasEnough(input)) {
			helper.fail("the pack recipe must match and be satisfied by 3 clay balls and 2 gunpowder");
			return;
		}
		if (!resultIs(helper, "vulcanizing_counts", recipe.result(), Items.BRICK, 2)) {
			return;
		}
		helper.succeed();
	}

	/**
	 * The fluid family reads the same way: fluid, volume, result and energy of a pack polymerizing recipe.
	 *
	 * @implements MOD-953 — a fluid-input family is readable from pack JSON with no code change.
	 */
	public static void packFluidFamilyIsRead(GameTestHelper helper) {
		Optional<RecipeHolder<?>> holder = loaded(helper, "polymerizing_water");
		if (holder.isEmpty()) {
			return;
		}
		if (!(holder.get().value() instanceof PolymerizingRecipe recipe)
				|| recipe.kind() != ModRecipes.POLYMERIZING) {
			helper.fail("polymerizing_water loaded as " + holder.get().value());
			return;
		}
		if (recipe.amount() != 500 || recipe.energy() != 123 || !recipe.accepts(Fluids.WATER)) {
			helper.fail("expected water, 500, 123 EU; got amount " + recipe.amount() + ", energy "
					+ recipe.energy() + ", accepts water " + recipe.accepts(Fluids.WATER));
			return;
		}
		if (!resultIs(helper, "polymerizing_water", recipe.result(), Items.SPONGE, 1)) {
			return;
		}
		helper.succeed();
	}

	/**
	 * Two otherwise identical recipes carry BOTH loaders' condition keys ({@code neoforge:conditions} and
	 * {@code fabric:load_conditions}); the loader reads its own key and ignores the other. The true one loads, which
	 * proves the key parses (a mistyped condition would drop both); the false one must not.
	 *
	 * @implements MOD-953 — a false load condition removes a recipe, which is how a pack disables one of ours.
	 */
	public static void falseLoadConditionKeepsARecipeOut(GameTestHelper helper) {
		RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
		if (manager.byKey(fixture("conditional_true")).isEmpty()) {
			helper.fail("the recipe with a TRUE load condition must load (otherwise the condition syntax is wrong)");
			return;
		}
		if (manager.byKey(fixture("conditional_false")).isPresent()) {
			helper.fail("the recipe with a FALSE load condition must not be loaded");
			return;
		}
		helper.succeed();
	}
}
