package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the jump itself (MOD-092): the price, the policy gate, and the one thing that must
 * never happen — EU disappearing without the player moving.
 *
 * <p>The warmup countdown and its cancellation hooks (damage / death / disconnect) are driven by
 * real player events and are covered by the manual client pass; what is tested here is everything
 * that can be decided from the server state alone.
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link TeleporterJumpScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation, so
 * the SAME scenario runs on BOTH loaders — NeoForge registers it in {@code NeoForgeGameTests}.
 */
public class TeleporterJumpGameTest {

	@GameTest
	public void tcTele002Fun01_costFormula(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Fun01_costFormula(helper);
	}

	@GameTest
	public void tcTele002Fun02_weightIgnoresArmour(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Fun02_weightIgnoresArmour(helper);
	}

	@GameTest
	public void tcTele002Sec01_policyGate(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Sec01_policyGate(helper);
	}

	@GameTest
	public void tcTele002Nrg01_failedJumpCostsNothing(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Nrg01_failedJumpCostsNothing(helper);
	}

	@GameTest
	public void tcTele002Fun03_successMovesAndCharges(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Fun03_successMovesAndCharges(helper);
	}

	@GameTest
	public void tcTele002Nrg02_jumpCountsAsSpending(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Nrg02_jumpCountsAsSpending(helper);
	}

	@GameTest
	public void tcTele002Nrg03_refusedJumpBooksNoSpending(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Nrg03_refusedJumpBooksNoSpending(helper);
	}

	@GameTest
	public void tcTele002Sec02_remoteBindsToOwner(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Sec02_remoteBindsToOwner(helper);
	}

	@GameTest
	public void tcTele002Sta01_warmupStateStartsClean(GameTestHelper helper) {
		TeleporterJumpScenarios.tcTele002Sta01_warmupStateStartsClean(helper);
	}
}
