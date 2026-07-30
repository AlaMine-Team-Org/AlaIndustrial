package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the trellis (MOD-280). Thin Fabric wrappers: the bodies are
 * loader-neutral in {@code common/.../gametest/TrellisScenarios} and the SAME bodies run on the
 * NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders exercise identical
 * planting, growth-gating, non-destructive harvest and drop rules.
 */
public class TrellisGameTest {

	/** @implements TC-TRELLIS-001-FUN01 — a seed plants on a bare trellis and spends exactly one. */
	@GameTest
	public void fun01_plantsSeed(GameTestHelper helper) {
		TrellisScenarios.fun01PlantsSeed(helper);
	}

	/** @implements TC-TRELLIS-001-NEG01 — clicking a planted trellis eats no seed. */
	@GameTest
	public void neg01_keepsSeedOnPlantedTrellis(GameTestHelper helper) {
		TrellisScenarios.neg01KeepsSeedOnPlantedTrellis(helper);
	}

	/** @implements TC-TRELLIS-001-FUN02 — the plant advances on moist farmland through the random tick. */
	@GameTest
	public void fun02_growsOnMoistFarmland(GameTestHelper helper) {
		TrellisScenarios.fun02GrowsOnMoistFarmland(helper);
	}

	/** @implements TC-TRELLIS-001-NEG02 — dry soil pauses growth without losing the stage or the plant. */
	@GameTest
	public void neg02_pausesOnDryFarmland(GameTestHelper helper) {
		TrellisScenarios.neg02PausesOnDryFarmland(helper);
	}

	/** @implements TC-TRELLIS-001-FUN03 — harvesting yields fibre, keeps the plant, resets it to mature. */
	@GameTest
	public void fun03_harvestKeepsPlant(GameTestHelper helper) {
		TrellisScenarios.fun03HarvestKeepsPlant(helper);
	}

	/** @implements TC-TRELLIS-001-NEG03 — an unripe bush yields nothing and does not change state. */
	@GameTest
	public void neg03_unripeYieldsNothing(GameTestHelper helper) {
		TrellisScenarios.neg03UnripeYieldsNothing(helper);
	}

	/** @implements TC-TRELLIS-001-REG01 — farmland survives under the collidable trellis. */
	@GameTest
	public void reg01_farmlandSurvivesUnderTrellis(GameTestHelper helper) {
		TrellisScenarios.reg01FarmlandSurvivesUnderTrellis(helper);
	}

	/** @implements TC-TRELLIS-001-REG02 — breaking the structure drops exactly one trellis. */
	@GameTest
	public void reg02_breakDropsExactlyOneTrellis(GameTestHelper helper) {
		TrellisScenarios.reg02BreakDropsExactlyOneTrellis(helper);
	}

	/** @implements TC-TRELLIS-001-REG03 — the harvest works with seeds in hand. */
	@GameTest
	public void reg03_harvestWorksWithSeedsInHand(GameTestHelper helper) {
		TrellisScenarios.reg03HarvestWorksWithSeedsInHand(helper);
	}

	/** @implements TC-TRELLIS-001-REG04 — a planted trellis returns its seed when broken. */
	@GameTest
	public void reg04_plantedTrellisReturnsSeed(GameTestHelper helper) {
		TrellisScenarios.reg04PlantedTrellisReturnsSeed(helper);
	}

	/** @implements TC-TRELLIS-001-NEG04 — a bare trellis returns no seed (no free-seed exploit). */
	@GameTest
	public void neg04_bareTrellisReturnsNoSeed(GameTestHelper helper) {
		TrellisScenarios.neg04BareTrellisReturnsNoSeed(helper);
	}

	/** @implements TC-TRELLIS-001-FUN04 — bone meal advances exactly one stage. */
	@GameTest
	public void fun04_bonemealAdvancesOneStage(GameTestHelper helper) {
		TrellisScenarios.fun04BonemealAdvancesOneStage(helper);
	}

	/** @implements TC-TRELLIS-001-NEG05 — bone meal obeys the water/light gate. */
	@GameTest
	public void neg05_bonemealRefusesDrySoil(GameTestHelper helper) {
		TrellisScenarios.neg05BonemealRefusesDrySoil(helper);
	}

	/** @implements TC-TRELLIS-001-FUN05 — the fruiting cycle repeats for a second harvest. */
	@GameTest
	public void fun05_secondHarvestCycle(GameTestHelper helper) {
		TrellisScenarios.fun05SecondHarvestCycle(helper);
	}

	/** @implements TC-TRELLIS-001-REG05 — breaking the upper half also drops exactly one trellis. */
	@GameTest
	public void reg05_breakingUpperHalfDropsOne(GameTestHelper helper) {
		TrellisScenarios.reg05BreakingUpperHalfDropsOne(helper);
	}

}
