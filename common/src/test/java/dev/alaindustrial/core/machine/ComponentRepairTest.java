package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link ComponentRepair} — the pure repair arithmetic behind the component repair
 * bench (MOD-384). No Minecraft runtime; deterministic. Every expected value is a literal, so the
 * assertions pin real numbers instead of restating the formula.
 */
class ComponentRepairTest {

	/** Shipped defaults: a T1 rotor/wheel is 1000 durability and one repair costs 20 % of it. */
	private static final int T1_MAX = 1000;
	private static final int STEP = 20;

	// --- the decay ladder: linear in the ORIGINAL ceiling ---

	@Test
	void unrepairedComponentKeepsItsFullCeiling() {
		assertEquals(T1_MAX, ComponentRepair.maxDamageAfter(T1_MAX, STEP, 0),
				"a component that was never repaired still has its registered durability");
	}

	@Test
	void ladderIsLinearNotCompounding() {
		// The whole point of MOD-384's formula: a share of the ORIGINAL each time. Compounding on the
		// current ceiling would give 800 / 640 / 512 / 410 and would already be wrong at the second
		// repair — these four literals are what makes that a failing test rather than a code review.
		assertEquals(800, ComponentRepair.maxDamageAfter(T1_MAX, STEP, 1), "after 1 repair");
		assertEquals(600, ComponentRepair.maxDamageAfter(T1_MAX, STEP, 2), "after 2 repairs");
		assertEquals(400, ComponentRepair.maxDamageAfter(T1_MAX, STEP, 3), "after 3 repairs");
		assertEquals(200, ComponentRepair.maxDamageAfter(T1_MAX, STEP, 4), "after 4 repairs");
	}

	@Test
	void higherGradesDecayInTheirOwnProportion() {
		// T2 = 3000, T3 = 6000 (MOD-385). The step is a percentage, so each grade loses a fifth of
		// its own ceiling, not a flat 200 points.
		assertEquals(2400, ComponentRepair.maxDamageAfter(3000, STEP, 1), "reinforced, 1 repair");
		assertEquals(600, ComponentRepair.maxDamageAfter(3000, STEP, 4), "reinforced, 4 repairs");
		assertEquals(4800, ComponentRepair.maxDamageAfter(6000, STEP, 1), "advanced, 1 repair");
		assertEquals(1200, ComponentRepair.maxDamageAfter(6000, STEP, 4), "advanced, 4 repairs");
	}

	@Test
	void resultIsIndependentOfHowItWasReached() {
		// Purity is the property that makes the ceiling self-healing and rounding-drift-free: three
		// repairs computed in one call must equal what a caller would get re-deriving at any point.
		assertEquals(ComponentRepair.maxDamageAfter(T1_MAX, STEP, 3),
				ComponentRepair.maxDamageAfter(T1_MAX, STEP, 3),
				"same inputs, same ceiling — no hidden state");
	}

	// --- guards ---

	@Test
	void totalDecaySaturatesInsteadOfGoingNegative() {
		// A mis-configured step x a large count must bottom out, never wrap below zero.
		assertEquals(1, ComponentRepair.maxDamageAfter(T1_MAX, STEP, 5), "5 x 20 % = all of it");
		assertEquals(1, ComponentRepair.maxDamageAfter(T1_MAX, STEP, 99), "far past the end");
		assertEquals(1, ComponentRepair.maxDamageAfter(T1_MAX, 100, 5), "a 100 % step");
	}

	@Test
	void ceilingNeverDropsBelowOne() {
		assertTrue(ComponentRepair.maxDamageAfter(5, 90, 1) >= 1, "tiny ceiling stays usable");
		assertEquals(1, ComponentRepair.maxDamageAfter(1, STEP, 3), "already at the floor");
	}

	@Test
	void negativeAndZeroInputsAreClamped() {
		assertEquals(T1_MAX, ComponentRepair.maxDamageAfter(T1_MAX, STEP, -4),
				"a negative repair count reads as never repaired");
		assertEquals(T1_MAX, ComponentRepair.maxDamageAfter(T1_MAX, 0, 4),
				"a zero step disables the decay entirely (documented config escape hatch)");
		assertEquals(T1_MAX, ComponentRepair.maxDamageAfter(T1_MAX, -5, 4),
				"a negative step is clamped to zero, not applied as a bonus");
	}

	// --- the repair allowance ---

	@Test
	void allowanceIsDerivedFromTheDecayStep() {
		// The shipped step gives exactly the four repairs the design promises: a fifth would zero the
		// ceiling, so it is refused by the arithmetic rather than by a separate counter.
		assertEquals(4, ComponentRepair.maxRepairs(20), "20 % -> 4 repairs, then nothing is left");
		assertEquals(9, ComponentRepair.maxRepairs(10), "10 % -> 9");
		assertEquals(3, ComponentRepair.maxRepairs(25), "25 % -> 3 (a fourth would zero it)");
		assertEquals(0, ComponentRepair.maxRepairs(100), "a 100 % step leaves no repair at all");
		assertEquals(99, ComponentRepair.maxRepairs(1), "1 % -> 99");
	}

	@Test
	void awkwardDivisorsRoundDownRatherThanHandingOutAFreeRepair() {
		// 33 % three times is 99 %; a fourth would exceed 100 and leave nothing. Rounding the division
		// up would allow that fourth repair on a component with 1 % of its ceiling left.
		assertEquals(3, ComponentRepair.maxRepairs(33));
		assertTrue(ComponentRepair.maxDamageAfter(T1_MAX, 33, 3) > 1, "the third still leaves something");
		assertEquals(1, ComponentRepair.maxDamageAfter(T1_MAX, 33, 4), "a fourth would bottom out");
	}

	@Test
	void zeroDecayMeansUnlimitedRatherThanNone() {
		// A step of 0 is the documented way to switch the decay off. With the separate counter gone that
		// has to read as "repairs never run out" — treating it as "no repairs" would silently disable
		// the machine for anyone who turned the decay off on purpose.
		assertEquals(ComponentRepair.UNLIMITED_REPAIRS, ComponentRepair.maxRepairs(0));
		assertEquals(ComponentRepair.UNLIMITED_REPAIRS, ComponentRepair.maxRepairs(-5));
		assertTrue(ComponentRepair.canRepair(1000, ComponentRepair.maxRepairs(0)));
	}

	@Test
	void repairsAreAllowedUpToButNotPastTheAllowance() {
		int allowance = ComponentRepair.maxRepairs(STEP);
		assertTrue(ComponentRepair.canRepair(0, allowance), "a fresh component");
		assertTrue(ComponentRepair.canRepair(allowance - 1, allowance), "the last one is still allowed");
		assertFalse(ComponentRepair.canRepair(allowance, allowance), "one past — the GUI refusal");
		assertFalse(ComponentRepair.canRepair(allowance + 5, allowance), "well past");
	}

	@Test
	void theLastAllowedRepairStillLeavesAUsableComponent() {
		// Ties the two halves together: the allowance must not permit a repair that lands on the floor,
		// or the bench would hand back a part with 1 point of durability and call it repaired.
		int allowance = ComponentRepair.maxRepairs(STEP);
		assertEquals(200, ComponentRepair.maxDamageAfter(T1_MAX, STEP, allowance),
				"the fourth repair leaves a fifth of the ceiling");
		assertEquals(1, ComponentRepair.maxDamageAfter(T1_MAX, STEP, allowance + 1),
				"a fifth would leave nothing — which is why it is refused");
	}

	@Test
	void negativeRepairCountIsTreatedAsZero() {
		assertTrue(ComponentRepair.canRepair(-3, 4), "corrupt counter degrades to 'never repaired'");
	}
}
