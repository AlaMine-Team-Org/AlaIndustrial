package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ExtractorBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * L2 persistence suite (R-PER-01/05): NBT save/load round-trip preserves block-entity state
 * (energy, progress, inventory, fluid tank, evolution) across blocks beyond generator/battery_box.
 * Migrated from the legacy IndustrializationSelfTest PERSISTENCE check.
 */
public class PersistenceGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * @implements R-PER-01 — macerator NBT round-trip preserves energy + progress + input count.
	 * @covers R-PER-01
	 *
	 * <p>Mirrors the legacy PERSISTENCE pattern: set state on a placed BE, {@code saveCustomOnly},
	 * build a fresh BE of the same type, {@code loadWithComponents}, assert each field survived.
	 * Pure NBT round-trip — no world ticking, so no sky/structure needs.
	 */
	@GameTest
	public void rPer01_maceratorNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.MACERATOR);
		MaceratorBlockEntity src = helper.getBlockEntity(POS, MaceratorBlockEntity.class);

		long energy0 = 1234L;
		int progress0 = 7;
		src.getEnergyStorage().amount = energy0;
		// setItem() before setting progress: setItem() on an input-slot item change resets progress to 0
		// (D-SWAP, R-NRG-10), so the input must be placed first for progress0 to stick.
		src.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 3));
		src.getDataAccess().set(2, progress0); // index 2 == progress
		int input0 = src.getItem(MaceratorBlockEntity.INPUT_SLOT).getCount();

		CompoundTag tag = src.saveCustomOnly(registries);
		MaceratorBlockEntity restored = new MaceratorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		int progress1 = restored.getDataAccess().get(2);
		int input1 = restored.getItem(MaceratorBlockEntity.INPUT_SLOT).getCount();
		if (energy0 != energy1 || progress0 != progress1 || input0 != input1) {
			helper.fail("macerator round-trip mismatch: energy " + energy0 + "->" + energy1
					+ " progress " + progress0 + "->" + progress1 + " input " + input0 + "->" + input1);
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 — electric furnace NBT round-trip preserves energy + progress + input count.
	 * @covers R-PER-01
	 */
	@GameTest
	public void rPer01_furnaceNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.ELECTRIC_FURNACE);
		ElectricFurnaceBlockEntity src = helper.getBlockEntity(POS, ElectricFurnaceBlockEntity.class);

		long energy0 = 5000L;
		int progress0 = 11;
		src.getEnergyStorage().amount = energy0;
		// setItem() before setting progress: setItem() on an input-slot item change resets progress to 0
		// (D-SWAP, R-NRG-10), so the input must be placed first for progress0 to stick.
		src.setItem(ElectricFurnaceBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 4));
		src.getDataAccess().set(2, progress0); // index 2 == progress
		int input0 = src.getItem(ElectricFurnaceBlockEntity.INPUT_SLOT).getCount();

		CompoundTag tag = src.saveCustomOnly(registries);
		ElectricFurnaceBlockEntity restored = new ElectricFurnaceBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		int progress1 = restored.getDataAccess().get(2);
		int input1 = restored.getItem(ElectricFurnaceBlockEntity.INPUT_SLOT).getCount();
		if (energy0 != energy1 || progress0 != progress1 || input0 != input1) {
			helper.fail("furnace round-trip mismatch: energy " + energy0 + "->" + energy1
					+ " progress " + progress0 + "->" + progress1 + " input " + input0 + "->" + input1);
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 — geothermal generator NBT round-trip preserves energy + fluidTank lava amount.
	 * @covers R-PER-01
	 *
	 * <p>The tank persists amount only (variant implicit); {@code loadAdditional} restores the LAVA
	 * variant iff amount &gt; 0, so a non-zero lava charge is set to exercise the restore path.
	 */
	@GameTest
	public void rPer01_geothermalFluidNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.GEOTHERMAL_GENERATOR);
		GeothermalGeneratorBlockEntity src = helper.getBlockEntity(POS, GeothermalGeneratorBlockEntity.class);

		long energy0 = 800L;
		long lava0 = FluidAmounts.BUCKET * 3;
		src.getEnergyStorage().amount = energy0;
		src.fluidTank.amount = lava0;
		src.fluidTank.fluid = dev.alaindustrial.core.FluidHolder.of(Fluids.LAVA);

		CompoundTag tag = src.saveCustomOnly(registries);
		GeothermalGeneratorBlockEntity restored = new GeothermalGeneratorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		long lava1 = restored.fluidTank.amount;
		boolean lavaVariant = restored.fluidTank.fluid.is(Fluids.LAVA);
		if (energy0 != energy1 || lava0 != lava1 || !lavaVariant) {
			helper.fail("geothermal round-trip mismatch: energy " + energy0 + "->" + energy1
					+ " lava " + lava0 + "->" + lava1 + " lavaVariant=" + lavaVariant);
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 — solar panel NBT round-trip preserves evolution progress + energy.
	 * @covers R-PER-01
	 *
	 * <p>{@code evolveProgress} is private; it is set/read through the panel's six-wide data access
	 * (index 4) — the same bridge persisted via {@code saveAdditional}/{@code loadAdditional}.
	 */
	@GameTest
	public void rPer01_solarEvolveNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.SOLAR_PANEL);
		SolarPanelBlockEntity src = helper.getBlockEntity(POS, SolarPanelBlockEntity.class);

		long energy0 = 600L;
		int evolve0 = 1500;
		src.getEnergyStorage().amount = energy0;
		src.setEvolveProgressTicks(evolve0); // raw counter; channel 4 syncs a permille projection

		CompoundTag tag = src.saveCustomOnly(registries);
		SolarPanelBlockEntity restored = new SolarPanelBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		int evolve1 = restored.getEvolveProgressTicks();
		if (energy0 != energy1 || evolve0 != evolve1) {
			helper.fail("solar round-trip mismatch: energy " + energy0 + "->" + energy1
					+ " evolveProgress " + evolve0 + "->" + evolve1);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-MACH-003-PER01 (= TC-MACH-001-PER01, compressor breakout) — compressor NBT
	 *     round-trip preserves energy + progress + input count.
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcMach003Per01_compressorNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.COMPRESSOR);
		CompressorBlockEntity src = helper.getBlockEntity(POS, CompressorBlockEntity.class);

		long energy0 = 300L;
		int progress0 = 42;
		src.getEnergyStorage().amount = energy0;
		// setItem() before setting progress: setItem() on an input-slot item change resets progress to 0
		// (D-SWAP, R-NRG-10), so the input must be placed first for progress0 to stick.
		src.setItem(CompressorBlockEntity.INPUT_SLOT, new ItemStack(Items.CLAY_BALL, 3));
		src.getDataAccess().set(2, progress0); // index 2 == progress
		int input0 = src.getItem(CompressorBlockEntity.INPUT_SLOT).getCount();

		CompoundTag tag = src.saveCustomOnly(registries);
		CompressorBlockEntity restored = new CompressorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		int progress1 = restored.getDataAccess().get(2);
		int input1 = restored.getItem(CompressorBlockEntity.INPUT_SLOT).getCount();
		if (energy0 != energy1 || progress0 != progress1 || input0 != input1) {
			helper.fail("compressor round-trip mismatch: energy " + energy0 + "->" + energy1
					+ " progress " + progress0 + "->" + progress1 + " input " + input0 + "->" + input1);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-MACH-004-PER01 (= TC-MACH-001-PER01, extractor breakout) — extractor NBT
	 *     round-trip preserves energy + progress + input count.
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcMach004Per01_extractorNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.EXTRACTOR);
		ExtractorBlockEntity src = helper.getBlockEntity(POS, ExtractorBlockEntity.class);

		long energy0 = 400L;
		int progress0 = 30;
		src.getEnergyStorage().amount = energy0;
		// setItem() before setting progress: setItem() on an input-slot item change resets progress to 0
		// (D-SWAP, R-NRG-10), so the input must be placed first for progress0 to stick.
		src.setItem(ExtractorBlockEntity.INPUT_SLOT, new ItemStack(Items.BLAZE_ROD, 2));
		src.getDataAccess().set(2, progress0); // index 2 == progress
		int input0 = src.getItem(ExtractorBlockEntity.INPUT_SLOT).getCount();

		CompoundTag tag = src.saveCustomOnly(registries);
		ExtractorBlockEntity restored = new ExtractorBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		int progress1 = restored.getDataAccess().get(2);
		int input1 = restored.getItem(ExtractorBlockEntity.INPUT_SLOT).getCount();
		if (energy0 != energy1 || progress0 != progress1 || input0 != input1) {
			helper.fail("extractor round-trip mismatch: energy " + energy0 + "->" + energy1
					+ " progress " + progress0 + "->" + progress1 + " input " + input0 + "->" + input1);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-EFURN-001-PER01 (= TC-MACH-002/003/004-PER02, freeze/resume) — electric furnace
	 *     loses power mid-operation: progress freezes (not reset), then resumes from the same point and
	 *     finishes once power returns.
	 * @covers R-NRG-10, R-PER-01
	 *
	 * <p>Live-tick scenario (not a pure NBT round-trip): drives {@code serverTick} directly, mirroring
	 * {@code MachineGameTest#drive}. No power → progress must not advance (frozen); on power's return the
	 * same progress value resumes and the operation completes to the expected product.
	 */
	@GameTest
	public void tcEFurn001Per01_furnaceFreezeThenResume(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(POS, ModBlocks.ELECTRIC_FURNACE);
		ElectricFurnaceBlockEntity be = helper.getBlockEntity(POS, ElectricFurnaceBlockEntity.class);
		BlockPos abs = be.getBlockPos();

		int ampleEu = 8000; // > any single op's E_op; set directly (bypasses cap)
		be.getEnergyStorage().amount = ampleEu;
		be.setItem(ElectricFurnaceBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 4));

		// Run partway (~50%), then cut power.
		int halfTicks = Config.scaledDuration(Config.electricFurnaceDuration) / 2;
		for (int i = 0; i < halfTicks; i++) {
			be.serverTick(level, abs, level.getBlockState(abs));
		}
		int frozenProgress = be.getDataAccess().get(2);
		if (frozenProgress <= 0) {
			helper.fail("furnace made no progress before power loss (frozenProgress=" + frozenProgress + ")");
		}

		be.getEnergyStorage().amount = 0;
		for (int i = 0; i < 200; i++) {
			be.serverTick(level, abs, level.getBlockState(abs));
		}
		int stillFrozen = be.getDataAccess().get(2);
		if (stillFrozen != frozenProgress) {
			helper.fail("furnace progress moved while unpowered: " + frozenProgress + "->" + stillFrozen);
		}
		if (!be.getItem(ElectricFurnaceBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("furnace produced output while unpowered");
		}

		// Power returns: resumes from the SAME progress (not reset to 0) and finishes.
		be.getEnergyStorage().amount = ampleEu;
		for (int i = 0; i < 400; i++) {
			be.serverTick(level, abs, level.getBlockState(abs));
		}
		ItemStack out = be.getItem(ElectricFurnaceBlockEntity.OUTPUT_SLOT);
		if (out.isEmpty() || !out.is(Items.IRON_INGOT)) {
			helper.fail("furnace did not resume/finish after power returned: output=" + out);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-PER01 — pump tank NBT round-trip preserves a non-zero lava amount and
	 *     restores the LAVA variant (not blank).
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcPump001Per01_pumpTankNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.PUMP);
		PumpBlockEntity src = helper.getBlockEntity(POS, PumpBlockEntity.class);

		long lava0 = FluidAmounts.BUCKET * 2;
		src.fluidTank.amount = lava0;
		src.fluidTank.fluid = dev.alaindustrial.core.FluidHolder.of(Fluids.LAVA);

		CompoundTag tag = src.saveCustomOnly(registries);
		PumpBlockEntity restored = new PumpBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long lava1 = restored.fluidTank.amount;
		boolean lavaVariant = restored.fluidTank.fluid.is(Fluids.LAVA);
		if (lava0 != lava1 || !lavaVariant) {
			helper.fail("pump tank round-trip mismatch: lava " + lava0 + "->" + lava1
					+ " lavaVariant=" + lavaVariant);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-PUMP-001-PER02 — pump tank NBT round-trip with an empty tank restores
	 *     {@code amount}=0 and a blank variant (no phantom lava reappears at zero amount).
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcPump001Per02_pumpTankEmptyNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModBlocks.PUMP);
		PumpBlockEntity src = helper.getBlockEntity(POS, PumpBlockEntity.class);
		// Tank starts empty (amount=0, blank fluid) — no action needed beyond placement.

		CompoundTag tag = src.saveCustomOnly(registries);
		PumpBlockEntity restored = new PumpBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long lava1 = restored.fluidTank.amount;
		boolean isBlank = restored.fluidTank.fluid.isEmpty();
		if (lava1 != 0L || !isBlank) {
			helper.fail("pump empty-tank round-trip mismatch: amount=" + lava1 + " blank=" + isBlank);
		}
		helper.succeed();
	}
}
