package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link WornComponentCue} (MOD-654): the threshold, the pause and its spread. Literal numbers,
 * so a tautology cannot pass; each rule has a boundary pair (one point on each side). The arguments after
 * the damage/max pair are the defaults: percent 10, cap 120, slow 200 ticks, fast 40 ticks.
 */
class WornComponentCueTest {

	@Test
	void thresholdIsPercentOfMaxForSmallComponents() {
		assertEquals(100, WornComponentCue.thresholdPoints(1000, 10, 120), "10% of 1000, under the 120 cap");
		assertEquals(20, WornComponentCue.thresholdPoints(200, 10, 120), "a repaired rotor: 10% of its 200");
	}

	@Test
	void thresholdIsCappedForBigComponents() {
		assertEquals(120, WornComponentCue.thresholdPoints(3000, 10, 120), "10% would be 300; cap 120");
		assertEquals(120, WornComponentCue.thresholdPoints(6000, 10, 120), "10% would be 600; cap 120");
	}

	@Test
	void thresholdIsZeroWhenSwitchedOffOrNoDurability() {
		assertEquals(0, WornComponentCue.thresholdPoints(1000, 0, 120), "0% disables the warning");
		assertEquals(0, WornComponentCue.thresholdPoints(0, 10, 120), "no durability");
		assertEquals(0, WornComponentCue.thresholdPoints(1000, 10, 0), "no cap points");
	}

	@Test
	void percentAboveHundredIsTreatedAsHundred() {
		assertEquals(500, WornComponentCue.thresholdPoints(500, 250, 1000));
	}

	@Test
	void wornBoundaryIsStrict() {
		// max 1000, threshold 100: remaining 100 (damage 900) is NOT below it, remaining 99 (damage 901) is.
		assertFalse(WornComponentCue.isWorn(900, 1000, 10, 120));
		assertTrue(WornComponentCue.isWorn(901, 1000, 10, 120));
		assertTrue(WornComponentCue.isWorn(999, 1000, 10, 120), "one point left");
	}

	@Test
	void pristineBrokenAndEmptyAreNotWorn() {
		assertFalse(WornComponentCue.isWorn(0, 1000, 10, 120), "no wear yet");
		assertFalse(WornComponentCue.isWorn(1000, 1000, 10, 120), "already spent");
		assertFalse(WornComponentCue.isWorn(1200, 1000, 10, 120), "over the max");
		assertFalse(WornComponentCue.isWorn(5, 0, 10, 120), "no durability");
		assertFalse(WornComponentCue.isWorn(950, 1000, 0, 120), "switched off");
	}

	@Test
	void pristineIsNotWornEvenAtOneHundredPercent() {
		assertFalse(WornComponentCue.isWorn(0, 1000, 100, 1000));
		assertTrue(WornComponentCue.isWorn(1, 1000, 100, 1000));
	}

	@Test
	void capGivesTheSameWindowToEveryGrade() {
		// reinforced (max 3000) and electronic (max 6000): warning from remaining 119 on both.
		assertFalse(WornComponentCue.isWorn(3000 - 120, 3000, 10, 120));
		assertTrue(WornComponentCue.isWorn(3000 - 119, 3000, 10, 120));
		assertFalse(WornComponentCue.isWorn(6000 - 120, 6000, 10, 120));
		assertTrue(WornComponentCue.isWorn(6000 - 119, 6000, 10, 120));
	}

	@Test
	void repairedComponentIsJudgedAgainstItsOwnLoweredMax() {
		// After a repair max is 800 and damage 0: pristine, silent. The edge is 80 points left.
		assertFalse(WornComponentCue.isWorn(0, 800, 10, 120));
		assertFalse(WornComponentCue.isWorn(720, 800, 10, 120), "80 left is the edge");
		assertTrue(WornComponentCue.isWorn(721, 800, 10, 120));
		// the same damage against the ORIGINAL 1000 ceiling is far from the end: the ceiling matters.
		assertFalse(WornComponentCue.isWorn(721, 1000, 10, 120));
	}

	@Test
	void criticalOnlyInTheLastQuarterOfTheWindow() {
		// threshold 100: critical when remaining <= 25.
		assertFalse(WornComponentCue.isCritical(974, 1000, 10, 120), "26 left");
		assertTrue(WornComponentCue.isCritical(975, 1000, 10, 120), "25 left");
		assertTrue(WornComponentCue.isCritical(999, 1000, 10, 120));
		assertFalse(WornComponentCue.isCritical(0, 1000, 10, 120), "not even worn");
		assertFalse(WornComponentCue.isCritical(1000, 1000, 10, 120), "spent");
	}

	@Test
	void nominalDelayShrinksTowardsZeroRemainder() {
		assertEquals(200, WornComponentCue.nominalDelay(900, 1000, 10, 120, 200, 40), "edge of the window");
		assertEquals(120, WornComponentCue.nominalDelay(950, 1000, 10, 120, 200, 40), "halfway");
		assertEquals(40, WornComponentCue.nominalDelay(1000, 1000, 10, 120, 200, 40), "nothing left");
		assertTrue(WornComponentCue.nominalDelay(990, 1000, 10, 120, 200, 40)
				< WornComponentCue.nominalDelay(930, 1000, 10, 120, 200, 40), "more often as it dies");
	}

	@Test
	void nominalDelayNeverBelowOneAndTolerantOfSwappedBounds() {
		assertEquals(1, WornComponentCue.nominalDelay(999, 1000, 10, 120, 0, 0));
		assertEquals(100, WornComponentCue.nominalDelay(1000, 1000, 10, 120, 100, 500), "fast > slow clamps");
	}

	@Test
	void nextDelaySpreadStaysInsideSeventyToOneThirtyPercent() {
		// nominal 200 at the edge.
		assertEquals(140, WornComponentCue.nextDelay(900, 1000, 10, 120, 200, 40, 0.0f));
		assertEquals(200, WornComponentCue.nextDelay(900, 1000, 10, 120, 200, 40, 0.5f));
		assertEquals(260, WornComponentCue.nextDelay(900, 1000, 10, 120, 200, 40, 1.0f));
		assertEquals(140, WornComponentCue.nextDelay(900, 1000, 10, 120, 200, 40, -3.0f), "roll clamped low");
		assertEquals(260, WornComponentCue.nextDelay(900, 1000, 10, 120, 200, 40, 7.0f), "roll clamped high");
	}

	@Test
	void firstDelayIsUniformInsideTheFirstHalfOfTheNominalPause() {
		assertEquals(1, WornComponentCue.firstDelay(900, 1000, 10, 120, 200, 40, 0.0f), "never zero");
		assertEquals(50, WornComponentCue.firstDelay(900, 1000, 10, 120, 200, 40, 0.5f));
		assertEquals(100, WornComponentCue.firstDelay(900, 1000, 10, 120, 200, 40, 1.0f), "at most 5 s at the edge");
	}

	@Test
	void pausesAreShortAtTheDefaults() {
		// owner remark 2026-10-18: the pause was too long even at 1%. Edge 10 s, nothing left 2 s.
		assertTrue(WornComponentCue.nominalDelay(900, 1000, 10, 120, 200, 40) <= 200);
		assertTrue(WornComponentCue.nominalDelay(999, 1000, 10, 120, 200, 40) <= 45, "almost spent: about 2 s");
		assertTrue(WornComponentCue.nextDelay(999, 1000, 10, 120, 200, 40, 1.0f) < 60, "even the longest draw");
	}

	@Test
	void smokeCloudsGrowAsTheComponentDies() {
		assertEquals(2, WornComponentCue.puffCount(901, 1000, 10, 120), "edge of the window");
		assertEquals(4, WornComponentCue.puffCount(950, 1000, 10, 120), "halfway: 2 + 3 * 0.5, rounded");
		assertEquals(5, WornComponentCue.puffCount(1000, 1000, 10, 120), "nothing left");
		assertEquals(2, WornComponentCue.puffCount(100, 1000, 10, 120), "fresh: the minimum, not below");
		assertEquals(2, WornComponentCue.puffCount(900, 1000, 0, 120), "switched off: the minimum");
	}
}
