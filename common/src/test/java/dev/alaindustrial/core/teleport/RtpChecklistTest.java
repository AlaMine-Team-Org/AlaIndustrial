package dev.alaindustrial.core.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.teleport.RtpChecklist.Access;
import dev.alaindustrial.core.teleport.RtpChecklist.Check;
import dev.alaindustrial.core.teleport.RtpChecklist.Facts;
import dev.alaindustrial.core.teleport.RtpChecklist.Mark;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MOD-630 — the random jump's checklist: every broken condition at once, and what stays unknown. */
class RtpChecklistTest {

	private static final long COST = 50_000;

	private static Facts ready() {
		return new Facts(true, Access.OK, true, true, 500_000, COST, 0);
	}

	@Test
	void aReadyStationPassesEveryCheck() {
		Facts facts = ready();
		assertTrue(RtpChecklist.failing(facts).isEmpty());
		assertNull(RtpChecklist.firstFailing(facts));
		assertTrue(RtpChecklist.allPassed(facts));
	}

	@Test
	void noChipAndNoChargeAreBothReported() {
		Facts facts = new Facts(true, Access.OK, true, false, 0, COST, 0);
		assertEquals(EnumSet.of(Check.CHIP, Check.CHARGE), RtpChecklist.failing(facts));
		assertEquals(Check.CHIP, RtpChecklist.firstFailing(facts), "the hint box explains the first broken row");
	}

	@Test
	void chargeExactlyAtThePriceIsEnoughAndOneBelowIsNot() {
		assertEquals(Mark.PASSED, RtpChecklist.mark(Check.CHARGE, new Facts(true, Access.OK, true, true, COST, COST, 0)));
		assertEquals(Mark.FAILED,
				RtpChecklist.mark(Check.CHARGE, new Facts(true, Access.OK, true, true, COST - 1, COST, 0)));
	}

	@Test
	void everyConditionCanFailTogether() {
		Facts facts = new Facts(false, Access.PRIVATE, true, false, 10, COST, 30);
		assertEquals(EnumSet.allOf(Check.class), RtpChecklist.failing(facts));
		assertEquals(Check.OVERWORLD, RtpChecklist.firstFailing(facts));
	}

	@Test
	void someoneElsesPrivateStationRevealsNeitherChipNorCharge() {
		Facts facts = new Facts(true, Access.PRIVATE, false, false, RtpChecklist.ENERGY_UNKNOWN, COST, 0);
		assertEquals(Mark.FAILED, RtpChecklist.mark(Check.ACCESS, facts));
		assertEquals(Mark.UNKNOWN, RtpChecklist.mark(Check.CHIP, facts));
		assertEquals(Mark.UNKNOWN, RtpChecklist.mark(Check.CHARGE, facts));
		assertEquals(EnumSet.of(Check.ACCESS), RtpChecklist.failing(facts));
	}

	@Test
	void aStationWithNoRecordIsUnknownRatherThanFailed() {
		Facts facts = new Facts(true, Access.UNKNOWN, false, false, RtpChecklist.ENERGY_UNKNOWN, COST, 0);
		assertTrue(RtpChecklist.failing(facts).isEmpty(), "unknown must not be reported as broken");
		assertFalse(RtpChecklist.allPassed(facts), "unknown must not be reported as ready either");
	}

	@Test
	void otherWorldAndMissingStationsFailAccess() {
		assertEquals(Mark.FAILED, RtpChecklist.mark(Check.ACCESS,
				new Facts(true, Access.OTHER_WORLD, true, true, 500_000, COST, 0)));
		assertEquals(Mark.FAILED, RtpChecklist.mark(Check.ACCESS,
				new Facts(true, Access.MISSING, false, false, RtpChecklist.ENERGY_UNKNOWN, COST, 0)));
	}

	@Test
	void aRechargingTeleporterFailsOnlyTheLastRow() {
		Facts facts = new Facts(true, Access.OK, true, true, 500_000, COST, 12);
		assertEquals(EnumSet.of(Check.RECHARGED), RtpChecklist.failing(facts));
	}

	@Test
	void stationsWithAChipComeFirstEachGroupInBindingOrder() {
		// The mockup's seven stations: chips on the 3rd, 4th and 6th.
		boolean[] chip = {false, false, true, true, false, true, false};
		List<Integer> order = RtpChecklist.order(chip);
		assertEquals(List.of(2, 3, 5, 0, 1, 4, 6), order);
		assertEquals(4, order.indexOf(0) + 1, "Teleporter 1 is 4 / 7 in frame G");
		assertEquals(3, order.indexOf(5) + 1, "Sky island is 3 / 7 in frame D");
	}

	@Test
	void stepWrapsAtBothEndsAndStartsAtTheFirst() {
		List<Integer> order = List.of(2, 3, 5, 0);
		assertEquals(3, RtpChecklist.step(order, 2, 1));
		assertEquals(2, RtpChecklist.step(order, 0, 1));
		assertEquals(0, RtpChecklist.step(order, 2, -1));
		assertEquals(2, RtpChecklist.step(order, -1, 1));
		assertEquals(-1, RtpChecklist.step(List.of(), 0, 1));
	}
}
