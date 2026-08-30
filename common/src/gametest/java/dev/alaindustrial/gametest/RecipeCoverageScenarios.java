package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.VanillaSmeltingMirror;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;

/**
 * L2 sanity suite that covers EVERY mod processing recipe JSON in one shot. Replaces the per-recipe
 * point checks in {@code MachineGameTest} (which only sample 5–10 recipes) with a structural
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
public final class RecipeCoverageScenarios {

	private RecipeCoverageScenarios() {}

	/** A typical kind ships ≈ 10–30 recipes; the lower bound guards "all recipes silently gone". */
	private static final int MIN_RECIPES_PER_KIND = 5;

	public static void tcRecie01_macerationRecipesAllLoad(GameTestHelper helper) {
		assertAllRecipesLoad(helper, ModRecipes.MACERATION, "maceration");
	}

	public static void tcRecie02_smeltingRecipesAllLoad(GameTestHelper helper) {
		assertAllRecipesLoad(helper, ModRecipes.SMELTING, "smelting");
	}

	public static void tcRecie03_compressingRecipesAllLoad(GameTestHelper helper) {
		assertAllRecipesLoad(helper, ModRecipes.COMPRESSING, "compressing");
	}

	public static void tcRecie04_extractingRecipesAllLoad(GameTestHelper helper) {
		assertAllRecipesLoad(helper, ModRecipes.EXTRACTING, "extracting");
	}

	/** MOD-257: the second generic fluid family must be registered and bound on the running loader. */
	public static void tcRecie11_distillingRecipeFamilyRegistered(GameTestHelper helper) {
		Identifier id = Industrialization.id(ModRecipes.DISTILLING.id());
		if (!BuiltInRegistries.RECIPE_TYPE.containsKey(id)) {
			helper.fail("distilling RecipeType is missing from the loader registry");
			return;
		}
		if (!BuiltInRegistries.RECIPE_SERIALIZER.containsKey(id)) {
			helper.fail("distilling RecipeSerializer is missing from the loader registry");
			return;
		}
		if (BuiltInRegistries.RECIPE_TYPE.getValue(id) != ModRecipes.DISTILLING.type()) {
			helper.fail("distilling FluidKind is not bound to its loader RecipeType");
			return;
		}
		if (BuiltInRegistries.RECIPE_SERIALIZER.getValue(id) != ModRecipes.DISTILLING.serializer()) {
			helper.fail("distilling FluidKind is not bound to its loader RecipeSerializer");
			return;
		}
		helper.succeed();
	}

	// --- Crafting-recipe resolve guards (MOD-225): the four processing scans above cover machine
	// PROCESSING recipes; these cover the crafting_shaped/shapeless recipes for the machine casing,
	// the retrofitted machine crafts and the plate blocks — a typo in an item id there loads no
	// recipe and is caught by neither recipeCheck nor recipeAdvancementCheck. Each builds the exact
	// grid and asserts the recipe resolves to the expected result. ---

	/** @implements TC-RECIE-005 — machine_casing crafts from an 8-iron-plate ring. */
	public static void tcRecie05_machineCasingResolves(GameTestHelper helper) {
		ItemStack p = s(ModContent.IRON_PLATE.get());
		assertCraftResolves(helper, 3, 3,
				List.of(p, p, p, p, ItemStack.EMPTY, p, p, p, p),
				ModContent.MACHINE_CASING_ITEM.get(), "machine_casing");
	}

	/** @implements TC-RECIE-006 — macerator crafts on the casing (FFF/IMI/IEI, retrofit). */
	public static void tcRecie06_maceratorResolves(GameTestHelper helper) {
		assertCraftResolves(helper, 3, 3, machineGrid(s(Items.FLINT), s(Items.FLINT), s(Items.FLINT)),
				ModContent.MACERATOR_ITEM.get(), "macerator");
	}

	/** @implements TC-RECIE-007 — extractor crafts on the casing (HIH/IMI/IEI, retrofit). */
	public static void tcRecie07_extractorResolves(GameTestHelper helper) {
		assertCraftResolves(helper, 3, 3, machineGrid(s(Items.HOPPER), s(Items.IRON_INGOT), s(Items.HOPPER)),
				ModContent.EXTRACTOR_ITEM.get(), "extractor");
	}

	/** @implements TC-RECIE-008 — compressor crafts on the casing (PIP/IMI/IEI, retrofit). */
	public static void tcRecie08_compressorResolves(GameTestHelper helper) {
		assertCraftResolves(helper, 3, 3, machineGrid(s(Items.PISTON), s(Items.IRON_INGOT), s(Items.PISTON)),
				ModContent.COMPRESSOR_ITEM.get(), "compressor");
	}

	/** @implements TC-RECIE-009 — silver_plate_block crafts from 4 silver plates (2x2). */
	public static void tcRecie09_silverPlateBlockResolves(GameTestHelper helper) {
		ItemStack p = s(ModContent.SILVER_PLATE.get());
		assertCraftResolves(helper, 2, 2, List.of(p, p, p, p),
				ModContent.SILVER_PLATE_BLOCK_ITEM.get(), "silver_plate_block");
	}

	/** @implements TC-RECIE-010 — tempered_iron_plate_block crafts from 4 tempered-iron plates (2x2). */
	public static void tcRecie10_temperedIronPlateBlockResolves(GameTestHelper helper) {
		ItemStack p = s(ModContent.TEMPERED_IRON_PLATE.get());
		assertCraftResolves(helper, 2, 2, List.of(p, p, p, p),
				ModContent.TEMPERED_IRON_PLATE_BLOCK_ITEM.get(), "tempered_iron_plate_block");
	}

	// --- Recipe-viewer category (MOD-523): the electric furnace lists each smelt exactly once. ---

	/**
	 * Items smelted by BOTH an {@code alaindustrial:smelting} recipe and the {@code minecraft:smelting}
	 * duplicate the Iron Furnace needs (MOD-455). Each was listed twice in the machine's own category
	 * until MOD-523: once as the mod recipe, once as the mirror of our own duplicate.
	 */
	private static final List<String> SMELTS_WITH_A_PARITY_DUPLICATE = List.of(
			"alaindustrial:copper_dust", "alaindustrial:gold_dust", "alaindustrial:iron_dust",
			"alaindustrial:nickel_dust", "alaindustrial:silver_dust", "alaindustrial:tin_dust",
			"alaindustrial:raw_nickel", "alaindustrial:raw_silver", "alaindustrial:raw_tin",
			"alaindustrial:raw_uranium", "alaindustrial:raw_palladium", "minecraft:iron_ingot");

	/**
	 * Controls: a mod-only smelt (no vanilla duplicate exists) and two vanilla-only ones. They must
	 * still be listed — exactly once — or the fix above has thrown out the mirrors it must keep.
	 */
	private static final List<String> SMELTS_WITHOUT_A_DUPLICATE = List.of(
			"alaindustrial:uranium_dust", "alaindustrial:palladium_dust",
			"minecraft:raw_iron", "minecraft:sand");

	/**
	 * @implements TC-RECIE-012 — every smelt appears exactly once in the electric furnace's own
	 *     recipe-viewer category: the twelve inputs that carry an Iron-Furnace parity duplicate are
	 *     shown by their mod recipe alone, and mod-only and vanilla-only smelts still show up.
	 */
	public static void tcRecie12_electricFurnaceCategoryListsEachSmeltOnce(GameTestHelper helper) {
		Collection<RecipeHolder<?>> recipes = helper.getLevel().getServer().getRecipeManager().getRecipes();
		List<AlaProcessingRecipe> category = electricFurnaceCategory(recipes);
		// Vanilla alone ships far more than this; the bound only guards "the filter ate the category".
		if (category.size() < 60) {
			helper.fail("electric furnace category collapsed to " + category.size()
					+ " cards — the mirror filter is dropping recipes it must keep");
			return;
		}
		for (String item : SMELTS_WITH_A_PARITY_DUPLICATE) {
			if (!assertShownExactlyOnce(helper, category, item)) {
				return;
			}
		}
		for (String item : SMELTS_WITHOUT_A_DUPLICATE) {
			if (!assertShownExactlyOnce(helper, category, item)) {
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * The cards JEI/REI put in the electric furnace's category: the mod's own smelting recipes plus
	 * the mirrored vanilla ones — assembled the same way both plugins assemble them.
	 */
	private static List<AlaProcessingRecipe> electricFurnaceCategory(Collection<RecipeHolder<?>> recipes) {
		List<AlaProcessingRecipe> category = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value() instanceof AlaProcessingRecipe recipe && recipe.kind() == ModRecipes.SMELTING) {
				category.add(recipe);
			}
		}
		for (RecipeHolder<AlaProcessingRecipe> holder : VanillaSmeltingMirror.mirrorAll(recipes)) {
			category.add(holder.value());
		}
		return category;
	}

	/**
	 * Counts the cards in the category that accept {@code itemId} — by asking each card's own
	 * ingredient, not by re-running the filter that built the list, so a broken filter cannot agree
	 * with itself here.
	 */
	private static boolean assertShownExactlyOnce(GameTestHelper helper, List<AlaProcessingRecipe> category,
			String itemId) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		if (item == null || item == Items.AIR) {
			helper.fail("probe item " + itemId + " does not exist — fix the test, not the filter");
			return false;
		}
		ItemStack probe = new ItemStack(item);
		int shown = 0;
		for (AlaProcessingRecipe recipe : category) {
			if (recipe.ingredient().test(probe)) {
				shown++;
			}
		}
		if (shown != 1) {
			helper.fail("electric furnace category shows " + itemId + " " + shown
					+ " time(s), expected exactly 1");
			return false;
		}
		return true;
	}

	private static ItemStack s(ItemLike item) {
		return new ItemStack(item);
	}

	/** The shared retrofit grid: top row = the machine's defining part; iron frame; casing body; circuit. */
	private static List<ItemStack> machineGrid(ItemStack topLeft, ItemStack topMid, ItemStack topRight) {
		ItemStack i = s(Items.IRON_INGOT);
		return List.of(
				topLeft, topMid, topRight,
				i, s(ModContent.MACHINE_CASING_ITEM.get()), i,
				i, s(ModContent.ELECTRONIC_CIRCUIT.get()), i);
	}

	private static void assertCraftResolves(GameTestHelper helper, int width, int height,
			List<ItemStack> grid, ItemLike expected, String name) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(width, height, grid);
		RecipeHolder<CraftingRecipe> recipe =
				level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (recipe == null) {
			helper.fail(name + " crafting recipe did not resolve — check pattern/key item ids in recipe/" + name + ".json");
			return;
		}
		ItemStack out = recipe.value().assemble(input);
		if (!out.is(expected.asItem())) {
			helper.fail(name + " recipe produced " + out + " (expected " + expected.asItem() + ")");
			return;
		}
		helper.succeed();
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
			// Every ingredient must be non-empty (would otherwise make its corresponding slot unusable).
			for (int input = 0; input < recipe.ingredients().size(); input++) {
				if (recipe.ingredients().get(input).isEmpty()) {
					helper.fail(label + " recipe " + id + " has an empty ingredient at index " + input
							+ " — check the 'ingredient'/'ingredients' field");
					return;
				}
			}
		}
		helper.succeed();
	}
}
