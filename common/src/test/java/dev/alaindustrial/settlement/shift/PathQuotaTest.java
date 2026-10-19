package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the town's path dispatcher (MOD-832, ADR-050 point 11).
 *
 * @implements MOD-832-PATHS — no more than the knob's number of new paths per tick in one town.
 */
class PathQuotaTest {

	@Test
	void grantsAtMostPerTickThenStartsAgainNextTick() {
		PathQuota quota = new PathQuota();
		int granted = 0;
		for (int ask = 0; ask < 10; ask++) {
			if (quota.tryAcquire(100L, 2)) {
				granted++;
			}
		}
		assertEquals(2, granted, "ten workers asking on one tick");
		assertTrue(quota.tryAcquire(101L, 2), "a new tick grants again");
	}

	@Test
	void unusedGrantsAreNotSavedUp() {
		PathQuota quota = new PathQuota();
		quota.tryAcquire(1L, 3);
		assertTrue(quota.tryAcquire(50L, 3));
		assertTrue(quota.tryAcquire(50L, 3));
		assertTrue(quota.tryAcquire(50L, 3));
		assertFalse(quota.tryAcquire(50L, 3), "the quiet ticks 2..49 must not leave extra grants for tick 50");
	}

	@Test
	void aZeroOrNegativeKnobStillLetsOneWorkerWalk() {
		PathQuota quota = new PathQuota();
		assertTrue(quota.tryAcquire(7L, 0));
		assertFalse(quota.tryAcquire(7L, 0));
		assertTrue(quota.tryAcquire(8L, -5));
	}
}
