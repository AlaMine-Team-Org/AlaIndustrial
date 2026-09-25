package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Re-measures the assembled teleporter's model and pins {@link TeleporterCableArm#BANDS} (MOD-672).
 *
 * <p>Rays run from each of the four cell edges inward through the core of the cable sleeve's cross-
 * section (5..11 less a one-pixel rim) at every height of the dropped sleeve (2..8), and stop at the
 * first cube of the model — rotated cubes included, since the octagon's corners are cubes turned about
 * Y. The band's depth must be the deepest of those nearest surfaces: shallower leaves a slit somewhere,
 * deeper runs the continuation into the housing for no reason.
 */
class TeleporterCableArmTest {

	private static final String MODEL = "assets/alaindustrial/models/block/teleporter_formed.json";
	private static final double STEP = 0.005;

	private record Cube(double[] from, double[] to, char axis, double angle, double[] origin) {
		boolean contains(double x, double y, double z) {
			if (angle != 0) {
				double a = Math.toRadians(-angle);
				double c = Math.cos(a);
				double s = Math.sin(a);
				double dx = x - origin[0];
				double dy = y - origin[1];
				double dz = z - origin[2];
				switch (axis) {
					case 'y' -> {
						double nx = dx * c + dz * s;
						dz = -dx * s + dz * c;
						dx = nx;
					}
					case 'x' -> {
						double ny = dy * c - dz * s;
						dz = dy * s + dz * c;
						dy = ny;
					}
					default -> {
						double nx = dx * c - dy * s;
						dy = dx * s + dy * c;
						dx = nx;
					}
				}
				x = dx + origin[0];
				y = dy + origin[1];
				z = dz + origin[2];
			}
			return from[0] <= x && x <= to[0] && from[1] <= y && y <= to[1] && from[2] <= z && z <= to[2];
		}
	}

	private static final String NUM = "(-?[0-9.]+)";
	private static final String VEC = "\\[\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*\\]";
	/** One element's box and optional rotation; the model writes from, to, rotation, faces in that order. */
	private static final Pattern ELEMENT = Pattern.compile("\"from\"\\s*:\\s*" + VEC + "\\s*,\\s*\"to\"\\s*:\\s*"
			+ VEC + "(\\s*,\\s*\"rotation\"\\s*:\\s*\\{\\s*\"origin\"\\s*:\\s*" + VEC
			+ "\\s*,\\s*\"axis\"\\s*:\\s*\"([xyz])\"\\s*,\\s*\"angle\"\\s*:\\s*" + NUM + "\\s*\\})?");

	private static double[] vec(Matcher m, int first) {
		return new double[] {Double.parseDouble(m.group(first)), Double.parseDouble(m.group(first + 1)),
				Double.parseDouble(m.group(first + 2))};
	}

	private static List<Cube> cubes() throws Exception {
		String json;
		try (InputStream in = TeleporterCableArmTest.class.getClassLoader().getResourceAsStream(MODEL)) {
			assertNotNull(in, MODEL + " must be on the test runtime classpath");
			json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		List<Cube> out = new ArrayList<>();
		Matcher m = ELEMENT.matcher(json);
		while (m.find()) {
			boolean rotated = m.group(7) != null;
			out.add(new Cube(vec(m, 1), vec(m, 4), rotated ? m.group(11).charAt(0) : 'y',
					rotated ? Double.parseDouble(m.group(12)) : 0, rotated ? vec(m, 8) : new double[] {8, 8, 8}));
		}
		int declared = json.split("\"from\"", -1).length - 1;
		assertEquals(declared, out.size(), "every element of the model must be parsed, rotated ones included");
		return out;
	}

	/** Depth from the cell edge of {@code side} (0 N, 1 S, 2 W, 3 E) to the first cube, in pixels. */
	private static double nearest(List<Cube> cubes, int side, double across, double y) {
		for (double d = 0; d < 8; d += STEP) {
			double x;
			double z;
			switch (side) {
				case 0 -> { x = across; z = d; }
				case 1 -> { x = across; z = 16 - d; }
				case 2 -> { x = d; z = across; }
				default -> { x = 16 - d; z = across; }
			}
			for (Cube cube : cubes) {
				if (cube.contains(x, y, z)) {
					return d;
				}
			}
		}
		return Double.POSITIVE_INFINITY;
	}

	@Test
	void bandCoversTheDroppedSleeveAndEndsOnTheDeepestNearestSurface() throws Exception {
		List<Cube> cubes = cubes();
		double deepest = 0;
		for (int side = 0; side < 4; side++) {
			for (double y = 2.25; y < 8; y += 0.5) {
				for (double across = 6.25; across < 10; across += 0.5) {
					double d = nearest(cubes, side, across, y);
					assertTrue(d < 8, "side " + side + " at (" + across + ", " + y
							+ ") meets no housing within half a cell — the continuation would have nowhere to end");
					deepest = Math.max(deepest, d);
				}
			}
		}
		assertEquals(1, TeleporterCableArm.BANDS.size());
		CableArmReach.Band band = TeleporterCableArm.BANDS.get(0);
		assertEquals(2f, band.bottom());
		assertEquals(8f, band.top());
		assertEquals(deepest, band.depth(), 0.02, "the band must end on the deepest nearest surface");
	}
}
