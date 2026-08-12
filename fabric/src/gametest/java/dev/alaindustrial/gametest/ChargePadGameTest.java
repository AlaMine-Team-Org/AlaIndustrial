package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Charging Station (MOD-274, suite TC-PAD-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/ChargePadScenarios} and the SAME bodies run
 * on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders exercise
 * identical distribution logic.
 */
public class ChargePadGameTest {

	/**
	 * @implements TC-PAD-001-FUN01 — one tick of contact moves one tick's budget into a carried item,
	 *     debits the station by exactly that, and lights the indicator.
	 */
	@GameTest
	public void tcPad001Fun01_chargesCarriedItem(GameTestHelper helper) {
		ChargePadScenarios.fun01ChargesCarriedItem(helper);
	}

	/**
	 * @implements TC-PAD-001-FUN02 — worn equipment is charged, not just carried items (the reason to
	 *     stand on the station after a flight).
	 */
	@GameTest
	public void tcPad001Fun02_chargesWornEquipment(GameTestHelper helper) {
		ChargePadScenarios.fun02ChargesWornEquipment(helper);
	}

	/**
	 * @implements TC-PAD-001-FUN03 — the Energy Pack charges despite its no_auto_charge tag: the tag
	 *     stops chargers charging chargers, which a stationary pad is not.
	 */
	@GameTest
	public void tcPad001Fun03_chargesEnergyPackDespiteTag(GameTestHelper helper) {
		ChargePadScenarios.fun03ChargesEnergyPackDespiteTag(helper);
	}

	/**
	 * @implements TC-PAD-001-FUN04 — no item takes more than its own input rate in a tick, even though
	 *     the station's budget is four times that.
	 */
	@GameTest
	public void tcPad001Fun04_respectsPerItemInputRate(GameTestHelper helper) {
		ChargePadScenarios.fun04RespectsPerItemInputRate(helper);
	}

	/**
	 * @implements TC-PAD-001-FUN05 — the per-tick output is a shared total: with demand above it, the
	 *     first items take a full input rate each and the rest wait for the next tick.
	 */
	@GameTest
	public void tcPad001Fun05_budgetIsSharedAndCapped(GameTestHelper helper) {
		ChargePadScenarios.fun05BudgetIsSharedAndCapped(helper);
	}

	/**
	 * @implements TC-PAD-001-FUN12 — a full worn armour set charges on the same tick as carried gear,
	 *     instead of filling two pieces at a time in scan order (MOD-334).
	 */
	@GameTest
	public void tcPad001Fun12_wholeArmourSetChargesTogether(GameTestHelper helper) {
		ChargePadScenarios.fun12WholeArmourSetChargesTogether(helper);
	}

	/** @implements TC-PAD-001-FUN06 — a station with a flat buffer charges nothing and reads EMPTY. */
	@GameTest
	public void tcPad001Fun06_emptyStationReportsEmpty(GameTestHelper helper) {
		ChargePadScenarios.fun06EmptyStationReportsEmpty(helper);
	}

	/** @implements TC-PAD-001-FUN07 — a visitor with full gear costs nothing and reads READY. */
	@GameTest
	public void tcPad001Fun07_fullVisitorReportsReady(GameTestHelper helper) {
		ChargePadScenarios.fun07FullVisitorReportsReady(helper);
	}

	/**
	 * @implements TC-PAD-001-FUN08 — the station releases its indicator and stops drawing once contact
	 *     goes stale (the idle-producer guard, MOD-214).
	 */
	@GameTest
	public void tcPad001Fun08_releasesAfterVisitorLeaves(GameTestHelper helper) {
		ChargePadScenarios.fun08ReleasesAfterVisitorLeaves(helper);
	}

	/** @implements TC-PAD-001-FUN09 — a spectator is charged nothing and drains nothing. */
	@GameTest
	public void tcPad001Fun09_spectatorIsIgnored(GameTestHelper helper) {
		ChargePadScenarios.fun09SpectatorIsIgnored(helper);
	}

	/**
	 * @implements TC-PAD-001-FUN10 — the block's own entityInside hook reaches the block entity, so the
	 *     feature cannot be dead in game while the direct-call cases stay green.
	 */
	@GameTest
	public void tcPad001Fun10_entityInsideHookReachesTheStation(GameTestHelper helper) {
		ChargePadScenarios.fun10EntityInsideHookReachesTheStation(helper);
	}

	/**
	 * @implements TC-PAD-001-FUN11 — a player straddling two stations is served by exactly one; the
	 *     other spends nothing.
	 */
	@GameTest
	public void tcPad001Fun11_onlyTheStationUnderfootServes(GameTestHelper helper) {
		ChargePadScenarios.fun11OnlyTheStationUnderfootServes(helper);
	}

	/** @implements TC-PAD-001-NRG01 — every face accepts energy and no face offers any. */
	@GameTest
	public void tcPad001Nrg01_everyFaceIsIntakeOnly(GameTestHelper helper) {
		ChargePadScenarios.nrg01EveryFaceIsIntakeOnly(helper);
	}

	/** @implements TC-PAD-001-NRG03 — the station draws EU out of an adjacent source into its buffer. */
	@GameTest
	public void tcPad001Nrg03_takesEnergyFromNeighbour(GameTestHelper helper) {
		ChargePadScenarios.nrg03TakesEnergyFromNeighbour(helper);
	}

	/** @implements TC-PAD-001-NRG02 — the banked buffer survives an NBT round trip. */
	@GameTest
	public void tcPad001Nrg02_bufferPersists(GameTestHelper helper) {
		ChargePadScenarios.nrg02BufferPersists(helper);
	}
	/** @implements MOD-406 — batched payouts deliver exactly what per-tick payouts did. */
	@GameTest
	public void mod406BatchedPayoutMatchesPerTickTotal(GameTestHelper helper) {
		ChargePadScenarios.mod406BatchedPayoutMatchesPerTickTotal(helper);
	}

	/** @implements MOD-406 — and the stack is rewritten a handful of times, not every tick. */
	@GameTest
	public void mod406PayoutsAreBatchedNotPerTick(GameTestHelper helper) {
		ChargePadScenarios.mod406PayoutsAreBatchedNotPerTick(helper);
	}
}
