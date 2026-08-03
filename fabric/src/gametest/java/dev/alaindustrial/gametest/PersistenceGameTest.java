package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 persistence suite (R-PER-01/05): NBT save/load round-trip preserves block-entity state
 * (energy, progress, inventory, fluid tank, evolution) across blocks beyond generator/battery_box.
 * Migrated from the legacy IndustrializationSelfTest PERSISTENCE check.
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link PersistenceScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a
 * delegation. That is what makes the SAME scenario run on BOTH loaders — NeoForge registers
 * it in {@code NeoForgeGameTests} — instead of only on whichever loader it happened to be
 * written in. NBT round-trips are worth the most here: 26.2 moved persistence to the
 * ValueInput/ValueOutput API, and that seam is loader-adjacent.
 */
public class PersistenceGameTest {

	@GameTest
	public void rPer01_maceratorNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.rPer01_maceratorNbtRoundTrip(helper);
	}

	@GameTest
	public void rPer01_furnaceNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.rPer01_furnaceNbtRoundTrip(helper);
	}

	@GameTest
	public void rPer01_geothermalFluidNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.rPer01_geothermalFluidNbtRoundTrip(helper);
	}

	@GameTest
	public void rPer01_solarEvolveNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.rPer01_solarEvolveNbtRoundTrip(helper);
	}

	@GameTest
	public void tcMach003Per01_compressorNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.tcMach003Per01_compressorNbtRoundTrip(helper);
	}

	@GameTest
	public void tcMach004Per01_extractorNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.tcMach004Per01_extractorNbtRoundTrip(helper);
	}

	@GameTest
	public void tcEFurn001Per01_furnaceFreezeThenResume(GameTestHelper helper) {
		PersistenceScenarios.tcEFurn001Per01_furnaceFreezeThenResume(helper);
	}

	@GameTest
	public void tcPump001Per01_pumpTankNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.tcPump001Per01_pumpTankNbtRoundTrip(helper);
	}

	@GameTest
	public void tcPump001Per02_pumpTankEmptyNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.tcPump001Per02_pumpTankEmptyNbtRoundTrip(helper);
	}

	@GameTest
	public void tcCable001Per01_bufferNbtRoundTrip(GameTestHelper helper) {
		PersistenceScenarios.tcCable001Per01_bufferNbtRoundTrip(helper);
	}

	@GameTest
	public void tcCable001Per02_legacyMachineKeysIgnoredOnLoad(GameTestHelper helper) {
		PersistenceScenarios.tcCable001Per02_legacyMachineKeysIgnoredOnLoad(helper);
	}
}
