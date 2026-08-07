package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ChargePadBlock;
import dev.alaindustrial.block.ChargePadState;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.PlayerEuDistributor;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Charging Station (MOD-274): banks EU from the grid and pours it into the gear of whoever is
 * standing on it.
 *
 * <p><b>It is a capacitor, not a machine.</b> Its buffer is sized to a visit rather than to an
 * operation, because the grid cannot deliver anywhere near {@link Config#chargePadOutputRate} — a
 * copper cable carries 12 EU/t. Filling slowly while nobody is around and emptying fast into a player
 * is the entire trick that makes "step on, walk away" possible; without the buffer the station would be
 * exactly as slow as the wire feeding it.
 *
 * <p><b>Energy is spent per transfer, never per tick.</b> An unoccupied station, or one whose visitor is
 * already full, spends nothing — the same demand-driven discipline as {@link ElectricHeaterBlockEntity}.
 *
 * <p>Note what that does and does not cover: the <em>spend</em> is demand-driven, the <em>intake</em> is
 * not. A station with room in its buffer keeps drawing from the grid whether or not anyone is standing
 * on it — that is the whole point of banking ahead of a visit, and it is why the block declares itself a
 * storage sink (see {@link #isEnergyStorageSink()}) rather than a machine. Without that declaration its
 * 20 000 EU of head room would read as machine demand and quietly drain the base's batteries into a
 * block that cannot give the energy back.
 *
 * <p><b>Contact is remembered as a timestamp, not a flag.</b> {@code entityInside} fires from the
 * entity's tick and {@link #onServerTick} from the block's, and vanilla does not order the two, so a
 * boolean "someone is here" set by one and cleared by the other can be read a tick early or a tick late
 * depending on which ran first. Worse, an entity that leaves the world between the two — a teleport, a
 * disconnect, a chunk unload — would never clear the flag at all, and the station would sit lit and
 * awake forever, drawing from the grid for nobody. A timestamp cannot get stuck: it simply stops being
 * recent.
 */
public final class ChargePadBlockEntity extends MachineBlockEntity {

	/**
	 * How stale the last contact may be before the station calls itself unoccupied. Two ticks, not one,
	 * because {@code entityInside} and the block tick are unordered within a tick: at one tick a station
	 * that is genuinely occupied would flicker off whenever the block happened to tick first.
	 */
	private static final int CONTACT_GRACE_TICKS = 2;

	/**
	 * Game time of the last {@link #chargePlayer} call, or -1 before the first one. Transient on purpose:
	 * a station that loads from disk has nobody on it, whatever was true when the chunk was saved.
	 */
	private long lastContactTick = -1L;

	public ChargePadBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.CHARGE_PAD_BE.get(), pos, state, EnergyTier.LV, 0,
				Config.chargePadBuffer, Config.chargePadInputRate, 0L);
	}

	/**
	 * Hands one tick's worth of EU to a player standing on the station, and reports what happened on the
	 * indicator. Called from {@link ChargePadBlock#entityInside} for every entity in the block's space,
	 * so it owns the side, type and spectator guards.
	 *
	 * <p>A spectator is excluded explicitly rather than by relying on their lack of collision — the same
	 * belt-and-braces the bare cable's shock check uses. Drifting through a station must not drain it.
	 */
	public void chargePlayer(Level level, Entity entity) {
		if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)
				|| player.isSpectator()) {
			return;
		}
		// Only the station the player is actually standing IN serves them. `entityInside` fires once per
		// block position the player's box overlaps, and a 0.6-wide hitbox straddles two positions on a
		// seam and four on a corner — so without this a player standing between two stations would be
		// served by both, taking 2× (or 4×) each item's documented input rate and the station's per-tick
		// output. That also settles the pass-through case: vanilla calls this hook for every cell on the
		// movement path, so someone sprinting across a row of stations would otherwise trigger every one
		// of them at full budget in a single tick.
		if (!player.blockPosition().equals(worldPosition)) {
			return;
		}
		lastContactTick = serverLevel.getGameTime();
		// Wake first: the station may have been asleep when the player arrived, and the tick that
		// releases the indicator once they leave has to be running.
		wake();
		long budget = Math.min(Config.chargePadOutputRate, energy.amount);
		if (budget <= 0) {
			updateIndicator(ChargePadState.EMPTY);
			return;
		}
		long moved = PlayerEuDistributor.distribute(player, budget, PlayerEuDistributor.Policy.STATION);
		if (moved > 0) {
			// Debited directly rather than through a transaction: this is the station's own internal
			// spend, and routing it through the transaction journal would fire the commit hook and wake
			// the block on its own energy leaving, which is what EnergyBuffer's contract reserves for
			// external delivery.
			energy.amount -= moved;
			setChanged();
		}
		updateIndicator(moved > 0 ? ChargePadState.CHARGING : ChargePadState.READY);
	}

	/**
	 * Releases the indicator once contact goes stale, then sleeps. Nothing else happens here — the
	 * transfer itself is driven entirely by {@link #chargePlayer}, so an empty station costs one
	 * comparison every {@link #IDLE_SLEEP_TICKS} ticks.
	 */
	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (lastContactTick >= 0 && level.getGameTime() - lastContactTick <= CONTACT_GRACE_TICKS) {
			return 0;
		}
		updateIndicator(ChargePadState.IDLE);
		return IDLE_SLEEP_TICKS;
	}

	/**
	 * Writes the indicator state, skipping the write when it already agrees. Same mechanism as
	 * {@code updateLit}, which cannot be reused: it is typed against {@code BlockStateProperties.LIT}
	 * and this block deliberately has four states rather than two (see {@link ChargePadState}).
	 */
	private void updateIndicator(ChargePadState next) {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = getBlockState();
		if (state.hasProperty(ChargePadBlock.STATE) && state.getValue(ChargePadBlock.STATE) != next) {
			level.setBlock(worldPosition, state.setValue(ChargePadBlock.STATE, next), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * The station banks EU rather than doing work with it, so the network fills it from the surplus
	 * left after working machines (MOD-009) — the same call the Teleporter makes, for the same reason.
	 *
	 * <p>Not a formality. {@code EnergyShare#split} divides supply <em>proportionally to demand</em>,
	 * and this block asks for {@link Config#chargePadOutputRate} (128) against the 32 EU/t every LV
	 * machine asks for: as a plain machine the station would win a 128:32 split and take ~80 % of a
	 * shared grid, so placing one would visibly stall the base's furnaces. Worse, its 20 000 EU of head
	 * room reads as machine demand even with nobody standing on it, which makes
	 * {@code EnergyLineDistributor#storageBudget} positive and starts draining every Battery Box on the
	 * line into a block that can never give the energy back ({@code maxExtract = 0}).
	 *
	 * <p>It still fills perfectly well: a player who wants it topped up now puts a generator or a
	 * battery box flush against it, because the direct, cable-less path ignores this flag entirely.
	 *
	 * <p>{@link #acceptsCascade()} stays false (inherited): the store-to-store cascade balances
	 * <em>fill fractions</em>, and letting a Battery Box level itself against a station that only ever
	 * spends on players would drain the bank by design rather than by demand.
	 */
	@Override
	public boolean isEnergyStorageSink() {
		return true;
	}

	/**
	 * MOD-353: the station accepts a slow trickle from stores over cable.
	 *
	 * <p>It had the same defect as the Teleporter and for the same reason — a storage sink outside the
	 * cascade creates no machine demand, so a Battery Box wired to it discharged nothing and the plate
	 * sat dead. That was worse here than on the Teleporter: the documented workaround ("stand a store
	 * flush against it") is far less obvious for a floor plate the player walks onto.
	 *
	 * <p>{@link #acceptsCascade()} still stays false — see the note above; this channel is the absolute,
	 * reserve-guarded one, not the proportional cascade.
	 */
	@Override
	public long storageFeedRate() {
		return dev.alaindustrial.Config.storageFeedRate;
	}

	/**
	 * Intake on every face. The station is a pure consumer, so no face may extract: left at the
	 * inherited {@link EnergyRole#BOTH} its ports would advertise extraction, and the network would
	 * enumerate a block that never produces anything as a candidate producer.
	 *
	 * <p>Not routed through {@code facingAwareRole} — there is no {@code FACING} on this block for it to
	 * exempt, so calling it would only obscure that every face is live.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}
}
