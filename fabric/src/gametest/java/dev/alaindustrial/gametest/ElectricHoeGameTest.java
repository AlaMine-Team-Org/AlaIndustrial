package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Diamond-Tipped Electric Hoe (MOD-378, suite TC-HOE-001). Thin Fabric
 * wrappers: the bodies are loader-neutral in {@code common/.../gametest/ElectricHoeScenarios} and the
 * SAME bodies run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders
 * exercise identical logic.
 */
public class ElectricHoeGameTest {

	/**
	 * @implements TC-HOE-001-FUN01 — the diamond-tipped upgrade (MOD-378) breaks hoe-mineable blocks at
	 *     10.5, strictly faster than the base hoe, keeps the hoe tier and still drops to hand speed when
	 *     flat.
	 */
	@GameTest
	public void tcHoe001Fun01_diamondTipSpeedAndTier(GameTestHelper helper) {
		ElectricHoeScenarios.fun01DiamondTipSpeedAndTier(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN02 — a plot tilled with the upgrade comes out at full farmland moisture,
	 *     with no water anywhere near it.
	 */
	@GameTest
	public void tcHoe001Fun02_diamondTipTillLeavesPlotWatered(GameTestHelper helper) {
		ElectricHoeScenarios.fun02DiamondTipTillLeavesPlotWatered(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN03 — the BASE hoe has no irrigation: the plot it tills stays dry, which is
	 *     what makes FUN02 a real assertion rather than a restatement of vanilla.
	 */
	@GameTest
	public void tcHoe001Fun03_baseHoeLeavesPlotDry(GameTestHelper helper) {
		ElectricHoeScenarios.fun03BaseHoeLeavesPlotDry(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN04 — a flat upgrade cannot water already-existing farmland, closing the
	 *     free-irrigation hole that gating on {@code consumesAction()} alone would have opened.
	 */
	@GameTest
	public void tcHoe001Fun04_flatUpgradeCannotWaterExistingFarmland(GameTestHelper helper) {
		ElectricHoeScenarios.fun04FlatUpgradeCannotWaterExistingFarmland(helper);
	}

	/**
	 * @implements TC-HOE-001-NEG01 — a flat hoe right-clicking a block no hoe can till returns PASS, so it
	 *     neither shouts "not enough charge" at a player who was not tilling nor swallows the click that
	 *     the off-hand item was meant to get (MOD-389).
	 */
	@GameTest
	public void tcHoe001Neg01_flatHoeOnNonTillableDoesNotSwallowClick(GameTestHelper helper) {
		ElectricHoeScenarios.neg01FlatHoeOnNonTillableDoesNotSwallowClick(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN05 — on a block that IS tillable, a flat hoe still refuses with CONSUME and
	 *     leaves the plot untilled, so the MOD-389 fix cannot degenerate into "always PASS".
	 */
	@GameTest
	public void tcHoe001Fun05_flatHoeOnTillableStillRefuses(GameTestHelper helper) {
		ElectricHoeScenarios.fun05FlatHoeOnTillableStillRefuses(helper);
	}
}
