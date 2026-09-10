package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariants of the generated incubator chamber silhouette (MOD-604).
 *
 * <p>The rows are written by {@code tools/gen_incubator_assets.py} out of the designer's model, so
 * nobody types these numbers — which is exactly why they need a guard. The block reads them as its
 * {@code VoxelShape}, and a regeneration that dropped a band, inverted a box or let one grow past the
 * block would still compile: the player would simply walk into an invisible corner, or click through
 * glass that is drawn but not there. Every assertion below states a property of the SHAPE rather
 * than a copy of today's numbers, so a legitimate change to the model keeps them green.
 *
 * <p>Minecraft-free on purpose: the table is the one part of the chamber the {@code common} unit
 * lane can reach, and that lane has no Minecraft jar.
 */
class IncubatorDomeGeometryTest {

	/** A block is sixteen pixels on every axis; nothing in a block model may leave it. */
	private static final double BLOCK = 16.0;
	private static final double EPS = 1.0e-9;

	@Test
	@DisplayName("every row is a real box: six numbers, and each axis runs low to high")
	void everyRowIsARealBox() {
		assertTrue(IncubatorDomeGeometry.STEPS.length > 0, "a chamber with no steps has no shape");
		for (double[] step : IncubatorDomeGeometry.STEPS) {
			assertEquals(6, step.length, "a box is {x0, y0, z0, x1, y1, z1}");
			for (int axis = 0; axis < 3; axis++) {
				assertTrue(step[axis + 3] - step[axis] > EPS,
						"axis " + axis + " runs backwards or is flat: " + describe(step));
			}
		}
	}

	@Test
	@DisplayName("no step leaves the block it belongs to")
	void everyStepStaysInsideItsBlock() {
		for (double[] step : IncubatorDomeGeometry.STEPS) {
			for (int i = 0; i < 6; i++) {
				assertTrue(step[i] >= -EPS && step[i] <= BLOCK + EPS,
						"coordinate " + i + " is outside 0..16: " + describe(step));
			}
		}
	}

	@Test
	@DisplayName("the steps tile the whole height, floor to lid, with no gap and no overlap")
	void stepsTileTheHeight() {
		double expected = 0.0;
		for (double[] step : IncubatorDomeGeometry.STEPS) {
			assertEquals(expected, step[1], EPS,
					"a hole or an overlap in the column of steps at " + describe(step));
			expected = step[4];
		}
		assertEquals(BLOCK, expected, EPS, "the steps stop short of the top of the block");
	}

	@Test
	@DisplayName("the chamber steps inward on the way up and never back out")
	void theChamberNarrowsUpwards() {
		double previous = Double.MAX_VALUE;
		for (double[] step : IncubatorDomeGeometry.STEPS) {
			double width = step[3] - step[0];
			double depth = step[5] - step[2];
			assertEquals(width, depth, EPS, "a step is not square in plan: " + describe(step));
			assertTrue(width < previous + EPS,
					"a step grows wider than the one below it: " + describe(step));
			previous = width;
		}
	}

	@Test
	@DisplayName("every step is centred on the block, so the chamber sits over its base")
	void everyStepIsCentred() {
		for (double[] step : IncubatorDomeGeometry.STEPS) {
			assertEquals(BLOCK, step[0] + step[3], EPS, "off centre on x: " + describe(step));
			assertEquals(BLOCK, step[2] + step[5], EPS, "off centre on z: " + describe(step));
		}
	}

	private static String describe(double[] step) {
		StringBuilder out = new StringBuilder("{");
		for (int i = 0; i < step.length; i++) {
			out.append(i == 0 ? "" : ", ").append(step[i]);
		}
		return out.append('}').toString();
	}
}
