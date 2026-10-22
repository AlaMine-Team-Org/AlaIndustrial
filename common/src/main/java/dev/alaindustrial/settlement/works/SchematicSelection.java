package dev.alaindustrial.settlement.works;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Which missing cells of a building's plan one schematic reply carries (MOD-857, owner's round 2, point 7): every one
 * when they fit the cap, else the {@code cap} nearest to the viewer — the cells the player can see first. The chosen
 * cells keep the plan's own order (bottom layer first), so a reply reads the same whatever the viewer's distance.
 *
 * <p>Minecraft-free on purpose: positions come in as plain coordinates.
 */
public final class SchematicSelection {

	private SchematicSelection() {
	}

	/**
	 * The indices to send, ascending.
	 *
	 * @param x      the cells' x, one per candidate
	 * @param y      the cells' y
	 * @param z      the cells' z
	 * @param viewX  the viewer's x
	 * @param viewY  the viewer's y
	 * @param viewZ  the viewer's z
	 * @param cap    the most cells one reply carries; 0 or less — none
	 * @return indices into the candidate arrays, ascending, at most {@code cap}
	 */
	public static int[] pick(int[] x, int[] y, int[] z, double viewX, double viewY, double viewZ, int cap) {
		int count = x.length;
		if (y.length != count || z.length != count) {
			throw new IllegalArgumentException("coordinate arrays differ in length");
		}
		if (cap <= 0 || count == 0) {
			return new int[0];
		}
		if (count <= cap) {
			return IntStream.range(0, count).toArray();
		}
		double[] distance = new double[count];
		for (int i = 0; i < count; i++) {
			double dx = x[i] + 0.5 - viewX;
			double dy = y[i] + 0.5 - viewY;
			double dz = z[i] + 0.5 - viewZ;
			distance[i] = dx * dx + dy * dy + dz * dz;
		}
		int[] nearest = IntStream.range(0, count).boxed()
				.sorted(Comparator.<Integer>comparingDouble(i -> distance[i]).thenComparingInt(i -> i))
				.limit(cap).mapToInt(Integer::intValue).toArray();
		Arrays.sort(nearest);
		return nearest;
	}
}
