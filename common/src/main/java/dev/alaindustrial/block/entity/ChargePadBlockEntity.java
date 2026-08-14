package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ChargePadBlock;
import dev.alaindustrial.block.ChargePadState;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.PlayerEuDistributor;
import dev.alaindustrial.menu.ChargePadMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
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
public final class ChargePadBlockEntity extends MachineBlockEntity implements MenuProvider {

	/**
	 * How stale the last contact may be before the station calls itself unoccupied. Two ticks, not one,
	 * because {@code entityInside} and the block tick are unordered within a tick: at one tick a station
	 * that is genuinely occupied would flicker off whenever the block happened to tick first.
	 */
	private static final int CONTACT_GRACE_TICKS = 2;

	/**
	 * Ticks of contact settled by one payout (MOD-406).
	 *
	 * <p>The station used to pay every tick, and every payout rewrites the charged stack's energy
	 * component. A rewritten stack no longer {@code matches} the client's copy, so
	 * {@code AbstractContainerMenu.broadcastChanges} re-sends that slot — twenty times a second, per
	 * charged item, plus the cursor and the crafting grid while a screen is open (MOD-082). The player
	 * sees it as a stuttering inventory and a stuttering sound on whatever they interact with.
	 *
	 * <p>Five ticks, matching {@code EnergyBlockEntity}'s client-sync cadence for the same reason: a
	 * quarter second is short enough that a charge bar still looks live, and it cuts the rewrite rate
	 * by five. The AMOUNT paid is unchanged — both the budget and the per-item rate ceiling are scaled
	 * by the number of ticks being settled.
	 */
	private static final int PAYOUT_INTERVAL_TICKS = 5;

	/**
	 * Game time of the last {@link #chargePlayer} call, or -1 before the first one. Transient on purpose:
	 * a station that loads from disk has nobody on it, whatever was true when the chunk was saved.
	 */
	private long lastContactTick = -1L;

	/**
	 * Contact ticks accrued since the last payout. Reset to 0 by a payout and by a break in contact, so
	 * a player who steps off and back on cannot bank a larger-than-honest lump sum.
	 */
	private int pendingTicks;

	/** The indicator state of the last payout, replayed on the ticks between payouts so it cannot blink. */
	private ChargePadState lastPayoutState = ChargePadState.READY;

	/**
	 * Live readout for the screen (MOD-416), refreshed on every payout and cleared when the visitor
	 * leaves. Transient like {@link #lastContactTick}: a station loaded from disk is serving nobody,
	 * whatever was true when the chunk was saved.
	 *
	 * <p>Measured on the SERVER and shipped as finished numbers rather than derived on the client,
	 * because the ingredients are not client knowledge: {@code ItemEnergy.capacity} reads {@link Config},
	 * and the mod never syncs the config to clients. A client doing this arithmetic would draw numbers
	 * from its own local balance on any server that retuned one.
	 */
	private int rateEuPerTick;
	private int itemsCharging;
	private int etaSeconds;

	/**
	 * Whether the step-off click still owes to be played. Set when someone arrives, cleared the moment it
	 * fires, so {@link #onServerTick} cannot replay it — the station is a storage sink and wakes again on
	 * every intake from the grid, which would otherwise re-run the "contact went stale" branch forever.
	 */
	private boolean clickOffPending;

	public ChargePadBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.CHARGE_PAD_BE.get(), pos, state, EnergyTier.LV, 0,
				Config.chargePadBuffer, Config.chargePadInputRate, 0L);
	}

	/**
	 * No upgrade panel — and this is load-bearing, not cosmetic. {@code MachineBlockEntity}'s constructor
	 * sizes the inventory as {@code slots + (this instanceof MenuProvider && hasUpgradePanel() ? 4 : 0)},
	 * so the moment this class gained a menu (MOD-416) the plate would silently have grown four slots:
	 * hoppers would start filling a floor plate, the NBT layout would change under existing worlds, and
	 * the screen would advertise upgrades the station has never accepted. {@link ChargePadMenu} answers
	 * the same — both sides must agree, the slot indices depend on it.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/**
	 * Test seam (MOD-416): whether the departure click is still owed.
	 *
	 * <p>Exposed because the invariant behind it is otherwise unobservable — a gametest cannot hear a
	 * sound, and the property worth guarding is not "a click happened" but "the click is armed exactly
	 * once per visit and is not re-armed when the grid wakes an unoccupied station".
	 */
	public boolean isClickOffPending() {
		return clickOffPending;
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
		// A gap in contact starts a fresh batch: the ticks the player spent elsewhere are not owed to
		// them, and paying them as a lump sum would let someone hop on and off for a burst of charge.
		long now = serverLevel.getGameTime();
		// Leading edge, same shape as EnergyBlockEntity's client sync: the first tick of a fresh contact
		// pays at once, so stepping onto the station starts charging immediately instead of a quarter
		// second later. Only the ticks after it are batched.
		boolean freshContact = lastContactTick < 0 || now - lastContactTick > CONTACT_GRACE_TICKS;
		if (freshContact) {
			// A gap in contact starts a fresh batch: the ticks the player spent elsewhere are not owed to
			// them, and paying them as a lump sum would let someone hop on and off for a burst of charge.
			pendingTicks = 0;
			// MOD-416: the arrival click. Hung on this edge and no other — entityInside fires once per
			// tick per cell the player's box overlaps, so anything less selective would play twenty times
			// a second, and the guards above have already excluded spectators and the neighbouring cells
			// a sprinting player clips through.
			playClick(serverLevel, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON);
			clickOffPending = true;
		}
		lastContactTick = now;
		// Wake first: the station may have been asleep when the player arrived, and the tick that
		// releases the indicator once they leave has to be running.
		wake();
		pendingTicks++;
		if (!freshContact && pendingTicks < PAYOUT_INTERVAL_TICKS) {
			// Between payouts: keep showing what the last one concluded rather than recomputing, or the
			// indicator would blink CHARGING/READY four ticks out of five.
			updateIndicator(lastPayoutState);
			return;
		}
		int settled = pendingTicks;
		pendingTicks = 0;
		long budget = Math.min((long) Config.chargePadOutputRate * settled, energy.getAmount());
		if (budget <= 0) {
			clearReadout();
			lastPayoutState = ChargePadState.EMPTY;
			updateIndicator(ChargePadState.EMPTY);
			return;
		}
		PlayerEuDistributor.Result payout = PlayerEuDistributor.distribute(player, budget,
				PlayerEuDistributor.Policy.STATION, settled);
		long moved = payout.movedEu();
		if (moved > 0) {
			// Debited directly rather than through a transaction: this is the station's own internal
			// spend, and routing it through the transaction journal would fire the commit hook and wake
			// the block on its own energy leaving, which is what EnergyBuffer's contract reserves for
			// external delivery.
			energy.drainInternal(moved);
			setChanged();
		}
		updateReadout(payout, settled);
		lastPayoutState = moved > 0 ? ChargePadState.CHARGING : ChargePadState.READY;
		updateIndicator(lastPayoutState);
	}

	/**
	 * Turns one payout into the three numbers the screen shows (MOD-416).
	 *
	 * <p>The rate is the batch divided by the ticks it settled, so it reads as EU per tick however often
	 * the station actually pays — the payout interval is an implementation detail the player should never
	 * see. The estimate divides the gear's remaining room by that rate, which is what makes it honest on a
	 * thin supply line: a station fed by copper cable delivers less, so the wait it reports grows instead
	 * of promising the 128 EU/t ceiling it cannot reach.
	 *
	 * <p>Everything is clamped into signed 16 bits, because {@code ContainerData} ships each channel as a
	 * short: a value above 32767 arrives NEGATIVE on the client rather than merely wrong. Charge and
	 * capacity fit by construction (the buffer is 20 000), the rate is capped at 128 by config, but the
	 * estimate is a quotient and a nearly-idle station can push it arbitrarily high.
	 */
	private void updateReadout(PlayerEuDistributor.Result payout, int settled) {
		rateEuPerTick = settled > 0 ? (int) (payout.movedEu() / settled) : 0;
		itemsCharging = Math.max(0, payout.itemsServed());
		long room = payout.remainingRoom();
		if (room > 0 && rateEuPerTick > 0) {
			// Round up: "1 s left" on a job that still needs part of a second is friendlier than a 0 that
			// reads as "done" while the bar is still moving.
			long ticks = (room + rateEuPerTick - 1) / rateEuPerTick;
			etaSeconds = (int) Math.min(Short.MAX_VALUE, (ticks + 19) / 20);
		} else {
			etaSeconds = 0;
		}
	}

	/** Blank the readout — the screen renders a dash rather than a zero that would read as a real number. */
	private void clearReadout() {
		rateEuPerTick = 0;
		itemsCharging = 0;
		etaSeconds = 0;
	}

	/** One-shot click at the plate, vanilla volume and pitch — the same 4-arg call the vanilla plate makes. */
	private void playClick(ServerLevel level, SoundEvent sound) {
		level.playSound(null, worldPosition, sound, SoundSource.BLOCKS);
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
		// MOD-416: the departure click, guarded by a flag rather than by the staleness test above. This
		// branch is NOT reached once per visit — the station is a storage sink, so every intake from the
		// grid wakes it and walks straight back in here with the same stale timestamp. Without the flag
		// the plate would click at a departure that already happened, over and over.
		if (clickOffPending) {
			clickOffPending = false;
			clearReadout();
			if (level instanceof ServerLevel serverLevel) {
				playClick(serverLevel, SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF);
			}
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

	/**
	 * Seven-wide data — hides {@link MachineBlockEntity#DATA_COUNT} on purpose so this name always states
	 * <em>this</em> machine's width, both here and in {@link ChargePadMenu}'s client stub. The width lives
	 * in exactly one place for a reason: when it was a literal on both sides, adding a channel to a block
	 * entity alone threw {@code ArrayIndexOutOfBoundsException} on the client render thread while every
	 * server test stayed green (MOD-235).
	 */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 3;

	/** EU per tick currently flowing into whoever stands on the plate. */
	public static final int DATA_RATE = 4;
	/** How many carried items are taking charge right now. */
	public static final int DATA_ITEMS = 5;
	/** Seconds until the visitor's gear is full; {@code 0} means "nothing to report", drawn as a dash. */
	public static final int DATA_ETA = 6;

	/**
	 * Base 0..3 plus the three readout channels. Channels 2 and 3 ({@code progress}/{@code maxProgress})
	 * are left untouched rather than reused: their meaning varies per machine and other machines' block
	 * entity renderers read them, so borrowing the indices for something else is how a GUI feature ported
	 * between machines quietly breaks the target's renderer.
	 */
	private final ContainerData chargePadData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_RATE -> rateEuPerTick;
				case DATA_ITEMS -> itemsCharging;
				case DATA_ETA -> etaSeconds;
				default -> ChargePadBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			// The three readout channels are derived and server-authoritative: they are computed from a
			// payout, never assigned from outside.
			if (index < DATA_RATE) {
				ChargePadBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return chargePadData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.charge_pad");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ChargePadMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
