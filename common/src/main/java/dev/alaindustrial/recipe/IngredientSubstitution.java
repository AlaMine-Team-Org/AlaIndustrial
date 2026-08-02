package dev.alaindustrial.recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * Which items may stand in for a recorded ingredient, cell by cell of the assembler's 3×3 grid
 * (MOD-275).
 *
 * <p><b>The candidates come from the recipe's own {@link Ingredient} for that cell — never "from the
 * tag in general".</b> A blueprint recording oak planks in a recipe written against
 * {@code #minecraft:planks} may substitute any of the twelve plank species; a blueprint recording oak
 * planks in {@code oak_stairs}, whose ingredient is literally {@code minecraft:oak_planks}, may
 * substitute nothing at all. That difference is the whole point, and it is why this reads the recipe
 * rather than the item's tags.
 *
 * <h2>How the positional mapping is obtained (26.2, verified with {@code javap})</h2>
 *
 * An earlier iteration of this machine recorded in its spec that 26.2's {@code Recipe} "has no way to
 * get ingredients positionally". That is wrong, and the surface that disproves it is on the
 * {@code Recipe} interface itself:
 *
 * <ul>
 *   <li>{@code Recipe.placementInfo()} is {@code public abstract} on the interface and returns a
 *       {@link PlacementInfo};</li>
 *   <li>{@link PlacementInfo#ingredients()} returns {@code List<Ingredient>} and
 *       {@link PlacementInfo#slotsToIngredientIndex()} returns an {@code IntList} <b>parallel to the
 *       recipe's placement slots</b>: entry {@code p} is the index into {@code ingredients()} that
 *       governs placement slot {@code p}, or {@code -1} ({@link PlacementInfo#EMPTY_SLOT}) for a slot
 *       the recipe leaves empty. The bytecode of {@code PlacementInfo.createFromOptionals} builds
 *       exactly that list from a {@code List<Optional<Ingredient>>}, keeping position by writing
 *       {@code -1} for the absent ones;</li>
 *   <li>{@code ShapedRecipe.createPlacementInfo()} feeds it
 *       {@code ShapedRecipePattern.ingredients()} — so for a shaped recipe the placement slots
 *       <b>are</b> the pattern cells, row-major, {@code getWidth() × getHeight()};</li>
 *   <li>vanilla consumes it the same way: {@code ServerPlaceRecipe} hands
 *       {@code slotsToIngredientIndex()} to {@code PlaceRecipeHelper.placeRecipe}, which walks it as
 *       recipe-width × recipe-height for a {@link ShapedRecipe} and as grid-width × grid-height for
 *       anything else, and drops each entry into a crafting-grid slot.</li>
 * </ul>
 *
 * <p>Two positional wrinkles have to be undone before those pattern positions mean anything in
 * blueprint coordinates, and both are handled by {@link CraftingGridMapping}:
 *
 * <ol>
 *   <li>{@code CraftingInput.of} trims empty edge rows and columns, so pattern cell {@code (r, c)}
 *       sits at grid cell {@code (top + r, left + c)};</li>
 *   <li>{@code ShapedRecipePattern.matches} accepts a <b>mirrored</b> layout for any pattern that is
 *       not left/right symmetrical, so the recorded grid may be the mirror image of the pattern.
 *       Which of the two happened is decided here by trying both against the recorded stacks and
 *       keeping the orientation whose ingredients actually accept them — the recorded layout is
 *       ground truth, since the machine solved the recipe from it.</li>
 * </ol>
 *
 * <p><b>Anything that is not a {@link ShapedRecipe} has no positions</b> — a shapeless recipe's
 * ingredient order is arbitrary by definition, and a third-party recipe class may place its slots
 * however it likes. For those, every ingredient of the recipe is offered as a candidate for every
 * cell, which is exactly as permissive as the recipe itself is, and the re-assembly gate in
 * {@code AssemblerBlockEntity} is what keeps it honest. The same split — {@code instanceof
 * ShapedRecipe} or else treat the placement list as flat — is the one vanilla makes in
 * {@code PlaceRecipeHelper}.
 *
 * <p><b>Components are ignored</b>, because a plain {@link Ingredient} ignores them: with substitution
 * on, an enchanted or worn instance satisfies a cell that recorded a pristine one. That is why the
 * feature is off by default and toggled per blueprint.
 */
public final class IngredientSubstitution {
	private IngredientSubstitution() {
	}

	/** Cells of the assembler's authoring grid. */
	public static final int GRID_SIZE = 9;

	/**
	 * For each of the nine grid cells, the items that may stand in for what the blueprint recorded
	 * there — empty where the recipe permits no alternative, or where the cell holds nothing.
	 *
	 * <p>The recorded item is included when the recipe accepts it; the caller is expected to have tried
	 * it first regardless, because "what the player put in the blueprint wins when it is available" is a
	 * promise this class does not make on its own.
	 *
	 * @param recipe       the recipe the recorded layout resolves to
	 * @param positioned   {@code CraftingInput.ofPositioned(3, 3, recordedGrid)} — the trimmed input and
	 *                     its origin inside the full grid
	 * @param recordedGrid the nine recorded stacks, row-major, empties included
	 */
	public static List<List<Item>> candidatesByCell(CraftingRecipe recipe, CraftingInput.Positioned positioned,
			List<ItemStack> recordedGrid) {
		List<Set<Item>> byCell = new ArrayList<>(GRID_SIZE);
		for (int i = 0; i < GRID_SIZE; i++) {
			byCell.add(new LinkedHashSet<>());
		}
		PlacementInfo info = recipe.placementInfo();
		if (info.isImpossibleToPlace()) {
			return freeze(byCell);
		}
		List<Ingredient> ingredients = info.ingredients();
		if (recipe instanceof ShapedRecipe shaped) {
			fillShaped(byCell, shaped, info, ingredients, positioned, recordedGrid);
		} else {
			fillFlat(byCell, ingredients, recordedGrid);
		}
		return freeze(byCell);
	}

	/**
	 * Shaped: placement slot {@code p} is pattern cell {@code (p / width, p % width)}, mapped onto the
	 * grid through the trim offsets — in whichever of the two orientations the recorded layout actually
	 * matched.
	 */
	private static void fillShaped(List<Set<Item>> byCell, ShapedRecipe shaped, PlacementInfo info,
			List<Ingredient> ingredients, CraftingInput.Positioned positioned, List<ItemStack> recordedGrid) {
		int width = shaped.getWidth();
		var slots = info.slotsToIngredientIndex();
		if (width <= 0 || slots.size() != width * shaped.getHeight()) {
			// The placement list is not the pattern grid after all; rather than guess at a mapping, fall
			// back to the permissive-but-validated flat treatment.
			fillFlat(byCell, ingredients, recordedGrid);
			return;
		}
		int left = positioned.left();
		int top = positioned.top();
		boolean direct = orientationFits(shaped, info, ingredients, positioned, recordedGrid, false);
		boolean mirrored = !direct && orientationFits(shaped, info, ingredients, positioned, recordedGrid, true);
		if (!direct && !mirrored) {
			// Neither orientation explains the recorded layout. Offering candidates now would be guessing.
			return;
		}
		for (int p = 0; p < slots.size(); p++) {
			int index = slots.getInt(p);
			if (index == PlacementInfo.EMPTY_SLOT || index < 0 || index >= ingredients.size()) {
				continue;
			}
			int cell = mirrored
					? CraftingGridMapping.gridIndexMirrored(p, width, left, top, CraftingGridMapping.GRID_WIDTH)
					: CraftingGridMapping.gridIndex(p, width, left, top, CraftingGridMapping.GRID_WIDTH);
			if (cell < 0 || cell >= GRID_SIZE || recordedGrid.get(cell).isEmpty()) {
				continue;
			}
			collect(byCell.get(cell), ingredients.get(index));
		}
	}

	/** Whether every recorded stack is accepted by the ingredient this orientation puts on its cell. */
	private static boolean orientationFits(ShapedRecipe shaped, PlacementInfo info, List<Ingredient> ingredients,
			CraftingInput.Positioned positioned, List<ItemStack> recordedGrid, boolean mirrored) {
		int width = shaped.getWidth();
		var slots = info.slotsToIngredientIndex();
		int left = positioned.left();
		int top = positioned.top();
		for (int p = 0; p < slots.size(); p++) {
			int cell = mirrored
					? CraftingGridMapping.gridIndexMirrored(p, width, left, top, CraftingGridMapping.GRID_WIDTH)
					: CraftingGridMapping.gridIndex(p, width, left, top, CraftingGridMapping.GRID_WIDTH);
			if (cell < 0 || cell >= GRID_SIZE) {
				return false;
			}
			int index = slots.getInt(p);
			ItemStack recorded = recordedGrid.get(cell);
			if (index == PlacementInfo.EMPTY_SLOT || index < 0 || index >= ingredients.size()) {
				if (!recorded.isEmpty()) {
					return false; // the pattern leaves this cell empty and the player did not
				}
				continue;
			}
			if (recorded.isEmpty() || !ingredients.get(index).test(recorded)) {
				return false;
			}
		}
		return true;
	}

	/** No usable positions: every ingredient of the recipe is a candidate for every filled cell. */
	private static void fillFlat(List<Set<Item>> byCell, List<Ingredient> ingredients,
			List<ItemStack> recordedGrid) {
		Set<Item> all = new LinkedHashSet<>();
		for (Ingredient ingredient : ingredients) {
			collect(all, ingredient);
		}
		if (all.isEmpty()) {
			return;
		}
		for (int cell = 0; cell < GRID_SIZE; cell++) {
			if (!recordedGrid.get(cell).isEmpty()) {
				byCell.get(cell).addAll(all);
			}
		}
	}

	private static void collect(Set<Item> into, Ingredient ingredient) {
		ingredient.items().map(Holder::value).forEach(into::add);
	}

	private static List<List<Item>> freeze(List<Set<Item>> byCell) {
		List<List<Item>> out = new ArrayList<>(byCell.size());
		for (Set<Item> cell : byCell) {
			out.add(List.copyOf(cell));
		}
		return List.copyOf(out);
	}
}
