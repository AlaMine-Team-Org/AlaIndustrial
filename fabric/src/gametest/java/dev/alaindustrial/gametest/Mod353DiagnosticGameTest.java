package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric wiring for the MOD-353 step-0 discriminating experiment. */
public class Mod353DiagnosticGameTest {

	/** @implements MOD-353 scene 1 — storage-only segment must move a hard zero into the teleporter. */
	@GameTest
	public void mod353Scene1_boxOverCableToTeleporter(GameTestHelper helper) {
		Mod353DiagnosticScenarios.scene1BoxOverCableToTeleporter(helper);
	}

	/** @implements MOD-353 scene 3 — flush box feeds the teleporter through the cable-less path. */
	@GameTest
	public void mod353Scene3_boxFlushAgainstTeleporter(GameTestHelper helper) {
		Mod353DiagnosticScenarios.scene3BoxFlushAgainstTeleporter(helper);
	}

	/** @implements MOD-353 — machines keep priority over the storage feed. */
	@GameTest
	public void mod353_storageFeedYieldsToMachines(GameTestHelper helper) {
		Mod353DiagnosticScenarios.mod353StorageFeedYieldsToMachines(helper);
	}

	/** @implements MOD-353 — the charging station is fixed by the same mechanism. */
	@GameTest
	public void mod353_chargePadChargesFromBatteryBoxOverCable(GameTestHelper helper) {
		Mod353DiagnosticScenarios.mod353ChargePadChargesFromBatteryBoxOverCable(helper);
	}

	/** @implements MOD-314 R3 — the cascade must not drain a box into the teleporter fund. */
	@GameTest
	public void mod314_cascadeIgnoresTeleporterFund(GameTestHelper helper) {
		Mod353DiagnosticScenarios.mod314CascadeIgnoresTeleporterFund(helper);
	}
}
