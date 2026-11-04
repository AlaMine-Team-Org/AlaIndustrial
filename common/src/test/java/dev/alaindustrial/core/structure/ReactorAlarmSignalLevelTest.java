package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The antenna boom's alarm level (MOD-945): 0 / 7 / 15 from the warning latch, the meltdown latch and the countdown.
 *
 * <p>The controller drives the two latches with {@link ReactorCore#alarmStaysLatched} — the warning one between the
 * warning line (70) and the rearm floor (60), the meltdown one between the meltdown line (85) and the warning line —
 * and hands them to {@link ReactorCore#alarmSignal}. These tests replay that on gauge sequences, with the shipped
 * thresholds, and set a naive threshold reading beside it as the negative control: the latches are the whole point.
 */
class ReactorAlarmSignalLevelTest {

	private static final int WARN = 70;
	private static final int REARM = 60;
	private static final int MELTDOWN = 85;

	/** The boom's level on every step of {@code gauge}, as the controller works it out tick by tick. */
	private static int[] latched(int... gauge) {
		int[] levels = new int[gauge.length];
		boolean warned = false;
		boolean melting = false;
		for (int i = 0; i < gauge.length; i++) {
			warned = ReactorCore.alarmStaysLatched(gauge[i], WARN, REARM, warned);
			melting = ReactorCore.alarmStaysLatched(gauge[i], MELTDOWN, WARN, melting);
			levels[i] = ReactorCore.alarmSignal(warned, melting, false);
		}
		return levels;
	}

	/** The negative control: the same gauge read with bare thresholds and no latch at all. */
	private static int[] naive(int... gauge) {
		int[] levels = new int[gauge.length];
		for (int i = 0; i < gauge.length; i++) {
			levels[i] = gauge[i] >= MELTDOWN ? 15 : gauge[i] >= WARN ? 7 : 0;
		}
		return levels;
	}

	private static int changes(int[] levels) {
		int changes = 0;
		for (int i = 1; i < levels.length; i++) {
			if (levels[i] != levels[i - 1]) {
				changes++;
			}
		}
		return changes;
	}

	@Test
	void theThreeLevelsComeFromTheThreeLatches() {
		assertEquals(0, ReactorCore.alarmSignal(false, false, false), "nothing latched: normal");
		assertEquals(7, ReactorCore.alarmSignal(true, false, false), "warning latch only");
		assertEquals(15, ReactorCore.alarmSignal(true, true, false), "meltdown latch");
		assertEquals(15, ReactorCore.alarmSignal(false, false, true), "an armed countdown alone is an emergency");
		assertEquals(15, ReactorCore.alarmSignal(true, false, true), "countdown over a warning");
	}

	@Test
	void aWholeExcursionClimbsAndFallsThroughBothDeadbands() {
		int[] gauge = {50, 71, 69, 65, 61, 60, 59, 71, 86, 84, 75, 69, 100};
		assertArrayEquals(new int[] {0, 7, 7, 7, 7, 7, 0, 7, 15, 15, 15, 7, 15}, latched(gauge));
	}

	@Test
	void bothBoundariesOfBothLatchesArePinned() {
		// The rearm floor itself still holds the warning; one point under it lets go.
		assertArrayEquals(new int[] {7, 7, 0}, latched(70, 60, 59));
		// The meltdown latch holds down to the warning line and lets go one point under it.
		assertArrayEquals(new int[] {15, 15, 7}, latched(85, 70, 69));
		// Each line switches on exactly at itself, not one point later.
		assertArrayEquals(new int[] {0, 7, 7, 15}, latched(69, 70, 84, 85));
	}

	@Test
	void aGaugeWobblingAtTheWarningLineDoesNotChatter() {
		int[] gauge = {65, 71, 69, 71, 69, 70, 68, 71, 69};
		assertEquals(1, changes(latched(gauge)), "the warning switches on once and stays on");
		// Negative control: without the latch the same wobble flips the signal on every step.
		assertTrue(changes(naive(gauge)) >= 6, "a bare threshold chatters on this gauge");
	}

	@Test
	void aGaugeWobblingAtTheMeltdownLineStaysAnEmergency() {
		int[] gauge = {80, 86, 84, 86, 84, 85, 84, 86};
		assertEquals(1, changes(latched(gauge)), "15 once, then held through 84-86");
		assertTrue(changes(naive(gauge)) >= 5, "a bare threshold jumps 15/7 on this gauge");
	}
}
