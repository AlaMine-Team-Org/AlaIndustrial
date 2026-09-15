package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the teleporter station registry (MOD-628).
 *
 * <p>The scenario bodies live in {@link TeleporterRegistryScenarios} ({@code common/src/gametest}); this class is
 * the Fabric wiring only, so the SAME scenarios run on BOTH loaders — NeoForge registers them in
 * {@code NeoForgeGameTests}.
 */
public class TeleporterRegistryGameTest {

	@GameTest
	public void tcTele006Fun01_placedStationIsRecorded(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Fun01_placedStationIsRecorded(helper);
	}

	@GameTest
	public void tcTele006Fun02_capsuleIsRecorded(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Fun02_capsuleIsRecorded(helper);
	}

	@GameTest
	public void tcTele006Fun03_ownerPrivacyAndChipAreRecorded(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Fun03_ownerPrivacyAndChipAreRecorded(helper);
	}

	@GameTest
	public void tcTele006Fun04_enteringTheWorldRecordsItself(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Fun04_enteringTheWorldRecordsItself(helper);
	}

	/** Two 101-tick waits: the default budget is far too short. */
	@GameTest(maxTicks = 260)
	public void tcTele006Nrg01_chargingIsThrottled(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Nrg01_chargingIsThrottled(helper);
	}

	@GameTest
	public void tcTele006Nrg02_jumpSpendIsRecorded(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Nrg02_jumpSpendIsRecorded(helper);
	}

	@GameTest
	public void tcTele006Sec01_foreignPrivateStationIsHidden(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Sec01_foreignPrivateStationIsHidden(helper);
	}

	@GameTest
	public void tcTele006Fun05_snapshotLoadsNoChunk(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Fun05_snapshotLoadsNoChunk(helper);
	}

	@GameTest
	public void tcTele006Brk01_removedStationIsForgotten(GameTestHelper helper) {
		TeleporterRegistryScenarios.tcTele006Brk01_removedStationIsForgotten(helper);
	}
}
