package dev.alaindustrial.core.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The crater a reactor leaves when its blast is not contained (MOD-736), as arithmetic with no Minecraft type in
 * sight — how wide it is, how much lava settles in it, and where each wave of the cascade goes off.
 *
 * <p><b>Why a cascade and not one bigger explosion.</b> A vanilla explosion traces 1352 rays; past about ten
 * blocks from the centre neighbouring rays are more than a block apart, so a single large blast leaves a hedgehog
 * of spikes rather than a bowl, and raising its power only makes the spikes longer. The crater is therefore carved
 * by many small explosions, each held to the radius where its own rays are still dense.
 *
 * <p><b>A wave goes off only inside what the waves before it destroyed — that is the land-claim rule.</b> Every
 * wave is a real {@code level.explode}, so a protection mod can refuse it; a refused wave destroys nothing, which
 * leaves the next ring nothing to grow from, and the crater stops at the claim's border. This class never says
 * where a wave goes on its own: it offers the cell nearest the planned point among the cells already destroyed.
 *
 * <p>Coordinates are offsets from the epicentre, so the planner never sees a world position.
 */
public final class CraterWaves {

	/** A cell, as an offset from the epicentre. */
	public record Cell(int x, int y, int z) {

		long distSqr(Cell other) {
			long dx = (long) x - other.x;
			long dy = (long) y - other.y;
			long dz = (long) z - other.z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	/** The point a wave is planned at, and the lattice node it was planned from. */
	public record Target(Cell at, Cell node) {
	}

	private CraterWaves() {
	}

	/**
	 * Radius of the crater, in blocks.
	 *
	 * <p>Grows with the rods, like the blast power does, and stops at a ceiling — a hundred-rod station and a
	 * two-hundred-rod one leave the same hole. Carried per TEN rods so the configuration stays integer.
	 *
	 * @param rods        rods burning when the countdown ran out
	 * @param base        radius of a core with no rods
	 * @param perTenRods  radius added per ten rods
	 * @param cap         hard ceiling; {@code <= 0} means none
	 */
	public static int radius(int rods, int base, int perTenRods, int cap) {
		int raw = Math.max(0, base) + Math.max(0, perTenRods) * Math.max(0, rods) / 10;
		return cap <= 0 ? raw : Math.min(raw, cap);
	}

	/**
	 * Lava sources poured into the bottom of a crater of this radius: the contained blast's handful plus a share
	 * per block of radius, so a large crater gets a lake rather than the same six cells a gutted room gets.
	 */
	public static int lavaCells(int baseCells, int perRadius, int radius) {
		return Math.max(0, baseCells) + Math.max(0, perRadius) * Math.max(0, radius);
	}

	/**
	 * The waves of a crater, in the order they go off: the nodes of a cubic lattice of the given spacing that lie
	 * within reach of the crater's radius, nearest first.
	 *
	 * <p>A node is planned only if a wave there stays inside the crater — its distance plus half a spacing is at
	 * most the radius. The epicentre itself is not a wave: it is the primary blast, which has already gone off.
	 * The order is total (distance, then x, y, z), so two runs plan the same crater.
	 *
	 * @param radius   crater radius, blocks
	 * @param spacing  distance between neighbouring waves, blocks; about the reach of one wave
	 */
	public static List<Target> plan(int radius, int spacing) {
		List<Target> targets = new ArrayList<>();
		if (radius <= 0 || spacing <= 0 || 2 * radius < spacing) {
			return targets;
		}
		Cell origin = new Cell(0, 0, 0);
		int reach = radius / spacing + 1;
		long limit2 = (long) (2 * radius - spacing) * (2 * radius - spacing);
		for (int kx = -reach; kx <= reach; kx++) {
			for (int ky = -reach; ky <= reach; ky++) {
				for (int kz = -reach; kz <= reach; kz++) {
					if (kx == 0 && ky == 0 && kz == 0) {
						continue;
					}
					Cell at = new Cell(kx * spacing, ky * spacing, kz * spacing);
					// d + s/2 ≤ r, squared as 4d² ≤ (2r − s)² so everything stays integer.
					if (4 * at.distSqr(origin) > limit2) {
						continue;
					}
					targets.add(new Target(at, new Cell(kx, ky, kz)));
				}
			}
		}
		targets.sort(Comparator.<Target>comparingLong(t -> t.at().distSqr(origin))
				.thenComparingInt(t -> t.at().x())
				.thenComparingInt(t -> t.at().y())
				.thenComparingInt(t -> t.at().z()));
		return targets;
	}

	/**
	 * What the waves so far destroyed, bucketed by the lattice node each cell is closest to — so finding where a
	 * planned wave may go off costs one lookup, not a search through tens of thousands of cells.
	 *
	 * <p>Each bucket keeps the single destroyed cell nearest its node; ties go to the smaller x, then y, then z,
	 * so the choice does not depend on the order the cells were offered in.
	 */
	public static final class Field {

		private final int spacing;
		private final Map<Cell, Cell> best = new HashMap<>();

		public Field(int spacing) {
			this.spacing = Math.max(1, spacing);
		}

		/** Records a destroyed cell, as an offset from the epicentre. */
		public void offer(int x, int y, int z) {
			Cell node = new Cell(node(x), node(y), node(z));
			Cell cell = new Cell(x, y, z);
			Cell centre = new Cell(node.x() * spacing, node.y() * spacing, node.z() * spacing);
			best.merge(node, cell, (kept, fresh) -> closer(centre, kept, fresh));
		}

		/**
		 * Where the wave planned at {@code target} may go off: the destroyed cell nearest it inside its own
		 * bucket, or {@code null} when nothing near it has been destroyed — the crater has not reached it, or
		 * something refused the waves that would have.
		 */
		public Cell centreFor(Target target) {
			return best.get(target.node());
		}

		/**
		 * The lattice index a coordinate rounds to, halves AWAY from zero: rounding them up would put x = −s/2 in
		 * the middle bucket and x = +s/2 in the outer one, and the crater would grow faster eastwards than west.
		 */
		private int node(int v) {
			int away = Math.floorDiv(2 * Math.abs(v) + spacing, 2 * spacing);
			return v < 0 ? -away : away;
		}

		private static Cell closer(Cell centre, Cell a, Cell b) {
			long da = a.distSqr(centre);
			long db = b.distSqr(centre);
			if (da != db) {
				return da < db ? a : b;
			}
			if (a.x() != b.x()) {
				return a.x() < b.x() ? a : b;
			}
			if (a.y() != b.y()) {
				return a.y() < b.y() ? a : b;
			}
			return a.z() <= b.z() ? a : b;
		}
	}
}
