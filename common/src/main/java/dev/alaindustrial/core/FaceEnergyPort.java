package dev.alaindustrial.core;

/**
 * A per-face view of a machine's energy buffer that enforces the face's {@link EnergyRole}
 * (R-NRG-03): an {@code IN} face accepts but never emits, an {@code OUT} face emits but never
 * accepts. Delegates the actual amount/capacity and the underlying buffer's own limits.
 *
 * <p>Platform-neutral (MOD-022 Phase 2): wraps a common {@link EnergyPort}, so the same face-role
 * logic applies whether the delegate is a Fabric Team Reborn buffer or a NeoForge {@code EnergyHandler}
 * adapter underneath.
 */
public final class FaceEnergyPort implements EnergyPort {
	private final EnergyPort delegate;
	private final EnergyRole role;

	public FaceEnergyPort(EnergyPort delegate, EnergyRole role) {
		this.delegate = delegate;
		this.role = role;
	}

	@Override
	public long insert(long maxAmount, Txn txn) {
		return role.canInsert() ? delegate.insert(maxAmount, txn) : 0;
	}

	@Override
	public long extract(long maxAmount, Txn txn) {
		return role.canExtract() ? delegate.extract(maxAmount, txn) : 0;
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
		// Role-only, deliberately NOT delegate.supportsInsertion(): the predicate is a capability check,
		// not a state check. It must stay consistent with insert() above, which gates on the role and then
		// lets the buffer return 0 when full. Delegating would re-answer the same capability question and,
		// on an adapter whose supports*() ever peeked at buffer state, could return false while insert()
		// still works — a supports/insert contradiction that confuses callers trusting the predicate.
		return role.canInsert();
	}

	@Override
	public boolean supportsExtraction() {
		return role.canExtract();
	}
}
