package dev.alaindustrial.item.energy;

import static dev.alaindustrial.item.energy.LowChargeRule.DEPLETED;
import static dev.alaindustrial.item.energy.LowChargeRule.LOW;
import static dev.alaindustrial.item.energy.LowChargeRule.NEVER;
import static dev.alaindustrial.item.energy.LowChargeRule.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The rules of the low-charge warning (MOD-652): a crossing warns once, a repeat only after its period, a
 * credit never, and only the item that asks for it announces running out. Capacity 10 000 and the default
 * 10% give the threshold 1 000, so every boundary below is a plain number.
 */
class LowChargeRuleTest {

	private static final long CAP = 10_000;
	private static final int PCT = 10;
	private static final long PERIOD = 1_200;
	private static final long NOW = 50_000;

	/** A tool-style decision: no depletion signal, the usual period, nothing warned before. */
	private static int tool(long before, long after) {
		return LowChargeRule.decide(before, after, CAP, PCT, 0L, NOW, NEVER, PERIOD);
	}

	private static int repeat(long before, long after, long lastWarn, long period) {
		return LowChargeRule.decide(before, after, CAP, PCT, 0L, NOW, lastWarn, period);
	}

	private static int jetpack(long before, long after, long lastWarn) {
		return LowChargeRule.decide(before, after, CAP, PCT, 1L, NOW, lastWarn, 20);
	}

	/** A drill-style decision: depleted once the charge cannot pay for one more block (price 50). */
	private static int drill(long before, long after, long lastWarn) {
		return LowChargeRule.decide(before, after, CAP, PCT, PRICE, NOW, lastWarn, PERIOD);
	}

	private static final long PRICE = 50;

	// --- the threshold itself ---

	@Test
	void thresholdIsStrict() {
		assertTrue(LowChargeRule.isLow(999, CAP, PCT));
		assertFalse(LowChargeRule.isLow(1_000, CAP, PCT), "exactly at the threshold is not yet low");
		assertFalse(LowChargeRule.isLow(1_001, CAP, PCT));
		assertTrue(LowChargeRule.isLow(0, CAP, PCT));
	}

	@Test
	void percentIsClampedToOneThroughHundred() {
		assertTrue(LowChargeRule.isLow(99, CAP, 0), "0% counts as 1%: 99 of 10000 is below it");
		assertFalse(LowChargeRule.isLow(100, CAP, 0));
		assertTrue(LowChargeRule.isLow(99, CAP, -5));
		assertTrue(LowChargeRule.isLow(9_999, CAP, 500), "over 100% counts as 100%");
		assertFalse(LowChargeRule.isLow(10_000, CAP, 500), "a full item is never low");
	}

	@Test
	void percentRoundsDownAndIsClamped() {
		assertEquals(9, LowChargeRule.percent(999, CAP), "9.99% reads 9, never 10");
		assertEquals(10, LowChargeRule.percent(1_000, CAP));
		assertEquals(0, LowChargeRule.percent(0, CAP));
		assertEquals(100, LowChargeRule.percent(CAP * 2, CAP));
		assertEquals(0, LowChargeRule.percent(-5, CAP));
		assertEquals(0, LowChargeRule.percent(5, 0));
		assertEquals(0, LowChargeRule.percent(5, -10));
	}

	// --- the crossing ---

	@Test
	void crossingDownWarnsOnce() {
		assertEquals(LOW, tool(1_500, 900));
	}

	@Test
	void crossingWarnsEvenRightAfterAnotherWarning() {
		assertEquals(LOW, repeat(1_500, 900, NOW - 1, PERIOD),
				"back above the threshold and down again is a new crossing, not a repeat");
	}

	@Test
	void landingExactlyOnTheThresholdDoesNotWarn() {
		assertEquals(NONE, tool(2_000, 1_000));
	}

	@Test
	void stepOnePastTheThresholdWarns() {
		assertEquals(LOW, tool(1_000, 999), "1000 is not low, 999 is: the step across it is the crossing");
	}

	@Test
	void aDebitThatStaysHighIsSilent() {
		assertEquals(NONE, tool(5_000, 4_000));
	}

	@Test
	void aCrossingToZeroStillWarnsForAnItemWithoutDepletionSignal() {
		assertEquals(LOW, tool(1_500, 0));
	}

	// --- credits never warn ---

	@Test
	void chargingNeverWarns() {
		assertEquals(NONE, tool(500, 900));
		assertEquals(NONE, tool(0, 500));
		assertEquals(NONE, tool(900, 900), "no change is not a debit");
		assertEquals(NONE, jetpack(0, 0, NEVER));
	}

	// --- the repeat ---

	@Test
	void repeatWaitsForItsPeriod() {
		assertEquals(NONE, repeat(800, 700, NOW - (PERIOD - 1), PERIOD));
		assertEquals(LOW, repeat(800, 700, NOW - PERIOD, PERIOD), "the period itself has elapsed");
		assertEquals(LOW, repeat(800, 700, NOW - PERIOD - 1, PERIOD));
	}

	@Test
	void anItemPickedUpAlreadyLowWarnsOnItsFirstSpend() {
		assertEquals(LOW, repeat(800, 700, NEVER, PERIOD),
				"it never crosses anything, so the repeat is its only chance");
		assertEquals(LOW, LowChargeRule.decide(800, 700, CAP, PCT, 0L, 1L << 40, NEVER, PERIOD),
				"NEVER must not overflow however late the world is");
	}

	@Test
	void aZeroPeriodTurnsRepeatsOff() {
		assertEquals(NONE, repeat(800, 700, NEVER, 0));
		assertEquals(NONE, repeat(800, 700, NEVER, -20));
		assertEquals(LOW, repeat(1_500, 900, NEVER, 0), "the crossing does not depend on the period");
	}

	@Test
	void anEmptyItemDoesNotRepeat() {
		assertEquals(NONE, repeat(50, 0, NEVER, PERIOD), "nothing is left to spend, so nothing to warn about again");
	}

	// --- running out ---

	@Test
	void theLastDropToZeroAnnouncesDepletionWhenAsked() {
		assertEquals(DEPLETED, jetpack(50, 0, NOW - 1));
		assertEquals(DEPLETED, jetpack(50, 0, NEVER));
	}

	@Test
	void depletionBeatsTheCrossing() {
		assertEquals(DEPLETED, jetpack(1_500, 0, NEVER), "one big debit from above the threshold to zero");
	}

	@Test
	void depletionIsOnlyForItemsThatAskForIt() {
		assertEquals(LOW, repeat(1_500, 0, NEVER, PERIOD));
		assertEquals(NONE, repeat(50, 0, NEVER, PERIOD));
	}

	@Test
	void aToolIsDepletedWhenTheRemainderNoLongerPaysForAnAction() {
		assertEquals(DEPLETED, drill(120, 49, NEVER), "49 left, price 50: the next block is free-hand");
		assertEquals(DEPLETED, drill(100, 40, NEVER), "from affordable twice to affordable never");
		assertEquals(DEPLETED, drill(50, 0, NEVER), "paid its last block exactly");
	}

	@Test
	void aToolStillAbleToPayIsNotDepleted() {
		assertEquals(NONE, drill(150, 100, NOW), "100 left, one more block fits");
		assertEquals(NONE, drill(150, PRICE, NOW), "exactly the price left still affords it (charge >= cost)");
	}

	@Test
	void aToolSignalsDepletionOncePerCrossing() {
		assertEquals(DEPLETED, drill(60, 10, NEVER));
		assertEquals(NONE, drill(10, 10, NEVER), "nothing is spent below the price, so nothing re-announces");
		assertEquals(NONE, drill(40, 40, NEVER));
		assertEquals(NONE, drill(40, 20, NOW), "already below the price: announced when it crossed, not again");
		assertEquals(DEPLETED, drill(60, 10, NEVER), "charged back above the price and run down again: once more");
	}

	@Test
	void aToolDepletionBeatsTheLowWarningOfTheSameDebit() {
		assertEquals(DEPLETED, drill(1_020, 10, NEVER));
	}

	@Test
	void aToolWithoutAPriceHasNoDepletionSignal() {
		assertEquals(LOW, LowChargeRule.decide(1_020, 10, CAP, PCT, 0L, NOW, NEVER, PERIOD));
	}

	@Test
	void aClampedLastDebitStillAnnouncesWhenItReachesZero() {
		assertEquals(DEPLETED, drill(30, 0, NEVER), "a debit larger than the charge, clamped to zero");
	}

	@Test
	void anAlreadyEmptyItemDoesNotAnnounceDepletionAgain() {
		assertEquals(NONE, jetpack(0, 0, NEVER));
	}

	@Test
	void aNegativeResultCountsAsZero() {
		assertEquals(DEPLETED, jetpack(10, -5, NEVER));
	}

	@Test
	void theJetpackBeepsAtItsPeriodWhileLow() {
		assertEquals(NONE, jetpack(900, 850, NOW - 19));
		assertEquals(LOW, jetpack(900, 850, NOW - 20));
	}

	// --- a buffer that does not exist ---

	@Test
	void noCapacityNeverWarns() {
		assertEquals(NONE, LowChargeRule.decide(5, 0, 0, PCT, 1L, NOW, NEVER, 20));
		assertEquals(NONE, LowChargeRule.decide(5, 0, -1, PCT, 1L, NOW, NEVER, 20));
	}
}
