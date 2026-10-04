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
	public void tcTele007Fun03_cancellationsWriteGreyLines(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun03_cancellationsWriteGreyLines(helper);
	}

	@GameTest
	public void tcTele007Fun07_refusedPressWritesALine(GameTestHelper helper) {
		TeleporterLogScenarios.tcTele007Fun07_refusedPressWritesALine(helper);
	}
}
