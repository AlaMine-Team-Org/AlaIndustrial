package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyNet;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Common base for every EU producer. Generators never accept external energy; each tick they ask
 * the subclass how much EU to {@link #produce}, add it to the buffer (capped, use-it-or-lose-it),
 * then push to neighbours. Subclasses only express their generation rule (fuel, sunlight, ...).
 */
public abstract class AbstractGeneratorBlockEntity extends MachineBlockEntity {
	protected AbstractGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, int slots, long capacity, long maxExtract) {
		super(type, pos, state, tier, slots, capacity, 0L, maxExtract);
	}

	/** EU generated this tick (before buffer capping). Subclasses may also update progress here. */
	protected abstract int produce(Level level, BlockPos pos, BlockState state);

	/**
	 * Generators are producers: every face but FACING emits, none accepts (R-NRG-03). FACING is
	 * energy-inert — no cable/hopper connects through the front (D-FACING).
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.OUT);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int made = produce(level, pos, state);
		made = (made > 0) ? Math.max(1, Math.round(made * Config.globalEuRateMultiplier)) : 0;
		boolean changed = false;
		if (made > 0) {
			long room = energy.getCapacity() - energy.amount;
			if (room > 0) {
				energy.amount += Math.min(room, (long) made);
				changed = true;
			}
		}
		// Direct push to a cable-less adjacent machine. The cabled path is owned by the EnergyNetwork
		// (which pulls from this generator's buffer), so skip cable neighbours to avoid double delivery.
		EnergyNet.distribute(level, pos, this, true);
		updateLit(made > 0);
		if (changed) {
			setChanged();
		}
		// Generators never sleep: production depends on world state (sunlight, fuel, lava) with no
		// wake event, and they must keep pushing to neighbours/network every tick (R-29).
		return 0;
	}
}
