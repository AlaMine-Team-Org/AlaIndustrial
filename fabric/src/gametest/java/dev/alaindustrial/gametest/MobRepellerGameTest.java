package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Mob Repeller (MOD-278, suite TC-REP-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/MobRepellerScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders
 * exercise identical field, evolution and kill-accounting logic.
 */
public class MobRepellerGameTest {

	/**
	 * @implements TC-REP-001-FUN01 — one powered tick sweeps the zone, expels the zombie in it and debits
	 *     exactly one tick of upkeep.
	 */
	@GameTest
	public void tcRep001Fun01ExpelsHostileAndBillsUpkeep(GameTestHelper helper) {
		MobRepellerScenarios.fun01ExpelsHostileAndBillsUpkeep(helper);
	}

	/**
	 * @implements TC-REP-001-FUN02 — a cow in the zone is left alone; the field must not clear a farm.
	 */
	@GameTest
	public void tcRep001Fun02IgnoresPassiveMobs(GameTestHelper helper) {
		MobRepellerScenarios.fun02IgnoresPassiveMobs(helper);
	}

	/**
	 * @implements TC-REP-001-FUN03 — a starved repeller neither bills nor lights up.
	 */
	@GameTest
	public void tcRep001Fun03UnpoweredFieldIsDark(GameTestHelper helper) {
		MobRepellerScenarios.fun03UnpoweredFieldIsDark(helper);
	}

	/**
	 * @implements TC-REP-001-FUN04 — a full Soul Vessel turns the LV block into MV, is consumed whole, and
	 *     the stored charge rides along.
	 */
	@GameTest
	public void tcRep001Fun04FullVesselEvolvesTier(GameTestHelper helper) {
		MobRepellerScenarios.fun04FullVesselEvolvesTier(helper);
	}

	/**
	 * @implements TC-REP-001-NEG01 — a vessel below the threshold is refused by the slot and never evolves.
	 */
	@GameTest
	public void tcRep001Neg01PartialVesselDoesNotEvolve(GameTestHelper helper) {
		MobRepellerScenarios.neg01PartialVesselDoesNotEvolve(helper);
	}

	/**
	 * @implements TC-REP-001-NEG04 — a non-vessel item is still refused by the slot.
	 */
	@GameTest
	public void tcRep001Neg04ForeignItemRejected(GameTestHelper helper) {
		MobRepellerScenarios.neg04ForeignItemRejected(helper);
	}

	/**
	 * @implements TC-REP-001-FUN05 — a hostile mob killed by the player banks exactly one soul.
	 */
	@GameTest
	public void tcRep001Fun05PlayerKillBanksSoul(GameTestHelper helper) {
		MobRepellerScenarios.fun05PlayerKillBanksSoul(helper);
	}

	/**
	 * @implements TC-REP-001-NEG02 — the same kill without a player behind it banks nothing.
	 */
	/**
	 * @implements TC-REP-001-FUN06 — the whole chain end to end through the loader's own death hook.
	 */
	@GameTest
	public void tcRep001Fun06RealKillThroughLoaderHook(GameTestHelper helper) {
		MobRepellerScenarios.fun06RealKillThroughLoaderHook(helper);
	}

	@GameTest
	public void tcRep001Neg02NonPlayerKillBanksNothing(GameTestHelper helper) {
		MobRepellerScenarios.neg02NonPlayerKillBanksNothing(helper);
	}

	/**
	 * @implements TC-REP-001-NEG03 — killing a passive mob banks nothing even when the player dealt it.
	 */
	@GameTest
	public void tcRep001Neg03PassiveKillBanksNothing(GameTestHelper helper) {
		MobRepellerScenarios.neg03PassiveKillBanksNothing(helper);
	}
}
