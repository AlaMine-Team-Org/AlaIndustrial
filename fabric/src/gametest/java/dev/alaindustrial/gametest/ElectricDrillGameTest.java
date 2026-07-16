package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Electric Drill (MOD-079, suite TC-DRILL-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/ElectricDrillScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise
 * identical drill logic (charge slot, EU drain, hand-speed fallback, pickaxe tags, enchantability).
 */
public class ElectricDrillGameTest {

	/**
	 * @implements TC-DRILL-001-FUN01 — the drill is accepted by the Battery Box charge slot (both
	 *     filters) and charges there at min(LV ceiling, its intake rate).
	 */
	@GameTest
	public void tcDrill001Fun01_chargeInBatteryBox(GameTestHelper helper) {
		ElectricDrillScenarios.fun01ChargeInBatteryBox(helper);
	}

	/**
	 * @implements TC-DRILL-001-FUN02 — mining a hard block with a charged drill drains exactly one
	 *     block's worth of EU.
	 */
	@GameTest
	public void tcDrill001Fun02_drainOnMineBlock(GameTestHelper helper) {
		ElectricDrillScenarios.fun02DrainOnMineBlock(helper);
	}

	/**
	 * @implements TC-DRILL-001-FUN03 — a drill below the per-block cost mines for free, spending no EU.
	 */
	@GameTest
	public void tcDrill001Fun03_noDrainBelowCost(GameTestHelper helper) {
		ElectricDrillScenarios.fun03NoDrainBelowCost(helper);
	}

	/**
	 * @implements TC-DRILL-001-FUN04 — 8.5 mining speed while charged (a touch above diamond), exactly hand speed (1.0f)
	 *     when flat, and correct-for-drops on stone/obsidian/diamond ore but not ancient debris.
	 */
	@GameTest
	public void tcDrill001Fun04_speedAndDrops(GameTestHelper helper) {
		ElectricDrillScenarios.fun04SpeedAndDrops(helper);
	}

	/**
	 * @implements TC-DRILL-001-FUN05 — instant-break blocks (zero hardness) never cost EU.
	 */
	@GameTest
	public void tcDrill001Fun05_noDrainOnZeroHardness(GameTestHelper helper) {
		ElectricDrillScenarios.fun05NoDrainOnZeroHardness(helper);
	}

	/**
	 * @implements TC-DRILL-001-FUN06 — the drill carries the pickaxe identity tags and is enchantable
	 *     with the mining enchantments a diamond pickaxe accepts.
	 */
	@GameTest
	public void tcDrill001Fun06_tagsAndEnchants(GameTestHelper helper) {
		ElectricDrillScenarios.fun06TagsAndEnchants(helper);
	}

	/**
	 * @implements TC-DRILL-001-FUN07 — right-click with the drill places a torch from the inventory
	 *     (MOD-089), consuming one torch and draining electricDrillTorchEuCost.
	 */
	@GameTest
	public void tcDrill001Fun07_placeTorchFromInventory(GameTestHelper helper) {
		ElectricDrillScenarios.fun07PlaceTorchFromInventory(helper);
	}

	/**
	 * @implements TC-DRILL-001-FUN08 — when both torch kinds are in the inventory, the drill places the
	 *     enriched uranium torch first (priority), leaving the vanilla stack untouched.
	 */
	@GameTest
	public void tcDrill001Fun08_torchPriorityUranium(GameTestHelper helper) {
		ElectricDrillScenarios.fun08TorchPriorityUranium(helper);
	}

	/**
	 * @implements TC-DRILL-001-FUN09 — regression for replaceable-block placement (tall grass): the torch
	 *     lands at the clicked cell via replaceClicked, and inventory + EU must still be consumed.
	 *     Guards against the b1573697 block-compare-against-wrong-pos bug returning.
	 */
	@GameTest
	public void tcDrill001Fun09_placeTorchOnReplaceableBlock(GameTestHelper helper) {
		ElectricDrillScenarios.fun09PlaceTorchOnReplaceableBlock(helper);
	}

	/**
	 * @implements TC-DRILL-001-PER01 — charge survives a stack copy, 0 EU removes the component, and
	 *     writes clamp at capacity.
	 */
	@GameTest
	public void tcDrill001Per01_chargeRoundTrip(GameTestHelper helper) {
		ElectricDrillScenarios.per01ChargeRoundTrip(helper);
	}
}
