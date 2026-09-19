package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Electric Bow (MOD-363, suite TC-BOW-001). Thin Fabric wrappers: the bodies
 * are loader-neutral in {@code common/.../gametest/ElectricBowScenarios} and the SAME bodies run on the
 * NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise identical bow
 * logic (charge slot, EU per shot, the live draw and launch, the charged flag, ammunition, tags).
 */
public class ElectricBowGameTest {

	/**
	 * @implements TC-BOW-001-FUN01 — the bow is accepted by the Battery Box charge slot (both filters) and
	 *     charges there at min(LV ceiling, its intake rate).
	 */
	@GameTest
	public void tcBow001Fun01_chargeInBatteryBox(GameTestHelper helper) {
		ElectricBowScenarios.fun01ChargeInBatteryBox(helper);
	}

	/**
	 * @implements TC-BOW-001-FUN02 — a charged bow is fully drawn in the live draw time, launches a crit at
	 *     vanilla speed × the live multiplier, consumes one arrow and pays exactly one shot of EU.
	 */
	@GameTest
	public void tcBow001Fun02_liveShotIsFasterAndPays(GameTestHelper helper) {
		ElectricBowScenarios.fun02LiveShotIsFasterAndPays(helper);
	}

	/**
	 * @implements TC-BOW-001-FUN03 — below one shot's worth even a full draw lets the arrow fall short: the
	 *     arrow is spent, no crit, no EU — a flat bow is not a free vanilla bow.
	 */
	@GameTest
	public void tcBow001Fun03_flatShotFallsShort(GameTestHelper helper) {
		ElectricBowScenarios.fun03FlatShotFallsShort(helper);
	}

	/**
	 * @implements TC-BOW-001-FUN04 — the electric_bow_charged flag follows the charge across the per-shot
	 *     price in both directions, including on the last powered shot.
	 */
	@GameTest
	public void tcBow001Fun04_chargedFlagFollowsCharge(GameTestHelper helper) {
		ElectricBowScenarios.fun04ChargedFlagFollowsCharge(helper);
	}

	/**
	 * @implements TC-BOW-001-FUN05 — a creative archer fires without spending EU.
	 */
	@GameTest
	public void tcBow001Fun05_creativeSpendsNothing(GameTestHelper helper) {
		ElectricBowScenarios.fun05CreativeSpendsNothing(helper);
	}

	/**
	 * @implements TC-BOW-001-FUN06 — with no arrows a charged bow neither draws nor fires nor spends EU.
	 */
	@GameTest
	public void tcBow001Fun06_noArrowNoShot(GameTestHelper helper) {
		ElectricBowScenarios.fun06NoArrowNoShot(helper);
	}

	/**
	 * @implements TC-BOW-001-FUN07 — a release too short to fire costs neither EU nor an arrow.
	 */
	@GameTest
	public void tcBow001Fun07_underDrawnReleaseCostsNothing(GameTestHelper helper) {
		ElectricBowScenarios.fun07UnderDrawnReleaseCostsNothing(helper);
	}

	/**
	 * @implements TC-BOW-001-FUN08 — the bow is in the bow enchantable and convention tags, takes Power,
	 *     Punch, Flame and Infinity, and rejects Unbreaking and Mending.
	 */
	@GameTest
	public void tcBow001Fun08_tagsAndEnchants(GameTestHelper helper) {
		ElectricBowScenarios.fun08TagsAndEnchants(helper);
	}
}
