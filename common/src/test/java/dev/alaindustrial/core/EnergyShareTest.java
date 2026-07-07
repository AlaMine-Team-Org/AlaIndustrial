package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link EnergyShare} — the pure energy-distribution arithmetic that backs
 * {@link EnergyNetwork#tick()}. No Minecraft runtime; deterministic. Replaces the implicit coverage
 * that previously only existed inside the L2 gametests.
 *
 * @implements energy-network distribution math (loss, deliverable, proportional split)
 */
class EnergyShareTest {

	// --- deliverable (MOD-009: throughput limit, no EU-destroying loss) ---

	@Test
	void deliverable_isMinOfSupplyAndDemand() {
		assertEquals(20L, EnergyShare.deliverable(100, 20), "demand-bound");
		assertEquals(8L, EnergyShare.deliverable(8, 50), "supply-bound");
	}

	@Test
	void deliverable_smallPacketStillMoves() {
		// The MOD-009 fix: the last tiny top-off packet is NOT swallowed — a buffer can reach 100%.
		assertEquals(2L, EnergyShare.deliverable(2, 2), "2 EU room → 2 EU delivered (no flat loss)");
		assertEquals(1L, EnergyShare.deliverable(64, 1), "1 EU room near full → 1 EU delivered");
	}

	@Test
	void deliverable_neverNegative() {
		assertEquals(0L, EnergyShare.deliverable(0, 0));
		assertEquals(0L, EnergyShare.deliverable(0, 100));
	}

	// --- cableLoss (MOD-021: proportional per-consumer cable loss) ---

	@Test
	void cableLoss_zeroWhenNoFlowDistanceOrRate() {
		assertEquals(0L, EnergyShare.cableLoss(0, 0.0125, 10), "no flow → no loss");
		assertEquals(0L, EnergyShare.cableLoss(32, 0.0125, 0), "zero distance → no loss");
		assertEquals(0L, EnergyShare.cableLoss(32, 0.0, 10), "zero rate → no loss");
	}

	@Test
	void cableLoss_trickleTopOffFloorsToZero() {
		// The MOD-009 guard: a 1 EU top-off packet over a 10-cable line loses nothing (floor(0.125)=0),
		// so a buffer still reaches exact capacity — a flat toll would have stranded it forever.
		assertEquals(0L, EnergyShare.cableLoss(1, 0.0125, 10), "1 EU over 10 cables → 0 loss");
	}

	@Test
	void cableLoss_shortLineAtFullPacketIsFree() {
		// floor(32 × 0.0125 × 2) = floor(0.8) = 0 — a 1–2 cable hop is loss-free even at a full packet.
		assertEquals(0L, EnergyShare.cableLoss(32, 0.0125, 2));
	}

	@Test
	void cableLoss_scalesWithFlowAndDistance() {
		assertEquals(1L, EnergyShare.cableLoss(8, 0.0125, 10), "fuel-gen flow (8) over 10 cables → 1");
		assertEquals(4L, EnergyShare.cableLoss(32, 0.0125, 10), "full packet (32) over 10 cables → 4");
	}

	@Test
	void cableLoss_neverExceedsGross() {
		// floor(200 × 0.0125 × 100) = 250, but you cannot lose more than what flowed → clamped to 200.
		assertEquals(200L, EnergyShare.cableLoss(200, 0.0125, 100));
	}

	// --- split ---

	@Test
	void split_exactProportions_noRemainder() {
		long[] share = EnergyShare.split(10, new long[] {5, 3, 2}, 10, 32);
		assertArrayEquals(new long[] {5, 3, 2}, share);
	}

	@Test
	void split_roundingRemainder_fullyRedistributed() {
		// 8 EU over three equal rooms of 3 (demand 9): floor gives 2+2+2=6, remainder 2 handed out.
		long[] share = EnergyShare.split(8, new long[] {3, 3, 3}, 9, 32);
		assertEquals(8L, sum(share), "every deliverable EU is placed (remainder not lost)");
		for (long s : share) {
			assertTrue(s <= 3, "never exceeds a consumer's room");
		}
	}

	@Test
	void split_packetCapLimitsDelivery() {
		// One consumer with huge room but a 32 EU/t cap: at most 32 moves even though 40 was offered.
		long[] share = EnergyShare.split(40, new long[] {100}, 100, 32);
		assertArrayEquals(new long[] {32}, share);
	}

	@Test
	void split_neverExceedsRoomOrCap_andSumWithinMoveTotal() {
		long packetCap = 32;
		long[] room = {7, 1, 50, 12};
		long demand = Arrays.stream(room).sum();
		long moveTotal = 40; // ≤ demand, per the production contract
		long[] share = EnergyShare.split(moveTotal, room, demand, packetCap);
		long total = 0;
		for (int i = 0; i < room.length; i++) {
			assertTrue(share[i] >= 0, "no negative share");
			assertTrue(share[i] <= room[i], "share ≤ room");
			assertTrue(share[i] <= packetCap, "share ≤ packet cap");
			total += share[i];
		}
		assertTrue(total <= moveTotal, "total distributed ≤ moveTotal");
	}

	@Test
	void split_zeroMoveTotal_givesAllZeros() {
		assertArrayEquals(new long[] {0, 0}, EnergyShare.split(0, new long[] {5, 5}, 10, 32));
	}

	@Test
	void split_singleConsumer_takesItsCappedShare() {
		assertArrayEquals(new long[] {16}, EnergyShare.split(16, new long[] {20}, 20, 32));
	}

	private static long sum(long[] a) {
		long s = 0;
		for (long v : a) {
			s += v;
		}
		return s;
	}
}
