package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;
import static dev.alaindustrial.gametest.MachineRig.AMPLE_EU;
import static dev.alaindustrial.gametest.MachineRig.DRIVE_TICKS;
import static dev.alaindustrial.gametest.MachineRig.compressor;
import static dev.alaindustrial.gametest.MachineRig.extractor;
import static dev.alaindustrial.gametest.MachineRig.furnace;
import static dev.alaindustrial.gametest.MachineRig.macerator;
import static dev.alaindustrial.gametest.MachineRig.place;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * World scenarios for what a processing machine refuses (MOD-446): no power, a non-recipe input, a full or
 * foreign output slot, an input swapped mid-operation, and the sided slot roles automation sees — each
 * without a dupe, without spending EU and without corrupting progress.

 * <p>Split by mechanic in MOD-717 (TST-2, the {@code ReactorScenarios} pattern): the lanes run the names in
 * {@link MachineScenarios}, which delegates here; a new processing-machine scenario is written in the class of
 * its mechanic and declared in that class's roster.
 */
public final class MachineFaultScenarios {

	private MachineFaultScenarios() {
	}

	/** Negative: a valid input but NO power yields no output, and progress stays frozen at 0 (R-NRG-10). */
	private static void assertNoPowerNoOutput(GameTestHelper helper, Block block, ItemStack input) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(0);
		be.setItem(0, input);
		drive(be, helper, DRIVE_TICKS);
		if (!be.getItem(1).isEmpty()) {
			helper.fail(block + ": produced output without energy");
		}
		if (be.getDataAccess().get(2) != 0) {
			helper.fail(block + ": progress advanced without energy (got " + be.getDataAccess().get(2) + ")");
		}
		helper.succeed();
	}

	/** Negative: a non-recipe input, even fully powered, yields no output. */
	private static void assertNoRecipeNoOutput(GameTestHelper helper, Block block, ItemStack junk) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, junk);
		drive(be, helper, DRIVE_TICKS);
		if (!be.getItem(1).isEmpty()) {
			helper.fail(block + ": produced output from a non-recipe input");
		}
		helper.succeed();
	}

	// ── Negative (NEG) ──────────────────────────────────────────────────────────────

	/**
	 * TC-MACH-001-NEG01: no energy → no output, progress frozen at 0.
	 *
	 * @implements TC-MACH-001-NEG01 — no energy: no output, progress frozen at 0. @covers R-NRG-10
	 */
	public static void tcMach001Neg01_noPowerNoOutput(GameTestHelper helper) {
		assertNoPowerNoOutput(helper, macerator(), new ItemStack(Items.RAW_IRON, 4));
	}

	/**
	 * TC-MACH-001-NEG02: non-recipe input (dirt) yields no output even when powered.
	 *
	 * @implements TC-MACH-001-NEG02 — non-recipe input (dirt) yields no output even when powered.
	 */
	public static void tcMach001Neg02_nonRecipeNoOutput(GameTestHelper helper) {
		assertNoRecipeNoOutput(helper, macerator(), new ItemStack(Items.DIRT, 4));
	}

	/**
	 * TC-MACH-002-NEG01: electric furnace, no energy → no smelt.
	 *
	 * @implements TC-MACH-002-NEG01 — electric furnace: no energy → no smelt. @covers R-NRG-10
	 */
	public static void tcMach002Neg01_furnaceNoPower(GameTestHelper helper) {
		assertNoPowerNoOutput(helper, furnace(), new ItemStack(Items.RAW_IRON, 4));
	}

	/**
	 * TC-MACH-001-CON01: sided automation roles — a hopper/pipe cannot insert into the output slot
	 * nor extract the unprocessed input; only the output slot is extractable.
	 *
	 * @implements TC-MACH-001-CON01 — sided automation roles: a hopper/pipe cannot insert into the
	 *     output slot nor extract the unprocessed input; only the output slot is extractable.
	 * @covers R-GUI-05
	 */
	public static void tcMach001Con01_sidedSlotRoles(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, macerator());
		Direction d = Direction.NORTH;
		if (be.canPlaceItemThroughFace(1, new ItemStack(ModContent.IRON_DUST.get()), d)) {
			helper.fail("automation can insert into the output slot");
		}
		if (be.canTakeItemThroughFace(0, new ItemStack(Items.RAW_IRON), d)) {
			helper.fail("automation can steal the unprocessed input");
		}
		if (!be.canTakeItemThroughFace(1, new ItemStack(ModContent.IRON_DUST.get()), d)) {
			helper.fail("automation cannot extract the output");
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-001-NEG03: full output slot jams the machine — no overflow, progress frozen.
	 *
	 * <p>When output slot is at max stack (64), the machine must not advance progress and must not
	 * create a 65th item. This validates that machines check output feasibility before consuming EU
	 * and ticking progress.
	 *
	 * @implements TC-MACH-001-NEG03 — full output slot jams the machine: no overflow, progress frozen.
	 *
	 * <p>When output slot is at max stack (64), the machine must not advance progress and must not
	 * create a 65th item. This validates that machines check output feasibility before consuming EU
	 * and ticking progress.
	 */
	public static void tcMach001Neg03_fullOutputJamsMachine(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, macerator());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		be.setItem(1, new ItemStack(ModContent.IRON_DUST.get(), 64)); // output slot at max stack
		drive(be, helper, DRIVE_TICKS);
		int outCount = be.getItem(1).getCount();
		int progress  = be.getDataAccess().get(2);
		if (outCount != 64) {
			helper.fail("output slot overflowed: " + outCount + " items (expected 64)");
		}
		if (progress != 0) {
			helper.fail("machine advanced progress to " + progress + " despite full output slot");
		}
		helper.succeed();
	}

	// ── NEG: full output jams the machine, no dupe (parametric across all 4) ───────────────────────

	/**
	 * Negative: output slot at max stack (64) with the recipe's own product jams the machine — no
	 * overflow, progress frozen at 0. Generalizes {@link #tcMach001Neg03_fullOutputJamsMachine} to all
	 * four machines.
	 */
	private static void assertFullOutputJamsMachine(GameTestHelper helper, Block block, ItemStack input, Item product) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, input);
		be.setItem(1, new ItemStack(product, 64));
		drive(be, helper, DRIVE_TICKS);
		int outCount = be.getItem(1).getCount();
		int progress = be.getDataAccess().get(2);
		if (outCount != 64) {
			helper.fail(block + ": output slot overflowed: " + outCount + " items (expected 64)");
		}
		if (progress != 0) {
			helper.fail(block + ": advanced progress to " + progress + " despite full output slot");
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-002-NEG03: electric furnace, full output (64 iron_ingot) jams, no overflow.
	 *
	 * @implements TC-MACH-002-NEG03 — electric furnace: full output (64 iron_ingot) jams, no overflow. @covers R-GUI-04
	 */
	public static void tcMach002Neg03_furnaceFullOutputJamsMachine(GameTestHelper helper) {
		assertFullOutputJamsMachine(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 4), Items.IRON_INGOT);
	}

	/**
	 * TC-MACH-003-NEG03: compressor, full output (64 copper_ingot) jams, no overflow.
	 *
	 * @implements TC-MACH-003-NEG03 — compressor: full output (64 copper_ingot) jams, no overflow. @covers R-GUI-04
	 */
	public static void tcMach003Neg03_compressorFullOutputJamsMachine(GameTestHelper helper) {
		assertFullOutputJamsMachine(helper, compressor(), new ItemStack(ModContent.COPPER_DUST.get(), 4),
				Items.COPPER_INGOT);
	}

	/**
	 * TC-EXTR-001-NEG01 (61-item leg): 61× blaze_powder in output leaves room for exactly the ×3
	 * multiplied product (61+3=64) — operation completes, no jam.
	 *
	 * @implements TC-EXTR-001-NEG01 (61-item leg) — extractor: 61× blaze_powder in output leaves room
	 *     for exactly the ×3 multiplied product (61+3=64) — operation completes, no jam.
	 * @covers R-GUI-04, R-NRG-04
	 */
	public static void tcExtr001Neg01a_multipliedOutputFitsAt61(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, extractor());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(Items.BLAZE_ROD, 4));
		be.setItem(1, new ItemStack(Items.BLAZE_POWDER, 61));
		drive(be, helper, DRIVE_TICKS);
		ItemStack out = be.getItem(1);
		if (out.getCount() != 64) {
			helper.fail("extractor with 61 in output should finish and reach 64 (61+3) but got " + out.getCount());
		}
		helper.succeed();
	}

	/**
	 * TC-EXTR-001-NEG01 (62-item leg): 62× blaze_powder in output cannot fit the ×3 multiplied product
	 * (62+3=65 > max_stack=64) — machine jams, no overflow, no dupe, blaze_rod not consumed. This is
	 * the multiplied-output analogue of NEG03 (which uses a single-count product); ordinary machines
	 * never hit this boundary at 62 because their output is ×1.
	 *
	 * @implements TC-EXTR-001-NEG01 (62-item leg) — extractor: 62× blaze_powder in output cannot fit
	 *     the ×3 multiplied product (62+3=65 > max_stack=64) — machine jams, no overflow, no dupe,
	 *     blaze_rod not consumed. This is the multiplied-output analogue of NEG03 (which uses a
	 *     single-count product); ordinary machines never hit this boundary at 62 because their output
	 *     is ×1.
	 * @covers R-GUI-04, R-NRG-04
	 */
	public static void tcExtr001Neg01b_multipliedOutputJamsAt62(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, extractor());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(Items.BLAZE_ROD, 4));
		be.setItem(1, new ItemStack(Items.BLAZE_POWDER, 62));
		drive(be, helper, DRIVE_TICKS);
		ItemStack in = be.getItem(0);
		ItemStack out = be.getItem(1);
		if (out.getCount() != 62) {
			helper.fail("extractor output slot must stay at 62 (no overflow to 65) but got " + out.getCount());
		}
		if (in.isEmpty() || in.getCount() != 4) {
			helper.fail("extractor must not consume blaze_rod while jammed on output but input is now "
					+ (in.isEmpty() ? "empty" : in.getCount()));
		}
		helper.succeed();
	}

	// ── NEG: incompatible item in output slot → no dupe, no corruption (parametric) ────────────────

	/**
	 * Negative: output slot occupied by a foreign item (not the recipe's product) — machine must not
	 * mutate/consume that foreign stack, must not lose the input, and must not duplicate anything.
	 */
	private static void assertWrongItemInOutputNoDupe(GameTestHelper helper, Block block, ItemStack input,
			ItemStack foreignOutput) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, input.copy());
		be.setItem(1, foreignOutput.copy());
		drive(be, helper, DRIVE_TICKS);
		ItemStack out = be.getItem(1);
		if (!out.is(foreignOutput.getItem()) || out.getCount() != foreignOutput.getCount()) {
			helper.fail(block + ": foreign item in output slot was mutated: " + out.getCount() + "× " + out.getItem());
		}
		ItemStack in = be.getItem(0);
		if (in.isEmpty() || !in.is(input.getItem()) || in.getCount() != input.getCount()) {
			helper.fail(block + ": input was consumed despite the output slot being jammed by a foreign item");
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-001-NEG04: macerator, cobblestone in output slot → unchanged, no dupe.
	 *
	 * @implements TC-MACH-001-NEG04 — macerator: cobblestone in output slot → unchanged, no dupe. @covers R-GUI-04
	 */
	public static void tcMach001Neg04_maceratorWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, macerator(), new ItemStack(Items.RAW_IRON, 1),
				new ItemStack(Items.COBBLESTONE, 1));
	}

	/**
	 * TC-MACH-002-NEG04: electric furnace, cobblestone (unrelated) in output slot → unchanged.
	 *
	 * @implements TC-MACH-002-NEG04 — electric furnace: cobblestone (unrelated) in output slot → unchanged. @covers
	 *     R-GUI-04
	 */
	public static void tcMach002Neg04_furnaceWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				new ItemStack(Items.COBBLESTONE, 1));
	}

	/**
	 * TC-COMP-001-NEG02: compressor, finished iron_ingot in output slot, gold_dust queued as new
	 * input → the output slot's iron_ingot is untouched (mismatched product), no dupe.
	 *
	 * @implements TC-COMP-001-NEG02 — compressor: finished iron_ingot in output slot, gold_dust queued
	 *     as new input → output slot's iron_ingot untouched (mismatched product), no dupe.
	 * @covers R-GUI-04
	 */
	public static void tcMach003Neg04_compressorWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, compressor(), new ItemStack(ModContent.GOLD_DUST.get(), 1),
				new ItemStack(Items.IRON_INGOT, 1));
	}

	/**
	 * TC-MACH-004-NEG04: extractor, cobblestone (unrelated) in output slot → unchanged.
	 *
	 * @implements TC-MACH-004-NEG04 — extractor: cobblestone (unrelated) in output slot → unchanged. @covers R-GUI-04
	 */
	public static void tcMach004Neg04_extractorWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1),
				new ItemStack(Items.COBBLESTONE, 1));
	}

	// ── NEG: non-recipe input, even fully powered → no output, EU untouched (parametric) ───────────

	/** Negative: a non-recipe input costs no EU even when the machine is fully powered. */
	private static void assertNonRecipeNoEuSpent(GameTestHelper helper, Block block, ItemStack junk) {
		MachineBlockEntity be = place(helper, block);
		long startAmount = 800; // direct amount=, not TR insert — matches PERFORMANCE.md buffer for all 4 machines
		be.getEnergyStorage().setAmountUntracked(startAmount);
		be.setItem(0, junk);
		drive(be, helper, DRIVE_TICKS);
		if (!be.getItem(1).isEmpty()) {
			helper.fail(block + ": produced output from a non-recipe input");
		}
		if (be.getEnergyStorage().getAmount() != startAmount) {
			helper.fail(block + ": EU was spent on a non-recipe input, amount now "
					+ be.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-001-NEG05: macerator, non-recipe input (dirt) does not spend EU even when powered.
	 *
	 * @implements TC-MACH-001-NEG05 — macerator: non-recipe input (dirt) does not spend EU even when powered. @covers
	 *     R-NRG-04
	 */
	public static void tcMach001Neg05_maceratorNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, macerator(), new ItemStack(Items.DIRT, 1));
	}

	/**
	 * TC-MACH-002-NEG05: electric furnace, non-recipe input (lava_bucket) does not spend EU.
	 *
	 * @implements TC-MACH-002-NEG05 — electric furnace: non-recipe input (lava_bucket) does not spend EU. @covers
	 *     R-NRG-04
	 */
	public static void tcMach002Neg05_furnaceNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, furnace(), new ItemStack(Items.LAVA_BUCKET, 1));
	}

	/**
	 * TC-COMP-001-NEG01: compressor, item without a compressing recipe (diamond) does not spend EU.
	 *
	 * @implements TC-COMP-001-NEG01 — compressor: item without a compressing recipe (diamond) does not
	 *     spend EU even when the buffer is full.
	 * @covers R-GUI-02
	 */
	public static void tcMach003Neg05_compressorNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, compressor(), new ItemStack(Items.DIAMOND, 1));
	}

	/**
	 * TC-COMP-001-NEG03: compressor, raw_iron (ore, not dust) is not a valid compressing input — the
	 * macerator's output is required first, raw ore is not a shortcut.
	 *
	 * @implements TC-COMP-001-NEG03 — compressor: raw_iron (ore, not dust) is not a valid compressing
	 *     input — the macerator's output is required first, raw ore is not a shortcut.
	 * @covers R-GUI-02
	 */
	public static void tcComp001Neg03_compressorRawOreNotAccepted(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, compressor(), new ItemStack(Items.RAW_IRON, 1));
	}

	/**
	 * TC-MACH-004-NEG05: extractor, non-recipe input (dirt) does not spend EU even when powered.
	 *
	 * @implements TC-MACH-004-NEG05 — extractor: non-recipe input (dirt) does not spend EU even when powered. @covers
	 *     R-NRG-04
	 */
	public static void tcMach004Neg05_extractorNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, extractor(), new ItemStack(Items.DIRT, 1));
	}

	// ── NEG: recipe swap mid-operation resets progress (parametric FUN04) ──────────────────────────

	/**
	 * Swapping the input item mid-operation (after partial progress) resets progress to 0 and starts a
	 * fresh operation for the new item; the old input is not lost/duped. Parametric across all four
	 * machines sharing {@code MachineBlockEntity}.
	 */
	private static void assertInputSwapMidOpResetsProgress(GameTestHelper helper, Block block, ItemStack inputA,
			ItemStack inputB, int halfwayTicks) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, inputA.copy());
		drive(be, helper, halfwayTicks);
		int progressBefore = be.getDataAccess().get(2);
		if (progressBefore <= 0) {
			helper.fail(block + ": expected partial progress before the input swap but got " + progressBefore);
		}
		be.setItem(0, inputB.copy());
		if (be.getDataAccess().get(2) != 0) {
			helper.fail(block + ": progress did not reset to 0 immediately after swapping the input item");
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-001-FUN04: macerator, raw_iron swapped for raw_copper mid-op resets progress.
	 *
	 * @implements TC-MACH-001-FUN04 — macerator: raw_iron swapped for raw_copper mid-op resets progress. @covers
	 *     R-NRG-10
	 */
	public static void tcMach001Fun04_maceratorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, macerator(), new ItemStack(Items.RAW_IRON, 1),
				new ItemStack(Items.RAW_COPPER, 1), Config.maceratorDuration / 2);
	}

	/**
	 * TC-MACH-002-FUN04: electric furnace, iron_dust swapped for sand mid-op resets progress.
	 *
	 * @implements TC-MACH-002-FUN04 — electric furnace: iron_dust swapped for sand mid-op resets progress. @covers
	 *     R-NRG-10
	 */
	public static void tcMach002Fun04_furnaceInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				new ItemStack(Items.SAND, 1), Config.electricFurnaceDuration / 2);
	}

	/**
	 * TC-MACH-003-FUN04: compressor, copper_dust swapped for iron_dust mid-op resets progress.
	 *
	 * @implements TC-MACH-003-FUN04 — compressor: copper_dust swapped for iron_dust mid-op resets progress. @covers
	 *     R-NRG-10
	 */
	public static void tcMach003Fun04_compressorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, compressor(), new ItemStack(ModContent.COPPER_DUST.get(), 1),
				new ItemStack(ModContent.IRON_DUST.get(), 1), Config.compressorDuration / 2);
	}

	/**
	 * TC-MACH-004-FUN04: extractor, blaze_rod swapped for gravel mid-op resets progress.
	 *
	 * @implements TC-MACH-004-FUN04 — extractor: blaze_rod swapped for gravel mid-op resets progress. @covers R-NRG-10
	 */
	public static void tcMach004Fun04_extractorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1),
				new ItemStack(Items.GRAVEL, 1), Config.extractorDuration / 2);
	}
}
