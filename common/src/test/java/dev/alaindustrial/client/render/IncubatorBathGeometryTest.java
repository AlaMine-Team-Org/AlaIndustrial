package dev.alaindustrial.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariants of the generated nutrient-bath column (MOD-605).
 *
 * <p>The rows are cut out of the chamber's own silhouette by {@code tools/gen_incubator_assets.py}.
 * The first version of this water was a single box inside a stepped room, and what the player saw was
 * a chamber empty under the lid and short at the floor — so the properties that matter are that the
 * steps <em>tile</em> the whole height and <em>follow</em> the room inwards. A regeneration that broke
 * either would still compile and would look exactly like that bug again.
 *
 * <p>Minecraft-free on purpose: this table is the part of the bath the {@code common} unit lane can
 * reach, and that lane has no Minecraft jar.
 */
class IncubatorBathGeometryTest {

	private static final float BLOCK = 16.0F;
	private static final float EPS = 1.0e-3F;

	@Test
	@DisplayName("every row is a real box standing inside the block")
	void everyStepIsARealBox() {
		assertTrue(IncubatorBathGeometry.STEPS.length > 0, "a bath with no steps holds nothing");
		for (float[] step : IncubatorBathGeometry.STEPS) {
			assertEquals(6, step.length, "a step is {x0, z0, x1, z1, y0, y1}");
			assertTrue(step[2] - step[0] > EPS, "no width: " + describe(step));
			assertTrue(step[3] - step[1] > EPS, "no depth: " + describe(step));
			assertTrue(step[5] - step[4] > EPS, "no height: " + describe(step));
			for (float v : step) {
				assertTrue(v >= 0.0F && v <= BLOCK, "outside the block: " + describe(step));
			}
		}
	}

	@Test
	@DisplayName("the steps tile the chamber from its floor to the level a full tank reaches")
	void stepsTileTheChamber() {
		float expected = IncubatorBathGeometry.FLOOR;
		for (float[] step : IncubatorBathGeometry.STEPS) {
			assertEquals(expected, step[4], EPS,
					"a gap or an overlap in the column at " + describe(step));
			expected = step[5];
		}
		assertEquals(IncubatorBathGeometry.CEILING, expected, EPS,
				"the water stops short of the level a full tank is supposed to reach");
	}

	@Test
	@DisplayName("the column follows the chamber inwards and never bulges back out")
	void stepsNarrowUpwards() {
		float previous = Float.MAX_VALUE;
		for (float[] step : IncubatorBathGeometry.STEPS) {
			float width = step[2] - step[0];
			float depth = step[3] - step[1];
			assertEquals(width, depth, EPS, "a step is not square in plan: " + describe(step));
			assertTrue(width < previous + EPS,
					"a step is wider than the one below it, so the water would sit outside the glass: "
							+ describe(step));
			previous = width;
		}
	}

	@Test
	@DisplayName("every step is centred, so the water stands over the machine rather than beside it")
	void stepsAreCentred() {
		for (float[] step : IncubatorBathGeometry.STEPS) {
			assertEquals(BLOCK, step[0] + step[2], EPS, "off centre on x: " + describe(step));
			assertEquals(BLOCK, step[1] + step[3], EPS, "off centre on z: " + describe(step));
		}
	}

	private static String describe(float[] step) {
		StringBuilder out = new StringBuilder("{");
		for (int i = 0; i < step.length; i++) {
			out.append(i == 0 ? "" : ", ").append(step[i]);
		}
		return out.append('}').toString();
	}
}
