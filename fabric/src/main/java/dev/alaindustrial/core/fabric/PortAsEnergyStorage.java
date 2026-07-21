package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.energy.EnergyPort;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

/**
 * Reverse adapter (MOD-022 Phase 2): exposes a neutral {@link EnergyPort} as a Team Reborn
 * {@link EnergyStorage}, so a machine's platform-neutral buffer can be published through Fabric's
 * {@code EnergyStorage.SIDED} capability lookup. The Fabric-side capability contract is the per-loader
 * binding seam; the buffer itself is loader-neutral.
 *
 * <p>Team Reborn passes a {@link TransactionContext}; this adapter wraps it as a neutral
 * {@link EnergyPort.Txn} ({@link FabricEnergyPort#wrap}) so the buffer's transaction enlistment reaches
 * Fabric's native snapshot journal. Both APIs are {@code long}-based, so amounts pass through unchanged.
 */
public final class PortAsEnergyStorage implements EnergyStorage {
	private final EnergyPort port;

	private PortAsEnergyStorage(EnergyPort port) {
		this.port = port;
	}

	/** Wrap a neutral {@link EnergyPort} as a Team Reborn {@link EnergyStorage}, or {@code null} for none. */
	public static EnergyStorage of(EnergyPort port) {
		return port == null ? null : new PortAsEnergyStorage(port);
	}

	@Override
	public long insert(long maxAmount, TransactionContext transaction) {
		return port.insert(maxAmount, FabricEnergyPort.wrap(transaction));
	}

	@Override
	public long extract(long maxAmount, TransactionContext transaction) {
		return port.extract(maxAmount, FabricEnergyPort.wrap(transaction));
	}

	@Override
	public long getAmount() {
		return port.getAmount();
	}

	@Override
	public long getCapacity() {
		return port.getCapacity();
	}

	@Override
	public boolean supportsInsertion() {
		return port.supportsInsertion();
	}

	@Override
	public boolean supportsExtraction() {
		return port.supportsExtraction();
	}
}
