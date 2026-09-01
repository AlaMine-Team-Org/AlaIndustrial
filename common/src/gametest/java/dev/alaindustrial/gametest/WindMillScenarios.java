package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.environment.WindMillOutput;
import dev.alaindustrial.core.environment.WindProfile;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral world-based gametest bodies (MOD-323 batch E) for the wind mill — the passive
 * height/sky/weather-driven LV generator: sampled rate, clearance/interference modes, rotor gate,
 * buffer cap, NBT persistence, chip evolution (day/night), chip-automation guards and rotor wear.
 * Each body drives {@code serverTick} directly (deterministic, no waiting).
 *
 * <p>Rate tests build the mill on a glass pillar at {@link #RAISED_POS} so the height base clears
 * the first step; {@link #requirePositiveHeightBase} is the tripwire that fails loudly if the rig
 * ever drops below it. Weather is set synchronously via {@code WeatherData} plus the interpolated
 * rain level. Numbers come from {@link Config} (canon), never hard-coded.
 */
public final class WindMillScenarios {

	private WindMillScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/**
	 * Raised position for the rate tests. The gametest structure sits below the first height base
	 * step, so a mill at {@link #POS} would always produce 0 and the rate assertion collapses to
	 * {@code 0 != 0}. {@code RAISED_POS} (relative Y = 20) lifts the mill above the first step so
	 * the base is ≥ 1 and the rate is observable end-to-end. A single glass pillar carries the mill
	 * (glass keeps the open-sky column clear; the mill's own clearance volume is in front, not
	 * below, so the pillar does not obstruct it).
	 */
	private static final BlockPos RAISED_POS = new BlockPos(1, 20, 1);

	private static WindMillBlockEntity place(GameTestHelper helper) {
		WindMillBlockEntity be = placeWithoutRotor(helper);
		// The rotor is a generation gate: install one so the production tests see real output.
		be.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(ModContent.WINDMILL_ROTOR.get()));
		return be;
	}

	/** Place a mill with no rotor — for the gate test (no rotor → no generation). */
	private static WindMillBlockEntity placeWithoutRotor(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModContent.WIND_MILL.get(), WindMillBlockEntity.class);
	}

	/**
	 * Place a mill at {@link #RAISED_POS} on a glass pillar so its Y clears the first height base
	 * step, and a rotor is installed. Used by the rate/wear tests that need a non-zero base.
	 */
	private static WindMillBlockEntity placeRaised(GameTestHelper helper) {
		// Build a glass pillar from the structure floor up to just under the mill. Glass is transparent
		// to skylight and the mill's clearance cone is the 2×2 in FRONT (FACING), not below, so the pillar
		// does not trigger the roofed/obstructed mode.
		for (int y = POS.getY(); y < RAISED_POS.getY(); y++) {
			helper.setBlock(new BlockPos(RAISED_POS.getX(), y, RAISED_POS.getZ()), Blocks.GLASS);
		}
		helper.setBlock(RAISED_POS, ModContent.WIND_MILL.get());
		WindMillBlockEntity be = helper.getBlockEntity(RAISED_POS, WindMillBlockEntity.class);
		if (be == null) {
			helper.fail("raised wind mill block entity missing after placement");
		}
		be.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(ModContent.WINDMILL_ROTOR.get()));
		return be;
	}

	/** Place a second wind mill (rotor installed) at the given position with the given FACING. */
	private static WindMillBlockEntity placeNeighbour(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, ModContent.WIND_MILL.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, facing));
		WindMillBlockEntity be = helper.getBlockEntity(pos, WindMillBlockEntity.class);
		if (be == null) {
			helper.fail("neighbour wind mill block entity missing after placement");
		}
		be.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(ModContent.WINDMILL_ROTOR.get()));
		return be;
	}

	private static void drive(WindMillBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
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

	/**
	 * Precondition guard against the false-green failure mode of the rate tests. The vanilla
	 * {@code GameTestServer} hardcodes the structure origin well below sea level; at {@code base == 0}
	 * {@link WindMillOutput#euFor} returns 0 regardless of weather, so the {@code got != expected}
	 * assertion collapses to {@code 0 != 0} — the test passes even with a broken {@code produce()}.
	 *
	 * <p>This guard makes that failure mode LOUD: if the mill sits at/below the first height base
	 * step ({@code base < 1}), the test fails at setup with a diagnostic instead of silently
	 * confirming nothing. The rate tests build the mill on a short pillar so {@code base >= 1} and
	 * this guard is a no-op; it stays in place as a tripwire in case the gametest world's sea level
	 * ever changes.
	 */
	private static void requirePositiveHeightBase(GameTestHelper helper, BlockPos millRel) {
		BlockPos abs = helper.absolutePos(millRel);
		int sea = helper.getLevel().getSeaLevel();
		// MOD-347: the height term is the shared altitude profile, not a 16-block ramp. Ask the
		// profile itself what base this rig gets, so the tripwire cannot drift from production.
		int ridge = WindProfile.ridgeY(sea,
				Config.windMillMaxBaseEuPerTick, 16, Config.windCloudY);
		int base = Math.round(Config.windMillMaxBaseEuPerTick
				* WindProfile.factor(abs.getY(), sea, ridge,
						Config.windCloudY, Config.windDeadY, Config.windRidgeFactor, Config.windTraceFactor));
		if (base <= 0) {
			helper.fail("wind-mill rate test region is below the first height base step: absY=" + abs.getY()
					+ " sea=" + sea + " → base=0 → expected rate is always 0, so the got!=expected assertion "
					+ "can never fail. Build the mill higher (Y ≥ sea+16=" + (sea + 16)
					+ ") or move positive rate coverage to L1 (WindMillOutputTest).");
		}
	}

	/** Rig geometry vs the MOD-347 wind profile — printed when a height-dependent assertion fails. */
	private static String rigDiagnostics(GameTestHelper helper, BlockPos rel) {
		BlockPos abs = helper.absolutePos(rel);
		int sea = helper.getLevel().getSeaLevel();
		int ridge = WindProfile.ridgeY(sea,
				Config.windMillMaxBaseEuPerTick, 16, Config.windCloudY);
		float f = WindProfile.factor(abs.getY(), sea, ridge,
				Config.windCloudY, Config.windDeadY, Config.windRidgeFactor, Config.windTraceFactor);
		return "absY=" + abs.getY() + " sea=" + sea + " ridge=" + ridge + " factor=" + f
				+ " base=" + Math.round(Config.windMillMaxBaseEuPerTick * f);
	}

	/** The per-tick EU the mill should produce under the current world state (open sky assumed). */
	private static int expectedRate(GameTestHelper helper, BlockPos millRel) {
		Level level = helper.getLevel();
		BlockPos abs = helper.absolutePos(millRel);
		return WindMillOutput.euFor(abs.getY(), level.getSeaLevel(), true,
				level.isRaining(), level.isThundering(),
				Config.windMillMaxBaseEuPerTick, 16, Config.windMillMaxEuPerTick,
				Config.windMillRainFactor, Config.windMillThunderFactor,
				Config.windCloudY, Config.windDeadY, Config.windRidgeFactor, Config.windTraceFactor, 1.0f);
	}

	private static long afterGlobalRate(int made) {
		return made > 0 ? Math.max(1, Math.round(made * Config.globalEuRateMultiplier)) : 0;
	}

	private static void assertMode(GameTestHelper helper, WindMillBlockEntity mill, String label, int expected) {
		int mode = mill.getDataAccess().get(3);
		if (mode != expected) {
			helper.fail(label + " mode = " + mode + "; expected " + expected);
		}
	}

	/** Build a glass pillar and place an evolved mill at {@link #RAISED_POS} with a rotor installed. */
	private static HighAltitudeWindMillBlockEntity placeRaisedHighAltitude(GameTestHelper helper) {
		for (int y = POS.getY(); y < RAISED_POS.getY(); y++) {
			helper.setBlock(new BlockPos(RAISED_POS.getX(), y, RAISED_POS.getZ()), Blocks.GLASS);
		}
		helper.setBlock(RAISED_POS, ModContent.HIGH_ALTITUDE_WIND_MILL.get());
		var be = helper.getBlockEntity(RAISED_POS, HighAltitudeWindMillBlockEntity.class);
		if (be == null) {
			helper.fail("raised high-altitude wind mill block entity missing after placement");
		}
		be.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(ModContent.WINDMILL_ROTOR.get()));
		return be;
	}

	/**
	 * Under open sky the wind mill produces the height/weather rate {@link WindMillOutput#euFor}
	 * yields for the region, sampled every {@code windMillSampleTicks}; driven past multiple sample
	 * windows, the accumulated EU equals the per-tick rate × ticks. Raised rig + height-base
	 * tripwire so the rate is non-zero.
	 * Mirrors: WindMillGameTest.tcWindmill001Fun01_generatesSampledRate
	 */
	public static void tcWindmill001Fun01_generatesSampledRate(GameTestHelper helper) {
		WindMillBlockEntity mill = placeRaised(helper); // raised so base >= 1 (see RAISED_POS)
		requirePositiveHeightBase(helper, RAISED_POS); // tripwire: fail loudly if the rig ever drops below base 1
		setClear(helper);
		mill.getEnergyStorage().setAmountUntracked(0);
		int ticks = Config.windMillSampleTicks * 2 + 5; // span multiple sample windows
		long perTick = afterGlobalRate(expectedRate(helper, RAISED_POS));
		drive(mill, helper, ticks);
		long got = mill.getEnergyStorage().getAmount();
		long expected = perTick * ticks;
		if (got != expected) {
			helper.fail("wind mill output over " + ticks + " ticks: got " + got + " EU, expected " + expected
					+ " (perTick=" + perTick + ", rate=" + expectedRate(helper, RAISED_POS) + " at y="
					+ helper.absolutePos(RAISED_POS).getY() + ", sea=" + helper.getLevel().getSeaLevel() + ")");
		}
		helper.succeed();
	}

	/**
	 * A solid block in the FRONT neighbour's space (where the spinning 2×2 rotor lives) stalls the
	 * blades: mode OBSTRUCTED. The mode assertion is the signal — EU is 0 from height in the region.
	 * Mirrors: WindMillGameTest.tcWindmill001Neg02_frontObstructionYieldsZero
	 */
	public static void tcWindmill001Neg02_frontObstructionYieldsZero(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		helper.setBlock(POS.north(), Blocks.STONE); // FACING NORTH → the front block the rotor occupies
		setRaining(helper, true); // storm would normally maximise output
		mill.getEnergyStorage().setAmountUntracked(0);
		drive(mill, helper, Config.windMillSampleTicks + 1);
		if (mill.getDataAccess().get(3) != WindMillBlockEntity.MODE_OBSTRUCTED) {
			helper.fail("front-obstructed wind mill mode = " + mill.getDataAccess().get(3)
					+ "; expected OBSTRUCTED (" + WindMillBlockEntity.MODE_OBSTRUCTED + ")");
		}
		helper.succeed();
	}

	/**
	 * The blade tips reach one block left/right of the FRONT block; a solid block beside the front
	 * stalls a blade tip: mode OBSTRUCTED.
	 * Mirrors: WindMillGameTest.tcWindmill001Neg03_sideObstructionYieldsZero
	 */
	public static void tcWindmill001Neg03_sideObstructionYieldsZero(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		helper.setBlock(POS.north().east(), Blocks.STONE); // blade tip reaches one block east of the front
		setRaining(helper, true);
		mill.getEnergyStorage().setAmountUntracked(0);
		drive(mill, helper, Config.windMillSampleTicks + 1);
		if (mill.getDataAccess().get(3) != WindMillBlockEntity.MODE_OBSTRUCTED) {
			helper.fail("side-obstructed wind mill mode = " + mill.getDataAccess().get(3)
					+ "; expected OBSTRUCTED (" + WindMillBlockEntity.MODE_OBSTRUCTED + ")");
		}
		helper.succeed();
	}

	/**
	 * The lower blade arc dips into the pit below the FRONT block; a solid block directly beneath
	 * the front stalls the blades: mode OBSTRUCTED.
	 * Mirrors: WindMillGameTest.tcWindmill001Neg04_pitObstructionYieldsZero
	 */
	public static void tcWindmill001Neg04_pitObstructionYieldsZero(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		helper.setBlock(POS.north().below(), Blocks.STONE); // the pit's centre is below the front block
		setRaining(helper, true);
		mill.getEnergyStorage().setAmountUntracked(0);
		drive(mill, helper, Config.windMillSampleTicks + 1);
		if (mill.getDataAccess().get(3) != WindMillBlockEntity.MODE_OBSTRUCTED) {
			helper.fail("pit-obstructed wind mill mode = " + mill.getDataAccess().get(3)
					+ "; expected OBSTRUCTED (" + WindMillBlockEntity.MODE_OBSTRUCTED + ")");
		}
		helper.succeed();
	}

	/**
	 * Control case: with open sky and all clearance positions free, the mill is NOT obstructed —
	 * mode CALM (distinct from OBSTRUCTED) proves the clearance check does not fire on empty space.
	 * Mirrors: WindMillGameTest.tcWindmill001Neg05_clearAreaNotObstructed
	 */
	public static void tcWindmill001Neg05_clearAreaNotObstructed(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		setClear(helper);
		drive(mill, helper, Config.windMillSampleTicks + 1);
		int mode = mill.getDataAccess().get(3);
		if (mode == WindMillBlockEntity.MODE_OBSTRUCTED) {
			helper.fail("wind mill reported OBSTRUCTED with a clear area; mode=" + mode);
		}
		helper.succeed();
	}

	/**
	 * The buffer caps at {@code Config.windMillBuffer}; excess EU is discarded
	 * (use-it-or-lose-it), even if the mill is producing.
	 * Mirrors: WindMillGameTest.tcWindmill001Prf01_bufferCapsAtMax
	 */
	public static void tcWindmill001Prf01_bufferCapsAtMax(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		setRaining(helper, true);
		mill.getEnergyStorage().setAmountUntracked(Config.windMillBuffer);
		drive(mill, helper, Config.windMillSampleTicks * 2);
		long got = mill.getEnergyStorage().getAmount();
		if (got != Config.windMillBuffer) {
			helper.fail("buffer changed from cap: expected " + Config.windMillBuffer + " got " + got);
		}
		helper.succeed();
	}

	/**
	 * The stored EU buffer survives an NBT save/load round-trip (energy persists via the base
	 * MachineBlockEntity; the mill's sampling state is transient and recomputed).
	 * Mirrors: WindMillGameTest.tcWindmill001Per01_energySurvivesNbtRoundTrip
	 */
	public static void tcWindmill001Per01_energySurvivesNbtRoundTrip(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		mill.getEnergyStorage().setAmountUntracked(1234); // seed a buffer independent of in-region production
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
	 * With no rotor in the slot the mill produces nothing, even under open sky and storm — the
	 * rotor is a generation gate (progression), not just cosmetic.
	 * Mirrors: WindMillGameTest.tcWindmill001Fun02_noRotorProducesNothing
	 */
	public static void tcWindmill001Fun02_noRotorProducesNothing(GameTestHelper helper) {
		WindMillBlockEntity mill = placeWithoutRotor(helper);
		setRaining(helper, true); // worst case: storm would normally maximise output
		mill.getEnergyStorage().setAmountUntracked(0);
		drive(mill, helper, Config.windMillSampleTicks * 2 + 5);
		long got = mill.getEnergyStorage().getAmount();
		if (got != 0) {
			helper.fail("rotorless wind mill generated " + got + " EU; expected 0 (no rotor = no generation)");
		}
		helper.succeed();
	}

	/**
	 * Two mills side by side (directly adjacent, same FACING) with rotors in both: the 2×2 rotor
	 * discs are coplanar and overlap by a full block, so BOTH mills report MODE_INTERFERENCE and
	 * produce nothing — there is no tie-break (MOD-051). Even a storm does not override interference.
	 * Mirrors: WindMillGameTest.tcWindmill001Neg06_sideBySideInterference
	 */
	public static void tcWindmill001Neg06_sideBySideInterference(GameTestHelper helper) {
		WindMillBlockEntity a = place(helper); // FACING NORTH at POS
		WindMillBlockEntity b = placeNeighbour(helper, POS.east(), Direction.NORTH);
		setRaining(helper, true);
		a.getEnergyStorage().setAmountUntracked(0);
		b.getEnergyStorage().setAmountUntracked(0);
		drive(a, helper, Config.windMillSampleTicks + 1);
		drive(b, helper, Config.windMillSampleTicks + 1);
		assertMode(helper, a, "side-by-side mill A", WindMillBlockEntity.MODE_INTERFERENCE);
		assertMode(helper, b, "side-by-side mill B", WindMillBlockEntity.MODE_INTERFERENCE);
		if (a.getEnergyStorage().getAmount() != 0 || b.getEnergyStorage().getAmount() != 0) {
			helper.fail("interfering mills generated EU; expected 0 for both");
		}
		helper.succeed();
	}

	/**
	 * Two mills facing each other across a one-block gap: both discs live in front of their mills
	 * and overlap inside the gap column, so both report MODE_INTERFERENCE (MOD-051).
	 * Mirrors: WindMillGameTest.tcWindmill001Neg07_faceToFaceInterference
	 */
	public static void tcWindmill001Neg07_faceToFaceInterference(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.WIND_MILL.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		WindMillBlockEntity a = helper.getBlockEntity(POS, WindMillBlockEntity.class);
		if (a == null) {
			helper.fail("wind mill block entity missing after placement");
		}
		a.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(ModContent.WINDMILL_ROTOR.get()));
		WindMillBlockEntity b = placeNeighbour(helper, POS.east(2), Direction.WEST);
		setClear(helper);
		drive(a, helper, Config.windMillSampleTicks + 1);
		drive(b, helper, Config.windMillSampleTicks + 1);
		assertMode(helper, a, "face-to-face mill A", WindMillBlockEntity.MODE_INTERFERENCE);
		assertMode(helper, b, "face-to-face mill B", WindMillBlockEntity.MODE_INTERFERENCE);
		helper.succeed();
	}

	/**
	 * A mill running clean flips to MODE_INTERFERENCE within one sample window after a rotor is
	 * installed in an adjacent mill (the disc appears only with a rotor). Guards the "player
	 * builds a second mill next to a working one" path (MOD-051).
	 * Mirrors: WindMillGameTest.tcWindmill001Neg08_lateRotorTriggersInterference
	 */
	public static void tcWindmill001Neg08_lateRotorTriggersInterference(GameTestHelper helper) {
		WindMillBlockEntity a = place(helper); // FACING NORTH, rotor installed
		setClear(helper);
		// Neighbour mill exists but has NO rotor yet: no disc, no interference.
		helper.setBlock(POS.east(), ModContent.WIND_MILL.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		WindMillBlockEntity b = helper.getBlockEntity(POS.east(), WindMillBlockEntity.class);
		if (b == null) {
			helper.fail("neighbour wind mill block entity missing after placement");
		}
		drive(a, helper, Config.windMillSampleTicks + 1);
		int mode = a.getDataAccess().get(3);
		if (mode == WindMillBlockEntity.MODE_INTERFERENCE) {
			helper.fail("mill A interfered while the neighbour had no rotor; mode=" + mode);
		}
		// Install the neighbour's rotor: A must flip to INTERFERENCE on its next sample.
		b.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(ModContent.WINDMILL_ROTOR.get()));
		drive(a, helper, Config.windMillSampleTicks);
		assertMode(helper, a, "mill A after neighbour rotor install", WindMillBlockEntity.MODE_INTERFERENCE);
		helper.succeed();
	}

	/**
	 * Control: mills two blocks apart (one air block between, same FACING) have discs meeting
	 * exactly edge-to-edge, which is NOT interference — both keep running. Guards against false
	 * positives that would outlaw legitimate compact wind farms (MOD-051).
	 * Mirrors: WindMillGameTest.tcWindmill001Neg09_spacedMillsNotInterfering
	 */
	public static void tcWindmill001Neg09_spacedMillsNotInterfering(GameTestHelper helper) {
		WindMillBlockEntity a = place(helper); // FACING NORTH at POS
		WindMillBlockEntity b = placeNeighbour(helper, POS.east(2), Direction.NORTH);
		setClear(helper);
		drive(a, helper, Config.windMillSampleTicks + 1);
		drive(b, helper, Config.windMillSampleTicks + 1);
		if (a.getDataAccess().get(3) == WindMillBlockEntity.MODE_INTERFERENCE
				|| b.getDataAccess().get(3) == WindMillBlockEntity.MODE_INTERFERENCE) {
			helper.fail("mills two blocks apart reported INTERFERENCE; discs only touch edge-to-edge");
		}
		helper.succeed();
	}

	/**
	 * Control: directly adjacent mills facing AWAY from each other (opposite FACING) put their
	 * discs on opposite sides — no overlap, no interference (MOD-051). Turning mills apart is the
	 * documented way to pack them tightly.
	 * Mirrors: WindMillGameTest.tcWindmill001Neg10_backToBackNotInterfering
	 */
	public static void tcWindmill001Neg10_backToBackNotInterfering(GameTestHelper helper) {
		WindMillBlockEntity a = place(helper); // FACING NORTH at POS
		WindMillBlockEntity b = placeNeighbour(helper, POS.east(), Direction.SOUTH);
		setClear(helper);
		drive(a, helper, Config.windMillSampleTicks + 1);
		drive(b, helper, Config.windMillSampleTicks + 1);
		if (a.getDataAccess().get(3) == WindMillBlockEntity.MODE_INTERFERENCE
				|| b.getDataAccess().get(3) == WindMillBlockEntity.MODE_INTERFERENCE) {
			helper.fail("opposite-facing adjacent mills reported INTERFERENCE; their discs cannot overlap");
		}
		helper.succeed();
	}

	/**
	 * Evolution freezes under interference: with a chip, a rotor and an interfering neighbour, the
	 * evolve counter does not advance (blades that cannot turn do not evolve — same rule as
	 * obstruction, MOD-051).
	 * Mirrors: WindMillGameTest.tcWindmill001Fun04_interferenceFreezesEvolution
	 */
	public static void tcWindmill001Fun04_interferenceFreezesEvolution(GameTestHelper helper) {
		WindMillBlockEntity a = place(helper); // FACING NORTH, rotor installed
		placeNeighbour(helper, POS.east(), Direction.NORTH); // interfering neighbour with rotor
		setClear(helper);
		a.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()));
		a.setEvolveProgressTicks(0);
		drive(a, helper, Config.windMillSampleTicks + 1);
		if (a.getEvolveProgressTicks() != 0) {
			helper.fail("evolve counter advanced under interference: " + a.getEvolveProgressTicks()
					+ " ticks; expected 0");
		}
		helper.succeed();
	}

	/**
	 * With an altitude chip and a rotor installed, the evolve counter advances under open sky; once
	 * {@link Config#windMillEvolveTicks} is reached the block transforms into
	 * {@code high_altitude_wind_mill} carrying its stored EU, the rotor (slot override) and
	 * consuming the chip (MOD-166 #4).
	 * Mirrors: WindMillGameTest.tcWindmill001Fun03_dayChipEvolvesToHighAltitude
	 */
	public static void tcWindmill001Fun03_dayChipEvolvesToHighAltitude(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper); // rotor installed
		setClear(helper);
		mill.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()));
		mill.getEnergyStorage().setAmountUntracked(1500); // seed EU to verify it carries across the transform
		mill.setEvolveProgressTicks(Config.windMillEvolveTicks - 1); // one tick short of evolution
		drive(mill, helper, 1); // the next tick trips the threshold
		// The block should have transformed — the old BE is no longer the block entity at POS.
		var evolved = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
		if (evolved == null || evolved.getType() != ModContent.HIGH_ALTITUDE_WIND_MILL_BE.get()) {
			helper.fail("wind mill did not evolve into high_altitude_wind_mill; got: " + evolved);
		}
		var evolvedMill = helper.getBlockEntity(POS, HighAltitudeWindMillBlockEntity.class);
		if (evolvedMill == null) {
			helper.fail("evolved block is not a HighAltitudeWindMillBlockEntity");
			return;
		}
		if (evolvedMill.getEnergyStorage().getAmount() != 1500) {
			helper.fail("evolved mill did not carry EU: expected 1500, got " + evolvedMill.getEnergyStorage().getAmount());
			return;
		}
		// MOD-166 (#4): the shared evolveInto helper also carries the rotor (slot override) and
		// consumes the chip. Pin both so a regression in the slot-overrides map of the helper
		// does not silently drop the rotor or leave the chip behind.
		if (!evolvedMill.getItem(WindMillBlockEntity.ROTOR_SLOT).is(ModContent.WINDMILL_ROTOR.get())) {
			helper.fail("evolved mill did not carry the installed rotor");
			return;
		}
		if (!evolvedMill.getItem(WindMillBlockEntity.CHIP_SLOT).isEmpty()) {
			helper.fail("evolved mill did not consume the chip slot");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-211 — automation may insert a chip only while the slot is EMPTY; without the
	 * {@code isEmpty()} guard in {@code WindMillBlockEntity.canPlaceItem} a hopper or item pipe
	 * fed the slot one chip at a time up to 64.
	 * Mirrors: WindMillGameTest.windMill_automationCannotStackSecondChip
	 */
	public static void windMill_automationCannotStackSecondChip(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		ItemStack chip = new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get());
		if (!mill.canPlaceItemThroughFace(WindMillBlockEntity.CHIP_SLOT, chip, Direction.UP)) {
			helper.fail("automation could not insert a chip into an empty slot");
		}
		mill.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()));
		if (mill.canPlaceItemThroughFace(WindMillBlockEntity.CHIP_SLOT, chip, Direction.UP)) {
			helper.fail("automation could insert a second chip into an occupied slot");
		}
		helper.succeed();
	}

	/**
	 * MOD-211 — evolution consumes exactly ONE chip and carries the rest across: a world saved
	 * before the guard can still hold a stack here, and the old code handed the shared helper a
	 * bare EMPTY, destroying all 64.
	 * Mirrors: WindMillGameTest.windMill_evolutionConsumesOneChipNotTheStack
	 */
	public static void windMill_evolutionConsumesOneChipNotTheStack(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper);
		setClear(helper);
		mill.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get(), 8));
		mill.setEvolveProgressTicks(Config.windMillEvolveTicks - 1);
		drive(mill, helper, 1);
		var evolved = helper.getBlockEntity(POS, HighAltitudeWindMillBlockEntity.class);
		if (evolved == null) {
			helper.fail("mill did not evolve");
			return;
		}
		ItemStack left = evolved.getItem(WindMillBlockEntity.CHIP_SLOT);
		if (left.getCount() != 7 || !left.is(ModContent.ALIGNMENT_CHIP_DAY.get())) {
			helper.fail("evolution destroyed the chip stack: expected 7 chips left, got " + left);
		}
		helper.succeed();
	}

	/**
	 * With a night (storm) chip and a rotor installed, the evolve counter advances under open sky;
	 * once {@link Config#windMillEvolveTicks} is reached the block transforms into
	 * {@code storm_wind_mill} carrying its stored EU and rotor, consuming the chip (MOD-166 #4)
	 * and keeping the owner so production stays attributed (MOD-133). Night branch of FUN03.
	 * Mirrors: WindMillGameTest.tcWindmill001Fun05_nightChipEvolvesToStorm
	 */
	public static void tcWindmill001Fun05_nightChipEvolvesToStorm(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper); // rotor installed
		setClear(helper);
		mill.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_NIGHT.get()));
		mill.getEnergyStorage().setAmountUntracked(1500); // seed EU to verify it carries across the transform
		// MOD-133 regression: seed an owner so the test can assert evolution carries it. The evolved block
		// is created via setBlockAndUpdate (no setPlacedBy), so without the evolveInto owner-transfer the
		// evolved mill's owner would be null and its production would never reach the player's profile.
		java.util.UUID ownerId = new java.util.UUID(0x51A2B3C4D5E6F708L, 0x1122334455667788L);
		mill.setOwner(ownerId, "TestPlayer");
		mill.setEvolveProgressTicks(Config.windMillEvolveTicks - 1); // one tick short of evolution
		drive(mill, helper, 1); // the next tick trips the threshold
		// The block should have transformed — the old BE is no longer the block entity at POS.
		var evolved = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
		if (evolved == null || evolved.getType() != ModContent.STORM_WIND_MILL_BE.get()) {
			helper.fail("wind mill did not evolve into storm_wind_mill; got: " + evolved);
		}
		var evolvedMill = helper.getBlockEntity(POS, StormWindMillBlockEntity.class);
		if (evolvedMill == null) {
			helper.fail("evolved block is not a StormWindMillBlockEntity");
			return;
		}
		if (evolvedMill.getEnergyStorage().getAmount() != 1500) {
			helper.fail("evolved mill did not carry EU: expected 1500, got " + evolvedMill.getEnergyStorage().getAmount());
			return;
		}
		// MOD-166 (#4): the shared evolveInto helper also carries the rotor (slot override) and
		// consumes the chip. Pin both so a regression in the slot-overrides map of the helper
		// does not silently drop the rotor or leave the chip behind.
		if (!evolvedMill.getItem(WindMillBlockEntity.ROTOR_SLOT).is(ModContent.WINDMILL_ROTOR.get())) {
			helper.fail("evolved mill did not carry the installed rotor");
			return;
		}
		if (!evolvedMill.getItem(WindMillBlockEntity.CHIP_SLOT).isEmpty()) {
			helper.fail("evolved mill did not consume the chip slot");
			return;
		}
		// MOD-133: the evolved mill must keep the owner so its production stays attributed to the player
		// (the per-generator breakdown in the profile). Guards the evolveInto owner-transfer fix.
		if (!ownerId.equals(evolvedMill.getOwner())) {
			helper.fail("evolved mill did not carry the owner: expected " + ownerId + ", got " + evolvedMill.getOwner());
			return;
		}
		helper.succeed();
	}

	// ── MOD-189: rotor wear — the rotor is a durability component that wears out and breaks ───────────

	/**
	 * A producing T1 wind mill wears its rotor down and, once its durability is spent, breaks it:
	 * the slot empties, generation halts and the mode drops to MODE_NO_ROTOR. Config override (1 EU
	 * per durability point) makes wear fast and deterministic, plus a rotor pre-damaged to one
	 * point from death.
	 * Mirrors: WindMillGameTest.tcWindmill001Wear01_rotorWearsOutAndBreaks
	 */
	public static void tcWindmill001Wear01_rotorWearsOutAndBreaks(GameTestHelper helper) {
		int savedRate = Config.windMillRotorEuPerDamage;
		try {
			Config.windMillRotorEuPerDamage = 1; // 1 EU of production spends 1 durability point
			WindMillBlockEntity mill = placeRaised(helper); // base >= 1 → real production under open sky
			requirePositiveHeightBase(helper, RAISED_POS);
			setClear(helper);
			ItemStack rotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
			rotor.setDamageValue(rotor.getMaxDamage() - 1); // one active tick from breaking
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT, rotor);
			drive(mill, helper, Config.windMillSampleTicks + 2); // sample so rate>0 is cached, then wear
			if (!mill.getItem(WindMillBlockEntity.ROTOR_SLOT).isEmpty()) {
				helper.fail("worn-out rotor was not removed from the slot; damage="
						+ mill.getItem(WindMillBlockEntity.ROTOR_SLOT).getDamageValue()
						+ " rate=" + mill.getDataAccess().get(2));
			}
			mill.getEnergyStorage().setAmountUntracked(0);
			drive(mill, helper, Config.windMillSampleTicks + 1);
			if (mill.getEnergyStorage().getAmount() != 0) {
				helper.fail("wind mill kept generating after its rotor broke");
			}
			assertMode(helper, mill, "broken-rotor mill", WindMillBlockEntity.MODE_NO_ROTOR);
			helper.succeed();
		} finally {
			Config.windMillRotorEuPerDamage = savedRate;
		}
	}

	/**
	 * The rotor wear path is the SHARED {@code AbstractGeneratorBlockEntity#wearComponent}, so it
	 * fires on the T2 evolutions too: a producing high-altitude mill breaks a spent rotor exactly
	 * like the T1 mill. Proves the wear call site in the T2 {@code produce()}.
	 * Mirrors: WindMillGameTest.tcWindmill001Wear02_t2HighAltitudeRotorBreaks
	 */
	public static void tcWindmill001Wear02_t2HighAltitudeRotorBreaks(GameTestHelper helper) {
		int savedRate = Config.windMillRotorEuPerDamage;
		try {
			Config.windMillRotorEuPerDamage = 1;
			var mill = placeRaisedHighAltitude(helper); // base >= 1 for the T2 formula → real production
			setClear(helper);
			ItemStack rotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
			rotor.setDamageValue(rotor.getMaxDamage() - 1);
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT, rotor);
			AlaGameTestHelper.drive(mill, helper, Config.windMillSampleTicks + 2);
			if (!mill.getItem(WindMillBlockEntity.ROTOR_SLOT).isEmpty()) {
				helper.fail("high-altitude T2 rotor did not break when spent; damage="
						+ mill.getItem(WindMillBlockEntity.ROTOR_SLOT).getDamageValue()
						+ " rate=" + mill.getDataAccess().get(2));
			}
			helper.succeed();
		} finally {
			Config.windMillRotorEuPerDamage = savedRate;
		}
	}

	/**
	 * A rotor in an idle mill (produces 0 EU) does NOT wear even at the aggressive
	 * 1-EU-per-point rate: wear accrues only while the mill produces EU. Idleness is forced with
	 * {@code windMillMaxBaseEuPerTick = 0} (not the rig's altitude) so the test stays about the
	 * wear gate and is immune to future retunes of the altitude curve (MOD-347).
	 * Mirrors: WindMillGameTest.tcWindmill001Wear03_noWearWhileIdle
	 */
	public static void tcWindmill001Wear03_noWearWhileIdle(GameTestHelper helper) {
		int savedRate = Config.windMillRotorEuPerDamage;
		int savedBase = Config.windMillMaxBaseEuPerTick;
		try {
			Config.windMillRotorEuPerDamage = 1;
			Config.windMillMaxBaseEuPerTick = 0; // force rate 0 at any height (see javadoc)
			WindMillBlockEntity mill = placeWithoutRotor(helper);
			setClear(helper);
			ItemStack rotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
			int seeded = rotor.getMaxDamage() - 1;
			rotor.setDamageValue(seeded);
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT, rotor);
			drive(mill, helper, Config.windMillSampleTicks * 2 + 5);
			// Guard against the test passing for the wrong reason: if the mill were somehow producing,
			// "no wear" would be a genuine bug rather than the expected result.
			int observedRate = mill.getDataAccess().get(2);
			if (observedRate != 0) {
				helper.fail("wear-gate test needs an idle mill but the rig produced " + observedRate
						+ " EU/t [" + rigDiagnostics(helper, POS) + "]");
			}
			ItemStack after = mill.getItem(WindMillBlockEntity.ROTOR_SLOT);
			if (after.isEmpty()) {
				helper.fail("idle wind mill (rate 0) wore out its rotor — wear must only accrue while producing EU"
						+ " [" + rigDiagnostics(helper, POS) + "]");
			}
			if (after.getDamageValue() != seeded) {
				helper.fail("idle wind mill changed rotor damage from " + seeded + " to " + after.getDamageValue()
						+ "; expected no wear at rate 0");
			}
			helper.succeed();
		} finally {
			Config.windMillRotorEuPerDamage = savedRate;
			Config.windMillMaxBaseEuPerTick = savedBase;
		}
	}

	/**
	 * Evolution must NOT repair the rotor: a partially-worn rotor keeps its exact damage when the
	 * mill evolves T1 → T2 (the shared {@code evolveInto} copies the stack). A free repair on
	 * evolution would break the wear economy. Uses the low-Y region (rate 0) so no wear accrues
	 * during the single evolution tick and the damage assertion is exact.
	 * Mirrors: WindMillGameTest.tcWindmill001Wear04_wearSurvivesEvolution
	 */
	public static void tcWindmill001Wear04_wearSurvivesEvolution(GameTestHelper helper) {
		WindMillBlockEntity mill = place(helper); // rotor installed at POS (base 0 → no wear)
		setClear(helper);
		ItemStack rotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
		int worn = rotor.getMaxDamage() / 2; // half-worn
		rotor.setDamageValue(worn);
		mill.setItem(WindMillBlockEntity.ROTOR_SLOT, rotor);
		mill.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()));
		mill.setEvolveProgressTicks(Config.windMillEvolveTicks - 1); // one tick short of evolution
		drive(mill, helper, 1); // trips the transform
		var evolvedMill = helper.getBlockEntity(POS, HighAltitudeWindMillBlockEntity.class);
		if (evolvedMill == null) {
			helper.fail("wind mill did not evolve into high_altitude_wind_mill");
			return;
		}
		ItemStack carried = evolvedMill.getItem(WindMillBlockEntity.ROTOR_SLOT);
		if (!carried.is(ModContent.WINDMILL_ROTOR.get())) {
			helper.fail("evolved mill lost the rotor");
			return;
		}
		if (carried.getDamageValue() != worn) {
			helper.fail("rotor wear was reset by evolution: expected damage " + worn + ", got "
					+ carried.getDamageValue() + " — evolution must not repair the rotor");
		}
		helper.succeed();
	}

	/**
	 * Wear tracks mechanical spinning, NOT delivered EU: a mill with a FULL buffer and no
	 * downstream consumer still wears its rotor (the blades turn whether or not the EU is
	 * stored). Pins the deliberate design decision that wear is not gated on the buffer-room
	 * check.
	 * Mirrors: WindMillGameTest.tcWindmill001Wear05_wearsAtFullBufferWithNoConsumer
	 */
	public static void tcWindmill001Wear05_wearsAtFullBufferWithNoConsumer(GameTestHelper helper) {
		int savedRate = Config.windMillRotorEuPerDamage;
		try {
			Config.windMillRotorEuPerDamage = 1;
			WindMillBlockEntity mill = placeRaised(helper); // base >= 1 → rate > 0 under open sky
			requirePositiveHeightBase(helper, RAISED_POS);
			setClear(helper);
			mill.getEnergyStorage().setAmountUntracked(mill.getEnergyStorage().getCapacity()); // FULL buffer, nothing draws it
			ItemStack rotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
			rotor.setDamageValue(rotor.getMaxDamage() - 1);
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT, rotor);
			drive(mill, helper, Config.windMillSampleTicks + 2);
			if (!mill.getItem(WindMillBlockEntity.ROTOR_SLOT).isEmpty()) {
				helper.fail("rotor did not wear at a full buffer — wear must track the spinning blades, not "
						+ "delivered EU; damage=" + mill.getItem(WindMillBlockEntity.ROTOR_SLOT).getDamageValue());
			}
			helper.succeed();
		} finally {
			Config.windMillRotorEuPerDamage = savedRate;
		}
	}
}
