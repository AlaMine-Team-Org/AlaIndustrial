package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric entry points for the overclocker-effect suite (MOD-392); bodies live in common. */
public class OverclockerEffectGameTest {

	/** Two full grinds back to back. */
	@GameTest(maxTicks = 900)
	public void overclocker_chipSpeedsTheMachineAndCostsMore(GameTestHelper helper) {
		OverclockerEffectScenarios.overclocker_chipSpeedsTheMachineAndCostsMore(helper);
	}

	@GameTest
	public void overclocker_chipAndProgressSurviveReload(GameTestHelper helper) {
		OverclockerEffectScenarios.overclocker_chipAndProgressSurviveReload(helper);
	}
}
