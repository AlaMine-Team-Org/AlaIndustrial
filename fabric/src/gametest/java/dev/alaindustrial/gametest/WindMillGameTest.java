package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.WindMillOutput;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModItems;
import net.minecraft.world.item.ItemStack;
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
		WindMillBlockEntity be = placeWithoutRotor(helper);
		// The rotor is a generation gate: install one so the production tests see real output.
		be.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(ModItems.WINDMILL_ROTOR));
		return be;
	}

	/** Place a mill with no rotor — for the gate test (no rotor → no generation). */
	private static WindMillBlockEntity placeWithoutRotor(GameTestHelper helper) {
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
	 * @implements TC-WINDMILL-001-CON01 — the mill pushes EU into a BatteryBox placed against its BACK face
	 *     (the only output face, opposite of FACING). FACING defaults to NORTH, so the back is SOUTH.
	 *     The mill buffer is pre-filled so there is always EU to push.
	 * @covers R-NRG-03, R-CON-01
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Con01_pushesToAdjacentBattery(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper); // FACING defaults to NORTH → output face is SOUTH
		mill.getEnergyStorage().amount = mill.getEnergyStorage().getCapacity(); // ample supply to push

		// Place the BatteryBox on the mill's SOUTH (back/output) face. The mill sits on the battery's
		// NORTH side, so the battery must face NORTH to expose its input (front) to the mill's output.
		BlockPos sink = POS.relative(Direction.SOUTH);
		helper.setBlock(sink, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		BatteryBoxBlockEntity battery = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (battery == null) {
			helper.fail("battery_box block entity missing after placement");
		}
		battery.getEnergyStorage().amount = 0;
		drive(mill, helper, 20);
		if (battery.getEnergyStorage().getAmount() <= 0) {
			helper.fail("battery_box on the back face received no EU from the wind mill");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-PHY01 — the mill emits EU only from its BACK face (opposite of FACING);
	 *     the front and the four sides are inert (single-output contract, R-NRG-03). FACING = NORTH by
	 *     default, so SOUTH is the sole OUT face; NORTH/EAST/WEST/UP/DOWN must not extract.
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcWindmill001Phy01_backFaceOnlyOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.WIND_MILL.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		// Only the back face (SOUTH) should support extraction.
		EnergyStorage back = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.SOUTH);
		if (back == null || !back.supportsExtraction()) {
			helper.fail("wind mill BACK (south) face must emit EU");
		}
		// The front and all four sides must be inert.
		for (Direction d : new Direction[]{
				Direction.NORTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
			EnergyStorage p = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), d);
			if (p != null && p.supportsExtraction()) {
				helper.fail("wind mill face " + d + " must NOT emit EU (only the back face does)");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-FUN02 — with no rotor in the slot the mill produces nothing, even under open
	 *     sky and storm. The rotor is a generation gate (progression), not just cosmetic.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Fun02_noRotorProducesNothing(GameTestHelper helper) {
		WindMillBlockEntity mill = placeWithoutRotor(helper);
		setRaining(helper, true); // worst case: storm would normally maximise output
		mill.getEnergyStorage().amount = 0;
		drive(mill, helper, Config.windMillSampleTicks * 2 + 5);
		long got = mill.getEnergyStorage().getAmount();
		if (got != 0) {
			helper.fail("rotorless wind mill generated " + got + " EU; expected 0 (no rotor = no generation)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-FUN03 — with an altitude chip and a rotor installed, the evolve counter
	 *     advances one tick per server-tick under open sky; once {@link Config#windMillEvolveTicks} is
	 *     reached the block transforms into {@code high_altitude_wind_mill} carrying its stored EU.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Fun03_dayChipEvolvesToHighAltitude(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper); // rotor installed
		setClear(helper);
		mill.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModItems.ALIGNMENT_CHIP_DAY));
		mill.getEnergyStorage().amount = 1500; // seed EU to verify it carries across the transform
		mill.setEvolveProgressTicks(Config.windMillEvolveTicks - 1); // one tick short of evolution
		drive(mill, helper, 1); // the next tick trips the threshold
		// The block should have transformed — the old BE is no longer the block entity at POS.
		var evolved = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
		if (evolved == null || evolved.getType() != dev.alaindustrial.registry.ModContent.HIGH_ALTITUDE_WIND_MILL_BE.get()) {
			helper.fail("wind mill did not evolve into high_altitude_wind_mill; got: " + evolved);
		}
		var evolvedMill = helper.getBlockEntity(POS, dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity.class);
		if (evolvedMill == null) {
			helper.fail("evolved block is not a HighAltitudeWindMillBlockEntity");
		} else if (evolvedMill.getEnergyStorage().getAmount() != 1500) {
			helper.fail("evolved mill did not carry EU: expected 1500, got " + evolvedMill.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}
}
