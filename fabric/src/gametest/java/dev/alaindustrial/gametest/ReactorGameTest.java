package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric entry points for {@link ReactorScenarios} (MOD-468).
 *
 * <p>Thin by design: the scenarios themselves live in {@code common} so the NeoForge lane replays the
 * identical code, and this class is only the annotation that makes Fabric find them.
 */
public class ReactorGameTest {

	@GameTest(maxTicks = 400)
	public void sealedFuelledAndPoweredReactorProduces(GameTestHelper helper) {
		ReactorScenarios.sealedFuelledAndPoweredReactorProduces(helper);
	}

	@GameTest(maxTicks = 400)
	public void removingTheSignalScramsTheReactor(GameTestHelper helper) {
		ReactorScenarios.removingTheSignalScramsTheReactor(helper);
	}

	@GameTest(maxTicks = 400)
	public void coolantCatchesACoreTheShellCannotHold(GameTestHelper helper) {
		ReactorScenarios.coolantCatchesACoreTheShellCannotHold(helper);
	}

	@GameTest(maxTicks = 400)
	public void poweredReactorFeedsACableOutsideTheShell(GameTestHelper helper) {
		ReactorScenarios.poweredReactorFeedsACableOutsideTheShell(helper);
	}

	@GameTest(maxTicks = 400)
	public void everyRackedRodWearsTogether(GameTestHelper helper) {
		ReactorScenarios.everyRackedRodWearsTogether(helper);
	}

	@GameTest(maxTicks = 400)
	public void nozzleVentsIntoAirAndStallsAgainstAWall(GameTestHelper helper) {
		ReactorScenarios.nozzleVentsIntoAirAndStallsAgainstAWall(helper);
	}

	@GameTest(maxTicks = 400)
	public void bareReactorProducesMeltsAndObeysTheSwitch(GameTestHelper helper) {
		ReactorScenarios.bareReactorProducesMeltsAndObeysTheSwitch(helper);
	}

	@GameTest(maxTicks = 400)
	public void breachingAWallDropsTheReactorIntoBareMode(GameTestHelper helper) {
		ReactorScenarios.breachingAWallDropsTheReactorIntoBareMode(helper);
	}

	@GameTest(maxTicks = 400)
	public void onlyOneControllerBurnsASharedRack(GameTestHelper helper) {
		ReactorScenarios.onlyOneControllerBurnsASharedRack(helper);
	}

	@GameTest(maxTicks = 400)
	public void anOverheatingRoomMeltsItsContentsAndKeepsItsShell(GameTestHelper helper) {
		ReactorScenarios.anOverheatingRoomMeltsItsContentsAndKeepsItsShell(helper);
	}
}
