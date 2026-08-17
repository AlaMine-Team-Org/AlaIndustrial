package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import dev.alaindustrial.Config;
import team.reborn.energy.api.EnergyStorage;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.fabric.FabricEnergyPort;

/**
 * Fabric lane for the processing machines (macerator, electric furnace, compressor, extractor,
 * sawmill). Migrated from legacy {@code IndustrializationSelfTest.PROCESSING_RECIPES}.
 *
 * <p>Since MOD-446 the loader-neutral bodies live in {@link MachineScenarios} and the NeoForge lane
 * registers the same set ({@code machine_*}); each wrapper here keeps its {@code @implements} tag so
 * traceability and the block docs that cite {@code MachineGameTest#tc…} are unchanged. Only the eight
 * cases that look at a machine THROUGH the Fabric Transfer/Energy API stay as real bodies in this
 * file — {@code *Prf02_*BufferCapsViaInsert} (transaction-committed {@code insert}) and
 * {@code *Con04_*AllFacesAcceptEnergy} ({@code EnergyStorage.SIDED}) — because they test this
 * loader's seam, not the machine.
 *
 * <p>Numbers/recipes come from datapack + {@link dev.alaindustrial.Config}; outputs from the recipe.
 */
public class MachineGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static MachineBlockEntity place(GameTestHelper helper, Block block) {
		return AlaGameTestHelper.place(helper, POS, block);
	}

	// ── Positive (FUN, EP valid class) ──────────────────────────────────────────────

	/** @implements TC-MACH-001-FUN01 — macerator grinds raw iron into 2× iron dust (MOD-095: raw ore
	 *      doubles, like ore blocks — Mekanism/IC2 model; only the ingot path is ×1). @covers R-GUI-02 */
	@GameTest
	public void tcMach001Fun01_maceratorGrindsRawIron(GameTestHelper helper) {
		MachineScenarios.tcMach001Fun01_maceratorGrindsRawIron(helper);
	}

	/** @implements TC-MACH-001-FUN-ironOre — macerator grinds an iron ore block into 2× iron dust
	 *      (the ×2 doubling path, via {@code #alaindustrial:macerable_iron}). @covers R-GUI-02 */
	@GameTest
	public void tcMach001FunIronOre_maceratorGrindsIronOre(GameTestHelper helper) {
		MachineScenarios.tcMach001FunIronOre_maceratorGrindsIronOre(helper);
	}

	/** MOD-245: stone sulfur ore follows the tag-driven ×2 maceration path. */
	@GameTest
	public void mod245_maceratorGrindsSulfurOre(GameTestHelper helper) {
		MachineScenarios.mod245_maceratorGrindsSulfurOre(helper);
	}

	/** MOD-245: the deepslate variant is present in the same macerable tag. */
	@GameTest
	public void mod245_maceratorGrindsDeepslateSulfurOre(GameTestHelper helper) {
		MachineScenarios.mod245_maceratorGrindsDeepslateSulfurOre(helper);
	}

	/** MOD-245: raw sulfur has its direct ×2 maceration recipe. */
	@GameTest
	public void mod245_maceratorGrindsRawSulfur(GameTestHelper helper) {
		MachineScenarios.mod245_maceratorGrindsRawSulfur(helper);
	}

	/**
	 * @implements TC-MACH-002-FUN01 — electric furnace smelts raw iron into an iron ingot via the
	 *     vanilla {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON;
	 *     raw_iron → iron_ingot is served by vanilla alone, so this also proves the fallback works).
	 */
	@GameTest
	public void tcMach002Fun01_furnaceSmeltsRawIron(GameTestHelper helper) {
		MachineScenarios.tcMach002Fun01_furnaceSmeltsRawIron(helper);
	}

	/** MOD-245: the vanilla smelting recipe is also served by the electric furnace fallback. */
	@GameTest
	public void mod245_furnaceSmeltsRawSulfur(GameTestHelper helper) {
		MachineScenarios.mod245_furnaceSmeltsRawSulfur(helper);
	}

	/** @implements TC-MACH-003-FUN01 — compressor compresses clay balls into a brick. */
	@GameTest
	public void tcMach003Fun01_compressorMakesBrick(GameTestHelper helper) {
		MachineScenarios.tcMach003Fun01_compressorMakesBrick(helper);
	}

	/** @implements TC-MACH-004-FUN01 — extractor extracts blaze powder from a blaze rod. */
	@GameTest
	public void tcMach004Fun01_extractorMakesBlazePowder(GameTestHelper helper) {
		MachineScenarios.tcMach004Fun01_extractorMakesBlazePowder(helper);
	}

	// ── Negative (NEG) ──────────────────────────────────────────────────────────────

	/** @implements TC-MACH-001-NEG01 — no energy: no output, progress frozen at 0. @covers R-NRG-10 */
	@GameTest
	public void tcMach001Neg01_noPowerNoOutput(GameTestHelper helper) {
		MachineScenarios.tcMach001Neg01_noPowerNoOutput(helper);
	}

	/** @implements TC-MACH-001-NEG02 — non-recipe input (dirt) yields no output even when powered. */
	@GameTest
	public void tcMach001Neg02_nonRecipeNoOutput(GameTestHelper helper) {
		MachineScenarios.tcMach001Neg02_nonRecipeNoOutput(helper);
	}

	/** @implements TC-MACH-002-NEG01 — electric furnace: no energy → no smelt. @covers R-NRG-10 */
	@GameTest
	public void tcMach002Neg01_furnaceNoPower(GameTestHelper helper) {
		MachineScenarios.tcMach002Neg01_furnaceNoPower(helper);
	}

	/**
	 * @implements TC-MACH-001-CON01 — sided automation roles: a hopper/pipe cannot insert into the
	 *     output slot nor extract the unprocessed input; only the output slot is extractable.
	 * @covers R-GUI-05
	 */
	@GameTest
	public void tcMach001Con01_sidedSlotRoles(GameTestHelper helper) {
		MachineScenarios.tcMach001Con01_sidedSlotRoles(helper);
	}

	/**
	 * @implements TC-MACH-001-PRF — the data-driven maceration recipe for an iron ore block yields ×2
	 *     and its EU cost equals the shared E_op (machineEuPerTick × maceratorDuration), keeping the JSON
	 *     recipe and {@link dev.alaindustrial.Config} in sync. Ported from
	 *     {@code IndustrializationSelfTest} MACERATOR_MULTIPLIER. @covers R-NRG-04 (E_op)
	 */
	@GameTest
	public void tcMach001Prf_maceratorEopMatchesConfig(GameTestHelper helper) {
		MachineScenarios.tcMach001Prf_maceratorEopMatchesConfig(helper);
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
		MachineScenarios.tcMach001Neg03_fullOutputJamsMachine(helper);
	}

	// ── Extra recipes (FUN) ──────────────────────────────────────────────────────────

	/**
	 * @implements TC-MACH-001-FUN-copperRaw — macerator grinds raw copper (direct recipe
	 *     {@code raw_copper.json}) into 2× copper dust, mirroring the iron raw path; raw ore doubles (MOD-095).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach001FunCopperRaw_maceratorGrindsRawCopper(GameTestHelper helper) {
		MachineScenarios.tcMach001FunCopperRaw_maceratorGrindsRawCopper(helper);
	}

	/**
	 * @implements TC-MACH-001-FUN-goldRaw — macerator grinds raw gold into 2× gold dust (direct
	 *     recipe {@code raw_gold.json}); raw ore doubles (MOD-095).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach001FunGoldRaw_maceratorGrindsRawGold(GameTestHelper helper) {
		MachineScenarios.tcMach001FunGoldRaw_maceratorGrindsRawGold(helper);
	}

	/**
	 * @implements TC-MACH-001-FUN-ironIngot — macerator grinds an iron ingot (direct recipe, not the
	 *     tag) into ×1 dust — the level-2 slitok path, distinct from the ×2 ore/raw path.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach001FunIronIngot_maceratorGrindsIronIngot(GameTestHelper helper) {
		MachineScenarios.tcMach001FunIronIngot_maceratorGrindsIronIngot(helper);
	}

	/** @implements TC-EFURN-001-FUN01 — electric furnace: mod recipe dust→ingot, iron_dust path. @covers R-GUI-02 */
	@GameTest
	public void tcEfurn001Fun01_furnaceSmeltsIronDust(GameTestHelper helper) {
		MachineScenarios.tcEfurn001Fun01_furnaceSmeltsIronDust(helper);
	}

	/**
	 * @implements TC-EFURN-001-FUN02 — electric furnace: vanilla smelting fallback (no mod recipe for
	 *     raw beef) still smelts food via {@code minecraft:smelting}.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef(GameTestHelper helper) {
		MachineScenarios.tcEfurn001Fun02_furnaceVanillaFallbackCooksBeef(helper);
	}

	/**
	 * @implements TC-EFURN-001-FUN03 — electric furnace smelts sand into glass via the vanilla
	 *     {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcEfurn001Fun03a_furnaceSmeltsSand(GameTestHelper helper) {
		MachineScenarios.tcEfurn001Fun03a_furnaceSmeltsSand(helper);
	}

	/**
	 * @implements TC-EFURN-001-FUN03 — electric furnace smelts cobblestone into stone via the vanilla
	 *     {@code minecraft:smelting} fallback (MOD-086 dropped the duplicate mod-side JSON).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcEfurn001Fun03b_furnaceSmeltsCobblestone(GameTestHelper helper) {
		MachineScenarios.tcEfurn001Fun03b_furnaceSmeltsCobblestone(helper);
	}

	/**
	 * @implements TC-EFURN-001-FUN05 — electric furnace vanilla fallback smelts wood into charcoal,
	 *     proving it inherits everything the vanilla furnace can smelt, not just the mod's own list.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal(GameTestHelper helper) {
		MachineScenarios.tcEfurn001Fun05_furnaceVanillaFallbackMakesCharcoal(helper);
	}

	/**
	 * @implements TC-EFURN-001-FUN04 — electric furnace runs at {@code electricFurnaceDuration} ticks
	 *     (100), half the vanilla furnace's 200 ticks: the product must not yet exist just before that
	 *     tick count and must exist once it is reached.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcEfurn001Fun04_furnaceDurationIsHalfVanilla(GameTestHelper helper) {
		MachineScenarios.tcEfurn001Fun04_furnaceDurationIsHalfVanilla(helper);
	}

	/** @implements TC-COMP-001-FUN02 — compressor: copper_dust → copper_ingot. @covers R-GUI-02 */
	@GameTest
	public void tcComp001Fun02_compressorMakesCopperIngot(GameTestHelper helper) {
		MachineScenarios.tcComp001Fun02_compressorMakesCopperIngot(helper);
	}

	/** @implements TC-COMP-001-FUN03 — compressor: gold_dust → gold_ingot. @covers R-GUI-02 */
	@GameTest
	public void tcComp001Fun03_compressorMakesGoldIngot(GameTestHelper helper) {
		MachineScenarios.tcComp001Fun03_compressorMakesGoldIngot(helper);
	}

	/** @implements TC-COMP-001-FUN04 — compressor: iron_dust → iron_ingot. @covers R-GUI-02 */
	@GameTest
	public void tcComp001Fun04_compressorMakesIronIngot(GameTestHelper helper) {
		MachineScenarios.tcComp001Fun04_compressorMakesIronIngot(helper);
	}

	/** @implements TC-EXTR-001-FUN02 — extractor: gravel → flint (single-output recipe). @covers R-GUI-02 */
	@GameTest
	public void tcExtr001Fun02a_extractorMakesFlint(GameTestHelper helper) {
		MachineScenarios.tcExtr001Fun02a_extractorMakesFlint(helper);
	}

	/**
	 * @implements TC-EXTR-001-FUN06 — extractor: cactus → 2× green_dye. Representative of the plant-derived
	 *     ×2 dye recipes (poppy/dandelion/cornflower/cocoa_beans/sea_pickle/lily_of_the_valley/melon_slice
	 *     all yield ×2 of their dye/seeds) — the new plant-processing niche. Verifies count and 1-per-op.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcExtr001Fun06_extractorMakesGreenDye(GameTestHelper helper) {
		MachineScenarios.tcExtr001Fun06_extractorMakesGreenDye(helper);
	}

	/**
	 * @implements TC-EXTR-001-FUN07 — extractor: pumpkin → 5× pumpkin_seeds. The largest multiplier in the
	 *     recipe set (×5) — exercises a distinct stack-fit boundary from the ×3 (blaze_rod) path.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcExtr001Fun07_extractorMakesPumpkinSeeds(GameTestHelper helper) {
		MachineScenarios.tcExtr001Fun07_extractorMakesPumpkinSeeds(helper);
	}

	// ── 1→1 accounting (FUN02 family) — exactly one input item consumed per operation ─────────────

	/**
	 * @implements TC-MACH-001-FUN02 — macerator consumes exactly 1 raw_iron per operation (150 ticks),
	 *     leaving 3 of the initial 4 and yielding exactly 2× iron_dust (MOD-095: raw ore doubles, Mekanism/IC2 model).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach001Fun02_maceratorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		MachineScenarios.tcMach001Fun02_maceratorConsumesExactlyOnePerOperation(helper);
	}

	/**
	 * @implements TC-MACH-002-FUN02 — electric furnace consumes exactly 1 iron_dust per operation
	 *     (electricFurnaceDuration ticks), yielding exactly 1× iron_ingot.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach002Fun02_furnaceConsumesExactlyOnePerOperation(GameTestHelper helper) {
		MachineScenarios.tcMach002Fun02_furnaceConsumesExactlyOnePerOperation(helper);
	}

	/**
	 * @implements TC-MACH-003-FUN02 — compressor consumes exactly 1 copper_dust per operation
	 *     (compressorDuration ticks); detailed 5-count variant is TC-COMP-001-FUN05.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach003Fun02_compressorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		MachineScenarios.tcMach003Fun02_compressorConsumesExactlyOnePerOperation(helper);
	}

	/**
	 * @implements TC-COMP-001-FUN05 — compressor consumes exactly 1 of 5 copper_dust per operation
	 *     (130 ticks), leaving 4 and yielding exactly 1× copper_ingot.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcComp001Fun05_compressorConsumesExactlyOneOfFive(GameTestHelper helper) {
		MachineScenarios.tcComp001Fun05_compressorConsumesExactlyOneOfFive(helper);
	}

	/**
	 * @implements TC-MACH-004-FUN02 — extractor consumes exactly 1 blaze_rod per operation
	 *     (extractorDuration ticks), yielding exactly 3× blaze_powder (multiplied output).
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach004Fun02_extractorConsumesExactlyOnePerOperation(GameTestHelper helper) {
		MachineScenarios.tcMach004Fun02_extractorConsumesExactlyOnePerOperation(helper);
	}

	// ── NEG: full output jams the machine, no dupe (parametric across all 4) ───────────────────────

	/** @implements TC-MACH-002-NEG03 — electric furnace: full output (64 iron_ingot) jams, no overflow. @covers R-GUI-04 */
	@GameTest
	public void tcMach002Neg03_furnaceFullOutputJamsMachine(GameTestHelper helper) {
		MachineScenarios.tcMach002Neg03_furnaceFullOutputJamsMachine(helper);
	}

	/** @implements TC-MACH-003-NEG03 — compressor: full output (64 copper_ingot) jams, no overflow. @covers R-GUI-04 */
	@GameTest
	public void tcMach003Neg03_compressorFullOutputJamsMachine(GameTestHelper helper) {
		MachineScenarios.tcMach003Neg03_compressorFullOutputJamsMachine(helper);
	}

	/**
	 * @implements TC-EXTR-001-NEG01 (61-item leg) — extractor: 61× blaze_powder in output leaves room
	 *     for exactly the ×3 multiplied product (61+3=64) — operation completes, no jam.
	 * @covers R-GUI-04, R-NRG-04
	 */
	@GameTest
	public void tcExtr001Neg01a_multipliedOutputFitsAt61(GameTestHelper helper) {
		MachineScenarios.tcExtr001Neg01a_multipliedOutputFitsAt61(helper);
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
		MachineScenarios.tcExtr001Neg01b_multipliedOutputJamsAt62(helper);
	}

	// ── NEG: incompatible item in output slot → no dupe, no corruption (parametric) ────────────────

	/** @implements TC-MACH-001-NEG04 — macerator: cobblestone in output slot → unchanged, no dupe. @covers R-GUI-04 */
	@GameTest
	public void tcMach001Neg04_maceratorWrongItemInOutputNoDupe(GameTestHelper helper) {
		MachineScenarios.tcMach001Neg04_maceratorWrongItemInOutputNoDupe(helper);
	}

	/** @implements TC-MACH-002-NEG04 — electric furnace: cobblestone (unrelated) in output slot → unchanged. @covers R-GUI-04 */
	@GameTest
	public void tcMach002Neg04_furnaceWrongItemInOutputNoDupe(GameTestHelper helper) {
		MachineScenarios.tcMach002Neg04_furnaceWrongItemInOutputNoDupe(helper);
	}

	/**
	 * @implements TC-COMP-001-NEG02 — compressor: finished iron_ingot in output slot, gold_dust queued
	 *     as new input → output slot's iron_ingot untouched (mismatched product), no dupe.
	 * @covers R-GUI-04
	 */
	@GameTest
	public void tcMach003Neg04_compressorWrongItemInOutputNoDupe(GameTestHelper helper) {
		MachineScenarios.tcMach003Neg04_compressorWrongItemInOutputNoDupe(helper);
	}

	/** @implements TC-MACH-004-NEG04 — extractor: cobblestone (unrelated) in output slot → unchanged. @covers R-GUI-04 */
	@GameTest
	public void tcMach004Neg04_extractorWrongItemInOutputNoDupe(GameTestHelper helper) {
		MachineScenarios.tcMach004Neg04_extractorWrongItemInOutputNoDupe(helper);
	}

	// ── NEG: non-recipe input, even fully powered → no output, EU untouched (parametric) ───────────

	/** @implements TC-MACH-001-NEG05 — macerator: non-recipe input (dirt) does not spend EU even when powered. @covers R-NRG-04 */
	@GameTest
	public void tcMach001Neg05_maceratorNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineScenarios.tcMach001Neg05_maceratorNonRecipeNoEuSpent(helper);
	}

	/** @implements TC-MACH-002-NEG05 — electric furnace: non-recipe input (lava_bucket) does not spend EU. @covers R-NRG-04 */
	@GameTest
	public void tcMach002Neg05_furnaceNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineScenarios.tcMach002Neg05_furnaceNonRecipeNoEuSpent(helper);
	}

	/**
	 * @implements TC-COMP-001-NEG01 — compressor: item without a compressing recipe (diamond) does not
	 *     spend EU even when the buffer is full.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcMach003Neg05_compressorNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineScenarios.tcMach003Neg05_compressorNonRecipeNoEuSpent(helper);
	}

	/**
	 * @implements TC-COMP-001-NEG03 — compressor: raw_iron (ore, not dust) is not a valid compressing
	 *     input — the macerator's output is required first, raw ore is not a shortcut.
	 * @covers R-GUI-02
	 */
	@GameTest
	public void tcComp001Neg03_compressorRawOreNotAccepted(GameTestHelper helper) {
		MachineScenarios.tcComp001Neg03_compressorRawOreNotAccepted(helper);
	}

	/** @implements TC-MACH-004-NEG05 — extractor: non-recipe input (dirt) does not spend EU even when powered. @covers R-NRG-04 */
	@GameTest
	public void tcMach004Neg05_extractorNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineScenarios.tcMach004Neg05_extractorNonRecipeNoEuSpent(helper);
	}

	// ── NEG: recipe swap mid-operation resets progress (parametric FUN04) ──────────────────────────

	/** @implements TC-MACH-001-FUN04 — macerator: raw_iron swapped for raw_copper mid-op resets progress. @covers R-NRG-10 */
	@GameTest
	public void tcMach001Fun04_maceratorInputSwapResetsProgress(GameTestHelper helper) {
		MachineScenarios.tcMach001Fun04_maceratorInputSwapResetsProgress(helper);
	}

	/** @implements TC-MACH-002-FUN04 — electric furnace: iron_dust swapped for sand mid-op resets progress. @covers R-NRG-10 */
	@GameTest
	public void tcMach002Fun04_furnaceInputSwapResetsProgress(GameTestHelper helper) {
		MachineScenarios.tcMach002Fun04_furnaceInputSwapResetsProgress(helper);
	}

	/** @implements TC-MACH-003-FUN04 — compressor: copper_dust swapped for iron_dust mid-op resets progress. @covers R-NRG-10 */
	@GameTest
	public void tcMach003Fun04_compressorInputSwapResetsProgress(GameTestHelper helper) {
		MachineScenarios.tcMach003Fun04_compressorInputSwapResetsProgress(helper);
	}

	/** @implements TC-MACH-004-FUN04 — extractor: blaze_rod swapped for gravel mid-op resets progress. @covers R-NRG-10 */
	@GameTest
	public void tcMach004Fun04_extractorInputSwapResetsProgress(GameTestHelper helper) {
		MachineScenarios.tcMach004Fun04_extractorInputSwapResetsProgress(helper);
	}

	// ── STA: lit blockstate tracks active/idle, no light emission (parametric) ─────────────────────

	/** @implements TC-MACH-001-STA01 — macerator lit tracks active/idle, no light emission. @covers R-VIS-01 */
	@GameTest
	public void tcMach001Sta01_maceratorLitTracksActive(GameTestHelper helper) {
		MachineScenarios.tcMach001Sta01_maceratorLitTracksActive(helper);
	}

	/** @implements TC-MACH-002-STA01 — electric furnace lit tracks active/idle, no light emission. @covers R-VIS-01 */
	@GameTest
	public void tcMach002Sta01_furnaceLitTracksActive(GameTestHelper helper) {
		MachineScenarios.tcMach002Sta01_furnaceLitTracksActive(helper);
	}

	/** @implements TC-COMP-001-STA01 / TC-MACH-003-STA01 — compressor lit tracks active/idle, no light emission. @covers R-VIS-01 */
	@GameTest
	public void tcMach003Sta01_compressorLitTracksActive(GameTestHelper helper) {
		MachineScenarios.tcMach003Sta01_compressorLitTracksActive(helper);
	}

	/** @implements TC-MACH-004-STA01 — extractor lit tracks active/idle, no light emission. @covers R-VIS-01 */
	@GameTest
	public void tcMach004Sta01_extractorLitTracksActive(GameTestHelper helper) {
		MachineScenarios.tcMach004Sta01_extractorLitTracksActive(helper);
	}

	// ── PRF: E_op exact & E_op−1 (BVA), parametric across all 4 machines ────────────────────────────

	/** @implements TC-MACH-001-PRF04 — macerator: E_op=300 exactly → output + amount==0 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcMach001Prf04_maceratorEopExactCompletes(GameTestHelper helper) {
		MachineScenarios.tcMach001Prf04_maceratorEopExactCompletes(helper);
	}

	/** @implements TC-MACH-001-PRF03 — macerator: E_op−1=299 → no output, progress=149/150 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcMach001Prf03_maceratorEopMinusOneStalls(GameTestHelper helper) {
		MachineScenarios.tcMach001Prf03_maceratorEopMinusOneStalls(helper);
	}

	/** @implements TC-EFURN-001-PRF01 — electric furnace: E_op=200 exactly → output + amount==0 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcEfurn001Prf01_furnaceEopExactCompletes(GameTestHelper helper) {
		MachineScenarios.tcEfurn001Prf01_furnaceEopExactCompletes(helper);
	}

	/** @implements TC-EFURN-001-PRF02 — electric furnace: E_op−1=199 → no output, progress=99/100 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcEfurn001Prf02_furnaceEopMinusOneStalls(GameTestHelper helper) {
		MachineScenarios.tcEfurn001Prf02_furnaceEopMinusOneStalls(helper);
	}

	/** @implements TC-COMP-001-PRF01 — compressor: E_op=260 exactly → output + amount==0 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcComp001Prf01_compressorEopExactCompletes(GameTestHelper helper) {
		MachineScenarios.tcComp001Prf01_compressorEopExactCompletes(helper);
	}

	/** @implements TC-COMP-001-PRF02 — compressor: E_op−1=259 → no output, progress=129/130 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcComp001Prf02_compressorEopMinusOneStalls(GameTestHelper helper) {
		MachineScenarios.tcComp001Prf02_compressorEopMinusOneStalls(helper);
	}

	/** @implements TC-EXTR-001-PRF01 — extractor: E_op=240 exactly → output + amount==0 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcExtr001Prf01_extractorEopExactCompletes(GameTestHelper helper) {
		MachineScenarios.tcExtr001Prf01_extractorEopExactCompletes(helper);
	}

	/** @implements TC-EXTR-001-PRF02 — extractor: E_op−1=239 → no output, progress=119/120 (BVA). @covers R-NRG-04 */
	@GameTest
	public void tcExtr001Prf02_extractorEopMinusOneStalls(GameTestHelper helper) {
		MachineScenarios.tcExtr001Prf02_extractorEopMinusOneStalls(helper);
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

	/** @implements TC-SAW-001-FUN01 — PLANKS mode (default): oak log → 6 oak planks (+50% over vanilla 4). */
	@GameTest
	public void tcSaw001Fun01_planksMode(GameTestHelper helper) {
		MachineScenarios.tcSaw001Fun01_planksMode(helper);
	}

	/** @implements TC-SAW-001-FUN02 — PLANKS mode, bamboo: bamboo block → 3 bamboo planks (halved, per vanilla 2/block). */
	@GameTest
	public void tcSaw001Fun02_bambooHalfYield(GameTestHelper helper) {
		MachineScenarios.tcSaw001Fun02_bambooHalfYield(helper);
	}

	/** @implements TC-SAW-001-FUN03 — STICKS mode: oak log (#minecraft:logs) → 18 sticks (MOD-215: 1.5× the 12 a
	 * player gets free from PLANKS mode + a workbench, so the mode is worth its own operation). */
	@GameTest
	public void tcSaw001Fun03_sticksMode(GameTestHelper helper) {
		MachineScenarios.tcSaw001Fun03_sticksMode(helper);
	}

	/** @implements TC-SAW-001-FUN04 — SLABS mode: oak log → 18 oak slabs (MOD-215: 1.5× the 12 from PLANKS
	 * mode + a workbench). */
	@GameTest
	public void tcSaw001Fun04_slabsMode(GameTestHelper helper) {
		MachineScenarios.tcSaw001Fun04_slabsMode(helper);
	}

	/** @implements TC-SAW-001-FUN05 — STAIRS mode: oak log → 6 oak stairs. */
	@GameTest
	public void tcSaw001Fun05_stairsMode(GameTestHelper helper) {
		MachineScenarios.tcSaw001Fun05_stairsMode(helper);
	}

	/**
	 * @implements TC-SAW-001-CON01 — the machine saws ONLY in the active mode. A regression guard: with the
	 *     mode set to STICKS, an oak log must produce sticks — never planks (which the default mode would
	 *     make). Fails if {@code resolveInput} ignored the selected mode.
	 */
	@GameTest
	public void tcSaw001Con01_onlyActiveModeSaws(GameTestHelper helper) {
		MachineScenarios.tcSaw001Con01_onlyActiveModeSaws(helper);
	}

	/**
	 * @implements TC-SAW-001-CON02 — the active mode survives an NBT round-trip (relog). Set STAIRS, save
	 *     the block entity to NBT, reload into a fresh instance, and assert the mode is still STAIRS.
	 */
	@GameTest
	public void tcSaw001Con02_modePersistsThroughNbt(GameTestHelper helper) {
		MachineScenarios.tcSaw001Con02_modePersistsThroughNbt(helper);
	}

	/**
	 * @implements TC-SAW-001-CON03 — switching the mode mid-operation resets progress to 0 (the new mode
	 *     saws a different product, so carrying progress over would be wrong).
	 */
	@GameTest
	public void tcSaw001Con03_modeSwitchResetsProgress(GameTestHelper helper) {
		MachineScenarios.tcSaw001Con03_modeSwitchResetsProgress(helper);
	}

}
