package dev.alaindustrial.recipe;

/**
 * Order-independent assignment of alloy components to machine input slots.
 *
 * <p>Every other machine in the mod matches its inputs <em>positionally</em>: slot 0 holds one fixed
 * ingredient, slot 1 another (see {@link AlaProcessingRecipe#matches}). The alloy smelter is the first
 * machine where the player may drop any component into any input slot, so "does this set of slots
 * satisfy this recipe" becomes a bipartite matching rather than an index-by-index comparison.
 *
 * <p>Deliberately Minecraft-free: an {@code Ingredient} test needs the game, but deciding <em>which
 * component goes in which slot</em> does not. Keeping the decision here lets it run in the L1 suite
 * ({@code AlloyMatchTest}) and be mutated by PIT, neither of which can load Minecraft classes.
 *
 * <p>The search is exhaustive backtracking. That is not a compromise: the smelter has three input
 * slots and at most three components, so the worst case is 3! = 6 permutations — cheaper than the
 * bookkeeping a smarter algorithm would need, and it always finds an assignment when one exists.
 * A greedy first-fit does not: with components [copper-or-tin, tin] and slots [tin, copper], greedy
 * hands the tin slot to the first component and then fails, while the recipe plainly fits.
 */
public final class AlloyMatch {
	private AlloyMatch() {
	}

	/** Marks a component that no slot satisfies, in the array {@link #assign} hands back. */
	public static final int UNASSIGNED = -1;

	/**
	 * Assign each component a distinct slot that satisfies it.
	 *
	 * @param accepts {@code accepts[component][slot]} — whether the stack in {@code slot} satisfies
	 *     {@code component}. The caller decides what "satisfies" means: pass ingredient-only tests to
	 *     ask "is this the right recipe", or ingredient-and-count tests to ask "can it run right now".
	 * @return one slot index per component, or {@code null} when no complete assignment exists. Never a
	 *     partial answer: a component list either fits entirely or does not fit at all.
	 */
	public static int[] assign(boolean[][] accepts, int slotCount) {
		if (accepts.length == 0 || slotCount <= 0 || accepts.length > slotCount) {
			return null;
		}
		int[] chosen = new int[accepts.length];
		java.util.Arrays.fill(chosen, UNASSIGNED);
		boolean[] taken = new boolean[slotCount];
		return search(accepts, slotCount, 0, chosen, taken) ? chosen : null;
	}

	private static boolean search(boolean[][] accepts, int slotCount, int component, int[] chosen,
			boolean[] taken) {
		if (component == accepts.length) {
			return true;
		}
		for (int slot = 0; slot < slotCount; slot++) {
			if (taken[slot] || !accepts[component][slot]) {
				continue;
			}
			taken[slot] = true;
			chosen[component] = slot;
			if (search(accepts, slotCount, component + 1, chosen, taken)) {
				return true;
			}
			taken[slot] = false;
			chosen[component] = UNASSIGNED;
		}
		return false;
	}

	/**
	 * Whether a recipe of {@code componentCount} components may run against {@code filledSlots} occupied
	 * inputs.
	 *
	 * <p>Equality, not "at least": a two-component recipe must NOT start while a third slot holds
	 * something. Without this a stray stack in the spare slot would be quietly ignored — the machine
	 * would consume the two it recognises and leave the player's third item sitting there forever, with
	 * no indication that it was never part of the recipe.
	 */
	public static boolean fillMatchesComponents(int filledSlots, int componentCount) {
		return filledSlots == componentCount;
	}
}
