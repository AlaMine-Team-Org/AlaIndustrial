package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Fermenter (MOD-146, suite TC-FERM-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/FermenterScenarios} and the SAME bodies run
 * on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}, {@code fermenter_*}), so
 * both loaders exercise identical logic — the price tiers, the water gate and the tank rules.
 */
public class FermenterGameTest {

	/**
	 * @implements TC-FERM-001-FUN01 — a batch brews biofuel, drinks water and eats its input.
	 */
	@GameTest(maxTicks = 500)
	public void tcFerm001Fun01_brewsBiofuelAndSpendsWater(GameTestHelper helper) {
		FermenterScenarios.fun01BrewsBiofuelAndSpendsWater(helper);
	}

	/**
	 * @implements TC-FERM-001-FUN02 — the rich tier out-yields the poor one at the same cost, which
	 * is the whole economy of the machine and the one thing no recipe test can see.
	 */
	@GameTest(maxTicks = 900)
	public void tcFerm001Fun02_richTierBrewsMoreThanPoor(GameTestHelper helper) {
		FermenterScenarios.fun02RichTierBrewsMoreThanPoor(helper);
	}

	/**
	 * @implements TC-FERM-001-CON01 — a dry tank blocks the batch; water is a config cost, so
	 * nothing in the recipe system enforces it.
	 */
	@GameTest(maxTicks = 500)
	public void tcFerm001Con01_dryTankBlocksWork(GameTestHelper helper) {
		FermenterScenarios.con01DryTankBlocksWork(helper);
	}

	/**
	 * @implements TC-FERM-001-CON02 — a full biofuel tank stalls the machine instead of voiding the
	 * brew or eating the input.
	 */
	@GameTest(maxTicks = 500)
	public void tcFerm001Con02_fullBiofuelTankStalls(GameTestHelper helper) {
		FermenterScenarios.con02FullBiofuelTankStalls(helper);
	}
}
