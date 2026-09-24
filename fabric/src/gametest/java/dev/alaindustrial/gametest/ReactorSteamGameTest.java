package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric entry points for {@link ReactorSteamScenarios} (MOD-662): the steam puffs over boiling stacks and the
 * nozzle's geyser and hiss. Thin by design — the scenarios live in {@code common} so NeoForge replays the same code.
 */
public class ReactorSteamGameTest {

	@GameTest(maxTicks = 400)
	public void aBoilingRoomPuffsOverEachBoilingStack(GameTestHelper helper) {
		ReactorSteamScenarios.aBoilingRoomPuffsOverEachBoilingStack(helper);
	}

	@GameTest(maxTicks = 200)
	public void aVentingNozzleThrowsAGeyserAndHisses(GameTestHelper helper) {
		ReactorSteamScenarios.aVentingNozzleThrowsAGeyserAndHisses(helper);
	}
}
