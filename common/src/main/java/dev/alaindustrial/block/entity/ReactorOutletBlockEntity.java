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
 * The outlet's buffer (MOD-468, stage 3).
 *
 * <p><b>A socket, not a battery.</b> It holds one tick of HV and no more, so it behaves as a hole in
 * the wall that power passes through rather than as storage to hoard in — the same sizing decision, and
 * for the same reason, as the fluid inlet next door. The reactor's own buffer is where energy waits.
 *
 * <p><b>It never pulls; the controller pushes.</b> An outlet has no idea which room it belongs to, and
 * teaching it to go looking would mean a search from every socket every tick. The controller already
 * walks its own shell when it scans, so it collects its outlets there and tops them up from its buffer
 * — one owner, one direction, no back-reference to keep valid when a wall is rebuilt.
 */
public class ReactorOutletBlockEntity extends EnergyBlockEntity {

	public ReactorOutletBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.REACTOR_OUTLET_BE.get(), pos, state, EnergyTier.HV,
				Config.reactorOutletBuffer, 0L, EnergyTier.HV.maxVoltage());
	}

	/**
	 * Gives on every face. Unlike a machine there is no front to reserve: the whole point of the block
	 * is that whichever face happens to stick out of the wall is the one a cable can use.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.OUT;
	}

	/**
	 * Accepts a top-up from the controller. Returns what was actually taken, so the controller can put
	 * the remainder somewhere useful instead of discarding it.
	 */
	public long fillFromReactor(long offered) {
		long room = Math.min(offered, energy.getCapacity() - energy.getAmount());
		if (room <= 0) {
			return 0;
		}
		energy.setAmountUntracked(energy.getAmount() + room);
		setChanged();
		return room;
	}

	/** Nothing of its own to do — see the class doc. Never sleeps, because it is never woken either. */
	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		return 0;
	}
}
