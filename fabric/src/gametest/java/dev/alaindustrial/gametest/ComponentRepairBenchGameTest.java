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
 * <p>{@code maxTicks} is generous on purpose: a repair is 1200 ticks at T1 and 3600 at T3 (MOD-465),
 * and the grade scenario runs two of them back to back.
 */
public class ComponentRepairBenchGameTest {

	@GameTest(maxTicks = 1800)
	public void repairsWornRotorAndLowersCeiling(GameTestHelper helper) {
		ComponentRepairBenchScenarios.repairsWornRotorAndLowersCeiling(helper);
	}

	@GameTest(maxTicks = 6200)
	public void ceilingLadderIsLinearAcrossRepeatedRepairs(GameTestHelper helper) {
		ComponentRepairBenchScenarios.ceilingLadderIsLinearAcrossRepeatedRepairs(helper);
	}

	@GameTest(maxTicks = 8000)
	public void everyGradeRepairsWithItsOwnMaterial(GameTestHelper helper) {
		ComponentRepairBenchScenarios.everyGradeRepairsWithItsOwnMaterial(helper);
	}

	@GameTest(maxTicks = 3200)
	public void missingMaterialResetsProgress(GameTestHelper helper) {
		ComponentRepairBenchScenarios.missingMaterialResetsProgress(helper);
	}

	@GameTest(maxTicks = 3100)
	public void spentComponentIsRefusedWithoutSpendingAnything(GameTestHelper helper) {
		ComponentRepairBenchScenarios.spentComponentIsRefusedWithoutSpendingAnything(helper);
	}

	@GameTest(maxTicks = 1800)
	public void intactComponentIsNotTouched(GameTestHelper helper) {
		ComponentRepairBenchScenarios.intactComponentIsNotTouched(helper);
	}

	@GameTest(maxTicks = 1800)
	public void wrongGradeMaterialIsRejected(GameTestHelper helper) {
		ComponentRepairBenchScenarios.wrongGradeMaterialIsRejected(helper);
	}

	@GameTest
	public void slotsRejectWhatTheyShould(GameTestHelper helper) {
		ComponentRepairBenchScenarios.slotsRejectWhatTheyShould(helper);
	}

	@GameTest(maxTicks = 1800)
	public void extractionOpensOnlyWhenTheBenchIsDone(GameTestHelper helper) {
		ComponentRepairBenchScenarios.extractionOpensOnlyWhenTheBenchIsDone(helper);
	}

	@GameTest(maxTicks = 1800)
	public void repairedPartSurvivesNbtRoundTrip(GameTestHelper helper) {
		ComponentRepairBenchScenarios.repairedPartSurvivesNbtRoundTrip(helper);
	}

	@GameTest(maxTicks = 2600)
	public void repairedWheelStillWearsInTheMill(GameTestHelper helper) {
		ComponentRepairBenchScenarios.repairedWheelStillWearsInTheMill(helper);
	}

	@GameTest(skyAccess = true, maxTicks = 1800)
	public void repairedRotorSurvivesMillEvolution(GameTestHelper helper) {
		ComponentRepairBenchScenarios.repairedRotorSurvivesMillEvolution(helper);
	}
}
