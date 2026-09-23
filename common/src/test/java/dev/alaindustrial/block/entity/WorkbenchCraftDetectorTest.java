package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** L1 coverage for {@link WorkbenchCraftDetector} — what counts as a craft on the workbench (MOD-656). */
class WorkbenchCraftDetectorTest {

	/** Taking a wooden door eats one plank from each of six cells. */
	@Test
	void consumedIngredientsAreACraft() {
		int[] before = {2, 2, 0, 2, 2, 0, 2, 2, 0};
		int[] after = {1, 1, 0, 1, 1, 0, 1, 1, 0};
		assertTrue(WorkbenchCraftDetector.craftedBetween(before, after));
	}

	/** One cell is enough: a recipe with a single ingredient still consumes it. */
	@Test
	void oneShrunkCellIsACraft() {
		int[] before = {0, 0, 0, 0, 5, 0, 0, 0, 0};
		int[] after = {0, 0, 0, 0, 4, 0, 0, 0, 0};
		assertTrue(WorkbenchCraftDetector.craftedBetween(before, after));
	}

	/** A cake: the milk buckets turn into empty buckets (same count) while the rest is consumed. */
	@Test
	void remainderItemsDoNotHideTheCraft() {
		int[] before = {1, 1, 1, 1, 1, 1, 1, 1, 1};
		int[] after = {1, 1, 1, 0, 1, 0, 0, 0, 0};
		assertTrue(WorkbenchCraftDetector.craftedBetween(before, after));
	}

	/** A click on an empty or refused result leaves the grid as it was — nothing to remember. */
	@Test
	void unchangedGridIsNotACraft() {
		int[] grid = {1, 0, 3, 0, 0, 0, 2, 0, 0};
		assertFalse(WorkbenchCraftDetector.craftedBetween(grid, grid.clone()));
	}

	/** Items only ever arriving in the grid is placement, not a craft. */
	@Test
	void growingGridIsNotACraft() {
		int[] before = {0, 0, 0, 0, 0, 0, 0, 0, 0};
		int[] after = {1, 0, 0, 0, 64, 0, 0, 0, 0};
		assertFalse(WorkbenchCraftDetector.craftedBetween(before, after));
	}

	@Test
	void mismatchedSnapshotsAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> WorkbenchCraftDetector.craftedBetween(new int[9], new int[4]));
	}
}
