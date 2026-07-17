package dev.alaindustrial.gametest.neoforge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.CoreEnergyScenarios;
import dev.alaindustrial.gametest.CoreFluidScenarios;
import dev.alaindustrial.gametest.CapsuleScenarios;
import dev.alaindustrial.gametest.GeothermalLavaInputScenarios;
import dev.alaindustrial.gametest.ElectricDrillScenarios;
import dev.alaindustrial.gametest.EnergyPackScenarios;
import dev.alaindustrial.gametest.PouchScenarios;
import dev.alaindustrial.gametest.ScytheScenarios;
import dev.alaindustrial.gametest.StockDisplayFrameScenarios;
import dev.alaindustrial.gametest.TemperedIronToolScenarios;
import dev.alaindustrial.gametest.ItemPipeScenarios;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
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
		// (dev.alaindustrial.gametest.CoreEnergyScenarios), so Fabric and NeoForge exercise the same core.
		// MOD-022 recipe seam: proves the machine RecipeType/RecipeSerializer register + resolve on NeoForge
		// (the frozen-registry fix — ModRecipesNeoForge). Fabric covers this via MachineGameTest; this is the
		// NeoForge world lane's first recipe-processing case.
		registerTest(event, "macerator_processes_recipe", 420, true,
				CoreEnergyScenarios::maceratorProcessesRecipe);
		// MOD-022 data-component seam: a charged battery box carries STORED_ENERGY on drop (frozen-registry
		// fix — ModDataComponentsNeoForge). Fabric covers this via BatteryBoxGameTest; NeoForge world lane's first.
		registerTest(event, "battery_box_drop_carries_energy", 40, true,
				CoreEnergyScenarios::batteryBoxDropCarriesEnergy);
		// Teleporter station (MOD-091): its face roles and its teleporter_private data component are
		// per-loader seams (energy adapter + frozen-registry), so the Fabric suite cannot vouch for them.
		registerTest(event, "teleporter_face_roles", 40, true,
				CoreEnergyScenarios::teleporterFaceRoles);
		registerTest(event, "teleporter_drop_carries_privacy", 40, true,
				CoreEnergyScenarios::teleporterDropCarriesPrivacy);
		registerTest(event, "generator_charges_adjacent_box", 40, true,
				CoreEnergyScenarios::generatorChargesAdjacentBox);
		registerTest(event, "full_neighbour_no_leak", 40, true,
				CoreEnergyScenarios::fullNeighbourNoLeak);
		registerTest(event, "generator_delivers_down_cable", 200, true,
				CoreEnergyScenarios::generatorDeliversDownCable);
		registerTest(event, "return_round_robin_no_leak", 40, true,
				CoreEnergyScenarios::returnRoundRobinNoLeak);
		registerTest(event, "mod009_battery_box_charges_to_full", 80, true,
				CoreEnergyScenarios::mod009BatteryBoxChargesToFull);
		// MOD-021 cable-loss on NeoForge: REQUIRED regression guard. This world lane surfaced (and the
		// adapter fix in 7e673dc7 / MOD-022 closed) a NeoForge-only energy-core defect the JUnit adapter
		// tests could not: NeoForgeEnergyLookup.find() had hardcoded EnergyRole.BOTH, and
		// NeoForgeEnergyPort.supportsInsertion/Extraction derived from that role, so EVERY face reported
		// supporting BOTH insertion and extraction regardless of its real per-face role. EnergyNetwork.
		// refreshEndpoints therefore classified every storage/consumer as a producer too, so
		// computeConsumerDistances seeded cable-distance 1 at every consumer and the MOD-021 loss floored
		// to 0 (the box gained a full lossless 32 EU/tick). insert()/extract() still gated correctly on
		// role via the block-side FaceEnergyPort, so delivery worked — only the classification-only
		// supports* predicates were wrong. Root cause: the per-face role was lost across the
		// Capabilities.Energy.BLOCK round-trip (EnergyHandler had no supports* predicate), fixed in the
		// NeoForge energy adapter. The scenario body now guards against regression (fails on lossless
		// delivery), so a reappearance of the defect will fail this required test. Fabric's
		// tcCable001Nrg02 passes because EnergyStorage.supports* reflect the real per-face capability.
		registerTest(event, "mod021_loss_over_ten_cables", 100, true,
				CoreEnergyScenarios::mod021LossOverTenCables);
		registerTest(event, "nbt_round_trip_preserves_state", 40, true,
				CoreEnergyScenarios::nbtRoundTripPreservesState);
		// MOD-025: ring network union-find merge on cycle, proven on both loaders (loader-neutral body
		// in common/.../CoreEnergyScenarios — NetworkManager/EnergyNetwork are shared, not per-loader).
		registerTest(event, "ring_network_merges_on_close", 200, true,
				CoreEnergyScenarios::ringNetworkMergesOnClose);
		// Phase-03 LV expansion: wind mill world-tested on NeoForge (passive LV generator, cable-less
		// push path). Loader-neutral body in common/.../CoreEnergyScenarios. (Water-mill scenarios removed:
		// block is hidden/not ready — tests deleted until it ships.)
		registerTest(event, "wind_mill_charges_adjacent_box", 40, true,
				CoreEnergyScenarios::windMillChargesAdjacentBox);

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
				CoreEnergyScenarios::machineNoPowerNoOutput);
		registerTest(event, "machine_full_output_jams", 420, true,
				CoreEnergyScenarios::machineFullOutputJams);
		registerTest(event, "machine_input_swap_resets_progress", 200, true,
				CoreEnergyScenarios::machineInputSwapResetsProgress);

		// Battery box rate BVA: the buffer's per-tick insert/extract caps must be EXACTLY LV.maxVoltage().
		// Loader-neutral (asserts the shared EnergyBuffer fields directly), complements the Fabric lane's
		// capability-view rate tests (BatteryBoxGameTest PRF03/Prf04).
		registerTest(event, "battery_box_rate_exact_lv", 40, true,
				CoreEnergyScenarios::batteryBoxRateExactLv);

		// Solar panel day/night generation (R-NRG-15). The day case asserts the exact rate × ticks; the
		// night case asserts 0 EU (a brightness-read regression that leaks day gen into night fails here).
		registerTest(event, "solar_panel_generates_by_day", 60, true,
				CoreEnergyScenarios::solarPanelGeneratesByDay);
		registerTest(event, "solar_panel_no_eu_at_night", 60, true,
				CoreEnergyScenarios::solarPanelNoEuAtNight);

		// Geothermal generator: exact EU rate from a lava bucket (catches a halved/doubled conversion
		// factor) + no-lava negative (catches unconditional generation).
		registerTest(event, "geothermal_lava_bucket_rate", 60, true,
				CoreEnergyScenarios::geothermalLavaBucketRate);
		registerTest(event, "geothermal_no_lava_no_eu", 60, true,
				CoreEnergyScenarios::geothermalNoLavaNoEu);

		// Cable network negatives: diagonal cables must not merge (R-CON-06); a vanilla furnace neighbour
		// must not NPE during endpoint discovery (loader-capability null-safety).
		registerTest(event, "diagonal_cables_do_not_connect", 40, true,
				CoreEnergyScenarios::diagonalCablesDoNotConnect);
		registerTest(event, "cable_vanilla_neighbor_no_npe", 100, true,
				CoreEnergyScenarios::cableVanillaNeighborNoNpe);

		// NBT persistence round-trip for the electric furnace (MachineBlockEntity save/load path on the
		// NeoForge lane for a machine other than the macerator already covered above).
		registerTest(event, "furnace_nbt_round_trip", 40, true,
				CoreEnergyScenarios::furnaceNbtRoundTrip);

		// Wind mill: thunder multiplies the rate on a raised rig (weather wiring end-to-end).
		registerTest(event, "wind_mill_thunder_multiplies_rate", 120, true,
				CoreEnergyScenarios::windMillThunderMultipliesRate);

		// Generator: full buffer pauses burn (R-NRG-11, no fuel waste) + exact EU/t rate (canon 8).
		registerTest(event, "generator_full_buffer_pauses_burn", 40, true,
				CoreEnergyScenarios::generatorFullBufferPausesBurn);
		registerTest(event, "generator_rate_per_tick_matches_config", 40, true,
				CoreEnergyScenarios::generatorRatePerTickMatchesConfig);

		// Battery box conservation: genDrain == boxGain on a partial-consumer cable path (no leak).
		registerTest(event, "battery_box_conservation_partial_consumer", 80, true,
				CoreEnergyScenarios::batteryBoxConservationPartialConsumer);

		// Moonlit panel: night generation at exact moonlitEuPerTick × multiplier.
		registerTest(event, "moonlit_panel_generates_at_night", 60, true,
				CoreEnergyScenarios::moonlitPanelGeneratesAtNight);

		// Machine positive recipes: compressor (dust->ingot), extractor (gravel->flint), furnace
		// (raw_iron->ingot). Each proves that machine's recipe-type resolves on the NeoForge lane.
		registerTest(event, "compressor_makes_copper_ingot", 420, true,
				CoreEnergyScenarios::compressorMakesCopperIngot);
		registerTest(event, "extractor_makes_flint", 420, true,
				CoreEnergyScenarios::extractorMakesFlint);
		registerTest(event, "furnace_smelts_raw_iron", 420, true,
				CoreEnergyScenarios::furnaceSmeltsRawIron);

		// Pump: source -> tank -> sink (geo) -> EU. Fluid transport end-to-end on the NeoForge lane.
		registerTest(event, "pump_source_to_tank_to_sink_to_eu", 100, true,
				CoreEnergyScenarios::pumpSourceToTankToSinkToEu);

		// Machine negatives parametric across all 4 machines (furnace/compressor/extractor): no-recipe
		// no-EU, full-output jam, input-swap reset. Closes the per-machine gap with MachineGameTest.
		registerTest(event, "furnace_non_recipe_no_eu_spent", 200, true,
				CoreEnergyScenarios::furnaceNonRecipeNoEuSpent);
		registerTest(event, "compressor_full_output_jams", 420, true,
				CoreEnergyScenarios::compressorFullOutputJams);
		registerTest(event, "extractor_input_swap_resets_progress", 200, true,
				CoreEnergyScenarios::extractorInputSwapResetsProgress);

		// Daylight solar: day generation at exact daylightEuPerTick + no EU at night (day-only).
		registerTest(event, "daylight_panel_generates_by_day", 60, true,
				CoreEnergyScenarios::daylightPanelGeneratesByDay);
		registerTest(event, "daylight_panel_no_eu_at_night", 60, true,
				CoreEnergyScenarios::daylightPanelNoEuAtNight);

		// Extra machine recipes: extractor ×3 blaze_powder, furnace vanilla fallback (sand→glass),
		// compressor iron_dust→iron_ingot.
		registerTest(event, "extractor_blaze_rod_to_powder", 420, true,
				CoreEnergyScenarios::extractorBlazeRodToPowder);
		registerTest(event, "furnace_smelts_sand_to_glass", 420, true,
				CoreEnergyScenarios::furnaceSmeltsSandToGlass);
		registerTest(event, "compressor_iron_dust_to_ingot", 420, true,
				CoreEnergyScenarios::compressorIronDustToIngot);

		// Machine E_op BVA: macerator exact (completes, amount==0) + E_op−1 (stalls at duration−1).
		registerTest(event, "macerator_eop_exact_completes", 420, true,
				CoreEnergyScenarios::maceratorEopExactCompletes);
		registerTest(event, "macerator_eop_minus_one_stalls", 420, true,
				CoreEnergyScenarios::maceratorEopMinusOneStalls);

		// Generator/solar buffer cap-1 BVA (R-NRG-01): tops off to exactly cap from cap-1.
		registerTest(event, "generator_buffer_caps_at_max_bva", 40, true,
				CoreEnergyScenarios::generatorBufferCapsAtMaxBva);
		registerTest(event, "solar_panel_buffer_caps_at_max_bva", 60, true,
				CoreEnergyScenarios::solarPanelBufferCapsAtMaxBva);

		// Solar evolution: day chip → daylight panel after solarEvolveTicks.
		registerTest(event, "solar_day_chip_evolves_to_daylight", 200, true,
				CoreEnergyScenarios::solarDayChipEvolvesToDaylight);

		// Ore tier-gate (R-BRK-09): wooden too low for all 8; stone OK for tin/silver/nickel,
		// iron required for uranium.
		registerTest(event, "ore_wooden_pickaxe_no_drop", 40, true,
				CoreEnergyScenarios::oreWoodenPickaxeNoDrop);
		registerTest(event, "ore_stone_pickaxe_tier_gate", 40, true,
				CoreEnergyScenarios::oreStonePickaxeTierGate);

		// Network split + rejoin (R-CON-04, R-CON-09): break stops delivery, replace resumes flow.
		registerTest(event, "network_split_rejoin_resumes_flow", 200, true,
				CoreEnergyScenarios::networkSplitRejoinResumesFlow);

		// Geothermal tank droplet↔MB boundary: one lava bucket = exactly 1000 mB.
		registerTest(event, "geothermal_tank_bucket_boundary", 40, true,
				CoreEnergyScenarios::geothermalTankBucketBoundary);

		// Generator rejects external EU (producer-only, R-NRG-03): maxInsert == 0.
		registerTest(event, "generator_rejects_external_eu", 40, true,
				CoreEnergyScenarios::generatorRejectsExternalEu);

		// Battery box half-charge drop carries EU (R-BRK-07, second charge value).
		registerTest(event, "battery_box_drop_carries_energy_half_charge", 40, true,
				CoreEnergyScenarios::batteryBoxDropCarriesEnergyHalfCharge);

		// Cable throughput cap ≤ LV.maxVoltage per tick (R-NRG-04, no overvoltage).
		registerTest(event, "cable_throughput_capped_at_lv", 40, true,
				CoreEnergyScenarios::cableThroughputCappedAtLv);

		// Macerator lit blockstate tracks active/idle (R-VIS-01).
		registerTest(event, "macerator_lit_tracks_active", 420, true,
				CoreEnergyScenarios::maceratorLitTracksActive);

		// Extractor multi-output recipes: cactus ×2 green_dye, pumpkin ×5 pumpkin_seeds.
		registerTest(event, "extractor_cactus_to_green_dye", 420, true,
				CoreEnergyScenarios::extractorCactusToGreenDye);
		registerTest(event, "extractor_pumpkin_to_seeds", 420, true,
				CoreEnergyScenarios::extractorPumpkinToSeeds);

		// Macerator tag-ingredient recipe: iron_ore → ×2 iron_dust (doubling path).
		registerTest(event, "macerator_iron_ore_doubles_dust", 420, true,
				CoreEnergyScenarios::maceratorIronOreDoublesDust);

		// Wind mill roofed → 0 EU (open-sky gate, mode wiring).
		registerTest(event, "wind_mill_roofed_yields_zero", 120, true,
				CoreEnergyScenarios::windMillRoofedYieldsZero);

		// Two generators sum into one consumer (R-CON-16, no dupe/shadow).
		registerTest(event, "two_generators_sum_into_one_consumer", 60, true,
				CoreEnergyScenarios::twoGeneratorsSumIntoOneConsumer);

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
		// MOD-084: the NeoForge half of the cross-mod bridge. Loader-specific bodies (they call
		// Capabilities.Energy.ITEM), mirroring the Fabric lane's ItemEnergyCapabilityGameTest.
		registerTest(event, "xmod_foreign_charger_fills_pouch", 40, true,
				ItemEnergyCapabilityScenarios::fun01ForeignChargerFillsPouch);
		registerTest(event, "xmod_foreign_machine_cannot_drain", 40, true,
				ItemEnergyCapabilityScenarios::neg01ForeignMachineCannotDrain);
		registerTest(event, "xmod_pack_charges_foreign_item", 40, true,
				ItemEnergyCapabilityScenarios::fun02PackChargesForeignItem);
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

		// Electric Drill (MOD-079, TC-DRILL-001) — same loader-neutral bodies as the Fabric
		// ElectricDrillGameTest suite: charge slot, EU drain, hand-speed fallback, pickaxe tags, enchants.
		registerTest(event, "drill_charge_in_battery_box", 80, true, ElectricDrillScenarios::fun01ChargeInBatteryBox);
		registerTest(event, "drill_drain_on_mine_block", 40, true, ElectricDrillScenarios::fun02DrainOnMineBlock);
		registerTest(event, "drill_no_drain_below_cost", 40, true, ElectricDrillScenarios::fun03NoDrainBelowCost);
		registerTest(event, "drill_speed_and_drops", 40, true, ElectricDrillScenarios::fun04SpeedAndDrops);
		registerTest(event, "drill_no_drain_on_zero_hardness", 40, true, ElectricDrillScenarios::fun05NoDrainOnZeroHardness);
		registerTest(event, "drill_tags_and_enchants", 40, true, ElectricDrillScenarios::fun06TagsAndEnchants);
		registerTest(event, "drill_charge_round_trip", 40, true, ElectricDrillScenarios::per01ChargeRoundTrip);
		// MOD-089 torch placement (TC-DRILL-001-FUN07/08/09): right-click places a torch from the inventory,
		// prefers the enriched uranium torch, and still consumes when the clicked block is replaceable.
		registerTest(event, "drill_place_torch_from_inventory", 40, true, ElectricDrillScenarios::fun07PlaceTorchFromInventory);
		registerTest(event, "drill_torch_priority_uranium", 40, true, ElectricDrillScenarios::fun08TorchPriorityUranium);
		registerTest(event, "drill_place_torch_on_replaceable_block", 40, true, ElectricDrillScenarios::fun09PlaceTorchOnReplaceableBlock);

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
		registerTest(event, "scythe_netherite_fire_resistant", 40, true, ScytheScenarios::con02NetheriteFireResistant);

		// MOD-104: exact end-to-end item movement through native item APIs and the disabled-face gate.
		registerTest(event, "item_pipe_transfers_between_chests", 40, true, ItemPipeScenarios::transfersBetweenChests);
		registerTest(event, "item_pipe_disabled_face_blocks_transfer", 40, true, ItemPipeScenarios::disabledFaceBlocksTransfer);
		registerTest(event, "item_pipe_disabled_link_blocks_transfer", 40, true,
				ItemPipeScenarios::disabledPipeLinkBlocksTransfer);
		// MOD-108: chest → pipe → MACHINE. The chest-to-chest cases above never touch a machine's
		// automation gate (canPlaceItemThroughFace), which is exactly the path players build.
		registerTest(event, "item_pipe_inserts_into_machine", 40, true, ItemPipeScenarios::insertsIntoMachine);
		// MOD-108 balance guard: one batch per interval, not per tick (MOD-104 shipped 20 items/s).
		registerTest(event, "item_pipe_respects_transfer_interval", 40, true, ItemPipeScenarios::respectsTransferInterval);
		// MOD-108: vanilla chests — the seam a player actually builds with.
		registerTest(event, "item_pipe_vanilla_chests", 40, true, ItemPipeScenarios::transfersBetweenVanillaChests);
		// MOD-108: shift-click with the wrench dismantles our blocks and leaves foreign ones alone.
		registerTest(event, "wrench_dismantles_own_blocks", 40, true, ItemPipeScenarios::wrenchDismantlesOwnBlocks);
	}

	/** Register one code-body scenario under the alaindustrial namespace with a sane maxTicks. */
	private static void registerTest(RegisterGameTestsEvent event, String name, int maxTicks, boolean required,
			Consumer<GameTestHelper> body) {
		TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
				emptyEnv,
				Identifier.withDefaultNamespace("empty"), // minecraft:empty structure (1x1x1), ships in vanilla data
				maxTicks,
				0,          // setupTicks
				required);
		event.registerTest(Industrialization.id(name), new CodeGameTestInstance(body, data));
	}
}
