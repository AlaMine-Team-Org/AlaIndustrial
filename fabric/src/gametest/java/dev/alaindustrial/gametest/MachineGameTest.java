package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.SawmillBlockEntity;
import dev.alaindustrial.block.entity.SawmillMode;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.alaindustrial.Config;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import team.reborn.energy.api.EnergyStorage;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.fabric.FabricEnergyPort;

/**
 * L2 functional suite for the processing machines (macerator, electric furnace, compressor,
 * extractor). They share {@link MachineBlockEntity} (slots 0=input, 1=output) so one generic helper
 * drives all four — the classifier/EP showcase: valid input → output, no power → no output (frozen),
 * invalid input → no output. Migrated from legacy {@code IndustrializationSelfTest.PROCESSING_RECIPES}.
 *
 * <p>Numbers/recipes come from datapack + {@link dev.alaindustrial.Config}; outputs from the recipe.
 */
public class MachineGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	private static final int AMPLE_EU = 8000;      // > any single op's E_op; set directly (bypasses cap)
	private static final int DRIVE_TICKS = 400;    // > longest machine duration (150) + margin

	private static MachineBlockEntity place(GameTestHelper helper, Block block) {
		return AlaGameTestHelper.place(helper, POS, block);
	}

	private static void drive(MachineBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
	}

	/** Positive: powered machine with a valid input produces the expected output (≥ minCount). */
	private void assertProduces(GameTestHelper helper, Block block, ItemStack input, Item expected, int minCount) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = AMPLE_EU;
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
	private void assertNoPowerNoOutput(GameTestHelper helper, Block block, ItemStack input) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = 0;
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
	private void assertNoRecipeNoOutput(GameTestHelper helper, Block block, ItemStack junk) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = AMPLE_EU;
		be.setItem(0, junk);
		drive(be, helper, DRIVE_TICKS);
		if (!be.getItem(1).isEmpty()) {
			helper.fail(block + ": produced output from a non-recipe input");
		}
		helper.succeed();
	}

	// ── Positive (FUN, EP valid class) ──────────────────────────────────────────────

	/** @implements TC-MACH-001-FUN01 — macerator grinds raw iron into 2× iron dust (MOD-095: raw ore
	 *      doubles, like ore blocks — Mekanism/IC2 model; only the ingot path is ×1). @covers R-GUI-02 */
	@GameTest
	public void tcMach001Fun01_maceratorGrindsRawIron(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_IRON, 4), ModItems.IRON_DUST, 2);
	}

	/** @implements TC-MACH-001-FUN-ironOre — macerator grinds an iron ore block into 2× iron dust
	 *      (the ×2 doubling path, via {@code #alaindustrial:macerable_iron}). @covers R-GUI-02 */
	@GameTest
	public void tcMach001FunIronOre_maceratorGrindsIronOre(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.MACERATOR, new ItemStack(Items.IRON_ORE, 4), ModItems.IRON_DUST, 2);
	}

	/**
	 * @implements TC-MACH-002-FUN01 — electric furnace smelts raw iron into an iron ingot via the
	 *     vanilla {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON;
	 *     raw_iron → iron_ingot is served by vanilla alone, so this also proves the fallback works).
	 */
	@GameTest
	public void tcMach002Fun01_furnaceSmeltsRawIron(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(Items.RAW_IRON, 4), Items.IRON_INGOT, 1);
	}

	/** @implements TC-MACH-003-FUN01 — compressor compresses clay balls into a brick. */
	@GameTest
	public void tcMach003Fun01_compressorMakesBrick(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.COMPRESSOR, new ItemStack(Items.CLAY_BALL, 4), Items.BRICK, 1);
	}

	/** @implements TC-MACH-004-FUN01 — extractor extracts blaze powder from a blaze rod. */
	@GameTest
	public void tcMach004Fun01_extractorMakesBlazePowder(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.EXTRACTOR, new ItemStack(Items.BLAZE_ROD, 4), Items.BLAZE_POWDER, 1);
	}

	// ── Negative (NEG) ──────────────────────────────────────────────────────────────

	/** @implements TC-MACH-001-NEG01 — no energy: no output, progress frozen at 0. @covers R-NRG-10 */
	@GameTest
	public void tcMach001Neg01_noPowerNoOutput(GameTestHelper helper) {
		assertNoPowerNoOutput(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_IRON, 4));
	}

	/** @implements TC-MACH-001-NEG02 — non-recipe input (dirt) yields no output even when powered. */
	@GameTest
	public void tcMach001Neg02_nonRecipeNoOutput(GameTestHelper helper) {
		assertNoRecipeNoOutput(helper, ModBlocks.MACERATOR, new ItemStack(Items.DIRT, 4));
	}

	/** @implements TC-MACH-002-NEG01 — electric furnace: no energy → no smelt. @covers R-NRG-10 */
	@GameTest
	public void tcMach002Neg01_furnaceNoPower(GameTestHelper helper) {
		assertNoPowerNoOutput(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(Items.RAW_IRON, 4));
	}

	/**
	 * @implements TC-MACH-001-CON01 — sided automation roles: a hopper/pipe cannot insert into the
	 *     output slot nor extract the unprocessed input; only the output slot is extractable.
	 * @covers R-GUI-05
	 */
	@GameTest
	public void tcMach001Con01_sidedSlotRoles(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, ModBlocks.MACERATOR);
		Direction d = Direction.NORTH;
		if (be.canPlaceItemThroughFace(1, new ItemStack(ModItems.IRON_DUST), d)) {
			helper.fail("automation can insert into the output slot");
		}
		if (be.canTakeItemThroughFace(0, new ItemStack(Items.RAW_IRON), d)) {
			helper.fail("automation can steal the unprocessed input");
		}
		if (!be.canTakeItemThroughFace(1, new ItemStack(ModItems.IRON_DUST), d)) {
			helper.fail("automation cannot extract the output");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-MACH-001-PRF — the data-driven maceration recipe for an iron ore block yields ×2
	 *     and its EU cost equals the shared E_op (machineEuPerTick × maceratorDuration), keeping the JSON
	 *     recipe and {@link dev.alaindustrial.Config} in sync. Ported from
	 *     {@code IndustrializationSelfTest} MACERATOR_MULTIPLIER. @covers R-NRG-04 (E_op)
	 */
	@GameTest
	public void tcMach001Prf_maceratorEopMatchesConfig(GameTestHelper helper) {
		// Looked up through the vanilla RecipeManager (R-14); iron_ore resolves via the
		// #alaindustrial:macerable_iron tag (R-15), proving tag ingredients match. Ore blocks and
		// raw_iron both macerate to ×2 dust (MOD-095, Mekanism/IC2 model); only the ingot path is ×1.
		SingleRecipeInput input = new SingleRecipeInput(new ItemStack(Items.IRON_ORE));
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
	 * @implements TC-MACH-001-NEG03 — full output slot jams the machine: no overflow, progress frozen.
	 *
	 * <p>When output slot is at max stack (64), the machine must not advance progress and must not
	 * create a 65th item. This validates that machines check output feasibility before consuming EU
	 * and ticking progress.
	 */
	@GameTest
	public void tcMach001Neg03_fullOutputJamsMachine(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, ModBlocks.MACERATOR);
		be.getEnergyStorage().amount = AMPLE_EU;
		be.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		be.setItem(1, new ItemStack(ModItems.IRON_DUST, 64)); // output slot at max stack
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

	/**
	 * @implements TC-MACH-001-FUN-copperRaw — macerator grinds raw copper (direct recipe
	 *     {@code raw_copper.json}) into 2× copper dust, mirroring the iron raw path; raw ore doubles (MOD-095).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach001FunCopperRaw_maceratorGrindsRawCopper(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_COPPER, 4), ModItems.COPPER_DUST, 2);
	}

	/**
	 * @implements TC-MACH-001-FUN-goldRaw — macerator grinds raw gold into 2× gold dust (direct
	 *     recipe {@code raw_gold.json}); raw ore doubles (MOD-095).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach001FunGoldRaw_maceratorGrindsRawGold(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_GOLD, 4), ModItems.GOLD_DUST, 2);
	}

	/**
	 * @implements TC-MACH-001-FUN-ironIngot — macerator grinds an iron ingot (direct recipe, not the
	 *     tag) into ×1 dust — the level-2 slitok path, distinct from the ×2 ore/raw path.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach001FunIronIngot_maceratorGrindsIronIngot(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.MACERATOR, new ItemStack(Items.IRON_INGOT, 4), ModItems.IRON_DUST, 1);
	}

	/** @implements TC-EFURN-001-FUN01 — electric furnace: mod recipe dust→ingot, iron_dust path. @covers R-GUI-02 */
	@GameTest
	public void tcEfurn001Fun01_furnaceSmeltsIronDust(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(ModItems.IRON_DUST, 4), Items.IRON_INGOT, 1);
	}

	/**
	 * @implements TC-EFURN-001-FUN02 — electric furnace: vanilla smelting fallback (no mod recipe for
	 *     raw beef) still smelts food via {@code minecraft:smelting}.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(Items.BEEF, 4), Items.COOKED_BEEF, 1);
	}

	/**
	 * @implements TC-EFURN-001-FUN03 — electric furnace smelts sand into glass via the vanilla
	 *     {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcEfurn001Fun03a_furnaceSmeltsSand(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(Items.SAND, 4), Items.GLASS, 1);
	}

	/**
	 * @implements TC-EFURN-001-FUN03 — electric furnace smelts cobblestone into stone via the vanilla
	 *     {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcEfurn001Fun03b_furnaceSmeltsCobblestone(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(Items.COBBLESTONE, 4), Items.STONE, 1);
	}

	/**
	 * @implements TC-EFURN-001-FUN05 — electric furnace vanilla fallback smelts wood into charcoal,
	 *     proving it inherits everything the vanilla furnace can smelt, not just the mod's own list.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(Items.OAK_LOG, 4), Items.CHARCOAL, 1);
	}

	/**
	 * @implements TC-EFURN-001-FUN04 — electric furnace runs at {@code electricFurnaceDuration} ticks
	 *     (100), half the vanilla furnace's 200 ticks: the product must not yet exist just before that
	 *     tick count and must exist once it is reached.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcEfurn001Fun04_furnaceDurationIsHalfVanilla(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, ModBlocks.ELECTRIC_FURNACE);
		be.getEnergyStorage().amount = AMPLE_EU;
		be.setItem(0, new ItemStack(ModItems.IRON_DUST, 4));
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

	/** @implements TC-COMP-001-FUN02 — compressor: copper_dust → copper_ingot. @covers R-GUI-02 */
	@GameTest
	public void tcComp001Fun02_compressorMakesCopperIngot(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.COMPRESSOR, new ItemStack(ModItems.COPPER_DUST, 4), Items.COPPER_INGOT, 1);
	}

	/** @implements TC-COMP-001-FUN03 — compressor: gold_dust → gold_ingot. @covers R-GUI-02 */
	@GameTest
	public void tcComp001Fun03_compressorMakesGoldIngot(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.COMPRESSOR, new ItemStack(ModItems.GOLD_DUST, 4), Items.GOLD_INGOT, 1);
	}

	/** @implements TC-COMP-001-FUN04 — compressor: iron_dust → iron_ingot. @covers R-GUI-02 */
	@GameTest
	public void tcComp001Fun04_compressorMakesIronIngot(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.COMPRESSOR, new ItemStack(ModItems.IRON_DUST, 4), Items.IRON_INGOT, 1);
	}

	/** @implements TC-EXTR-001-FUN02 — extractor: gravel → flint (single-output recipe). @covers R-GUI-02 */
	@GameTest
	public void tcExtr001Fun02a_extractorMakesFlint(GameTestHelper helper) {
		assertProduces(helper, ModBlocks.EXTRACTOR, new ItemStack(Items.GRAVEL, 4), Items.FLINT, 1);
	}

	/**
	 * @implements TC-EXTR-001-FUN06 — extractor: cactus → 2× green_dye. Representative of the plant-derived
	 *     ×2 dye recipes (poppy/dandelion/cornflower/cocoa_beans/sea_pickle/lily_of_the_valley/melon_slice
	 *     all yield ×2 of their dye/seeds) — the new plant-processing niche. Verifies count and 1-per-op.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcExtr001Fun06_extractorMakesGreenDye(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, ModBlocks.EXTRACTOR, Items.CACTUS, 4,
				Config.extractorDuration, Items.DYE.green(), 2);
	}

	/**
	 * @implements TC-EXTR-001-FUN07 — extractor: pumpkin → 5× pumpkin_seeds. The largest multiplier in the
	 *     recipe set (×5) — exercises a distinct stack-fit boundary from the ×3 (blaze_rod) path.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcExtr001Fun07_extractorMakesPumpkinSeeds(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, ModBlocks.EXTRACTOR, Items.PUMPKIN, 4,
				Config.extractorDuration, Items.PUMPKIN_SEEDS, 5);
	}

	// ── 1→1 accounting (FUN02 family) — exactly one input item consumed per operation ─────────────

	/**
	 * Positive: exactly one operation's worth of input is consumed, no more — drives ticks for a
	 * single operation only (not the full DRIVE_TICKS) so a bug that consumes >1 input per op would
	 * be caught by the input-count assertion.
	 */
	private void assertConsumesExactlyOnePerOperation(GameTestHelper helper, Block block, Item inputItem,
			int startCount, int durationTicks, Item expectedOutput, int expectedOutputCount) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = AMPLE_EU;
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
	 * @implements TC-MACH-001-FUN02 — macerator consumes exactly 1 raw_iron per operation (150 ticks),
	 *     leaving 3 of the initial 4 and yielding exactly 2× iron_dust (MOD-095: raw ore doubles, Mekanism/IC2 model).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach001Fun02_maceratorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, ModBlocks.MACERATOR, Items.RAW_IRON, 4,
				Config.maceratorDuration, ModItems.IRON_DUST, 2);
	}

	/**
	 * @implements TC-MACH-002-FUN02 — electric furnace consumes exactly 1 iron_dust per operation
	 *     (electricFurnaceDuration ticks), yielding exactly 1× iron_ingot.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach002Fun02_furnaceConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, ModBlocks.ELECTRIC_FURNACE, ModItems.IRON_DUST, 4,
				Config.electricFurnaceDuration, Items.IRON_INGOT, 1);
	}

	/**
	 * @implements TC-MACH-003-FUN02 — compressor consumes exactly 1 copper_dust per operation
	 *     (compressorDuration ticks); detailed 5-count variant is TC-COMP-001-FUN05.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach003Fun02_compressorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, ModBlocks.COMPRESSOR, ModItems.COPPER_DUST, 4,
				Config.compressorDuration, Items.COPPER_INGOT, 1);
	}

	/**
	 * @implements TC-COMP-001-FUN05 — compressor consumes exactly 1 of 5 copper_dust per operation
	 *     (130 ticks), leaving 4 and yielding exactly 1× copper_ingot.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcComp001Fun05_compressorConsumesExactlyOneOfFive(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, ModBlocks.COMPRESSOR, ModItems.COPPER_DUST, 5,
				Config.compressorDuration, Items.COPPER_INGOT, 1);
	}

	/**
	 * @implements TC-MACH-004-FUN02 — extractor consumes exactly 1 blaze_rod per operation
	 *     (extractorDuration ticks), yielding exactly 3× blaze_powder (multiplied output).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach004Fun02_extractorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		assertConsumesExactlyOnePerOperation(helper, ModBlocks.EXTRACTOR, Items.BLAZE_ROD, 4,
				Config.extractorDuration, Items.BLAZE_POWDER, 3);
	}

	// ── NEG: full output jams the machine, no dupe (parametric across all 4) ───────────────────────

	/**
	 * Negative: output slot at max stack (64) with the recipe's own product jams the machine — no
	 * overflow, progress frozen at 0. Generalizes {@link #tcMach001Neg03_fullOutputJamsMachine} to all
	 * four machines.
	 */
	private void assertFullOutputJamsMachine(GameTestHelper helper, Block block, ItemStack input, Item product) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = AMPLE_EU;
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

	/** @implements TC-MACH-002-NEG03 — electric furnace: full output (64 iron_ingot) jams, no overflow. @covers R-GUI-04 */
	@GameTest
	public void tcMach002Neg03_furnaceFullOutputJamsMachine(GameTestHelper helper) {
		assertFullOutputJamsMachine(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(ModItems.IRON_DUST, 4), Items.IRON_INGOT);
	}

	/** @implements TC-MACH-003-NEG03 — compressor: full output (64 copper_ingot) jams, no overflow. @covers R-GUI-04 */
	@GameTest
	public void tcMach003Neg03_compressorFullOutputJamsMachine(GameTestHelper helper) {
		assertFullOutputJamsMachine(helper, ModBlocks.COMPRESSOR, new ItemStack(ModItems.COPPER_DUST, 4), Items.COPPER_INGOT);
	}

	/**
	 * @implements TC-EXTR-001-NEG01 (61-item leg) — extractor: 61× blaze_powder in output leaves room
	 *     for exactly the ×3 multiplied product (61+3=64) — operation completes, no jam.
	 * @covers R-GUI-04, R-NRG-04
	 */
	@GameTest
	public void tcExtr001Neg01a_multipliedOutputFitsAt61(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, ModBlocks.EXTRACTOR);
		be.getEnergyStorage().amount = AMPLE_EU;
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
	 * @implements TC-EXTR-001-NEG01 (62-item leg) — extractor: 62× blaze_powder in output cannot fit
	 *     the ×3 multiplied product (62+3=65 > max_stack=64) — machine jams, no overflow, no dupe,
	 *     blaze_rod not consumed. This is the multiplied-output analogue of NEG03 (which uses a
	 *     single-count product); ordinary machines never hit this boundary at 62 because their output
	 *     is ×1.
	 * @covers R-GUI-04, R-NRG-04
	 */
	@GameTest
	public void tcExtr001Neg01b_multipliedOutputJamsAt62(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, ModBlocks.EXTRACTOR);
		be.getEnergyStorage().amount = AMPLE_EU;
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
	private void assertWrongItemInOutputNoDupe(GameTestHelper helper, Block block, ItemStack input, ItemStack foreignOutput) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = AMPLE_EU;
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

	/** @implements TC-MACH-001-NEG04 — macerator: cobblestone in output slot → unchanged, no dupe. @covers R-GUI-04 */
	@GameTest
	public void tcMach001Neg04_maceratorWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_IRON, 1),
				new ItemStack(Items.COBBLESTONE, 1));
	}

	/** @implements TC-MACH-002-NEG04 — electric furnace: cobblestone (unrelated) in output slot → unchanged. @covers R-GUI-04 */
	@GameTest
	public void tcMach002Neg04_furnaceWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(ModItems.IRON_DUST, 1),
				new ItemStack(Items.COBBLESTONE, 1));
	}

	/**
	 * @implements TC-COMP-001-NEG02 — compressor: finished iron_ingot in output slot, gold_dust queued
	 *     as new input → output slot's iron_ingot untouched (mismatched product), no dupe.
	 * @covers R-GUI-04
	 */
	@GameTest
	public void tcMach003Neg04_compressorWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, ModBlocks.COMPRESSOR, new ItemStack(ModItems.GOLD_DUST, 1),
				new ItemStack(Items.IRON_INGOT, 1));
	}

	/** @implements TC-MACH-004-NEG04 — extractor: cobblestone (unrelated) in output slot → unchanged. @covers R-GUI-04 */
	@GameTest
	public void tcMach004Neg04_extractorWrongItemInOutputNoDupe(GameTestHelper helper) {
		assertWrongItemInOutputNoDupe(helper, ModBlocks.EXTRACTOR, new ItemStack(Items.BLAZE_ROD, 1),
				new ItemStack(Items.COBBLESTONE, 1));
	}

	// ── NEG: non-recipe input, even fully powered → no output, EU untouched (parametric) ───────────

	/** Negative: a non-recipe input costs no EU even when the machine is fully powered. */
	private void assertNonRecipeNoEuSpent(GameTestHelper helper, Block block, ItemStack junk) {
		MachineBlockEntity be = place(helper, block);
		long startAmount = 800; // direct amount=, not TR insert — matches PERFORMANCE.md buffer for all 4 machines
		be.getEnergyStorage().amount = startAmount;
		be.setItem(0, junk);
		drive(be, helper, DRIVE_TICKS);
		if (!be.getItem(1).isEmpty()) {
			helper.fail(block + ": produced output from a non-recipe input");
		}
		if (be.getEnergyStorage().amount != startAmount) {
			helper.fail(block + ": EU was spent on a non-recipe input, amount now " + be.getEnergyStorage().amount);
		}
		helper.succeed();
	}

	/** @implements TC-MACH-001-NEG05 — macerator: non-recipe input (dirt) does not spend EU even when powered. @covers R-NRG-04 */
	@GameTest
	public void tcMach001Neg05_maceratorNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, ModBlocks.MACERATOR, new ItemStack(Items.DIRT, 1));
	}

	/** @implements TC-MACH-002-NEG05 — electric furnace: non-recipe input (lava_bucket) does not spend EU. @covers R-NRG-04 */
	@GameTest
	public void tcMach002Neg05_furnaceNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(Items.LAVA_BUCKET, 1));
	}

	/**
	 * @implements TC-COMP-001-NEG01 — compressor: item without a compressing recipe (diamond) does not
	 *     spend EU even when the buffer is full.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach003Neg05_compressorNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, ModBlocks.COMPRESSOR, new ItemStack(Items.DIAMOND, 1));
	}

	/**
	 * @implements TC-COMP-001-NEG03 — compressor: raw_iron (ore, not dust) is not a valid compressing
	 *     input — the macerator's output is required first, raw ore is not a shortcut.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcComp001Neg03_compressorRawOreNotAccepted(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, ModBlocks.COMPRESSOR, new ItemStack(Items.RAW_IRON, 1));
	}

	/** @implements TC-MACH-004-NEG05 — extractor: non-recipe input (dirt) does not spend EU even when powered. @covers R-NRG-04 */
	@GameTest
	public void tcMach004Neg05_extractorNonRecipeNoEuSpent(GameTestHelper helper) {
		assertNonRecipeNoEuSpent(helper, ModBlocks.EXTRACTOR, new ItemStack(Items.DIRT, 1));
	}

	// ── NEG: recipe swap mid-operation resets progress (parametric FUN04) ──────────────────────────

	/**
	 * @implements TC-MACH-001-FUN04 — swapping the input item mid-operation (after partial progress)
	 *     resets progress to 0 and starts a fresh operation for the new item; the old input is not
	 *     lost/duped. Parametric across all four machines sharing {@code MachineBlockEntity}.
	 * @covers R-NRG-10
	 */
	private void assertInputSwapMidOpResetsProgress(GameTestHelper helper, Block block, ItemStack inputA,
			ItemStack inputB, int halfwayTicks) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = AMPLE_EU;
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

	/** @implements TC-MACH-001-FUN04 — macerator: raw_iron swapped for raw_copper mid-op resets progress. @covers R-NRG-10 */
	@GameTest
	public void tcMach001Fun04_maceratorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_IRON, 1),
				new ItemStack(Items.RAW_COPPER, 1), Config.maceratorDuration / 2);
	}

	/** @implements TC-MACH-002-FUN04 — electric furnace: iron_dust swapped for sand mid-op resets progress. @covers R-NRG-10 */
	@GameTest
	public void tcMach002Fun04_furnaceInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(ModItems.IRON_DUST, 1),
				new ItemStack(Items.SAND, 1), Config.electricFurnaceDuration / 2);
	}

	/** @implements TC-MACH-003-FUN04 — compressor: copper_dust swapped for iron_dust mid-op resets progress. @covers R-NRG-10 */
	@GameTest
	public void tcMach003Fun04_compressorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, ModBlocks.COMPRESSOR, new ItemStack(ModItems.COPPER_DUST, 1),
				new ItemStack(ModItems.IRON_DUST, 1), Config.compressorDuration / 2);
	}

	/** @implements TC-MACH-004-FUN04 — extractor: blaze_rod swapped for gravel mid-op resets progress. @covers R-NRG-10 */
	@GameTest
	public void tcMach004Fun04_extractorInputSwapResetsProgress(GameTestHelper helper) {
		assertInputSwapMidOpResetsProgress(helper, ModBlocks.EXTRACTOR, new ItemStack(Items.BLAZE_ROD, 1),
				new ItemStack(Items.GRAVEL, 1), Config.extractorDuration / 2);
	}

	// ── STA: lit blockstate tracks active/idle, no light emission (parametric) ─────────────────────

	/**
	 * Positive/negative pair: the block's {@code lit} property switches on while an operation is
	 * progressing (powered + valid input) and switches back off once the machine has no work left
	 * (input exhausted). Mirrors {@code GeneratorGameTest#tcGen001Sta01_litStateTracksBurning} but for
	 * a processing machine's EU-driven progress instead of a burning generator.
	 */
	private void assertLitTracksActive(GameTestHelper helper, Block block, ItemStack singleInput) {
		MachineBlockEntity be = place(helper, block);
		BlockPos abs = be.getBlockPos();
		be.getEnergyStorage().amount = AMPLE_EU;
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

	/** @implements TC-MACH-001-STA01 — macerator lit tracks active/idle, no light emission. @covers R-VIS-01 */
	@GameTest
	public void tcMach001Sta01_maceratorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_IRON, 1));
	}

	/** @implements TC-MACH-002-STA01 — electric furnace lit tracks active/idle, no light emission. @covers R-VIS-01 */
	@GameTest
	public void tcMach002Sta01_furnaceLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(ModItems.IRON_DUST, 1));
	}

	/** @implements TC-COMP-001-STA01 / TC-MACH-003-STA01 — compressor lit tracks active/idle, no light emission. @covers R-VIS-01 */
	@GameTest
	public void tcMach003Sta01_compressorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, ModBlocks.COMPRESSOR, new ItemStack(Items.CLAY_BALL, 1));
	}

	/** @implements TC-MACH-004-STA01 — extractor lit tracks active/idle, no light emission. @covers R-VIS-01 */
	@GameTest
	public void tcMach004Sta01_extractorLitTracksActive(GameTestHelper helper) {
		assertLitTracksActive(helper, ModBlocks.EXTRACTOR, new ItemStack(Items.BLAZE_ROD, 1));
	}

	// ── PRF: E_op exact & E_op−1 (BVA), parametric across all 4 machines ────────────────────────────

	/** BVA: exactly E_op available → operation completes and EU is fully spent (amount==0). */
	private void assertEopExactCompletes(GameTestHelper helper, Block block, ItemStack input, int durationTicks,
			int euPerTick, Item expectedOutput) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = (long) durationTicks * euPerTick;
		be.setItem(0, input);
		drive(be, helper, durationTicks);
		ItemStack out = be.getItem(1);
		if (out.isEmpty() || !out.is(expectedOutput)) {
			helper.fail(block + ": E_op exact (" + (durationTicks * euPerTick) + " EU) did not complete the operation");
		}
		if (be.getEnergyStorage().amount != 0) {
			helper.fail(block + ": E_op exact should leave amount==0 but got " + be.getEnergyStorage().amount);
		}
		helper.succeed();
	}

	/** BVA: E_op−1 available → operation never completes; progress freezes one tick short. */
	private void assertEopMinusOneStalls(GameTestHelper helper, Block block, ItemStack input, int durationTicks,
			int euPerTick) {
		MachineBlockEntity be = place(helper, block);
		be.getEnergyStorage().amount = (long) durationTicks * euPerTick - 1;
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

	/** @implements TC-MACH-001-PRF04 — macerator: E_op=300 exactly → output + amount==0 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcMach001Prf04_maceratorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_IRON, 1),
				Config.maceratorDuration, Config.machineEuPerTick, ModItems.IRON_DUST);
	}

	/** @implements TC-MACH-001-PRF03 — macerator: E_op−1=299 → no output, progress=149/150 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcMach001Prf03_maceratorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, ModBlocks.MACERATOR, new ItemStack(Items.RAW_IRON, 1),
				Config.maceratorDuration, Config.machineEuPerTick);
	}

	/** @implements TC-EFURN-001-PRF01 — electric furnace: E_op=200 exactly → output + amount==0 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcEfurn001Prf01_furnaceEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(ModItems.IRON_DUST, 1),
				Config.electricFurnaceDuration, Config.machineEuPerTick, Items.IRON_INGOT);
	}

	/** @implements TC-EFURN-001-PRF02 — electric furnace: E_op−1=199 → no output, progress=99/100 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcEfurn001Prf02_furnaceEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, ModBlocks.ELECTRIC_FURNACE, new ItemStack(ModItems.IRON_DUST, 1),
				Config.electricFurnaceDuration, Config.machineEuPerTick);
	}

	/** @implements TC-COMP-001-PRF01 — compressor: E_op=260 exactly → output + amount==0 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcComp001Prf01_compressorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, ModBlocks.COMPRESSOR, new ItemStack(ModItems.IRON_DUST, 1),
				Config.compressorDuration, Config.machineEuPerTick, Items.IRON_INGOT);
	}

	/** @implements TC-COMP-001-PRF02 — compressor: E_op−1=259 → no output, progress=129/130 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcComp001Prf02_compressorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, ModBlocks.COMPRESSOR, new ItemStack(ModItems.IRON_DUST, 1),
				Config.compressorDuration, Config.machineEuPerTick);
	}

	/** @implements TC-EXTR-001-PRF01 — extractor: E_op=240 exactly → output + amount==0 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcExtr001Prf01_extractorEopExactCompletes(GameTestHelper helper) {
		assertEopExactCompletes(helper, ModBlocks.EXTRACTOR, new ItemStack(Items.BLAZE_ROD, 1),
				Config.extractorDuration, Config.machineEuPerTick, Items.BLAZE_POWDER);
	}

	/** @implements TC-EXTR-001-PRF02 — extractor: E_op−1=239 → no output, progress=119/120 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcExtr001Prf02_extractorEopMinusOneStalls(GameTestHelper helper) {
		assertEopMinusOneStalls(helper, ModBlocks.EXTRACTOR, new ItemStack(Items.BLAZE_ROD, 1),
				Config.extractorDuration, Config.machineEuPerTick);
	}

	// ── PRF: buffer cap 800 EU via the real Team Reborn insert() path (parametric) ─────────────────

	/**
	 * BVA: inserting far more than the buffer's capacity through the real TR Energy API {@code insert}
	 * (transaction-committed, not the direct {@code amount=} field write used elsewhere in this suite)
	 * must cap at the configured buffer (800 EU / {@code Config.machineBuffer}), never exceeding it.
	 */
	private void assertBufferCapsViaInsert(GameTestHelper helper, Block block, int expectedBuffer) {
		MachineBlockEntity be = place(helper, block);
		EnergyPort storage = be.getEnergyStorage();
		// A single insert() is rate-capped at maxInsert (32 EU/t LV), separate from capacity. Insert
		// repeatedly until the buffer saturates, then verify it caps at the configured buffer (never over).
		for (int i = 0; i < 100; i++) {
			long moved;
			try (Transaction tx = Transaction.openOuter()) {
				moved = storage.insert(8000, FabricEnergyPort.wrap(tx));
				tx.commit();
			}
			if (moved == 0) {
				break;
			}
		}
		if (be.getEnergyStorage().getAmount() != expectedBuffer) {
			helper.fail(block + ": buffer cap via TR insert() expected " + expectedBuffer
					+ " but got " + be.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-MACH-001-PRF02 — macerator: insert(8000) via TR API caps at its own buffer
	 *     ({@code Config.maceratorBuffer}, distinct constant from the shared {@code machineBuffer} used
	 *     by the other three machines — both default to 800 EU per PERFORMANCE.md).
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcMach001Prf02_maceratorBufferCapsViaInsert(GameTestHelper helper) {
		assertBufferCapsViaInsert(helper, ModBlocks.MACERATOR, Config.maceratorBuffer);
	}

	/** @implements TC-MACH-002-PRF02 — electric furnace: insert(8000) via TR API caps at buffer=800 EU. @covers R-NRG-01 */
	@GameTest
	public void tcMach002Prf02_furnaceBufferCapsViaInsert(GameTestHelper helper) {
		assertBufferCapsViaInsert(helper, ModBlocks.ELECTRIC_FURNACE, Config.machineBuffer);
	}

	/** @implements TC-MACH-003-PRF02 — compressor: insert(8000) via TR API caps at buffer=800 EU. @covers R-NRG-01 */
	@GameTest
	public void tcMach003Prf02_compressorBufferCapsViaInsert(GameTestHelper helper) {
		assertBufferCapsViaInsert(helper, ModBlocks.COMPRESSOR, Config.machineBuffer);
	}

	/** @implements TC-MACH-004-PRF02 — extractor: insert(8000) via TR API caps at buffer=800 EU. @covers R-NRG-01 */
	@GameTest
	public void tcMach004Prf02_extractorBufferCapsViaInsert(GameTestHelper helper) {
		assertBufferCapsViaInsert(helper, ModBlocks.EXTRACTOR, Config.machineBuffer);
	}

	// ── CON: pairwise 5 non-FACING faces + FACING face (parametric) ────────────────────────────────

	/**
	 * @implements TC-MACH-001-CON04 — energy face roles across all 6 world faces, default placement
	 *     (FACING=NORTH): the 5 non-FACING faces are IN-only; FACING itself is energy-inert (no port),
	 *     per the human decision D-FACING (R-NRG-03). Matches
	 *     {@code EnergyFaceGameTest#rNrg03_maceratorEveryFaceInOnly}.
	 * @covers R-CON-01, R-NRG-03
	 */
	private void assertAllSixFacesAcceptEnergy(GameTestHelper helper, Block block) {
		helper.setBlock(POS, block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		for (Direction d : Direction.values()) {
			EnergyStorage port = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), d);
			if (d == Direction.NORTH) {
				if (port != null) {
					helper.fail(block + ": FACING face (north) must be inert (no energy port)");
				}
				continue;
			}
			if (port == null || !port.supportsInsertion() || port.supportsExtraction()) {
				helper.fail(block + ": face " + d + " must be IN-only");
			}
		}
		helper.succeed();
	}

	/** @implements TC-MACH-001-CON04 — macerator: 5 non-FACING faces IN-only, FACING inert. @covers R-CON-01, R-NRG-03 */
	@GameTest
	public void tcMach001Con04_maceratorAllFacesAcceptEnergy(GameTestHelper helper) {
		assertAllSixFacesAcceptEnergy(helper, ModBlocks.MACERATOR);
	}

	/** @implements TC-MACH-002-CON04 — electric furnace: 5 non-FACING faces IN-only, FACING inert. @covers R-CON-01, R-NRG-03 */
	@GameTest
	public void tcMach002Con04_furnaceAllFacesAcceptEnergy(GameTestHelper helper) {
		assertAllSixFacesAcceptEnergy(helper, ModBlocks.ELECTRIC_FURNACE);
	}

	/** @implements TC-MACH-003-CON04 — compressor: 5 non-FACING faces IN-only, FACING inert. @covers R-CON-01, R-NRG-03 */
	@GameTest
	public void tcMach003Con04_compressorAllFacesAcceptEnergy(GameTestHelper helper) {
		assertAllSixFacesAcceptEnergy(helper, ModBlocks.COMPRESSOR);
	}

	/** @implements TC-MACH-004-CON04 — extractor: 5 non-FACING faces IN-only, FACING inert. @covers R-CON-01, R-NRG-03 */
	@GameTest
	public void tcMach004Con04_extractorAllFacesAcceptEnergy(GameTestHelper helper) {
		assertAllSixFacesAcceptEnergy(helper, ModBlocks.EXTRACTOR);
	}

	// ── Sawmill (MOD-150): four switchable modes, per-species yield, mode persistence ──────────────

	/**
	 * Place a powered sawmill in the given mode, feed {@code input}, drive exactly ONE operation
	 * ({@code sawmillDuration} ticks), and assert the output is exactly {@code count} of {@code expected}
	 * and exactly one input item was consumed. Driving a single op (not {@link #DRIVE_TICKS}) keeps the
	 * per-op yield assertion exact rather than accumulating across the whole input stack.
	 */
	private void assertSawsInMode(GameTestHelper helper, SawmillMode mode, ItemStack input, Item expected, int count) {
		MachineBlockEntity be = place(helper, ModBlocks.SAWMILL);
		((SawmillBlockEntity) be).setMode(mode);
		be.getEnergyStorage().amount = AMPLE_EU;
		int startCount = input.getCount();
		be.setItem(0, input.copy());
		drive(be, helper, SawmillBlockEntity.DEFAULT_DURATION);
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

	/** @implements TC-SAW-001-FUN01 — PLANKS mode (default): oak log → 6 oak planks (+50% over vanilla 4). */
	@GameTest
	public void tcSaw001Fun01_planksMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.PLANKS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_PLANKS, 6);
	}

	/** @implements TC-SAW-001-FUN02 — PLANKS mode, bamboo: bamboo block → 3 bamboo planks (halved, per vanilla 2/block). */
	@GameTest
	public void tcSaw001Fun02_bambooHalfYield(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.PLANKS, new ItemStack(Items.BAMBOO_BLOCK, 4), Items.BAMBOO_PLANKS, 3);
	}

	/** @implements TC-SAW-001-FUN03 — STICKS mode: oak log (#minecraft:logs) → 12 sticks. */
	@GameTest
	public void tcSaw001Fun03_sticksMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.STICKS, new ItemStack(Items.OAK_LOG, 4), Items.STICK, 12);
	}

	/** @implements TC-SAW-001-FUN04 — SLABS mode: oak log → 12 oak slabs. */
	@GameTest
	public void tcSaw001Fun04_slabsMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.SLABS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_SLAB, 12);
	}

	/** @implements TC-SAW-001-FUN05 — STAIRS mode: oak log → 6 oak stairs. */
	@GameTest
	public void tcSaw001Fun05_stairsMode(GameTestHelper helper) {
		assertSawsInMode(helper, SawmillMode.STAIRS, new ItemStack(Items.OAK_LOG, 4), Items.OAK_STAIRS, 6);
	}

	/**
	 * @implements TC-SAW-001-CON01 — the machine saws ONLY in the active mode. A regression guard: with the
	 *     mode set to STICKS, an oak log must produce sticks — never planks (which the default mode would
	 *     make). Fails if {@code resolveInput} ignored the selected mode.
	 */
	@GameTest
	public void tcSaw001Con01_onlyActiveModeSaws(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, ModBlocks.SAWMILL);
		((SawmillBlockEntity) be).setMode(SawmillMode.STICKS);
		be.getEnergyStorage().amount = AMPLE_EU;
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
	 * @implements TC-SAW-001-CON02 — the active mode survives an NBT round-trip (relog). Set STAIRS, save
	 *     the block entity to NBT, reload into a fresh instance, and assert the mode is still STAIRS.
	 */
	@GameTest
	public void tcSaw001Con02_modePersistsThroughNbt(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, ModBlocks.SAWMILL);
		((SawmillBlockEntity) be).setMode(SawmillMode.STAIRS);

		var registries = helper.getLevel().registryAccess();
		net.minecraft.nbt.CompoundTag tag = be.saveCustomOnly(registries);
		SawmillBlockEntity restored = new SawmillBlockEntity(be.getBlockPos(),
				helper.getLevel().getBlockState(be.getBlockPos()));
		restored.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

		if (restored.getMode() != SawmillMode.STAIRS) {
			helper.fail("sawmill mode lost on NBT round-trip: expected STAIRS but got " + restored.getMode());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-SAW-001-CON03 — switching the mode mid-operation resets progress to 0 (the new mode
	 *     saws a different product, so carrying progress over would be wrong).
	 */
	@GameTest
	public void tcSaw001Con03_modeSwitchResetsProgress(GameTestHelper helper) {
		MachineBlockEntity be = place(helper, ModBlocks.SAWMILL);
		SawmillBlockEntity saw = (SawmillBlockEntity) be;
		saw.setMode(SawmillMode.PLANKS);
		be.getEnergyStorage().amount = AMPLE_EU;
		be.setItem(0, new ItemStack(Items.OAK_LOG, 4));
		drive(be, helper, SawmillBlockEntity.DEFAULT_DURATION / 2);
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
