package dev.alaindustrial.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 unit tests for the sampling geometry of the oil structure filter (MOD-238 audit, generalised
 * in MOD-248).
 *
 * <p>The world-side half of {@link OilLakeFilter} cannot be tested at L2 — a gametest world never
 * runs the chunk-decoration pipeline, so {@code shouldPlace} is never reached with a
 * {@code WorldGenRegion} — and the registration/data half is guarded separately by
 * {@code NeoForgeOilWorldGenTest}. What remains, and what is genuinely easy to break, is WHICH
 * positions of the candidate footprint get probed. That is this file.
 *
 * <p>Since MOD-248 the footprint is an argument rather than a constant: four oil features with four
 * extents share this filter, from a five-block surface puddle to a geyser whose shaft runs two
 * hundred blocks down. The interesting cases are therefore the ones the old fixed geometry could
 * not express — a large radius, and a vertical span that must be walked in steps instead of
 * sampling three levels and hoping.
 *
 * @implements TC-OIL-002-UNIT01 — oil structure filter footprint sampling
 */
class OilLakeSamplesTest {

	/** Every position the filter would probe for the given origin and footprint. */
	private static List<int[]> probedAt(int x, int y, int z, int radius, int minY, int maxY, int step) {
		List<int[]> seen = new ArrayList<>();
		OilLakeSamples.anyBlocked(x, y, z, radius, minY, maxY, step, (px, py, pz) -> {
			seen.add(new int[] { px, py, pz });
			return false;
		});
		return seen;
	}

	private static boolean probes(int radius, int minY, int maxY, int step, int px, int py, int pz) {
		return probedAt(0, 0, 0, radius, minY, maxY, step).stream()
				.anyMatch(p -> p[0] == px && p[1] == py && p[2] == pz);
	}

	/**
	 * The four footprint corners are probed, not just the origin column. A filter that only looked at
	 * the origin would still pass "a structure at the origin is rejected" while letting a deposit sit
	 * on a village nine blocks away — the exact gap this class closes.
	 */
	@ParameterizedTest(name = "corner ({0}, {1}) of a radius-9 footprint is probed")
	@CsvSource({ "-9, -9", "-9, 9", "9, -9", "9, 9" })
	void everyFootprintCornerIsProbed(int dx, int dz) {
		assertTrue(probes(9, -5, 5, 4, dx, 0, dz),
				"corner (" + dx + ", " + dz + ") is inside the footprint and must be probed");
	}

	/** The corners scale with the declared radius — that is the whole point of parameterising it. */
	@ParameterizedTest(name = "radius {0} probes its own corners and nothing wider")
	@CsvSource({ "3", "5", "9", "14", "16" })
	void cornersFollowTheDeclaredRadius(int radius) {
		assertTrue(probes(radius, -2, 2, 2, radius, 0, radius),
				"radius " + radius + " must probe its (+r, +r) corner");
		for (int[] p : probedAt(0, 0, 0, radius, -2, 2, 2)) {
			assertTrue(Math.abs(p[0]) <= radius, "x probe outside the declared radius: " + p[0]);
			assertTrue(Math.abs(p[2]) <= radius, "z probe outside the declared radius: " + p[2]);
		}
	}

	/** The origin column stays covered as well — corners are an addition, not a replacement. */
	@Test
	void originColumnIsProbed() {
		assertTrue(probes(9, -5, 5, 4, 0, 0, 0), "the placement origin itself must be probed");
	}

	/**
	 * Structure pieces are matched by bounding box, so a piece band that a deposit only clips is
	 * invisible at the placement level: the vertical span must be walked, and both ends included.
	 */
	@ParameterizedTest(name = "level y={0} of a -9..9 span with step 5 is probed")
	@CsvSource({ "-9", "-4", "1", "6", "9" })
	void theVerticalSpanIsWalkedIncludingBothEnds(int dy) {
		assertTrue(probes(14, -9, 9, 5, 0, dy, 0), "y offset " + dy + " must be probed");
	}

	/**
	 * The geyser case, which the pre-MOD-248 geometry could not express at all: two hundred blocks of
	 * shaft sampled every eight blocks leaves no gap wide enough to hide an Ancient City (its pieces
	 * are far taller than eight blocks), and the top of the spout is still covered.
	 */
	@Test
	void theGeyserSpanLeavesNoGapWiderThanTheStep() {
		List<int[]> centre = probedAt(0, 0, 0, 13, -200, 4, 8).stream()
				.filter(p -> p[0] == 0 && p[2] == 0)
				.sorted((a, b) -> Integer.compare(a[1], b[1]))
				.toList();
		assertTrue(centre.size() > 25, "expected the whole shaft to be sampled, got " + centre.size()
				+ " levels — a coarser walk can miss a structure entirely");
		for (int i = 1; i < centre.size(); i++) {
			int gap = centre.get(i)[1] - centre.get(i - 1)[1];
			assertTrue(gap <= 8, "gap of " + gap + " blocks between probed levels exceeds the step");
		}
		assertTrue(centre.get(0)[1] == -200, "the bottom of the span must be probed");
		assertTrue(centre.get(centre.size() - 1)[1] == 4, "the top of the span must be probed");
	}

	/** No probe may stray outside the footprint the feature actually writes into. */
	@Test
	void noProbeLeavesTheFootprint() {
		for (int[] p : probedAt(0, 0, 0, 9, -5, 5, 4)) {
			assertTrue(p[0] >= -9 && p[0] <= 9, "x probe out of footprint: " + p[0]);
			assertTrue(p[2] >= -9 && p[2] <= 9, "z probe out of footprint: " + p[2]);
			assertTrue(p[1] >= -5 && p[1] <= 5, "y probe out of the declared span: " + p[1]);
		}
	}

	/** Probes are offsets from the origin, not absolute coordinates. */
	@Test
	void probesAreRelativeToTheOrigin() {
		List<int[]> seen = probedAt(100, -20, -37, 9, -5, 5, 4);
		assertTrue(seen.stream().anyMatch(p -> p[0] == 91 && p[1] == -25 && p[2] == -46),
				"origin (100, -20, -37) must probe its (-9, -5, -9) corner at (91, -25, -46)");
	}

	/**
	 * The placement origin's own level is probed whatever the step works out to. Without this the
	 * medium-lake footprint (span −5…5, step 4) walks −5, −1, 3, 5 and never looks at the level the
	 * feature is actually anchored on — the single most likely level to sit inside a structure.
	 */
	@ParameterizedTest(name = "span {0}..{1} step {2} still probes the origin level")
	@CsvSource({ "-5, 5, 4", "-9, 9, 5", "-200, 4, 8", "-3, 3, 3" })
	void theOriginLevelIsAlwaysProbed(int minY, int maxY, int step) {
		assertTrue(probes(9, minY, maxY, step, 0, 0, 0),
				"y=0 must be probed for span " + minY + ".." + maxY + " step " + step);
	}

	/** A degenerate step must not spin forever or skip the walk; it is clamped to one. */
	@Test
	void aZeroStepIsClampedRatherThanLooping() {
		List<int[]> seen = probedAt(0, 0, 0, 3, -2, 2, 0);
		assertTrue(seen.stream().anyMatch(p -> p[0] == 0 && p[1] == -1 && p[2] == 0),
				"a step of 0 must be treated as 1, probing every level");
	}

	/** A clean site places: nothing blocked means the filter says yes. */
	@Test
	void nothingBlockedMeansPlacementAllowed() {
		assertFalse(OilLakeSamples.anyBlocked(0, 0, 0, 9, -5, 5, 4, (x, y, z) -> false));
	}

	/** A single hit anywhere in the footprint rejects the whole placement. */
	@Test
	void oneBlockedCornerRejectsThePlacement() {
		assertTrue(OilLakeSamples.anyBlocked(0, 0, 0, 9, -5, 5, 4, (x, y, z) -> x == 9 && z == 9),
				"a structure under one corner must reject the deposit");
	}

	/** Short circuit: once a hit is found no further probing happens. */
	@Test
	void probingStopsAtTheFirstHit() {
		int[] calls = { 0 };
		OilLakeSamples.anyBlocked(0, 0, 0, 9, -5, 5, 4, (x, y, z) -> {
			calls[0]++;
			return true;
		});
		assertTrue(calls[0] == 1, "expected exactly one probe before short-circuiting, got " + calls[0]);
	}
}
