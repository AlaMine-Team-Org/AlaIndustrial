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
}
