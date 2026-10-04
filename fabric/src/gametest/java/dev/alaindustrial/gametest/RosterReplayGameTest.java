package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric entry point for the MOD-717 roster floor ({@link RosterReplayScenarios}). Hand-wired through the
 * {@code fabric-gametest} entrypoint on purpose: it must keep running when the {@code ScenarioRosterFabric}
 * {@code main} entrypoint it watches is gone. The NeoForge lane wires the same body in
 * {@code NeoForgeGameTests}.
 */
public class RosterReplayGameTest {

	/** @implements MOD-717-RST01 — every roster entry is bound in this lane's TEST_FUNCTION and TEST_INSTANCE */
	@GameTest
	public void rosterIsReplayedOnThisLane(GameTestHelper helper) {
		RosterReplayScenarios.rosterIsReplayedOnThisLane(helper, RosterEntry.Lane.FABRIC);
	}
}
