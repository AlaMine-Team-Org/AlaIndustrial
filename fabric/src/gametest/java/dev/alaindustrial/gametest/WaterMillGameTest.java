package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 functional suite for the water mill — the passive water-driven LV generator. Mirrors the
 * structure of {@link GeneratorGameTest} / {@link SolarPanelGameTest}: each method is one case,
 * traced via {@code @implements}, driving {@code serverTick} directly (deterministic, no waiting).
 *
 * <p>Water detection reads the world's fluid state directly ({@code level.getFluidState(...).is(WATER)}),
 * so a test only needs to {@code setBlock} vanilla water on a horizontal face and drive one tick — no
 * flow settling or block ticks required. Numbers come from {@link Config} (canon), never hard-coded.
 */
public class WaterMillGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static WaterMillBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.WATER_MILL);
		WaterMillBlockEntity be = helper.getBlockEntity(POS, WaterMillBlockEntity.class);
		if (be == null) {
			helper.fail("water mill block entity missing after placement");
		}
		return be;
	}

	private static void drive(WaterMillBlockEntity be, GameTestHelper helper, int ticks) {
		BlockPos abs = be.getBlockPos();
		for (int i = 0; i < ticks; i++) {
			BlockState state = helper.getLevel().getBlockState(abs);
			be.serverTick(helper.getLevel(), abs, state);
		}
	}

	/**
	 * @implements TC-WMILL-001-FUN01 — water on N horizontal faces yields {@code waterMillEuPerTick × N}
	 *     EU/t. Drives one clean tick each at N = 1, 2, 3, 4 and asserts the exact per-tick gain scales.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcWmill001Fun01_generatesPerWaterFace(GameTestHelper helper) {
		WaterMillBlockEntity mill = place(helper);
		Direction[] faces = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		for (int n = 1; n <= 4; n++) {
			helper.setBlock(POS.relative(faces[n - 1]), Blocks.WATER);
			mill.getEnergyStorage().amount = 0;
			drive(mill, helper, 1);
			long got = mill.getEnergyStorage().getAmount();
			long expected = Math.max(0, Math.round((long) Config.waterMillEuPerTick * n * Config.globalEuRateMultiplier));
			if (got != expected) {
				helper.fail("water on " + n + " faces: got " + got + " EU/t, expected " + expected
						+ " (waterMillEuPerTick=" + Config.waterMillEuPerTick + " × " + n + ")");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WMILL-001-NEG01 — no adjacent water → 0 EU (dry mill is idle).
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcWmill001Neg01_noWaterYieldsZero(GameTestHelper helper) {
		WaterMillBlockEntity mill = place(helper);
		mill.getEnergyStorage().amount = 0;
		drive(mill, helper, 20);
		long got = mill.getEnergyStorage().getAmount();
		if (got != 0) {
			helper.fail("dry water mill generated " + got + " EU; expected 0");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WMILL-001-NEG02 — water on the top/bottom (vertical) faces is ignored; only the four
	 *     horizontal faces count. With water only above and below, output stays 0.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcWmill001Neg02_verticalWaterIgnored(GameTestHelper helper) {
		WaterMillBlockEntity mill = place(helper);
		helper.setBlock(POS.above(), Blocks.WATER);
		helper.setBlock(POS.below(), Blocks.WATER);
		mill.getEnergyStorage().amount = 0;
		drive(mill, helper, 20);
		long got = mill.getEnergyStorage().getAmount();
		if (got != 0) {
			helper.fail("vertical water should not drive the mill: got " + got + " EU; expected 0");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WMILL-001-PRF01 — buffer caps at {@code Config.waterMillBuffer} even fully surrounded;
	 *     excess EU is discarded (use-it-or-lose-it).
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcWmill001Prf01_bufferCapsAtMax(GameTestHelper helper) {
		WaterMillBlockEntity mill = place(helper);
		for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			helper.setBlock(POS.relative(d), Blocks.WATER);
		}
		mill.getEnergyStorage().amount = Config.waterMillBuffer;
		drive(mill, helper, 20);
		long got = mill.getEnergyStorage().getAmount();
		if (got != Config.waterMillBuffer) {
			helper.fail("buffer changed from cap: expected " + Config.waterMillBuffer + " got " + got);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WMILL-001-PER01 — the stored EU buffer survives an NBT save/load round-trip (energy
	 *     persists via the base MachineBlockEntity; the mill adds no state of its own).
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcWmill001Per01_energySurvivesNbtRoundTrip(GameTestHelper helper) {
		WaterMillBlockEntity mill = place(helper);
		helper.setBlock(POS.relative(Direction.NORTH), Blocks.WATER);
		helper.setBlock(POS.relative(Direction.SOUTH), Blocks.WATER);
		drive(mill, helper, 5);
		long energy0 = mill.getEnergyStorage().getAmount();
		if (energy0 <= 0) {
			helper.fail("expected the mill to have accumulated EU before the round-trip");
		}

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = mill.saveCustomOnly(registries);
		WaterMillBlockEntity restored = new WaterMillBlockEntity(mill.getBlockPos(),
				helper.getLevel().getBlockState(mill.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != energy0) {
			helper.fail("NBT round-trip lost energy: " + energy0 + " -> " + restored.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WMILL-001-CON01 — the mill pushes EU into a directly-adjacent BatteryBox (no cable).
	 *     Water on one horizontal face keeps it producing; the battery on another face receives EU.
	 * @covers R-NRG-03, R-CON-01
	 */
	@GameTest
	public void tcWmill001Con01_pushesToAdjacentBattery(GameTestHelper helper) {
		WaterMillBlockEntity mill = place(helper);
		helper.setBlock(POS.relative(Direction.NORTH), Blocks.WATER); // keep it generating
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
			helper.fail("adjacent battery_box received no EU from the water mill");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WMILL-001-PHY01 — the FACING (front) face emits no EU; the other five faces are
	 *     OUT-only (generator face contract, R-NRG-03). FACING defaults to NORTH on a freshly-set block.
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcWmill001Phy01_facingFaceInert(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.WATER_MILL.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		EnergyStorage front = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.NORTH);
		if (front != null && front.supportsExtraction()) {
			helper.fail("water mill FACING (front) face must not emit EU");
		}
		for (Direction d : new Direction[]{
				Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
			EnergyStorage p = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), d);
			if (p == null || !p.supportsExtraction()) {
				helper.fail("water mill face " + d + " must emit EU");
			}
		}
		helper.succeed();
	}
}
