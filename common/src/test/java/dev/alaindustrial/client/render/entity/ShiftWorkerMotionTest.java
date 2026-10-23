package dev.alaindustrial.client.render.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 checks of the shift worker's movement (MOD-882): the raised arm of the greeting never leans into the helmet,
 * a standing worker does not swing its legs, the greeting ends on time, and the stand's look about stops while it
 * watches a player.
 */
class ShiftWorkerMotionTest {

	private static final float EPS = 1.0E-5F;

	@Test
	void theRaisedArmAlwaysLeansOutOfTheHelmet() {
		ShiftWorkerMotion m = new ShiftWorkerMotion();
		int greeting = 0;
		for (float t = 0.0F; t < ShiftWorkerMotion.GREET_TICKS; t += 0.25F) {
			m.compute(0.0F, 0.0F, t, t, 1.0F);
			if (m.greet > 0.0F) {
				greeting++;
				// A raised right arm goes outward with a negative zRot; at least the margin past the vertical.
				assertTrue(m.greetArmZ <= -ShiftWorkerMotion.GREET_MIN_OUTWARD + EPS,
						"arm leans into the helmet at tick " + t + ": zRot " + m.greetArmZ);
				assertTrue(m.greetArmX < -1.5F, "arm is not raised at tick " + t);
			}
		}
		assertTrue(greeting > 100, "the greeting barely ran");
	}

	@Test
	void theGreetingEndsOnTimeAndIsOffWithoutOne() {
		ShiftWorkerMotion m = new ShiftWorkerMotion();
		m.compute(0.0F, 0.0F, 10.0F, ShiftWorkerMotion.GREET_TICKS, 0.0F);
		assertEquals(0.0F, m.greet);
		m.compute(0.0F, 0.0F, 10.0F, -1.0F, 0.0F);
		assertEquals(0.0F, m.greet);
		m.compute(0.0F, 0.0F, 10.0F, ShiftWorkerMotion.GREET_TICKS / 2, 0.0F);
		assertEquals(1.0F, m.greet, EPS);
	}

	@Test
	void aStandingWorkerKeepsItsLegsStill() {
		ShiftWorkerMotion m = new ShiftWorkerMotion();
		for (float age = 0.0F; age < 200.0F; age += 3.7F) {
			m.compute(12.3F, 0.0F, age, -1.0F, 0.0F);
			assertEquals(0.0F, m.rightLegX, EPS);
			assertEquals(0.0F, m.leftLegX, EPS);
			assertEquals(0.0F, m.bodyZ, EPS);
		}
	}

	@Test
	void theWalkSinksOnContactAndTheBalloonBouncesWithinItsRange() {
		ShiftWorkerMotion m = new ShiftWorkerMotion();
		float lowest = Float.MAX_VALUE;
		float highest = -Float.MAX_VALUE;
		for (float pos = 0.0F; pos < 40.0F; pos += 0.1F) {
			m.compute(pos, 0.5F, 0.0F, -1.0F, 0.0F);
			lowest = Math.min(lowest, m.lift);
			highest = Math.max(highest, m.lift);
			assertTrue(m.packX >= -EPS && m.packX <= ShiftWorkerMotion.BALLOON_BOUNCE + EPS, "balloon " + m.packX);
		}
		assertEquals(0.0F, lowest, 0.02F);
		assertEquals(ShiftWorkerMotion.STEP_SINK, highest, 0.02F);
	}

	@Test
	void theLookAboutStopsWhileWatchingAPlayer() {
		ShiftWorkerMotion m = new ShiftWorkerMotion();
		float age = ShiftWorkerMotion.LOOK_TICKS / 4; // the far end of the look
		m.compute(0.0F, 0.0F, age, -1.0F, 0.0F);
		assertEquals(ShiftWorkerMotion.LOOK_YAW, m.headY, 1.0E-3F);
		m.compute(0.0F, 0.0F, age, -1.0F, 1.0F);
		assertEquals(0.0F, m.headY, EPS);
	}
}
