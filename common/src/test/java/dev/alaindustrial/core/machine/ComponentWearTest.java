package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link ComponentWear#step} — the pure wear arithmetic backing the wind mill rotor /
 * water mill wheel wear (MOD-189). No Minecraft runtime; deterministic. Constants are literal so the
 * assertions pin real numbers (a tautological {@code f(x) == f(x)} would pass without the fix).
 */
class ComponentWearTest {

	// --- accumulation below the threshold: no damage yet ---

	@Test
	void belowThresholdAccumulatesWithoutDamage() {
		ComponentWear.Result r = ComponentWear.step(0, 100, 1.0f, 480, 0, 1000);
		assertEquals(100, r.accumulatorEu(), "100 EU accrued, none spent (100 < 480)");
		assertEquals(0, r.newDamage(), "no durability point spent below the threshold");
		assertFalse(r.broken(), "not broken");
	}

	@Test
	void carriedAccumulatorAddsUp() {
		// 380 carried + 100 this tick = 480 → exactly one point, remainder 0.
		ComponentWear.Result r = ComponentWear.step(380, 100, 1.0f, 480, 0, 1000);
		assertEquals(0, r.accumulatorEu(), "480 total spends exactly one 480-EU point, 0 remainder");
		assertEquals(1, r.newDamage(), "one durability point spent");
		assertFalse(r.broken());
	}

	@Test
	void remainderCarriesToNextTick() {
		// 400 carried + 100 = 500 → one point (480), remainder 20.
		ComponentWear.Result r = ComponentWear.step(400, 100, 1.0f, 480, 5, 1000);
		assertEquals(20, r.accumulatorEu(), "500 − 480 = 20 EU carried");
		assertEquals(6, r.newDamage(), "damage advanced 5 → 6");
		assertFalse(r.broken());
	}

	@Test
	void largeTickSpendsMultiplePoints() {
		// 1000 EU at 480/point = 2 points, remainder 40.
		ComponentWear.Result r = ComponentWear.step(0, 1000, 1.0f, 480, 0, 1000);
		assertEquals(40, r.accumulatorEu(), "1000 − 2×480 = 40 EU carried");
		assertEquals(2, r.newDamage(), "two durability points spent in one big tick");
		assertFalse(r.broken());
	}

	// --- weather multiplier ---

	@Test
	void weatherFactorScalesTheAddedEu() {
		// 100 EU × 1.5 = 150 EU accrued this tick.
		ComponentWear.Result r = ComponentWear.step(0, 100, 1.5f, 480, 0, 1000);
		assertEquals(150, r.accumulatorEu(), "100 × 1.5 = 150 EU of wear");
		assertEquals(0, r.newDamage());
		assertFalse(r.broken());
	}

	@Test
	void weatherFactorCanPushAcrossTheThreshold() {
		// 400 carried + 100×1.5=150 = 550 → one point, remainder 70.
		ComponentWear.Result r = ComponentWear.step(400, 100, 1.5f, 480, 0, 1000);
		assertEquals(70, r.accumulatorEu(), "550 − 480 = 70 EU carried");
		assertEquals(1, r.newDamage(), "the storm multiplier tipped it over one point");
		assertFalse(r.broken());
	}

	// --- the per-tick floor: at least 1 EU of wear ---

	@Test
	void atLeastOneEuOfWearPerActiveTick() {
		// producedEu 1 × factor 0.4 rounds to 0, but the floor keeps it at 1 so wear never stalls.
		ComponentWear.Result r = ComponentWear.step(0, 1, 0.4f, 480, 0, 1000);
		assertEquals(1, r.accumulatorEu(), "sub-1 wear is floored to 1 EU so a tiny output still wears");
	}

	// --- breakage ---

	@Test
	void reachingMaxDamageBreaks() {
		// 479 carried + 1 = 480 → one point, 999 → 1000 == max → broken.
		ComponentWear.Result r = ComponentWear.step(479, 1, 1.0f, 480, 999, 1000);
		assertTrue(r.broken(), "damage reached maxDamage → broken");
		assertEquals(1000, r.newDamage(), "damage clamped to maxDamage on break");
		assertEquals(0, r.accumulatorEu(), "accumulator cleared on break");
	}

	@Test
	void overshootStillClampsToMaxOnBreak() {
		// A huge tick that would overshoot maxDamage still reports exactly maxDamage and broken.
		ComponentWear.Result r = ComponentWear.step(0, 100_000, 1.0f, 1, 998, 1000);
		assertTrue(r.broken(), "an overshooting tick breaks");
		assertEquals(1000, r.newDamage(), "damage never exceeds maxDamage");
	}

	@Test
	void oneShortOfMaxDoesNotBreak() {
		// 999 damage with less than one full point of wear this tick stays alive at 999.
		ComponentWear.Result r = ComponentWear.step(0, 100, 1.0f, 480, 999, 1000);
		assertFalse(r.broken(), "still under maxDamage — alive");
		assertEquals(999, r.newDamage(), "no point spent, damage unchanged");
		assertEquals(100, r.accumulatorEu());
	}
}
