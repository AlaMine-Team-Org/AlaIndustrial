package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 guard for the shared mock players (MOD-500). Thin Fabric wrappers over the loader-neutral
 * bodies in {@code common/.../gametest/MockPlayerScenarios}; the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}), because the mock is built by vanilla
 * game-test code that both loaders share — a difference here would be a difference in what every
 * other suite is standing on.
 */
public class MockPlayerGameTest {

	@GameTest
	public void inLevelMockIsWiredIntoTheLevel(GameTestHelper helper) {
		MockPlayerScenarios.inLevelMockIsWiredIntoTheLevel(helper);
	}

	@GameTest
	public void survivalMockIsBilledDespiteReportingCreative(GameTestHelper helper) {
		MockPlayerScenarios.survivalMockIsBilledDespiteReportingCreative(helper);
	}
}
