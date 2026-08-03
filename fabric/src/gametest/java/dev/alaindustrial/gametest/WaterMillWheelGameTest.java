package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 coverage for the MOD-024 installable water-wheel component gate.
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link WaterMillWheelScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation.
 * That is what makes the SAME scenario run on BOTH loaders — NeoForge registers it in
 * {@code NeoForgeGameTests} — instead of only on whichever loader it happened to be written in.
 */
public class WaterMillWheelGameTest {

	@GameTest
	public void waterMillWheel_missingWheelStopsGeneration(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMillWheel_missingWheelStopsGeneration(helper);
	}

	@GameTest
	public void waterMillWheel_installedWheelEnablesGeneration(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMillWheel_installedWheelEnablesGeneration(helper);
	}

	@GameTest
	public void waterMillWheel_progressTracksWaterFaces(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMillWheel_progressTracksWaterFaces(helper);
	}

	@GameTest
	public void waterMillWheel_slotFilterIsDedicated(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMillWheel_slotFilterIsDedicated(helper);
	}

	@GameTest
	public void waterMill_craftingRecipeResolves(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_craftingRecipeResolves(helper);
	}

	@GameTest
	public void waterMillWheel_craftingRecipeResolves(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMillWheel_craftingRecipeResolves(helper);
	}

	@GameTest
	public void waterMill_sideBySideInterference(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_sideBySideInterference(helper);
	}

	@GameTest
	public void waterMill_faceToFaceInterference(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_faceToFaceInterference(helper);
	}

	@GameTest
	public void waterMill_spacedMillsDoNotInterfere(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_spacedMillsDoNotInterfere(helper);
	}

	@GameTest
	public void waterMill_backToBackDoNotInterfere(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_backToBackDoNotInterfere(helper);
	}

	@GameTest
	public void waterMill_faceToFaceAdjacentObstructed(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_faceToFaceAdjacentObstructed(helper);
	}

	@GameTest
	public void waterMill_wallInFrontStallsAndRecovers(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_wallInFrontStallsAndRecovers(helper);
	}

	@GameTest
	public void waterMill_blockAboveFrontAlsoObstructs(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_blockAboveFrontAlsoObstructs(helper);
	}

	@GameTest
	public void waterMill_solidRiverBedBelowFrontIsAllowed(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_solidRiverBedBelowFrontIsAllowed(helper);
	}

	@GameTest
	public void waterMill_sideBlockObstructs(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_sideBlockObstructs(helper);
	}

	@GameTest
	public void waterMill_waterBesideWheelIsAllowed(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_waterBesideWheelIsAllowed(helper);
	}

	@GameTest
	public void waterMill_interferenceClearsWithinScanInterval(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_interferenceClearsWithinScanInterval(helper);
	}

	@GameTest
	public void waterMill_neighbourWheelRemovalClearsInterference(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_neighbourWheelRemovalClearsInterference(helper);
	}

	@GameTest
	public void waterMill_dryMillShowsNoWaterHint(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_dryMillShowsNoWaterHint(helper);
	}

	@GameTest
	public void waterMill_stillSourceDoesNotGenerate(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_stillSourceDoesNotGenerate(helper);
	}

	@GameTest
	public void waterMill_energyOnlyFromBackFace(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_energyOnlyFromBackFace(helper);
	}

	@GameTest
	public void waterMill_frontFaceInertForAutomation(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_frontFaceInertForAutomation(helper);
	}

	@GameTest
	public void waterMill_automationCannotStackSecondWheel(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMill_automationCannotStackSecondWheel(helper);
	}

	@GameTest
	public void waterMillWheel_wearsOutAndBreaks(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMillWheel_wearsOutAndBreaks(helper);
	}

	@GameTest
	public void waterMillWheel_noWearWhileDry(GameTestHelper helper) {
		WaterMillWheelScenarios.waterMillWheel_noWearWhileDry(helper);
	}
}
