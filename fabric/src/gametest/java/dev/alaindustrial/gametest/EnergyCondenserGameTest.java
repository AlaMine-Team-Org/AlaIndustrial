package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric entry points for the Energy Condenser suite (MOD-393); bodies live in common. */
public class EnergyCondenserGameTest {

	@GameTest
	public void condenser_tierFollowsTheBank(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_tierFollowsTheBank(helper);
	}

	@GameTest
	public void condenser_takingSpendsTheWholeBank(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_takingSpendsTheWholeBank(helper);
	}

	@GameTest
	public void condenser_facesAreSplitBetweenPowerAndItems(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_facesAreSplitBetweenPowerAndItems(helper);
	}

	@GameTest
	public void condenser_cannotCompeteWithMachines(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_cannotCompeteWithMachines(helper);
	}

	@GameTest
	public void condenser_menuSlotsStayInsideTheContainer(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_menuSlotsStayInsideTheContainer(helper);
	}

	@GameTest
	public void condenser_hasNoUpgradePanel(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_hasNoUpgradePanel(helper);
	}

	/** 200 driven ticks — the default tick budget is too small for this one. */
	@GameTest(maxTicks = 260)
	public void condenser_banksWhatTheGridOffers(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_banksWhatTheGridOffers(helper);
	}

	/** 120 driven ticks in two phases. */
	@GameTest(maxTicks = 200)
	public void condenser_doesNotStarveAMachine(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_doesNotStarveAMachine(helper);
	}

	/** 120 driven ticks in two phases. */
	@GameTest(maxTicks = 200)
	public void condenser_doesNotDrainAChargedStore(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_doesNotDrainAChargedStore(helper);
	}

	@GameTest
	public void condenser_cannotBeReachedByAFlushStore(GameTestHelper helper) {
		EnergyCondenserScenarios.condenser_cannotBeReachedByAFlushStore(helper);
	}
}
