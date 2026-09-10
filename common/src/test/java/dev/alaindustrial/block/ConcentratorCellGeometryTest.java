package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariants of the generated concentrator cell table (MOD-603).
 *
 * <p>The table is written by {@code tools/gen_radiant_solar_panel_assets.py} out of the designer's
 * model, so nobody types these numbers — which is exactly why they need a guard. A regeneration that
 * silently changed the cut, the rotation convention or the order of the rows would still compile, and
 * the machine would draw itself inside out with no error anywhere. Every assertion below states a
 * property of the STRUCTURE, not a copy of the current numbers, so a legitimate change to the model
 * keeps them green and a broken cut does not.
 *
 * <p>Minecraft-free on purpose: this is the one part of the structure that can be reached from the
 * {@code common} unit lane, which has no Minecraft jar.
 */
class ConcentratorCellGeometryTest {

	private static final int CELLS = 8;
	private static final int FACINGS = 4;

	@Test
	@DisplayName("the eight cells are the eight corners of a two-by-two-by-two box, each once")
	void offsetsAreTheEightCorners() {
		assertEquals(CELLS, ConcentratorCellGeometry.OFFSETS.length, "cell count");
		Set<String> seen = new HashSet<>();
		for (int[] cell : ConcentratorCellGeometry.OFFSETS) {
			assertEquals(3, cell.length, "an offset is a triple");
			for (int axis : cell) {
				assertTrue(axis == 0 || axis == 1,
						"a cell of a two-wide box is at 0 or 1 on every axis, got " + axis);
			}
			assertTrue(seen.add(cell[0] + "," + cell[1] + "," + cell[2]),
					"two cells share the offset " + cell[0] + "," + cell[1] + "," + cell[2]);
		}
		assertEquals(CELLS, seen.size(), "every corner is used exactly once");
	}

	@Test
	@DisplayName("the first cell is the core, standing at the origin of the structure")
	void theCoreIsTheOrigin() {
		assertArrayEquals(new int[] {0, 0, 0}, ConcentratorCellGeometry.OFFSETS[0],
				"the core anchors the structure, so its offset from itself is zero");
		assertEquals("CORE", ConcentratorCellGeometry.NAMES[0]);
	}

	@Test
	@DisplayName("every cell has a name, and the names are distinct")
	void namesLineUpWithOffsets() {
		assertEquals(ConcentratorCellGeometry.OFFSETS.length, ConcentratorCellGeometry.NAMES.length,
				"a cell without a name cannot be turned into a blockstate value");
		Set<String> names = new HashSet<>();
		for (String name : ConcentratorCellGeometry.NAMES) {
			assertTrue(names.add(name), "duplicate cell name " + name);
		}
	}

	@Test
	@DisplayName("every box is a real volume that stays inside its own block")
	void boxesAreInsideTheirBlock() {
		assertEquals(FACINGS, ConcentratorCellGeometry.BOXES.length, "one row per horizontal facing");
		for (double[][] row : ConcentratorCellGeometry.BOXES) {
			assertEquals(CELLS, row.length, "one box per cell");
			for (double[] box : row) {
				assertEquals(6, box.length, "a box is two corners");
				for (int axis = 0; axis < 3; axis++) {
					assertTrue(box[axis] >= 0.0 && box[axis + 3] <= 16.0,
							"a shape may not leave its own block: " + box[axis] + ".." + box[axis + 3]);
					assertTrue(box[axis + 3] > box[axis],
							"a cell with a flat shape would be walked through");
				}
			}
		}
	}

	@Test
	@DisplayName("the four facings are quarter turns of one another")
	void facingsAreQuarterTurnsOfTheCanonicalRow() {
		int north = indexOfYaw(0);
		for (int facing = 0; facing < FACINGS; facing++) {
			int quarters = ConcentratorCellGeometry.MODEL_YAW[facing] / 90;
			for (int cell = 0; cell < CELLS; cell++) {
				assertArrayEquals(turned(ConcentratorCellGeometry.BOXES[north][cell], quarters),
						ConcentratorCellGeometry.BOXES[facing][cell], 1.0e-6,
						"facing row " + facing + ", cell " + ConcentratorCellGeometry.NAMES[cell]
								+ " is not the canonical box turned " + quarters + " quarter(s)");
			}
		}
	}

	@Test
	@DisplayName("the four yaw values are the four right angles, once each")
	void yawCoversEveryRightAngle() {
		assertEquals(FACINGS, ConcentratorCellGeometry.MODEL_YAW.length);
		Set<Integer> seen = new HashSet<>();
		for (int yaw : ConcentratorCellGeometry.MODEL_YAW) {
			assertTrue(yaw % 90 == 0 && yaw >= 0 && yaw < 360, "not a right angle: " + yaw);
			assertTrue(seen.add(yaw), "two facings share the rotation " + yaw);
		}
		assertEquals(FACINGS, seen.size());
	}

	/** Index of the row whose model rotation is the given angle. */
	private static int indexOfYaw(int yaw) {
		for (int i = 0; i < ConcentratorCellGeometry.MODEL_YAW.length; i++) {
			if (ConcentratorCellGeometry.MODEL_YAW[i] == yaw) {
				return i;
			}
		}
		throw new AssertionError("no facing row is drawn unrotated");
	}

	/**
	 * A box turned clockwise seen from above, the way vanilla's blockstate {@code y} turns a model:
	 * {@code (x, z) -> (16 - z, x)} per quarter. Written out here independently of the generator so
	 * the assertion checks the convention rather than repeating whatever the generator did.
	 */
	private static double[] turned(double[] box, int quarters) {
		double x0 = box[0];
		double z0 = box[2];
		double x1 = box[3];
		double z1 = box[5];
		for (int i = 0; i < quarters; i++) {
			double nx0 = 16.0 - z1;
			double nx1 = 16.0 - z0;
			double nz0 = x0;
			double nz1 = x1;
			x0 = nx0;
			x1 = nx1;
			z0 = nz0;
			z1 = nz1;
		}
		return new double[] {x0, box[1], z0, x1, box[4], z1};
	}
}
