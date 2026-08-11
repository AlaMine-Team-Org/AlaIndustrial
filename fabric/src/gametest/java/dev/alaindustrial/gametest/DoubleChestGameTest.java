package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the double chest (MOD-391, suite TC-CHEST-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/DoubleChestScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}).
 */
public class DoubleChestGameTest {

	/** @implements TC-CHEST-001-FUN01 — a LEFT half pairs a same-tier SINGLE up as RIGHT, 72 joined slots. */
	@GameTest
	public void tcChest001Fun01_pairFormsAndJoins(GameTestHelper helper) {
		DoubleChestScenarios.fun01PairFormsAndJoins(helper);
	}

	/** @implements TC-CHEST-001-FUN02 — a foreign-tier neighbour never joins. */
	@GameTest
	public void tcChest001Fun02_crossTierNeverPairs(GameTestHelper helper) {
		DoubleChestScenarios.fun02CrossTierNeverPairs(helper);
	}

	/** @implements TC-CHEST-001-FUN03 — auto-join without sneak; sneak placement stays single. */
	@GameTest
	public void tcChest001Fun03_placementSneakStaysSingle(GameTestHelper helper) {
		DoubleChestScenarios.fun03PlacementSneakStaysSingle(helper);
	}

	/** @implements TC-CHEST-001-FUN04 — breaking one half reverts the partner to SINGLE, items intact. */
	@GameTest
	public void tcChest001Fun04_breakHalfRevertsPartner(GameTestHelper helper) {
		DoubleChestScenarios.fun04BreakHalfRevertsPartner(helper);
	}

	/** @implements TC-CHEST-001-FUN05 — a hopper overflows a full first half into the second. */
	@GameTest
	public void tcChest001Fun05_hopperOverflowsIntoSecondHalf(GameTestHelper helper) {
		DoubleChestScenarios.fun05HopperOverflowsIntoSecondHalf(helper);
	}

	/** @implements TC-CHEST-001-FUN06 — the comparator reads the pair as one, the same from either half. */
	@GameTest
	public void tcChest001Fun06_comparatorReadsJoined(GameTestHelper helper) {
		DoubleChestScenarios.fun06ComparatorReadsJoined(helper);
	}

	/** @implements TC-CHEST-001-FUN07 — the 6-row window scrolls over the double's rows. */
	@GameTest
	public void tcChest001Fun07_windowScrollsHiddenRows(GameTestHelper helper) {
		DoubleChestScenarios.fun07WindowScrollsHiddenRows(helper);
	}

	/** @implements TC-CHEST-001-FUN08 — shift-click reaches the hidden rows. */
	@GameTest
	public void tcChest001Fun08_shiftClickReachesHiddenRows(GameTestHelper helper) {
		DoubleChestScenarios.fun08ShiftClickReachesHiddenRows(helper);
	}

	/** @implements TC-CHEST-001-FUN09 — waterlogging survives the pair breaking apart. */
	@GameTest
	public void tcChest001Fun09_waterloggedSurvivesBreak(GameTestHelper helper) {
		DoubleChestScenarios.fun09WaterloggedSurvivesBreak(helper);
	}

	/** @implements TC-CHEST-001-FUN10 — breaking a half invalidates the open double window. */
	@GameTest
	public void tcChest001Fun10_breakingHalfClosesWindow(GameTestHelper helper) {
		DoubleChestScenarios.fun10BreakingHalfClosesWindow(helper);
	}

	/** @implements TC-CHEST-001-PER01 — a half's items survive the NBT round-trip. */
	@GameTest
	public void tcChest001Per01_itemsSurviveNbtRoundTrip(GameTestHelper helper) {
		DoubleChestScenarios.per01ItemsSurviveNbtRoundTrip(helper);
	}
}
