package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.energy.EnergyPort;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric implementation of the platform-neutral {@link EnergyPort} (MOD-022 Phase 2): a thin adapter
 * over a Team Reborn {@link EnergyStorage}. Team Reborn is Fabric-only; the NeoForge side implements
 * {@code EnergyPort} over {@code EnergyHandler} instead.
 *
 * <p>Team Reborn's API is already {@code long}-based, so amounts pass through unchanged. The neutral
 * {@link EnergyPort.Txn} handle wraps Fabric's {@link TransactionContext} ({@link FabricTxn}); this
 * adapter unwraps it before delegating. The common {@link dev.alaindustrial.core.energy.EnergyBuffer} enlists
 * itself with the {@link EnergyPort.Txn}; {@link FabricTxn} bridges that enlistment onto a Fabric
 * {@link SnapshotParticipant} so the buffer's snapshot / final-commit hooks run on Fabric's native
 * transaction journal.
 */
public final class FabricEnergyPort implements EnergyPort {
	private final EnergyStorage delegate;

	public FabricEnergyPort(EnergyStorage delegate) {
		this.delegate = delegate;
	}

	/** Wrap a Team Reborn storage as a neutral {@link EnergyPort}, or {@code null} for a {@code null} storage. */
	public static EnergyPort of(EnergyStorage storage) {
		return storage == null ? null : new FabricEnergyPort(storage);
	}

	@Override
	public long insert(long maxAmount, Txn txn) {
		return delegate.insert(maxAmount, unwrap(txn));
	}

	@Override
	public long extract(long maxAmount, Txn txn) {
		return delegate.extract(maxAmount, unwrap(txn));
	}

	@Override
	public long getAmount() {
		return delegate.getAmount();
	}

	@Override
	public long getCapacity() {
		return delegate.getCapacity();
	}

	@Override
	public boolean supportsInsertion() {
		return delegate.supportsInsertion();
	}

	@Override
	public boolean supportsExtraction() {
		return delegate.supportsExtraction();
	}

	private static TransactionContext unwrap(Txn txn) {
		if (txn instanceof FabricTxn ft) {
			return ft.ctx();
		}
		throw new IllegalArgumentException("Expected a Fabric transaction handle, got: " + txn);
	}

	/**
	 * Fabric-side {@link EnergyPort.Txn} carrying the loader's {@link TransactionContext}. Bridges the
	 * neutral {@link EnergyPort.Participant} enlistment onto Fabric {@link SnapshotParticipant}s: each
	 * distinct participant gets one bridge for this transaction's lifetime (keyed by identity), so
	 * repeated {@code enlist} calls dedupe through {@code updateSnapshots} exactly as a native buffer's
	 * repeated {@code insert}/{@code extract} in one transaction would.
	 *
	 * <p><b>One handle per native transaction.</b> A single {@code FabricTxn} must be reused for the
	 * whole life of a given {@link TransactionContext} so its {@code bridges} map (and therefore the
	 * snapshot dedupe) is shared across every operation in that transaction. Callers that already own the
	 * transaction ({@link FabricEnergyTransactions}) create one directly; the SIDED capability seam,
	 * which is re-entered per operation with only the {@code TransactionContext}, resolves the shared
	 * handle through {@link #forContext} instead of wrapping a fresh one each time.
	 */
	public static final class FabricTxn implements EnergyPort.Txn {
		/** Transactions are thread-confined in Fabric, so a plain per-thread identity cache is safe. */
		private static final ThreadLocal<Map<TransactionContext, FabricTxn>> ACTIVE =
				ThreadLocal.withInitial(IdentityHashMap::new);

		private final TransactionContext ctx;
		private final Map<EnergyPort.Participant, ParticipantBridge> bridges = new IdentityHashMap<>();

		public FabricTxn(TransactionContext ctx) {
			this.ctx = ctx;
		}

		/**
		 * The shared {@link FabricTxn} for {@code ctx}, creating (and registering an auto-eviction close
		 * callback for) it on first use. Use this from re-entrant seams that only have the native
		 * {@link TransactionContext} in hand, so all operations in one transaction share one handle.
		 *
		 * <p><b>Eviction contract.</b> The handle is cached per-thread, keyed by {@link TransactionContext}
		 * identity, and auto-evicted by the {@code addCloseCallback} registered here when the transaction
		 * closes. This is safe because Fabric fires the close callback as part of tearing the transaction
		 * down, after its try-with-resources body has exited — so no in-flight operation ever observes an
		 * evicted handle, and a later transaction reusing the same identity gets a fresh handle. The code
		 * relies on Fabric's guarantee that the close callback runs once per transaction on close; if that
		 * contract changed (e.g. a callback firing mid-transaction), this caching would need revisiting.
		 */
		public static FabricTxn forContext(TransactionContext ctx) {
			return ACTIVE.get().computeIfAbsent(ctx, c -> {
				FabricTxn handle = new FabricTxn(c);
				c.addCloseCallback((closed, result) -> ACTIVE.get().remove(closed));
				return handle;
			});
		}

		public TransactionContext ctx() {
			return ctx;
		}

		@Override
		public void enlist(EnergyPort.Participant participant) {
			bridges.computeIfAbsent(participant, ParticipantBridge::new).updateSnapshots(ctx);
		}
	}

	/** A Fabric {@link SnapshotParticipant} that delegates to a neutral {@link EnergyPort.Participant}. */
	private static final class ParticipantBridge extends SnapshotParticipant<Long> {
		private final EnergyPort.Participant participant;

		ParticipantBridge(EnergyPort.Participant participant) {
			this.participant = participant;
		}

		@Override
		protected Long createSnapshot() {
			return participant.createSnapshot();
		}

		@Override
		protected void readSnapshot(Long snapshot) {
			participant.readSnapshot(snapshot);
		}

		@Override
		protected void onFinalCommit() {
			participant.onFinalCommit();
		}
	}

	/**
	 * The neutral {@link EnergyPort.Txn} for a Fabric {@link TransactionContext}. Resolves the single
	 * shared {@link FabricTxn} for that transaction (see {@link FabricTxn#forContext}) so every entry
	 * path — the {@link FabricEnergyTransactions} owner and the SIDED capability seam — shares one
	 * snapshot-dedupe map for the transaction's lifetime.
	 */
	public static Txn wrap(TransactionContext ctx) {
		return FabricTxn.forContext(ctx);
	}
}
