package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Electric Shovel (suite TC-SHOVEL-001) — its right-click interactions
 * (MOD-379) and the base tool's EU contract (MOD-364).
 * Thin Fabric wrappers: the bodies are loader-neutral in
 * {@code common/.../gametest/ElectricShovelScenarios} and the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise identical logic.
 *
 * <p>The shovel had no gametest on either loader before this suite, which is why the NeoForge defect
 * MOD-379 shipped unnoticed.
 */
public class ElectricShovelGameTest {

	/**
	 * @implements TC-SHOVEL-001-FUN01 — right-clicking grass with the Electric Shovel leaves a dirt path;
	 *     the MOD-379 regression, red on the NeoForge lane before the ability was declared.
	 */
	@GameTest
	public void tcShovel001Fun01_shovelMakesDirtPath(GameTestHelper helper) {
		ElectricShovelScenarios.fun01ShovelMakesDirtPath(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN02 — a vanilla diamond shovel paths the identical fixture, which is
	 *     what makes a red FUN01 an assertion about our item rather than about the rig.
	 */
	@GameTest
	public void tcShovel001Fun02_vanillaShovelPathsTheSameFixture(GameTestHelper helper) {
		ElectricShovelScenarios.fun02VanillaShovelPathsTheSameFixture(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN03 — making a path costs no EU, and a fully discharged shovel still
	 *     makes one.
	 */
	@GameTest
	public void tcShovel001Fun03_pathMakingIsFree(GameTestHelper helper) {
		ElectricShovelScenarios.fun03PathMakingIsFree(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN04 — right-clicking a lit campfire douses it, the second ability the
	 *     same NeoForge gate controls.
	 */
	@GameTest
	public void tcShovel001Fun04_shovelDousesLitCampfire(GameTestHelper helper) {
		ElectricShovelScenarios.fun04ShovelDousesLitCampfire(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN05 — the shovel is accepted by both Battery Box charge-slot filters and
	 *     charges there at min(LV ceiling, its own intake rate) (MOD-364).
	 */
	@GameTest
	public void tcShovel001Fun05_chargeInBatteryBox(GameTestHelper helper) {
		ElectricShovelScenarios.fun05ChargeInBatteryBox(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN06 — digging one dirt block with a charged shovel drains exactly
	 *     electricShovelEuPerBlock (MOD-364).
	 */
	@GameTest
	public void tcShovel001Fun06_drainOnMineBlock(GameTestHelper helper) {
		ElectricShovelScenarios.fun06DrainOnMineBlock(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN07 — one EU below the per-block cost the shovel digs for free and at
	 *     exactly hand speed 1.0f on its own domain block (MOD-364).
	 */
	@GameTest
	public void tcShovel001Fun07_noDrainBelowCost(GameTestHelper helper) {
		ElectricShovelScenarios.fun07NoDrainBelowCost(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN08 — a zero-hardness block costs nothing, while a snow layer (0.1)
	 *     costs the full per-block drain, which is the claim the item's javadoc makes and had no test
	 *     behind it (MOD-364).
	 */
	@GameTest
	public void tcShovel001Fun08_zeroHardnessFreeSnowCosts(GameTestHelper helper) {
		ElectricShovelScenarios.fun08ZeroHardnessFreeSnowCosts(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN09 — 9.0 on shovel blocks while charged, exactly 1.0f one EU below the
	 *     cost, drops kept either way and refused on a foreign block; the shovel's first speed coverage of
	 *     any kind (MOD-364).
	 */
	@GameTest
	public void tcShovel001Fun09_speedAndDrops(GameTestHelper helper) {
		ElectricShovelScenarios.fun09SpeedAndDrops(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-PER01 — charge survives a stack copy, 0 EU removes the component, and
	 *     writes clamp at capacity (MOD-364).
	 */
	@GameTest
	public void tcShovel001Per01_chargeRoundTrip(GameTestHelper helper) {
		ElectricShovelScenarios.per01ChargeRoundTrip(helper);
	}
}
