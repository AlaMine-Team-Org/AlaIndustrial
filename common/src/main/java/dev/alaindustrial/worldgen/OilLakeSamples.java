package dev.alaindustrial.worldgen;

/**
 * The Minecraft-free core of {@link OilLakeFilter} (MOD-238, generalised in MOD-248): which
 * columns/levels of a candidate oil feature are probed for a protected structure, expressed as plain
 * integer offsets around the placement origin.
 *
 * <p>Extracted so the sampling geometry — the part that is actually easy to get wrong — is covered
 * by L1 unit tests. The L1 suite runs without the Minecraft jar on its classpath, so anything with
 * a Minecraft type in its signature cannot be linked there; this class deliberately has none.
 *
 * <p><b>Where the numbers come from.</b> The footprint is no longer one fixed box. Until MOD-248 the
 * only oil feature was {@code minecraft:lake}, whose {@code place} does
 * {@code origin.offset(-8, -4, -8)} and writes a 16 × 8 × 16 box; now each placed feature declares
 * its own extent, because a surface puddle, a deep reservoir and a geyser shaft running three
 * hundred blocks down have nothing in common but the origin. Probing the origin column alone (what
 * PneumaticCraft: Repressurized's oil-lake filter does) leaves most of any of those footprints
 * hanging over a structure the filter was supposed to protect, which is exactly the case that
 * matters: the features run at step {@code LAKES} (index 1) but write into a 3 × 3 chunk area, so
 * they can overwrite a structure in an already-decorated neighbour chunk. The four footprint corners
 * are probed as well.
 *
 * <p><b>Why the vertical sampling is a step and not two endpoints.</b> Structure pieces are matched
 * by bounding box, so a piece band that a feature only clips is invisible at the placement level.
 * For a lake, bottom/middle/top was enough — the box is eight blocks tall. A geyser spans the whole
 * world height, and three levels over three hundred blocks would sail straight past an Ancient City.
 */
public final class OilLakeSamples {

	/** Probe callback: is the protected-structure set present at this absolute block position? */
	@FunctionalInterface
	public interface PositionProbe {
		boolean blocked(int x, int y, int z);
	}

	/** X offsets of the probed columns as multiples of the footprint radius: origin plus corners. */
	private static final int[] X_UNITS = { 0, -1, -1, 1, 1 };

	/** Z offsets of the probed columns, index-aligned with {@link #X_UNITS}. */
	private static final int[] Z_UNITS = { 0, -1, 1, -1, 1 };

	private OilLakeSamples() {
	}

	/**
	 * True when any probed position of the footprint reports a protected structure. Short circuits on
	 * the first hit — the caller only needs to know whether to reject the placement.
	 *
	 * @param horizontalRadius half-extent of the footprint on X and Z
	 * @param minYOffset       lowest probed level, relative to the origin (negative for downwards)
	 * @param maxYOffset       highest probed level, relative to the origin; always probed
	 * @param yStep            spacing of the intermediate levels; values below one are treated as one
	 */
	public static boolean anyBlocked(int originX, int originY, int originZ, int horizontalRadius,
			int minYOffset, int maxYOffset, int yStep, PositionProbe probe) {
		int[] levels = levels(minYOffset, maxYOffset, yStep);
		for (int column = 0; column < X_UNITS.length; column++) {
			int x = originX + X_UNITS[column] * horizontalRadius;
			int z = originZ + Z_UNITS[column] * horizontalRadius;
			for (int dy : levels) {
				if (probe.blocked(x, originY + dy, z)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The Y offsets probed in each column, ascending and without duplicates: the walk from the bottom
	 * of the span in {@code yStep} increments, plus two levels that are always included whatever the
	 * step works out to —
	 *
	 * <ul>
	 *   <li>the TOP of the span, because the walk lands on it only when the span divides evenly;</li>
	 *   <li>the placement origin's own level, which is the single most likely level to matter and
	 *       which a coarse step can otherwise stride straight over.</li>
	 * </ul>
	 */
	public static int[] levels(int minYOffset, int maxYOffset, int yStep) {
		int step = Math.max(1, yStep);
		int low = Math.min(minYOffset, maxYOffset);
		int high = Math.max(minYOffset, maxYOffset);
		java.util.TreeSet<Integer> offsets = new java.util.TreeSet<>();
		for (int dy = low; dy < high; dy += step) {
			offsets.add(dy);
		}
		offsets.add(high);
		if (low <= 0 && 0 <= high) {
			offsets.add(0);
		}
		int[] result = new int[offsets.size()];
		int i = 0;
		for (int offset : offsets) {
			result[i++] = offset;
		}
		return result;
	}
}
