package dev.alaindustrial.worldgen;

/**
 * The Minecraft-free core of {@link OilGeyserFeature} (MOD-265): which cells of a geyser are oil,
 * which are the seal around it, and how many oil sources the whole deposit is worth.
 *
 * <p>Extracted for the same reason {@link OilLakeShape} was, and for one more. The lake had an
 * MC-free core from the start, so its volume could always be measured at L1; the geyser had none,
 * and the number that reached {@code docs/PERFORMANCE.md} was therefore produced by a second,
 * hand-written copy of this arithmetic. That copy was wrong in two ways at once — it compared the
 * dome against {@code distance < radius²} where the feature uses {@code <=}, and it forgot the shaft
 * and the spout entirely — and nothing could have caught it, because there was no shared oracle to
 * disagree with. The published figure was smaller than the dome alone.
 *
 * <p>So the rule this class exists to enforce: <b>the feature and any measurement of it must call
 * the same methods.</b> A test or a validator that re-derives these formulas is checking its own
 * copy, not the game.
 */
public final class OilGeyserShape {

	private OilGeyserShape() {
	}

	/**
	 * Whether a dome cell offset from the centre holds an oil source.
	 *
	 * <p>The comparison is non-strict on purpose: the radius is the outermost shell of oil, not the
	 * first shell of seal. One off-by-one here is 78 blocks at radius 11.
	 */
	public static boolean isDomeSource(int radius, int dx, int dy, int dz) {
		return dx * dx + dy * dy + dz * dz <= radius * radius;
	}

	/**
	 * Whether a dome cell is a candidate for the seal — the one-cell layer wrapped around the oil.
	 * Whether it actually becomes stone depends on what is already there, which only the world knows.
	 */
	public static boolean isDomeShell(int radius, int dx, int dy, int dz) {
		int distance = dx * dx + dy * dy + dz * dz;
		return distance > radius * radius && distance <= (radius + 1) * (radius + 1);
	}

	/** Half-extent of the cube the dome pass has to walk: the seal reaches one cell past the oil. */
	public static int domeScanRadius(int radius) {
		return radius + 1;
	}

	/** Oil sources in a dome of the given radius, counted rather than approximated by its volume. */
	public static int domeSourceCount(int radius) {
		int scan = domeScanRadius(radius);
		int count = 0;
		for (int dx = -scan; dx <= scan; dx++) {
			for (int dz = -scan; dz <= scan; dz++) {
				for (int dy = -scan; dy <= scan; dy++) {
					if (isDomeSource(radius, dx, dy, dz)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	/**
	 * Lowest level of the shaft. It starts one cell <em>inside</em> the dome so the column is
	 * continuous with the oil below it rather than resting on top of it — which is also why two of
	 * its cells are already dome oil, see {@link #DOME_SHAFT_OVERLAP}.
	 */
	public static int shaftFromY(int domeCenterY, int domeRadius) {
		return domeCenterY + domeRadius - 1;
	}

	/** Highest level of the shaft: the last cell below the terrain, where the spout takes over. */
	public static int shaftToY(int surfaceY) {
		return surfaceY - 1;
	}

	/**
	 * Cells the shaft pass writes, terrain included. Derived from the two bounds rather than from a
	 * formula of its own, so it cannot drift from where the feature actually starts and stops.
	 */
	public static int shaftLength(int surfaceY, int domeCenterY, int domeRadius) {
		return shaftToY(surfaceY) - shaftFromY(domeCenterY, domeRadius) + 1;
	}

	/**
	 * Cells written by both the dome pass and the shaft pass, and therefore counted twice by a naive
	 * sum. The shaft begins at {@code domeCenterY + radius - 1}; the dome's own column reaches up to
	 * {@code domeCenterY + radius}. That is exactly two levels of overlap, for every radius.
	 */
	public static final int DOME_SHAFT_OVERLAP = 2;

	/**
	 * Distinct oil source blocks in a whole geyser — dome plus shaft plus spout, minus the overlap.
	 *
	 * <p>This is the geometric figure: the number the feature would write into an empty column. In a
	 * real world it is an upper bound, because {@code canReplace} and the build-height limit veto
	 * individual cells. It also depends on {@code surfaceY}, which is terrain, not configuration —
	 * a published number has to name the surface level it assumed.
	 */
	public static int sourceBlockCount(int surfaceY, int domeCenterY, int domeRadius, int spoutHeight) {
		return domeSourceCount(domeRadius)
				+ shaftLength(surfaceY, domeCenterY, domeRadius) - DOME_SHAFT_OVERLAP
				+ spoutHeight;
	}
}
