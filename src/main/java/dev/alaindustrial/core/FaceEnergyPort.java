package dev.alaindustrial.core;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

/**
 * A per-face view of a machine's energy buffer that enforces the face's {@link EnergyRole}
 * (R-NRG-03): an {@code IN} face accepts but never emits, an {@code OUT} face emits but never
 * accepts. Delegates the actual amount/capacity and the underlying buffer's own limits.
 */
public final class FaceEnergyPort implements EnergyStorage {
	private final EnergyStorage delegate;
	private final EnergyRole role;

	public FaceEnergyPort(EnergyStorage delegate, EnergyRole role) {
		this.delegate = delegate;
		this.role = role;
	}

	@Override
	public long insert(long maxAmount, TransactionContext transaction) {
		return role.canInsert() ? delegate.insert(maxAmount, transaction) : 0;
	}

	@Override
	public long extract(long maxAmount, TransactionContext transaction) {
		return role.canExtract() ? delegate.extract(maxAmount, transaction) : 0;
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
		return role.canInsert() && delegate.supportsInsertion();
	}

	@Override
	public boolean supportsExtraction() {
		return role.canExtract() && delegate.supportsExtraction();
	}
}
