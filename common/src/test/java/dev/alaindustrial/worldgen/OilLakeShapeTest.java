package dev.alaindustrial.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 unit tests for the oil-deposit blob geometry (MOD-248).
 *
 * <p>{@link OilLakeFeature} does no bounds checking when it looks for cells bordering the hollow —
 * it relies on {@link OilLakeShape} never filling the outermost layer of the grid. If that
 * invariant breaks the feature reads past the array on the very first lake, and in a world it looks
 * like an unsealed deposit rather than a crash, which is far harder to trace. Hence the emphasis
 * here on the edges and on the index mapping rather than on the aesthetics of the shape.
 *
 * @implements TC-OIL-002-UNIT02 — oil deposit blob grid
 */
class OilLakeShapeTest {

	/** A deterministic stand-in for the world's RandomSource. */
	private static DoubleSupplier seeded(long seed) {
		Random random = new Random(seed);
		return random::nextDouble;
	}

	/** Every grid cell maps to its own slot — an overlap silently merges two parts of the lake. */
	@ParameterizedTest(name = "index mapping is injective for radius {0}x{1}")
	@CsvSource({ "3, 2", "9, 5", "14, 9" })
	void indicesAreUniqueAndInRange(int horizontalRadius, int verticalRadius) {
		int width = OilLakeShape.width(horizontalRadius);
		int height = OilLakeShape.height(verticalRadius);
		Set<Integer> seen = new HashSet<>();
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				for (int y = 0; y < height; y++) {
					int index = OilLakeShape.index(horizontalRadius, verticalRadius, x, y, z);
					assertTrue(index >= 0 && index < width * width * height,
							"index " + index + " outside the grid for (" + x + ", " + y + ", " + z + ")");
					assertTrue(seen.add(index),
							"index " + index + " is claimed twice — the flat mapping collides");
				}
			}
		}
		assertEquals(width * width * height, seen.size(), "the mapping does not cover the whole grid");
	}

	/**
	 * The load-bearing invariant: the outer shell of the grid is never hollow, so a filled cell always
	 * has all six neighbours inside the array and the feature's barrier scan needs no bounds check.
	 */
	@ParameterizedTest(name = "seed {0} leaves the grid shell empty")
	@CsvSource({ "1", "42", "1234567", "-99" })
	void theOuterShellIsNeverFilled(long seed) {
		int horizontalRadius = 14;
		int verticalRadius = 9;
		int width = OilLakeShape.width(horizontalRadius);
		int height = OilLakeShape.height(verticalRadius);
		boolean[] filled = OilLakeShape.build(horizontalRadius, verticalRadius, 10, seeded(seed));
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				for (int y = 0; y < height; y++) {
					boolean onShell = x == 0 || z == 0 || y == 0
							|| x == width - 1 || z == width - 1 || y == height - 1;
					if (onShell) {
						assertFalse(filled[OilLakeShape.index(horizontalRadius, verticalRadius, x, y, z)],
								"shell cell (" + x + ", " + y + ", " + z + ") must stay solid");
					}
				}
			}
		}
	}

	/** The smallest allowed deposit still produces a hollow — a lake of nothing is not a lake. */
	@Test
	void theSmallestAllowedDepositIsNotEmpty() {
		boolean[] filled = OilLakeShape.build(OilLakeConfiguration.MIN_HORIZONTAL_RADIUS, 2, 4, seeded(7));
		boolean any = false;
		for (boolean cell : filled) {
			any |= cell;
		}
		assertTrue(any, "radius 3 x 2 with four blobs produced no hollow cells at all");
	}

	/** Same seed, same grid: worldgen must be reproducible or a seed stops meaning anything. */
	@Test
	void theSameSeedProducesTheSameGrid() {
		boolean[] first = OilLakeShape.build(9, 5, 6, seeded(2024));
		boolean[] second = OilLakeShape.build(9, 5, 6, seeded(2024));
		assertEquals(first.length, second.length);
		for (int i = 0; i < first.length; i++) {
			assertEquals(first[i], second[i], "grids diverge at index " + i + " for the same seed");
		}
	}

	/**
	 * The blob really is the ellipsoid its parameters describe — the assertions the invariant tests
	 * above cannot make.
	 *
	 * <p>With every draw fixed at {@code 0.5} and a 10 × 6 box the arithmetic resolves to a single
	 * ellipsoid centred on {@code (10, 6, 10)} with radii {@code 6.25 / 3.55 / 6.25}: the grid is
	 * 20 × 12 × 20, so {@code radiusX = 3.5 + 0.5 × (9 − 3.5)} and
	 * {@code centreX = 7.25 + 0.5 × (12.75 − 7.25)}. The cells below are picked well clear of the
	 * surface — the nearest is 20 % inside it — so nothing here depends on floating-point luck, and
	 * every one of them moves if a radius or a centre shifts.
	 */
	@Test
	void oneBlobIsTheEllipsoidItsParametersDescribe() {
		boolean[] filled = OilLakeShape.build(10, 6, 1, () -> 0.5);
		assertTrue(at(filled, 10, 6, 10), "the centre of the blob must be hollow");
		assertTrue(at(filled, 15, 6, 10), "5 blocks along X is well inside a 6.25 radius");
		assertFalse(at(filled, 18, 6, 10), "8 blocks along X is well outside a 6.25 radius");
		assertTrue(at(filled, 5, 6, 10), "the blob is centred on X=10, not on the box edge");
		assertFalse(at(filled, 2, 6, 10), "8 blocks the other way along X is outside too");
		assertTrue(at(filled, 10, 6, 15), "Z uses its own radius and must match X's");
		assertFalse(at(filled, 10, 6, 18), "8 blocks along Z is outside");
		assertTrue(at(filled, 10, 8, 10), "2 blocks along Y is inside a 3.55 radius");
		assertFalse(at(filled, 10, 10, 10), "4 blocks along Y is outside a 3.55 radius");
	}

	/**
	 * Exactly {@code blobCount} blobs are drawn, no more. The supplier here scripts two DIFFERENT
	 * blobs — the first centred at {@code (7.25, 6, 7.25)} with radii {@code 6.25/3.55/6.25}, the
	 * second at {@code (15.5, 6, 15.5)} with radii {@code 3.5/2.1/3.5} — and asks for one. Cell
	 * {@code (17, 6, 17)} belongs to the second blob alone, so it is empty if and only if the loop
	 * stopped where it was told to.
	 */
	@Test
	void onlyTheRequestedNumberOfBlobsIsDrawn() {
		double[] script = {
				0.5, 0.5, 0.5, 0.0, 0.0, 0.5,
				0.0, 0.0, 0.0, 1.0, 1.0, 0.5,
		};
		int[] cursor = { 0 };
		boolean[] filled = OilLakeShape.build(10, 6, 1, () -> script[cursor[0]++ % script.length]);
		assertTrue(at(filled, 7, 6, 7), "the first blob must be drawn");
		assertFalse(at(filled, 17, 6, 17),
				"cell (17, 6, 17) belongs to the second blob, which was not requested");
		assertEquals(6, cursor[0], "one blob must consume exactly six random draws");
	}

	/** Reads a cell of a 10 × 6 grid by world-ish coordinates, keeping the assertions above legible. */
	private static boolean at(boolean[] filled, int x, int y, int z) {
		return filled[OilLakeShape.index(10, 6, x, y, z)];
	}

	/**
	 * The lower half of the box is fluid and the upper half is the air pocket. Getting this backwards
	 * turns every underground deposit into a blob of oil with a cavity underneath it.
	 */
	@ParameterizedTest(name = "level {1} of a vertical radius {0} box is fluid: {2}")
	@CsvSource({ "5, 0, true", "5, 4, true", "5, 5, false", "5, 9, false", "2, 1, true", "2, 2, false" })
	void theFluidLevelSplitIsTheLowerHalf(int verticalRadius, int y, boolean expected) {
		assertEquals(expected, OilLakeShape.isFluidLevel(verticalRadius, y));
	}
}
