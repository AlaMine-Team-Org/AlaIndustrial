package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the teleporter capsule (MOD-112): assembly from two glass blocks, the door, disassembly,
 * and the jump gate on an unassembled station.
 *
 * <p>The scenario bodies live in {@link TeleporterCapsuleScenarios} ({@code common/src/gametest}); this
 * class is the Fabric wiring only, so the SAME scenarios run on BOTH loaders — NeoForge registers them in
 * {@code NeoForgeGameTests}.
 */
public class TeleporterCapsuleGameTest {

	@GameTest
	public void tcTele005Fun01_twoGlassFormTheCapsule(GameTestHelper helper) {
		TeleporterCapsuleScenarios.tcTele005Fun01_twoGlassFormTheCapsule(helper);
	}

	@GameTest
	public void tcTele005Fun02_secondGlassFoundByPolling(GameTestHelper helper) {
		TeleporterCapsuleScenarios.tcTele005Fun02_secondGlassFoundByPolling(helper);
	}

	/** The door closes after {@code teleporterCapsuleDoorOpenTicks} (60): the default budget is too short. */
	@GameTest(maxTicks = 120)
	public void tcTele005Fun03_doorOpensAndClosesItself(GameTestHelper helper) {
		TeleporterCapsuleScenarios.tcTele005Fun03_doorOpensAndClosesItself(helper);
	}

	@GameTest(maxTicks = 160)
	public void tcTele005Neg01_doorWaitsForAnOccupiedDoorway(GameTestHelper helper) {
		TeleporterCapsuleScenarios.tcTele005Neg01_doorWaitsForAnOccupiedDoorway(helper);
	}

	@GameTest
	public void tcTele005Neg02_clickDuringSlideIsIgnored(GameTestHelper helper) {
		TeleporterCapsuleScenarios.tcTele005Neg02_clickDuringSlideIsIgnored(helper);
	}

	@GameTest
	public void tcTele005Brk01_breakingACellReturnsItsGlass(GameTestHelper helper) {
		TeleporterCapsuleScenarios.tcTele005Brk01_breakingACellReturnsItsGlass(helper);
	}

	@GameTest
	public void tcTele005Brk02_breakingTheStationFreesBothGlasses(GameTestHelper helper) {
		TeleporterCapsuleScenarios.tcTele005Brk02_breakingTheStationFreesBothGlasses(helper);
	}

	@GameTest
	public void tcTele005Sec01_stationWithoutCapsuleRefusesJump(GameTestHelper helper) {
		TeleporterCapsuleScenarios.tcTele005Sec01_stationWithoutCapsuleRefusesJump(helper);
	}
}
