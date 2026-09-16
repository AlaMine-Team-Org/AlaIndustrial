package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the remote's log (MOD-631). The scenario bodies live in {@link TeleporterLogScenarios}
 * ({@code common/src/gametest}); this is the Fabric wiring only, so the same scenarios run on both loaders — NeoForge
 * registers them in {@code NeoForgeGameTests}.
 */
public class TeleporterLogGameTest {

	@GameTest
	public void tcTele007Fun01_bindingWritesALine(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun01_bindingWritesALine(helper);
	}

	@GameTest
	public void tcTele007Fun02_renameAndDeleteWriteLines(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun02_renameAndDeleteWriteLines(helper);
	}

	@GameTest
	public void tcTele007Fun03_cancellationsWriteGreyLines(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun03_cancellationsWriteGreyLines(helper);
	}

	@GameTest
	public void tcTele007Fun04_jumpAndRefusalLinesCarryTheirNumbers(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun04_jumpAndRefusalLinesCarryTheirNumbers(helper);
	}

	@GameTest
	public void tcTele007Fun05_repeatsMergeAndTheOldestGoes(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun05_repeatsMergeAndTheOldestGoes(helper);
	}

	@GameTest
	public void tcTele007Fun06_readMarkClearsTheBadge(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun06_readMarkClearsTheBadge(helper);
	}

	@GameTest
	public void tcTele007Fun07_refusedPressWritesALine(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun07_refusedPressWritesALine(helper);
	}

	@GameTest
	public void tcTele007Neg01_noRemoteWritesNothing(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Neg01_noRemoteWritesNothing(helper);
	}

	@GameTest
	public void tcTele007Sta01_logTravelsWithTheItem(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Sta01_logTravelsWithTheItem(helper);
	}
}
