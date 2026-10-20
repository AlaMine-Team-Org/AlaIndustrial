package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Re-measures the Electric Mob Trap's model and pins {@link ElectricMobTrapCableArm#BANDS} (MOD-855).
 *
 * <p>The continuation runs over the base plate to the plate under the spark gap. Two checks hold it there
 * from every side: a ray at that plate's height through the sleeve's cross-section meets the plate exactly
 * at the band's depth, and no point of the continuation above the base plate lies inside any cube of the
 * model, so it neither stops short of the plate nor runs into the housing.
 */
class ElectricMobTrapCableArmTest {

	private static final String MODEL = "assets/alaindustrial/models/block/electric_mob_trap.json";
	private static final double STEP = 0.005;
	/** Top of the base plate, the one part of the model that reaches the cell edge under the sleeve. */
	private static final double BASE_TOP = 3.0;

	private record Cube(double[] from, double[] to) {
		boolean contains(double x, double y, double z) {
			return from[0] <= x && x <= to[0] && from[1] <= y && y <= to[1] && from[2] <= z && z <= to[2];
		}
	}

	private static final String NUM = "(-?[0-9.]+)";
	private static final String VEC = "\\[\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*\\]";
	private static final Pattern ELEMENT = Pattern.compile("\"from\"\\s*:\\s*" + VEC + "\\s*,\\s*\"to\"\\s*:\\s*"
			+ VEC);

	private static double[] vec(Matcher m, int first) {
		return new double[] {Double.parseDouble(m.group(first)), Double.parseDouble(m.group(first + 1)),
				Double.parseDouble(m.group(first + 2))};
	}

	private static List<Cube> cubes() throws Exception {
		String json;
		try (InputStream in = ElectricMobTrapCableArmTest.class.getClassLoader().getResourceAsStream(MODEL)) {
			assertNotNull(in, MODEL + " must be on the test runtime classpath");
			json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		assertFalse(json.contains("\"rotation\""), "the measurement reads axis-aligned cubes only");
		List<Cube> out = new ArrayList<>();
		Matcher m = ELEMENT.matcher(json);
		while (m.find()) {
			out.add(new Cube(vec(m, 1), vec(m, 4)));
		}
		int declared = json.split("\"from\"", -1).length - 1;
		assertEquals(declared, out.size(), "every element of the model must be parsed");
		return out;
	}

	/** The model point {@code d} pixels in from the edge of {@code side} (0 N, 1 S, 2 W, 3 E). */
	private static double[] point(int side, double across, double y, double d) {
		return switch (side) {
			case 0 -> new double[] {across, y, d};
			case 1 -> new double[] {across, y, 16 - d};
			case 2 -> new double[] {d, y, across};
			default -> new double[] {16 - d, y, across};
		};
	}

	private static boolean inside(List<Cube> cubes, double[] p) {
		for (Cube cube : cubes) {
			if (cube.contains(p[0], p[1], p[2])) {
				return true;
			}
		}
		return false;
	}

	@Test
	void bandRunsOverTheBasePlateAndEndsOnThePlateUnderTheSparkGap() throws Exception {
		List<Cube> cubes = cubes();
		assertEquals(1, ElectricMobTrapCableArm.BANDS.size());
		CableArmReach.Band band = ElectricMobTrapCableArm.BANDS.get(0);
		assertEquals((float) CableBlock.LOW_SLEEVE_BOTTOM, band.bottom());
		assertEquals((float) CableBlock.LOW_SLEEVE_TOP, band.top());
		double inset = 0.05;
		for (int side = 0; side < 4; side++) {
			for (double across = CableBlock.SLEEVE_MIN + inset; across < CableBlock.SLEEVE_MAX; across += 0.5) {
				double hit = Double.POSITIVE_INFINITY;
				for (double d = 0; d < 8; d += STEP) {
					if (inside(cubes, point(side, across, BASE_TOP + 0.5, d))) {
						hit = d;
						break;
					}
				}
				assertEquals(band.depth(), hit, 0.02, "side " + side + " at " + across
						+ ": the band must end on the plate under the spark gap");
				for (double y = BASE_TOP + inset; y < band.top(); y += 0.25) {
					for (double d = inset; d < band.depth(); d += 0.25) {
						assertFalse(inside(cubes, point(side, across, y, d)), "side " + side + ": the continuation"
								+ " at (" + across + ", " + y + ", depth " + d + ") runs into the model");
					}
				}
			}
		}
	}
}
