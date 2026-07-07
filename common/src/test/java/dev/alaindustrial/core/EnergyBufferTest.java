package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link EnergyBuffer} — the platform-neutral buffer math that MOD-022 Phase 2 extracted
 * from Team Reborn's {@code SimpleEnergyStorage} (Fabric) and NeoForge's {@code SimpleEnergyHandler}.
 * Both loaders drive this same common class, so these loader-free tests pin the transfer semantics on
 * <em>both</em> at once (the adapters are thin pass-throughs). Guards the migration against a regression
 * that would silently diverge Fabric and NeoForge behaviour.
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
		assertEquals(32, b.amount);
	}

	@Test
	void insert_cappedByRemainingCapacity() {
		EnergyBuffer b = buffer(100, 1000, 1000);
		b.amount = 95;
		assertEquals(5, b.insert(1000, new FakeTxn()), "insert must not overfill past capacity");
		assertEquals(100, b.amount);
	}

	@Test
	void insert_cappedByRequestedAmount() {
		EnergyBuffer b = buffer(1000, 1000, 1000);
		assertEquals(7, b.insert(7, new FakeTxn()), "insert must not exceed the requested amount");
	}

	@Test
	void insert_intoFullBufferMovesNothing() {
		EnergyBuffer b = buffer(100, 32, 32);
		b.amount = 100;
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
		b.amount = 19_999;
		assertEquals(1, b.insert(32, new FakeTxn()), "the 1 EU top-off must move");
		assertEquals(20_000, b.amount, "buffer must reach exact capacity");
	}

	// --- extract math: extracted = min(maxExtract, min(maxAmount, amount)) ---

	@Test
	void extract_cappedByMaxExtractRate() {
		EnergyBuffer b = buffer(1000, 32, 32);
		b.amount = 500;
		assertEquals(32, b.extract(1000, new FakeTxn()));
		assertEquals(468, b.amount);
	}

	@Test
	void extract_cappedByStoredAmount() {
		EnergyBuffer b = buffer(1000, 1000, 1000);
		b.amount = 9;
		assertEquals(9, b.extract(1000, new FakeTxn()), "cannot extract more than is stored");
		assertEquals(0, b.amount);
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
		machine.amount = 800;
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
		b.amount = 500;
		b.extract(10, tx);
		assertEquals(2, tx.enlistCount, "a non-zero extract must enlist too");
	}

	@Test
	void snapshotRoundTripsAmount() {
		EnergyBuffer b = buffer(1000, 32, 32);
		b.amount = 321;
		long snap = b.createSnapshot();
		b.amount = 0;
		b.readSnapshot(snap);
		assertEquals(321, b.amount, "readSnapshot must restore the captured amount (rollback path)");
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
}
