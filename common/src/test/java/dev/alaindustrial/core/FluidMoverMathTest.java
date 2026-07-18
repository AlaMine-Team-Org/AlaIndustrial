package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * L1 coverage for {@link FluidMoverMath} — the pure refund arithmetic extracted from
 * {@link FluidMover#move} so the partial-acceptance refund path can be unit-tested (and pitest-mutated)
 * without a live Minecraft runtime. {@link FluidMover}'s API takes {@link FluidPort} + {@link FluidHolder}
 * (both coupled to {@code net.minecraft.Fluid}, absent from {@code :common}'s L1 classpath), so the
 * inline refund math was a 12-mutant NO_COVERAGE hole in the MOD-110 pitest baseline. {@link FluidMoverMath}
 * drops the MC coupling and keeps only the deterministic arithmetic; this suite pins every line.
 *
 * <p>The load-bearing invariant (see {@link FluidMover} class doc): a partial-acceptance move must never
 * destroy or duplicate fluid. The shortfall ({@code extracted - inserted}) and the moved-with-refund
 * return ({@code inserted + refunded}) are the two MATH-mutant-sensitive spots — a {@code -}→{@code +} on
 * shortfall refunds the SUM (duplicating fluid), a {@code +}→{@code -} on the return under-reports by
 * 2×refund. Property sweeps pin conservation across many (extracted, inserted) tuples.
 *
 * @implements fluid-mover refund arithmetic (MOD-028 / MOD-113)
 */
class FluidMoverMathTest {

	// --- nothingExtracted: extracted <= 0 ---

	@Test
	void nothingExtracted_trueAtZero() {
		// The boundary mutant flips `<=` to `<` — would treat extracted==0 as "something extracted" and
		// fall through to a pointless insert(0) call. 0 must read as "nothing".
		assertTrue(FluidMoverMath.nothingExtracted(0),
				"extracted == 0 is the empty-source / probe case — nothing moved");
	}

	@Test
	void nothingExtracted_trueAtNegative() {
		assertTrue(FluidMoverMath.nothingExtracted(-1),
				"a negative extract (out-of-contract, defensive) is treated as nothing");
	}

	@Test
	void nothingExtracted_falseAtPositive() {
		assertFalse(FluidMoverMath.nothingExtracted(1),
				"1 mB extracted -> NOT nothing (proceed to insert)");
		assertFalse(FluidMoverMath.nothingExtracted(1000),
				"1000 mB extracted -> NOT nothing");
	}

	// --- shortfallNeeded: inserted < extracted ---

	@Test
	void shortfallNeeded_trueOnPartialAcceptance() {
		assertTrue(FluidMoverMath.shortfallNeeded(3, 10),
				"target accepted 3 of 10 -> refund the other 7");
	}

	@Test
	void shortfallNeeded_falseOnFullAcceptance() {
		// The boundary mutant flips `<` to `<=` — would trigger a refund even when inserted == extracted
		// (full acceptance), computing shortfall = 0 and calling from.insert(0) needlessly. Harmless to
		// the result but a behaviour divergence pitest catches.
		assertFalse(FluidMoverMath.shortfallNeeded(10, 10),
				"full acceptance (inserted == extracted) -> no refund needed");
	}

	@Test
	void shortfallNeeded_falseWhenTargetAcceptsMoreThanExtracted() {
		// Cannot happen on a well-formed port (insert clamps to the requested amount), but the helper
		// must not misfire: inserted > extracted is NOT a shortfall.
		assertFalse(FluidMoverMath.shortfallNeeded(15, 10),
				"inserted > extracted (impossible in practice) -> not a shortfall");
	}

	// --- shortfall: extracted - inserted ---

	@Test
	void shortfall_isExtractedMinusInserted() {
		assertEquals(7L, FluidMoverMath.shortfall(10, 3),
				"shortfall = extracted - inserted (the amount to refund)");
	}

	@Test
	void shortfall_zeroOnFullAcceptance() {
		assertEquals(0L, FluidMoverMath.shortfall(10, 10),
				"full acceptance -> zero shortfall");
	}

	/**
	 * The MATH mutant flips {@code extracted - inserted} to {@code extracted + inserted}. On a
	 * partial-acceptance move (extracted=10, inserted=3) the mutant would refund 13 mB — duplicating
	 * the moved fluid and destroying conservation. This exact-value assertion kills it.
	 */
	@Test
	void shortfall_mathMutantFlipsToSum_isCaught() {
		// Correct: 10 - 3 = 7. MATH mutant: 10 + 3 = 13.
		assertEquals(7L, FluidMoverMath.shortfall(10, 3),
				"MATH mutant (extracted + inserted) would return 13, duplicating fluid");
	}

	// --- movedWithRefund: inserted + refunded ---

	@Test
	void movedWithRefund_isInsertedPlusRefunded() {
		assertEquals(10L, FluidMoverMath.movedWithRefund(3, 7),
				"movedWithRefund = inserted + refunded (total that ended up moving)");
	}

	@Test
	void movedWithRefund_zeroRefundReturnsInserted() {
		assertEquals(10L, FluidMoverMath.movedWithRefund(10, 0),
				"zero refund -> return the direct insert");
	}

	/**
	 * The MATH mutant flips {@code inserted + refunded} to {@code inserted - refunded}. On a
	 * partial-acceptance move (inserted=3, refunded=7) the mutant would return 3 - 7 = -4 — under-reporting
	 * the moved amount by 2×refund AND going negative. This exact-value assertion kills it.
	 */
	@Test
	void movedWithRefund_mathMutantFlipsToSubtract_isCaught() {
		// Correct: 3 + 7 = 10. MATH mutant: 3 - 7 = -4.
		assertEquals(10L, FluidMoverMath.movedWithRefund(3, 7),
				"MATH mutant (inserted - refunded) would return -4, under-reporting by 2x");
	}

	/**
	 * The PrimitiveReturns mutant zeroes the return on every partial-acceptance move, making the move
	 * look like nothing transferred even though {@code inserted} mB did land in the target. Pin the
	 * non-zero return on a typical partial-acceptance case.
	 */
	@Test
	void movedWithRefund_primitiveReturnZeroMutant_isCaught() {
		assertEquals(10L, FluidMoverMath.movedWithRefund(3, 7),
				"PrimitiveReturns mutant would return 0 — masking a real partial-acceptance move");
	}

	// --- property-based sweeps: conservation across partial-acceptance scenarios ---

	/**
	 * Conservation: the moved-with-refund total equals {@code inserted + refunded} (by definition), and
	 * never exceeds the originally-extracted amount (you can't move more than you pulled). A MATH mutant
	 * on either the shortfall or the moved-with-refund line breaks this on partial-acceptance inputs.
	 */
	@ParameterizedTest
	@MethodSource("partialAcceptanceSweep")
	void refundConservation_insertedPlusRefundedNeverExceedsExtracted(long extracted, long inserted) {
		// refunded is clamped: at most the shortfall, which is extracted - inserted.
		long shortfall = FluidMoverMath.shortfall(extracted, inserted);
		long refunded = Math.min(shortfall, shortfall); // refund target always has room (extract freed it)
		long moved = FluidMoverMath.movedWithRefund(inserted, refunded);
		assertEquals(extracted, moved,
				"inserted + refunded == extracted (full conservation, no loss/gain)");
	}

	/** shortfall is always in [0, extracted]: never negative, never more than what was pulled. */
	@ParameterizedTest
	@MethodSource("partialAcceptanceSweep")
	void shortfall_alwaysInZeroToExtracted(long extracted, long inserted) {
		long shortfall = FluidMoverMath.shortfall(extracted, inserted);
		assertTrue(shortfall >= 0, "shortfall never negative");
		assertTrue(shortfall <= extracted, "shortfall never exceeds extracted");
	}

	/** movedWithRefund is always >= inserted (refunded is non-negative): refund never reduces the move. */
	@ParameterizedTest
	@MethodSource("partialAcceptanceSweep")
	void movedWithRefund_neverBelowInserted(long extracted, long inserted) {
		long shortfall = FluidMoverMath.shortfall(extracted, inserted);
		long moved = FluidMoverMath.movedWithRefund(inserted, shortfall);
		assertTrue(moved >= inserted, "movedWithRefund >= inserted (refund adds, never subtracts)");
	}

	private static Stream<Arguments> partialAcceptanceSweep() {
		// Spread covering: full acceptance, tiny partial, large partial, minimal 1-mB cases.
		return Stream.of(
				Arguments.of(1000L, 1000L),  // full acceptance
				Arguments.of(1000L, 999L),   // 1 mB short
				Arguments.of(1000L, 1L),     // tiny acceptance, huge refund
				Arguments.of(1000L, 500L),   // half
				Arguments.of(10L, 3L),       // small move, classic MOD-028 case
				Arguments.of(1L, 0L),        // extract 1, target refuses -> full refund
				Arguments.of(1L, 1L),        // minimal full acceptance
				Arguments.of(10_000L, 9999L) // large move, 1 mB short
		);
	}
}
