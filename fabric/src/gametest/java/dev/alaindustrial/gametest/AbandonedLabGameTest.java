package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the abandoned lab (MOD-513): the seven lab templates reach the game intact, and the lab
 * is injected into exactly the biomes of its tag.
 *
 * <p>The scenario bodies live in {@link AbandonedLabScenarios} ({@code common/src/gametest}); this class
 * is the Fabric wiring only, so the SAME scenarios run on BOTH loaders — NeoForge registers them in
 * {@code NeoForgeGameTests}.
 */
public class AbandonedLabGameTest {

	@GameTest
	public void labTemplatesReachTheGameIntact(GameTestHelper helper) {
		AbandonedLabScenarios.labTemplatesReachTheGameIntact(helper);
	}

	@GameTest
	public void labIsInjectedIntoItsBiomes(GameTestHelper helper) {
		AbandonedLabScenarios.labIsInjectedIntoItsBiomes(helper);
	}
}
