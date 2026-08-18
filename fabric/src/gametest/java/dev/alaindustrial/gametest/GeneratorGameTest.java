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

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set.
	// Each is the GeneratorEnergyScenarios twin of a GeneratorScenarios case above (two common bodies per
	// behaviour — merging them is a follow-up); until then both lanes run both.

	/**
	 * @implements TC-GEN-001-FUN02 — buffer tops off to exactly {@code generatorBuffer} from cap−1 (BVA leg).
	 * Body: {@link GeneratorEnergyScenarios#generatorBufferCapsAtMaxBva}.
	 */
	@GameTest
	public void tcGen001Fun02b_bufferCapsAtMaxBva(GameTestHelper helper) {
		GeneratorEnergyScenarios.generatorBufferCapsAtMaxBva(helper);
	}

	/**
	 * @implements TC-GEN-001-FUN04 — direct push into an adjacent battery box (cable-less path).
	 * Body: {@link GeneratorEnergyScenarios#generatorChargesAdjacentBox}.
	 */
	@GameTest
	public void tcGen001Fun04b_chargesAdjacentBox(GameTestHelper helper) {
		GeneratorEnergyScenarios.generatorChargesAdjacentBox(helper);
	}

	/**
	 * @implements TC-GEN-001-NEG01 — the generator publishes maxInsert == 0 and takes nothing from outside.
	 * Body: {@link GeneratorEnergyScenarios#generatorRejectsExternalEu}.
	 */
	@GameTest
	public void tcGen001Neg01b_rejectsExternalEu(GameTestHelper helper) {
		GeneratorEnergyScenarios.generatorRejectsExternalEu(helper);
	}

	/**
	 * @implements TC-GEN-001-NEG03 — a full buffer pauses the burn (R-NRG-11, no fuel waste).
	 * Body: {@link GeneratorEnergyScenarios#generatorFullBufferPausesBurn}.
	 */
	@GameTest
	public void tcGen001Neg03b_fullBufferPausesBurn(GameTestHelper helper) {
		GeneratorEnergyScenarios.generatorFullBufferPausesBurn(helper);
	}

	/**
	 * @implements TC-GEN-001-NEG05 — a full adjacent consumer does not drain the generator into the void.
	 * Body: {@link GeneratorEnergyScenarios#fullNeighbourNoLeak}.
	 */
	@GameTest
	public void tcGen001Neg05b_fullNeighbourNoLeak(GameTestHelper helper) {
		GeneratorEnergyScenarios.fullNeighbourNoLeak(helper);
	}

	/**
	 * @implements TC-GEN-001-PRF01 — exact EU/t rate == {@code fuelEuPerTick} × multiplier.
	 * Body: {@link GeneratorEnergyScenarios#generatorRatePerTickMatchesConfig}.
	 */
	@GameTest
	public void tcGen001Prf01b_ratePerTickMatchesConfig(GameTestHelper helper) {
		GeneratorEnergyScenarios.generatorRatePerTickMatchesConfig(helper);
	}

	/**
	 * MOD-386 — a caught strike banks the whole configured packet into the tip's capacitor.
	 * Body: {@link LightningRodScenarios#strikeBanksIntoTheCapacitor}.
	 */
	@GameTest
	public void lightningRod_strikeBanksIntoTheCapacitor(GameTestHelper helper) {
		LightningRodScenarios.strikeBanksIntoTheCapacitor(helper);
	}

	/**
	 * MOD-386 — no conductor tip → the strike is lost outright and no EU appears from nothing.
	 * Body: {@link LightningRodScenarios#strikeWithoutTipBanksNothing}.
	 */
	@GameTest
	public void lightningRod_strikeWithoutTipBanksNothing(GameTestHelper helper) {
		LightningRodScenarios.strikeWithoutTipBanksNothing(helper);
	}

	/**
	 * MOD-386 — a strike on a full capacitor is wasted, never overflows, and costs the tip extra wear.
	 * Body: {@link LightningRodScenarios#strikeOnFullCapacitorIsWastedAndWearsTheTip}.
	 */
	@GameTest
	public void lightningRod_strikeOnFullCapacitorIsWastedAndWearsTheTip(GameTestHelper helper) {
		LightningRodScenarios.strikeOnFullCapacitorIsWastedAndWearsTheTip(helper);
	}

	/**
	 * MOD-386 — the banked burst leaves as a flat rate, under the LV ceiling.
	 * Body: {@link LightningRodScenarios#capacitorBleedsIntoTheBuffer}.
	 */
	@GameTest
	public void lightningRod_capacitorBleedsIntoTheBuffer(GameTestHelper helper) {
		LightningRodScenarios.capacitorBleedsIntoTheBuffer(helper);
	}

	/**
	 * MOD-386 — a saturated network parks the reserve instead of destroying it.
	 * Body: {@link LightningRodScenarios#aFullBufferDoesNotDrainTheCapacitor}.
	 */
	@GameTest
	public void lightningRod_aFullBufferDoesNotDrainTheCapacitor(GameTestHelper helper) {
		LightningRodScenarios.aFullBufferDoesNotDrainTheCapacitor(helper);
	}

	/**
	 * MOD-386 — banked charge survives a save/load round trip.
	 * Body: {@link LightningRodScenarios#capacitorSurvivesSaveAndLoad}.
	 */
	@GameTest
	public void lightningRod_capacitorSurvivesSaveAndLoad(GameTestHelper helper) {
		LightningRodScenarios.capacitorSurvivesSaveAndLoad(helper);
	}

	/**
	 * MOD-386 — a tip that wears out mid-strike takes its banked charge with it.
	 * Body: {@link LightningRodScenarios#aBreakingTipTakesItsStoredChargeWithIt}.
	 */
	@GameTest
	public void lightningRod_aBreakingTipTakesItsStoredChargeWithIt(GameTestHelper helper) {
		LightningRodScenarios.aBreakingTipTakesItsStoredChargeWithIt(helper);
	}
}
