package dev.alaindustrial.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * World scenarios for the processing machines, under the names both lanes run (MOD-446) — macerator,
 * electric furnace, compressor, extractor, sawmill and recycler. The cases that observe a machine through the
 * Fabric Transfer/Energy API ({@code Transaction} insert, {@code EnergyStorage.SIDED}) stay in the Fabric
 * {@code MachineGameTest}: they test that loader's seam, not the machine.
 *
 * <p><b>A facade since MOD-717 (TST-2).</b> The two lanes run these names, so they stay; each scenario
 * delegates to the class of its mechanic — {@link MachineRecipeScenarios}, {@link MachineFaultScenarios},
 * {@link MachineOperationScenarios}, {@link SawmillScenarios}, {@link RecyclerScenarios} — and the rig lives
 * in {@link MachineRig}. A new processing-machine scenario goes into the class of its mechanic and joins the
 * roster of that class, not this one.
 */
public final class MachineScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MachineScenarios::tcMach001Fun01_maceratorGrindsRawIron,
								"machine_tc_mach001_fun01_macerator_grinds_raw_iron")
						.fabricId("MachineGameTest", "tcMach001Fun01_maceratorGrindsRawIron").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001FunIronOre_maceratorGrindsIronOre,
								"machine_tc_mach001_fun_iron_ore_macerator_grinds_iron_ore")
						.fabricId("MachineGameTest", "tcMach001FunIronOre_maceratorGrindsIronOre").ticks(20, 40),
				RosterEntry.of(MachineScenarios::mod245_maceratorGrindsSulfurOre,
								"machine_mod245_macerator_grinds_sulfur_ore")
						.fabricId("MachineGameTest", "mod245_maceratorGrindsSulfurOre").ticks(20, 40),
				RosterEntry.of(MachineScenarios::mod245_maceratorGrindsDeepslateSulfurOre,
								"machine_mod245_macerator_grinds_deepslate_sulfur_ore")
						.fabricId("MachineGameTest", "mod245_maceratorGrindsDeepslateSulfurOre").ticks(20, 40),
				RosterEntry.of(MachineScenarios::mod245_maceratorGrindsRawSulfur,
								"machine_mod245_macerator_grinds_raw_sulfur")
						.fabricId("MachineGameTest", "mod245_maceratorGrindsRawSulfur").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach002Fun01_furnaceSmeltsRawIron,
								"machine_tc_mach002_fun01_furnace_smelts_raw_iron")
						.fabricId("MachineGameTest", "tcMach002Fun01_furnaceSmeltsRawIron").ticks(20, 40),
				RosterEntry.of(MachineScenarios::mod245_furnaceSmeltsRawSulfur,
								"machine_mod245_furnace_smelts_raw_sulfur")
						.fabricId("MachineGameTest", "mod245_furnaceSmeltsRawSulfur").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach003Fun01_compressorMakesBrick,
								"machine_tc_mach003_fun01_compressor_makes_brick")
						.fabricId("MachineGameTest", "tcMach003Fun01_compressorMakesBrick").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach004Fun01_extractorMakesBlazePowder,
								"machine_tc_mach004_fun01_extractor_makes_blaze_powder")
						.fabricId("MachineGameTest", "tcMach004Fun01_extractorMakesBlazePowder").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Neg01_noPowerNoOutput,
								"machine_tc_mach001_neg01_no_power_no_output")
						.fabricId("MachineGameTest", "tcMach001Neg01_noPowerNoOutput").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Neg02_nonRecipeNoOutput,
								"machine_tc_mach001_neg02_non_recipe_no_output")
						.fabricId("MachineGameTest", "tcMach001Neg02_nonRecipeNoOutput").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach002Neg01_furnaceNoPower,
								"machine_tc_mach002_neg01_furnace_no_power")
						.fabricId("MachineGameTest", "tcMach002Neg01_furnaceNoPower").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Con01_sidedSlotRoles,
								"machine_tc_mach001_con01_sided_slot_roles")
						.fabricId("MachineGameTest", "tcMach001Con01_sidedSlotRoles").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Prf_maceratorEopMatchesConfig,
								"machine_tc_mach001_prf_macerator_eop_matches_config")
						.fabricId("MachineGameTest", "tcMach001Prf_maceratorEopMatchesConfig").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Neg03_fullOutputJamsMachine,
								"machine_tc_mach001_neg03_full_output_jams_machine")
						.fabricId("MachineGameTest", "tcMach001Neg03_fullOutputJamsMachine").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001FunCopperRaw_maceratorGrindsRawCopper,
								"machine_tc_mach001_fun_copper_raw_macerator_grinds_raw_copper")
						.fabricId("MachineGameTest", "tcMach001FunCopperRaw_maceratorGrindsRawCopper").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001FunGoldRaw_maceratorGrindsRawGold,
								"machine_tc_mach001_fun_gold_raw_macerator_grinds_raw_gold")
						.fabricId("MachineGameTest", "tcMach001FunGoldRaw_maceratorGrindsRawGold").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001FunIronIngot_maceratorGrindsIronIngot,
								"machine_tc_mach001_fun_iron_ingot_macerator_grinds_iron_ingot")
						.fabricId("MachineGameTest", "tcMach001FunIronIngot_maceratorGrindsIronIngot").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcEfurn001Fun01_furnaceSmeltsIronDust,
								"machine_tc_efurn001_fun01_furnace_smelts_iron_dust")
						.fabricId("MachineGameTest", "tcEfurn001Fun01_furnaceSmeltsIronDust").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef,
								"machine_tc_efurn001_fun02_furnace_vanilla_fallback_cooks_beef")
						.fabricId("MachineGameTest", "tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcEfurn001Fun03a_furnaceSmeltsSand,
								"machine_tc_efurn001_fun03a_furnace_smelts_sand")
						.fabricId("MachineGameTest", "tcEfurn001Fun03a_furnaceSmeltsSand").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcEfurn001Fun03b_furnaceSmeltsCobblestone,
								"machine_tc_efurn001_fun03b_furnace_smelts_cobblestone")
						.fabricId("MachineGameTest", "tcEfurn001Fun03b_furnaceSmeltsCobblestone").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal,
								"machine_tc_efurn001_fun05_furnace_vanilla_fallback_makes_charcoal")
						.fabricId("MachineGameTest", "tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcEfurn001Fun04_furnaceDurationIsHalfVanilla,
								"machine_tc_efurn001_fun04_furnace_duration_is_half_vanilla")
						.fabricId("MachineGameTest", "tcEfurn001Fun04_furnaceDurationIsHalfVanilla").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Fun02_compressorMakesCopperIngot,
								"machine_tc_comp001_fun02_compressor_makes_copper_ingot")
						.fabricId("MachineGameTest", "tcComp001Fun02_compressorMakesCopperIngot").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Fun03_compressorMakesGoldIngot,
								"machine_tc_comp001_fun03_compressor_makes_gold_ingot")
						.fabricId("MachineGameTest", "tcComp001Fun03_compressorMakesGoldIngot").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Fun04_compressorMakesIronIngot,
								"machine_tc_comp001_fun04_compressor_makes_iron_ingot")
						.fabricId("MachineGameTest", "tcComp001Fun04_compressorMakesIronIngot").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcExtr001Fun02a_extractorMakesFlint,
								"machine_tc_extr001_fun02a_extractor_makes_flint")
						.fabricId("MachineGameTest", "tcExtr001Fun02a_extractorMakesFlint").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcExtr001Fun06_extractorMakesGreenDye,
								"machine_tc_extr001_fun06_extractor_makes_green_dye")
						.fabricId("MachineGameTest", "tcExtr001Fun06_extractorMakesGreenDye").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcExtr001Fun07_extractorMakesPumpkinSeeds,
								"machine_tc_extr001_fun07_extractor_makes_pumpkin_seeds")
						.fabricId("MachineGameTest", "tcExtr001Fun07_extractorMakesPumpkinSeeds").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Fun02_maceratorConsumesExactlyOnePerOperation,
								"machine_tc_mach001_fun02_macerator_consumes_exactly_one_per_operation")
						.fabricId("MachineGameTest", "tcMach001Fun02_maceratorConsumesExactlyOnePerOperation")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach002Fun02_furnaceConsumesExactlyOnePerOperation,
								"machine_tc_mach002_fun02_furnace_consumes_exactly_one_per_operation")
						.fabricId("MachineGameTest", "tcMach002Fun02_furnaceConsumesExactlyOnePerOperation")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach003Fun02_compressorConsumesExactlyOnePerOperation,
								"machine_tc_mach003_fun02_compressor_consumes_exactly_one_per_operation")
						.fabricId("MachineGameTest", "tcMach003Fun02_compressorConsumesExactlyOnePerOperation")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Fun05_compressorConsumesExactlyOneOfFive,
								"machine_tc_comp001_fun05_compressor_consumes_exactly_one_of_five")
						.fabricId("MachineGameTest", "tcComp001Fun05_compressorConsumesExactlyOneOfFive")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Fun15_compressorCompactsGlowstoneDust,
								"machine_tc_comp001_fun15_compressor_compacts_glowstone_dust")
						.fabricId("MachineGameTest", "tcComp001Fun15_compressorCompactsGlowstoneDust").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Fun16_compressorCompactsRedstone,
								"machine_tc_comp001_fun16_compressor_compacts_redstone")
						.fabricId("MachineGameTest", "tcComp001Fun16_compressorCompactsRedstone").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Neg07_compressorRejectsPartialGlowstoneBatch,
								"machine_tc_comp001_neg07_compressor_rejects_partial_glowstone_batch")
						.fabricId("MachineGameTest", "tcComp001Neg07_compressorRejectsPartialGlowstoneBatch")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Neg08_compressorRejectsPartialRedstoneBatch,
								"machine_tc_comp001_neg08_compressor_rejects_partial_redstone_batch")
						.fabricId("MachineGameTest", "tcComp001Neg08_compressorRejectsPartialRedstoneBatch")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Fun17_compressorCompactsCoalDustIntoCarbonRod,
								"machine_tc_comp001_fun17_compressor_compacts_coal_dust_into_carbon_rod")
						.fabricId("MachineGameTest", "tcComp001Fun17_compressorCompactsCoalDustIntoCarbonRod")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Neg09_compressorRejectsPartialCoalDustBatch,
								"machine_tc_comp001_neg09_compressor_rejects_partial_coal_dust_batch")
						.fabricId("MachineGameTest", "tcComp001Neg09_compressorRejectsPartialCoalDustBatch")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Gui06_compressorReportsPartialBatch,
								"machine_tc_comp001_gui06_compressor_reports_partial_batch")
						.fabricId("MachineGameTest", "tcComp001Gui06_compressorReportsPartialBatch").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Gui07_compressorReportsWrongItemAndJammedOutput,
								"machine_tc_comp001_gui07_compressor_reports_wrong_item_and_jammed_output")
						.fabricId("MachineGameTest", "tcComp001Gui07_compressorReportsWrongItemAndJammedOutput")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Gui08_compressorReportsStarvationButNotTrickle,
								"machine_tc_comp001_gui08_compressor_reports_starvation_but_not_trickle")
						.fabricId("MachineGameTest", "tcComp001Gui08_compressorReportsStarvationButNotTrickle")
						.ticks(20, 400),
				RosterEntry.of(MachineScenarios::tcMach004Fun02_extractorConsumesExactlyOnePerOperation,
								"machine_tc_mach004_fun02_extractor_consumes_exactly_one_per_operation")
						.fabricId("MachineGameTest", "tcMach004Fun02_extractorConsumesExactlyOnePerOperation")
						.ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach002Neg03_furnaceFullOutputJamsMachine,
								"machine_tc_mach002_neg03_furnace_full_output_jams_machine")
						.fabricId("MachineGameTest", "tcMach002Neg03_furnaceFullOutputJamsMachine").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach003Neg03_compressorFullOutputJamsMachine,
								"machine_tc_mach003_neg03_compressor_full_output_jams_machine")
						.fabricId("MachineGameTest", "tcMach003Neg03_compressorFullOutputJamsMachine").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcExtr001Neg01a_multipliedOutputFitsAt61,
								"machine_tc_extr001_neg01a_multiplied_output_fits_at61")
						.fabricId("MachineGameTest", "tcExtr001Neg01a_multipliedOutputFitsAt61").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcExtr001Neg01b_multipliedOutputJamsAt62,
								"machine_tc_extr001_neg01b_multiplied_output_jams_at62")
						.fabricId("MachineGameTest", "tcExtr001Neg01b_multipliedOutputJamsAt62").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Neg04_maceratorWrongItemInOutputNoDupe,
								"machine_tc_mach001_neg04_macerator_wrong_item_in_output_no_dupe")
						.fabricId("MachineGameTest", "tcMach001Neg04_maceratorWrongItemInOutputNoDupe").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach002Neg04_furnaceWrongItemInOutputNoDupe,
								"machine_tc_mach002_neg04_furnace_wrong_item_in_output_no_dupe")
						.fabricId("MachineGameTest", "tcMach002Neg04_furnaceWrongItemInOutputNoDupe").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach003Neg04_compressorWrongItemInOutputNoDupe,
								"machine_tc_mach003_neg04_compressor_wrong_item_in_output_no_dupe")
						.fabricId("MachineGameTest", "tcMach003Neg04_compressorWrongItemInOutputNoDupe").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach004Neg04_extractorWrongItemInOutputNoDupe,
								"machine_tc_mach004_neg04_extractor_wrong_item_in_output_no_dupe")
						.fabricId("MachineGameTest", "tcMach004Neg04_extractorWrongItemInOutputNoDupe").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Neg05_maceratorNonRecipeNoEuSpent,
								"machine_tc_mach001_neg05_macerator_non_recipe_no_eu_spent")
						.fabricId("MachineGameTest", "tcMach001Neg05_maceratorNonRecipeNoEuSpent").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach002Neg05_furnaceNonRecipeNoEuSpent,
								"machine_tc_mach002_neg05_furnace_non_recipe_no_eu_spent")
						.fabricId("MachineGameTest", "tcMach002Neg05_furnaceNonRecipeNoEuSpent").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach003Neg05_compressorNonRecipeNoEuSpent,
								"machine_tc_mach003_neg05_compressor_non_recipe_no_eu_spent")
						.fabricId("MachineGameTest", "tcMach003Neg05_compressorNonRecipeNoEuSpent").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Neg03_compressorRawOreNotAccepted,
								"machine_tc_comp001_neg03_compressor_raw_ore_not_accepted")
						.fabricId("MachineGameTest", "tcComp001Neg03_compressorRawOreNotAccepted").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach004Neg05_extractorNonRecipeNoEuSpent,
								"machine_tc_mach004_neg05_extractor_non_recipe_no_eu_spent")
						.fabricId("MachineGameTest", "tcMach004Neg05_extractorNonRecipeNoEuSpent").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Fun04_maceratorInputSwapResetsProgress,
								"machine_tc_mach001_fun04_macerator_input_swap_resets_progress")
						.fabricId("MachineGameTest", "tcMach001Fun04_maceratorInputSwapResetsProgress").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach002Fun04_furnaceInputSwapResetsProgress,
								"machine_tc_mach002_fun04_furnace_input_swap_resets_progress")
						.fabricId("MachineGameTest", "tcMach002Fun04_furnaceInputSwapResetsProgress").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach003Fun04_compressorInputSwapResetsProgress,
								"machine_tc_mach003_fun04_compressor_input_swap_resets_progress")
						.fabricId("MachineGameTest", "tcMach003Fun04_compressorInputSwapResetsProgress").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach004Fun04_extractorInputSwapResetsProgress,
								"machine_tc_mach004_fun04_extractor_input_swap_resets_progress")
						.fabricId("MachineGameTest", "tcMach004Fun04_extractorInputSwapResetsProgress").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Sta01_maceratorLitTracksActive,
								"machine_tc_mach001_sta01_macerator_lit_tracks_active")
						.fabricId("MachineGameTest", "tcMach001Sta01_maceratorLitTracksActive").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach002Sta01_furnaceLitTracksActive,
								"machine_tc_mach002_sta01_furnace_lit_tracks_active")
						.fabricId("MachineGameTest", "tcMach002Sta01_furnaceLitTracksActive").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach003Sta01_compressorLitTracksActive,
								"machine_tc_mach003_sta01_compressor_lit_tracks_active")
						.fabricId("MachineGameTest", "tcMach003Sta01_compressorLitTracksActive").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach004Sta01_extractorLitTracksActive,
								"machine_tc_mach004_sta01_extractor_lit_tracks_active")
						.fabricId("MachineGameTest", "tcMach004Sta01_extractorLitTracksActive").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Prf04_maceratorEopExactCompletes,
								"machine_tc_mach001_prf04_macerator_eop_exact_completes")
						.fabricId("MachineGameTest", "tcMach001Prf04_maceratorEopExactCompletes").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcMach001Prf03_maceratorEopMinusOneStalls,
								"machine_tc_mach001_prf03_macerator_eop_minus_one_stalls")
						.fabricId("MachineGameTest", "tcMach001Prf03_maceratorEopMinusOneStalls").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcEfurn001Prf01_furnaceEopExactCompletes,
								"machine_tc_efurn001_prf01_furnace_eop_exact_completes")
						.fabricId("MachineGameTest", "tcEfurn001Prf01_furnaceEopExactCompletes").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcEfurn001Prf02_furnaceEopMinusOneStalls,
								"machine_tc_efurn001_prf02_furnace_eop_minus_one_stalls")
						.fabricId("MachineGameTest", "tcEfurn001Prf02_furnaceEopMinusOneStalls").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Prf01_compressorEopExactCompletes,
								"machine_tc_comp001_prf01_compressor_eop_exact_completes")
						.fabricId("MachineGameTest", "tcComp001Prf01_compressorEopExactCompletes").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcComp001Prf02_compressorEopMinusOneStalls,
								"machine_tc_comp001_prf02_compressor_eop_minus_one_stalls")
						.fabricId("MachineGameTest", "tcComp001Prf02_compressorEopMinusOneStalls").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcExtr001Prf01_extractorEopExactCompletes,
								"machine_tc_extr001_prf01_extractor_eop_exact_completes")
						.fabricId("MachineGameTest", "tcExtr001Prf01_extractorEopExactCompletes").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcExtr001Prf02_extractorEopMinusOneStalls,
								"machine_tc_extr001_prf02_extractor_eop_minus_one_stalls")
						.fabricId("MachineGameTest", "tcExtr001Prf02_extractorEopMinusOneStalls").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcRecycler001Fun01_monoBatchCastsPoor,
								"machine_tc_recycler001_fun01_mono_batch_casts_poor")
						.fabricId("MachineGameTest", "tcRecycler001Fun01_monoBatchCastsPoor").ticks(20, 120),
				RosterEntry.of(MachineScenarios::tcRecycler001Fun02_mixedBatchCastsRich,
								"machine_tc_recycler001_fun02_mixed_batch_casts_rich")
						.fabricId("MachineGameTest", "tcRecycler001Fun02_mixedBatchCastsRich").ticks(20, 120),
				RosterEntry.of(MachineScenarios::tcRecycler001Fun03_lampsFollowFractions,
								"machine_tc_recycler001_fun03_lamps_follow_fractions")
						.fabricId("MachineGameTest", "tcRecycler001Fun03_lampsFollowFractions").ticks(20, 120),
				RosterEntry.of(MachineScenarios::tcRecycler001Con01_grassIsMineral,
								"machine_tc_recycler001_con01_grass_is_mineral")
						.fabricId("MachineGameTest", "tcRecycler001Con01_grassIsMineral").ticks(20, 120),
				RosterEntry.of(MachineScenarios::tcRecycler001Con02_noBladesNoWork,
								"machine_tc_recycler001_con02_no_blades_no_work")
						.fabricId("MachineGameTest", "tcRecycler001Con02_noBladesNoWork").ticks(20, 120),
				RosterEntry.of(MachineScenarios::tcRecycler001Con03_fullAshStops,
								"machine_tc_recycler001_con03_full_ash_stops")
						.fabricId("MachineGameTest", "tcRecycler001Con03_fullAshStops").ticks(20, 120),
				RosterEntry.of(MachineScenarios::tcRecycler001Con04_leftoverBriquetteDoesNotBlock,
								"machine_tc_recycler001_con04_leftover_briquette_does_not_block")
						.fabricId("MachineGameTest", "tcRecycler001Con04_leftoverBriquetteDoesNotBlock")
						.ticks(20, 120),
				RosterEntry.of(MachineScenarios::tcRecycler001Con05_ashNeverOverstacks,
								"machine_tc_recycler001_con05_ash_never_overstacks")
						.fabricId("MachineGameTest", "tcRecycler001Con05_ashNeverOverstacks").ticks(20, 120),
				RosterEntry.of(MachineScenarios::tcSaw001Fun01_planksMode, "machine_tc_saw001_fun01_planks_mode")
						.fabricId("MachineGameTest", "tcSaw001Fun01_planksMode").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcSaw001Fun02_bambooHalfYield,
								"machine_tc_saw001_fun02_bamboo_half_yield")
						.fabricId("MachineGameTest", "tcSaw001Fun02_bambooHalfYield").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcSaw001Fun03_sticksMode, "machine_tc_saw001_fun03_sticks_mode")
						.fabricId("MachineGameTest", "tcSaw001Fun03_sticksMode").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcSaw001Fun04_slabsMode, "machine_tc_saw001_fun04_slabs_mode")
						.fabricId("MachineGameTest", "tcSaw001Fun04_slabsMode").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcSaw001Fun05_stairsMode, "machine_tc_saw001_fun05_stairs_mode")
						.fabricId("MachineGameTest", "tcSaw001Fun05_stairsMode").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcSaw001Con01_onlyActiveModeSaws,
								"machine_tc_saw001_con01_only_active_mode_saws")
						.fabricId("MachineGameTest", "tcSaw001Con01_onlyActiveModeSaws").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcSaw001Con02_modePersistsThroughNbt,
								"machine_tc_saw001_con02_mode_persists_through_nbt")
						.fabricId("MachineGameTest", "tcSaw001Con02_modePersistsThroughNbt").ticks(20, 40),
				RosterEntry.of(MachineScenarios::tcSaw001Con03_modeSwitchResetsProgress,
								"machine_tc_saw001_con03_mode_switch_resets_progress")
						.fabricId("MachineGameTest", "tcSaw001Con03_modeSwitchResetsProgress").ticks(20, 40),
				RosterEntry.of(MachineScenarios::mod576CoastingFinishesOnlySupplyGaps,
								"machine_mod576_coasting_finishes_only_supply_gaps")
						.fabricId("MachineGameTest", "mod576CoastingFinishesOnlySupplyGaps").ticks(400));

		private Roster() {}
	}

	private MachineScenarios() {
	}

	public static void tcMach001Fun01_maceratorGrindsRawIron(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach001Fun01_maceratorGrindsRawIron(helper);
	}

	public static void tcMach001FunIronOre_maceratorGrindsIronOre(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach001FunIronOre_maceratorGrindsIronOre(helper);
	}

	public static void mod245_maceratorGrindsSulfurOre(GameTestHelper helper) {
		MachineRecipeScenarios.mod245_maceratorGrindsSulfurOre(helper);
	}

	public static void mod245_maceratorGrindsDeepslateSulfurOre(GameTestHelper helper) {
		MachineRecipeScenarios.mod245_maceratorGrindsDeepslateSulfurOre(helper);
	}

	public static void mod245_maceratorGrindsRawSulfur(GameTestHelper helper) {
		MachineRecipeScenarios.mod245_maceratorGrindsRawSulfur(helper);
	}

	public static void tcMach002Fun01_furnaceSmeltsRawIron(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach002Fun01_furnaceSmeltsRawIron(helper);
	}

	public static void mod245_furnaceSmeltsRawSulfur(GameTestHelper helper) {
		MachineRecipeScenarios.mod245_furnaceSmeltsRawSulfur(helper);
	}

	public static void tcMach003Fun01_compressorMakesBrick(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach003Fun01_compressorMakesBrick(helper);
	}

	public static void tcMach004Fun01_extractorMakesBlazePowder(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach004Fun01_extractorMakesBlazePowder(helper);
	}

	public static void tcMach001Neg01_noPowerNoOutput(GameTestHelper helper) {
		MachineFaultScenarios.tcMach001Neg01_noPowerNoOutput(helper);
	}

	public static void tcMach001Neg02_nonRecipeNoOutput(GameTestHelper helper) {
		MachineFaultScenarios.tcMach001Neg02_nonRecipeNoOutput(helper);
	}

	public static void tcMach002Neg01_furnaceNoPower(GameTestHelper helper) {
		MachineFaultScenarios.tcMach002Neg01_furnaceNoPower(helper);
	}

	public static void tcMach001Con01_sidedSlotRoles(GameTestHelper helper) {
		MachineFaultScenarios.tcMach001Con01_sidedSlotRoles(helper);
	}

	public static void tcMach001Prf_maceratorEopMatchesConfig(GameTestHelper helper) {
		MachineOperationScenarios.tcMach001Prf_maceratorEopMatchesConfig(helper);
	}

	public static void mod576CoastingFinishesOnlySupplyGaps(GameTestHelper helper) {
		MachineOperationScenarios.mod576CoastingFinishesOnlySupplyGaps(helper);
	}

	public static void tcMach001Neg03_fullOutputJamsMachine(GameTestHelper helper) {
		MachineFaultScenarios.tcMach001Neg03_fullOutputJamsMachine(helper);
	}

	public static void tcMach001FunCopperRaw_maceratorGrindsRawCopper(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach001FunCopperRaw_maceratorGrindsRawCopper(helper);
	}

	public static void tcMach001FunGoldRaw_maceratorGrindsRawGold(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach001FunGoldRaw_maceratorGrindsRawGold(helper);
	}

	public static void tcMach001FunIronIngot_maceratorGrindsIronIngot(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach001FunIronIngot_maceratorGrindsIronIngot(helper);
	}

	public static void tcEfurn001Fun01_furnaceSmeltsIronDust(GameTestHelper helper) {
		MachineRecipeScenarios.tcEfurn001Fun01_furnaceSmeltsIronDust(helper);
	}

	public static void tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef(GameTestHelper helper) {
		MachineRecipeScenarios.tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef(helper);
	}

	public static void tcEfurn001Fun03a_furnaceSmeltsSand(GameTestHelper helper) {
		MachineRecipeScenarios.tcEfurn001Fun03a_furnaceSmeltsSand(helper);
	}

	public static void tcEfurn001Fun03b_furnaceSmeltsCobblestone(GameTestHelper helper) {
		MachineRecipeScenarios.tcEfurn001Fun03b_furnaceSmeltsCobblestone(helper);
	}

	public static void tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal(GameTestHelper helper) {
		MachineRecipeScenarios.tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal(helper);
	}

	public static void tcEfurn001Fun04_furnaceDurationIsHalfVanilla(GameTestHelper helper) {
		MachineRecipeScenarios.tcEfurn001Fun04_furnaceDurationIsHalfVanilla(helper);
	}

	public static void tcComp001Fun02_compressorMakesCopperIngot(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Fun02_compressorMakesCopperIngot(helper);
	}

	public static void tcComp001Fun03_compressorMakesGoldIngot(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Fun03_compressorMakesGoldIngot(helper);
	}

	public static void tcComp001Fun04_compressorMakesIronIngot(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Fun04_compressorMakesIronIngot(helper);
	}

	public static void tcExtr001Fun02a_extractorMakesFlint(GameTestHelper helper) {
		MachineRecipeScenarios.tcExtr001Fun02a_extractorMakesFlint(helper);
	}

	public static void tcExtr001Fun06_extractorMakesGreenDye(GameTestHelper helper) {
		MachineRecipeScenarios.tcExtr001Fun06_extractorMakesGreenDye(helper);
	}

	public static void tcExtr001Fun07_extractorMakesPumpkinSeeds(GameTestHelper helper) {
		MachineRecipeScenarios.tcExtr001Fun07_extractorMakesPumpkinSeeds(helper);
	}

	public static void tcMach001Fun02_maceratorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach001Fun02_maceratorConsumesExactlyOnePerOperation(helper);
	}

	public static void tcMach002Fun02_furnaceConsumesExactlyOnePerOperation(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach002Fun02_furnaceConsumesExactlyOnePerOperation(helper);
	}

	public static void tcMach003Fun02_compressorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach003Fun02_compressorConsumesExactlyOnePerOperation(helper);
	}

	public static void tcComp001Fun05_compressorConsumesExactlyOneOfFive(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Fun05_compressorConsumesExactlyOneOfFive(helper);
	}

	public static void tcComp001Fun15_compressorCompactsGlowstoneDust(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Fun15_compressorCompactsGlowstoneDust(helper);
	}

	public static void tcComp001Fun16_compressorCompactsRedstone(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Fun16_compressorCompactsRedstone(helper);
	}

	public static void tcComp001Neg07_compressorRejectsPartialGlowstoneBatch(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Neg07_compressorRejectsPartialGlowstoneBatch(helper);
	}

	public static void tcComp001Neg08_compressorRejectsPartialRedstoneBatch(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Neg08_compressorRejectsPartialRedstoneBatch(helper);
	}

	public static void tcComp001Fun17_compressorCompactsCoalDustIntoCarbonRod(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Fun17_compressorCompactsCoalDustIntoCarbonRod(helper);
	}

	public static void tcComp001Neg09_compressorRejectsPartialCoalDustBatch(GameTestHelper helper) {
		MachineRecipeScenarios.tcComp001Neg09_compressorRejectsPartialCoalDustBatch(helper);
	}

	public static void tcComp001Gui06_compressorReportsPartialBatch(GameTestHelper helper) {
		MachineOperationScenarios.tcComp001Gui06_compressorReportsPartialBatch(helper);
	}

	public static void tcComp001Gui07_compressorReportsWrongItemAndJammedOutput(GameTestHelper helper) {
		MachineOperationScenarios.tcComp001Gui07_compressorReportsWrongItemAndJammedOutput(helper);
	}

	public static void tcComp001Gui08_compressorReportsStarvationButNotTrickle(GameTestHelper helper) {
		MachineOperationScenarios.tcComp001Gui08_compressorReportsStarvationButNotTrickle(helper);
	}

	public static void tcMach004Fun02_extractorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		MachineRecipeScenarios.tcMach004Fun02_extractorConsumesExactlyOnePerOperation(helper);
	}

	public static void tcMach002Neg03_furnaceFullOutputJamsMachine(GameTestHelper helper) {
		MachineFaultScenarios.tcMach002Neg03_furnaceFullOutputJamsMachine(helper);
	}

	public static void tcMach003Neg03_compressorFullOutputJamsMachine(GameTestHelper helper) {
		MachineFaultScenarios.tcMach003Neg03_compressorFullOutputJamsMachine(helper);
	}

	public static void tcExtr001Neg01a_multipliedOutputFitsAt61(GameTestHelper helper) {
		MachineFaultScenarios.tcExtr001Neg01a_multipliedOutputFitsAt61(helper);
	}

	public static void tcExtr001Neg01b_multipliedOutputJamsAt62(GameTestHelper helper) {
		MachineFaultScenarios.tcExtr001Neg01b_multipliedOutputJamsAt62(helper);
	}

	public static void tcMach001Neg04_maceratorWrongItemInOutputNoDupe(GameTestHelper helper) {
		MachineFaultScenarios.tcMach001Neg04_maceratorWrongItemInOutputNoDupe(helper);
	}

	public static void tcMach002Neg04_furnaceWrongItemInOutputNoDupe(GameTestHelper helper) {
		MachineFaultScenarios.tcMach002Neg04_furnaceWrongItemInOutputNoDupe(helper);
	}

	public static void tcMach003Neg04_compressorWrongItemInOutputNoDupe(GameTestHelper helper) {
		MachineFaultScenarios.tcMach003Neg04_compressorWrongItemInOutputNoDupe(helper);
	}

	public static void tcMach004Neg04_extractorWrongItemInOutputNoDupe(GameTestHelper helper) {
		MachineFaultScenarios.tcMach004Neg04_extractorWrongItemInOutputNoDupe(helper);
	}

	public static void tcMach001Neg05_maceratorNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineFaultScenarios.tcMach001Neg05_maceratorNonRecipeNoEuSpent(helper);
	}

	public static void tcMach002Neg05_furnaceNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineFaultScenarios.tcMach002Neg05_furnaceNonRecipeNoEuSpent(helper);
	}

	public static void tcMach003Neg05_compressorNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineFaultScenarios.tcMach003Neg05_compressorNonRecipeNoEuSpent(helper);
	}

	public static void tcComp001Neg03_compressorRawOreNotAccepted(GameTestHelper helper) {
		MachineFaultScenarios.tcComp001Neg03_compressorRawOreNotAccepted(helper);
	}

	public static void tcMach004Neg05_extractorNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineFaultScenarios.tcMach004Neg05_extractorNonRecipeNoEuSpent(helper);
	}

	public static void tcMach001Fun04_maceratorInputSwapResetsProgress(GameTestHelper helper) {
		MachineFaultScenarios.tcMach001Fun04_maceratorInputSwapResetsProgress(helper);
	}

	public static void tcMach002Fun04_furnaceInputSwapResetsProgress(GameTestHelper helper) {
		MachineFaultScenarios.tcMach002Fun04_furnaceInputSwapResetsProgress(helper);
	}

	public static void tcMach003Fun04_compressorInputSwapResetsProgress(GameTestHelper helper) {
		MachineFaultScenarios.tcMach003Fun04_compressorInputSwapResetsProgress(helper);
	}

	public static void tcMach004Fun04_extractorInputSwapResetsProgress(GameTestHelper helper) {
		MachineFaultScenarios.tcMach004Fun04_extractorInputSwapResetsProgress(helper);
	}

	public static void tcMach001Sta01_maceratorLitTracksActive(GameTestHelper helper) {
		MachineOperationScenarios.tcMach001Sta01_maceratorLitTracksActive(helper);
	}

	public static void tcMach002Sta01_furnaceLitTracksActive(GameTestHelper helper) {
		MachineOperationScenarios.tcMach002Sta01_furnaceLitTracksActive(helper);
	}

	public static void tcMach003Sta01_compressorLitTracksActive(GameTestHelper helper) {
		MachineOperationScenarios.tcMach003Sta01_compressorLitTracksActive(helper);
	}

	public static void tcMach004Sta01_extractorLitTracksActive(GameTestHelper helper) {
		MachineOperationScenarios.tcMach004Sta01_extractorLitTracksActive(helper);
	}

	public static void tcMach001Prf04_maceratorEopExactCompletes(GameTestHelper helper) {
		MachineOperationScenarios.tcMach001Prf04_maceratorEopExactCompletes(helper);
	}

	public static void tcMach001Prf03_maceratorEopMinusOneStalls(GameTestHelper helper) {
		MachineOperationScenarios.tcMach001Prf03_maceratorEopMinusOneStalls(helper);
	}

	public static void tcEfurn001Prf01_furnaceEopExactCompletes(GameTestHelper helper) {
		MachineOperationScenarios.tcEfurn001Prf01_furnaceEopExactCompletes(helper);
	}

	public static void tcEfurn001Prf02_furnaceEopMinusOneStalls(GameTestHelper helper) {
		MachineOperationScenarios.tcEfurn001Prf02_furnaceEopMinusOneStalls(helper);
	}

	public static void tcComp001Prf01_compressorEopExactCompletes(GameTestHelper helper) {
		MachineOperationScenarios.tcComp001Prf01_compressorEopExactCompletes(helper);
	}

	public static void tcComp001Prf02_compressorEopMinusOneStalls(GameTestHelper helper) {
		MachineOperationScenarios.tcComp001Prf02_compressorEopMinusOneStalls(helper);
	}

	public static void tcExtr001Prf01_extractorEopExactCompletes(GameTestHelper helper) {
		MachineOperationScenarios.tcExtr001Prf01_extractorEopExactCompletes(helper);
	}

	public static void tcExtr001Prf02_extractorEopMinusOneStalls(GameTestHelper helper) {
		MachineOperationScenarios.tcExtr001Prf02_extractorEopMinusOneStalls(helper);
	}

	public static void tcSaw001Fun01_planksMode(GameTestHelper helper) {
		SawmillScenarios.tcSaw001Fun01_planksMode(helper);
	}

	public static void tcSaw001Fun02_bambooHalfYield(GameTestHelper helper) {
		SawmillScenarios.tcSaw001Fun02_bambooHalfYield(helper);
	}

	public static void tcSaw001Fun03_sticksMode(GameTestHelper helper) {
		SawmillScenarios.tcSaw001Fun03_sticksMode(helper);
	}

	public static void tcSaw001Fun04_slabsMode(GameTestHelper helper) {
		SawmillScenarios.tcSaw001Fun04_slabsMode(helper);
	}

	public static void tcSaw001Fun05_stairsMode(GameTestHelper helper) {
		SawmillScenarios.tcSaw001Fun05_stairsMode(helper);
	}

	public static void tcSaw001Con01_onlyActiveModeSaws(GameTestHelper helper) {
		SawmillScenarios.tcSaw001Con01_onlyActiveModeSaws(helper);
	}

	public static void tcSaw001Con02_modePersistsThroughNbt(GameTestHelper helper) {
		SawmillScenarios.tcSaw001Con02_modePersistsThroughNbt(helper);
	}

	public static void tcSaw001Con03_modeSwitchResetsProgress(GameTestHelper helper) {
		SawmillScenarios.tcSaw001Con03_modeSwitchResetsProgress(helper);
	}

	public static void tcRecycler001Fun01_monoBatchCastsPoor(GameTestHelper helper) {
		RecyclerScenarios.tcRecycler001Fun01_monoBatchCastsPoor(helper);
	}

	public static void tcRecycler001Fun02_mixedBatchCastsRich(GameTestHelper helper) {
		RecyclerScenarios.tcRecycler001Fun02_mixedBatchCastsRich(helper);
	}

	public static void tcRecycler001Con01_grassIsMineral(GameTestHelper helper) {
		RecyclerScenarios.tcRecycler001Con01_grassIsMineral(helper);
	}

	public static void tcRecycler001Con02_noBladesNoWork(GameTestHelper helper) {
		RecyclerScenarios.tcRecycler001Con02_noBladesNoWork(helper);
	}

	public static void tcRecycler001Con03_fullAshStops(GameTestHelper helper) {
		RecyclerScenarios.tcRecycler001Con03_fullAshStops(helper);
	}

	public static void tcRecycler001Fun03_lampsFollowFractions(GameTestHelper helper) {
		RecyclerScenarios.tcRecycler001Fun03_lampsFollowFractions(helper);
	}

	public static void tcRecycler001Con04_leftoverBriquetteDoesNotBlock(GameTestHelper helper) {
		RecyclerScenarios.tcRecycler001Con04_leftoverBriquetteDoesNotBlock(helper);
	}

	public static void tcRecycler001Con05_ashNeverOverstacks(GameTestHelper helper) {
		RecyclerScenarios.tcRecycler001Con05_ashNeverOverstacks(helper);
	}
}
