package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

/**
 * Loader-neutral world-based energy gametest bodies (MOD-022) — generators: the fuel generator,
 * solar/daylight/moonlit panels (incl. evolution), wind mill, water mill (wheel gate, wear,
 * clearance) and the geothermal generator. Suite contract and shared helpers:
 * {@link EnergyScenarioSupport}.
 */
public final class GeneratorEnergyScenarios {

	private GeneratorEnergyScenarios() {}

	// ── scenario 1: generator → directly-adjacent battery box (no cable) ──────────────────────────

	private static final BlockPos GEN = new BlockPos(1, 2, 1);

	/**
	 * Generator pushes EU into a directly-adjacent battery box (cable-less push path).
	 * Mirrors: GeneratorGameTest.tcGen001Fun04_pushesToAdjacentConsumer
	 */
	public static void generatorChargesAdjacentBox(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		GeneratorBlockEntity gen = helper.getBlockEntity(GEN, GeneratorBlockEntity.class);
		if (gen == null) {
			helper.fail("generator block entity missing after placement");
		}
		BlockPos sink = GEN.east(); // generator sits on the box's WEST side
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		for (int i = 0; i < 20; i++) {
			tick(helper, gen);
		}
		if (box == null || box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery box received no EU from the generator");
		}
		helper.succeed();
	}

	/**
	 * A generator whose ONLY adjacent neighbour is FULL must not lose EU (EnergyMover full-neighbour
	 * no-leak guard). Pre-charge the generator to half with no fuel (produce()==0), fill the neighbour,
	 * and assert the generator buffer is byte-for-byte unchanged after driving.
	 * Mirrors: GeneratorGameTest.tcGen001Neg05_fullAdjacentConsumerDoesNotDrainGenerator
	 */
	public static void fullNeighbourNoLeak(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		GeneratorBlockEntity gen = helper.getBlockEntity(GEN, GeneratorBlockEntity.class);
		BlockPos sink = GEN.east();
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		BatteryBoxBlockEntity box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (gen == null || box == null) {
			helper.fail("generator or battery_box missing after placement");
		}
		long genStart = gen.getEnergyStorage().getCapacity() / 2;
		gen.getEnergyStorage().setAmountUntracked(genStart); // no fuel: buffer can only change via the push path
		gen.setChanged();
		box.getEnergyStorage().setAmountUntracked(box.getEnergyStorage().getCapacity()); // neighbour full
		box.setChanged();
		for (int i = 0; i < 20; i++) {
			tick(helper, gen);
		}
		long genEnd = gen.getEnergyStorage().getAmount();
		if (genEnd != genStart) {
			helper.fail("generator lost " + (genStart - genEnd) + " EU pushing into a FULL neighbour — "
					+ "EnergyMover refund leak (extracted EU not restored to the maxInsert==0 generator)");
		}
		helper.succeed();
	}

	// ── scenario 6: wind mill pushes buffered EU to an adjacent battery box ───────────────────────

	/**
	 * Wind mill pushes its buffered EU into a directly-adjacent battery box (passive LV generator, no cable).
	 * The buffer is pre-filled so the test is independent of the low test-region altitude (the height→rate
	 * arithmetic is covered numerically at L1 in {@code WindMillOutputTest}); this verifies the world wiring —
	 * face roles and the cable-less push path — on the NeoForge lane.
	 * Mirrors: WindMillGameTest.tcWindmill001Con01_pushesToAdjacentBattery
	 */
	public static void windMillChargesAdjacentBox(GameTestHelper helper) {
		BlockPos millPos = new BlockPos(1, 2, 1);
		helper.setBlock(millPos, ModContent.WIND_MILL.get());
		WindMillBlockEntity mill = helper.getBlockEntity(millPos, WindMillBlockEntity.class);
		if (mill == null) {
			helper.fail("wind mill block entity missing after placement");
		}
		mill.getEnergyStorage().setAmountUntracked(mill.getEnergyStorage().getCapacity()); // ample supply to push
		mill.setChanged();
		// The wind mill emits only from its BACK face (opposite of FACING = NORTH → SOUTH), and since
		// MOD-179 the direct push honors that role too — so the box must sit behind the mill with its
		// input (FACING) face towards it. Mirrors the Fabric tcWindmill001Con01 placement.
		BlockPos sink = millPos.south();
		helper.setBlock(sink, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		BatteryBoxBlockEntity box = helper.getBlockEntity(sink, BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery box missing after placement");
		}
		box.getEnergyStorage().setAmountUntracked(0);
		for (int i = 0; i < 20; i++) {
			tick(helper, mill);
		}
		if (box.getEnergyStorage().getAmount() <= 0) {
			helper.fail("adjacent battery box received no EU from the wind mill");
		}
		helper.succeed();
	}

	// ── scenario 6b: water mill — wheel gate + clearance parity on the NeoForge lane (MOD-179) ─────

	/**
	 * Water mill wheel gate on the NeoForge lane: adjacent water alone produces nothing until the
	 * water wheel is installed, then EU flows. Loader-neutral body — the deep per-case coverage
	 * (faces, interference geometry, cache cadence) lives in the Fabric {@code WaterMillWheelGameTest};
	 * this proves the shared produce() path and slot gating work end to end on NeoForge too.
	 * Mirrors: WaterMillWheelGameTest.waterMillWheel_missingWheelStopsGeneration / installedWheelEnablesGeneration
	 */
	public static void waterMillWheelGate(GameTestHelper helper) {
		BlockPos millPos = new BlockPos(1, 2, 1);
		helper.setBlock(millPos, ModContent.WATER_MILL.get());
		WaterMillBlockEntity mill = helper.getBlockEntity(millPos, WaterMillBlockEntity.class);
		if (mill == null) {
			helper.fail("water mill block entity missing after placement");
		}
		// MOD-188: the mill counts only FLOWING water — a still source powers nothing. Place a static
		// flowing block (LEVEL 1); tick() drives only the mill's serverTick, so it does not dissipate.
		// MOD-352: it has to go in a cell the WHEEL sweeps, not beside the mill block. Default FACING is
		// NORTH, so the undershot cell is one below the front cell.
		helper.setBlock(millPos.north().below(), Blocks.WATER.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 1));
		for (int i = 0; i < 5; i++) {
			tick(helper, mill);
		}
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("water mill generated EU without an installed wheel");
		}
		mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		for (int i = 0; i < 5; i++) {
			tick(helper, mill);
		}
		if (mill.getEnergyStorage().getAmount() <= 0) {
			helper.fail("water mill with water and wheel generated no EU");
		}
		helper.succeed();
	}

	/**
	 * Water mill wheel wear (MOD-189) on the NeoForge lane: a producing mill wears its wheel down and, once
	 * spent, breaks it — the slot empties and generation stops. Also proves the NeoForge-registered
	 * {@code water_mill_wheel} is actually a durability item (wear bails on a non-damageable stack, so a
	 * mis-registration would silently make the wheel eternal). Deep wear coverage (rates, idle no-wear,
	 * evolution) lives on the Fabric lane; this is the loader-parity smoke. A Config override (1 EU per
	 * durability point — the RATE is read live) plus a wheel pre-damaged to one point from death keep it
	 * fast and deterministic.
	 * Mirrors: WaterMillWheelGameTest.waterMillWheel_wearsOutAndBreaks
	 */
	public static void waterMillWheelWearsOut(GameTestHelper helper) {
		int savedRate = Config.waterMillWheelEuPerDamage;
		try {
			Config.waterMillWheelEuPerDamage = 1; // 1 EU of production spends 1 durability point
			BlockPos millPos = new BlockPos(1, 2, 1);
			helper.setBlock(millPos, ModContent.WATER_MILL.get());
			WaterMillBlockEntity mill = helper.getBlockEntity(millPos, WaterMillBlockEntity.class);
			if (mill == null) {
				helper.fail("water mill block entity missing after placement");
				return;
			}
			// MOD-352: the wheel's undershot cell (default FACING is NORTH), not the mill's own face.
			helper.setBlock(millPos.north().below(), Blocks.WATER.defaultBlockState()
					.setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 1)); // one driven side → 1 EU/t
			ItemStack wheel = new ItemStack(ModContent.WATER_MILL_WHEEL.get());
			if (!wheel.isDamageableItem()) {
				helper.fail("water_mill_wheel is not a durability item on NeoForge — wear can never apply");
				return;
			}
			wheel.setDamageValue(wheel.getMaxDamage() - 1); // one active tick from breaking
			mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, wheel);
			for (int i = 0; i < 4; i++) {
				tick(helper, mill);
			}
			if (!mill.getItem(WaterMillBlockEntity.WHEEL_SLOT).isEmpty()) {
				helper.fail("worn-out water wheel not removed on NeoForge; damage="
						+ mill.getItem(WaterMillBlockEntity.WHEEL_SLOT).getDamageValue());
				return;
			}
			mill.getEnergyStorage().setAmountUntracked(0);
			for (int i = 0; i < 4; i++) {
				tick(helper, mill);
			}
			if (mill.getEnergyStorage().getAmount() != 0) {
				helper.fail("water mill kept generating after its wheel broke");
				return;
			}
			helper.succeed();
		} finally {
			Config.waterMillWheelEuPerDamage = savedRate;
		}
	}

	/**
	 * Two water mills face to face with no gap stall symmetrically on the NeoForge lane: each wheel
	 * would clip through the other mill's casing, so the clearance check (MOD-179) reports
	 * MODE_OBSTRUCTED on both and neither produces. This is the exact audit-found blind spot of the
	 * AABB interference test, proven loader-neutral.
	 * Mirrors: WaterMillWheelGameTest.waterMill_faceToFaceAdjacentObstructed
	 */
	public static void waterMillAdjacentFaceToFaceStalls(GameTestHelper helper) {
		BlockPos aPos = new BlockPos(1, 2, 1);
		BlockPos bPos = aPos.east();
		helper.setBlock(aPos, ModContent.WATER_MILL.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		helper.setBlock(bPos, ModContent.WATER_MILL.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		WaterMillBlockEntity a = helper.getBlockEntity(aPos, WaterMillBlockEntity.class);
		WaterMillBlockEntity b = helper.getBlockEntity(bPos, WaterMillBlockEntity.class);
		if (a == null || b == null) {
			helper.fail("water mill block entity missing after placement");
		}
		a.setItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		b.setItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		helper.setBlock(aPos.north(), Blocks.WATER); // a water face for A: obstruction must still win
		for (int i = 0; i < 3; i++) {
			tick(helper, a);
			tick(helper, b);
		}
		if (a.getDataAccess().get(3) != WaterMillBlockEntity.MODE_OBSTRUCTED
				|| b.getDataAccess().get(3) != WaterMillBlockEntity.MODE_OBSTRUCTED) {
			helper.fail("adjacent face-to-face mills not both MODE_OBSTRUCTED: A="
					+ a.getDataAccess().get(3) + " B=" + b.getDataAccess().get(3));
		}
		if (a.getEnergyStorage().getAmount() != 0) {
			helper.fail("obstructed water mill A generated EU");
		}
		helper.succeed();
	}

	// ── scenario 9: solar panel day/night generation (R-NRG-15) ────────────────────────────────────

	/** Set the level to clear daytime and recompute skyDarken synchronously (no tick wait). */
	private static void setClearDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set day");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		level.updateSkyBrightness();
	}

	/** Set the level to clear midnight and recompute skyDarken synchronously. */
	private static void setNight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		level.updateSkyBrightness();
	}

	private static final BlockPos SOLAR = new BlockPos(1, 2, 1);

	/**
	 * Solar panel generates EU by day under open sky, accumulating at exactly {@code solarEuPerTick} ×
	 * globalEuRateMultiplier × ticks. Exercises the full day-brightness wiring on the NeoForge lane
	 * (level.isBrightOutside → produce → buffer). The buffer (8000) is far from full at 20 × 1 EU = 20.
	 * Mirrors: SolarPanelGameTest.tcSolar001Fun01_generatesByDay
	 */
	public static void solarPanelGeneratesByDay(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel) {
			int ticks = 20;
			for (int i = 0; i < ticks; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long perTick = Math.max(1, Math.round(Config.solarEuPerTick * Config.globalEuRateMultiplier));
			long expected = perTick * ticks;
			long got = panel.getEnergyStorage().getAmount();
			if (got != expected) {
				helper.fail("solar day generation over " + ticks + " ticks: got " + got + " EU, expected exactly "
						+ expected + " (perTick=" + perTick + ", bright=" + helper.getLevel().isBrightOutside() + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("solar panel block entity missing");
	}

	/**
	 * Solar panel generates 0 EU at midnight (night mode). A panel that leaks day generation into the
	 * night (broken brightness read or cached skyDarken) would fail here.
	 * Mirrors: SolarPanelGameTest.tcSolar001Neg01_noEuAtNight
	 */
	public static void solarPanelNoEuAtNight(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.SOLAR_PANEL.get());
		setNight(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel) {
			for (int i = 0; i < 20; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long got = panel.getEnergyStorage().getAmount();
			if (got != 0) {
				helper.fail("solar panel generated " + got + " EU at midnight; expected 0 (bright="
						+ helper.getLevel().isBrightOutside() + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("solar panel block entity missing");
	}

	// ── scenario 10: geothermal generator EU rate + no-lava negative ───────────────────────────────

	private static final BlockPos GEO = new BlockPos(1, 2, 1);

	/**
	 * Geothermal generator burns a lava bucket at exactly {@code geothermalEuPerTick} EU/t: over 5 ticks
	 * the buffer grows by {@code 5 × geothermalEuPerTick = 80} EU (buffer 4000 is far from full). Catches a
	 * regression that halves/doubles the conversion factor. The empty bucket is returned to the output slot.
	 * Mirrors: FluidGameTest.tcGeo001Fun01_lavaBucketProducesEu
	 */
	public static void geothermalLavaBucketRate(GameTestHelper helper) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, GEO) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
			geo.setItem(dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity.INPUT_SLOT,
					new ItemStack(Items.LAVA_BUCKET));
			int ticks = 5;
			for (int i = 0; i < ticks; i++) {
				geo.serverTick(helper.getLevel(), geo.getBlockPos(),
						helper.getLevel().getBlockState(geo.getBlockPos()));
			}
			long expected = (long) ticks * Config.geothermalEuPerTick;
			long got = geo.getEnergyStorage().getAmount();
			if (got != expected) {
				helper.fail("geothermal produced " + got + " EU over " + ticks + " ticks, expected exactly "
						+ expected + " (" + ticks + " × geothermalEuPerTick=" + Config.geothermalEuPerTick + ")");
				return;
			}
			if (!geo.getItem(dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity.OUTPUT_SLOT)
					.is(Items.BUCKET)) {
				helper.fail("lava bucket consumed but the empty bucket was not returned");
				return;
			}
			if (!geo.getItem(dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity.INPUT_SLOT).isEmpty()) {
				helper.fail("lava bucket was not consumed from the input slot");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("geothermal block entity missing");
	}

	/**
	 * Geothermal generator with no lava produces 0 EU. Catches a regression where the generator produces
	 * EU unconditionally (ignores the lava/bucket gate).
	 * Mirrors: FluidGameTest.tcGeo001Neg01_noLavaNoEu
	 */
	public static void geothermalNoLavaNoEu(GameTestHelper helper) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, GEO) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
			for (int i = 0; i < 10; i++) {
				geo.serverTick(helper.getLevel(), geo.getBlockPos(),
						helper.getLevel().getBlockState(geo.getBlockPos()));
			}
			long got = geo.getEnergyStorage().getAmount();
			if (got != 0) {
				helper.fail("geothermal produced " + got + " EU without lava; expected 0");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("geothermal block entity missing");
	}

	// ── scenario 13: wind mill weather multiplier on a raised rig (R-NRG-04) ──────────────────────

	private static final BlockPos WIND_RAISED = new BlockPos(1, 20, 1); // above sea level (see WindMillGameTest)

	/** Place a wind mill on a glass pillar at WIND_RAISED with a rotor. */
	private static WindMillBlockEntity placeWindRaised(GameTestHelper helper) {
		for (int y = 2; y < WIND_RAISED.getY(); y++) {
			helper.setBlock(new BlockPos(WIND_RAISED.getX(), y, WIND_RAISED.getZ()), Blocks.GLASS);
		}
		helper.setBlock(WIND_RAISED, ModContent.WIND_MILL.get());
		WindMillBlockEntity mill = helper.getBlockEntity(WIND_RAISED, WindMillBlockEntity.class);
		if (mill != null) {
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(dev.alaindustrial.registry.ModContent.WINDMILL_ROTOR.get()));
		}
		return mill;
	}

	/**
	 * A thunderstorm multiplies the wind mill's base rate (×windMillThunderFactor). Rather than calling
	 * {@code WindMillOutput.euFor} for the oracle (which would make the test tautological — the same code
	 * under test computes the expected value), this drives the real mill in-world for a fixed tick window
	 * at clear weather and at thunder, measuring the buffer growth each time. The storm rate must be
	 * STRICTLY greater than the clear rate (thunder factor > 1), proving the weather wiring end-to-end
	 * through the block entity, not just the static helper.
	 * Mirrors: WindMillGameTest.tcWindmill001Sta01_thunderMultipliesRate
	 */
	public static void windMillThunderMultipliesRate(GameTestHelper helper) {
		WindMillBlockEntity mill = placeWindRaised(helper);
		if (mill == null) {
			helper.fail("raised wind mill block entity missing");
			return;
		}
		ServerLevel level = helper.getLevel();
		int ticks = Config.windMillSampleTicks > 0 ? Config.windMillSampleTicks : 40;

		// Clear-weather sample: empty buffer, drive, measure growth.
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		mill.getEnergyStorage().setAmountUntracked(0);
		for (int i = 0; i < ticks; i++) {
			mill.serverTick(level, mill.getBlockPos(), level.getBlockState(mill.getBlockPos()));
		}
		long clearRate = mill.getEnergyStorage().getAmount();

		// Storm sample: empty buffer again, drive, measure growth.
		level.getWeatherData().setRaining(true);
		level.getWeatherData().setThundering(true);
		level.setRainLevel(1.0f);
		mill.getEnergyStorage().setAmountUntracked(0);
		for (int i = 0; i < ticks; i++) {
			mill.serverTick(level, mill.getBlockPos(), level.getBlockState(mill.getBlockPos()));
		}
		long stormRate = mill.getEnergyStorage().getAmount();

		if (clearRate <= 0) {
			helper.fail("raised wind mill generated 0 EU over " + ticks + " clear-weather ticks"
					+ " — raise the rig further so height base > 0");
			return;
		}
		if (stormRate <= clearRate) {
			helper.fail("thunder did not raise wind output: clear=" + clearRate + " storm=" + stormRate
					+ " over " + ticks + " ticks (expected storm > clear, thunderFactor="
					+ Config.windMillThunderFactor + ")");
			return;
		}
		helper.succeed();
	}

	// ── MOD-356: the GUI readout must equal what the buffer actually gains ─────────────────────────

	/**
	 * A solar panel's readout channel must carry the <b>effective</b> rate — the EU the buffer really
	 * gains — not {@code produce()}'s mechanical figure from before {@link Config#globalEuRateMultiplier}.
	 *
	 * <p><b>Why the multiplier override is load-bearing.</b> At the shipped default of 1.0 the two numbers
	 * are identical, which is exactly why this bug shipped: every existing generator test is green while
	 * the GUI on a retuned server shows half (or a third) of the truth. Driving at 2.0 separates them —
	 * on the pre-fix code the panel gains 2 EU in the measured tick while the channel still reads 1.
	 *
	 * <p>The buffer is zeroed and exactly ONE tick is measured, so "what the channel says" and "what the
	 * buffer gained" are the same tick's numbers rather than an average over a window.
	 */
	public static void solarPanelReadoutMatchesBufferGain(GameTestHelper helper) {
		float savedMultiplier = Config.globalEuRateMultiplier;
		try {
			Config.globalEuRateMultiplier = 2.0f;
			helper.setBlock(SOLAR, ModContent.SOLAR_PANEL.get());
			setClearDay(helper);
			if (!(be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel)) {
				helper.fail("solar panel block entity missing");
				return;
			}
			ServerLevel level = helper.getLevel();
			BlockState state = level.getBlockState(panel.getBlockPos());
			// One warm-up tick populates the sky/weather cache, then measure a single clean tick.
			panel.serverTick(level, panel.getBlockPos(), state);
			panel.getEnergyStorage().setAmountUntracked(0);
			panel.serverTick(level, panel.getBlockPos(), state);

			long gained = panel.getEnergyStorage().getAmount();
			int shown = panel.getDataAccess().get(2);
			if (gained <= 0) {
				helper.fail("solar panel gained no EU in the measured tick — the rig is not generating,"
						+ " so this test could never catch a wrong readout");
				return;
			}
			if (gained == Config.solarEuPerTick) {
				helper.fail("globalEuRateMultiplier=2.0 did not reach the buffer (gained " + gained
						+ " EU, the mechanical rate) — the readout comparison below would be vacuous");
				return;
			}
			if (shown != gained) {
				helper.fail("solar panel readout says " + shown + " EU/t but the buffer gained " + gained
						+ " EU in the same tick (globalEuRateMultiplier=2.0)");
				return;
			}
			helper.succeed();
		} finally {
			Config.globalEuRateMultiplier = savedMultiplier;
		}
	}

	/**
	 * The wind mill's readout must equal the buffer gain, <b>and</b> channel 2 must stay the mechanical
	 * rate. Both halves matter: the mill is the one generator where the rate channel is not free, because
	 * {@code WindMillRotorBlockEntityRenderer} turns channel 2 into the blades' angular speed. Publishing
	 * the multiplied number there would fix the text and break the picture — an EU-economy knob would make
	 * the rotor visibly spin faster and, past ~2×, pin it to the renderer's {@code min(rate, 16)} cap so
	 * wind strength stops reading off the spin at all. So the fix has to split the two, and this test
	 * fails if either half regresses.
	 *
	 * <p>The last leg covers the {@code max(1, ...)} floor: at a multiplier small enough to round the rate
	 * to zero, a mill that is genuinely turning must still report at least 1 EU/t, never 0.
	 */
	public static void windMillReadoutMatchesBufferGain(GameTestHelper helper) {
		float savedMultiplier = Config.globalEuRateMultiplier;
		try {
			WindMillBlockEntity mill = placeWindRaised(helper);
			if (mill == null) {
				helper.fail("raised wind mill block entity missing");
				return;
			}
			ServerLevel level = helper.getLevel();
			BlockPos pos = mill.getBlockPos();
			level.getWeatherData().setRaining(false);
			level.getWeatherData().setThundering(false);
			level.setRainLevel(0.0f);

			// Warm the sampling cache at the shipped multiplier and read off the mechanical rate.
			Config.globalEuRateMultiplier = 1.0f;
			int warmUp = Math.max(1, Config.windMillSampleTicks);
			for (int i = 0; i < warmUp; i++) {
				mill.serverTick(level, pos, level.getBlockState(pos));
			}
			int mechanical = mill.getDataAccess().get(2);
			if (mechanical <= 0) {
				helper.fail("raised wind mill produced nothing over " + warmUp + " ticks — raise the rig"
						+ " further so the height base is above 0, otherwise this test cannot fail");
				return;
			}

			// ×2 — one measured tick.
			Config.globalEuRateMultiplier = 2.0f;
			mill.getEnergyStorage().setAmountUntracked(0);
			mill.serverTick(level, pos, level.getBlockState(pos));
			long gained = mill.getEnergyStorage().getAmount();
			if (gained != mechanical * 2L) {
				helper.fail("globalEuRateMultiplier=2.0 did not reach the buffer: gained " + gained
						+ " EU on a mechanical rate of " + mechanical + " (expected " + (mechanical * 2L) + ")");
				return;
			}
			int shown = mill.getDataAccess().get(WindMillBlockEntity.RATE_CHANNEL);
			if (shown != gained) {
				helper.fail("wind mill readout says " + shown + " EU/t but the buffer gained " + gained
						+ " EU in the same tick (globalEuRateMultiplier=2.0)");
				return;
			}
			int rotorChannel = mill.getDataAccess().get(2);
			if (rotorChannel != mechanical) {
				helper.fail("channel 2 changed to " + rotorChannel + " under a 2.0 multiplier (mechanical rate is "
						+ mechanical + ") — it drives the rotor's spin speed and must stay mechanical,"
						+ " an EU-economy knob may not speed up the blades");
				return;
			}

			// Floor: a multiplier small enough to round to zero still reports a turning mill as ≥ 1 EU/t.
			Config.globalEuRateMultiplier = 0.1f;
			mill.getEnergyStorage().setAmountUntracked(0);
			mill.serverTick(level, pos, level.getBlockState(pos));
			long floorGain = mill.getEnergyStorage().getAmount();
			int floorShown = mill.getDataAccess().get(WindMillBlockEntity.RATE_CHANNEL);
			if (floorGain < 1) {
				helper.fail("a turning mill credited " + floorGain + " EU at multiplier 0.1 — the max(1, ...)"
						+ " floor is gone");
				return;
			}
			if (floorShown != floorGain) {
				helper.fail("at multiplier 0.1 the readout says " + floorShown + " EU/t but the buffer gained "
						+ floorGain + " EU — the floor must apply to the readout too, never showing 0"
						+ " while the mill turns");
				return;
			}
			helper.succeed();
		} finally {
			Config.globalEuRateMultiplier = savedMultiplier;
		}
	}

	/**
	 * The same readout contract on both <b>T2</b> mills, which MOD-356 gave a {@code ContainerData} bridge
	 * of their own — before it they had none and inherited the base four channels.
	 *
	 * <p>Without this the two riskiest pieces of that commit were untested: a wrong {@code RATE_CHANNEL}
	 * index or a forgotten {@code publishEffectiveRate} override would leave the bridge serving a silent 0,
	 * and nothing else would notice — the T2 mills have no other L2 coverage at all, and the width guard in
	 * {@code MenuDataWidthScenarios} checks how many channels there are, never what is in them.
	 *
	 * <p>The two mills are exercised one after another on the same pillar (the second replaces the first)
	 * rather than side by side: two rotor discs within the same scan cube would trip the MOD-051
	 * interference rule and stall both.
	 */
	public static void t2WindMillReadoutsMatchBufferGain(GameTestHelper helper) {
		float savedMultiplier = Config.globalEuRateMultiplier;
		try {
			for (int y = 2; y < WIND_RAISED.getY(); y++) {
				helper.setBlock(new BlockPos(WIND_RAISED.getX(), y, WIND_RAISED.getZ()), Blocks.GLASS);
			}
			ServerLevel level = helper.getLevel();
			level.getWeatherData().setRaining(false);
			level.getWeatherData().setThundering(false);
			level.setRainLevel(0.0f);
			if (!assertT2Readout(helper, ModContent.HIGH_ALTITUDE_WIND_MILL.get(),
					HighAltitudeWindMillBlockEntity.RATE_CHANNEL, "high-altitude wind mill")) {
				return;
			}
			if (!assertT2Readout(helper, ModContent.STORM_WIND_MILL.get(),
					StormWindMillBlockEntity.RATE_CHANNEL, "storm wind mill")) {
				return;
			}
			helper.succeed();
		} finally {
			Config.globalEuRateMultiplier = savedMultiplier;
		}
	}

	/**
	 * Place {@code block} on the shared pillar with a rotor, then assert its rate channel equals the buffer
	 * gain at a 2.0 multiplier while channel 2 stays mechanical. Returns false once it has failed the test.
	 */
	private static boolean assertT2Readout(GameTestHelper helper, Block block, int rateChannel, String label) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(WIND_RAISED, block);
		MachineBlockEntity mill = helper.getBlockEntity(WIND_RAISED, MachineBlockEntity.class);
		if (mill == null) {
			helper.fail(label + ": block entity missing");
			return false;
		}
		// Slot 0 is ROTOR_SLOT on both T2 branches (they declare it separately, same index as the T1 mill).
		mill.setItem(0, new ItemStack(ModContent.WINDMILL_ROTOR.get()));

		Config.globalEuRateMultiplier = 1.0f;
		int warmUp = Math.max(1, Config.windMillSampleTicks);
		for (int i = 0; i < warmUp; i++) {
			mill.serverTick(level, WIND_RAISED, level.getBlockState(WIND_RAISED));
		}
		int mechanical = mill.getDataAccess().get(2);
		if (mechanical <= 0) {
			helper.fail(label + ": produced nothing over " + warmUp + " ticks — raise the rig further,"
					+ " otherwise this test cannot fail");
			return false;
		}

		Config.globalEuRateMultiplier = 2.0f;
		mill.getEnergyStorage().setAmountUntracked(0);
		mill.serverTick(level, WIND_RAISED, level.getBlockState(WIND_RAISED));
		long gained = mill.getEnergyStorage().getAmount();
		if (gained != mechanical * 2L) {
			helper.fail(label + ": multiplier 2.0 banked " + gained + " EU on a mechanical rate of "
					+ mechanical + " (expected " + (mechanical * 2L) + ")");
			return false;
		}
		int shown = mill.getDataAccess().get(rateChannel);
		if (shown != gained) {
			helper.fail(label + ": readout channel " + rateChannel + " says " + shown
					+ " EU/t but the buffer gained " + gained + " EU in the same tick");
			return false;
		}
		if (mill.getDataAccess().get(2) != mechanical) {
			helper.fail(label + ": channel 2 became " + mill.getDataAccess().get(2) + " under a 2.0 multiplier"
					+ " (mechanical rate is " + mechanical + ") — it drives the rotor's spin speed and must"
					+ " stay mechanical on the T2 branches too");
			return false;
		}
		helper.setBlock(WIND_RAISED, Blocks.AIR); // free the pillar for the next mill (MOD-051 interference)
		return true;
	}

	// ── scenario 15: generator full buffer pauses burn (R-NRG-11) ──────────────────────────────────

	/**
	 * A generator with coal but a FULL buffer must pause the burn — burnTime must not decrement while
	 * there is no room for the produced EU. Catches a regression that wastes fuel when full.
	 * Mirrors: GeneratorGameTest.tcGen001Neg03_fullBufferPausesBurn
	 */
	public static void generatorFullBufferPausesBurn(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			// Start a burn.
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos()));
			// Force buffer full.
			gen.getEnergyStorage().setAmountUntracked(gen.getEnergyStorage().getCapacity());
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos()));
			int burn1 = gen.getDataAccess().get(2); // progress == burnTime
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos()));
			int burn2 = gen.getDataAccess().get(2);
			if (!(burn1 > 0 && burn1 == burn2)) {
				helper.fail("full buffer must freeze burn: burn1=" + burn1 + " burn2=" + burn2);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("generator block entity missing");
	}

	/**
	 * Generator EU rate equals {@code Config.fuelEuPerTick} (canon 8 EU/t): one clean tick from empty
	 * produces exactly that much.
	 * Mirrors: GeneratorGameTest.tcGen001Prf01_ratePerTickMatchesConfig
	 */
	public static void generatorRatePerTickMatchesConfig(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos())); // tick 1 starts the burn
			gen.getEnergyStorage().setAmountUntracked(0); // measure one clean tick from empty
			gen.serverTick(helper.getLevel(), gen.getBlockPos(),
					helper.getLevel().getBlockState(gen.getBlockPos()));
			long made = gen.getEnergyStorage().getAmount();
			if (made != Config.fuelEuPerTick) {
				helper.fail("EU/t expected " + Config.fuelEuPerTick + " but measured " + made);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("generator block entity missing");
	}

	// ── scenario 17: moonlit solar panel night generation ──────────────────────────────────────────

	private static final BlockPos MOONLIT = new BlockPos(1, 2, 1);

	/**
	 * Moonlit solar panel generates EU at midnight, accumulating at exactly {@code moonlitEuPerTick} ×
	 * globalEuRateMultiplier × ticks. Exercises the night-brightness branch (inverse of the day panel).
	 * Mirrors: SolarPanelGameTest.tcMoonlit001Fun01_generatesAtNight
	 */
	public static void moonlitPanelGeneratesAtNight(GameTestHelper helper) {
		helper.setBlock(MOONLIT, ModContent.MOONLIT_SOLAR_PANEL.get());
		setNight(helper);
		if (be(helper, MOONLIT) instanceof dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity panel) {
			int ticks = 20;
			for (int i = 0; i < ticks; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long perTick = Math.max(1, Math.round(Config.moonlitEuPerTick * Config.globalEuRateMultiplier));
			long expected = perTick * ticks;
			long got = panel.getEnergyStorage().getAmount();
			if (got != expected) {
				helper.fail("moonlit night generation over " + ticks + " ticks: got " + got + " EU, expected exactly "
						+ expected + " (perTick=" + perTick + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("moonlit panel block entity missing");
	}

	// ── scenario 21: daylight solar panel day generation ───────────────────────────────────────────

	/**
	 * Daylight solar panel generates EU by day, accumulating at exactly {@code daylightEuPerTick} ×
	 * globalEuRateMultiplier × ticks. The daylight panel is the day-evolved branch (4 EU/t).
	 * Mirrors: SolarPanelGameTest.tcDaylight001Fun01_generatesByDay
	 */
	public static void daylightPanelGeneratesByDay(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setClearDay(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity panel) {
			int ticks = 20;
			for (int i = 0; i < ticks; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long perTick = Math.max(1, Math.round(Config.daylightEuPerTick * Config.globalEuRateMultiplier));
			long expected = perTick * ticks;
			long got = panel.getEnergyStorage().getAmount();
			if (got != expected) {
				helper.fail("daylight day generation over " + ticks + " ticks: got " + got + " EU, expected exactly "
						+ expected + " (perTick=" + perTick + ", bright=" + helper.getLevel().isBrightOutside() + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("daylight panel block entity missing");
	}

	/**
	 * Daylight solar panel is day-only: at midnight it produces 0 EU (it does NOT inherit the moonlit
	 * night branch). Catches a regression that lets the evolved panel generate at night.
	 */
	public static void daylightPanelNoEuAtNight(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.DAYLIGHT_SOLAR_PANEL.get());
		setNight(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity panel) {
			for (int i = 0; i < 20; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long got = panel.getEnergyStorage().getAmount();
			if (got != 0) {
				helper.fail("daylight panel generated " + got + " EU at midnight; expected 0 (day-only)");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("daylight panel block entity missing");
	}

	// ── scenario 24: generator buffer cap-1 BVA (R-NRG-01) ─────────────────────────────────────────

	/**
	 * Generator buffer caps at {@code Config.generatorBuffer} (BVA): pre-charged to cap−1, after a
	 * burning tick it tops off to exactly cap, never above. Starting at cap only proves "stays full";
	 * the cap−1 leg proves the boundary is actually reached and enforced.
	 * Mirrors: GeneratorGameTest.tcGen001Fun02_bufferCapsAtMax
	 */
	public static void generatorBufferCapsAtMaxBva(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			long cap = gen.getEnergyStorage().getCapacity();
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().setAmountUntracked(cap - 1); // BVA: one EU short
			// A few ticks: the burn must start and produce EU, topping the buffer off to exactly cap.
			for (int i = 0; i < 5; i++) {
				gen.serverTick(helper.getLevel(), gen.getBlockPos(),
						helper.getLevel().getBlockState(gen.getBlockPos()));
			}
			long got = gen.getEnergyStorage().getAmount();
			if (got != cap) {
				helper.fail("generator buffer did not settle at cap: expected " + cap + " (from cap-1) got " + got);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("generator block entity missing");
	}

	/**
	 * Solar panel buffer caps at {@code Config.solarBuffer} (BVA): pre-charged to cap−1, after a clear-day
	 * tick it tops off to exactly cap.
	 * Mirrors: SolarPanelGameTest.tcSolar001Prf02_bufferCapsAtMax
	 */
	public static void solarPanelBufferCapsAtMaxBva(GameTestHelper helper) {
		helper.setBlock(SOLAR, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		if (be(helper, SOLAR) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel) {
			long cap = Config.solarBuffer;
			panel.getEnergyStorage().setAmountUntracked(cap - 1);
			for (int i = 0; i < 5; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
			}
			long got = panel.getEnergyStorage().getAmount();
			if (got != cap) {
				helper.fail("solar buffer did not settle at cap: expected " + cap + " (from cap-1) got " + got);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("solar panel block entity missing");
	}

	// ── scenario 25: solar evolution chip (day → daylight) ─────────────────────────────────────────

	private static final BlockPos EVO = new BlockPos(1, 2, 1);

	/**
	 * A base solar panel with a day alignment chip, under clear daylight, evolves into the daylight
	 * panel after {@code solarEvolveTicks} of accumulated sky-time. The block in the world changes from
	 * SOLAR_PANEL to DAYLIGHT_SOLAR_PANEL. Catches a regression that breaks the evolution wiring.
	 * Mirrors: SolarPanelGameTest.tcSolar001Fun02_dayChipEvolvesToDaylight
	 */
	public static void solarDayChipEvolvesToDaylight(GameTestHelper helper) {
		helper.setBlock(EVO, ModContent.SOLAR_PANEL.get());
		setClearDay(helper);
		if (be(helper, EVO) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity panel) {
			panel.setItem(dev.alaindustrial.block.entity.SolarPanelBlockEntity.CHIP_SLOT,
					new ItemStack(dev.alaindustrial.registry.ModContent.ALIGNMENT_CHIP_DAY.get()));
			// Pre-charge the buffer so the carry-EU assertion below is meaningful (placement leaves 0).
			long energy0 = 1500L;
			panel.getEnergyStorage().setAmountUntracked(energy0);
			for (int i = 0; i < Config.solarEvolveTicks + 100; i++) {
				panel.serverTick(helper.getLevel(), panel.getBlockPos(),
						helper.getLevel().getBlockState(panel.getBlockPos()));
				// Re-grab the BE: evolution replaces it with a DaylightSolarPanelBlockEntity.
				if (!(be(helper, EVO) instanceof dev.alaindustrial.block.entity.SolarPanelBlockEntity)) {
					break;
				}
			}
			net.minecraft.world.level.block.state.BlockState evolved =
					helper.getLevel().getBlockState(helper.absolutePos(EVO));
			if (evolved.getBlock() != ModContent.DAYLIGHT_SOLAR_PANEL.get()) {
				helper.fail("solar panel did not evolve into daylight after " + Config.solarEvolveTicks
						+ " ticks with a day chip; block=" + evolved.getBlock());
				return;
			}
			// The shared evolveInto helper must carry the stored EU (clamped to the target capacity) and
			// consume the chip — pin both so a regression in AbstractGeneratorBlockEntity.evolveInto
			// (e.g. wrong slot override map) does not silently land.
			if (!(be(helper, EVO) instanceof MachineBlockEntity evolvedBe)) {
				helper.fail("evolved daylight panel has no MachineBlockEntity");
				return;
			}
			long energy1 = evolvedBe.getEnergyStorage().getAmount();
			if (energy1 < energy0) {
				helper.fail("evolution lost stored EU: " + energy0 + " -> " + energy1);
				return;
			}
			if (!evolvedBe.getItem(dev.alaindustrial.block.entity.SolarPanelBlockEntity.CHIP_SLOT).isEmpty()) {
				helper.fail("evolution did not consume the chip slot");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("solar panel block entity missing");
	}

	// ── scenario 28: geothermal fluid tank droplet↔MB boundary ─────────────────────────────────────

	/**
	 * One lava bucket yields exactly {@code geothermalBurnTicks × geothermalEuPerTick} EU total — the
	 * canonical fuel-value invariant (one bucket = 1000 burn ticks × 16 EU/t = 16000 EU). The geothermal
	 * converts a bucket directly into its internal {@code lavaTicks} burn buffer (not the fluid tank, which
	 * is pump-fed), so this asserts the cumulative EU after the whole bucket burns. Catches a regression
	 * that drops the conversion factor or mis-counts burn ticks. Drives enough ticks to exhaust the burn
	 * and drains the EU buffer between ticks so the cap does not mask the total.
	 * Mirrors: FluidGameTest.tcGeo001Prf03_oneBucketYieldsTotalEu
	 */
	public static void geothermalTankBucketBoundary(GameTestHelper helper) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, GEO) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
			geo.setItem(dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity.INPUT_SLOT,
					new ItemStack(Items.LAVA_BUCKET));
			long expected = (long) Config.geothermalBurnTicks * Config.geothermalEuPerTick;
			long total = 0;
			// Drive well past the burn duration, draining the buffer each tick so the cap never pauses
			// the burn (which would mask the cumulative total).
			for (int i = 0; i < Config.geothermalBurnTicks + 100; i++) {
				geo.serverTick(helper.getLevel(), geo.getBlockPos(),
						helper.getLevel().getBlockState(geo.getBlockPos()));
				total += geo.getEnergyStorage().getAmount();
				geo.getEnergyStorage().setAmountUntracked(0); // drain, so production never pauses
			}
			if (total != expected) {
				helper.fail("one lava bucket yielded " + total + " EU total, expected exactly " + expected
						+ " (geothermalBurnTicks=" + Config.geothermalBurnTicks + " × geothermalEuPerTick="
						+ Config.geothermalEuPerTick + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("geothermal block entity missing");
	}

	// ── scenario 29: generator rejects external EU (producer-only, R-NRG-03) ────────────────────────

	/**
	 * The generator publishes {@code maxInsert == 0}: it is a producer only and never accepts external EU.
	 * A regression that lets the generator soak up EU from its neighbours (acting as a sink) would fail
	 * here. Catches the same invariant as the Fabric lane's GeneratorGameTest NEG01.
	 * Mirrors: GeneratorGameTest.tcGen001Neg01_rejectsExternalEu
	 */
	public static void generatorRejectsExternalEu(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			if (gen.getEnergyStorage().supportsInsertion()) {
				helper.fail("generator storage supports insertion — it must be a producer only (maxInsert=0)");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("generator block entity missing");
	}

	// ── scenario 35: wind mill roofed → 0 EU (mode wiring) ─────────────────────────────────────────

	/**
	 * A roofed wind mill (no open-sky column above) produces 0 EU regardless of height/weather. Catches
	 * a regression that drops the open-sky gate. The mill is at the default low POS, so height is already
	 * 0; a stone block above makes the mode ROOFED, distinguishing "dead from roof" from "dead from height".
	 * Mirrors: WindMillGameTest.tcWindmill001Neg01_roofedYieldsZero (mode leg)
	 */
	public static void windMillRoofedYieldsZero(GameTestHelper helper) {
		BlockPos millPos = new BlockPos(1, 2, 1);
		helper.setBlock(millPos, ModContent.WIND_MILL.get());
		helper.setBlock(millPos.above(), Blocks.STONE); // roof
		if (be(helper, millPos) instanceof WindMillBlockEntity mill) {
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT,
					new ItemStack(dev.alaindustrial.registry.ModContent.WINDMILL_ROTOR.get()));
			// Even in a thunderstorm a roof kills it — the sky gate outranks the weather multiplier.
			ServerLevel level = helper.getLevel();
			level.getWeatherData().setRaining(true);
			level.getWeatherData().setThundering(true);
			level.setRainLevel(1.0f);
			mill.getEnergyStorage().setAmountUntracked(0);
			for (int i = 0; i < Config.windMillSampleTicks + 5; i++) {
				mill.serverTick(level, mill.getBlockPos(), level.getBlockState(mill.getBlockPos()));
			}
			long got = mill.getEnergyStorage().getAmount();
			if (got != 0) {
				helper.fail("roofed wind mill generated " + got + " EU; expected 0 (no open sky)");
				return;
			}
			// The mill sits at the low rig height, so 0 EU alone cannot tell "dead from roof" from "dead
			// from height": the MODE code on the maxProgress sync channel (3) is the discriminating signal.
			if (mill.getDataAccess().get(3) != WindMillBlockEntity.MODE_ROOFED) {
				helper.fail("roofed wind mill mode = " + mill.getDataAccess().get(3) + "; expected ROOFED ("
						+ WindMillBlockEntity.MODE_ROOFED + ")");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("wind mill block entity missing");
	}
}
