package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the crystal greenhouse (MOD-505, suite TC-FARM-001). Thin Fabric wrappers:
 * the bodies are loader-neutral in {@code common/.../gametest/CrystalFarmScenarios} and the SAME
 * bodies run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders
 * exercise identical room logic.
 */
public class CrystalFarmGameTest {

	/** @implements TC-FARM-001-FUN01 — a closed greenhouse seals and paints its shell. */
	@GameTest
	public void tcFarm001Fun01_sealedRoomForms(GameTestHelper helper) {
		CrystalFarmScenarios.fun01SealedRoomForms(helper);
	}

	/** @implements TC-FARM-001-FUN02 — a breached greenhouse un-forms and its shell goes loose. */
	@GameTest
	public void tcFarm001Fun02_breachUnformsAndRepaints(GameTestHelper helper) {
		CrystalFarmScenarios.fun02BreachUnformsAndRepaints(helper);
	}

	/** @implements TC-FARM-001-FUN03 — a breach is reported at the missing block, not at the leash. */
	@GameTest
	public void tcFarm001Fun03_breachIsReportedAtTheHole(GameTestHelper helper) {
		CrystalFarmScenarios.fun03BreachIsReportedAtTheHole(helper);
	}

	/** @implements TC-FARM-001-FUN05 — removing the controller by any means clears the shell. */
	@GameTest
	public void tcFarm001Fun05_controllerRemovedAnyWayClearsUp(GameTestHelper helper) {
		CrystalFarmScenarios.fun05ControllerRemovedAnyWayClearsUp(helper);
	}

	/** @implements TC-FARM-001-FUN04 — a seedbed is tended inside a sealed room, and only there. */
	@GameTest
	public void tcFarm001Fun04_seedbedKnowsItIsTended(GameTestHelper helper) {
		CrystalFarmScenarios.fun04SeedbedKnowsItIsTended(helper);
	}

	/**
	 * @implements TC-FARM-001-FUN06 — a hand-opened greenhouse door shuts itself again.
	 *     {@code maxTicks} covers the shipped 100-tick delay plus the scenario's own margin.
	 */
	@GameTest(maxTicks = 200)
	public void tcFarm001Fun06_doorClosesAfterHandOpen(GameTestHelper helper) {
		CrystalFarmScenarios.fun06DoorClosesAfterHandOpen(helper);
	}

	/** @implements TC-FARM-001-FUN07 — a door under a live signal still closes, and stays closed. */
	@GameTest(maxTicks = 200)
	public void tcFarm001Fun07_doorClosesUnderRedstone(GameTestHelper helper) {
		CrystalFarmScenarios.fun07DoorClosesUnderRedstone(helper);
	}

	/** @implements TC-FARM-001-FUN08 — an occupied doorway re-arms the timer, a cleared one closes. */
	@GameTest(maxTicks = 200)
	public void tcFarm001Fun08_doorWaitsForTheDoorwayToClear(GameTestHelper helper) {
		CrystalFarmScenarios.fun08DoorWaitsForTheDoorwayToClear(helper);
	}
}
