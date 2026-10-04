package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyPort;
import org.junit.jupiter.api.Test;

/**
 * The rule that decides whether a block's energy counters run (MOD-692): they follow the statistics state
 * on a block with a panel, and stay off on a block without one.
 */
class StatsCounterGateTest {

	/** Minimal transaction handle; the buffer's commit hook is driven explicitly. */
	private static final class FakeTxn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	private static EnergyBuffer buffer() {
		return new EnergyBuffer(1000, 100, 100, () -> {
		});
	}

	/** One committed delivery of {@code eu}, the way a cable brings it. */
	private static void deliver(EnergyBuffer b, long eu) {
		b.insert(eu, new FakeTxn());
		b.onFinalCommit();
	}

	@Test
	void panelWithStatsSwitchesTheCountersOn() {
		EnergyBuffer b = buffer();
		StatsCounterGate.sync(b, true, () -> true);
		assertTrue(b.countersEnabled());
		deliver(b, 40);
		assertEquals(40, b.getTotalEnergyIn(), "a measuring block counts what it receives");
	}

	@Test
	void panelWithoutStatsKeepsThemOff() {
		EnergyBuffer b = buffer();
		StatsCounterGate.sync(b, true, () -> false);
		assertFalse(b.countersEnabled());
		deliver(b, 40);
		assertEquals(0, b.getTotalEnergyIn(), "no chip, no measurement (MOD-125)");
	}

	/** Open question 3: Free Telemetry answers "stats on" for a panelless block, and the gate says no. */
	@Test
	void noPanelKeepsThemOffEvenWhenStatsAreOn() {
		EnergyBuffer b = buffer();
		StatsCounterGate.sync(b, false, () -> true);
		assertFalse(b.countersEnabled());
		deliver(b, 40);
		assertEquals(0, b.getTotalEnergyIn(), "a block without a panel must not start counting");
	}

	@Test
	void noPanelDoesNotAskForTheStatsState() {
		boolean[] asked = {false};
		StatsCounterGate.sync(buffer(), false, () -> {
			asked[0] = true;
			return true;
		});
		assertFalse(asked[0], "the skill lookup behind hasStatsChip() is not paid for a panelless block");
	}

	/** The chip coming out switches counting off, and what was counted stays. */
	@Test
	void countersFollowTheStatsStateBackOff() {
		EnergyBuffer b = buffer();
		StatsCounterGate.sync(b, true, () -> true);
		deliver(b, 40);
		StatsCounterGate.sync(b, true, () -> false);
		assertFalse(b.countersEnabled());
		deliver(b, 30);
		assertEquals(40, b.getTotalEnergyIn(), "the total freezes when the chip is pulled, it is not wiped");
		StatsCounterGate.sync(b, true, () -> true);
		deliver(b, 20);
		assertEquals(60, b.getTotalEnergyIn(), "and counting resumes from the moment it goes back in");
	}
}
