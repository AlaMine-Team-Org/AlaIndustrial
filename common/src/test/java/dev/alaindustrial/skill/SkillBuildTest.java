package dev.alaindustrial.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.stats.LevelMath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules of the upgrade tree (MOD-483), checked without a game.
 *
 * <p>These are the assertions the owner's decisions turn into: the fork is hard, the arithmetic makes
 * exactly two capstones affordable per run, and a reset is priced off what it refunds. Every one of
 * them would otherwise only be observable by playing to level 40.
 */
class SkillBuildTest {

	/** Every Fragment a level-40 player can hold: one per level ABOVE the first (MOD-483). */
	private static final int FULL_BUDGET = 39;

	@Test
	@DisplayName("An empty build can only enter a branch")
	void emptyBuildOnlyOffersEntries() {
		SkillBuild build = SkillBuild.EMPTY;
		assertTrue(build.canBuy(SkillBranch.ENERGY, SkillSlot.IN, FULL_BUDGET));
		assertFalse(build.canBuy(SkillBranch.ENERGY, SkillSlot.A1, FULL_BUDGET));
		assertEquals(SkillBuild.Refusal.LOCKED,
				build.refuse(SkillBranch.ENERGY, SkillSlot.MID, FULL_BUDGET));
	}

	@Test
	@DisplayName("Taking one side of a fork closes the other forever")
	void forkIsHard() {
		SkillBuild build = SkillBuild.EMPTY
				.with(SkillBranch.ENERGY, SkillSlot.IN)
				.with(SkillBranch.ENERGY, SkillSlot.A1);

		assertTrue(build.blocked(SkillBranch.ENERGY, SkillSlot.B1));
		assertEquals(SkillBuild.Refusal.FORK_CLOSED,
				build.refuse(SkillBranch.ENERGY, SkillSlot.B1, FULL_BUDGET));
		// …and it stays closed however many points arrive later.
		assertFalse(build.canBuy(SkillBranch.ENERGY, SkillSlot.B1, Integer.MAX_VALUE));
	}

	@Test
	@DisplayName("A fork closed in one branch leaves the same fork open in another")
	void forkIsPerBranch() {
		SkillBuild build = SkillBuild.EMPTY
				.with(SkillBranch.ENERGY, SkillSlot.IN)
				.with(SkillBranch.ENERGY, SkillSlot.A1)
				.with(SkillBranch.MECH, SkillSlot.IN);

		assertTrue(build.blocked(SkillBranch.ENERGY, SkillSlot.B1));
		assertFalse(build.blocked(SkillBranch.MECH, SkillSlot.B1));
		assertTrue(build.canBuy(SkillBranch.MECH, SkillSlot.B1, FULL_BUDGET));
	}

	@Test
	@DisplayName("Either side of a fork unlocks the node below it")
	void eitherForkSideUnlocksTheNextNode() {
		SkillBuild viaA = SkillBuild.EMPTY
				.with(SkillBranch.HAZARD, SkillSlot.IN)
				.with(SkillBranch.HAZARD, SkillSlot.A1);
		SkillBuild viaB = SkillBuild.EMPTY
				.with(SkillBranch.HAZARD, SkillSlot.IN)
				.with(SkillBranch.HAZARD, SkillSlot.B1);

		assertTrue(viaA.canBuy(SkillBranch.HAZARD, SkillSlot.MID, FULL_BUDGET));
		assertTrue(viaB.canBuy(SkillBranch.HAZARD, SkillSlot.MID, FULL_BUDGET));
	}

	@Test
	@DisplayName("A finished branch costs 18 points, so 39 buys exactly two capstones")
	void twoCapstonesPerRun() {
		SkillBuild build = fullPath(SkillBuild.EMPTY, SkillBranch.ENERGY);
		assertEquals(SkillSlot.BRANCH_PATH_COST, build.spent());
		assertEquals(18, build.spent());

		build = fullPath(build, SkillBranch.MECH);
		assertEquals(36, build.spent());
		// Two capstones done, three points left — an entry node and one fork into a third branch,
		// never enough to finish it.
		assertEquals(3, build.free(FULL_BUDGET));
		assertFalse(build.canBuy(SkillBranch.AGRO, SkillSlot.CAP, FULL_BUDGET));

		// A third full path would need 18 more against the 3 that remain.
		SkillBuild afterTwo = build;
		assertEquals(SkillBuild.Refusal.NOT_ENOUGH_POINTS,
				afterTwo.with(SkillBranch.AGRO, SkillSlot.IN)
						.with(SkillBranch.AGRO, SkillSlot.A1)
						.refuse(SkillBranch.AGRO, SkillSlot.MID, FULL_BUDGET));
	}

	@Test
	@DisplayName("The whole tree costs 72 against a budget of 39")
	void wholeTreeIsOutOfReach() {
		SkillBuild build = SkillBuild.EMPTY;
		for (SkillBranch branch : SkillBranch.values()) {
			build = fullPath(build, branch);
		}
		assertEquals(72, build.spent());
		assertEquals(0, build.free(FULL_BUDGET));
	}

	@Test
	@DisplayName("A Fragment costs a level the player earned, so level 1 is worth nothing")
	void pointsTrackEarnedLevels() {
		// The defect this pins down: level 1 is where everyone starts, and it used to hand out a
		// Fragment, which bought the entry node of a branch before the player produced any EU.
		assertEquals(0, SkillPoints.forLevel(1));
		assertEquals(0, SkillPoints.forLevel(0));
		assertEquals(1, SkillPoints.forLevel(2));
		assertEquals(11, SkillPoints.forLevel(12));
		assertEquals(FULL_BUDGET, SkillPoints.forLevel(LevelMath.MAX_LEVEL));
		// A level beyond the ceiling must not mint extra points.
		assertEquals(FULL_BUDGET, SkillPoints.forLevel(LevelMath.MAX_LEVEL + 10));
	}

	@Test
	@DisplayName("The budget buys two branches and a look at a third, never the whole tree")
	void budgetBuysTwoBranchesAndALook() {
		// 39 against 4 x 18: the arithmetic the tree is balanced on, asserted rather than assumed,
		// because it moved when the free first Fragment was taken away.
		assertEquals(39, FULL_BUDGET);
		assertEquals(2, FULL_BUDGET / SkillSlot.BRANCH_PATH_COST);
		int leftOver = FULL_BUDGET - 2 * SkillSlot.BRANCH_PATH_COST;
		assertEquals(SkillSlot.IN.cost() + SkillSlot.A1.cost(), leftOver);
	}

	@Test
	@DisplayName("A build that outlives its level never reports negative free points")
	void freePointsNeverGoNegative() {
		SkillBuild build = fullPath(SkillBuild.EMPTY, SkillBranch.ENERGY);
		assertEquals(0, build.free(2));
	}

	@Test
	@DisplayName("Buying is refused when the points are short, whatever the tree allows")
	void pointsAreChecked() {
		SkillBuild build = SkillBuild.EMPTY.with(SkillBranch.MECH, SkillSlot.IN);
		assertEquals(SkillBuild.Refusal.NOT_ENOUGH_POINTS,
				build.refuse(SkillBranch.MECH, SkillSlot.A1, 1));
		assertTrue(build.canBuy(SkillBranch.MECH, SkillSlot.A1, 3));
	}

	@Test
	@DisplayName("An already-owned node is refused as owned, not as unaffordable")
	void ownedIsItsOwnRefusal() {
		SkillBuild build = SkillBuild.EMPTY.with(SkillBranch.AGRO, SkillSlot.IN);
		assertEquals(SkillBuild.Refusal.ALREADY_TAKEN,
				build.refuse(SkillBranch.AGRO, SkillSlot.IN, 0));
	}

	@Test
	@DisplayName("Per-branch counters see only their own branch")
	void perBranchCounters() {
		SkillBuild build = SkillBuild.EMPTY
				.with(SkillBranch.ENERGY, SkillSlot.IN)
				.with(SkillBranch.ENERGY, SkillSlot.A1)
				.with(SkillBranch.MECH, SkillSlot.IN);

		assertEquals(2, build.takenIn(SkillBranch.ENERGY));
		assertEquals(3, build.spentIn(SkillBranch.ENERGY));
		assertEquals(1, build.takenIn(SkillBranch.MECH));
		assertEquals(0, build.takenIn(SkillBranch.HAZARD));
	}

	/** Entry, one side, node, one side, capstone — the only shape a finished branch can have. */
	private static SkillBuild fullPath(SkillBuild build, SkillBranch branch) {
		return build.with(branch, SkillSlot.IN)
				.with(branch, SkillSlot.A1)
				.with(branch, SkillSlot.MID)
				.with(branch, SkillSlot.A2)
				.with(branch, SkillSlot.CAP);
	}
}
