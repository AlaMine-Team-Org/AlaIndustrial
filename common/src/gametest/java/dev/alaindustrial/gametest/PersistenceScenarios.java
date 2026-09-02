package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.BlockEntityDataMigrations;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ExtractorBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModContent;
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
public final class PersistenceScenarios {

	private PersistenceScenarios() {}


	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * @implements R-PER-01 — macerator NBT round-trip preserves energy + progress + input count.
	 * @covers R-PER-01
	 *
	 * <p>Mirrors the legacy PERSISTENCE pattern: set state on a placed BE, {@code saveCustomOnly},
	 * build a fresh BE of the same type, {@code loadWithComponents}, assert each field survived.
	 * Pure NBT round-trip — no world ticking, so no sky/structure needs.
	 */
	public static void rPer01_maceratorNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.MACERATOR.get());
		MaceratorBlockEntity src = helper.getBlockEntity(POS, MaceratorBlockEntity.class);

		// Below machineBuffer (800): a buffer cannot hold more than its capacity, and since
		// MOD-400 setAmountUntracked clamps instead of accepting an impossible charge.
		long energy0 = 734L;
		int progress0 = 7;
		src.getEnergyStorage().setAmountUntracked(energy0);
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
	public static void rPer01_furnaceNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.ELECTRIC_FURNACE.get());
		ElectricFurnaceBlockEntity src = helper.getBlockEntity(POS, ElectricFurnaceBlockEntity.class);

		long energy0 = 512L; // below machineBuffer (800) — see the macerator case above
		int progress0 = 11;
		src.getEnergyStorage().setAmountUntracked(energy0);
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
	public static void rPer01_geothermalFluidNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.GEOTHERMAL_GENERATOR.get());
		GeothermalGeneratorBlockEntity src = helper.getBlockEntity(POS, GeothermalGeneratorBlockEntity.class);

		long energy0 = 800L;
		long lava0 = FluidAmounts.BUCKET * 3;
		src.getEnergyStorage().setAmountUntracked(energy0);
		src.fluidTank.amount = lava0;
		src.fluidTank.fluid = dev.alaindustrial.core.fluid.FluidHolder.of(Fluids.LAVA);

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
	public static void rPer01_solarEvolveNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.SOLAR_PANEL.get());
		SolarPanelBlockEntity src = helper.getBlockEntity(POS, SolarPanelBlockEntity.class);

		long energy0 = 600L;
		int evolve0 = 1500;
		src.getEnergyStorage().setAmountUntracked(energy0);
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
	public static void tcMach003Per01_compressorNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.COMPRESSOR.get());
		CompressorBlockEntity src = helper.getBlockEntity(POS, CompressorBlockEntity.class);

		long energy0 = 300L;
		int progress0 = 42;
		src.getEnergyStorage().setAmountUntracked(energy0);
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
	public static void tcMach004Per01_extractorNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.EXTRACTOR.get());
		ExtractorBlockEntity src = helper.getBlockEntity(POS, ExtractorBlockEntity.class);

		long energy0 = 400L;
		int progress0 = 30;
		src.getEnergyStorage().setAmountUntracked(energy0);
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
	public static void tcEFurn001Per01_furnaceFreezeThenResume(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(POS, ModContent.ELECTRIC_FURNACE.get());
		ElectricFurnaceBlockEntity be = helper.getBlockEntity(POS, ElectricFurnaceBlockEntity.class);
		BlockPos abs = be.getBlockPos();

		int ampleEu = 8000; // > any single op's E_op; set directly (bypasses cap)
		be.getEnergyStorage().setAmountUntracked(ampleEu);
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

		be.getEnergyStorage().setAmountUntracked(0);
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
		be.getEnergyStorage().setAmountUntracked(ampleEu);
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
	public static void tcPump001Per01_pumpTankNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.PUMP.get());
		PumpBlockEntity src = helper.getBlockEntity(POS, PumpBlockEntity.class);

		long lava0 = FluidAmounts.BUCKET * 2;
		src.fluidTank.amount = lava0;
		src.fluidTank.fluid = dev.alaindustrial.core.fluid.FluidHolder.of(Fluids.LAVA);

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
	public static void tcPump001Per02_pumpTankEmptyNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.PUMP.get());
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

	/**
	 * @implements TC-CABLE-001-PER01 — cable NBT round-trip preserves the live segment buffer.
	 *     The cable's persistence path is a SLIM ONE: since MOD-166 (#8) it overrides
	 *     {@code saveAdditional}/{@code loadAdditional} to write ONLY the {@code "Energy"} key,
	 *     skipping the machine-path {@code Progress}/{@code MaxProgress}/{@code items} keys that are
	 *     always empty/zero on a transport segment. This test pins that the slim path round-trips a
	 *     non-zero cable buffer cleanly (the canonical case — a charged segment between a generator
	 *     and a consumer carries cableBuffer EU in transit).
	 * @covers R-PER-01
	 */
	public static void tcCable001Per01_bufferNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.COPPER_CABLE.get());
		CableBlockEntity src = helper.getBlockEntity(POS, CableBlockEntity.class);

		long energy0 = 7L; // a partially-filled segment (cableBuffer = 12)
		src.getEnergyStorage().setAmountUntracked(energy0);

		CompoundTag tag = src.saveCustomOnly(registries);
		CableBlockEntity restored = new CableBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		if (energy0 != energy1) {
			helper.fail("cable round-trip mismatch: energy " + energy0 + "->" + energy1);
		}
		// Slim path means the tag must NOT carry the machine-path keys Progress/MaxProgress
		// (cable progress is always 0 — the slim path correctly omits them).
		if (tag.contains("Progress") || tag.contains("MaxProgress")) {
			helper.fail("cable slim NBT must not carry Progress/MaxProgress, but tag = " + tag);
		}
		if (!tag.contains("Energy")) {
			helper.fail("cable slim NBT must carry the Energy key, but tag = " + tag);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-CABLE-001-PER02 — legacy cable NBT (with Progress/MaxProgress keys) loads cleanly.
	 *     A player who saved the world before MOD-166 (#8) has cables whose NBT carries
	 *     {@code Progress}/{@code MaxProgress} from the old machine-path persistence. The slim
	 *     {@code loadAdditional} no longer reads those keys — this test injects NON-ZERO legacy values
	 *     and pins that loading such a tag does not throw, the buffer round-trips, and the keys are
	 *     actually dropped: a re-save carries {@code Energy} and neither legacy key.
	 * @covers R-PER-01
	 */
	public static void tcCable001Per02_legacyMachineKeysIgnoredOnLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		helper.setBlock(POS, ModContent.COPPER_CABLE.get());
		CableBlockEntity src = helper.getBlockEntity(POS, CableBlockEntity.class);

		// Save a real tag (slim path), then inject the legacy keys by hand.
		src.getEnergyStorage().setAmountUntracked(5L);
		CompoundTag tag = src.saveCustomOnly(registries);
		// Inject a NON-ZERO legacy progress: a slim load must IGNORE it (progress stays 0). If a future
		// change reverts the slim loadAdditional back to reading these keys, restored progress becomes 7
		// and this test fails — pinning that the keys are actually dropped, not merely absent.
		tag.putInt("Progress", 7);
		tag.putInt("MaxProgress", 200);

		CableBlockEntity restored = new CableBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		long energy1 = restored.getEnergyStorage().getAmount();
		if (energy1 != 5L) {
			helper.fail("legacy cable load mismatch: energy 5->" + energy1);
		}
		// Since MOD-400 the cable has no progress to read back: it extends EnergyBlockEntity, which has
		// no `progress` field and no dataAccess at all, so "the legacy key was ignored" is now a
		// STRUCTURAL fact rather than a value to assert — the old check read index 2 of a data bridge
		// that no longer exists on this class. What remains checkable, and is what a player's save
		// actually depends on, is that the injected keys neither throw on load nor come back out on
		// save: a re-save must produce the slim tag again, with the legacy keys gone for good.
		CompoundTag resaved = restored.saveCustomOnly(registries);
		if (resaved.contains("Progress") || resaved.contains("MaxProgress")) {
			helper.fail("legacy cable keys survived a load/save round-trip: " + resaved
					+ " — a transport segment must not carry machine progress in its NBT");
		}
		if (!resaved.contains("Energy")) {
			helper.fail("re-saved cable tag lost the Energy key: " + resaved);
		}
		helper.succeed();
	}

	// -- MOD-556: the tank now saves itself; the bytes on disk must not have moved ----------------

	/** Second/third probe positions inside the 8^3 rig, so each machine gets its own block. */
	private static final BlockPos POS_B = new BlockPos(3, 2, 1);
	private static final BlockPos POS_C = new BlockPos(5, 2, 1);

	/**
	 * @implements R-PER-01 -- every machine tank still writes the exact key pair it wrote before
	 *     MOD-556 moved the code into {@code FluidTank}: {@code <prefix>Mb} (a long, in mB) and
	 *     {@code <prefix>Fluid} (the fluid's registry id, {@code ""} when empty). Both keys are
	 *     written even for an empty tank, which is what the six copies did.
	 * @covers R-PER-01
	 *
	 * <p>The key literals here are hand-written on purpose. Deriving them from the production code
	 * would make this test agree with any rename, which is the one thing it exists to refuse: a
	 * renamed key does not fail to load, it loads as "absent" and silently empties a player's machine.
	 * The single-tank shape ({@code FluidTankMb}), the machine-specific one ({@code SolutionMb}) and
	 * the multi-tank prefixes ({@code OilMb}/{@code DieselMb}/{@code FuelOilMb}) are all covered,
	 * because a prefix is exactly where a consolidation slips.
	 */
	public static void mod556_tankKeysUnchangedAfterSelfSave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();

		// 1. Single tank, a MOD fluid: the polymerizer holds oil, so the id is not a vanilla one.
		helper.setBlock(POS, ModContent.POLYMERIZER.get());
		PolymerizerBlockEntity poly = helper.getBlockEntity(POS, PolymerizerBlockEntity.class);
		poly.fluidTank.fluid = FluidHolder.of(ModContent.OIL.get());
		poly.fluidTank.amount = 3000L;
		CompoundTag polyTag = poly.saveCustomOnly(registries);
		if (polyTag.getLongOr("FluidTankMb", -1L) != 3000L
				|| !"alaindustrial:oil".equals(polyTag.getStringOr("FluidTankFluid", "<absent>"))) {
			helper.fail("polymerizer tank no longer writes FluidTankMb/FluidTankFluid: " + polyTag);
			return;
		}

		// 2. An EMPTY tank still writes BOTH keys, the id as the empty string. A tank that stopped
		// writing its id when empty would look harmless and would leave the previous id in place on any
		// reader that treats a missing key as "keep what you had".
		poly.fluidTank.fluid = FluidHolder.EMPTY;
		poly.fluidTank.amount = 0L;
		CompoundTag emptyTag = poly.saveCustomOnly(registries);
		if (emptyTag.getLongOr("FluidTankMb", -1L) != 0L
				|| !"".equals(emptyTag.getStringOr("FluidTankFluid", "<absent>"))) {
			helper.fail("an empty polymerizer tank must still write both keys: " + emptyTag);
			return;
		}

		// 3. A machine whose prefix is not "FluidTank": the sprinkler's solution tank.
		helper.setBlock(POS_B, ModContent.SPRINKLER.get());
		SprinklerBlockEntity sprinkler = helper.getBlockEntity(POS_B, SprinklerBlockEntity.class);
		long solution = Math.min(500L, sprinkler.tank.capacity);
		sprinkler.tank.fluid = FluidHolder.of(Fluids.WATER);
		sprinkler.tank.amount = solution;
		CompoundTag sprinklerTag = sprinkler.saveCustomOnly(registries);
		if (sprinklerTag.getLongOr("SolutionMb", -1L) != solution
				|| !"minecraft:water".equals(sprinklerTag.getStringOr("SolutionFluid", "<absent>"))) {
			helper.fail("sprinkler tank no longer writes SolutionMb/SolutionFluid: " + sprinklerTag);
			return;
		}

		// 4. Three tanks behind three prefixes: the distillation column.
		helper.setBlock(POS_C, ModContent.DISTILLATION_COLUMN.get());
		DistillationColumnBlockEntity column =
				helper.getBlockEntity(POS_C, DistillationColumnBlockEntity.class);
		column.oilTank.fluid = FluidHolder.of(ModContent.OIL.get());
		column.oilTank.amount = 1200L;
		// Water and lava stand in for diesel/fuel oil: any registered fluid proves the id path, and a
		// vanilla one lets the expected string be a hand-written literal rather than a lookup.
		column.dieselTank.fluid = FluidHolder.of(Fluids.WATER);
		column.dieselTank.amount = 340L;
		column.fuelOilTank.fluid = FluidHolder.of(Fluids.LAVA);
		column.fuelOilTank.amount = 7L;
		CompoundTag columnTag = column.saveCustomOnly(registries);
		if (columnTag.getLongOr("OilMb", -1L) != 1200L
				|| !"alaindustrial:oil".equals(columnTag.getStringOr("OilFluid", "<absent>"))
				|| columnTag.getLongOr("DieselMb", -1L) != 340L
				|| !"minecraft:water".equals(columnTag.getStringOr("DieselFluid", "<absent>"))
				|| columnTag.getLongOr("FuelOilMb", -1L) != 7L
				|| !"minecraft:lava".equals(columnTag.getStringOr("FuelOilFluid", "<absent>"))) {
			helper.fail("distillation column tank prefixes drifted: " + columnTag);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 -- a save written by a build from BEFORE MOD-556 still loads. The tags here
	 *     are hand-built in the old shape and handed straight to {@code loadWithComponents}; nothing in
	 *     them came from the current save path, so this is the "an existing world opens" guarantee
	 *     rather than a round trip of the new code with itself.
	 * @covers R-PER-01
	 *
	 * <p>Also pins the two pump fallbacks that used to live in its own {@code holderFromKey}: the
	 * droplet-valued {@code "FluidTank"} key of Fabric v0.1.0, and the pre-MOD-099 bare {@code "lava"}
	 * spelling -- the latter has no branch of its own any more, because {@code Identifier.tryParse}
	 * gives a namespace-less path the {@code minecraft} default. If that ever stops being true, this
	 * fails instead of quietly emptying a pump.
	 */
	public static void mod556_preRefactorSavesStillLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);

		// 1. Distillation column, three prefixes, written the way the old code wrote them.
		helper.setBlock(POS, ModContent.DISTILLATION_COLUMN.get());
		CompoundTag columnTag = new CompoundTag();
		columnTag.putLong("OilMb", 2500L);
		columnTag.putString("OilFluid", "alaindustrial:oil");
		columnTag.putLong("DieselMb", 800L);
		columnTag.putString("DieselFluid", "minecraft:water");
		columnTag.putLong("FuelOilMb", 0L);
		columnTag.putString("FuelOilFluid", "");
		DistillationColumnBlockEntity column =
				new DistillationColumnBlockEntity(abs, level.getBlockState(abs));
		column.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, columnTag));
		if (column.oilTank.amount != 2500L || !column.oilTank.fluid.is(ModContent.OIL.get())
				|| column.dieselTank.amount != 800L || !column.dieselTank.fluid.is(Fluids.WATER)
				|| column.fuelOilTank.amount != 0L || !column.fuelOilTank.fluid.isEmpty()) {
			helper.fail("a pre-MOD-556 column save did not come back: oil=" + column.oilTank.amount
					+ " diesel=" + column.dieselTank.amount + " fuelOil=" + column.fuelOilTank.amount);
			return;
		}

		// 2. Galvanic bath: a positive amount whose fluid id no longer resolves (its mod was removed)
		// drops the contents rather than keeping a phantom amount -- the historic invariant, both ways.
		helper.setBlock(POS_B, ModContent.GALVANIC_BATH.get());
		BlockPos absB = helper.absolutePos(POS_B);
		CompoundTag bathTag = new CompoundTag();
		bathTag.putLong("FluidTankMb", 4000L);
		bathTag.putString("FluidTankFluid", "somemod:unobtainium");
		GalvanicBathBlockEntity bath = new GalvanicBathBlockEntity(absB, level.getBlockState(absB));
		bath.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, bathTag));
		if (bath.fluidTank.amount != 0L || !bath.fluidTank.fluid.isEmpty()) {
			helper.fail("an unresolvable fluid id must empty the tank, got amount="
					+ bath.fluidTank.amount + " fluid=" + bath.fluidTank.fluid);
			return;
		}

		// 3. Pump, Fabric v0.1.0: droplet-valued "FluidTank", no fluid id at all -> 2 buckets of lava.
		helper.setBlock(POS_C, ModContent.PUMP.get());
		BlockPos absC = helper.absolutePos(POS_C);
		CompoundTag dropletTag = new CompoundTag();
		dropletTag.putLong("FluidTank", FluidAmounts.BUCKET * 2 * FluidAmounts.FABRIC_DROPLETS_PER_MB);
		PumpBlockEntity dropletPump = new PumpBlockEntity(absC, level.getBlockState(absC));
		dropletPump.loadWithComponents(
				TagValueInput.create(ProblemReporter.DISCARDING, registries, dropletTag));
		if (dropletPump.fluidTank.amount != FluidAmounts.BUCKET * 2
				|| !dropletPump.fluidTank.fluid.is(Fluids.LAVA)) {
			helper.fail("legacy droplet pump save lost its lava: amount=" + dropletPump.fluidTank.amount
					+ " fluid=" + dropletPump.fluidTank.fluid);
			return;
		}

		// 4. Pump, pre-MOD-099: the bare "lava" spelling, no namespace.
		CompoundTag bareTag = new CompoundTag();
		bareTag.putLong("FluidTankMb", 1000L);
		bareTag.putString("FluidTankFluid", "lava");
		PumpBlockEntity barePump = new PumpBlockEntity(absC, level.getBlockState(absC));
		barePump.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, bareTag));
		if (barePump.fluidTank.amount != 1000L || !barePump.fluidTank.fluid.is(Fluids.LAVA)) {
			helper.fail("the bare lava spelling no longer resolves: amount="
					+ barePump.fluidTank.amount + " fluid=" + barePump.fluidTank.fluid);
			return;
		}
		helper.succeed();
	}

	// -- MOD-556: the save-format version and the ladder it starts ---------------------------------

	/**
	 * @implements R-PER-01 -- the block-entity format version and the migration ladder agree, and the
	 *     version actually gates the ladder: a save that declares version 0 is repaired, one that
	 *     declares the current version is left alone.
	 * @covers R-PER-01
	 *
	 * <p>The shape half is the block-entity twin of {@code ConfigSchemaTest}'s ladder guard, and it
	 * lives here rather than in the L1 suite for a concrete reason: the ladder's rungs are
	 * {@code Consumer<EnergyBlockEntity>} lambdas, so merely reading the list runs a
	 * {@code LambdaMetafactory} link that resolves a block-entity method handle -- and {@code :common}
	 * has no Minecraft jar on its test classpath. A lane with Minecraft is the only place this
	 * assertion can honestly run.
	 *
	 * <p>The behaviour half is what protects a player: without it the numbers could agree while nothing
	 * called the ladder at all.
	 */
	public static void mod556_dataVersionMatchesTheLadder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();

		// 1. One rung per version hop, ascending and gapless. A bump with no rung leaves every old save
		// unconverted; a rung with no bump means it never runs. Both fail here instead of in a world.
		if (BlockEntityDataMigrations.stepCount() != BlockEntityDataMigrations.DATA_VERSION) {
			helper.fail("the save-format ladder has " + BlockEntityDataMigrations.stepCount()
					+ " rung(s) but DATA_VERSION is " + BlockEntityDataMigrations.DATA_VERSION
					+ " — every hop 0->1, 1->2, ... needs exactly one rung");
			return;
		}
		for (int i = 0; i < BlockEntityDataMigrations.stepCount(); i++) {
			if (BlockEntityDataMigrations.stepFromVersion(i) != i) {
				helper.fail("rung " + i + " converts from version "
						+ BlockEntityDataMigrations.stepFromVersion(i)
						+ "; the ladder is walked in list order and must be ascending and gapless");
				return;
			}
		}

		// 2. The save path stamps the version. Everything below depends on it being written at all.
		helper.setBlock(POS, ModContent.BATTERY_BOX.get());
		BlockPos abs = helper.absolutePos(POS);
		BatteryBoxBlockEntity box = helper.getBlockEntity(POS, BatteryBoxBlockEntity.class);
		// An item with no EU buffer in the discharge slot is the pre-MOD-083 tell the rung looks for:
		// back then that slot did not exist and the first upgrade chip lived there.
		box.setItem(BatteryBoxBlockEntity.DISCHARGE_SLOT, new ItemStack(Items.REDSTONE));
		CompoundTag current = box.saveCustomOnly(registries);
		if (current.getIntOr(BlockEntityDataMigrations.DATA_VERSION_KEY, -1)
				!= BlockEntityDataMigrations.DATA_VERSION) {
			helper.fail("a saved block entity does not carry its format version: " + current);
			return;
		}

		// 3. A tag with NO version key is version 0 and gets repaired: the run shifts one slot up.
		CompoundTag legacy = box.saveCustomOnly(registries);
		legacy.remove(BlockEntityDataMigrations.DATA_VERSION_KEY);
		BatteryBoxBlockEntity migrated = new BatteryBoxBlockEntity(abs, level.getBlockState(abs));
		migrated.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, legacy));
		if (!migrated.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT).isEmpty()
				|| !migrated.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT + 1).is(Items.REDSTONE)) {
			helper.fail("a versionless battery box save was not migrated: discharge="
					+ migrated.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT) + " next="
					+ migrated.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT + 1));
			return;
		}

		// 4. Running the rung twice must not shift anything a second time — an older jar of this mod
		// re-saves without the version key, so already-repaired data comes back round as "version 0".
		CompoundTag reSavedByAnOldJar = migrated.saveCustomOnly(registries);
		reSavedByAnOldJar.remove(BlockEntityDataMigrations.DATA_VERSION_KEY);
		BatteryBoxBlockEntity twice = new BatteryBoxBlockEntity(abs, level.getBlockState(abs));
		twice.loadWithComponents(
				TagValueInput.create(ProblemReporter.DISCARDING, registries, reSavedByAnOldJar));
		if (!twice.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT + 1).is(Items.REDSTONE)) {
			helper.fail("the 0->1 rung is not idempotent: a second pass moved the chip again, leaving "
					+ twice.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT + 1));
			return;
		}

		// 5. A tag that declares the CURRENT version is left alone — this is what the version buys, and
		// without it the rung would keep re-deciding on every load exactly as the old heuristic did.
		BatteryBoxBlockEntity untouched = new BatteryBoxBlockEntity(abs, level.getBlockState(abs));
		untouched.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, current));
		if (!untouched.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT).is(Items.REDSTONE)) {
			helper.fail("a current-version save was migrated anyway: discharge="
					+ untouched.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT));
			return;
		}
		helper.succeed();
	}
}
