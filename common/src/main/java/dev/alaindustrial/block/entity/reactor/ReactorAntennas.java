package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.entity.ReactorAntennaBlockEntity;
import dev.alaindustrial.core.energy.EnergyBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * A controller's antennas (MOD-941): which ones it serves this tick, and the serving itself — the link and the
 * upkeep, topped up from the reactor's buffer when an antenna is short.
 *
 * <p>An antenna whose cable keeps it full asks nothing of the reactor; one that is short is topped up first, so a
 * reactor with charge keeps its own antenna on the air even with no cable. The controller serves them before its
 * outlets: watching the reactor comes before selling its power.
 *
 * <p>A sealed room's antennas are the ones its shell walk found ({@link ReactorRoom#antennas}); without a sealed room
 * (bare, or a room still being built) an antenna counts when it touches the controller with a face.
 */
public final class ReactorAntennas {

	/** The antennas served on the last tick, rebuilt every tick; runtime only. */
	private final List<BlockPos> served = new ArrayList<>();

	/** Serves this tick's antennas of the controller at {@code pos}, paying any shortfall out of {@code reactor}. */
	public void serve(Level level, BlockPos pos, ReactorRoom room, EnergyBuffer reactor) {
		served.clear();
		if (room.isSealed()) {
			for (BlockPos at : room.antennas()) {
				serveOne(level, pos, at, reactor);
			}
			return;
		}
		for (Direction face : Direction.values()) {
			serveOne(level, pos, pos.relative(face), reactor);
		}
	}

	private void serveOne(Level level, BlockPos pos, BlockPos at, EnergyBuffer reactor) {
		if (!(level.getBlockEntity(at) instanceof ReactorAntennaBlockEntity antenna)) {
			return;
		}
		long shortfall = antenna.shortfall();
		if (shortfall > 0 && reactor.getAmount() > 0) {
			long taken = antenna.topUpFromReactor(Math.min(shortfall, reactor.getAmount()));
			reactor.setAmountUntracked(reactor.getAmount() - taken);
		}
		antenna.serve(level, pos);
		served.add(at.immutable());
	}

	/** The antennas served on the last tick — the ones a tablet may hear. */
	public List<BlockPos> served() {
		return served;
	}
}
