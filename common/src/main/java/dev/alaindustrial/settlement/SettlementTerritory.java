package dev.alaindustrial.settlement;

import java.util.Optional;

/**
 * The shape of a settlement's territory (MOD-831, MOD-848): a rectangle of block columns, from the bottom of the world
 * to the top.
 *
 * <p><b>The rule</b> ({@link #area}). Every town holds the square of its tier's radius around its core, {@code 2r + 1}
 * blocks on a side. A town that a structure built (epic decision R59) also holds its own buildings: the footprint of
 * every piece of the structure, grown by a margin on every side ({@code SettlementConfig.settlementTerritoryMargin})
 * and by as much again as the tier's radius has grown past the first tier's. The territory is the smallest rectangle
 * around both — the envelope, not the union, so it stays one rectangle the border shell can draw and a player can
 * pace out. Both parts only grow with the tier, so the territory never shrinks when the town moves up a tier; and
 * the footprint part grows on every side, so a higher tier widens the town even where its buildings, not the square,
 * set the edge.
 *
 * <p><b>Why a column and not a cube or a disc.</b> The town's laboratory lies under its town hall (epic MOD-824,
 * decision R29) and the drone station sits on its roof (R21), so a height limit would cut the town at one end or
 * the other. A rectangle rather than a disc because every other zone of the mod is one — the mob repeller's field,
 * the zone preview — and because "r blocks along an axis" is the number a player can pace out.
 *
 * <p>Minecraft-free: plain coordinates in, so the L1 lane checks the edges directly.
 */
public final class SettlementTerritory {

	private SettlementTerritory() {
	}

	/**
	 * A rectangle of block columns, bounds inclusive: the column at ({@code minX}, {@code minZ}) and the one at
	 * ({@code maxX}, {@code maxZ}) both belong to it. Every operation saturates at the ends of the int range rather
	 * than wrapping, so a town near the world border cannot turn into one on the far side.
	 */
	public record Area(int minX, int minZ, int maxX, int maxZ) {

		public Area {
			if (minX > maxX || minZ > maxZ) {
				throw new IllegalArgumentException("an empty area: " + minX + ".." + maxX + " x " + minZ + ".." + maxZ);
			}
		}

		/** The square of {@code radius} (at least 0) around the column ({@code x}, {@code z}). */
		public static Area square(int x, int z, int radius) {
			if (radius < 0) {
				throw new IllegalArgumentException("a negative radius: " + radius);
			}
			return new Area(clamp((long) x - radius), clamp((long) z - radius), clamp((long) x + radius),
					clamp((long) z + radius));
		}

		/** Whether the column ({@code x}, {@code z}) belongs to this area. */
		public boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		/** Whether every column of {@code other} belongs to this area. */
		public boolean contains(Area other) {
			return other.minX >= minX && other.maxX <= maxX && other.minZ >= minZ && other.maxZ <= maxZ;
		}

		/** This area grown by {@code by} (at least 0) columns on every side. */
		public Area grow(int by) {
			if (by < 0) {
				throw new IllegalArgumentException("an area does not shrink: " + by);
			}
			return new Area(clamp((long) minX - by), clamp((long) minZ - by), clamp((long) maxX + by),
					clamp((long) maxZ + by));
		}

		/** The smallest area holding both this one and {@code other}. */
		public Area envelope(Area other) {
			return new Area(Math.min(minX, other.minX), Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
					Math.max(maxZ, other.maxZ));
		}

		/** Columns along X, saturated at {@link Integer#MAX_VALUE}. */
		public int sizeX() {
			return clamp((long) maxX - minX + 1);
		}

		/** Columns along Z, saturated at {@link Integer#MAX_VALUE}. */
		public int sizeZ() {
			return clamp((long) maxZ - minZ + 1);
		}

		/**
		 * How many columns lie between ({@code x}, {@code z}) and the nearest edge of this area, that edge included: a
		 * column on the edge has a reach of 0. Every column within that many blocks along both axes belongs to the
		 * area; a column outside has a negative reach.
		 */
		public int reach(int x, int z) {
			long alongX = Math.min((long) x - minX, (long) maxX - x);
			long alongZ = Math.min((long) z - minZ, (long) maxZ - z);
			return clamp(Math.min(alongX, alongZ));
		}
	}

	/** Whether the block at ({@code x}, {@code z}) lies within {@code radius} of the core at {@code coreX, coreZ}. */
	public static boolean contains(int coreX, int coreZ, int radius, int x, int z) {
		if (radius < 0) {
			return false;
		}
		// long arithmetic: a core near the world border and a point on the far side must not overflow.
		return Math.abs((long) x - coreX) <= radius && Math.abs((long) z - coreZ) <= radius;
	}

	/**
	 * The territory of a town whose core stands at ({@code coreX}, {@code coreZ}) — the rule of the class comment.
	 *
	 * @param radius          the tier's radius: the square around the core every town holds
	 * @param footprint       the footprint of the town's buildings, for a town a structure built; empty for any other
	 *                        (a core of the demo stand, of a command, of an older save)
	 * @param footprintGrowth columns the footprint grows by on every side: the margin plus the tier's growth
	 */
	public static Area area(int coreX, int coreZ, int radius, Optional<Area> footprint, int footprintGrowth) {
		Area square = Area.square(coreX, coreZ, Math.max(0, radius));
		return footprint.map(town -> town.grow(Math.max(0, footprintGrowth)).envelope(square)).orElse(square);
	}

	/**
	 * The territory of a town of {@code tier} — {@link #area} with the tier's radius and, for its footprint, the margin
	 * of {@link SettlementConfig#settlementTerritoryMargin} plus the tier's growth ({@link SettlementTier#growth()}).
	 */
	public static Area of(int coreX, int coreZ, SettlementTier tier, Optional<Area> footprint) {
		return area(coreX, coreZ, tier.radius(), footprint, SettlementConfig.settlementTerritoryMargin + tier.growth());
	}

	private static int clamp(long value) {
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
	}
}
