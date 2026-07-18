package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link EnergyMover} — the neutral "probe then commit" energy-move helper. Uses a fake
 * {@link EnergyTransactions} installed via {@link EnergyTransactions#install} so no loader runtime is
 * needed, and hand-rolled {@link EnergyPort} fakes that record calls and return distinctive amounts.
 *
 * <p>The probe/commit split is the load-bearing invariant (see {@link EnergyMover} class doc): a naive
 * "extract then refund surplus" leaks EU when the source is a rate-0-insert generator. These tests pin
 * that {@link EnergyMover#commit} transfers exactly the probed amount with no refund path.
 *
 * @implements energy-mover probe/commit contract (loss-free transfer sized to the target's room)
 */
class EnergyMoverTest {

	/** A fake EnergyTransactions that runs body callbacks synchronously against a shared fake txn. */
	private static final class FakeTransactions implements EnergyTransactions {
		final FakeTxn txn = new FakeTxn();

		@Override
		public void runCommitting(Consumer<EnergyPort.Txn> body) {
			body.accept(txn);
		}

		@Override
		public <T> T simulate(Function<EnergyPort.Txn, T> body) {
			return body.apply(txn);
		}
	}

	/** A fake txn that counts enlistments (same shape as EnergyBufferTest.FakeTxn). */
	private static final class FakeTxn implements EnergyPort.Txn {
		int enlistCount;

		@Override
		public void enlist(EnergyPort.Participant participant) {
			enlistCount++;
		}
	}

	/** A fake EnergyPort whose extract/insert return their capacity, bounded by the requested maxAmount
	 *  (mirrors how a real buffer clamps to the requested amount). */
	private static final class StubPort implements EnergyPort {
		final long extractable;
		final long insertable;
		long lastExtractRequest;
		long lastInsertRequest;
		boolean extractCalled;
		boolean insertCalled;

		StubPort(long extractable, long insertable) {
			this.extractable = extractable;
			this.insertable = insertable;
		}

		@Override
		public long insert(long maxAmount, EnergyPort.Txn txn) {
			insertCalled = true;
			lastInsertRequest = maxAmount;
			return Math.min(insertable, maxAmount);
		}

		@Override
		public long extract(long maxAmount, EnergyPort.Txn txn) {
			extractCalled = true;
			lastExtractRequest = maxAmount;
			return Math.min(extractable, maxAmount);
		}

		@Override
		public long getAmount() {
			return 0;
		}

		@Override
		public long getCapacity() {
			return 0;
		}

		@Override
		public boolean supportsInsertion() {
			return true;
		}

		@Override
		public boolean supportsExtraction() {
			return true;
		}
	}

	private FakeTransactions txns;

	// Install the fake transactions before each test so EnergyTransactions.get() resolves to it.
	@org.junit.jupiter.api.BeforeEach
	void installFakeTransactions() {
		txns = new FakeTransactions();
		EnergyTransactions.install(txns);
	}

	@AfterEach
	void clearTransactions() {
		// Reset the global so we don't leak state into other test classes on the same JVM.
		EnergyTransactions.install(null);
	}

	// --- probe: the dry-run sizing pass ---

	@Test
	void probe_returnsWhatTargetAccepts_boundedBySourceAndMax() {
		// Source can give 50, target accepts 30 → probe returns 30 (the binding constraint is the target).
		StubPort from = new StubPort(50, 0);
		StubPort to = new StubPort(0, 30);
		assertEquals(30L, EnergyMover.probe(from, to, 100),
				"probe returns the amount the target will accept, bounded by what the source can give");
	}

	@Test
	void probe_boundedByMaxAmount() {
		StubPort from = new StubPort(100, 0);
		StubPort to = new StubPort(0, 100);
		assertEquals(20L, EnergyMover.probe(from, to, 20),
				"probe respects the maxAmount ceiling");
	}

	@Test
	void probe_zeroWhenSourceCannotExtract() {
		StubPort from = new StubPort(0, 0); // source empty
		StubPort to = new StubPort(0, 50);
		assertEquals(0L, EnergyMover.probe(from, to, 100), "nothing extractable → probe 0");
	}

	@Test
	void probe_zeroWhenTargetCannotAccept() {
		StubPort from = new StubPort(50, 0);
		StubPort to = new StubPort(0, 0); // target full / refuses
		assertEquals(0L, EnergyMover.probe(from, to, 100), "target accepts nothing → probe 0");
	}

	@Test
	void probe_passesExactProbedAmountFromExtractToInsert() {
		// probe extracts the ORIGINAL maxAmount from `from`, then inserts exactly what extract returned
		// into `to`. So from.extract sees maxAmount (100), but to.insert sees the extractable (40) —
		// never the original maxAmount. This is the wiring that makes the move loss-free.
		StubPort from = new StubPort(40, 0);
		StubPort to = new StubPort(0, 40);
		EnergyMover.probe(from, to, 100);
		assertEquals(100L, from.lastExtractRequest, "extract receives the original maxAmount");
		assertEquals(40L, to.lastInsertRequest,
				"insert receives exactly what extract returned (the probed amount), not maxAmount");
	}

	@Test
	void probe_returnsZeroForNullOrNonPositiveArgs() {
		StubPort from = new StubPort(50, 0);
		StubPort to = new StubPort(0, 50);
		assertEquals(0L, EnergyMover.probe(null, to, 100), "null source → 0");
		assertEquals(0L, EnergyMover.probe(from, null, 100), "null target → 0");
		assertEquals(0L, EnergyMover.probe(from, to, 0), "maxAmount=0 → 0");
		assertEquals(0L, EnergyMover.probe(from, to, -5), "negative maxAmount → 0");
	}

	/**
	 * Kills the L34 {@code maxAmount <= 0} {@code ConditionalsBoundary} survivor. The existing test above
	 * only asserts the return value is 0 — a mutant flipping {@code <=} to {@code <} returns 0 too, but
	 * only AFTER skipping the guard on {@code maxAmount == 0} and calling {@code from.extract(0, sim)}
	 * inside the {@code simulate} lambda (a side effect). This test asserts that side effect stayed
	 * absent: on the {@code =0} boundary the probe must short-circuit <em>before</em> touching either port.
	 */
	@Test
	void probe_maxAmountZero_shortCircuitsBeforeCallingExtract() {
		StubPort from = new StubPort(50, 0);
		StubPort to = new StubPort(0, 50);
		assertEquals(0L, EnergyMover.probe(from, to, 0));
		assertFalse(from.extractCalled,
				"maxAmount == 0 must short-circuit before from.extract (ConditionalsBoundary survivor)");
		assertFalse(to.insertCalled, "maxAmount == 0 must short-circuit before to.insert");
	}

	/**
	 * Kills the L39 {@code extracted <= 0} {@code ConditionalsBoundary} survivor inside the probe lambda.
	 * A mutant flipping {@code <=} to {@code <} skips the {@code return 0L} on {@code extracted == 0} and
	 * falls through to {@code to.insert(0, sim)} (a side effect on an empty source). The existing
	 * {@link #probe_zeroWhenSourceCannotExtract} asserts only the return; this one asserts the insert
	 * call stayed absent.
	 */
	@Test
	void probe_emptySource_doesNotCallInsertOnTarget() {
		StubPort from = new StubPort(0, 0); // source yields 0
		StubPort to = new StubPort(0, 50);
		assertEquals(0L, EnergyMover.probe(from, to, 100));
		assertTrue(from.extractCalled, "probe does call from.extract to size the move");
		assertFalse(to.insertCalled,
				"extracted == 0 must short-circuit before to.insert (ConditionalsBoundary survivor)");
	}

	// --- commit: transfers exactly the probed amount, no refund path ---

	@Test
	void commit_extractsAndInsertsExactlyTheProbedAmount() {
		StubPort from = new StubPort(Long.MAX_VALUE, 0);
		StubPort to = new StubPort(0, Long.MAX_VALUE);
		EnergyMover.commit(from, to, 42, txns.txn);
		assertTrue(from.extractCalled, "commit must call from.extract");
		assertTrue(to.insertCalled, "commit must call to.insert");
		assertEquals(42L, from.lastExtractRequest, "commit extracts exactly the probed amount");
		assertEquals(42L, to.lastInsertRequest, "commit inserts exactly the probed amount");
	}

	@Test
	void commit_zeroOrNegativeAmountIsNoOp() {
		StubPort from = new StubPort(Long.MAX_VALUE, 0);
		StubPort to = new StubPort(0, Long.MAX_VALUE);
		EnergyMover.commit(from, to, 0, txns.txn);
		assertTrue(!from.extractCalled && !to.insertCalled, "amount=0 → no calls");
		EnergyMover.commit(from, to, -1, txns.txn);
		assertTrue(!from.extractCalled && !to.insertCalled, "negative amount → no calls");
	}
}
