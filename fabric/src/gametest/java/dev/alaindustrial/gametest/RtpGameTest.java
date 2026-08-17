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
	public void tcTele004Fun01_chipFitsOnceAndIsConsumed(GameTestHelper helper) {
		RtpScenarios.tcTele004Fun01_chipFitsOnceAndIsConsumed(helper);
	}

	@GameTest
	public void tcTele004Neg01_moduleIsWhatOpensTheGate(GameTestHelper helper) {
		RtpScenarios.tcTele004Neg01_moduleIsWhatOpensTheGate(helper);
	}

	@GameTest
	public void tcTele004Nrg01_refusedJumpCostsNothing(GameTestHelper helper) {
		RtpScenarios.tcTele004Nrg01_refusedJumpCostsNothing(helper);
	}

	@GameTest
	public void tcTele004Nrg02_chargesExactlyTheFlatPrice(GameTestHelper helper) {
		RtpScenarios.tcTele004Nrg02_chargesExactlyTheFlatPrice(helper);
	}

	@GameTest
	public void tcTele004Sta01_moduleSurvivesAReload(GameTestHelper helper) {
		RtpScenarios.tcTele004Sta01_moduleSurvivesAReload(helper);
	}

	@GameTest
	public void tcTele004Brk01_dropCarriesTheModule(GameTestHelper helper) {
		RtpScenarios.tcTele004Brk01_dropCarriesTheModule(helper);
	}
}
