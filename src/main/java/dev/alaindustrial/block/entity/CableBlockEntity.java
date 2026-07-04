package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV copper cable: a transport segment of a logical {@link dev.alaindustrial.core.EnergyNetwork}.
 * The cable no longer pushes energy itself — the network owns transport and ticks once per server
 * tick via {@link NetworkManager}. The cable's job is lifecycle: it registers itself with the
 * {@link NetworkManager} on first server tick (after its level + neighbours are loaded) and
 * unregisters on {@link #setRemoved()}. Cable transport is a throughput limit owned by the network
 * (tier packetCap per consumer), not an EU-destroying toll — see MOD-009.
 */
public class CableBlockEntity extends MachineBlockEntity {
	/** Whether this cable has been registered with its level's {@link NetworkManager}. */
	private boolean registered;

	public CableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.COPPER_CABLE, pos, state, EnergyTier.LV, 0,
				Config.cableBuffer, EnergyTier.LV.maxVoltage(), EnergyTier.LV.maxVoltage());
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// Register lazily on the first server tick: by now the level is set and the chunk (with its
		// neighbours) is loaded, so endpoint discovery and cross-chunk unions are correct. This is the
		// robust "on load" path that also uniformly covers world/chunk load (not just block placement).
		ensureRegistered();
		// Transport is owned by the network tick, not the cable; the per-cable check above is trivial,
		// so cables stay awake (return 0) rather than manage a sleep timer (R-29).
		return 0;
	}

	/**
	 * Registers this cable with its level's {@link NetworkManager} if not already done, keeping
	 * {@link #registered} in lockstep with the actual registration so {@link #setRemoved()} can
	 * reliably unregister it later. {@link NetworkManager#register} is itself idempotent, but that
	 * alone isn't enough — the flag must be set here (the same call site) or a caller that registers
	 * without setting it (e.g. wanting the network's awake state synchronously) would leave the
	 * network permanently unaware that this cable was ever removed if it's broken before its first
	 * {@link #onServerTick}. Called both from the tick path above and eagerly from
	 * {@link dev.alaindustrial.block.CableBlock#setPlacedBy}.
	 */
	public void ensureRegistered() {
		if (!registered && level instanceof ServerLevel) {
			NetworkManager.register(this);
			registered = true;
		}
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel) {
			NetworkManager.unregister(this);
			registered = false;
		}
		super.setRemoved();
	}
}
