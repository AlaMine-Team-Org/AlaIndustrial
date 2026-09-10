package dev.alaindustrial.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariants of the generated section-ghost boxes (MOD-603).
 *
 * <p>The table is written by {@code tools/gen_concentrator_section_assets.py} from the same builder
 * that writes the block's model, so nobody types these numbers. What needs guarding is not their
 * exact values but the two properties the shape was designed around: that the cage stays inside its
 * block, and that the glass core never shares a plane with the frame.
 */
class ConcentratorSectionGhostTest {

	/** Eight corners plus twelve edge bars. */
	private static final int FRAME_BOXES = 20;

	@Test
	@DisplayName("the cage is eight corners and twelve bars, and nothing else")
	void frameHasEveryPieceOfTheCage() {
		assertEquals(FRAME_BOXES, ConcentratorSectionGhost.FRAME.length,
				"a missing bar leaves a gap in the ghost the placed block does not have");
	}

	@Test
	@DisplayName("every box is a real volume and stays inside its own block")
	void boxesAreInsideTheBlock() {
		for (double[] box : allBoxes()) {
			assertEquals(6, box.length, "a box is two corners");
			for (int axis = 0; axis < 3; axis++) {
				assertTrue(box[axis] >= 0.0 && box[axis + 3] <= 1.0,
						"the ghost may not leave its cell: " + box[axis] + ".." + box[axis + 3]);
				assertTrue(box[axis + 3] > box[axis], "a flat box draws nothing");
			}
		}
	}

	@Test
	@DisplayName("the glass core shares no plane with the cage")
	void glassNeverTouchesTheFrame() {
		double[] glass = ConcentratorSectionGhost.GLASS;
		for (double[] frame : ConcentratorSectionGhost.FRAME) {
			for (int axis = 0; axis < 3; axis++) {
				for (int glassSide = 0; glassSide < 2; glassSide++) {
					for (int frameSide = 0; frameSide < 2; frameSide++) {
						double g = glass[axis + glassSide * 3];
						double f = frame[axis + frameSide * 3];
						assertTrue(Math.abs(g - f) > 1.0e-6,
								"glass and frame share the plane " + g + " on axis " + axis
										+ " — coplanar faces make the block flicker as the camera moves");
					}
				}
			}
		}
	}

	@Test
	@DisplayName("the glass core sits inside the cage, not around it")
	void glassIsInsideTheCage() {
		double[] glass = ConcentratorSectionGhost.GLASS;
		for (int axis = 0; axis < 3; axis++) {
			assertTrue(glass[axis] > 0.0 && glass[axis + 3] < 1.0,
					"the core must not reach the block boundary, or it would z-fight the neighbour");
		}
	}

	private static double[][] allBoxes() {
		double[][] frame = ConcentratorSectionGhost.FRAME;
		double[][] all = new double[frame.length + 1][];
		System.arraycopy(frame, 0, all, 0, frame.length);
		all[frame.length] = ConcentratorSectionGhost.GLASS;
		return all;
	}
}
