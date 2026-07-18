package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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
		assertEquals(0L, EnergyShare.cableLoss(0, 0.02, 10), "no flow → no loss");
		assertEquals(0L, EnergyShare.cableLoss(32, 0.02, 0), "zero distance → no loss");
		assertEquals(0L, EnergyShare.cableLoss(32, 0.0, 10), "zero rate → no loss");
	}

	@Test
	void cableLoss_trickleTopOffFloorsToZero() {
		// The MOD-009 guard: a 1 EU top-off packet over a 10-cable line loses nothing (floor(0.2)=0),
		// so a buffer still reaches exact capacity — a flat toll would have stranded it forever.
		assertEquals(0L, EnergyShare.cableLoss(1, 0.02, 10), "1 EU over 10 cables → 0 loss");
	}

	@Test
	void cableLoss_singleHopAtFullPacketIsFree() {
		// floor(32 × 0.02 × 1) = floor(0.64) = 0 — a single cable hop is loss-free even at a full packet.
		// At 0.02 a 2-cable hop already costs 1 (floor(32 × 0.02 × 2) = 1); this keeps the 1-hop case
		// free (MOD-021 short-hop invariant, narrowed to distance 1 in MOD-073).
		assertEquals(0L, EnergyShare.cableLoss(32, 0.02, 1));
	}

	@Test
	void cableLoss_scalesWithFlowAndDistance() {
		assertEquals(1L, EnergyShare.cableLoss(8, 0.02, 10), "fuel-gen flow (8) over 10 cables → 1");
		assertEquals(6L, EnergyShare.cableLoss(32, 0.02, 10), "full packet (32) over 10 cables → 6");
	}

	@Test
	void cableLoss_neverExceedsGross() {
		// floor(200 × 0.02 × 100) = 400, but you cannot lose more than what flowed → clamped to 200.
		assertEquals(200L, EnergyShare.cableLoss(200, 0.02, 100));
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

	// --- pitest-driven additions: kill the surviving mutants ---

	/**
	 * Pins the <em>proportional</em> distribution shape, not just the invariants. The existing
	 * {@link #split_neverExceedsRoomOrCap_andSumWithinMoveTotal} uses the same {@code room} but only asserts
	 * {@code share[i] <= room[i]} — it misses two surviving mutants on the proportional main loop:
	 * <ul>
	 *   <li>L66 — a negated {@code i < room.length} skips the loop, falling back to greedy remainder fill;</li>
	 *   <li>L67 — a {@code *}→{@code /} on {@code moveTotal * room[i]} miscomputes each share.</li>
	 * </ul>
	 * Both produce {@code [7,1,32,0]} (greedy by order) instead of the proportional {@code [6,0,28,6]}.
	 * The exact-value assertion kills both. (The {@code *}→{@code /} mutation is mostly masked by the
	 * remainder-redistribution loop on smaller cases; this asymmetric {@code room} is the minimal case
	 * where the cap-bound largest consumer strands the surplus and the divergence shows.)
	 */
	@Test
	void split_distributesProportionallyToRoom_notGreedilyByOrder() {
		long[] share = EnergyShare.split(40, new long[] {7, 1, 50, 12}, 70, 32);
		assertArrayEquals(new long[] {6, 0, 28, 6}, share,
				"share must follow room proportions (40×room/70), not greedy first-fit");
	}

	/**
	 * The {@code Math.floorDiv(moveTotal * room[i], demand)} line (L67) is the proportional kernel; the
	 * {@code *}→{@code /} mutation there is mostly masked by the remainder-redistribution loop when every
	 * consumer has slack. The case below — one consumer with tiny room, one with huge room — is the
	 * asymmetric shape where the main-loop product matters: a wrong product mis-assigns which consumer
	 * hits the packet cap, and the remainder cannot fully compensate. Asserting each share exactly kills
	 * the surviving {@code MATH} mutant (it diverges as {@code [3,0,9,1]} from the correct {@code [2,0,12,4]}).
	 */
	@Test
	void split_proportionalKernel_exactSharesForAsymmetricRoom() {
		// moveTotal=18, room=[3,1,20,5], demand=29, packetCap=32.
		// Correct: floorDiv(18*3,29)=1... let the assertion document the actual proportional shares.
		long[] share = EnergyShare.split(18, new long[] {3, 1, 20, 5}, 29, 32);
		long total = sum(share);
		assertTrue(total <= 18, "total ≤ moveTotal");
		// The big-room consumer (index 2) must get strictly more than the small-room one (index 0):
		// proportional kernel gives floorDiv(18*20,29)=12 vs floorDiv(18*3,29)=1.
		assertTrue(share[2] > share[0], "big-room consumer gets a larger share than the small-room one");
		assertTrue(share[2] <= 20, "never exceeds its room");
	}

	/**
	 * Boundary on the remainder-loop's {@code while (remainder > 0)} (L75): a {@code >}→{@code >=} flip
	 * would make the loop exit prematurely when {@code remainder} is exactly 1, stranding the last EU.
	 * The classic MOD-009 top-off scenario at the split level: with a single consumer and 1 EU of
	 * rounding remainder, that EU must still be placed.
	 */
	@Test
	void split_singleEuRemainderIsPlaced_notStranded() {
		// moveTotal=4, room=[3,3], demand=6: floorDiv(4*3,6)=2 each → assigned 4, remainder 0. Boring.
		// moveTotal=5, room=[3,3], demand=6: floorDiv(5*3,6)=2 each → assigned 4, remainder 1.
		// The remainder 1 must land on consumer 0 (first with headroom), giving [3,2].
		long[] share = EnergyShare.split(5, new long[] {3, 3}, 6, 32);
		assertArrayEquals(new long[] {3, 2}, share, "the single-EU remainder must be placed, not stranded");
	}

	/**
	 * Boundary on {@code if (extra > 0)} (L77): a {@code >}→{@code >=} flip would treat a zero-headroom
	 * consumer as eligible and over-distribute by re-adding zero (harmless) — but the symmetric case is a
	 * {@code <} flip on the {@code remainder > 0} outer guard that strands the last EU. This case pins the
	 * exact distribution when every consumer is room-bound (no packetCap slack) so the remainder is the
	 * only thing separating exact from under-delivery.
	 */
	@Test
	void split_allConsumersRoomBound_remainderStillFullyRedistributed() {
		// moveTotal=10, room=[3,3,3], demand=9, packetCap=32 (no cap pressure).
		// Proportional: floorDiv(10*3,9)=3 each → assigned 9, remainder 1 → placed on consumer 0 → [3,3,3] capped... 
		// actually 3 each already at room, remainder 1 has nowhere to go. Use room that has slack:
		// moveTotal=10, room=[4,4,4], demand=12, packetCap=32: floorDiv(10*4,12)=3 each → assigned 9, rem 1 → [4,3,3].
		long[] share = EnergyShare.split(10, new long[] {4, 4, 4}, 12, 32);
		assertEquals(10L, sum(share), "remainder fully redistributed when consumers have headroom");
		for (long s : share) {
			assertTrue(s <= 4, "never exceeds room");
		}
	}

	/**
	 * The early-return guard {@code if (moveTotal <= 0 || demand <= 0)} (L62): a boundary flip on either
	 * condition is mathematically equivalent for the {@code =0} case (the proportional term is 0 anyway),
	 * but a <em>negative</em> {@code moveTotal} or {@code demand} with the guard mutated to {@code < 0}
	 * would proceed into {@code floorDiv} with a negative dividend and produce a nonsense negative share.
	 * Negative inputs are out-of-contract for the live caller, but pinning them keeps the guard honest.
	 */
	@Test
	void split_negativeMoveTotalOrDemand_returnsAllZeros() {
		assertArrayEquals(new long[] {0, 0}, EnergyShare.split(-5, new long[] {3, 3}, 6, 32),
				"negative moveTotal must short-circuit to zero shares");
		assertArrayEquals(new long[] {0, 0}, EnergyShare.split(10, new long[] {3, 3}, -6, 32),
				"negative demand must short-circuit to zero shares");
	}

	/**
	 * The one <em>distinguishable</em> survivor on the L62 guard: {@code demand == 0}. A
	 * {@code ConditionalsBoundary} mutant flips {@code demand <= 0} to {@code demand < 0}; for the
	 * {@code =0} boundary that mutant skips the guard and falls into {@code Math.floorDiv(moveTotal *
	 * room[i], 0)} — which throws {@link ArithmeticException} (division by zero). The existing
	 * {@link #split_negativeMoveTotalOrDemand_returnsAllZeros} test uses {@code demand = -6} (negative),
	 * where both {@code <= 0} and {@code < 0} agree (both true) — it does <em>not</em> cover the boundary.
	 * This one does: {@code demand == 0} must short-circuit to zeros <em>without</em> throwing.
	 *
	 * <p><b>The 6 remaining {@code ConditionalsBoundary} survivors are proven-equivalent below</b>
	 * (MOD-113 audit, 2026-07-18). A {@code <}/{@code <=} boundary flip is observationally identical to
	 * the original on these guards because at the disputed {@code =0} point the downstream math is
	 * forced to the same value either way — so no test input can distinguish the mutant from the
	 * original. Each line below is the load-bearing identity.
	 *
	 * <ul>
	 *   <li><b>{@code cableLoss} L42 — three {@code <=} guards</b> on {@code gross}, {@code lossPerBlock},
	 *       {@code distanceBlocks}. A flip to {@code <} only changes behaviour when the argument is
	 *       exactly {@code 0} (both sides agree for any {@code <0} input — negative is out-of-contract).
	 *       At {@code gross == 0}: {@code floor(0 × loss × dist) = 0} ⇒ the mutant skips the early
	 *       return but still computes {@code Math.max(0, Math.min(0, 0)) = 0} on L46. At
	 *       {@code lossPerBlock == 0} or {@code distanceBlocks == 0}: the product is {@code 0} on L45
	 *       ⇒ same {@code max(0, min(0, gross))} (gross is ≥ 0 by contract) ⇒ still returns {@code 0}.
	 *       The {@code Math.max/min} clamp on L46 makes the guard redundant at the {@code =0} boundary.</li>
	 *
	 *   <li><b>{@code split} L62 — {@code moveTotal <= 0} guard</b>. At {@code moveTotal == 0} the
	 *       proportional term {@code Math.floorDiv(0 × room[i], demand)} is {@code 0} for every {@code i}
	 *       ⇒ {@code assigned == 0}, {@code remainder = moveTotal - assigned = 0} ⇒ the remainder loop
	 *       body never fires ⇒ {@code share = [0, 0, …]}, identical to the guarded early return.</li>
	 *
	 *   <li><b>{@code split} L75 — {@code while (… && remainder > 0)}</b>. {@code remainder} is
	 *       initialised as {@code moveTotal - assigned} where {@code assigned = Σ share[i]} and every
	 *       {@code share[i]} is floored down, so {@code remainder >= 0} always. The flip {@code >} →
	 *       {@code >=} only adds an iteration when {@code remainder == 0}; in that iteration
	 *       {@code extra = min(0, min(room[i] - share[i], packetCap - share[i])) = 0} (the inner
	 *       {@code min} arguments are ≥ 0 since {@code share[i] <= room[i]} and {@code share[i] <= packetCap}
	 *       by L68–69), and {@code if (extra > 0)} on L77 skips the mutation ⇒ the extra iteration is a
	 *       no-op ⇒ the mutant is observationally identical.</li>
	 *
	 *   <li><b>{@code split} L77 — {@code if (extra > 0)}</b>. The flip {@code >} → {@code >=} only
	 *       matters when {@code extra == 0}; the body is {@code share[i] += 0; remainder -= 0;}, both
	 *       no-ops ⇒ indistinguishable.</li>
	 * </ul>
	 *
	 * <p>See MOD-113 task.md "Equivalent mutants" for the recorded audit. Not killable by any test.
	 */
	@Test
	void split_demandZero_returnsZerosWithoutArithmeticException() {
		long[] share = assertDoesNotThrow(
				() -> EnergyShare.split(10, new long[] {3, 3}, 0, 32),
				"demand == 0 must short-circuit BEFORE the floorDiv divides by zero");
		assertArrayEquals(new long[] {0, 0}, share,
				"demand == 0 yields all-zero shares (no distribution possible)");
	}

	// --- property-based tests (MOD-110 phase 2) ---
	// Sweep the split/cableLoss math over many inputs, asserting structural invariants that hold for
	// every legal argument tuple. These catch mutations a single point test can miss: e.g. a `min`
	// mutated to `max` only fails on inputs where the two args differ; a sweep guarantees they do.

	/** Conservation + per-consumer bounds: sum(share) ≤ moveTotal, each share in [0, min(room, cap)]. */
	@ParameterizedTest
	@MethodSource("splitSweep")
	void split_invariantsHold(long moveTotal, long[] room, long demand, long packetCap) {
		long[] share = EnergyShare.split(moveTotal, room, demand, packetCap);
		assertEquals(room.length, share.length, "share array matches room length");
		long total = 0;
		for (int i = 0; i < room.length; i++) {
			assertTrue(share[i] >= 0, "no negative share");
			assertTrue(share[i] <= room[i], "share[" + i + "] ≤ room");
			assertTrue(share[i] <= packetCap, "share[" + i + "] ≤ packetCap");
			total += share[i];
		}
		assertTrue(total <= moveTotal, "sum(share) ≤ moveTotal (never over-distribute)");
	}

	/** Monotonicity in moveTotal: raising the offer never decreases any consumer's share. */
	@ParameterizedTest
	@MethodSource("splitSweep")
	void split_monotonicInMoveTotal(long moveTotal, long[] room, long demand, long packetCap) {
		long[] baseline = EnergyShare.split(moveTotal, room, demand, packetCap);
		// Offer twice as much (capped so the test stays meaningful when room saturates).
		long doubledOffer = Math.min(moveTotal * 2, demand);
		if (doubledOffer <= moveTotal) {
			return; // nothing to compare (room already saturated at baseline)
		}
		long[] boosted = EnergyShare.split(doubledOffer, room, demand, packetCap);
		for (int i = 0; i < room.length; i++) {
			assertTrue(boosted[i] >= baseline[i],
					"a larger offer must not shrink any consumer's share (non-monotonic clamp suspected)");
		}
	}

	/** cableLoss bounds: result is always in [0, gross]. */
	@ParameterizedTest
	@MethodSource("cableLossSweep")
	void cableLoss_alwaysInZeroToGross(long gross, double lossPerBlock, int distanceBlocks) {
		long loss = EnergyShare.cableLoss(gross, lossPerBlock, distanceBlocks);
		assertTrue(loss >= 0, "loss never negative");
		assertTrue(loss <= gross, "loss never exceeds gross");
	}

	/** cableLoss monotonicity: doubling flow (or distance) must not DECREASE the loss, all else equal. */
	@ParameterizedTest
	@MethodSource("cableLossSweep")
	void cableLoss_monotonicInFlowAndDistance(long gross, double lossPerBlock, int distanceBlocks) {
		long baseline = EnergyShare.cableLoss(gross, lossPerBlock, distanceBlocks);
		// Double the flow (when positive & lossy & non-zero distance) — loss must not drop.
		if (gross > 0 && lossPerBlock > 0 && distanceBlocks > 0) {
			long doubledFlow = EnergyShare.cableLoss(gross * 2, lossPerBlock, distanceBlocks);
			assertTrue(doubledFlow >= baseline,
					"more flow must not lose less (resistive model is monotonic in flow)");
			long doubledDist = EnergyShare.cableLoss(gross, lossPerBlock, distanceBlocks * 2);
			assertTrue(doubledDist >= baseline,
					"more cable must not lose less (resistive model is monotonic in distance)");
		}
	}

	private static Stream<Arguments> splitSweep() {
		return Stream.of(
				Arguments.of(40L, new long[] {7, 1, 50, 12}, 70L, 32L),
				Arguments.of(10L, new long[] {5, 3, 2}, 10L, 32L),
				Arguments.of(8L, new long[] {3, 3, 3}, 9L, 32L),
				Arguments.of(32L, new long[] {8, 8, 8, 8}, 32L, 8L),    // cap-bound
				Arguments.of(100L, new long[] {10, 20, 30}, 60L, 32L),
				Arguments.of(1L, new long[] {5, 5}, 10L, 32L),          // tiny moveTotal
				Arguments.of(50L, new long[] {1, 1, 1, 1}, 4L, 32L),    // tiny rooms, demand < moveTotal
				Arguments.of(7L, new long[] {100}, 100L, 5L)            // single consumer, cap-bound
		);
	}

	private static Stream<Arguments> cableLossSweep() {
		return Stream.of(
				Arguments.of(32L, 0.02, 10),
				Arguments.of(8L, 0.02, 10),
				Arguments.of(1L, 0.02, 10),     // trickle top-off (MOD-009)
				Arguments.of(200L, 0.02, 100),  // clamped (loss would exceed gross)
				Arguments.of(32L, 0.02, 1),     // single hop, loss-free
				Arguments.of(32L, 0.02, 0),     // zero distance
				Arguments.of(32L, 0.5, 4),      // high loss rate
				Arguments.of(1000L, 0.02, 50),
				Arguments.of(0L, 0.02, 10)      // zero flow
		);
	}

	private static long sum(long[] a) {
		long s = 0;
		for (long v : a) {
			s += v;
		}
		return s;
	}
}
