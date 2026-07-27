package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Polymerizer (MOD-019, suite TC-POLY-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/PolymerizerScenarios} and the SAME bodies run
 * on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}, {@code polymerizer_*}), so
 * both loaders exercise identical logic — the fluid-fed recipe family, the tank filter, and the
 * no-dupe/no-void guards.
 */
public class PolymerizerGameTest {

	/**
	 * @implements TC-POLY-001-FUN01 — one bucket of oil plus EU yields one raw rubber and leaves the
	 * tank empty, with its fluid identity cleared.
	 */
	@GameTest(maxTicks = 400)
	public void tcPoly001Fun01_oilBecomesRawRubber(GameTestHelper helper) {
		PolymerizerScenarios.fun01OilBecomesRawRubber(helper);
	}

	/**
	 * @implements TC-POLY-001-FUN02 — an oil bucket in the fill slot is emptied into the tank and the
	 * empty bucket drops into the slot below.
	 */
	@GameTest
	public void tcPoly001Fun02_bucketFillsTank(GameTestHelper helper) {
		PolymerizerScenarios.fun02BucketFillsTank(helper);
	}

	/** @implements TC-POLY-001-NEG01 — no EU: no product, no oil consumed, progress pinned at 0. */
	@GameTest(maxTicks = 400)
	public void tcPoly001Neg01_noPowerNoOutput(GameTestHelper helper) {
		PolymerizerScenarios.neg01NoPowerNoOutput(helper);
	}

	/**
	 * @implements TC-POLY-001-NEG02 — 999 mB is one millibucket short of the recipe: nothing is
	 * produced and nothing is nibbled off the tank.
	 */
	@GameTest(maxTicks = 400)
	public void tcPoly001Neg02_belowRecipeVolumeIsInert(GameTestHelper helper) {
		PolymerizerScenarios.neg02BelowRecipeVolumeIsInert(helper);
	}

	/**
	 * @implements TC-POLY-001-CON01 — a full result slot freezes the operation: the oil stays in the
	 * tank and the output stack keeps its count (no dupe, no void).
	 */
	@GameTest(maxTicks = 400)
	public void tcPoly001Con01_fullOutputKeepsOil(GameTestHelper helper) {
		PolymerizerScenarios.con01FullOutputKeepsOil(helper);
	}

	/** @implements TC-POLY-001-REG01 — the tank accepts oil and refuses water. */
	@GameTest
	public void tcPoly001Reg01_tankRefusesNonOil(GameTestHelper helper) {
		PolymerizerScenarios.reg01TankRefusesNonOil(helper);
	}

	/** @implements TC-POLY-001-REG02 — the tank takes source oil and refuses the flowing variant. */
	@GameTest
	public void tcPoly001Reg02_tankRefusesFlowingOil(GameTestHelper helper) {
		PolymerizerScenarios.reg02TankRefusesFlowingOil(helper);
	}

	/**
	 * @implements TC-POLY-001-CON03 — a neighbour fills the tank through the published fluid port
	 * (the path a pump or a foreign pipe takes), not by touching the tank field.
	 */
	@GameTest
	public void tcPoly001Con03_neighbourFillsThroughFluidPort(GameTestHelper helper) {
		PolymerizerScenarios.con03NeighbourFillsThroughFluidPort(helper);
	}

	/** @implements TC-POLY-001-STA01 — tank volume, fluid identity, energy and progress survive NBT. */
	@GameTest
	public void tcPoly001Sta01_nbtRoundTripPreservesTank(GameTestHelper helper) {
		PolymerizerScenarios.sta01NbtRoundTripPreservesTank(helper);
	}
}
