package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Electric Chainsaw (suite TC-CHAINSAW-001) — the diamond-tipped upgrade
 * (MOD-374) and the base tool's EU contract (MOD-364). Thin Fabric wrappers: the bodies are
 * loader-neutral in {@code common/.../gametest/ElectricChainsawScenarios} and the SAME bodies run on the
 * NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise identical
 * logic.
 */
public class ElectricChainsawGameTest {

	/**
	 * @implements TC-CHAINSAW-001-FUN01 — the diamond-tipped upgrade (MOD-374) cuts at 10.5 on logs and
	 *     on leaves, strictly faster than the base chainsaw, keeps the axe tier and still drops to hand
	 *     speed when flat.
	 */
	@GameTest
	public void tcChainsaw001Fun01_diamondTipSpeedAndTier(GameTestHelper helper) {
		ElectricChainsawScenarios.fun01DiamondTipSpeedAndTier(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN02 — sneak + right-click toggles the upgrade's Silk Touch mode, and
	 *     the mode changes the real leaf loot-table drop both ways (leaf block ↔ no leaf block); a plain
	 *     click is inert.
	 */
	@GameTest
	public void tcChainsaw001Fun02_diamondTipSilkToggleOnLeaves(GameTestHelper helper) {
		ElectricChainsawScenarios.fun02DiamondTipSilkToggleOnLeaves(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN03 — the BASE chainsaw has no Silk Touch mode: sneak-clicking never
	 *     enchants it and its leaf drops never include the leaf block.
	 */
	@GameTest
	public void tcChainsaw001Fun03_baseChainsawHasNoSilkMode(GameTestHelper helper) {
		ElectricChainsawScenarios.fun03BaseChainsawHasNoSilkMode(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN04 — the base chainsaw is accepted by both Battery Box charge-slot
	 *     filters and charges there at min(LV ceiling, its own intake rate) (MOD-364).
	 */
	@GameTest
	public void tcChainsaw001Fun04_chargeInBatteryBox(GameTestHelper helper) {
		ElectricChainsawScenarios.fun04ChargeInBatteryBox(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN05 — cutting one oak log with a charged base chainsaw drains exactly
	 *     electricChainsawEuPerBlock (MOD-364).
	 */
	@GameTest
	public void tcChainsaw001Fun05_drainOnMineBlock(GameTestHelper helper) {
		ElectricChainsawScenarios.fun05DrainOnMineBlock(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN06 — one EU below the per-block cost the chainsaw cuts for free and
	 *     at exactly hand speed 1.0f on its own domain block (MOD-364).
	 */
	@GameTest
	public void tcChainsaw001Fun06_noDrainBelowCost(GameTestHelper helper) {
		ElectricChainsawScenarios.fun06NoDrainBelowCost(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN07 — a zero-hardness block costs nothing, while oak leaves (0.2) cost
	 *     the full per-block drain, which is the claim the item's javadoc makes and had no test behind it
	 *     (MOD-364).
	 */
	@GameTest
	public void tcChainsaw001Fun07_zeroHardnessFreeLeavesCost(GameTestHelper helper) {
		ElectricChainsawScenarios.fun07ZeroHardnessFreeLeavesCost(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN08 — 9.0 on logs and on leaves while charged (the third TOOL rule,
	 *     checked on the BASE tool for the first time), exactly 1.0f one EU below the cost, drops kept
	 *     either way and refused on a foreign block (MOD-364).
	 */
	@GameTest
	public void tcChainsaw001Fun08_speedAndDrops(GameTestHelper helper) {
		ElectricChainsawScenarios.fun08SpeedAndDrops(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-PER01 — charge survives a stack copy, 0 EU removes the component, and
	 *     writes clamp at capacity (MOD-364).
	 */
	@GameTest
	public void tcChainsaw001Per01_chargeRoundTrip(GameTestHelper helper) {
		ElectricChainsawScenarios.per01ChargeRoundTrip(helper);
	}
}
