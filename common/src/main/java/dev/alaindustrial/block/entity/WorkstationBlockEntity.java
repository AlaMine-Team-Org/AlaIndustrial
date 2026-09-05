package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.WorkstationPart;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Workstation's brain (MOD-483) — one buffer per assembled machine, held by the lower half.
 *
 * <p>Every part of the block carries one of these, because a state whose block is an
 * {@code EntityBlock} has to produce a block entity and the upper half will need one of its own to
 * hang a renderer on. Only the lower half's does anything: it alone ticks, it alone accepts energy,
 * and it alone drives the lit state of both halves. A casing's block entity is an empty buffer
 * nobody can reach — no face accepts energy, so a cable will not even draw an arm toward it.
 *
 * <p><b>No knob of its own for the buffer.</b> MV's default capacity is what an MV block gets unless
 * it has a reason to differ, and this one does not: it stores energy only to pay for an upgrade the
 * player buys, and the tier default already covers several of those. A knob invented without a
 * reason is a number somebody has to keep in step with the balance document forever.
 */
public class WorkstationBlockEntity extends EnergyBlockEntity {

	public WorkstationBlockEntity(BlockPos pos, BlockState state) {
		// maxExtract = 0: a workstation is a sink. Nothing ever flows back out of it, the same
		// contract the charging station, the energy condenser and the teleporter station keep.
		super(ModContent.WORKSTATION_BE.get(), pos, state, EnergyTier.MV,
				EnergyTier.MV.capacity(), EnergyTier.MV.maxVoltage(), 0L);
	}

	/**
	 * Keeps both halves' screens in step with whether the machine has power, then sleeps.
	 *
	 * <p>Sleeping is safe here and returning 0 would not be: nothing drains this buffer yet, so the
	 * only event that can change the screens is energy arriving — and a committed insert already wakes
	 * the block entity through the buffer's commit hook. A machine that never sleeps is a tick every
	 * tick for every workstation in the world, which is exactly what R-29 exists to stop.
	 */
	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// Charge for the ticks that actually went by, never a fixed batch (MOD-483).
		//
		// The sleep this method asks for is not a promise: a committed insert calls wake(), so a station
		// on a live cable runs this EVERY tick. Debiting "one second's worth" per visit therefore billed
		// one second per tick — 120 EU/t against a configured 6 — and a supply under that could never
		// leave anything in the buffer, so the screens stayed dark at 64 EU/t and lit at 128. Measuring
		// the interval instead makes the price 6 EU/t whatever wakes the block.
		long now = level.getGameTime();
		if (this.lastUpkeepTick == NEVER_CHARGED) {
			this.lastUpkeepTick = now;
		}
		// Capped at the batch length: a chunk that was unloaded for a week owes nothing, because the
		// machine was not running. Loading it must not present a bill for the time it stood still.
		long elapsed = Mth.clamp(now - this.lastUpkeepTick, 0L, UPKEEP_INTERVAL_TICKS);
		long upkeep = elapsed * Config.workstationEuPerTick;
		if (upkeep > 0 && getEnergyStorage().getAmount() > 0) {
			getEnergyStorage().drainInternal(upkeep);
			this.lastUpkeepTick = now;
			setChanged();
		} else if (upkeep > 0) {
			// Nothing left to take: move the clock anyway, or an empty station would run up a debt and
			// swallow the first EU that reaches it the moment a cable arrives.
			this.lastUpkeepTick = now;
		}
		WorkstationBlock.setLit(level, pos, getEnergyStorage().getAmount() > 0L);
		// Never IDLE_SLEEP_TICKS: a machine that consumes has nothing to be idle about. An unpowered
		// one still re-checks on this cadence, which is what lets it light up again when a cable
		// reaches it.
		return UPKEEP_INTERVAL_TICKS;
	}

	/**
	 * Energy goes into the lower half and nowhere else.
	 *
	 * <p>The front stays inert through {@link #facingAwareRole} (R-NRG-03) so a cable never draws an
	 * arm across the screens the player is meant to look at. The casing and the upper half report
	 * {@link EnergyRole#NONE} on every face: a loose casing is inventory that happens to be standing
	 * in the world, and the upper half is a monitor arm.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		BlockState state = getBlockState();
		if (!state.hasProperty(WorkstationBlock.PART)
				|| state.getValue(WorkstationBlock.PART) != WorkstationPart.LOWER) {
			return EnergyRole.NONE;
		}
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	/**
	 * Served after the working machines, like the other pure stores.
	 *
	 * <p>Without this an idle workstation asking for a full MV tick would outbid every LV machine on
	 * the same run — the exact reason the charging station and the teleporter declare it.
	 */
	@Override
	public boolean isEnergyStorageSink() {
		return true;
	}

	/**
	 * Clock for the fold-out animation — CLIENT ONLY, and deliberately not stored or synced.
	 *
	 * <p>The same scheme the reactor airlock uses for its sliding leaf: the server flips one blockstate
	 * property, that block update is already on the wire, and each client notices the edge on its own
	 * tick and remembers when it saw it. The animation therefore costs no extra bytes at all.
	 *
	 * <p>What it gives up, on purpose: a client that loads the chunk mid-roll sees the finished pose
	 * instead of the roll. For screens folding out that is invisible; paying a whole block-entity
	 * packet per event to fix it is not worth it (the garden drone is the mod's only field that does).
	 */
	/**
	 * The longest stretch a single debit may cover, in ticks — and the cadence an idle station
	 * re-checks itself on. One second; see {@link #onServerTick}.
	 */
	private static final int UPKEEP_INTERVAL_TICKS = 20;

	/** No tick has been billed yet — set on the first visit, so a fresh load owes nothing. */
	private static final long NEVER_CHARGED = Long.MIN_VALUE;

	/**
	 * Game time of the last upkeep debit. Deliberately NOT persisted: a station that was unloaded was
	 * not running, so after a reload the clock simply starts again at the first tick.
	 */
	private long lastUpkeepTick = NEVER_CHARGED;

	private static final long NO_TRANSITION = Long.MIN_VALUE;

	/** One second, the length the designer keyframed the fold-out at. Cosmetic, so no config knob. */
	private static final int TRANSITION_TICKS = 20;

	private boolean lastLit;
	private boolean stateSeen;
	private long transitionStart = NO_TRANSITION;

	/** Called from the block's client ticker on the lower half — the pair keeps one clock. */
	public void clientTick(BlockState state, long gameTime) {
		observe(state.hasProperty(WorkstationBlock.LIT) && state.getValue(WorkstationBlock.LIT),
				gameTime);
	}

	/**
	 * First sighting only takes a reading. A machine that comes into view already running must not
	 * replay its own start-up, which is what makes this idempotent and safe to call from the renderer
	 * as well as from the ticker.
	 */
	private void observe(boolean lit, long gameTime) {
		if (!this.stateSeen) {
			this.stateSeen = true;
			this.lastLit = lit;
			return;
		}
		if (lit != this.lastLit) {
			this.lastLit = lit;
			this.transitionStart = gameTime;
		}
	}

	/**
	 * The clock both halves read: the lower one's.
	 *
	 * <p>Two clocks would drift apart on exactly the frame where the block update lands between two
	 * client ticks, and the seam between the halves would visibly break. Falls back to itself while
	 * the pair is broken for a frame.
	 */
	public WorkstationBlockEntity animationClock() {
		BlockState state = getBlockState();
		if (this.level == null || !state.hasProperty(WorkstationBlock.PART)
				|| state.getValue(WorkstationBlock.PART) == WorkstationPart.LOWER) {
			return this;
		}
		return this.level.getBlockEntity(this.worldPosition.below())
				instanceof WorkstationBlockEntity lower ? lower : this;
	}

	/** 0 folded away, 1 fully out; eased, so the screens settle instead of stopping dead. */
	public float openProgress(long gameTime, float partialTicks) {
		BlockState state = getBlockState();
		boolean lit = state.hasProperty(WorkstationBlock.LIT) && state.getValue(WorkstationBlock.LIT);
		// Idempotent on purpose: a frame that arrives before the tick starts the roll instead of
		// drawing its end.
		observe(lit, gameTime);
		if (this.transitionStart == NO_TRANSITION) {
			return lit ? 1.0F : 0.0F;
		}
		// The subtraction is between two longs BEFORE the cast: a world tens of millions of ticks old
		// cannot hold consecutive ticks in a float, and the roll would quantise and then freeze.
		float elapsed = (float) (gameTime - this.transitionStart) + partialTicks;
		float t = Mth.clamp(elapsed / TRANSITION_TICKS, 0.0F, 1.0F);
		float eased = t * t * (3.0F - 2.0F * t);
		return lit ? eased : 1.0F - eased;
	}

}
