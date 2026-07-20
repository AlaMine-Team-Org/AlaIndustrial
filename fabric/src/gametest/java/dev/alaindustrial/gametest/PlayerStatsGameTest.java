package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the MOD-133 player-stats attribution rules. Thin Fabric wrappers over the
 * loader-neutral bodies in {@code common/.../gametest/PlayerStatsScenarios}; the SAME bodies run on
 * the NeoForge world lane ({@code NeoForgeGameTests}), so both loaders pin identical XP-attribution
 * behaviour (completed-work-only, anti-AFK, creative/ownerless exclusions).
 */
public class PlayerStatsGameTest {

	/** @implements completed machine operation credits useful EU (XP) to its online survival owner. */
	@GameTest
	public void xpFromCompletedWork(GameTestHelper helper) {
		PlayerStatsScenarios.xpFromCompletedWork(helper);
	}

	/** @implements aborting an operation before completion earns no XP (anti-AFK / redstone-loop guard). */
	@GameTest
	public void noXpFromAbortedWork(GameTestHelper helper) {
		PlayerStatsScenarios.noXpFromAbortedWork(helper);
	}

	/** @implements a creative owner earns no XP (creative EU is free). */
	@GameTest
	public void noXpForCreativeOwner(GameTestHelper helper) {
		PlayerStatsScenarios.noXpForCreativeOwner(helper);
	}

	/** @implements an ownerless machine (structure / demo stand) credits no player. */
	@GameTest
	public void noStatsForNullOwner(GameTestHelper helper) {
		PlayerStatsScenarios.noStatsForNullOwner(helper);
	}

	/** @implements an offline owner accrues nothing (the gate that also neutralises fake players). */
	@GameTest
	public void noStatsForOfflineOwner(GameTestHelper helper) {
		PlayerStatsScenarios.noStatsForOfflineOwner(helper);
	}

	/** @implements a running generator credits its owner's career production, but no machine EU. */
	@GameTest
	public void generatorProductionAttributedToOwner(GameTestHelper helper) {
		PlayerStatsScenarios.generatorProductionAttributedToOwner(helper);
	}

	/** @implements only EU that fit in the buffer is attributed — never gross production. */
	@GameTest
	public void bufferCapLimitsAttributedProduction(GameTestHelper helper) {
		PlayerStatsScenarios.bufferCapLimitsAttributedProduction(helper);
	}

	/** @implements gamemode transitions neither wipe career EU nor leave XP accrual stuck off. */
	@GameTest(maxTicks = 1400)
	public void modeTransitionsPreserveAndResumeAccrual(GameTestHelper helper) {
		PlayerStatsScenarios.modeTransitionsPreserveAndResumeAccrual(helper);
	}

	/** @implements a player's logout (flushPlayer) saves their pending tail instead of dropping it. */
	@GameTest
	public void flushPlayerSavesTailOnLogout(GameTestHelper helper) {
		PlayerStatsScenarios.flushPlayerSavesTailOnLogout(helper);
	}

	/** @implements activeTicks counts a tick once, not once per generator that fired in it. */
	@GameTest
	public void activeTicksNotScaledByGeneratorCount(GameTestHelper helper) {
		PlayerStatsScenarios.activeTicksNotScaledByGeneratorCount(helper);
	}

	/** @implements MOD-156 active time keeps accruing while the buffer is full and no EU is credited. */
	@GameTest
	public void activeTimeAccruesWithFullBuffer(GameTestHelper helper) {
		PlayerStatsScenarios.activeTimeAccruesWithFullBuffer(helper);
	}
}
