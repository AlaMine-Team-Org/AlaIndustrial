package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 server game tests — the **common-to-all-blocks** layer (RULES.md {@code R-*}). These run in a
 * real {@link net.minecraft.server.level.ServerLevel} via {@code ./gradlew runGameTest} and exit
 * non-zero on failure, so a regression fails CI (unlike the legacy logging self-test).
 *
 * <p>Parametric over the whole {@code alaindustrial} block registry — new blocks are covered
 * automatically, no per-block edit (mirrors the legacy {@code BLOCK_STANDARDS} check). Per-block
 * functional suites and integration scenarios come on top of this layer.
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link AlaCommonScenarios}
 * ({@code common/src/gametest}).</b> What stays here is the Fabric wiring only: the
 * {@code @GameTest} annotation and a delegation, so the SAME scenario runs on BOTH loaders
 * (NeoForge registers it in {@code NeoForgeGameTests}). The Garden Drone and menu-data-width
 * entries below already delegated to common before this task.
 *
 * <p>See docs/testing/AUTOMATION-STANDARDS.md (§2 naming, §3 traceability, §4 world conditions).
 */
public class AlaCommonGameTest {

	@GameTest
	public void everyBlockPlacesAndBreaks(GameTestHelper helper) {
		AlaCommonScenarios.everyBlockPlacesAndBreaks(helper);
	}

	@GameTest
	public void networkTickGuardIsolatesThrows(GameTestHelper helper) {
		AlaCommonScenarios.networkTickGuardIsolatesThrows(helper);
	}

	@GameTest
	public void everyBlockDropsItself(GameTestHelper helper) {
		AlaCommonScenarios.everyBlockDropsItself(helper);
	}

	@GameTest
	public void everyBlockNoDropByHand(GameTestHelper helper) {
		AlaCommonScenarios.everyBlockNoDropByHand(helper);
	}

	@GameTest
	public void blockStandardsAllBlocks(GameTestHelper helper) {
		AlaCommonScenarios.blockStandardsAllBlocks(helper);
	}

	@GameTest
	public void alaCommandRegistered(GameTestHelper helper) {
		AlaCommonScenarios.alaCommandRegistered(helper);
	}

	/**
	 * MOD-277: the Garden Drone Station's four actions, its EU accounting, and the two ways the
	 * harvest must refuse to run (no power, no room). Bodies are loader-neutral — the NeoForge lane
	 * runs the same ones.
	 *
	 * @implements TC-DRONE-001-FUN01 — bare dirt is tilled, one action's EU is spent
	 */
	@GameTest
	public void gardenDroneTillsDirt(GameTestHelper helper) {
		GardenDroneScenarios.fun01TillsDirtAndSpendsEu(helper);
	}

	/** @implements TC-DRONE-001-FUN02 — a seed is planted on bare farmland and consumed */
	@GameTest
	public void gardenDronePlantsSeed(GameTestHelper helper) {
		GardenDroneScenarios.fun02PlantsSeedOnFarmland(helper);
	}

	/** @implements TC-DRONE-001-FUN03 — a ripe crop lands in the station, never in the world */
	@GameTest
	public void gardenDroneHarvestsIntoStation(GameTestHelper helper) {
		GardenDroneScenarios.fun03HarvestsRipeCropIntoStation(helper);
	}

	/** @implements TC-DRONE-001-FUN04 — an unpowered station leaves the crop alone */
	@GameTest
	public void gardenDroneWithoutEnergyDoesNothing(GameTestHelper helper) {
		GardenDroneScenarios.fun04NoEnergyLeavesCropUntouched(helper);
	}

	/** @implements TC-DRONE-001-FUN05 — a full output blocks the harvest instead of voiding it */
	@GameTest
	public void gardenDroneFullOutputKeepsCrop(GameTestHelper helper) {
		GardenDroneScenarios.fun05FullOutputLeavesCropStanding(helper);
	}

	/** @implements TC-DRONE-001-FUN08 — the hoe's last point is spent and the slot frees up */
	@GameTest
	public void gardenDroneHoeBreaksOnLastUse(GameTestHelper helper) {
		GardenDroneScenarios.fun08HoeBreaksOnItsLastUse(helper);
	}

	/** @implements TC-DRONE-001-FUN09 — the hoe is not destroyed one use early */
	@GameTest
	public void gardenDroneHoeSurvivesUntilLastUse(GameTestHelper helper) {
		GardenDroneScenarios.fun09HoeSurvivesUntilItsLastUse(helper);
	}

	/** @implements TC-DRONE-001-FUN10 — the drone stands on the tile it worked before flying home */
	@GameTest
	public void gardenDroneStandsOnTileBeforeFlyingHome(GameTestHelper helper) {
		GardenDroneScenarios.fun10StandsOnTheTileBeforeFlyingHome(helper);
	}

	/** @implements TC-DRONE-001-FUN07 — an empty dock tends nothing and says why */
	@GameTest
	public void gardenDroneWithoutDroneIsInert(GameTestHelper helper) {
		GardenDroneScenarios.fun07WithoutDroneNothingHappens(helper);
	}

	/** @implements TC-DRONE-001-FUN06 — the drone flies before its action lands */
	@GameTest
	public void gardenDroneFlightDelaysAction(GameTestHelper helper) {
		GardenDroneScenarios.fun06FlightDelaysTheAction(helper);
	}

	/**
	 * MOD-235: every machine menu's client stub is bound to exactly as many sync channels as its block
	 * entity projects. Parametric over {@code ContentManifest.MENUS}, so a new machine menu is covered
	 * without editing this file. Body is loader-neutral — the NeoForge lane runs the same one.
	 *
	 * @implements TC-CMN-001-REG02 — client menu data width == block entity channel count
	 */
	@GameTest
	public void menuDataWidthMatchesBlockEntity(GameTestHelper helper) {
		MenuDataWidthScenarios.reg02ClientMenuWidthMatchesBlockEntity(helper);
	}

	@GameTest
	public void oresInConventionTags(GameTestHelper helper) {
		AlaCommonScenarios.oresInConventionTags(helper);
	}

	/**
	 * MOD-335 guard — the structure this lane runs on must contain a rig, or drops fall into a chunk
	 * nothing force-loads and drop counts silently read zero. Fabric's default template already
	 * satisfies this; the guard exists so a future {@code structure = ...} override cannot regress it.
	 */
	@GameTest
	public void gametestRigStructureFitsRigs(GameTestHelper helper) {
		AlaCommonScenarios.gametestRigStructureFitsRigs(helper);
	}
}
