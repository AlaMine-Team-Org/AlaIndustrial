package dev.alaindustrial.settlement.shift;

/**
 * The day grid of an ALA town (MOD-889; epic MOD-824, decisions R75, R150, R156): a pure function from the world clock
 * and a worker's place in its town to what the worker does ({@link RoutineActivity}).
 *
 * <p><b>The grid is shared, the switches are not.</b> Every activity starts at its hour of the grid ({@link #GRID}:
 * 0 wake, 1000 work, 6000 break, 9000 gathering, 11000 rest, 12500 home), but each worker switches a little earlier
 * or later, by an offset of its own for every switch of every day. The offsets of one switch of one day are an affine
 * bijection of the worker's rank in its town (its place among the town's living workers by number) onto
 * {@link #slots} slots {@link #SPACING} ticks apart: no two workers of a town switch on the same tick, nor within one
 * think interval of each other, unless the town has more workers than slots — and then they share a slot evenly.
 * The bijection's step and shift come from the day and the town, so the order is different every day: someone who
 * left first yesterday may finish last today. The same inputs give the same answer, on any machine.
 *
 * <p><b>Bounds.</b> An offset is at most {@link #halfWidth} away from the grid hour: at most {@link #MAX_JITTER}, and
 * less than half the gap to either neighbouring hour, so the activities of a worker keep their order every day.
 *
 * <p>Minecraft-free on purpose: the L1 lane tests it ({@code DayRoutineTest}).
 */
public final class DayRoutine {

	/** Ticks of one Minecraft day. */
	public static final int DAY = ShiftSchedule.DAY;
	/** Ticks between two neighbouring slots: a worker's think interval, so two switches never share a think tick. */
	public static final int SPACING = ShiftSchedule.THINK_INTERVAL;
	/** The furthest a worker's switch moves from its grid hour, in ticks (plan 0.1.220, block B5: up to 1000). */
	public static final int MAX_JITTER = 1000;

	/** The hour each activity starts at, in {@link RoutineActivity} order. */
	static final int[] GRID = {0, 1_000, 6_000, 9_000, 11_000, 12_500};

	private static final RoutineActivity[] ACTIVITIES = RoutineActivity.values();
	private static final int[] HALF = new int[GRID.length];
	private static final int[] SLOTS = new int[GRID.length];

	static {
		if (ACTIVITIES.length != GRID.length) {
			throw new IllegalStateException("the day grid names " + GRID.length + " hours for " + ACTIVITIES.length
					+ " activities");
		}
		for (int i = 0; i < GRID.length; i++) {
			int previous = Math.floorMod(GRID[i] - GRID[(i + GRID.length - 1) % GRID.length], DAY);
			int next = Math.floorMod(GRID[(i + 1) % GRID.length] - GRID[i], DAY);
			HALF[i] = Math.min(MAX_JITTER, Math.min(previous, next) / 2 - SPACING);
			SLOTS[i] = largestPrimeAtMost(2 * HALF[i] / SPACING + 1);
		}
	}

	private DayRoutine() {
	}

	/** The hour of the grid {@code activity} starts at, 0 to 23999. */
	public static int gridHour(RoutineActivity activity) {
		return GRID[activity.ordinal()];
	}

	/** How far, at most, a worker's switch to {@code activity} lies from its grid hour, in ticks. */
	public static int halfWidth(RoutineActivity activity) {
		return HALF[activity.ordinal()];
	}

	/** How many workers of one town switch to {@code activity} on distinct slots (a prime). */
	public static int slots(RoutineActivity activity) {
		return SLOTS[activity.ordinal()];
	}

	/**
	 * The world clock tick at which the worker of rank {@code rank} switches to {@code activity} on day {@code day}
	 * (day {@code d} runs from {@code d * DAY}); the switch to {@link RoutineActivity#WAKE} may fall at the end of the
	 * day before.
	 *
	 * @param salt the town's own number in the hash, so two towns do not walk in step either
	 */
	public static long switchAt(long day, RoutineActivity activity, int rank, long salt) {
		int i = activity.ordinal();
		int slots = SLOTS[i];
		long hash = mix(mix(salt) ^ mix(day * 31L + i));
		int step = 1 + (int) Math.floorMod(hash, (long) (slots - 1));
		int shift = (int) Math.floorMod(mix(hash), (long) slots);
		int slot = (int) Math.floorMod((long) rank * step + shift, (long) slots);
		return day * DAY + GRID[i] - HALF[i] + (long) slot * SPACING;
	}

	/** What the worker of rank {@code rank} of the town salted {@code salt} does at world clock tick {@code clock}. */
	public static RoutineActivity activityAt(long clock, int rank, long salt) {
		long day = Math.floorDiv(clock, (long) DAY);
		RoutineActivity current = RoutineActivity.SLEEP;
		long latest = Long.MIN_VALUE;
		for (long d = day - 1; d <= day + 1; d++) {
			for (RoutineActivity activity : ACTIVITIES) {
				long at = switchAt(d, activity, rank, salt);
				if (at <= clock && at > latest) {
					latest = at;
					current = activity;
				}
			}
		}
		return current;
	}

	/** The day of {@code clock}: the index the offsets of that day are drawn from. */
	public static long day(long clock) {
		return Math.floorDiv(clock, (long) DAY);
	}

	/** SplitMix64's finaliser: a well-spread 64-bit hash of {@code value}. */
	static long mix(long value) {
		long z = value + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	static int largestPrimeAtMost(int n) {
		for (int candidate = Math.max(2, n); candidate > 2; candidate--) {
			if (isPrime(candidate)) {
				return candidate;
			}
		}
		return 2;
	}

	private static boolean isPrime(int n) {
		if (n < 2) {
			return false;
		}
		for (int d = 2; (long) d * d <= n; d++) {
			if (n % d == 0) {
				return false;
			}
		}
		return true;
	}
}
