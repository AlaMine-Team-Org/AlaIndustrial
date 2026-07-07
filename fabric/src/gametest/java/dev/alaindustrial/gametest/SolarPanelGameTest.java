package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import dev.alaindustrial.Config;
import net.minecraft.core.Direction;
import team.reborn.energy.api.EnergyStorage;

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

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/** Clear daytime, brightness recomputed NOW (no tick wait). Weather reset for isolation. */
	private static void setClearDay(GameTestHelper helper) {
		var level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set day");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f); // isRaining() reads the interpolated level, not WeatherData
		level.updateSkyBrightness(); // skyDarken now reflects day → isBrightOutside() true synchronously
	}

	/** Clear midnight, brightness recomputed NOW. Mirror of {@link #setClearDay}. */
	private static void setNight(GameTestHelper helper) {
		var level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		level.updateSkyBrightness();
	}

	/** Turn on rain in the current (already-settled) time, synchronously: WeatherData + interpolated level. */
	private static void setRaining(GameTestHelper helper, boolean thunder) {
		var level = helper.getLevel();
		level.getWeatherData().setRaining(true);
		if (thunder) {
			level.getWeatherData().setThundering(true);
		}
		level.setRainLevel(1.0f); // isRaining() reads the interpolated rain level, not WeatherData
	}

	private static SolarPanelBlockEntity panelAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof SolarPanelBlockEntity p ? p : null;
	}

	private static void drive(SolarPanelBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.serverTick(helper.getLevel(), be.getBlockPos(), helper.getLevel().getBlockState(be.getBlockPos()));
		}
	}

	/** @implements TC-SOLAR-001-FUN01 — generates EU by day under open sky. @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Fun01_generatesByDay(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 20);
		if (panel.getEnergyStorage().getAmount() <= 0) {
			helper.fail("no EU by day (bright=" + helper.getLevel().isBrightOutside() + ")");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SOLAR-001-STA01 — rain flags the weather production mode (day + rain → MODE_WEATHER).
	 *     The mode flag fires for the GUI even though output is 0 in weather (MOD-003; see NEG02). Rain set
	 *     after the clear-day brightness is settled; everything synchronous.
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
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
	 * @implements TC-SOLAR-001-STA02 — thunderstorm also flags MODE_WEATHER (same zero-output as rain, MOD-003).
	 *     Thunder always co-occurs with rain; both flags set so {@code isRaining()} reads true.
	 *
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta02_thunderFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		setRaining(helper, true);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		if (mode != SolarPanelBlockEntity.MODE_WEATHER) {
			helper.fail("thunderstorm did not flag MODE_WEATHER, got mode " + mode);
		}
		helper.succeed();
	}

	/** @implements TC-SOLAR-001-FUN02 — a day evolution chip evolves the panel into the daylight panel. */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Fun02_dayChipEvolvesToDaylight(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModItems.ALIGNMENT_CHIP_DAY));
		BlockPos abs = panel.getBlockPos();
		// Brightness is settled now; synchronous driving keeps it constant (no world tick between calls).
		for (int i = 0; i <= Config.solarEvolveTicks
				&& helper.getLevel().getBlockState(abs).getBlock() == ModBlocks.SOLAR_PANEL; i++) {
			panel.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
		if (helper.getLevel().getBlockState(abs).getBlock() != ModBlocks.DAYLIGHT_SOLAR_PANEL) {
			helper.fail("day chip did not evolve panel into the daylight panel");
		}
		helper.succeed();
	}

	// ── NEG: base panel must produce 0 EU when sky/time conditions are wrong ─────────

	/** @implements TC-SOLAR-001-NEG01 — base panel generates 0 EU at night (day-only). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Neg01_noEuAtNight(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setNight(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("solar panel generated " + amount + " EU at midnight; expected 0");
		}
		helper.succeed();
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
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
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
	 * @implements TC-SOLAR-001-NEG03 — an opaque block above cancels sky access → 0 EU.
	 *
	 * <p>Since MOD-004 the panel classifies sky access by scanning the column above it directly
	 * ({@link dev.alaindustrial.core.SolarSky}), not via {@code canSeeSkyFromBelowWater} — so a solid
	 * roof is detected even in the deep gametest region (the old heightmap/below-sea quirk that forced
	 * this case to MANUAL/L3 no longer applies).
	 *
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
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
	 * @implements TC-SOLAR-001-FUN04 — glass above does NOT reduce generation (fully sky-transparent →
	 *     CLEAR, full output, MODE_DAY). MOD-004.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Fun04_glassAboveStaysFull(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.GLASS);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Config.solarEuPerTick * Config.globalEuRateMultiplier));
		int mode = panel.getDataAccess().get(3);
		if (got != expected || mode != SolarPanelBlockEntity.MODE_DAY) {
			helper.fail("glass should keep full output: got " + got + " (expected " + expected
					+ "), mode " + mode + " (expected MODE_DAY)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SOLAR-001-STA03 — a translucent block (leaves) above flags MODE_PARTIAL and still
	 *     generates (MOD-004). The base panel's 1 EU/t × 0.5 rounds back to 1, so assert the mode flag.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta03_leavesAboveFlagPartial(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		drive(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		if (mode != SolarPanelBlockEntity.MODE_PARTIAL) {
			helper.fail("leaves above should flag MODE_PARTIAL (" + SolarPanelBlockEntity.MODE_PARTIAL
					+ "), got " + mode);
		}
		if (panel.getEnergyStorage().getAmount() <= 0) {
			helper.fail("partial sky should still generate some EU");
		}
		helper.succeed();
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
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.SNOW);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		int snowBase = Math.max(1, Math.round(Config.solarEuPerTick * Config.solarSnowFactor));
		long expected = Math.max(1, Math.round(snowBase * Config.globalEuRateMultiplier));
		int mode = panel.getDataAccess().get(3);
		if (mode != SolarPanelBlockEntity.MODE_SNOW) {
			helper.fail("snow layer above should flag MODE_SNOW (" + SolarPanelBlockEntity.MODE_SNOW
					+ "), got " + mode);
		}
		if (got != expected) {
			helper.fail("snow layer output: got " + got + " EU (expected " + expected
					+ " = max(1, round(" + Config.solarEuPerTick + " × " + Config.solarSnowFactor + ")))");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SOLAR-001-STA09 — WEATHER beats SNOW: a snow layer above the panel plus an active
	 *     thunderstorm resolves to MODE_WEATHER with 0 EU, not MODE_SNOW.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta09_snowLayerPlusThunderIsWeather(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.SNOW);
		setClearDay(helper);
		setRaining(helper, true);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;
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
	 * @implements TC-SOLAR-001-STA10 — NIGHT beats SNOW: a snow layer above the panel at night yields 0 EU
	 *     (mode NIGHT), never MODE_SNOW.
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta10_snowLayerAtNightIsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.SNOW);
		setNight(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;
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
	 * @implements TC-SOLAR-001-PHY01 — the solar panel's top face (working surface) does not expose an
	 *     energy output interface; the other five faces are OUT-only (R-NRG-03).
	 *
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcSolar001Phy01_topFaceNoOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		assertTopFaceWorkingSurface(helper, "solar panel");
		helper.succeed();
	}

	/** Shared assertion: top face emits no EU (working surface), the other five faces are OUT-only. */
	static void assertTopFaceWorkingSurface(GameTestHelper helper, String label) {
		EnergyStorage top = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.UP);
		if (top != null && top.supportsExtraction()) {
			helper.fail(label + ": top face (working surface) must not emit EU");
		}
		for (Direction d : new Direction[]{
				Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
			EnergyStorage p = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), d);
			if (p == null || !p.supportsExtraction()) {
				helper.fail(label + " face " + d + " must emit EU");
			}
		}
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
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;
		drive(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Config.solarEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("EU/tick mismatch: expected " + expected + " (solarEuPerTick="
					+ Config.solarEuPerTick + " × globalEuRateMultiplier=" + Config.globalEuRateMultiplier
					+ ") got " + got);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SOLAR-001-PRF02 — buffer cannot exceed {@code Config.solarBuffer}; excess EU is
	 *     discarded (use-it-or-lose-it). Covers R-NRG-01 for the solar panel.
	 *
	 * @covers R-NRG-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = Config.solarBuffer;
		drive(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		if (got != Config.solarBuffer) {
			helper.fail("buffer changed from cap: expected " + Config.solarBuffer + " got " + got);
		}
		helper.succeed();
	}

	// ── Moonlit panel (night generator — inverse conditions of the base panel) ───────

	private static MoonlitSolarPanelBlockEntity moonlitAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof MoonlitSolarPanelBlockEntity p
				? p : null;
	}

	private static void driveMoonlit(MoonlitSolarPanelBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.serverTick(helper.getLevel(), be.getBlockPos(), helper.getLevel().getBlockState(be.getBlockPos()));
		}
	}

	/** @implements TC-MOONLIT-001-NEG01 — moonlit panel is night-only: by clear day it must produce 0 EU. @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Neg01_noEuByDay(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		setClearDay(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		driveMoonlit(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("moonlit panel generated " + amount + " EU by day; expected 0");
		}
		helper.succeed();
	}

	/** @implements TC-MOONLIT-001-FUN01 — moonlit panel generates EU at midnight. @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Fun01_generatesAtNight(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		driveMoonlit(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount <= 0) {
			helper.fail("moonlit panel produced " + amount + " EU at midnight; expected > 0");
		}
		helper.succeed();
	}

	/** @implements TC-MOONLIT-001-STA01 — night + rain flags the weather mode (output 0, MOD-003). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		setNight(helper);
		setRaining(helper, false);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		driveMoonlit(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		if (mode != MoonlitSolarPanelBlockEntity.MODE_NIGHT_WEATHER) {
			helper.fail("expected MODE_NIGHT_WEATHER (" + MoonlitSolarPanelBlockEntity.MODE_NIGHT_WEATHER
					+ "), got " + mode + " (isRaining=" + helper.getLevel().isRaining() + ")");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-MOONLIT-001-STA03 — a night thunderstorm flags MODE_NIGHT_WEATHER but, unlike the
	 *     day panels (0 EU), the moonlit panel keeps a small trickle: {@code moonlitWeatherEuPerTick}
	 *     EU/t, instead of going dark. Rain shares this code path (STA01 covers the rain mode flag).
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Sta03_thunderYieldsWeatherTrickle(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		setNight(helper);
		setRaining(helper, true); // thunderstorm
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().amount = 0;
		int ticks = 20;
		driveMoonlit(panel, helper, ticks);
		long amount = panel.getEnergyStorage().getAmount();
		int perTick = Math.max(1, Math.round(Config.moonlitWeatherEuPerTick * Config.globalEuRateMultiplier));
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

	/** @implements TC-MOONLIT-001-NEG03 — opaque block above cancels sky access at night → 0 EU (MOD-004). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.STONE);
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		driveMoonlit(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("moonlit generated " + amount + " EU under stone at night; expected 0");
		}
		helper.succeed();
	}

	/** @implements TC-MOONLIT-001-STA02 — leaves above at night → MODE_NIGHT_PARTIAL, output ×factor (2→1). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Sta02_leavesAbovePartialHalvesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().amount = 0;
		driveMoonlit(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Math.round(Config.moonlitEuPerTick * Config.solarTransparentFactor)
				* Config.globalEuRateMultiplier));
		int mode = panel.getDataAccess().get(3);
		if (got != expected || mode != MoonlitSolarPanelBlockEntity.MODE_NIGHT_PARTIAL) {
			helper.fail("moonlit under leaves: got " + got + " EU (expected " + expected + "), mode " + mode
					+ " (expected MODE_NIGHT_PARTIAL)");
		}
		helper.succeed();
	}

	/** @implements TC-MOONLIT-001-PHY01 — top face (working surface) emits no EU; other five faces OUT-only. @covers R-NRG-03 */
	@GameTest
	public void tcMoonlit001Phy01_topFaceNoOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		assertTopFaceWorkingSurface(helper, "moonlit panel");
		helper.succeed();
	}

	/** @implements TC-MOONLIT-001-PRF01 — EU/tick equals Config.moonlitEuPerTick (× globalEuRateMultiplier). @covers R-NRG-04 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().amount = 0;
		driveMoonlit(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Config.moonlitEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("moonlit EU/tick mismatch: expected " + expected + " (moonlitEuPerTick="
					+ Config.moonlitEuPerTick + ") got " + got);
		}
		helper.succeed();
	}

	/** @implements TC-MOONLIT-001-PRF02 — buffer caps at Config.solarBuffer (use-it-or-lose-it). @covers R-NRG-01 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcMoonlit001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MOONLIT_SOLAR_PANEL);
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().amount = Config.solarBuffer;
		driveMoonlit(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		if (got != Config.solarBuffer) {
			helper.fail("moonlit buffer changed from cap: expected " + Config.solarBuffer + " got " + got);
		}
		helper.succeed();
	}

	/** @implements TC-SOLAR-001-FUN03 — a night evolution chip evolves the base panel into the moonlit panel. */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Fun03_nightChipEvolvesToMoonlit(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setNight(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModItems.ALIGNMENT_CHIP_NIGHT));
		BlockPos abs = panel.getBlockPos();
		for (int i = 0; i <= Config.solarEvolveTicks
				&& helper.getLevel().getBlockState(abs).getBlock() == ModBlocks.SOLAR_PANEL; i++) {
			panel.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
		if (helper.getLevel().getBlockState(abs).getBlock() != ModBlocks.MOONLIT_SOLAR_PANEL) {
			helper.fail("night chip did not evolve panel into the moonlit panel");
		}
		helper.succeed();
	}

	// ── Daylight panel (T2 day branch — 4 EU/t, day-only) ────────────────────────────

	private static AbstractGeneratorBlockEntity genAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof AbstractGeneratorBlockEntity g
				? g : null;
	}

	private static void driveGen(AbstractGeneratorBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.serverTick(helper.getLevel(), be.getBlockPos(), helper.getLevel().getBlockState(be.getBlockPos()));
		}
	}

	/** @implements TC-DAYLIGHT-001-FUN01 — daylight panel generates EU by day under open sky. @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Fun01_generatesByDay(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		driveGen(panel, helper, 20);
		if (panel.getEnergyStorage().getAmount() <= 0) {
			helper.fail("daylight panel produced no EU by day (bright=" + helper.getLevel().isBrightOutside() + ")");
		}
		helper.succeed();
	}

	/** @implements TC-DAYLIGHT-001-NEG01 — daylight panel produces 0 EU at night (day-only). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Neg01_noEuAtNight(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		setNight(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		driveGen(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("daylight panel generated " + amount + " EU at midnight; expected 0");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-DAYLIGHT-001-NEG02 — rain/thunder stops generation entirely (0 EU; MOD-003).
	 * @covers R-NRG-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Neg02_rainYieldsZeroEu(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		setClearDay(helper);
		setRaining(helper, false);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		driveGen(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("rain: daylight generated " + amount + " EU (expected 0 — see MOD-003)");
		}
		helper.succeed();
	}

	/** @implements TC-DAYLIGHT-001-NEG03 — opaque block above cancels sky access → 0 EU (MOD-004 direct scan). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.STONE);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		driveGen(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("daylight generated " + amount + " EU under stone; expected 0");
		}
		helper.succeed();
	}

	/** @implements TC-DAYLIGHT-001-STA02 — leaves above → MODE_DAY_PARTIAL, output ×solarTransparentFactor (4→2). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Sta02_leavesAbovePartialHalvesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().amount = 0;
		driveGen(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Math.round(Config.daylightEuPerTick * Config.solarTransparentFactor)
				* Config.globalEuRateMultiplier));
		int mode = panel.getDataAccess().get(3);
		if (got != expected || mode != DaylightSolarPanelBlockEntity.MODE_DAY_PARTIAL) {
			helper.fail("daylight under leaves: got " + got + " EU (expected " + expected + "), mode " + mode
					+ " (expected MODE_DAY_PARTIAL)");
		}
		helper.succeed();
	}

	/** @implements TC-DAYLIGHT-001-FUN02 — glass above keeps full output (CLEAR). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Fun02_glassAboveStaysFull(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.GLASS);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().amount = 0;
		driveGen(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Config.daylightEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("daylight under glass should stay full: got " + got + " expected " + expected);
		}
		helper.succeed();
	}

	/** @implements TC-DAYLIGHT-001-STA01 — day + rain flags the weather mode (output 0, MOD-003). @covers R-NRG-15 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		setClearDay(helper);
		setRaining(helper, false);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		driveGen(panel, helper, 1);
		int mode = panel.getDataAccess().get(3);
		if (mode != DaylightSolarPanelBlockEntity.MODE_DAY_WEATHER) {
			helper.fail("expected MODE_DAY_WEATHER (" + DaylightSolarPanelBlockEntity.MODE_DAY_WEATHER
					+ "), got " + mode + " (isRaining=" + helper.getLevel().isRaining() + ")");
		}
		helper.succeed();
	}

	/** @implements TC-DAYLIGHT-001-PHY01 — top face (working surface) emits no EU; other five faces OUT-only. @covers R-NRG-03 */
	@GameTest
	public void tcDaylight001Phy01_topFaceNoOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		assertTopFaceWorkingSurface(helper, "daylight panel");
		helper.succeed();
	}

	/** @implements TC-DAYLIGHT-001-PRF01 — EU/tick equals Config.daylightEuPerTick (× globalEuRateMultiplier). @covers R-NRG-04 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().amount = 0;
		driveGen(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Config.daylightEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("daylight EU/tick mismatch: expected " + expected + " (daylightEuPerTick="
					+ Config.daylightEuPerTick + ") got " + got);
		}
		helper.succeed();
	}

	/** @implements TC-DAYLIGHT-001-PRF02 — buffer caps at Config.solarBuffer (use-it-or-lose-it). @covers R-NRG-01 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcDaylight001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.DAYLIGHT_SOLAR_PANEL);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().amount = Config.solarBuffer;
		driveGen(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		if (got != Config.solarBuffer) {
			helper.fail("daylight buffer changed from cap: expected " + Config.solarBuffer + " got " + got);
		}
		helper.succeed();
	}

	// ── STA: advanced sky-blocker classes (ice / glowstone) ──────────────────────────

	/**
	 * @implements TC-SOLAR-001-STA13 — an ice block above the base panel classifies PARTIAL, not
	 *     BLOCKED. {@code Blocks.ICE} is registered with {@code .noOcclusion()}
	 *     ({@code canOcclude()=false}) and its default full-cube shape makes
	 *     {@code propagatesSkylightDown()} false too, so {@link dev.alaindustrial.core.SolarSky#classify}
	 *     falls through both the "skip" and "BLOCKED" branches to {@code Access.PARTIAL} — the same
	 *     bucket as leaves/cobweb (MOD-004): reduced output via {@code Config.solarTransparentFactor},
	 *     not zero. (An earlier version of this test assumed ice was occlusion-opaque like stone; it is
	 *     not — verified against {@code Blocks.ICE}'s {@code BlockBehaviour.Properties} and
	 *     {@code SolarSky.classify}'s actual branch order.)
	 * @covers R-VIS-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta13_iceAboveYieldsBlocked(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.ICE);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;
		drive(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		int mode = panel.getDataAccess().get(3);
		// production is rounded PER TICK (Math.round(base * factor)), not on the 20-tick total.
		long expected = (long) Math.round(Config.solarEuPerTick * Config.solarTransparentFactor) * 20;
		if (got != expected || mode != SolarPanelBlockEntity.MODE_PARTIAL) {
			helper.fail("ice above should yield " + expected + " EU / MODE_PARTIAL (canOcclude()=false on Ice, so"
					+ " SolarSky.classify falls through to PARTIAL, not BLOCKED), got " + got + " EU, mode " + mode);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SOLAR-001-STA15 — a Glowstone block above the base panel is opaque to skylight
	 *     (block light emitted by the block itself is not sky light), so it classifies BLOCKED like stone:
	 *     0 EU. Guards against conflating "emits light" with "lets sky light through".
	 * @covers R-VIS-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Sta15_glowstoneAboveYieldsBlocked(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		helper.setBlock(POS.above(), Blocks.GLOWSTONE);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;
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
	 * @implements TC-SOLAR-001-NEG08 — a water source block directly above the base panel is opaque to
	 *     skylight ({@code canOcclude()=false} but a non-empty fluid state trips the {@code SolarSky}
	 *     fluid check), so the panel yields 0 EU, same as a stone roof.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Neg08_waterAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
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
	 * @implements TC-SOLAR-001-PRF03 — {@code Config.globalEuRateMultiplier} scales the base panel's
	 *     per-tick output linearly (2.0× → double EU/t). The knob is a mutable static, so it is restored
	 *     to its original value at the end of the test to avoid poisoning any other test in the same batch.
	 * @covers R-NRG-12
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Prf03_globalRateMultiplierScalesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		float saved = Config.globalEuRateMultiplier;
		try {
			Config.globalEuRateMultiplier = 2.0f;
			panel.getEnergyStorage().amount = 0;
			drive(panel, helper, 1);
			long got = panel.getEnergyStorage().getAmount();
			long expected = Math.max(1, Math.round(Config.solarEuPerTick * 2.0f));
			if (got != expected) {
				helper.fail("globalEuRateMultiplier=2.0 expected " + expected + " EU/t, got " + got);
			}
		} finally {
			Config.globalEuRateMultiplier = saved;
		}
		helper.succeed();
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
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		int saved = Config.solarEuPerTick;
		try {
			Config.solarEuPerTick = saved * 3;
			panel.getEnergyStorage().amount = 0;
			drive(panel, helper, 1);
			long got = panel.getEnergyStorage().getAmount();
			long expected = Math.max(1, Math.round(Config.solarEuPerTick * Config.globalEuRateMultiplier));
			if (got != expected) {
				helper.fail("new solarEuPerTick=" + Config.solarEuPerTick + " not applied: expected " + expected
						+ " got " + got);
			}
		} finally {
			Config.solarEuPerTick = saved;
		}
		helper.succeed();
	}

	// ── CON: neighbour connectivity / network split ──────────────────────────────────

	/**
	 * @implements TC-SOLAR-001-CON01 — a BatteryBox adjacent to the panel but facing AWAY (its input face
	 *     is single-axis, MOD-006) does not receive EU: no compatible interface meets across that face pair.
	 * @covers R-CON-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con01_batteryBoxWrongFacingGetsNothing(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = Config.solarBuffer; // ample supply to push, if a route existed

		BlockPos batteryPos = POS.relative(Direction.EAST);
		// BatteryBox input face = FACING (MOD-006). FACING=NORTH means input faces north, not the panel
		// sitting on its WEST side — so the contacting face pair is not an input, EU cannot flow in.
		helper.setBlock(batteryPos, ModBlocks.BATTERY_BOX.defaultBlockState()
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
	 * @implements TC-SOLAR-001-CON02 — an opaque block (stone) between the panel and a BatteryBox, with no
	 *     cable bridging the gap, blocks delivery entirely: energy does not pass through plain blocks.
	 * @covers R-CON-10
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con02_opaqueGapBlocksDelivery(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = Config.solarBuffer;

		BlockPos gapPos = POS.relative(Direction.EAST);
		BlockPos batteryPos = gapPos.relative(Direction.EAST);
		helper.setBlock(gapPos, Blocks.STONE);
		helper.setBlock(batteryPos, ModBlocks.BATTERY_BOX.defaultBlockState()
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
	 * @implements TC-SOLAR-001-CON03 — a consumer placed directly adjacent to an already-generating panel
	 *     starts receiving EU without any warm-up: the very next serverTick after placement moves EU in.
	 * @covers R-CON-15
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con03_immediateDeliveryOnPlacement(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = Config.solarBuffer; // generation already running, buffer full

		BlockPos batteryPos = POS.relative(Direction.EAST);
		helper.setBlock(batteryPos, ModBlocks.BATTERY_BOX.defaultBlockState()
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
	 * @implements TC-SOLAR-001-CON04 — two LV consumers on two different side faces of the same panel
	 *     never together exceed the panel's own per-tick production ({@code Config.solarEuPerTick} ×
	 *     {@code globalEuRateMultiplier}): the output is not duplicated per face.
	 * @covers R-CON-16
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con04_twoReceiversDoNotDoubleOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;

		BlockPos batteryEastPos = POS.relative(Direction.EAST);
		BlockPos batterySouthPos = POS.relative(Direction.SOUTH);
		helper.setBlock(batteryEastPos, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(batterySouthPos, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		var batteryEast = helper.getBlockEntity(batteryEastPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		var batterySouth = helper.getBlockEntity(batterySouthPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		if (batteryEast == null || batterySouth == null) {
			helper.fail("battery_box block entities missing after placement");
		}
		drive(panel, helper, 1); // one production tick worth of EU to distribute
		long total = batteryEast.getEnergyStorage().getAmount() + batterySouth.getEnergyStorage().getAmount();
		long perTickCap = Math.max(1, Math.round(Config.solarEuPerTick * Config.globalEuRateMultiplier));
		if (total > perTickCap) {
			helper.fail("two receivers together got " + total + " EU in one tick; expected <= " + perTickCap
					+ " (output must not double per face)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SOLAR-001-CON05 — while an adjacent BatteryBox is full, the panel keeps generating
	 *     into its own internal buffer (capped at {@code Config.solarBuffer}) instead of losing the EU;
	 *     once the BatteryBox has room again delivery resumes automatically on the next tick.
	 * @covers R-CON-01, R-NRG-01
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void tcSolar001Con05_bufferHoldsWhileReceiverFull(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().amount = 0;

		BlockPos batteryPos = POS.relative(Direction.EAST);
		helper.setBlock(batteryPos, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		var battery = helper.getBlockEntity(batteryPos, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		if (battery == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		battery.getEnergyStorage().amount = battery.getEnergyStorage().getCapacity(); // full: cannot accept

		drive(panel, helper, 20); // keep generating while the only receiver is full
		long panelAmount = panel.getEnergyStorage().getAmount();
		if (panelAmount <= 0) {
			helper.fail("panel lost its EU instead of buffering it while the receiver was full: "
					+ panelAmount);
		}
		if (panelAmount > Config.solarBuffer) {
			helper.fail("panel buffer exceeded its cap while holding EU: " + panelAmount + " > " + Config.solarBuffer);
		}

		// Free up room in the receiver: delivery must resume automatically, no player action beyond time.
		battery.getEnergyStorage().amount = 0;
		long before = panel.getEnergyStorage().getAmount();
		drive(panel, helper, 5);
		if (battery.getEnergyStorage().getAmount() <= 0) {
			helper.fail("delivery did not resume once the receiver had room again");
		}
		if (panel.getEnergyStorage().getAmount() > before) {
			// Not strictly required to fall, but it must not just keep climbing past cap unmoved.
			if (panel.getEnergyStorage().getAmount() > Config.solarBuffer) {
				helper.fail("panel buffer exceeded cap after resuming delivery");
			}
		}
		helper.succeed();
	}
}
