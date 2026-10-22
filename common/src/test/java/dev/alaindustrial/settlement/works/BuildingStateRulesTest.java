package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.BuildingStateRules.Before;
import dev.alaindustrial.settlement.works.BuildingStateRules.Measure;
import dev.alaindustrial.settlement.works.BuildingStateRules.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 for how a building's state follows from a pass and its jobs (MOD-857, epic decisions R79, R85, R86): the 90 %
 * rule with key points, the log line only on the first restoration, an intact building that loses a block back in
 * repair, and the job-driven states between passes.
 */
class BuildingStateRulesTest {

	private static final double AT = BuildingStateRules.DEFAULT_RESTORED_AT;
	private static final List<String> NONE = List.of();

	@Test
	void ninetyPercentWithEveryKeyPointRestores() {
		assertTrue(BuildingStateRules.meetsRestoration(new Measure(90, 100, NONE), AT));
		assertFalse(BuildingStateRules.meetsRestoration(new Measure(89, 100, NONE), AT));
		assertFalse(BuildingStateRules.meetsRestoration(new Measure(100, 100, List.of("roof")), AT),
				"a missing key point holds the restoration back however many cells match");
		assertFalse(BuildingStateRules.meetsRestoration(new Measure(0, 0, NONE), AT), "nothing compared");
		assertTrue(BuildingStateRules.meetsRestoration(new Measure(793, 880, NONE), AT), "793/880 = 90.1 %");
		assertFalse(BuildingStateRules.meetsRestoration(new Measure(791, 880, NONE), AT), "791/880 = 89.9 %");
	}

	@Test
	void theFirstRestorationIsLoggedOnce() {
		Verdict first = BuildingStateRules.afterPass(Before.NEVER, new Measure(95, 100, NONE), AT, false, true);
		assertEquals(BuildingState.INTACT, first.state());
		assertTrue(first.justRestored());
		assertTrue(first.everRestored());
		assertEquals(5, first.allowedMissing());
		Verdict again = BuildingStateRules.afterPass(new Before(true, 5), new Measure(97, 100, NONE), AT, false, false);
		assertEquals(BuildingState.INTACT, again.state());
		assertFalse(again.justRestored(), "a building already restored is not logged again");
		assertEquals(3, again.allowedMissing(), "the allowance only shrinks");
	}

	@Test
	void anIntactBuildingThatLosesABlockGoesBackToRepair() {
		Verdict broken = BuildingStateRules.afterPass(new Before(true, 0), new Measure(99, 100, NONE), AT, false,
				false);
		assertEquals(BuildingState.REPAIR, broken.state(), "one block broken from a complete building");
		assertEquals(0, broken.allowedMissing());
		assertFalse(broken.justRestored());
		Verdict mended = BuildingStateRules.afterPass(new Before(true, 0), new Measure(100, 100, NONE), AT, false,
				false);
		assertEquals(BuildingState.INTACT, mended.state());
		assertFalse(mended.justRestored(), "mending is not a new restoration");
	}

	@Test
	void aRestoredBuildingNewlyDamagedAsksForItsRepairOnce() {
		Before restored = new Before(true, 0);
		Measure broken = new Measure(97, 100, NONE);
		Verdict repair = BuildingStateRules.afterPass(restored, broken, AT, false, false);
		assertTrue(BuildingStateRules.newlyDamaged(restored, 0, repair, broken),
				"three blocks broken out of a whole one");
		assertFalse(BuildingStateRules.newlyDamaged(restored, 3, repair, broken),
				"the same three missing as before the pass: no new damage, no second repair");
		assertTrue(BuildingStateRules.newlyDamaged(restored, 2, repair, broken), "one more broken during the repair");
		assertFalse(BuildingStateRules.newlyDamaged(restored, -1, repair, broken),
				"never measured: nothing to compare");
		Measure ruin = new Measure(40, 100, NONE);
		Verdict never = BuildingStateRules.afterPass(Before.NEVER, ruin, AT, false, false);
		assertFalse(BuildingStateRules.newlyDamaged(Before.NEVER, 10, never, ruin),
				"a ruin never restored waits for its first restoration, not for a repair");
		Measure whole = new Measure(100, 100, NONE);
		Verdict intact = BuildingStateRules.afterPass(restored, whole, AT, false, false);
		assertFalse(BuildingStateRules.newlyDamaged(restored, 0, intact, whole), "an intact building needs nothing");
	}

	@Test
	void aDamagedBuildingWholeAgainIsMended() {
		Before restored = new Before(true, 0);
		Verdict intact = BuildingStateRules.afterPass(restored, new Measure(100, 100, NONE), AT, false, false);
		assertTrue(BuildingStateRules.mended(restored, BuildingState.REPAIR, intact));
		assertFalse(BuildingStateRules.mended(restored, BuildingState.INTACT, intact), "it never left intact");
		Verdict first = BuildingStateRules.afterPass(Before.NEVER, new Measure(100, 100, NONE), AT, false, true);
		assertFalse(BuildingStateRules.mended(Before.NEVER, BuildingState.REPAIR, first),
				"the first restoration has its own news");
		Verdict still = BuildingStateRules.afterPass(restored, new Measure(99, 100, NONE), AT, false, true);
		assertFalse(BuildingStateRules.mended(restored, BuildingState.REPAIR, still), "still a block short");
	}

	@Test
	void aBrokenKeyPointTakesAnIntactBuildingBackToRepair() {
		Verdict verdict = BuildingStateRules.afterPass(new Before(true, 5), new Measure(96, 100, List.of("door")), AT,
				false, false);
		assertEquals(BuildingState.REPAIR, verdict.state());
	}

	@Test
	void aNeverRestoredBuildingFollowsItsJobs() {
		Measure ruin = new Measure(52, 100, List.of("roof"));
		assertEquals(BuildingState.RUIN, BuildingStateRules.afterPass(Before.NEVER, ruin, AT, false, false).state());
		assertEquals(BuildingState.CLEARING,
				BuildingStateRules.afterPass(Before.NEVER, ruin, AT, true, false).state());
		assertEquals(BuildingState.CLEARING, BuildingStateRules.afterPass(Before.NEVER, ruin, AT, true, true).state(),
				"the debris goes first: clearing while both jobs are open");
		assertEquals(BuildingState.REPAIR, BuildingStateRules.afterPass(Before.NEVER, ruin, AT, false, true).state());
	}

	@Test
	void betweenPassesTheJobsMoveTheStateButNeverTakeAwayIntact() {
		assertEquals(BuildingState.CLEARING,
				BuildingStateRules.betweenPasses(BuildingState.RUIN, Before.NEVER, true, false));
		assertEquals(BuildingState.RUIN,
				BuildingStateRules.betweenPasses(BuildingState.CLEARING, Before.NEVER, false, false));
		assertEquals(BuildingState.INTACT,
				BuildingStateRules.betweenPasses(BuildingState.INTACT, new Before(true, 0), true, true));
		assertEquals(BuildingState.REPAIR,
				BuildingStateRules.betweenPasses(BuildingState.REPAIR, new Before(true, 0), false, false));
	}

	@Test
	void aMeasureCountsItsMissingCellsAndPercent() {
		Measure measure = new Measure(458, 878, NONE);
		assertEquals(420, measure.missing());
		assertEquals(52, measure.percent());
		assertThrows(IllegalArgumentException.class, () -> new Measure(5, 4, NONE));
	}
}
