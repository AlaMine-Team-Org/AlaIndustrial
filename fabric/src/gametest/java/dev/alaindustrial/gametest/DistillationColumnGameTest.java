package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Distillation Column (MOD-251, suite TC-DIST-001). Thin Fabric
 * wrappers: the bodies are loader-neutral in {@code common/.../gametest/DistillationColumnScenarios}
 * and the SAME bodies run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests},
 * {@code distillation_column_*}) — the mod's first 1×1×3 multiblock, per-segment fluid ports,
 * warm-up gate and fluid-preserving one-item drop.
 */
public class DistillationColumnGameTest {

	/**
	 * @implements TC-DIST-001-FUN01 — 1000 mB crude + EU → 700 mB diesel (top tank) + 200 mB fuel
	 * oil (bottom tank); the oil tank ends empty with its fluid identity cleared.
	 */
	@GameTest(maxTicks = 600)
	public void tcDist001Fun01_oilSplitsIntoFractions(GameTestHelper helper) {
		DistillationColumnScenarios.fun01OilSplitsIntoFractions(helper);
	}

	/**
	 * @implements TC-DIST-001-FUN02 — the warm-up gates the first run: EU burns while heating, but
	 * no oil is consumed and no progress accrues.
	 */
	@GameTest(maxTicks = 400)
	public void tcDist001Fun02_warmupGatesDistillation(GameTestHelper helper) {
		DistillationColumnScenarios.fun02WarmupGatesDistillation(helper);
	}

	/**
	 * @implements TC-DIST-001-CON01 — a full diesel tank freezes the column: no EU spent, no oil
	 * consumed, and no half-run delivers the other fraction alone.
	 */
	@GameTest(maxTicks = 600)
	public void tcDist001Con01_fullDieselTankFreezes(GameTestHelper helper) {
		DistillationColumnScenarios.con01FullDieselTankFreezes(helper);
	}

	/**
	 * @implements TC-DIST-001-FUN03 — an empty bucket in the diesel drain slot is bottled into a
	 * diesel bucket; the tank drops exactly one bucket's volume.
	 */
	@GameTest
	public void tcDist001Fun03_drainSlotBottlesDiesel(GameTestHelper helper) {
		DistillationColumnScenarios.fun03DrainSlotBottlesDiesel(helper);
	}

	/**
	 * @implements TC-DIST-001-SEG01 — round 3: breaking the MIDDLE segment degrades the tower (one
	 * segment item drops, the base becomes a blank keeping its tanks, the top becomes a blank).
	 */
	@GameTest(maxTicks = 100)
	public void tcDist001Seg01_breakMiddleDegradesTower(GameTestHelper helper) {
		DistillationColumnScenarios.seg01BreakMiddleDegradesTower(helper);
	}

	/**
	 * @implements TC-DIST-001-FRM01 — round 3: three hand-placed blanks self-assemble into the
	 * formed tower (base master + middle + top), scanning down from any of the three.
	 */
	@GameTest
	public void tcDist001Frm01_threeBlanksFormTheTower(GameTestHelper helper) {
		DistillationColumnScenarios.frm01ThreeBlanksFormTheTower(helper);
	}

	/**
	 * @implements TC-DIST-001-PRT01 — the fixed port layout: the middle segment accepts crude into
	 * the base's intake, the top exposes diesel, the base exposes fuel oil.
	 */
	@GameTest
	public void tcDist001Prt01_segmentPortsMatchLayout(GameTestHelper helper) {
		DistillationColumnScenarios.prt01SegmentPortsMatchLayout(helper);
	}

	/** @implements TC-DIST-001-PRT02 — {@code fluidPort(null)} answers the oil tank, never NPEs. */
	@GameTest
	public void tcDist001Prt02_nullSidePortIsOilTank(GameTestHelper helper) {
		DistillationColumnScenarios.prt02NullSidePortIsOilTank(helper);
	}

	/**
	 * @implements TC-DIST-001-STA01 — all three tanks (amount + fluid identity) and the warm-up heat
	 * survive an NBT round-trip.
	 */
	@GameTest
	public void tcDist001Sta01_nbtRoundTripPreservesTanksAndHeat(GameTestHelper helper) {
		DistillationColumnScenarios.sta01NbtRoundTripPreservesTanksAndHeat(helper);
	}

	/**
	 * @implements TC-DIST-001-CRK01 — cracking (round 2): 1000 mB fuel oil in the intake + EU →
	 * 600 mB diesel, single result, intake drained.
	 */
	@GameTest(maxTicks = 700)
	public void tcDist001Crk01_fuelOilCracksIntoDiesel(GameTestHelper helper) {
		DistillationColumnScenarios.crk01FuelOilCracksIntoDiesel(helper);
	}

	/**
	 * @implements TC-DIST-001-SEC01 — a Rectification Section on the tower adds +50 mB diesel per
	 * run (losses 10 % → 5 %), fuel oil unchanged.
	 */
	@GameTest(maxTicks = 600)
	public void tcDist001Sec01_sectionBoostsDiesel(GameTestHelper helper) {
		DistillationColumnScenarios.sec01SectionBoostsDiesel(helper);
	}

	/**
	 * @implements TC-DIST-001-FOU01 — at 100 % fouling the column freezes without consuming
	 * anything; a wrench cleaning yields 1..3 coal, resets fouling and distilling resumes.
	 */
	@GameTest(maxTicks = 1000)
	public void tcDist001Fou01_fouledStopsUntilCleaned(GameTestHelper helper) {
		DistillationColumnScenarios.fou01FouledStopsUntilCleaned(helper);
	}
}
