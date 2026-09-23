package dev.alaindustrial.block.entity;

/**
 * Decides whether a click on the Industrial Workbench's result slot actually crafted something (MOD-656).
 *
 * <p>The bench remembers the last recipe a player crafted on it, so it has to tell a craft apart from
 * every other click on the result slot: an empty result, a click refused because the cursor already
 * holds something else, a result the player could not pick up. A craft is the one outcome that
 * consumes ingredients, so the test is on the grid: at least one cell holds fewer items after the
 * click than before it. Cells that grew (a milk bucket leaving an empty bucket behind has the same
 * count) or stayed put do not count on their own.
 *
 * <p>Kept free of Minecraft types so the rule is testable in the plain L1 lane.
 */
public final class WorkbenchCraftDetector {

	private WorkbenchCraftDetector() {
	}

	/**
	 * Whether the grid lost items between the two snapshots.
	 *
	 * @param before per-cell item counts right before the click
	 * @param after  per-cell item counts right after the click, same length
	 */
	public static boolean craftedBetween(int[] before, int[] after) {
		if (before.length != after.length) {
			throw new IllegalArgumentException("grid sizes differ: " + before.length + " vs " + after.length);
		}
		for (int i = 0; i < before.length; i++) {
			if (after[i] < before[i]) {
				return true;
			}
		}
		return false;
	}
}
