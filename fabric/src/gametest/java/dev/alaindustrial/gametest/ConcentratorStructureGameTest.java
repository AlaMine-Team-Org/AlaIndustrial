package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric entry points for the Mirror Concentrator's assembly scenarios (MOD-603).
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

	/** @implements MOD-603 — energy stays on the core; the seven sections are inert. */
	@GameTest(maxTicks = 40)
	public void mod603_onlyTheCoreCarriesEnergy(GameTestHelper helper) {
		ConcentratorStructureScenarios.onlyTheCoreCarriesEnergy(helper);
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
