package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * jqwik property-based coverage for {@link EnergyBuffer} (MOD-144). {@link EnergyBufferTest}'s 31
 * point tests already achieve 100% pitest kill on this class, so the goal here is <b>depth, not
 * coverage</b>: ~1000 randomised {@code (capacity, maxInsert, maxExtract, amount)} tuples per
 * property exercise invariants that point tests only spot-check. A future refactor that introduces a
 * subtle non-monotonicity or a clamp-swap on a rare input boundary (e.g.
 * {@code maxInsert == capacity - amount}) would slip past point tests but trip a property.
 *
 * <p>Three load-bearing invariants pinned:
 * <ul>
 *   <li><b>Range</b>: insert/extract results are always in {@code [0, min(cap, rate)]} / {@code [0, amount]}.</li>
 *   <li><b>Conservation</b>: post-insert amount never exceeds capacity; post-extract amount never negative.</li>
 *   <li><b>Monotonicity</b>: a larger request never moves less than a smaller one (no non-monotonic clamp).</li>
 * </ul>
 *
 * <p>The double-nested {@code Math.min} in {@link EnergyBuffer#insert}/{@link EnergyBuffer#extract}
 * ({@code Math.min(maxInsert, Math.min(maxAmount, capacity - amount))}) is the canonical mutation
 * target: an inner {@code min→max} swap on a rare input where the args coincide is invisible to
 * point tests but caught by a sweep that guarantees divergence.
 *
 * <p>jqwik API: {@link Provide @Provide} instance methods returning {@link Arbitrary}{@code <T>},
 * referenced by name from {@link ForAll @ForAll("name")}. Verified against jqwik 1.9 user guide.
 *
 * @see EnergyBufferTest for exhaustive point coverage (still the primary regression catcher)
 */
class EnergyBufferPropertyTest {

	/** A non-negative long in a sane EU-magnitude range (≤ 16M, well past HV tier 160k cap). */
	@Provide
	Arbitrary<Long> nonNegativeEu() {
		return Arbitraries.longs().between(0L, 16_000_000L);
	}

	/** A buffer instance with randomised limits; the {@code amount} is set independently below. */
	private static EnergyBuffer buffer(long capacity, long maxInsert, long maxExtract) {
		return new EnergyBuffer(capacity, maxInsert, maxExtract, () -> {
		});
	}

	/** Minimal in-memory txn handle — records enlistments so the participant contract is exercised. */
	private static final class FakeTxn implements EnergyPort.Txn {
		int enlistCount;

		@Override
		public void enlist(EnergyPort.Participant participant) {
			enlistCount++;
		}
	}

	// --- range invariants ---

	@Property
	void insert_resultAlwaysInZeroToMaxInsert(
			@ForAll("nonNegativeEu") long capacity,
			@ForAll("nonNegativeEu") long maxInsert,
			@ForAll("nonNegativeEu") long amount,
			@ForAll("nonNegativeEu") long maxAmount) {
		// Force amount ≤ capacity (in-contract: a well-formed buffer never holds more than capacity).
		long amountBounded = Math.min(amount, capacity);
		EnergyBuffer b = buffer(capacity, maxInsert, 0);
		b.amount = amountBounded;
		long moved = b.insert(maxAmount, new FakeTxn());
		assertTrue(moved >= 0, "insert never negative");
		assertTrue(moved <= maxInsert, "insert ≤ maxInsert (per-op rate cap)");
		assertTrue(moved <= maxAmount, "insert ≤ requested maxAmount");
		assertTrue(moved <= capacity - amountBounded, "insert ≤ remaining capacity");
	}

	@Property
	void extract_resultAlwaysInZeroToMaxExtract(
			@ForAll("nonNegativeEu") long capacity,
			@ForAll("nonNegativeEu") long maxExtract,
			@ForAll("nonNegativeEu") long amount,
			@ForAll("nonNegativeEu") long maxAmount) {
		long amountBounded = Math.min(amount, capacity);
		EnergyBuffer b = buffer(capacity, 0, maxExtract);
		b.amount = amountBounded;
		long moved = b.extract(maxAmount, new FakeTxn());
		assertTrue(moved >= 0, "extract never negative");
		assertTrue(moved <= maxExtract, "extract ≤ maxExtract (per-op rate cap)");
		assertTrue(moved <= maxAmount, "extract ≤ requested maxAmount");
		assertTrue(moved <= amountBounded, "extract ≤ stored amount");
	}

	// --- conservation invariants ---

	@Property
	void insert_neverOverfillsCapacity(
			@ForAll("nonNegativeEu") long capacity,
			@ForAll("nonNegativeEu") long maxInsert,
			@ForAll("nonNegativeEu") long amount,
			@ForAll("nonNegativeEu") long maxAmount) {
		long amountBounded = Math.min(amount, capacity);
		EnergyBuffer b = buffer(capacity, maxInsert, 0);
		b.amount = amountBounded;
		b.insert(maxAmount, new FakeTxn());
		assertTrue(b.amount <= capacity, "post-insert amount ≤ capacity (no overfill)");
		assertTrue(b.amount >= amountBounded, "insert never decreases the stored amount");
	}

	@Property
	void extract_neverDrivesNegative(
			@ForAll("nonNegativeEu") long capacity,
			@ForAll("nonNegativeEu") long maxExtract,
			@ForAll("nonNegativeEu") long amount,
			@ForAll("nonNegativeEu") long maxAmount) {
		long amountBounded = Math.min(amount, capacity);
		EnergyBuffer b = buffer(capacity, 0, maxExtract);
		b.amount = amountBounded;
		b.extract(maxAmount, new FakeTxn());
		assertTrue(b.amount >= 0, "post-extract amount ≥ 0 (no negative balance)");
		assertTrue(b.amount <= amountBounded, "extract never increases the stored amount");
	}

	// --- monotonicity invariants ---

	@Property
	void insert_monotonicInRequest(
			@ForAll("nonNegativeEu") long capacity,
			@ForAll("nonNegativeEu") long maxInsert,
			@ForAll("nonNegativeEu") long amount,
			@ForAll("nonNegativeEu") long maxAmountBig) {
		// A larger request never moves LESS than a smaller one. Catches a non-monotonic clamp mutant
		// that would e.g. cap on a wrong operand. Skip degenerate cases where smaller == larger.
		long amountBounded = Math.min(amount, capacity);
		long maxAmountSmall = maxAmountBig / 2;
		if (maxAmountSmall == maxAmountBig) return; // nothing to compare
		EnergyBuffer bSmall = buffer(capacity, maxInsert, 0);
		bSmall.amount = amountBounded;
		long small = bSmall.insert(maxAmountSmall, new FakeTxn());
		EnergyBuffer bBig = buffer(capacity, maxInsert, 0);
		bBig.amount = amountBounded;
		long big = bBig.insert(maxAmountBig, new FakeTxn());
		assertTrue(big >= small, "larger request must not move less (non-monotonic clamp suspected)");
	}

	@Property
	void extract_monotonicInRequest(
			@ForAll("nonNegativeEu") long capacity,
			@ForAll("nonNegativeEu") long maxExtract,
			@ForAll("nonNegativeEu") long amount,
			@ForAll("nonNegativeEu") long maxAmountBig) {
		long amountBounded = Math.min(amount, capacity);
		long maxAmountSmall = maxAmountBig / 2;
		if (maxAmountSmall == maxAmountBig) return;
		EnergyBuffer bSmall = buffer(capacity, 0, maxExtract);
		bSmall.amount = amountBounded;
		long small = bSmall.extract(maxAmountSmall, new FakeTxn());
		EnergyBuffer bBig = buffer(capacity, 0, maxExtract);
		bBig.amount = amountBounded;
		long big = bBig.extract(maxAmountBig, new FakeTxn());
		assertTrue(big >= small, "larger extract request must not move less");
	}

	// --- boundary pinning: the double-min swap divergence case ---

	/**
	 * The double-nested {@code Math.min(maxInsert, Math.min(maxAmount, capacity - amount))} is invisible
	 * to a point test on inputs where {@code maxInsert == maxAmount == capacity - amount} (the three
	 * operands coincide, so any min-swap yields the same number). jqwik rarely hits that exact triple by
	 * chance, so we pin it explicitly: with all three equal, the result is exactly that value.
	 */
	@Test
	void insert_allThreeOperandsEqual_returnsThatValue() {
		// capacity=100, amount=68 → room=32. maxInsert=32, maxAmount=32 → all three min-operands are 32.
		EnergyBuffer b = buffer(100, 32, 0);
		b.amount = 68;
		assertEquals(32L, b.insert(32, new FakeTxn()),
				"triple-coincidence case: all three min-operands equal 32 → result 32");
	}

	@Test
	void extract_allThreeOperandsEqual_returnsThatValue() {
		// amount=32, maxExtract=32, maxAmount=32 → all three min-operands are 32.
		EnergyBuffer b = buffer(100, 0, 32);
		b.amount = 32;
		assertEquals(32L, b.extract(32, new FakeTxn()),
				"triple-coincidence case: all three min-operands equal 32 → result 32");
	}
}
