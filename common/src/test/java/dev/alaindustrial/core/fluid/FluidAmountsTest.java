package dev.alaindustrial.core.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * L1 coverage for the mB↔droplet conversion in {@link FluidAmounts} (MOD-284). The conversion itself
 * is trivial arithmetic; what needs pinning is the REMAINDER contract, because that is what the
 * Fabric adapter relies on to avoid creating or destroying fluid when a foreign storage moves an
 * amount that is not a whole number of millibuckets.
 *
 * <p>Style follows {@link TankMathTest}: point tests per branch plus property sweeps, every
 * assertion carrying the invariant it defends.
 *
 * @implements fluid unit conversion (MOD-028 / MOD-284): exact scale-up, floor-down, sub-mB tail
 */
class FluidAmountsTest {

	// --- toDroplets: scaling up is exact, and overflow is loud rather than silent ---

	@Test
	void toDroplets_scalesByEightyOne() {
		assertEquals(81L, FluidAmounts.toDroplets(1), "1 mB is 81 droplets");
		assertEquals(81_000L, FluidAmounts.toDroplets(FluidAmounts.BUCKET),
				"a bucket is 81000 droplets — the Fabric FluidConstants.BUCKET value");
	}

	@Test
	void toDroplets_zeroStaysZero() {
		assertEquals(0L, FluidAmounts.toDroplets(0), "nothing scales to nothing");
	}

	/**
	 * {@code Long.MAX_VALUE} is a tempting shorthand for "give me everything", and a silent wrap would
	 * turn it into a negative request that a storage may interpret as an insert. Overflow must throw.
	 */
	@Test
	void toDroplets_overflowThrowsRatherThanWrapping() {
		assertThrows(ArithmeticException.class, () -> FluidAmounts.toDroplets(Long.MAX_VALUE),
				"an unbounded request must fail loudly, never wrap to a negative amount");
	}

	// --- toMbFloor / remainderDroplets: the split that keeps a transfer honest ---

	@Test
	void toMbFloor_exactMultipleHasNoTail() {
		assertEquals(2L, FluidAmounts.toMbFloor(162), "162 droplets is exactly 2 mB");
		assertEquals(0L, FluidAmounts.remainderDroplets(162), "an exact multiple leaves no tail");
	}

	@Test
	void toMbFloor_belowOneMillibucketFloorsToZero() {
		assertEquals(0L, FluidAmounts.toMbFloor(80), "80 droplets is less than one mB");
		assertEquals(80L, FluidAmounts.remainderDroplets(80),
				"the whole amount is tail — flooring it away would destroy those droplets");
	}

	/**
	 * The concrete case behind MOD-284: a foreign storage that moved 121 droplets. Reporting 1 mB and
	 * walking away leaves 40 droplets unaccounted for on the other side of the boundary.
	 */
	@Test
	void toMbFloor_partialMillibucketSplitsIntoWholeAndTail() {
		assertEquals(1L, FluidAmounts.toMbFloor(121), "121 droplets is 1 whole mB…");
		assertEquals(40L, FluidAmounts.remainderDroplets(121), "…plus a 40-droplet tail");
	}

	/**
	 * Fractions of a bucket that real mods use and that do NOT divide evenly by 81 — which is why the
	 * "our transactions are always bucket-multiples" assumption does not extend to foreign storages.
	 * A ninth of a bucket is 9000 droplets, a bottle 27000; neither is a whole number of millibuckets.
	 *
	 * <p>Note which fractions are absent: halves, quarters and tenths (40500, 20250, 8100 droplets) all
	 * land on exact millibuckets, so they were never at risk. The hazard is specifically thirds and
	 * ninths, because 1000 mB does not divide by 3.
	 */
	@ParameterizedTest
	@ValueSource(longs = {9_000L, 27_000L})
	void commonBucketFractions_doNotDivideEvenly(long droplets) {
		assertTrue(FluidAmounts.remainderDroplets(droplets) > 0,
				droplets + " droplets is a common bucket fraction that leaves a sub-mB tail");
	}

	/** The counterpart: fractions that ARE exact millibuckets must not be reported as risky. */
	@ParameterizedTest
	@ValueSource(longs = {40_500L, 20_250L, 8_100L})
	void halvesQuartersAndTenthsAreExactMillibuckets(long droplets) {
		assertEquals(0L, FluidAmounts.remainderDroplets(droplets),
				droplets + " droplets divides evenly into millibuckets — no tail to hand back");
	}

	// --- property sweeps ---

	/** The split is lossless by construction: floor×81 + tail must reconstruct the original amount. */
	@ParameterizedTest
	@MethodSource("dropletAmounts")
	void splitReconstructsTheOriginalAmount(long droplets) {
		long whole = FluidAmounts.toMbFloor(droplets);
		long tail = FluidAmounts.remainderDroplets(droplets);
		assertEquals(droplets, FluidAmounts.toDroplets(whole) + tail,
				"whole mB (as droplets) + tail == the original droplet amount");
	}

	/** The tail is always a proper remainder — never negative, never a whole mB in disguise. */
	@ParameterizedTest
	@MethodSource("dropletAmounts")
	void tailIsAlwaysBelowOneMillibucket(long droplets) {
		long tail = FluidAmounts.remainderDroplets(droplets);
		assertTrue(tail >= 0, "tail is never negative");
		assertTrue(tail < FluidAmounts.FABRIC_DROPLETS_PER_MB,
				"a tail of 81 or more would be a whole mB that should have been reported");
	}

	/** Round-tripping a mB amount through droplets is exact — mB is the coarser unit. */
	@ParameterizedTest
	@MethodSource("millibucketAmounts")
	void millibucketRoundTripIsLossless(long mb) {
		assertEquals(mb, FluidAmounts.toMbFloor(FluidAmounts.toDroplets(mb)),
				"mB -> droplets -> mB must return the original amount");
		assertEquals(0L, FluidAmounts.remainderDroplets(FluidAmounts.toDroplets(mb)),
				"a value that came from mB never has a tail");
	}

	private static Stream<Arguments> dropletAmounts() {
		return Stream.of(
				Arguments.of(0L),
				Arguments.of(1L),          // a single droplet: pure tail
				Arguments.of(80L),         // one droplet short of a mB
				Arguments.of(81L),         // exactly one mB
				Arguments.of(121L),        // the MOD-284 example
				Arguments.of(9_000L),      // a ninth of a bucket
				Arguments.of(27_000L),     // a bottle
				Arguments.of(81_000L),     // a whole bucket
				Arguments.of(81_001L));    // a bucket plus a droplet
	}

	private static Stream<Arguments> millibucketAmounts() {
		return Stream.of(
				Arguments.of(0L),
				Arguments.of(1L),
				Arguments.of(500L),
				Arguments.of(FluidAmounts.BUCKET),
				Arguments.of(10_000L));
	}
}
