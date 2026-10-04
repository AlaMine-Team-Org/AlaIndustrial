package dev.alaindustrial.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * World scenarios for the nuclear reactor (MOD-468).
 *
 * <p><b>Written because a player could not start one and neither of us could say why.</b> Every
 * condition the reactor checks is invisible from outside — sealed shell, racked rods, a redstone
 * signal, an open throttle, room in the buffer — and reasoning about them from the source had already
 * been wrong twice. A scenario that builds the room, fuels it, powers it and reads the output settles
 * the question instead of arguing it, and keeps it settled.
 *
 * <p><b>A facade since MOD-713 (TST-2).</b> The two lanes run these names (the roster below), so they stay; each
 * scenario delegates to the class of its mechanic — {@link ReactorRoomScenarios}, {@link ReactorBareModeScenarios},
 * {@link ReactorBlastScenarios}, {@link ReactorAdvancementScenarios}, {@link ReactorConsoleScenarios} — and the rigs
 * live in {@link ReactorRig}. A new reactor scenario goes into the class of its mechanic and joins the roster of
 * that class, not this one.
 */
public final class ReactorScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReactorScenarios::sealedFuelledAndPoweredReactorProduces,
								"reactor_sealed_fuelled_powered_produces")
						.fabricId("ReactorGameTest", "sealedFuelledAndPoweredReactorProduces").ticks(400),
				RosterEntry.of(ReactorScenarios::removingTheSignalScramsTheReactor, "reactor_signal_removal_scrams")
						.fabricId("ReactorGameTest", "removingTheSignalScramsTheReactor").ticks(400),
				RosterEntry.of(ReactorScenarios::coolantCatchesACoreTheShellCannotHold,
								"reactor_coolant_catches_runaway")
						.fabricId("ReactorGameTest", "coolantCatchesACoreTheShellCannotHold").ticks(400),
				RosterEntry.of(ReactorScenarios::everyRackedRodWearsTogether, "reactor_rods_wear_together")
						.fabricId("ReactorGameTest", "everyRackedRodWearsTogether").ticks(400),
				RosterEntry.of(ReactorScenarios::nozzleVentsIntoAirAndStallsAgainstAWall,
								"reactor_nozzle_vents_and_stalls")
						.fabricId("ReactorGameTest", "nozzleVentsIntoAirAndStallsAgainstAWall").ticks(400),
				RosterEntry.of(ReactorScenarios::poweredReactorFeedsACableOutsideTheShell,
								"reactor_feeds_cable_outside_shell")
						.fabricId("ReactorGameTest", "poweredReactorFeedsACableOutsideTheShell").ticks(400),
				RosterEntry.of(ReactorScenarios::bareReactorProducesMeltsAndObeysTheSwitch,
								"reactor_bare_produces_melts_and_obeys_switch")
						.fabricId("ReactorGameTest", "bareReactorProducesMeltsAndObeysTheSwitch").ticks(400),
				RosterEntry.of(ReactorScenarios::breachingAWallDropsTheReactorIntoBareMode,
								"reactor_breach_drops_into_bare_mode")
						.fabricId("ReactorGameTest", "breachingAWallDropsTheReactorIntoBareMode").ticks(400),
				RosterEntry.of(ReactorScenarios::onlyOneControllerBurnsASharedRack,
								"reactor_one_controller_per_shared_rack")
						.fabricId("ReactorGameTest", "onlyOneControllerBurnsASharedRack").ticks(400),
				RosterEntry.of(ReactorScenarios::anOverheatingRoomMeltsItsContentsAndKeepsItsShell,
								"reactor_meltdown_spares_the_shell")
						.fabricId("ReactorGameTest", "anOverheatingRoomMeltsItsContentsAndKeepsItsShell").ticks(400),
				RosterEntry.of(ReactorScenarios::aWorkingRoomMeltsPlainPipesAndSparesReinforcedOnes,
								"reactor_working_room_melts_plain_pipes")
						.fabricId("ReactorGameTest", "aWorkingRoomMeltsPlainPipesAndSparesReinforcedOnes").ticks(400),
				RosterEntry.of(ReactorScenarios::aWorkingRoomMeltsPlainSteamPipesAndSparesReinforcedOnes,
								"reactor_working_room_melts_plain_steam_pipes")
						.fabricId("ReactorGameTest", "aWorkingRoomMeltsPlainSteamPipesAndSparesReinforcedOnes")
						.ticks(400),
				RosterEntry.of(ReactorScenarios::aShieldedLeverInsideTheRoomSealsAndScrams,
								"reactor_lever_seals_and_scrams")
						.fabricId("ReactorGameTest", "aShieldedLeverInsideTheRoomSealsAndScrams").ticks(400),
				RosterEntry.of(ReactorScenarios::aCoreAtFullScaleCountsDownAndBlowsItsRoomApart,
								"reactor_blast_countdown_and_explosion")
						.fabricId("ReactorGameTest", "aCoreAtFullScaleCountsDownAndBlowsItsRoomApart").ticks(400),
				RosterEntry.of(ReactorScenarios::aRedstoneClockDoesNotSaveTheReactor,
								"reactor_redstone_clock_does_not_save_it")
						.fabricId("ReactorGameTest", "aRedstoneClockDoesNotSaveTheReactor").ticks(400),
				RosterEntry.of(ReactorScenarios::aFullBufferStillCooksTheCore, "reactor_full_buffer_still_cooks")
						.fabricId("ReactorGameTest", "aFullBufferStillCooksTheCore").ticks(400),
				RosterEntry.of(ReactorScenarios::aBareClusterSettlesUntilItIsTooBig, "reactor_bare_instability_limit")
						.fabricId("ReactorGameTest", "aBareClusterSettlesUntilItIsTooBig").ticks(400),
				RosterEntry.of(ReactorScenarios::aLavaFarmBurnsNoFuel, "reactor_lava_farm_burns_no_fuel")
						.fabricId("ReactorGameTest", "aLavaFarmBurnsNoFuel").ticks(400),
				RosterEntry.of(ReactorScenarios::aBlockedExplosionLeavesNoAftermath,
								"reactor_blocked_blast_leaves_no_aftermath")
						.fabricId("ReactorGameTest", "aBlockedExplosionLeavesNoAftermath").ticks(400),
				RosterEntry.of(ReactorScenarios::aBrokenRackGivesItsRodsBack, "reactor_broken_rack_returns_rods")
						.fabricId("ReactorGameTest", "aBrokenRackGivesItsRodsBack").ticks(400),
				RosterEntry.of(ReactorScenarios::reactorMilestonesReachTheControllersOwner,
								"reactor_milestones_reach_the_owner")
						.fabricId("ReactorGameTest", "reactorMilestonesReachTheControllersOwner").ticks(400),
				RosterEntry.of(ReactorScenarios::anUnownedReactorAwardsNobody, "reactor_unowned_awards_nobody")
						.fabricId("ReactorGameTest", "anUnownedReactorAwardsNobody").ticks(400),
				RosterEntry.of(ReactorScenarios::consoleChannelsCarryTheServersHeatMarks,
								"reactor_console_channels_and_no_slots")
						.fabricId("ReactorGameTest", "consoleChannelsCarryTheServersHeatMarks").ticks(100),
				RosterEntry.of(ReactorScenarios::zoneSnapshotMatchesTheRacks, "reactor_zone_snapshot_matches_racks")
						.fabricId("ReactorGameTest", "zoneSnapshotMatchesTheRacks").ticks(100),
				RosterEntry.of(ReactorScenarios::theEventLogRecordsEachTransitionOnce,
								"reactor_event_log_records_each_transition_once")
						.fabricId("ReactorGameTest", "theEventLogRecordsEachTransitionOnce").ticks(100));

		private Roster() {}
	}

	private ReactorScenarios() {
	}

	/** Delegates to {@link ReactorRoomScenarios#sealedFuelledAndPoweredReactorProduces}. */
	public static void sealedFuelledAndPoweredReactorProduces(GameTestHelper helper) {
		ReactorRoomScenarios.sealedFuelledAndPoweredReactorProduces(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#removingTheSignalScramsTheReactor}. */
	public static void removingTheSignalScramsTheReactor(GameTestHelper helper) {
		ReactorRoomScenarios.removingTheSignalScramsTheReactor(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#coolantCatchesACoreTheShellCannotHold}. */
	public static void coolantCatchesACoreTheShellCannotHold(GameTestHelper helper) {
		ReactorRoomScenarios.coolantCatchesACoreTheShellCannotHold(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#everyRackedRodWearsTogether}. */
	public static void everyRackedRodWearsTogether(GameTestHelper helper) {
		ReactorRoomScenarios.everyRackedRodWearsTogether(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#nozzleVentsIntoAirAndStallsAgainstAWall}. */
	public static void nozzleVentsIntoAirAndStallsAgainstAWall(GameTestHelper helper) {
		ReactorRoomScenarios.nozzleVentsIntoAirAndStallsAgainstAWall(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#poweredReactorFeedsACableOutsideTheShell}. */
	public static void poweredReactorFeedsACableOutsideTheShell(GameTestHelper helper) {
		ReactorRoomScenarios.poweredReactorFeedsACableOutsideTheShell(helper);
	}

	/** Delegates to {@link ReactorBareModeScenarios#bareReactorProducesMeltsAndObeysTheSwitch}. */
	public static void bareReactorProducesMeltsAndObeysTheSwitch(GameTestHelper helper) {
		ReactorBareModeScenarios.bareReactorProducesMeltsAndObeysTheSwitch(helper);
	}

	/** Delegates to {@link ReactorBareModeScenarios#breachingAWallDropsTheReactorIntoBareMode}. */
	public static void breachingAWallDropsTheReactorIntoBareMode(GameTestHelper helper) {
		ReactorBareModeScenarios.breachingAWallDropsTheReactorIntoBareMode(helper);
	}

	/** Delegates to {@link ReactorBareModeScenarios#onlyOneControllerBurnsASharedRack}. */
	public static void onlyOneControllerBurnsASharedRack(GameTestHelper helper) {
		ReactorBareModeScenarios.onlyOneControllerBurnsASharedRack(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#anOverheatingRoomMeltsItsContentsAndKeepsItsShell}. */
	public static void anOverheatingRoomMeltsItsContentsAndKeepsItsShell(GameTestHelper helper) {
		ReactorRoomScenarios.anOverheatingRoomMeltsItsContentsAndKeepsItsShell(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#aWorkingRoomMeltsPlainPipesAndSparesReinforcedOnes}. */
	public static void aWorkingRoomMeltsPlainPipesAndSparesReinforcedOnes(GameTestHelper helper) {
		ReactorRoomScenarios.aWorkingRoomMeltsPlainPipesAndSparesReinforcedOnes(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#aWorkingRoomMeltsPlainSteamPipesAndSparesReinforcedOnes}. */
	public static void aWorkingRoomMeltsPlainSteamPipesAndSparesReinforcedOnes(GameTestHelper helper) {
		ReactorRoomScenarios.aWorkingRoomMeltsPlainSteamPipesAndSparesReinforcedOnes(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#aShieldedLeverInsideTheRoomSealsAndScrams}. */
	public static void aShieldedLeverInsideTheRoomSealsAndScrams(GameTestHelper helper) {
		ReactorRoomScenarios.aShieldedLeverInsideTheRoomSealsAndScrams(helper);
	}

	/** Delegates to {@link ReactorBlastScenarios#aCoreAtFullScaleCountsDownAndBlowsItsRoomApart}. */
	public static void aCoreAtFullScaleCountsDownAndBlowsItsRoomApart(GameTestHelper helper) {
		ReactorBlastScenarios.aCoreAtFullScaleCountsDownAndBlowsItsRoomApart(helper);
	}

	/** Delegates to {@link ReactorBlastScenarios#aRedstoneClockDoesNotSaveTheReactor}. */
	public static void aRedstoneClockDoesNotSaveTheReactor(GameTestHelper helper) {
		ReactorBlastScenarios.aRedstoneClockDoesNotSaveTheReactor(helper);
	}

	/** Delegates to {@link ReactorBlastScenarios#aFullBufferStillCooksTheCore}. */
	public static void aFullBufferStillCooksTheCore(GameTestHelper helper) {
		ReactorBlastScenarios.aFullBufferStillCooksTheCore(helper);
	}

	/** Delegates to {@link ReactorBareModeScenarios#aBareClusterSettlesUntilItIsTooBig}. */
	public static void aBareClusterSettlesUntilItIsTooBig(GameTestHelper helper) {
		ReactorBareModeScenarios.aBareClusterSettlesUntilItIsTooBig(helper);
	}

	/** Delegates to {@link ReactorBareModeScenarios#aLavaFarmBurnsNoFuel}. */
	public static void aLavaFarmBurnsNoFuel(GameTestHelper helper) {
		ReactorBareModeScenarios.aLavaFarmBurnsNoFuel(helper);
	}

	/** Delegates to {@link ReactorBlastScenarios#aBlockedExplosionLeavesNoAftermath}. */
	public static void aBlockedExplosionLeavesNoAftermath(GameTestHelper helper) {
		ReactorBlastScenarios.aBlockedExplosionLeavesNoAftermath(helper);
	}

	/** Delegates to {@link ReactorRoomScenarios#aBrokenRackGivesItsRodsBack}. */
	public static void aBrokenRackGivesItsRodsBack(GameTestHelper helper) {
		ReactorRoomScenarios.aBrokenRackGivesItsRodsBack(helper);
	}

	/** Delegates to {@link ReactorAdvancementScenarios#reactorMilestonesReachTheControllersOwner}. */
	public static void reactorMilestonesReachTheControllersOwner(GameTestHelper helper) {
		ReactorAdvancementScenarios.reactorMilestonesReachTheControllersOwner(helper);
	}

	/** Delegates to {@link ReactorAdvancementScenarios#anUnownedReactorAwardsNobody}. */
	public static void anUnownedReactorAwardsNobody(GameTestHelper helper) {
		ReactorAdvancementScenarios.anUnownedReactorAwardsNobody(helper);
	}

	/** Delegates to {@link ReactorConsoleScenarios#consoleChannelsCarryTheServersHeatMarks}. */
	public static void consoleChannelsCarryTheServersHeatMarks(GameTestHelper helper) {
		ReactorConsoleScenarios.consoleChannelsCarryTheServersHeatMarks(helper);
	}

	/** Delegates to {@link ReactorConsoleScenarios#zoneSnapshotMatchesTheRacks}. */
	public static void zoneSnapshotMatchesTheRacks(GameTestHelper helper) {
		ReactorConsoleScenarios.zoneSnapshotMatchesTheRacks(helper);
	}

	/** Delegates to {@link ReactorConsoleScenarios#theEventLogRecordsEachTransitionOnce}. */
	public static void theEventLogRecordsEachTransitionOnce(GameTestHelper helper) {
		ReactorConsoleScenarios.theEventLogRecordsEachTransitionOnce(helper);
	}

}
