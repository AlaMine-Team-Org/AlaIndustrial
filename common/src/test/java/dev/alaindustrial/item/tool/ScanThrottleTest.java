package dev.alaindustrial.item.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 — holding the use key on a cable no longer runs a full scan every four ticks (MOD-665, D4).
 */
class ScanThrottleTest {
	private static final UUID ALICE = new UUID(1, 1);
	private static final UUID BOB = new UUID(2, 2);

	/**
	 * @implements MOD-665-D4-THROTTLE — the use key held on one cable scans once, not five times a second
	 * @covers MOD-665
	 */
	@Test
	void heldUseKeyScansOnce() {
		ScanThrottle throttle = new ScanThrottle();
		int scans = 0;
		// Vanilla repeats the use every 4 ticks while the key is held: 10 repeats over 40 ticks.
		for (long t = 100; t < 100 + ScanThrottle.SAME_TARGET_TICKS; t += 4) {
			if (throttle.tryScan(ALICE, 42L, t)) {
				scans++;
			}
		}
		assertTrue(scans == 1, "same cable held for two seconds must scan once, scanned " + scans);
		assertTrue(throttle.tryScan(ALICE, 42L, 100 + ScanThrottle.SAME_TARGET_TICKS),
				"after the window the same cable may be scanned again");
	}

	/** @implements MOD-665-D4-THROTTLE — another cable after half a second, other players never blocked */
	@Test
	void otherTargetsAndPlayers() {
		ScanThrottle throttle = new ScanThrottle();
		assertTrue(throttle.tryScan(ALICE, 1L, 0));
		assertFalse(throttle.tryScan(ALICE, 2L, ScanThrottle.MIN_INTERVAL_TICKS - 1),
				"a sweep across cables with the key held is still limited");
		assertTrue(throttle.tryScan(ALICE, 2L, ScanThrottle.MIN_INTERVAL_TICKS));
		assertTrue(throttle.tryScan(BOB, 1L, 1), "one player's scans never hold up another's");
	}

	/** @implements MOD-665-D4-THROTTLE — a world with an earlier clock is never throttled by the last one */
	@Test
	void clockGoingBackwardsDoesNotBlock() {
		ScanThrottle throttle = new ScanThrottle();
		assertTrue(throttle.tryScan(ALICE, 7L, 50_000));
		assertTrue(throttle.tryScan(ALICE, 7L, 10));
	}
}
