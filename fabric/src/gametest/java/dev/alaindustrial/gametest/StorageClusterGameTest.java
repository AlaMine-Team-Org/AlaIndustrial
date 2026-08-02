package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric lane for the modular-warehouse checks. Bodies are loader-neutral and live in
 * {@link StorageClusterScenarios}; the NeoForge lane registers the same four.
 */
public class StorageClusterGameTest {

	/** @implements TC-STOR-001 — face contact merges, edge/corner contact does not. */
	@GameTest
	public void tcStor01_faceAdjacentMergesDiagonalDoesNot(GameTestHelper helper) {
		StorageClusterScenarios.faceAdjacentMergesDiagonalDoesNot(helper);
	}

	/** @implements TC-STOR-002 — a fifth module adds no slots. */
	@GameTest
	public void tcStor02_fifthModuleAddsNothing(GameTestHelper helper) {
		StorageClusterScenarios.fifthModuleAddsNothing(helper);
	}

	/** @implements TC-STOR-003 — cluster slot N belongs to exactly one module. */
	@GameTest
	public void tcStor03_itemsStayInTheirOwnModule(GameTestHelper helper) {
		StorageClusterScenarios.itemsStayInTheirOwnModule(helper);
	}

	/** @implements TC-STOR-004 — breaking a middle module splits the cluster without touching its neighbours. */
	@GameTest
	public void tcStor04_breakingMiddleModuleSplitsWithoutLoss(GameTestHelper helper) {
		StorageClusterScenarios.breakingMiddleModuleSplitsWithoutLoss(helper);
	}

	/** @implements TC-STOR-005 — the module exposes an item port on both loaders. */
	@GameTest
	public void tcStor05_moduleExposesItemPortOnEveryFace(GameTestHelper helper) {
		StorageClusterScenarios.moduleExposesItemPortOnEveryFace(helper);
	}

	/** @implements TC-STOR-006 — seams appear only while the whole group is one warehouse. */
	@GameTest
	public void tcStor06_seamsMatchClusterMembership(GameTestHelper helper) {
		StorageClusterScenarios.seamsMatchClusterMembership(helper);
	}

	/** @implements TC-STOR-007 — seams follow the wall in both axes and never cross a diagonal. */
	@GameTest
	public void tcStor07_seamsJoinAWallAndNeverADiagonal(GameTestHelper helper) {
		StorageClusterScenarios.seamsJoinAWallAndNeverADiagonal(helper);
	}

	/** @implements TC-STOR-008 — the window shows six rows and scrolls over the rest. */
	@GameTest
	public void tcStor08_windowShowsSixRowsAndScrolls(GameTestHelper helper) {
		StorageClusterScenarios.windowShowsSixRowsAndScrolls(helper);
	}

	/** @implements TC-STOR-009 — shift-click reaches the rows the window is not showing. */
	@GameTest
	public void tcStor09_shiftClickReachesScrolledAwayRows(GameTestHelper helper) {
		StorageClusterScenarios.shiftClickReachesScrolledAwayRows(helper);
	}

	/** @implements TC-STOR-010 — capacity falls to zero as modules are removed and never goes negative. */
	@GameTest
	public void tcStor10_capacityNeverGoesNegative(GameTestHelper helper) {
		StorageClusterScenarios.capacityNeverGoesNegative(helper);
	}

	/** @implements TC-STOR-011 — an unloaded position is skipped, not force-loaded, and costs no items. */
	@GameTest
	public void tcStor11_unloadedPositionIsSkippedNotForceLoaded(GameTestHelper helper) {
		StorageClusterScenarios.unloadedPositionIsSkippedNotForceLoaded(helper);
	}

	/** @implements TC-STOR-012 — pipes fill and drain the warehouse; simulate and execute agree. */
	@GameTest
	public void tcStor12_pipeFillsAndDrainsTheWarehouse(GameTestHelper helper) {
		StorageClusterScenarios.pipeFillsAndDrainsTheWarehouse(helper);
	}

	/** @implements TC-STOR-013 — module contents round-trip through NBT, slot positions included. */
	@GameTest
	public void tcStor13_moduleContentsSurviveReload(GameTestHelper helper) {
		StorageClusterScenarios.moduleContentsSurviveReload(helper);
	}
}
