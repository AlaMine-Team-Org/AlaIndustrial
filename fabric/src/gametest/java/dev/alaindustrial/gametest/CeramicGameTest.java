package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric entry points for the quench press (MOD-590) — logic lives in {@link CeramicScenarios}. */
public class CeramicGameTest {

	@GameTest(maxTicks = 100)
	public void quenchPressSplitsFloatingBriquettes(GameTestHelper helper) {
		CeramicScenarios.quenchPressSplitsFloatingBriquettes(helper);
	}

	@GameTest(maxTicks = 100)
	public void dryPressPaysNothing(GameTestHelper helper) {
		CeramicScenarios.dryPressPaysNothing(helper);
	}

	@GameTest(maxTicks = 100)
	public void pressWithoutRedstonePaysNothing(GameTestHelper helper) {
		CeramicScenarios.pressWithoutRedstonePaysNothing(helper);
	}
}
