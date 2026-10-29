package dev.alaindustrial.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 — where a monitoring panel puts its watched item (MOD-863): a 3D model's back rests on the
 * opaque screen instead of sinking behind it, its front stays inside the block, and a model wider
 * than a slot shrinks to its cell.
 */
class MonitorItemFitTest {

	/** A block's inventory pose: the unit cube turned 30° about X and 225° about Y, scaled 0.625. */
	private static float[] guiBlockBox() {
		double ax = Math.toRadians(30);
		double ay = Math.toRadians(225);
		float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
				Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int i = 0; i < 8; i++) {
			double x = ((i & 1) == 0 ? -0.5 : 0.5) * 0.625;
			double y = ((i & 2) == 0 ? -0.5 : 0.5) * 0.625;
			double z = ((i & 4) == 0 ? -0.5 : 0.5) * 0.625;
			double x1 = x * Math.cos(ay) + z * Math.sin(ay);
			double z1 = -x * Math.sin(ay) + z * Math.cos(ay);
			double y2 = y * Math.cos(ax) - z1 * Math.sin(ax);
			double z2 = y * Math.sin(ax) + z1 * Math.cos(ax);
			float[] p = {(float) x1, (float) y2, (float) z2};
			for (int a = 0; a < 3; a++) {
				box[2 * a] = Math.min(box[2 * a], p[a]);
				box[2 * a + 1] = Math.max(box[2 * a + 1], p[a]);
			}
		}
		return box;
	}

	private static float back(MonitorItemFit.Fit fit, float minZ) {
		return fit.z() + fit.depthScale() * minZ;
	}

	private static float front(MonitorItemFit.Fit fit, float maxZ) {
		return fit.z() + fit.depthScale() * maxZ;
	}

	/**
	 * @implements MOD-863 — the whole of a block's isometric cube lies in front of the screen: before the
	 *     fix the slab was centred on the screen plane and its far half was hidden
	 * @covers MOD-863
	 */
	@Test
	void blockCubeRestsOnTheScreenAndStaysInsideTheBlock() {
		float[] b = guiBlockBox();
		MonitorItemFit.Fit fit = MonitorItemFit.fit(b[0], b[1], b[2], b[3], b[4], b[5]);
		assertTrue(back(fit, b[4]) >= MonitorItemFit.SCREEN_PLANE + 0.001F,
				"back of the cube " + back(fit, b[4]) + " behind the screen " + MonitorItemFit.SCREEN_PLANE);
		assertTrue(front(fit, b[5]) <= 0.5F, "front of the cube pokes out of the block: " + front(fit, b[5]));
		assertEquals(MonitorItemFit.BASE_SCALE, fit.scale(), 0.0F, "a block icon keeps the inventory size");
	}

	/**
	 * @implements MOD-863 — a model far deeper than a block is squeezed into the recess, not pushed out
	 * @covers MOD-863
	 */
	@Test
	void deepModelIsSqueezedIntoTheRecess() {
		MonitorItemFit.Fit fit = MonitorItemFit.fit(-0.5F, 0.5F, -0.5F, 0.5F, -3.0F, 1.0F);
		assertTrue(back(fit, -3.0F) >= MonitorItemFit.SCREEN_PLANE + 0.001F);
		assertTrue(front(fit, 1.0F) <= MonitorItemFit.FRONT_LIMIT + 1e-6F);
		assertTrue(fit.depthScale() < MonitorItemFit.MAX_DEPTH_SCALE);
	}

	/**
	 * @implements MOD-863 — a model reaching past its slot shrinks so it stays in its own cell
	 * @covers MOD-863
	 */
	@Test
	void oversizedModelShrinksToItsCell() {
		MonitorItemFit.Fit fit = MonitorItemFit.fit(-1.0F, 0.6F, -0.4F, 0.8F, -0.05F, 0.05F);
		assertEquals(MonitorItemFit.BASE_SCALE * MonitorItemFit.SLOT_HALF,
				fit.scale(), 1e-6F, "the widest reach, 1.0, lands on half a slot");
	}

	/**
	 * @implements MOD-863 — a flat item looks as it did: same size, lying on the screen
	 * @covers MOD-863
	 */
	@Test
	void flatItemKeepsItsSizeAndPlace() {
		float half = 1.0F / 32.0F;
		MonitorItemFit.Fit fit = MonitorItemFit.fit(-0.5F, 0.5F, -0.5F, 0.5F, -half, half);
		assertEquals(MonitorItemFit.BASE_SCALE, fit.scale(), 0.0F);
		assertEquals(MonitorItemFit.MAX_DEPTH_SCALE, fit.depthScale(), 0.0F);
		assertEquals(MonitorItemFit.SCREEN_PLANE + MonitorItemFit.SCREEN_CLEARANCE, back(fit, -half), 1e-6F);
	}
}
