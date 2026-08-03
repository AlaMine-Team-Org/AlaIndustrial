package dev.alaindustrial.core.energy;

/**
 * The geometric ordering rule for block positions, as pure arithmetic (no Minecraft types).
 *
 * <p><b>Why it lives in its own Minecraft-free class.</b> This rule decides who gets energy first
 * when two consumers are equally far from a source, and it was already written WRONG once: the first
 * attempt sorted by the packed {@code BlockPos.asLong()}, which is not monotone in the coordinates at
 * all. That defect shipped into a commit and was caught by a human review, not by a test — because
 * there was no test that could catch it. A rule this load-bearing has to be checkable at L1, and
 * anything taking a {@code BlockPos} cannot be (the L1 suite runs without the Minecraft jar).
 *
 * <p><b>The rule.</b> Compare x, then y, then z, each as a plain signed {@code int}. The property that
 * matters is <b>translation invariance</b>: moving a whole build by a constant vector must not change
 * the relative order of its blocks — otherwise "the same layout behaves the same wherever it is built"
 * is false, and a base straddling the origin behaves differently from the same base elsewhere.
 *
 * <p><b>Why not {@code asLong}.</b> It packs the coordinates into bit FIELDS
 * ({@code (x & mask) << 38 | (z & mask) << 12 | (y & mask)}), and masking a negative number keeps its
 * two's-complement bits, so inside its field a negative coordinate sorts as a large positive value.
 * Only x is monotone, and only because its field happens to reach the long's sign bit. Worked example:
 * {@code (0,64,-1)} sorts after {@code (0,64,0)}, yet shifting both by +1 on z puts {@code (0,64,0)}
 * before {@code (0,64,1)} — a plain translation flipping the order. The same flip happens across
 * {@code y = 0}, which sits in the middle of the normal build range.
 */
public final class PosOrder {
	private PosOrder() {
	}

	/**
	 * Total order over positions: x, then y, then z.
	 *
	 * <p>Uses {@link Integer#compare} rather than subtraction — subtraction overflows for coordinates
	 * far apart and would silently invert the result.
	 *
	 * @return negative, zero or positive, in the usual {@link java.util.Comparator} sense
	 */
	public static int compare(int x1, int y1, int z1, int x2, int y2, int z2) {
		int byX = Integer.compare(x1, x2);
		if (byX != 0) {
			return byX;
		}
		int byY = Integer.compare(y1, y2);
		if (byY != 0) {
			return byY;
		}
		return Integer.compare(z1, z2);
	}
}
