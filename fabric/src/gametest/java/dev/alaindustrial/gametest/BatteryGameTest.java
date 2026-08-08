package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wiring for the Battery scenarios (MOD-083). Bodies live in {@link BatteryScenarios} so the
 * NeoForge world lane runs exactly the same code.
 */
public class BatteryGameTest {

	/**
	 * @implements TC-BATTERY-001-FUN01 — charging a stack costs count × the per-item gain (dupe guard).
	 */
	@GameTest
	public void tcBattery001Fun01_chargingAStackCostsPerItem(GameTestHelper helper) {
		BatteryScenarios.battery01ChargingAStackCostsPerItem(helper);
	}

	/**
	 * @implements TC-BATTERY-001-FUN02 — a full stack of 16 still charges against the 32 EU/t ceiling.
	 */
	@GameTest
	public void tcBattery001Fun02_fullStackStillCharges(GameTestHelper helper) {
		BatteryScenarios.battery02FullStackStillCharges(helper);
	}

	/**
	 * @implements TC-BATTERY-001-FUN03 — the Battery Box discharge slot drains a stack, conserving EU.
	 */
	@GameTest
	public void tcBattery001Fun03_dischargeSlotDrainsStack(GameTestHelper helper) {
		BatteryScenarios.battery03DischargeSlotDrainsStack(helper);
	}

	/**
	 * @implements TC-BATTERY-001-FUN04 — drained batteries stack with fresh ones, equal charges stack,
	 *     unequal ones do not.
	 */
	@GameTest
	public void tcBattery001Fun04_stackingFollowsCharge(GameTestHelper helper) {
		BatteryScenarios.battery04StackingFollowsCharge(helper);
	}

	/**
	 * @implements TC-BATTERY-001-FUN05 — a charge-carrying recipe hands the result the EU of its
	 *     batteries, clamped to the result's buffer.
	 */
	@GameTest
	public void tcBattery001Fun05_craftCarriesChargeIntoResult(GameTestHelper helper) {
		BatteryScenarios.battery05CraftCarriesChargeIntoResult(helper);
	}

	/**
	 * @implements TC-BATTERY-001-FUN06 — the battery is tagged no_auto_charge, so a worn Energy Pack
	 *     does not empty itself into a stack of spares.
	 */
	@GameTest
	public void tcBattery001Fun06_excludedFromAutoCharge(GameTestHelper helper) {
		BatteryScenarios.battery06BatteryIsExcludedFromAutoCharge(helper);
	}

	/**
	 * @implements TC-BATTERY-001-FUN07 — assembling the same recipe twice yields identical stacks, so a
	 *     bulk craft cannot multiply energy.
	 */
	@GameTest
	public void tcBattery001Fun07_repeatedAssemblyDoesNotAccumulate(GameTestHelper helper) {
		BatteryScenarios.battery07RepeatedAssemblyDoesNotAccumulate(helper);
	}

	/**
	 * @implements TC-BATTERY-001-FUN08 — charged warehouse stock reaches the assembled result, and the
	 *     result differs from the blueprint's promise by charge alone.
	 */
	@GameTest
	public void tcBattery001Fun08_assemblerCarriesChargeThrough(GameTestHelper helper) {
		BatteryScenarios.battery08AssemblerCarriesChargeThrough(helper);
	}

	/**
	 * @implements TC-BATTERY-001-FUN09 — upgrading a charged Electric Drill to the diamond tip carries
	 *     its EU into the upgrade instead of burning it (MOD-373).
	 */
	@GameTest
	public void tcBattery001Fun09_drillUpgradeCarriesChargeIntoResult(GameTestHelper helper) {
		BatteryScenarios.battery09DrillUpgradeCarriesChargeIntoResult(helper);
	}
}
