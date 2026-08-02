package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.CreativeTabContent;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;

/**
 * Loader-neutral MOD-299 acceptance scenarios, invoked by both loader GameTest lanes.
 *
 * <p>Covers the two recipes introduced/reworked by MOD-299 and the creative-tab visibility of the
 * new item. {@code recipe_check.py} only validates the mod's PROCESSING families, and
 * {@code recipe_advancement_check.py} only pairs a recipe with its unlock advancement — neither
 * builds a grid, so a crafting recipe that loads happily but can never be assembled (wrong key, a
 * tag with no members, a pattern that no longer matches the keys) slips past both. These scenarios
 * assemble the exact grid a player would.
 */
public final class AdvancedCircuitScenarios {
	private AdvancedCircuitScenarios() {
	}

	/**
	 * The MOD-299 pair: {@code RGR/GCG/RGR} → one advanced circuit, and the reworked casing
	 * {@code PPP/ACA/PPP} → one advanced machine casing. Both grids are asserted to resolve to
	 * exactly one of the expected item.
	 *
	 * <p>The old all-plate casing grid ({@code PPP/PCP/PPP}) is asserted NOT to resolve: the point
	 * of the task is that the MV step got more expensive, and a leftover recipe that still accepts
	 * eight plates would make the whole change cosmetic while every other assertion stayed green.
	 */
	public static void recipesAndVisibility(GameTestHelper helper) {
		ItemStack rubber = new ItemStack(ModContent.RUBBER.get());
		ItemStack gold = new ItemStack(ModContent.GOLD_PLATE.get());
		ItemStack circuit = new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get());

		assertCraft(helper, List.of(
				rubber, gold, rubber,
				gold, circuit, gold,
				rubber, gold, rubber),
				ModContent.ADVANCED_CIRCUIT.get(), 1, "advanced_circuit");

		ItemStack plate = new ItemStack(ModContent.TEMPERED_IRON_PLATE.get());
		ItemStack advanced = new ItemStack(ModContent.ADVANCED_CIRCUIT.get());
		ItemStack casing = new ItemStack(ModContent.MACHINE_CASING_ITEM.get());

		assertCraft(helper, List.of(
				plate, plate, plate,
				advanced, casing, advanced,
				plate, plate, plate),
				ModContent.ADVANCED_MACHINE_CASING_ITEM.get(), 1, "advanced_machine_casing");

		assertNoCraft(helper, List.of(
				plate, plate, plate,
				plate, casing, plate,
				plate, plate, plate),
				"advanced_machine_casing (pre-MOD-299 all-plate grid)");

		// The item has to be in the MOD's own tab, not only in the vanilla ingredients tab — the
		// exact miss MOD-275 shipped with the assembly blueprint. CreativeTabContent.main is what
		// feeds the mod tab; ingredients() feeds the vanilla one and is checked separately below.
		List<Item> modTab = new ArrayList<>();
		CreativeTabContent.main(item -> modTab.add(item.asItem()));
		assertVisibleExactlyOnce(helper, modTab, ModContent.ADVANCED_CIRCUIT.get(),
				"advanced_circuit (mod tab)");

		List<Item> vanillaIngredients = new ArrayList<>();
		CreativeTabContent.ingredients(item -> vanillaIngredients.add(item.asItem()));
		assertVisibleExactlyOnce(helper, vanillaIngredients, ModContent.ADVANCED_CIRCUIT.get(),
				"advanced_circuit (vanilla ingredients tab)");

		helper.succeed();
	}

	private static void assertCraft(GameTestHelper helper, List<ItemStack> grid, ItemLike expected,
			int expectedCount, String label) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, grid);
		RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (recipe == null) {
			helper.fail(label + " recipe did not resolve");
			return;
		}
		ItemStack output = recipe.value().assemble(input);
		if (!output.is(expected.asItem()) || output.getCount() != expectedCount) {
			helper.fail(label + " recipe produced " + output + "; expected exactly " + expectedCount
					+ " " + expected.asItem());
		}
	}

	private static void assertNoCraft(GameTestHelper helper, List<ItemStack> grid, String label) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, grid);
		level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
				.ifPresent(recipe -> helper.fail(label + " unexpectedly resolved to "
						+ recipe.value().assemble(input)));
	}

	private static void assertVisibleExactlyOnce(GameTestHelper helper, List<Item> visible,
			ItemLike expected, String name) {
		long count = visible.stream().filter(item -> item == expected.asItem()).count();
		if (count != 1L) {
			helper.fail(name + " occurs " + count + " times; expected exactly once");
		}
	}
}
