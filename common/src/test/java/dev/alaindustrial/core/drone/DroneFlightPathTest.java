package dev.alaindustrial.core.drone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The drawn flight along a route (MOD-858, owner's round 2): exact ends, rounded corners that stay near the route. */
class DroneFlightPathTest {

	private static final float EPS = 1.0E-4F;
	/** Up 3, across 4: an L of length 7 with its corner at (0, 3, 0). */
	private static final float[] ELL = {0, 0, 0, 0, 3, 0, 4, 3, 0};

	@Test
	void endsAreExactAndLengthIsTheSumOfLegs() {
		DroneFlightPath path = new DroneFlightPath(ELL);
		assertEquals(7.0F, path.length(), EPS);
		float[] out = new float[3];
		path.rounded(0.0F, out);
		assertArrayEquals(new float[] {0, 0, 0}, out, EPS);
		path.rounded(1.0F, out);
		assertArrayEquals(new float[] {4, 3, 0}, out, EPS);
		path.point(3.0F / 7.0F, out);
		assertArrayEquals(new float[] {0, 3, 0}, out, EPS);
	}

	/** Away from a corner the rounded point is the point; at the corner it is pulled in, by less than the window. */
	@Test
	void roundsOnlyTheCorner() {
		DroneFlightPath path = new DroneFlightPath(ELL);
		float[] plain = new float[3];
		float[] round = new float[3];
		path.point(1.5F / 7.0F, plain);
		path.rounded(1.5F / 7.0F, round);
		assertArrayEquals(plain, round, EPS);
		path.rounded(3.0F / 7.0F, round);
		assertTrue(round[0] > 0.0F && round[1] < 3.0F, "the corner must be cut inwards");
		float off = (float) Math.hypot(round[0], round[1] - 3.0F);
		assertTrue(off < DroneFlightPath.ROUNDING, "the rounded corner strays " + off + " from it");
	}

	@Test
	void boundsCoverEveryCorner() {
		assertArrayEquals(new float[] {0, 0, 0, 4, 3, 0}, DroneFlightPath.bounds(ELL), EPS);
	}
}
