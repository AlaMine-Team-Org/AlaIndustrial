package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Thin Fabric wrappers for the loader-neutral MOD-425 Thermal Centrifuge scenarios.
 *
 * <p>The {@code maxTicks} budgets below are formal, not load-bearing: {@code AlaGameTestHelper.drive}
 * spins {@code serverTick} inside a SINGLE game tick, so a whole scenario finishes in one. They are
 * set to match the NeoForge registrations one-for-one so the two lanes cannot drift.
 */
public class ThermalCentrifugeGameTest {

	@GameTest(maxTicks = 600)
	public void tcCentr001Neg01_noSignalCostsNothingAnywhere(GameTestHelper helper) {
		ThermalCentrifugeScenarios.neg01NoSignalCostsNothingAnywhere(helper);
	}

	@GameTest(maxTicks = 40)
	public void tcCentr001Fun01_spinUpCostsTheWorkingRate(GameTestHelper helper) {
		ThermalCentrifugeScenarios.fun01SpinUpCostsTheWorkingRate(helper);
	}

	@GameTest(maxTicks = 40)
	public void tcCentr001Fun05_signalWakesTheMachineImmediately(GameTestHelper helper) {
		ThermalCentrifugeScenarios.fun05SignalWakesTheMachineImmediately(helper);
	}

	@GameTest(maxTicks = 200)
	public void tcCentr001Fun02_fullSpinTakesExactlyConfigTicks(GameTestHelper helper) {
		ThermalCentrifugeScenarios.fun02FullSpinTakesExactlyConfigTicks(helper);
	}

	@GameTest(maxTicks = 200)
	public void tcCentr001Fun03_cutSignalDropsTheSpinOutright(GameTestHelper helper) {
		ThermalCentrifugeScenarios.fun03CutSignalDropsTheSpinOutright(helper);
	}

	@GameTest(maxTicks = 200)
	public void tcCentr001Neg02_starvedRotorShedsOneRevolutionThenFreezes(GameTestHelper helper) {
		ThermalCentrifugeScenarios.neg02StarvedRotorShedsOneRevolutionThenFreezes(helper);
	}

	@GameTest(maxTicks = 1000)
	public void tcCentr001Neg03_passiveHeatDoesNotStartIt(GameTestHelper helper) {
		ThermalCentrifugeScenarios.neg03PassiveHeatDoesNotStartIt(helper);
	}

	@GameTest(maxTicks = 600)
	public void tcCentr001Fun04_hotHeaterMakesTwoShavings(GameTestHelper helper) {
		ThermalCentrifugeScenarios.fun04HotHeaterMakesTwoShavings(helper);
	}

	@GameTest(maxTicks = 600)
	public void tcCentr001Con01_blockedOutputStopsWorkAndNamesIt(GameTestHelper helper) {
		ThermalCentrifugeScenarios.con01BlockedOutputStopsWorkAndNamesIt(helper);
	}

	@GameTest(maxTicks = 300)
	public void tcCentr001Sta01_spinSurvivesReload(GameTestHelper helper) {
		ThermalCentrifugeScenarios.sta01SpinSurvivesReload(helper);
	}
}
