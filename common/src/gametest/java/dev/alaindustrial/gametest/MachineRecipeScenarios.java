package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;
import static dev.alaindustrial.gametest.MachineRig.AMPLE_EU;
import static dev.alaindustrial.gametest.MachineRig.DRIVE_TICKS;
import static dev.alaindustrial.gametest.MachineRig.compressor;
import static dev.alaindustrial.gametest.MachineRig.extractor;
import static dev.alaindustrial.gametest.MachineRig.furnace;
import static dev.alaindustrial.gametest.MachineRig.macerator;
import static dev.alaindustrial.gametest.MachineRig.place;
import static dev.alaindustrial.gametest.MachineRig.processing;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * World scenarios for what a powered processing machine makes from a valid input (MOD-446): the recipe
 * outputs of the macerator, electric furnace, compressor and extractor, one-to-one accounting, and the batch
 * recipes that consume several items per operation (MOD-455).

 * <p>Split by mechanic in MOD-717 (TST-2, the {@code ReactorScenarios} pattern): the lanes run the names in
 * {@link MachineScenarios}, which delegates here; a new processing-machine scenario is written in the class of
 * its mechanic and declared in that class's roster.
 */
public final class MachineRecipeScenarios {

	private MachineRecipeScenarios() {
	}

	/** Positive: powered machine with a valid input produces the expected output (≥ minCount). */
	private static void assertProduces(GameTestHelper helper, Block block, ItemStack input, Item expected,
			int minCount) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, input);
		drive(be, helper, DRIVE_TICKS);
		ItemStack out = be.getItem(1);
		if (out.isEmpty() || !out.is(expected) || out.getCount() < minCount) {
			helper.fail(block + ": expected ≥" + minCount + "× " + expected + " but got "
					+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
		}
		helper.succeed();
	}

	// ── Positive (FUN, EP valid class) ──────────────────────────────────────────────

	/**
	 * TC-MACH-001-FUN01: macerator grinds raw iron into 2× iron dust (MOD-095: raw ore doubles).
	 *
	 * @implements TC-MACH-001-FUN01 — macerator grinds raw iron into 2× iron dust (MOD-095: raw ore
	 *      doubles, like ore blocks — Mekanism/IC2 model; only the ingot path is ×1). @covers R-GUI-02
	 */
	public static void tcMach001Fun01_maceratorGrindsRawIron(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.RAW_IRON, 4), ModContent.IRON_DUST.get(), 2);
	}

	/**
	 * TC-MACH-001-FUN-ironOre: iron ore block → 2× iron dust via {@code #alaindustrial:macerable_iron}.
	 *
	 * @implements TC-MACH-001-FUN-ironOre — macerator grinds an iron ore block into 2× iron dust
	 *      (the ×2 doubling path, via {@code #alaindustrial:macerable_iron}). @covers R-GUI-02
	 */
	public static void tcMach001FunIronOre_maceratorGrindsIronOre(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.IRON_ORE, 4), ModContent.IRON_DUST.get(), 2);
	}

	/** MOD-245: stone sulfur ore follows the tag-driven ×2 maceration path. */
	public static void mod245_maceratorGrindsSulfurOre(GameTestHelper helper) {
		assertProduces(helper, macerator(),
				new ItemStack(ModContent.SULFUR_ORE_ITEM.get(), 4), ModContent.SULFUR_DUST.get(), 2);
	}

	/** MOD-245: the deepslate variant is present in the same macerable tag. */
	public static void mod245_maceratorGrindsDeepslateSulfurOre(GameTestHelper helper) {
		assertProduces(helper, macerator(),
				new ItemStack(ModContent.DEEPSLATE_SULFUR_ORE_ITEM.get(), 4), ModContent.SULFUR_DUST.get(), 2);
	}

	/** MOD-245: raw sulfur has its direct ×2 maceration recipe. */
	public static void mod245_maceratorGrindsRawSulfur(GameTestHelper helper) {
		assertProduces(helper, macerator(),
				new ItemStack(ModContent.RAW_SULFUR.get(), 4), ModContent.SULFUR_DUST.get(), 2);
	}

	/**
	 * TC-MACH-002-FUN01: electric furnace smelts raw iron into an iron ingot via the vanilla
	 * {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON).
	 *
	 * @implements TC-MACH-002-FUN01 — electric furnace smelts raw iron into an iron ingot via the
	 *     vanilla {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON;
	 *     raw_iron → iron_ingot is served by vanilla alone, so this also proves the fallback works).
	 */
	public static void tcMach002Fun01_furnaceSmeltsRawIron(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.RAW_IRON, 4), Items.IRON_INGOT, 1);
	}

	/** MOD-245: the vanilla smelting recipe is also served by the electric furnace fallback. */
	public static void mod245_furnaceSmeltsRawSulfur(GameTestHelper helper) {
		assertProduces(helper, furnace(),
				new ItemStack(ModContent.RAW_SULFUR.get(), 4), ModContent.SULFUR_DUST.get(), 1);
	}

	/**
	 * TC-MACH-003-FUN01: compressor compresses clay balls into a brick.
	 *
	 * @implements TC-MACH-003-FUN01 — compressor compresses clay balls into a brick.
	 */
	public static void tcMach003Fun01_compressorMakesBrick(GameTestHelper helper) {
		assertProduces(helper, compressor(), new ItemStack(Items.CLAY_BALL, 4), Items.BRICK, 1);
	}

	/**
	 * TC-MACH-004-FUN01: extractor extracts blaze powder from a blaze rod.
	 *
	 * @implements TC-MACH-004-FUN01 — extractor extracts blaze powder from a blaze rod.
	 */
	public static void tcMach004Fun01_extractorMakesBlazePowder(GameTestHelper helper) {
		assertProduces(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 4), Items.BLAZE_POWDER, 1);
	}

	// ── Extra recipes (FUN) ──────────────────────────────────────────────────────────

	/**
	 * TC-MACH-001-FUN-copperRaw: raw copper (direct recipe {@code raw_copper.json}) → 2× copper dust.
	 *
	 * @implements TC-MACH-001-FUN-copperRaw — macerator grinds raw copper (direct recipe
	 *     {@code raw_copper.json}) into 2× copper dust, mirroring the iron raw path; raw ore doubles (MOD-095).
	 * @covers R-GUI-02
	 */
	public static void tcMach001FunCopperRaw_maceratorGrindsRawCopper(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.RAW_COPPER, 4), ModContent.COPPER_DUST.get(), 2);
	}

	/**
	 * TC-MACH-001-FUN-goldRaw: raw gold → 2× gold dust (direct recipe {@code raw_gold.json}).
	 *
	 * @implements TC-MACH-001-FUN-goldRaw — macerator grinds raw gold into 2× gold dust (direct
	 *     recipe {@code raw_gold.json}); raw ore doubles (MOD-095).
	 * @covers R-GUI-02
	 */
	public static void tcMach001FunGoldRaw_maceratorGrindsRawGold(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.RAW_GOLD, 4), ModContent.GOLD_DUST.get(), 2);
	}

	/**
	 * TC-MACH-001-FUN-ironIngot: an iron ingot (direct recipe, not the tag) → ×1 dust, distinct from the ×2
	 * ore/raw path.
	 *
	 * @implements TC-MACH-001-FUN-ironIngot — macerator grinds an iron ingot (direct recipe, not the
	 *     tag) into ×1 dust — the level-2 slitok path, distinct from the ×2 ore/raw path.
	 * @covers R-GUI-02
	 */
	public static void tcMach001FunIronIngot_maceratorGrindsIronIngot(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.IRON_INGOT, 4), ModContent.IRON_DUST.get(), 1);
	}

	/**
	 * TC-EFURN-001-FUN01: electric furnace, mod recipe dust→ingot, iron_dust path.
	 *
	 * @implements TC-EFURN-001-FUN01 — electric furnace: mod recipe dust→ingot, iron_dust path. @covers R-GUI-02
	 */
	public static void tcEfurn001Fun01_furnaceSmeltsIronDust(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 4), Items.IRON_INGOT, 1);
	}

	/**
	 * TC-EFURN-001-FUN02: vanilla smelting fallback (no mod recipe for raw beef) still smelts food.
	 *
	 * @implements TC-EFURN-001-FUN02 — electric furnace: vanilla smelting fallback (no mod recipe for
	 *     raw beef) still smelts food via {@code minecraft:smelting}.
	 * @covers R-GUI-02
	 */
	public static void tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.BEEF, 4), Items.COOKED_BEEF, 1);
	}

	/**
	 * TC-EFURN-001-FUN03 (sand leg): sand → glass via the vanilla {@code minecraft:smelting} fallback.
	 *
	 * @implements TC-EFURN-001-FUN03 — electric furnace smelts sand into glass via the vanilla
	 *     {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON).
	 * @covers R-GUI-02
	 */
	public static void tcEfurn001Fun03a_furnaceSmeltsSand(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.SAND, 4), Items.GLASS, 1);
	}

	/**
	 * TC-EFURN-001-FUN03 (cobblestone leg): cobblestone → stone via the vanilla fallback.
	 *
	 * @implements TC-EFURN-001-FUN03 — electric furnace smelts cobblestone into stone via the vanilla
	 *     {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON).
	 * @covers R-GUI-02
	 */
	public static void tcEfurn001Fun03b_furnaceSmeltsCobblestone(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.COBBLESTONE, 4), Items.STONE, 1);
	}

	/**
	 * TC-EFURN-001-FUN05: the vanilla fallback smelts wood into charcoal — everything the vanilla furnace can.
	 *
	 * @implements TC-EFURN-001-FUN05 — electric furnace vanilla fallback smelts wood into charcoal,
	 *     proving it inherits everything the vanilla furnace can smelt, not just the mod's own list.
	 * @covers R-GUI-02
	 */
	public static void tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.OAK_LOG, 4), Items.CHARCOAL, 1);
	}

	/**
	 * TC-EFURN-001-FUN04: the electric furnace runs at {@code electricFurnaceDuration} ticks (100),
	 * half the vanilla furnace's 200: the product must not yet exist just before that tick count and
	 * must exist once it is reached.
	 *
	 * @implements TC-EFURN-001-FUN04 — electric furnace runs at {@code electricFurnaceDuration} ticks
	 *     (100), half the vanilla furnace's 200 ticks: the product must not yet exist just before that
	 *     tick count and must exist once it is reached.
	 * @covers R-NRG-04
	 */
	public static void tcEfurn001Fun04_furnaceDurationIsHalfVanilla(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, furnace());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(ModContent.IRON_DUST.get(), 4));
		drive(be, helper, Config.electricFurnaceDuration - 1);
		if (!be.getItem(1).isEmpty()) {
			helper.fail("furnace finished before electricFurnaceDuration (" + Config.electricFurnaceDuration
					+ ") ticks");
		}
		drive(be, helper, 1);
		if (be.getItem(1).isEmpty() || !be.getItem(1).is(Items.IRON_INGOT)) {
			helper.fail("furnace did not finish exactly at electricFurnaceDuration ticks");
		}
		helper.succeed();
	}

	/**
	 * TC-COMP-001-FUN02: compressor, copper_dust → copper_ingot.
	 *
	 * @implements TC-COMP-001-FUN02 — compressor: copper_dust → copper_ingot. @covers R-GUI-02
	 */
	public static void tcComp001Fun02_compressorMakesCopperIngot(GameTestHelper helper) {
		assertProduces(helper, compressor(), new ItemStack(ModContent.COPPER_DUST.get(), 4), Items.COPPER_INGOT, 1);
	}

	/**
	 * TC-COMP-001-FUN03: compressor, gold_dust → gold_ingot.
	 *
	 * @implements TC-COMP-001-FUN03 — compressor: gold_dust → gold_ingot. @covers R-GUI-02
	 */
	public static void tcComp001Fun03_compressorMakesGoldIngot(GameTestHelper helper) {
		assertProduces(helper, compressor(), new ItemStack(ModContent.GOLD_DUST.get(), 4), Items.GOLD_INGOT, 1);
	}

	/**
	 * TC-COMP-001-FUN04: compressor, iron_dust → iron_ingot.
	 *
	 * @implements TC-COMP-001-FUN04 — compressor: iron_dust → iron_ingot. @covers R-GUI-02
	 */
	public static void tcComp001Fun04_compressorMakesIronIngot(GameTestHelper helper) {
		assertProduces(helper, compressor(), new ItemStack(ModContent.IRON_DUST.get(), 4), Items.IRON_INGOT, 1);
	}

	/**
	 * TC-EXTR-001-FUN02: extractor, gravel → flint (single-output recipe).
	 *
	 * @implements TC-EXTR-001-FUN02 — extractor: gravel → flint (single-output recipe). @covers R-GUI-02
	 */
	public static void tcExtr001Fun02a_extractorMakesFlint(GameTestHelper helper) {
		assertProduces(helper, extractor(), new ItemStack(Items.GRAVEL, 4), Items.FLINT, 1);
	}

	/**
	 * TC-EXTR-001-FUN06: extractor, cactus → 2× green_dye. Representative of the plant-derived ×2 dye
	 * recipes — the plant-processing niche. Verifies count and 1-per-op.
	 *
	 * @implements TC-EXTR-001-FUN06 — extractor: cactus → 2× green_dye. Representative of the plant-derived
	 *     ×2 dye recipes (poppy/dandelion/cornflower/cocoa_beans/sea_pickle/lily_of_the_valley/melon_slice
	 *     all yield ×2 of their dye/seeds) — the new plant-processing niche. Verifies count and 1-per-op.
	 * @covers R-GUI-02
	 */
	public static void tcExtr001Fun06_extractorMakesGreenDye(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, extractor(), Items.CACTUS, 4,
				Config.extractorDuration, Items.DYE.green(), 2);
	}

	/**
	 * TC-EXTR-001-FUN07: extractor, pumpkin → 5× pumpkin_seeds. The largest multiplier in the recipe
	 * set (×5) — exercises a distinct stack-fit boundary from the ×3 (blaze_rod) path.
	 *
	 * @implements TC-EXTR-001-FUN07 — extractor: pumpkin → 5× pumpkin_seeds. The largest multiplier in the
	 *     recipe set (×5) — exercises a distinct stack-fit boundary from the ×3 (blaze_rod) path.
	 * @covers R-GUI-02
	 */
	public static void tcExtr001Fun07_extractorMakesPumpkinSeeds(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, extractor(), Items.PUMPKIN, 4,
				Config.extractorDuration, Items.PUMPKIN_SEEDS, 5);
	}

	// ── 1→1 accounting (FUN02 family) — exactly one input item consumed per operation ─────────────

	/**
	 * Positive: exactly one operation's worth of input is consumed, no more — drives ticks for a
	 * single operation only (not the full DRIVE_TICKS) so a bug that consumes >1 input per op would
	 * be caught by the input-count assertion.
	 */
	private static void assertConsumesExactlyOnePerOperation(GameTestHelper helper, Block block, Item inputItem,
			int startCount, int durationTicks, Item expectedOutput, int expectedOutputCount) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(inputItem, startCount));
		drive(be, helper, durationTicks);
		ItemStack in = be.getItem(0);
		ItemStack out = be.getItem(1);
		if (in.isEmpty() || in.getCount() != startCount - 1) {
			helper.fail(block + ": expected " + (startCount - 1) + "× " + inputItem + " left in input but got "
					+ (in.isEmpty() ? "empty" : in.getCount() + "× " + in.getItem()));
		}
		if (out.isEmpty() || !out.is(expectedOutput) || out.getCount() != expectedOutputCount) {
			helper.fail(block + ": expected exactly " + expectedOutputCount + "× " + expectedOutput
					+ " in output but got " + (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-001-FUN02: macerator consumes exactly 1 raw_iron per operation, yielding exactly 2× iron_dust.
	 *
	 * @implements TC-MACH-001-FUN02 — macerator consumes exactly 1 raw_iron per operation (150 ticks),
	 *     leaving 3 of the initial 4 and yielding exactly 2× iron_dust (MOD-095: raw ore doubles, Mekanism/IC2 model).
	 * @covers R-GUI-02
	 */
	public static void tcMach001Fun02_maceratorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, macerator(), Items.RAW_IRON, 4,
				Config.maceratorDuration, ModContent.IRON_DUST.get(), 2);
	}

	/**
	 * TC-MACH-002-FUN02: electric furnace consumes exactly 1 iron_dust per operation, yielding 1× iron_ingot.
	 *
	 * @implements TC-MACH-002-FUN02 — electric furnace consumes exactly 1 iron_dust per operation
	 *     (electricFurnaceDuration ticks), yielding exactly 1× iron_ingot.
	 * @covers R-GUI-02
	 */
	public static void tcMach002Fun02_furnaceConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, furnace(), ModContent.IRON_DUST.get(), 4,
				Config.electricFurnaceDuration, Items.IRON_INGOT, 1);
	}

	/**
	 * TC-MACH-003-FUN02: compressor consumes exactly 1 copper_dust per operation.
	 *
	 * @implements TC-MACH-003-FUN02 — compressor consumes exactly 1 copper_dust per operation
	 *     (compressorDuration ticks); detailed 5-count variant is TC-COMP-001-FUN05.
	 * @covers R-GUI-02
	 */
	public static void tcMach003Fun02_compressorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, compressor(), ModContent.COPPER_DUST.get(), 4,
				Config.compressorDuration, Items.COPPER_INGOT, 1);
	}

	/**
	 * TC-COMP-001-FUN05: compressor consumes exactly 1 of 5 copper_dust per operation, leaving 4.
	 *
	 * @implements TC-COMP-001-FUN05 — compressor consumes exactly 1 of 5 copper_dust per operation
	 *     (130 ticks), leaving 4 and yielding exactly 1× copper_ingot.
	 * @covers R-GUI-02
	 */
	public static void tcComp001Fun05_compressorConsumesExactlyOneOfFive(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, compressor(), ModContent.COPPER_DUST.get(), 5,
				Config.compressorDuration, Items.COPPER_INGOT, 1);
	}

	// ── Batch recipes (MOD-455): input_counts > 1 on a single-slot processing machine ──────────────

	/**
	 * Positive: a batch recipe consumes its WHOLE stated price in one operation, not one item. The
	 * regression this pins is a dupe: before MOD-455 the shared tick loop shrank the input by a
	 * hard-coded 1 regardless of the recipe's {@code input_counts}, so four-dust glowstone would have
	 * been bought with a single dust while JEI/REI honestly drew "4×".
	 */
	private static void assertConsumesBatchPerOperation(GameTestHelper helper, Block block, Item inputItem,
			int startCount, int batchSize, int durationTicks, Item expectedOutput, int expectedOutputCount) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(inputItem, startCount));
		drive(be, helper, durationTicks);
		ItemStack in = be.getItem(0);
		ItemStack out = be.getItem(1);
		int expectedLeft = startCount - batchSize;
		int actualLeft = in.isEmpty() ? 0 : in.getCount();
		if (actualLeft != expectedLeft) {
			helper.fail(block + ": batch of " + batchSize + " should leave " + expectedLeft + "× " + inputItem
					+ " but left " + actualLeft);
		}
		if (out.isEmpty() || !out.is(expectedOutput) || out.getCount() != expectedOutputCount) {
			helper.fail(block + ": expected exactly " + expectedOutputCount + "× " + expectedOutput
					+ " in output but got " + (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
		}
		helper.succeed();
	}

	/**
	 * Negative: a partial batch produces nothing and burns no progress. Guards the other half of the
	 * MOD-455 dupe — the price must be on hand BEFORE the operation runs, not merely at its end.
	 */
	private static void assertPartialBatchProducesNothing(GameTestHelper helper, Block block, ItemStack partial,
			int durationTicks) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, partial.copy());
		drive(be, helper, durationTicks * 2);
		if (!be.getItem(1).isEmpty()) {
			helper.fail(block + ": produced " + be.getItem(1) + " from an underpaid batch");
		}
		if (be.getItem(0).getCount() != partial.getCount()) {
			helper.fail(block + ": consumed input from an underpaid batch (left "
					+ be.getItem(0).getCount() + " of " + partial.getCount() + ")");
		}
		if (be.getDataAccess().get(2) != 0) {
			helper.fail(block + ": advanced progress to " + be.getDataAccess().get(2) + " on an underpaid batch");
		}
		helper.succeed();
	}

	/**
	 * TC-COMP-001-FUN14: compressor, 4× glowstone_dust → 1 glowstone, leaving the 5th dust untouched.
	 *
	 * @implements TC-COMP-001-FUN15 — compressor compacts 4× glowstone_dust into 1 glowstone in one
	 *     operation (130 ticks), leaving the 5th dust untouched (MOD-455 batch price).
	 * @covers R-GUI-02
	 */
	public static void tcComp001Fun15_compressorCompactsGlowstoneDust(GameTestHelper helper) {
		assertConsumesBatchPerOperation(helper, compressor(), Items.GLOWSTONE_DUST, 5, 4,
				Config.compressorDuration, Items.GLOWSTONE, 1);
	}

	/**
	 * TC-COMP-001-FUN15: compressor, 9× redstone → 1 redstone_block, leaving the 10th untouched.
	 *
	 * @implements TC-COMP-001-FUN16 — compressor compacts 9× redstone into 1 redstone_block in one
	 *     operation (130 ticks), leaving the 10th untouched.
	 * @covers R-GUI-02
	 */
	public static void tcComp001Fun16_compressorCompactsRedstone(GameTestHelper helper) {
		assertConsumesBatchPerOperation(helper, compressor(), Items.REDSTONE, 10, 9,
				Config.compressorDuration, Items.REDSTONE_BLOCK, 1);
	}

	/**
	 * TC-COMP-001-NEG05: 3 glowstone_dust is an underpaid batch — no output, no progress, no loss.
	 *
	 * @implements TC-COMP-001-NEG07 — 3 glowstone_dust is an underpaid batch: no output, no progress,
	 *     no input consumed. Pins the dupe MOD-455 closed.
	 */
	public static void tcComp001Neg07_compressorRejectsPartialGlowstoneBatch(GameTestHelper helper) {
		assertPartialBatchProducesNothing(helper, compressor(), new ItemStack(Items.GLOWSTONE_DUST, 3),
				Config.compressorDuration);
	}

	/**
	 * TC-COMP-001-NEG06: 8 redstone is an underpaid batch — the 9-item price is not negotiable.
	 *
	 * @implements TC-COMP-001-NEG08 — 8 redstone is an underpaid batch: the 9-item price is not
	 *     negotiable.
	 */
	public static void tcComp001Neg08_compressorRejectsPartialRedstoneBatch(GameTestHelper helper) {
		assertPartialBatchProducesNothing(helper, compressor(), new ItemStack(Items.REDSTONE, 8),
				Config.compressorDuration);
	}

	/**
	 * TC-COMP-001-FUN17 (MOD-591): the carbon rod — the mod's largest batch, sixteen coal dust for one.
	 *
	 * <p><b>This pins the RECIPE, not the mechanic.</b> The batch mechanic is already covered from both
	 * sides by glowstone and redstone above, and re-testing it here would only add a scenario that can
	 * never be the first to redden. What has no coverage at all is this recipe's own data: its
	 * ingredient is a TAG ({@code #c:dusts/coal}), which lives in a JSON file and survives a rename in
	 * silence, and its price is the number 16, which nothing but the game ever reads. Mutate either —
	 * retag the ingredient, or edit {@code input_counts} to 15 — and this is what goes red while the
	 * MOD-455 scenarios stay green, which is the sign that it earns its place instead of echoing them.
	 *
	 * <p>Seventeen dust rather than sixteen, so the assertion tells "consumed the batch" apart from
	 * "consumed the slot": a leftover of exactly one proves the machine took 16 and stopped.
	 *
	 * @implements TC-COMP-001-FUN17 — the carbon rod recipe itself: the {@code #c:dusts/coal} tag
	 *     resolves and the sixteen-dust price holds (MOD-591).
	 */
	public static void tcComp001Fun17_compressorCompactsCoalDustIntoCarbonRod(GameTestHelper helper) {
		assertConsumesBatchPerOperation(helper, compressor(), ModContent.COAL_DUST.get(), 17, 16,
				Config.compressorDuration, ModContent.CARBON_ROD.get(), 1);
	}

	/**
	 * TC-COMP-001-NEG09 (MOD-591): 15 coal dust is one short — no rod, no loss, no progress.
	 *
	 * @implements TC-COMP-001-NEG09 — fifteen coal dust is one short of the rod's price (MOD-591).
	 */
	public static void tcComp001Neg09_compressorRejectsPartialCoalDustBatch(GameTestHelper helper) {
		assertPartialBatchProducesNothing(helper, compressor(), new ItemStack(ModContent.COAL_DUST.get(), 15),
				Config.compressorDuration);
	}

	/**
	 * TC-MACH-004-FUN02: extractor consumes exactly 1 blaze_rod per operation, yielding exactly 3× blaze_powder.
	 *
	 * @implements TC-MACH-004-FUN02 — extractor consumes exactly 1 blaze_rod per operation
	 *     (extractorDuration ticks), yielding exactly 3× blaze_powder (multiplied output).
	 * @covers R-GUI-02
	 */
	public static void tcMach004Fun02_extractorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, extractor(), Items.BLAZE_ROD, 4,
				Config.extractorDuration, Items.BLAZE_POWDER, 3);
	}
}
