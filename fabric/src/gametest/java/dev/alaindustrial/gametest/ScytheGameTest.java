package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the scythe (MOD-068). Thin Fabric wrappers: the bodies are loader-neutral in
 * {@code common/.../gametest/ScytheScenarios} and the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders exercise identical AOE logic
 * (the {@code scythe_harvestable} tag filter, the {@code ServerPlayerGameMode.destroyBlock} path,
 * durability, the per-use cap, and the shift / crops / water guards).
 */
public class ScytheGameTest {

	/** @implements TC-SCYTHE-001-FUN01 — clears foliage in the box, keeps solids and blocks outside it. */
	@GameTest
	public void fun01_clearsFoliageKeepsSolids(GameTestHelper helper) {
		ScytheScenarios.fun01ClearsFoliageKeepsSolids(helper);
	}

	/** @implements TC-SCYTHE-001-NEG01 — shift (secondary use) does not trigger the AOE. */
	@GameTest
	public void neg01_shiftDoesNotAoe(GameTestHelper helper) {
		ScytheScenarios.neg01ShiftDoesNotAoe(helper);
	}

	/** @implements TC-SCYTHE-001-PRF01 — durability drops by exactly the number of blocks broken. */
	@GameTest
	public void prf01_durabilityPerBlock(GameTestHelper helper) {
		ScytheScenarios.prf01DurabilityPerBlock(helper);
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

	/** @implements TC-SCYTHE-001-NEG02 — crops and water are not harvested. */
	@GameTest
	public void neg02_keepsCropsAndWater(GameTestHelper helper) {
		ScytheScenarios.neg02KeepsCropsAndWater(helper);
	}

	/** @implements TC-SCYTHE-001-CON02 — only the netherite tier is fire-resistant. */
	@GameTest
	public void con02_netheriteFireResistant(GameTestHelper helper) {
		ScytheScenarios.con02NetheriteFireResistant(helper);
	}
}
