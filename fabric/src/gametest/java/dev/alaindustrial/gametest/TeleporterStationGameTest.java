package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Teleporter station (MOD-091) — the HV anchor, without the jump
 * (MOD-092) or the GUI (MOD-093). Covers what this task actually ships: HV intake on five faces
 * with an inert front, the oversized buffer, owner/privacy state and how it survives save/load and
 * break → place.
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link TeleporterStationScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation, so
 * the SAME scenario runs on BOTH loaders — NeoForge registers it in {@code NeoForgeGameTests}.
 */
public class TeleporterStationGameTest {

	@GameTest
	public void tcTele001Fun01_acceptsButNeverEmits(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Fun01_acceptsButNeverEmits(helper);
	}

	@GameTest
	public void tcTele001Fun02_bufferMatchesConfig(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Fun02_bufferMatchesConfig(helper);
	}

	@GameTest
	public void tcTele001Fun03_hvIntakeRate(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Fun03_hvIntakeRate(helper);
	}

	@GameTest
	public void tcTele001Nrg03_frontFaceInert(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Nrg03_frontFaceInert(helper);
	}

	@GameTest
	public void tcTele001Per01_stateSurvivesNbt(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Per01_stateSurvivesNbt(helper);
	}

	@GameTest
	public void tcTele001Per02_defaultsToPrivate(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Per02_defaultsToPrivate(helper);
	}

	@GameTest
	public void tcTele001Brk07_dropCarriesEnergyAndPrivacyButNotOwner(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Brk07_dropCarriesEnergyAndPrivacyButNotOwner(helper);
	}

	@GameTest
	public void tcTele001Sec01_privacyGate(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Sec01_privacyGate(helper);
	}

	@GameTest
	public void tcTele001Con01_isAStorageSink(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Con01_isAStorageSink(helper);
	}

	@GameTest
	public void tcTele001Con02_doesNotStarveMachines(GameTestHelper helper) {
		TeleporterStationScenarios.tcTele001Con02_doesNotStarveMachines(helper);
	}

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set ──

	/**
	 * @implements TC-TELE-001-NRG03 — the station's face roles as the energy core sees them (intake on the
	 * working faces, inert front). Body: {@link MachineEnergyScenarios#teleporterFaceRoles}.
	 */
	@GameTest
	public void tcTele001Nrg03b_faceRoles(GameTestHelper helper) {
		MachineEnergyScenarios.teleporterFaceRoles(helper);
	}

	/**
	 * @implements TC-TELE-001-BRK07 — the {@code teleporter_private} data component rides the dropped station
	 * (the loader's data-component registration seam). Body: {@link MachineEnergyScenarios#teleporterDropCarriesPrivacy}.
	 */
	@GameTest
	public void tcTele001Brk07b_dropCarriesPrivacy(GameTestHelper helper) {
		MachineEnergyScenarios.teleporterDropCarriesPrivacy(helper);
	}
}
