package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link LightningRodOutput} (MOD-386) — the lightning rod's arithmetic, asserted
 * without the game on the classpath.
 *
 * <p>Every expectation here is a hand-written literal, never recomputed from the production formula:
 * a test that says {@code assertEquals(base * mult, capacityFor(base, mult, cap))} restates the code
 * and passes for any code (the tautology trap this repo has already been bitten by).
 */
class LightningRodOutputTest {

	// --- capacityFor / bleedFor: the multiplier goes in BEFORE the clamp ---

	@Test
	void gradeMultiplierScalesCapacityAndBleed() {
		// 20 000 × 1.25 = 25 000, well under the 32 000 ceiling, so the multiplier is visible.
		assertEquals(25_000, LightningRodOutput.capacityFor(20_000, 1.25f, 32_000));
		// 16 × 1.5 = 24, under the LV ceiling of 32.
		assertEquals(24, LightningRodOutput.bleedFor(16, 1.5f, 32));
	}

	@Test
	void baselineMultiplierChangesNothing() {
		assertEquals(20_000, LightningRodOutput.capacityFor(20_000, 1.0f, 32_000));
		assertEquals(16, LightningRodOutput.bleedFor(16, 1.0f, 32));
	}

	/**
	 * The ceiling holds for ANY multiplier, which is the whole reason the scale is applied inside these
	 * methods rather than at the call site. Exhaustive over a range no operator would ever configure,
	 * so the property is proven rather than sampled at today's three grades.
	 */
	@Test
	void noMultiplierHoweverAbsurdCanBreachTheCeiling() {
		for (float multiplier = 0.0f; multiplier <= 100.0f; multiplier += 0.25f) {
			assertTrue(LightningRodOutput.capacityFor(20_000, multiplier, 32_000) <= 32_000,
					"capacity breached its ceiling at multiplier " + multiplier);
			assertTrue(LightningRodOutput.bleedFor(16, multiplier, 32) <= 32,
					"bleed breached the LV ceiling at multiplier " + multiplier);
		}
	}

	/**
	 * A grade configured to 0 must still leave the tip usable. A tip that reads as installed but banks
	 * nothing is indistinguishable from a bug from inside the game, so the floor is deliberate.
	 */
	@Test
	void aZeroedMultiplierStillLeavesTheTipUsable() {
		assertEquals(1, LightningRodOutput.capacityFor(20_000, 0.0f, 32_000));
		assertEquals(1, LightningRodOutput.bleedFor(16, 0.0f, 32));
	}

	@Test
	void aZeroBaseOrCeilingYieldsNothing() {
		assertEquals(0, LightningRodOutput.capacityFor(0, 1.25f, 32_000));
		assertEquals(0, LightningRodOutput.capacityFor(20_000, 1.25f, 0));
		assertEquals(0, LightningRodOutput.bleedFor(0, 1.5f, 32));
	}

	// --- accepted(): the surplus of a strike is LOST, and that loss is the game ---

	@Test
	void anEmptyCapacitorTakesTheWholeStrike() {
		assertEquals(20_000, LightningRodOutput.accepted(20_000, 20_000));
	}

	@Test
	void aPartlyFullCapacitorTakesOnlyWhatFits() {
		// 6000 EU of room left: 14 000 EU of the strike is destroyed, not queued.
		assertEquals(6_000, LightningRodOutput.accepted(20_000, 6_000));
	}

	@Test
	void aFullCapacitorTakesNothing() {
		assertEquals(0, LightningRodOutput.accepted(20_000, 0));
		assertEquals(0, LightningRodOutput.accepted(20_000, -5));
	}

	// --- strikeDivisor(): thunder outranks rain ---

	/**
	 * The load-bearing case. {@code Level#isThundering()} implies {@code isRaining()}, so testing rain
	 * first would silently hand a thunderstorm the rain branch's far rarer odds and the storm would
	 * feel identical to drizzle — a balance bug with no crash and no log line.
	 */
	@Test
	void thunderWinsOverRainWhenBothAreTrue() {
		assertEquals(900, LightningRodOutput.strikeDivisor(true, true, 900, 4800));
	}

	@Test
	void plainRainUsesTheRainOdds() {
		assertEquals(4800, LightningRodOutput.strikeDivisor(false, true, 900, 4800));
	}

	@Test
	void clearWeatherNeverStrikes() {
		assertEquals(0, LightningRodOutput.strikeDivisor(false, false, 900, 4800));
	}

	@Test
	void aNegativeDivisorIsClampedOffRatherThanCrashingTheRoll() {
		// Guards the caller's `random.nextInt(divisor)`, which throws on a non-positive bound.
		assertEquals(0, LightningRodOutput.strikeDivisor(true, true, -1, 4800));
		assertEquals(0, LightningRodOutput.strikeDivisor(false, true, 900, -7));
	}
}
