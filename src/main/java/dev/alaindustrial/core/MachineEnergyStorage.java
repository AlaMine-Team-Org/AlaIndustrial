package dev.alaindustrial.core;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * The energy buffer backing every machine. Wraps Team Reborn's {@link SimpleEnergyStorage}
 * (transaction-safe) and notifies its owning block entity whenever a committed transaction
 * changes the stored amount, so persistence + GUI sync stay correct.
 */
public class MachineEnergyStorage extends SimpleEnergyStorage {
	private final MachineBlockEntity owner;

	public MachineEnergyStorage(long capacity, long maxInsert, long maxExtract, MachineBlockEntity owner) {
		super(capacity, maxInsert, maxExtract);
		this.owner = owner;
	}

	@Override
	protected void onFinalCommit() {
		super.onFinalCommit();
		owner.markDirtyAndSync();
		// External energy delivery/extraction (the network inserting power, a generator pushing) must
		// wake an idle consumer so it resumes on the next tick (R-29). Internal drain mutates the
		// amount field directly (no transaction), so it never spuriously wakes a working machine.
		owner.wake();
	}
}
