package dev.alaindustrial.recipe;

/**
 * Maps an index in a <em>trimmed</em> {@code CraftingInput} back to a cell of the full 3×3 grid
 * (MOD-275).
 *
 * <p><b>Why this exists.</b> {@code CraftingInput.of(3, 3, nineStacks)} does not keep the 3×3 shape:
 * its bytecode measures the first and last non-empty column and row and rebuilds the input at
 * {@code maxCol - minCol + 1} wide. So a recipe that only uses the middle column comes back as a
 * 1×3 input, {@code size()} is 3 rather than 9, and — the part that actually loses items —
 * {@code CraftingRecipe.getRemainingItems(input)} returns a list of <em>three</em> entries whose
 * indices mean nothing in grid coordinates. Vanilla dodges the problem by throwing remainders on the
 * floor; an assembler has to put them back where they came from, so the offset has to be undone.
 *
 * <p>{@code CraftingInput.ofPositioned} hands back exactly the two numbers needed for that —
 * {@code left()} and {@code top()}, the trimmed grid's origin inside the full one.
 *
 * <p>Deliberately free of any Minecraft type so the arithmetic can be unit-tested at L1: a class
 * with an MC type in its signature does not link outside a game environment.
 */
public final class CraftingGridMapping {
	private CraftingGridMapping() {
	}

	/** Cells across the assembler's pattern grid — the same 3 the blueprint records. */
	public static final int GRID_WIDTH = 3;

	/**
	 * The full-grid index of trimmed cell {@code trimmedIndex}, or {@code -1} when the trimmed input is
	 * degenerate (an all-empty grid trims to width 0, and vanilla's {@code CraftingInput.EMPTY} is
	 * exactly that).
	 *
	 * @param trimmedIndex index into the trimmed input, row-major
	 * @param trimmedWidth {@code input.width()} of the trimmed input
	 * @param left         {@code Positioned.left()} — the trimmed grid's first column in the full grid
	 * @param top          {@code Positioned.top()} — the trimmed grid's first row in the full grid
	 * @param gridWidth    width of the full grid ({@value #GRID_WIDTH} for the assembler)
	 */
	public static int gridIndex(int trimmedIndex, int trimmedWidth, int left, int top, int gridWidth) {
		if (trimmedWidth <= 0 || trimmedIndex < 0 || gridWidth <= 0) {
			return -1;
		}
		int row = top + trimmedIndex / trimmedWidth;
		int col = left + trimmedIndex % trimmedWidth;
		if (col >= gridWidth) {
			return -1;
		}
		return row * gridWidth + col;
	}

	/**
	 * The same mapping for a pattern that matched <em>mirrored</em>: column {@code c} of the pattern
	 * lands on column {@code trimmedWidth - 1 - c} of the grid.
	 *
	 * <p>Needed because {@code ShapedRecipePattern.matches(CraftingInput)} tries the mirrored
	 * orientation first for any pattern that is not left/right symmetrical (verified in the 26.2
	 * bytecode: it calls the private {@code matches(input, true)} before {@code matches(input, false)}).
	 * A shaped recipe can therefore be satisfied by a layout that is the mirror image of its pattern,
	 * and a caller mapping pattern positions onto grid cells has to know which of the two happened —
	 * otherwise it attributes the wrong ingredient to an asymmetric cell.
	 *
	 * @see #gridIndex(int, int, int, int, int)
	 */
	public static int gridIndexMirrored(int trimmedIndex, int trimmedWidth, int left, int top, int gridWidth) {
		if (trimmedWidth <= 0 || trimmedIndex < 0 || gridWidth <= 0) {
			return -1;
		}
		int row = top + trimmedIndex / trimmedWidth;
		int col = left + (trimmedWidth - 1 - trimmedIndex % trimmedWidth);
		if (col >= gridWidth) {
			return -1;
		}
		return row * gridWidth + col;
	}
}
