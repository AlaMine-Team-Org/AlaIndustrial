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

	/**
	 * Stored EU. Private since MOD-400: every write now goes through a named method that says which
	 * KIND of write it is (see the class doc), so a reader no longer has to infer from context whether
	 * a given assignment was meant to be transactional.
	 */
	private long amount;

	public final long capacity;
	public final long maxInsert;
	public final long maxExtract;

	/** Fired once after the outermost transaction that inserted/extracted through this buffer commits. */
	private final Runnable onCommit;

	// --- Lifetime counters (MOD-125) -----------------------------------------------------------
	//
	// Four counters, because "energy moved" has four distinct meanings and a panel that merges them
	// lies to the player. External transfers (in/out) are what the wires delivered or drew; internal
	// production/consumption is what this block made or spent on its own work. For a generator the
	// difference between `generated` and `out` IS the use-it-or-lose-it loss on a full buffer — the
	// signal that its consumers are undersized — so collapsing the pair would erase the one number
	// worth showing.
	//
	// WHY THE EXTERNAL PAIR IS NOT COUNTED IN insert()/extract(): those run inside SIMULATIONS too
	// (EnergyNetwork/EnergyMover probe a move before committing it, then roll back). Counting there
	// would bank energy that never moved, and the error would grow with every tick the network
	// re-probes. Instead the pair is settled in onFinalCommit() — the one hook that fires only after
	// the outermost transaction actually commits — by diffing `amount` against the last settled
	// value. The three untracked writers below keep that baseline in step, so the diff seen at commit
	// time is purely the transactional movement.

	private long totalEnergyIn;
	private long totalEnergyOut;
	private long totalEnergyGenerated;
	private long totalEnergyConsumed;

	/**
	 * {@link #amount} as of the last time a counter was settled. Not persisted: it is re-seeded from the
	 * loaded charge in {@link #setAmountUntracked}, and a diff can only ever span one server session.
	 */
	private long settledAmount;

	/**
	 * Whether the counters above advance at all (MOD-125). Off by default: a machine measures only while
	 * a statistics chip is fitted, so an un-instrumented base pays nothing — not the arithmetic, and not
	 * the four extra longs in its save tag.
	 *
	 * <p>The baseline is still maintained while disabled (see {@link #onFinalCommit}), so fitting a chip
	 * to a running machine starts counting from that moment instead of banking whatever moved in the
	 * transaction that happened to be open.
	 */
	private boolean countersEnabled;

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

	// --- Untracked writes: the machine's own bookkeeping, deliberately outside the transaction ---
	//
	// These three replace the old `public long amount` (MOD-400). They do NOT enlist with a
	// transaction and do NOT fire onCommit, which is the whole point: a machine spending its own
	// buffer on its own work is not a transfer, and firing the delivery hook there would wake an idle
	// consumer every tick (R-29). Each one clamps to [0, capacity] — a guarantee the raw field could
	// not make, and one that turns "stored a negative charge" from a silent corruption into a no-op.

	/**
	 * Spend EU on this block's own work (a machine's per-tick drain). Returns what was actually
	 * spent, which is less than asked only when the buffer holds less.
	 */
	public long drainInternal(long eu) {
		if (eu <= 0) {
			return 0L;
		}
		long spent = Math.min(eu, amount);
		amount -= spent;
		if (countersEnabled) {
			totalEnergyConsumed += spent;
		}
		settledAmount -= spent;
		return spent;
	}

	/**
	 * Add EU this block generated itself (a generator's per-tick output). Returns what was actually
	 * stored — less than asked when the buffer fills up, which is how a generator learns it should
	 * stop burning fuel.
	 */
	public long produceInternal(long eu) {
		if (eu <= 0) {
			return 0L;
		}
		long stored = Math.min(eu, capacity - amount);
		amount += stored;
		if (countersEnabled) {
			totalEnergyGenerated += stored;
		}
		settledAmount += stored;
		return stored;
	}

	/**
	 * Set the stored charge outright: reading a save, restoring from an item component, or arranging a
	 * test fixture. Not for gameplay — a machine that "sets" its charge is losing or creating EU
	 * without saying where it went.
	 *
	 * <p>Clamped, so a corrupted or hand-edited save cannot install a charge outside the buffer's own
	 * range and have every later comparison read it as valid.
	 */
	public void setAmountUntracked(long eu) {
		amount = Math.clamp(eu, 0L, capacity);
		// Re-seed the counter baseline: this write is a restore, not a movement, and leaving the old
		// baseline behind would make the next commit diff report the whole loaded charge as "received".
		settledAmount = amount;
	}

	// --- Lifetime counters: read + restore (MOD-125) ---------------------------------------------

	/** EU delivered into this buffer from outside (cables, direct pushes, chargers). */
	public long getTotalEnergyIn() {
		return totalEnergyIn;
	}

	/** EU drawn out of this buffer by the network or a neighbour. */
	public long getTotalEnergyOut() {
		return totalEnergyOut;
	}

	/** EU this block produced itself. Exceeds {@link #getTotalEnergyOut()} by whatever a full buffer lost. */
	public long getTotalEnergyGenerated() {
		return totalEnergyGenerated;
	}

	/** EU this block spent on its own work. */
	public long getTotalEnergyConsumed() {
		return totalEnergyConsumed;
	}

	/** Turn the lifetime counters on or off (MOD-125: driven by the statistics chip). */
	public void setCountersEnabled(boolean enabled) {
		this.countersEnabled = enabled;
	}

	/** Whether the lifetime counters are currently advancing. */
	public boolean countersEnabled() {
		return countersEnabled;
	}

	/**
	 * Reinstate persisted counters when the block entity loads. Separate from {@link #setAmountUntracked}
	 * because the charge and the career totals are independent: a buffer can load empty and still carry a
	 * million EU of history.
	 */
	public void restoreCounters(long in, long out, long generated, long consumed) {
		this.totalEnergyIn = Math.max(0L, in);
		this.totalEnergyOut = Math.max(0L, out);
		this.totalEnergyGenerated = Math.max(0L, generated);
		this.totalEnergyConsumed = Math.max(0L, consumed);
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

	/**
	 * Settle the external transfer counters and fire the owner's commit hook.
	 *
	 * <p>The diff against {@link #settledAmount} is the whole trick (MOD-125): this runs only after the
	 * outermost transaction commits, so a rolled-back simulation — which restored {@link #amount} through
	 * {@link #readSnapshot} and never reached here — contributes nothing. A transaction that both inserted
	 * and extracted settles as its net effect, which is the honest answer for a buffer that ended the
	 * transaction where it started.
	 */
	@Override
	public void onFinalCommit() {
		long delta = amount - settledAmount;
		// The baseline is refreshed either way: while measuring is off the diff is discarded rather than
		// accumulated, so switching it on later starts from "now" instead of replaying history.
		if (countersEnabled) {
			if (delta > 0) {
				totalEnergyIn += delta;
			} else if (delta < 0) {
				totalEnergyOut -= delta;
			}
		}
		settledAmount = amount;
		onCommit.run();
	}
}
