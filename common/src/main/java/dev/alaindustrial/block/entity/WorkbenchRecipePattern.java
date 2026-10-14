package dev.alaindustrial.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * What the Advanced Industrial Workbench's remembered recipe expects in each grid cell (MOD-810), so a
 * pipe or hopper can be told "this item belongs there, that one does not".
 *
 * <p>Built from the recipe's own {@link Recipe#placementInfo()} — the same ingredient list the recipe book
 * uses to lay a recipe out — so a datapack recipe, a modded one and a vanilla one are all read the same way.
 * A shaped recipe keeps its shape and is placed where the items already in the grid say it is (top-left
 * when the grid is empty, as the recipe book places it); a shapeless one is a bag of ingredients that any
 * cell may hold. A recipe that cannot be laid out at all ({@link PlacementInfo#isImpossibleToPlace()}, a
 * special recipe with no fixed ingredients) yields nothing, and the bench then accepts nothing.
 */
public final class WorkbenchRecipePattern {

	private static final int SIZE = 3;

	private final int width;
	private final int height;
	/** Shaped: one entry per pattern cell (null = must stay empty). Shapeless: the ingredient list. */
	private final List<Ingredient> cells;
	private final boolean shaped;

	private WorkbenchRecipePattern(int width, int height, List<Ingredient> cells, boolean shaped) {
		this.width = width;
		this.height = height;
		this.cells = cells;
		this.shaped = shaped;
	}

	/** The pattern of {@code recipe}, or empty when it has no fixed ingredients. */
	public static Optional<WorkbenchRecipePattern> of(Recipe<?> recipe) {
		PlacementInfo info = recipe.placementInfo();
		if (info.isImpossibleToPlace() || info.ingredients().isEmpty()) {
			return Optional.empty();
		}
		if (recipe instanceof ShapedRecipe shapedRecipe) {
			int w = shapedRecipe.getWidth();
			int h = shapedRecipe.getHeight();
			List<Ingredient> cells = new ArrayList<>(w * h);
			for (int i = 0; i < w * h; i++) {
				int index = i < info.slotsToIngredientIndex().size() ? info.slotsToIngredientIndex().getInt(i)
						: PlacementInfo.EMPTY_SLOT;
				cells.add(index == PlacementInfo.EMPTY_SLOT ? null : info.ingredients().get(index));
			}
			return Optional.of(new WorkbenchRecipePattern(w, h, cells, true));
		}
		return Optional.of(new WorkbenchRecipePattern(0, 0, List.copyOf(info.ingredients()), false));
	}

	/**
	 * Whether {@code stack} may be inserted into grid cell {@code slot} (0..8) given what the grid holds now,
	 * and among the cells that may take it, whether {@code slot} is one of the emptiest — the "spread evenly" rule.
	 *
	 * @param grid the nine grid stacks
	 */
	public boolean accepts(List<ItemStack> grid, int slot, ItemStack stack) {
		if (stack.isEmpty() || slot < 0 || slot >= SIZE * SIZE) {
			return false;
		}
		boolean[] eligible = eligibleCells(grid, stack);
		if (!eligible[slot]) {
			return false;
		}
		int min = Integer.MAX_VALUE;
		for (int i = 0; i < eligible.length; i++) {
			if (eligible[i]) {
				min = Math.min(min, grid.get(i).getCount());
			}
		}
		return grid.get(slot).getCount() == min;
	}

	private boolean[] eligibleCells(List<ItemStack> grid, ItemStack stack) {
		boolean[] eligible = new boolean[SIZE * SIZE];
		for (int i = 0; i < eligible.length; i++) {
			ItemStack held = grid.get(i);
			if (!held.isEmpty()
					&& (!ItemStack.isSameItemSameComponents(held, stack)
							|| held.getCount() >= held.getMaxStackSize())) {
				continue;
			}
			eligible[i] = true;
		}
		if (shaped) {
			applyShape(grid, stack, eligible);
		} else {
			applyBag(grid, stack, eligible);
		}
		return eligible;
	}

	/** Keeps only the cells the pattern, placed where the grid's contents allow, wants {@code stack} in. */
	private void applyShape(List<ItemStack> grid, ItemStack stack, boolean[] eligible) {
		for (int oy = 0; oy <= SIZE - height; oy++) {
			for (int ox = 0; ox <= SIZE - width; ox++) {
				if (!fits(grid, ox, oy)) {
					continue;
				}
				for (int i = 0; i < eligible.length; i++) {
					int cx = i % SIZE - ox;
					int cy = i / SIZE - oy;
					boolean inside = cx >= 0 && cx < width && cy >= 0 && cy < height;
					Ingredient wanted = inside ? cells.get(cx + cy * width) : null;
					eligible[i] &= wanted != null && wanted.test(stack);
				}
				return;
			}
		}
		java.util.Arrays.fill(eligible, false);
	}

	/** Whether every item already in the grid sits where this pattern, shifted by (ox, oy), wants it. */
	private boolean fits(List<ItemStack> grid, int ox, int oy) {
		for (int i = 0; i < SIZE * SIZE; i++) {
			ItemStack held = grid.get(i);
			if (held.isEmpty()) {
				continue;
			}
			int cx = i % SIZE - ox;
			int cy = i / SIZE - oy;
			if (cx < 0 || cx >= width || cy < 0 || cy >= height) {
				return false;
			}
			Ingredient wanted = cells.get(cx + cy * width);
			if (wanted == null || !wanted.test(held)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Shapeless: a cell already holding this item may always take more; an empty cell only while the
	 * ingredients can still be matched one-to-one with the occupied cells plus this new one.
	 */
	private void applyBag(List<ItemStack> grid, ItemStack stack, boolean[] eligible) {
		List<ItemStack> occupied = new ArrayList<>();
		for (ItemStack held : grid) {
			if (!held.isEmpty()) {
				occupied.add(held);
			}
		}
		occupied.add(stack);
		boolean roomForNew = canMatchAll(occupied);
		boolean wanted = false;
		for (Ingredient ingredient : cells) {
			wanted |= ingredient.test(stack);
		}
		for (int i = 0; i < eligible.length; i++) {
			if (!wanted || (grid.get(i).isEmpty() && !roomForNew)) {
				eligible[i] = false;
			}
		}
	}

	/** Bipartite match of {@code items} onto the ingredient list (each ingredient used once). */
	private boolean canMatchAll(List<ItemStack> items) {
		if (items.size() > cells.size()) {
			return false;
		}
		int[] owner = new int[cells.size()];
		java.util.Arrays.fill(owner, -1);
		for (int i = 0; i < items.size(); i++) {
			if (!augment(items, i, new boolean[cells.size()], owner)) {
				return false;
			}
		}
		return true;
	}

	private boolean augment(List<ItemStack> items, int item, boolean[] seen, int[] owner) {
		for (int c = 0; c < cells.size(); c++) {
			if (seen[c] || !cells.get(c).test(items.get(item))) {
				continue;
			}
			seen[c] = true;
			if (owner[c] < 0 || augment(items, owner[c], seen, owner)) {
				owner[c] = item;
				return true;
			}
		}
		return false;
	}
}
