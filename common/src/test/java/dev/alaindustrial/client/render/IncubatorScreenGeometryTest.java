package dev.alaindustrial.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariants of the generated incubator equaliser (MOD-604).
 *
 * <p>These numbers are cut out of the designer's model by {@code tools/gen_incubator_assets.py} and
 * are the ONE place the renderer and the block model agree on where the panel is. A regeneration that
 * picked up the wrong cubes, lost the half turn that puts the panel on the block's front, or let a
 * bar grow past the recess it sits in would still compile — and the machine would draw its screen on
 * a blank face, or poke five green splinters through its own casing. Every assertion states a
 * property of the panel rather than a copy of today's figures.
 *
 * <p>Minecraft-free on purpose: the table is the part of the panel the {@code common} unit lane can
 * reach, and that lane has no Minecraft jar.
 */
class IncubatorScreenGeometryTest {

	private static final float BLOCK = 16.0F;
	private static final float EPS = 1.0e-4F;

	@Test
	@DisplayName("the panel has bars at all, and each is a real rectangle")
	void everyBarIsARectangle() {
		assertTrue(IncubatorScreenGeometry.BARS.length > 0, "an equaliser with no bars is not one");
		for (float[] bar : IncubatorScreenGeometry.BARS) {
			assertEquals(3, bar.length, "a bar is {x0, x1, idleTop}");
			assertTrue(bar[1] - bar[0] > EPS, "a bar with no width: " + describe(bar));
		}
	}

	@Test
	@DisplayName("bars run left to right and never overlap each other")
	void barsAreOrderedAndDisjoint() {
		float previousRight = Float.NEGATIVE_INFINITY;
		for (float[] bar : IncubatorScreenGeometry.BARS) {
			assertTrue(bar[0] >= previousRight - EPS,
					"a bar starts before the previous one ends: " + describe(bar));
			previousRight = bar[1];
		}
	}

	@Test
	@DisplayName("every bar stands inside the block and inside its own recess")
	void barsStayInsideTheWell() {
		assertTrue(IncubatorScreenGeometry.WELL_TOP > IncubatorScreenGeometry.BASE_Y + EPS,
				"the recess has no height to draw a bar in");
		assertTrue(IncubatorScreenGeometry.FRONT_Z >= 0.0F
						&& IncubatorScreenGeometry.FRONT_Z <= BLOCK,
				"the panel sits outside the block it belongs to");
		for (float[] bar : IncubatorScreenGeometry.BARS) {
			assertTrue(bar[0] >= 0.0F && bar[1] <= BLOCK,
					"a bar leaves the block sideways: " + describe(bar));
			assertTrue(bar[2] > IncubatorScreenGeometry.BASE_Y + EPS,
					"a bar at rest is invisible: " + describe(bar));
			assertTrue(bar[2] <= IncubatorScreenGeometry.WELL_TOP + EPS,
					"a bar at rest pokes through the panel: " + describe(bar));
		}
	}

	@Test
	@DisplayName("the panel's half turn is a quarter-turn multiple the blockstate can express")
	void frontFaceYawIsAQuarterTurn() {
		float yaw = IncubatorScreenGeometry.FRONT_FACE_YAW;
		assertTrue(yaw >= 0.0F && yaw < 360.0F, "yaw outside one turn: " + yaw);
		assertEquals(0.0F, yaw % 90.0F, EPS,
				"a blockstate can only turn a model by quarters, got " + yaw);
	}

	@Test
	@DisplayName("the bars take their colour from inside the palette, not from its edge")
	void indicatorSitsInsideThePalette() {
		for (float uv : new float[] {IncubatorScreenGeometry.INDICATOR_U,
				IncubatorScreenGeometry.INDICATOR_V}) {
			assertTrue(uv > 0.0F && uv < 1.0F,
					"a texel coordinate on or past the sprite border samples a neighbour: " + uv);
		}
	}

	private static String describe(float[] bar) {
		StringBuilder out = new StringBuilder("{");
		for (int i = 0; i < bar.length; i++) {
			out.append(i == 0 ? "" : ", ").append(bar[i]);
		}
		return out.append('}').toString();
	}
}
