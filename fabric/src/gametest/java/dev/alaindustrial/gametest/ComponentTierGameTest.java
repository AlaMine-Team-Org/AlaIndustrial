package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 coverage for the MOD-385 three-grade component ladder (rotor and wheel).
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link ComponentTierScenarios}
 * ({@code common/src/gametest}).</b> What stays here is the Fabric wiring only: the
 * {@code @GameTest} annotation and a delegation, so the SAME scenario also runs on NeoForge, which
 * registers it in {@code NeoForgeGameTests}.
 */
public class ComponentTierGameTest {

	@GameTest(skyAccess = true, maxTicks = 600)
	public void windMill_betterRotorProducesMoreEu(GameTestHelper helper) {
		ComponentTierScenarios.windMill_betterRotorProducesMoreEu(helper);
	}

	@GameTest(maxTicks = 600)
	public void waterMill_betterWheelProducesMoreEu(GameTestHelper helper) {
		ComponentTierScenarios.waterMill_betterWheelProducesMoreEu(helper);
	}

	@GameTest(maxTicks = 600)
	public void baselineGradeIsUnchanged(GameTestHelper helper) {
		ComponentTierScenarios.baselineGradeIsUnchanged(helper);
	}

	@GameTest(skyAccess = true, maxTicks = 600)
	public void noGradeExceedsTheWindMillCap(GameTestHelper helper) {
		ComponentTierScenarios.noGradeExceedsTheWindMillCap(helper);
	}

	@GameTest
	public void rotorSlotAcceptsEveryGrade(GameTestHelper helper) {
		ComponentTierScenarios.rotorSlotAcceptsEveryGrade(helper);
	}

	@GameTest
	public void wheelSlotAcceptsEveryGrade(GameTestHelper helper) {
		ComponentTierScenarios.wheelSlotAcceptsEveryGrade(helper);
	}

	@GameTest(skyAccess = true, maxTicks = 600)
	public void evolutionCarriesTheUpgradedRotor(GameTestHelper helper) {
		ComponentTierScenarios.evolutionCarriesTheUpgradedRotor(helper);
	}
}
