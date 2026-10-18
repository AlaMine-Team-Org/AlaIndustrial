package dev.alaindustrial.item.energy;

import static dev.alaindustrial.item.energy.LowChargeRule.DEPLETED;
import static dev.alaindustrial.item.energy.LowChargeRule.LOW;
import static dev.alaindustrial.item.energy.LowChargeRule.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The memory half of the low-charge warning (MOD-652): who was told what and when, per player and per kind of
 * item. The decision itself is {@link LowChargeRuleTest}'s; here only the clocks are under test.
 */
class LowChargeLedgerTest {

	private static final long CAP = 10_000;
	private static final int PCT = 10;
	private static final long PERIOD = 1_200;
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
	private static final Object DRILL = "drill";
	private static final Object SAW = "saw";

	private final LowChargeLedger ledger = new LowChargeLedger();

	/** A tool-style debit of 50 EU from {@code before}, no depletion signal. */
	private int tool(UUID player, Object kind, long before, long now) {
		return ledger.decide(player, kind, before, before - 50, CAP, PCT, 0L, now, PERIOD);
	}

	@Test
	void crossingWarnsAndStartsTheClock() {
		assertEquals(LOW, ledger.decide(ALICE, DRILL, 1_020, 970, CAP, PCT, 0L, 100, PERIOD));
		assertEquals(NONE, tool(ALICE, DRILL, 970, 101), "now inside the period");
	}

	@Test
	void aSilentDebitStartsNoClock() {
		assertEquals(NONE, tool(ALICE, DRILL, 5_000, 100), "well above the threshold");
		assertEquals(LOW, tool(ALICE, DRILL, 1_020, 101));
		assertEquals(NONE, tool(ALICE, DRILL, 970, 102), "the clock started with the warning, not before");
	}

	@Test
	void theRepeatComesAtExactlyThePeriod() {
		assertEquals(LOW, tool(ALICE, DRILL, 800, 100));
		assertEquals(NONE, tool(ALICE, DRILL, 750, 100 + PERIOD - 1));
		assertEquals(LOW, tool(ALICE, DRILL, 750, 100 + PERIOD));
	}

	@Test
	void theClockRestartsFromTheLatestWarning() {
		assertEquals(LOW, tool(ALICE, DRILL, 800, 100));
		assertEquals(LOW, tool(ALICE, DRILL, 750, 100 + PERIOD));
		assertEquals(NONE, tool(ALICE, DRILL, 700, 100 + PERIOD + PERIOD - 1), "counted from the second warning");
		assertEquals(LOW, tool(ALICE, DRILL, 700, 100 + 2 * PERIOD));
	}

	@Test
	void eachKindOfItemKeepsItsOwnClock() {
		assertEquals(LOW, tool(ALICE, DRILL, 800, 100));
		assertEquals(LOW, tool(ALICE, SAW, 800, 101), "a warning about the drill does not silence the saw");
		assertEquals(NONE, tool(ALICE, DRILL, 750, 102));
		assertEquals(NONE, tool(ALICE, SAW, 750, 103));
	}

	@Test
	void eachPlayerKeepsTheirOwnClock() {
		assertEquals(LOW, tool(ALICE, DRILL, 800, 100));
		assertEquals(LOW, tool(BOB, DRILL, 800, 101));
	}

	@Test
	void forgettingAPlayerLetsTheNextSpendWarnAgain() {
		assertEquals(LOW, tool(ALICE, DRILL, 800, 100));
		assertEquals(NONE, tool(ALICE, DRILL, 750, 101));
		ledger.forget(ALICE);
		assertEquals(LOW, tool(ALICE, DRILL, 700, 102));
	}

	@Test
	void forgettingOnePlayerLeavesTheOthersAlone() {
		assertEquals(LOW, tool(ALICE, DRILL, 800, 100));
		assertEquals(LOW, tool(BOB, DRILL, 800, 100));
		ledger.forget(ALICE);
		assertEquals(NONE, tool(BOB, DRILL, 750, 101), "Bob was already told and is still inside the period");
	}

	@Test
	void forgettingAnUnknownPlayerIsHarmless() {
		ledger.forget(ALICE);
		assertEquals(LOW, tool(ALICE, DRILL, 800, 100));
	}

	@Test
	void clearForgetsEveryone() {
		assertEquals(LOW, tool(ALICE, DRILL, 800, 100));
		assertEquals(LOW, tool(BOB, DRILL, 800, 100));
		ledger.clear();
		assertEquals(LOW, tool(ALICE, DRILL, 750, 101));
		assertEquals(LOW, tool(BOB, DRILL, 750, 101));
	}

	@Test
	void theJetpackRepeatsEverySecondAndThenAnnouncesDepletion() {
		assertEquals(LOW, ledger.decide(ALICE, DRILL, 1_020, 970, CAP, PCT, 1L, 100, 20));
		assertEquals(NONE, ledger.decide(ALICE, DRILL, 970, 920, CAP, PCT, 1L, 119, 20));
		assertEquals(LOW, ledger.decide(ALICE, DRILL, 920, 870, CAP, PCT, 1L, 120, 20));
		assertEquals(DEPLETED, ledger.decide(ALICE, DRILL, 30, 0, CAP, PCT, 1L, 121, 20),
				"running out is announced at once, inside the beep's period");
	}

	@Test
	void aWarningMeasuresAgainstTheGivenThreshold() {
		assertEquals(LOW, ledger.decide(ALICE, DRILL, 3_500, 2_900, CAP, 30, 0L, 100, PERIOD));
		assertEquals(NONE, ledger.decide(BOB, DRILL, 3_500, 2_900, CAP, 20, 0L, 100, PERIOD));
	}

	@Test
	void aToolAnnouncesDepletionOnceUntilItIsChargedBackUp() {
		assertEquals(DEPLETED, ledger.decide(ALICE, DRILL, 120, 49, CAP, PCT, 50L, 100, PERIOD));
		assertEquals(NONE, ledger.decide(ALICE, DRILL, 49, 49, CAP, PCT, 50L, 101, PERIOD));
		assertEquals(DEPLETED, ledger.decide(ALICE, DRILL, 120, 49, CAP, PCT, 50L, 102, PERIOD),
				"charged back above the price and run down again");
	}
}
