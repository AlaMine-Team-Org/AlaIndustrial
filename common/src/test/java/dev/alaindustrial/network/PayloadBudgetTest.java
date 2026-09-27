package dev.alaindustrial.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 — the Network Analyzer payload has a ceiling (MOD-665, D4): a base big enough to exceed vanilla's
 * 1 MiB custom-payload limit used to be shipped whole, and on Fabric that drops the connection.
 */
class PayloadBudgetTest {

	/** Vanilla {@code ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE} in 26.3 (checked with javap). */
	private static final int VANILLA_MAX_PAYLOAD_BYTES = 1_048_576;

	/**
	 * @implements MOD-665-D4-PAYLOAD — the capped payload stays well inside vanilla's limit
	 * @covers MOD-665
	 */
	@Test
	void ceilingFitsInsideTheVanillaLimit() {
		// 8 bytes per position plus one face byte per endpoint — the worst case counts every position as an
		// endpoint. Leaves half the limit for list headers, the dimension key and the numbers.
		long worst = (long) PayloadBudget.MAX_POSITIONS * (8 + 1);
		assertTrue(worst <= VANILLA_MAX_PAYLOAD_BYTES / 2,
				"MAX_POSITIONS=" + PayloadBudget.MAX_POSITIONS + " can reach " + worst + " bytes");
	}

	/** @implements MOD-665-D4-PAYLOAD — a network over the ceiling is cut, endpoints first, and flagged */
	@Test
	void fitKeepsEndpointsAndCutsCables() {
		assertTrue(PayloadBudget.truncates(200_000, 10, 20, 5, PayloadBudget.MAX_POSITIONS));
		assertArrayEquals(new int[] {PayloadBudget.MAX_POSITIONS - 35, 10, 20, 5},
				PayloadBudget.fit(200_000, 10, 20, 5, PayloadBudget.MAX_POSITIONS));
		assertArrayEquals(new int[] {0, 3, 1, 0}, PayloadBudget.fit(50, 3, 9, 9, 4),
				"endpoints alone over the ceiling: producers, then consumers, then storage");
	}

	/** @implements MOD-665-D4-PAYLOAD — a network under the ceiling goes out whole */
	@Test
	void smallNetworkIsNotTouched() {
		assertFalse(PayloadBudget.truncates(100, 2, 3, 1, PayloadBudget.MAX_POSITIONS));
		assertArrayEquals(new int[] {100, 2, 3, 1}, PayloadBudget.fit(100, 2, 3, 1, PayloadBudget.MAX_POSITIONS));
	}
}
