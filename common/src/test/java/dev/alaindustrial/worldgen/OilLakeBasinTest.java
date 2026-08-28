package dev.alaindustrial.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 measurement of the basin boundary — the denominator {@link OilLakeShape#basinHangsInTheOpen}
 * judges against (MOD-526).
 *
 * <p>{@code OilLakeFeature} refuses a site whose fluid half opens into a cavity over
 * {@link OilLakeShape#MAX_OPEN_BASIN_PERCENT} of its boundary, because sealing that much of a cave
 * hall leaves the deposit hanging in it as a stone bowl. A percentage is only as meaningful as what
 * it is a percentage OF, and that quantity is pure geometry: how many cells the fluid half of a
 * deposit borders on. It is pinned here, per shipped tier, so that widening a configured feature —
 * or changing the blob shape — reddens a test instead of silently moving what the rule means in
 * blocks.
 *
 * <p>The protocol is the one {@link OilLakeVolumeTest} fixed and published: seed 265, 2001 samples.
 * Same reasoning, and deliberately the same numbers, so the two measurements describe the same runs.
 *
 * @implements TC-OIL-002-UNIT04 — oil deposit basin boundary
 */
class OilLakeBasinTest {

	/** Pinned sampler seed — {@link OilLakeVolumeTest} explains why it is part of the figures. */
	private static final long SEED = 265L;

	/** Samples per layer, odd for the same reason as in {@link OilLakeVolumeTest}. */
	private static final int SAMPLES = 2001;

	/**
	 * How large the basin boundary of each layer is, and how many deposits are so small that a
	 * SINGLE open cell would already trip the rule.
	 *
	 * <p>That last column is the one worth watching. The rule is a share, so on a small enough basin
	 * it degenerates into "one cave cell anywhere and the deposit is refused" — which is vanilla's
	 * behaviour, the very thing the deviation exists to avoid. The count is pinned rather than
	 * asserted to be zero because the smallest tier legitimately rolls near-degenerate deposits (see
	 * {@code aVerticalRadiusOfTwoCannotHoldOilAtAll}); what must not happen is that number growing
	 * unnoticed.
	 */
	@ParameterizedTest(name = "{0} lake layer has the pinned basin boundary")
	@CsvSource({
			//        h_min  h_max  v_min  v_max  b_min  b_max    min  median   max   fragile
			"small,       3,     5,     3,     4,     2,     4,     6,     34,    86,        0",
			"medium,      6,     9,     3,     5,     4,     7,    42,    137,   268,        0",
			"large,      10,    14,     6,     9,     6,    10,   217,    466,   851,        0",
	})
	void aLayerHasThePinnedBasinBoundary(String layer, int horizontalMin, int horizontalMax,
			int verticalMin, int verticalMax, int blobMin, int blobMax,
			int expectedMin, int expectedMedian, int expectedMax, int expectedFragile) {
		int[] boundaries = sampleLayer(horizontalMin, horizontalMax, verticalMin, verticalMax,
				blobMin, blobMax);
		int[] sorted = boundaries.clone();
		Arrays.sort(sorted);

		int fragile = 0;
		for (int boundary : boundaries) {
			if (OilLakeShape.basinHangsInTheOpen(1, boundary)) {
				fragile++;
			}
		}

		assertEquals(expectedMin, sorted[0], layer + ": smallest basin boundary changed");
		assertEquals(expectedMedian, sorted[SAMPLES / 2], layer + ": median basin boundary changed");
		assertEquals(expectedMax, sorted[SAMPLES - 1], layer + ": largest basin boundary changed");
		assertEquals(expectedFragile, fragile, layer
				+ ": number of deposits a single open cell would refuse changed");
	}

	/** The share is a strict "more than", so a basin exactly at the limit is still placed. */
	@Test
	void aBasinExactlyAtTheLimitStillHolds() {
		int boundary = 100;
		int limit = OilLakeShape.MAX_OPEN_BASIN_PERCENT;
		assertFalse(OilLakeShape.basinHangsInTheOpen(limit, boundary),
				"a basin open on exactly the permitted share must still be placed");
		assertTrue(OilLakeShape.basinHangsInTheOpen(limit + 1, boundary),
				"one cell past the permitted share must refuse the site");
	}

	/**
	 * A deposit that reaches no fluid level at all has no basin, and nothing with no basin can hang.
	 *
	 * <p>Reachable, not hypothetical: at {@code verticalRadius 3} the single usable level is filled
	 * only by a blob that gets there, and 15 of 2001 surface deposits do not (MOD-502). Without the
	 * guard the rule divides by an empty boundary and every one of them is refused.
	 */
	@Test
	void anEmptyBasinNeverHangs() {
		assertFalse(OilLakeShape.basinHangsInTheOpen(0, 0), "no basin, nothing to hang");
		assertFalse(OilLakeShape.basinHangsInTheOpen(7, 0),
				"open cells without a basin are cells of some other deposit's boundary");
	}

	/**
	 * The counter and the feature agree on what a boundary cell is.
	 *
	 * <p>{@link OilLakeShape#basinBoundaryCells} exists so this measurement counts exactly the cells
	 * {@code OilLakeFeature} walks; if it ever grew its own idea of "borders hollow" the pinned
	 * numbers above would describe a set of cells the game never looks at. Counted here a second
	 * time, straight off {@link OilLakeShape#bordersHollow}, against the same grid.
	 */
	@Test
	void theCounterWalksTheSameCellsTheFeatureDoes() {
		int horizontalRadius = 12;
		int verticalRadius = 7;
		boolean[] filled = OilLakeShape.build(horizontalRadius, verticalRadius, 8,
				new Random(SEED)::nextDouble);
		int width = OilLakeShape.width(horizontalRadius);
		int height = OilLakeShape.height(verticalRadius);
		int expected = 0;
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				for (int y = 0; y < height; y++) {
					if (!OilLakeShape.isFluidLevel(verticalRadius, y)
							|| filled[OilLakeShape.index(horizontalRadius, verticalRadius, x, y, z)]) {
						continue;
					}
					if (OilLakeShape.bordersHollow(filled, horizontalRadius, verticalRadius, width,
							height, x, y, z)) {
						expected++;
					}
				}
			}
		}
		assertTrue(expected > 0, "this deposit has no basin boundary — then it proves nothing");
		assertEquals(expected, OilLakeShape.basinBoundaryCells(filled, horizontalRadius, verticalRadius),
				"basinBoundaryCells counts a different set of cells than the feature's own walk");
	}

	/** One sampled run of a layer, drawn exactly the way the feature draws a deposit. */
	private static int[] sampleLayer(int horizontalMin, int horizontalMax, int verticalMin,
			int verticalMax, int blobMin, int blobMax) {
		Random random = new Random(SEED);
		int[] boundaries = new int[SAMPLES];
		for (int sample = 0; sample < SAMPLES; sample++) {
			int horizontalRadius = between(random, horizontalMin, horizontalMax);
			int verticalRadius = between(random, verticalMin, verticalMax);
			int blobs = between(random, blobMin, blobMax);
			boolean[] filled = OilLakeShape.build(horizontalRadius, verticalRadius, blobs,
					random::nextDouble);
			boundaries[sample] = OilLakeShape.basinBoundaryCells(filled, horizontalRadius,
					verticalRadius);
		}
		return boundaries;
	}

	/** {@code UniformInt.sample} — inclusive on both ends, drawn from the one shared stream. */
	private static int between(Random random, int low, int high) {
		return low + random.nextInt(high - low + 1);
	}
}
