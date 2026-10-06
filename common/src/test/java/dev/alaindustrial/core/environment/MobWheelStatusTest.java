package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link MobWheelStatus} (MOD-763): the sync ordinal round-trips, out-of-range reads as
 * unformed, and only the states the player must act on are blocking.
 *
 * @implements mob wheel status line
 */
class MobWheelStatusTest {

	@Test
	void ordinalRoundTripsAndOutOfRangeIsUnformed() {
		for (MobWheelStatus status : MobWheelStatus.values()) {
			assertEquals(status, MobWheelStatus.byOrdinal(status.ordinal()));
		}
		assertEquals(MobWheelStatus.UNFORMED, MobWheelStatus.byOrdinal(-1));
		assertEquals(MobWheelStatus.UNFORMED, MobWheelStatus.byOrdinal(MobWheelStatus.values().length));
	}

	@Test
	void keysAndBlocking() {
		assertEquals("gui.alaindustrial.mob_wheel.status.no_mob", MobWheelStatus.NO_MOB.translationKey());
		assertTrue(MobWheelStatus.UNFORMED.isBlocking());
		assertTrue(MobWheelStatus.NO_MOB.isBlocking());
		assertTrue(MobWheelStatus.EXHAUSTED.isBlocking());
		assertTrue(MobWheelStatus.LOST.isBlocking());
		assertFalse(MobWheelStatus.RUNNING.isBlocking());
		assertFalse(MobWheelStatus.PAUSED.isBlocking());
		assertFalse(MobWheelStatus.RESTING.isBlocking());
		assertTrue(MobWheelStatus.DISTRACTED.isBlocking());
		assertEquals("gui.alaindustrial.mob_wheel.status.distracted", MobWheelStatus.DISTRACTED.translationKey());
	}

	/** The ordinal is the sync contract: DISTRACTED (D9) was appended, nothing before it moved. */
	@Test
	void distractedIsAppendedLast() {
		assertEquals(7, MobWheelStatus.DISTRACTED.ordinal());
		assertEquals(6, MobWheelStatus.LOST.ordinal());
		assertEquals(MobWheelStatus.values().length - 1, MobWheelStatus.DISTRACTED.ordinal());
	}
}
