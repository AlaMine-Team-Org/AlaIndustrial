package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wiring for the battery drawer scenarios (MOD-679). Bodies live in {@link BatteryDrawerScenarios}
 * so the NeoForge world lane runs exactly the same code.
 */
public class BatteryDrawerGameTest {

	/**
	 * @implements TC-CMN-001-DRW01 — a stack of 16 batteries feeds the machine one LV packet a tick, EU
	 *     conserved.
	 */
	@GameTest
	public void tcCmn001Drw01_drainsStackIntoBuffer(GameTestHelper helper) {
		BatteryDrawerScenarios.drawer01DrainsStackIntoBuffer(helper);
	}

	/**
	 * @implements TC-CMN-001-DRW02 — an idle machine keeps draining its drawer every tick while it sleeps.
	 */
	@GameTest
	public void tcCmn001Drw02_drainsWhileTheMachineSleeps(GameTestHelper helper) {
		BatteryDrawerScenarios.drawer02DrainsWhileTheMachineSleeps(helper);
	}

	/**
	 * @implements TC-CMN-001-DRW03 — the drawer slot is the last index and no face offers it to automation.
	 */
	@GameTest
	public void tcCmn001Drw03_slotIsLastAndHidden(GameTestHelper helper) {
		BatteryDrawerScenarios.drawer03SlotIsLastAndHiddenFromAutomation(helper);
	}

	/**
	 * @implements TC-CMN-001-DRW04 — only consumers have a drawer; the manifest agrees with the block entity.
	 */
	@GameTest
	public void tcCmn001Drw04_onlyConsumersHaveADrawer(GameTestHelper helper) {
		BatteryDrawerScenarios.drawer04OnlyConsumersHaveADrawer(helper);
	}

	/**
	 * @implements TC-CMN-001-DRW05 — a crystal blank is refused and a full machine leaves the battery alone.
	 */
	@GameTest
	public void tcCmn001Drw05_blankRefusedAndFullMachineWaits(GameTestHelper helper) {
		BatteryDrawerScenarios.drawer05BlankRefusedAndFullMachineWaits(helper);
	}
}
