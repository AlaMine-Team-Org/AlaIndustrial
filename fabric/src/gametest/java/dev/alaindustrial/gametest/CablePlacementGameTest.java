package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for MOD-039 "cable cannot be placed flush against another cable" plus the cable
 * connection-arm contracts (MOD-038 / MOD-061 / MOD-071 / MOD-194).
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link CablePlacementScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation.
 * That is what makes the SAME scenario run on BOTH loaders — NeoForge registers it in
 * {@code NeoForgeGameTests} — instead of only on whichever loader it happened to be written in.
 * The class-wide MOD-199 sweep already delegated to {@link CableFaceParityScenarios} and keeps
 * doing so.
 */
public class CablePlacementGameTest {

	@GameTest
	public void mod039_cableUseReturnsPass(GameTestHelper helper) {
		CablePlacementScenarios.mod039_cableUseReturnsPass(helper);
	}

	@GameTest
	public void mod039_machineUseReturnsSuccess(GameTestHelper helper) {
		CablePlacementScenarios.mod039_machineUseReturnsSuccess(helper);
	}

	@GameTest
	public void mod039_rmbOnCablePlacesAdjacent(GameTestHelper helper) {
		CablePlacementScenarios.mod039_rmbOnCablePlacesAdjacent(helper);
	}

	@GameTest
	public void mod038_cableDoesNotConnectToIronChest(GameTestHelper helper) {
		CablePlacementScenarios.mod038_cableDoesNotConnectToIronChest(helper);
	}

	@GameTest
	public void cableConnectsOnlyToWindMillBackFace(GameTestHelper helper) {
		CablePlacementScenarios.cableConnectsOnlyToWindMillBackFace(helper);
	}

	@GameTest
	public void cableConnectsOnlyToWaterMillBackFace(GameTestHelper helper) {
		CablePlacementScenarios.cableConnectsOnlyToWaterMillBackFace(helper);
	}

	@GameTest
	public void cableConnectsOnlyToBatteryBoxIOFaces(GameTestHelper helper) {
		CablePlacementScenarios.cableConnectsOnlyToBatteryBoxIOFaces(helper);
	}

	@GameTest
	public void mod061_cableDoesNotArmToGeneratorFacing(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableDoesNotArmToGeneratorFacing(helper);
	}

	@GameTest
	public void mod061_cableDoesNotArmToGeothermalFacing(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableDoesNotArmToGeothermalFacing(helper);
	}

	@GameTest
	public void mod061_cableDoesNotArmToMaceratorFacing(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableDoesNotArmToMaceratorFacing(helper);
	}

	@GameTest
	public void mod061_cableDoesNotArmToElectricFurnaceFacing(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableDoesNotArmToElectricFurnaceFacing(helper);
	}

	@GameTest
	public void mod061_cableDoesNotArmToExtractorFacing(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableDoesNotArmToExtractorFacing(helper);
	}

	@GameTest
	public void mod061_cableDoesNotArmToCompressorFacing(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableDoesNotArmToCompressorFacing(helper);
	}

	@GameTest
	public void mod061_cableFacingInertOnNonNorthFacing(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableFacingInertOnNonNorthFacing(helper);
	}

	@GameTest
	public void mod061_cableDoesNotArmToPumpFacing(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableDoesNotArmToPumpFacing(helper);
	}

	@GameTest
	public void mod061_cableStaleFlagsReshapeOnFirstTick(GameTestHelper helper) {
		CablePlacementScenarios.mod061_cableStaleFlagsReshapeOnFirstTick(helper);
	}

	@GameTest
	public void mod071_cableDoesNotArmToSolarPanelUp(GameTestHelper helper) {
		CablePlacementScenarios.mod071_cableDoesNotArmToSolarPanelUp(helper);
	}

	@GameTest
	public void mod071_cableDoesNotArmToDaylightSolarPanelUp(GameTestHelper helper) {
		CablePlacementScenarios.mod071_cableDoesNotArmToDaylightSolarPanelUp(helper);
	}

	@GameTest
	public void mod071_cableDoesNotArmToMoonlitSolarPanelUp(GameTestHelper helper) {
		CablePlacementScenarios.mod071_cableDoesNotArmToMoonlitSolarPanelUp(helper);
	}

	@GameTest
	public void mod199_inertFacesRejectCableArms(GameTestHelper helper) {
		CableFaceParityScenarios.inertFacesRejectCableArms(helper);
	}
}
