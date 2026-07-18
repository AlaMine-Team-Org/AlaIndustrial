package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.EnergyPort;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.neoforge.NeoForgeEnergyPort.NeoForgeTxn;
import java.util.function.Consumer;
import java.util.function.Function;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge implementation of the neutral {@link EnergyTransactions} SPI (MOD-022 Phase 2). Opens a
 * NeoForge {@code Transaction.openRoot()} and either commits (verified pattern
 * {@code try (Transaction tx = Transaction.openRoot()) { ...; tx.commit(); }}) or lets try-with-resources
 * close the transaction without committing (= rollback / simulate). The {@code TransactionContext} is
 * wrapped as a neutral {@link EnergyPort.Txn}; the cached bridge handle is evicted after close, since
 * NeoForge's {@code TransactionContext} exposes no close callback.
 *
 * <p><b>Wiring status:</b> installed and live on NeoForge — the energy network, item pipes and machine
 * BlockEntities all tick and open transactions through this at runtime (e.g. the item-pipe transfer in
 * {@code ItemNetwork.tick} via {@code ItemMover.move → runCommitting}).
 */
public final class NeoForgeEnergyTransactions implements EnergyTransactions {

	@Override
	public void runCommitting(Consumer<EnergyPort.Txn> body) {
		Transaction tx = Transaction.openRoot();
		try {
			body.accept(NeoForgeEnergyPort.wrap(tx));
			tx.commit();
		} finally {
			tx.close();
			NeoForgeTxn.evict(tx);
		}
	}

	@Override
	public <T> T simulate(Function<EnergyPort.Txn, T> body) {
		Transaction tx = Transaction.openRoot();
		try {
			// No commit: closing the transaction rolls everything back (dry run).
			return body.apply(NeoForgeEnergyPort.wrap(tx));
		} finally {
			tx.close();
			NeoForgeTxn.evict(tx);
		}
	}
}
