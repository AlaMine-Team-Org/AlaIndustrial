package dev.alaindustrial.core.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The reactor's core seen from above, one entry per vertical stack of columns (MOD-620) — what the «Core» tab
 * draws.
 *
 * <p><b>A stack, not a column, is the unit.</b> The owner's decision for the tab: a stack is one vessel for
 * water and steam (the controller settles its tanks as one), so it is also what the player reads. Aggregating
 * here, on the server, is what keeps the packet at most one entry per footprint cell instead of one per column
 * of a 12³ room.
 *
 * <p><b>"Densest", not "hottest".</b> Heat is one number for the whole room, so there is no hottest stack to
 * point at. What differs between stacks is how many fuelled neighbours their columns have. Every neighbour pair
 * raises the whole room's output and heat — the bonus is room-wide and the output is shared across all rods — so
 * the mark shows where that bonus is being earned, not a stack that runs hotter than the rest.
 *
 * <p>Minecraft-free so the aggregation, the marks and the fuel arithmetic are L1-tested; the controller only
 * feeds it what the world holds.
 */
public final class ReactorZone {

	/**
	 * Widest zone the tab shows, in stacks along either axis: as wide as a bare pile found within the default search
	 * radius of 8 can spread. A room past that (the room limit has no ceiling in the config) is shown by its
	 * north-west corner of this size, rather than refused.
	 */
	public static final int MAX_SPAN = 17;

	/** Most stacks a snapshot carries — a full {@link #MAX_SPAN} square. */
	public static final int MAX_STACKS = MAX_SPAN * MAX_SPAN;

	/**
	 * Steam share of a vessel from which its exhaust counts as blocked: the water is there, but boiling it has almost
	 * nowhere to go. The line the «Console» tab has drawn its steam bar amber on since MOD-618, so both tabs call the
	 * same vessel blocked (MOD-621).
	 */
	public static final int STEAM_BLOCKED_PERCENT = 90;

	private ReactorZone() {
	}

	/**
	 * One column as the controller reads it.
	 *
	 * @param fuelledDamage the damage of each fuelled rod
	 * @param spentRods     spent casings still racked
	 */
	public record Column(int x, int y, int z, int[] fuelledDamage, int spentRods, long water, long waterCapacity,
			long steam, long steamCapacity, boolean boiling) {

		/** A column that has given no water to the reaction lately — what every column is outside a running room. */
		public Column(int x, int y, int z, int[] fuelledDamage, int spentRods, long water, long waterCapacity, long steam,
				long steamCapacity) {
			this(x, y, z, fuelledDamage, spentRods, water, waterCapacity, steam, steamCapacity, false);
		}
	}

	/**
	 * One stack of columns: offsets from the zone's north-west corner, and what its columns hold together.
	 *
	 * @param averageWearPermille mean wear over every racked rod, a spent casing counting as fully worn
	 * @param worstWearPermille   the most worn racked rod
	 * @param remainingEu         energy the fuelled rods still hold
	 * @param neighbours          fuelled neighbours of the stack's fuelled columns, in all six directions
	 */
	public record Stack(int x, int z, int columns, int fuelledRods, int spentRods, int averageWearPermille,
			int worstWearPermille, long remainingEu, int neighbours, Coolant coolant) {

		/** Whether any rod is racked here, fuelled or spent. */
		public boolean hasRods() {
			return fuelledRods + spentRods > 0;
		}

		public int waterPercent() {
			return coolant.waterPercent();
		}

		public int steamPercent() {
			return coolant.steamPercent();
		}
	}

	/**
	 * A stack's water and steam, summed over its columns in millibuckets, and the two faults the «Coolant» tab marks
	 * (MOD-621).
	 *
	 * <p>The faults are judged per vessel, not over the sums: a vessel is a run of columns touching top to bottom, the
	 * way the controller settles them, and a cell with a gap in its height holds two. Summed, a full lower vessel would
	 * hide an empty or choked one above it.
	 *
	 * @param dry     some vessel in the stack holds no water and gave none to the reaction lately. A vessel the reaction
	 *                boils empty every tick while its pipe refills it reads 0 mB at whatever moment it is looked at, and
	 *                it is working, not dry (review, MOD-621)
	 * @param blocked some vessel's steam fills at least {@link #STEAM_BLOCKED_PERCENT} of its room, so its water has
	 *                nowhere to boil to
	 */
	public record Coolant(long water, long waterCapacity, long steam, long steamCapacity, boolean dry, boolean blocked) {

		public int waterPercent() {
			return percent(water, waterCapacity);
		}

		public int steamPercent() {
			return percent(steam, steamCapacity);
		}
	}

	/**
	 * Folds columns into stacks, north to south and then west to east.
	 *
	 * @param originX   the zone's west edge; stack offsets are counted from here
	 * @param originZ   the zone's north edge
	 * @param width     the zone's extent east-west, in stacks; a column past it (or past {@link #MAX_SPAN}) is left out
	 * @param depth     the zone's extent north-south
	 * @param rodEnergy EU one rod is worth, as the burn spends it
	 */
	public static List<Stack> stacks(List<Column> columns, int originX, int originZ, int width, int depth,
			long rodEnergy) {
		// Neighbours are counted over every column, shown or not: a rack past the cut still pairs with one inside it,
		// exactly as the reactor counts it.
		Set<Long> fuelled = new HashSet<>();
		for (Column column : columns) {
			if (column.fuelledDamage().length > 0) {
				fuelled.add(key(column.x(), column.y(), column.z()));
			}
		}
		int shownWidth = Math.min(width, MAX_SPAN);
		int shownDepth = Math.min(depth, MAX_SPAN);
		Map<Long, Tally> byStack = new TreeMap<>();
		for (Column column : columns) {
			int x = column.x() - originX;
			int z = column.z() - originZ;
			// Cut by position, not by list order: taking the first stacks in order left a hall past the span with whole
			// rows missing on the east side and cells drawn outside the grid (review, MOD-620).
			if (x < 0 || z < 0 || x >= shownWidth || z >= shownDepth) {
				continue;
			}
			Tally tally = byStack.computeIfAbsent(((long) z << 32) | x, k -> new Tally(x, z));
			tally.add(column, rodEnergy, fuelled);
		}
		List<Stack> out = new ArrayList<>();
		for (Tally tally : byStack.values()) {
			out.add(tally.stack());
		}
		return out;
	}

	/**
	 * The stack whose columns have the most fuelled neighbours, or -1 if no column has any. A tie goes to the first in
	 * the list's order: the northernmost, then the westernmost.
	 */
	public static int densest(List<Stack> stacks) {
		int best = -1;
		for (int i = 0; i < stacks.size(); i++) {
			if (stacks.get(i).neighbours() > 0 && (best < 0 || stacks.get(i).neighbours() > stacks.get(best).neighbours())) {
				best = i;
			}
		}
		return best;
	}

	/**
	 * The stack to service first: the one holding the most worn rod — a spent casing is the most worn rod there
	 * is — or -1 while nothing racked has worn at all. A tie goes to the northernmost, then the westernmost.
	 */
	public static int replaceFirst(List<Stack> stacks) {
		int best = -1;
		for (int i = 0; i < stacks.size(); i++) {
			Stack stack = stacks.get(i);
			if (stack.hasRods() && stack.worstWearPermille() > 0
					&& (best < 0 || stack.worstWearPermille() > stacks.get(best).worstWearPermille())) {
				best = i;
			}
		}
		return best;
	}

	/**
	 * One block position as a set key, packed like {@code BlockPos.asLong}: 26 bits each for x and z, 12 for y.
	 *
	 * <p>The first version gave every axis 21 bits after an offset of 2^20 and OR-ed them together. A world position a
	 * few million blocks out does not fit 21 bits, so z spilled into y, and the key one block up came out equal to the
	 * column's own: a lone column counted itself as its neighbour. The NeoForge gametest world sits at z ≈ 14.8 million,
	 * which is where the scenario caught it.
	 */
	private static long key(int x, int y, int z) {
		return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
	}

	private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	private static final class Tally {
		private final int x;
		private final int z;
		private int columns;
		private int fuelledRods;
		private int spentRods;
		private long wearSum;
		private int worst;
		private long remainingEu;
		private int neighbours;
		private long water;
		private long waterCapacity;
		private long steam;
		private long steamCapacity;
		private final List<Column> members = new ArrayList<>();

		private Tally(int x, int z) {
			this.x = x;
			this.z = z;
		}

		private void add(Column column, long rodEnergy, Set<Long> fuelled) {
			members.add(column);
			columns++;
			fuelledRods += column.fuelledDamage().length;
			spentRods += column.spentRods();
			for (int damage : column.fuelledDamage()) {
				int wear = FuelRodMath.wearPermille(damage);
				wearSum += wear;
				worst = Math.max(worst, wear);
				remainingEu += FuelRodMath.remainingEnergy(damage, rodEnergy);
			}
			if (column.spentRods() > 0) {
				wearSum += 1000L * column.spentRods();
				worst = 1000;
			}
			if (column.fuelledDamage().length > 0) {
				for (int[] side : SIDES) {
					if (fuelled.contains(key(column.x() + side[0], column.y() + side[1], column.z() + side[2]))) {
						neighbours++;
					}
				}
			}
			water += column.water();
			waterCapacity += column.waterCapacity();
			steam += column.steam();
			steamCapacity += column.steamCapacity();
		}

		private Stack stack() {
			int racked = fuelledRods + spentRods;
			return new Stack(x, z, columns, fuelledRods, spentRods, racked == 0 ? 0 : (int) (wearSum / racked), worst,
					remainingEu, neighbours, coolant());
		}

		/** The sums, and each fault judged vessel by vessel: runs of columns touching top to bottom. */
		private Coolant coolant() {
			List<Column> byHeight = new ArrayList<>(members);
			byHeight.sort(Comparator.comparingInt(Column::y));
			boolean dry = false;
			boolean blocked = false;
			int start = 0;
			while (start < byHeight.size()) {
				long runWater = 0;
				long runWaterCapacity = 0;
				long runSteam = 0;
				long runSteamCapacity = 0;
				boolean runBoiling = false;
				int end = start;
				do {
					Column column = byHeight.get(end);
					runBoiling |= column.boiling();
					runWater += column.water();
					runWaterCapacity += column.waterCapacity();
					runSteam += column.steam();
					runSteamCapacity += column.steamCapacity();
					end++;
				} while (end < byHeight.size() && byHeight.get(end).y() == byHeight.get(end - 1).y() + 1);
				dry |= runWaterCapacity > 0 && runWater <= 0 && !runBoiling;
				blocked |= runSteamCapacity > 0 && runSteam * 100 >= runSteamCapacity * STEAM_BLOCKED_PERCENT;
				start = end;
			}
			return new Coolant(water, waterCapacity, steam, steamCapacity, dry, blocked);
		}
	}

	/**
	 * A share in percent, rounded to the nearest like the «Console» tab's coolant readout: a pipe parks a full column
	 * a few mB short of capacity, and a truncating divide showed 99 % here while the other tab said 100 % (review,
	 * MOD-620).
	 */
	static int percent(long part, long whole) {
		if (whole <= 0) {
			return 0;
		}
		return (int) Math.max(0, Math.min(100, (part * 200 + whole) / (whole * 2)));
	}
}
