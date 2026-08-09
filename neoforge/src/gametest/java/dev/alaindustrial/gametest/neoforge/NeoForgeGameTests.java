package dev.alaindustrial.gametest.neoforge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.AssemblerPerfScenarios;
import dev.alaindustrial.gametest.ForeignMaterialScenarios;
import dev.alaindustrial.gametest.CableFaceParityScenarios;
import dev.alaindustrial.gametest.FluidPipeScenarios;
import dev.alaindustrial.gametest.CableEnergyScenarios;
import dev.alaindustrial.gametest.AdvancedCircuitScenarios;
import dev.alaindustrial.gametest.CableInsulationScenarios;
import dev.alaindustrial.gametest.CableBreakerScenarios;
import dev.alaindustrial.gametest.CableShockScenarios;
import dev.alaindustrial.gametest.ChargePadScenarios;
import dev.alaindustrial.gametest.CoreFluidScenarios;
import dev.alaindustrial.gametest.GeneratorEnergyScenarios;
import dev.alaindustrial.gametest.MachineEnergyScenarios;
import dev.alaindustrial.gametest.BatteryScenarios;
import dev.alaindustrial.gametest.CesuScenarios;
import dev.alaindustrial.gametest.StorageEnergyScenarios;
import dev.alaindustrial.gametest.RecipeTagScenarios;
import dev.alaindustrial.gametest.AssemblerScenarios;
import dev.alaindustrial.gametest.StorageClusterScenarios;
import dev.alaindustrial.gametest.WorldContentScenarios;
import dev.alaindustrial.gametest.CapsuleScenarios;
import dev.alaindustrial.gametest.GeothermalLavaInputScenarios;
import dev.alaindustrial.gametest.ElectricChainsawScenarios;
import dev.alaindustrial.gametest.ElectricHoeScenarios;
import dev.alaindustrial.gametest.ElectricShovelScenarios;
import dev.alaindustrial.gametest.ElectricDrillScenarios;
import dev.alaindustrial.gametest.ElectricSaberScenarios;
import dev.alaindustrial.gametest.MagnetScenarios;
import dev.alaindustrial.gametest.OilScenarios;
import dev.alaindustrial.gametest.FluxweaveArmorScenarios;
import dev.alaindustrial.gametest.AlloySmelterScenarios;
import dev.alaindustrial.gametest.GalvanicBathScenarios;
import dev.alaindustrial.gametest.DistillationColumnScenarios;
import dev.alaindustrial.gametest.PolymerizerScenarios;
import dev.alaindustrial.gametest.VulcanizerScenarios;
import dev.alaindustrial.gametest.GardenDroneScenarios;
import dev.alaindustrial.gametest.MenuDataWidthScenarios;
import dev.alaindustrial.gametest.EnergyPackScenarios;
import dev.alaindustrial.gametest.JetpackScenarios;
import dev.alaindustrial.gametest.GuideBookGiverScenarios;
import dev.alaindustrial.gametest.PouchScenarios;
import dev.alaindustrial.gametest.HammerScenarios;
import dev.alaindustrial.gametest.TrellisScenarios;
import dev.alaindustrial.gametest.IncubatorScenarios;
import dev.alaindustrial.gametest.ScytheScenarios;
import dev.alaindustrial.gametest.StockDisplayFrameScenarios;
import dev.alaindustrial.gametest.TemperedIronToolScenarios;
import dev.alaindustrial.gametest.ItemPipeScenarios;
import dev.alaindustrial.gametest.PersistenceScenarios;
import dev.alaindustrial.gametest.EnrichedUraniumTorchScenarios;
import dev.alaindustrial.gametest.WaterMillWheelScenarios;
import dev.alaindustrial.gametest.CablePlacementScenarios;
import dev.alaindustrial.gametest.GeneratorScenarios;
import dev.alaindustrial.gametest.RecipeCoverageScenarios;
import dev.alaindustrial.gametest.NetworkAnalyzerScenarios;
import dev.alaindustrial.gametest.TeleporterStationScenarios;
import dev.alaindustrial.gametest.TeleporterGuiScenarios;
import dev.alaindustrial.gametest.TeleporterJumpScenarios;
import dev.alaindustrial.gametest.OreScenarios;
import dev.alaindustrial.gametest.MuteChipScenarios;
import dev.alaindustrial.gametest.FluidTankScenarios;
import dev.alaindustrial.gametest.AlaCommonScenarios;
import dev.alaindustrial.registry.ModContent;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MOD-022 — NeoForge world gametest lane (GATE). Registers world-based energy scenarios that run on a
 * real chunk-loaded {@code gameTestServer} (see {@code neoforge/build.gradle} {@code runs.gameTests}).
 * This is the coverage the JUnit {@code EphemeralTestServerProvider} could not provide (no ticking world).
 *
 * <p><b>Registration mechanism (verified against 26.2.0.8-beta sources):</b>
 * <ul>
 *   <li>{@code RegistryDataLoader.load(..., fromResources=true)} posts a
 *       {@code net.neoforged.neoforge.event.RegisterGameTestsEvent} on the mod bus when
 *       {@code GameTestHooks.isGametestEnabled()} is true. The event exposes the writable
 *       {@code TEST_ENVIRONMENT} and {@code TEST_INSTANCE} registries.</li>
 *   <li>{@code event.registerEnvironment(id, new TestEnvironmentDefinition.AllOf())} adds an empty
 *       environment and returns its {@code Holder}, which fills {@code TestData.environment}.</li>
 *   <li>{@code event.registerTest(id, GameTestInstance)} inserts a live instance into
 *       {@code Registries.TEST_INSTANCE}. {@code GameTestServer} runs it by calling {@code instance.run}
 *       directly (no codec round-trip) — see {@link CodeGameTestInstance} for why a custom instance is
 *       used instead of {@code FunctionGameTestInstance} (which needs an unreachable
 *       {@code TEST_FUNCTION} entry).</li>
 *   <li>Structure {@code minecraft:empty} (1x1x1) ships in vanilla data; blocks are placed in the same
 *       chunk a few relative blocks from the structure origin, so a small empty structure suffices.</li>
 * </ul>
 */
public final class NeoForgeGameTests {

	/** Shared empty test environment holder, filled on {@link #register}. */
	private static Holder<TestEnvironmentDefinition<?>> emptyEnv;

	/**
	 * Registers {@link CodeGameTestInstance#CODEC} as the {@code alaindustrial:code} instance type so the
	 * {@code TEST_INSTANCE} registry can be encoded during the client known-packs handshake without the
	 * {@code ClassCastException} the borrowed function-codec caused. {@code register(modBus)} is called from
	 * {@code IndustrializationNeoForge}. Registering the type is harmless outside the gametest run (the
	 * instances themselves are only added when {@code RegisterGameTestsEvent} fires).
	 */
	public static final DeferredRegister<MapCodec<? extends GameTestInstance>> INSTANCE_TYPES =
			DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Industrialization.MOD_ID);

	public static final DeferredHolder<MapCodec<? extends GameTestInstance>, MapCodec<CodeGameTestInstance>> CODE_TYPE =
			INSTANCE_TYPES.register("code", () -> CodeGameTestInstance.CODEC);

	private NeoForgeGameTests() {}

	/** Mod-bus listener, wired from {@code IndustrializationNeoForge}. */
	public static void register(RegisterGameTestsEvent event) {
		emptyEnv = event.registerEnvironment(Industrialization.id("empty_env"),
				new TestEnvironmentDefinition.AllOf());

		// Core world energy scenarios — shared, loader-neutral bodies in common
		// (dev.alaindustrial.gametest.*EnergyScenarios), so Fabric and NeoForge exercise the same core.
		// MOD-022 recipe seam: proves the machine RecipeType/RecipeSerializer register + resolve on NeoForge
		// (the frozen-registry fix — ModRecipesNeoForge). Fabric covers this via MachineGameTest; this is the
		// NeoForge world lane's first recipe-processing case.
		// MOD-062 Industrialist: the per-loader seams (POI state map via registry callback, the
		// profession's data-driven trade sets, the server-start village pool injection).
		registerTest(event, "industrialist_workbench_poi_mapping", 40, true,
				dev.alaindustrial.gametest.IndustrialistScenarios::workbenchStateMapsToPoi);
		registerTest(event, "industrialist_trade_sets_resolve", 40, true,
				dev.alaindustrial.gametest.IndustrialistScenarios::tradeSetsResolvePerLevel);
		registerTest(event, "industrialist_pool_injection_idempotent", 40, true,
				dev.alaindustrial.gametest.IndustrialistScenarios::poolInjectionIsIdempotent);
		registerTest(event, "industrialist_house_cap_filter", 40, true,
				dev.alaindustrial.gametest.IndustrialistScenarios::houseCapFilter);
		registerTest(event, "industrialist_house_structure_loads", 40, true,
				dev.alaindustrial.gametest.IndustrialistScenarios::houseStructureLoads);
		registerTest(event, "macerator_processes_recipe", 420, true,
				MachineEnergyScenarios::maceratorProcessesRecipe);
		// MOD-022 data-component seam: a charged battery box carries STORED_ENERGY on drop (frozen-registry
		// fix — ModDataComponentsNeoForge). Fabric covers this via BatteryBoxGameTest; NeoForge world lane's first.
		registerTest(event, "battery_box_drop_carries_energy", 40, true,
				StorageEnergyScenarios::batteryBoxDropCarriesEnergy);
		// Reinforced Energy Storage (MOD-351): the short-overflow guard has to run on BOTH loaders —
		// ContainerData is vanilla, but each loader builds its own menu/BE registration around it, and
		// this bug was invisible until a client actually rendered the number.
		registerTest(event, "cesu_sync_channels_fit_short", 40, true,
				CesuScenarios::cesu01SyncChannelsFitShort);
		registerTest(event, "cesu_scaled_channels_match_buffer", 40, true,
				CesuScenarios::cesu02ScaledChannelsMatchBuffer);
		registerTest(event, "cesu_discharge_slot_fills_buffer", 40, true,
				CesuScenarios::cesu03DischargeSlotFillsBuffer);
		// Battery (MOD-083): the stacking EU carrier. The per-stack arithmetic and the charge-carrying
		// recipe both run through loader-registered surfaces (item capability, recipe serializer), so
		// the NeoForge lane has to run the same bodies rather than trust the Fabric suite.
		registerTest(event, "battery_charging_a_stack_costs_per_item", 40, true,
				BatteryScenarios::battery01ChargingAStackCostsPerItem);
		registerTest(event, "battery_full_stack_still_charges", 40, true,
				BatteryScenarios::battery02FullStackStillCharges);
		registerTest(event, "battery_discharge_slot_drains_stack", 40, true,
				BatteryScenarios::battery03DischargeSlotDrainsStack);
		registerTest(event, "battery_stacking_follows_charge", 40, true,
				BatteryScenarios::battery04StackingFollowsCharge);
		registerTest(event, "battery_craft_carries_charge", 40, true,
				BatteryScenarios::battery05CraftCarriesChargeIntoResult);
		registerTest(event, "battery_excluded_from_auto_charge", 40, true,
				BatteryScenarios::battery06BatteryIsExcludedFromAutoCharge);
		registerTest(event, "battery_repeated_assembly_no_accumulation", 40, true,
				BatteryScenarios::battery07RepeatedAssemblyDoesNotAccumulate);
		registerTest(event, "battery_assembler_carries_charge", 40, true,
				BatteryScenarios::battery08AssemblerCarriesChargeThrough);
			registerTest(event, "battery_drill_upgrade_carries_charge", 40, true,
					BatteryScenarios::battery09DrillUpgradeCarriesChargeIntoResult);
		// Teleporter station (MOD-091): its face roles and its teleporter_private data component are
		// per-loader seams (energy adapter + frozen-registry), so the Fabric suite cannot vouch for them.
		registerTest(event, "teleporter_face_roles", 40, true,
				MachineEnergyScenarios::teleporterFaceRoles);
		registerTest(event, "teleporter_drop_carries_privacy", 40, true,
				MachineEnergyScenarios::teleporterDropCarriesPrivacy);
		registerTest(event, "generator_charges_adjacent_box", 40, true,
				GeneratorEnergyScenarios::generatorChargesAdjacentBox);
		registerTest(event, "full_neighbour_no_leak", 40, true,
				GeneratorEnergyScenarios::fullNeighbourNoLeak);
		registerTest(event, "generator_delivers_down_cable", 200, true,
				CableEnergyScenarios::generatorDeliversDownCable);
		registerTest(event, "return_round_robin_no_leak", 40, true,
				CableEnergyScenarios::returnRoundRobinNoLeak);
		// MOD-070 segment-to-segment flow: accumulation in intermediate cables + retention on break.
		registerTest(event, "line_accumulates_in_segments", 80, true,
				CableEnergyScenarios::lineAccumulatesInSegments);
		registerTest(event, "break_retains_at_source", 100, true,
				CableEnergyScenarios::breakRetainsAtSource);
		// MOD-219/MOD-358: each grade carries its own segment buffer — electrum > gold > copper > tin.
		registerTest(event, "cable_grades_carry_their_own_buffer", 260, true,
				CableEnergyScenarios::cableGradesCarryTheirOwnBuffer);
		registerTest(event, "mixed_network_takes_loss_from_strongest_cable", 220, true,
				CableEnergyScenarios::mixedNetworkTakesLossFromStrongestCable);
		registerTest(event, "mod259_insulated_copper_loses_less_than_bare", 400, true,
				CableInsulationScenarios::insulatedCopperLosesLessThanBare);
		registerTest(event, "mod259_insulated_tin_loses_less_than_bare", 400, true,
				CableInsulationScenarios::insulatedTinLosesLessThanBare);
		registerTest(event, "mod268_insulated_gold_loses_less_than_bare", 400, true,
				CableInsulationScenarios::insulatedGoldLosesLessThanBare);
		registerTest(event, "mod358_insulated_electrum_loses_less_than_bare", 400, true,
				CableInsulationScenarios::insulatedElectrumLosesLessThanBare);
		registerTest(event, "mod259_mixed_copper_uses_bare_loss_deterministically", 400, true,
				CableInsulationScenarios::mixedCopperUsesBareLossDeterministically);
		registerTest(event, "mod259_recipes_and_visibility", 40, true,
				CableInsulationScenarios::recipesAndVisibility);
		registerTest(event, "mod299_advanced_circuit_recipes_and_visibility", 40, true,
				AdvancedCircuitScenarios::recipesAndVisibility);
		registerTest(event, "mod260_energized_bare_only", 80, true,
				CableShockScenarios::energizedBareOnly);
		registerTest(event, "tc_brk001_nrg01_open_breaker_cuts_the_line", 80, true,
				CableBreakerScenarios::tcBrk001Nrg01_openBreakerCutsTheLine);
		registerTest(event, "tc_brk001_sta01_open_breaker_survives_cable_ticks", 80, true,
				CableBreakerScenarios::tcBrk001Sta01_openBreakerSurvivesCableTicks);
		registerTest(event, "tc_brk001_sec01_open_breaker_disarms_the_shock", 80, true,
				CableBreakerScenarios::tcBrk001Sec01_openBreakerDisarmsTheShock);
		registerTest(event, "tc_brk001_fun01_removing_breaker_restores_the_line", 80, true,
				CableBreakerScenarios::tcBrk001Fun01_removingBreakerRestoresTheLine);
		registerTest(event, "tc_brk001_vis01_open_breaker_gaps_the_run", 80, true,
				CableBreakerScenarios::tcBrk001Vis01_openBreakerGapsTheRun);
		registerTest(event, "mod260_retained_buffer_is_safe", 80, true,
				CableShockScenarios::retainedBufferIsSafe);
		registerTest(event, "mod269_proximity_radius_respects_cover_and_config", 80, true,
				CableShockScenarios::proximityRadiusRespectsCoverAndConfig);
		registerTest(event, "mod279_shock_guard_gates_shock_and_opens_grace_window", 80, true,
				CableShockScenarios::shockGuardGatesShockAndOpensGraceWindow);
		registerTest(event, "mod279_shock_guard_shields_from_the_side", 80, true,
				CableShockScenarios::shockGuardShieldsFromTheSide);
		registerTest(event, "mod279_shock_guard_install_rules", 80, true,
				CableShockScenarios::shockGuardInstallRules);
		registerTest(event, "mod279_shock_guard_pops_when_down_connection_appears", 80, true,
				CableShockScenarios::shockGuardPopsWhenDownConnectionAppears);
		registerTest(event, "in_place_grade_swap_rebuilds_segment", 40, true,
				CableEnergyScenarios::inPlaceGradeSwapRebuildsSegment);
		registerTest(event, "mod314_cascade_charges_empty_battery_box_over_cable", 60, true,
				StorageEnergyScenarios::cascadeChargesEmptyBatteryBoxOverCable);
		registerTest(event, "mod314_cascade_stops_at_equilibrium", 200, true,
				StorageEnergyScenarios::cascadeStopsAtEquilibrium);
		registerTest(event, "mod314_cascade_charges_mid_bus_battery_box", 100, true,
				StorageEnergyScenarios::cascadeChargesMidBusBatteryBox);
		registerTest(event, "source_fills_cable_without_consumer", 40, true,
				CableEnergyScenarios::sourceFillsCableWithoutConsumer);
		registerTest(event, "storage_charges_through_line", 100, true,
				StorageEnergyScenarios::storageChargesThroughLine);
		registerTest(event, "storage_charges_with_cabled_output_face", 100, true,
				StorageEnergyScenarios::storageChargesWithCabledOutputFace);
		registerTest(event, "storage_charges_past_idle_producer", 200, true,
				StorageEnergyScenarios::storageChargesPastIdleProducer);
		// MOD-252: the flow field is seeded from demand, not from the sources. Without it a source fences
		// off every source behind it (the arm case), and a base with no live generator reopens the MOD-214
		// seam through the storage-only hole in the live-supply set.
		registerTest(event, "mod252_far_source_behind_near_source_discharges", 80, true,
				CableEnergyScenarios::mod252FarSourceBehindNearSourceDischarges);
		registerTest(event, "mod252_vertical_source_groups_both_discharge", 160, true,
				CableEnergyScenarios::mod252VerticalSourceGroupsBothDischarge);
		registerTest(event, "mod252_two_consumers_on_both_sides_of_source", 160, true,
				CableEnergyScenarios::mod252TwoConsumersOnBothSidesOfSource);
		// MOD-252 (review): seeding is reachability, not priority — a machine on one side of the source must
		// not fence off the Battery Box on the other.
		registerTest(event, "mod252_machine_and_storage_on_both_sides_of_source", 200, true,
				CableEnergyScenarios::mod252MachineAndStorageOnBothSidesOfSource);
		registerTest(event, "mod254_every_source_feeds_a_saturated_line", 80, true,
				CableEnergyScenarios::mod254EverySourceFeedsASaturatedLine);
		registerTest(event, "mod254_fork_feeds_both_branches", 200, true,
				CableEnergyScenarios::mod254ForkFeedsBothBranches);
		registerTest(event, "mod252_base_without_live_generator_feeds_machine", 200, true,
				StorageEnergyScenarios::mod252BaseWithoutLiveGeneratorFeedsMachine);
		// MOD-255: a dual-role Battery Box (wired on both faces into ONE network) must not drink back its
		// own discharge, and must not circulate it when nothing needs powering.
		registerTest(event, "mod255_dual_role_battery_feeds_machine_through_line", 200, true,
				StorageEnergyScenarios::mod255DualRoleBatteryFeedsMachineThroughLine);
		registerTest(event, "mod255_dual_role_battery_holds_charge_without_consumers", 80, true,
				StorageEnergyScenarios::mod255DualRoleBatteryHoldsChargeWithoutConsumers);
		registerTest(event, "lone_storage_source_sleeps", 40, true,
				StorageEnergyScenarios::loneStorageSourceSleeps);
		registerTest(event, "mod009_battery_box_charges_to_full", 80, true,
				StorageEnergyScenarios::mod009BatteryBoxChargesToFull);
		// MOD-021 cable-loss on NeoForge: REQUIRED regression guard. This world lane surfaced (and the
		// adapter fix in 7e673dc7 / MOD-022 closed) a NeoForge-only energy-core defect the JUnit adapter
		// tests could not: NeoForgeEnergyLookup.find() had hardcoded EnergyRole.BOTH, and
		// NeoForgeEnergyPort.supportsInsertion/Extraction derived from that role, so EVERY face reported
		// supporting BOTH insertion and extraction regardless of its real per-face role. EnergyNetwork.
		// refreshEndpoints therefore classified every storage/consumer as a producer too, so
		// computeProducerField seeded cable-distance 1 at every consumer and the MOD-021 loss floored
		// to 0 (the box gained a full lossless 32 EU/tick). insert()/extract() still gated correctly on
		// role via the block-side FaceEnergyPort, so delivery worked — only the classification-only
		// supports* predicates were wrong. Root cause: the per-face role was lost across the
		// Capabilities.Energy.BLOCK round-trip (EnergyHandler had no supports* predicate), fixed in the
		// NeoForge energy adapter. The scenario body now guards against regression (fails on lossless
		// delivery), so a reappearance of the defect will fail this required test. Fabric's
		// tcCable001Nrg02 passes because EnergyStorage.supports* reflect the real per-face capability.
		registerTest(event, "mod021_loss_over_ten_cables", 100, true,
				CableEnergyScenarios::mod021LossOverTenCables);
		registerTest(event, "mod253_long_copper_line_still_delivers_and_tops_off", 200, true,
				CableEnergyScenarios::mod253LongCopperLineStillDeliversAndTopsOff);
		registerTest(event, "nbt_round_trip_preserves_state", 40, true,
				MachineEnergyScenarios::nbtRoundTripPreservesState);
		// MOD-025: ring network union-find merge on cycle, proven on both loaders (loader-neutral body
		// in common/.../CableEnergyScenarios — NetworkManager/EnergyNetwork are shared, not per-loader).
		registerTest(event, "ring_network_merges_on_close", 200, true,
				CableEnergyScenarios::ringNetworkMergesOnClose);
		// Phase-03 LV expansion: wind mill world-tested on NeoForge (passive LV generator, cable-less
		// push path). Loader-neutral body in common/.../GeneratorEnergyScenarios.
		registerTest(event, "wind_mill_charges_adjacent_box", 40, true,
				GeneratorEnergyScenarios::windMillChargesAdjacentBox);
		// MOD-179: water mill on the NeoForge lane — the wheel gate and the clearance stall for two
		// mills placed face-to-face with no gap (the audit-found AABB blind spot). Deep per-case
		// coverage stays on the Fabric lane (WaterMillWheelGameTest); these prove loader parity.
		registerTest(event, "water_mill_wheel_gate", 40, true,
				GeneratorEnergyScenarios::waterMillWheelGate);
		registerTest(event, "water_mill_adjacent_face_to_face_stalls", 40, true,
				GeneratorEnergyScenarios::waterMillAdjacentFaceToFaceStalls);
		// MOD-189: wheel wear — a producing mill breaks a spent wheel (loader-parity smoke; also asserts
		// the NeoForge-registered water_mill_wheel is a durability item). Deep coverage on the Fabric lane.
		registerTest(event, "water_mill_wheel_wears_out", 40, true,
				GeneratorEnergyScenarios::waterMillWheelWearsOut);

		// MOD-028: fluid multiloader — proves the FluidPort/FluidTank abstraction works end to end on
		// NeoForge (Capabilities.Fluid.BLOCK resolves, transactions commit, lava becomes EU).
		registerTest(event, "fluid_source_to_pump_to_geo_to_eu", 40, true,
				CoreFluidScenarios::sourceToPumpToGeoToEu);

		// MOD-077: geothermal lava-input parity (capsule in the GUI slot, vanilla lava bucket via the
		// RightClickBlock interception helper, full-tank no-op) + lava-capsule furnace fuel (the two
		// common furnace mixins). Proves the mixins actually apply on the NeoForge lane.
		registerTest(event, "geothermal_capsule_in_slot_drains", 40, true,
				GeothermalLavaInputScenarios::fun05CapsuleInSlotDrains);
		registerTest(event, "geothermal_bucket_deposit_via_shift", 40, true,
				GeothermalLavaInputScenarios::fun06BucketDepositViaShift);
		registerTest(event, "geothermal_bucket_full_tank_no_op", 40, true,
				GeothermalLavaInputScenarios::neg06BucketFullTankNoOp);
		registerTest(event, "lava_capsule_is_furnace_fuel", 40, true,
				GeothermalLavaInputScenarios::fun04LavaCapsuleIsFurnaceFuel);
		registerTest(event, "furnace_fuel_slot_caps_lava_capsule", 40, true,
				GeothermalLavaInputScenarios::fun05FurnaceFuelSlotCapsOne);

		// Machine processing negatives — shared MachineBlockEntity loop on the NeoForge lane. These close
		// the coverage gap with the Fabric MachineGameTest (no-power, full-output jam, input-swap reset),
		// catching dupe/overflow/progress-reset regressions on this loader's processing path.
		registerTest(event, "machine_no_power_no_output", 420, true,
				MachineEnergyScenarios::machineNoPowerNoOutput);
		registerTest(event, "machine_full_output_jams", 420, true,
				MachineEnergyScenarios::machineFullOutputJams);
		registerTest(event, "machine_input_swap_resets_progress", 200, true,
				MachineEnergyScenarios::machineInputSwapResetsProgress);

		// Battery box rate BVA: the buffer's per-tick insert/extract caps must be EXACTLY LV.maxVoltage().
		// Loader-neutral (asserts the shared EnergyBuffer fields directly), complements the Fabric lane's
		// capability-view rate tests (BatteryBoxGameTest PRF03/Prf04).
		registerTest(event, "battery_box_rate_exact_lv", 40, true,
				StorageEnergyScenarios::batteryBoxRateExactLv);

		// Solar panel day/night generation (R-NRG-15). The day case asserts the exact rate × ticks; the
		// night case asserts 0 EU (a brightness-read regression that leaks day gen into night fails here).
		registerTest(event, "solar_panel_generates_by_day", 60, true,
				GeneratorEnergyScenarios::solarPanelGeneratesByDay);
		registerTest(event, "solar_panel_no_eu_at_night", 60, true,
				GeneratorEnergyScenarios::solarPanelNoEuAtNight);

		// Geothermal generator: exact EU rate from a lava bucket (catches a halved/doubled conversion
		// factor) + no-lava negative (catches unconditional generation).
		registerTest(event, "geothermal_lava_bucket_rate", 60, true,
				GeneratorEnergyScenarios::geothermalLavaBucketRate);
		registerTest(event, "geothermal_no_lava_no_eu", 60, true,
				GeneratorEnergyScenarios::geothermalNoLavaNoEu);

		// Cable network negatives: diagonal cables must not merge (R-CON-06); a vanilla furnace neighbour
		// must not NPE during endpoint discovery (loader-capability null-safety).
		registerTest(event, "diagonal_cables_do_not_connect", 40, true,
				CableEnergyScenarios::diagonalCablesDoNotConnect);
		registerTest(event, "cable_vanilla_neighbor_no_npe", 100, true,
				CableEnergyScenarios::cableVanillaNeighborNoNpe);

		// NBT persistence round-trip for the electric furnace (MachineBlockEntity save/load path on the
		// NeoForge lane for a machine other than the macerator already covered above).
		registerTest(event, "furnace_nbt_round_trip", 40, true,
				MachineEnergyScenarios::furnaceNbtRoundTrip);

		// Wind mill: thunder multiplies the rate on a raised rig (weather wiring end-to-end).
		registerTest(event, "wind_mill_thunder_multiplies_rate", 120, true,
				GeneratorEnergyScenarios::windMillThunderMultipliesRate);

		// MOD-356: the GUI readout must equal what the buffer actually gains under a retuned
		// globalEuRateMultiplier — and on the wind mill, channel 2 must stay the mechanical rate the
		// rotor renderer spins on.
		registerTest(event, "solar_panel_readout_matches_buffer_gain", 60, true,
				GeneratorEnergyScenarios::solarPanelReadoutMatchesBufferGain);
		registerTest(event, "wind_mill_readout_matches_buffer_gain", 120, true,
				GeneratorEnergyScenarios::windMillReadoutMatchesBufferGain);
		registerTest(event, "t2_wind_mill_readouts_match_buffer_gain", 120, true,
				GeneratorEnergyScenarios::t2WindMillReadoutsMatchBufferGain);

		// Generator: full buffer pauses burn (R-NRG-11, no fuel waste) + exact EU/t rate (canon 8).
		registerTest(event, "generator_full_buffer_pauses_burn", 40, true,
				GeneratorEnergyScenarios::generatorFullBufferPausesBurn);
		registerTest(event, "generator_rate_per_tick_matches_config", 40, true,
				GeneratorEnergyScenarios::generatorRatePerTickMatchesConfig);

		// Battery box conservation: genDrain == boxGain on a partial-consumer cable path (no leak).
		registerTest(event, "battery_box_conservation_partial_consumer", 80, true,
				StorageEnergyScenarios::batteryBoxConservationPartialConsumer);

		// Moonlit panel: night generation at exact moonlitEuPerTick × multiplier.
		registerTest(event, "moonlit_panel_generates_at_night", 60, true,
				GeneratorEnergyScenarios::moonlitPanelGeneratesAtNight);

		// Machine positive recipes: compressor (dust->ingot), extractor (gravel->flint), furnace
		// (raw_iron->ingot). Each proves that machine's recipe-type resolves on the NeoForge lane.
		registerTest(event, "compressor_makes_copper_ingot", 420, true,
				MachineEnergyScenarios::compressorMakesCopperIngot);
		registerTest(event, "extractor_makes_flint", 420, true,
				MachineEnergyScenarios::extractorMakesFlint);
		registerTest(event, "furnace_smelts_raw_iron", 420, true,
				MachineEnergyScenarios::furnaceSmeltsRawIron);

		// MOD-290: recipes now name c: tags. An empty tag makes a recipe load fine and never match,
		// and the tags are supplied by the loader — so this has to be asserted on the NeoForge lane too.
		registerTest(event, "consumed_common_tags_are_non_empty", 40, true,
				RecipeTagScenarios::consumedCommonTagsAreNonEmpty);
		registerTest(event, "every_mod_recipe_ingredient_resolves", 40, true,
				RecipeTagScenarios::everyModRecipeIngredientResolves);
		registerTest(event, "referenced_foreign_tags_resolve", 40, true,
				RecipeTagScenarios::referencedForeignTagsResolve);

		// MOD-287: the modular warehouse. Which modules merge is pure common logic, but it has to hold
		// on both lanes — the block entity is registered separately per loader.
		registerTest(event, "storage_face_adjacent_merges", 40, true,
				StorageClusterScenarios::faceAdjacentMergesDiagonalDoesNot);
		registerTest(event, "storage_fifth_module_adds_nothing", 40, true,
				StorageClusterScenarios::fifthModuleAddsNothing);
		registerTest(event, "storage_items_stay_in_their_own_module", 40, true,
				StorageClusterScenarios::itemsStayInTheirOwnModule);
		registerTest(event, "storage_breaking_middle_module_splits", 40, true,
				StorageClusterScenarios::breakingMiddleModuleSplitsWithoutLoss);
		registerTest(event, "storage_module_exposes_item_port", 40, true,
				StorageClusterScenarios::moduleExposesItemPortOnEveryFace);
		// The seam flags are written by a hand-rolled refresh walk on block place/break, so they are
		// world behaviour, not pure logic — both lanes have to see them.
		registerTest(event, "storage_seams_match_cluster_membership", 40, true,
				StorageClusterScenarios::seamsMatchClusterMembership);
		registerTest(event, "storage_seams_join_wall_not_diagonal", 40, true,
				StorageClusterScenarios::seamsJoinAWallAndNeverADiagonal);
		registerTest(event, "storage_window_shows_six_rows_and_scrolls", 40, true,
				StorageClusterScenarios::windowShowsSixRowsAndScrolls);
		registerTest(event, "storage_shift_click_reaches_scrolled_away_rows", 40, true,
				StorageClusterScenarios::shiftClickReachesScrolledAwayRows);
		registerTest(event, "storage_capacity_never_goes_negative", 40, true,
				StorageClusterScenarios::capacityNeverGoesNegative);
		registerTest(event, "storage_unloaded_position_is_skipped", 40, true,
				StorageClusterScenarios::unloadedPositionIsSkippedNotForceLoaded);
		registerTest(event, "storage_pipe_fills_and_drains_warehouse", 100, true,
				StorageClusterScenarios::pipeFillsAndDrainsTheWarehouse);
		registerTest(event, "storage_module_contents_survive_reload", 40, true,
				StorageClusterScenarios::moduleContentsSurviveReload);

		// MOD-275: the assembler. Every case is a documented failure of a comparable machine in another
		// mod, and each has to hold on both lanes — the block entity, the menu and the craft-remainder
		// hook are all registered per loader, and the remainder hook in particular is a different
		// mechanism on each (Fabric mixin vs NeoForge patch).
		registerTest(event, "assembler_balance_numbers", 40, true,
				AssemblerScenarios::con01BalanceNumbers);
		registerTest(event, "assembler_one_operation_costs_one_operation", 40, true,
				AssemblerScenarios::fun01OneOperationCostsExactlyOneOperation);
		registerTest(event, "assembler_no_eu_without_materials", 40, true,
				AssemblerScenarios::neg01NoEuWithoutMaterials);
		registerTest(event, "assembler_no_eu_when_output_full", 40, true,
				AssemblerScenarios::neg02NoEuWhenOutputFull);
		registerTest(event, "assembler_output_area_uses_every_slot", 40, true,
				AssemblerScenarios::fun02OutputAreaUsesEverySlot);
		registerTest(event, "assembler_hammer_remainder_returns_home", 40, true,
				AssemblerScenarios::fun03HammerRemainderReturnsToItsOwnSlot);
		registerTest(event, "assembler_self_returning_ingredient_terminates", 40, true,
				AssemblerScenarios::neg03SelfReturningIngredientTerminates);
		registerTest(event, "assembler_queue_skips_starved_blueprint", 40, true,
				AssemblerScenarios::fun04QueueSkipsStarvedBlueprint);
		registerTest(event, "assembler_queue_rotates_between_blueprints", 40, true,
				AssemblerScenarios::fun05QueueRotatesBetweenBlueprints);
		registerTest(event, "assembler_missing_recipe_stops_without_crashing", 40, true,
				AssemblerScenarios::neg04MissingRecipeStopsWithoutCrashing);
		registerTest(event, "assembler_two_machines_share_one_warehouse", 40, true,
				AssemblerScenarios::fun06TwoAssemblersShareOneWarehouse);
		registerTest(event, "assembler_blueprint_tooltip_names_its_result", 40, true,
				AssemblerScenarios::gui01BlueprintTooltipNamesItsResult);
		registerTest(event, "assembler_window_shows_the_active_blueprint", 40, true,
				AssemblerScenarios::gui02WindowShowsTheActiveBlueprint);
		registerTest(event, "assembler_active_blueprint_slot_is_synced", 40, true,
				AssemblerScenarios::gui03ActiveBlueprintSlotIsSynced);
		registerTest(event, "assembler_blueprint_icon_is_wired_to_the_cached_result", 40, true,
				AssemblerScenarios::gui04BlueprintIconIsWiredToTheCachedResult);
		registerTest(event, "assembler_craft_fills_nine_cells", 40, true,
				AssemblerScenarios::con02CraftFillsNineCells);
		registerTest(event, "assembler_hidden_tab_slots_are_unreachable", 40, true,
				AssemblerScenarios::tab01HiddenTabSlotsAreUnreachable);
		registerTest(event, "assembler_recording_does_not_stop_production", 40, true,
				AssemblerScenarios::tab02RecordingDoesNotStopProduction);
		registerTest(event, "assembler_write_belongs_to_the_record_tab", 40, true,
				AssemblerScenarios::tab03WriteBelongsToTheRecordTab);

		registerTest(event, "assembler_substitution_off_by_default", 40, true,
				AssemblerScenarios::sub01OffByDefaultOnWhenToggled);
		registerTest(event, "assembler_substitution_prefers_recorded_item", 40, true,
				AssemblerScenarios::sub02RecordedItemWins);
		registerTest(event, "assembler_substitution_only_this_recipes_ingredient", 40, true,
				AssemblerScenarios::sub03OnlyThisRecipesIngredient);
		registerTest(event, "assembler_substitution_validated_by_reassembly", 40, true,
				AssemblerScenarios::sub04ValidatedByReassembly);
		registerTest(event, "assembler_substitution_button_channel_routes", 40, true,
				AssemblerScenarios::sub05ButtonChannelRoutesToSubstitution);
		registerTest(event, "assembler_breaking_drops_queue_and_output", 40, true,
				AssemblerScenarios::brk01BreakingTheMachineDropsQueueAndOutput);
		registerTest(event, "assembler_state_survives_reload", 40, true,
				AssemblerScenarios::per01AssemblerStateSurvivesReload);

		// MOD-290 cross-mod lane: the fake foreign mod's tin, reaching our tags and our recipes.
		registerTest(event, "foreign_material_reaches_our_tags", 40, true,
				ForeignMaterialScenarios::tags04ForeignItemReachesOurTags);
		registerTest(event, "foreign_ore_is_macerated", 100, true,
				ForeignMaterialScenarios::tags05MaceratorGrindsForeignOre);
		registerTest(event, "foreign_ingot_feeds_our_craft", 40, true,
				ForeignMaterialScenarios::tags06ForeignIngotFeedsOurCraft);

		// MOD-275/MOD-287 — the warehouse tick-cost measurement. maxTicks is generous: the body runs
		// three 2200-tick phases inside a single game tick, so the gametest clock is not what bounds it.
		registerTest(event, "assembler_full_warehouse_tick_cost", 100, true,
				AssemblerPerfScenarios::perf01FullWarehouseTickCost);

		// Pump: source -> tank -> sink (geo) -> EU. Fluid transport end-to-end on the NeoForge lane.
		registerTest(event, "pump_source_to_tank_to_sink_to_eu", 100, true,
				WorldContentScenarios::pumpSourceToTankToSinkToEu);

		// Machine negatives parametric across all 4 machines (furnace/compressor/extractor): no-recipe
		// no-EU, full-output jam, input-swap reset. Closes the per-machine gap with MachineGameTest.
		registerTest(event, "furnace_non_recipe_no_eu_spent", 200, true,
				MachineEnergyScenarios::furnaceNonRecipeNoEuSpent);
		registerTest(event, "compressor_full_output_jams", 420, true,
				MachineEnergyScenarios::compressorFullOutputJams);
		registerTest(event, "extractor_input_swap_resets_progress", 200, true,
				MachineEnergyScenarios::extractorInputSwapResetsProgress);

		// Daylight solar: day generation at exact daylightEuPerTick + no EU at night (day-only).
		registerTest(event, "daylight_panel_generates_by_day", 60, true,
				GeneratorEnergyScenarios::daylightPanelGeneratesByDay);
		registerTest(event, "daylight_panel_no_eu_at_night", 60, true,
				GeneratorEnergyScenarios::daylightPanelNoEuAtNight);

		// Extra machine recipes: extractor ×3 blaze_powder, furnace vanilla fallback (sand→glass),
		// compressor iron_dust→iron_ingot.
		registerTest(event, "extractor_blaze_rod_to_powder", 420, true,
				MachineEnergyScenarios::extractorBlazeRodToPowder);
		registerTest(event, "furnace_smelts_sand_to_glass", 420, true,
				MachineEnergyScenarios::furnaceSmeltsSandToGlass);
		registerTest(event, "compressor_iron_dust_to_ingot", 420, true,
				MachineEnergyScenarios::compressorIronDustToIngot);

		// Machine E_op BVA: macerator exact (completes, amount==0) + E_op−1 (stalls at duration−1).
		registerTest(event, "macerator_eop_exact_completes", 420, true,
				MachineEnergyScenarios::maceratorEopExactCompletes);
		registerTest(event, "macerator_eop_minus_one_stalls", 420, true,
				MachineEnergyScenarios::maceratorEopMinusOneStalls);

		// Generator/solar buffer cap-1 BVA (R-NRG-01): tops off to exactly cap from cap-1.
		registerTest(event, "generator_buffer_caps_at_max_bva", 40, true,
				GeneratorEnergyScenarios::generatorBufferCapsAtMaxBva);
		registerTest(event, "solar_panel_buffer_caps_at_max_bva", 60, true,
				GeneratorEnergyScenarios::solarPanelBufferCapsAtMaxBva);

		// Solar evolution: day chip → daylight panel after solarEvolveTicks.
		registerTest(event, "solar_day_chip_evolves_to_daylight", 200, true,
				GeneratorEnergyScenarios::solarDayChipEvolvesToDaylight);

		// MOD-310: the two former ore mirrors (ore_wooden_pickaxe_no_drop / ore_stone_pickaxe_tier_gate)
		// are gone — the shared OreScenarios::tcOre001Brk02_pickaxeTierGate strictly subsumes them
		// (same assertions plus sulfur, the deepslate variants, and diamond/netherite/gold).

		// Network split + rejoin (R-CON-04, R-CON-09): break stops delivery, replace resumes flow.
		registerTest(event, "network_split_rejoin_resumes_flow", 200, true,
				CableEnergyScenarios::networkSplitRejoinResumesFlow);

		// Geothermal tank droplet↔MB boundary: one lava bucket = exactly 1000 mB.
		registerTest(event, "geothermal_tank_bucket_boundary", 40, true,
				GeneratorEnergyScenarios::geothermalTankBucketBoundary);

		// Generator rejects external EU (producer-only, R-NRG-03): maxInsert == 0.
		registerTest(event, "generator_rejects_external_eu", 40, true,
				GeneratorEnergyScenarios::generatorRejectsExternalEu);

		// Battery box half-charge drop carries EU (R-BRK-07, second charge value).
		registerTest(event, "battery_box_drop_carries_energy_half_charge", 40, true,
				StorageEnergyScenarios::batteryBoxDropCarriesEnergyHalfCharge);

		// Cable throughput cap ≤ LV.maxVoltage per tick (R-NRG-04, no overvoltage).
		registerTest(event, "cable_throughput_capped_at_lv", 40, true,
				CableEnergyScenarios::cableThroughputCappedAtLv);

		// Macerator lit blockstate tracks active/idle (R-VIS-01).
		registerTest(event, "macerator_lit_tracks_active", 420, true,
				MachineEnergyScenarios::maceratorLitTracksActive);

		// Extractor multi-output recipes: cactus ×2 green_dye, pumpkin ×5 pumpkin_seeds.
		registerTest(event, "extractor_cactus_to_green_dye", 420, true,
				MachineEnergyScenarios::extractorCactusToGreenDye);
		registerTest(event, "extractor_pumpkin_to_seeds", 420, true,
				MachineEnergyScenarios::extractorPumpkinToSeeds);

		// Macerator tag-ingredient recipe: iron_ore → ×2 iron_dust (doubling path).
		registerTest(event, "macerator_iron_ore_doubles_dust", 420, true,
				MachineEnergyScenarios::maceratorIronOreDoublesDust);

		// Wind mill roofed → 0 EU (open-sky gate, mode wiring).
		registerTest(event, "wind_mill_roofed_yields_zero", 120, true,
				GeneratorEnergyScenarios::windMillRoofedYieldsZero);

		// Two generators sum into one consumer (R-CON-16, no dupe/shadow).
		registerTest(event, "two_generators_sum_into_one_consumer", 60, true,
				CableEnergyScenarios::twoGeneratorsSumIntoOneConsumer);

		// MOD-057: tempered-iron tools are in vanilla membership tags → enchanting table accepts them.
		// Loader-neutral bodies in TemperedIronToolScenarios (common); guards the data/minecraft/tags/item
		// JSON fix against regression. Mirrors Fabric TemperedIronToolsGameTest.tcTi001/tcTi002.
		registerTest(event, "tempered_iron_tool_membership_tags", 40, true,
				TemperedIronToolScenarios::toolMembershipTags);
		registerTest(event, "tempered_iron_enchantment_accepted", 40, true,
				TemperedIronToolScenarios::enchantmentAccepted);

		// Battery Pouch (MOD-052, TC-POUCH-001) — same loader-neutral bodies as the Fabric PouchGameTest
		// suite, so the pouch components / weight math / EU lock / Battery Box charge slot are proven
		// on the NeoForge frozen-registry path too.
		registerTest(event, "pouch_insert_requires_energy", 40, true, PouchScenarios::fun01InsertRequiresEnergy);
		registerTest(event, "pouch_capacity_leftover", 40, true, PouchScenarios::fun02CapacityLeftover);
		registerTest(event, "pouch_weight_math", 40, true, PouchScenarios::fun03WeightMath);
		registerTest(event, "pouch_remove_lifo", 40, true, PouchScenarios::fun04RemoveLifo);
		registerTest(event, "pouch_passive_drain", 40, true, PouchScenarios::fun05PassiveDrain);
		registerTest(event, "pouch_no_drain_when_empty", 40, true, PouchScenarios::fun06NoDrainWhenEmpty);
		registerTest(event, "pouch_no_drain_in_creative", 40, true, PouchScenarios::fun05bNoDrainInCreative);
		registerTest(event, "pouch_charge_in_battery_box", 80, true, PouchScenarios::fun07ChargeInBatteryBox);
		registerTest(event, "pouch_merge_on_insert", 40, true, PouchScenarios::fun08MergeOnInsert);

		// Guide Book (MOD-067, TC-GUIDE-001) — auto-give ledger; same body as the Fabric GuideBookGameTest.
		registerTest(event, "guide_book_give_once", 40, true, GuideBookGiverScenarios::giveOnce);

		// Stock Display Frame (MOD-066, TC-FRAME-001) — same loader-neutral bodies as the Fabric suite.
		registerTest(event, "stock_frame_counts_whole_container", 60, true, StockDisplayFrameScenarios::fun01CountsWholeContainer);
		registerTest(event, "stock_frame_filter_counts_matching", 60, true, StockDisplayFrameScenarios::fun02FilterCountsOnlyMatching);
		registerTest(event, "stock_frame_double_chest_combined", 60, true, StockDisplayFrameScenarios::fun03DoubleChestCombined);
		registerTest(event, "stock_frame_updates_after_change", 60, true, StockDisplayFrameScenarios::fun04UpdatesAfterChange);
		registerTest(event, "stock_frame_no_container", 60, true, StockDisplayFrameScenarios::fun05NoContainer);
		registerTest(event, "stock_frame_drops_own_item", 60, true, StockDisplayFrameScenarios::fun06DropsOwnItem);
		registerTest(event, "pouch_tooltip_image", 40, true, PouchScenarios::fun09TooltipImage);
		registerTest(event, "pouch_no_pouch_in_pouch", 40, true, PouchScenarios::neg01NoPouchInPouch);
		registerTest(event, "pouch_hopper_cut_off", 40, true, PouchScenarios::neg02HopperCutOff);
		registerTest(event, "pouch_drain_floors_and_locks", 40, true, PouchScenarios::neg03DrainFloorsAndLocks);
		registerTest(event, "pouch_menu_slot_filter", 40, true, PouchScenarios::neg04MenuSlotFilter);
		registerTest(event, "pouch_charge_rate_cap", 40, true, PouchScenarios::prf01ChargeRateCap);
		registerTest(event, "pouch_no_overcharge", 40, true, PouchScenarios::prf02NoOvercharge);
		registerTest(event, "pouch_contents_round_trip", 40, true, PouchScenarios::per01ContentsRoundTrip);
		registerTest(event, "pouch_energy_semantics", 40, true, PouchScenarios::per02EnergySemantics);

		// Energy Pack (MOD-065, TC-PACK-001) — same loader-neutral bodies as the Fabric EnergyPackGameTest
		// suite: worn-pack transfer, the offhand pass, the anti-loop filter, the charge slot, persistence.
		registerTest(event, "pack_worn_charges_pouch", 40, true, EnergyPackScenarios::fun01WornPackChargesPouch);
		registerTest(event, "pack_charges_offhand", 40, true, EnergyPackScenarios::fun02ChargesOffhand);
		registerTest(event, "pack_budget_split", 40, true, EnergyPackScenarios::fun03BudgetSplitAcrossConsumers);
		registerTest(event, "pack_charge_in_battery_box", 80, true, EnergyPackScenarios::fun04ChargeInBatteryBox);
		registerTest(event, "pack_does_not_charge_pack", 40, true, EnergyPackScenarios::fun05PackDoesNotChargePack);
		// Jetpack (MOD-148, TC-JET-001) — same loader-neutral bodies as the Fabric JetpackGameTest
		// suite: EU burn per thrust tick, the powerless glide, the grounded/ceiling gates, charging.
		registerTest(event, "jetpack_thrust_burns_eu", 40, true, JetpackScenarios::fun01ThrustBurnsEuAndZeroesFall);
		registerTest(event, "jetpack_released_jump_burns_nothing", 40, true, JetpackScenarios::fun02ReleasedJumpBurnsNothing);
		registerTest(event, "jetpack_drained_glide", 40, true, JetpackScenarios::fun03DrainedGlideStillZeroesFall);
		registerTest(event, "jetpack_charge_in_battery_box", 80, true, JetpackScenarios::fun04ChargeInBatteryBox);
		registerTest(event, "jetpack_worn_pack_charges_jetpack", 40, true, JetpackScenarios::fun05WornPackChargesJetpack);
		registerTest(event, "jetpack_worn_asset_follows_charge", 40, true, JetpackScenarios::fun06WornAssetFollowsCharge);
		registerTest(event, "jetpack_creative_burns_nothing", 40, true, JetpackScenarios::neg01CreativeBurnsNothing);
		registerTest(event, "jetpack_on_ground_burns_nothing", 40, true, JetpackScenarios::neg02OnGroundBurnsNothing);
		registerTest(event, "jetpack_above_ceiling_refuses_thrust", 40, true, JetpackScenarios::neg03AboveCeilingRefusesThrust);
		registerTest(event, "jetpack_tail_charge_drains_to_zero", 40, true, JetpackScenarios::fun07TailChargeDrainsToZero);
		registerTest(event, "jetpack_empty_falls_normally", 40, true, JetpackScenarios::neg04EmptyJetpackFallsNormally);
		registerTest(event, "jetpack_flight_glow_placed_and_swept", 40, true, JetpackScenarios::fun08FlightGlowPlacedAndSwept);

		// MOD-084: the NeoForge half of the cross-mod bridge. Loader-specific bodies (they call
		// Capabilities.Energy.ITEM), mirroring the Fabric lane's ItemEnergyCapabilityGameTest.
		registerTest(event, "xmod_foreign_charger_fills_pouch", 40, true,
				ItemEnergyCapabilityScenarios::fun01ForeignChargerFillsPouch);
		registerTest(event, "xmod_foreign_machine_cannot_drain", 40, true,
				ItemEnergyCapabilityScenarios::neg01ForeignMachineCannotDrain);
		registerTest(event, "xmod_pack_charges_foreign_item", 40, true,
				ItemEnergyCapabilityScenarios::fun02PackChargesForeignItem);
		// MOD-372: the roster guard — every powered item of the mod must be reachable through
		// Capabilities.Energy.ITEM, not just the ones someone remembered to add to the literal list.
		registerTest(event, "xmod_every_powered_item_exposes_capability", 40, true,
				ItemEnergyCapabilityScenarios::reg01EveryPoweredItemExposesCapability);
		registerTest(event, "pack_worn_asset_follows_charge", 40, true, EnergyPackScenarios::fun06WornAssetFollowsCharge);
		registerTest(event, "pack_inventory_tick_transfers", 60, true, EnergyPackScenarios::fun07InventoryTickDrivesTransfer);
		registerTest(event, "pack_component_charge_fixes_look", 40, true, EnergyPackScenarios::fun08ChargedByComponentFixesItsLook);
		registerTest(event, "pack_offhand_pack_not_charged", 40, true, EnergyPackScenarios::neg04PackInOffhandNotCharged);
		registerTest(event, "pack_no_transfer_when_idle", 40, true, EnergyPackScenarios::neg01NoTransferWhenNothingToDo);
		registerTest(event, "pack_floors_at_zero", 40, true, EnergyPackScenarios::neg02PackFloorsAtZero);
		registerTest(event, "pack_menu_slot_accepts_pack", 40, true, EnergyPackScenarios::neg03MenuSlotAcceptsPack);
		registerTest(event, "pack_creative_keeps_charge", 40, true, EnergyPackScenarios::fun09CreativeKeepsCharge);
		registerTest(event, "pack_charges_cursor_and_grid", 40, true, EnergyPackScenarios::fun10ChargesCursorAndCraftGrid);
		registerTest(event, "pack_skips_open_container", 40, true, EnergyPackScenarios::neg05DoesNotChargeOpenContainer);
		registerTest(event, "pack_charge_round_trip", 40, true, EnergyPackScenarios::per01ChargeRoundTrip);

		// Charging Station (MOD-274, TC-PAD-001) — same loader-neutral bodies as the Fabric
		// ChargePadGameTest suite: the transfer, worn gear, the tag and input-rate guards, the
		// indicator states, the idle release and the energy-face contract.
		registerTest(event, "pad_charges_carried_item", 40, true, ChargePadScenarios::fun01ChargesCarriedItem);
		registerTest(event, "pad_charges_worn_equipment", 40, true, ChargePadScenarios::fun02ChargesWornEquipment);
		registerTest(event, "pad_charges_pack_despite_tag", 40, true, ChargePadScenarios::fun03ChargesEnergyPackDespiteTag);
		registerTest(event, "pad_respects_input_rate", 40, true, ChargePadScenarios::fun04RespectsPerItemInputRate);
		registerTest(event, "pad_budget_shared_and_capped", 40, true, ChargePadScenarios::fun05BudgetIsSharedAndCapped);
		registerTest(event, "pad_armour_set_together", 40, true, ChargePadScenarios::fun12WholeArmourSetChargesTogether);
		registerTest(event, "pad_empty_reports_empty", 40, true, ChargePadScenarios::fun06EmptyStationReportsEmpty);
		registerTest(event, "pad_full_visitor_ready", 40, true, ChargePadScenarios::fun07FullVisitorReportsReady);
		registerTest(event, "pad_releases_after_leaving", 40, true, ChargePadScenarios::fun08ReleasesAfterVisitorLeaves);
		registerTest(event, "pad_spectator_ignored", 40, true, ChargePadScenarios::fun09SpectatorIsIgnored);
		registerTest(event, "pad_entity_inside_hook", 40, true, ChargePadScenarios::fun10EntityInsideHookReachesTheStation);
		registerTest(event, "pad_only_underfoot_serves", 40, true, ChargePadScenarios::fun11OnlyTheStationUnderfootServes);
		registerTest(event, "pad_faces_intake_only", 40, true, ChargePadScenarios::nrg01EveryFaceIsIntakeOnly);
		registerTest(event, "pad_buffer_persists", 40, true, ChargePadScenarios::nrg02BufferPersists);
		registerTest(event, "pad_takes_energy_from_neighbour", 40, true, ChargePadScenarios::nrg03TakesEnergyFromNeighbour);

		// Electric Drill (MOD-079, TC-DRILL-001) — same loader-neutral bodies as the Fabric
		// ElectricDrillGameTest suite: charge slot, EU drain, hand-speed fallback, pickaxe tags, enchants.
		registerTest(event, "drill_charge_in_battery_box", 80, true, ElectricDrillScenarios::fun01ChargeInBatteryBox);
		registerTest(event, "drill_drain_on_mine_block", 40, true, ElectricDrillScenarios::fun02DrainOnMineBlock);
		registerTest(event, "drill_no_drain_below_cost", 40, true, ElectricDrillScenarios::fun03NoDrainBelowCost);
		registerTest(event, "drill_speed_and_drops", 40, true, ElectricDrillScenarios::fun04SpeedAndDrops);
		registerTest(event, "drill_no_drain_on_zero_hardness", 40, true, ElectricDrillScenarios::fun05NoDrainOnZeroHardness);
		registerTest(event, "drill_tags_and_enchants", 40, true, ElectricDrillScenarios::fun06TagsAndEnchants);
		registerTest(event, "drill_charge_round_trip", 40, true, ElectricDrillScenarios::per01ChargeRoundTrip);

		// Electric Saber (MOD-149, TC-SABER-001) — same loader-neutral bodies as the Fabric
		// ElectricSaberGameTest suite: charge slot, EU per hit, on/off switch, charge-driven attributes,
		// sword tags + the WEAPON contract, and the discharge.
		registerTest(event, "saber_charge_in_battery_box", 80, true, ElectricSaberScenarios::fun01ChargeInBatteryBox);
		registerTest(event, "saber_drain_on_hit", 40, true, ElectricSaberScenarios::fun02DrainOnHit);
		registerTest(event, "saber_no_drain_below_cost", 40, true, ElectricSaberScenarios::fun03NoDrainBelowCost);
		registerTest(event, "saber_attributes_follow_charge", 40, true, ElectricSaberScenarios::fun04AttributesFollowCharge);
		registerTest(event, "saber_switched_off_spends_nothing", 40, true, ElectricSaberScenarios::fun05SwitchedOffSpendsNothing);
		registerTest(event, "saber_creative_spends_nothing", 40, true, ElectricSaberScenarios::fun06CreativeSpendsNothing);
		registerTest(event, "saber_tags_and_enchants", 40, true, ElectricSaberScenarios::fun07TagsAndEnchants);
		registerTest(event, "saber_shock_only_when_live", 40, true, ElectricSaberScenarios::fun08ShockOnlyWhenLive);
		// MOD-089 torch placement (TC-DRILL-001-FUN07/08/09): right-click places a torch from the inventory,
		// prefers the enriched uranium torch, and still consumes when the clicked block is replaceable.
		registerTest(event, "drill_place_torch_from_inventory", 40, true, ElectricDrillScenarios::fun07PlaceTorchFromInventory);
		registerTest(event, "drill_torch_priority_uranium", 40, true, ElectricDrillScenarios::fun08TorchPriorityUranium);
		registerTest(event, "drill_place_torch_on_replaceable_block", 40, true, ElectricDrillScenarios::fun09PlaceTorchOnReplaceableBlock);
		// MOD-097 (TC-DRILL-001-NEG01): a drill below the torch cost refuses to place instead of a freebie.
		registerTest(event, "drill_torch_refused_below_cost", 40, true, ElectricDrillScenarios::neg01TorchRefusedBelowCost);
		// MOD-321 (TC-DRILL-001-FUN10/11): the diamond-tipped upgrade digs faster at the same tier, and its
		// sneak-toggled Silk Touch mode flips the real loot-table drop both ways.
		registerTest(event, "drill_diamond_tip_speed_and_tier", 40, true, ElectricDrillScenarios::fun10DiamondTipSpeedAndTier);
		registerTest(event, "drill_diamond_tip_silk_toggle", 40, true, ElectricDrillScenarios::fun11DiamondTipSilkToggle);
		registerTest(event, "drill_upgrade_recipe_any_state", 40, true, ElectricDrillScenarios::fun12UpgradeRecipeAcceptsAnyDrillState);

		// MOD-374 (TC-CHAINSAW-001-FUN01..03): the chainsaw's diamond-tipped upgrade cuts faster at the
		// same tier, its sneak-toggled Silk Touch mode flips the real leaf loot table both ways, and the
		// base chainsaw stays free of the mode entirely.
		registerTest(event, "chainsaw_diamond_tip_speed_and_tier", 40, true, ElectricChainsawScenarios::fun01DiamondTipSpeedAndTier);
		registerTest(event, "chainsaw_diamond_tip_silk_toggle_leaves", 40, true, ElectricChainsawScenarios::fun02DiamondTipSilkToggleOnLeaves);
		registerTest(event, "chainsaw_base_has_no_silk_mode", 40, true, ElectricChainsawScenarios::fun03BaseChainsawHasNoSilkMode);

		// MOD-378 (TC-HOE-001-FUN01..04): the hoe's diamond-tipped upgrade breaks faster at the same tier
		// and hands every plot it tills full moisture, while the base hoe leaves plots dry and a flat
		// upgrade cannot water farmland it did not just create.
		registerTest(event, "hoe_diamond_tip_speed_and_tier", 40, true, ElectricHoeScenarios::fun01DiamondTipSpeedAndTier);
		registerTest(event, "hoe_diamond_tip_till_waters_plot", 40, true, ElectricHoeScenarios::fun02DiamondTipTillLeavesPlotWatered);
		registerTest(event, "hoe_base_leaves_plot_dry", 40, true, ElectricHoeScenarios::fun03BaseHoeLeavesPlotDry);
		registerTest(event, "hoe_flat_upgrade_cannot_water_farmland", 40, true, ElectricHoeScenarios::fun04FlatUpgradeCannotWaterExistingFarmland);

		// MOD-379 (TC-SHOVEL-001-FUN01..04): the shovel's right-click interactions. This lane is the one
		// that matters — the shovel could not path or douse on NeoForge at all until the item declared
		// DEFAULT_SHOVEL_ACTIONS, and it shipped that way because it had no gametest on either loader.
		registerTest(event, "shovel_makes_dirt_path", 40, true, ElectricShovelScenarios::fun01ShovelMakesDirtPath);
		registerTest(event, "shovel_vanilla_paths_same_fixture", 40, true, ElectricShovelScenarios::fun02VanillaShovelPathsTheSameFixture);
		registerTest(event, "shovel_path_making_is_free", 40, true, ElectricShovelScenarios::fun03PathMakingIsFree);
		registerTest(event, "shovel_douses_lit_campfire", 40, true, ElectricShovelScenarios::fun04ShovelDousesLitCampfire);

		// MOD-132 Electromagnet (suite TC-MAGNET-001) — same neutral bodies as the Fabric MagnetGameTest.
		registerTest(event, "magnet_pulls_nearby_drop", 40, true, MagnetScenarios::fun01PullsNearbyDrop);
		registerTest(event, "magnet_flat_inert", 40, true, MagnetScenarios::fun02FlatMagnetInert);
		registerTest(event, "magnet_disabled_inert", 40, true, MagnetScenarios::fun03DisabledMagnetInert);
		registerTest(event, "magnet_respects_pickup_delay", 40, true, MagnetScenarios::fun04RespectsPickupDelay);
		registerTest(event, "magnet_out_of_range_ignored", 40, true, MagnetScenarios::fun05OutOfRangeIgnored);
		registerTest(event, "magnet_toggle_via_use", 40, true, MagnetScenarios::fun06ToggleViaUse);
		registerTest(event, "magnet_toggle_round_trip", 40, true, MagnetScenarios::per01ToggleRoundTrip);

		// MOD-063 Vacuum Capsule (suite TC-CAPS-001) — same neutral bodies as the Fabric CapsuleGameTest.
		registerTest(event, "capsule_component_round_trip", 40, true, CapsuleScenarios::per01ComponentRoundTrip);
		registerTest(event, "capsule_stacking_by_fluid", 40, true, CapsuleScenarios::fun01StackingByFluid);
		registerTest(event, "capsule_fill_from_tank", 40, true, CapsuleScenarios::fun02FillFromTank);
		registerTest(event, "capsule_empty_into_tank", 40, true, CapsuleScenarios::fun03EmptyIntoTank);
		// MOD-099: capsule ↔ pump through the REAL ServerPlayerGameMode routing (GUI-eats-click guard).
		// Longer timeout: makeMockServerPlayerInLevel + openMenu path is heavier than a mock player.
		registerTest(event, "capsule_use_routing_fill", 100, true, CapsuleScenarios::fun05UseRoutingFill);
		registerTest(event, "capsule_use_routing_empty", 100, true, CapsuleScenarios::fun06UseRoutingEmpty);
		registerTest(event, "capsule_use_routing_offhand", 100, true, CapsuleScenarios::fun07UseRoutingOffHand);
		registerTest(event, "pump_sync_channels_fit_short", 100, true, CapsuleScenarios::fun08SyncChannelsFitShort);
		registerTest(event, "pump_menu_stub_width_matches", 100, true, CapsuleScenarios::fun09MenuStubWidthMatches);
		// MOD-235 (TC-CMN-001-REG02): the same client-stub-width check, swept over EVERY machine menu in
		// ContentManifest.MENUS — the pump's FUN09 above only ever covered the pump. Longer timeout: the
		// body places 17 machines and builds 22 menus.
		// MOD-277: the Garden Drone Station — the same five bodies the Fabric lane runs.
		registerTest(event, "garden_drone_tills_dirt", 40, true,
				GardenDroneScenarios::fun01TillsDirtAndSpendsEu);
		registerTest(event, "garden_drone_plants_seed", 40, true,
				GardenDroneScenarios::fun02PlantsSeedOnFarmland);
		registerTest(event, "garden_drone_harvests_into_station", 40, true,
				GardenDroneScenarios::fun03HarvestsRipeCropIntoStation);
		registerTest(event, "garden_drone_without_energy_does_nothing", 40, true,
				GardenDroneScenarios::fun04NoEnergyLeavesCropUntouched);
		registerTest(event, "garden_drone_full_output_keeps_crop", 40, true,
				GardenDroneScenarios::fun05FullOutputLeavesCropStanding);
		registerTest(event, "garden_drone_without_drone_is_inert", 40, true,
				GardenDroneScenarios::fun07WithoutDroneNothingHappens);
		registerTest(event, "garden_drone_hoe_breaks_on_last_use", 40, true,
				GardenDroneScenarios::fun08HoeBreaksOnItsLastUse);
		registerTest(event, "garden_drone_hoe_survives_until_last_use", 40, true,
				GardenDroneScenarios::fun09HoeSurvivesUntilItsLastUse);
		registerTest(event, "garden_drone_flight_delays_action", 300, true,
				GardenDroneScenarios::fun06FlightDelaysTheAction);
		// MOD-317: the drone holds on the tile it worked before the return leg starts.
		registerTest(event, "garden_drone_stands_on_tile_before_flying_home", 300, true,
				GardenDroneScenarios::fun10StandsOnTheTileBeforeFlyingHome);
		registerTest(event, "menu_data_width_matches_block_entity", 200, true,
				MenuDataWidthScenarios::reg02ClientMenuWidthMatchesBlockEntity);
		// MOD-107: the pump's slots exchange with fluid containers through the loader's item fluid
		// capability. FUN10/FUN11 guard the vanilla-bucket behaviour the bridge replaced (previously
		// untested); FUN12/FUN13 cover the capsule the player found inert in the slot.
		registerTest(event, "pump_slot_bucket_fill", 100, true, CapsuleScenarios::fun10BucketFillsTankFromSlot);
		registerTest(event, "pump_slot_bucket_drain", 100, true, CapsuleScenarios::fun11BucketDrainsTankFromSlot);
		registerTest(event, "pump_slot_capsule_fill", 100, true, CapsuleScenarios::fun12CapsuleFillsTankFromSlot);
		registerTest(event, "pump_slot_capsule_drain", 100, true, CapsuleScenarios::fun13CapsuleDrainsTankFromSlot);

		// MOD-068 / MOD-098 Scythe (suites TC-SCYTHE-001 + TC-SCYTHE-002) — same neutral bodies as the
		// Fabric ScytheGameTest. Proves the AOE clear, tag filters, ServerPlayerGameMode.destroyBlock
		// durability/creative parity, the per-use cap, the flat 1/block durability (incl. hardness-0),
		// and the decor / crop modes (crop-protection, maturity, foliage-untouched-in-crop-mode).
		registerTest(event, "scythe_clears_foliage_keeps_solids", 40, true, ScytheScenarios::fun01ClearsFoliageKeepsSolids);
		registerTest(event, "scythe_shift_crop_mode_keeps_decor", 40, true, ScytheScenarios::neg01ShiftCropModeKeepsDecor);
		registerTest(event, "scythe_durability_per_block", 40, true, ScytheScenarios::prf01DurabilityPerBlock);
		registerTest(event, "scythe_durability_on_instant_block", 40, true, ScytheScenarios::prf03DurabilityOnInstantBlock);
		registerTest(event, "scythe_creative_no_durability", 40, true, ScytheScenarios::prf02CreativeNoDurability);
		registerTest(event, "scythe_stops_at_max_blocks", 40, true, ScytheScenarios::bva01StopsAtMaxBlocks);
		registerTest(event, "scythe_keeps_crops_and_water", 40, true, ScytheScenarios::neg02KeepsCropsAndWater);
		registerTest(event, "scythe_crop_mode_harvests_mature", 40, true, ScytheScenarios::fun02CropModeHarvestsMature);
		registerTest(event, "scythe_crop_mode_keeps_immature", 40, true, ScytheScenarios::neg03CropModeKeepsImmature);
		registerTest(event, "scythe_crop_mode_keeps_foliage", 40, true, ScytheScenarios::neg04CropModeKeepsFoliage);
		registerTest(event, "scythe_crop_mode_harvests_cane_stalk", 40, true, ScytheScenarios::fun03CropModeHarvestsCaneStalk);
		registerTest(event, "scythe_crop_mode_keeps_lone_cactus", 40, true, ScytheScenarios::neg05CropModeKeepsLoneCactus);
		registerTest(event, "scythe_crop_mode_keeps_stem", 40, true, ScytheScenarios::neg06CropModeKeepsStem);
		// MOD-325: torchflower_crop is harvestable at its real max age (1), not CropBlock#getMaxAge() (2).
		registerTest(event, "scythe_crop_mode_harvests_torchflower", 40, true,
				ScytheScenarios::fun06CropModeHarvestsTorchflowerAtMaxAge);
		registerTest(event, "scythe_crop_mode_keeps_immature_torchflower", 40, true,
				ScytheScenarios::neg08CropModeKeepsImmatureTorchflower);
		registerTest(event, "scythe_netherite_fire_resistant", 40, true, ScytheScenarios::con02NetheriteFireResistant);
		// MOD-315 bonus seed drop (suite TC-SCYTHE-003). Deterministic by construction: the torchflower
		// loot table yields exactly one seed, so the baseline is exact and a bonus shows up as a second.
		registerTest(event, "scythe_crop_bonus_absent_on_zero_tier", 40, true, ScytheScenarios::fun04CropBonusAbsentOnZeroChanceTier);
		registerTest(event, "scythe_crop_bonus_always_at_full_chance", 40, true, ScytheScenarios::fun05CropBonusAlwaysDropsAtFullChance);
		registerTest(event, "scythe_crop_bonus_skips_non_crop_block", 40, true, ScytheScenarios::neg07CropBonusSkipsNonCropBlock);

		// MOD-104: exact end-to-end item movement through native item APIs and the disabled-face gate.
		registerTest(event, "item_pipe_transfers_between_chests", 40, true, ItemPipeScenarios::transfersBetweenChests);
		registerTest(event, "item_pipe_disabled_face_blocks_transfer", 40, true, ItemPipeScenarios::disabledFaceBlocksTransfer);
		registerTest(event, "item_pipe_disabled_link_blocks_transfer", 40, true,
				ItemPipeScenarios::disabledPipeLinkBlocksTransfer);
		// MOD-282: rebuild only ever splits, so a re-enabled pipe link used to leave two networks
		// forever — a line that renders whole and moves nothing. Split guard included so a naive
		// "merge everything adjacent" fix cannot pass by destroying the disconnect feature.
		registerTest(event, "item_pipe_reenabled_link_rejoins_network", 40, true,
				ItemPipeScenarios::reEnabledPipeLinkRejoinsNetwork);
		registerTest(event, "item_pipe_disabled_link_splits_network", 40, true,
				ItemPipeScenarios::disabledPipeLinkSplitsNetwork);
		registerTest(event, "item_pipe_wrench_cycle_restores_link", 40, true,
				ItemPipeScenarios::wrenchCycleRestoresPipeLink);
		// MOD-151: the fluid pipe on the NeoForge fluid-capability seam.
		registerTest(event, "fluid_pipe_transfers_between_tanks", 60, true,
				FluidPipeScenarios::transfersBetweenTanks);
		registerTest(event, "fluid_pipe_segments_hold_fluid_in_transit", 60, true,
				FluidPipeScenarios::segmentsHoldFluidInTransit);
		registerTest(event, "fluid_pipe_reenabled_link_rejoins_network", 40, true,
				FluidPipeScenarios::reEnabledPipeLinkRejoinsNetwork);
		registerTest(event, "fluid_pipe_segment_refuses_second_fluid", 40, true,
				FluidPipeScenarios::segmentRefusesASecondFluid);
		registerTest(event, "fluid_pipe_broken_segment_loses_contents", 60, true,
				FluidPipeScenarios::brokenSegmentLosesItsContentsWithoutDuplicating);
		// MOD-108: chest → pipe → MACHINE. The chest-to-chest cases above never touch a machine's
		// automation gate (canPlaceItemThroughFace), which is exactly the path players build.
		// MOD-178: source → three insert chests, first two full — the pipe must skip to the free one.
		registerTest(event, "item_pipe_skips_full_targets", 40, true, ItemPipeScenarios::skipsFullTargetsAndFillsFreeOne);
		registerTest(event, "item_pipe_inserts_into_machine", 40, true, ItemPipeScenarios::insertsIntoMachine);
		// MOD-108 balance guard: one batch per interval, not per tick (MOD-104 shipped 20 items/s).
		registerTest(event, "item_pipe_respects_transfer_interval", 40, true, ItemPipeScenarios::respectsTransferInterval);
		// MOD-108: vanilla chests — the seam a player actually builds with.
		registerTest(event, "item_pipe_vanilla_chests", 40, true, ItemPipeScenarios::transfersBetweenVanillaChests);
		// MOD-108: shift-click with the wrench dismantles our blocks and leaves foreign ones alone.
		registerTest(event, "wrench_dismantles_own_blocks", 40, true, ItemPipeScenarios::wrenchDismantlesOwnBlocks);
		// MOD-193: the MOD-115 furnace family, which ran on Fabric only. The sided WorldlyContainer path
		// (top=input, side=fuel, bottom=result) is exactly where this loader differed: Fabric's
		// ItemStorage.SIDED falls back to any Container, NeoForge publishes the capability per block
		// entity — so a mod machine missing from that list is invisible to the pipe here and nowhere
		// else. The iron-furnace cases below are the guards for that; the vanilla-furnace ones pin the
		// round-robin itself (vanilla publishes its own capability, so they pass either way).
		registerTest(event, "item_pipe_vanilla_furnace_bottom_extract", 60, true,
				h -> ItemPipeScenarios.extractsFurnaceResultFromBottom(h, Blocks.FURNACE, "vanilla furnace bottom extract"));
		registerTest(event, "item_pipe_iron_furnace_bottom_extract", 60, true,
				h -> ItemPipeScenarios.extractsFurnaceResultFromBottom(h, ModContent.IRON_FURNACE.get(),
						"iron furnace bottom extract"));
		registerTest(event, "item_pipe_distributes_one_source_to_two_furnaces", 60, true,
				ItemPipeScenarios::distributesOneSourceToTwoFurnaces);
		registerTest(event, "item_pipe_distributes_across_rebuilds", 60, true,
				ItemPipeScenarios::distributesToTwoFurnacesAcrossRebuilds);
		registerTest(event, "item_pipe_ore_and_coal_to_two_iron_furnaces", 60, true,
				ItemPipeScenarios::distributesOreAndCoalToTwoIronFurnaces);
		registerTest(event, "item_pipe_feeds_both_furnaces_in_one_interval", 60, true,
				ItemPipeScenarios::feedsBothFurnacesWithinOneInterval);
		// MOD-199: class-wide guard against a cable arm on an energy-inert face. Sweeps the block
		// registry rather than naming blocks, so a block added later is covered without anyone
		// writing a test for it — the gap that let MOD-194 (water mill) ship.
		registerTest(event, "cable_face_parity_inert_faces_reject_arms", 60, true,
				CableFaceParityScenarios::inertFacesRejectCableArms);
		// MOD-133 player-stats attribution — same bodies as the Fabric PlayerStatsGameTest lane. These
		// pin the machine-XP rule, the anti-AFK abort guard, creative/ownerless exclusions, and generator
		// production attribution.
		registerTest(event, "player_stats_xp_from_completed_work", 500, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::xpFromCompletedWork);
		registerTest(event, "player_stats_no_xp_from_aborted_work", 60, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::noXpFromAbortedWork);
		registerTest(event, "player_stats_no_xp_for_creative_owner", 500, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::noXpForCreativeOwner);
		// MOD-264: a pump credits no mastery at all — unattended extraction is not "useful work".
		registerTest(event, "player_stats_pump_grants_no_mastery", 200, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::pumpGrantsNoMastery);
		registerTest(event, "player_stats_no_stats_for_null_owner", 500, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::noStatsForNullOwner);
		registerTest(event, "player_stats_no_stats_for_offline_owner", 500, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::noStatsForOfflineOwner);
		registerTest(event, "player_stats_generator_production_attributed", 500, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::generatorProductionAttributedToOwner);
		registerTest(event, "player_stats_buffer_cap_limits_attribution", 300, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::bufferCapLimitsAttributedProduction);
		// MOD-156: mode-transition and lifecycle coverage — a creative pass neither wipes nor resumes-wrong,
		// a disconnect flushes the pending tail, and activeTicks does not scale with generator count.
		registerTest(event, "player_stats_mode_transitions_preserve_and_resume", 1400, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::modeTransitionsPreserveAndResumeAccrual);
		registerTest(event, "player_stats_flush_player_saves_tail_on_logout", 500, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::flushPlayerSavesTailOnLogout);
		registerTest(event, "player_stats_active_ticks_not_scaled_by_generator_count", 60, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::activeTicksNotScaledByGeneratorCount);
		registerTest(event, "player_stats_active_time_accrues_with_full_buffer", 60, true,
				dev.alaindustrial.gametest.PlayerStatsScenarios::activeTimeAccruesWithFullBuffer);
		// Forge Hammer (MOD-078): craft-remainder mechanic on the NeoForge lane — the hammer stays in the
		// grid, one durability per plate, breaks on its last point. Loader-neutral bodies in HammerScenarios.
		registerTest(event, "hammer_stays_with_one_damage", 40, true, HammerScenarios::fun01HammerStaysWithOneDamage);
		registerTest(event, "hammer_exactly_one_damage_per_plate", 40, true, HammerScenarios::prf01ExactlyOneDamagePerPlate);
		registerTest(event, "hammer_last_durability_breaks", 40, true, HammerScenarios::neg01LastDurabilityBreaks);
		registerTest(event, "hammer_durability_128", 40, true, HammerScenarios::con01Durability128);
		// Incubator (MOD-118, suite TC-INCU-001): the multiblock gate, the chip-driven mode, the uranium
		// charge and — the regressions worth a second loader — the output slot never eating a result.
		registerTest(event, "incubator_glass_forms_dome", 40, true,
				IncubatorScenarios::fun01GlassOnTopFormsTheDome);

		// Cotton trellis (MOD-280) — same loader-neutral bodies as the Fabric suite.
		registerTest(event, "cotton_plants_seed", 40, true,
				TrellisScenarios::fun01PlantsSeed);
		registerTest(event, "cotton_keeps_seed_on_planted", 40, true,
				TrellisScenarios::neg01KeepsSeedOnPlantedTrellis);
		registerTest(event, "cotton_grows_on_moist_farmland", 40, true,
				TrellisScenarios::fun02GrowsOnMoistFarmland);
		registerTest(event, "cotton_pauses_on_dry_farmland", 40, true,
				TrellisScenarios::neg02PausesOnDryFarmland);
		registerTest(event, "cotton_harvest_keeps_plant", 40, true,
				TrellisScenarios::fun03HarvestKeepsPlant);
		registerTest(event, "cotton_unripe_yields_nothing", 40, true,
				TrellisScenarios::neg03UnripeYieldsNothing);
		registerTest(event, "cotton_farmland_survives", 40, true,
				TrellisScenarios::reg01FarmlandSurvivesUnderTrellis);
		registerTest(event, "cotton_break_drops_one", 40, true,
				TrellisScenarios::reg02BreakDropsExactlyOneTrellis);
		registerTest(event, "cotton_harvest_with_seeds_in_hand", 40, true,
				TrellisScenarios::reg03HarvestWorksWithSeedsInHand);
		registerTest(event, "cotton_planted_returns_seed", 40, true,
				TrellisScenarios::reg04PlantedTrellisReturnsSeed);
		registerTest(event, "cotton_bare_returns_no_seed", 40, true,
				TrellisScenarios::neg04BareTrellisReturnsNoSeed);
		registerTest(event, "cotton_bonemeal_one_stage", 40, true,
				TrellisScenarios::fun04BonemealAdvancesOneStage);
		registerTest(event, "cotton_bonemeal_refuses_dry", 40, true,
				TrellisScenarios::neg05BonemealRefusesDrySoil);
		registerTest(event, "cotton_second_harvest_cycle", 60, true,
				TrellisScenarios::fun05SecondHarvestCycle);
		registerTest(event, "cotton_break_upper_drops_one", 40, true,
				TrellisScenarios::reg05BreakingUpperHalfDropsOne);
		registerTest(event, "incubator_dome_returns_glass", 40, true,
				IncubatorScenarios::fun02RemovingTheDomeReturnsTheOriginalGlass);
		registerTest(event, "incubator_transform_yields_mineral", 40, true,
				IncubatorScenarios::fun03TransformYieldsTheOtherMineral);
		registerTest(event, "incubator_ingot_powers_attempts", 40, true,
				IncubatorScenarios::fun04OneIngotPowersSeveralAttempts);
		registerTest(event, "incubator_non_player_break_returns_glass", 40, true,
				IncubatorScenarios::fun05NonPlayerBreakStillReturnsTheGlass);
		registerTest(event, "incubator_base_break_keeps_coloured_glass", 40, true,
				IncubatorScenarios::fun06BreakingTheBaseKeepsTheColouredGlass);
		registerTest(event, "incubator_automation_takes_products_only", 40, true,
				IncubatorScenarios::fun07AutomationTakesOnlyTheProducts);
		registerTest(event, "incubator_dome_click_opens_menu", 40, true,
				IncubatorScenarios::fun08ClickingTheDomeOpensTheMenu);
		registerTest(event, "incubator_advancements_awarded", 40, true,
				IncubatorScenarios::fun09TakingTheResultAwardsTheAdvancements);
		registerTest(event, "incubator_ingot_commits_on_insertion", 40, true,
				IncubatorScenarios::fun10IngotCommitsOnInsertion);
		registerTest(event, "incubator_no_dome_no_output", 40, true,
				IncubatorScenarios::neg01NoDomeNoOutput);
		registerTest(event, "incubator_no_chip_no_output", 40, true,
				IncubatorScenarios::neg02NoChipNoOutput);
		registerTest(event, "incubator_creative_break_drops_nothing", 40, true,
				IncubatorScenarios::neg03CreativeBreakDropsNoGlass);
		registerTest(event, "incubator_dome_blocks_pistons", 40, true,
				IncubatorScenarios::con01DomeBlocksPistons);
		registerTest(event, "incubator_graded_result_not_destroyed", 40, true,
				IncubatorScenarios::reg01GradedResultNeverDestroysTheOutput);
		registerTest(event, "incubator_slag_not_destroyed", 40, true,
				IncubatorScenarios::reg02SlagNeverDestroysTheOutput);
		registerTest(event, "incubator_no_free_reroll", 40, true,
				IncubatorScenarios::reg03NoFreeRerollBeforeTheCycleIsPaid);
		registerTest(event, "incubator_hopper_cannot_steal_chip", 40, true,
				IncubatorScenarios::reg04HopperBelowCannotStealTheChip);
		registerTest(event, "incubator_status_explains_every_stall", 40, true,
				IncubatorScenarios::reg05StatusExplainsEveryStall);
		registerTest(event, "incubator_pipe_returns_result_to_input", 40, true,
				IncubatorScenarios::reg06PipeReturnsTheResultToTheInput);
		registerTest(event, "incubator_client_menu_data_width", 40, true,
				IncubatorScenarios::reg07ClientMenuCoversEveryDataChannel);
		registerTest(event, "incubator_pipe_drains_products", 40, true,
				IncubatorScenarios::reg08PipeDrainsProductsIntoAChest);
		registerTest(event, "pipe_front_face_not_an_endpoint", 40, true,
				ItemPipeScenarios::frontFaceIsNotAnEndpoint);
		// Oil fluid (MOD-238, suite TC-OIL-001) — same loader-neutral bodies as the Fabric
		// OilGameTest suite. The per-loader seam is the whole point here: the NeoForge fluid is a
		// DeferredRegister FluidType subclass, so bucket/capsule/pump exchange, the
		// no-source-conversion rule, the oilBurns gate and the viscous spread profile must be
		// proven against that registration path too, not just Fabric's eager one.
		registerTest(event, "oil_bucket_place_and_pickup", 60, true,
				OilScenarios::fun01BucketPlaceAndPickup);
		registerTest(event, "oil_capsule_pickup_and_place", 100, true,
				OilScenarios::fun02CapsulePickupAndPlace);
		registerTest(event, "oil_pump_drains_lake_into_tank", 340, true,
				OilScenarios::fun03PumpDrainsOilLakeIntoTank);
		registerTest(event, "oil_gap_never_becomes_source", 220, true,
				OilScenarios::neg01GapNeverBecomesSource);
		registerTest(event, "oil_burn_gate_on_then_off", 220, true,
				OilScenarios::fun04BurnGateOnThenOff);
		registerTest(event, "oil_burn_spreads_across_pool", 160, true,
				OilScenarios::fun05BurnSpreadsAcrossPool);
		registerTest(event, "oil_lava_neighbour_never_ignites", 160, true,
				OilScenarios::neg02LavaNeighbourNeverIgnites);
		registerTest(event, "oil_dispenser_empties_bucket", 120, true,
				OilScenarios::fun06DispenserEmptiesOilBucket);
		registerTest(event, "oil_viscous_spread_profile", 300, true,
				OilScenarios::prf01ViscousSpreadProfile);
		registerTest(event, "oil_torch_logs_with_oil", 80, true,
				OilScenarios::fun08TorchLogsWithOil);
		registerTest(event, "oil_first_bucket_awards_advancement", 100, true,
				OilScenarios::fun09FirstOilAwardsTheAdvancement);

		// Polymerizer (MOD-019, suite TC-POLY-001) — same loader-neutral bodies as the Fabric
		// PolymerizerGameTest suite. The seam matters here: the machine's tank is published through a
		// different capability on each loader, and its fluid recipe family rides a RecipeType registered
		// eagerly on Fabric but deferred on NeoForge — a family that fails to bind makes the machine
		// silently inert on that loader alone.
		registerTest(event, "polymerizer_oil_becomes_raw_rubber", 400, true,
				PolymerizerScenarios::fun01OilBecomesRawRubber);
		// Galvanic Bath (MOD-127, suite TC-BATH-001) — same loader-neutral bodies as the Fabric
		// GalvanicBathGameTest suite. Two rules here are invisible to the recipe system: the fibre tag
		// (string OR cotton must both work) and the water gate (water is not a recipe ingredient, so a
		// dropped check would let the bath run on an empty tank).
		registerTest(event, "galvanic_bath_string_becomes_flux_thread", 700, true,
				GalvanicBathScenarios::fun01StringBecomesFluxThread);
		registerTest(event, "galvanic_bath_cotton_becomes_flux_thread", 700, true,
				GalvanicBathScenarios::fun02CottonBecomesFluxThread);
		registerTest(event, "galvanic_bath_operation_debits_water", 700, true,
				GalvanicBathScenarios::fun03OperationDebitsWater);
		registerTest(event, "galvanic_bath_bucket_fills_tank", 60, true,
				GalvanicBathScenarios::fun04BucketFillsTank);
		registerTest(event, "galvanic_bath_dry_tank_blocks_work", 700, true,
				GalvanicBathScenarios::con01DryTankBlocksWork);
		registerTest(event, "galvanic_bath_partial_water_blocks_work", 700, true,
				GalvanicBathScenarios::con02PartialWaterBlocksWork);
		registerTest(event, "galvanic_bath_tank_refuses_extraction", 60, true,
				GalvanicBathScenarios::con03TankRefusesExtraction);
		registerTest(event, "galvanic_bath_tank_refuses_non_water", 60, true,
				GalvanicBathScenarios::reg01TankRefusesNonWater);
		// Alloy Smelter (MOD-064, suite TC-ALLOY-001) — same loader-neutral bodies as the Fabric
		// AlloySmelterGameTest suite. The rule worth the most here is FUN02: this is the mod's only
		// machine whose inputs are matched as an unordered multiset, so a regression to a positional
		// compare would leave FUN01 green and show up only there.
		registerTest(event, "alloy_smelter_bronze_is_smelted", 400, true,
				AlloySmelterScenarios::fun01BronzeIsSmelted);
		registerTest(event, "alloy_smelter_order_does_not_matter", 400, true,
				AlloySmelterScenarios::fun02OrderDoesNotMatter);
		registerTest(event, "alloy_smelter_consumes_per_component_counts", 400, true,
				AlloySmelterScenarios::fun03ConsumesPerComponentCounts);
		registerTest(event, "alloy_smelter_electrum_from_gold_and_silver", 400, true,
				AlloySmelterScenarios::fun04ElectrumFromGoldAndSilver);
		registerTest(event, "alloy_smelter_spare_slot_blocks_shorter_recipe", 400, true,
				AlloySmelterScenarios::con01SpareSlotBlocksShorterRecipe);
		registerTest(event, "alloy_smelter_short_component_blocks_work", 400, true,
				AlloySmelterScenarios::con02ShortComponentBlocksWork);
		registerTest(event, "alloy_smelter_no_energy_blocks_work", 400, true,
				AlloySmelterScenarios::con03NoEnergyBlocksWork);
		registerTest(event, "alloy_smelter_duplicate_input_refused", 60, true,
				AlloySmelterScenarios::reg01DuplicateInputRefused);
		registerTest(event, "alloy_smelter_non_component_refused", 60, true,
				AlloySmelterScenarios::reg02NonComponentRefused);
		registerTest(event, "alloy_smelter_output_slot_refuses_insert", 60, true,
				AlloySmelterScenarios::reg03OutputSlotRefusesInsert);
		registerTest(event, "alloy_smelter_input_swap_resets_progress", 400, true,
				AlloySmelterScenarios::reg04InputSwapResetsProgress);
		registerTest(event, "alloy_smelter_distinct_metals_cannot_fill_every_slot", 60, true,
				AlloySmelterScenarios::reg05DistinctMetalsCannotFillEverySlot);
		registerTest(event, "alloy_smelter_speed_multiplier_keeps_operation_cost", 400, true,
				AlloySmelterScenarios::reg06SpeedMultiplierKeepsOperationCost);
		// Fluxweave armour (MOD-127, suite TC-FLUX-001) — the same loader-neutral bodies as the Fabric
		// FluxweaveArmorGameTest suite. The invariant worth the most here is REG01: the bonuses share a
		// component with the material's own armour modifiers, so a rewrite that replaced instead of
		// extended would leave a drained suit with no protection at all.
		registerTest(event, "fluxweave_drained_set_keeps_base_protection", 60, true,
				FluxweaveArmorScenarios::reg01DrainedSetKeepsBaseProtection);
		registerTest(event, "fluxweave_charge_switches_bonuses_on", 60, true,
				FluxweaveArmorScenarios::fun01ChargeSwitchesBonusesOn);
		registerTest(event, "fluxweave_boots_soften_but_never_cancel_falls", 60, true,
				FluxweaveArmorScenarios::con01BootsSoftenButNeverCancelFalls);
		registerTest(event, "fluxweave_step_assist_is_opt_in", 60, true,
				FluxweaveArmorScenarios::fun02StepAssistIsOptIn);
		registerTest(event, "fluxweave_set_bonus_applies_once_per_second", 200, true,
				FluxweaveArmorScenarios::con02SetBonusAppliesOncePerSecond);
		registerTest(event, "fluxweave_dead_piece_breaks_the_set", 200, true,
				FluxweaveArmorScenarios::con03DeadPieceBreaksTheSet);
		registerTest(event, "fluxweave_worn_asset_follows_charge", 60, true,
				FluxweaveArmorScenarios::reg02WornAssetFollowsCharge);
		registerTest(event, "polymerizer_bucket_fills_tank", 60, true,
				PolymerizerScenarios::fun02BucketFillsTank);
		registerTest(event, "polymerizer_no_power_no_output", 400, true,
				PolymerizerScenarios::neg01NoPowerNoOutput);
		registerTest(event, "polymerizer_below_recipe_volume_is_inert", 400, true,
				PolymerizerScenarios::neg02BelowRecipeVolumeIsInert);
		registerTest(event, "polymerizer_full_output_keeps_oil", 400, true,
				PolymerizerScenarios::con01FullOutputKeepsOil);
		registerTest(event, "polymerizer_tank_refuses_non_oil", 60, true,
				PolymerizerScenarios::reg01TankRefusesNonOil);
		registerTest(event, "polymerizer_tank_refuses_flowing_oil", 60, true,
				PolymerizerScenarios::reg02TankRefusesFlowingOil);
		registerTest(event, "polymerizer_neighbour_fills_through_fluid_port", 60, true,
				PolymerizerScenarios::con03NeighbourFillsThroughFluidPort);
		registerTest(event, "polymerizer_nbt_round_trip_preserves_tank", 60, true,
				PolymerizerScenarios::sta01NbtRoundTripPreservesTank);

		// Distillation Column (MOD-251, suite TC-DIST-001) — same bodies as the Fabric wrappers:
		// the first 1×1×3 multiblock, per-segment fluid ports, warm-up gate, fluid-preserving drop.
		registerTest(event, "distillation_column_oil_splits_into_fractions", 600, true,
				DistillationColumnScenarios::fun01OilSplitsIntoFractions);
		registerTest(event, "distillation_column_warmup_gates_distillation", 400, true,
				DistillationColumnScenarios::fun02WarmupGatesDistillation);
		registerTest(event, "distillation_column_full_diesel_tank_freezes", 600, true,
				DistillationColumnScenarios::con01FullDieselTankFreezes);
		registerTest(event, "distillation_column_drain_slot_bottles_diesel", 60, true,
				DistillationColumnScenarios::fun03DrainSlotBottlesDiesel);
		registerTest(event, "distillation_column_break_middle_degrades_tower", 100, true,
				DistillationColumnScenarios::seg01BreakMiddleDegradesTower);
		registerTest(event, "distillation_column_three_blanks_form_tower", 60, true,
				DistillationColumnScenarios::frm01ThreeBlanksFormTheTower);
		registerTest(event, "distillation_column_segment_ports_match_layout", 60, true,
				DistillationColumnScenarios::prt01SegmentPortsMatchLayout);
		registerTest(event, "distillation_column_null_side_port_is_oil_tank", 60, true,
				DistillationColumnScenarios::prt02NullSidePortIsOilTank);
		registerTest(event, "distillation_column_nbt_round_trip", 60, true,
				DistillationColumnScenarios::sta01NbtRoundTripPreservesTanksAndHeat);
		registerTest(event, "distillation_column_fuel_oil_cracks_into_diesel", 700, true,
				DistillationColumnScenarios::crk01FuelOilCracksIntoDiesel);
		registerTest(event, "distillation_column_section_boosts_diesel", 600, true,
				DistillationColumnScenarios::sec01SectionBoostsDiesel);
		registerTest(event, "distillation_column_fouled_stops_until_cleaned", 1000, true,
				DistillationColumnScenarios::fou01FouledStopsUntilCleaned);

		// Vulcanizer + external heat (MOD-258, suite TC-VULC-001). These are the same bodies as the
		// Fabric wrappers and cover the two-input recipe, heat multipliers, demand-driven electric
		// heater, automation faces, cycle snapshot and persistence on the deferred-registration lane.
		registerTest(event, "vulcanizer_heat_levels_scale_output", 700, true,
				VulcanizerScenarios::fun01HeatLevelsScaleOutput);
		registerTest(event, "vulcanizer_all_passive_heat_sources_resolve", 40, true,
				VulcanizerScenarios::fun02AllPassiveHeatSourcesResolve);
		registerTest(event, "vulcanizer_no_heat_no_work", 40, true,
				VulcanizerScenarios::neg01NoHeatNoWork);
		registerTest(event, "vulcanizer_no_power_no_work", 300, true,
				VulcanizerScenarios::neg02NoPowerNoWork);
		registerTest(event, "vulcanizer_inactive_heat_sources_resolve_as_none", 40, true,
				VulcanizerScenarios::neg03InactiveHeatSourcesResolveAsNone);
		registerTest(event, "vulcanizer_partial_sulfur_batch_does_not_work", 300, true,
				VulcanizerScenarios::neg04PartialSulfurBatchDoesNotWork);
		registerTest(event, "vulcanizer_output_jam_freezes_both_consumers", 40, true,
				VulcanizerScenarios::con01OutputJamFreezesBothConsumers);
		registerTest(event, "vulcanizer_heater_is_demand_driven", 40, true,
				VulcanizerScenarios::con02HeaterIsDemandDriven);
		registerTest(event, "vulcanizer_heater_tariff_is_atomic_at_threshold", 40, true,
				VulcanizerScenarios::con03HeaterTariffIsAtomicAtThreshold);
		registerTest(event, "vulcanizer_heat_upgrade_restarts_cycle", 300, true,
				VulcanizerScenarios::reg01HeatUpgradeRestartsCycle);
		registerTest(event, "vulcanizer_automation_keeps_inputs_separated", 40, true,
				VulcanizerScenarios::reg02AutomationKeepsInputsSeparated);
		registerTest(event, "vulcanizer_heat_downgrade_restarts_cycle", 300, true,
				VulcanizerScenarios::reg03HeatDowngradeRestartsCycle);
		registerTest(event, "vulcanizer_round_trip_preserves_in_flight_cycle", 40, true,
				VulcanizerScenarios::sta01RoundTripPreservesInFlightCycle);
		registerTest(event, "vulcanizer_rubber_production_advancement", 300, true,
				VulcanizerScenarios::fun03RubberProductionAdvancement);

		// Scenarios moved out of Fabric-only bodies into common: the NBT round-trips and the two
		// enriched-uranium-torch guarantees. Neither was exercised on NeoForge at all before the move.
		// Note on what this does and does not buy: ten of the eleven persistence scenarios rebuild the
		// block entity directly rather than resolving it from a registered BlockEntityType, so what
		// they add here is the setBlock + getBlockEntity wiring on this loader, not the 26.2
		// ValueInput/ValueOutput seam itself.
		registerTest(event, "persistence_r_per01_macerator_nbt_round_trip", 40, true,
				PersistenceScenarios::rPer01_maceratorNbtRoundTrip);
		registerTest(event, "persistence_r_per01_furnace_nbt_round_trip", 40, true,
				PersistenceScenarios::rPer01_furnaceNbtRoundTrip);
		registerTest(event, "persistence_r_per01_geothermal_fluid_nbt_round_trip", 40, true,
				PersistenceScenarios::rPer01_geothermalFluidNbtRoundTrip);
		registerTest(event, "persistence_r_per01_solar_evolve_nbt_round_trip", 40, true,
				PersistenceScenarios::rPer01_solarEvolveNbtRoundTrip);
		registerTest(event, "persistence_tc_mach003_per01_compressor_nbt_round_trip", 40, true,
				PersistenceScenarios::tcMach003Per01_compressorNbtRoundTrip);
		registerTest(event, "persistence_tc_mach004_per01_extractor_nbt_round_trip", 40, true,
				PersistenceScenarios::tcMach004Per01_extractorNbtRoundTrip);
		registerTest(event, "persistence_tc_efurn001_per01_furnace_freeze_then_resume", 40, true,
				PersistenceScenarios::tcEFurn001Per01_furnaceFreezeThenResume);
		registerTest(event, "persistence_tc_pump001_per01_pump_tank_nbt_round_trip", 40, true,
				PersistenceScenarios::tcPump001Per01_pumpTankNbtRoundTrip);
		registerTest(event, "persistence_tc_pump001_per02_pump_tank_empty_nbt_round_trip", 40, true,
				PersistenceScenarios::tcPump001Per02_pumpTankEmptyNbtRoundTrip);
		registerTest(event, "persistence_tc_cable001_per01_buffer_nbt_round_trip", 40, true,
				PersistenceScenarios::tcCable001Per01_bufferNbtRoundTrip);
		registerTest(event, "persistence_tc_cable001_per02_legacy_machine_keys_ignored_on_load", 40, true,
				PersistenceScenarios::tcCable001Per02_legacyMachineKeysIgnoredOnLoad);
		registerTest(event, "torch_torches_emit_vanilla_torch_light", 40, true,
				EnrichedUraniumTorchScenarios::torchesEmitVanillaTorchLight);
		registerTest(event, "torch_wall_torch_drops_standing_torch", 40, true,
				EnrichedUraniumTorchScenarios::wallTorchDropsStandingTorch);

		// ── MOD-310 — bodies lifted from the Fabric gametest source set into common ─────
		// Every scenario below used to exist for one loader only, so the matching mechanic was
		// never verified here at all. That class is now a thin wrapper around the shared body.
		// Scenarios already covered by an existing mirror below are deliberately NOT registered
		// twice; the skip list lives with the task that moved them.

		// Water mill + wheel (MOD-310): wheel placement, neighbour interference, wear, face roles.
		registerTest(event, "water_mill_wheel_progress_tracks_water_faces", 40, true,
				WaterMillWheelScenarios::waterMillWheel_progressTracksWaterFaces);
		registerTest(event, "water_mill_wheel_slot_filter_is_dedicated", 40, true,
				WaterMillWheelScenarios::waterMillWheel_slotFilterIsDedicated);
		registerTest(event, "water_mill_crafting_recipe_resolves", 40, true,
				WaterMillWheelScenarios::waterMill_craftingRecipeResolves);
		registerTest(event, "water_mill_wheel_crafting_recipe_resolves", 40, true,
				WaterMillWheelScenarios::waterMillWheel_craftingRecipeResolves);
		registerTest(event, "water_mill_side_by_side_interference", 40, true,
				WaterMillWheelScenarios::waterMill_sideBySideInterference);
		registerTest(event, "water_mill_face_to_face_interference", 40, true,
				WaterMillWheelScenarios::waterMill_faceToFaceInterference);
		registerTest(event, "water_mill_spaced_mills_do_not_interfere", 40, true,
				WaterMillWheelScenarios::waterMill_spacedMillsDoNotInterfere);
		registerTest(event, "water_mill_back_to_back_do_not_interfere", 40, true,
				WaterMillWheelScenarios::waterMill_backToBackDoNotInterfere);
		registerTest(event, "water_mill_wall_in_front_stalls_and_recovers", 40, true,
				WaterMillWheelScenarios::waterMill_wallInFrontStallsAndRecovers);
		registerTest(event, "water_mill_block_above_front_also_obstructs", 40, true,
				WaterMillWheelScenarios::waterMill_blockAboveFrontAlsoObstructs);
		registerTest(event, "water_mill_solid_river_bed_below_front_obstructs", 40, true,
				WaterMillWheelScenarios::waterMill_solidRiverBedBelowFrontObstructs);
		registerTest(event, "water_mill_every_wheel_plane_cell_obstructs", 60, true,
				WaterMillWheelScenarios::waterMill_everyWheelPlaneCellObstructs);
		registerTest(event, "water_mill_side_block_obstructs", 40, true,
				WaterMillWheelScenarios::waterMill_sideBlockObstructs);
		registerTest(event, "water_mill_water_beside_wheel_is_allowed", 40, true,
				WaterMillWheelScenarios::waterMill_waterBesideWheelIsAllowed);
		registerTest(event, "water_mill_interference_clears_within_scan_interval", 40, true,
				WaterMillWheelScenarios::waterMill_interferenceClearsWithinScanInterval);
		registerTest(event, "water_mill_neighbour_wheel_removal_clears_interference", 40, true,
				WaterMillWheelScenarios::waterMill_neighbourWheelRemovalClearsInterference);
		registerTest(event, "water_mill_dry_mill_shows_no_water_hint", 40, true,
				WaterMillWheelScenarios::waterMill_dryMillShowsNoWaterHint);
		registerTest(event, "water_mill_still_source_does_not_generate", 40, true,
				WaterMillWheelScenarios::waterMill_stillSourceDoesNotGenerate);
		registerTest(event, "water_mill_energy_only_from_back_face", 40, true,
				WaterMillWheelScenarios::waterMill_energyOnlyFromBackFace);
		registerTest(event, "water_mill_front_face_inert_for_automation", 40, true,
				WaterMillWheelScenarios::waterMill_frontFaceInertForAutomation);
		registerTest(event, "water_mill_automation_cannot_stack_second_wheel", 40, true,
				WaterMillWheelScenarios::waterMill_automationCannotStackSecondWheel);
		registerTest(event, "water_mill_wheel_no_wear_while_dry", 40, true,
				WaterMillWheelScenarios::waterMillWheel_noWearWhileDry);
		registerTest(event, "water_mill_production_rate_channel_tracks_output", 40, true,
				WaterMillWheelScenarios::waterMill_productionRateChannelTracksOutput);
		registerTest(event, "water_mill_production_rate_channel_follows_global_multiplier", 40, true,
				WaterMillWheelScenarios::waterMill_productionRateChannelFollowsGlobalMultiplier);
		registerTest(event, "water_mill_readout_keeps_floor_under_tiny_multiplier", 40, true,
				WaterMillWheelScenarios::waterMill_readoutKeepsFloorUnderTinyMultiplier);
		registerTest(event, "water_mill_wheel_wear_follows_mechanical_rate", 40, true,
				WaterMillWheelScenarios::waterMillWheel_wearFollowsMechanicalRateNotMultiplier);
		registerTest(event, "water_mill_block_neighbours_do_not_drive_wheel", 40, true,
				WaterMillWheelScenarios::waterMill_blockNeighboursDoNotDriveWheel);
		registerTest(event, "water_mill_all_four_wheel_cells_drive", 40, true,
				WaterMillWheelScenarios::waterMill_allFourWheelCellsDrive);

		// Cable placement and the face-connection rules (MOD-038/039/061/071).
		registerTest(event, "mod039_cable_use_returns_pass", 40, true,
				CablePlacementScenarios::mod039_cableUseReturnsPass);
		registerTest(event, "mod039_machine_use_returns_success", 40, true,
				CablePlacementScenarios::mod039_machineUseReturnsSuccess);
		registerTest(event, "mod039_rmb_on_cable_places_adjacent", 40, true,
				CablePlacementScenarios::mod039_rmbOnCablePlacesAdjacent);
		registerTest(event, "mod038_cable_does_not_connect_to_iron_chest", 40, true,
				CablePlacementScenarios::mod038_cableDoesNotConnectToIronChest);
		registerTest(event, "cable_connects_only_to_wind_mill_back_face", 40, true,
				CablePlacementScenarios::cableConnectsOnlyToWindMillBackFace);
		registerTest(event, "cable_connects_only_to_water_mill_back_face", 40, true,
				CablePlacementScenarios::cableConnectsOnlyToWaterMillBackFace);
		registerTest(event, "cable_connects_only_to_battery_box_io_faces", 40, true,
				CablePlacementScenarios::cableConnectsOnlyToBatteryBoxIOFaces);
		registerTest(event, "mod061_cable_does_not_arm_to_generator_facing", 40, true,
				CablePlacementScenarios::mod061_cableDoesNotArmToGeneratorFacing);
		registerTest(event, "mod061_cable_does_not_arm_to_geothermal_facing", 40, true,
				CablePlacementScenarios::mod061_cableDoesNotArmToGeothermalFacing);
		registerTest(event, "mod061_cable_does_not_arm_to_macerator_facing", 40, true,
				CablePlacementScenarios::mod061_cableDoesNotArmToMaceratorFacing);
		registerTest(event, "mod061_cable_does_not_arm_to_electric_furnace_facing", 40, true,
				CablePlacementScenarios::mod061_cableDoesNotArmToElectricFurnaceFacing);
		registerTest(event, "mod061_cable_does_not_arm_to_extractor_facing", 40, true,
				CablePlacementScenarios::mod061_cableDoesNotArmToExtractorFacing);
		registerTest(event, "mod061_cable_does_not_arm_to_compressor_facing", 40, true,
				CablePlacementScenarios::mod061_cableDoesNotArmToCompressorFacing);
		registerTest(event, "mod061_cable_facing_inert_on_non_north_facing", 40, true,
				CablePlacementScenarios::mod061_cableFacingInertOnNonNorthFacing);
		registerTest(event, "mod061_cable_does_not_arm_to_pump_facing", 40, true,
				CablePlacementScenarios::mod061_cableDoesNotArmToPumpFacing);
		registerTest(event, "mod061_cable_stale_flags_reshape_on_first_tick", 40, true,
				CablePlacementScenarios::mod061_cableStaleFlagsReshapeOnFirstTick);
		registerTest(event, "mod071_cable_does_not_arm_to_solar_panel_up", 40, true,
				CablePlacementScenarios::mod071_cableDoesNotArmToSolarPanelUp);
		registerTest(event, "mod071_cable_does_not_arm_to_daylight_solar_panel_up", 40, true,
				CablePlacementScenarios::mod071_cableDoesNotArmToDaylightSolarPanelUp);
		registerTest(event, "mod071_cable_does_not_arm_to_moonlit_solar_panel_up", 40, true,
				CablePlacementScenarios::mod071_cableDoesNotArmToMoonlitSolarPanelUp);

		// Fuel generator (MOD-310): burning, fuel slot, drops, hitbox, LIT state.
		registerTest(event, "tc_gen001_fun01_coal_raises_buffer", 40, true,
				GeneratorScenarios::tcGen001Fun01_coalRaisesBuffer);
		registerTest(event, "tc_gen001_per01_state_survives_nbt_round_trip", 40, true,
				GeneratorScenarios::tcGen001Per01_stateSurvivesNbtRoundTrip);
		registerTest(event, "tc_gen001_neg04_lava_bucket_rejected", 40, true,
				GeneratorScenarios::tcGen001Neg04_lavaBucketRejected);
		registerTest(event, "tc_gen001_neg04b_menu_slot_rejects_lava_bucket", 40, true,
				GeneratorScenarios::tcGen001Neg04b_menuSlotRejectsLavaBucket);
		registerTest(event, "tc_gen001_fun03_coal_block_burns_longer", 40, true,
				GeneratorScenarios::tcGen001Fun03_coalBlockBurnsLonger);
		registerTest(event, "tc_gen001_neg02_non_fuel_produces_no_eu", 40, true,
				GeneratorScenarios::tcGen001Neg02_nonFuelProducesNoEu);
		registerTest(event, "tc_gen001_neg02b_fuel_slot_rejects_non_fuel", 40, true,
				GeneratorScenarios::tcGen001Neg02b_fuelSlotRejectsNonFuel);
		registerTest(event, "tc_gen001_per02_break_drops_fuel_no_dupe", 40, true,
				GeneratorScenarios::tcGen001Per02_breakDropsFuelNoDupe);
		registerTest(event, "tc_gen001_con01_pairwise_neighbours", 40, true,
				GeneratorScenarios::tcGen001Con01_pairwiseNeighbours);
		registerTest(event, "tc_gen001_phy02_hitbox_is_full_cube", 40, true,
				GeneratorScenarios::tcGen001Phy02_hitboxIsFullCube);
		registerTest(event, "tc_gen001_prf02_packet_capped_at_lv", 40, true,
				GeneratorScenarios::tcGen001Prf02_packetCappedAtLv);
		registerTest(event, "tc_gen001_sta01_lit_state_tracks_burning", 40, true,
				GeneratorScenarios::tcGen001Sta01_litStateTracksBurning);

		// Recipe coverage: every family loads and the key crafts resolve.
		registerTest(event, "tc_recie01_maceration_recipes_all_load", 40, true,
				RecipeCoverageScenarios::tcRecie01_macerationRecipesAllLoad);
		registerTest(event, "tc_recie02_smelting_recipes_all_load", 40, true,
				RecipeCoverageScenarios::tcRecie02_smeltingRecipesAllLoad);
		registerTest(event, "tc_recie03_compressing_recipes_all_load", 40, true,
				RecipeCoverageScenarios::tcRecie03_compressingRecipesAllLoad);
		registerTest(event, "tc_recie04_extracting_recipes_all_load", 40, true,
				RecipeCoverageScenarios::tcRecie04_extractingRecipesAllLoad);
		registerTest(event, "tc_recie11_distilling_recipe_family_registered", 40, true,
				RecipeCoverageScenarios::tcRecie11_distillingRecipeFamilyRegistered);
		registerTest(event, "tc_recie05_machine_casing_resolves", 40, true,
				RecipeCoverageScenarios::tcRecie05_machineCasingResolves);
		registerTest(event, "tc_recie06_macerator_resolves", 40, true,
				RecipeCoverageScenarios::tcRecie06_maceratorResolves);
		registerTest(event, "tc_recie07_extractor_resolves", 40, true,
				RecipeCoverageScenarios::tcRecie07_extractorResolves);
		registerTest(event, "tc_recie08_compressor_resolves", 40, true,
				RecipeCoverageScenarios::tcRecie08_compressorResolves);
		registerTest(event, "tc_recie09_silver_plate_block_resolves", 40, true,
				RecipeCoverageScenarios::tcRecie09_silverPlateBlockResolves);
		registerTest(event, "tc_recie10_tempered_iron_plate_block_resolves", 40, true,
				RecipeCoverageScenarios::tcRecie10_temperedIronPlateBlockResolves);

		// Network analyzer (MOD-047): traversal through storage, stop conditions, traversal cap.
		registerTest(event, "mod047_traverse_bridges_battery_box", 40, true,
				NetworkAnalyzerScenarios::mod047_traverseBridgesBatteryBox);
		registerTest(event, "mod047_stop_at_storage_stays_in_segment", 40, true,
				NetworkAnalyzerScenarios::mod047_stopAtStorageStaysInSegment);
		registerTest(event, "mod047_traverse_cap_flags_limit", 40, true,
				NetworkAnalyzerScenarios::mod047_traverseCapFlagsLimit);

		// Teleporter station: buffer, privacy, drops, role in the network.
		registerTest(event, "tc_tele001_fun02_buffer_matches_config", 40, true,
				TeleporterStationScenarios::tcTele001Fun02_bufferMatchesConfig);
		registerTest(event, "tc_tele001_fun03_hv_intake_rate", 40, true,
				TeleporterStationScenarios::tcTele001Fun03_hvIntakeRate);
		registerTest(event, "tc_tele001_per01_state_survives_nbt", 40, true,
				TeleporterStationScenarios::tcTele001Per01_stateSurvivesNbt);
		registerTest(event, "tc_tele001_per02_defaults_to_private", 40, true,
				TeleporterStationScenarios::tcTele001Per02_defaultsToPrivate);
		registerTest(event, "tc_tele001_brk07_drop_carries_energy_and_privacy_but_not_owner", 40, true,
				TeleporterStationScenarios::tcTele001Brk07_dropCarriesEnergyAndPrivacyButNotOwner);
		registerTest(event, "tc_tele001_sec01_privacy_gate", 40, true,
				TeleporterStationScenarios::tcTele001Sec01_privacyGate);
		registerTest(event, "tc_tele001_con01_is_a_storage_sink", 40, true,
				TeleporterStationScenarios::tcTele001Con01_isAStorageSink);
		registerTest(event, "tc_tele001_con02_does_not_starve_machines", 40, true,
				TeleporterStationScenarios::tcTele001Con02_doesNotStarveMachines);

		// Server side of the teleporter screens: index bounds, list limit, permissions.
		registerTest(event, "tc_tele003_sec01_index_bounds_are_exact", 40, true,
				TeleporterGuiScenarios::tcTele003Sec01_indexBoundsAreExact);
		registerTest(event, "tc_tele003_sec02_rename_clamps_name", 40, true,
				TeleporterGuiScenarios::tcTele003Sec02_renameClampsName);
		registerTest(event, "tc_tele003_sec04_wire_carries_legacy_length_names", 40, true,
				TeleporterGuiScenarios::tcTele003Sec04_wireCarriesLegacyLengthNames);
		registerTest(event, "tc_tele003_fun01_list_is_immutable", 40, true,
				TeleporterGuiScenarios::tcTele003Fun01_listIsImmutable);
		registerTest(event, "tc_tele003_fun02_dedupe_by_dim_and_pos", 40, true,
				TeleporterGuiScenarios::tcTele003Fun02_dedupeByDimAndPos);
		registerTest(event, "tc_tele003_fun03_limit_is_the_configured_one", 40, true,
				TeleporterGuiScenarios::tcTele003Fun03_limitIsTheConfiguredOne);
		registerTest(event, "tc_tele003_fun05_auto_names_take_the_lowest_free_number", 40, true,
				TeleporterGuiScenarios::tcTele003Fun05_autoNamesTakeTheLowestFreeNumber);
		registerTest(event, "tc_tele003_sec03_only_owner_toggles_privacy", 40, true,
				TeleporterGuiScenarios::tcTele003Sec03_onlyOwnerTogglesPrivacy);
		registerTest(event, "tc_tele003_fun04_menu_reads_the_held_remote", 40, true,
				TeleporterGuiScenarios::tcTele003Fun04_menuReadsTheHeldRemote);

		// Teleporter jump: cost formula, policy gate, warmup.
		registerTest(event, "tc_tele002_fun01_cost_formula", 40, true,
				TeleporterJumpScenarios::tcTele002Fun01_costFormula);
		registerTest(event, "tc_tele002_fun02_weight_ignores_armour", 40, true,
				TeleporterJumpScenarios::tcTele002Fun02_weightIgnoresArmour);
		registerTest(event, "tc_tele002_sec01_policy_gate", 40, true,
				TeleporterJumpScenarios::tcTele002Sec01_policyGate);
		registerTest(event, "tc_tele002_nrg01_failed_jump_costs_nothing", 40, true,
				TeleporterJumpScenarios::tcTele002Nrg01_failedJumpCostsNothing);
		registerTest(event, "tc_tele002_fun03_success_moves_and_charges", 40, true,
				TeleporterJumpScenarios::tcTele002Fun03_successMovesAndCharges);
		registerTest(event, "tc_tele002_nrg02_jump_counts_as_spending", 40, true,
				TeleporterJumpScenarios::tcTele002Nrg02_jumpCountsAsSpending);
		registerTest(event, "tc_tele002_nrg03_refused_jump_books_no_spending", 40, true,
				TeleporterJumpScenarios::tcTele002Nrg03_refusedJumpBooksNoSpending);
		registerTest(event, "tc_tele002_sec02_remote_binds_to_owner", 40, true,
				TeleporterJumpScenarios::tcTele002Sec02_remoteBindsToOwner);
		registerTest(event, "tc_tele002_sta01_warmup_state_starts_clean", 40, true,
				TeleporterJumpScenarios::tcTele002Sta01_warmupStateStartsClean);

		// Ores (MOD-310): drops, tier gate, hardness, hitbox, fire resistance.
		registerTest(event, "tc_ore001_brk01_drops_itself_with_pickaxe", 40, true,
				OreScenarios::tcOre001Brk01_dropsItselfWithPickaxe);
		registerTest(event, "tc_ore001_brk03_no_drop_by_hand_axe_or_shovel", 40, true,
				OreScenarios::tcOre001Brk03_noDropByHandAxeOrShovel);
		registerTest(event, "tc_ore001_brk02_pickaxe_tier_gate", 40, true,
				OreScenarios::tcOre001Brk02_pickaxeTierGate);
		registerTest(event, "tc_ore001_brk04_fortune_and_silk_touch_neutral", 40, true,
				OreScenarios::tcOre001Brk04_fortuneAndSilkTouchNeutral);
		registerTest(event, "tc_ore001_brk05_hardness_stone_vs_deepslate", 40, true,
				OreScenarios::tcOre001Brk05_hardnessStoneVsDeepslate);
		registerTest(event, "tc_ore001_phy02_full_cube_hitbox", 40, true,
				OreScenarios::tcOre001Phy02_fullCubeHitbox);
		registerTest(event, "tc_ore001_phy04_non_flammable", 40, true,
				OreScenarios::tcOre001Phy04_nonFlammable);

		// Silence chip: active slot, persistence, slot filters, drops.
		registerTest(event, "mute_chip_is_muted_reflects_active_slot", 40, true,
				MuteChipScenarios::muteChip_isMutedReflectsActiveSlot);
		registerTest(event, "mute_chip_persists_across_nbt_round_trip", 40, true,
				MuteChipScenarios::muteChip_persistsAcrossNbtRoundTrip);
		registerTest(event, "mute_chip_hoppers_cannot_reach_upgrade_slots", 40, true,
				MuteChipScenarios::muteChip_hoppersCannotReachUpgradeSlots);
		registerTest(event, "mute_chip_drops_on_break", 40, true,
				MuteChipScenarios::muteChip_dropsOnBreak);
		registerTest(event, "mute_chip_drops_from_formerly_slotless_machine", 40, true,
				MuteChipScenarios::muteChip_dropsFromFormerlySlotlessMachine);
		registerTest(event, "mute_chip_menu_slot_filter_and_quick_move", 40, true,
				MuteChipScenarios::muteChip_menuSlotFilterAndQuickMove);
		registerTest(event, "mute_chip_crafting_recipes_resolve", 40, true,
				MuteChipScenarios::muteChip_craftingRecipesResolve);

		// Fluid tank: contents codec, NBT + component, item atomicity.
		registerTest(event, "tc_fluid_tank001_fun02_glass_wall_stops_a_click", 40, true,
				FluidTankScenarios::tcFluidTank001Fun02_glassWallStopsAClick);
		registerTest(event, "tc_fluid_tank001_dat01_contents_codec_round_trips", 40, true,
				FluidTankScenarios::tcFluidTank001Dat01_contentsCodecRoundTrips);
		registerTest(event, "tc_fluid_tank001_per01_nbt_and_component_round_trip", 40, true,
				FluidTankScenarios::tcFluidTank001Per01_nbtAndComponentRoundTrip);
		registerTest(event, "tc_fluid_tank001_safe01_filled_item_is_atomic_and_unstackable", 40, true,
				FluidTankScenarios::tcFluidTank001Safe01_filledItemIsAtomicAndUnstackable);
		registerTest(event, "tc_fluid_tank001_bva01_component_amount_clamps_to_capacity", 40, true,
				FluidTankScenarios::tcFluidTank001Bva01_componentAmountClampsToCapacity);
		registerTest(event, "tc_fluid_tank001_per02_placing_a_filled_tank_keeps_its_contents", 40, true,
				FluidTankScenarios::tcFluidTank001Per02_placingAFilledTankKeepsItsContents);
		registerTest(event, "tc_fluid_tank001_fun01_bucket_and_capsule_use_real_click_routing", 40, true,
				FluidTankScenarios::tcFluidTank001Fun01_bucketAndCapsuleUseRealClickRouting);
		registerTest(event, "tc_fluid_tank001_fun03_mod_fluid_bucket_round_trips_and_mob_bucket_is_refused",
				40, true,
				FluidTankScenarios::tcFluidTank001Fun03_modFluidBucketRoundTripsAndMobBucketIsRefused);

		// End-to-end content invariants: block registry, loot, the /ala command, c: tags.
		registerTest(event, "every_block_places_and_breaks", 40, true,
				AlaCommonScenarios::everyBlockPlacesAndBreaks);
		registerTest(event, "network_tick_guard_isolates_throws", 40, true,
				AlaCommonScenarios::networkTickGuardIsolatesThrows);
		registerTest(event, "every_block_drops_itself", 40, true,
				AlaCommonScenarios::everyBlockDropsItself);
		registerTest(event, "every_block_no_drop_by_hand", 40, true,
				AlaCommonScenarios::everyBlockNoDropByHand);
		registerTest(event, "block_standards_all_blocks", 40, true,
				AlaCommonScenarios::blockStandardsAllBlocks);
		registerTest(event, "ala_command_registered", 40, true,
				AlaCommonScenarios::alaCommandRegistered);
		registerTest(event, "ores_in_convention_tags", 40, true,
				AlaCommonScenarios::oresInConventionTags);
		// MOD-335 guard: fails deterministically if RIG_STRUCTURE is ever shrunk back to a 1x1x1
		// template, which silently made every drop count in this lane unreliable.
		registerTest(event, "gametest_rig_structure_fits_rigs", 40, true,
				AlaCommonScenarios::gametestRigStructureFitsRigs);
	}

	/**
	 * Structure template every scenario is placed on: 8x8x8 of air, shipped in this source set as
	 * {@code data/alaindustrial/structure/gametest_rig.nbt}.
	 *
	 * <p><b>Why not {@code minecraft:empty} (MOD-335).</b> The vanilla template is 1x1x1, and the
	 * engine sizes three things off the STRUCTURE box, not off what a scenario body actually writes:
	 * <ul>
	 *   <li>{@code TestInstanceBlockEntity.forceLoadChunks()} force-loads only the chunks intersecting
	 *       {@code getStructureBoundingBox()};</li>
	 *   <li>{@code GameTestInfo} waits for only those chunks to be entity-ticking before running;</li>
	 *   <li>{@code StructureGridSpawner} spaces the grid by {@code getTestBounds().getXsize() + 5}
	 *       (rows: {@code + 6}), so a 1x1x1 template packed tests 6 blocks apart.</li>
	 * </ul>
	 * Our rigs are up to 5x5 (the scythe/trellis platforms), so a rig whose origin landed late in a
	 * chunk straddled the border and dropped its items into a chunk this lane never loaded —
	 * {@code getEntitiesOfClass} then counted zero and the test failed with "no drop". It reproduced
	 * on exactly the tests whose origin had {@code z % 16 == 14} (rig z spans 14..18, crossing 16),
	 * which is why it looked random: {@code GameTestServer} picks a RANDOM world origin per run.
	 *
	 * <p>8x8x8 + {@code padding = 1} mirrors Fabric's {@code fabric-gametest-api-v1:empty} (same size,
	 * same default padding) — the reason the Fabric lane never saw this failure on identical bodies.
	 */
	private static final Identifier RIG_STRUCTURE = Industrialization.id("gametest_rig");

	/**
	 * Padding around the structure. Widens grid spacing and, importantly, extends
	 * {@code clearSpaceForStructure} + {@code removeEntities} so a neighbour's leftover drops are
	 * cleared before this test runs. Matches the Fabric annotation default.
	 */
	private static final int RIG_PADDING = 1;

	/** Register one code-body scenario under the alaindustrial namespace with a sane maxTicks. */
	private static void registerTest(RegisterGameTestsEvent event, String name, int maxTicks, boolean required,
			Consumer<GameTestHelper> body) {
		TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
				emptyEnv,
				RIG_STRUCTURE,
				maxTicks,
				0,          // setupTicks
				required,
				Rotation.NONE,
				false,      // manualOnly
				1,          // maxAttempts — a retry would only mask a real defect
				1,          // requiredSuccesses
				false,      // skyAccess
				RIG_PADDING);
		event.registerTest(Industrialization.id(name), new CodeGameTestInstance(body, data));
	}
}
