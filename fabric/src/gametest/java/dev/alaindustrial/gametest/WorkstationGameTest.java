package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration for the loader-neutral MOD-483 workstation scenarios. */
public final class WorkstationGameTest {

	@GameTest
	public void mod483TwoCasingsAssemble(GameTestHelper helper) {
		WorkstationScenarios.frm01TwoCasingsAssemble(helper);
	}

	@GameTest
	public void mod483ThreeCasingsPairTheBottomTwo(GameTestHelper helper) {
		WorkstationScenarios.frm02ThreeCasingsPairTheBottomTwo(helper);
	}

	@GameTest
	public void mod483BreakingUpperDegradesLower(GameTestHelper helper) {
		WorkstationScenarios.brk01BreakingUpperDegradesLower(helper);
	}

	@GameTest
	public void mod483BreakingLowerDegradesUpper(GameTestHelper helper) {
		WorkstationScenarios.brk02BreakingLowerDegradesUpper(helper);
	}

	@GameTest
	public void mod483OnlyTheLowerHalfTakesEnergy(GameTestHelper helper) {
		WorkstationScenarios.nrg01OnlyTheLowerHalfTakesEnergy(helper);
	}
}
