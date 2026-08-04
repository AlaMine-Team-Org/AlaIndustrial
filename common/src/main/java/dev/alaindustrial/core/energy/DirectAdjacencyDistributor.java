package dev.alaindustrial.core.energy;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Direct (cable-less) energy push: each tick a source pushes to its six direct neighbours, capped
 * by tier voltage (the per-tick transfer limit). This is the cable-bypass path — when a generator
 * sits directly next to a machine with no cable between them. The cabled path is owned by
 * {@link EnergyNetwork}, which is a different class despite the similar name.
 *
 * <p>Renamed from {@code EnergyNet} to avoid the long-standing confusion with {@link EnergyNetwork}
 * (the cable-graph distributor). The two are not duplicates and never were — the old {@code EnergyNet}
 * pushed to direct neighbours only, {@link EnergyNetwork} pulls across cable segments — but the
 * names were near-identical and every reader had to re-derive the difference from the javadoc.
 *
 * <p>MOD-022 Phase 2: runs entirely on the neutral energy abstraction — {@link EnergyPort} ports,
 * {@link EnergyLookup} for per-face resolution, {@link EnergyMover} for the transfer, and
 * {@link EnergyTransactions} for the commit. No loader energy API is referenced here; the loader-bound
 * lookup + transaction live behind those SPIs (Fabric installs Team Reborn / Fabric Transfer impls).
 */
public final class DirectAdjacencyDistributor {
	private DirectAdjacencyDistributor() {
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
			// Honor the SOURCE's per-face role (R-NRG-03, MOD-179): a face that cannot extract (the
			// FACING facade of generators, the wind/water mill's non-back faces, the BatteryBox's IN
			// faces) must not leak energy through the direct cable-less push either. Before MOD-179
			// only the target side was filtered (via supportsInsertion), so a battery box placed
			// against a role-NONE face still charged — bypassing the documented face contract.
			if (!source.energyRoleForFace(dir).canExtract()) {
				continue;
			}
			BlockPos np = pos.relative(dir);
			if (skipCables && level.getBlockEntity(np) instanceof CableBlockEntity) {
				continue;
			}
			EnergyPort target = lookup.find(level, np, dir.getOpposite());
			if (target == null || !target.supportsInsertion()) {
				continue;
			}
			long limit = srcTier.maxVoltage();
			// MOD-314: store → store is the ONE pairing this push must not perform at full tilt. Everything
			// else here (generator → machine, generator → store) stays an unconditional push, because
			// neither can flow back and so neither can oscillate.
			//
			// Two stores can. Left unregulated, this push is a pump that ignores fill levels entirely, and
			// a pump beats the regulator: with two boxes flush against each other and a cable looping back,
			// it keeps re-creating the very gradient the cabled cascade is trying to close, and every lap
			// pays MOD-021 loss. That circulates forever and drains both. The same gradient rule applied on
			// both paths removes the asymmetry that makes it possible — and keeps one story for the player,
			// rather than "flush against each other drains to empty, through a cable it levels out".
			if (source.isEnergyStorageSink() && isCascadeStore(level, np)) {
				if (!source.acceptsCascade()) {
					continue;
				}
				limit = CascadeShare.allowance(src.getAmount(), src.getCapacity(),
						target.getAmount(), target.getCapacity(), dev.alaindustrial.Config.cableBuffer, limit);
				if (limit <= 0) {
					continue;
				}
			}
			// Probe (dry run) how much the target will take, then commit exactly that. Sizing the move
			// before extracting avoids over-pulling from the source and losing the surplus — a generator
			// buffer has maxInsert == 0, so a refund could not put anything back (see EnergyMover).
			long movable = EnergyMover.probe(src, target, limit);
			if (movable <= 0) {
				continue;
			}
			EnergyTransactions.get().runCommitting(
					tx -> EnergyMover.commit(src, target, movable, tx));
		}
	}

	/**
	 * True if the block at {@code pos} is a store that takes part in the cascade (MOD-314). Deliberately
	 * the same {@code acceptsCascade} predicate the cabled path uses, so the two cannot drift into
	 * different answers for the same pair of blocks.
	 */
	private static boolean isCascadeStore(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof MachineBlockEntity mbe && mbe.acceptsCascade();
	}
}
