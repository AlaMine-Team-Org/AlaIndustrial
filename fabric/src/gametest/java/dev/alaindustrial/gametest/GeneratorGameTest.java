package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the fuel generator — automates the parts of {@code TC-GEN-001}
 * (docs/testing/blocks/generators/generator.md) that need a live {@link net.minecraft.server.level.ServerLevel}.
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link GeneratorScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation.
 * That is what makes the SAME scenario run on BOTH loaders — NeoForge registers it in
 * {@code NeoForgeGameTests} — instead of only on whichever loader it happened to be written in.
 */
public class GeneratorGameTest {

	@GameTest
	public void tcGen001Fun01_coalRaisesBuffer(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Fun01_coalRaisesBuffer(helper);
	}

	@GameTest
	public void tcGen001Fun02_bufferCapsAtMax(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Fun02_bufferCapsAtMax(helper);
	}

	@GameTest
	public void tcGen001Neg01_rejectsExternalEu(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Neg01_rejectsExternalEu(helper);
	}

	@GameTest
	public void tcGen001Neg03_fullBufferPausesBurn(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Neg03_fullBufferPausesBurn(helper);
	}

	@GameTest
	public void tcGen001Per01_stateSurvivesNbtRoundTrip(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Per01_stateSurvivesNbtRoundTrip(helper);
	}

	@GameTest
	public void tcGen001Neg04_lavaBucketRejected(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Neg04_lavaBucketRejected(helper);
	}

	@GameTest
	public void tcGen001Neg04b_menuSlotRejectsLavaBucket(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Neg04b_menuSlotRejectsLavaBucket(helper);
	}

	@GameTest
	public void tcGen001Fun03_coalBlockBurnsLonger(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Fun03_coalBlockBurnsLonger(helper);
	}

	@GameTest
	public void tcGen001Fun04_pushesToAdjacentConsumer(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Fun04_pushesToAdjacentConsumer(helper);
	}

	@GameTest
	public void tcGen001Neg05_fullAdjacentConsumerDoesNotDrainGenerator(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Neg05_fullAdjacentConsumerDoesNotDrainGenerator(helper);
	}

	@GameTest
	public void tcGen001Neg02_nonFuelProducesNoEu(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Neg02_nonFuelProducesNoEu(helper);
	}

	@GameTest
	public void tcGen001Prf01_ratePerTickMatchesConfig(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Prf01_ratePerTickMatchesConfig(helper);
	}

	@GameTest
	public void tcGen001Neg02b_fuelSlotRejectsNonFuel(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Neg02b_fuelSlotRejectsNonFuel(helper);
	}

	@GameTest
	public void tcGen001Per02_breakDropsFuelNoDupe(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Per02_breakDropsFuelNoDupe(helper);
	}

	@GameTest
	public void tcGen001Con01_pairwiseNeighbours(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Con01_pairwiseNeighbours(helper);
	}

	@GameTest
	public void tcGen001Phy02_hitboxIsFullCube(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Phy02_hitboxIsFullCube(helper);
	}

	@GameTest
	public void tcGen001Prf02_packetCappedAtLv(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Prf02_packetCappedAtLv(helper);
	}

	@GameTest
	public void tcGen001Sta01_litStateTracksBurning(GameTestHelper helper) {
		GeneratorScenarios.tcGen001Sta01_litStateTracksBurning(helper);
	}
}
