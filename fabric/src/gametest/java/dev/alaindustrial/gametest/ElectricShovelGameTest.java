package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Electric Shovel's right-click interactions (MOD-379, suite TC-SHOVEL-001).
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
}
