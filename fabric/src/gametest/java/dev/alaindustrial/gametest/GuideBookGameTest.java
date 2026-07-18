package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Guide Book auto-give (MOD-067, suite TC-GUIDE-001). Thin Fabric
 * wrappers over the loader-neutral bodies in {@code common/.../gametest/GuideBookGiverScenarios};
 * the SAME bodies run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}), so
 * both loaders exercise identical give-once ledger logic.
 */
public class GuideBookGameTest {

	/**
	 * @implements TC-GUIDE-001-FUN01 — first join gives exactly one book; a repeat call is a no-op
	 *     (the {@code SavedData} ledger prevents duplicates on relog/death).
	 */
	@GameTest
	public void tcGuide001Fun01_giveOnce(GameTestHelper helper) {
		GuideBookGiverScenarios.giveOnce(helper);
	}
}
