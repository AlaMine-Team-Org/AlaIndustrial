package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Electric Saber (MOD-149, suite TC-SABER-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/ElectricSaberScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise
 * identical saber logic (charge slot, EU per hit, on/off switch, charge-driven attributes, tags).
 */
public class ElectricSaberGameTest {

	/**
	 * @implements TC-SABER-001-FUN01 — the saber is accepted by the Battery Box charge slot (both
	 *     filters) and charges there at min(LV ceiling, its intake rate).
	 */
	@GameTest
	public void tcSaber001Fun01_chargeInBatteryBox(GameTestHelper helper) {
		ElectricSaberScenarios.fun01ChargeInBatteryBox(helper);
	}

	/**
	 * @implements TC-SABER-001-FUN02 — a landed hit with a live saber drains exactly one hit's worth of EU.
	 */
	@GameTest
	public void tcSaber001Fun02_drainOnHit(GameTestHelper helper) {
		ElectricSaberScenarios.fun02DrainOnHit(helper);
	}

	/**
	 * @implements TC-SABER-001-FUN03 — below the per-hit cost the saber spends nothing and never goes negative.
	 */
	@GameTest
	public void tcSaber001Fun03_noDrainBelowCost(GameTestHelper helper) {
		ElectricSaberScenarios.fun03NoDrainBelowCost(helper);
	}

	/**
	 * @implements TC-SABER-001-FUN04 — damage and reach modifiers follow the charge across the per-hit
	 *     threshold in both directions.
	 */
	@GameTest
	public void tcSaber001Fun04_attributesFollowCharge(GameTestHelper helper) {
		ElectricSaberScenarios.fun04AttributesFollowCharge(helper);
	}

	/**
	 * @implements TC-SABER-001-FUN05 — a switched-off saber spends no EU and carries no bonuses even on a
	 *     full buffer; switching back on restores them immediately.
	 */
	@GameTest
	public void tcSaber001Fun05_switchedOffSpendsNothing(GameTestHelper helper) {
		ElectricSaberScenarios.fun05SwitchedOffSpendsNothing(helper);
	}

	/**
	 * @implements TC-SABER-001-FUN06 — a creative attacker spends no charge (MOD-081).
	 */
	@GameTest
	public void tcSaber001Fun06_creativeSpendsNothing(GameTestHelper helper) {
		ElectricSaberScenarios.fun06CreativeSpendsNothing(helper);
	}

	/**
	 * @implements TC-SABER-001-FUN07 — sword identity tags, melee enchantments, and the WEAPON contract
	 *     that makes the EU hook run at all.
	 */
	@GameTest
	public void tcSaber001Fun07_tagsAndEnchants(GameTestHelper helper) {
		ElectricSaberScenarios.fun07TagsAndEnchants(helper);
	}

	/**
	 * @implements TC-SABER-001-FUN08 — the electric discharge lands on a live hit and on nothing else.
	 */
	@GameTest
	public void tcSaber001Fun08_shockOnlyWhenLive(GameTestHelper helper) {
		ElectricSaberScenarios.fun08ShockOnlyWhenLive(helper);
	}
}
