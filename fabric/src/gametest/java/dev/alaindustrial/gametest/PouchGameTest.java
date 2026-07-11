package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Battery Pouch (MOD-052, suite TC-POUCH-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/PouchScenarios} and the SAME bodies are
 * registered on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders
 * exercise identical pouch logic (components, weight math, the EU lock, the Battery Box charge slot).
 */
public class PouchGameTest {

	/**
	 * @implements TC-POUCH-001-FUN01 — a depleted (0 EU) pouch refuses insertion through the click
	 *     path; once charged, the same click stores the cursor stack.
	 */
	@GameTest
	public void tcPouch001Fun01_insertRequiresEnergy(GameTestHelper helper) {
		PouchScenarios.fun01InsertRequiresEnergy(helper);
	}

	/**
	 * @implements TC-POUCH-001-FUN02 — capacity is 128 weight; a stack that does not fit comes back
	 *     as leftover and the pouch reports full.
	 */
	@GameTest
	public void tcPouch001Fun02_capacityLeftover(GameTestHelper helper) {
		PouchScenarios.fun02CapacityLeftover(helper);
	}

	/**
	 * @implements TC-POUCH-001-FUN03 — vanilla-bundle weight classes: 1 (stack-64), 4 (stack-16),
	 *     64 (unstackable); pickaxe + 16 pearls fill 128 exactly.
	 */
	@GameTest
	public void tcPouch001Fun03_weightMath(GameTestHelper helper) {
		PouchScenarios.fun03WeightMath(helper);
	}

	/**
	 * @implements TC-POUCH-001-FUN04 — extraction is LIFO by whole stacks (Q-EXT-1).
	 */
	@GameTest
	public void tcPouch001Fun04_removeLifo(GameTestHelper helper) {
		PouchScenarios.fun04RemoveLifo(helper);
	}

	/**
	 * @implements TC-POUCH-001-FUN05 — one drain step removes exactly lvPouchDrainPerSecond EU while
	 *     items are inside.
	 */
	@GameTest
	public void tcPouch001Fun05_passiveDrain(GameTestHelper helper) {
		PouchScenarios.fun05PassiveDrain(helper);
	}

	/**
	 * @implements TC-POUCH-001-FUN06 — an empty pouch never drains and never rewrites its components.
	 */
	@GameTest
	public void tcPouch001Fun06_noDrainWhenEmpty(GameTestHelper helper) {
		PouchScenarios.fun06NoDrainWhenEmpty(helper);
	}

	/**
	 * @implements TC-POUCH-001-FUN07 — the Battery Box charge slot refills the pouch from the block
	 *     buffer (1000 EU moves box → pouch).
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcPouch001Fun07_chargeInBatteryBox(GameTestHelper helper) {
		PouchScenarios.fun07ChargeInBatteryBox(helper);
	}

	/**
	 * @implements TC-POUCH-001-FUN08 — insertion merges into an existing partial stack before
	 *     opening a new one (auto-merge, design §3.3).
	 */
	@GameTest
	public void tcPouch001Fun08_mergeOnInsert(GameTestHelper helper) {
		PouchScenarios.fun08MergeOnInsert(helper);
	}

	/**
	 * @implements TC-POUCH-001-FUN09 — the bundle-style tooltip image (contents grid + weight bar)
	 *     is exposed exactly when the pouch holds items.
	 */
	@GameTest
	public void tcPouch001Fun09_tooltipImage(GameTestHelper helper) {
		PouchScenarios.fun09TooltipImage(helper);
	}

	/**
	 * @implements TC-POUCH-001-NEG01 — a pouch can never be stored inside a pouch.
	 */
	@GameTest
	public void tcPouch001Neg01_noPouchInPouch(GameTestHelper helper) {
		PouchScenarios.neg01NoPouchInPouch(helper);
	}

	/**
	 * @implements TC-POUCH-001-NEG02 — the charge slot is GUI-only: hoppers can neither push into it
	 *     (canPlaceItemThroughFace) nor pull from it (canTakeItemThroughFace), on every face.
	 */
	@GameTest
	public void tcPouch001Neg02_hopperCutOff(GameTestHelper helper) {
		PouchScenarios.neg02HopperCutOff(helper);
	}

	/**
	 * @implements TC-POUCH-001-NEG03 — drain floors at exactly 0 EU (never negative) and the lock
	 *     then refuses extraction while keeping the contents.
	 */
	@GameTest
	public void tcPouch001Neg03_drainFloorsAndLocks(GameTestHelper helper) {
		PouchScenarios.neg03DrainFloorsAndLocks(helper);
	}

	/**
	 * @implements TC-POUCH-001-NEG04 — the menu slot's mayPlace (client prediction) accepts only
	 *     PouchItem, mirroring the BE-side canPlaceItem.
	 * @covers R-GUI-01
	 */
	@GameTest
	public void tcPouch001Neg04_menuSlotFilter(GameTestHelper helper) {
		PouchScenarios.neg04MenuSlotFilter(helper);
	}

	/**
	 * @implements TC-POUCH-001-PRF01 — the charge slot moves at most the LV ceiling (32 EU) per tick.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcPouch001Prf01_chargeRateCap(GameTestHelper helper) {
		PouchScenarios.prf01ChargeRateCap(helper);
	}

	/**
	 * @implements TC-POUCH-001-PRF02 — the pouch buffer stops exactly at lvPouchBuffer (no
	 *     overcharge) and the box pays exactly what moved.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcPouch001Prf02_noOvercharge(GameTestHelper helper) {
		PouchScenarios.prf02NoOvercharge(helper);
	}

	/**
	 * @implements TC-POUCH-001-PER01 — contents and charge survive a full ItemStack codec
	 *     round-trip: stack types, counts and LIFO order intact.
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcPouch001Per01_contentsRoundTrip(GameTestHelper helper) {
		PouchScenarios.per01ContentsRoundTrip(helper);
	}

	/**
	 * @implements TC-POUCH-001-PER02 — energy component semantics: clamp to capacity, floor at 0,
	 *     and absent-at-zero normalisation.
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcPouch001Per02_energySemantics(GameTestHelper helper) {
		PouchScenarios.per02EnergySemantics(helper);
	}
}
