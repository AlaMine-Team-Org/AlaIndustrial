package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric lane for the Assembler (MOD-275). Bodies are loader-neutral and live in
 * {@link AssemblerScenarios}; the NeoForge lane registers the same set.
 */
public class AssemblerGameTest {

	/** @implements TC-ASM-001 — 8 EU/t × 40 ticks = 320 EU per operation. */
	@GameTest
	public void tcAsm01_balanceNumbers(GameTestHelper helper) {
		AssemblerScenarios.con01BalanceNumbers(helper);
	}

	/** @implements TC-ASM-002 — the happy path, end to end. */
	@GameTest
	public void tcAsm02_oneOperationCostsExactlyOneOperation(GameTestHelper helper) {
		AssemblerScenarios.fun01OneOperationCostsExactlyOneOperation(helper);
	}

	/** @implements TC-ASM-003 — no materials, no EU. */
	@GameTest
	public void tcAsm03_noEuWithoutMaterials(GameTestHelper helper) {
		AssemblerScenarios.neg01NoEuWithoutMaterials(helper);
	}

	/** @implements TC-ASM-004 — a jammed output costs nothing. */
	@GameTest
	public void tcAsm04_noEuWhenOutputFull(GameTestHelper helper) {
		AssemblerScenarios.neg02NoEuWhenOutputFull(helper);
	}

	/** @implements TC-ASM-005 — the whole output area is used before the machine calls itself full. */
	@GameTest
	public void tcAsm05_outputAreaUsesEverySlot(GameTestHelper helper) {
		AssemblerScenarios.fun02OutputAreaUsesEverySlot(helper);
	}

	/** @implements TC-ASM-006 — a stack-dependent remainder returns to its own warehouse slot. */
	@GameTest
	public void tcAsm06_hammerRemainderReturnsToItsOwnSlot(GameTestHelper helper) {
		AssemblerScenarios.fun03HammerRemainderReturnsToItsOwnSlot(helper);
	}

	/** @implements TC-ASM-007 — a self-returning ingredient terminates with the consumable. */
	@GameTest
	public void tcAsm07_selfReturningIngredientTerminates(GameTestHelper helper) {
		AssemblerScenarios.neg03SelfReturningIngredientTerminates(helper);
	}

	/** @implements TC-ASM-008 — a starved blueprint does not block the queue. */
	@GameTest
	public void tcAsm08_queueSkipsStarvedBlueprint(GameTestHelper helper) {
		AssemblerScenarios.fun04QueueSkipsStarvedBlueprint(helper);
	}

	/** @implements TC-ASM-009 — the queue rotates instead of starving its tail. */
	@GameTest
	public void tcAsm09_queueRotatesBetweenBlueprints(GameTestHelper helper) {
		AssemblerScenarios.fun05QueueRotatesBetweenBlueprints(helper);
	}

	/** @implements TC-ASM-010 — a recipe that no longer exists stalls the machine safely. */
	@GameTest
	public void tcAsm10_missingRecipeStopsWithoutCrashing(GameTestHelper helper) {
		AssemblerScenarios.neg04MissingRecipeStopsWithoutCrashing(helper);
	}

	/** @implements TC-ASM-011 — several assemblers on one cluster conserve items. */
	@GameTest
	public void tcAsm11_twoAssemblersShareOneWarehouse(GameTestHelper helper) {
		AssemblerScenarios.fun06TwoAssemblersShareOneWarehouse(helper);
	}

	/** @implements TC-ASM-012 — a recorded blueprint names its product. */
	@GameTest
	public void tcAsm12_blueprintTooltipNamesItsResult(GameTestHelper helper) {
		AssemblerScenarios.gui01BlueprintTooltipNamesItsResult(helper);
	}

	/** @implements TC-ASM-013 — an idle window shows the active blueprint, the player's grid overrides it. */
	@GameTest
	public void tcAsm13_windowShowsTheActiveBlueprint(GameTestHelper helper) {
		AssemblerScenarios.gui02WindowShowsTheActiveBlueprint(helper);
	}

	/** @implements TC-ASM-014 — the active blueprint slot reaches the GUI's data channel. */
	@GameTest
	public void tcAsm14_activeBlueprintSlotIsSynced(GameTestHelper helper) {
		AssemblerScenarios.gui03ActiveBlueprintSlotIsSynced(helper);
	}

	/** @implements TC-ASM-015 — the blueprint's icon switches on the cached-result component. */
	@GameTest
	public void tcAsm15_blueprintIconIsWiredToTheCachedResult(GameTestHelper helper) {
		AssemblerScenarios.gui04BlueprintIconIsWiredToTheCachedResult(helper);
	}

	/** @implements TC-ASM-016 — the craft occupies all nine cells and really loads. */
	@GameTest
	public void tcAsm16_craftFillsNineCells(GameTestHelper helper) {
		AssemblerScenarios.con02CraftFillsNineCells(helper);
	}

	/** @implements TC-ASM-017 — a hidden tab's slots cannot be reached from the server. */
	@GameTest
	public void tcAsm17_hiddenTabSlotsAreUnreachable(GameTestHelper helper) {
		AssemblerScenarios.tab01HiddenTabSlotsAreUnreachable(helper);
	}

	/** @implements TC-ASM-018 — the tab is a view switch: production continues while recording. */
	@GameTest
	public void tcAsm18_recordingDoesNotStopProduction(GameTestHelper helper) {
		AssemblerScenarios.tab02RecordingDoesNotStopProduction(helper);
	}

	/** @implements TC-ASM-019 — Write consumes the Record tab's blank, and only from that tab. */
	@GameTest
	public void tcAsm19_writeBelongsToTheRecordTab(GameTestHelper helper) {
		AssemblerScenarios.tab03WriteBelongsToTheRecordTab(helper);
	}

	// MOD-275 tail — ingredient substitution. Numbered from 020: 017-019 were already taken by the
	// tab split shipped on main, and both lines had independently claimed 017-019 before the merge.

	/** @implements TC-ASM-020 — substitution defaults to off and is honoured when switched on. */
	@GameTest
	public void tcAsm20_substitutionOffByDefault(GameTestHelper helper) {
		AssemblerScenarios.sub01OffByDefaultOnWhenToggled(helper);
	}

	/** @implements TC-ASM-021 — the recorded item is preferred over any stand-in. */
	@GameTest
	public void tcAsm21_substitutionPrefersRecordedItem(GameTestHelper helper) {
		AssemblerScenarios.sub02RecordedItemWins(helper);
	}

	/** @implements TC-ASM-022 — an exact-id ingredient admits no stand-in. */
	@GameTest
	public void tcAsm22_substitutionOnlyThisRecipesIngredient(GameTestHelper helper) {
		AssemblerScenarios.sub03OnlyThisRecipesIngredient(helper);
	}

	/** @implements TC-ASM-023 — substitution is validated by re-assembling the recipe. */
	@GameTest
	public void tcAsm23_substitutionValidatedByReassembly(GameTestHelper helper) {
		AssemblerScenarios.sub04ValidatedByReassembly(helper);
	}

	/** @implements TC-ASM-025 — the substitution toggle is reachable over the menu button channel. */
	@GameTest
	public void tcAsm25_substitutionButtonChannelRoutes(GameTestHelper helper) {
		AssemblerScenarios.sub05ButtonChannelRoutesToSubstitution(helper);
	}

	/** @implements TC-ASM-024 — a full warehouse with four assemblers costs a fraction of a tick. */
	@GameTest
	public void tcAsm24_fullWarehouseTickCost(GameTestHelper helper) {
		AssemblerPerfScenarios.perf01FullWarehouseTickCost(helper);
	}

	/** @implements TC-ASM-026 — breaking the assembler returns its queue and output, and only those. */
	@GameTest
	public void tcAsm26_breakingTheMachineDropsQueueAndOutput(GameTestHelper helper) {
		AssemblerScenarios.brk01BreakingTheMachineDropsQueueAndOutput(helper);
	}

	/** @implements TC-ASM-027 — the assembler's queue, progress and cursor survive a reload. */
	@GameTest
	public void tcAsm27_assemblerStateSurvivesReload(GameTestHelper helper) {
		AssemblerScenarios.per01AssemblerStateSurvivesReload(helper);
	}
}
