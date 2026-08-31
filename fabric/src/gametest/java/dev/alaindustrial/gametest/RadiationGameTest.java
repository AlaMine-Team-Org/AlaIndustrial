package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for radiation (MOD-470). Thin Fabric wrappers: the bodies are loader-neutral in
 * {@code common/.../gametest/RadiationScenarios} and the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders exercise identical
 * line-of-sight, exposure and transformation logic.
 */
public class RadiationGameTest {

	/**
	 * @implements R-RAD-01 — a fuelled rod irradiates what it can see across open air.
	 */
	@GameTest
	public void radRodIrradiatesWhatItCanSee(GameTestHelper helper) {
		RadiationScenarios.rodIrradiatesWhatItCanSee(helper);
	}

	/**
	 * @implements R-RAD-02 — one casing block between rod and bystander takes the exposure to zero.
	 */
	@GameTest
	public void radCasingBlocksTheRod(GameTestHelper helper) {
		RadiationScenarios.casingBlocksTheRod(helper);
	}

	/**
	 * @implements R-RAD-03 — a villager past the threshold becomes a persistent zombie villager.
	 */
	@GameTest
	public void radVillagerBecomesZombieVillager(GameTestHelper helper) {
		RadiationScenarios.villagerBecomesZombieVillager(helper);
	}

	/**
	 * @implements R-RAD-13 — a full suit is complete protection on a mob (MOD-535).
	 */
	@GameTest
	public void radSuitedVillagerTakesNoDose(GameTestHelper helper) {
		RadiationScenarios.suitedVillagerTakesNoDose(helper);
	}

	/**
	 * @implements R-RAD-14 — a real dispenser dresses the villager, and the suit then answers (MOD-535).
	 */
	@GameTest
	public void radDispenserDressesTheVillagerForRadiation(GameTestHelper helper) {
		RadiationScenarios.dispenserDressesTheVillagerForRadiation(helper);
	}

	/**
	 * @implements R-RAD-15 — a miss is a visible eject, never a silently dressed bystander (MOD-535).
	 */
	@GameTest
	public void radDispenserRefusesToDressAnyoneButConvertibleMobs(GameTestHelper helper) {
		RadiationScenarios.dispenserRefusesToDressAnyoneButConvertibleMobs(helper);
	}

	/**
	 * @implements R-RAD-16 — the live server-tick chain shields a suited villager end to end (MOD-535).
	 */
	@GameTest(maxTicks = 200)
	public void radLiveTickChainShieldsTheSuitedVillager(GameTestHelper helper) {
		RadiationScenarios.liveTickChainShieldsTheSuitedVillager(helper);
	}

	/**
	 * @implements R-RAD-04 — a cow becomes a mooshroom and is never hurt on the way.
	 */
	@GameTest
	public void radCowBecomesMooshroom(GameTestHelper helper) {
		RadiationScenarios.cowBecomesMooshroom(helper);
	}

	/**
	 * @implements R-RAD-05 — a zombie villager accumulates no dose; it is already the outcome.
	 */
	@GameTest
	public void radZombieVillagerIsPastTheEnd(GameTestHelper helper) {
		RadiationScenarios.zombieVillagerIsPastTheEnd(helper);
	}

	/**
	 * @implements R-RAD-06 — uranium dropped on the ground keeps radiating.
	 */
	@GameTest
	public void radDroppedUraniumStillRadiates(GameTestHelper helper) {
		RadiationScenarios.droppedUraniumStillRadiates(helper);
	}

	/**
	 * @implements R-RAD-07 — the same rack hits harder at one block than at two.
	 */
	@GameTest
	public void radDistanceWeakensTheRod(GameTestHelper helper) {
		RadiationScenarios.distanceWeakensTheRod(helper);
	}

	/**
	 * @implements R-RAD-08 — a wall stops dropped uranium exactly as it stops a rod.
	 */
	@GameTest
	public void radCasingBlocksDroppedUranium(GameTestHelper helper) {
		RadiationScenarios.casingBlocksDroppedUranium(helper);
	}

	/**
	 * @implements R-RAD-09 — a closed airlock stops the rod, an open one leaks.
	 */
	@GameTest
	public void radOpenDoorLeaksRadiation(GameTestHelper helper) {
		RadiationScenarios.openDoorLeaksRadiation(helper);
	}

	/**
	 * @implements R-RAD-12 — a lever bolted to the shell leaves the room's radiation picture alone.
	 */
	@GameTest
	public void radShieldedLeverOnTheWallDoesNotLeak(GameTestHelper helper) {
		RadiationScenarios.shieldedLeverOnTheWallDoesNotLeakRadiation(helper);
	}

	/**
	 * @implements R-RAD-10 — uranium radiates through an ordinary chest and not through a shielding one.
	 */
	@GameTest
	public void radShieldingChestStopsWhatAnOrdinaryChestDoesNot(GameTestHelper helper) {
		RadiationScenarios.shieldingChestStopsWhatAnOrdinaryChestDoesNot(helper);
	}

	/**
	 * @implements R-RAD-11 — the sweep leaves a chest whose loot is not generated yet untouched.
	 */
	@GameTest
	public void radSweepLeavesUngeneratedLootAlone(GameTestHelper helper) {
		RadiationScenarios.sweepLeavesUngeneratedLootAlone(helper);
	}
}
