package dev.alaindustrial.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.entity.TemperedGearRoll.EquipPlan;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link TemperedGearRoll} — the natural mob-spawn tempered-iron decision (MOD-130).
 * Pure math, no Minecraft on the classpath. Pins both the balance thresholds and the exact order in
 * which random values are consumed, so a reorder or a constant change is caught here.
 *
 * <p>The in-game caller feeds {@code RandomSource::nextDouble}; here a scripted sequence stands in.
 */
class TemperedGearRollTest {

	/** A DoubleSupplier that replays a fixed script; throws if the code draws more values than scripted. */
	private static DoubleSupplier script(double... values) {
		return new DoubleSupplier() {
			private int i = 0;

			@Override
			public double getAsDouble() {
				if (i >= values.length) {
					throw new AssertionError("decide() drew more random values than the test scripted");
				}
				return values[i++];
			}
		};
	}

	/** Every roll succeeds → a zombie gets the full tempered set plus a sword. Order: armor, 3×continue, weapon. */
	@Test
	void zombieAllSuccessGetsFullSetAndSword() {
		EquipPlan plan = TemperedGearRoll.decide(true, script(0.0, 0.0, 0.0, 0.0, 0.0), 1.0f);
		assertEquals(new EquipPlan(true, true, true, true, true), plan);
		assertTrue(plan.any());
	}

	/** Every roll fails → nothing. Only two draws happen: the armor gate, then the weapon gate. */
	@Test
	void zombieAllFailGetsNothing() {
		EquipPlan plan = TemperedGearRoll.decide(true, script(0.99, 0.99), 1.0f);
		assertEquals(EquipPlan.NONE, plan);
		assertFalse(plan.any());
	}

	/** Skeletons never draw or receive a weapon, even when every roll would pass. */
	@Test
	void skeletonNeverGetsWeapon() {
		// 4 draws only: armor gate + 3 continues. A 5th (weapon) draw would throw via the script guard.
		EquipPlan plan = TemperedGearRoll.decide(false, script(0.0, 0.0, 0.0, 0.0), 1.0f);
		assertEquals(new EquipPlan(true, true, true, true, false), plan);
		assertFalse(plan.weapon());
	}

	/** The continue-chance stops the set on the first miss: helmet+chest, then a miss drops legs/boots. */
	@Test
	void partialSetStopsOnFirstContinueMiss() {
		// armor hit, chest continue hit, legs continue MISS, then weapon gate (zombie).
		EquipPlan plan = TemperedGearRoll.decide(true, script(0.0, 0.0, 0.9, 0.99), 1.0f);
		assertEquals(new EquipPlan(true, true, false, false, false), plan);
	}

	/** The armor gate scales with the difficulty multiplier: same draw passes at full, fails at half. */
	@Test
	void armorGateScalesWithDifficulty() {
		// At full: draw 0.03 < 0.04 (armor passes) → a continue draw (0.99 miss) → a weapon draw (0.99 miss).
		EquipPlan atFull = TemperedGearRoll.decide(true, script(0.03, 0.99, 0.99), 1.0f);
		assertTrue(atFull.helmet(), "0.03 < 0.04 at full difficulty → helmet");

		// At half: 0.03 > 0.04×0.5=0.02 (armor fails, no continue draw) → weapon draw (0.99 miss).
		EquipPlan atHalf = TemperedGearRoll.decide(true, script(0.03, 0.99), 0.5f);
		assertFalse(atHalf.helmet(), "0.03 > 0.02 at half difficulty → no armor");
	}

	/** The weapon gate also scales with difficulty and sits at the documented 2 % base. */
	@Test
	void weaponGateScalesWithDifficulty() {
		// armor gate fails (0.99); weapon draw 0.015: below 0.02×1.0 (pass), above 0.02×0.5=0.01 (fail).
		assertTrue(TemperedGearRoll.decide(true, script(0.99, 0.015), 1.0f).weapon());
		assertFalse(TemperedGearRoll.decide(true, script(0.99, 0.015), 0.5f).weapon());
	}
}
