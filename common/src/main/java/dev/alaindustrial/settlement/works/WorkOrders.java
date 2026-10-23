package dev.alaindustrial.settlement.works;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The orders of a town (round 2 of 0.1.219, owner's answers Q1–Q3): an order is one building with work open on it —
 * its clearing, its road and its building, in that order — and the town's owner sets which order goes first. The
 * drones take the jobs of the first order nobody works on yet ({@link WorkQueue#takeNext(String, java.util.Set,
 * java.util.function.IntUnaryOperator)}), one drone on one building, so N nests run up to N orders side by side.
 *
 * <p><b>Derived, not stored.</b> Which buildings are orders follows from the queue alone: every building with an open
 * job. The town keeps only the owner's order of them ({@code explicit}); a building with open jobs that the owner never
 * placed — a repair the town raised by itself, the hall's jobs of a save from 0.1.218 — joins at the end by its oldest
 * open job, and a finished order leaves by itself. Nothing «jumps» to the next building once one is done.
 *
 * <p>Minecraft-free on purpose: the L1 lane reads it.
 */
public final class WorkOrders {

	/** The rank of a building that is no order: after every order. */
	public static final int NO_ORDER = Integer.MAX_VALUE;

	private WorkOrders() {
	}

	/**
	 * The town's orders, first first: the buildings of {@code explicit} that still have an open job, in that order,
	 * then every other building with an open job, by its oldest open job.
	 */
	public static List<Integer> effective(List<Integer> explicit, List<WorkJob> jobs) {
		Set<Integer> open = new LinkedHashSet<>();
		for (WorkJob job : jobs) {
			if (job.status().open()) {
				open.add(job.building());
			}
		}
		List<Integer> out = new ArrayList<>();
		for (int building : explicit) {
			if (open.contains(building) && !out.contains(building)) {
				out.add(building);
			}
		}
		// The jobs are in number order, so the first open job of a building is its oldest.
		for (int building : open) {
			if (!out.contains(building)) {
				out.add(building);
			}
		}
		return out;
	}

	/** The place of {@code building} in {@code orders}, or {@link #NO_ORDER}. */
	public static int rank(List<Integer> orders, int building) {
		int at = orders.indexOf(building);
		return at < 0 ? NO_ORDER : at;
	}

	/**
	 * {@code orders} with {@code building} moved {@code delta} places (negative — earlier), kept within the list; the
	 * list unchanged when the building is no order.
	 */
	public static List<Integer> move(List<Integer> orders, int building, int delta) {
		List<Integer> out = new ArrayList<>(orders);
		int at = out.indexOf(building);
		if (at < 0 || delta == 0) {
			return out;
		}
		int to = Math.max(0, Math.min(out.size() - 1, at + delta));
		out.remove(at);
		out.add(to, building);
		return out;
	}
}
