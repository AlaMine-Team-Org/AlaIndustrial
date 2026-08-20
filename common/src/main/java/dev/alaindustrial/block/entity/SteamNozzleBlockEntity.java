package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The nozzle's tank and its plume (MOD-468, stage 3).
 *
 * <p>Accepts steam, destroys it at {@code Config.reactorNozzleVentRate} mB a tick, and draws a puff
 * for each tick it actually vented. Nothing here can be extracted from: steam that reaches the nozzle
 * is gone, which is the contract the whole loop is balanced against.
 */
public class SteamNozzleBlockEntity extends BlockEntity implements FluidPortHost {

	/**
	 * Insert-only, and only steam. Sized to a handful of ticks so a line that briefly outruns the vent
	 * rate does not stall the columns behind it — but far too small to be used as storage, which is
	 * what would happen if a player could park steam here waiting for stage 5's turbine.
	 */
	public final FluidTank tank = new FluidTank(Config.reactorNozzleBuffer,
			fluid -> fluid.is(ModContent.STEAM.get()), fluid -> false, this::setChanged) {
		/**
		 * One-way, and it says so. {@code FluidTank} answers both questions with "capacity > 0", which
		 * is true of every tank and tells a pipe nothing — the real restriction lives in the predicates
		 * above, where nothing can read it. An exhaust that admits it only ever takes is what lets a
		 * pipe run to it without the player wrenching a fitting that has no second option.
		 */
		@Override
		public boolean supportsExtraction() {
			return false;
		}
	};

	public SteamNozzleBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.STEAM_NOZZLE_BE.get(), pos, state);
	}

	@Override
	public FluidPort fluidPort(Direction side) {
		// Every face but the mouth: a pipe joins the body, never the opening. Publishing the mouth too
		// would let a player run the exhaust line into the very block the steam is released into, and
		// the nozzle would quietly feed itself.
		return side == getBlockState().getValue(SteamNozzleBlock.FACING) ? null : tank;
	}

	/** Vents what it can and puffs if it did. Silent and still when there is nothing to release. */
	public void tick(Level level, BlockPos pos, BlockState state) {
		if (tank.amount <= 0) {
			return;
		}
		Direction facing = state.getValue(SteamNozzleBlock.FACING);
		BlockPos mouth = pos.relative(facing);
		// Steam needs somewhere to go. A nozzle facing a wall backs up — the line behind it fills, the
		// columns stop boiling, and the reactor's own heat gauge reports the mistake.
		if (!level.getBlockState(mouth).canBeReplaced()) {
			return;
		}
		long vented = Math.min(tank.amount, Config.reactorNozzleVentRate);
		tank.amount -= vented;
		if (tank.amount == 0) {
			tank.fluid = FluidHolder.EMPTY;
		}
		setChanged();
		if (level instanceof ServerLevel server) {
			// Scaled to the flow so a trickle looks like a trickle: the plume is the only readout the
			// exhaust has, and a fixed-size puff would report a stalled loop as a healthy one.
			int puffs = (int) Math.max(1, Math.min(8, vented * 8 / Math.max(1, Config.reactorNozzleVentRate)));
			server.sendParticles(ParticleTypes.CLOUD,
					pos.getX() + 0.5 + facing.getStepX() * 0.6,
					pos.getY() + 0.5 + facing.getStepY() * 0.6,
					pos.getZ() + 0.5 + facing.getStepZ() * 0.6,
					puffs, 0.12, 0.12, 0.12, 0.02);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("SteamMb", tank.amount);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		tank.amount = Math.max(0, Math.min(tank.capacity, input.getLongOr("SteamMb", 0)));
		tank.fluid = tank.amount > 0 ? FluidHolder.of(ModContent.STEAM.get()) : FluidHolder.EMPTY;
	}
}
