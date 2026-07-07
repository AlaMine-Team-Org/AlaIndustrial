package dev.alaindustrial.core;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Minimal energy transport: each tick a source pushes to its six neighbours, capped by tier
 * voltage (the per-tick transfer limit). Correctness over cleverness — no cached network graph
 * (Stage 1 scope). A receiver only ever accepts up to its own per-tick limit; any surplus offered
 * beyond that is simply not transferred — there is no overvoltage penalty.
 *
 * <p>MOD-022 Phase 2: runs entirely on the neutral energy abstraction — {@link EnergyPort} ports,
 * {@link EnergyLookup} for per-face resolution, {@link EnergyMover} for the transfer, and
 * {@link EnergyTransactions} for the commit. No loader energy API is referenced here; the loader-bound
 * lookup + transaction live behind those SPIs (Fabric installs Team Reborn / Fabric Transfer impls).
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
		EnergyPort src = source.getEnergyStorage();
		if (!src.supportsExtraction() || src.getAmount() <= 0) {
			return;
		}
		EnergyTier srcTier = source.getTier();
		EnergyLookup lookup = EnergyLookup.get();
		for (Direction dir : Direction.values()) {
			BlockPos np = pos.relative(dir);
			if (skipCables && level.getBlockEntity(np) instanceof CableBlockEntity) {
				continue;
			}
			EnergyPort target = lookup.find(level, np, dir.getOpposite());
			if (target == null || !target.supportsInsertion()) {
				continue;
			}
			// Probe (dry run) how much the target will take, then commit exactly that. Sizing the move
			// before extracting avoids over-pulling from the source and losing the surplus — a generator
			// buffer has maxInsert == 0, so a refund could not put anything back (see EnergyMover).
			long movable = EnergyMover.probe(src, target, srcTier.maxVoltage());
			if (movable <= 0) {
				continue;
			}
			EnergyTransactions.get().runCommitting(
					tx -> EnergyMover.commit(src, target, movable, tx));
		}
	}

}
