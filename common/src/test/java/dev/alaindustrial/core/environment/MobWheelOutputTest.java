package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 tests for {@link MobWheelOutput} (MOD-763): the "mob → EU/t" mapping behind the mob wheel's
 * {@code produce()}. Run against the shipped knob defaults (EU and stamina multipliers 100 %, bonus +50 %) and
 * the species table of {@link MobWheelProfile} — the boundary values the spec and the docs quote.
 *
 * @implements mob wheel output = floor(power × euMultiplier × bonusFactor × pace × (0.5 + 0.5 × stamina))
 */
class MobWheelOutputTest {

	@ParameterizedTest
	@CsvSource({ "CHICKEN, 1", "PIG, 5", "SHEEP, 3", "COW, 4", "GOAT, 8", "VILLAGER, 7", "ZOMBIE, 7",
			"HUSK, 7", "DROWNED, 6", "SKELETON, 6", "STRAY, 6", "BOGGED, 6", "PARCHED, 6", "WITCH, 5",
			"VINDICATOR, 8", "PILLAGER, 6", "EVOKER, 9", "PIGLIN, 6", "PIGLIN_BRUTE, 9", "ZOMBIFIED_PIGLIN, 6",
			"CREEPER, 10" })
	void freshMobAtPaceOneWithoutBonusMakesItsPower(MobWheelProfile profile, int expected) {
		assertEquals(expected, MobWheelOutput.euFor(profile.basePowerEuPerTick(), 100, 0, 1.0, 1.0));
	}

	@Test
	void dayZombieAtTopPaceFloorsToEight() {
		// 7 × 1.2 = 8.4 — rounded DOWN, never to the nearest.
		assertEquals(8, MobWheelOutput.euFor(7, 100, 0, MobWheelProfile.RAGGED_PACE_MAX, 1.0));
	}

	@Test
	void nightZombieAtTopPaceFloorsToTwelve() {
		// 7 × 1.5 × 1.2 = 12.6 → 12: exactly a copper cable, never 13.
		assertEquals(12, MobWheelOutput.euFor(7, 100, 50, MobWheelProfile.RAGGED_PACE_MAX, 1.0));
	}

	@Test
	void bonusIsFlooredOnceAtTheEnd() {
		assertEquals(10, MobWheelOutput.euFor(7, 100, 50, 1.0, 1.0), "7 × 1.5 = 10.5 → 10");
		assertEquals(8, MobWheelOutput.euFor(7, 100, 50, MobWheelProfile.RAGGED_PACE_MIN, 1.0), "10.5 × 0.8 = 8.4");
		assertEquals(5, MobWheelOutput.euFor(7, 100, 0, MobWheelProfile.RAGGED_PACE_MIN, 1.0), "7 × 0.8 = 5.6 → 5");
		assertEquals(6, MobWheelOutput.euFor(7, 100, 50, MobWheelProfile.RAGGED_PACE_MAX, 0.0), "12.6 / 2 = 6.3");
		assertEquals(9, MobWheelOutput.euFor(7, 100, 50, MobWheelProfile.RAGGED_PACE_MAX, 0.5), "12.6 × .75");
	}

	@Test
	void euMultiplierScalesBeforeTheFloor() {
		assertEquals(10, MobWheelOutput.euFor(5, 200, 0, 1.0, 1.0), "pig at 200 %");
		assertEquals(2, MobWheelOutput.euFor(5, 50, 0, 1.0, 1.0), "pig at 50 %: 2.5 → 2");
		assertEquals(15, MobWheelOutput.euFor(10, 150, 0, 1.0, 1.0), "creeper at 150 %");
		assertEquals(0, MobWheelOutput.euFor(10, 0, 0, 1.0, 1.0), "0 % switches the wheel off");
		assertEquals(0, MobWheelOutput.euFor(10, -5, 50, 1.0, 1.0));
	}

	@Test
	void zeroStaminaIsHalfPowerFlooredNotZero() {
		assertEquals(2, MobWheelOutput.euFor(5, 100, 0, 1.0, 0.0), "pig 5 × 0.5 = 2.5 → 2");
		assertEquals(0, MobWheelOutput.euFor(1, 100, 0, 1.0, 0.0), "chicken 1 × 0.5 = 0.5 → 0");
		assertEquals(5, MobWheelOutput.euFor(10, 100, 0, 1.0, 0.0), "creeper 10 × 0.5 = 5");
		assertEquals(4, MobWheelOutput.euFor(7, 100, 0, MobWheelProfile.RAGGED_PACE_MAX, 0.0), "4.2 → 4");
	}

	@Test
	void staminaFractionIsClamped() {
		assertEquals(5, MobWheelOutput.euFor(5, 100, 0, 1.0, 7.0));
		assertEquals(2, MobWheelOutput.euFor(5, 100, 0, 1.0, -3.0));
	}

	@Test
	void pauseOrNoPowerGivesNothing() {
		assertEquals(0, MobWheelOutput.euFor(5, 100, 0, 0.0, 1.0), "a pause makes nothing");
		assertEquals(0, MobWheelOutput.euFor(5, 100, 0, -1.0, 1.0));
		assertEquals(0, MobWheelOutput.euFor(5, 100, 0, Double.NaN, 1.0));
		assertEquals(0, MobWheelOutput.euFor(0, 100, 50, 1.0, 1.0));
		assertEquals(0, MobWheelOutput.euFor(-4, 100, 50, 1.0, 1.0));
		assertEquals(5, MobWheelOutput.euFor(5, 100, -50, 1.0, 1.0), "a negative bonus counts as none");
	}

	@Test
	void notRunningGivesNothingWhateverTheMob() {
		for (MobWheelProfile profile : MobWheelProfile.values()) {
			assertEquals(0, MobWheelOutput.euFor(profile, false, true, 0, 1L, 1.0), profile.name());
		}
	}

	@Test
	void pausingRunnersMakeNothingInTheirPause() {
		int pauseTick = MobWheelProfile.PAUSING_RUN_TICKS;
		assertEquals(0, MobWheelOutput.euFor(MobWheelProfile.VILLAGER, true, false, pauseTick, 0L, 1.0));
		assertEquals(0, MobWheelOutput.euFor(MobWheelProfile.EVOKER, true, false, pauseTick, 0L, 1.0));
	}

	@Test
	void theGoatsFastPaceIsVisualOnly() {
		assertEquals(8, MobWheelOutput.euFor(MobWheelProfile.GOAT, true, false, 123, 9L, 1.0));
		assertEquals(2, MobWheelProfile.GOAT.spinFactor());
		assertEquals(1, MobWheelProfile.PIG.spinFactor());
	}

	@Test
	void peaksPerSpecies() {
		assertEquals(1, MobWheelOutput.peakEuPerTick(MobWheelProfile.CHICKEN));
		assertEquals(10, MobWheelOutput.peakEuPerTick(MobWheelProfile.CREEPER));
		assertEquals(10, MobWheelOutput.peakEuPerTick(MobWheelProfile.PIGLIN_BRUTE), "9 × 1.2 = 10.8 → 10");
		assertEquals(10, MobWheelOutput.peakEuPerTick(MobWheelProfile.DROWNED), "6 × 1.5 × 1.2 = 10.8 → 10");
		assertEquals(9, MobWheelOutput.peakEuPerTick(MobWheelProfile.STRAY), "6 × 1.5");
		assertEquals(12, MobWheelOutput.peakEuPerTick(MobWheelProfile.ZOMBIE));
		assertEquals(8, MobWheelOutput.peakEuPerTick(MobWheelProfile.HUSK), "no night bonus: 7 × 1.2 = 8.4 → 8");
	}

	@Test
	void theWheelsCeilingIsTheNightZombieAndFitsACopperCable() {
		assertEquals(12, MobWheelOutput.peakEuPerTick());
	}

	@Test
	void everyZombiePaceStaysWithinTheDayAndNightCeilings() {
		int dayMax = 0;
		int nightMax = 0;
		for (int tick = 0; tick < 20 * 2000; tick += MobWheelProfile.RAGGED_PACE_STEP_TICKS) {
			int day = MobWheelOutput.euFor(MobWheelProfile.ZOMBIE, true, false, tick, 42L, 1.0);
			int night = MobWheelOutput.euFor(MobWheelProfile.ZOMBIE, true, true, tick, 42L, 1.0);
			assertTrue(day >= 5 && day <= 8, "day zombie " + day + " at tick " + tick);
			assertTrue(night >= 8 && night <= 12, "night zombie " + night + " at tick " + tick);
			dayMax = Math.max(dayMax, day);
			nightMax = Math.max(nightMax, night);
		}
		assertEquals(8, dayMax, "the day ceiling is reached");
		assertEquals(12, nightMax, "the night ceiling is reached");
	}
}
