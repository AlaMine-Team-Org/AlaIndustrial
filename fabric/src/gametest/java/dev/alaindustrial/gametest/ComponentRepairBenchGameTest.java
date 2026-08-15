package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 coverage for the component repair bench (MOD-384).
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link ComponentRepairBenchScenarios}
 * ({@code common/src/gametest}).</b> What stays here is the Fabric wiring only: the
 * {@code @GameTest} annotation and a delegation, so the SAME scenario also runs on NeoForge, which
 * registers it in {@code NeoForgeGameTests}.
 *
 * <p>{@code maxTicks} is generous on purpose: a repair is 625 ticks at T1 and 2250 at T3, and the
 * grade scenario runs two of them back to back.
 */
public class ComponentRepairBenchGameTest {

	@GameTest(maxTicks = 900)
	public void repairsWornRotorAndLowersCeiling(GameTestHelper helper) {
		ComponentRepairBenchScenarios.repairsWornRotorAndLowersCeiling(helper);
	}

	@GameTest(maxTicks = 3200)
	public void ceilingLadderIsLinearAcrossRepeatedRepairs(GameTestHelper helper) {
		ComponentRepairBenchScenarios.ceilingLadderIsLinearAcrossRepeatedRepairs(helper);
	}

	@GameTest(maxTicks = 4200)
	public void everyGradeRepairsWithItsOwnMaterial(GameTestHelper helper) {
		ComponentRepairBenchScenarios.everyGradeRepairsWithItsOwnMaterial(helper);
	}

	@GameTest(maxTicks = 1000)
	public void missingMaterialFreezesProgressRatherThanResetting(GameTestHelper helper) {
		ComponentRepairBenchScenarios.missingMaterialFreezesProgressRatherThanResetting(helper);
	}

	@GameTest(maxTicks = 1600)
	public void spentComponentIsRefusedWithoutSpendingAnything(GameTestHelper helper) {
		ComponentRepairBenchScenarios.spentComponentIsRefusedWithoutSpendingAnything(helper);
	}

	@GameTest(maxTicks = 900)
	public void intactComponentIsNotTouched(GameTestHelper helper) {
		ComponentRepairBenchScenarios.intactComponentIsNotTouched(helper);
	}

	@GameTest(maxTicks = 900)
	public void wrongGradeMaterialIsRejected(GameTestHelper helper) {
		ComponentRepairBenchScenarios.wrongGradeMaterialIsRejected(helper);
	}

	@GameTest
	public void slotsRejectWhatTheyShould(GameTestHelper helper) {
		ComponentRepairBenchScenarios.slotsRejectWhatTheyShould(helper);
	}

	@GameTest(maxTicks = 900)
	public void extractionOpensOnlyWhenTheBenchIsDone(GameTestHelper helper) {
		ComponentRepairBenchScenarios.extractionOpensOnlyWhenTheBenchIsDone(helper);
	}

	@GameTest(maxTicks = 900)
	public void repairedPartSurvivesNbtRoundTrip(GameTestHelper helper) {
		ComponentRepairBenchScenarios.repairedPartSurvivesNbtRoundTrip(helper);
	}

	@GameTest(maxTicks = 1400)
	public void repairedWheelStillWearsInTheMill(GameTestHelper helper) {
		ComponentRepairBenchScenarios.repairedWheelStillWearsInTheMill(helper);
	}

	@GameTest(skyAccess = true, maxTicks = 900)
	public void repairedRotorSurvivesMillEvolution(GameTestHelper helper) {
		ComponentRepairBenchScenarios.repairedRotorSurvivesMillEvolution(helper);
	}
}
