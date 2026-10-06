package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link MobWheelDistraction} (MOD-763, D9): only a live player target within six blocks
 * distracts the occupant.
 *
 * @implements mob wheel distraction by a player target
 */
class MobWheelDistractionTest {

	@Test
	void aLivePlayerTargetWithinSixBlocksDistracts() {
		assertTrue(MobWheelDistraction.isDistracted(true, 0.0));
		assertTrue(MobWheelDistraction.isDistracted(true, 36.0), "exactly six blocks still counts");
		assertFalse(MobWheelDistraction.isDistracted(true, 36.01), "just beyond six blocks does not");
	}

	@Test
	void noPlayerTargetNeverDistracts() {
		assertFalse(MobWheelDistraction.isDistracted(false, 0.0));
		assertFalse(MobWheelDistraction.isDistracted(false, 4.0));
	}
}
