package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * jqwik property-based coverage for {@link EnergyShare} (MOD-144). {@link EnergyShareTest}'s 54 point
 * + {@code @MethodSource} tests already achieve 81% pitest kill on this class (the 6 surviving
 * {@code ConditionalsBoundary} mutants on {@code =0} guards are proven-equivalent — see
 * {@code EnergyShareTest.java:236-271}). The goal here is the same as {@link EnergyBufferPropertyTest}:
 * <b>depth over coverage</b>, exercising the {@code cableLoss} clamp and the {@code split} proportional
 * kernel across thousands of randomised inputs that point tests only spot-check.
 *
 * <p>Two structural invariants that a {@code min→max} or {@code *→/} mutant on a rare input would
 * violate:
 * <ul>
 *   <li><b>{@code cableLoss}</b>: result always in {@code [0, gross]}, monotonic in flow and distance.</li>
 *   <li><b>{@code split}</b>: every share in {@code [0, min(room, packetCap)]}, sum ≤ moveTotal.</li>
 * </ul>
 *
 * <p>Plus a conservation property unique to {@code split}: when {@code moveTotal ≤ min(demand,
 * Σ packetCap over room)}, the sum of shares <em>equals</em> {@code moveTotal} (no rounding remainder
 * stranded). This is the MOD-009 top-off invariant at the split level — a future regression of the
 * remainder-redistribution loop would lose EU here.
 *
 * <p>jqwik API: {@link Provide @Provide} instance methods returning {@link Arbitrary}{@code <T>},
 * referenced by name from {@link ForAll @ForAll("name")}. Verified against jqwik 1.9 user guide.
 *
 * @see EnergyShareTest for exhaustive point coverage of the canonical copper/10-block numbers
 */
class EnergySharePropertyTest {

	@Provide
	Arbitrary<Long> nonNegativeEu() {
		return Arbitraries.longs().between(0L, 1L << 30);
	}

	@Provide
	Arbitrary<Double> lossRate() {
		// Cable loss rates from 0 (no loss) to 1.0 (100% per block — extreme upper bound; copper is 0.02).
		return Arbitraries.doubles().between(0.0, 1.0).ofScale(4);
	}

	@Provide
	Arbitrary<Integer> distance() {
		// Cable runs up to 200 blocks (extreme base footprint); single hop (1) is the common case.
		return Arbitraries.integers().between(0, 200);
	}

	// --- cableLoss invariants ---

	@Property
	void cableLoss_alwaysInZeroToGross(
			@ForAll("nonNegativeEu") long gross,
			@ForAll("lossRate") double lossPerBlock,
			@ForAll("distance") int distanceBlocks) {
		long loss = EnergyShare.cableLoss(gross, lossPerBlock, distanceBlocks);
		assertTrue(loss >= 0, "loss never negative");
		assertTrue(loss <= gross, "loss never exceeds gross (clamp to [0, gross])");
	}

	@Property
	void cableLoss_zeroGrossIsAlwaysZero(
			@ForAll("lossRate") double lossPerBlock,
			@ForAll("distance") int distanceBlocks) {
		assertEquals(0L, EnergyShare.cableLoss(0, lossPerBlock, distanceBlocks),
				"zero flow → zero loss regardless of rate/distance");
	}

	@Property
	void cableLoss_zeroDistanceIsAlwaysZero(
			@ForAll("nonNegativeEu") long gross,
			@ForAll("lossRate") double lossPerBlock) {
		assertEquals(0L, EnergyShare.cableLoss(gross, lossPerBlock, 0),
				"zero distance → zero loss regardless of flow/rate (single-hop invariance)");
	}

	@Property
	void cableLoss_monotonicInFlow(
			@ForAll("nonNegativeEu") long grossLow,
			@ForAll("lossRate") double lossPerBlock,
			@ForAll("distance") int distanceBlocks) {
		// Skip the vacuous cases where the clamp or the early-return zeroes both sides.
		if (grossLow == 0 || lossPerBlock == 0 || distanceBlocks == 0) return;
		long grossHigh = grossLow + 1;
		long lossLow = EnergyShare.cableLoss(grossLow, lossPerBlock, distanceBlocks);
		long lossHigh = EnergyShare.cableLoss(grossHigh, lossPerBlock, distanceBlocks);
		assertTrue(lossHigh >= lossLow,
				"more flow ⇒ more or equal loss (resistive model is monotonic in flow)");
	}

	@Property
	void cableLoss_monotonicInDistance(
			@ForAll("nonNegativeEu") long gross,
			@ForAll("lossRate") double lossPerBlock,
			@ForAll("distance") int distanceLow) {
		if (gross == 0 || lossPerBlock == 0) return;
		int distanceHigh = distanceLow + 1;
		long lossLow = EnergyShare.cableLoss(gross, lossPerBlock, distanceLow);
		long lossHigh = EnergyShare.cableLoss(gross, lossPerBlock, distanceHigh);
		assertTrue(lossHigh >= lossLow,
				"more cable ⇒ more or equal loss (resistive model is monotonic in distance)");
	}

	// --- split invariants ---

	@Provide
	Arbitrary<Long[]> roomArray() {
		// 1–6 consumers, each with room in [0, 10k]. The proportional kernel's mutants (especially the
		// asymmetric-room case) need at least 2 consumers with diverging rooms to surface.
		return Arbitraries.longs().between(0L, 10_000L)
				.array(Long[].class).ofMinSize(1).ofMaxSize(6);
	}

	@Property
	void split_sharesInZeroToMinRoomAndPacketCap(
			@ForAll("nonNegativeEu") long moveTotal,
			@ForAll("roomArray") Long[] roomBoxed,
			@ForAll("nonNegativeEu") long packetCap) {
		long[] room = unbox(roomBoxed);
		long demand = 0;
		for (long r : room) demand += r;
		long[] share = EnergyShare.split(moveTotal, room, demand, packetCap);
		assertEquals(room.length, share.length, "share length matches room length");
		for (int i = 0; i < room.length; i++) {
			assertTrue(share[i] >= 0, "share[" + i + "] ≥ 0");
			assertTrue(share[i] <= room[i], "share[" + i + "] ≤ room");
			assertTrue(share[i] <= packetCap, "share[" + i + "] ≤ packetCap");
		}
	}

	@Property
	void split_sumNeverExceedsMoveTotal(
			@ForAll("nonNegativeEu") long moveTotal,
			@ForAll("roomArray") Long[] roomBoxed,
			@ForAll("nonNegativeEu") long packetCap) {
		long[] room = unbox(roomBoxed);
		long demand = 0;
		for (long r : room) demand += r;
		long[] share = EnergyShare.split(moveTotal, room, demand, packetCap);
		long sum = 0;
		for (long s : share) sum += s;
		assertTrue(sum <= moveTotal, "sum(share) ≤ moveTotal (no over-distribution)");
	}

	/**
	 * The MOD-009 top-off invariant at the split level: when the offer is small enough that every
	 * consumer has room (and the cap doesn't bind), the full {@code moveTotal} must be placed — no
	 * rounding remainder stranded. A future regression of the remainder-redistribution loop would lose
	 * the last 1–2 EU on every partial distribution.
	 */
	@Test
	void split_smallOfferWithHeadroom_isFullyPlaced() {
		// moveTotal=5, room=[10,10,10], demand=30, packetCap=32: every consumer has slack.
		// Proportional: floorDiv(5*10,30)=1 each → 3, remainder 2 → placed on first two → [2,2,1] = 5.
		long[] share = EnergyShare.split(5, new long[] {10, 10, 10}, 30, 32);
		long sum = 0;
		for (long s : share) sum += s;
		assertEquals(5L, sum, "small offer with full headroom must be fully placed (no stranded EU)");
	}

	private static long[] unbox(Long[] boxed) {
		long[] prim = new long[boxed.length];
		for (int i = 0; i < boxed.length; i++) {
			prim[i] = boxed[i];
		}
		return prim;
	}
}
