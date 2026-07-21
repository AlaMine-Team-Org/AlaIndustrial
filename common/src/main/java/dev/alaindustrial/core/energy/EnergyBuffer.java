package dev.alaindustrial.core.energy;

/**
 * The platform-neutral energy buffer backing every machine (MOD-022 Phase 2). Owns the stored EU and
 * the per-operation transfer limits, and implements {@link EnergyPort} directly so it is both the
 * backing store and its own self-view.
 *
 * <p><b>Semantics.</b> The transaction-safe {@link #insert}/{@link #extract} math is an exact
 * re-implementation of Team Reborn's {@code SimpleEnergyStorage} (Fabric) and NeoForge's
 * {@code SimpleEnergyHandler}, which are byte-for-byte identical in their transfer math:
 * {@code inserted = min(maxInsert, min(max, capacity - amount))},
 * {@code extracted = min(maxExtract, min(max, amount))}; a non-zero move enlists the buffer with the
 * transaction (snapshot-before-mutate) and then adjusts {@link #amount}. Because the loaders share this
 * math and we pin a 1:1 EU:native-unit ratio, balance numbers are identical on both.
 *
 * <p><b>Transactions.</b> This buffer is a {@link EnergyPort.Participant}: it snapshots and restores its
 * own {@link #amount}, and fires {@link #onCommit} once when the outermost modifying transaction
 * commits. The loader's {@link EnergyPort.Txn} bridges these hooks onto its native snapshot journal
 * ({@code SnapshotParticipant} on Fabric, {@code SnapshotJournal} on NeoForge), so rollback and
 * final-commit behave exactly as the native buffers did.
 *
 * <p><b>Direct field access (legacy compatibility).</b> Machine content reads and writes {@link #amount}
 * directly for internal (non-transactional) drain/production and persistence — the same pattern the old
 * {@code MachineEnergyStorage}/{@code SimpleEnergyStorage} allowed. Those direct writes intentionally do
 * <em>not</em> go through the transaction journal or fire {@link #onCommit}; they are the machine's own
 * per-tick bookkeeping (R-29 notes this is why an internal drain must not spuriously wake a machine).
 */
public class EnergyBuffer implements EnergyPort, EnergyPort.Participant {

	/** Stored EU. Public and mutable for internal drain/production and persistence (see class doc). */
	public long amount;

	public final long capacity;
	public final long maxInsert;
	public final long maxExtract;

	/** Fired once after the outermost transaction that inserted/extracted through this buffer commits. */
	private final Runnable onCommit;

	/**
	 * @param capacity   maximum EU this buffer holds
	 * @param maxInsert  per-{@link #insert} transfer cap (0 ⇒ cannot receive)
	 * @param maxExtract per-{@link #extract} transfer cap (0 ⇒ cannot emit)
	 * @param onCommit   run once when the outermost modifying transaction commits (persistence + GUI
	 *                   sync + wake); may be a no-op
	 */
	public EnergyBuffer(long capacity, long maxInsert, long maxExtract, Runnable onCommit) {
		if (capacity < 0 || maxInsert < 0 || maxExtract < 0) {
			throw new IllegalArgumentException("Energy buffer limits must be non-negative");
		}
		this.capacity = capacity;
		this.maxInsert = maxInsert;
		this.maxExtract = maxExtract;
		this.onCommit = onCommit;
	}

	@Override
	public long insert(long maxAmount, Txn txn) {
		if (maxAmount < 0) {
			throw new IllegalArgumentException("maxAmount must be non-negative");
		}
		long inserted = Math.min(maxInsert, Math.min(maxAmount, capacity - amount));
		if (inserted > 0) {
			txn.enlist(this);
			amount += inserted;
			return inserted;
		}
		return 0;
	}

	@Override
	public long extract(long maxAmount, Txn txn) {
		if (maxAmount < 0) {
			throw new IllegalArgumentException("maxAmount must be non-negative");
		}
		long extracted = Math.min(maxExtract, Math.min(maxAmount, amount));
		if (extracted > 0) {
			txn.enlist(this);
			amount -= extracted;
			return extracted;
		}
		return 0;
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
		return maxInsert > 0;
	}

	@Override
	public boolean supportsExtraction() {
		return maxExtract > 0;
	}

	// --- EnergyPort.Participant: the loader's native journal drives these ---

	@Override
	public long createSnapshot() {
		return amount;
	}

	@Override
	public void readSnapshot(long snapshot) {
		amount = snapshot;
	}

	@Override
	public void onFinalCommit() {
		onCommit.run();
	}
}
