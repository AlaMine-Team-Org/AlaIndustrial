package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Galvanic Bath (MOD-127, suite TC-BATH-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/GalvanicBathScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}, {@code galvanic_bath_*}),
 * so both loaders exercise identical logic — the fibre tag, the water gate and the tank rules.
 */
public class GalvanicBathGameTest {

	/**
	 * @implements TC-BATH-001-FUN01 — vanilla string plus silver, water and EU yields one flux thread.
	 */
	@GameTest(maxTicks = 700)
	public void tcBath001Fun01_stringBecomesFluxThread(GameTestHelper helper) {
		GalvanicBathScenarios.fun01StringBecomesFluxThread(helper);
	}

	/**
	 * @implements TC-BATH-001-FUN02 — cotton fibre reaches the same result as string, so the farming
	 * route into the Fluxweave chain stays open.
	 */
	@GameTest(maxTicks = 700)
	public void tcBath001Fun02_cottonBecomesFluxThread(GameTestHelper helper) {
		GalvanicBathScenarios.fun02CottonBecomesFluxThread(helper);
	}

	/** @implements TC-BATH-001-FUN03 — a completed operation debits exactly one recipe's worth of water. */
	@GameTest(maxTicks = 700)
	public void tcBath001Fun03_operationDebitsWater(GameTestHelper helper) {
		GalvanicBathScenarios.fun03OperationDebitsWater(helper);
	}

	/** @implements TC-BATH-001-FUN04 — a water bucket in the fill slot loads the tank and drops empty. */
	@GameTest
	public void tcBath001Fun04_bucketFillsTank(GameTestHelper helper) {
		GalvanicBathScenarios.fun04BucketFillsTank(helper);
	}

	/**
	 * @implements TC-BATH-001-CON01 — a dry tank blocks the operation and reports NO_WATER. Water is
	 * not a recipe ingredient, so nothing in the recipe system enforces this.
	 */
	@GameTest(maxTicks = 700)
	public void tcBath001Con01_dryTankBlocksWork(GameTestHelper helper) {
		GalvanicBathScenarios.con01DryTankBlocksWork(helper);
	}

	/** @implements TC-BATH-001-CON02 — one millibucket short is still short: nothing runs, nothing is spent. */
	@GameTest(maxTicks = 700)
	public void tcBath001Con02_partialWaterBlocksWork(GameTestHelper helper) {
		GalvanicBathScenarios.con02PartialWaterBlocksWork(helper);
	}

	/** @implements TC-BATH-001-CON03 — the tank is feedstock, not storage: neighbours cannot drain it. */
	@GameTest
	public void tcBath001Con03_tankRefusesExtraction(GameTestHelper helper) {
		GalvanicBathScenarios.con03TankRefusesExtraction(helper);
	}

	/** @implements TC-BATH-001-REG01 — the tank takes water and refuses everything else. */
	@GameTest
	public void tcBath001Reg01_tankRefusesNonWater(GameTestHelper helper) {
		GalvanicBathScenarios.reg01TankRefusesNonWater(helper);
	}
}
