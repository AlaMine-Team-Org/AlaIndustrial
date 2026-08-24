package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyRole;
import java.util.IdentityHashMap;
import java.util.Map;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import dev.alaindustrial.core.energy.EnergyBuffer;

/**
 * NeoForge implementation of the platform-neutral {@link EnergyPort} (MOD-022 Phase 2): an adapter over
 * a NeoForge {@link EnergyHandler} ({@code net.neoforged.neoforge.transfer.energy.EnergyHandler},
 * verified against 26.2.0.67). This is the read/insert/extract view of a foreign or self-published
 * {@code EnergyHandler}; the reverse direction (publishing a common {@code EnergyBuffer} as an
 * {@code EnergyHandler} for the capability lookup) lives in {@link BufferAsEnergyHandler}.
 *
 * <p><b>API asymmetry handled here.</b> The verified 26.2 {@code EnergyHandler} exposes
 * {@code getAmountAsLong()/getCapacityAsLong()} but {@code insert(int, TransactionContext)} /
 * {@code extract(int, TransactionContext)} — no {@code long} overloads and no {@code supports*}
 * predicates. This adapter caps the neutral {@code long} ASK at {@code Integer.MAX_VALUE} (matching the
 * {@code int} signature without clamping below the request) and derives the {@code supports*} predicates
 * from the per-face {@link EnergyRole} <em>only</em> — never from buffer state — so common transport code
 * sees the same state-independent contract it sees on Fabric (where {@code supports*} is a fixed
 * capability, not a "full/empty right now?" check).
 *
 * <p>The neutral {@link EnergyPort.Txn} handle wraps a NeoForge {@link TransactionContext}
 * ({@link NeoForgeTxn}); this adapter unwraps it before delegating. Transactions follow the verified
 * pattern {@code try (Transaction tx = Transaction.openRoot()) { ...; tx.commit(); }} on the caller
 * side (see {@code NeoForgeEnergyTransactions}); this class never opens or commits one.
 */
public final class NeoForgeEnergyPort implements EnergyPort {
	private final EnergyHandler delegate;
	private final EnergyRole role;

	public NeoForgeEnergyPort(EnergyHandler delegate, EnergyRole role) {
		this.delegate = delegate;
		this.role = role;
	}

	/** Wrap an {@link EnergyHandler} as a neutral {@link EnergyPort} with the given face role. */
	public static EnergyPort of(EnergyHandler handler, EnergyRole role) {
		return handler == null ? null : new NeoForgeEnergyPort(handler, role);
	}

	@Override
	public long insert(long maxAmount, Txn txn) {
		if (!role.canInsert()) {
			return 0;
		}
		// Cap the ASK at Integer.MAX_VALUE (EnergyHandler.insert is int-based) but never below the request,
		// matching Fabric which never clamps the ask, only the answer. maxAmount is non-negative per the
		// EnergyPort contract, so the cast stays in [0, Integer.MAX_VALUE] (insert() rejects negatives).
		return delegate.insert((int) Math.min(maxAmount, Integer.MAX_VALUE), unwrap(txn));
	}

	@Override
	public long extract(long maxAmount, Txn txn) {
		if (!role.canExtract()) {
			return 0;
		}
		return delegate.extract((int) Math.min(maxAmount, Integer.MAX_VALUE), unwrap(txn));
	}

	@Override
	public long getAmount() {
		return delegate.getAmountAsLong();
	}

	@Override
	public long getCapacity() {
		return delegate.getCapacityAsLong();
	}

	@Override
	public boolean supportsInsertion() {
		// EnergyHandler has no supports* predicate. It is a capability check ("can this port ever
		// receive?"), NOT a state check ("is it full right now?") — Fabric's EnergyStorage.supportsInsertion
		// is state-independent (SimpleEnergyStorage returns maxInsert > 0, verified in the 5.0.0 bytecode).
		// A runtime capacity check here would wrongly return false at full capacity and let the network
		// refuse a 1 EU trickle top-off, stranding energy that insert() would floor to 0 on its own
		// (breaking the MOD-009 exact-capacity invariant). Guard by role only; insert() returns 0 when full.
		return role.canInsert();
	}

	@Override
	public boolean supportsExtraction() {
		return role.canExtract();
	}

	static TransactionContext unwrap(Txn txn) {
		if (txn instanceof NeoForgeTxn nt) {
			return nt.ctx();
		}
		throw new IllegalArgumentException("Expected a NeoForge transaction handle, got: " + txn);
	}

	/**
	 * Wrap a NeoForge {@link TransactionContext} we OWN as a neutral {@link EnergyPort.Txn}.
	 *
	 * <p><b>Caller must evict.</b> The wrapped handle is cached per-thread keyed by
	 * {@link TransactionContext} identity. Unlike Fabric, NeoForge's {@link TransactionContext} is a sealed
	 * interface exposing only {@code depth()} — it has no close-callback API (verified against
	 * 26.2.0.67), so this method cannot auto-evict on close. Every caller MUST call
	 * {@link NeoForgeTxn#evict(TransactionContext)} after the transaction closes, or the thread-local cache
	 * leaks one entry per transaction. The {@link NeoForgeEnergyTransactions} SPI (the sole owner path) does
	 * this in a {@code finally} block. For a transaction we do NOT own — a reverse-adapter capability call
	 * whose lifecycle belongs to a foreign mod — use {@link #wrapForeign} instead, which self-evicts.
	 */
	public static Txn wrap(TransactionContext ctx) {
		return NeoForgeTxn.forContext(ctx);
	}

	/**
	 * Wrap a FOREIGN NeoForge {@link TransactionContext} — one whose open/commit/close lifecycle we do not
	 * own — as a neutral {@link EnergyPort.Txn}, for the reverse adapters ({@link BufferAsEnergyHandler},
	 * {@link TankAsResourceHandler}) that publish our buffers/tanks through {@code Capabilities.*.BLOCK}.
	 *
	 * <p>Unlike {@link #wrap}, there is no {@code finally} we can hang an {@code evict} on: the foreign mod
	 * opened the transaction and will close it out of our sight. So on cache miss this installs a
	 * self-evicting housekeeping journal bound to the transaction's root (MOD-185); the cached handle — and
	 * the {@code journals} map it accumulates across the {@link TransactionContext} identities NeoForge
	 * reuses between successive root transactions — is dropped from the per-thread cache when the foreign
	 * transaction closes (root commit or root abort), instead of leaking one journal entry per foreign
	 * buffer/tank touched for the life of the session.
	 */
	public static Txn wrapForeign(TransactionContext ctx) {
		return NeoForgeTxn.forForeignContext(ctx);
	}

	/**
	 * NeoForge-side {@link EnergyPort.Txn} carrying the loader's {@link TransactionContext}. Bridges the
	 * neutral {@link EnergyPort.Participant} enlistment onto NeoForge {@link SnapshotJournal}s so a common
	 * {@link dev.alaindustrial.core.energy.EnergyBuffer} that participates in a move gets its snapshot / final
	 * commit hooks driven by NeoForge's native journal. One handle is shared per {@link TransactionContext}
	 * (keyed by identity, evicted on close) so repeated enlists dedupe through {@code updateSnapshots}.
	 */
	public static final class NeoForgeTxn implements EnergyPort.Txn {
		/** NeoForge transactions are thread-confined, so a per-thread identity cache is safe. */
		private static final ThreadLocal<Map<TransactionContext, NeoForgeTxn>> ACTIVE =
				ThreadLocal.withInitial(IdentityHashMap::new);

		private final TransactionContext ctx;
		private final Map<EnergyPort.Participant, ParticipantJournal> journals = new IdentityHashMap<>();

		NeoForgeTxn(TransactionContext ctx) {
			this.ctx = ctx;
		}

		/**
		 * The shared {@link NeoForgeTxn} for an OWNED {@code ctx}. NeoForge's {@link TransactionContext}
		 * exposes no close callback, so the handle is created for the root and evicted by
		 * {@link NeoForgeEnergyTransactions} after the transaction closes (single owner). For the common
		 * case there is exactly one root transaction in flight per move.
		 */
		public static NeoForgeTxn forContext(TransactionContext ctx) {
			return ACTIVE.get().computeIfAbsent(ctx, NeoForgeTxn::new);
		}

		/**
		 * The shared {@link NeoForgeTxn} for a FOREIGN {@code ctx} (a reverse-adapter capability call).
		 * Identical to {@link #forContext} except that a fresh entry also gets a self-evicting
		 * {@link EvictionJournal}, so the foreign mod's close — which we never see directly — drops the entry
		 * (MOD-185). Registered once per entry: a second capability call inside the SAME foreign transaction
		 * hits the cache and reuses the handle, so repeated buffer enlists still dedupe through the single
		 * cached {@code journals} map (one {@link ParticipantJournal} per buffer → correct rollback ordering;
		 * a fresh-per-call handle would spawn a second journal and silently dup/lose EU on abort).
		 */
		public static NeoForgeTxn forForeignContext(TransactionContext ctx) {
			Map<TransactionContext, NeoForgeTxn> active = ACTIVE.get();
			NeoForgeTxn cached = active.get(ctx);
			if (cached != null) {
				return cached;
			}
			NeoForgeTxn created = new NeoForgeTxn(ctx);
			// Publish the entry BEFORE registering the hook so the buffer's own enlist, which runs later in
			// the same insert/extract call, shares this handle. updateSnapshots must run while ctx is open —
			// it is: we are inside the foreign mod's insert/extract, so its transaction has not closed yet.
			active.put(ctx, created);
			new EvictionJournal(ctx).updateSnapshots(ctx);
			return created;
		}

		/** Drop the cached handle for {@code ctx} once its transaction has closed. */
		public static void evict(TransactionContext ctx) {
			ACTIVE.get().remove(ctx);
		}

		/** Visible for tests only: size of the per-thread {@link #ACTIVE} cache, for leak-detection asserts. */
		public static int activeCacheSize() {
			return ACTIVE.get().size();
		}

		public TransactionContext ctx() {
			return ctx;
		}

		@Override
		public void enlist(EnergyPort.Participant participant) {
			journals.computeIfAbsent(participant, ParticipantJournal::new).updateSnapshots(ctx);
		}
	}

	/**
	 * A stateless {@link SnapshotJournal} whose only job is to evict a foreign {@link NeoForgeTxn} from
	 * {@link NeoForgeTxn#ACTIVE} when the foreign transaction closes (MOD-185). NeoForge exposes no close
	 * callback on {@link TransactionContext}, but a journal enlisted via {@link #updateSnapshots} IS notified
	 * on close through {@link #revertToSnapshot} (abort) and {@link #onRootCommit} (commit) — the only
	 * overridable close seams, since {@code SnapshotJournal.onClose} is package-private.
	 *
	 * <p><b>Evict on the close of whatever context we were enlisted in (MOD-285).</b>
	 * {@link #onRootCommit} fires at root commit, and {@link #revertToSnapshot} fires when that context
	 * aborts — nested or not. Both drop the entry for {@link #ctx}, which is safe at any depth because the
	 * cache is keyed by that exact {@link TransactionContext}: once it closes, nothing can look the entry
	 * up again.
	 *
	 * <p>This used to be gated to {@code depth()==0} out of concern that dropping an entry on a nested
	 * close could let a later enlist of the same buffer build a SECOND {@link ParticipantJournal} — which
	 * on abort would revert in registration order and strand the buffer at its intermediate value, the
	 * exact fresh-per-call regression MOD-185 rejected. The concern does not apply: a nested context and
	 * its root are different cache keys, so the root's entry (and its one-journal-per-buffer map) is
	 * untouched. The gate instead ORPHANED entries — a foreign mod that probes in a nested transaction,
	 * aborts the probe and commits the root for real (the standard simulate-then-commit pattern) left its
	 * handle in the cache forever, and since NeoForge reuses {@code TransactionContext} identities between
	 * root transactions, a later transaction could pick up that stale handle and its dead journals.
	 * Both the orphan and the multi-journal concern are pinned by {@code NeoForgeTxnCacheTest}.
	 *
	 * <p>The journal carries no state — its snapshot is a constant token — so it never mutates game state; it
	 * is pure per-thread-cache housekeeping.
	 */
	private static final class EvictionJournal extends SnapshotJournal<Object> {
		private static final Object TOKEN = new Object();

		private final TransactionContext ctx;

		EvictionJournal(TransactionContext ctx) {
			this.ctx = ctx;
		}

		@Override
		protected Object createSnapshot() {
			return TOKEN; // non-null required by SnapshotJournal; carries no state
		}

		@Override
		protected void revertToSnapshot(Object snapshot) {
			// Evict whenever the context this journal was enlisted in aborts, nested or not (MOD-285).
			// The entry is keyed by that exact TransactionContext, so once it closes the entry is dead
			// regardless of depth: nothing can look it up again. A depth()==0 gate used to stand here to
			// protect against a second ParticipantJournal being built for a buffer mid-transaction, but a
			// nested context and its root are DIFFERENT cache keys, so dropping the nested entry cannot
			// disturb the root's. Pinned by NeoForgeTxnCacheTest#bufferReEnlistedAfterNestedAbort_*.
			NeoForgeTxn.evict(ctx);
		}

		@Override
		protected void onRootCommit(Object originalState) {
			// Fires once, after the root transaction committed and closed — always the root, always safe.
			NeoForgeTxn.evict(ctx);
		}
	}

	/** A NeoForge {@link SnapshotJournal} that delegates to a neutral {@link EnergyPort.Participant}. */
	private static final class ParticipantJournal extends SnapshotJournal<Long> {
		private final EnergyPort.Participant participant;

		ParticipantJournal(EnergyPort.Participant participant) {
			this.participant = participant;
		}

		@Override
		protected Long createSnapshot() {
			return participant.createSnapshot();
		}

		@Override
		protected void revertToSnapshot(Long snapshot) {
			participant.readSnapshot(snapshot);
		}

		@Override
		protected void onRootCommit(Long originalState) {
			participant.onFinalCommit();
		}
	}
}
