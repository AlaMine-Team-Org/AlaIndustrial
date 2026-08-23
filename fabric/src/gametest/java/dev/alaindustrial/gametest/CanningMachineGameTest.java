package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Canning Machine (MOD-383, suite TC-CAN-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/CanningMachineScenarios} and the SAME
 * bodies run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests},
 * {@code canning_machine_*}), so both loaders exercise identical logic — the stacking guarantee, the
 * self-feed guard, the hazardous-food blacklist and the anti-duplication invariant.
 */
public class CanningMachineGameTest {

	/** @implements TC-CAN-001-FUN01 — food plus an empty can becomes a ration, spending one can. */
	@GameTest(maxTicks = 400)
	public void tcCan001Fun01_foodBecomesRation(GameTestHelper helper) {
		CanningMachineScenarios.fun01FoodBecomesRation(helper);
	}

	/**
	 * @implements TC-CAN-001-FUN02 — rations from pork and from bread occupy one stack. The reason the
	 * machine exists: give the ration per-stack data and only this case goes red.
	 */
	@GameTest(maxTicks = 600)
	public void tcCan001Fun02_rationsFromDifferentFoodsStack(GameTestHelper helper) {
		CanningMachineScenarios.fun02RationsFromDifferentFoodsStack(helper);
	}

	/** @implements TC-CAN-001-FUN03 — rich food out-yields poor food for the same item count. */
	@GameTest(maxTicks = 1200)
	public void tcCan001Fun03_richFoodYieldsMoreRations(GameTestHelper helper) {
		CanningMachineScenarios.fun03RichFoodYieldsMoreRations(helper);
	}

	/** @implements TC-CAN-001-FUN04 — absorption banks calories with no can and no power present. */
	@GameTest(maxTicks = 600)
	public void tcCan001Fun04_absorptionNeedsNeitherCanNorPower(GameTestHelper helper) {
		CanningMachineScenarios.fun04AbsorptionNeedsNeitherCanNorPower(helper);
	}

	/** @implements TC-CAN-001-CON02 — unpowered: no ration and no can spent. @covers R-NRG-10 */
	@GameTest(maxTicks = 600)
	public void tcCan001Con02_noPowerNoOutput(GameTestHelper helper) {
		CanningMachineScenarios.con02NoPowerNoOutput(helper);
	}

	/** @implements TC-CAN-001-CON03 — a full output jams the press; absorption still banks calories. */
	@GameTest(maxTicks = 600)
	public void tcCan001Con03_fullOutputJams(GameTestHelper helper) {
		CanningMachineScenarios.con03FullOutputJams(helper);
	}

	/** @implements TC-CAN-001-REG01 — hazardous food is refused. @covers R-GUI-02 */
	@GameTest(maxTicks = 200)
	public void tcCan001Reg01_hazardousFoodRefused(GameTestHelper helper) {
		CanningMachineScenarios.reg01HazardousFoodRefused(helper);
	}

	/** @implements TC-CAN-001-REG02 — the ration is refused as input, so the machine cannot eat itself. */
	@GameTest(maxTicks = 200)
	public void tcCan001Reg02_rationRefusedAsInput(GameTestHelper helper) {
		CanningMachineScenarios.reg02RationRefusedAsInput(helper);
	}

	/** @implements TC-CAN-001-REG03 — non-food is refused, and each slot keeps its own contract. */
	@GameTest(maxTicks = 200)
	public void tcCan001Reg03_nonFoodRefused(GameTestHelper helper) {
		CanningMachineScenarios.reg03NonFoodRefused(helper);
	}

	/** @implements TC-CAN-001-DUP01 — food value out is strictly below food value in. */
	@GameTest(maxTicks = 1200)
	public void tcCan001Dup01_valueStrictlyDecreases(GameTestHelper helper) {
		CanningMachineScenarios.dup01ValueStrictlyDecreases(helper);
	}

	/** @implements TC-CAN-001-EAT01 — a ration restores exactly its declared nutrition and saturation. */
	@GameTest(maxTicks = 200)
	public void tcCan001Eat01_rationFeedsExactly(GameTestHelper helper) {
		CanningMachineScenarios.eat01RationFeedsExactly(helper);
	}

	/** @implements TC-CAN-001-EAT02 — a full player cannot eat a ration, unlike a golden apple. */
	@GameTest(maxTicks = 200)
	public void tcCan001Eat02_fullPlayerCannotEat(GameTestHelper helper) {
		CanningMachineScenarios.eat02FullPlayerCannotEat(helper);
	}

	/** @implements TC-CAN-001-EAT03 — the ration carries no consume effects; the golden apple does. */
	@GameTest(maxTicks = 200)
	public void tcCan001Eat03_noSideEffectsCarried(GameTestHelper helper) {
		CanningMachineScenarios.eat03NoSideEffectsCarried(helper);
	}

	/** @implements TC-CAN-001-PER01 — banked calories survive a save/load round trip. */
	@GameTest(maxTicks = 400)
	public void tcCan001Per01_bufferSurvivesReload(GameTestHelper helper) {
		CanningMachineScenarios.per01BufferSurvivesReload(helper);
	}
}
