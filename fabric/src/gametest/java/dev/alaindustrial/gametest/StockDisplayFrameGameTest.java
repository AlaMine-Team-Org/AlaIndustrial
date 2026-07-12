package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Stock Display Frame (MOD-066, suite TC-FRAME-001). Thin Fabric
 * wrappers: the bodies are loader-neutral in {@code common/.../gametest/StockDisplayFrameScenarios}
 * and the SAME bodies run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}).
 */
public class StockDisplayFrameGameTest {

	/** @implements TC-FRAME-001-FUN01 — an empty frame counts the whole container. */
	@GameTest
	public void tcFrame001Fun01_countsWholeContainer(GameTestHelper helper) {
		StockDisplayFrameScenarios.fun01CountsWholeContainer(helper);
	}

	/** @implements TC-FRAME-001-FUN02 — a filter item counts only matching stacks. */
	@GameTest
	public void tcFrame001Fun02_filterCountsOnlyMatching(GameTestHelper helper) {
		StockDisplayFrameScenarios.fun02FilterCountsOnlyMatching(helper);
	}

	/** @implements TC-FRAME-001-FUN03 — a double chest is counted as one combined container. */
	@GameTest
	public void tcFrame001Fun03_doubleChestCombined(GameTestHelper helper) {
		StockDisplayFrameScenarios.fun03DoubleChestCombined(helper);
	}

	/** @implements TC-FRAME-001-FUN04 — the count follows inventory changes within one scan. */
	@GameTest
	public void tcFrame001Fun04_updatesAfterChange(GameTestHelper helper) {
		StockDisplayFrameScenarios.fun04UpdatesAfterChange(helper);
	}

	/** @implements TC-FRAME-001-FUN05 — a non-container support reports NO_CONTAINER. */
	@GameTest
	public void tcFrame001Fun05_noContainer(GameTestHelper helper) {
		StockDisplayFrameScenarios.fun05NoContainer(helper);
	}

	/** @implements TC-FRAME-001-FUN06 — breaking the frame drops the mod's own item. */
	@GameTest
	public void tcFrame001Fun06_dropsOwnItem(GameTestHelper helper) {
		StockDisplayFrameScenarios.fun06DropsOwnItem(helper);
	}
}
