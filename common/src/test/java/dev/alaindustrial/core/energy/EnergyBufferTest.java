package dev.alaindustrial.core.energy;

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
 * L1 coverage for {@link EnergyBuffer} — the platform-neutral buffer math that MOD-022 Phase 2 extracted
 * from Team Reborn's {@code SimpleEnergyStorage} (Fabric) and NeoForge's {@code SimpleEnergyHandler}.
 * Both loaders drive this same common class, so these loader-free tests pin the transfer semantics on
 * <em>both</em> at once (the adapters are thin pass-throughs). Guards the migration against a regression
 * that would silently diverge Fabric and NeoForge behaviour.
 *
 * @implements energy-buffer insert/extract math, capacity/rate caps, transaction snapshot/rollback (MOD-009 top-off)
 */
class EnergyBufferTest {

	/** Minimal in-memory transaction handle — records enlistments so the participant contract is testable. */
	private static final class FakeTxn implements EnergyPort.Txn {
		int enlistCount;

		@Override
		public void enlist(EnergyPort.Participant participant) {
			enlistCount++;
		}
	}

	private static EnergyBuffer buffer(long capacity, long maxInsert, long maxExtract) {
		return new EnergyBuffer(capacity, maxInsert, maxExtract, () -> {
		});
	}

	// --- insert math: inserted = min(maxInsert, min(maxAmount, capacity - amount)) ---

	@Test
	void insert_cappedByMaxInsertRate() {
		EnergyBuffer b = buffer(1000, 32, 32);
		assertEquals(32, b.insert(1000, new FakeTxn()), "insert must not exceed the per-op maxInsert");
		assertEquals(32, b.getAmount());
	}

	@Test
	void insert_cappedByRemainingCapacity() {
		EnergyBuffer b = buffer(100, 1000, 1000);
		b.setAmountUntracked(95);
		assertEquals(5, b.insert(1000, new FakeTxn()), "insert must not overfill past capacity");
		assertEquals(100, b.getAmount());
	}

	@Test
	void insert_cappedByRequestedAmount() {
		EnergyBuffer b = buffer(1000, 1000, 1000);
		assertEquals(7, b.insert(7, new FakeTxn()), "insert must not exceed the requested amount");
	}

	@Test
	void insert_intoFullBufferMovesNothing() {
		EnergyBuffer b = buffer(100, 32, 32);
		b.setAmountUntracked(100);
		FakeTxn tx = new FakeTxn();
		assertEquals(0, b.insert(32, tx));
		assertEquals(0, tx.enlistCount, "a zero move must not enlist (no snapshot churn)");
	}

	@Test
	void insert_generatorBufferRejects() {
		EnergyBuffer gen = buffer(4000, 0, 32); // maxInsert == 0: a producer
		assertEquals(0, gen.insert(32, new FakeTxn()), "a maxInsert==0 buffer accepts nothing");
		assertFalse(gen.supportsInsertion());
	}

	/**
	 * The MOD-009 top-off invariant at the buffer level: a buffer one EU short of full accepts exactly the
	 * last EU and reaches its precise capacity (no off-by-one, no stranded remainder).
	 */
	@Test
	void insert_lastEuReachesExactCapacity() {
		EnergyBuffer b = buffer(20_000, 32, 32);
		b.setAmountUntracked(19_999);
		assertEquals(1, b.insert(32, new FakeTxn()), "the 1 EU top-off must move");
		assertEquals(20_000, b.getAmount(), "buffer must reach exact capacity");
	}

	// --- extract math: extracted = min(maxExtract, min(maxAmount, amount)) ---

	@Test
	void extract_cappedByMaxExtractRate() {
		EnergyBuffer b = buffer(1000, 32, 32);
		b.setAmountUntracked(500);
		assertEquals(32, b.extract(1000, new FakeTxn()));
		assertEquals(468, b.getAmount());
	}

	@Test
	void extract_cappedByStoredAmount() {
		EnergyBuffer b = buffer(1000, 1000, 1000);
		b.setAmountUntracked(9);
		assertEquals(9, b.extract(1000, new FakeTxn()), "cannot extract more than is stored");
		assertEquals(0, b.getAmount());
	}

	@Test
	void extract_fromEmptyMovesNothing() {
		EnergyBuffer b = buffer(1000, 32, 32);
		FakeTxn tx = new FakeTxn();
		assertEquals(0, b.extract(32, tx));
		assertEquals(0, tx.enlistCount);
	}

	@Test
	void extract_consumerBufferEmitsNothing() {
		EnergyBuffer machine = buffer(800, 32, 0); // maxExtract == 0: a pure consumer
		machine.setAmountUntracked(800);
		assertEquals(0, machine.extract(32, new FakeTxn()));
		assertFalse(machine.supportsExtraction());
	}

	// --- transaction participant contract ---

	@Test
	void nonZeroMoveEnlistsBeforeMutating() {
		EnergyBuffer b = buffer(1000, 32, 32);
		FakeTxn tx = new FakeTxn();
		b.insert(10, tx);
		assertEquals(1, tx.enlistCount, "a non-zero insert must enlist for snapshot/rollback");
		b.setAmountUntracked(500);
		b.extract(10, tx);
		assertEquals(2, tx.enlistCount, "a non-zero extract must enlist too");
	}

	@Test
	void snapshotRoundTripsAmount() {
		EnergyBuffer b = buffer(1000, 32, 32);
		b.setAmountUntracked(321);
		long snap = b.createSnapshot();
		b.setAmountUntracked(0);
		b.readSnapshot(snap);
		assertEquals(321, b.getAmount(), "readSnapshot must restore the captured amount (rollback path)");
	}

	// --- construction + capability flags ---

	@Test
	void rejectsNegativeLimits() {
		assertThrows(IllegalArgumentException.class, () -> buffer(-1, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> buffer(100, -1, 0));
		assertThrows(IllegalArgumentException.class, () -> buffer(100, 0, -1));
	}

	@Test
	void rejectsNegativeRequest() {
		EnergyBuffer b = buffer(100, 32, 32);
		assertThrows(IllegalArgumentException.class, () -> b.insert(-1, new FakeTxn()));
		assertThrows(IllegalArgumentException.class, () -> b.extract(-1, new FakeTxn()));
	}

	@Test
	void capabilityFlagsFollowRates() {
		assertTrue(buffer(100, 32, 32).supportsInsertion());
		assertTrue(buffer(100, 32, 32).supportsExtraction());
		assertFalse(buffer(100, 0, 32).supportsInsertion());
		assertFalse(buffer(100, 32, 0).supportsExtraction());
	}

	// --- pitest-driven additions: kill the surviving boundary mutants ---
	// The constructor guard (L48) and the insert/extract maxAmount guards (L59, L73) all use strict {@code < 0}.
	// A boundary mutation flipping {@code <} to {@code <=} would reject the legitimate zero value, breaking
	// the documented contract ({@code maxInsert == 0} is the canonical "producer" buffer; {@code maxAmount == 0}
	// is a no-op probe). These pin zero as a valid, accepted input so the flip is caught.

	@Test
	void constructor_acceptsAllZeroLimits() {
		// 0 limits are legal — a 0-capacity / 0-rate buffer is the "producer" or "consumer" degenerate case.
		EnergyBuffer zero = buffer(0, 0, 0);
		assertEquals(0, zero.capacity);
		assertEquals(0, zero.maxInsert);
		assertEquals(0, zero.maxExtract);
	}

	@Test
	void insert_acceptsZeroMaxAmountAsNoOp() {
		EnergyBuffer b = buffer(1000, 32, 32);
		FakeTxn tx = new FakeTxn();
		assertEquals(0, b.insert(0, tx), "maxAmount=0 is a probe — must return 0 without throwing");
		assertEquals(0, tx.enlistCount, "a zero probe must not enlist");
		assertEquals(0, b.getAmount());
	}

	@Test
	void extract_acceptsZeroMaxAmountAsNoOp() {
		EnergyBuffer b = buffer(1000, 32, 32);
		b.setAmountUntracked(50);
		FakeTxn tx = new FakeTxn();
		assertEquals(0, b.extract(0, tx), "maxAmount=0 is a probe — must return 0 without throwing");
		assertEquals(0, tx.enlistCount);
		assertEquals(50, b.getAmount());
	}

	// --- read-only projections: getAmount/getCapacity return the live fields, never a hardcoded 0 ---

	@Test
	void getAmountAndCapacity_reportLiveFields_notZeroDefaults() {
		EnergyBuffer b = buffer(10_000, 32, 32);
		b.setAmountUntracked(4321);
		assertEquals(4321, b.getAmount(), "getAmount returns the live stored amount");
		assertEquals(10_000, b.getCapacity(), "getCapacity returns the configured capacity");
		// Empty buffer still reports capacity, and amount=0 is a real value (not a hardcoded-0 return mutant)
		EnergyBuffer empty = buffer(5000, 32, 32);
		assertEquals(0, empty.getAmount(), "empty buffer reports 0 — distinguishable from a return-0 mutation");
		assertEquals(5000, empty.getCapacity());
	}

	@Test
	void onFinalCommit_firesTheConfiguredCallback() {
		// A VoidMethodCallMutator that drops onCommit.run() would leave the counter at 0 — this catches it.
		int[] fired = {0};
		EnergyBuffer b = new EnergyBuffer(1000, 32, 32, () -> fired[0]++);
		b.onFinalCommit();
		assertEquals(1, fired[0], "onFinalCommit must invoke the configured onCommit callback");
		b.onFinalCommit();
		assertEquals(2, fired[0], "the callback fires once per onFinalCommit call");
	}

	// --- property-based tests (MOD-110 phase 2) ---
	// These exercise the transfer math over a sweep of inputs, asserting structural INVARIANTS that
	// hold for every (capacity, rate, amount) tuple — conservation, monotonicity, clamping. A point test
	// pins one value; a property test pins the SHAPE. Mutations like Math.min(a,b) -> Math.max(a,b),
	// or `capacity - amount` -> `capacity + amount`, slip past point tests on lucky inputs but break
	// these invariants across the sweep, so pitest kills them across many mutants at once.

	/** Conservation: insert never leaves the buffer above capacity or below its starting amount. */
	@ParameterizedTest
	@MethodSource("insertSweep")
	void insert_neverExceedsCapacity_orDropsBelowStart(long capacity, long maxInsert, long startAmount, long request) {
		EnergyBuffer b = buffer(capacity, maxInsert, maxInsert);
		b.setAmountUntracked(startAmount);
		long moved = b.insert(request, new FakeTxn());
		long after = b.getAmount();
		assertTrue(after >= startAmount, "insert must never DECREASE the stored amount");
		assertTrue(after <= capacity, "insert must never exceed capacity");
		assertTrue(moved >= 0 && moved <= maxInsert, "returned amount is in [0, maxInsert]");
		assertTrue(moved <= request, "returned amount never exceeds the request");
		assertTrue(moved == after - startAmount, "returned amount equals the actual delta (no hidden loss/creation)");
	}

	/** Monotonicity: a larger request never moves LESS than a smaller one (the rate cap is the only ceiling). */
	@ParameterizedTest
	@MethodSource("insertSweep")
	void insert_isMonotonicInRequest(long capacity, long maxInsert, long startAmount, long request) {
		EnergyBuffer smaller = buffer(capacity, maxInsert, maxInsert);
		smaller.setAmountUntracked(startAmount);
		long movedSmaller = smaller.insert(request / 2, new FakeTxn());

		EnergyBuffer larger = buffer(capacity, maxInsert, maxInsert);
		larger.setAmountUntracked(startAmount);
		long movedLarger = larger.insert(request, new FakeTxn());
		assertTrue(movedLarger >= movedSmaller,
				"a larger request must move at least as much as a smaller one (no non-monotonic clamp)");
	}

	/** Conservation: extract never leaves the buffer below 0 or above its starting amount. */
	@ParameterizedTest
	@MethodSource("extractSweep")
	void extract_neverGoesNegative_orExceedsStart(long capacity, long maxExtract, long startAmount, long request) {
		EnergyBuffer b = buffer(capacity, maxExtract, maxExtract);
		b.setAmountUntracked(startAmount);
		long moved = b.extract(request, new FakeTxn());
		long after = b.getAmount();
		assertTrue(after >= 0, "extract must never drive the buffer negative");
		assertTrue(after <= startAmount, "extract must never INCREASE the stored amount");
		assertTrue(moved >= 0 && moved <= maxExtract, "returned amount is in [0, maxExtract]");
		assertTrue(moved <= request, "returned amount never exceeds the request");
		assertTrue(moved == startAmount - after, "returned amount equals the actual delta (no hidden creation/loss)");
	}

	/**
	 * Idempotency at the boundary: extracting the full stored amount twice — the second extract moves
	 * nothing. A mutation that mis-computes the second extract (e.g. not clamping to {@code amount})
	 * would drive the buffer negative on the repeat.
	 */
	@ParameterizedTest
	@MethodSource("extractSweep")
	void extract_drainingTwiceSecondMoveIsZero(long capacity, long maxExtract, long startAmount, long request) {
		EnergyBuffer b = buffer(capacity, maxExtract, maxExtract);
		b.setAmountUntracked(startAmount);
		b.extract(request, new FakeTxn());
		long secondMove = b.extract(request, new FakeTxn());
		// After the first extract, amount is reduced; the second extract must respect the NEW amount.
		// In particular, once amount hits 0, every subsequent extract returns 0.
		if (b.getAmount() == 0) {
			assertEquals(0, secondMove, "once drained, further extracts move nothing");
		} else {
			assertTrue(secondMove >= 0);
		}
		assertTrue(b.getAmount() >= 0, "buffer never negative even after repeated extracts");
	}

	private static Stream<Arguments> insertSweep() {
		// A spread of (capacity, maxInsert, startAmount, request) covering: empty/full/near-full buffers,
		// zero & generous rates, requests smaller/larger than room, and edge values (0, 1, capacity-1).
		return Stream.of(
				Arguments.of(100L, 32L, 0L, 50L),     // empty, room plenty
				Arguments.of(100L, 32L, 95L, 50L),   // near-full, little room
				Arguments.of(100L, 32L, 100L, 50L),  // full, no room
				Arguments.of(100L, 1000L, 50L, 200L),// rate not binding, request > room
				Arguments.of(1000L, 1L, 0L, 100L),   // tiny rate, request huge
				Arguments.of(20_000L, 32L, 19_999L, 32L), // the MOD-009 last-EU top-off boundary
				Arguments.of(1L, 1L, 0L, 1L),        // minimal non-trivial buffer
				Arguments.of(100L, 32L, 0L, 0L)      // zero request
		);
	}

	private static Stream<Arguments> extractSweep() {
		return Stream.of(
				Arguments.of(1000L, 32L, 500L, 1000L),
				Arguments.of(1000L, 32L, 9L, 1000L),    // less than one rate-unit stored
				Arguments.of(1000L, 32L, 0L, 100L),     // empty
				Arguments.of(1000L, 1000L, 500L, 100L), // request < stored
				Arguments.of(1000L, 1L, 100L, 1000L),   // tiny rate
				Arguments.of(1L, 1L, 1L, 1L),           // minimal
				Arguments.of(1000L, 32L, 500L, 0L)      // zero request
		);
	}
	// --- untracked writes (MOD-400): the machine's own bookkeeping, outside any transaction ---

	/**
	 * The three untracked writers exist to replace a public mutable field, and the clamp is the part
	 * the field could not provide: a buffer that accepts 20 000 EU into a 800 EU capacity puts the
	 * machine in a state the game can never produce, and every later comparison against it reads as
	 * valid. Four gametest fixtures were doing exactly that until this clamp appeared.
	 */
	@Test
	void setAmountUntracked_clampsIntoTheBuffersOwnRange() {
		EnergyBuffer b = buffer(800L, 32L, 32L);

		b.setAmountUntracked(20_000L);
		assertEquals(800L, b.getAmount(), "a charge above capacity is clamped, not stored");

		b.setAmountUntracked(-5L);
		assertEquals(0L, b.getAmount(), "a negative charge is clamped to empty");

		b.setAmountUntracked(512L);
		assertEquals(512L, b.getAmount(), "a charge inside the range is stored exactly");
	}

	/** Draining reports what was actually spent and never goes below empty. */
	@Test
	void drainInternal_spendsWhatIsThereAndReportsIt() {
		EnergyBuffer b = buffer(1000L, 32L, 32L);
		b.setAmountUntracked(50L);

		assertEquals(20L, b.drainInternal(20L), "a covered drain reports the full amount");
		assertEquals(30L, b.getAmount());
		assertEquals(30L, b.drainInternal(100L), "an over-drain reports only what was there");
		assertEquals(0L, b.getAmount(), "and leaves the buffer empty, never negative");
		assertEquals(0L, b.drainInternal(10L), "draining an empty buffer moves nothing");
		assertEquals(0L, b.drainInternal(-5L), "a negative request is a no-op, not a refund");
	}

	/** Producing reports what was actually stored — that is how a generator learns the buffer is full. */
	@Test
	void produceInternal_storesUpToCapacityAndReportsIt() {
		EnergyBuffer b = buffer(100L, 32L, 32L);
		b.setAmountUntracked(90L);

		assertEquals(10L, b.produceInternal(50L), "only the room that existed is stored");
		assertEquals(100L, b.getAmount());
		assertEquals(0L, b.produceInternal(10L), "a full buffer stores nothing");
		assertEquals(100L, b.getAmount(), "and does not overflow past capacity");
		assertEquals(0L, b.produceInternal(-5L), "a negative request is a no-op");
	}

	/**
	 * The load-bearing half of "untracked": these three writes must NOT enlist with a transaction and
	 * must NOT fire the commit hook. The hook means "energy was delivered or drawn from outside", and
	 * it wakes an idle consumer (R-29) — firing it for a machine spending its own buffer on its own
	 * work would wake that machine every single tick, which is the cost the sleep gate exists to avoid.
	 */
	@Test
	void untrackedWrites_doNotFireTheCommitHook() {
		int[] commits = {0};
		EnergyBuffer b = new EnergyBuffer(1000L, 32L, 32L, () -> commits[0]++);

		b.setAmountUntracked(500L);
		b.drainInternal(100L);
		b.produceInternal(50L);
		assertEquals(0, commits[0], "untracked bookkeeping must not look like an external transfer");

		// Control: the tracked path still does fire it, so the assertion above cannot pass vacuously.
		b.onFinalCommit();
		assertEquals(1, commits[0], "the commit hook itself still works");
	}
}
