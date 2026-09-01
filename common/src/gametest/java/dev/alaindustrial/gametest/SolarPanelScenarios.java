package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-neutral world-based gametest bodies for the solar panel family (MOD-323): weather /
 * sky-blocking / physical / performance states of the base solar, moonlit and daylight panels.
 *
 * <p>Suite contract mirrors {@link SolarPanelGameTest} on the Fabric lane. Isolation note: every
 * gametest in a batch shares ONE {@code ServerLevel}, so world time is global. Each body sets
 * time/weather and then calls {@code updateSkyBrightness()} to recompute {@code skyDarken}
 * synchronously, reading production in the SAME method body with no {@code runAfterDelay}.
 */
public final class SolarPanelScenarios {

	private SolarPanelScenarios() {}

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
		AlaGameTestHelper.drive(be, helper, ticks);
	}

	private static MoonlitSolarPanelBlockEntity moonlitAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof MoonlitSolarPanelBlockEntity p
				? p : null;
	}

	private static void driveMoonlit(MoonlitSolarPanelBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.serverTick(helper.getLevel(), be.getBlockPos(), helper.getLevel().getBlockState(be.getBlockPos()));
		}
	}

	private static AbstractGeneratorBlockEntity genAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof AbstractGeneratorBlockEntity g
				? g : null;
	}

	private static void driveGen(AbstractGeneratorBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.serverTick(helper.getLevel(), be.getBlockPos(), helper.getLevel().getBlockState(be.getBlockPos()));
		}
	}

	/**
	 * Shared assertion: top face emits no EU (working surface), the other five faces are OUT-only.
	 * Loader-neutral equivalent of the Fabric lane's {@code EnergyStorage.SIDED} probe: the per-face
	 * {@link EnergyPortHost#energyPort} is exactly what both loaders' energy capability is derived
	 * from (MOD-433), so a null port with a non-null extracting port elsewhere proves the same thing.
	 */
	private static void assertTopFaceWorkingSurface(GameTestHelper helper, String label) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof EnergyPortHost host)) {
			helper.fail(label + ": no EnergyPortHost at " + POS);
			return;
		}
		EnergyPort top = host.energyPort(Direction.UP);
		if (top != null && top.supportsExtraction()) {
			helper.fail(label + ": top face (working surface) must not emit EU");
		}
		for (Direction d : new Direction[]{
				Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
			EnergyPort p = host.energyPort(d);
			if (p == null || !p.supportsExtraction()) {
				helper.fail(label + " face " + d + " must emit EU");
			}
		}
	}

	/**
	 * Rain flags the weather production mode: day + rain resolves to MODE_WEATHER (0 EU output).
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta02_rainFlagsWeatherMode
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
	 * A thunderstorm also flags MODE_WEATHER (thunder always co-occurs with rain).
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta03_thunderFlagsWeatherMode
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
		for (int i = 0; i <= Config.solarEvolveTicks
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

	// ── NEG: base panel must produce 0 EU when sky/time conditions are wrong ─────────

	/**
	 * Rain/thunder stops base-panel generation entirely (0 EU; MOD-003).
	 * Mirrors: SolarPanelGameTest.tcSolar001Neg02_rainYieldsZeroEu
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
	 */
	public static void tcSolar001Fun04_glassAboveStaysFull(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.GLASS);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
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
	 * A translucent block (leaves) above flags MODE_PARTIAL and still generates at exactly the
	 * partial rate (base × solarTransparentFactor, MOD-004).
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta06_leavesAboveFlagPartial
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
		long perTick = Math.max(1, Math.round(Math.round(Config.solarEuPerTick * Config.solarTransparentFactor)
				* Config.globalEuRateMultiplier));
		long got = panel.getEnergyStorage().getAmount();
		if (got != perTick) {
			helper.fail("partial-sky generation over 1 tick: got " + got + " EU, expected exactly " + perTick
					+ " (max(1, round(round(" + Config.solarEuPerTick + " × " + Config.solarTransparentFactor
					+ ") × " + Config.globalEuRateMultiplier + ")))");
		}
		helper.succeed();
	}

	/**
	 * A snow layer directly above flags MODE_SNOW and dims output to
	 * max(1, round(solarEuPerTick × solarSnowFactor)).
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta05_snowLayerAboveFlagsSnow
	 */
	public static void tcSolar001Sta05_snowLayerAboveFlagsSnow(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.SNOW);
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
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
	 * WEATHER beats SNOW: a snow layer above plus an active thunderstorm resolves to MODE_WEATHER
	 * with 0 EU, not MODE_SNOW.
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta09_snowLayerPlusThunderIsWeather
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
	 */
	public static void tcSolar001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
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

	// ── Moonlit panel (night generator — inverse conditions of the base panel) ───────

	/**
	 * The moonlit panel is night-only: by clear day it must produce 0 EU.
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Neg01_noEuByDay
	 */
	public static void tcMoonlit001Neg01_noEuByDay(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setClearDay(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		driveMoonlit(panel, helper, 20);
		long amount = panel.getEnergyStorage().getAmount();
		if (amount != 0) {
			helper.fail("moonlit panel generated " + amount + " EU by day; expected 0");
		}
		helper.succeed();
	}

	/**
	 * Night + rain flags MODE_NIGHT_WEATHER (output 0, MOD-003).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Sta01_rainFlagsWeatherMode
	 */
	public static void tcMoonlit001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
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
	 * A night thunderstorm flags MODE_NIGHT_WEATHER but keeps a small trickle
	 * (moonlitWeatherEuPerTick EU/t) instead of going dark.
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Sta03_thunderYieldsWeatherTrickle
	 */
	public static void tcMoonlit001Sta03_thunderYieldsWeatherTrickle(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		setRaining(helper, true); // thunderstorm
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
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

	/**
	 * An opaque block above cancels sky access at night → 0 EU (MOD-004).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Neg03_opaqueBlockAboveYieldsZero
	 */
	public static void tcMoonlit001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
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

	/**
	 * Leaves above at night → MODE_NIGHT_PARTIAL, output × solarTransparentFactor.
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Sta02_leavesAbovePartialHalvesOutput
	 */
	public static void tcMoonlit001Sta02_leavesAbovePartialHalvesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
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

	/**
	 * Moonlit top face (working surface) emits no EU; the other five faces are OUT-only.
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Phy01_topFaceNoOutput
	 */
	public static void tcMoonlit001Phy01_topFaceNoOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		assertTopFaceWorkingSurface(helper, "moonlit panel");
		helper.succeed();
	}

	/**
	 * Moonlit EU/tick equals Config.moonlitEuPerTick (× globalEuRateMultiplier).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Prf01_euRateMatchesConfig
	 */
	public static void tcMoonlit001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		driveMoonlit(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Config.moonlitEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("moonlit EU/tick mismatch: expected " + expected + " (moonlitEuPerTick="
					+ Config.moonlitEuPerTick + ") got " + got);
		}
		helper.succeed();
	}

	/**
	 * Moonlit buffer caps at Config.solarBuffer (use-it-or-lose-it).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Prf02_bufferCapsAtMax
	 */
	public static void tcMoonlit001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		MoonlitSolarPanelBlockEntity panel = moonlitAt(helper);
		panel.getEnergyStorage().setAmountUntracked(Config.solarBuffer);
		driveMoonlit(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		if (got != Config.solarBuffer) {
			helper.fail("moonlit buffer changed from cap: expected " + Config.solarBuffer + " got " + got);
		}
		helper.succeed();
	}

	/**
	 * A night evolution chip evolves the base panel into the moonlit panel, carrying the stored EU
	 * and consuming the chip (shared evolveInto, MOD-166 #4).
	 * Mirrors: SolarPanelGameTest.tcSolar001Fun03_nightChipEvolvesToMoonlit
	 */
	public static void tcSolar001Fun03_nightChipEvolvesToMoonlit(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setNight(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.setItem(SolarPanelBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_NIGHT.get()));
		long energy0 = 1500L;
		panel.getEnergyStorage().setAmountUntracked(energy0);
		BlockPos abs = panel.getBlockPos();
		for (int i = 0; i <= Config.solarEvolveTicks
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
	 */
	public static void tcDaylight001Neg02_rainYieldsZeroEu(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
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

	/**
	 * An opaque block above cancels the daylight panel's sky access → 0 EU (MOD-004 direct scan).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Neg03_opaqueBlockAboveYieldsZero
	 */
	public static void tcDaylight001Neg03_opaqueBlockAboveYieldsZero(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
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

	/**
	 * Leaves above the daylight panel → MODE_DAY_PARTIAL, output × solarTransparentFactor.
	 * Mirrors: SolarPanelGameTest.tcDaylight001Sta02_leavesAbovePartialHalvesOutput
	 */
	public static void tcDaylight001Sta02_leavesAbovePartialHalvesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
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

	/**
	 * Glass above keeps full output (CLEAR).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Fun02_glassAboveStaysFull
	 */
	public static void tcDaylight001Fun02_glassAboveStaysFull(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		helper.setBlock(POS.above(), Blocks.GLASS);
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		driveGen(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Config.daylightEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("daylight under glass should stay full: got " + got + " expected " + expected);
		}
		helper.succeed();
	}

	/**
	 * Day + rain flags MODE_DAY_WEATHER (output 0, MOD-003).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Sta01_rainFlagsWeatherMode
	 */
	public static void tcDaylight001Sta01_rainFlagsWeatherMode(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
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

	/**
	 * Daylight top face (working surface) emits no EU; the other five faces are OUT-only.
	 * Mirrors: SolarPanelGameTest.tcDaylight001Phy01_topFaceNoOutput
	 */
	public static void tcDaylight001Phy01_topFaceNoOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		assertTopFaceWorkingSurface(helper, "daylight panel");
		helper.succeed();
	}

	/**
	 * Daylight EU/tick equals Config.daylightEuPerTick (× globalEuRateMultiplier).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Prf01_euRateMatchesConfig
	 */
	public static void tcDaylight001Prf01_euRateMatchesConfig(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().setAmountUntracked(0);
		driveGen(panel, helper, 1);
		long got = panel.getEnergyStorage().getAmount();
		long expected = Math.max(1, Math.round(Config.daylightEuPerTick * Config.globalEuRateMultiplier));
		if (got != expected) {
			helper.fail("daylight EU/tick mismatch: expected " + expected + " (daylightEuPerTick="
					+ Config.daylightEuPerTick + ") got " + got);
		}
		helper.succeed();
	}

	/**
	 * Daylight buffer caps at Config.solarBuffer (use-it-or-lose-it).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Prf02_bufferCapsAtMax
	 */
	public static void tcDaylight001Prf02_bufferCapsAtMax(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		AbstractGeneratorBlockEntity panel = genAt(helper);
		panel.getEnergyStorage().setAmountUntracked(Config.solarBuffer);
		driveGen(panel, helper, 20);
		long got = panel.getEnergyStorage().getAmount();
		if (got != Config.solarBuffer) {
			helper.fail("daylight buffer changed from cap: expected " + Config.solarBuffer + " got " + got);
		}
		helper.succeed();
	}

	// ── STA: advanced sky-blocker classes (ice / glowstone) ──────────────────────────

	/**
	 * An ice block above classifies PARTIAL, not BLOCKED: Ice is noOcclusion() and its full-cube
	 * shape stops skylight propagation, so SolarSky.classify falls through to PARTIAL (MOD-004) —
	 * reduced output via solarTransparentFactor, not zero.
	 * Mirrors: SolarPanelGameTest.tcSolar001Sta13_iceAboveYieldsBlocked
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
		long expected = (long) Math.round(Config.solarEuPerTick * Config.solarTransparentFactor) * 20;
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
	 */
	public static void tcSolar001Prf03_globalRateMultiplierScalesOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		float saved = Config.globalEuRateMultiplier;
		try {
			Config.globalEuRateMultiplier = 2.0f;
			panel.getEnergyStorage().setAmountUntracked(0);
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
	 * A changed Config.solarEuPerTick is picked up by the very next production tick (the field is
	 * read live in produce(), not cached at construction); restored afterward.
	 * Mirrors: SolarPanelGameTest.tcSolar001Prf04_configChangeAppliesNextTick
	 */
	public static void tcSolar001Prf04_configChangeAppliesNextTick(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		int saved = Config.solarEuPerTick;
		try {
			Config.solarEuPerTick = saved * 3;
			panel.getEnergyStorage().setAmountUntracked(0);
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
	 * A BatteryBox adjacent to the panel but facing AWAY (single-axis input face, MOD-006)
	 * receives no EU: no compatible interface meets across that face pair.
	 * Mirrors: SolarPanelGameTest.tcSolar001Con01_batteryBoxWrongFacingGetsNothing
	 */
	public static void tcSolar001Con01_batteryBoxWrongFacingGetsNothing(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(Config.solarBuffer); // ample supply to push, if a route existed

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
	 */
	public static void tcSolar001Con02_opaqueGapBlocksDelivery(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(Config.solarBuffer);

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
	 */
	public static void tcSolar001Con03_immediateDeliveryOnPlacement(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		SolarPanelBlockEntity panel = panelAt(helper);
		panel.getEnergyStorage().setAmountUntracked(Config.solarBuffer); // generation already running, buffer full

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
		long perTickCap = Math.max(1, Math.round(Config.solarEuPerTick * Config.globalEuRateMultiplier));
		if (total > perTickCap) {
			helper.fail("two receivers together got " + total + " EU in one tick; expected <= " + perTickCap
					+ " (output must not double per face)");
		}
		helper.succeed();
	}

	/**
	 * While an adjacent BatteryBox is full, the panel keeps generating into its own internal
	 * buffer (capped at Config.solarBuffer); once the box has room again delivery resumes on the
	 * next tick.
	 * Mirrors: SolarPanelGameTest.tcSolar001Con05_bufferHoldsWhileReceiverFull
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
		if (panelAmount > Config.solarBuffer) {
			helper.fail("panel buffer exceeded its cap while holding EU: " + panelAmount + " > " + Config.solarBuffer);
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
			if (panel.getEnergyStorage().getAmount() > Config.solarBuffer) {
				helper.fail("panel buffer exceeded cap after resuming delivery");
			}
		}
		helper.succeed();
	}
}
