package dev.alaindustrial.settlement.shift;

/**
 * What a shift worker does at this hour of its day (MOD-889; epic MOD-824, decisions R75, R150, R156): one step of
 * the town's day grid ({@link DayRoutine}). The coarse {@link ShiftMode} still says whether the worker may act at all
 * (power, oil); while it may, the activity says where it goes.
 *
 * <p>Minecraft-free on purpose: the L1 lane loads it ({@code DayRoutineTest}).
 */
public enum RoutineActivity {
	/** About 0 (6:00): gets up from its charging bed and leaves the house for the town. */
	WAKE(ShiftMode.IDLE, 120),
	/** About 1000 (7:00): at its workplace; a worker with no work strolls in the town (R150). */
	WORK(ShiftMode.WORK, 60),
	/** About 6000 (noon): a break: steps away from what it did, stands, looks about. */
	BREAK(ShiftMode.IDLE, 300),
	/** About 9000 (15:00): the gathering in the town hall, the workers standing in a group facing each other. */
	GATHER(ShiftMode.IDLE, 400),
	/** About 11000 (17:00): rest: strolls the town, by the roads and its own house. */
	REST(ShiftMode.IDLE, 120),
	/** About 12500 (18:30): home, lying on its charging bed, charging through the night (R7, R101). */
	SLEEP(ShiftMode.HOME, 300);

	private final ShiftMode mode;
	private final int strollInterval;

	RoutineActivity(ShiftMode mode, int strollInterval) {
		this.mode = mode;
		this.strollInterval = strollInterval;
	}

	/** The coarse mode the worker shows while doing this. */
	public ShiftMode mode() {
		return mode;
	}

	/** How many ticks, on average, the worker waits between two short walks while doing this. */
	public int strollInterval() {
		return strollInterval;
	}

	/** Whether the worker does this out of doors, so a rain sends it under a roof. Work at a workplace goes on. */
	public boolean outdoors() {
		return this != SLEEP;
	}
}
