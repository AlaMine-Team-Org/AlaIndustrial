package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the town's day grid (MOD-889; epic MOD-824, R75, R156): the grid hours, the personal offsets that
 * keep a town from switching as one, and their change from day to day.
 *
 * @implements MOD-889-GRID — the day grid is a pure function of the clock, the worker's rank and its town
 * @implements MOD-889-NO-PUPPETS — no two workers of a 20-worker town switch on the same tick; offsets change by day
 */
class DayRoutineTest {

	private static final int TOWN = 7;
	private static final int WORKERS = 20;
	private static final RoutineActivity[] ALL = RoutineActivity.values();

	@Test
	void everyWorkerFollowsTheGridAwayFromTheSwitches() {
		long[] times = {500, 3_000, 7_500, 10_100, 11_750, 18_000};
		RoutineActivity[] expected = {RoutineActivity.WAKE, RoutineActivity.WORK, RoutineActivity.BREAK,
				RoutineActivity.GATHER, RoutineActivity.REST, RoutineActivity.SLEEP};
		for (long day = 0; day < 30; day++) {
			for (int rank = 0; rank < 60; rank++) {
				for (int i = 0; i < times.length; i++) {
					assertEquals(expected[i], DayRoutine.activityAt(day * DayRoutine.DAY + times[i], rank, TOWN),
							"day " + day + ", rank " + rank + ", time " + times[i]);
				}
			}
		}
	}

	@Test
	void switchesStayWithinTheirBoundsAndKeepTheirOrder() {
		for (long day = -3; day < 40; day++) {
			for (int rank = 0; rank < 120; rank++) {
				long previous = Long.MIN_VALUE;
				for (RoutineActivity activity : ALL) {
					long at = DayRoutine.switchAt(day, activity, rank, TOWN);
					long grid = day * DayRoutine.DAY + DayRoutine.gridHour(activity);
					assertTrue(Math.abs(at - grid) <= DayRoutine.halfWidth(activity),
							activity + " moved " + (at - grid) + " ticks, the bound is "
									+ DayRoutine.halfWidth(activity));
					assertTrue(DayRoutine.halfWidth(activity) <= DayRoutine.MAX_JITTER);
					assertTrue(at > previous, "day " + day + ", rank " + rank + ": " + activity + " out of order");
					previous = at;
				}
				assertTrue(previous < DayRoutine.switchAt(day + 1, RoutineActivity.WAKE, rank, TOWN),
						"the night must end after it began");
			}
		}
	}

	@Test
	void noTwoWorkersOfATownSwitchWithinOneThinkInterval() {
		for (long day = 0; day < 50; day++) {
			for (RoutineActivity activity : ALL) {
				List<Long> at = new ArrayList<>();
				for (int rank = 0; rank < WORKERS; rank++) {
					at.add(DayRoutine.switchAt(day, activity, rank, TOWN));
				}
				at.sort(null);
				for (int i = 1; i < at.size(); i++) {
					assertTrue(at.get(i) - at.get(i - 1) >= DayRoutine.SPACING, "day " + day + ", " + activity
							+ ": two workers switch " + (at.get(i) - at.get(i - 1)) + " ticks apart");
				}
			}
		}
	}

	/**
	 * The town as the game runs it: 20 workers with scattered entity ids, each asking every think interval offset by
	 * its id, over ten days tick by tick, never two switches on one tick.
	 */
	@Test
	void aTownNeverChangesItsActivityAsOne() {
		int[] ids = new int[WORKERS];
		for (int i = 0; i < WORKERS; i++) {
			ids[i] = 1_000 + i * 37 + (i * i) % 13;
		}
		RoutineActivity[] doing = new RoutineActivity[WORKERS];
		int switches = 0;
		for (long tick = 0; tick < 10L * DayRoutine.DAY; tick++) {
			int switchedNow = 0;
			for (int w = 0; w < WORKERS; w++) {
				if (Math.floorMod(tick + ids[w], (long) ShiftSchedule.THINK_INTERVAL) != 0) {
					continue;
				}
				RoutineActivity now = DayRoutine.activityAt(tick, w, TOWN);
				if (doing[w] != null && now != doing[w]) {
					switchedNow++;
					switches++;
				}
				doing[w] = now;
			}
			assertTrue(switchedNow <= 1, switchedNow + " workers switched together on tick " + tick);
		}
		assertTrue(switches >= WORKERS * ALL.length * 9, "the workers lived their days: " + switches);
	}

	@Test
	void offsetsDifferFromDayToDayAndBetweenTowns() {
		for (int rank = 0; rank < WORKERS; rank++) {
			for (RoutineActivity activity : ALL) {
				Set<Long> offsets = new HashSet<>();
				for (long day = 0; day < 10; day++) {
					offsets.add(DayRoutine.switchAt(day, activity, rank, TOWN) - day * DayRoutine.DAY);
				}
				assertTrue(offsets.size() >= 5, "rank " + rank + " switches to " + activity
						+ " at nearly the same offset every day: " + offsets);
			}
		}
		int same = 0;
		for (int rank = 0; rank < WORKERS; rank++) {
			if (DayRoutine.switchAt(3, RoutineActivity.GATHER, rank, TOWN)
					== DayRoutine.switchAt(3, RoutineActivity.GATHER, rank, TOWN + 1)) {
				same++;
			}
		}
		assertTrue(same < WORKERS / 2, "two towns gather in step: " + same);
	}

	@Test
	void theOrderOfTheTownChangesFromDayToDay() {
		Map<Long, List<Integer>> orders = new HashMap<>();
		for (long day = 0; day < 5; day++) {
			List<Integer> ranks = new ArrayList<>();
			for (int rank = 0; rank < WORKERS; rank++) {
				ranks.add(rank);
			}
			final long d = day;
			ranks.sort((a, b) -> Long.compare(DayRoutine.switchAt(d, RoutineActivity.REST, a, TOWN),
					DayRoutine.switchAt(d, RoutineActivity.REST, b, TOWN)));
			orders.put(day, ranks);
		}
		assertEquals(orders.size(), new HashSet<>(orders.values()).size(), "the order repeats: " + orders);
		assertTrue(!orders.get(0L).getFirst().equals(orders.get(1L).getFirst())
				|| !orders.get(1L).getFirst().equals(orders.get(2L).getFirst()),
				"the same worker leaves first every day: " + orders);
	}

	@Test
	void theSameInputsGiveTheSameAnswer() {
		for (long clock = -50_000; clock < 100_000; clock += 977) {
			assertEquals(DayRoutine.activityAt(clock, 4, TOWN), DayRoutine.activityAt(clock, 4, TOWN));
			assertEquals(DayRoutine.switchAt(clock / 1000, RoutineActivity.BREAK, 9, TOWN),
					DayRoutine.switchAt(clock / 1000, RoutineActivity.BREAK, 9, TOWN));
		}
	}

	@Test
	void slotsArePrimeAndSpanTheirWidth() {
		for (RoutineActivity activity : ALL) {
			int slots = DayRoutine.slots(activity);
			for (int d = 2; d * d <= slots; d++) {
				assertTrue(slots % d != 0, activity + ": " + slots + " is not prime");
			}
			assertTrue((slots - 1) * DayRoutine.SPACING <= 2 * DayRoutine.halfWidth(activity));
			assertTrue(slots >= WORKERS, activity + " has room for fewer than " + WORKERS + " workers: " + slots);
		}
	}
}
