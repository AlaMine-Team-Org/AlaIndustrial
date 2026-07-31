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

	// --- property-based sweeps: conservation across partial-acceptance scenarios ---

	/**
	 * Conservation of the split: what the target kept plus what goes back to the source is exactly what
	 * was pulled, so the refund path neither destroys nor creates fluid. A MATH mutant on the shortfall
	 * line breaks this on every partial-acceptance input.
	 *
	 * <p>MOD-283: this used to be phrased as {@code movedWithRefund(inserted, refunded) == extracted}
	 * and called the result "moved", which is what encoded the gross-outflow confusion in the first
	 * place — the refunded part goes back to the SOURCE and has moved nowhere. It also computed the
	 * refund as {@code Math.min(shortfall, shortfall)}, a self-min that asserted nothing about the
	 * intended clamp.
	 */
	@ParameterizedTest
	@MethodSource("partialAcceptanceSweep")
	void refundConservation_targetKeepPlusSourceRefundEqualsExtracted(long extracted, long inserted) {
		long shortfall = FluidMoverMath.shortfall(extracted, inserted);
		assertEquals(extracted, inserted + shortfall,
				"inserted (stays in target) + shortfall (returns to source) == extracted");
	}

	/** shortfall is always in [0, extracted]: never negative, never more than what was pulled. */
	@ParameterizedTest
	@MethodSource("partialAcceptanceSweep")
	void shortfall_alwaysInZeroToExtracted(long extracted, long inserted) {
		long shortfall = FluidMoverMath.shortfall(extracted, inserted);
		assertTrue(shortfall >= 0, "shortfall never negative");
		assertTrue(shortfall <= extracted, "shortfall never exceeds extracted");
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
