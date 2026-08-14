package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Electric Hoe (suite TC-HOE-001) — the diamond-tipped upgrade (MOD-378),
 * the flat-hoe click paths (MOD-389) and the base tool's EU contract including paid tilling (MOD-364).
 * Thin Fabric wrappers: the bodies are loader-neutral in
 * {@code common/.../gametest/ElectricHoeScenarios} and the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise identical logic.
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

	/**
	 * @implements TC-HOE-001-FUN06 — the hoe is accepted by both Battery Box charge-slot filters and
	 *     charges there at min(LV ceiling, its own intake rate) (MOD-364).
	 */
	@GameTest
	public void tcHoe001Fun06_chargeInBatteryBox(GameTestHelper helper) {
		ElectricHoeScenarios.fun06ChargeInBatteryBox(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN07 — breaking one hay block with a charged hoe drains exactly
	 *     electricHoeEuPerBlock (MOD-364).
	 */
	@GameTest
	public void tcHoe001Fun07_drainOnMineBlock(GameTestHelper helper) {
		ElectricHoeScenarios.fun07DrainOnMineBlock(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN08 — one EU below the per-block cost the hoe breaks for free and at
	 *     exactly hand speed 1.0f on its own domain block (MOD-364).
	 */
	@GameTest
	public void tcHoe001Fun08_noDrainBelowCost(GameTestHelper helper) {
		ElectricHoeScenarios.fun08NoDrainBelowCost(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN09 — a zero-hardness block costs nothing, while a moss block (0.1) costs
	 *     the full per-block drain (MOD-364).
	 */
	@GameTest
	public void tcHoe001Fun09_zeroHardnessFreeMossCosts(GameTestHelper helper) {
		ElectricHoeScenarios.fun09ZeroHardnessFreeMossCosts(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN10 — 9.0 on hoe blocks while charged, exactly 1.0f one EU below the cost,
	 *     drops kept either way and refused on a foreign block (MOD-364).
	 */
	@GameTest
	public void tcHoe001Fun10_speedAndDrops(GameTestHelper helper) {
		ElectricHoeScenarios.fun10SpeedAndDrops(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN11 — a successful till drains exactly electricHoeTillEuCost; the only path
	 *     where the hoe spends EU in normal play, and it was covered by nothing (MOD-364).
	 */
	@GameTest
	public void tcHoe001Fun11_tillDrainsExactlyTillCost(GameTestHelper helper) {
		ElectricHoeScenarios.fun11TillDrainsExactlyTillCost(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN12 — one EU below the till cost the hoe refuses with CONSUME, leaves the
	 *     plot dirt and does not touch the buffer; the boundary FUN05's zero-charge case cannot see
	 *     (MOD-364).
	 */
	@GameTest
	public void tcHoe001Fun12_tillRefusedJustBelowCost(GameTestHelper helper) {
		ElectricHoeScenarios.fun12TillRefusedJustBelowCost(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN13 — a CHARGED hoe clicking a block no hoe can till returns PASS and keeps
	 *     every EU; the charged twin of NEG01, whose flat hoe makes its charge check a fixture assertion
	 *     rather than a statement about spending (MOD-364).
	 */
	@GameTest
	public void tcHoe001Fun13_chargedHoeOnNonTillableKeepsBuffer(GameTestHelper helper) {
		ElectricHoeScenarios.fun13ChargedHoeOnNonTillableKeepsBuffer(helper);
	}

	/**
	 * @implements TC-HOE-001-PER01 — charge survives a stack copy, 0 EU removes the component, and writes
	 *     clamp at capacity (MOD-364).
	 */
	@GameTest
	public void tcHoe001Per01_chargeRoundTrip(GameTestHelper helper) {
		ElectricHoeScenarios.per01ChargeRoundTrip(helper);
	}
}
