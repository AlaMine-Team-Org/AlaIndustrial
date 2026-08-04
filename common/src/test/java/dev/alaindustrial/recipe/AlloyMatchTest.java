package dev.alaindustrial.recipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 suite for the alloy smelter's order-independent matcher (MOD-064).
 *
 * <p>Every case here is a bug the machine would otherwise ship: a recipe that only works when the
 * player happens to load the slots in declaration order, a two-component recipe that silently runs
 * with junk in the spare slot, or a greedy assignment that rejects a mix that plainly fits.
 */
class AlloyMatchTest {

	/** {@code accepts[component][slot]} built from a compact "which slots suit this component" table. */
	private static boolean[][] table(boolean[]... rows) {
		return rows;
	}

	private static boolean[] slots(boolean... values) {
		return values;
	}

	@Test
	void assignsComponentsToTheSlotsThatAcceptThem() {
		// component 0 fits slot 1 only, component 1 fits slot 0 only.
		int[] assignment = AlloyMatch.assign(table(
				slots(false, true, false),
				slots(true, false, false)), 3);
		assertArrayEquals(new int[] {1, 0}, assignment);
	}

	@Test
	void orderOfTheSlotsDoesNotChangeTheAnswer() {
		// The same two components against the mirrored loading order still resolve — this is the whole
		// point of the machine: the player may drop copper and tin into any slots.
		boolean[][] forward = table(slots(true, false, false), slots(false, true, false));
		boolean[][] reversed = table(slots(false, true, false), slots(true, false, false));
		assertNotNull(AlloyMatch.assign(forward, 3));
		assertNotNull(AlloyMatch.assign(reversed, 3));
	}

	@Test
	void neverAssignsTwoComponentsToTheSameSlot() {
		// Both components accept slot 0 and nothing else — one stack cannot pay for two components.
		assertNull(AlloyMatch.assign(table(
				slots(true, false, false),
				slots(true, false, false)), 3));
	}

	@Test
	void backtracksInsteadOfCommittingToAGreedyFirstFit() {
		// Component 0 is permissive (fits slots 0 and 1); component 1 fits slot 0 only. A greedy pass
		// hands slot 0 to component 0 and then fails, though the mix plainly fits: 0→1, 1→0.
		int[] assignment = AlloyMatch.assign(table(
				slots(true, true, false),
				slots(true, false, false)), 3);
		assertArrayEquals(new int[] {1, 0}, assignment);
	}

	@Test
	void aComponentNoSlotAcceptsMakesTheWholeRecipeFail() {
		assertNull(AlloyMatch.assign(table(
				slots(true, false, false),
				slots(false, false, false)), 3));
	}

	@Test
	void moreComponentsThanSlotsCannotFit() {
		assertNull(AlloyMatch.assign(table(
				slots(true, true),
				slots(true, true),
				slots(true, true)), 2));
	}

	@Test
	void anEmptyComponentListIsNotAMatch() {
		// A recipe with no components would otherwise "fit" every input, including an empty machine.
		assertNull(AlloyMatch.assign(new boolean[0][], 3));
	}

	@Test
	void spareOccupiedSlotBlocksAShorterRecipe() {
		// The strictness rule (MOD-064): two components must not run against three filled slots, or the
		// player's third stack is quietly ignored forever.
		assertTrue(AlloyMatch.fillMatchesComponents(2, 2));
		assertFalse(AlloyMatch.fillMatchesComponents(3, 2));
		// …and a component short is not a match either, or the machine would run underpaid.
		assertFalse(AlloyMatch.fillMatchesComponents(1, 2));
	}
}
