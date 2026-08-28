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
	 * Share of the basin boundary that may open into a cavity before the site is refused (MOD-526).
	 *
	 * <p>{@code OilLakeFeature} deliberately lets an underground gap through and seals it with the
	 * barrier pass, because refusing every clipped cave would keep the deep tier from generating at
	 * all. That concession used to be unbounded, and unbounded is what a player saw: a blob whose
	 * lower half fell inside a cave hall was accepted, the barrier pass wrapped it in one layer of
	 * stone, and the deposit hung in the air as a bowl with nothing behind its rim.
	 *
	 * <p>The number is measured, not chosen. Over 723 sites of a real world (seed 526, 29 tiles of
	 * 256 chunks), 601 of which today's code accepts, the share is lopsided rather than spread: 86 %
	 * of the deposits keep it under a tenth, and the tail past a quarter is where the deposit sits
	 * inside a cavity instead of in the rock beside it. The limit costs 7.5 % of the mine-depth layer
	 * and 5.8 % of the deep one — written down, because a frequency the docs promise is a number
	 * somebody will check ({@code docs/blocks/materials/oil.md}).
	 */
	public static final int MAX_OPEN_BASIN_PERCENT = 25;

	/**
	 * Whether so much of the basin boundary opens into a cavity that the deposit would hang in it
	 * rather than sit in rock.
	 *
	 * <p>A basin with no boundary at all (a deposit that reaches no fluid level — geometry allows it
	 * at the smallest vertical radius) cannot hang: there is nothing to hold up.
	 */
	public static boolean basinHangsInTheOpen(int openCells, int boundaryCells) {
		return boundaryCells > 0 && openCells * 100 > boundaryCells * MAX_OPEN_BASIN_PERCENT;
	}

	/** The six neighbours a boundary cell is tested against, as plain offsets. */
	private static final int[][] NEIGHBOURS = {
			{ -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };

	/**
	 * Whether an empty grid cell touches a hollow one — the definition of a boundary cell, shared by
	 * both of the feature's boundary walks and by the basin measurement, so that all three count the
	 * same cells.
	 *
	 * <p>{@link #build} guarantees the outermost layer of the grid is never filled, so a hollow cell
	 * always has all six neighbours in range; the bounds test here only walks the grid edges.
	 */
	public static boolean bordersHollow(boolean[] filled, int horizontalRadius, int verticalRadius,
			int width, int height, int x, int y, int z) {
		for (int[] offset : NEIGHBOURS) {
			int nx = x + offset[0];
			int ny = y + offset[1];
			int nz = z + offset[2];
			if (nx < 0 || nx >= width || nz < 0 || nz >= width || ny < 0 || ny >= height) {
				continue;
			}
			if (filled[index(horizontalRadius, verticalRadius, nx, ny, nz)]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * How many boundary cells the fluid half of a deposit has — the denominator
	 * {@link #basinHangsInTheOpen} judges against, exposed so an L1 measurement can pin what a
	 * percentage means in blocks for each shipped tier.
	 */
	public static int basinBoundaryCells(boolean[] filled, int horizontalRadius, int verticalRadius) {
		int width = width(horizontalRadius);
		int height = height(verticalRadius);
		int count = 0;
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				for (int y = 0; y < verticalRadius; y++) {
					if (!filled[index(horizontalRadius, verticalRadius, x, y, z)]
							&& bordersHollow(filled, horizontalRadius, verticalRadius, width, height,
									x, y, z)) {
						count++;
					}
				}
			}
		}
		return count;
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
