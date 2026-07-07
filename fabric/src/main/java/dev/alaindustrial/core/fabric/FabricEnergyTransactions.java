package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.EnergyPort;
import dev.alaindustrial.core.EnergyTransactions;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/**
 * Fabric implementation of the neutral {@link EnergyTransactions} SPI (MOD-022 Phase 2). Opens a Fabric
 * {@code Transaction.openOuter()} and either commits or aborts around the caller's body, wrapping the
 * {@code TransactionContext} as a neutral {@link EnergyPort.Txn} via {@link FabricEnergyPort#wrap}.
 *
 * <p>This is the per-loader transaction seam: common transport code decides <em>what</em> to move; this
 * class owns <em>how</em> the Fabric transaction is opened and committed.
 */
public final class FabricEnergyTransactions implements EnergyTransactions {

	@Override
	public void runCommitting(Consumer<EnergyPort.Txn> body) {
		try (Transaction tx = Transaction.openOuter()) {
			body.accept(FabricEnergyPort.wrap(tx));
			tx.commit();
		}
	}

	@Override
	public <T> T simulate(Function<EnergyPort.Txn, T> body) {
		try (Transaction tx = Transaction.openOuter()) {
			T result = body.apply(FabricEnergyPort.wrap(tx));
			tx.abort();
			return result;
		}
	}
}
