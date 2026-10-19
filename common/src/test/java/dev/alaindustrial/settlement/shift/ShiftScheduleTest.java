package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.shift.ShiftSchedule.TownState;
import java.util.EnumSet;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the shift planner (MOD-832, ADR-050 point 10): the mode from the hour, the shift and the town,
 * and how often a worker asks.
 *
 * @implements MOD-832-SCHEDULE — the shift planner is a pure function covered without Minecraft.
 */
class ShiftScheduleTest {

	private static final TownState DARK = new TownState(false, true);
	private static final TownState NO_OIL = new TownState(true, false);

	@Test
	void dayWorkerWorksByDayGoesHomeAtNightAndIdlesBetween() {
		assertEquals(ShiftMode.WORK, ShiftSchedule.modeAt(6_000, false, TownState.RUNNING), "noon");
		assertEquals(ShiftMode.WORK, ShiftSchedule.modeAt(ShiftSchedule.WORK_START, false, TownState.RUNNING));
		assertEquals(ShiftMode.IDLE, ShiftSchedule.modeAt(ShiftSchedule.WORK_END, false, TownState.RUNNING),
				"work ends at WORK_END itself");
		assertEquals(ShiftMode.HOME, ShiftSchedule.modeAt(18_000, false, TownState.RUNNING), "midnight");
		assertEquals(ShiftMode.IDLE, ShiftSchedule.modeAt(500, false, TownState.RUNNING), "6:30, before work");
		assertEquals(ShiftMode.IDLE, ShiftSchedule.modeAt(23_800, false, TownState.RUNNING), "just before dawn");
	}

	@Test
	void nightWorkerPatrolsAtNightAndSleepsByDay() {
		assertEquals(ShiftMode.PATROL, ShiftSchedule.modeAt(18_000, true, TownState.RUNNING));
		assertEquals(ShiftMode.HOME, ShiftSchedule.modeAt(6_000, true, TownState.RUNNING));
		assertEquals(ShiftMode.IDLE, ShiftSchedule.modeAt(12_000, true, TownState.RUNNING));
	}

	/** R10 before R8 before the clock: a dark town hibernates even without oil, at any hour, on any shift. */
	@Property
	void noPowerHibernatesWhateverElse(@ForAll long time, @ForAll boolean nightShift, @ForAll boolean oil) {
		assertEquals(ShiftMode.HIBERNATE, ShiftSchedule.modeAt(time, nightShift, new TownState(false, oil)));
	}

	@Property
	void noOilHalfSleepsWhateverTheHour(@ForAll long time, @ForAll boolean nightShift) {
		assertEquals(ShiftMode.HALF_SLEEP, ShiftSchedule.modeAt(time, nightShift, NO_OIL));
	}

	/** The time is read modulo a day, negative time included: day N and day N+1 agree at the same hour. */
	@Property
	void theHourAloneDecides(@ForAll @IntRange(min = 0, max = 23_999) int hour,
			@ForAll @LongRange(min = -1_000_000, max = 1_000_000) long day, @ForAll boolean nightShift) {
		assertEquals(ShiftSchedule.modeAt(hour, nightShift, TownState.RUNNING),
				ShiftSchedule.modeAt(day * ShiftSchedule.DAY + hour, nightShift, TownState.RUNNING));
	}

	/** A running town never answers HIBERNATE or HALF_SLEEP — those come only from the town's state. */
	@Property
	void runningTownUsesOnlyTheDayModes(@ForAll long time, @ForAll boolean nightShift) {
		Set<ShiftMode> dayModes = EnumSet.of(ShiftMode.WORK, ShiftMode.HOME, ShiftMode.PATROL, ShiftMode.IDLE);
		assertTrue(dayModes.contains(ShiftSchedule.modeAt(time, nightShift, TownState.RUNNING)));
	}

	/** Each worker thinks exactly once per interval, whatever its id. */
	@Property
	void thinksExactlyOncePerInterval(@ForAll @IntRange(min = 0, max = 1_000_000) int entityId,
			@ForAll @LongRange(min = 0, max = 1_000_000) long start) {
		for (ShiftMode mode : new ShiftMode[] {ShiftMode.WORK, ShiftMode.HALF_SLEEP}) {
			int interval = ShiftSchedule.thinkInterval(mode);
			int thinks = 0;
			for (long tick = start; tick < start + interval; tick++) {
				if (ShiftSchedule.thinksOn(tick, entityId, mode)) {
					thinks++;
				}
			}
			assertEquals(1, thinks, mode + ": thinks per interval for id " + entityId);
		}
	}

	/** Twenty workers with consecutive ids spread over the twenty ticks of one interval, not on one tick. */
	@Test
	void consecutiveIdsThinkOnDifferentTicks() {
		Set<Long> ticks = new java.util.HashSet<>();
		for (int id = 0; id < ShiftSchedule.THINK_INTERVAL; id++) {
			for (long tick = 0; tick < ShiftSchedule.THINK_INTERVAL; tick++) {
				if (ShiftSchedule.thinksOn(tick, id, ShiftMode.WORK)) {
					ticks.add(tick);
				}
			}
		}
		assertEquals(ShiftSchedule.THINK_INTERVAL, ticks.size());
	}

	@Test
	void halfSleepIsSlowAndRareAndHibernationStill() {
		assertEquals(ShiftSchedule.HALF_SLEEP_THINK_INTERVAL, ShiftSchedule.thinkInterval(ShiftMode.HALF_SLEEP));
		assertEquals(ShiftSchedule.THINK_INTERVAL, ShiftSchedule.thinkInterval(ShiftMode.WORK));
		assertEquals(0.3, ShiftSchedule.speedFactor(ShiftMode.HALF_SLEEP), 1e-9);
		assertEquals(0.0, ShiftSchedule.speedFactor(ShiftMode.HIBERNATE), 1e-9);
		assertEquals(1.0, ShiftSchedule.speedFactor(ShiftMode.WORK), 1e-9);
		assertEquals(Integer.MAX_VALUE, ShiftSchedule.strollInterval(ShiftMode.HIBERNATE));
		assertEquals(0, ShiftSchedule.strollRadius(ShiftMode.HIBERNATE, 12));
	}

	/** No walk ever reaches past the wander radius, and every mode but hibernation walks at least one block. */
	@Property
	void strollRadiusStaysInsideTheWanderRadius(@ForAll @IntRange(min = 1, max = 256) int wanderRadius) {
		for (ShiftMode mode : ShiftMode.values()) {
			int radius = ShiftSchedule.strollRadius(mode, wanderRadius);
			assertTrue(radius <= wanderRadius, mode + " walks " + radius + " > " + wanderRadius);
			assertTrue(mode == ShiftMode.HIBERNATE ? radius == 0 : radius >= 1, mode + " radius " + radius);
		}
	}
}
