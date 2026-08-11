package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric entry points for the overclocker-arm suite (MOD-392); bodies live in common. */
public class OverclockerPanelGameTest {

	@GameTest
	public void overclocker_generatorRefusesTheChip(GameTestHelper helper) {
		OverclockerPanelScenarios.overclocker_generatorRefusesTheChip(helper);
	}

	@GameTest
	public void overclocker_refusalSurvivesAPanelDrag(GameTestHelper helper) {
		OverclockerPanelScenarios.overclocker_refusalSurvivesAPanelDrag(helper);
	}

	@GameTest
	public void overclocker_manifestMatchesTheBlockEntity(GameTestHelper helper) {
		OverclockerPanelScenarios.overclocker_manifestMatchesTheBlockEntity(helper);
	}
}
