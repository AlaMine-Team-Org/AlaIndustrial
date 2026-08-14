package dev.alaindustrial.core.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * L1 coverage for {@link FluidFlowMath} — the pure long-math kernel extracted from
 * {@link FluidNetwork} so the anti-slosh rule and the intake-room test can be unit-tested (and
 * pitest-mutated) without a live Minecraft runtime. {@link FluidNetwork} is typed on
 * {@code ServerLevel}/{@code BlockPos}/{@code Direction} throughout and sits in pitest's
 * {@code excludedClasses} (MOD-324, 81 permanent NO_COVERAGE mutations), so before this extraction
 * neither the {@code <= 1} threshold nor the halving was pinned by any test at any level.
 *
 * <p>Style follows {@code TankMathTest} / {@code FluidMoverMathTest}: point tests for each branch +
 * property-based sweeps, every assertion carrying a message that names the invariant a mutant would
 * break. The load-bearing case is the THRESHOLD, tested on the boundary itself — gaps of 0, 1, 2 and
 * 3, plus a negative gap. A gap of 1 is the deliberate off-by-one ({@code 1 / 2 == 0}, so the hop
 * would be idle); a gap of 3 proves the threshold is compared against the gap and not against its
 * half (half of 3 is already 1, a hop that moves something); a negative gap proves the same test
 * doubles as the uphill guard.
 *
 * <p>The sweeps deliberately never restate {@code gap / 2}: an assertion that recomputes the formula
 * under test is a tautology, so the invariants are phrased in terms of the segment amounts instead
 * ("the giver never ends up below the receiver", "an allowed hop always moves at least 1 mB").
 *
 * @implements fluid-line anti-slosh threshold + intake room (MOD-151 / MOD-333): half-the-gap hop,
 *           the {@code <= 1} boundary, the uphill guard, the full-segment skip
 */
class FluidFlowMathTest {

	// --- imbalance: fromAmount - toAmount ---

	/**
	 * The inputs are chosen, not incidental: on the textbook pair {@code (100, 0)} the MATH mutant
	 * ({@code from - to} → {@code from + to}) also returns 100 and survives. {@code (100, 40)} is a pair
	 * where the two formulas part company — the mutant answers 140, i.e. it would hand over half the SUM
	 * of the two segments and empty the giver far below the receiver.
	 */
	@Test
	void imbalance_isFromMinusTo() {
		assertEquals(60L, FluidFlowMath.imbalance(100, 40),
				"the gap is what the giver holds minus what the receiver holds "
						+ "(MATH mutant `from + to` would return 140 here)");
	}

	@Test
	void imbalance_negativeWhenNeighbourHoldsMore() {
		assertEquals(-60L, FluidFlowMath.imbalance(40, 100),
				"a fuller neighbour must yield a negative gap (the uphill case)");
	}

	@Test
	void imbalance_zeroOnEvenSegments() {
		assertEquals(0L, FluidFlowMath.imbalance(500, 500),
				"two evened-out segments have no gap (a PrimitiveReturns mutant is indistinguishable "
						+ "here, which is why the exact-value tests above exist)");
	}

	// NB: there is deliberately no separate `imbalance_mathMutantFlipsToSum_isCaught` here. It used to
	// exist and its assertion was byte-for-byte the one in `imbalance_isFromMinusTo` — same inputs
	// (100, 40), same expected 60, only the message differed. A second copy of an assertion kills no
	// mutant the first one leaves alive; it only adds 1 to the test count, which is exactly the kind of
	// number COVERAGE.md is supposed to be able to trust. Restating it with other inputs would not help
	// either: all three `imbalance` cases above already diverge from the `from + to` mutant, so the
	// mutant is killed three times over. What the deleted test really carried was the ARGUMENT for the
	// inputs, and that now lives in the javadoc of the test that makes the assertion.

	// --- tooSmallToHop: imbalance <= 1 (the boundary this class was extracted for) ---

	@Test
	void tooSmallToHop_trueAtZero() {
		assertTrue(FluidFlowMath.tooSmallToHop(0),
				"two evened-out segments must not hop (there is nothing to even out)");
	}

	/**
	 * THE off-by-one. At a gap of exactly 1 the halving yields 0, so the hop would move nothing —
	 * {@code FluidNetwork.moveFluid} returns on {@code amount <= 0} before it opens a transaction, so
	 * what the idle hop wastes is the call, not a transaction. The CONDITIONALS_BOUNDARY mutant
	 * ({@code <=}→{@code <}, i.e. the threshold lowered to {@code <= 0}) lets that idle hop through;
	 * this is the assertion that names it.
	 */
	@Test
	void tooSmallToHop_trueAtOne() {
		assertTrue(FluidFlowMath.tooSmallToHop(1),
				"a gap of 1 must be skipped — halving it yields 0, so the hop would be idle");
	}

	@Test
	void tooSmallToHop_falseAtTwo() {
		assertFalse(FluidFlowMath.tooSmallToHop(2),
				"a gap of 2 is the smallest hop that actually moves fluid (1 mB)");
	}

	/**
	 * The threshold is compared against the GAP, not against the hop it implies. At a gap of 3 the hop
	 * is already 1 — a threshold that had been (mis)written against the halved value would still allow
	 * this, so this case is what distinguishes the two readings from the gap-of-2 case alone.
	 */
	@Test
	void tooSmallToHop_falseAtThree() {
		assertFalse(FluidFlowMath.tooSmallToHop(3),
				"a gap of 3 must hop — the threshold is on the gap, not on half of it");
	}

	@Test
	void tooSmallToHop_trueForNegativeGap() {
		assertTrue(FluidFlowMath.tooSmallToHop(-1),
				"a segment must not push uphill into a fuller neighbour");
		assertTrue(FluidFlowMath.tooSmallToHop(-1000),
				"a large uphill gap is still uphill");
	}

	@Test
	void tooSmallToHop_falseForLargeGap() {
		assertFalse(FluidFlowMath.tooSmallToHop(1000),
				"a full segment beside an empty one must hop");
	}

	// --- hopAmount: imbalance / 2 (half the gap, rounded towards zero) ---

	@Test
	void hopAmount_halvesTheGap() {
		// Exact values, because they are what kills the MATH mutant (`/`→`*`): it would return 4, 8
		// and 2000 respectively — twice the gap instead of half of it.
		assertEquals(1L, FluidFlowMath.hopAmount(2), "half of 2 is 1");
		assertEquals(2L, FluidFlowMath.hopAmount(4), "half of 4 is 2");
		assertEquals(500L, FluidFlowMath.hopAmount(1000),
				"half of 1000 is 500 (MATH mutant `* 2` would return 2000)");
	}

	/**
	 * Rounding towards zero is load-bearing, not incidental: at a gap of 3 the hop must be 1, because 2
	 * would drop the giver to 0 below a receiver that ends up at 1 — the overshoot that starts the
	 * sloshing this rule exists to prevent.
	 */
	@Test
	void hopAmount_roundsTowardsZeroOnOddGap() {
		assertEquals(1L, FluidFlowMath.hopAmount(3),
				"half of 3 rounds down to 1 — rounding up would push the giver below the receiver");
		assertEquals(2L, FluidFlowMath.hopAmount(5), "half of 5 rounds down to 2");
	}

	@Test
	void hopAmount_isZeroAtTheSkippedGaps() {
		// Not a live path (tooSmallToHop rejects both), but it is the arithmetic REASON the threshold is
		// <= 1 rather than <= 0, so it is pinned rather than assumed.
		assertEquals(0L, FluidFlowMath.hopAmount(0), "no gap, no hop");
		assertEquals(0L, FluidFlowMath.hopAmount(1),
				"a gap of 1 halves to 0 — this is why the threshold skips it");
	}

	// --- room: capacity - amount ---

	@Test
	void room_isCapacityMinusAmount() {
		assertEquals(600L, FluidFlowMath.room(1000, 400),
				"free space is the capacity minus what is stored");
	}

	@Test
	void room_zeroOnFullSegment() {
		assertEquals(0L, FluidFlowMath.room(1000, 1000),
				"a full segment has no room");
	}

	@Test
	void room_isFullCapacityOnEmptySegment() {
		assertEquals(1000L, FluidFlowMath.room(1000, 0),
				"an empty segment can take its whole capacity");
	}

	/**
	 * Pins the MATH mutant ({@code capacity - amount}→{@code capacity + amount}) on inputs where the
	 * two formulas diverge — the same technique as
	 * {@code TankMathTest#toInsert_negativeAmountYieldsCapacityMinusAmount_notCapacityPlus}. A negative
	 * stored amount is out-of-contract for a live tank, but it is where subtraction and addition part
	 * company most visibly.
	 */
	@Test
	void room_negativeAmountDivergesFromMathMutant() {
		// Correct: 100 - (-60) = 160. MATH mutant: 100 + (-60) = 40.
		assertEquals(160L, FluidFlowMath.room(100, -60),
				"MATH mutant (capacity + amount) would return 40 here instead of 160");
	}

	// --- noRoomLeft: room <= 0 ---

	@Test
	void noRoomLeft_trueAtZero() {
		assertTrue(FluidFlowMath.noRoomLeft(0),
				"zero room means full — the segment must not ask the donor for anything");
	}

	@Test
	void noRoomLeft_trueOnNegativeRoom() {
		assertTrue(FluidFlowMath.noRoomLeft(-1),
				"negative room (out-of-contract, defensive) also counts as full");
	}

	@Test
	void noRoomLeft_falseAtOne() {
		// The CONDITIONALS_BOUNDARY mutant flips `<= 0` to `< 0` — it would call the intake with a
		// zero-sized request on a full segment. 1 mB of room must still read as "there is room".
		assertFalse(FluidFlowMath.noRoomLeft(1),
				"1 mB of room is still room — the segment may pull");
		assertFalse(FluidFlowMath.noRoomLeft(1000),
				"an empty segment has room");
	}

	// --- property-based sweeps: the rule stated without restating the formula -------------------

	/**
	 * The anti-slosh invariant itself: after an allowed hop the giver is still at or above the
	 * receiver, so the next tick has no reason to send the fluid straight back. Phrased over the
	 * segment amounts rather than over {@code gap / 2}, so it stays independent of the implementation
	 * it is checking. A {@code /}→{@code *} MATH mutant on the hop breaks it on every allowed pair.
	 */
	@ParameterizedTest
	@MethodSource("segmentPairSweep")
	void giverNeverFallsBelowReceiver(long fromAmount, long toAmount) {
		long gap = FluidFlowMath.imbalance(fromAmount, toAmount);
		if (FluidFlowMath.tooSmallToHop(gap)) {
			return;
		}
		long hop = FluidFlowMath.hopAmount(gap);
		assertTrue(fromAmount - hop >= toAmount + hop,
				"after the hop the giver must not end up below the receiver (that is the sloshing)");
	}

	/**
	 * The invariant that makes the {@code <= 1} threshold mean something: whenever a hop is allowed, it
	 * moves at least 1 mB. Lowering the threshold to {@code <= 0} admits a gap of 1, whose hop is 0 —
	 * so this sweep is what turns that boundary mutant from "equivalent" into "killed".
	 */
	@ParameterizedTest
	@MethodSource("segmentPairSweep")
	void allowedHopAlwaysMovesSomething(long fromAmount, long toAmount) {
		long gap = FluidFlowMath.imbalance(fromAmount, toAmount);
		if (FluidFlowMath.tooSmallToHop(gap)) {
			return;
		}
		assertTrue(FluidFlowMath.hopAmount(gap) >= 1,
				"an allowed hop must move at least 1 mB, never an idle 0");
	}

	/**
	 * Filling a segment by exactly its room brings it to capacity and never past it. The second
	 * assertion is what reaches {@link FluidFlowMath#noRoomLeft}'s boundary from the sweep: with the
	 * {@code <=}→{@code <} mutant a full segment (room 0) walks past the guard and lands here.
	 */
	@ParameterizedTest
	@MethodSource("intakeSweep")
	void roomFillsExactlyToCapacity(long capacity, long amount) {
		long room = FluidFlowMath.room(capacity, amount);
		if (FluidFlowMath.noRoomLeft(room)) {
			return;
		}
		assertEquals(capacity, amount + room,
				"amount + room == capacity (the intake asks for exactly what fits)");
		assertTrue(room > 0, "a segment that is not full has strictly positive room");
	}

	private static Stream<Arguments> segmentPairSweep() {
		// Pairs of (giver, receiver) segment contents, in mB.
		return Stream.of(
				Arguments.of(1000L, 0L),    // full beside empty — the largest hop
				Arguments.of(1000L, 999L),  // gap 1: the off-by-one, skipped
				Arguments.of(1000L, 998L),  // gap 2: the smallest live hop
				Arguments.of(1000L, 997L),  // gap 3: odd gap, rounds down
				Arguments.of(500L, 500L),   // evened out, skipped
				Arguments.of(0L, 1000L),    // uphill, skipped
				Arguments.of(1L, 0L),       // gap 1 at the bottom of the range
				Arguments.of(3L, 0L),       // gap 3 at the bottom of the range
				Arguments.of(0L, 0L),       // both empty
				Arguments.of(8000L, 17L)    // a lopsided pair well away from the boundaries
		);
	}

	private static Stream<Arguments> intakeSweep() {
		// Pairs of (segment capacity, stored amount), in mB.
		return Stream.of(
				Arguments.of(1000L, 0L),      // empty segment
				Arguments.of(1000L, 999L),    // 1 mB of room left
				Arguments.of(1000L, 1000L),   // full, skipped
				Arguments.of(1000L, 500L),    // half full
				Arguments.of(1L, 0L),         // minimal non-trivial segment
				Arguments.of(0L, 0L),         // zero-capacity segment, skipped
				Arguments.of(10_000L, 9999L)  // 1 mB of room in a large segment
		);
	}
}
