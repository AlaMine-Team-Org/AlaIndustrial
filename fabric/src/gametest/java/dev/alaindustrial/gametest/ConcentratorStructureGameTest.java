package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric entry points for the Mirror Concentrator's assembly scenarios (MOD-603) and for the assembled
 * machine answering as one — screen and energy port from any of its cells (MOD-608).
 *
 * <p>Bodies live in {@link ConcentratorStructureScenarios} so the NeoForge lane runs the very same
 * code; this class is the wiring the loader needs and nothing else.
 */
public class ConcentratorStructureGameTest {

	/** @implements MOD-603 — seven sections assemble the machine, in the box the player filled. */
	@GameTest(maxTicks = 100)
	public void mod603_assemblesInTheBoxThatWasFilled(GameTestHelper helper) {
		ConcentratorStructureScenarios.assemblesInTheBoxThatWasFilled(helper);
	}

	/** @implements MOD-603 — six sections out of seven leave the machine un-assembled. */
	@GameTest(maxTicks = 40)
	public void mod603_refusesToAssembleWithACellMissing(GameTestHelper helper) {
		ConcentratorStructureScenarios.refusesToAssembleWithACellMissing(helper);
	}

	/** @implements MOD-603 — breaking any one of the eight cells takes the whole machine apart. */
	@GameTest(maxTicks = 120)
	public void mod603_breakingAnyCellDisassemblesAll(GameTestHelper helper) {
		ConcentratorStructureScenarios.breakingAnyCellDisassemblesAll(helper);
	}

	/** @implements MOD-608 — the bottom tier lends the core's port and draws the cable arm; the top tier does not. */
	@GameTest(maxTicks = 40)
	public void mod608_bottomTierLendsTheCorePort(GameTestHelper helper) {
		ConcentratorStructureScenarios.bottomTierLendsTheCorePort(helper);
	}

	/** @implements MOD-608 — the core's screen opens from any cell of the assembled machine. */
	@GameTest(maxTicks = 40)
	public void mod608_anyCellOpensTheCoreScreen(GameTestHelper helper) {
		ConcentratorStructureScenarios.anyCellOpensTheCoreScreen(helper);
	}

	/** @implements MOD-608 — energy leaves the machine through a cable that touches only a section. */
	@GameTest(maxTicks = 40)
	public void mod608_sectionOnlyCableCarriesTheCoreEnergy(GameTestHelper helper) {
		ConcentratorStructureScenarios.sectionOnlyCableCarriesTheCoreEnergy(helper);
	}

	/** @implements MOD-608 — the per-source packet cap holds per machine, not per touching cell. */
	@GameTest(maxTicks = 40)
	public void mod608_oneMachinePushesOnePacket(GameTestHelper helper) {
		ConcentratorStructureScenarios.oneMachinePushesOnePacket(helper);
	}

	/** @implements MOD-608 — a multiblock's supply is counted once, so storage still backs up the machines. */
	@GameTest(maxTicks = 40)
	public void mod608_oneMachineCountsOnceForBackupPower(GameTestHelper helper) {
		ConcentratorStructureScenarios.oneMachineCountsOnceForBackupPower(helper);
	}

	/** @implements MOD-609 — a cable drops to the bottom tier and reaches into its housing. */
	@GameTest(maxTicks = 40)
	public void mod609_cableDropsToTheBottomTier(GameTestHelper helper) {
		ConcentratorStructureScenarios.cableDropsToTheBottomTier(helper);
	}

	/** @implements MOD-603 — a roof over any of the four columns stops the assembled machine. */
	@GameTest(skyAccess = true, maxTicks = 100)
	public void mod603_anyCoveredColumnStopsTheMachine(GameTestHelper helper) {
		ConcentratorStructureScenarios.anyCoveredColumnStopsTheMachine(helper);
	}

	/** @implements MOD-603 — the wings turn about the structure's real middle on every facing. */
	@GameTest(maxTicks = 40)
	public void mod603_structureCentreMatchesItsCells(GameTestHelper helper) {
		ConcentratorStructureScenarios.structureCentreMatchesItsCells(helper);
	}

	/** @implements MOD-603 — the drawing transform lands every cell where its blocks stand. */
	@GameTest(maxTicks = 40)
	public void mod603_canonicalMappingLandsOnTheCells(GameTestHelper helper) {
		ConcentratorStructureScenarios.canonicalMappingLandsOnTheCells(helper);
	}

	/** @implements MOD-603 — building the structure is never a downgrade. */
	@GameTest(maxTicks = 40)
	public void mod603_assembledOutputIsNeverADowngrade(GameTestHelper helper) {
		ConcentratorStructureScenarios.assembledOutputIsNeverADowngrade(helper);
	}
}
