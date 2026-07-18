package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * L1 coverage for {@link TankMath} — the pure long-math kernel extracted from {@link FluidTank} so
 * the transfer arithmetic can be unit-tested (and pitest-mutated) without a live Minecraft runtime.
 * {@link FluidTank}'s public API takes a {@link FluidHolder} (wrapping {@code net.minecraft.Fluid}),
 * which is absent from {@code :common}'s L1 classpath, so the inline math was a 42-mutant
 * NO_COVERAGE hole in the MOD-110 pitest baseline. {@link TankMath} drops the MC coupling and keeps
 * only the deterministic arithmetic; this suite pins every line of it.
 *
 * <p>Style follows {@link EnergyBufferTest}: point tests for each branch + property-based sweeps
 * (conservation, monotonicity) over a spread of inputs. Every assertion carries a descriptive
 * message — pitest mutators (MATH flips {@code +}↔{@code -}, CONDITIONALS_BOUNDARY flips {@code <}↔
 * {@code <=}, RETURNS zeroes a return) break one specific invariant, and the message names which.
 *
 * @implements fluid-tank transfer math (MOD-028 / MOD-113): capacity guard, insert/extract clamps,
 *           fluid-clear-at-zero invariant, capability flag
 */
class TankMathTest {

	// --- checkCapacity: capacity < 0 is invalid; 0 is legal (sealed/degenerate tank) ---

	@Test
	void checkCapacity_acceptsZero() {
		// The boundary mutant ConditionalsBoundary flips `<` to `<=` — would reject the legal 0-capacity
		// tank (a sealed tank that holds nothing). 0 must round-trip cleanly.
		assertEquals(0L, TankMath.checkCapacity(0), "zero capacity is legal (sealed/degenerate tank)");
	}

	@Test
	void checkCapacity_acceptsPositive() {
		assertEquals(1000L, TankMath.checkCapacity(1000));
		assertEquals(10_000L, TankMath.checkCapacity(10_000));
	}

	@Test
	void checkCapacity_rejectsNegative() {
		assertThrows(IllegalArgumentException.class, () -> TankMath.checkCapacity(-1),
				"negative capacity is invalid");
		assertThrows(IllegalArgumentException.class, () -> TankMath.checkCapacity(-10_000),
				"large negative capacity is invalid");
	}

	@Test
	void checkCapacity_rejectsExactlyBelowZero_notBelowZeroBoundary() {
		// Pins the boundary precisely: -1 throws, 0 does not. A NegateConditionals mutant (flips the
		// guard to `>= 0`) would throw on 0 instead of returning it.
		assertThrows(IllegalArgumentException.class, () -> TankMath.checkCapacity(-1));
		assertEquals(0L, TankMath.checkCapacity(0));
	}

	// --- toInsert: min(maxAmount, capacity - amount) ---

	@Test
	void toInsert_cappedByRemainingCapacity() {
		// room = 100 - 95 = 5; maxAmount 1000 -> Math.min(1000, 5) = 5.
		assertEquals(5L, TankMath.toInsert(95, 100, 1000),
				"insert must not overfill past capacity (the capacity - amount kernel)");
	}

	@Test
	void toInsert_cappedByRequestedAmount() {
		// room = 1000 - 0 = 1000; maxAmount 7 -> Math.min(7, 1000) = 7.
		assertEquals(7L, TankMath.toInsert(0, 1000, 7),
				"insert must not exceed the requested maxAmount");
	}

	@Test
	void toInsert_intoFullTankMovesNothing() {
		// room = 100 - 100 = 0; any maxAmount -> Math.min(maxAmount, 0) = 0.
		assertEquals(0L, TankMath.toInsert(100, 100, 1000),
				"a full tank (amount == capacity) accepts nothing");
		assertEquals(0L, TankMath.toInsert(100, 100, 0),
				"a full tank accepts nothing even on a zero-amount probe");
	}

	@Test
	void toInsert_emptyTankAcceptsUpToCapacity() {
		// room = capacity - 0 = capacity; capped by maxAmount only when maxAmount < capacity.
		assertEquals(100L, TankMath.toInsert(0, 100, 100), "empty tank: maxAmount == capacity");
		assertEquals(50L, TankMath.toInsert(0, 100, 50), "empty tank: maxAmount < capacity");
	}

	/**
	 * The MOD-009-style last-mB top-off at the tank level: a tank 1 mB short of full accepts exactly
	 * the last mB. Pins the {@code capacity - amount} subtraction against a MATH {@code -}→{@code +}
	 * mutant (which would compute {@code capacity + amount} and over-report room wildly).
	 */
	@Test
	void toInsert_lastMillibucketReachesExactCapacity() {
		assertEquals(1L, TankMath.toInsert(9999, 10_000, 1000),
				"the 1 mB top-off must fit (capacity - amount = 1, min(1000, 1) = 1)");
	}

	/**
	 * Pins the MATH mutant that flips {@code capacity - amount} to {@code capacity + amount}: with a
	 * negative-amount input the mutant would yield {@code capacity + amount} (a smaller number) instead
	 * of the correct {@code capacity - amount} (a larger number). Negative {@code amount} is
	 * out-of-contract for the live tank, but pinning it keeps the helper honest.
	 */
	@Test
	void toInsert_negativeAmountYieldsCapacityMinusAmount_notCapacityPlus() {
		// Correct: min(50, 100 - (-5)) = min(50, 105) = 50. MATH mutant: min(50, 100 + (-5)) = min(50,95)=50.
		// Those collide here — pick inputs where they diverge: amount=-60, capacity=100, maxAmount=200.
		// Correct: min(200, 100-(-60))=min(200,160)=160. Mutant: min(200,100+(-60))=min(200,40)=40.
		assertEquals(160L, TankMath.toInsert(-60, 100, 200),
				"MATH mutant (capacity+amount) would return 40 here instead of 160");
	}

	// --- toExtract: min(maxAmount, amount) ---

	@Test
	void toExtract_cappedByStoredAmount() {
		assertEquals(9L, TankMath.toExtract(9, 1000),
				"cannot extract more than is stored (the min(maxAmount, amount) clamp)");
	}

	@Test
	void toExtract_cappedByRequestedAmount() {
		assertEquals(32L, TankMath.toExtract(500, 32),
				"extract must not exceed the requested maxAmount");
	}

	@Test
	void toExtract_fromEmptyTankMovesNothing() {
		assertEquals(0L, TankMath.toExtract(0, 1000), "an empty tank yields nothing");
	}

	/**
	 * The MOD-088 rollback-fluid-identity boundary: extracting the full stored amount drives
	 * {@code amount} to 0. {@link TankMath#toExtract} must return the exact stored amount (not 0), so
	 * the caller can tell "I drained everything" from "the tank was empty". A PrimitiveReturns mutant
	 * zeroing the return would conflate the two.
	 */
	@Test
	void toExtract_drainingEverythingReturnsStored_notZero() {
		assertEquals(500L, TankMath.toExtract(500, 1000),
				"draining the full tank returns the stored amount (not a zero-return mutant)");
	}

	// --- shouldClearFluid: amount == 0 ---

	@Test
	void shouldClearFluid_trueAtZero() {
		assertTrue(TankMath.shouldClearFluid(0),
				"amount == 0 must trigger the fluid-clear invariant (commit/rollback terminal)");
	}

	@Test
	void shouldClearFluid_falseAtPositiveAmount() {
		assertFalse(TankMath.shouldClearFluid(1), "1 mB left -> fluid identity retained");
		assertFalse(TankMath.shouldClearFluid(10_000), "full tank -> fluid identity retained");
	}

	/**
	 * The MOD-088 critical case: a rollback to a positive amount must NOT clear the fluid. A
	 * NegateConditionals mutant flipping {@code ==} to {@code !=} would clear the fluid on every
	 * positive amount, breaking the cross-mod capability contract (tank reads amount>0, fluid==EMPTY).
	 * This is the test that catches it.
	 */
	@Test
	void shouldClearFluid_negativeAmountAlsoFalse_fluidClearedOnlyAtZero() {
		assertFalse(TankMath.shouldClearFluid(-1),
				"a negative amount (transient, out-of-contract) must not clear fluid either");
	}

	// --- supportsOp: capacity > 0 (a zero-capacity tank is sealed) ---

	@Test
	void supportsOp_trueForPositiveCapacity() {
		assertTrue(TankMath.supportsOp(1), "1 mB capacity tank is operational");
		assertTrue(TankMath.supportsOp(10_000), "10-bucket capacity tank is operational");
	}

	@Test
	void supportsOp_falseForZeroCapacity() {
		// The CONDITIONALS_BOUNDARY mutant flips `> 0` to `>= 0` — would report a zero-capacity tank
		// as operational. 0 must read as NOT operational.
		assertFalse(TankMath.supportsOp(0), "zero-capacity tank is sealed (not operational)");
	}

	@Test
	void supportsOp_falseForNegativeCapacity() {
		assertFalse(TankMath.supportsOp(-1),
				"negative capacity (transient) is not operational");
	}

	// --- property-based sweeps: structural invariants across many (capacity, amount, maxAmount) ---

	/**
	 * Conservation: toInsert never returns more than the available room, the requested maxAmount, or
	 * drives the implied post-insert amount above capacity. A MATH mutant ({@code -}→{@code +} on
	 * {@code capacity - amount}, or {@code min}→{@code max} via an internal swap in a future rewrite)
	 * breaks this across the sweep.
	 */
	@ParameterizedTest
	@MethodSource("transferSweep")
	void toInsert_neverExceedsRoomOrRequest(long amount, long capacity, long maxAmount) {
		long room = capacity - amount;
		long result = TankMath.toInsert(amount, capacity, maxAmount);
		assertTrue(result <= maxAmount, "toInsert <= maxAmount");
		assertTrue(result <= room, "toInsert <= capacity - amount (room)");
		assertTrue(result <= capacity, "toInsert <= capacity");
	}

	/** Monotonicity in maxAmount: a larger request never moves LESS than a smaller one. */
	@ParameterizedTest
	@MethodSource("transferSweep")
	void toInsert_isMonotonicInRequest(long amount, long capacity, long maxAmount) {
		long smaller = TankMath.toInsert(amount, capacity, Math.max(0, maxAmount / 2));
		long larger = TankMath.toInsert(amount, capacity, maxAmount);
		assertTrue(larger >= smaller,
				"a larger maxAmount must not move less (no non-monotonic clamp)");
	}

	/**
	 * Conservation: toInsert + amount never exceeds capacity, AND toInsert is exactly the delta that
	 * moves (no hidden loss/creation). This pins the {@code +} in the caller's {@code amount += toInsert}
	 * against a MATH flip there, by pinning the value the caller adds.
	 */
	@ParameterizedTest
	@MethodSource("transferSweep")
	void toInsert_plusAmount_staysWithinCapacity(long amount, long capacity, long maxAmount) {
		long moved = TankMath.toInsert(amount, capacity, maxAmount);
		assertTrue(amount + moved <= capacity, "amount + moved <= capacity (no overfill)");
		assertTrue(amount + moved >= amount, "insert never decreases the stored amount");
	}

	/**
	 * Conservation: toExtract never returns more than is stored or than requested. Drives the
	 * post-extract amount non-negative.
	 */
	@ParameterizedTest
	@MethodSource("transferSweep")
	void toExtract_neverExceedsStoredOrRequest(long amount, long capacity, long maxAmount) {
		long result = TankMath.toExtract(amount, maxAmount);
		assertTrue(result <= amount, "toExtract <= stored amount");
		assertTrue(result <= maxAmount, "toExtract <= maxAmount");
		assertTrue(amount - result >= 0, "amount - moved >= 0 (extract never drives the tank negative)");
	}

	/** Idempotency at the boundary: extracting the full stored amount twice — second extract is 0. */
	@ParameterizedTest
	@MethodSource("transferSweep")
	void toExtract_drainingTwiceSecondMoveIsZero(long amount, long capacity, long maxAmount) {
		long first = TankMath.toExtract(amount, maxAmount);
		long remaining = amount - first;
		long second = TankMath.toExtract(remaining, maxAmount);
		if (remaining == 0) {
			assertEquals(0L, second, "once drained, further extracts move nothing");
		}
		assertTrue(remaining - second >= 0, "buffer never negative after repeated extracts");
	}

	private static Stream<Arguments> transferSweep() {
		// A spread covering: empty/full/near-full tanks, zero & generous requests, and edge values.
		return Stream.of(
				Arguments.of(0L, 1000L, 50L),       // empty, room plenty
				Arguments.of(995L, 1000L, 50L),     // near-full, little room
				Arguments.of(1000L, 1000L, 50L),    // full, no room
				Arguments.of(50L, 1000L, 2000L),    // request > room
				Arguments.of(0L, 1000L, 1000L),     // empty, exact-fill request
				Arguments.of(9999L, 10_000L, 1000L),// the MOD-009-style last-mB top-off boundary
				Arguments.of(0L, 1L, 1L),           // minimal non-trivial tank
				Arguments.of(500L, 1000L, 0L),      // zero request (probe)
				Arguments.of(9L, 1000L, 1000L),     // less than one "rate-unit" stored
				Arguments.of(500L, 1000L, 100L)     // request < stored
		);
	}
}
