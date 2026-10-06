package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link MobWheelStamina} (MOD-763, D4/D5): running spends, exhaustion stops, rest restores
 * only an exhausted mob and resumes only at full, food works only on an exhausted mob.
 *
 * @implements mob wheel stamina transitions
 */
class MobWheelStaminaTest {

	@Test
	void freshMobIsFullAndRuns() {
		MobWheelStamina s = MobWheelStamina.full(100);
		assertTrue(s.canRun());
		assertEquals(1.0, s.fraction());
		assertEquals(1000, s.permille());
	}

	@Test
	void runningSpendsOneTickAtATimeAndExhaustsAtZero() {
		MobWheelStamina s = MobWheelStamina.full(3);
		s.runTick();
		s.runTick();
		assertTrue(s.canRun());
		assertEquals(1, s.remaining());
		s.runTick();
		assertEquals(0, s.remaining());
		assertTrue(s.exhausted());
		assertFalse(s.canRun());
		s.runTick();
		assertEquals(0, s.remaining(), "an exhausted mob spends nothing more");
	}

	@Test
	void restDoesNothingToAMobThatIsNotExhausted() {
		MobWheelStamina s = MobWheelStamina.full(10);
		s.runTick();
		s.restTick(1000, 4, 1);
		assertEquals(9, s.remaining(), "only an exhausted mob rests");
	}

	@Test
	void fullRestTakesRestTicksAtRateOneAndResumesOnlyAtFull() {
		MobWheelStamina s = exhausted(6000);
		int ticks = 0;
		while (s.exhausted()) {
			s.restTick(1000, 1, 24000);
			ticks++;
			if (s.exhausted()) {
				assertFalse(s.canRun(), "a half-rested mob does not run");
			}
		}
		assertEquals(24000, ticks);
		assertEquals(6000, s.remaining());
		assertTrue(s.canRun());
	}

	@Test
	void speciesRateAndHayShortenTheRest() {
		assertEquals(8000, restTicks(3000, 1), "villager x3");
		assertEquals(48000, restTicks(500, 1), "zombie x0.5");
		assertEquals(6000, restTicks(1000, 4), "hay x4");
		assertEquals(2000, restTicks(3000, 4), "villager on hay");
	}

	@Test
	void slowRestLosesNothingToRounding() {
		// max 7 over 24000 ticks gains far less than one point per tick; the accumulator must still deliver 7.
		MobWheelStamina s = exhausted(7);
		int ticks = 0;
		while (s.exhausted()) {
			s.restTick(1000, 1, 24000);
			ticks++;
		}
		assertEquals(24000, ticks);
	}

	@Test
	void feedingRefillsAnExhaustedMobAtOnce() {
		MobWheelStamina s = exhausted(50);
		assertTrue(s.feed());
		assertEquals(50, s.remaining());
		assertTrue(s.canRun());
	}

	@Test
	void feedingIsRefusedUntilExhausted() {
		MobWheelStamina s = MobWheelStamina.full(50);
		assertFalse(s.feed(), "a fresh mob eats nothing");
		s.runTick();
		assertFalse(s.feed(), "a tired but running mob eats nothing either");
		assertEquals(49, s.remaining());
	}

	@Test
	void restoreClampsAndDerivesExhaustion() {
		MobWheelStamina s = MobWheelStamina.restore(10, 99, false, -5);
		assertEquals(10, s.remaining());
		assertEquals(0L, s.restAccumulator());
		MobWheelStamina empty = MobWheelStamina.restore(10, 0, false, 0);
		assertTrue(empty.exhausted(), "zero stamina is exhausted whatever the flag said");
		MobWheelStamina zeroMax = MobWheelStamina.restore(0, 0, false, 0);
		assertEquals(1, zeroMax.max());
	}

	/** D8: a witch restores a quarter of its stamina once per run, at a quarter left, never above full. */
	@Test
	void selfRestoreHappensOncePerRunAtTheThreshold() {
		MobWheelStamina s = MobWheelStamina.full(100);
		for (int i = 0; i < 74; i++) {
			s.runTick();
		}
		assertEquals(26, s.remaining());
		assertFalse(s.selfRestore(25, 25), "26 % left is above the threshold");
		s.runTick();
		assertTrue(s.selfRestore(25, 25), "25 % left is the threshold");
		assertEquals(50, s.remaining());
		assertTrue(s.selfRestored());
		while (s.remaining() > 1) {
			s.runTick();
		}
		assertFalse(s.selfRestore(25, 25), "only once per run");
		s.runTick();
		assertTrue(s.exhausted());
		assertFalse(s.selfRestored(), "exhaustion ends the run");
		assertFalse(s.selfRestore(25, 25), "an exhausted mob does not restore itself");
		assertTrue(s.feed());
		for (int i = 0; i < 80; i++) {
			s.runTick();
		}
		assertTrue(s.selfRestore(25, 25), "the next run may restore again");
		MobWheelStamina nearFull = MobWheelStamina.full(100);
		assertTrue(nearFull.selfRestore(100, 25));
		assertEquals(100, nearFull.remaining(), "never above the maximum");
	}

	@Test
	void selfRestoredFlagSurvivesASaveOfARunningMobOnly() {
		assertTrue(MobWheelStamina.restore(100, 40, false, 0L, true).selfRestored());
		assertFalse(MobWheelStamina.restore(100, 0, true, 0L, true).selfRestored(), "an exhausted mob has no run");
	}

	private static MobWheelStamina exhausted(int max) {
		return MobWheelStamina.restore(max, 0, true, 0L);
	}

	private static int restTicks(int permille, int hay) {
		MobWheelStamina s = exhausted(6000);
		int ticks = 0;
		while (s.exhausted()) {
			s.restTick(permille, hay, 24000);
			ticks++;
		}
		return ticks;
	}
}
