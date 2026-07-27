package dev.alaindustrial.worldgen;

import java.util.function.DoubleSupplier;

/**
 * The Minecraft-free core of {@link OilLakeFeature} (MOD-248): which cells of the lake bounding box
 * are hollowed out, expressed as a flat boolean grid over plain integers.
 *
 * <p>Extracted for the same reason {@link OilLakeSamples} was — the L1 suite runs without the
 * Minecraft jar on its classpath, so anything with a Minecraft type in its signature cannot be
 * linked there, and the blob geometry is the part of the feature that is actually easy to get wrong
 * (an off-by-one in the ellipsoid bounds writes outside the grid, or, worse, produces a lake that
 * touches the edge of the box and therefore never gets a barrier shell).
 *
 * <p>The shape is a union of randomly placed ellipsoids inside the box, which is what gives a lake
 * an irregular outline instead of a stamped sphere. Every ellipsoid is constrained to fit strictly
 * inside the grid with a one-cell margin, so the outermost layer of the box is always empty and a
 * filled cell always has all six neighbours in range — the barrier pass in the feature relies on
 * that and does no bounds checking.
 */
public final class OilLakeShape {

	private OilLakeShape() {
	}

	/** Width (and depth) of the grid in cells for a given horizontal half-extent. */
	public static int width(int horizontalRadius) {
		return horizontalRadius * 2;
	}

	/** Height of the grid in cells for a given vertical half-extent. */
	public static int height(int verticalRadius) {
		return verticalRadius * 2;
	}

	/** Flat index of a cell; the caller iterates the three axes itself. */
	public static int index(int horizontalRadius, int verticalRadius, int x, int y, int z) {
		return (x * width(horizontalRadius) + z) * height(verticalRadius) + y;
	}

	/**
	 * Whether a grid level holds fluid rather than the air pocket above it. The lower half of the box
	 * is oil and the upper half is the cavity — the same split vanilla lakes use, and the reason an
	 * underground deposit reads as a pool in a cave instead of a solid blob of liquid.
	 */
	public static boolean isFluidLevel(int verticalRadius, int y) {
		return y < verticalRadius;
	}

	/**
	 * Builds the hollow-cell grid. {@code nextDouble} supplies uniform values in {@code [0, 1)} — the
	 * caller passes the world's {@code RandomSource}, the L1 tests pass a deterministic stub.
	 */
	public static boolean[] build(int horizontalRadius, int verticalRadius, int blobCount,
			DoubleSupplier nextDouble) {
		int width = width(horizontalRadius);
		int height = height(verticalRadius);
		boolean[] filled = new boolean[width * width * height];
		for (int blob = 0; blob < blobCount; blob++) {
			double radiusX = between(nextDouble, horizontalRadius * 0.35, horizontalRadius - 1.0);
			double radiusZ = between(nextDouble, horizontalRadius * 0.35, horizontalRadius - 1.0);
			double radiusY = between(nextDouble, verticalRadius * 0.35, verticalRadius - 1.0);
			double centerX = between(nextDouble, radiusX + 1.0, width - radiusX - 1.0);
			double centerZ = between(nextDouble, radiusZ + 1.0, width - radiusZ - 1.0);
			double centerY = between(nextDouble, radiusY + 1.0, height - radiusY - 1.0);
			for (int x = 1; x < width - 1; x++) {
				double dx = (x - centerX) / radiusX;
				for (int z = 1; z < width - 1; z++) {
					double dz = (z - centerZ) / radiusZ;
					for (int y = 1; y < height - 1; y++) {
						double dy = (y - centerY) / radiusY;
						if (dx * dx + dy * dy + dz * dz < 1.0) {
							filled[index(horizontalRadius, verticalRadius, x, y, z)] = true;
						}
					}
				}
			}
		}
		return filled;
	}

	/**
	 * Uniform value in {@code [min, max)}, collapsing to {@code min} when the range is empty. The
	 * degenerate case is reachable at the smallest allowed radii and must not consume a different
	 * number of random values than the normal path, or two lakes with the same seed would diverge.
	 */
	private static double between(DoubleSupplier nextDouble, double min, double max) {
		double roll = nextDouble.getAsDouble();
		return max <= min ? min : min + roll * (max - min);
	}
}
