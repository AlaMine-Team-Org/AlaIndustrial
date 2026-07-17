package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link FaceEnergyPort} — the per-face role-gating wrapper around an {@link EnergyPort}
 * (R-NRG-03). Uses a hand-rolled fake delegate (the same pattern {@link EnergyBufferTest} uses for its
 * {@code FakeTxn}) so no Minecraft runtime is needed. Pitest baseline found 8 uncovered mutants on this
 * class; the highest-value one is the {@code supportsInsertion()}/{@code supportsExtraction()} "role-only,
 * not delegate" invariant the class javadoc warns about explicitly.
 *
 * @implements R-NRG-03 per-face energy gating (IN accepts only, OUT emits only, BOTH passes through, NONE blocks)
 */
class FaceEnergyPortTest {

	/** Records every call and returns distinctive values so the wrapper's forwarding is observable. */
	private static final class RecordingPort implements EnergyPort {
		long insertReturned;
		long extractReturned;
		boolean supportsInsertion = true;
		boolean supportsExtraction = true;
		long amount = 999;
		long capacity = 8888;
		boolean insertCalled;
		boolean extractCalled;

		@Override
		public long insert(long maxAmount, EnergyPort.Txn txn) {
			insertCalled = true;
			return insertReturned;
		}

		@Override
		public long extract(long maxAmount, EnergyPort.Txn txn) {
			extractCalled = true;
			return extractReturned;
		}

		@Override
		public long getAmount() {
			return amount;
		}

		@Override
		public long getCapacity() {
			return capacity;
		}

		@Override
		public boolean supportsInsertion() {
			return supportsInsertion;
		}

		@Override
		public boolean supportsExtraction() {
			return supportsExtraction;
		}
	}

	private static final class NoopTxn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
			// forwarding path — the wrapper does not enlist anything itself
		}
	}

	private final EnergyPort.Txn txn = new NoopTxn();

	// --- IN role: insert delegates, extract blocked ---

	@Test
	void inRole_delegatesInsert_returnsExactDelegateValue() {
		RecordingPort delegate = new RecordingPort();
		delegate.insertReturned = 17L;
		FaceEnergyPort port = new FaceEnergyPort(delegate, EnergyRole.IN);
		assertEquals(17L, port.insert(32, txn), "insert must return the delegate's exact value");
		assertTrue(delegate.insertCalled, "insert must forward to the delegate");
	}

	@Test
	void inRole_blocksExtract() {
		RecordingPort delegate = new RecordingPort();
		delegate.extractReturned = 17L;
		FaceEnergyPort port = new FaceEnergyPort(delegate, EnergyRole.IN);
		assertEquals(0L, port.extract(32, txn), "IN face must not emit");
		assertFalse(delegate.extractCalled, "IN face must not even call delegate.extract");
	}

	// --- OUT role: extract delegates, insert blocked ---

	@Test
	void outRole_delegatesExtract_returnsExactDelegateValue() {
		RecordingPort delegate = new RecordingPort();
		delegate.extractReturned = 23L;
		FaceEnergyPort port = new FaceEnergyPort(delegate, EnergyRole.OUT);
		assertEquals(23L, port.extract(32, txn), "extract must return the delegate's exact value");
		assertTrue(delegate.extractCalled);
	}

	@Test
	void outRole_blocksInsert() {
		RecordingPort delegate = new RecordingPort();
		delegate.insertReturned = 23L;
		FaceEnergyPort port = new FaceEnergyPort(delegate, EnergyRole.OUT);
		assertEquals(0L, port.insert(32, txn), "OUT face must not accept");
		assertFalse(delegate.insertCalled, "OUT face must not even call delegate.insert");
	}

	// --- BOTH: both delegate; NONE: both blocked ---

	@Test
	void bothRole_delegatesBothDirections() {
		RecordingPort delegate = new RecordingPort();
		delegate.insertReturned = 5L;
		delegate.extractReturned = 7L;
		FaceEnergyPort port = new FaceEnergyPort(delegate, EnergyRole.BOTH);
		assertEquals(5L, port.insert(32, txn));
		assertEquals(7L, port.extract(32, txn));
		assertTrue(delegate.insertCalled);
		assertTrue(delegate.extractCalled);
	}

	@Test
	void noneRole_blocksBothDirections() {
		RecordingPort delegate = new RecordingPort();
		delegate.insertReturned = 5L;
		delegate.extractReturned = 7L;
		FaceEnergyPort port = new FaceEnergyPort(delegate, EnergyRole.NONE);
		assertEquals(0L, port.insert(32, txn), "NONE face must not accept");
		assertEquals(0L, port.extract(32, txn), "NONE face must not emit");
		assertFalse(delegate.insertCalled);
		assertFalse(delegate.extractCalled);
	}

	// --- The supports*() invariant the class javadoc warns about ---

	/**
	 * The documented invariant (FaceEnergyPort javadoc lines 42-49): {@code supportsInsertion()} answers
	 * from the ROLE, never from {@code delegate.supportsInsertion()}. A mutation that delegates would make
	 * the predicate follow the delegate's state and could contradict {@code insert()} (which still works on
	 * an IN face). Pin it with a delegate that reports {@code supportsInsertion() == false}: the IN-wrapped
	 * port must STILL report true.
	 */
	@Test
	void supportsInsertion_followsRole_notDelegateState() {
		RecordingPort delegate = new RecordingPort();
		delegate.supportsInsertion = false; // delegate says "no"
		FaceEnergyPort inPort = new FaceEnergyPort(delegate, EnergyRole.IN);
		assertTrue(inPort.supportsInsertion(),
				"IN face reports supportsInsertion from the ROLE, contradicting the delegate — by design");

		FaceEnergyPort outPort = new FaceEnergyPort(delegate, EnergyRole.OUT);
		assertFalse(outPort.supportsInsertion(), "OUT face reports supportsInsertion=false from the role");
	}

	@Test
	void supportsExtraction_followsRole_notDelegateState() {
		RecordingPort delegate = new RecordingPort();
		delegate.supportsExtraction = false; // delegate says "no"
		FaceEnergyPort outPort = new FaceEnergyPort(delegate, EnergyRole.OUT);
		assertTrue(outPort.supportsExtraction(),
				"OUT face reports supportsExtraction from the ROLE, contradicting the delegate — by design");

		FaceEnergyPort inPort = new FaceEnergyPort(delegate, EnergyRole.IN);
		assertFalse(inPort.supportsExtraction(), "IN face reports supportsExtraction=false from the role");
	}

	// --- amount/capacity always delegate (they are read-only projections) ---

	@Test
	void amountAndCapacity_alwaysDelegate() {
		RecordingPort delegate = new RecordingPort();
		FaceEnergyPort port = new FaceEnergyPort(delegate, EnergyRole.NONE);
		assertEquals(999L, port.getAmount(), "getAmount always delegates, regardless of role");
		assertEquals(8888L, port.getCapacity(), "getCapacity always delegates, regardless of role");
	}
}
