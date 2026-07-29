package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Demand-driven LV heat source. It spends EU only through {@link #consumeHeatTick()}, called by a
 * Vulcanizer that is about to advance one useful processing tick.
 */
public final class ElectricHeaterBlockEntity extends MachineBlockEntity {
	/**
	 * Two ticks keep LIT stable regardless of whether this BE or the Vulcanizer is ticked first.
	 * Each successful consumer call renews the lease.
	 */
	private int litLeaseTicks;

	public ElectricHeaterBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ELECTRIC_HEATER_BE.get(), pos, state, EnergyTier.LV, 0,
				Config.electricHeaterBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	public boolean canSupplyHeatTick() {
		return energy.amount >= Config.electricHeaterEuPerTickEffective();
	}

	/**
	 * Atomically pays for one useful heat tick. A competing consumer can make this return false after
	 * discovery, so the Vulcanizer must only advance when it returns true.
	 */
	public boolean consumeHeatTick() {
		int cost = Config.electricHeaterEuPerTickEffective();
		if (energy.amount < cost) {
			return false;
		}
		energy.amount -= cost;
		litLeaseTicks = 2;
		updateLit(true);
		setChanged();
		wake();
		return true;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		boolean active = litLeaseTicks > 0;
		updateLit(active);
		if (active) {
			litLeaseTicks--;
			return 0;
		}
		/*
		 * Energy delivery wakes this BE through EnergyBuffer's commit hook, but it does not produce a
		 * neighbour block update. Wake the consumer above explicitly so a Vulcanizer that previously
		 * went to sleep on NO_HEAT can discover the newly powered heater on the next tick instead of
		 * waiting for its 40-tick safety poll. No EU is spent here: the consumer pays atomically through
		 * consumeHeatTick() only after all of its own work predicates pass.
		 */
		if (canSupplyHeatTick()
				&& level.getBlockEntity(pos.above()) instanceof VulcanizerBlockEntity vulcanizer) {
			vulcanizer.onHeatNeighbourChanged();
		}
		return IDLE_SLEEP_TICKS;
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}
}
