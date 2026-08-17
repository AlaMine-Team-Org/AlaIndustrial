package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the lifetime counters {@link EnergyBuffer} keeps for the statistics panel (MOD-125).
 *
 * <p>The counters exist to answer four different questions — what the wires brought in, what they drew
 * out, what the block made itself, what it spent on its own work — and the tests below pin each to the
 * write path that means it. The load-bearing one is {@link #simulatedTransferIsNotCounted()}: energy
 * transport probes a move before committing it, and a counter that banked those probes would drift
 * upward every tick a network re-evaluates itself, on every machine, forever.
 *
 * @implements MOD-125 lifetime energy counters: external transfer settled on commit, rollback-safe
 */
class EnergyBufferStatsTest {

	/** Minimal transaction handle; the buffer's participant hooks are driven explicitly per test. */
	private static final class FakeTxn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	/** A buffer that is MEASURING — i.e. one whose machine has a statistics chip fitted. */
	private static EnergyBuffer buffer(long capacity, long maxInsert, long maxExtract) {
		EnergyBuffer b = new EnergyBuffer(capacity, maxInsert, maxExtract, () -> {
		});
		b.setCountersEnabled(true);
		return b;
	}

	/** A buffer with no chip fitted — the default state of every machine in the world. */
	private static EnergyBuffer unmeteredBuffer(long capacity, long maxInsert, long maxExtract) {
		return new EnergyBuffer(capacity, maxInsert, maxExtract, () -> {
		});
	}

	// --- the chip gate (MOD-125): no chip, no measurement -----------------------------------------

	@Test
	void withoutTheChipNothingIsCounted() {
		EnergyBuffer b = unmeteredBuffer(1000, 100, 100);
		b.insert(40, new FakeTxn());
		b.onFinalCommit();
		b.produceInternal(70);
		b.drainInternal(30);
		assertEquals(0, b.getTotalEnergyIn(), "an un-instrumented machine measures nothing");
		assertEquals(0, b.getTotalEnergyGenerated());
		assertEquals(0, b.getTotalEnergyConsumed());
		assertEquals(80, b.getAmount(), "but the energy itself still moves normally");
	}

	/**
	 * Fitting a chip starts the clock at that moment rather than replaying what the buffer did before.
	 * The baseline has to stay maintained while measuring is off, or the first commit afterwards would
	 * report the whole pre-chip charge as a delivery.
	 */
	@Test
	void fittingTheChipCountsFromThatMomentOn() {
		EnergyBuffer b = unmeteredBuffer(1000, 100, 100);
		b.insert(100, new FakeTxn());
		b.onFinalCommit();
		assertEquals(0, b.getTotalEnergyIn());

		b.setCountersEnabled(true);
		b.insert(25, new FakeTxn());
		b.onFinalCommit();
		assertEquals(25, b.getTotalEnergyIn(), "only what moved after the chip went in");
	}

	@Test
	void freshBufferHasZeroedCounters() {
		EnergyBuffer b = buffer(1000, 100, 100);
		assertEquals(0, b.getTotalEnergyIn());
		assertEquals(0, b.getTotalEnergyOut());
		assertEquals(0, b.getTotalEnergyGenerated());
		assertEquals(0, b.getTotalEnergyConsumed());
	}

	@Test
	void committedInsertCountsAsEnergyIn() {
		EnergyBuffer b = buffer(1000, 100, 100);
		b.insert(40, new FakeTxn());
		b.onFinalCommit();
		assertEquals(40, b.getTotalEnergyIn(), "a committed delivery is energy the wires actually brought");
		assertEquals(0, b.getTotalEnergyOut());
	}

	@Test
	void committedExtractCountsAsEnergyOut() {
		EnergyBuffer b = buffer(1000, 100, 100);
		b.setAmountUntracked(500);
		b.extract(60, new FakeTxn());
		b.onFinalCommit();
		assertEquals(60, b.getTotalEnergyOut());
		assertEquals(0, b.getTotalEnergyIn());
	}

	/**
	 * The whole reason the counters are settled on commit rather than inside {@code insert}/{@code extract}.
	 * A simulated move mutates the amount, then the transaction rolls back through {@code readSnapshot} and
	 * never reaches {@code onFinalCommit} — so nothing may have been counted.
	 */
	@Test
	void simulatedTransferIsNotCounted() {
		EnergyBuffer b = buffer(1000, 100, 100);
		long snapshot = b.createSnapshot();
		b.insert(100, new FakeTxn());
		assertEquals(100, b.getAmount(), "the probe does move the amount while it is in flight");
		b.readSnapshot(snapshot);

		assertEquals(0, b.getAmount(), "rollback restores the charge");
		assertEquals(0, b.getTotalEnergyIn(), "a rolled-back probe must not bank energy that never moved");
		assertEquals(0, b.getTotalEnergyOut());
	}

	/** A rollback must not poison the baseline either: the next real transfer still counts exactly once. */
	@Test
	void transferAfterRollbackStillCountsOnce() {
		EnergyBuffer b = buffer(1000, 100, 100);
		long snapshot = b.createSnapshot();
		b.insert(100, new FakeTxn());
		b.readSnapshot(snapshot);

		b.insert(25, new FakeTxn());
		b.onFinalCommit();
		assertEquals(25, b.getTotalEnergyIn(), "the committed transfer counts, the abandoned probe does not");
	}

	@Test
	void internalProductionAndDrainCountSeparately() {
		EnergyBuffer b = buffer(1000, 100, 100);
		b.produceInternal(70);
		b.drainInternal(30);
		assertEquals(70, b.getTotalEnergyGenerated());
		assertEquals(30, b.getTotalEnergyConsumed());
		assertEquals(0, b.getTotalEnergyIn(), "own production is not a delivery from outside");
		assertEquals(0, b.getTotalEnergyOut(), "own spending is not a draw by the grid");
	}

	/**
	 * A generator's overflow is the gap between what it made and what the buffer took, and it must show up
	 * as exactly that: production counted in full, nothing invented on the transfer counters.
	 */
	@Test
	void productionIntoFullBufferCountsOnlyWhatFits() {
		EnergyBuffer b = buffer(100, 100, 100);
		b.setAmountUntracked(90);
		assertEquals(10, b.produceInternal(50), "only the part that fits is stored");
		assertEquals(10, b.getTotalEnergyGenerated(), "and only that part is credited as generated");
	}

	/**
	 * Untracked writes keep the commit baseline in step. Without that, the first commit after an internal
	 * drain would read the drain as a grid draw and double-count it.
	 */
	@Test
	void internalWritesDoNotLeakIntoTransferCounters() {
		EnergyBuffer b = buffer(1000, 100, 100);
		b.produceInternal(200);
		b.drainInternal(50);

		b.insert(10, new FakeTxn());
		b.onFinalCommit();

		assertEquals(10, b.getTotalEnergyIn(), "only the transactional part counts as received");
		assertEquals(0, b.getTotalEnergyOut(), "the internal drain must not surface as an outgoing transfer");
	}

	/** Loading a save restores the charge without the load itself reading as a delivery. */
	@Test
	void loadingChargeIsNotADelivery() {
		EnergyBuffer b = buffer(1000, 100, 100);
		b.setAmountUntracked(800);
		b.onFinalCommit();
		assertEquals(0, b.getTotalEnergyIn(), "restoring a saved charge is not energy the wires brought");
	}

	@Test
	void restoreCountersReinstatesPersistedTotals() {
		EnergyBuffer b = buffer(1000, 100, 100);
		b.restoreCounters(11, 22, 33, 44);
		assertEquals(11, b.getTotalEnergyIn());
		assertEquals(22, b.getTotalEnergyOut());
		assertEquals(33, b.getTotalEnergyGenerated());
		assertEquals(44, b.getTotalEnergyConsumed());
	}

	/** A corrupted save must not install negative career totals that every later comparison reads as valid. */
	@Test
	void restoreCountersClampsNegatives() {
		EnergyBuffer b = buffer(1000, 100, 100);
		b.restoreCounters(-5, -5, -5, -5);
		assertEquals(0, b.getTotalEnergyIn());
		assertEquals(0, b.getTotalEnergyOut());
		assertEquals(0, b.getTotalEnergyGenerated());
		assertEquals(0, b.getTotalEnergyConsumed());
	}

	/** A transaction that both fills and drains settles as its net effect, not as two gross movements. */
	@Test
	void netZeroTransactionCountsNothing() {
		EnergyBuffer b = buffer(1000, 100, 100);
		b.setAmountUntracked(100);
		b.insert(50, new FakeTxn());
		b.extract(50, new FakeTxn());
		b.onFinalCommit();
		assertEquals(0, b.getTotalEnergyIn());
		assertEquals(0, b.getTotalEnergyOut());
	}
}
