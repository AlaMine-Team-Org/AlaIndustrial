package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration for the loader-neutral MOD-662 steam pipe scenarios. */
public final class SteamPipeGameTest {

	/** MOD-662: a steam pipe and a fluid pipe laid side by side never join. */
	@GameTest
	public void mod662FamiliesNeverJoin(GameTestHelper helper) {
		SteamPipeScenarios.familiesNeverJoin(helper);
	}

	/** MOD-662: a fluid pipe refuses steam; a steam pipe refuses everything but steam. */
	@GameTest
	public void mod662EachFamilyRefusesTheOthersFluid(GameTestHelper helper) {
		SteamPipeScenarios.eachFamilyRefusesTheOthersFluid(helper);
	}

	/** MOD-662: a fluid pipe laid today on a column's top neither connects nor migrates. */
	@GameTest
	public void mod662NewFluidPipeOnAColumnTopStaysDisconnected(GameTestHelper helper) {
		SteamPipeScenarios.aNewFluidPipeOnAColumnTopStaysDisconnected(helper);
	}

	/** MOD-662 migration, case 1: an old pipe holding steam becomes a steam pipe, same segment. */
	@GameTest
	public void mod662MigrationTurnsASteamFilledPipe(GameTestHelper helper) {
		SteamPipeScenarios.migrationTurnsASteamFilledPipeIntoASteamPipe(helper);
	}

	/** MOD-662 migration, case 2: an empty old pipe on a column's top becomes a steam pipe. */
	@GameTest
	public void mod662MigrationTurnsAnEmptyPipeOnAColumnTop(GameTestHelper helper) {
		SteamPipeScenarios.migrationTurnsAnEmptyPipeOnAColumnTop(helper);
	}

	/** MOD-662 migration, case 3: the wave carries a whole empty line column → inlet → nozzle. */
	@GameTest
	public void mod662MigrationWaveConvertsAWholeEmptyLine(GameTestHelper helper) {
		SteamPipeScenarios.migrationWaveConvertsAWholeEmptyLine(helper);
	}

	/** MOD-662 migration, case 4: water lines stay fluid pipes. */
	@GameTest
	public void mod662MigrationLeavesWaterLinesAlone(GameTestHelper helper) {
		SteamPipeScenarios.migrationLeavesWaterLinesAlone(helper);
	}

	/** MOD-662 migration, case 5: a reinforced fluid pipe becomes a reinforced steam pipe. */
	@GameTest
	public void mod662MigrationKeepsTheReinforcedGrade(GameTestHelper helper) {
		SteamPipeScenarios.migrationKeepsTheReinforcedGrade(helper);
	}

	/** MOD-662 migration, case 6: a pipe on a column's top and against a column's side stays. */
	@GameTest
	public void mod662MigrationLeavesAnAmbiguousPipe(GameTestHelper helper) {
		SteamPipeScenarios.migrationLeavesAnAmbiguousPipe(helper);
	}
}
