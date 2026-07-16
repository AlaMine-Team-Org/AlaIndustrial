package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Energy Pack (MOD-065, suite TC-PACK-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/EnergyPackScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise
 * identical transfer logic (worn-pack charging, the offhand pass, the anti-loop filter, the Battery
 * Box charge slot, component persistence).
 */
public class EnergyPackGameTest {

	/**
	 * @implements TC-PACK-001-FUN01 — a worn pack tops up a carried pouch by one output batch per
	 *     step (energyPackOutputRate × 20 EU), paying exactly what it sends.
	 */
	@GameTest
	public void tcPack001Fun01_wornPackChargesPouch(GameTestHelper helper) {
		EnergyPackScenarios.fun01WornPackChargesPouch(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN02 — the offhand is not part of the main inventory list in 26.2 and
	 *     is served by its own pass.
	 */
	@GameTest
	public void tcPack001Fun02_chargesOffhand(GameTestHelper helper) {
		EnergyPackScenarios.fun02ChargesOffhand(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN03 — the batch is split across consumers in slot order: the first
	 *     pouch fills, the leftover budget flows on to the next.
	 */
	@GameTest
	public void tcPack001Fun03_budgetSplitAcrossConsumers(GameTestHelper helper) {
		EnergyPackScenarios.fun03BudgetSplitAcrossConsumers(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN04 — the pack charges in the Battery Box slot at
	 *     min(LV ceiling, its own intake rate).
	 */
	@GameTest
	public void tcPack001Fun04_chargeInBatteryBox(GameTestHelper helper) {
		EnergyPackScenarios.fun04ChargeInBatteryBox(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN05 — a pack never charges another pack (anti-loop filter).
	 */
	@GameTest
	public void tcPack001Fun05_packDoesNotChargePack(GameTestHelper helper) {
		EnergyPackScenarios.fun05PackDoesNotChargePack(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN06 — the worn asset follows the charge: lit while charged, drained
	 *     (default) at 0 EU.
	 */
	@GameTest
	public void tcPack001Fun06_wornAssetFollowsCharge(GameTestHelper helper) {
		EnergyPackScenarios.fun06WornAssetFollowsCharge(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN07 — the real tick path: a worn pack feeds the pouch exactly one
	 *     batch per second through inventoryTick, and a pack that is not worn transfers nothing.
	 */
	@GameTest
	public void tcPack001Fun07_inventoryTickDrivesTransfer(GameTestHelper helper) {
		EnergyPackScenarios.fun07InventoryTickDrivesTransfer(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN08 — a pack charged straight through the component (/give, loot)
	 *     corrects its worn look on the next tick.
	 */
	@GameTest
	public void tcPack001Fun08_chargedByComponentFixesItsLook(GameTestHelper helper) {
		EnergyPackScenarios.fun08ChargedByComponentFixesItsLook(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN09 — creative and spectator keep the pack's charge (MOD-081): the
	 *     consumers are still fed, the pack pays nothing, survival still pays.
	 */
	@GameTest
	public void tcPack001Fun09_creativeKeepsCharge(GameTestHelper helper) {
		EnergyPackScenarios.fun09CreativeKeepsCharge(helper);
	}

	/**
	 * @implements TC-PACK-001-FUN10 — the stack on the cursor and the inventory's 2×2 crafting grid
	 *     are charged as well (MOD-082).
	 */
	@GameTest
	public void tcPack001Fun10_chargesCursorAndCraftGrid(GameTestHelper helper) {
		EnergyPackScenarios.fun10ChargesCursorAndCraftGrid(helper);
	}

	/**
	 * @implements TC-PACK-001-NEG05 — the slots of an open container (a chest) are NOT charged: the
	 *     pack reaches the cursor and its own crafting grid, nothing else (MOD-082).
	 */
	@GameTest
	public void tcPack001Neg05_doesNotChargeOpenContainer(GameTestHelper helper) {
		EnergyPackScenarios.neg05DoesNotChargeOpenContainer(helper);
	}

	/**
	 * @implements TC-PACK-001-NEG04 — a pack in the offhand is not charged either (the anti-loop
	 *     filter covers the offhand pass too).
	 */
	@GameTest
	public void tcPack001Neg04_packInOffhandNotCharged(GameTestHelper helper) {
		EnergyPackScenarios.neg04PackInOffhandNotCharged(helper);
	}

	/**
	 * @implements TC-PACK-001-NEG01 — an empty pack, a full pouch or a plain item all result in no
	 *     transfer at all.
	 */
	@GameTest
	public void tcPack001Neg01_noTransferWhenNothingToDo(GameTestHelper helper) {
		EnergyPackScenarios.neg01NoTransferWhenNothingToDo(helper);
	}

	/**
	 * @implements TC-PACK-001-NEG02 — the pack hands over exactly its remaining EU and floors at 0.
	 */
	@GameTest
	public void tcPack001Neg02_packFloorsAtZero(GameTestHelper helper) {
		EnergyPackScenarios.neg02PackFloorsAtZero(helper);
	}

	/**
	 * @implements TC-PACK-001-NEG03 — the charge slot filter accepts every powered item and refuses
	 *     items without a buffer.
	 */
	@GameTest
	public void tcPack001Neg03_menuSlotAcceptsPack(GameTestHelper helper) {
		EnergyPackScenarios.neg03MenuSlotAcceptsPack(helper);
	}

	/**
	 * @implements TC-PACK-001-PER01 — charge survives a stack copy, 0 EU removes the component, and
	 *     writes clamp at capacity.
	 */
	@GameTest
	public void tcPack001Per01_chargeRoundTrip(GameTestHelper helper) {
		EnergyPackScenarios.per01ChargeRoundTrip(helper);
	}
}
