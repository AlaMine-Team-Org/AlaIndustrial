package dev.alaindustrial.core;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;

/**
 * Minimal energy transport: each tick a source pushes to its six neighbours, capped by tier
 * voltage (the per-tick transfer limit). Correctness over cleverness — no cached network graph
 * (Stage 1 scope). A receiver only ever accepts up to its own per-tick limit; any surplus offered
 * beyond that is simply not transferred — there is no overvoltage penalty.
 */
public final class EnergyNet {
	private EnergyNet() {
	}

	/** Push energy from {@code source} to every adjacent insertable storage, with tier limits. */
	public static void distribute(Level level, BlockPos pos, MachineBlockEntity source) {
		distribute(level, pos, source, false);
	}

	/**
	 * Push energy from {@code source} to adjacent insertable storages, with tier limits. When
	 * {@code skipCables} is true, cable neighbours are ignored — used by generators so the cabled
	 * path is handled exclusively by the {@link EnergyNetwork} (which pulls from the generator),
	 * while the direct, cable-less adjacency case (generator touching a machine) still works.
	 */
	public static void distribute(Level level, BlockPos pos, MachineBlockEntity source, boolean skipCables) {
		EnergyStorage src = source.getEnergyStorage();
		if (!src.supportsExtraction() || src.getAmount() <= 0) {
			return;
		}
		EnergyTier srcTier = source.getTier();
		for (Direction dir : Direction.values()) {
			BlockPos np = pos.relative(dir);
			if (skipCables && level.getBlockEntity(np) instanceof CableBlockEntity) {
				continue;
			}
			EnergyStorage target = EnergyStorage.SIDED.find(level, np, dir.getOpposite());
			if (target == null || !target.supportsInsertion()) {
				continue;
			}
			try (Transaction tx = Transaction.openOuter()) {
				EnergyStorageUtil.move(src, target, srcTier.maxVoltage(), tx);
				tx.commit();
			}
		}
	}

}
