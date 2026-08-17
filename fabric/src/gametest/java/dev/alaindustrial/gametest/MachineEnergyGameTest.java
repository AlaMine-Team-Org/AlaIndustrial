package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wiring for {@link MachineEnergyScenarios} — the loader-neutral processing-machine bodies the
 * NeoForge world lane registers ({@code NeoForgeGameTests}). Until MOD-445 these ran on NeoForge only;
 * the lane-parity gate ({@code docs/tools/gametest_lane_parity_check.py}) now requires every common
 * scenario to be wired into both lanes.
 *
 * <p>The per-machine TC-* cases live in {@link MachineScenarios} (MOD-446) and are wrapped by
 * {@link MachineGameTest}; several of them assert strictly more than the twin body here (exact input
 * consumption, progress frozen at 0), so this class was not folded into that one. Merging the two
 * common families ({@link MachineScenarios} and {@link MachineEnergyScenarios}) into one body per
 * behaviour is a follow-up noted in the task; until then both lanes run both.
 */
public class MachineEnergyGameTest {

	/** Macerator processes a recipe end-to-end: recipe type + serializer resolve, output appears. */
	@GameTest
	public void maceratorProcessesRecipe(GameTestHelper helper) {
		MachineEnergyScenarios.maceratorProcessesRecipe(helper);
	}

	/** Valid input, no EU: no output and no progress (R-NRG-10). Twin of TC-MACH-001-NEG01. */
	@GameTest
	public void machineNoPowerNoOutput(GameTestHelper helper) {
		MachineEnergyScenarios.machineNoPowerNoOutput(helper);
	}

	/** Full output slot jams the machine, no dupe. Twin of TC-MACH-001-NEG03. */
	@GameTest
	public void machineFullOutputJams(GameTestHelper helper) {
		MachineEnergyScenarios.machineFullOutputJams(helper);
	}

	/** Swapping the input mid-operation resets progress. Twin of TC-MACH-001-FUN04. */
	@GameTest
	public void machineInputSwapResetsProgress(GameTestHelper helper) {
		MachineEnergyScenarios.machineInputSwapResetsProgress(helper);
	}

	/** Compressor: copper_dust → copper_ingot. Twin of TC-COMP-001-FUN02. */
	@GameTest
	public void compressorMakesCopperIngot(GameTestHelper helper) {
		MachineEnergyScenarios.compressorMakesCopperIngot(helper);
	}

	/** Extractor: gravel → flint. Twin of TC-EXTR-001-FUN02. */
	@GameTest
	public void extractorMakesFlint(GameTestHelper helper) {
		MachineEnergyScenarios.extractorMakesFlint(helper);
	}

	/** Electric furnace: raw_iron → iron_ingot (vanilla smelting fallback). Twin of TC-MACH-002-FUN01. */
	@GameTest
	public void furnaceSmeltsRawIron(GameTestHelper helper) {
		MachineEnergyScenarios.furnaceSmeltsRawIron(helper);
	}

	/** Electric furnace: a non-recipe input spends no EU. Twin of TC-MACH-002-NEG05. */
	@GameTest
	public void furnaceNonRecipeNoEuSpent(GameTestHelper helper) {
		MachineEnergyScenarios.furnaceNonRecipeNoEuSpent(helper);
	}

	/** Compressor: full output slot jams the machine. Twin of TC-MACH-003-NEG03. */
	@GameTest
	public void compressorFullOutputJams(GameTestHelper helper) {
		MachineEnergyScenarios.compressorFullOutputJams(helper);
	}

	/** Extractor: input swap mid-operation resets progress. Twin of TC-MACH-004-FUN04. */
	@GameTest
	public void extractorInputSwapResetsProgress(GameTestHelper helper) {
		MachineEnergyScenarios.extractorInputSwapResetsProgress(helper);
	}

	/** Extractor: blaze_rod → blaze_powder (×3 multiplier path). Twin of TC-MACH-004-FUN02. */
	@GameTest
	public void extractorBlazeRodToPowder(GameTestHelper helper) {
		MachineEnergyScenarios.extractorBlazeRodToPowder(helper);
	}

	/** Electric furnace: sand → glass. Twin of TC-EFURN-001-FUN03. */
	@GameTest
	public void furnaceSmeltsSandToGlass(GameTestHelper helper) {
		MachineEnergyScenarios.furnaceSmeltsSandToGlass(helper);
	}

	/** Compressor: iron_dust → iron_ingot. Twin of TC-COMP-001-FUN04. */
	@GameTest
	public void compressorIronDustToIngot(GameTestHelper helper) {
		MachineEnergyScenarios.compressorIronDustToIngot(helper);
	}

	/** Macerator: E_op exactly → output, buffer 0 (BVA). Twin of TC-MACH-001-PRF04. */
	@GameTest
	public void maceratorEopExactCompletes(GameTestHelper helper) {
		MachineEnergyScenarios.maceratorEopExactCompletes(helper);
	}

	/** Macerator: E_op − 1 stalls one tick short (BVA). Twin of TC-MACH-001-PRF03. */
	@GameTest
	public void maceratorEopMinusOneStalls(GameTestHelper helper) {
		MachineEnergyScenarios.maceratorEopMinusOneStalls(helper);
	}

	/** Macerator: LIT tracks active/idle. Twin of TC-MACH-001-STA01. */
	@GameTest
	public void maceratorLitTracksActive(GameTestHelper helper) {
		MachineEnergyScenarios.maceratorLitTracksActive(helper);
	}

	/** Extractor: cactus → 2× green dye (×2 plant-dye path). Twin of TC-EXTR-001-FUN06. */
	@GameTest
	public void extractorCactusToGreenDye(GameTestHelper helper) {
		MachineEnergyScenarios.extractorCactusToGreenDye(helper);
	}

	/** Extractor: pumpkin → 5× pumpkin seeds (largest multiplier). Twin of TC-EXTR-001-FUN07. */
	@GameTest
	public void extractorPumpkinToSeeds(GameTestHelper helper) {
		MachineEnergyScenarios.extractorPumpkinToSeeds(helper);
	}

	/** Macerator: iron ore block → 2× iron dust through the macerable_iron tag. Twin of TC-MACH-001-FUN-ironOre. */
	@GameTest
	public void maceratorIronOreDoublesDust(GameTestHelper helper) {
		MachineEnergyScenarios.maceratorIronOreDoublesDust(helper);
	}
}
