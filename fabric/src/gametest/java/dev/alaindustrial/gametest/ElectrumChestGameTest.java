package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the electrum chest (MOD-409, suite TC-CHEST-002). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/ElectrumChestScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}).
 */
public class ElectrumChestGameTest {

	/** @implements TC-CHEST-002-FUN01 — a single electrum chest scrolls a 6-row window over 9 rows. */
	@GameTest
	public void tcChest002Fun01_singleChestWindowScrolls(GameTestHelper helper) {
		ElectrumChestScenarios.fun01SingleChestWindowScrolls(helper);
	}

	/** @implements TC-CHEST-002-FUN02 — shift-click reaches the rows the window does not show. */
	@GameTest
	public void tcChest002Fun02_shiftClickReachesHiddenRows(GameTestHelper helper) {
		ElectrumChestScenarios.fun02ShiftClickReachesHiddenRows(helper);
	}

	/** @implements TC-CHEST-002-FUN03 — an electrum chest never pairs with the gold tier. */
	@GameTest
	public void tcChest002Fun03_neverPairsWithGold(GameTestHelper helper) {
		ElectrumChestScenarios.fun03NeverPairsWithGold(helper);
	}
}
