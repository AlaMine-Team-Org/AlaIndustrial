package dev.alaindustrial.core.energy;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Platform-neutral entry point for opening energy transactions (MOD-022 Phase 2). The two loaders open
 * and commit transactions with different native APIs — Fabric {@code Transaction.openOuter()} /
 * {@code commit()} / {@code abort()} versus NeoForge {@code Transaction.openRoot()} / {@code commit()}
 * / try-with-resources rollback — so common transport code never opens one directly. Instead it asks the
 * loader-provided implementation to open a transaction, hands it back an {@link EnergyPort.Txn}, and the
 * implementation commits or aborts around the callback.
 *
 * <p>The active implementation is installed once at mod init by each loader's entrypoint via
 * {@link #install(EnergyTransactions)}; common code reaches it through {@link #get()}.
 */
public interface EnergyTransactions {

	/**
	 * Open a transaction, run {@code body} against its {@link EnergyPort.Txn}, then <b>commit</b>. Any
	 * energy inserted/extracted through the handle is applied atomically when {@code body} returns
	 * normally; a thrown exception rolls the transaction back.
	 */
	void runCommitting(Consumer<EnergyPort.Txn> body);

	/**
	 * Open a transaction, run {@code body} against its {@link EnergyPort.Txn}, then <b>abort</b>
	 * (roll back) — a dry run. Returns whatever {@code body} computed. Nothing {@code body} moved is
	 * persisted; use this to probe extractable/insertable amounts without committing.
	 */
	<T> T simulate(Function<EnergyPort.Txn, T> body);

	// --- service locator (installed by the loader entrypoint) ---

	EnergyTransactions[] INSTANCE = new EnergyTransactions[1];

	/** Install the loader's implementation (called once from the loader entrypoint at mod init). */
	static void install(EnergyTransactions impl) {
		INSTANCE[0] = impl;
	}

	/** The installed loader implementation. Throws if the entrypoint has not installed one yet. */
	static EnergyTransactions get() {
		EnergyTransactions impl = INSTANCE[0];
		if (impl == null) {
			throw new IllegalStateException(
					"EnergyTransactions not installed — the loader entrypoint must call install() at init");
		}
		return impl;
	}
}
