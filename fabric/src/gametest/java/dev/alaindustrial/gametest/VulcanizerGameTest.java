package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Thin Fabric wrappers for the loader-neutral MOD-258 Vulcanizer scenarios. */
public class VulcanizerGameTest {

	@GameTest(maxTicks = 700)
	public void tcVulc001Fun01_heatLevelsScaleOutput(GameTestHelper helper) {
		VulcanizerScenarios.fun01HeatLevelsScaleOutput(helper);
	}

	@GameTest
	public void tcVulc001Fun02_allPassiveHeatSourcesResolve(GameTestHelper helper) {
		VulcanizerScenarios.fun02AllPassiveHeatSourcesResolve(helper);
	}

	@GameTest
	public void tcVulc001Neg01_noHeatNoWork(GameTestHelper helper) {
		VulcanizerScenarios.neg01NoHeatNoWork(helper);
	}

	@GameTest(maxTicks = 300)
	public void tcVulc001Neg02_noPowerNoWork(GameTestHelper helper) {
		VulcanizerScenarios.neg02NoPowerNoWork(helper);
	}

	@GameTest
	public void tcVulc001Neg03_inactiveHeatSourcesResolveAsNone(GameTestHelper helper) {
		VulcanizerScenarios.neg03InactiveHeatSourcesResolveAsNone(helper);
	}

	@GameTest(maxTicks = 300)
	public void tcVulc001Neg04_partialSulfurBatchDoesNotWork(GameTestHelper helper) {
		VulcanizerScenarios.neg04PartialSulfurBatchDoesNotWork(helper);
	}

	@GameTest
	public void tcVulc001Con01_outputJamFreezesBothConsumers(GameTestHelper helper) {
		VulcanizerScenarios.con01OutputJamFreezesBothConsumers(helper);
	}

	@GameTest
	public void tcVulc001Con02_heaterIsDemandDriven(GameTestHelper helper) {
		VulcanizerScenarios.con02HeaterIsDemandDriven(helper);
	}

	@GameTest
	public void tcVulc001Con03_heaterTariffIsAtomicAtThreshold(GameTestHelper helper) {
		VulcanizerScenarios.con03HeaterTariffIsAtomicAtThreshold(helper);
	}

	@GameTest(maxTicks = 300)
	public void tcVulc001Reg01_heatUpgradeRestartsCycle(GameTestHelper helper) {
		VulcanizerScenarios.reg01HeatUpgradeRestartsCycle(helper);
	}

	@GameTest
	public void tcVulc001Reg02_automationKeepsInputsSeparated(GameTestHelper helper) {
		VulcanizerScenarios.reg02AutomationKeepsInputsSeparated(helper);
	}

	@GameTest(maxTicks = 300)
	public void tcVulc001Reg03_heatDowngradeRestartsCycle(GameTestHelper helper) {
		VulcanizerScenarios.reg03HeatDowngradeRestartsCycle(helper);
	}

	@GameTest
	public void tcVulc001Sta01_roundTripPreservesInFlightCycle(GameTestHelper helper) {
		VulcanizerScenarios.sta01RoundTripPreservesInFlightCycle(helper);
	}

	@GameTest(maxTicks = 300)
	public void tcVulc001Fun03_rubberProductionAdvancement(GameTestHelper helper) {
		VulcanizerScenarios.fun03RubberProductionAdvancement(helper);
	}
}
