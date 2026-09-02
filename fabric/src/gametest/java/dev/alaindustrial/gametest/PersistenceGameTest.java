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

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set ──

	/**
	 * Macerator save/load round-trip through the shared {@code MachineBlockEntity} persistence path (buffer,
	 * slots, progress). Loader-neutral twin of {@link #rPer01_maceratorNbtRoundTrip}. Body:
	 * {@link MachineEnergyScenarios#nbtRoundTripPreservesState}.
	 */
	@GameTest
	public void rPer01b_maceratorNbtRoundTripPreservesState(GameTestHelper helper) {
		MachineEnergyScenarios.nbtRoundTripPreservesState(helper);
	}

	/**
	 * Electric furnace save/load round-trip — the same {@code MachineBlockEntity} path for a machine other than
	 * the macerator. Body: {@link MachineEnergyScenarios#furnaceNbtRoundTrip}.
	 */
	@GameTest
	public void rPer01c_furnaceNbtRoundTrip(GameTestHelper helper) {
		MachineEnergyScenarios.furnaceNbtRoundTrip(helper);
	}

	// -- MOD-556: the tank saves itself; the on-disk shape must not have moved with it --

	/**
	 * Every machine tank still writes the exact {@code <prefix>Mb} / {@code <prefix>Fluid} pair it wrote
	 * before the code moved into {@code FluidTank}. Body:
	 * {@link PersistenceScenarios#mod556_tankKeysUnchangedAfterSelfSave}.
	 */
	@GameTest
	public void mod556Per01_tankKeysUnchangedAfterSelfSave(GameTestHelper helper) {
		PersistenceScenarios.mod556_tankKeysUnchangedAfterSelfSave(helper);
	}

	/**
	 * A world saved by a build from before the move still opens: hand-built pre-MOD-556 tags load with
	 * their contents intact, including the pump's two legacy fallbacks. Body:
	 * {@link PersistenceScenarios#mod556_preRefactorSavesStillLoad}.
	 */
	@GameTest
	public void mod556Per02_preRefactorSavesStillLoad(GameTestHelper helper) {
		PersistenceScenarios.mod556_preRefactorSavesStillLoad(helper);
	}

	/**
	 * The block-entity format version and its migration ladder agree, and the version really gates the
	 * ladder. Body: {@link PersistenceScenarios#mod556_dataVersionMatchesTheLadder}.
	 */
	@GameTest
	public void mod556Per03_dataVersionMatchesTheLadder(GameTestHelper helper) {
		PersistenceScenarios.mod556_dataVersionMatchesTheLadder(helper);
	}
}
