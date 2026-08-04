package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Alloy Smelter (MOD-064, suite TC-ALLOY-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/AlloySmelterScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}, {@code alloy_smelter_*}),
 * so both loaders exercise identical logic — the unordered match, the per-component counts, the
 * strictness rule and the anti-deadlock input filter.
 */
public class AlloySmelterGameTest {

	/** @implements TC-ALLOY-001-FUN01 — three copper and one tin become two bronze. */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Fun01_bronzeIsSmelted(GameTestHelper helper) {
		AlloySmelterScenarios.fun01BronzeIsSmelted(helper);
	}

	/**
	 * @implements TC-ALLOY-001-FUN02 — the same mix in reversed slots yields the same bronze. The
	 * negative control for the whole design: a positional matcher fails only here.
	 */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Fun02_orderDoesNotMatter(GameTestHelper helper) {
		AlloySmelterScenarios.fun02OrderDoesNotMatter(helper);
	}

	/** @implements TC-ALLOY-001-FUN03 — each input shrinks by its own count, not one of each. */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Fun03_consumesPerComponentCounts(GameTestHelper helper) {
		AlloySmelterScenarios.fun03ConsumesPerComponentCounts(helper);
	}

	/** @implements TC-ALLOY-001-FUN04 — a two-component alloy runs with the third slot left empty. */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Fun04_electrumFromGoldAndSilver(GameTestHelper helper) {
		AlloySmelterScenarios.fun04ElectrumFromGoldAndSilver(helper);
	}

	/** @implements TC-ALLOY-001-CON01 — junk in the spare slot blocks a two-component recipe. */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Con01_spareSlotBlocksShorterRecipe(GameTestHelper helper) {
		AlloySmelterScenarios.con01SpareSlotBlocksShorterRecipe(helper);
	}

	/** @implements TC-ALLOY-001-CON02 — one copper short: nothing runs, nothing is consumed. */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Con02_shortComponentBlocksWork(GameTestHelper helper) {
		AlloySmelterScenarios.con02ShortComponentBlocksWork(helper);
	}

	/** @implements TC-ALLOY-001-CON03 — an unpowered smelter holds the recipe but produces nothing. */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Con03_noEnergyBlocksWork(GameTestHelper helper) {
		AlloySmelterScenarios.con03NoEnergyBlocksWork(helper);
	}

	/**
	 * @implements TC-ALLOY-001-REG01 — an input slot refuses a metal another input already holds, so a
	 * hopper cannot fill all three with one metal and deadlock the machine.
	 */
	@GameTest
	public void tcAlloy001Reg01_duplicateInputRefused(GameTestHelper helper) {
		AlloySmelterScenarios.reg01DuplicateInputRefused(helper);
	}

	/**
	 * @implements TC-ALLOY-001-REG05 — three DIFFERENT valid metals cannot occupy all three inputs while
	 * no shipped alloy takes three components, so hoppers cannot strand the machine.
	 */
	@GameTest
	public void tcAlloy001Reg05_distinctMetalsCannotFillEverySlot(GameTestHelper helper) {
		AlloySmelterScenarios.reg05DistinctMetalsCannotFillEverySlot(helper);
	}

	/** @implements TC-ALLOY-001-REG06 — the speed multiplier is energy-neutral: the op still costs 1200 EU. */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Reg06_speedMultiplierKeepsOperationCost(GameTestHelper helper) {
		AlloySmelterScenarios.reg06SpeedMultiplierKeepsOperationCost(helper);
	}

	/** @implements TC-ALLOY-001-REG02 — an item no alloy recipe uses is refused outright. */
	@GameTest
	public void tcAlloy001Reg02_nonComponentRefused(GameTestHelper helper) {
		AlloySmelterScenarios.reg02NonComponentRefused(helper);
	}

	/** @implements TC-ALLOY-001-REG03 — the output slot never accepts a manual insert. */
	@GameTest
	public void tcAlloy001Reg03_outputSlotRefusesInsert(GameTestHelper helper) {
		AlloySmelterScenarios.reg03OutputSlotRefusesInsert(helper);
	}

	/**
	 * @implements TC-ALLOY-001-REG04 — swapping a component mid-melt restarts the operation. The shared
	 * base only watches slot 0, so this guards the two slots it does not.
	 */
	@GameTest(maxTicks = 400)
	public void tcAlloy001Reg04_inputSwapResetsProgress(GameTestHelper helper) {
		AlloySmelterScenarios.reg04InputSwapResetsProgress(helper);
	}
}
