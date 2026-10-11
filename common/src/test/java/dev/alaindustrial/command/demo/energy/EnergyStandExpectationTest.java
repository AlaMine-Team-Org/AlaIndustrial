package dev.alaindustrial.command.demo.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * MOD-799: the energy stand's rules judged on invented readings — so each one is shown to fail on the violation it
 * names, not only to pass on a healthy bay. The headline case is the one the stand exists for: a store charging
 * while a machine on the same line starves ("stores before machines") fails the bay.
 */
class EnergyStandExpectationTest {
	private static final int T = 200;

	private static ProbeReading machine(String name, long need, long used) {
		return new ProbeReading(name, ProbeKind.MACHINE, T, need, used, used, 0, 0, true);
	}

	private static ProbeReading store(String name, long delta) {
		return new ProbeReading(name, ProbeKind.STORE, T, 0, 0, Math.max(0, delta), delta, 0, true);
	}

	private static ProbeReading source(String name, long out) {
		return new ProbeReading(name, ProbeKind.SOURCE, T, 0, 0, 0, 0, out, false);
	}

	private static BayReading bay(Map<String, Boolean> flags, ProbeReading... probes) {
		Map<String, ProbeReading> map = new LinkedHashMap<>();
		for (ProbeReading p : probes) {
			map.put(p.name(), p);
		}
		return new BayReading("E00", T, map, 0, 4, T, flags);
	}

	private static boolean passes(List<Expectation> rules, BayReading reading) {
		return rules.stream().allMatch(rule -> rule.judge(reading).pass());
	}

	/** The rules of a "machine behind a box" bay. */
	private static final List<Expectation> MACHINE_THEN_BOX = List.of(Expectation.fed("M1"),
			Expectation.charges("B1", 4), Expectation.conserves(0.25));

	@Test
	void aHealthyBayPasses() {
		BayReading healthy = bay(Map.of(), source("S1", 12 * T), machine("M1", 2, 2 * T), store("B1", 10 * T));
		assertTrue(passes(MACHINE_THEN_BOX, healthy));
	}

	@Test
	void storesBeforeMachinesFailsTheBay() {
		BayReading storesFirst = bay(Map.of(), source("S1", 12 * T), machine("M1", 2, 0), store("B1", 12 * T));
		assertFalse(passes(MACHINE_THEN_BOX, storesFirst));
		Expectation.Verdict fed = Expectation.fed("M1").judge(storesFirst);
		assertFalse(fed.pass());
		assertEquals("M1 used 0.00/t of need 2/t", fed.detail());
	}

	@Test
	void aMachineAtEightyPercentIsNotFed() {
		assertFalse(Expectation.fed("M1").judge(bay(Map.of(), machine("M1", 10, 8 * T))).pass());
		assertTrue(Expectation.fed("M1").judge(bay(Map.of(), machine("M1", 10, 9 * T))).pass());
	}

	@Test
	void countersOffIsNeverAPass() {
		ProbeReading blind = new ProbeReading("M1", ProbeKind.MACHINE, T, 2, 2 * T, 2 * T, 0, 0, false);
		assertFalse(Expectation.fed("M1").judge(bay(Map.of(), blind)).pass());
		assertFalse(Expectation.conserves(0.25).judge(bay(Map.of(), source("S1", 2 * T), blind)).pass());
	}

	@Test
	void theFarMachineAheadOfTheNearOneFails() {
		BayReading farFirst = bay(Map.of(), machine("M1", 2, T), machine("M2", 2, 2 * T));
		assertFalse(Expectation.nearFirst("M1", "M2").judge(farFirst).pass());
		assertTrue(Expectation.nearFirst("M2", "M1").judge(farFirst).pass());
	}

	@Test
	void energyFromNothingAndExcessLossBothFail() {
		BayReading fromNothing = bay(Map.of(), source("S1", 10 * T), machine("M1", 2, 2 * T), store("B1", 12 * T));
		assertFalse(Expectation.conserves(0.25).judge(fromNothing).pass());
		BayReading lossy = bay(Map.of(), source("S1", 20 * T), machine("M1", 2, 2 * T), store("B1", 2 * T));
		assertFalse(Expectation.conserves(0.25).judge(lossy).pass());
		assertTrue(Expectation.conserves(0.9).judge(lossy).pass());
	}

	@Test
	void theFarStoreChargingFirstFails() {
		BayReading farFirst = bay(Map.of(), store("B1", 0), store("B2", 12 * T));
		assertFalse(Expectation.storeOrder("B1", "B2").judge(farFirst).pass());
	}

	@Test
	void theGapFlagPicksTheRule() {
		Expectation gap = Expectation.when("gap", Expectation.fed("M1"), Expectation.dark("M1"));
		BayReading dark = bay(Map.of("gap", false), machine("M1", 2, 0));
		assertTrue(gap.judge(dark).pass());
		assertFalse(gap.judge(bay(Map.of("gap", true), machine("M1", 2, 0))).pass());
		assertTrue(gap.judge(bay(Map.of("gap", true), machine("M1", 2, 2 * T))).pass());
	}

	@Test
	void anUnknownProbeNameFailsInsteadOfThrowing() {
		assertFalse(Expectation.fed("M9").judge(bay(Map.of(), machine("M1", 2, 2 * T))).pass());
	}
}
