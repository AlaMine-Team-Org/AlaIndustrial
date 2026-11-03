package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.ReactorAntennaBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.PerTickDemand;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The antenna's buffer and its link to a controller (MOD-941).
 *
 * <p><b>The controller serves it; it never looks for the controller.</b> Like the outlet, an antenna has no idea
 * which room it belongs to. The controller collects its antennas on the walk it already makes and calls
 * {@link #serve} every tick: that call is both the link (who owns this antenna, and since when) and the upkeep.
 * An antenna nobody served for {@link #LINK_TIMEOUT_TICKS} is silent — its controller is gone, unloaded, or no
 * longer counts it.
 *
 * <p><b>Power, in order:</b> a cable on any face fills the buffer like any consumer; when the buffer cannot pay
 * the next stretch of upkeep, the controller tops it up from the reactor's own buffer
 * ({@link #topUpFromReactor}); with neither, the upkeep goes unpaid and the antenna stops transmitting.
 */
public class ReactorAntennaBlockEntity extends EnergyBlockEntity implements PerTickDemand {

	/** Ticks an unserved antenna still counts as linked: a little slack over the controller's every-tick call. */
	public static final long LINK_TIMEOUT_TICKS = 5L;

	/** The longest stretch one {@link #serve} bills, as the monitor core does: skipped, never overbilled. */
	private static final long UPKEEP_INTERVAL_TICKS = 20L;

	private static final long NEVER = Long.MIN_VALUE;

	/** The controller that served this antenna last; runtime only — the controller re-links it on its first tick. */
	private @Nullable BlockPos controller;
	private long lastServed = NEVER;
	private boolean paid;
	private boolean masted;

	public ReactorAntennaBlockEntity(BlockPos pos, BlockState state) {
		// A sink: nothing flows back out of an antenna.
		super(ModContent.REACTOR_ANTENNA_BE.get(), pos, state, EnergyTier.LV,
				ReactorConfig.reactorAntennaBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	/** Power goes in through any face: whichever face sticks out of the wall is the one a cable can reach. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}

	/** Its upkeep per tick, doubled until the buffer holds one billing stretch of it (as the monitor core). */
	@Override
	public long perTickDemand() {
		long upkeep = Math.max(1, ReactorConfig.reactorAntennaUpkeepEuPerTick);
		long reserve = Math.min(energy.getCapacity(), upkeep * UPKEEP_INTERVAL_TICKS);
		return energy.getAmount() < reserve ? 2 * upkeep : upkeep;
	}

	/** EU this antenna still wants to hold one billing stretch of upkeep; what the reactor may top up. */
	public long shortfall() {
		long reserve = Math.min(energy.getCapacity(),
				(long) Math.max(0, ReactorConfig.reactorAntennaUpkeepEuPerTick) * UPKEEP_INTERVAL_TICKS);
		return Math.max(0L, reserve - energy.getAmount());
	}

	/** Accepts a top-up from the reactor's buffer; returns what was actually taken. */
	public long topUpFromReactor(long offered) {
		long room = Math.min(offered, energy.getCapacity() - energy.getAmount());
		if (room <= 0) {
			return 0;
		}
		energy.setAmountUntracked(energy.getAmount() + room);
		setChanged();
		return room;
	}

	/**
	 * Called by the owning controller every tick: records the link and bills the upkeep for the ticks since the
	 * last call. Priced on elapsed ticks, never a fixed batch, as the monitor core is.
	 *
	 * @return whether the upkeep was paid — whether the antenna is transmitting
	 */
	public boolean serve(Level level, BlockPos controllerPos) {
		long now = level.getGameTime();
		// The boom is the antenna's aerial: no boom (its cell built over), no transmission. One that an explosion
		// took grows back here as soon as its cell is free.
		masted = ReactorAntennaBlock.placeMast(level, worldPosition, getBlockState());
		long elapsed = lastServed == NEVER ? 1L : Math.max(0L, Math.min(now - lastServed, UPKEEP_INTERVAL_TICKS));
		this.controller = controllerPos.immutable();
		this.lastServed = now;
		if (elapsed == 0L) {
			// A second call within the same tick bills nothing and must not turn an unpaid antenna into a paid one:
			// the verdict of this tick's first call stands.
			return paid;
		}
		long due = elapsed * Math.max(0, ReactorConfig.reactorAntennaUpkeepEuPerTick);
		if (energy.getAmount() < due) {
			// Take what is there anyway, as the monitor core does: an unpaid antenna stays silent instead of
			// flickering back on every time a trickle arrives.
			energy.drainInternal(energy.getAmount());
			paid = false;
		} else {
			energy.drainInternal(due);
			paid = true;
		}
		setChanged();
		return paid;
	}

	/** The controller this antenna belongs to while it is linked, or {@code null}. */
	public @Nullable BlockPos linkedController(long gameTime) {
		return isLinked(gameTime) ? controller : null;
	}

	/** Whether a controller served this antenna within {@link #LINK_TIMEOUT_TICKS}. */
	public boolean isLinked(long gameTime) {
		return controller != null && lastServed != NEVER && gameTime - lastServed <= LINK_TIMEOUT_TICKS;
	}

	/** Whether it is linked, its boom stands and its last upkeep was paid — whether a tablet can hear it. */
	public boolean isTransmitting(long gameTime) {
		return isLinked(gameTime) && paid && masted;
	}

	/** Nothing of its own to do: the controller serves it. Never woken by a ticker either — it has none. */
	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		return 0;
	}
}
