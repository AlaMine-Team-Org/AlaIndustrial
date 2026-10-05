package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;
import static dev.alaindustrial.gametest.SolarPanelRig.POS;
import static dev.alaindustrial.gametest.SolarPanelRig.assertTopFaceWorkingSurface;
import static dev.alaindustrial.gametest.SolarPanelRig.concentratorAt;
import static dev.alaindustrial.gametest.SolarPanelRig.daylightAt;
import static dev.alaindustrial.gametest.SolarPanelRig.genAt;
import static dev.alaindustrial.gametest.SolarPanelRig.moonlitAt;
import static dev.alaindustrial.gametest.SolarPanelRig.panelAt;
import static dev.alaindustrial.gametest.SolarPanelRig.setClearDay;
import static dev.alaindustrial.gametest.SolarPanelRig.setNight;
import static dev.alaindustrial.gametest.SolarPanelRig.setRaining;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-neutral world-based gametest bodies for the solar panel family (MOD-323): weather /
 * sky-blocking / physical / performance states of the base solar, moonlit and daylight panels.
 *
 * <p>Both lanes run these bodies from the roster below (ADR-038). Isolation note: every
 * gametest in a batch shares ONE {@code ServerLevel}, so world time is global. Each body sets
 * time/weather and then calls {@code updateSkyBrightness()} to recompute {@code skyDarken}
 * synchronously, reading production in the SAME method body with no {@code runAfterDelay}.
 */
public final class SolarPanelScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SolarPanelScenarios::tcSolar001Sta02_rainFlagsWeatherMode,
								"solar_rain_flags_weather_mode")
						.fabricId("SolarPanelGameTest", "tcSolar001Sta02_rainFlagsWeatherMode").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Sta03_thunderFlagsWeatherMode,
								"solar_thunder_flags_weather_mode")
						.fabricId("SolarPanelGameTest", "tcSolar001Sta03_thunderFlagsWeatherMode").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::mod602_snowBlacksOutConcentratorButNotDaylightPanel,
								"mod602_snow_blacks_out_concentrator_but_not_daylight_panel")
						.fabricId("SolarPanelGameTest", "mod602_snowBlacksOutConcentratorButNotDaylightPanel")
						.ticks(60).sky(),
				RosterEntry.of(SolarPanelScenarios::mod602_noonWindowLiftsOutput, "mod602_noon_window_lifts_output")
						.fabricId("SolarPanelGameTest", "mod602_noonWindowLiftsOutput").ticks(60).sky(),
				RosterEntry.of(SolarPanelScenarios::mod602_daylightPanelTakesResonanceChipOnly,
								"mod602_daylight_panel_takes_resonance_chip_only")
						.fabricId("SolarPanelGameTest", "mod602_daylightPanelTakesResonanceChipOnly").ticks(40)
						.sky(true, false),
				RosterEntry.of(SolarPanelScenarios::mod602_daylightPanelEvolvesIntoConcentrator,
								"mod602_daylight_panel_evolves_into_concentrator")
						.fabricId("SolarPanelGameTest", "mod602_daylightPanelEvolvesIntoConcentrator").ticks(60).sky(),
				RosterEntry.of(SolarPanelScenarios::solarPanel_automationCannotStackSecondChip,
								"solar_automation_cannot_stack_second_chip")
						.fabricId("SolarPanelGameTest", "solarPanel_automationCannotStackSecondChip").ticks(20, 100),
				RosterEntry.of(SolarPanelScenarios::solarPanel_evolutionConsumesOneChipNotTheStack,
								"solar_evolution_consumes_one_chip_not_the_stack")
						.fabricId("SolarPanelGameTest", "solarPanel_evolutionConsumesOneChipNotTheStack").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::solarPanel_removingChipClearsEvolutionProgress,
								"solar_removing_chip_clears_evolution_progress")
						.fabricId("SolarPanelGameTest", "solarPanel_removingChipClearsEvolutionProgress").ticks(60)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::solarPanel_swappingChipBranchClearsEvolutionProgress,
								"solar_swapping_chip_branch_clears_evolution_progress")
						.fabricId("SolarPanelGameTest", "solarPanel_swappingChipBranchClearsEvolutionProgress")
						.ticks(60).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Neg02_rainYieldsZeroEu, "solar_rain_yields_zero_eu")
						.fabricId("SolarPanelGameTest", "tcSolar001Neg02_rainYieldsZeroEu").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Neg03_opaqueBlockAboveYieldsZero,
								"solar_opaque_block_above_yields_zero")
						.fabricId("SolarPanelGameTest", "tcSolar001Neg03_opaqueBlockAboveYieldsZero").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Fun04_glassAboveStaysFull, "solar_glass_above_stays_full")
						.fabricId("SolarPanelGameTest", "tcSolar001Fun04_glassAboveStaysFull").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Sta06_leavesAboveFlagPartial,
								"solar_leaves_above_flag_partial")
						.fabricId("SolarPanelGameTest", "tcSolar001Sta06_leavesAboveFlagPartial").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Sta05_snowLayerAboveFlagsSnow,
								"solar_snow_layer_above_flags_snow")
						.fabricId("SolarPanelGameTest", "tcSolar001Sta05_snowLayerAboveFlagsSnow").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Sta09_snowLayerPlusThunderIsWeather,
								"solar_snow_layer_plus_thunder_is_weather")
						.fabricId("SolarPanelGameTest", "tcSolar001Sta09_snowLayerPlusThunderIsWeather").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Sta10_snowLayerAtNightIsZero,
								"solar_snow_layer_at_night_is_zero")
						.fabricId("SolarPanelGameTest", "tcSolar001Sta10_snowLayerAtNightIsZero").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Phy01_topFaceNoOutput, "solar_top_face_no_output")
						.fabricId("SolarPanelGameTest", "tcSolar001Phy01_topFaceNoOutput").ticks(20, 100),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Prf01_euRateMatchesConfig, "solar_eu_rate_matches_config")
						.fabricId("SolarPanelGameTest", "tcSolar001Prf01_euRateMatchesConfig").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcMoonlit001Neg01_noEuByDay, "moonlit_no_eu_by_day")
						.fabricId("SolarPanelGameTest", "tcMoonlit001Neg01_noEuByDay").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcMoonlit001Sta01_rainFlagsWeatherMode,
								"moonlit_rain_flags_weather_mode")
						.fabricId("SolarPanelGameTest", "tcMoonlit001Sta01_rainFlagsWeatherMode").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcMoonlit001Sta03_thunderYieldsWeatherTrickle,
								"moonlit_thunder_yields_weather_trickle")
						.fabricId("SolarPanelGameTest", "tcMoonlit001Sta03_thunderYieldsWeatherTrickle").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcMoonlit001Neg03_opaqueBlockAboveYieldsZero,
								"moonlit_opaque_block_above_yields_zero")
						.fabricId("SolarPanelGameTest", "tcMoonlit001Neg03_opaqueBlockAboveYieldsZero").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcMoonlit001Sta02_leavesAbovePartialHalvesOutput,
								"moonlit_leaves_above_partial_halves_output")
						.fabricId("SolarPanelGameTest", "tcMoonlit001Sta02_leavesAbovePartialHalvesOutput").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcMoonlit001Phy01_topFaceNoOutput, "moonlit_top_face_no_output")
						.fabricId("SolarPanelGameTest", "tcMoonlit001Phy01_topFaceNoOutput").ticks(20, 100),
				RosterEntry.of(SolarPanelScenarios::tcMoonlit001Prf01_euRateMatchesConfig,
								"moonlit_eu_rate_matches_config")
						.fabricId("SolarPanelGameTest", "tcMoonlit001Prf01_euRateMatchesConfig").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcMoonlit001Prf02_bufferCapsAtMax, "moonlit_buffer_caps_at_max")
						.fabricId("SolarPanelGameTest", "tcMoonlit001Prf02_bufferCapsAtMax").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Fun03_nightChipEvolvesToMoonlit,
								"solar_night_chip_evolves_to_moonlit")
						.fabricId("SolarPanelGameTest", "tcSolar001Fun03_nightChipEvolvesToMoonlit").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcDaylight001Neg02_rainYieldsZeroEu, "daylight_rain_yields_zero_eu")
						.fabricId("SolarPanelGameTest", "tcDaylight001Neg02_rainYieldsZeroEu").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcDaylight001Neg03_opaqueBlockAboveYieldsZero,
								"daylight_opaque_block_above_yields_zero")
						.fabricId("SolarPanelGameTest", "tcDaylight001Neg03_opaqueBlockAboveYieldsZero").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcDaylight001Sta02_leavesAbovePartialHalvesOutput,
								"daylight_leaves_above_partial_halves_output")
						.fabricId("SolarPanelGameTest", "tcDaylight001Sta02_leavesAbovePartialHalvesOutput").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcDaylight001Fun02_glassAboveStaysFull,
								"daylight_glass_above_stays_full")
						.fabricId("SolarPanelGameTest", "tcDaylight001Fun02_glassAboveStaysFull").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcDaylight001Sta01_rainFlagsWeatherMode,
								"daylight_rain_flags_weather_mode")
						.fabricId("SolarPanelGameTest", "tcDaylight001Sta01_rainFlagsWeatherMode").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcDaylight001Phy01_topFaceNoOutput, "daylight_top_face_no_output")
						.fabricId("SolarPanelGameTest", "tcDaylight001Phy01_topFaceNoOutput").ticks(20, 100),
				RosterEntry.of(SolarPanelScenarios::tcDaylight001Prf01_euRateMatchesConfig,
								"daylight_eu_rate_matches_config")
						.fabricId("SolarPanelGameTest", "tcDaylight001Prf01_euRateMatchesConfig").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcDaylight001Prf02_bufferCapsAtMax, "daylight_buffer_caps_at_max")
						.fabricId("SolarPanelGameTest", "tcDaylight001Prf02_bufferCapsAtMax").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Sta13_iceAboveYieldsBlocked,
								"solar_ice_above_yields_blocked")
						.fabricId("SolarPanelGameTest", "tcSolar001Sta13_iceAboveYieldsBlocked").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Sta15_glowstoneAboveYieldsBlocked,
								"solar_glowstone_above_yields_blocked")
						.fabricId("SolarPanelGameTest", "tcSolar001Sta15_glowstoneAboveYieldsBlocked").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Neg08_waterAboveYieldsZero,
								"solar_water_above_yields_zero")
						.fabricId("SolarPanelGameTest", "tcSolar001Neg08_waterAboveYieldsZero").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Prf03_globalRateMultiplierScalesOutput,
								"solar_global_rate_multiplier_scales_output")
						.fabricId("SolarPanelGameTest", "tcSolar001Prf03_globalRateMultiplierScalesOutput").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Prf04_configChangeAppliesNextTick,
								"solar_config_change_applies_next_tick")
						.fabricId("SolarPanelGameTest", "tcSolar001Prf04_configChangeAppliesNextTick").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Con01_batteryBoxWrongFacingGetsNothing,
								"solar_battery_box_wrong_facing_gets_nothing")
						.fabricId("SolarPanelGameTest", "tcSolar001Con01_batteryBoxWrongFacingGetsNothing").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Con02_opaqueGapBlocksDelivery,
								"solar_opaque_gap_blocks_delivery")
						.fabricId("SolarPanelGameTest", "tcSolar001Con02_opaqueGapBlocksDelivery").ticks(40).sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Con03_immediateDeliveryOnPlacement,
								"solar_immediate_delivery_on_placement")
						.fabricId("SolarPanelGameTest", "tcSolar001Con03_immediateDeliveryOnPlacement").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Con04_twoReceiversDoNotDoubleOutput,
								"solar_two_receivers_do_not_double_output")
						.fabricId("SolarPanelGameTest", "tcSolar001Con04_twoReceiversDoNotDoubleOutput").ticks(40)
						.sky(),
				RosterEntry.of(SolarPanelScenarios::tcSolar001Con05_bufferHoldsWhileReceiverFull,
								"solar_buffer_holds_while_receiver_full")
						.fabricId("SolarPanelGameTest", "tcSolar001Con05_bufferHoldsWhileReceiverFull").ticks(40)
						.sky());

		private Roster() {}
	}

	private SolarPanelScenarios() {}

	/**
	 * Rain flags the weather production mode: day + rain resolves to MODE_WEATHER (0 EU output).
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta02_rainFlagsWeatherMode
	 *
	 * @implements TC-SOLAR-001-STA02 — rain flags the weather production mode (day + rain → MODE_WEATHER).
	 *     The mode flag fires for the GUI even though output is 0 in weather (MOD-003; see NEG02). Rain set
	 *     after the clear-day brightness is settled; everything synchronous.
	 */
	public static void tcSolar001Sta02_rainFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		setRaining(helper, false);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 1);
		int mode = panel.getDataAccess().get(3); // maxProgress carries the mode code
		if (mode != SolarPanelBlockEntity.MODE_WEATHER) {
			helper.fail("expected MODE_WEATHER (" + SolarPanelBlockEntity.MODE_WEATHER + "), got " + mode
					+ " (isRaining=" + helper.getLevel().isRaining() + ")");
		}
		helper.succeed();
	}

	/**
	 * A storm flags MODE_WEATHER and stops the panel dead.
	 *
	 * <p><b>What this scenario does NOT cover, and why it cannot (MOD-602).</b> In a real daytime
	 * thunderstorm the sky darkens far enough that {@code isBrightOutside()} turns false, and that is
	 * the case the mode used to answer with "night" at noon. It cannot be staged here, and both
	 * reasons were measured with a probe run rather than guessed:
	 *
	 * <ul>
	 *   <li>Every scenario in this batch shares ONE {@code ServerLevel}, and weather is global to it.
	 *       Neighbouring bodies call {@code setClearDay}, which switches rain and thunder off — a probe
	 *       that set a storm and read the world thirty ticks later found {@code isThundering=false}.
	 *       Holding a storm across a delay is therefore not possible in this suite.</li>
	 *   <li>Without a delay the sky cannot darken at all: {@code isBrightOutside()} reads
	 *       {@code skyDarken}, which {@code updateSkyBrightness()} derives from
	 *       {@code EnvironmentAttributes.SKY_LIGHT_LEVEL} — an attribute the level folds the weather
	 *       into on ITS tick, not when a caller flips a flag.</li>
	 * </ul>
	 *
	 * <p>So the daytime-thunder label is verified in the dev client, not here. The rule itself is one
	 * line in {@code produce()} guarded by {@code SolarSky.isClockDaytime}; if this ever becomes
	 * testable, the honest shape is a scenario in its own batch, not a delay bolted onto this one.
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta03_thunderFlagsWeatherMode
	 *
	 * @implements TC-SOLAR-001-STA03 — thunderstorm also flags MODE_WEATHER (same zero-output as rain, MOD-003).
	 *     Thunder always co-occurs with rain; both flags set so {@code isRaining()} reads true.
	 * @covers R-NRG-15
	 */
	public static void tcSolar001Sta03_thunderFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		setRaining(helper, true);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		if (mode != SolarPanelBlockEntity.MODE_WEATHER) {
			helper.fail("thunderstorm did not flag MODE_WEATHER, got mode " + mode);
		}
		if (panel.getDataAccess().get(2) != 0) {
			helper.fail("a storm must stop a day panel dead, got "
					+ panel.getDataAccess().get(2) + " EU/t");
		}
		helper.succeed();
	}

	/**
	 * Automation may insert a chip only while the slot is EMPTY — a hopper must not stack a second
	 * chip into the occupied slot.
	 * Mirrors: SolarPanelGameTest.solarPanel_automationCannotStackSecondChip
	 */
	public static void solarPanel_automationCannotStackSecondChip(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		SolarPanelBlockEntity panel = panelAt(helper);
		ItemStack chip = new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get());
		if (!panel.canPlaceItemThroughFace(SolarPanelBlockEntity.CHIP_SLOT, chip, Direction.UP)) {
			helper.fail("automation could not insert a chip into an empty slot");
		}
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()));
		if (panel.canPlaceItemThroughFace(SolarPanelBlockEntity.CHIP_SLOT, chip, Direction.UP)) {
			helper.fail("automation could insert a second chip into an occupied slot");
		}
		helper.succeed();
	}

	/**
	 * Evolution consumes exactly ONE chip and carries the rest of the stack across (a pre-guard save
	 * can still hold a stack of 64; the old code destroyed all of them).
	 * Mirrors: SolarPanelGameTest.solarPanel_evolutionConsumesOneChipNotTheStack
	 */
	public static void solarPanel_evolutionConsumesOneChipNotTheStack(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get(), 8));
		BlockPos abs = panel.getBlockPos();
		for (int i = 0; i <= GeneratorConfig.solarEvolveTicks
				&& helper.getLevel().getBlockState(abs).getBlock() == ModContent.SOLAR_PANEL.get(); i++) {
			panel.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
		if (!(helper.getLevel().getBlockEntity(abs) instanceof dev.alaindustrial.block.entity.MachineBlockEntity evolved)) {
			helper.fail("panel did not evolve");
			return;
		}
		ItemStack left = evolved.getItem(SolarPanelBlockEntity.CHIP_SLOT);
		if (left.getCount() != 7 || !left.is(ModContent.ALIGNMENT_CHIP_DAY.get())) {
			helper.fail("evolution destroyed the chip stack: expected 7 chips left, got " + left);
		}
		helper.succeed();
	}

	/**
	 * Pulling the chip out abandons the progress it earned — the counter belongs to the chip, not to
	 * the block. Before MOD-601 the counter simply froze, so a player could farm most of an evolution,
	 * take the chip back and keep the progress for free.
	 */
	public static void solarPanel_removingChipClearsEvolutionProgress(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		BlockPos abs = panel.getBlockPos();
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()));
		for (int i = 0; i < 40; i++) {
			panel.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
		if (panel.getEvolveProgressTicks() <= 0) {
			helper.fail("the day chip earned no progress under a clear sky - the rig is wrong, not the fix");
		}
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, ItemStack.EMPTY);
		panel.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		if (panel.getEvolveProgressTicks() != 0) {
			helper.fail("progress survived the chip being removed: " + panel.getEvolveProgressTicks());
		}
		helper.succeed();
	}

	/**
	 * Swapping a day chip for a night one starts the night run from zero. This is the case the slot
	 * cannot report by itself: a swap done in a single click never leaves the slot empty for a tick to
	 * observe, so the counter has to remember which chip earned it.
	 */
	public static void solarPanel_swappingChipBranchClearsEvolutionProgress(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		BlockPos abs = panel.getBlockPos();
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()));
		for (int i = 0; i < 40; i++) {
			panel.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
		int earned = panel.getEvolveProgressTicks();
		if (earned <= 0) {
			helper.fail("the day chip earned no progress under a clear sky - the rig is wrong, not the fix");
		}
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_NIGHT.get()));
		panel.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		if (panel.getEvolveProgressTicks() != 0) {
			helper.fail("day progress carried over to the night chip: " + panel.getEvolveProgressTicks());
		}
		helper.succeed();
	}

	// ── NEG: base panel must produce 0 EU when sky/time conditions are wrong ─────────

	/**
	 * Rain/thunder stops base-panel generation entirely (0 EU; MOD-003).
	 * Mirrors: SolarPanelGameTest.tcSolar001Neg02_rainYieldsZeroEu
	 *
	 * @implements TC-SOLAR-001-NEG02 — rain/thunder stops generation entirely (0 EU). MOD-003: rain blocks
	 *     direct sunlight, so the panel produces nothing (the {@code solarWeatherFactor} ×0.5 halving was
	 *     removed). The weather MODE flag still fires (see STA01); only the EU output is zero.
	 * @covers R-NRG-15
	 */
	public static void tcSolar001Neg02_rainYieldsZeroEu(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		setRaining(helper, false);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("rain: generated " + amount + " EU (expected 0 — MOD-003)");
		}
		helper.succeed();
	}

	/**
	 * An opaque block above cancels sky access → 0 EU (SolarSky direct column scan, MOD-004).
	 * Mirrors: SolarPanelGameTest.tcSolar001Neg03_opaqueBlockAboveYieldsZero
	 *
	 * @implements TC-SOLAR-001-NEG03 — an opaque block above cancels sky access → 0 EU.
	 *
	 * <p>Since MOD-004 the panel classifies sky access by scanning the column above it directly
	 * ({@link dev.alaindustrial.core.environment.SolarSky}), not via {@code canSeeSkyFromBelowWater} — so a solid
	 * roof is detected even in the deep gametest region (the old heightmap/below-sea quirk that forced
	 * this case to MANUAL/L3 no longer applies).
	 * @covers R-NRG-15
	 */
	public static void tcSolar001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.STONE);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("generated " + amount + " EU under stone; expected 0");
		}
		helper.succeed();
	}

	/**
	 * Glass above does NOT reduce generation: fully sky-transparent → CLEAR, full output, MODE_DAY.
	 * Mirrors: SolarPanelGameTest.tcSolar001Fun04_glassAboveStaysFull
	 *
	 * @implements TC-SOLAR-001-FUN04 — glass above does NOT reduce generation (fully sky-transparent →
	 *     CLEAR, full output, MODE_DAY). MOD-004.
	 * @covers R-NRG-15
	 */
	public static void tcSolar001Fun04_glassAboveStaysFull(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.GLASS);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(GeneratorConfig.solarEuPerTick * Config.globalEuRateMultiplier));
		int mode = panel.getDataAccess().get(3);
		if (got != expected || mode != SolarPanelBlockEntity.MODE_DAY) {
			helper.fail("glass should keep full output: got " + got + " (expected " + expected
					+ "), mode " + mode + " (expected MODE_DAY)");
		}
		helper.succeed();
	}

	/**
	 * A translucent block (leaves) above flags MODE_PARTIAL and still generates at exactly the
	 * partial rate (base × solarTransparentFactor, MOD-004).
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta06_leavesAboveFlagPartial
	 *
	 * @implements TC-SOLAR-001-STA06 — a translucent block (leaves) above flags MODE_PARTIAL and still
	 *     generates (MOD-004). The base panel's 1 EU/t × 0.5 rounds back to 1, so assert the mode flag
	 *     AND the exact 1 EU/t generation (a regression that classifies leaves as BLOCKED → 0 EU, or
	 *     that drops the partial factor, is caught either way).
	 * @covers R-NRG-15
	 */
	public static void tcSolar001Sta06_leavesAboveFlagPartial(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		if (mode != SolarPanelBlockEntity.MODE_PARTIAL) {
			helper.fail("leaves above should flag MODE_PARTIAL (" + SolarPanelBlockEntity.MODE_PARTIAL
					+ "), got " + mode);
		}
		// Partial generation: base 1 EU/t × solarTransparentFactor (0.5) → max(1, round(0.5)) = 1 EU,
		// then × globalEuRateMultiplier. Assert the exact value so a regression to 0 (misclassified as
		// BLOCKED) or to full-day output (factor dropped) is caught, not just "<anything > 0>".
		long perTick = Math.max(1, Math.round(Math.round(GeneratorConfig.solarEuPerTick
				* GeneratorConfig.solarTransparentFactor)
				* Config.globalEuRateMultiplier));
		long got = panel.getEnergyStorage().getAmount();
		if (got != perTick) {
			helper.fail("partial-sky generation over 1 tick: got " + got + " EU, expected exactly " + perTick
					+ " (max(1, round(round(" + GeneratorConfig.solarEuPerTick + " × "
							+ GeneratorConfig.solarTransparentFactor
					+ ") × " + Config.globalEuRateMultiplier + ")))");
		}
		helper.succeed();
	}

	/**
	 * A snow layer directly above flags MODE_SNOW and dims output to
	 * max(1, round(solarEuPerTick × solarSnowFactor)).
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta05_snowLayerAboveFlagsSnow
	 *
	 * @implements TC-SOLAR-001-STA05 — a snow LAYER ({@code minecraft:snow}) directly above the panel
	 *     flags MODE_SNOW and dims output to {@code max(1, round(solarEuPerTick × solarSnowFactor))}. The
	 *     floor keeps the T1 base of 1 from truncating to 0 in snow, so the panel still trickles 1 EU/t.
	 *     MODE_SNOW beats the BLOCKED/PARTIAL/DAY classification.
	 * @covers R-NRG-15
	 */
	public static void tcSolar001Sta05_snowLayerAboveFlagsSnow(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.SNOW);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		int snowBase = Math.max(1, Math.round(GeneratorConfig.solarEuPerTick * GeneratorConfig.solarSnowFactor));
		long expected = Math.max(1, Math.round(snowBase * Config.globalEuRateMultiplier));
		int mode = panel.getDataAccess().get(3);
		if (mode != SolarPanelBlockEntity.MODE_SNOW) {
			helper.fail("snow layer above should flag MODE_SNOW (" + SolarPanelBlockEntity.MODE_SNOW
					+ "), got " + mode);
		}
		if (got != expected) {
			helper.fail("snow layer output: got " + got + " EU (expected " + expected
					+ " = max(1, round(" + GeneratorConfig.solarEuPerTick + " × " + GeneratorConfig.solarSnowFactor
							+ ")))");
		}
		helper.succeed();
	}

	/**
	 * WEATHER beats SNOW: a snow layer above plus an active thunderstorm resolves to MODE_WEATHER
	 * with 0 EU, not MODE_SNOW.
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta09_snowLayerPlusThunderIsWeather
	 *
	 * @implements TC-SOLAR-001-STA09 — WEATHER beats SNOW: a snow layer above the panel plus an active
	 *     thunderstorm resolves to MODE_WEATHER with 0 EU, not MODE_SNOW.
	 * @covers R-NRG-15
	 */
	public static void tcSolar001Sta09_snowLayerPlusThunderIsWeather(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.SNOW);
		setClearDay(helper);
		setRaining(helper, true);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		long got = panel.getEnergyStorage().getAmount();
		if (mode != SolarPanelBlockEntity.MODE_WEATHER || got != 0) {
			helper.fail("snow layer + thunder should be MODE_WEATHER/0 EU (WEATHER > SNOW), got mode " + mode
					+ ", " + got + " EU");
		}
		helper.succeed();
	}

	/**
	 * NIGHT beats SNOW: a snow layer above at night yields 0 EU (mode NIGHT), never MODE_SNOW.
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta10_snowLayerAtNightIsZero
	 *
	 * @implements TC-SOLAR-001-STA10 — NIGHT beats SNOW: a snow layer above the panel at night yields 0 EU
	 *     (mode NIGHT), never MODE_SNOW.
	 * @covers R-NRG-15
	 */
	public static void tcSolar001Sta10_snowLayerAtNightIsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.SNOW);
		setNight(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		int mode = panel.getDataAccess().get(3);
		if (got != 0 || mode != SolarPanelBlockEntity.MODE_NIGHT) {
			helper.fail("snow layer at night should be 0 EU / MODE_NIGHT (NIGHT > SNOW), got " + got
					+ " EU, mode " + mode);
		}
		helper.succeed();
	}

	// ── PHY: face isolation — working surface (top) must not emit EU ────────────────

	/**
	 * The solar panel's top face (working surface) does not expose an energy output interface; the
	 * other five faces are OUT-only.
	 * Mirrors: SolarPanelGameTest.tcSolar001Phy01_topFaceNoOutput
	 *
	 * @implements TC-SOLAR-001-PHY01 — the solar panel's top face (working surface) does not expose an
	 *     energy output interface; the other five faces are OUT-only (R-NRG-03).
	 * @covers R-NRG-03
	 */
	public static void tcSolar001Phy01_topFaceNoOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		assertTopFaceWorkingSurface(helper, "solar panel");
		helper.succeed();
	}

	// ── PRF: performance / config contract ──────────────────────────────────────────

	/**
	 * Production rate per tick equals Config.solarEuPerTick (× globalEuRateMultiplier); the config
	 * constant is the source of truth.
	 * Mirrors: SolarPanelGameTest.tcSolar001Prf01_euRateMatchesConfig
	 *
	 * @implements TC-SOLAR-001-PRF01 — production rate per tick equals {@code GeneratorConfig.solarEuPerTick}
	 *     (× globalEuRateMultiplier). Config constant is the source of truth, not the concept doc.
	 * @covers R-NRG-04
	 */
	public static void tcSolar001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(GeneratorConfig.solarEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("EU/tick mismatch: expected " + expected + " (solarEuPerTick="
					+ GeneratorConfig.solarEuPerTick + " × globalEuRateMultiplier=" + Config.globalEuRateMultiplier
					+ ") got " + got);
		}
		helper.succeed();
	}

	// ── Moonlit panel (night generator — inverse conditions of the base panel) ───────

	/**
	 * The moonlit panel is night-only: by clear day it must produce 0 EU.
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Neg01_noEuByDay
	 *
	 * @implements TC-MOONLIT-001-NEG01 — moonlit panel is night-only: by clear day it must produce 0 EU. @covers
	 *     R-NRG-15
	 */
	public static void tcMoonlit001Neg01_noEuByDay(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setClearDay(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		drive(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("moonlit panel generated " + amount + " EU by day; expected 0");
		}
		helper.succeed();
	}

	/**
	 * Night + rain flags MODE_NIGHT_WEATHER (output 0, MOD-003).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Sta01_rainFlagsWeatherMode
	 *
	 * @implements TC-MOONLIT-001-STA01 — night + rain flags the weather mode (output 0, MOD-003). @covers R-NRG-15
	 */
	public static void tcMoonlit001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		setRaining(helper, false);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		drive(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		if (mode != MoonlitSolarPanelBlockEntity.MODE_NIGHT_WEATHER) {
			helper.fail("expected MODE_NIGHT_WEATHER (" + MoonlitSolarPanelBlockEntity.MODE_NIGHT_WEATHER
					+ "), got " + mode + " (isRaining=" + helper.getLevel().isRaining() + ")");
		}
		helper.succeed();
	}

	/**
	 * A night thunderstorm flags MODE_NIGHT_WEATHER but keeps a small trickle
	 * (moonlitWeatherEuPerTick EU/t) instead of going dark.
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Sta03_thunderYieldsWeatherTrickle
	 *
	 * @implements TC-MOONLIT-001-STA03 — a night thunderstorm flags MODE_NIGHT_WEATHER but, unlike the
	 *     day panels (0 EU), the moonlit panel keeps a small trickle: {@code moonlitWeatherEuPerTick}
	 *     EU/t, instead of going dark. Rain shares this code path (STA01 covers the rain mode flag).
	 * @covers R-NRG-15
	 */
	public static void tcMoonlit001Sta03_thunderYieldsWeatherTrickle(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		setRaining(helper, true); // thunderstorm
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		int ticks = 20;
		drive(panel, helper, ticks);
		long amount = panel.getEnergyStorage().getAmount();
		int perTick = Math.max(1, Math.round(GeneratorConfig.moonlitWeatherEuPerTick * Config.globalEuRateMultiplier));
		long expected = (long) perTick * ticks;
		int mode = panel.getDataAccess().get(3);
		if (mode != MoonlitSolarPanelBlockEntity.MODE_NIGHT_WEATHER) {
			helper.fail("moonlit rain should flag MODE_NIGHT_WEATHER ("
					+ MoonlitSolarPanelBlockEntity.MODE_NIGHT_WEATHER + "), got " + mode);
		}
		if (amount != expected) {
			helper.fail("moonlit thunder trickle: got " + amount + " EU over " + ticks
					+ " ticks (expected " + expected + " = " + perTick + "/t)");
		}
		helper.succeed();
	}

	/**
	 * An opaque block above cancels sky access at night → 0 EU (MOD-004).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Neg03_opaqueBlockAboveYieldsZero
	 *
	 * @implements TC-MOONLIT-001-NEG03 — opaque block above cancels sky access at night → 0 EU (MOD-004). @covers
	 *     R-NRG-15
	 */
	public static void tcMoonlit001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.STONE);
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		drive(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("moonlit generated " + amount + " EU under stone at night; expected 0");
		}
		helper.succeed();
	}

	/**
	 * Leaves above at night → MODE_NIGHT_PARTIAL, output × solarTransparentFactor.
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Sta02_leavesAbovePartialHalvesOutput
	 *
	 * @implements TC-MOONLIT-001-STA02 — leaves above at night → MODE_NIGHT_PARTIAL, output ×factor (2→1). @covers
	 *     R-NRG-15
	 */
	public static void tcMoonlit001Sta02_leavesAbovePartialHalvesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Math.round(GeneratorConfig.moonlitEuPerTick
				* GeneratorConfig.solarTransparentFactor)
				* Config.globalEuRateMultiplier));
		int mode = panel.getDataAccess().get(3);
		if (got != expected || mode != MoonlitSolarPanelBlockEntity.MODE_NIGHT_PARTIAL) {
			helper.fail("moonlit under leaves: got " + got + " EU (expected " + expected + "), mode " + mode
					+ " (expected MODE_NIGHT_PARTIAL)");
		}
		helper.succeed();
	}

	/**
	 * Moonlit top face (working surface) emits no EU; the other five faces are OUT-only.
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Phy01_topFaceNoOutput
	 *
	 * @implements TC-MOONLIT-001-PHY01 — top face (working surface) emits no EU; other five faces OUT-only. @covers
	 *     R-NRG-03
	 */
	public static void tcMoonlit001Phy01_topFaceNoOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		assertTopFaceWorkingSurface(helper, "moonlit panel");
		helper.succeed();
	}

	/**
	 * Moonlit EU/tick equals Config.moonlitEuPerTick (× globalEuRateMultiplier).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Prf01_euRateMatchesConfig
	 *
	 * @implements TC-MOONLIT-001-PRF01 — EU/tick equals Config.moonlitEuPerTick (× globalEuRateMultiplier). @covers
	 *     R-NRG-04
	 */
	public static void tcMoonlit001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(GeneratorConfig.moonlitEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("moonlit EU/tick mismatch: expected " + expected + " (moonlitEuPerTick="
					+ GeneratorConfig.moonlitEuPerTick + ") got " + got);
		}
		helper.succeed();
	}

	/**
	 * Moonlit buffer caps at Config.solarBuffer (use-it-or-lose-it).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Prf02_bufferCapsAtMax
	 *
	 * @implements TC-MOONLIT-001-PRF02 — buffer caps at Config.solarBuffer (use-it-or-lose-it). @covers R-NRG-01
	 */
	public static void tcMoonlit001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().setAmountUntracked(GeneratorConfig.solarBuffer);
		drive(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		if (got != GeneratorConfig.solarBuffer) {
			helper.fail("moonlit buffer changed from cap: expected " + GeneratorConfig.solarBuffer + " got " + got);
		}
		helper.succeed();
	}

	/**
	 * A night evolution chip evolves the base panel into the moonlit panel, carrying the stored EU
	 * and consuming the chip (shared evolveInto, MOD-166 #4).
	 * Mirrors: SolarPanelGameTest.tcSolar001Fun03_nightChipEvolvesToMoonlit
	 *
	 * @implements TC-SOLAR-001-FUN03 — a night evolution chip evolves the base panel into the moonlit
	 *     panel, carrying the stored EU and consuming the chip (shared evolveInto, MOD-166 #4).
	 */
	public static void tcSolar001Fun03_nightChipEvolvesToMoonlit(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setNight(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_NIGHT.get()));
		long energy0 = 1500L;
		panel.getEnergyStorage().setAmountUntracked(energy0);
		BlockPos abs = panel.getBlockPos();
		for (int i = 0; i <= GeneratorConfig.solarEvolveTicks
				&& helper.getLevel().getBlockState(abs).getBlock() == ModContent.SOLAR_PANEL.get(); i++) {
			panel.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
		if (helper.getLevel().getBlockState(abs).getBlock() != ModContent.MOONLIT_SOLAR_PANEL.get()) {
			helper.fail("night chip did not evolve panel into the moonlit panel");
		}
		if (!(helper.getLevel().getBlockEntity(abs) instanceof dev.alaindustrial.block.entity.MachineBlockEntity evolved)) {
			helper.fail("evolved moonlit panel has no MachineBlockEntity");
			return;
		}
		long energy1 = evolved.getEnergyStorage().getAmount();
		if (energy1 < energy0) {
			helper.fail("evolution lost stored EU: " + energy0 + " -> " + energy1);
		}
		if (!evolved.getItem(SolarPanelBlockEntity.CHIP_SLOT).isEmpty()) {
			helper.fail("evolution did not consume the chip slot");
		}
		helper.succeed();
	}

	// ── Daylight panel (T2 day branch — 4 EU/t, day-only) ────────────────────────────

	/**
	 * Rain/thunder stops daylight generation entirely (0 EU; MOD-003).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Neg02_rainYieldsZeroEu
	 *
	 * @implements TC-DAYLIGHT-001-NEG02 — rain/thunder stops generation entirely (0 EU; MOD-003).
	 * @covers R-NRG-15
	 */
	public static void tcDaylight001Neg02_rainYieldsZeroEu(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		setRaining(helper, false);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		drive(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("rain: daylight generated " + amount + " EU (expected 0 — see MOD-003)");
		}
		helper.succeed();
	}

	/**
	 * An opaque block above cancels the daylight panel's sky access → 0 EU (MOD-004 direct scan).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Neg03_opaqueBlockAboveYieldsZero
	 *
	 * @implements TC-DAYLIGHT-001-NEG03 — opaque block above cancels sky access → 0 EU (MOD-004 direct scan). @covers
	 *     R-NRG-15
	 */
	public static void tcDaylight001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.STONE);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		drive(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("daylight generated " + amount + " EU under stone; expected 0");
		}
		helper.succeed();
	}

	/**
	 * Leaves above the daylight panel → MODE_DAY_PARTIAL, output × solarTransparentFactor.
	 * Mirrors: SolarPanelGameTest.tcDaylight001Sta02_leavesAbovePartialHalvesOutput
	 *
	 * @implements TC-DAYLIGHT-001-STA02 — leaves above → MODE_DAY_PARTIAL, output ×solarTransparentFactor (4→2).
	 *     @covers R-NRG-15
	 */
	public static void tcDaylight001Sta02_leavesAbovePartialHalvesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Math.round(GeneratorConfig.daylightEuPerTick
				* GeneratorConfig.solarTransparentFactor)
				* Config.globalEuRateMultiplier));
		int mode = panel.getDataAccess().get(3);
		if (got != expected || mode != DaylightSolarPanelBlockEntity.MODE_DAY_PARTIAL) {
			helper.fail("daylight under leaves: got " + got + " EU (expected " + expected + "), mode " + mode
					+ " (expected MODE_DAY_PARTIAL)");
		}
		helper.succeed();
	}

	/**
	 * Glass above keeps full output (CLEAR).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Fun02_glassAboveStaysFull
	 *
	 * @implements TC-DAYLIGHT-001-FUN02 — glass above keeps full output (CLEAR). @covers R-NRG-15
	 */
	public static void tcDaylight001Fun02_glassAboveStaysFull(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.GLASS);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(GeneratorConfig.daylightEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("daylight under glass should stay full: got " + got + " expected " + expected);
		}
		helper.succeed();
	}

	/**
	 * Day + rain flags MODE_DAY_WEATHER (output 0, MOD-003).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Sta01_rainFlagsWeatherMode
	 *
	 * @implements TC-DAYLIGHT-001-STA01 — day + rain flags the weather mode (output 0, MOD-003). @covers R-NRG-15
	 */
	public static void tcDaylight001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		setRaining(helper, false);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		drive(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		if (mode != DaylightSolarPanelBlockEntity.MODE_DAY_WEATHER) {
			helper.fail("expected MODE_DAY_WEATHER (" + DaylightSolarPanelBlockEntity.MODE_DAY_WEATHER
					+ "), got " + mode + " (isRaining=" + helper.getLevel().isRaining() + ")");
		}
		helper.succeed();
	}

	/**
	 * Daylight top face (working surface) emits no EU; the other five faces are OUT-only.
	 * Mirrors: SolarPanelGameTest.tcDaylight001Phy01_topFaceNoOutput
	 *
	 * @implements TC-DAYLIGHT-001-PHY01 — top face (working surface) emits no EU; other five faces OUT-only. @covers
	 *     R-NRG-03
	 */
	public static void tcDaylight001Phy01_topFaceNoOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		assertTopFaceWorkingSurface(helper, "daylight panel");
		helper.succeed();
	}

	/**
	 * Daylight EU/tick equals Config.daylightEuPerTick (× globalEuRateMultiplier).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Prf01_euRateMatchesConfig
	 *
	 * @implements TC-DAYLIGHT-001-PRF01 — EU/tick equals Config.daylightEuPerTick (× globalEuRateMultiplier). @covers
	 *     R-NRG-04
	 */
	public static void tcDaylight001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(GeneratorConfig.daylightEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("daylight EU/tick mismatch: expected " + expected + " (daylightEuPerTick="
					+ GeneratorConfig.daylightEuPerTick + ") got " + got);
		}
		helper.succeed();
	}

	/**
	 * Daylight buffer caps at Config.solarBuffer (use-it-or-lose-it).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Prf02_bufferCapsAtMax
	 *
	 * @implements TC-DAYLIGHT-001-PRF02 — buffer caps at Config.solarBuffer (use-it-or-lose-it). @covers R-NRG-01
	 */
	public static void tcDaylight001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().setAmountUntracked(GeneratorConfig.solarBuffer);
		drive(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		if (got != GeneratorConfig.solarBuffer) {
			helper.fail("daylight buffer changed from cap: expected " + GeneratorConfig.solarBuffer + " got " + got);
		}
		helper.succeed();
	}

	// ── STA: advanced sky-blocker classes (ice / glowstone) ──────────────────────────

	/**
	 * An ice block above classifies PARTIAL, not BLOCKED: Ice is noOcclusion() and its full-cube
	 * shape stops skylight propagation, so SolarSky.classify falls through to PARTIAL (MOD-004) —
	 * reduced output via solarTransparentFactor, not zero.
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta13_iceAboveYieldsBlocked
	 *
	 * @implements TC-SOLAR-001-STA13 — an ice block above the base panel classifies PARTIAL, not
	 *     BLOCKED. {@code Blocks.ICE} is registered with {@code .noOcclusion()}
	 *     ({@code canOcclude()=false}) and its default full-cube shape makes
	 *     {@code propagatesSkylightDown()} false too, so {@link dev.alaindustrial.core.environment.SolarSky#classify}
	 *     falls through both the "skip" and "BLOCKED" branches to {@code Access.PARTIAL} — the same
	 *     bucket as leaves/cobweb (MOD-004): reduced output via {@code GeneratorConfig.solarTransparentFactor},
	 *     not zero. (An earlier version of this test assumed ice was occlusion-opaque like stone; it is
	 *     not — verified against {@code Blocks.ICE}'s {@code BlockBehaviour.Properties} and
	 *     {@code SolarSky.classify}'s actual branch order.)
	 * @covers R-VIS-01
	 */
	public static void tcSolar001Sta13_iceAboveYieldsBlocked(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.ICE);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		int mode = panel.getDataAccess().get(3);
		// production is rounded PER TICK (Math.round(base * factor)), not on the 20-tick total.
		long expected = (long) Math.round(GeneratorConfig.solarEuPerTick * GeneratorConfig.solarTransparentFactor) * 20;
		if (got != expected || mode != SolarPanelBlockEntity.MODE_PARTIAL) {
			helper.fail("ice above should yield " + expected + " EU / MODE_PARTIAL (canOcclude()=false on Ice, so"
					+ " SolarSky.classify falls through to PARTIAL, not BLOCKED), got " + got + " EU, mode " + mode);
		}
		helper.succeed();
	}

	/**
	 * A glowstone block above is opaque to skylight (block light is not sky light), so it
	 * classifies BLOCKED like stone: 0 EU.
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta15_glowstoneAboveYieldsBlocked
	 *
	 * @implements TC-SOLAR-001-STA15 — a Glowstone block above the base panel is opaque to skylight
	 *     (block light emitted by the block itself is not sky light), so it classifies BLOCKED like stone:
	 *     0 EU. Guards against conflating "emits light" with "lets sky light through".
	 * @covers R-VIS-01
	 */
	public static void tcSolar001Sta15_glowstoneAboveYieldsBlocked(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.GLOWSTONE);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		drive(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		if (got != 0) {
			helper.fail("glowstone above should block generation: got " + got + " EU; expected 0"
					+ " (block light must not be treated as sky light)");
		}
		helper.succeed();
	}

	// ── NEG: advanced negative classes (water above) ─────────────────────────────────

	/**
	 * A water source block directly above is opaque to skylight (non-empty fluid state trips the
	 * SolarSky fluid check): 0 EU, same as a stone roof.
	 * Mirrors: SolarPanelGameTest.tcSolar001Neg08_waterAboveYieldsZero
	 *
	 * @implements TC-SOLAR-001-NEG08 — a water source block directly above the base panel is opaque to
	 *     skylight ({@code canOcclude()=false} but a non-empty fluid state trips the {@code SolarSky}
	 *     fluid check), so the panel yields 0 EU, same as a stone roof.
	 * @covers R-NRG-04
	 */
	public static void tcSolar001Neg08_waterAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.WATER);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("generated " + amount + " EU under water; expected 0 (fluid blocks skylight)");
		}
		helper.succeed();
	}

	// ── PRF: globalEuRateMultiplier + config reload ──────────────────────────────────

	/**
	 * Config.globalEuRateMultiplier scales the per-tick output linearly (2.0× → double EU/t); the
	 * mutable static is restored at the end to avoid poisoning the batch.
	 * Mirrors: SolarPanelGameTest.tcSolar001Prf03_globalRateMultiplierScalesOutput
	 *
	 * @implements TC-SOLAR-001-PRF03 — {@code Config.globalEuRateMultiplier} scales the base panel's
	 *     per-tick output linearly (2.0× → double EU/t). The knob is a mutable static, so it is restored
	 *     to its original value at the end of the test to avoid poisoning any other test in the same batch.
	 * @covers R-NRG-12
	 */
	public static void tcSolar001Prf03_globalRateMultiplierScalesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		try (ConfigOverrides o = ConfigOverrides.sync().set("globalEuRateMultiplier", 2.0f)) {
			panel.getEnergyStorage().setAmountUntracked(0);
			drive(panel, helper, 1);
			long got = panel.getEnergyStorage().getAmount();
			long expected = Math.max(1, Math.round(GeneratorConfig.solarEuPerTick * 2.0f));
			if (got != expected) {
				helper.fail("globalEuRateMultiplier=2.0 expected " + expected + " EU/t, got " + got);
			}
		}
		helper.succeed();
	}

	/**
	 * A changed GeneratorConfig.solarEuPerTick is picked up by the very next production tick (the field is
	 * read live in produce(), not cached at construction); restored afterward.
	 * Mirrors: SolarPanelGameTest.tcSolar001Prf04_configChangeAppliesNextTick
	 *
	 * @implements TC-SOLAR-001-PRF04 — a changed {@code GeneratorConfig.solarEuPerTick} is picked up by the very
	 *     next production tick (the field is read live in {@code produce()}, not cached at block-entity
	 *     construction). This is the in-process equivalent of a config file `/reload`: the datapack-reload
	 *     path ({@code Config.loadFrom}) simply re-assigns the same static fields that {@code produce()}
	 *     reads every tick, so mutating the field directly exercises the identical "new value applies
	 *     without a restart" contract without needing to touch the filesystem or fire a real reload event.
	 *     The field is restored afterward to avoid poisoning other tests in the same batch.
	 * @covers R-CFG-02
	 */
	public static void tcSolar001Prf04_configChangeAppliesNextTick(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		int saved = GeneratorConfig.solarEuPerTick;
		try (ConfigOverrides o = ConfigOverrides.sync().set("solarEuPerTick", saved * 3)) {
			panel.getEnergyStorage().setAmountUntracked(0);
			drive(panel, helper, 1);
			long got = panel.getEnergyStorage().getAmount();
			long expected = Math.max(1, Math.round(GeneratorConfig.solarEuPerTick * Config.globalEuRateMultiplier));
			if (got != expected) {
				helper.fail("new solarEuPerTick=" + GeneratorConfig.solarEuPerTick + " not applied: expected "
						+ expected
						+ " got " + got);
			}
		}
		helper.succeed();
	}

	// ── CON: neighbour connectivity / network split ──────────────────────────────────

	/**
	 * A BatteryBox adjacent to the panel but facing AWAY (single-axis input face, MOD-006)
	 * receives no EU: no compatible interface meets across that face pair.
	 * Mirrors: SolarPanelGameTest.tcSolar001Con01_batteryBoxWrongFacingGetsNothing
	 *
	 * @implements TC-SOLAR-001-CON01 — a BatteryBox adjacent to the panel but facing AWAY (its input face
	 *     is single-axis, MOD-006) does not receive EU: no compatible interface meets across that face pair.
	 * @covers R-CON-01
	 */
	public static void tcSolar001Con01_batteryBoxWrongFacingGetsNothing(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		// ample supply to push, if a route existed
		panel.getEnergyStorage().setAmountUntracked(GeneratorConfig.solarBuffer);

		BlockPos batteryPos = POS.relative(Direction.EAST);
		// BatteryBox input face = FACING (MOD-006). FACING=NORTH means input faces north, not the panel
		// sitting on its WEST side — so the contacting face pair is not an input, EU cannot flow in.
		helper.setBlock(batteryPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		var battery = helper.getBlockEntity(batteryPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		if (battery == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		drive(panel, helper, 20);
		if (battery.getEnergyStorage().getAmount() != 0) {
			helper.fail("battery_box facing away received " + battery.getEnergyStorage().getAmount()
					+ " EU; expected 0 (no compatible interface across that face pair)");
		}
		helper.succeed();
	}

	/**
	 * An opaque block (stone) between the panel and a BatteryBox, with no cable bridging the gap,
	 * blocks delivery entirely: energy does not pass through plain blocks.
	 * Mirrors: SolarPanelGameTest.tcSolar001Con02_opaqueGapBlocksDelivery
	 *
	 * @implements TC-SOLAR-001-CON02 — an opaque block (stone) between the panel and a BatteryBox, with no
	 *     cable bridging the gap, blocks delivery entirely: energy does not pass through plain blocks.
	 * @covers R-CON-10
	 */
	public static void tcSolar001Con02_opaqueGapBlocksDelivery(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(GeneratorConfig.solarBuffer);

		BlockPos gapPos = POS.relative(Direction.EAST);
		BlockPos batteryPos = gapPos.relative(Direction.EAST);
		helper.setBlock(gapPos, Blocks.STONE);
		helper.setBlock(batteryPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		var battery = helper.getBlockEntity(batteryPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		if (battery == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		drive(panel, helper, 20);
		if (battery.getEnergyStorage().getAmount() != 0) {
			helper.fail("EU crossed an opaque stone gap: battery_box has "
					+ battery.getEnergyStorage().getAmount() + " EU; expected 0");
		}
		helper.succeed();
	}

	/**
	 * A consumer placed directly adjacent to an already-generating panel starts receiving EU
	 * without any warm-up: the very next serverTick after placement moves EU in.
	 * Mirrors: SolarPanelGameTest.tcSolar001Con03_immediateDeliveryOnPlacement
	 *
	 * @implements TC-SOLAR-001-CON03 — a consumer placed directly adjacent to an already-generating panel
	 *     starts receiving EU without any warm-up: the very next serverTick after placement moves EU in.
	 * @covers R-CON-15
	 */
	public static void tcSolar001Con03_immediateDeliveryOnPlacement(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		// generation already running, buffer full
		panel.getEnergyStorage().setAmountUntracked(GeneratorConfig.solarBuffer);

		BlockPos batteryPos = POS.relative(Direction.EAST);
		helper.setBlock(batteryPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		var battery = helper.getBlockEntity(batteryPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		if (battery == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		drive(panel, helper, 1); // one tick after placement — no pause, no re-placement
		if (battery.getEnergyStorage().getAmount() <= 0) {
			helper.fail("battery_box received no EU on the tick immediately after placement");
		}
		helper.succeed();
	}

	/**
	 * Two LV consumers on two different side faces of the same panel never together exceed the
	 * panel's per-tick production: the output is not duplicated per face.
	 * Mirrors: SolarPanelGameTest.tcSolar001Con04_twoReceiversDoNotDoubleOutput
	 *
	 * @implements TC-SOLAR-001-CON04 — two LV consumers on two different side faces of the same panel
	 *     never together exceed the panel's own per-tick production ({@code GeneratorConfig.solarEuPerTick} ×
	 *     {@code globalEuRateMultiplier}): the output is not duplicated per face.
	 * @covers R-CON-16
	 */
	public static void tcSolar001Con04_twoReceiversDoNotDoubleOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);

		BlockPos batteryEastPos = POS.relative(Direction.EAST);
		BlockPos batterySouthPos = POS.relative(Direction.SOUTH);
		helper.setBlock(batteryEastPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(batterySouthPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		var batteryEast = helper.getBlockEntity(batteryEastPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		var batterySouth = helper.getBlockEntity(batterySouthPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		if (batteryEast == null || batterySouth == null) {
			helper.fail("battery_box block entities missing after placement");
		}
		drive(panel, helper, 1); // one production tick worth of EU to distribute
		long total = batteryEast.getEnergyStorage().getAmount() + batterySouth.getEnergyStorage().getAmount();
		long perTickCap = Math.max(1, Math.round(GeneratorConfig.solarEuPerTick * Config.globalEuRateMultiplier));
		if (total > perTickCap) {
			helper.fail("two receivers together got " + total + " EU in one tick; expected <= " + perTickCap
					+ " (output must not double per face)");
		}
		helper.succeed();
	}

	/**
	 * While an adjacent BatteryBox is full, the panel keeps generating into its own internal
	 * buffer (capped at GeneratorConfig.solarBuffer); once the box has room again delivery resumes on the
	 * next tick.
	 * Mirrors: SolarPanelGameTest.tcSolar001Con05_bufferHoldsWhileReceiverFull
	 *
	 * @implements TC-SOLAR-001-CON05 — while an adjacent BatteryBox is full, the panel keeps generating
	 *     into its own internal buffer (capped at {@code GeneratorConfig.solarBuffer}) instead of losing the EU;
	 *     once the BatteryBox has room again delivery resumes automatically on the next tick.
	 * @covers R-CON-01, R-NRG-01
	 */
	public static void tcSolar001Con05_bufferHoldsWhileReceiverFull(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);

		BlockPos batteryPos = POS.relative(Direction.EAST);
		helper.setBlock(batteryPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		var battery = helper.getBlockEntity(batteryPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		if (battery == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		battery.getEnergyStorage().setAmountUntracked(battery.getEnergyStorage().getCapacity()); // full: cannot accept

		drive(panel, helper, 20); // keep generating while the only receiver is full
		long panelAmount = panel.getEnergyStorage().getAmount();
		if (panelAmount <= 0) {
			helper.fail("panel lost its EU instead of buffering it while the receiver was full: "
					+ panelAmount);
		}
		if (panelAmount > GeneratorConfig.solarBuffer) {
			helper.fail("panel buffer exceeded its cap while holding EU: " + panelAmount + " > "
					+ GeneratorConfig.solarBuffer);
		}

		// Free up room in the receiver: delivery must resume automatically, no player action beyond time.
		battery.getEnergyStorage().setAmountUntracked(0);
		long before = panel.getEnergyStorage().getAmount();
		drive(panel, helper, 5);
		if (battery.getEnergyStorage().getAmount() <= 0) {
			helper.fail("delivery did not resume once the receiver had room again");
		}
		if (panel.getEnergyStorage().getAmount() > before) {
			// Not strictly required to fall, but it must not just keep climbing past cap unmoved.
			if (panel.getEnergyStorage().getAmount() > GeneratorConfig.solarBuffer) {
				helper.fail("panel buffer exceeded cap after resuming delivery");
			}
		}
		helper.succeed();
	}

	// --- Mirror Concentrator, the day branch's third rung (MOD-602) ---

	/**
	 * Snow blacks the concentrator OUT, where the panels below it keep a floored trickle.
	 *
	 * <p>That difference is the whole point of the block: flat cells under a dusting still catch
	 * something, a snowed-over mirror reflects nothing. Both halves are asserted here — a change that
	 * gave the concentrator the family's 1 EU/t floor back would otherwise stay green.
	 *
	 * @implements MOD-602 — snow blacks the concentrator out while the daylight panel keeps its trickle.
	 */
	public static void mod602_snowBlacksOutConcentratorButNotDaylightPanel(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.RADIANT_SOLAR_PANEL.get());
		setClearDay(helper);
		helper.setBlock(POS.above(), Blocks.SNOW);
		RadiantSolarPanelBlockEntity concentrator = concentratorAt(helper);
		drive(concentrator, helper, 2);
		int mode = concentrator.getDataAccess().get(3);
		int rate = concentrator.getDataAccess().get(2);
		if (mode != RadiantSolarPanelBlockEntity.MODE_DAY_SNOW) {
			helper.fail("snow should flag MODE_DAY_SNOW ("
					+ RadiantSolarPanelBlockEntity.MODE_DAY_SNOW + "), got " + mode);
		}
		if (rate != 0) {
			helper.fail("snow must stop the concentrator dead, got " + rate + " EU/t");
		}

		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.SNOW);
		DaylightSolarPanelBlockEntity daylight = daylightAt(helper);
		drive(daylight, helper, 2);
		if (daylight.getDataAccess().get(2) <= 0) {
			helper.fail("the daylight panel must keep its trickle under snow, got "
					+ daylight.getDataAccess().get(2) + " EU/t");
		}
		helper.setBlock(POS.above(), Blocks.AIR);
		helper.succeed();
	}

	/**
	 * The noon window lifts output, and outside the window it does not.
	 *
	 * <p>Both ends are checked: "noon is higher" alone would also pass for code that lifted output all
	 * day long.
	 *
	 * @implements MOD-602 — the noon window lifts the concentrator's output, and only inside it.
	 */
	public static void mod602_noonWindowLiftsOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.RADIANT_SOLAR_PANEL.get());
		var level = helper.getLevel();
		var server = level.getServer();
		setClearDay(helper);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
		level.updateSkyBrightness();
		RadiantSolarPanelBlockEntity concentrator = concentratorAt(helper);
		drive(concentrator, helper, 2);
		int noonMode = concentrator.getDataAccess().get(3);
		int noonRate = concentrator.getDataAccess().get(2);
		if (noonMode != RadiantSolarPanelBlockEntity.MODE_DAY_PEAK) {
			helper.fail("at noon expected MODE_DAY_PEAK ("
					+ RadiantSolarPanelBlockEntity.MODE_DAY_PEAK + "), got " + noonMode);
		}

		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set day");
		level.updateSkyBrightness();
		drive(concentrator, helper, 2);
		int dayRate = concentrator.getDataAccess().get(2);
		if (concentrator.getDataAccess().get(3) != RadiantSolarPanelBlockEntity.MODE_DAY) {
			helper.fail("outside the noon window expected MODE_DAY, got "
					+ concentrator.getDataAccess().get(3));
		}
		if (noonRate <= dayRate) {
			helper.fail("noon must beat plain day: noon " + noonRate + " EU/t against " + dayRate);
		}
		helper.succeed();
	}

	/**
	 * The daylight panel takes the resonance chip and refuses an alignment chip.
	 *
	 * <p>The refusal is the half that matters: a slot that swallows any chip looks like it is working,
	 * and the player then waits a day for an evolution that will never come.
	 *
	 * @implements MOD-602 — the daylight panel's slot takes the resonance chip and nothing else.
	 */
	public static void mod602_daylightPanelTakesResonanceChipOnly(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		DaylightSolarPanelBlockEntity panel = daylightAt(helper);
		ItemStack resonance = new ItemStack(ModContent.RESONANCE_CHIP.get());
		ItemStack alignment = new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get());
		if (!panel.canPlaceItemThroughFace(DaylightSolarPanelBlockEntity.CHIP_SLOT, resonance, Direction.UP)) {
			helper.fail("the daylight panel refused a resonance chip in an empty slot");
		}
		if (panel.canPlaceItemThroughFace(DaylightSolarPanelBlockEntity.CHIP_SLOT, alignment, Direction.UP)) {
			helper.fail("the daylight panel took an alignment chip — that one belongs a rung lower "
					+ "and evolves nothing here");
		}
		panel.setItem(DaylightSolarPanelBlockEntity.CHIP_SLOT, resonance.copy());
		if (panel.canPlaceItemThroughFace(DaylightSolarPanelBlockEntity.CHIP_SLOT, resonance, Direction.UP)) {
			helper.fail("automation stacked a second chip into an occupied slot");
		}
		helper.succeed();
	}

	/**
	 * The daylight panel grows into the concentrator and carries its stored energy across.
	 *
	 * @implements MOD-602 — the daylight panel grows into the concentrator, carrying its energy.
	 */
	public static void mod602_daylightPanelEvolvesIntoConcentrator(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		DaylightSolarPanelBlockEntity panel = daylightAt(helper);
		panel.setItem(DaylightSolarPanelBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.RESONANCE_CHIP.get()));
		panel.getEnergyStorage().setAmountUntracked(500);
		// One tick short of the threshold: the transform must land on the next one.
		panel.setEvolveProgressTicks(GeneratorConfig.solarEvolveTicks - 1);
		drive(panel, helper, 2);
		if (helper.getLevel().getBlockState(helper.absolutePos(POS)).getBlock()
				!= ModContent.RADIANT_SOLAR_PANEL.get()) {
			helper.fail("the panel did not become a concentrator at the threshold");
		}
		RadiantSolarPanelBlockEntity grown = concentratorAt(helper);
		if (grown == null) {
			helper.fail("no concentrator block entity after the transform");
		}
		if (grown.getEnergyStorage().getAmount() <= 0) {
			helper.fail("the transform lost the stored energy");
		}
		helper.succeed();
	}
}
