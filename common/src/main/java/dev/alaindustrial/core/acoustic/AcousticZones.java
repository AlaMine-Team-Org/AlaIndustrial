package dev.alaindustrial.core.acoustic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The geometry of the sound absorber (MOD-804): absorbers that touch by a face form a group, a group's zone
 * is its bounding box grown by its tier's radius on every side, and a sound inside several zones takes the
 * strongest single one — zones never add up (owner decision 2026-10-13).
 *
 * <p>Minecraft-free: cells are plain integer triples, so the rules are pinned on the L1 lane
 * ({@code AcousticZonesTest}); the client registry feeds it the absorber positions it found.
 */
public final class AcousticZones {
	private AcousticZones() {
	}

	/** One absorber block. */
	public record Cell(int x, int y, int z) {
		Cell step(int dx, int dy, int dz) {
			return new Cell(x + dx, y + dy, z + dz);
		}
	}

	/**
	 * One group and its zone.
	 *
	 * @param size the number of absorbers in the group (uncapped; {@link AbsorberTier#GROUP_CAP} is a readout cap)
	 * @param tier the group's tier
	 * @param minX the group's bounding box, in block coordinates, inclusive
	 */
	public record Zone(int size, AbsorberTier tier, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

		/** Whether the point lies in the zone: the box from the min corner to max + 1, grown by the radius. */
		public boolean contains(double x, double y, double z) {
			int r = tier.radius();
			return x >= minX - r && x <= maxX + 1 + r
					&& y >= minY - r && y <= maxY + 1 + r
					&& z >= minZ - r && z <= maxZ + 1 + r;
		}

		/** Whether the block cell belongs to the group's bounding box (the HUD's lookup of a focused block). */
		public boolean boxContains(int x, int y, int z) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		}
	}

	private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/** Split the absorbers into face-connected groups, each with its tier and bounding box. */
	public static List<Zone> groups(Collection<Cell> cells) {
		return split(cells, null);
	}

	/** Every absorber mapped to the group it belongs to. */
	public static Map<Cell, Zone> membership(Collection<Cell> cells) {
		Map<Cell, Zone> out = new HashMap<>();
		split(cells, out);
		return out;
	}

	private static List<Zone> split(Collection<Cell> cells, @Nullable Map<Cell, Zone> members) {
		Set<Cell> left = new HashSet<>(cells);
		List<Zone> out = new ArrayList<>();
		ArrayDeque<Cell> frontier = new ArrayDeque<>();
		while (!left.isEmpty()) {
			Cell start = left.iterator().next();
			left.remove(start);
			frontier.add(start);
			int size = 0;
			int minX = start.x();
			int minY = start.y();
			int minZ = start.z();
			int maxX = minX;
			int maxY = minY;
			int maxZ = minZ;
			List<Cell> group = new ArrayList<>();
			while (!frontier.isEmpty()) {
				Cell cell = frontier.poll();
				group.add(cell);
				size++;
				minX = Math.min(minX, cell.x());
				minY = Math.min(minY, cell.y());
				minZ = Math.min(minZ, cell.z());
				maxX = Math.max(maxX, cell.x());
				maxY = Math.max(maxY, cell.y());
				maxZ = Math.max(maxZ, cell.z());
				for (int[] s : STEPS) {
					Cell next = cell.step(s[0], s[1], s[2]);
					if (left.remove(next)) {
						frontier.add(next);
					}
				}
			}
			Zone zone = new Zone(size, AbsorberTier.forGroupSize(size), minX, minY, minZ, maxX, maxY, maxZ);
			out.add(zone);
			if (members != null) {
				for (Cell cell : group) {
					members.put(cell, zone);
				}
			}
		}
		return out;
	}

	/**
	 * The volume multiplier at a point: the strongest zone that contains it, or 1 when none does. Never
	 * the product of two zones — a second group nearby does not make a sound quieter than the stronger one.
	 */
	public static float factorAt(List<Zone> zones, double x, double y, double z) {
		float factor = 1.0f;
		for (Zone zone : zones) {
			if (zone.contains(x, y, z)) {
				factor = Math.min(factor, zone.tier().volumeFactor());
			}
		}
		return factor;
	}
}
