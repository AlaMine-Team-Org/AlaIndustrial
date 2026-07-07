package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.WindMillOutput;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 functional suite for the wind mill — the passive height/sky/weather-driven LV generator. Mirrors the
 * structure of {@link WaterMillGameTest} / {@link SolarPanelGameTest}: each method is one case, traced via
 * {@code @implements}, driving {@code serverTick} directly (deterministic, no waiting).
 *
 * <p><b>Height note.</b> A Fabric gametest structure sits near world Y = 0, well below sea level
 * ({@code level.getSeaLevel()} = 63), so the wind mill's height base is 0 in-world. The FUN/weather cases
 * therefore assert the mill's per-tick output equals {@link WindMillOutput#euFor} evaluated against the
 * <em>real</em> world state (absolute Y, sea level, sky, weather) — the same pure function {@code produce()}
 * calls — so the wiring (sky gate, weather read, sampling, buffering) is verified end-to-end regardless of
 * the region's altitude. The full height→base scaling and weather-multiplier arithmetic (0 at sea level,
 * +1/16 blocks, cap 4, rain ×1.5, thunder ×2, cap 8) is covered numerically at L1 in {@code WindMillOutputTest}.
 *
 * <p>Weather is set synchronously the way the solar suite does it — {@code WeatherData} plus the interpolated
 * rain level ({@code isRaining()} reads the latter). Numbers come from {@link Config} (canon), never hard-coded.
 */
public class WindMillGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static WindMillBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.WIND_MILL);
		WindMillBlockEntity be = helper.getBlockEntity(POS, WindMillBlockEntity.class);
		if (be == null) {
			helper.fail("wind mill block entity missing after placement");
		}
		return be;
	}

	private static void drive(WindMillBlockEntity be, GameTestHelper helper, int ticks) {
		BlockPos abs = be.getBlockPos();
		for (int i = 0; i < ticks; i++) {
			BlockState state = helper.getLevel().getBlockState(abs);
			be.serverTick(helper.getLevel(), abs, state);
		}
	}

	private static void setClear(GameTestHelper helper) {
		var level = helper.getLevel();
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f); // isRaining() reads the interpolated level, not WeatherData
	}

	private static void setRaining(GameTestHelper helper, boolean thunder) {
		var level = helper.getLevel();
		level.getWeatherData().setRaining(true);
		if (thunder) {
			level.getWeatherData().setThundering(true);
		}
		level.setRainLevel(1.0f);
	}

	/** The per-tick EU the mill should produce under the current world state (open sky assumed). */
	private static int expectedRate(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos abs = helper.absolutePos(POS);
		return WindMillOutput.euFor(abs.getY(), level.getSeaLevel(), true,
				level.isRaining(), level.isThundering(),
				Config.windMillMaxBaseEuPerTick, Config.windMillMaxEuPerTick,
				Config.windMillRainFactor, Config.windMillThunderFactor);
	}

	private static long afterGlobalRate(int made) {
		return made > 0 ? Math.max(1, Math.round(made * Config.globalEuRateMultiplier)) : 0;
	}

	/**
	 * @implements TC-WINDMILL-001-FUN01 — under open sky the wind mill produces the height/weather rate
	 *     {@link WindMillOutput#euFor} yields for the region, sampled every {@code windMillSampleTicks}. Driven
	 *     for more than one sample window; the accumulated EU equals the per-tick rate × ticks.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Fun01_generatesSampledRate(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		setClear(helper);
		mill.getEnergyStorage().amount = 0;
		int ticks = Config.windMillSampleTicks * 2 + 5; // span multiple sample windows
		long perTick = afterGlobalRate(expectedRate(helper));
		drive(mill, helper, ticks);
		long got = mill.getEnergyStorage().getAmount();
		long expected = perTick * ticks;
		if (got != expected) {
			helper.fail("wind mill output over " + ticks + " ticks: got " + got + " EU, expected " + expected
					+ " (perTick=" + perTick + ", rate=" + expectedRate(helper) + " at y="
					+ helper.absolutePos(POS).getY() + ", sea=" + helper.getLevel().getSeaLevel() + ")");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-STA01 — a thunderstorm multiplies the base rate (×windMillThunderFactor,
	 *     capped at windMillMaxEuPerTick): the storm rate is ≥ the clear-sky rate for the same block. The
	 *     per-tick output matches {@link WindMillOutput#euFor} evaluated with thunder active.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Sta01_thunderMultipliesRate(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		setClear(helper);
		int clearRate = expectedRate(helper);
		setRaining(helper, true);
		int stormRate = expectedRate(helper);
		if (stormRate < clearRate) {
			helper.fail("thunder rate " + stormRate + " < clear rate " + clearRate + " (weather must not reduce output)");
		}
		// Drive one full sample window from a fresh block so the cached rate reflects the storm.
		mill.getEnergyStorage().amount = 0;
		long perTick = afterGlobalRate(stormRate);
		drive(mill, helper, Config.windMillSampleTicks);
		long expected = perTick * Config.windMillSampleTicks;
		long got = mill.getEnergyStorage().getAmount();
		if (got != expected) {
			helper.fail("thunder output: got " + got + " EU, expected " + expected + " (perTick=" + perTick + ")");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-NEG01 — a solid roof (no open sky column) forces 0 EU, no matter the
	 *     height or weather. Drives well past a sample window under a thunderstorm and asserts nothing accrues.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg01_roofedYieldsZero(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		helper.setBlock(POS.above(), Blocks.STONE);
		setRaining(helper, true); // even in a storm, a roof kills it
		mill.getEnergyStorage().amount = 0;
		drive(mill, helper, Config.windMillSampleTicks * 2 + 5);
		long got = mill.getEnergyStorage().getAmount();
		if (got != 0) {
			helper.fail("roofed wind mill generated " + got + " EU; expected 0 (no open sky)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-PRF01 — the buffer caps at {@code Config.windMillBuffer}; excess EU is
	 *     discarded (use-it-or-lose-it), even if the mill is producing.
	 * @covers R-NRG-01
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Prf01_bufferCapsAtMax(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		setRaining(helper, true);
		mill.getEnergyStorage().amount = Config.windMillBuffer;
		drive(mill, helper, Config.windMillSampleTicks * 2);
		long got = mill.getEnergyStorage().getAmount();
		if (got != Config.windMillBuffer) {
			helper.fail("buffer changed from cap: expected " + Config.windMillBuffer + " got " + got);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-PER01 — the stored EU buffer survives an NBT save/load round-trip (energy
	 *     persists via the base MachineBlockEntity; the mill's sampling state is transient and recomputed).
	 * @covers R-PER-01
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Per01_energySurvivesNbtRoundTrip(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		mill.getEnergyStorage().amount = 1234; // seed a buffer independent of in-region production
		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = mill.saveCustomOnly(registries);
		WindMillBlockEntity restored = new WindMillBlockEntity(mill.getBlockPos(),
				helper.getLevel().getBlockState(mill.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		if (restored.getEnergyStorage().getAmount() != 1234) {
			helper.fail("NBT round-trip lost energy: 1234 -> " + restored.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-CON01 — the mill pushes EU into a directly-adjacent BatteryBox (no cable).
	 *     Its buffer is pre-filled so there is always EU to push; the battery on another face receives it.
	 * @covers R-NRG-03, R-CON-01
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Con01_pushesToAdjacentBattery(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		mill.getEnergyStorage().amount = mill.getEnergyStorage().getCapacity(); // ample supply to push

		BlockPos sink = POS.relative(Direction.EAST);
		// BatteryBox input face = FACING (MOD-006); the mill sits on its WEST side, so face WEST.
		helper.setBlock(sink, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity battery = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (battery == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		battery.getEnergyStorage().amount = 0;
		drive(mill, helper, 20);
		if (battery.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery_box received no EU from the wind mill");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-PHY01 — the FACING (front) face emits no EU; the other five faces are
	 *     OUT-only (generator face contract, R-NRG-03). FACING defaults to NORTH on a freshly-set block, and
	 *     is production-inert either way (FACING never affects the passive rate).
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcWindmill001Phy01_facingFaceInert(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.WIND_MILL.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		EnergyStorage front = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.NORTH);
		if (front != null && front.supportsExtraction()) {
			helper.fail("wind mill FACING (front) face must not emit EU");
		}
		for (Direction d : new Direction[]{
				Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
			EnergyStorage p = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), d);
			if (p == null || !p.supportsExtraction()) {
				helper.fail("wind mill face " + d + " must emit EU");
			}
		}
		helper.succeed();
	}
}
