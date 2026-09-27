package dev.alaindustrial.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * L1 — the Network Analyzer's "moved" arithmetic (MOD-665): a stale reading is not reported as current
 * (D10), and energy that passes through a store is not summed twice across the networks it bridges (D11).
 */
class AnalyzerTotalsTest {

	/**
	 * @implements MOD-665-D10 — a network's last delivery counts only while its last tick is recent
	 * @covers MOD-665
	 */
	@Test
	void staleReadingIsZero() {
		assertEquals(40L, AnalyzerTotals.fresh(40L, 1000L, 1000L), "same tick is current");
		assertEquals(40L, AnalyzerTotals.fresh(40L, 1000L, 1000L + AnalyzerTotals.FRESH_WINDOW_TICKS),
				"the edge of the window is still current");
		assertEquals(0L, AnalyzerTotals.fresh(40L, 1000L, 1001L + AnalyzerTotals.FRESH_WINDOW_TICKS),
				"a network that stopped ticking a second ago must not keep reporting its old flow");
		assertEquals(0L, AnalyzerTotals.fresh(40L, Long.MIN_VALUE, 5L), "never ticked is nothing");
	}

	/**
	 * @implements MOD-665-D11 — generator → store → machine sums the machine's share once, not the
	 *     store's charge on top of it
	 * @covers MOD-665
	 */
	@Test
	void passThroughStoreCountsOnce() {
		// Network A: the generator charges the store 10 EU (all of it into storage).
		// Network C: the store discharges 6 EU into the line, 6 reach the machine.
		long[] moved = {10, 6};
		long[] toStorage = {10, 0};
		long[] fromStorage = {0, 6};
		// Machines got 6; the store gained 10 - 6 = 4 net. The old plain sum said 16.
		assertEquals(10L, AnalyzerTotals.deliveredAcross(moved, toStorage, fromStorage));
	}

	/** @implements MOD-665-D11 — a draining store adds nothing of its own; charging alone still shows */
	@Test
	void drainingOrChargingStores() {
		assertEquals(6L, AnalyzerTotals.deliveredAcross(new long[] {0, 6}, new long[] {0, 0}, new long[] {0, 6}),
				"no generator: only what reached the machine");
		assertEquals(10L, AnalyzerTotals.deliveredAcross(new long[] {10}, new long[] {10}, new long[] {0}),
				"a bank that only charges still reads as moving energy");
		assertEquals(0L, AnalyzerTotals.deliveredAcross(new long[0], new long[0], new long[0]));
		assertThrows(IllegalArgumentException.class,
				() -> AnalyzerTotals.deliveredAcross(new long[1], new long[2], new long[1]));
	}
}
