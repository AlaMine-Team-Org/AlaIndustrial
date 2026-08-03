package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 sanity suite that covers EVERY mod processing recipe JSON in one shot, plus the crafting-recipe
 * resolve guards (MOD-225).
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link RecipeCoverageScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation, so
 * the SAME recipe coverage runs on BOTH loaders (NeoForge registers it in
 * {@code NeoForgeGameTests}). Datapack loading and registry binding are exactly the seam where the
 * two loaders can drift apart.
 */
public class RecipeCoverageGameTest {

	@GameTest
	public void tcRecie01_macerationRecipesAllLoad(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie01_macerationRecipesAllLoad(helper);
	}

	@GameTest
	public void tcRecie02_smeltingRecipesAllLoad(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie02_smeltingRecipesAllLoad(helper);
	}

	@GameTest
	public void tcRecie03_compressingRecipesAllLoad(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie03_compressingRecipesAllLoad(helper);
	}

	@GameTest
	public void tcRecie04_extractingRecipesAllLoad(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie04_extractingRecipesAllLoad(helper);
	}

	@GameTest
	public void tcRecie11_distillingRecipeFamilyRegistered(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie11_distillingRecipeFamilyRegistered(helper);
	}

	@GameTest
	public void tcRecie05_machineCasingResolves(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie05_machineCasingResolves(helper);
	}

	@GameTest
	public void tcRecie06_maceratorResolves(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie06_maceratorResolves(helper);
	}

	@GameTest
	public void tcRecie07_extractorResolves(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie07_extractorResolves(helper);
	}

	@GameTest
	public void tcRecie08_compressorResolves(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie08_compressorResolves(helper);
	}

	@GameTest
	public void tcRecie09_silverPlateBlockResolves(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie09_silverPlateBlockResolves(helper);
	}

	@GameTest
	public void tcRecie10_temperedIronPlateBlockResolves(GameTestHelper helper) {
		RecipeCoverageScenarios.tcRecie10_temperedIronPlateBlockResolves(helper);
	}
}
