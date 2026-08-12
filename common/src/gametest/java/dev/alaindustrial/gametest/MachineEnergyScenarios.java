package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;

/**
 * Loader-neutral world-based energy gametest bodies (MOD-022) — processing machines
 * (macerator/compressor/extractor/electric furnace): recipe seams, negatives, E_op boundary
 * analysis, lit state, NBT persistence round-trips and the teleporter station seams (MOD-091).
 * Suite contract and shared helpers: {@link EnergyScenarioSupport}.
 */
public final class MachineEnergyScenarios {

	private MachineEnergyScenarios() {}

	// ── scenario 0: macerator processes a recipe (recipe-registry seam, MOD-022) ──────────────────

	private static final BlockPos MAC = new BlockPos(1, 2, 1);

	/**
	 * A powered macerator with a valid input produces its recipe output. This proves the machine
	 * {@code RecipeType}+{@code RecipeSerializer} resolve and recipe lookup works on THIS loader — the
	 * NeoForge frozen-registry seam (MOD-022): NeoForge registers these via a {@code DeferredRegister}
	 * ({@code ModRecipesNeoForge}), so {@code Kind.type()} must resolve the deferred holder at tick time.
	 * Input {@code minecraft:emerald → alaindustrial:emerald_dust} (see data/.../recipe/maceration).
	 * Mirrors the Fabric-side {@code MachineGameTest} processing cases, which the NeoForge world lane lacked.
	 */
	public static void maceratorProcessesRecipe(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000); // > any single op's E_op; bypasses the per-tick cap
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.EMERALD));
			for (int i = 0; i < 400; i++) { // > longest machine duration + margin
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(MaceratorBlockEntity.OUTPUT_SLOT);
			if (out.isEmpty()) {
				helper.fail("macerator produced no output — maceration recipe did not resolve on this loader");
				return;
			}
			// Assert the SPECIFIC output item, not just non-empty — otherwise a regression returning the
			// input emerald (or any fallback item) would pass trivially. Sibling tests (iron_ore→dust)
			// already check identity; this first recipe must too.
			if (!out.is(dev.alaindustrial.registry.ModContent.EMERALD_DUST.get())) {
				helper.fail("macerator output was " + out.getItem() + " x" + out.getCount()
						+ ", expected emerald_dust — wrong recipe resolved on this loader");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 5: NBT persistence round-trip ────────────────────────────────────────────────────

	private static final BlockPos PER_POS = new BlockPos(1, 2, 1);

	/**
	 * NBT save/load round-trip preserves a macerator's energy + progress + input count.
	 * Mirrors: PersistenceGameTest.rPer01_maceratorNbtRoundTrip
	 */
	public static void nbtRoundTripPreservesState(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(PER_POS);
		helper.setBlock(PER_POS, ModContent.MACERATOR.get());
		MaceratorBlockEntity src = helper.getBlockEntity(PER_POS, MaceratorBlockEntity.class);

		long energy0 = 734L; // below machineBuffer (800): a buffer cannot hold more than its capacity
		int progress0 = 7;
		src.getEnergyStorage().setAmountUntracked(energy0);
		// setItem() resets progress on an input change, so place the input BEFORE setting progress.
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

	// ── scenario 7: machine negatives — no power, full output jam, input swap ──────────────────────

	/**
	 * A machine with a valid input but NO energy produces no output and does not advance progress
	 * (R-NRG-10). Exercises the shared {@code MachineBlockEntity} processing loop on the NeoForge lane —
	 * the Fabric lane covers this per-machine in {@code MachineGameTest#assertNoPowerNoOutput}.
	 * Mirrors: MachineGameTest.tcMach001Neg01_noPowerNoOutput
	 */
	public static void machineNoPowerNoOutput(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(0);
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			if (!mac.getItem(1).isEmpty()) {
				helper.fail("macerator produced output without energy: " + mac.getItem(1));
				return;
			}
			if (mac.getDataAccess().get(2) != 0) {
				helper.fail("macerator advanced progress without energy: " + mac.getDataAccess().get(2));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	/**
	 * A machine whose output slot is already at the max stack (64) of the recipe's product JAMS: it must
	 * not overflow past 64, must not advance progress, must not consume the input. Catches a
	 * dupe/overflow regression on the NeoForge processing path.
	 * Mirrors: MachineGameTest.tcMach001Neg03_fullOutputJamsMachine
	 */
	public static void machineFullOutputJams(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 4));
			mac.setItem(1, new ItemStack(ModContent.IRON_DUST.get(), 64));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			int outCount = mac.getItem(1).getCount();
			int progress = mac.getDataAccess().get(2);
			if (outCount != 64) {
				helper.fail("output slot overflowed: " + outCount + " items (expected 64)");
				return;
			}
			if (progress != 0) {
				helper.fail("machine advanced progress to " + progress + " despite full output slot");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	/**
	 * Swapping the input item mid-operation resets progress to 0 (R-NRG-10): partial progress on item A
	 * must not carry over to item B. Drives half a maceration cycle, swaps raw_iron → raw_copper,
	 * asserts progress reset.
	 * Mirrors: MachineGameTest.tcMach001Fun04_maceratorInputSwapResetsProgress
	 */
	public static void machineInputSwapResetsProgress(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 1));
			int halfway = Config.maceratorDuration / 2;
			for (int i = 0; i < halfway; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			int progressBefore = mac.getDataAccess().get(2);
			if (progressBefore <= 0) {
				helper.fail("expected partial progress before the swap but got " + progressBefore);
				return;
			}
			mac.setItem(0, new ItemStack(Items.RAW_COPPER, 1));
			if (mac.getDataAccess().get(2) != 0) {
				helper.fail("progress did not reset to 0 after swapping the input: "
						+ mac.getDataAccess().get(2));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 12: NBT persistence round-trip for all 4 machines ─────────────────────────────────

	/**
	 * NBT save/load round-trip preserves an electric furnace's energy + progress + input count.
	 * Exercises the shared {@code MachineBlockEntity} save/load path on the NeoForge lane for a machine
	 * OTHER than the macerator (already covered by {@link #nbtRoundTripPreservesState}).
	 * Mirrors: PersistenceGameTest (furnace round-trip)
	 */
	public static void furnaceNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(PER_POS);
		helper.setBlock(PER_POS, ModContent.ELECTRIC_FURNACE.get());
		dev.alaindustrial.block.entity.MachineBlockEntity src =
				helper.getBlockEntity(PER_POS, dev.alaindustrial.block.entity.MachineBlockEntity.class);
		if (src == null) {
			helper.fail("electric furnace block entity missing");
			return;
		}
		long energy0 = 645L; // below machineBuffer (800), see above
		int progress0 = 9;
		src.getEnergyStorage().setAmountUntracked(energy0);
		src.setItem(0, new ItemStack(Items.RAW_IRON, 3));
		src.getDataAccess().set(2, progress0);
		int input0 = src.getItem(0).getCount();

		CompoundTag tag = src.saveCustomOnly(registries);
		dev.alaindustrial.block.entity.MachineBlockEntity restored =
				new dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != energy0
				|| restored.getDataAccess().get(2) != progress0
				|| restored.getItem(0).getCount() != input0) {
			helper.fail("furnace round-trip mismatch: energy " + energy0 + "->"
					+ restored.getEnergyStorage().getAmount() + " progress " + progress0 + "->"
					+ restored.getDataAccess().get(2) + " input " + input0 + "->"
					+ restored.getItem(0).getCount());
			return;
		}
		helper.succeed();
	}

	// ── scenario 18: extractor + compressor positive recipes ──────────────────────────────────────

	/**
	 * Compressor compresses a copper dust into a copper ingot. Proves the compressor's recipe lookup
	 * resolves on the NeoForge lane (recipe-type registration seam) — distinct from the macerator case.
	 * Mirrors: MachineGameTest.tcComp001Fun02_compressorMakesCopperIngot
	 */
	public static void compressorMakesCopperIngot(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.COMPRESSOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(dev.alaindustrial.registry.ModContent.COPPER_DUST.get(), 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.COPPER_INGOT)) {
				helper.fail("compressor did not produce a copper ingot from copper dust: "
						+ (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("compressor block entity missing");
	}

	/**
	 * Extractor extracts flint from gravel. Proves the extractor's recipe lookup resolves on the NeoForge
	 * lane — distinct from the macerator/compressor cases.
	 * Mirrors: MachineGameTest.tcExtr001Fun02a_extractorMakesFlint
	 */
	public static void extractorMakesFlint(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.GRAVEL, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.FLINT)) {
				helper.fail("extractor did not produce flint from gravel: "
						+ (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	/**
	 * Electric furnace smelts raw iron into an iron ingot. Proves the furnace's recipe lookup (mod +
	 * vanilla fallback) resolves on the NeoForge lane.
	 * Mirrors: MachineGameTest.tcMach002Fun01_furnaceSmeltsRawIron
	 */
	public static void furnaceSmeltsRawIron(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.ELECTRIC_FURNACE.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.IRON_INGOT)) {
				helper.fail("electric furnace did not smelt raw iron into an iron ingot: "
						+ (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("electric furnace block entity missing");
	}

	// ── scenario 20: machine no-recipe negative (parametric, all 4 machines) ───────────────────────

	/**
	 * Electric furnace with a non-recipe input (dirt) spends no EU even when fully powered. Catches a
	 * regression that consumes EU for an invalid input.
	 * Mirrors: MachineGameTest.tcMach002Neg05_furnaceNonRecipeNoEuSpent
	 */
	public static void furnaceNonRecipeNoEuSpent(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.ELECTRIC_FURNACE.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			long start = 800;
			mac.getEnergyStorage().setAmountUntracked(start);
			mac.setItem(0, new ItemStack(Items.DIRT, 1));
			for (int i = 0; i < 200; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			if (!mac.getItem(1).isEmpty()) {
				helper.fail("furnace produced output from dirt: " + mac.getItem(1));
				return;
			}
			if (mac.getEnergyStorage().getAmount() != start) {
				helper.fail("furnace spent EU on a non-recipe: " + start + " -> " + mac.getEnergyStorage().getAmount());
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("electric furnace block entity missing");
	}

	/**
	 * Compressor full output (64 copper ingots) jams: no overflow, no progress, input not consumed.
	 * Mirrors: MachineGameTest.tcMach003Neg03_compressorFullOutputJamsMachine
	 */
	public static void compressorFullOutputJams(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.COMPRESSOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(dev.alaindustrial.registry.ModContent.COPPER_DUST.get(), 4));
			mac.setItem(1, new ItemStack(Items.COPPER_INGOT, 64));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			int outCount = mac.getItem(1).getCount();
			int progress = mac.getDataAccess().get(2);
			if (outCount != 64) {
				helper.fail("compressor output overflowed: " + outCount + " (expected 64)");
				return;
			}
			if (progress != 0) {
				helper.fail("compressor advanced progress to " + progress + " despite full output");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("compressor block entity missing");
	}

	/**
	 * Extractor input swap mid-operation resets progress (R-NRG-10): blaze_rod swapped for gravel
	 * clears accumulated progress.
	 * Mirrors: MachineGameTest.tcMach004Fun04_extractorInputSwapResetsProgress
	 */
	public static void extractorInputSwapResetsProgress(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.BLAZE_ROD, 1));
			int halfway = Config.extractorDuration / 2;
			for (int i = 0; i < halfway; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			int progressBefore = mac.getDataAccess().get(2);
			if (progressBefore <= 0) {
				helper.fail("expected partial progress before swap but got " + progressBefore);
				return;
			}
			mac.setItem(0, new ItemStack(Items.GRAVEL, 1));
			if (mac.getDataAccess().get(2) != 0) {
				helper.fail("progress did not reset after swapping blaze_rod -> gravel: "
						+ mac.getDataAccess().get(2));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	// ── scenario 22: extra machine recipes (multi-output / vanilla fallback) ───────────────────────

	/**
	 * Extractor produces 3× blaze powder from a blaze rod (multi-output recipe). Catches a regression
	 * that drops the ×3 multiplier.
	 * Mirrors: MachineGameTest.tcMach004Fun02_extractorConsumesExactlyOnePerOperation
	 */
	public static void extractorBlazeRodToPowder(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.BLAZE_ROD, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.BLAZE_POWDER) || out.getCount() < 3) {
				helper.fail("extractor blaze_rod expected >=3 blaze_powder, got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	/**
	 * Electric furnace smelts sand to glass (vanilla smelting fallback). Proves the furnace inherits the
	 * vanilla {@code minecraft:smelting} recipe type, not just the mod's own recipes.
	 * Mirrors: MachineGameTest.tcEfurn001Fun03a_furnaceSmeltsSand
	 */
	public static void furnaceSmeltsSandToGlass(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.ELECTRIC_FURNACE.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.SAND, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.GLASS)) {
				helper.fail("furnace did not smelt sand to glass: " + (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("electric furnace block entity missing");
	}

	/**
	 * Compressor compresses an iron dust into an iron ingot. Mirrors the vanilla ingot-forming path.
	 * Mirrors: MachineGameTest.tcComp001Fun04_compressorMakesIronIngot
	 */
	public static void compressorIronDustToIngot(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.COMPRESSOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(dev.alaindustrial.registry.ModContent.IRON_DUST.get(), 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.IRON_INGOT)) {
				helper.fail("compressor did not produce iron ingot from iron dust: "
						+ (out.isEmpty() ? "empty" : out));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("compressor block entity missing");
	}

	// ── scenario 23: machine E_op exact / E_op−1 BVA (parametric) ──────────────────────────────────

	/**
	 * Macerator E_op exact BVA: exactly {@code maceratorDuration × machineEuPerTick} EU completes one
	 * operation and leaves the buffer at 0. Catches an off-by-one in the EU-per-tick accounting.
	 * Mirrors: MachineGameTest.tcMach001Prf04_maceratorEopExactCompletes
	 */
	public static void maceratorEopExactCompletes(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			long eOp = (long) Config.maceratorDuration * Config.machineEuPerTick;
			mac.getEnergyStorage().setAmountUntracked(eOp);
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 1));
			for (int i = 0; i < Config.maceratorDuration; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			if (mac.getItem(1).isEmpty() || !mac.getItem(1).is(dev.alaindustrial.registry.ModContent.IRON_DUST.get())) {
				helper.fail("E_op exact (" + eOp + ") did not complete the maceration");
				return;
			}
			if (mac.getEnergyStorage().getAmount() != 0) {
				helper.fail("E_op exact should leave amount==0 but got " + mac.getEnergyStorage().getAmount());
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	/**
	 * Macerator E_op−1 BVA: one EU short of a full operation never produces output and progress freezes
	 * at {@code duration − 1}. Catches a regression that completes on a short budget.
	 * Mirrors: MachineGameTest.tcMach001Prf03_maceratorEopMinusOneStalls
	 */
	public static void maceratorEopMinusOneStalls(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked((long) Config.maceratorDuration * Config.machineEuPerTick - 1);
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 1));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			if (!mac.getItem(1).isEmpty()) {
				helper.fail("E_op−1 must not produce any output");
				return;
			}
			int progress = mac.getDataAccess().get(2);
			if (progress != Config.maceratorDuration - 1) {
				helper.fail("E_op−1 progress expected " + (Config.maceratorDuration - 1) + " but got " + progress);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 32: machine lit blockstate tracks active/idle (R-VIS-01) ──────────────────────────

	/**
	 * The macerator's {@code lit} blockstate is on while an operation is progressing (powered + valid
	 * input) and off once the machine has no work left (input exhausted). Catches a regression that
	 * leaves the block permanently lit (or never lights it).
	 * Mirrors: MachineGameTest.tcMach001Sta01_maceratorLitTracksActive
	 */
	public static void maceratorLitTracksActive(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			BlockPos abs = mac.getBlockPos();
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.RAW_IRON, 1));
			for (int i = 0; i < 3; i++) {
				mac.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
			}
			if (!helper.getLevel().getBlockState(abs).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
				helper.fail("macerator must be LIT while actively processing");
				return;
			}
			// Drain the input so the machine has nothing left; give it a tick to clear LIT.
			mac.setItem(0, ItemStack.EMPTY);
			for (int i = 0; i < 3; i++) {
				mac.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
			}
			if (helper.getLevel().getBlockState(abs).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
				helper.fail("macerator must not stay LIT once there is no input left to process");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── scenario 33: extra machine recipes — cactus (×2 dye), pumpkin (×5 seeds) ──────────────────

	/**
	 * Extractor produces 2× green dye from a cactus (plant-derived ×2 recipe). Catches a regression that
	 * drops the ×2 multiplier on the plant-processing niche.
	 * Mirrors: MachineGameTest.tcExtr001Fun06_extractorMakesGreenDye
	 */
	public static void extractorCactusToGreenDye(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.CACTUS, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.DYE.green()) || out.getCount() < 2) {
				helper.fail("extractor cactus expected >=2 green_dye, got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	/**
	 * Extractor produces 5× pumpkin seeds from a pumpkin (the largest multiplier in the recipe set, ×5).
	 * Exercises a distinct stack-fit boundary from the ×2 / ×3 paths.
	 * Mirrors: MachineGameTest.tcExtr001Fun07_extractorMakesPumpkinSeeds
	 */
	public static void extractorPumpkinToSeeds(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.EXTRACTOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.PUMPKIN, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(Items.PUMPKIN_SEEDS) || out.getCount() < 5) {
				helper.fail("extractor pumpkin expected >=5 pumpkin_seeds, got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("extractor block entity missing");
	}

	// ── scenario 34: macerator tag-ingredient recipe (iron_ore ×2 doubling) ───────────────────────

	/**
	 * Macerator grinds an iron ORE BLOCK into 2× iron dust via the {@code #alaindustrial:macerable_iron}
	 * tag — the ×2 doubling path for the ore-block input (resolved through the item tag). Under MOD-095
	 * raw_iron also macerates to ×2 via its own direct recipe, so both inputs double. Proves tag
	 * ingredients resolve on the NeoForge lane.
	 * Mirrors: MachineGameTest.tcMach001FunIronOre_maceratorGrindsIronOre
	 */
	public static void maceratorIronOreDoublesDust(GameTestHelper helper) {
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, MAC) instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(8000);
			mac.setItem(0, new ItemStack(Items.IRON_ORE, 4));
			for (int i = 0; i < 400; i++) {
				mac.serverTick(helper.getLevel(), mac.getBlockPos(),
						helper.getLevel().getBlockState(mac.getBlockPos()));
			}
			ItemStack out = mac.getItem(1);
			if (out.isEmpty() || !out.is(dev.alaindustrial.registry.ModContent.IRON_DUST.get())
					|| out.getCount() < 2) {
				helper.fail("macerator iron_ore expected >=2 iron_dust (×2 doubling), got "
						+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("macerator block entity missing");
	}

	// ── teleporter station (MOD-091): loader-neutral seams the NeoForge world lane must guard ──────

	private static final BlockPos STATION = new BlockPos(1, 2, 1);

	/**
	 * The station accepts EU on its five working faces and stays inert on its FACING front, on this
	 * loader too. This is the exact defect class the NeoForge energy adapter has produced before
	 * (every face reporting both insert and extract regardless of its real role), so the Fabric-side
	 * {@code TC-TELE-001-NRG03} is not enough on its own — the adapter is per-loader code.
	 */
	public static void teleporterFaceRoles(GameTestHelper helper) {
		helper.setBlock(STATION, ModContent.TELEPORTER.get());
		if (!(be(helper, STATION) instanceof TeleporterBlockEntity station)) {
			helper.fail("teleporter block entity missing");
			return;
		}
		Direction facing = station.getBlockState().getValue(HorizontalMachineBlock.FACING);
		if (station.energyPort(facing) != null) {
			helper.fail("teleporter FACING front must expose no energy port on this loader");
			return;
		}
		for (Direction dir : Direction.values()) {
			if (dir == facing) {
				continue;
			}
			if (station.energyRoleForFace(dir) != EnergyRole.IN) {
				helper.fail("teleporter face " + dir + " must accept EU (IN), got "
						+ station.energyRoleForFace(dir));
				return;
			}
		}
		if (station.getEnergyStorage().supportsExtraction()) {
			helper.fail("teleporter must never emit EU — the network could drain the jump fund");
			return;
		}
		helper.succeed();
	}

	/**
	 * The station's privacy flag rides its dropped item through the {@code teleporter_private} data
	 * component — the MOD-022 frozen-registry seam, which is exactly what breaks per-loader when a
	 * component is not registered on one of them (see the battery-box STORED_ENERGY case above).
	 */
	public static void teleporterDropCarriesPrivacy(GameTestHelper helper) {
		helper.setBlock(STATION, ModContent.TELEPORTER.get());
		if (!(be(helper, STATION) instanceof TeleporterBlockEntity station)) {
			helper.fail("teleporter block entity missing");
			return;
		}
		station.setPrivate(false);
		station.getEnergyStorage().setAmountUntracked(4242L);
		DataComponentMap map = station.collectComponents();
		if (!Boolean.FALSE.equals(map.get(ModDataComponents.TELEPORTER_PRIVATE.get()))) {
			helper.fail("teleporter did not carry TELEPORTER_PRIVATE on drop: "
					+ map.get(ModDataComponents.TELEPORTER_PRIVATE.get())
					+ " (data component unregistered on this loader?)");
			return;
		}
		Long eu = map.get(ModDataComponents.STORED_ENERGY.get());
		if (eu == null || eu.longValue() != 4242L) {
			helper.fail("teleporter did not carry STORED_ENERGY on drop: " + eu);
			return;
		}
		helper.succeed();
	}
}
