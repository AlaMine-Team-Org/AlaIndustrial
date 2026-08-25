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
 * L1 measurement of how much oil a deposit is actually worth (MOD-265).
 *
 * <p>The numbers published in {@code docs/PERFORMANCE.md} used to come from a one-off run that left
 * nothing behind — no seed, no script, no test. They could not be reproduced, and when they were
 * finally recomputed two of the four rows turned out to be wrong. This class is the missing half of
 * the fix: it runs the real geometry and pins the result, so that changing the shape of a deposit —
 * or the ranges in a configured feature — reddens a test instead of silently invalidating a table.
 *
 * <p><b>What the numbers mean.</b> They are counts of oil <em>source</em> blocks (one source = one
 * bucket = 1000 mB) over the bare geometry. In a world they are an upper bound: the feature still
 * skips cells it may not replace, and {@code OilLakeFeature} abandons whole sites that cannot hold a
 * lake. They also are not a specific world's deposits — worldgen runs on xoroshiro through
 * {@code WorldgenRandom}, not on {@link Random} — so what is pinned here is the distribution of the
 * shape, sampled through a source that a second implementation can reproduce exactly.
 *
 * <p><b>Why the protocol is spelled out.</b> {@link #SEED} and {@link #SAMPLES} are part of the
 * published figures, not an implementation detail: at 400 samples the deep layer's median swings by
 * about 4 % from seed to seed, which is how the old table came to hold numbers nobody could
 * regenerate. {@code docs/tools/oil_volume_check.py} replays this exact protocol in Python and
 * compares its result against the same literals below, which is what keeps the replica honest —
 * without that link the two implementations would be free to drift apart, each green on its own.
 *
 * @implements TC-OIL-002-UNIT03 — oil deposit volume
 */
class OilLakeVolumeTest {

	/** Pinned sampler seed. Changing it changes every figure below and in the docs. */
	private static final long SEED = 265L;

	/**
	 * Samples per layer. Odd on purpose: an even count makes the median the average of two
	 * neighbours, and a value ending in {@code .5} cannot be compared across two languages without
	 * first agreeing on a rounding convention. An odd count makes the median an actual sample.
	 */
	private static final int SAMPLES = 2001;

	/** Surface level the geyser figures assume; its shaft is as long as the terrain is high. */
	private static final int GEYSER_SURFACE_Y = 64;

	/** Depth of the geyser dome, from {@code oil_geyser.json}. */
	private static final int GEYSER_DOME_CENTER_Y = -45;

	/**
	 * The pinned volume of each lake layer, run through the real {@link OilLakeShape}.
	 *
	 * <p>The radius and blob ranges are the ones in {@code oil_lake_{small,medium,large}.json}; they
	 * are repeated here because {@code :common:test} has no Minecraft on its classpath and therefore
	 * cannot read a configured feature through its codec. The Python gate reads those files for real
	 * and fails if they stop matching these columns, so the duplication is watched rather than
	 * trusted.
	 */
	@ParameterizedTest(name = "{0} lake layer holds the pinned amount of oil")
	@CsvSource({
			//        h_min  h_max  v_min  v_max  b_min  b_max    min  median   max        sum  empty
			"small,       3,     5,     2,     3,     2,     4,     0,      2,    45,    16265,  995",
			"medium,      6,     9,     3,     5,     4,     7,    13,    136,   477,   317674,    0",
			"large,      10,    14,     6,     9,     6,    10,   364,   1239,  2953,  2592464,    0",
	})
	void aLayerHoldsThePinnedAmountOfOil(String layer, int horizontalMin, int horizontalMax,
			int verticalMin, int verticalMax, int blobMin, int blobMax,
			int expectedMin, int expectedMedian, int expectedMax, long expectedSum, int expectedEmpty) {
		int[] volumes = sampleLayer(horizontalMin, horizontalMax, verticalMin, verticalMax,
				blobMin, blobMax);
		int[] sorted = volumes.clone();
		Arrays.sort(sorted);

		long sum = 0;
		int empty = 0;
		for (int volume : volumes) {
			sum += volume;
			if (volume == 0) {
				empty++;
			}
		}

		assertEquals(expectedMin, sorted[0], layer + ": smallest deposit changed");
		assertEquals(expectedMedian, sorted[SAMPLES / 2], layer + ": median deposit changed");
		assertEquals(expectedMax, sorted[SAMPLES - 1], layer + ": largest deposit changed");
		// The sum rather than the mean: it is an exact integer, so the assertion needs no tolerance
		// and no agreement on how to round. The published mean is this divided by SAMPLES.
		assertEquals(expectedSum, sum, layer + ": total oil over the run changed");
		assertEquals(expectedEmpty, empty, layer + ": number of deposits containing no oil changed");
	}

	/**
	 * Half of every surface deposit in the game contains no oil whatsoever, and that is arithmetic
	 * rather than luck.
	 *
	 * <p>Oil occupies levels {@code 0 <= y < verticalRadius}. Level 0 is the grid shell, which
	 * {@link OilLakeShape} never fills. Level 1 cannot be filled either, for any deposit at all: a
	 * blob's centre is drawn from {@code [radiusY + 1, …)}, so {@code |1 - centreY| >= radiusY},
	 * so the normalised distance is at least 1 and the strictly-less-than test can never pass. That
	 * leaves {@code verticalRadius - 2} usable levels — and {@code oil_lake_small} rolls a vertical
	 * radius of 2 or 3 with equal odds.
	 *
	 * <p>So a player who finds a surface deposit finds, half the time, a hole of cave air in the
	 * ground with nothing in it. This test pins the defect rather than the intent: whoever changes
	 * the ranges or the fluid split to fix it will see this go red, which is the point.
	 */
	@ParameterizedTest(name = "a vertical radius of 2 holds no oil at all (seed {0})")
	@CsvSource({ "1", "42", "265", "-99", "1234567" })
	void aVerticalRadiusOfTwoHoldsNoOilAtAll(long seed) {
		Random random = new Random(seed);
		for (int horizontalRadius = 3; horizontalRadius <= 5; horizontalRadius++) {
			for (int blobs = 2; blobs <= 4; blobs++) {
				assertEquals(0, oilSources(horizontalRadius, 2, blobs, random),
						"a " + horizontalRadius + "x2 deposit with " + blobs
								+ " blobs produced oil, which the geometry does not allow");
			}
		}
	}

	/**
	 * The lowest level that can ever hold oil is 2, not 1 — the invariant behind the test above,
	 * asserted directly on a deposit big enough that the shape is otherwise dense.
	 */
	@Test
	void theLowestLevelThatCanHoldOilIsTwo() {
		int horizontalRadius = 14;
		int verticalRadius = 9;
		boolean[] filled = OilLakeShape.build(horizontalRadius, verticalRadius, 10,
				new Random(SEED)::nextDouble);
		int width = OilLakeShape.width(horizontalRadius);
		boolean anyAtTwo = false;
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				assertFalse(filled[OilLakeShape.index(horizontalRadius, verticalRadius, x, 1, z)],
						"level 1 of (" + x + ", " + z + ") is hollow, which the centre bound forbids");
				anyAtTwo |= filled[OilLakeShape.index(horizontalRadius, verticalRadius, x, 2, z)];
			}
		}
		assertTrue(anyAtTwo, "level 2 is empty too — then this test proves nothing about level 1");
	}

	/**
	 * The surface of a blob is solid: a cell at normalised distance exactly 1 stays outside.
	 *
	 * <p>Two implementations now compute this shape — the feature and the Python gate — and a
	 * comparison that is strict in one and not in the other would disagree only on cells that land
	 * exactly on the surface. No such cell exists for the shipped radius ranges, which is precisely
	 * why the difference has to be pinned deliberately: it cannot be caught by sampling, and it would
	 * surface the day somebody widens a configured feature.
	 *
	 * <p>The parameters are chosen to make the boundary exactly representable rather than to be
	 * realistic: every draw fixed at {@code 0.5} gives {@code radiusY = 13} centred on {@code y = 20},
	 * and a centre of {@code x = z = 3} on the horizontal axes, so the cell 13 below the centre sits
	 * at distance exactly 1.0 with no rounding involved at all.
	 */
	@Test
	void theSurfaceOfABlobIsSolid() {
		int horizontalRadius = 3;
		int verticalRadius = 20;
		boolean[] filled = OilLakeShape.build(horizontalRadius, verticalRadius, 1, () -> 0.5);
		assertFalse(filled[OilLakeShape.index(horizontalRadius, verticalRadius, 3, 7, 3)],
				"the cell exactly one radius from the centre is on the surface and must stay solid");
		assertTrue(filled[OilLakeShape.index(horizontalRadius, verticalRadius, 3, 8, 3)],
				"the cell just inside the surface must be hollow — otherwise this proves nothing");
	}

	/**
	 * A blob whose range collapses still spends its six draws.
	 *
	 * <p>{@code OilLakeShape.between} draws before it checks whether the range is empty, so that a
	 * degenerate blob costs the stream the same six values as any other. Skipping the draw would be
	 * the obvious simplification and it would desynchronise every later blob — and the Python gate
	 * replays the same stream, so it would desynchronise that too. The shipped radius ranges cannot
	 * reach the degenerate branch, so it can only be pinned by calling the primitive directly.
	 */
	@Test
	void aDegenerateBlobStillSpendsItsDraws() {
		int[] draws = { 0 };
		OilLakeShape.build(1, 1, 2, () -> {
			draws[0]++;
			return 0.5;
		});
		assertEquals(12, draws[0],
				"two blobs of a collapsed deposit must cost twelve draws, like any other two");
	}

	/**
	 * The dome's outermost shell of oil is the one at exactly the radius.
	 *
	 * <p>This is the off-by-one that produced the wrong published figure: computing the dome with a
	 * strict {@code <} instead of {@code <=} loses the entire surface layer of the sphere — 78 blocks
	 * at radius 11 — and the resulting number was small enough to be less than the dome the game
	 * actually builds.
	 */
	@ParameterizedTest(name = "the dome boundary at radius {0} is oil, not seal")
	@CsvSource({ "8", "9", "10", "11" })
	void theDomeBoundaryCellIsOil(int radius) {
		assertTrue(OilGeyserShape.isDomeSource(radius, radius, 0, 0),
				"the cell at exactly the radius must be oil");
		assertFalse(OilGeyserShape.isDomeShell(radius, radius, 0, 0),
				"the cell at exactly the radius must not also count as seal");
		assertFalse(OilGeyserShape.isDomeSource(radius, radius + 1, 0, 0),
				"one cell past the radius must not be oil");
		assertTrue(OilGeyserShape.isDomeShell(radius, radius + 1, 0, 0),
				"one cell past the radius must be seal");
	}

	/**
	 * The shaft starts inside the dome, so a plain sum of the three parts counts two cells twice.
	 *
	 * <p>Counted here by walking the shaft and asking the dome about each cell, rather than by
	 * restating {@link OilGeyserShape#DOME_SHAFT_OVERLAP} — a test that repeats the constant it is
	 * checking would stay green if the shaft's starting level moved.
	 */
	@ParameterizedTest(name = "shaft and dome overlap by exactly two cells at radius {0}")
	@CsvSource({ "8", "9", "10", "11" })
	void theShaftAndTheDomeOverlapByTwoCells(int radius) {
		int fromY = OilGeyserShape.shaftFromY(GEYSER_DOME_CENTER_Y, radius);
		int toY = OilGeyserShape.shaftToY(GEYSER_SURFACE_Y);
		int inside = 0;
		for (int y = fromY; y <= toY; y++) {
			if (OilGeyserShape.isDomeSource(radius, 0, y - GEYSER_DOME_CENTER_Y, 0)) {
				inside++;
			}
		}
		assertEquals(OilGeyserShape.DOME_SHAFT_OVERLAP, inside,
				"the number of shaft cells already flooded by the dome changed");
	}

	/** The pinned size of a geyser, the mod's largest deposit and the one no L1 test used to cover. */
	@ParameterizedTest(name = "a geyser of radius {0} with a spout of {1} is worth {3} oil")
	@CsvSource({
			//     radius  spout  dome  total
			"           8,     1, 2109,  2210",
			"           8,     3, 2109,  2212",
			"           9,     2, 3071,  3172",
			"          10,     2, 4169,  4269",
			"          11,     1, 5575,  5673",
			"          11,     3, 5575,  5675",
	})
	void aGeyserIsWorthThePinnedAmountOfOil(int radius, int spoutHeight, int expectedDome,
			int expectedTotal) {
		assertEquals(expectedDome, OilGeyserShape.domeSourceCount(radius),
				"the dome interior at radius " + radius + " changed");
		assertEquals(expectedTotal, OilGeyserShape.sourceBlockCount(GEYSER_SURFACE_Y,
				GEYSER_DOME_CENTER_Y, radius, spoutHeight),
				"the whole geyser at radius " + radius + " changed");
	}

	/**
	 * Runs one layer through the same call order {@code OilLakeFeature.place} uses, so that the
	 * pinned figures describe the feature and not a rearrangement of it: horizontal radius, then
	 * vertical radius, then blob count, then the six draws per blob that {@link OilLakeShape} takes.
	 */
	private static int[] sampleLayer(int horizontalMin, int horizontalMax, int verticalMin,
			int verticalMax, int blobMin, int blobMax) {
		Random random = new Random(SEED);
		int[] volumes = new int[SAMPLES];
		for (int i = 0; i < SAMPLES; i++) {
			int horizontalRadius = random.nextInt(horizontalMax - horizontalMin + 1) + horizontalMin;
			int verticalRadius = random.nextInt(verticalMax - verticalMin + 1) + verticalMin;
			int blobs = random.nextInt(blobMax - blobMin + 1) + blobMin;
			volumes[i] = oilSources(horizontalRadius, verticalRadius, blobs, random);
		}
		return volumes;
	}

	/** Source blocks in one deposit: hollow cells below the fluid line, exactly as the feature writes them. */
	private static int oilSources(int horizontalRadius, int verticalRadius, int blobs, Random random) {
		boolean[] filled = OilLakeShape.build(horizontalRadius, verticalRadius, blobs,
				random::nextDouble);
		int width = OilLakeShape.width(horizontalRadius);
		int height = OilLakeShape.height(verticalRadius);
		int count = 0;
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				for (int y = 0; y < height; y++) {
					if (OilLakeShape.isFluidLevel(verticalRadius, y)
							&& filled[OilLakeShape.index(horizontalRadius, verticalRadius, x, y, z)]) {
						count++;
					}
				}
			}
		}
		return count;
	}
}
