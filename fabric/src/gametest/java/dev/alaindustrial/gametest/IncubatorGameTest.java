package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the incubator (MOD-118). Thin Fabric wrappers: the bodies are
 * loader-neutral in {@code common/.../gametest/IncubatorScenarios} and the SAME bodies run on the
 * NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders exercise the
 * multiblock gate, the chip-driven mode, the uranium charge and the outcome accounting.
 */
public class IncubatorGameTest {

	/** @implements TC-INCU-001-FUN01 — glass placed on the base becomes the dome and forms the structure. */
	@GameTest
	public void fun01_glassOnTopFormsTheDome(GameTestHelper helper) {
		IncubatorScenarios.fun01GlassOnTopFormsTheDome(helper);
	}

	/** @implements TC-INCU-001-FUN02 — removing the dome unforms the machine and returns that exact glass. */
	@GameTest
	public void fun02_removingTheDomeReturnsTheOriginalGlass(GameTestHelper helper) {
		IncubatorScenarios.fun02RemovingTheDomeReturnsTheOriginalGlass(helper);
	}

	/** @implements TC-INCU-001-FUN03 — a complete rig transforms lapis into redstone. */
	@GameTest
	public void fun03_transformYieldsTheOtherMineral(GameTestHelper helper) {
		IncubatorScenarios.fun03TransformYieldsTheOtherMineral(helper);
	}

	/** @implements TC-INCU-001-FUN04 — one ingot is a charge of several attempts, then becomes ash. */
	@GameTest
	public void fun04_oneIngotPowersSeveralAttempts(GameTestHelper helper) {
		IncubatorScenarios.fun04OneIngotPowersSeveralAttempts(helper);
	}

	/** @implements TC-INCU-001-FUN05 — a non-player break of the dome still hands the glass back. */
	@GameTest
	public void fun05_nonPlayerBreakStillReturnsTheGlass(GameTestHelper helper) {
		IncubatorScenarios.fun05NonPlayerBreakStillReturnsTheGlass(helper);
	}

	/** @implements TC-INCU-001-FUN06 — breaking the base leaves the coloured glass, not plain glass. */
	@GameTest
	public void fun06_breakingTheBaseKeepsTheColouredGlass(GameTestHelper helper) {
		IncubatorScenarios.fun06BreakingTheBaseKeepsTheColouredGlass(helper);
	}

	/** @implements TC-INCU-001-FUN07 — automation takes the result and the ash, never the chip or fuel. */
	@GameTest
	public void fun07_automationTakesOnlyTheProducts(GameTestHelper helper) {
		IncubatorScenarios.fun07AutomationTakesOnlyTheProducts(helper);
	}

	/** @implements TC-INCU-001-FUN08 — right-clicking the dome opens the machine below it. */
	@GameTest
	public void fun08_clickingTheDomeOpensTheMenu(GameTestHelper helper) {
		IncubatorScenarios.fun08ClickingTheDomeOpensTheMenu(helper);
	}

	/** @implements TC-INCU-001-FUN09 — taking the result awards the three incubator advancements. */
	@GameTest
	public void fun09_takingTheResultAwardsTheAdvancements(GameTestHelper helper) {
		IncubatorScenarios.fun09TakingTheResultAwardsTheAdvancements(helper);
	}

	/** @implements TC-INCU-001-FUN10 — the ingot commits on insertion once the dome is on. */
	@GameTest
	public void fun10_ingotCommitsOnInsertion(GameTestHelper helper) {
		IncubatorScenarios.fun10IngotCommitsOnInsertion(helper);
	}

	/** @implements TC-INCU-001-NEG01 — without the dome nothing runs, whatever the fuel and power. */
	@GameTest
	public void neg01_noDomeNoOutput(GameTestHelper helper) {
		IncubatorScenarios.neg01NoDomeNoOutput(helper);
	}

	/** @implements TC-INCU-001-NEG02 — with no chip there is no mode, so nothing runs. */
	@GameTest
	public void neg02_noChipNoOutput(GameTestHelper helper) {
		IncubatorScenarios.neg02NoChipNoOutput(helper);
	}

	/** @implements TC-INCU-001-NEG03 — a creative break of the dome drops nothing. */
	@GameTest
	public void neg03_creativeBreakDropsNoGlass(GameTestHelper helper) {
		IncubatorScenarios.neg03CreativeBreakDropsNoGlass(helper);
	}

	/** @implements TC-INCU-001-CON01 — a piston cannot move the dome away from its base. */
	@GameTest
	public void con01_domeBlocksPistons(GameTestHelper helper) {
		IncubatorScenarios.con01DomeBlocksPistons(helper);
	}

	/** @implements TC-INCU-001-REG01 — a graded result is never destroyed by an occupied output slot. */
	@GameTest
	public void reg01_gradedResultNeverDestroysTheOutput(GameTestHelper helper) {
		IncubatorScenarios.reg01GradedResultNeverDestroysTheOutput(helper);
	}

	/** @implements TC-INCU-001-REG02 — slag is never destroyed by an occupied output slot either. */
	@GameTest
	public void reg02_slagNeverDestroysTheOutput(GameTestHelper helper) {
		IncubatorScenarios.reg02SlagNeverDestroysTheOutput(helper);
	}

	/** @implements TC-INCU-001-REG03 — the outcome is rolled at the end of a paid cycle, not up front. */
	@GameTest
	public void reg03_noFreeRerollBeforeTheCycleIsPaid(GameTestHelper helper) {
		IncubatorScenarios.reg03NoFreeRerollBeforeTheCycleIsPaid(helper);
	}

	/** @implements TC-INCU-001-REG04 — a hopper below collects results and ash but never the chip. */
	@GameTest
	public void reg04_hopperBelowCannotStealTheChip(GameTestHelper helper) {
		IncubatorScenarios.reg04HopperBelowCannotStealTheChip(helper);
	}

	/** @implements TC-INCU-001-REG05 — the status line names every reason the machine is idle. */
	@GameTest
	public void reg05_statusExplainsEveryStall(GameTestHelper helper) {
		IncubatorScenarios.reg05StatusExplainsEveryStall(helper);
	}

	/** @implements TC-INCU-001-REG06 — a pipe may return the result into the same machine's input. */
	@GameTest
	public void reg06_pipeReturnsTheResultToTheInput(GameTestHelper helper) {
		IncubatorScenarios.reg06PipeReturnsTheResultToTheInput(helper);
	}

	/** @implements TC-INCU-001-REG07 — the client menu is as wide as the block entity's channel list. */
	@GameTest
	public void reg07_clientMenuCoversEveryDataChannel(GameTestHelper helper) {
		IncubatorScenarios.reg07ClientMenuCoversEveryDataChannel(helper);
	}

	/** @implements TC-INCU-001-REG08 — a pipe on a side face drains the result and ash into a chest. */
	@GameTest
	public void reg08_pipeDrainsProductsIntoAChest(GameTestHelper helper) {
		IncubatorScenarios.reg08PipeDrainsProductsIntoAChest(helper);
	}
}
