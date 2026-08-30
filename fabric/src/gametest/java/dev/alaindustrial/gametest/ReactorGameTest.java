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

	@GameTest(maxTicks = 400)
	public void aShieldedLeverInsideTheRoomSealsAndScrams(GameTestHelper helper) {
		ReactorScenarios.aShieldedLeverInsideTheRoomSealsAndScrams(helper);
	}

	// MOD-471 — the accident at the top of the scale, and the lava farm it now has a limit for.
	@GameTest(maxTicks = 400)
	public void aCoreAtFullScaleCountsDownAndBlowsItsRoomApart(GameTestHelper helper) {
		ReactorScenarios.aCoreAtFullScaleCountsDownAndBlowsItsRoomApart(helper);
	}

	@GameTest(maxTicks = 400)
	public void aRedstoneClockDoesNotSaveTheReactor(GameTestHelper helper) {
		ReactorScenarios.aRedstoneClockDoesNotSaveTheReactor(helper);
	}

	@GameTest(maxTicks = 400)
	public void aFullBufferStillCooksTheCore(GameTestHelper helper) {
		ReactorScenarios.aFullBufferStillCooksTheCore(helper);
	}

	@GameTest(maxTicks = 400)
	public void aBareClusterSettlesUntilItIsTooBig(GameTestHelper helper) {
		ReactorScenarios.aBareClusterSettlesUntilItIsTooBig(helper);
	}

	@GameTest(maxTicks = 400)
	public void aLavaFarmBurnsNoFuel(GameTestHelper helper) {
		ReactorScenarios.aLavaFarmBurnsNoFuel(helper);
	}

	@GameTest(maxTicks = 400)
	public void aBlockedExplosionLeavesNoAftermath(GameTestHelper helper) {
		ReactorScenarios.aBlockedExplosionLeavesNoAftermath(helper);
	}

	@GameTest(maxTicks = 400)
	public void aBrokenRackGivesItsRodsBack(GameTestHelper helper) {
		ReactorScenarios.aBrokenRackGivesItsRodsBack(helper);
	}

	@GameTest(maxTicks = 400)
	public void reactorMilestonesReachTheControllersOwner(GameTestHelper helper) {
		ReactorScenarios.reactorMilestonesReachTheControllersOwner(helper);
	}

	@GameTest(maxTicks = 400)
	public void anUnownedReactorAwardsNobody(GameTestHelper helper) {
		ReactorScenarios.anUnownedReactorAwardsNobody(helper);
	}
}
