package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration for the loader-neutral MOD-483 purchase and upkeep scenarios. */
public final class SkillPurchaseGameTest {

	@GameTest
	public void mod483BuyStoresTheNodeAndChargesTheStation(GameTestHelper helper) {
		SkillPurchaseScenarios.buyStoresTheNodeAndChargesTheStation(helper);
	}

	@GameTest
	public void mod483BuyRefusedWhenTheStationIsEmpty(GameTestHelper helper) {
		SkillPurchaseScenarios.buyRefusedWhenTheStationIsEmpty(helper);
	}

	@GameTest
	public void mod483BuyRefusedWithoutFragments(GameTestHelper helper) {
		SkillPurchaseScenarios.buyRefusedWithoutFragments(helper);
	}

	@GameTest
	public void mod483BuyRefusedOnTheClosedSideOfAFork(GameTestHelper helper) {
		SkillPurchaseScenarios.buyRefusedOnTheClosedSideOfAFork(helper);
	}

	@GameTest
	public void mod483OfflineOwnerGetsNoBuffs(GameTestHelper helper) {
		SkillPurchaseScenarios.offlineOwnerGetsNoBuffs(helper);
	}

	@GameTest
	public void mod483BuildSurvivesSaveAndLoad(GameTestHelper helper) {
		SkillPurchaseScenarios.buildSurvivesSaveAndLoad(helper);
	}

	/** Runs a 40-tick measurement window, so it needs more than the default budget. */
	@GameTest(maxTicks = 100)
	public void mod483UpkeepIsPricedPerTickNotPerVisit(GameTestHelper helper) {
		SkillPurchaseScenarios.upkeepIsPricedPerTickNotPerVisit(helper);
	}
}
