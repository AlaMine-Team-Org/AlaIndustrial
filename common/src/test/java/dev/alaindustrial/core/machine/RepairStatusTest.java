package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link RepairStatus} (MOD-384) — the priority chain the repair bench's screen
 * turns into its one status line, and the gate its tick loop spends EU behind.
 */
class RepairStatusTest {

	private static final int LIMIT = 4;

	/** Everything present and allowed → the bench works. */
	@Test
	void readyWhenComponentIsWornMaterialPresentAndUnderTheLimit() {
		assertSame(RepairStatus.READY,
				RepairStatus.resolve(true, true, 0, LIMIT, true));
		assertSame(RepairStatus.READY,
				RepairStatus.resolve(true, true, LIMIT - 1, LIMIT, true),
				"the last allowed repair still starts");
	}

	// --- the priority chain, one masking step at a time ---

	@Test
	void emptyOrForeignSlotIsNoTarget() {
		assertSame(RepairStatus.NO_TARGET, RepairStatus.resolve(false, false, 0, LIMIT, false));
		assertSame(RepairStatus.NO_TARGET, RepairStatus.resolve(false, true, 0, LIMIT, true),
				"nothing else matters without a component to work on");
	}

	@Test
	void intactComponentReportsNothingToRepair() {
		assertSame(RepairStatus.NOT_DAMAGED, RepairStatus.resolve(true, false, 0, LIMIT, true));
	}

	@Test
	void spentComponentReportsTheLimitEvenWithMaterialReady() {
		// The important masking case: a player holding the right plate must be told the component is
		// finished, not that something is missing — fetching more plates would never help.
		assertSame(RepairStatus.LIMIT_REACHED, RepairStatus.resolve(true, true, LIMIT, LIMIT, true));
		assertSame(RepairStatus.LIMIT_REACHED, RepairStatus.resolve(true, true, LIMIT + 5, LIMIT, true));
	}

	@Test
	void missingMaterialIsReportedOnlyOnceEverythingElseIsFine() {
		assertSame(RepairStatus.NEEDS_MATERIAL, RepairStatus.resolve(true, true, 1, LIMIT, false));
	}

	@Test
	void limitMasksMissingMaterial() {
		assertSame(RepairStatus.LIMIT_REACHED, RepairStatus.resolve(true, true, LIMIT, LIMIT, false),
				"the permanent reason wins over the fixable one");
	}

	@Test
	void intactMasksMissingMaterial() {
		assertSame(RepairStatus.NOT_DAMAGED, RepairStatus.resolve(true, false, 0, LIMIT, false));
	}

	// --- the two predicates the tick loop branches on ---

	@Test
	void onlyReadyCanWork() {
		for (RepairStatus s : RepairStatus.values()) {
			assertEquals(s == RepairStatus.READY, s.canWork(), s + ".canWork()");
		}
	}

	@Test
	void progressSurvivesAStallButNotALostJob() {
		// hasRepairableTarget() decides freeze-vs-reset: a missing plate must not throw away a
		// half-finished repair, while pulling the component out must.
		assertTrue(RepairStatus.READY.hasRepairableTarget());
		assertTrue(RepairStatus.NEEDS_MATERIAL.hasRepairableTarget(), "a stall, not a lost job");
		assertFalse(RepairStatus.NO_TARGET.hasRepairableTarget());
		assertFalse(RepairStatus.NOT_DAMAGED.hasRepairableTarget());
		assertFalse(RepairStatus.LIMIT_REACHED.hasRepairableTarget());
	}

	// --- sync-channel round trip ---

	@Test
	void everyStatusRoundTripsThroughItsCode() {
		for (RepairStatus s : RepairStatus.values()) {
			assertSame(s, RepairStatus.byCode(s.code()), "round trip for " + s);
		}
	}

	@Test
	void outOfRangeCodeFallsBackInsteadOfThrowing() {
		// A truncated or stale channel value reaches the render thread; it must degrade, not crash.
		assertSame(RepairStatus.NO_TARGET, RepairStatus.byCode(-1));
		assertSame(RepairStatus.NO_TARGET, RepairStatus.byCode(RepairStatus.values().length));
		assertSame(RepairStatus.NO_TARGET, RepairStatus.byCode(Integer.MAX_VALUE));
	}
}
