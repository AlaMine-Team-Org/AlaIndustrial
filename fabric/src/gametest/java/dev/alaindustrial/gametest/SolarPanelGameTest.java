package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the solar panel — generation condition (R-NRG-15) + weather + evolution.
 * The decision-table showcase (time × sky × weather).
 *
 * <p><b>Isolation note (important).</b> Every gametest in a batch shares ONE {@code ServerLevel}, so
 * world time is global. {@code isBrightOutside()} reads the level's {@code skyDarken} field, which the
 * server only recomputes inside {@code tickTime()} on the next tick. The earlier design set time, then
 * deferred its assertions via {@link GameTestHelper#runAfterDelay} to let that tick pass — but yielding
 * let a CONCURRENT night test run {@code /time set midnight} in the gap, flipping the clock for everyone
 * (day tests saw {@code bright=false}; the moonlit day-negative saw night and produced EU). The fix:
 * every test sets time/weather and then calls {@link net.minecraft.world.level.Level#updateSkyBrightness()}
 * to recompute {@code skyDarken} <i>synchronously</i>, and reads production in the SAME method body with no
 * {@code runAfterDelay}. A test body runs to completion on the server thread without another test
 * interleaving, so the world state a test establishes cannot be raced.
 */
public class SolarPanelGameTest {

	/**
	 * @implements TC-SOLAR-001-FUN01 — generates EU by day under open sky, accumulating at exactly the
	 *     config rate × ticks. The buffer (8000) is far from full at 20 ticks × 1 EU/t = 20 EU, so the
	 *     rate is read cleanly. A regression that halves/doubles {@code solarEuPerTick} or drops the
	 *     global multiplier is caught here, not just by the neighbouring PRF01 — an upper-bound-only
	 *     {@code amount > 0} would pass a panel that generates 0.1 EU/t or 100 EU/t.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Fun01_generatesByDay(GameTestHelper helper) {
		GeneratorEnergyScenarios.solarPanelGeneratesByDay(helper);
	}

	/**
	 * @implements TC-SOLAR-001-STA02 — rain flags the weather production mode (day + rain → MODE_WEATHER).
	 *     The mode flag fires for the GUI even though output is 0 in weather (MOD-003; see NEG02). Rain set
	 *     after the clear-day brightness is settled; everything synchronous.
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta02_rainFlagsWeatherMode(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Sta02_rainFlagsWeatherMode(helper);
	}

	/**
	 * @implements TC-SOLAR-001-STA03 — thunderstorm also flags MODE_WEATHER (same zero-output as rain, MOD-003).
	 *     Thunder always co-occurs with rain; both flags set so {@code isRaining()} reads true.
	 *
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta03_thunderFlagsWeatherMode(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Sta03_thunderFlagsWeatherMode(helper);
	}

	/** @implements TC-SOLAR-001-FUN02 — a day evolution chip evolves the panel into the daylight panel,
	 *     carrying the stored EU and consuming the chip (the shared evolveInto helper, MOD-166 #4). */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Fun02_dayChipEvolvesToDaylight(GameTestHelper helper) {
		GeneratorEnergyScenarios.solarDayChipEvolvesToDaylight(helper);
	}

	/**
	 * MOD-211 — automation may insert a chip only while the slot is EMPTY. The panel has no FACING, so
	 * {@code getSlotsForFace} exposes the chip slot on every face, the container declares no max stack
	 * size and the chip is a plain 64-stack item: before the guard a hopper fed the slot up to 64.
	 * Regression guard: without the {@code isEmpty()} check in {@code SolarPanelBlockEntity.canPlaceItem}
	 * the second assertion fails.
	 */
	@GameTest
	public void solarPanel_automationCannotStackSecondChip(GameTestHelper helper) {
		SolarPanelScenarios.solarPanel_automationCannotStackSecondChip(helper);
	}

	/**
	 * MOD-211 — evolution consumes exactly ONE chip and carries the rest across. A world saved before
	 * the guard above can still hold a stack here, and the old code handed the shared helper a bare
	 * EMPTY, destroying all 64. Regression guard: without the {@code shrink(1)} the evolved panel's chip
	 * slot comes out empty and the count assertion fails.
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void solarPanel_evolutionConsumesOneChipNotTheStack(GameTestHelper helper) {
		SolarPanelScenarios.solarPanel_evolutionConsumesOneChipNotTheStack(helper);
	}

	// ── NEG: base panel must produce 0 EU when sky/time conditions are wrong ─────────

	/** @implements TC-SOLAR-001-NEG01 — base panel generates 0 EU at night (day-only). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Neg01_noEuAtNight(GameTestHelper helper) {
		GeneratorEnergyScenarios.solarPanelNoEuAtNight(helper);
	}

	/**
	 * @implements TC-SOLAR-001-NEG02 — rain/thunder stops generation entirely (0 EU). MOD-003: rain blocks
	 *     direct sunlight, so the panel produces nothing (the {@code solarWeatherFactor} ×0.5 halving was
	 *     removed). The weather MODE flag still fires (see STA01); only the EU output is zero.
	 *
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Neg02_rainYieldsZeroEu(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Neg02_rainYieldsZeroEu(helper);
	}

	/**
	 * @implements TC-SOLAR-001-NEG03 — an opaque block above cancels sky access → 0 EU.
	 *
	 * <p>Since MOD-004 the panel classifies sky access by scanning the column above it directly
	 * ({@link dev.alaindustrial.core.environment.SolarSky}), not via {@code canSeeSkyFromBelowWater} — so a solid
	 * roof is detected even in the deep gametest region (the old heightmap/below-sea quirk that forced
	 * this case to MANUAL/L3 no longer applies).
	 *
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Neg03_opaqueBlockAboveYieldsZero(helper);
	}

	/**
	 * @implements TC-SOLAR-001-FUN04 — glass above does NOT reduce generation (fully sky-transparent →
	 *     CLEAR, full output, MODE_DAY). MOD-004.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Fun04_glassAboveStaysFull(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Fun04_glassAboveStaysFull(helper);
	}

	/**
	 * @implements TC-SOLAR-001-STA06 — a translucent block (leaves) above flags MODE_PARTIAL and still
	 *     generates (MOD-004). The base panel's 1 EU/t × 0.5 rounds back to 1, so assert the mode flag
	 *     AND the exact 1 EU/t generation (a regression that classifies leaves as BLOCKED → 0 EU, or
	 *     that drops the partial factor, is caught either way).
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta06_leavesAboveFlagPartial(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Sta06_leavesAboveFlagPartial(helper);
	}

	/**
	 * @implements TC-SOLAR-001-STA05 — a snow LAYER ({@code minecraft:snow}) directly above the panel
	 *     flags MODE_SNOW and dims output to {@code max(1, round(solarEuPerTick × solarSnowFactor))}. The
	 *     floor keeps the T1 base of 1 from truncating to 0 in snow, so the panel still trickles 1 EU/t.
	 *     MODE_SNOW beats the BLOCKED/PARTIAL/DAY classification.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta05_snowLayerAboveFlagsSnow(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Sta05_snowLayerAboveFlagsSnow(helper);
	}

	/**
	 * @implements TC-SOLAR-001-STA09 — WEATHER beats SNOW: a snow layer above the panel plus an active
	 *     thunderstorm resolves to MODE_WEATHER with 0 EU, not MODE_SNOW.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta09_snowLayerPlusThunderIsWeather(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Sta09_snowLayerPlusThunderIsWeather(helper);
	}

	/**
	 * @implements TC-SOLAR-001-STA10 — NIGHT beats SNOW: a snow layer above the panel at night yields 0 EU
	 *     (mode NIGHT), never MODE_SNOW.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta10_snowLayerAtNightIsZero(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Sta10_snowLayerAtNightIsZero(helper);
	}

	// ── PHY: face isolation — working surface (top) must not emit EU ────────────────

	/**
	 * @implements TC-SOLAR-001-PHY01 — the solar panel's top face (working surface) does not expose an
	 *     energy output interface; the other five faces are OUT-only (R-NRG-03).
	 *
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcSolar001Phy01_topFaceNoOutput(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Phy01_topFaceNoOutput(helper);
	}

	// ── PRF: performance / config contract ──────────────────────────────────────────

	/**
	 * @implements TC-SOLAR-001-PRF01 — production rate per tick equals {@code Config.solarEuPerTick}
	 *     (× globalEuRateMultiplier). Config constant is the source of truth, not the concept doc.
	 *
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Prf01_euRateMatchesConfig(helper);
	}

	/**
	 * @implements TC-SOLAR-001-PRF02 — buffer caps at {@code Config.solarBuffer} (BVA). Pre-charges the
	 *     panel to {@code cap − 1} (one EU short of full) and drives a clear-day tick: generation of
	 *     ≥1 EU must top the buffer off to exactly {@code cap}, never above. Starting AT the cap only
	 *     proves "stays full", which would also pass a buffer that silently drains — the cap−1 leg is
	 *     what proves the boundary is actually reached and enforced.
	 * @covers R-NRG-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		GeneratorEnergyScenarios.solarPanelBufferCapsAtMaxBva(helper);
	}

	// ── Moonlit panel (night generator — inverse conditions of the base panel) ───────

	/** @implements TC-MOONLIT-001-NEG01 — moonlit panel is night-only: by clear day it must produce 0 EU. @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Neg01_noEuByDay(GameTestHelper helper) {
		SolarPanelScenarios.tcMoonlit001Neg01_noEuByDay(helper);
	}

	/**
	 * @implements TC-MOONLIT-001-FUN01 — moonlit panel generates EU at midnight, accumulating at exactly
	 *     {@code moonlitEuPerTick} × globalEuRateMultiplier × ticks. See {@link #tcSolar001Fun01_generatesByDay}
	 *     for why an upper-bound-only assertion is insufficient. Moonlit buffer (8000) is far from full
	 *     at 20 × 3 EU = 60, so the rate reads cleanly.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Fun01_generatesAtNight(GameTestHelper helper) {
		GeneratorEnergyScenarios.moonlitPanelGeneratesAtNight(helper);
	}

	/** @implements TC-MOONLIT-001-STA01 — night + rain flags the weather mode (output 0, MOD-003). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		SolarPanelScenarios.tcMoonlit001Sta01_rainFlagsWeatherMode(helper);
	}

	/**
	 * @implements TC-MOONLIT-001-STA03 — a night thunderstorm flags MODE_NIGHT_WEATHER but, unlike the
	 *     day panels (0 EU), the moonlit panel keeps a small trickle: {@code moonlitWeatherEuPerTick}
	 *     EU/t, instead of going dark. Rain shares this code path (STA01 covers the rain mode flag).
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Sta03_thunderYieldsWeatherTrickle(GameTestHelper helper) {
		SolarPanelScenarios.tcMoonlit001Sta03_thunderYieldsWeatherTrickle(helper);
	}

	/** @implements TC-MOONLIT-001-NEG03 — opaque block above cancels sky access at night → 0 EU (MOD-004). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		SolarPanelScenarios.tcMoonlit001Neg03_opaqueBlockAboveYieldsZero(helper);
	}

	/** @implements TC-MOONLIT-001-STA02 — leaves above at night → MODE_NIGHT_PARTIAL, output ×factor (2→1). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Sta02_leavesAbovePartialHalvesOutput(GameTestHelper helper) {
		SolarPanelScenarios.tcMoonlit001Sta02_leavesAbovePartialHalvesOutput(helper);
	}

	/** @implements TC-MOONLIT-001-PHY01 — top face (working surface) emits no EU; other five faces OUT-only. @covers R-NRG-03 */
	@GameTest
	public void tcMoonlit001Phy01_topFaceNoOutput(GameTestHelper helper) {
		SolarPanelScenarios.tcMoonlit001Phy01_topFaceNoOutput(helper);
	}

	/** @implements TC-MOONLIT-001-PRF01 — EU/tick equals Config.moonlitEuPerTick (× globalEuRateMultiplier). @covers R-NRG-04 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		SolarPanelScenarios.tcMoonlit001Prf01_euRateMatchesConfig(helper);
	}

	/** @implements TC-MOONLIT-001-PRF02 — buffer caps at Config.solarBuffer (use-it-or-lose-it). @covers R-NRG-01 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		SolarPanelScenarios.tcMoonlit001Prf02_bufferCapsAtMax(helper);
	}

	/** @implements TC-SOLAR-001-FUN03 — a night evolution chip evolves the base panel into the moonlit
	 *     panel, carrying the stored EU and consuming the chip (shared evolveInto, MOD-166 #4). */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Fun03_nightChipEvolvesToMoonlit(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Fun03_nightChipEvolvesToMoonlit(helper);
	}

	// ── Daylight panel (T2 day branch — 4 EU/t, day-only) ────────────────────────────

	/**
	 * @implements TC-DAYLIGHT-001-FUN01 — daylight panel generates EU by day under open sky, accumulating
	 *     at exactly {@code daylightEuPerTick} × globalEuRateMultiplier × ticks. See
	 *     {@link #tcSolar001Fun01_generatesByDay} for why an upper-bound-only assertion is insufficient.
	 *     Daylight buffer (8000) is far from full at 20 × 4 EU = 80, so the rate reads cleanly.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Fun01_generatesByDay(GameTestHelper helper) {
		GeneratorEnergyScenarios.daylightPanelGeneratesByDay(helper);
	}

	/** @implements TC-DAYLIGHT-001-NEG01 — daylight panel produces 0 EU at night (day-only). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Neg01_noEuAtNight(GameTestHelper helper) {
		GeneratorEnergyScenarios.daylightPanelNoEuAtNight(helper);
	}

	/**
	 * @implements TC-DAYLIGHT-001-NEG02 — rain/thunder stops generation entirely (0 EU; MOD-003).
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Neg02_rainYieldsZeroEu(GameTestHelper helper) {
		SolarPanelScenarios.tcDaylight001Neg02_rainYieldsZeroEu(helper);
	}

	/** @implements TC-DAYLIGHT-001-NEG03 — opaque block above cancels sky access → 0 EU (MOD-004 direct scan). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		SolarPanelScenarios.tcDaylight001Neg03_opaqueBlockAboveYieldsZero(helper);
	}

	/** @implements TC-DAYLIGHT-001-STA02 — leaves above → MODE_DAY_PARTIAL, output ×solarTransparentFactor (4→2). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Sta02_leavesAbovePartialHalvesOutput(GameTestHelper helper) {
		SolarPanelScenarios.tcDaylight001Sta02_leavesAbovePartialHalvesOutput(helper);
	}

	/** @implements TC-DAYLIGHT-001-FUN02 — glass above keeps full output (CLEAR). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Fun02_glassAboveStaysFull(GameTestHelper helper) {
		SolarPanelScenarios.tcDaylight001Fun02_glassAboveStaysFull(helper);
	}

	/** @implements TC-DAYLIGHT-001-STA01 — day + rain flags the weather mode (output 0, MOD-003). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		SolarPanelScenarios.tcDaylight001Sta01_rainFlagsWeatherMode(helper);
	}

	/** @implements TC-DAYLIGHT-001-PHY01 — top face (working surface) emits no EU; other five faces OUT-only. @covers R-NRG-03 */
	@GameTest
	public void tcDaylight001Phy01_topFaceNoOutput(GameTestHelper helper) {
		SolarPanelScenarios.tcDaylight001Phy01_topFaceNoOutput(helper);
	}

	/** @implements TC-DAYLIGHT-001-PRF01 — EU/tick equals Config.daylightEuPerTick (× globalEuRateMultiplier). @covers R-NRG-04 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		SolarPanelScenarios.tcDaylight001Prf01_euRateMatchesConfig(helper);
	}

	/** @implements TC-DAYLIGHT-001-PRF02 — buffer caps at Config.solarBuffer (use-it-or-lose-it). @covers R-NRG-01 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		SolarPanelScenarios.tcDaylight001Prf02_bufferCapsAtMax(helper);
	}

	// ── STA: advanced sky-blocker classes (ice / glowstone) ──────────────────────────

	/**
	 * @implements TC-SOLAR-001-STA13 — an ice block above the base panel classifies PARTIAL, not
	 *     BLOCKED. {@code Blocks.ICE} is registered with {@code .noOcclusion()}
	 *     ({@code canOcclude()=false}) and its default full-cube shape makes
	 *     {@code propagatesSkylightDown()} false too, so {@link dev.alaindustrial.core.environment.SolarSky#classify}
	 *     falls through both the "skip" and "BLOCKED" branches to {@code Access.PARTIAL} — the same
	 *     bucket as leaves/cobweb (MOD-004): reduced output via {@code Config.solarTransparentFactor},
	 *     not zero. (An earlier version of this test assumed ice was occlusion-opaque like stone; it is
	 *     not — verified against {@code Blocks.ICE}'s {@code BlockBehaviour.Properties} and
	 *     {@code SolarSky.classify}'s actual branch order.)
	 * @covers R-VIS-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta13_iceAboveYieldsBlocked(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Sta13_iceAboveYieldsBlocked(helper);
	}

	/**
	 * @implements TC-SOLAR-001-STA15 — a Glowstone block above the base panel is opaque to skylight
	 *     (block light emitted by the block itself is not sky light), so it classifies BLOCKED like stone:
	 *     0 EU. Guards against conflating "emits light" with "lets sky light through".
	 * @covers R-VIS-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta15_glowstoneAboveYieldsBlocked(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Sta15_glowstoneAboveYieldsBlocked(helper);
	}

	// ── NEG: advanced negative classes (water above) ─────────────────────────────────

	/**
	 * @implements TC-SOLAR-001-NEG08 — a water source block directly above the base panel is opaque to
	 *     skylight ({@code canOcclude()=false} but a non-empty fluid state trips the {@code SolarSky}
	 *     fluid check), so the panel yields 0 EU, same as a stone roof.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Neg08_waterAboveYieldsZero(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Neg08_waterAboveYieldsZero(helper);
	}

	// ── PRF: globalEuRateMultiplier + config reload ──────────────────────────────────

	/**
	 * @implements TC-SOLAR-001-PRF03 — {@code Config.globalEuRateMultiplier} scales the base panel's
	 *     per-tick output linearly (2.0× → double EU/t). The knob is a mutable static, so it is restored
	 *     to its original value at the end of the test to avoid poisoning any other test in the same batch.
	 * @covers R-NRG-12
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Prf03_globalRateMultiplierScalesOutput(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Prf03_globalRateMultiplierScalesOutput(helper);
	}

	/**
	 * @implements TC-SOLAR-001-PRF04 — a changed {@code Config.solarEuPerTick} is picked up by the very
	 *     next production tick (the field is read live in {@code produce()}, not cached at block-entity
	 *     construction). This is the in-process equivalent of a config file `/reload`: the datapack-reload
	 *     path ({@code Config.loadFrom}) simply re-assigns the same static fields that {@code produce()}
	 *     reads every tick, so mutating the field directly exercises the identical "new value applies
	 *     without a restart" contract without needing to touch the filesystem or fire a real reload event.
	 *     The field is restored afterward to avoid poisoning other tests in the same batch.
	 * @covers R-CFG-02
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Prf04_configChangeAppliesNextTick(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Prf04_configChangeAppliesNextTick(helper);
	}

	// ── CON: neighbour connectivity / network split ──────────────────────────────────

	/**
	 * @implements TC-SOLAR-001-CON01 — a BatteryBox adjacent to the panel but facing AWAY (its input face
	 *     is single-axis, MOD-006) does not receive EU: no compatible interface meets across that face pair.
	 * @covers R-CON-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con01_batteryBoxWrongFacingGetsNothing(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Con01_batteryBoxWrongFacingGetsNothing(helper);
	}

	/**
	 * @implements TC-SOLAR-001-CON02 — an opaque block (stone) between the panel and a BatteryBox, with no
	 *     cable bridging the gap, blocks delivery entirely: energy does not pass through plain blocks.
	 * @covers R-CON-10
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con02_opaqueGapBlocksDelivery(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Con02_opaqueGapBlocksDelivery(helper);
	}

	/**
	 * @implements TC-SOLAR-001-CON03 — a consumer placed directly adjacent to an already-generating panel
	 *     starts receiving EU without any warm-up: the very next serverTick after placement moves EU in.
	 * @covers R-CON-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con03_immediateDeliveryOnPlacement(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Con03_immediateDeliveryOnPlacement(helper);
	}

	/**
	 * @implements TC-SOLAR-001-CON04 — two LV consumers on two different side faces of the same panel
	 *     never together exceed the panel's own per-tick production ({@code Config.solarEuPerTick} ×
	 *     {@code globalEuRateMultiplier}): the output is not duplicated per face.
	 * @covers R-CON-16
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con04_twoReceiversDoNotDoubleOutput(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Con04_twoReceiversDoNotDoubleOutput(helper);
	}

	/**
	 * @implements TC-SOLAR-001-CON05 — while an adjacent BatteryBox is full, the panel keeps generating
	 *     into its own internal buffer (capped at {@code Config.solarBuffer}) instead of losing the EU;
	 *     once the BatteryBox has room again delivery resumes automatically on the next tick.
	 * @covers R-CON-01, R-NRG-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con05_bufferHoldsWhileReceiverFull(GameTestHelper helper) {
		SolarPanelScenarios.tcSolar001Con05_bufferHoldsWhileReceiverFull(helper);
	}

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set ──

	/**
	 * MOD-356 — the panel's readout channel carries the EFFECTIVE rate (what the buffer really gains under
	 * {@code globalEuRateMultiplier}), not {@code produce()}'s mechanical figure. Body: {@link
	 * GeneratorEnergyScenarios#solarPanelReadoutMatchesBufferGain}.
	 */
	@GameTest(skyAccess = true, maxTicks = 60)
	public void mod356_solarPanelReadoutMatchesBufferGain(GameTestHelper helper) {
		GeneratorEnergyScenarios.solarPanelReadoutMatchesBufferGain(helper);
	}
}
