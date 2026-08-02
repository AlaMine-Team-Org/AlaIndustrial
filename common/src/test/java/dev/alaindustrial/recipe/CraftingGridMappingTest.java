package dev.alaindustrial.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for the trimmed-grid arithmetic behind the assembler's craft remainders (MOD-275).
 *
 * <p>Runnable at L1 precisely because {@link CraftingGridMapping} names no Minecraft type: the real
 * inputs come out of {@code CraftingInput.ofPositioned}, but the mapping itself is integer
 * arithmetic, and integer arithmetic is where the off-by-one lives.
 *
 * <p>The values below are the ones vanilla's own trimming produces for the layouts the assembler
 * actually meets, written out rather than recomputed — a test that re-derives the expected index with
 * the same formula the code uses would agree with any formula at all.
 *
 * @implements TC-ASM-006 — the arithmetic half of "a remainder returns to its own slot".
 */
class CraftingGridMappingTest {

	private static final int W = CraftingGridMapping.GRID_WIDTH;

	/** A full 3×3 pattern does not trim: the trimmed index and the grid index are the same number. */
	@Test
	void untrimmedGridIsIdentity() {
		for (int i = 0; i < 9; i++) {
			assertEquals(i, CraftingGridMapping.gridIndex(i, 3, 0, 0, W),
					"a 3x3 input at left=0, top=0 must map straight through");
		}
	}

	/**
	 * Two ingredients in the middle row, columns 1 and 2 (grid cells 4 and 5) — the layout the Forge
	 * Hammer test uses. Vanilla trims that to a 2×1 input at left=1, top=1, so the remainder list has
	 * two entries and neither of their indices is its grid cell.
	 */
	@Test
	void centreRowPairMapsBackToCellsFourAndFive() {
		assertEquals(4, CraftingGridMapping.gridIndex(0, 2, 1, 1, W));
		assertEquals(5, CraftingGridMapping.gridIndex(1, 2, 1, 1, W));
	}

	/** A single column of two (the stick layout): 1×2 at the origin, so cells 0 and 3. */
	@Test
	void singleColumnPairMapsBackToCellsZeroAndThree() {
		assertEquals(0, CraftingGridMapping.gridIndex(0, 1, 0, 0, W));
		assertEquals(3, CraftingGridMapping.gridIndex(1, 1, 0, 0, W));
	}

	/** The bottom-right 2×2 block: left=1, top=1 gives cells 4, 5, 7, 8. */
	@Test
	void bottomRightBlockMapsBackToItsFourCells() {
		assertEquals(4, CraftingGridMapping.gridIndex(0, 2, 1, 1, W));
		assertEquals(5, CraftingGridMapping.gridIndex(1, 2, 1, 1, W));
		assertEquals(7, CraftingGridMapping.gridIndex(2, 2, 1, 1, W));
		assertEquals(8, CraftingGridMapping.gridIndex(3, 2, 1, 1, W));
	}

	/**
	 * A degenerate input reports "no cell" rather than an index. An all-empty grid trims to width 0
	 * (vanilla's {@code CraftingInput.EMPTY}), and dividing by that would throw inside a block-entity
	 * tick — the one place an exception is least welcome.
	 */
	@Test
	void degenerateInputHasNoCell() {
		assertEquals(-1, CraftingGridMapping.gridIndex(0, 0, 0, 0, W), "zero-width input");
		assertEquals(-1, CraftingGridMapping.gridIndex(-1, 2, 0, 0, W), "negative index");
		assertEquals(-1, CraftingGridMapping.gridIndex(0, 2, 0, 0, 0), "zero-width grid");
	}

	/** An index that would run off the right edge of the grid is rejected, not wrapped onto the next row. */
	@Test
	void overflowingColumnIsRejected() {
		assertEquals(-1, CraftingGridMapping.gridIndex(1, 2, 2, 0, W),
				"left=2 plus column 1 is column 3, which is off a 3-wide grid");
	}

	/**
	 * The mirrored mapping is the direct one with the columns reversed inside the trimmed block.
	 *
	 * <p>A 3-wide pattern at the origin: pattern column 0 lands on grid column 2 and vice versa, while
	 * rows are untouched. Written against explicit expected cells rather than against
	 * {@code gridIndex(...)} with a flipped argument, so it stays a statement about the mapping and not
	 * a restatement of the implementation.
	 */
	@Test
	void mirroredMappingReversesColumnsWithinTheTrimmedBlock() {
		assertEquals(2, CraftingGridMapping.gridIndexMirrored(0, 3, 0, 0, W));
		assertEquals(1, CraftingGridMapping.gridIndexMirrored(1, 3, 0, 0, W));
		assertEquals(0, CraftingGridMapping.gridIndexMirrored(2, 3, 0, 0, W));
		assertEquals(5, CraftingGridMapping.gridIndexMirrored(3, 3, 0, 0, W));
		assertEquals(3, CraftingGridMapping.gridIndexMirrored(5, 3, 0, 0, W));
	}

	/** Mirroring happens inside the trimmed block, so the trim offset still shifts the result. */
	@Test
	void mirroredMappingRespectsTheTrimOffset() {
		// A 2-wide block starting at column 1: pattern column 0 -> grid column 2, column 1 -> column 1.
		assertEquals(2, CraftingGridMapping.gridIndexMirrored(0, 2, 1, 0, W));
		assertEquals(1, CraftingGridMapping.gridIndexMirrored(1, 2, 1, 0, W));
		// ...and top=1 moves the whole thing down a row.
		assertEquals(5, CraftingGridMapping.gridIndexMirrored(0, 2, 1, 1, W));
	}

	/** A single-column pattern is its own mirror image — the two mappings must agree. */
	@Test
	void singleColumnIsItsOwnMirror() {
		assertEquals(CraftingGridMapping.gridIndex(0, 1, 0, 0, W),
				CraftingGridMapping.gridIndexMirrored(0, 1, 0, 0, W));
		assertEquals(CraftingGridMapping.gridIndex(1, 1, 0, 0, W),
				CraftingGridMapping.gridIndexMirrored(1, 1, 0, 0, W));
	}

	/** The same degenerate guards as the direct mapping: no cell rather than a division by zero. */
	@Test
	void mirroredDegenerateInputHasNoCell() {
		assertEquals(-1, CraftingGridMapping.gridIndexMirrored(0, 0, 0, 0, W), "zero-width input");
		assertEquals(-1, CraftingGridMapping.gridIndexMirrored(-1, 2, 0, 0, W), "negative index");
		assertEquals(-1, CraftingGridMapping.gridIndexMirrored(0, 2, 0, 0, 0), "zero-width grid");
		// Mirroring moves which pattern index overflows: with left=2 it is index 0 that lands on
		// column 3, where the direct mapping overflows on index 1.
		assertEquals(-1, CraftingGridMapping.gridIndexMirrored(0, 2, 2, 0, W), "column off the grid");
		assertEquals(2, CraftingGridMapping.gridIndexMirrored(1, 2, 2, 0, W), "...and index 1 fits");
	}
}
