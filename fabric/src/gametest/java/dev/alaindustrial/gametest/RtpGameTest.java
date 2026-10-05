package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the random jump (MOD-116): fitting the chip, the gate it opens, and the rule that a
 * refused jump costs nothing.
 *
 * <p>The scenario bodies live in {@link RtpScenarios} ({@code common/src/gametest}); what stays here
 * is the Fabric wiring only, so the SAME scenario runs on both loaders — NeoForge registers it in
 * {@code NeoForgeGameTests}.
 *
 * <p>An actual 5000-block jump is deliberately absent: see the class javadoc of {@link RtpScenarios}
 * for why this lane cannot express it, and {@code RtpSiteFinderTest} for where the search is covered.
 */
public class RtpGameTest {

	@GameTest
	public void tcTele004Neg02_pressOnBrokenStationStartsNothing(GameTestHelper helper) {
		RtpScenarios.tcTele004Neg02_pressOnBrokenStationStartsNothing(helper);
	}
}
