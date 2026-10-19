package dev.alaindustrial.settlement.shift;

/**
 * The shift planner (MOD-832, ADR-050 point 10): a pure function from the time of day, the worker's shift and the
 * state of the town to a {@link ShiftMode}, plus how often a worker asks it. It replaces the villager's brain and
 * schedule — 26.3 has no {@code Schedule} class and its {@code Activity}/{@code SensorType} constructors are
 * private — and it is what the L1 lane tests without Minecraft.
 *
 * <p><b>Priority.</b> No power beats everything (R10: a town without energy hibernates, whatever the hour); no
 * oil comes next (R8: half-sleep); only then does the clock decide. The first slice has no oil in the game yet,
 * so the entity always passes {@code oil = true} and half-sleep is reached only by these tests.
 *
 * <p><b>The day.</b> Minecraft time of day, 0 to 23999, 0 being 6:00. A day worker works from 7:00 to 17:00,
 * goes home from 18:30 to 5:30 and is idle in between; a night worker (the watchman of R8, a later stage)
 * patrols in the home hours and sleeps in the work hours.
 *
 * <p><b>How often.</b> A worker asks once every {@link #THINK_INTERVAL} ticks, offset by its entity id so that a
 * town's workers do not all think on the same tick; a half-asleep one every {@link #HALF_SLEEP_THINK_INTERVAL}.
 */
public final class ShiftSchedule {

	/** Ticks of one Minecraft day. */
	public static final int DAY = 24_000;
	/** 7:00 — work starts. */
	public static final int WORK_START = 1_000;
	/** 17:00 — work ends. */
	public static final int WORK_END = 11_000;
	/** 18:30 — home time starts, a little before dusk. */
	public static final int HOME_START = 12_500;
	/** 5:30 — home time ends, a little before sunrise. */
	public static final int HOME_END = 23_500;

	/** A worker asks the planner once every this many ticks (ADR-050: once per 20 ticks, offset by id). */
	public static final int THINK_INTERVAL = 20;
	/** A half-asleep worker thinks this rarely (ADR-050, the research's guide value). */
	public static final int HALF_SLEEP_THINK_INTERVAL = 100;
	/** A half-asleep worker walks at this fraction of its speed (ADR-050, the research's guide value). */
	public static final double HALF_SLEEP_SPEED = 0.3;

	private ShiftSchedule() {
	}

	/**
	 * What the town lets its workers do.
	 *
	 * @param powered the core holds its territory (lit); false — every worker hibernates
	 * @param oil     the town has oil left; false — half-sleep (no oil in the first slice: always true)
	 */
	public record TownState(boolean powered, boolean oil) {
		/** A lit core with oil — the only state of the first slice while the core is lit. */
		public static final TownState RUNNING = new TownState(true, true);
	}

	/**
	 * The mode of a worker at {@code timeOfDay}.
	 *
	 * @param timeOfDay  game time of day in ticks; any long, read modulo {@link #DAY}
	 * @param nightShift true for a night worker (patrols at night, sleeps by day)
	 * @param town       the state of the worker's town
	 */
	public static ShiftMode modeAt(long timeOfDay, boolean nightShift, TownState town) {
		if (!town.powered()) {
			return ShiftMode.HIBERNATE;
		}
		if (!town.oil()) {
			return ShiftMode.HALF_SLEEP;
		}
		int time = (int) Math.floorMod(timeOfDay, (long) DAY);
		boolean workHours = time >= WORK_START && time < WORK_END;
		boolean homeHours = time >= HOME_START && time < HOME_END;
		if (nightShift) {
			return homeHours ? ShiftMode.PATROL : workHours ? ShiftMode.HOME : ShiftMode.IDLE;
		}
		return workHours ? ShiftMode.WORK : homeHours ? ShiftMode.HOME : ShiftMode.IDLE;
	}

	/** The ticks between two thinks of a worker in {@code mode}. */
	public static int thinkInterval(ShiftMode mode) {
		return mode == ShiftMode.HALF_SLEEP ? HALF_SLEEP_THINK_INTERVAL : THINK_INTERVAL;
	}

	/**
	 * Whether a worker with entity id {@code entityId}, {@code tickCount} ticks old, thinks on this tick. Each
	 * worker thinks exactly once per interval, and the offset by id spreads a town's workers over the interval.
	 */
	public static boolean thinksOn(long tickCount, int entityId, ShiftMode mode) {
		return Math.floorMod(tickCount + entityId, (long) thinkInterval(mode)) == 0;
	}

	/** The walking speed factor in {@code mode}: 1, slowed in half-sleep, 0 in hibernation. */
	public static double speedFactor(ShiftMode mode) {
		return switch (mode) {
			case HALF_SLEEP -> HALF_SLEEP_SPEED;
			case HIBERNATE -> 0.0;
			default -> 1.0;
		};
	}

	/**
	 * How many ticks a worker in {@code mode} waits between two walks, on average. Working and patrolling workers
	 * move most; a worker at home or between shifts mostly stands; a half-asleep one almost never moves.
	 */
	public static int strollInterval(ShiftMode mode) {
		return switch (mode) {
			case WORK, PATROL -> 60;
			case IDLE -> 120;
			case HOME -> 240;
			case HALF_SLEEP -> 600;
			case HIBERNATE -> Integer.MAX_VALUE;
		};
	}

	/**
	 * How far from its anchor a worker in {@code mode} walks, as a part of the whole wander radius: the whole
	 * radius at work and on patrol, a third of it at home (it stays by the house), half of it otherwise.
	 */
	public static int strollRadius(ShiftMode mode, int wanderRadius) {
		int radius = switch (mode) {
			case WORK, PATROL -> wanderRadius;
			case HOME -> wanderRadius / 3;
			case IDLE, HALF_SLEEP -> wanderRadius / 2;
			case HIBERNATE -> 0;
		};
		return Math.max(mode == ShiftMode.HIBERNATE ? 0 : 1, radius);
	}
}
