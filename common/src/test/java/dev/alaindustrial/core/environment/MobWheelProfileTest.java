package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.environment.MobWheelProfile.Bonus;
import dev.alaindustrial.core.environment.MobWheelProfile.Surroundings;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 tests for {@link MobWheelProfile} (MOD-763, D3/D4/D8): the roster and its lookup, each species' numbers,
 * pace rule, bonus condition and trait, and the roster-wide knobs.
 *
 * @implements mob wheel species profiles and pace rules
 */
class MobWheelProfileTest {

	private static final List<String> ROSTER = List.of("minecraft:chicken", "minecraft:pig", "minecraft:sheep",
			"minecraft:cow", "minecraft:goat", "minecraft:villager", "minecraft:zombie", "minecraft:zombie_villager",
			"minecraft:husk", "minecraft:drowned", "minecraft:skeleton", "minecraft:stray", "minecraft:bogged",
			"minecraft:parched", "minecraft:witch", "minecraft:vindicator", "minecraft:pillager", "minecraft:evoker",
			"minecraft:piglin", "minecraft:piglin_brute", "minecraft:zombified_piglin", "minecraft:creeper");

	@Test
	void theRosterIsExactlyTheDesignedTypes() {
		assertEquals(ROSTER, MobWheelProfile.RUNNER_TYPE_IDS);
		assertEquals(MobWheelProfile.ZOMBIE, MobWheelProfile.byEntityTypeId("minecraft:zombie_villager"));
		// Too big for the passage (D8), or not a runner at all.
		for (String other : new String[] {"minecraft:hoglin", "minecraft:wither_skeleton", "minecraft:spider",
				"minecraft:wolf", "minecraft:iron_golem", "pig", ""}) {
			assertNull(MobWheelProfile.byEntityTypeId(other), other);
		}
	}

	@Test
	void everyRunnerTypeHasItsOwnIndexAndMapsBackToItsProfile() {
		for (int i = 0; i < MobWheelProfile.RUNNER_TYPE_IDS.size(); i++) {
			String id = MobWheelProfile.runnerTypeId(i);
			assertEquals(i, MobWheelProfile.runnerIndex(id));
			assertTrue(MobWheelProfile.byEntityTypeId(id).entityTypeIds().contains(id), id);
		}
		assertEquals(-1, MobWheelProfile.runnerIndex("minecraft:hoglin"));
		assertEquals(-1, MobWheelProfile.runnerIndex(null));
		assertNull(MobWheelProfile.runnerTypeId(-1));
		assertNull(MobWheelProfile.runnerTypeId(MobWheelProfile.RUNNER_TYPE_IDS.size()));
		assertEquals(MobWheelProfile.ZOMBIE, MobWheelProfile.byName("ZOMBIE"));
		assertNull(MobWheelProfile.byName("zombie"));
	}

	@ParameterizedTest
	@CsvSource({ "CHICKEN, 24000, 1000", "PIG, 6000, 1000", "SHEEP, 12000, 1000", "COW, 9000, 1500",
			"GOAT, 2400, 2000", "VILLAGER, 6000, 3000", "ZOMBIE, 4800, 500", "HUSK, 6000, 500",
			"DROWNED, 4800, 500", "SKELETON, 6000, 1000", "STRAY, 6000, 1000", "BOGGED, 6000, 1000",
			"PARCHED, 6000, 1000", "WITCH, 7200, 1000", "VINDICATOR, 3600, 1000", "PILLAGER, 6000, 1000",
			"EVOKER, 4800, 1000", "PIGLIN, 6000, 1000", "PIGLIN_BRUTE, 4800, 500", "ZOMBIFIED_PIGLIN, 7200, 500",
			"CREEPER, 12000, 2000" })
	void staminaAndRestFollowTheTable(MobWheelProfile profile, int stamina, int restPermille) {
		assertEquals(stamina, profile.staminaTicks());
		assertEquals(restPermille, profile.restPermille());
	}

	@Test
	void theCreeperIsTheStrongestAndTheChickenTheWeakest() {
		for (MobWheelProfile profile : MobWheelProfile.values()) {
			assertTrue(MobWheelProfile.CREEPER.basePowerEuPerTick() >= profile.basePowerEuPerTick(), profile.name());
			assertTrue(MobWheelProfile.CHICKEN.basePowerEuPerTick() <= profile.basePowerEuPerTick(), profile.name());
			assertTrue(MobWheelProfile.CHICKEN.baseStaminaTicks() >= profile.baseStaminaTicks(), profile.name());
		}
	}

	@Test
	void staminaMultiplierScalesEveryStaminaAndNeverReachesZero() {
		assertEquals(3000, MobWheelProfile.PIG.staminaTicks(50));
		assertEquals(48000, MobWheelProfile.CHICKEN.staminaTicks(200));
		assertEquals(1, MobWheelProfile.GOAT.staminaTicks(0), "never zero: a fresh mob runs at least a tick");
		assertEquals(1, MobWheelProfile.GOAT.staminaTicks(-10));
	}

	@Test
	void eachBonusFollowsItsOwnCondition() {
		Surroundings night = new Surroundings(true, false, false, false);
		Surroundings rain = new Surroundings(false, true, false, false);
		Surroundings snowy = new Surroundings(false, false, true, false);
		Surroundings hotDry = new Surroundings(false, false, false, true);
		Surroundings all = new Surroundings(true, true, true, true);
		assertTrue(MobWheelProfile.ZOMBIE.bonusApplies(night));
		assertFalse(MobWheelProfile.ZOMBIE.bonusApplies(rain));
		assertFalse(MobWheelProfile.HUSK.bonusApplies(all), "a husk has no night bonus");
		assertTrue(MobWheelProfile.DROWNED.bonusApplies(rain));
		assertTrue(MobWheelProfile.BOGGED.bonusApplies(rain));
		assertFalse(MobWheelProfile.DROWNED.bonusApplies(night));
		assertTrue(MobWheelProfile.STRAY.bonusApplies(snowy));
		assertFalse(MobWheelProfile.STRAY.bonusApplies(rain));
		assertTrue(MobWheelProfile.PARCHED.bonusApplies(hotDry));
		assertFalse(MobWheelProfile.PARCHED.bonusApplies(snowy));
		for (MobWheelProfile profile : MobWheelProfile.values()) {
			assertFalse(profile.bonusApplies(Surroundings.PLAIN), profile.name());
			assertEquals(profile.bonus() != Bonus.NONE, profile.bonusApplies(all), profile.name());
		}
	}

	@Test
	void traitsBelongToTheirSpecies() {
		assertEquals(MobWheelProfile.Trait.SELF_RESTORE, MobWheelProfile.WITCH.trait());
		assertEquals(MobWheelProfile.Trait.NO_ZOMBIFICATION, MobWheelProfile.PIGLIN.trait());
		assertEquals(MobWheelProfile.Trait.NO_ZOMBIFICATION, MobWheelProfile.PIGLIN_BRUTE.trait());
		assertEquals(MobWheelProfile.Trait.NO_SWELL, MobWheelProfile.CREEPER.trait());
		assertEquals(MobWheelProfile.Trait.NONE, MobWheelProfile.ZOMBIFIED_PIGLIN.trait());
	}

	@Test
	void steadyAndFastRunnersNeverChangePace() {
		for (int tick = 0; tick < 1000; tick++) {
			assertEquals(1.0, MobWheelProfile.PIG.pace(tick, tick * 31L));
			assertEquals(1.0, MobWheelProfile.CREEPER.pace(tick, 7L));
			assertEquals(1.0, MobWheelProfile.GOAT.pace(tick, 7L));
		}
	}

	@Test
	void pausingRunnersRunThenPauseEveryCycle() {
		int cycle = MobWheelProfile.PAUSING_RUN_TICKS + MobWheelProfile.PAUSING_PAUSE_TICKS;
		for (MobWheelProfile profile : new MobWheelProfile[] {MobWheelProfile.VILLAGER, MobWheelProfile.EVOKER}) {
			assertEquals(1.0, profile.pace(0, 0L));
			assertEquals(1.0, profile.pace(MobWheelProfile.PAUSING_RUN_TICKS - 1, 0L));
			assertEquals(0.0, profile.pace(MobWheelProfile.PAUSING_RUN_TICKS, 0L));
			assertEquals(0.0, profile.pace(cycle - 1, 0L));
			assertEquals(1.0, profile.pace(cycle, 0L));
			int running = 0;
			for (int tick = 0; tick < cycle * 10; tick++) {
				running += profile.pace(tick, 0L) > 0 ? 1 : 0;
			}
			assertEquals(MobWheelProfile.PAUSING_RUN_TICKS * 10, running);
		}
	}

	@Test
	void raggedPaceIsHeldForAStepAndStaysInRange() {
		Set<Double> seen = new HashSet<>();
		double sum = 0;
		int steps = 20_000;
		for (int step = 0; step < steps; step++) {
			int first = step * MobWheelProfile.RAGGED_PACE_STEP_TICKS;
			double pace = MobWheelProfile.ZOMBIE.pace(first, 99L);
			for (int t = 1; t < MobWheelProfile.RAGGED_PACE_STEP_TICKS; t++) {
				assertEquals(pace, MobWheelProfile.ZOMBIE.pace(first + t, 99L), "pace changes only at a step");
			}
			assertTrue(pace >= 0.8 - 1e-12 && pace <= 1.2 + 1e-12, "pace " + pace);
			seen.add(Math.round(pace * 100) / 100.0);
			sum += pace;
		}
		assertEquals(9, seen.size(), "0.80, 0.85, … 1.20 all occur: " + seen);
		assertEquals(1.0, sum / steps, 0.01, "the average pace is 1");
		assertTrue(seen.contains(0.8) && seen.contains(1.2), "both bounds are reachable");
	}

	@Test
	void raggedSeedChangesTheSequence() {
		int differ = 0;
		for (int step = 0; step < 100; step++) {
			int tick = step * MobWheelProfile.RAGGED_PACE_STEP_TICKS;
			if (MobWheelProfile.ZOMBIE.pace(tick, 1L) != MobWheelProfile.ZOMBIE.pace(tick, 2L)) {
				differ++;
			}
		}
		assertTrue(differ > 50, "two seeds should stumble differently, got " + differ + " differing steps");
	}
}
