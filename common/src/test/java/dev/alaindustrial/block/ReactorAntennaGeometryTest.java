package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The boom's collision boxes are the boom's drawing (MOD-941): {@link ReactorAntennaGeometry#MAST_BOXES} must be the
 * elements of the installed model, or the outline and the collision would disagree with what the player sees. Both
 * come from the chosen design — the model from {@code install_chosen.py}, the boxes by hand — so this test is the
 * one place a re-export that moved an element is caught.
 */
class ReactorAntennaGeometryTest {

	private static final Path MODEL =
			Path.of("src/main/resources/assets/alaindustrial/models/block/reactor_antenna_mast.json");
	private static final Pattern NUMBERS = Pattern.compile(
			"\"(from|to)\"\\s*:\\s*\\[\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*\\]");

	@Test
	void theCollisionBoxesAreTheModelsElements() throws IOException {
		Matcher m = NUMBERS.matcher(Files.readString(MODEL));
		List<double[]> corners = new ArrayList<>();
		while (m.find()) {
			corners.add(new double[] {Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)),
					Double.parseDouble(m.group(4))});
		}
		assertEquals(2 * ReactorAntennaGeometry.MAST_BOXES.length, corners.size(),
				"one from/to pair per box of ReactorAntennaGeometry.MAST_BOXES");
		for (int i = 0; i < ReactorAntennaGeometry.MAST_BOXES.length; i++) {
			double[] box = ReactorAntennaGeometry.MAST_BOXES[i];
			assertArrayEquals(new double[] {box[0], box[1], box[2]}, corners.get(2 * i), 1e-9, "from of box " + i);
			assertArrayEquals(new double[] {box[3], box[4], box[5]}, corners.get(2 * i + 1), 1e-9, "to of box " + i);
		}
	}

	/** North is the model as drawn; the other five are the blockstate's own rotations of a known point. */
	@Test
	void eachFacingTurnsThePointAsTheBlockstateDoes() {
		// The tip of the boom, at the north face in the model, ends on the face the antenna points to.
		double[] tip = {8, 13, 2};
		assertArrayEquals(new double[] {8, 13, 2}, ReactorAntennaGeometry.rotate(Facing.NORTH, 8, 13, 2), 1e-9);
		assertArrayEquals(new double[] {14, 13, 8}, ReactorAntennaGeometry.rotate(Facing.EAST, tip[0], tip[1], tip[2]),
				1e-9);
		assertArrayEquals(new double[] {8, 13, 14}, ReactorAntennaGeometry.rotate(Facing.SOUTH, tip[0], tip[1],
				tip[2]), 1e-9);
		assertArrayEquals(new double[] {2, 13, 8}, ReactorAntennaGeometry.rotate(Facing.WEST, tip[0], tip[1], tip[2]),
				1e-9);
		assertArrayEquals(new double[] {8, 14, 13}, ReactorAntennaGeometry.rotate(Facing.UP, tip[0], tip[1], tip[2]),
				1e-9);
		assertArrayEquals(new double[] {8, 2, 3}, ReactorAntennaGeometry.rotate(Facing.DOWN, tip[0], tip[1], tip[2]),
				1e-9);
	}

	/** The sleeve and the arm are drawn by their own models; their boxes are the outline and collision of each. */
	@Test
	void theSleeveAndArmBoxesAreTheirModels() throws IOException {
		assertModelIs("reactor_antenna_mast_sleeve", ReactorAntennaGeometry.SLEEVE_BOX);
		assertModelIs("reactor_antenna_mast_arm", ReactorAntennaGeometry.ARM_BOX);
	}

	/**
	 * A cable joins any face of the boom's cell but the antenna's and the one above the Yagi elements, and the
	 * blockstate draws the sleeve for exactly those faces — the installer and this rule must name the same set.
	 */
	@Test
	void theBlockstateDrawsTheSleeveForTheFacesACableMayJoin() throws IOException {
		String states = Files.readString(
				Path.of("src/main/resources/assets/alaindustrial/blockstates/reactor_antenna_mast.json"));
		for (Facing facing : Facing.values()) {
			int joinable = 0;
			for (Facing face : Facing.values()) {
				if (ReactorAntennaGeometry.isCableFace(facing, face)) {
					joinable++;
					String term = "\"facing\": \"" + facing.name().toLowerCase() + "\",\\s*\""
							+ face.name().toLowerCase() + "\": \"true\"";
					assertTrue(Pattern.compile(term).matcher(states).find(),
							"no sleeve for a cable on the " + face + " face of a " + facing + " boom");
				}
			}
			assertEquals(4, joinable, "faces a cable may join for " + facing);
			assertFalse(ReactorAntennaGeometry.isCableFace(facing, opposite(facing)), "the antenna's own face");
		}
		assertFalse(ReactorAntennaGeometry.isCableFace(Facing.EAST, Facing.UP), "the Yagi side of a wall boom");
		assertFalse(ReactorAntennaGeometry.isCableFace(Facing.UP, Facing.SOUTH), "the Yagi side of a roof boom");
		assertFalse(ReactorAntennaGeometry.isCableFace(Facing.DOWN, Facing.NORTH), "the Yagi side of a ceiling boom");
	}

	private static Facing opposite(Facing facing) {
		return Facing.values()[facing.ordinal() ^ 1];
	}

	private static void assertModelIs(String model, double[] box) throws IOException {
		Matcher m = NUMBERS.matcher(Files.readString(
				Path.of("src/main/resources/assets/alaindustrial/models/block/" + model + ".json")));
		List<double[]> corners = new ArrayList<>();
		while (m.find()) {
			corners.add(new double[] {Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)),
					Double.parseDouble(m.group(4))});
		}
		assertEquals(2, corners.size(), model + " is one box");
		assertArrayEquals(new double[] {box[0], box[1], box[2]}, corners.get(0), 1e-9, model + " from");
		assertArrayEquals(new double[] {box[3], box[4], box[5]}, corners.get(1), 1e-9, model + " to");
	}

	/** A stand-in for Minecraft's Direction: the rotation reads only the constant's name. */
	private enum Facing { DOWN, UP, NORTH, SOUTH, WEST, EAST }
}
