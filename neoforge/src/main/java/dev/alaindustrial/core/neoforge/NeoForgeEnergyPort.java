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
 * verified against 26.2.0.8-beta). This is the read/insert/extract view of a foreign or self-published
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
	 * Wrap a NeoForge {@link TransactionContext} as a neutral {@link EnergyPort.Txn}.
	 *
	 * <p><b>Caller must evict.</b> The wrapped handle is cached per-thread keyed by
	 * {@link TransactionContext} identity. Unlike Fabric, NeoForge's {@link TransactionContext} is a sealed
	 * interface exposing only {@code depth()} — it has no close-callback API (verified against
	 * 26.2.0.8-beta), so this method cannot auto-evict on close. Every caller MUST call
	 * {@link NeoForgeTxn#evict(TransactionContext)} after the transaction closes, or the thread-local cache
	 * leaks one entry per transaction. The {@link NeoForgeEnergyTransactions} SPI does this in a
	 * {@code finally} block; any other seam that calls {@code wrap} directly is bound by the same contract.
	 */
	public static Txn wrap(TransactionContext ctx) {
		return NeoForgeTxn.forContext(ctx);
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
		 * The shared {@link NeoForgeTxn} for {@code ctx}. NeoForge's {@link TransactionContext} exposes no
		 * close callback, so the handle is created for the root and evicted by
		 * {@link NeoForgeEnergyTransactions} after the transaction closes (single owner). For the common
		 * case there is exactly one root transaction in flight per move.
		 */
		public static NeoForgeTxn forContext(TransactionContext ctx) {
			return ACTIVE.get().computeIfAbsent(ctx, NeoForgeTxn::new);
		}

		/** Drop the cached handle for {@code ctx} once its transaction has closed. */
		public static void evict(TransactionContext ctx) {
			ACTIVE.get().remove(ctx);
		}

		public TransactionContext ctx() {
			return ctx;
		}

		@Override
		public void enlist(EnergyPort.Participant participant) {
			journals.computeIfAbsent(participant, ParticipantJournal::new).updateSnapshots(ctx);
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
