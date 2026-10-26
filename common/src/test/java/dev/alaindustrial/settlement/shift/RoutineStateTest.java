package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for one worker's day as it goes (MOD-889): finishing what it does before a switch, giving a place up
 * once it gets no closer, and waiting out unreachable targets.
 *
 * @implements MOD-889-FINISH — a switch waits for the worker to finish its walk, at most a cap
 * @implements MOD-889-STUCK-RULE — no progress for K ticks gives the place up exactly once
 * @implements MOD-889-BACKOFF — an unreachable target is retried after a growing wait, not every tick
 */
class RoutineStateTest {

	@Test
	void theFirstThinkSwitchesAtOnceAndASwitchWaitsForTheWalk() {
		RoutineState state = new RoutineState();
		assertTrue(state.shouldSwitch(RoutineActivity.WORK, 0, true), "the first think takes the activity at once");
		state.adopt(RoutineActivity.WORK, 0);
		assertFalse(state.shouldSwitch(RoutineActivity.WORK, 20, false));
		assertFalse(state.shouldSwitch(RoutineActivity.BREAK, 100, true), "it finishes its walk first");
		assertFalse(state.shouldSwitch(RoutineActivity.BREAK, 100 + RoutineState.FINISH_CAP - 1, true));
		assertTrue(state.shouldSwitch(RoutineActivity.BREAK, 100 + RoutineState.FINISH_CAP, true),
				"a walk never holds a switch longer than the cap");
		RoutineState idle = new RoutineState();
		idle.adopt(RoutineActivity.WORK, 0);
		assertTrue(idle.shouldSwitch(RoutineActivity.BREAK, 40, false), "a worker that does nothing switches at once");
	}

	@Test
	void noProgressGivesThePlaceUpOnce() {
		RoutineState state = new RoutineState();
		state.adopt(RoutineActivity.GATHER, 0);
		long hall = 42L;
		assertEquals(RoutineState.Progress.ON_WAY, state.track(hall, 30.0, 0));
		assertEquals(RoutineState.Progress.ON_WAY, state.track(hall, 28.5, 100), "1.5 blocks closer is progress");
		long last = 100;
		for (long t = 120; t < last + RoutineState.STUCK_TICKS; t += 20) {
			assertEquals(RoutineState.Progress.ON_WAY, state.track(hall, 28.0, t), "tick " + t);
		}
		assertEquals(RoutineState.Progress.STUCK, state.track(hall, 28.0, last + RoutineState.STUCK_TICKS));
		assertEquals(RoutineState.Progress.GIVEN_UP, state.track(hall, 28.0, last + RoutineState.STUCK_TICKS + 20),
				"the place is given up once: no second line");
		assertTrue(state.gaveUp(hall));
		state.adopt(RoutineActivity.REST, 2_000);
		assertFalse(state.gaveUp(hall), "the next activity tries again");
	}

	@Test
	void aPauseDoesNotCountAsBeingStuck() {
		RoutineState state = new RoutineState();
		state.adopt(RoutineActivity.WORK, 0);
		state.track(5L, 20.0, 0);
		state.paused(RoutineState.STUCK_TICKS - 10);
		assertEquals(RoutineState.Progress.ON_WAY, state.track(5L, 20.0, RoutineState.STUCK_TICKS + 10));
	}

	@Test
	void anUnreachableTargetWaitsLongerEachTime() {
		RoutineState state = new RoutineState();
		long target = 9L;
		assertTrue(state.mayPlan(target, 0));
		long now = 0;
		long previousWait = 0;
		for (int failure = 1; failure <= 8; failure++) {
			state.planFailed(target, now);
			long wait = 0;
			while (!state.mayPlan(target, now + wait)) {
				wait++;
			}
			assertTrue(wait >= previousWait, "failure " + failure + ": waited " + wait + " after " + previousWait);
			assertTrue(wait <= RoutineState.BACKOFF_MAX);
			previousWait = wait;
			now += wait;
		}
		assertEquals(RoutineState.BACKOFF_MAX, previousWait, "the wait grows to its cap");
		assertTrue(state.mayPlan(77L, now), "another target is not held back");
		state.planFound(target);
		assertTrue(state.mayPlan(target, now) && state.failures(target) == 0, "a found path clears the target");
	}

	@Test
	void triesOverAMinuteStayFew() {
		RoutineState state = new RoutineState();
		int tries = 0;
		for (long tick = 0; tick < 1_200; tick++) {
			if (state.mayPlan(1L, tick)) {
				tries++;
				state.planFailed(1L, tick);
			}
		}
		assertTrue(tries <= 6, "an unreachable target was tried " + tries + " times in a minute");
	}

	@Test
	void theMemoryOfTargetsIsSmall() {
		RoutineState state = new RoutineState();
		for (long target = 0; target < 10; target++) {
			state.planFailed(target, 0);
		}
		assertTrue(state.mayPlan(0L, 0), "the oldest target is forgotten");
		assertFalse(state.mayPlan(9L, 0), "the newest is remembered");
	}

	@Test
	void theStrollFollowsTheActivityOnlyInItsMode() {
		RoutineState state = new RoutineState();
		assertEquals(ShiftSchedule.strollInterval(ShiftMode.IDLE), state.strollInterval(ShiftMode.IDLE));
		state.adopt(RoutineActivity.GATHER, 0);
		assertEquals(RoutineActivity.GATHER.strollInterval(), state.strollInterval(ShiftMode.IDLE));
		assertEquals(ShiftSchedule.strollInterval(ShiftMode.HALF_SLEEP), state.strollInterval(ShiftMode.HALF_SLEEP));
	}
}
