package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.AbstractProcessingMachineBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.ProcessingMachineStatus;
import dev.alaindustrial.block.entity.SawmillBlockEntity;
import dev.alaindustrial.block.entity.SawmillMode;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral gametest bodies for the processing machines — macerator, electric furnace,
 * compressor, extractor and the sawmill (MOD-446). They share {@link MachineBlockEntity}
 * (slots 0=input, 1=output) so one generic helper drives all of them: valid input → output, no
 * power → no output (frozen), invalid input → no output, exact E_op, jams and no-dupe, lit state.
 *
 * <p>Wrapped by the Fabric {@code MachineGameTest} suite (which keeps the {@code @implements}
 * traceability tags — do NOT duplicate them here, the traceability generator scans this source set
 * too) and registered on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests},
 * {@code machine_*}), so both loaders run the SAME bodies. The eight cases that observe a machine
 * through the Fabric Transfer/Energy API ({@code Transaction} insert, {@code EnergyStorage.SIDED})
 * stay in the Fabric suite: they test that loader's seam, not the machine.
 *
 * <p>Numbers/recipes come from datapack + {@link Config}; outputs from the recipe. Method names
 * mirror the Fabric test names one-to-one so the wrapper is a one-liner and docs that cite
 * {@code MachineGameTest#tc…} keep resolving.
 */
public final class MachineScenarios {

	private MachineScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final int AMPLE_EU = 8000;      // > any single op's E_op; set directly (bypasses cap)
	private static final int DRIVE_TICKS = 400;    // > longest machine duration (150) + margin

	private static MachineBlockEntity place(GameTestHelper helper, Block block) {
		return AlaGameTestHelper.place(helper, POS, block);
	}

	private static void drive(MachineBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
	}

	private static Block macerator() {
		return ModContent.MACERATOR.get();
	}

	private static Block furnace() {
		return ModContent.ELECTRIC_FURNACE.get();
	}

	private static Block compressor() {
		return ModContent.COMPRESSOR.get();
	}

	private static Block extractor() {
		return ModContent.EXTRACTOR.get();
	}

	private static Block sawmill() {
		return ModContent.SAWMILL.get();
	}

	/** Positive: powered machine with a valid input produces the expected output (≥ minCount). */
	private static void assertProduces(GameTestHelper helper, Block block, ItemStack input, Item expected, int minCount) {
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

	// ── Positive (FUN, EP valid class) ──────────────────────────────────────────────

	/** TC-MACH-001-FUN01: macerator grinds raw iron into 2× iron dust (MOD-095: raw ore doubles). */
	public static void tcMach001Fun01_maceratorGrindsRawIron(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.RAW_IRON, 4), ModContent.IRON_DUST.get(), 2);
	}

	/** TC-MACH-001-FUN-ironOre: iron ore block → 2× iron dust via {@code #alaindustrial:macerable_iron}. */
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
	 */
	public static void tcMach002Fun01_furnaceSmeltsRawIron(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.RAW_IRON, 4), Items.IRON_INGOT, 1);
	}

	/** MOD-245: the vanilla smelting recipe is also served by the electric furnace fallback. */
	public static void mod245_furnaceSmeltsRawSulfur(GameTestHelper helper) {
		assertProduces(helper, furnace(),
				new ItemStack(ModContent.RAW_SULFUR.get(), 4), ModContent.SULFUR_DUST.get(), 1);
	}

	/** TC-MACH-003-FUN01: compressor compresses clay balls into a brick. */
	public static void tcMach003Fun01_compressorMakesBrick(GameTestHelper helper) {
		assertProduces(helper, compressor(), new ItemStack(Items.CLAY_BALL, 4), Items.BRICK, 1);
	}

	/** TC-MACH-004-FUN01: extractor extracts blaze powder from a blaze rod. */
	public static void tcMach004Fun01_extractorMakesBlazePowder(GameTestHelper helper) {
		assertProduces(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 4), Items.BLAZE_POWDER, 1);
	}

	// ── Negative (NEG) ──────────────────────────────────────────────────────────────

	/** TC-MACH-001-NEG01: no energy → no output, progress frozen at 0. */
	public static void tcMach001Neg01_noPowerNoOutput(GameTestHelper helper) {
		assertNoPowerNoOutput(helper, macerator(), new ItemStack(Items.RAW_IRON, 4));
	}

	/** TC-MACH-001-NEG02: non-recipe input (dirt) yields no output even when powered. */
	public static void tcMach001Neg02_nonRecipeNoOutput(GameTestHelper helper) {
		assertNoRecipeNoOutput(helper, macerator(), new ItemStack(Items.DIRT, 4));
	}

	/** TC-MACH-002-NEG01: electric furnace, no energy → no smelt. */
	public static void tcMach002Neg01_furnaceNoPower(GameTestHelper helper) {
		assertNoPowerNoOutput(helper, furnace(), new ItemStack(Items.RAW_IRON, 4));
	}

	/**
	 * TC-MACH-001-CON01: sided automation roles — a hopper/pipe cannot insert into the output slot
	 * nor extract the unprocessed input; only the output slot is extractable.
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
	 * TC-MACH-001-PRF: the data-driven maceration recipe for an iron ore block yields ×2 and its EU
	 * cost equals the shared E_op (machineEuPerTick × maceratorDuration), keeping the JSON recipe and
	 * {@link Config} in sync. Ported from {@code IndustrializationSelfTest} MACERATOR_MULTIPLIER.
	 */
	public static void tcMach001Prf_maceratorEopMatchesConfig(GameTestHelper helper) {
		// Looked up through the vanilla RecipeManager (R-14); iron_ore resolves via the
		// #alaindustrial:macerable_iron tag (R-15), proving tag ingredients match. Ore blocks and
		// raw_iron both macerate to ×2 dust (MOD-095, Mekanism/IC2 model); only the ingot path is ×1.
		ProcessingRecipeInput input = new ProcessingRecipeInput(new ItemStack(Items.IRON_ORE));
		AlaProcessingRecipe ironRecipe = ModRecipes.MACERATION.newCheck()
				.getRecipeFor(input, helper.getLevel()).map(RecipeHolder::value).orElse(null);
		if (ironRecipe == null) {
			helper.fail("no maceration recipe for iron_ore (datapack not loaded?)");
			return;
		}
		int count = ironRecipe.assemble(input).getCount();
		if (count != 2) {
			helper.fail("iron_ore maceration count expected 2 but got " + count);
		}
		int eOp = Config.machineEuPerTick * Config.maceratorDuration;
		if (ironRecipe.energy() / Config.machineEuPerTick != Config.maceratorDuration) {
			helper.fail("raw_iron maceration E_op mismatch: energy=" + ironRecipe.energy()
					+ " but machineEuPerTick(" + Config.machineEuPerTick + ")×maceratorDuration("
					+ Config.maceratorDuration + ")=" + eOp);
		}
		helper.succeed();
	}

	/**
	 * TC-MACH-001-NEG03: full output slot jams the machine — no overflow, progress frozen.
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

	// ── Extra recipes (FUN) ──────────────────────────────────────────────────────────

	/** TC-MACH-001-FUN-copperRaw: raw copper (direct recipe {@code raw_copper.json}) → 2× copper dust. */
	public static void tcMach001FunCopperRaw_maceratorGrindsRawCopper(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.RAW_COPPER, 4), ModContent.COPPER_DUST.get(), 2);
	}

	/** TC-MACH-001-FUN-goldRaw: raw gold → 2× gold dust (direct recipe {@code raw_gold.json}). */
	public static void tcMach001FunGoldRaw_maceratorGrindsRawGold(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.RAW_GOLD, 4), ModContent.GOLD_DUST.get(), 2);
	}

	/** TC-MACH-001-FUN-ironIngot: an iron ingot (direct recipe, not the tag) → ×1 dust, distinct from the ×2 ore/raw path. */
	public static void tcMach001FunIronIngot_maceratorGrindsIronIngot(GameTestHelper helper) {
		assertProduces(helper, macerator(), new ItemStack(Items.IRON_INGOT, 4), ModContent.IRON_DUST.get(), 1);
	}

	/** TC-EFURN-001-FUN01: electric furnace, mod recipe dust→ingot, iron_dust path. */
	public static void tcEfurn001Fun01_furnaceSmeltsIronDust(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 4), Items.IRON_INGOT, 1);
	}

	/** TC-EFURN-001-FUN02: vanilla smelting fallback (no mod recipe for raw beef) still smelts food. */
	public static void tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.BEEF, 4), Items.COOKED_BEEF, 1);
	}

	/** TC-EFURN-001-FUN03 (sand leg): sand → glass via the vanilla {@code minecraft:smelting} fallback. */
	public static void tcEfurn001Fun03a_furnaceSmeltsSand(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.SAND, 4), Items.GLASS, 1);
	}

	/** TC-EFURN-001-FUN03 (cobblestone leg): cobblestone → stone via the vanilla fallback. */
	public static void tcEfurn001Fun03b_furnaceSmeltsCobblestone(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.COBBLESTONE, 4), Items.STONE, 1);
	}

	/** TC-EFURN-001-FUN05: the vanilla fallback smelts wood into charcoal — everything the vanilla furnace can. */
	public static void tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal(GameTestHelper helper) {
		assertProduces(helper, furnace(), new ItemStack(Items.OAK_LOG, 4), Items.CHARCOAL, 1);
	}

	/**
	 * TC-EFURN-001-FUN04: the electric furnace runs at {@code electricFurnaceDuration} ticks (100),
	 * half the vanilla furnace's 200: the product must not yet exist just before that tick count and
	 * must exist once it is reached.
	 */
	public static void tcEfurn001Fun04_furnaceDurationIsHalfVanilla(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, furnace());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(ModContent.IRON_DUST.get(), 4));
		drive(be, helper, Config.electricFurnaceDuration - 1);
		if (!be.getItem(1).isEmpty()) {
			helper.fail("furnace finished before electricFurnaceDuration (" + Config.electricFurnaceDuration + ") ticks");
		}
		drive(be, helper, 1);
		if (be.getItem(1).isEmpty() || !be.getItem(1).is(Items.IRON_INGOT)) {
			helper.fail("furnace did not finish exactly at electricFurnaceDuration ticks");
		}
		helper.succeed();
	}

	/** TC-COMP-001-FUN02: compressor, copper_dust → copper_ingot. */
	public static void tcComp001Fun02_compressorMakesCopperIngot(GameTestHelper helper) {
		assertProduces(helper, compressor(), new ItemStack(ModContent.COPPER_DUST.get(), 4), Items.COPPER_INGOT, 1);
	}

	/** TC-COMP-001-FUN03: compressor, gold_dust → gold_ingot. */
	public static void tcComp001Fun03_compressorMakesGoldIngot(GameTestHelper helper) {
		assertProduces(helper, compressor(), new ItemStack(ModContent.GOLD_DUST.get(), 4), Items.GOLD_INGOT, 1);
	}

	/** TC-COMP-001-FUN04: compressor, iron_dust → iron_ingot. */
	public static void tcComp001Fun04_compressorMakesIronIngot(GameTestHelper helper) {
		assertProduces(helper, compressor(), new ItemStack(ModContent.IRON_DUST.get(), 4), Items.IRON_INGOT, 1);
	}

	/** TC-EXTR-001-FUN02: extractor, gravel → flint (single-output recipe). */
	public static void tcExtr001Fun02a_extractorMakesFlint(GameTestHelper helper) {
		assertProduces(helper, extractor(), new ItemStack(Items.GRAVEL, 4), Items.FLINT, 1);
	}

	/**
	 * TC-EXTR-001-FUN06: extractor, cactus → 2× green_dye. Representative of the plant-derived ×2 dye
	 * recipes — the plant-processing niche. Verifies count and 1-per-op.
	 */
	public static void tcExtr001Fun06_extractorMakesGreenDye(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, extractor(), Items.CACTUS, 4,
				Config.extractorDuration, Items.DYE.green(), 2);
	}

	/**
	 * TC-EXTR-001-FUN07: extractor, pumpkin → 5× pumpkin_seeds. The largest multiplier in the recipe
	 * set (×5) — exercises a distinct stack-fit boundary from the ×3 (blaze_rod) path.
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

	/** TC-MACH-001-FUN02: macerator consumes exactly 1 raw_iron per operation, yielding exactly 2× iron_dust. */
	public static void tcMach001Fun02_maceratorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, macerator(), Items.RAW_IRON, 4,
				Config.maceratorDuration, ModContent.IRON_DUST.get(), 2);
	}

	/** TC-MACH-002-FUN02: electric furnace consumes exactly 1 iron_dust per operation, yielding 1× iron_ingot. */
	public static void tcMach002Fun02_furnaceConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, furnace(), ModContent.IRON_DUST.get(), 4,
				Config.electricFurnaceDuration, Items.IRON_INGOT, 1);
	}

	/** TC-MACH-003-FUN02: compressor consumes exactly 1 copper_dust per operation. */
	public static void tcMach003Fun02_compressorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, compressor(), ModContent.COPPER_DUST.get(), 4,
				Config.compressorDuration, Items.COPPER_INGOT, 1);
	}

	/** TC-COMP-001-FUN05: compressor consumes exactly 1 of 5 copper_dust per operation, leaving 4. */
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

	/** TC-COMP-001-FUN14: compressor, 4× glowstone_dust → 1 glowstone, leaving the 5th dust untouched. */
	public static void tcComp001Fun15_compressorCompactsGlowstoneDust(GameTestHelper helper) {
		assertConsumesBatchPerOperation(helper, compressor(), Items.GLOWSTONE_DUST, 5, 4,
				Config.compressorDuration, Items.GLOWSTONE, 1);
	}

	/** TC-COMP-001-FUN15: compressor, 9× redstone → 1 redstone_block, leaving the 10th untouched. */
	public static void tcComp001Fun16_compressorCompactsRedstone(GameTestHelper helper) {
		assertConsumesBatchPerOperation(helper, compressor(), Items.REDSTONE, 10, 9,
				Config.compressorDuration, Items.REDSTONE_BLOCK, 1);
	}

	/** TC-COMP-001-NEG05: 3 glowstone_dust is an underpaid batch — no output, no progress, no loss. */
	public static void tcComp001Neg07_compressorRejectsPartialGlowstoneBatch(GameTestHelper helper) {
		assertPartialBatchProducesNothing(helper, compressor(), new ItemStack(Items.GLOWSTONE_DUST, 3),
				Config.compressorDuration);
	}

	/** TC-COMP-001-NEG06: 8 redstone is an underpaid batch — the 9-item price is not negotiable. */
	public static void tcComp001Neg08_compressorRejectsPartialRedstoneBatch(GameTestHelper helper) {
		assertPartialBatchProducesNothing(helper, compressor(), new ItemStack(Items.REDSTONE, 8),
				Config.compressorDuration);
	}

	// ── Status channel (MOD-458): the machine says WHY it is stalled ───────────────────────────────

	private static AbstractProcessingMachineBlockEntity processing(GameTestHelper helper, Block block) {
		return (AbstractProcessingMachineBlockEntity) place(helper, block);
	}

	/**
	 * Assert BOTH halves of the readout every time. {@code status()} is the server's own verdict; the
	 * {@code DATA_STATUS} channel is what a screen actually reads — and the two can part company, because
	 * a subclass that appends channels of its own supplies its own bridge (the Sawmill does, for its mode).
	 * Checking only the field would leave that bridge untested and the caption blank in game.
	 */
	private static void assertStatus(GameTestHelper helper, AbstractProcessingMachineBlockEntity be,
			ProcessingMachineStatus expected, String what) {
		if (be.status() != expected) {
			helper.fail(what + ": expected " + expected + " but the machine reports " + be.status());
		}
		int wire = be.getDataAccess().get(AbstractProcessingMachineBlockEntity.DATA_STATUS);
		if (wire != expected.ordinal()) {
			helper.fail(what + ": readout channel carries ordinal " + wire + " ("
					+ ProcessingMachineStatus.byOrdinal(wire) + ") instead of " + expected);
		}
	}

	/** TC-COMP-001-GUI06: a partial batch names itself — and stops the moment it is topped up. */
	public static void tcComp001Gui06_compressorReportsPartialBatch(GameTestHelper helper) {
		AbstractProcessingMachineBlockEntity be = processing(helper, compressor());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);

		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.NO_INPUT, "an empty compressor");

		be.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 3));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.NOT_ENOUGH_INPUT, "3 of a 4-dust batch");

		// The redstone leftover: 64 / 9 parks exactly one item in the slot after every full stack, which
		// without this caption is the single most jam-looking state the compressor can reach.
		be.setItem(0, new ItemStack(Items.REDSTONE, 1));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.NOT_ENOUGH_INPUT, "1 of a 9-redstone batch");

		be.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 4));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.READY, "a full 4-dust batch");
		helper.succeed();
	}

	/** TC-COMP-001-GUI07: the two stalls that are not about the batch — a wrong item and a jammed output. */
	public static void tcComp001Gui07_compressorReportsWrongItemAndJammedOutput(GameTestHelper helper) {
		AbstractProcessingMachineBlockEntity be = processing(helper, compressor());
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);

		// setItem bypasses canPlaceItem deliberately: a datapack reload or a /setblock can leave an item in
		// the slot that no longer resolves, and that is exactly the state worth captioning.
		be.setItem(0, new ItemStack(Items.DIAMOND, 1));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.NO_RECIPE, "an item the compressor cannot press");

		be.setItem(0, new ItemStack(Items.CLAY_BALL, 8));
		be.setItem(1, new ItemStack(Items.BRICK, 64));
		drive(be, helper, 1);
		assertStatus(helper, be, ProcessingMachineStatus.OUTPUT_BLOCKED, "a full output slot");
		helper.succeed();
	}

	/**
	 * TC-COMP-001-GUI08: an empty buffer is captioned only once it means something.
	 *
	 * <p>The second half is the regression this case exists for. A machine fed just under its draw works
	 * every other tick; a bare {@code buffer < cost} test would strobe "No energy" at 10 Hz while the arrow
	 * visibly advances. One LV solar panel against one LV machine is exactly that setup, so this is an
	 * ordinary early-game base, not a contrived one.
	 */
	public static void tcComp001Gui08_compressorReportsStarvationButNotTrickle(GameTestHelper helper) {
		AbstractProcessingMachineBlockEntity be = processing(helper, compressor());
		be.setItem(0, new ItemStack(Items.CLAY_BALL, 8));
		be.getEnergyStorage().setAmountUntracked(0);
		// Two unpaid evaluations, and an idle machine sleeps 40 ticks between them (R-29).
		drive(be, helper, 90);
		assertStatus(helper, be, ProcessingMachineStatus.NO_ENERGY, "input but no power at all");

		int cost = Config.machineEuPerTick;
		int supply = cost - 1;
		BlockPos pos = be.getBlockPos();
		boolean hasWorked = false;
		int lastProgress = be.getDataAccess().get(2);
		for (int tick = 0; tick < 200; tick++) {
			be.getEnergyStorage().setAmountUntracked(be.getEnergyStorage().getAmount() + supply);
			be.wake(); // an arriving packet wakes the machine, the way the buffer's commit hook does in world
			be.serverTick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos));
			int progress = be.getDataAccess().get(2);
			hasWorked |= progress > lastProgress;
			lastProgress = progress;
			// Assertions start once the machine has visibly worked. The invariant being pinned is about the
			// STEADY state of a trickle-fed machine; the one transition tick out of a dead buffer is not part
			// of it, and there "no energy" is simply true — the machine has not managed a paid tick in ninety.
			if (hasWorked && be.status() == ProcessingMachineStatus.NO_ENERGY) {
				helper.fail("trickling at " + supply + " EU/t against " + cost + " EU/t reported NO_ENERGY on"
						+ " tick " + tick + ", after the machine had already resumed making progress");
			}
		}
		// Without this the loop above is vacuous: a machine that never worked also never says NO_ENERGY.
		if (!hasWorked) {
			helper.fail("the trickle half advanced no progress at all — it proves nothing");
		}
		helper.succeed();
	}

	/** TC-MACH-004-FUN02: extractor consumes exactly 1 blaze_rod per operation, yielding exactly 3× blaze_powder. */
	public static void tcMach004Fun02_extractorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, extractor(), Items.BLAZE_ROD, 4,
				Config.extractorDuration, Items.BLAZE_POWDER, 3);
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

	/** TC-MACH-002-NEG03: electric furnace, full output (64 iron_ingot) jams, no overflow. */
	public static void tcMach002Neg03_furnaceFullOutputJamsMachine(GameTestHelper helper) {
		assertFullOutputJamsMachine(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 4), Items.IRON_INGOT);
	}

	/** TC-MACH-003-NEG03: compressor, full output (64 copper_ingot) jams, no overflow. */
	public static void tcMach003Neg03_compressorFullOutputJamsMachine(GameTestHelper helper) {
		assertFullOutputJamsMachine(helper, compressor(), new ItemStack(ModContent.COPPER_DUST.get(), 4), Items.COPPER_INGOT);
	}

	/**
	 * TC-EXTR-001-NEG01 (61-item leg): 61× blaze_powder in output leaves room for exactly the ×3
	 * multiplied product (61+3=64) — operation completes, no jam.
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
	private static void assertWrongItemInOutputNoDupe(GameTestHelper helper, Block block, ItemStack input, ItemStack foreignOutput) {
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

	/** TC-MACH-001-NEG04: macerator, cobblestone in output slot → unchanged, no dupe. */
	public static void tcMach001Neg04_maceratorWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, macerator(), new ItemStack(Items.RAW_IRON, 1),
				new ItemStack(Items.COBBLESTONE, 1));
	}

	/** TC-MACH-002-NEG04: electric furnace, cobblestone (unrelated) in output slot → unchanged. */
	public static void tcMach002Neg04_furnaceWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				new ItemStack(Items.COBBLESTONE, 1));
	}

	/**
	 * TC-COMP-001-NEG02: compressor, finished iron_ingot in output slot, gold_dust queued as new
	 * input → the output slot's iron_ingot is untouched (mismatched product), no dupe.
	 */
	public static void tcMach003Neg04_compressorWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, compressor(), new ItemStack(ModContent.GOLD_DUST.get(), 1),
				new ItemStack(Items.IRON_INGOT, 1));
	}

	/** TC-MACH-004-NEG04: extractor, cobblestone (unrelated) in output slot → unchanged. */
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
			helper.fail(block + ": EU was spent on a non-recipe input, amount now " + be.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/** TC-MACH-001-NEG05: macerator, non-recipe input (dirt) does not spend EU even when powered. */
	public static void tcMach001Neg05_maceratorNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, macerator(), new ItemStack(Items.DIRT, 1));
	}

	/** TC-MACH-002-NEG05: electric furnace, non-recipe input (lava_bucket) does not spend EU. */
	public static void tcMach002Neg05_furnaceNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, furnace(), new ItemStack(Items.LAVA_BUCKET, 1));
	}

	/** TC-COMP-001-NEG01: compressor, item without a compressing recipe (diamond) does not spend EU. */
	public static void tcMach003Neg05_compressorNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, compressor(), new ItemStack(Items.DIAMOND, 1));
	}

	/**
	 * TC-COMP-001-NEG03: compressor, raw_iron (ore, not dust) is not a valid compressing input — the
	 * macerator's output is required first, raw ore is not a shortcut.
	 */
	public static void tcComp001Neg03_compressorRawOreNotAccepted(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, compressor(), new ItemStack(Items.RAW_IRON, 1));
	}

	/** TC-MACH-004-NEG05: extractor, non-recipe input (dirt) does not spend EU even when powered. */
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

	/** TC-MACH-001-FUN04: macerator, raw_iron swapped for raw_copper mid-op resets progress. */
	public static void tcMach001Fun04_maceratorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, macerator(), new ItemStack(Items.RAW_IRON, 1),
				new ItemStack(Items.RAW_COPPER, 1), Config.maceratorDuration / 2);
	}

	/** TC-MACH-002-FUN04: electric furnace, iron_dust swapped for sand mid-op resets progress. */
	public static void tcMach002Fun04_furnaceInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				new ItemStack(Items.SAND, 1), Config.electricFurnaceDuration / 2);
	}

	/** TC-MACH-003-FUN04: compressor, copper_dust swapped for iron_dust mid-op resets progress. */
	public static void tcMach003Fun04_compressorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, compressor(), new ItemStack(ModContent.COPPER_DUST.get(), 1),
				new ItemStack(ModContent.IRON_DUST.get(), 1), Config.compressorDuration / 2);
	}

	/** TC-MACH-004-FUN04: extractor, blaze_rod swapped for gravel mid-op resets progress. */
	public static void tcMach004Fun04_extractorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1),
				new ItemStack(Items.GRAVEL, 1), Config.extractorDuration / 2);
	}

	// ── STA: lit blockstate tracks active/idle, no light emission (parametric) ─────────────────────

	/**
	 * Positive/negative pair: the block's {@code lit} property switches on while an operation is
	 * progressing (powered + valid input) and switches back off once the machine has no work left
	 * (input exhausted). Mirrors {@code GeneratorGameTest#tcGen001Sta01_litStateTracksBurning} but for
	 * a processing machine's EU-driven progress instead of a burning generator.
	 */
	private static void assertLitTracksActive(GameTestHelper helper, Block block, ItemStack singleInput) {
		MachineBlockEntity be = place(helper, block);
		BlockPos abs = be.getBlockPos();
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, singleInput.copy());
		drive(be, helper, 3);
		if (!helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail(block + ": must be LIT while actively processing");
		}
		// Drain the input so the machine has nothing left to process; give it a tick to notice and
		// clear LIT via updateLit(false).
		be.setItem(0, ItemStack.EMPTY);
		drive(be, helper, 3);
		if (helper.getLevel().getBlockState(abs).getValue(BlockStateProperties.LIT)) {
			helper.fail(block + ": must not stay LIT once there is no input left to process");
		}
		helper.succeed();
	}

	/** TC-MACH-001-STA01: macerator lit tracks active/idle, no light emission. */
	public static void tcMach001Sta01_maceratorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, macerator(), new ItemStack(Items.RAW_IRON, 1));
	}

	/** TC-MACH-002-STA01: electric furnace lit tracks active/idle, no light emission. */
	public static void tcMach002Sta01_furnaceLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1));
	}

	/** TC-COMP-001-STA01 / TC-MACH-003-STA01: compressor lit tracks active/idle, no light emission. */
	public static void tcMach003Sta01_compressorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, compressor(), new ItemStack(Items.CLAY_BALL, 1));
	}

	/** TC-MACH-004-STA01: extractor lit tracks active/idle, no light emission. */
	public static void tcMach004Sta01_extractorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1));
	}

	// ── PRF: E_op exact & E_op−1 (BVA), parametric across all 4 machines ────────────────────────────

	/** BVA: exactly E_op available → operation completes and EU is fully spent (amount==0). */
	private static void assertEopExactCompletes(GameTestHelper helper, Block block, ItemStack input, int durationTicks,
			int euPerTick, Item expectedOutput) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked((long) durationTicks * euPerTick);
		be.setItem(0, input);
		drive(be, helper, durationTicks);
		ItemStack out = be.getItem(1);
		if (out.isEmpty() || !out.is(expectedOutput)) {
			helper.fail(block + ": E_op exact (" + (durationTicks * euPerTick) + " EU) did not complete the operation");
		}
		if (be.getEnergyStorage().getAmount() != 0) {
			helper.fail(block + ": E_op exact should leave amount==0 but got " + be.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/** BVA: E_op−1 available → operation never completes; progress freezes one tick short. */
	private static void assertEopMinusOneStalls(GameTestHelper helper, Block block, ItemStack input, int durationTicks,
			int euPerTick) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().setAmountUntracked((long) durationTicks * euPerTick - 1);
		be.setItem(0, input);
		drive(be, helper, DRIVE_TICKS);
		if (!be.getItem(1).isEmpty()) {
			helper.fail(block + ": E_op−1 (one EU short) must not produce any output");
		}
		int progress = be.getDataAccess().get(2);
		if (progress != durationTicks - 1) {
			helper.fail(block + ": E_op−1 progress expected " + (durationTicks - 1) + " but got " + progress);
		}
		helper.succeed();
	}

	/** TC-MACH-001-PRF04: macerator, E_op=300 exactly → output + amount==0 (BVA). */
	public static void tcMach001Prf04_maceratorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, macerator(), new ItemStack(Items.RAW_IRON, 1),
				Config.maceratorDuration, Config.machineEuPerTick, ModContent.IRON_DUST.get());
	}

	/** TC-MACH-001-PRF03: macerator, E_op−1=299 → no output, progress=149/150 (BVA). */
	public static void tcMach001Prf03_maceratorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, macerator(), new ItemStack(Items.RAW_IRON, 1),
				Config.maceratorDuration, Config.machineEuPerTick);
	}

	/** TC-EFURN-001-PRF01: electric furnace, E_op=200 exactly → output + amount==0 (BVA). */
	public static void tcEfurn001Prf01_furnaceEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				Config.electricFurnaceDuration, Config.machineEuPerTick, Items.IRON_INGOT);
	}

	/** TC-EFURN-001-PRF02: electric furnace, E_op−1=199 → no output, progress=99/100 (BVA). */
	public static void tcEfurn001Prf02_furnaceEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, furnace(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				Config.electricFurnaceDuration, Config.machineEuPerTick);
	}

	/** TC-COMP-001-PRF01: compressor, E_op=260 exactly → output + amount==0 (BVA). */
	public static void tcComp001Prf01_compressorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, compressor(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				Config.compressorDuration, Config.machineEuPerTick, Items.IRON_INGOT);
	}

	/** TC-COMP-001-PRF02: compressor, E_op−1=259 → no output, progress=129/130 (BVA). */
	public static void tcComp001Prf02_compressorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, compressor(), new ItemStack(ModContent.IRON_DUST.get(), 1),
				Config.compressorDuration, Config.machineEuPerTick);
	}

	/** TC-EXTR-001-PRF01: extractor, E_op=240 exactly → output + amount==0 (BVA). */
	public static void tcExtr001Prf01_extractorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1),
				Config.extractorDuration, Config.machineEuPerTick, Items.BLAZE_POWDER);
	}

	/** TC-EXTR-001-PRF02: extractor, E_op−1=239 → no output, progress=119/120 (BVA). */
	public static void tcExtr001Prf02_extractorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, extractor(), new ItemStack(Items.BLAZE_ROD, 1),
				Config.extractorDuration, Config.machineEuPerTick);
	}

	// ── Sawmill (MOD-150): four switchable modes, per-species yield, mode persistence ──────────────

	/**
	 * Place a powered sawmill in the given mode, feed {@code input}, drive exactly ONE operation
	 * ({@code sawmillDuration} ticks), and assert the output is exactly {@code count} of {@code expected}
	 * and exactly one input item was consumed. Driving a single op (not {@link #DRIVE_TICKS}) keeps the
	 * per-op yield assertion exact rather than accumulating across the whole input stack.
	 */
	private static void assertSawsInMode(GameTestHelper helper, SawmillMode mode, ItemStack input, Item expected, int count) {
		MachineBlockEntity be = place(helper, sawmill());
		((SawmillBlockEntity) be).setMode(mode);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		int startCount = input.getCount();
		be.setItem(0, input.copy());
		drive(be, helper, Config.sawmillDuration);
		ItemStack out = be.getItem(1);
		if (out.isEmpty() || !out.is(expected) || out.getCount() != count) {
			helper.fail("sawmill[" + mode + "]: expected exactly " + count + "× " + expected + " but got "
					+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
		}
		ItemStack in = be.getItem(0);
		if (in.getCount() != startCount - 1) {
			helper.fail("sawmill[" + mode + "]: expected exactly one input consumed (" + (startCount - 1)
					+ " left) but got " + in.getCount());
		}
		helper.succeed();
	}

	/** TC-SAW-001-FUN01: PLANKS mode (default), oak log → 6 oak planks (+50% over vanilla 4). */
	public static void tcSaw001Fun01_planksMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.PLANKS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_PLANKS, 6);
	}

	/** TC-SAW-001-FUN02: PLANKS mode, bamboo block → 3 bamboo planks (halved, per vanilla 2/block). */
	public static void tcSaw001Fun02_bambooHalfYield(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.PLANKS, new ItemStack(Items.BAMBOO_BLOCK, 4), Items.BAMBOO_PLANKS, 3);
	}

	/** TC-SAW-001-FUN03: STICKS mode, oak log (#minecraft:logs) → 18 sticks (MOD-215: 1.5× the free 12). */
	public static void tcSaw001Fun03_sticksMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.STICKS, new ItemStack(Items.OAK_LOG, 4), Items.STICK, 18);
	}

	/** TC-SAW-001-FUN04: SLABS mode, oak log → 18 oak slabs (MOD-215: 1.5× the 12 from PLANKS + a workbench). */
	public static void tcSaw001Fun04_slabsMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.SLABS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_SLAB, 18);
	}

	/** TC-SAW-001-FUN05: STAIRS mode, oak log → 6 oak stairs. */
	public static void tcSaw001Fun05_stairsMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.STAIRS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_STAIRS, 6);
	}

	/**
	 * TC-SAW-001-CON01: the machine saws ONLY in the active mode. A regression guard: with the mode set
	 * to STICKS, an oak log must produce sticks — never planks (which the default mode would make).
	 * Fails if {@code resolveInput} ignored the selected mode.
	 */
	public static void tcSaw001Con01_onlyActiveModeSaws(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, sawmill());
		((SawmillBlockEntity) be).setMode(SawmillMode.STICKS);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(Items.OAK_LOG, 4));
		drive(be, helper, DRIVE_TICKS);
		ItemStack out = be.getItem(1);
		if (out.isEmpty() || !out.is(Items.STICK)) {
			helper.fail("sawmill in STICKS mode did not produce sticks from a log: "
					+ (out.isEmpty() ? "empty" : out.getCount() + "× " + out.getItem()));
		}
		if (out.is(Items.OAK_PLANKS)) {
			helper.fail("sawmill ignored the active mode and produced planks");
		}
		helper.succeed();
	}

	/**
	 * TC-SAW-001-CON02: the active mode survives an NBT round-trip (relog). Set STAIRS, save the block
	 * entity to NBT, reload into a fresh instance, and assert the mode is still STAIRS.
	 */
	public static void tcSaw001Con02_modePersistsThroughNbt(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, sawmill());
		((SawmillBlockEntity) be).setMode(SawmillMode.STAIRS);

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = be.saveCustomOnly(registries);
		SawmillBlockEntity restored = new SawmillBlockEntity(be.getBlockPos(),
				helper.getLevel().getBlockState(be.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getMode() != SawmillMode.STAIRS) {
			helper.fail("sawmill mode lost on NBT round-trip: expected STAIRS but got " + restored.getMode());
		}
		helper.succeed();
	}

	/**
	 * TC-SAW-001-CON03: switching the mode mid-operation resets progress to 0 (the new mode saws a
	 * different product, so carrying progress over would be wrong).
	 */
	public static void tcSaw001Con03_modeSwitchResetsProgress(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, sawmill());
		SawmillBlockEntity saw = (SawmillBlockEntity) be;
		saw.setMode(SawmillMode.PLANKS);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(0, new ItemStack(Items.OAK_LOG, 4));
		drive(be, helper, Config.sawmillDuration / 2);
		if (be.getDataAccess().get(2) <= 0) {
			helper.fail("sawmill made no progress before the mode switch");
		}
		saw.setMode(SawmillMode.SLABS);
		if (be.getDataAccess().get(2) != 0) {
			helper.fail("sawmill progress did not reset to 0 after switching mode");
		}
		helper.succeed();
	}
}
