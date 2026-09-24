package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.GeyserBaseParticleOptions;
import net.minecraft.core.particles.GeyserParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The nozzle's tank and its plume (MOD-468, stage 3).
 *
 * <p>Accepts steam, destroys it at {@code Config.reactorNozzleVentRate} mB a tick, and shows it: a geyser burst
 * sized to what it vented and a hiss while it keeps venting (MOD-662). Nothing here can be extracted from: steam that reaches the nozzle
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

	/**
	 * Ticks between the hiss clips while the nozzle keeps venting (MOD-662). The vanilla geyser clips it plays run
	 * six to thirteen seconds, so a new one every five seconds overlaps the last and the ear hears one steady hiss.
	 */
	static final int VENT_SOUND_PERIOD_TICKS = 100;

	/**
	 * The shortest gap between two starts of the hiss. A line that feeds in pulses switches the nozzle between
	 * venting and idle every few ticks, and without this floor every restart would stack another clip on the last.
	 */
	static final int VENT_SOUND_MIN_GAP_TICKS = 20;

	/** Whether the nozzle vented on its previous tick — the edge the hiss starts on. Not persisted. */
	private boolean wasVenting;

	/** Game time the hiss last started, or {@link Long#MIN_VALUE} if never since load. Not persisted. */
	private long lastSoundAt = Long.MIN_VALUE;

	/** Steam vented since the last burst was drawn, in mB. */
	private long plumeVented;

	/** Ticks into the current burst window; a burst is drawn when it reaches the configured interval. */
	private int plumeTicks;

	/** Hiss clips started since load. A live counter for the server side, which cannot hear what it plays. */
	private int ventSounds;

	/** Geyser bursts drawn since load, and the particle types of the last one — the plume's only server-side trace. */
	private int plumeBursts;
	private java.util.List<ParticleType<?>> lastBurstTypes = java.util.List.of();

	/** Vents what it can, then draws and voices it. Silent and still when there is nothing to release. */
	public void tick(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(SteamNozzleBlock.FACING);
		long vented = vent(level, pos.relative(facing));
		if (level instanceof ServerLevel server) {
			hiss(server, pos, vented > 0);
			plume(server, pos, facing, vented);
		}
	}

	/** Destroys up to one tick's worth of steam into the air in front of the mouth. Returns how much. */
	private long vent(Level level, BlockPos mouth) {
		if (tank.amount <= 0) {
			return 0;
		}
		// Steam needs somewhere to go. A nozzle facing a wall backs up — the line behind it fills, the
		// columns stop boiling, and the reactor's own heat gauge reports the mistake.
		if (!level.getBlockState(mouth).canBeReplaced()) {
			return 0;
		}
		long vented = Math.min(tank.amount, Config.reactorNozzleVentRate);
		tank.amount -= vented;
		if (tank.amount == 0) {
			tank.fluid = FluidHolder.EMPTY;
		}
		setChanged();
		return vented;
	}

	/**
	 * Starts the hiss when venting begins and again every {@link #VENT_SOUND_PERIOD_TICKS} while it goes on.
	 *
	 * <p>Our own event, {@code alaindustrial:block.steam_nozzle.vent}, over the vanilla geyser clips: vanilla's own
	 * event for them carries no subtitle, and a sound a deaf player cannot see is a readout half the players lack.
	 * Nothing is copied — {@code sounds.json} names Minecraft's files. {@code null} as the player so the nearest
	 * player hears it too: a block entity has no client-side prediction to double it.
	 */
	private void hiss(ServerLevel level, BlockPos pos, boolean venting) {
		if (venting) {
			long now = level.getGameTime();
			long since = lastSoundAt == Long.MIN_VALUE ? Long.MAX_VALUE : now - lastSoundAt;
			boolean start = wasVenting ? since >= VENT_SOUND_PERIOD_TICKS : since >= VENT_SOUND_MIN_GAP_TICKS;
			if (start) {
				lastSoundAt = now;
				ventSounds++;
				level.playSound(null, pos, ModSounds.STEAM_NOZZLE_VENT.get(), SoundSource.BLOCKS,
						wasVenting ? 0.4f : 0.5f, 0.9f + level.getRandom().nextFloat() * 0.2f);
			}
		}
		wasVenting = venting;
	}

	/**
	 * A geyser burst for the steam vented over the last few ticks, scaled to it.
	 *
	 * <p>Scaled to the flow so a trickle looks like a trickle: the plume is the only readout the exhaust has, and a
	 * fixed-size burst would report a stalled loop as a healthy one. Batched over
	 * {@code Config.reactorNozzlePlumeIntervalTicks} because a geyser particle lives a second or more — one a tick
	 * would pile into a white wall. A nozzle facing up throws the tall column vanilla geysers throw; one facing
	 * sideways cannot, since those particles only ever rise, so it breathes puffs out of its mouth instead.
	 *
	 * <p>Only the 9-argument {@code sendParticles}: it is the one overload Minecraft 26.2 shares with 26.3.
	 */
	private void plume(ServerLevel level, BlockPos pos, Direction facing, long vented) {
		plumeVented += vented;
		if (plumeVented <= 0) {
			plumeTicks = 0;
			return;
		}
		int interval = Math.max(1, Config.reactorNozzlePlumeIntervalTicks);
		if (++plumeTicks < interval) {
			return;
		}
		float share = Math.min(1f, (float) plumeVented / ((long) interval * Math.max(1, Config.reactorNozzleVentRate)));
		plumeVented = 0;
		plumeTicks = 0;
		plumeBursts++;
		if (facing == Direction.UP) {
			double x = pos.getX() + 0.5;
			double y = pos.getY() + 1.0;
			double z = pos.getZ() + 0.5;
			level.sendParticles(new GeyserParticleOptions(ParticleTypes.GEYSER_PLUME, 1), x, y, z,
					1 + Math.round(2 * share), 0.1, 0.0, 0.1, 0.0);
			level.sendParticles(new GeyserBaseParticleOptions(ParticleTypes.GEYSER_BASE, 1, 1.5f), x, y, z,
					2, 0.15, 0.0, 0.15, 0.0);
			lastBurstTypes = java.util.List.of(ParticleTypes.GEYSER_PLUME, ParticleTypes.GEYSER_BASE);
		} else {
			level.sendParticles(new GeyserBaseParticleOptions(ParticleTypes.GEYSER_POOF, 0, 1f + share),
					pos.getX() + 0.5 + facing.getStepX() * 0.6,
					pos.getY() + 0.5 + facing.getStepY() * 0.6,
					pos.getZ() + 0.5 + facing.getStepZ() * 0.6,
					2 + Math.round(4 * share), 0.12, 0.12, 0.12, 0.0);
			lastBurstTypes = java.util.List.of(ParticleTypes.GEYSER_POOF);
		}
	}

	/** Hiss clips started since load (MOD-662). */
	public int getVentSounds() {
		return ventSounds;
	}

	/** Geyser bursts drawn since load (MOD-662). */
	public int getPlumeBursts() {
		return plumeBursts;
	}

	/** The particle types the last burst sent — what a scenario checks to know the old cloud puff is gone. */
	public java.util.List<ParticleType<?>> getLastBurstTypes() {
		return lastBurstTypes;
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
