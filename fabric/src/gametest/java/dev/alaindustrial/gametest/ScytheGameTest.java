package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the scythe (MOD-068 / MOD-098). Thin Fabric wrappers: the bodies are
 * loader-neutral in {@code common/.../gametest/ScytheScenarios} and the SAME bodies run on the
 * NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders exercise identical
 * AOE logic (the {@code scythe_harvestable} / {@code scythe_crops} tag filters, the
 * {@code ServerPlayerGameMode.destroyBlock} path, durability, the per-use cap, the decor/crop modes,
 * and the crop-protection / maturity guards).
 */
public class ScytheGameTest {

	/** @implements TC-SCYTHE-001-FUN01 — clears foliage in the box, keeps solids and blocks outside it. */
	@GameTest
	public void fun01_clearsFoliageKeepsSolids(GameTestHelper helper) {
		ScytheScenarios.fun01ClearsFoliageKeepsSolids(helper);
	}

	/** @implements TC-SCYTHE-001-NEG01 — shift (crop mode) over plain decor returns PASS and clears nothing. */
	@GameTest
	public void neg01_shiftCropModeKeepsDecor(GameTestHelper helper) {
		ScytheScenarios.neg01ShiftCropModeKeepsDecor(helper);
	}

	/** @implements TC-SCYTHE-001-PRF01 — durability drops by exactly the number of blocks broken (leaves). */
	@GameTest
	public void prf01_durabilityPerBlock(GameTestHelper helper) {
		ScytheScenarios.prf01DurabilityPerBlock(helper);
	}

	/** @implements TC-SCYTHE-001-PRF03 — durability is spent on hardness-0 (instant) blocks too (MOD-098 fix). */
	@GameTest
	public void prf03_durabilityOnInstantBlock(GameTestHelper helper) {
		ScytheScenarios.prf03DurabilityOnInstantBlock(helper);
	}

	/** @implements TC-SCYTHE-001-PRF02 — creative / instabuild spends no durability. */
	@GameTest
	public void prf02_creativeNoDurability(GameTestHelper helper) {
		ScytheScenarios.prf02CreativeNoDurability(helper);
	}

	/** @implements TC-SCYTHE-001-BVA01 — never breaks more than the tier's max-blocks cap. */
	@GameTest
	public void bva01_stopsAtMaxBlocks(GameTestHelper helper) {
		ScytheScenarios.bva01StopsAtMaxBlocks(helper);
	}

	/** @implements TC-SCYTHE-001-NEG02 — crops and water are not harvested in decor mode. */
	@GameTest
	public void neg02_keepsCropsAndWater(GameTestHelper helper) {
		ScytheScenarios.neg02KeepsCropsAndWater(helper);
	}

	/** @implements TC-SCYTHE-002-FUN01 — crop mode (shift) harvests mature crops. */
	@GameTest
	public void fun02_cropModeHarvestsMature(GameTestHelper helper) {
		ScytheScenarios.fun02CropModeHarvestsMature(helper);
	}

	/** @implements TC-SCYTHE-002-NEG01 — crop mode keeps immature crops. */
	@GameTest
	public void neg03_cropModeKeepsImmature(GameTestHelper helper) {
		ScytheScenarios.neg03CropModeKeepsImmature(helper);
	}

	/** @implements TC-SCYTHE-002-NEG02 — crop mode keeps decorative foliage. */
	@GameTest
	public void neg04_cropModeKeepsFoliage(GameTestHelper helper) {
		ScytheScenarios.neg04CropModeKeepsFoliage(helper);
	}

	/** @implements TC-SCYTHE-002-FUN02 — crop mode harvests the stalk above a sugar-cane base, keeps the base. */
	@GameTest
	public void fun03_cropModeHarvestsCaneStalk(GameTestHelper helper) {
		ScytheScenarios.fun03CropModeHarvestsCaneStalk(helper);
	}

	/** @implements TC-SCYTHE-002-NEG03 — crop mode keeps a lone cactus base (no stalk above it). */
	@GameTest
	public void neg05_cropModeKeepsLoneCactus(GameTestHelper helper) {
		ScytheScenarios.neg05CropModeKeepsLoneCactus(helper);
	}

	/** @implements TC-SCYTHE-002-NEG04 — crop mode keeps melon/pumpkin stems even when ripe. */
	@GameTest
	public void neg06_cropModeKeepsStem(GameTestHelper helper) {
		ScytheScenarios.neg06CropModeKeepsStem(helper);
	}

	/** @implements TC-SCYTHE-001-CON02 — only the netherite tier is fire-resistant. */
	@GameTest
	public void con02_netheriteFireResistant(GameTestHelper helper) {
		ScytheScenarios.con02NetheriteFireResistant(helper);
	}
}
