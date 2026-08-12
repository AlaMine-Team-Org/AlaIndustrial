package dev.alaindustrial.block.entity;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.FaceEnergyPort;
// MOD-022 Phase 2: the energy spine runs on the common EnergyPort/EnergyBuffer abstraction — no loader
// energy API is imported here, so this base class (and everything under it) lives in common. The buffer
// is a platform-neutral EnergyBuffer; each loader exposes it as its native capability through the
// FabricEnergyPort / NeoForgeEnergyPort adapters and its own transaction system.
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Everything a block needs to sit on the EU grid and nothing more (MOD-400): an {@link EnergyBuffer},
 * a voltage {@link EnergyTier}, per-face energy roles, the idle-sleep server tick, energy persistence
 * and the client-sync plumbing.
 *
 * <p><b>Why this layer exists.</b> The cable and the two pipes are transport, not machines, yet they
 * used to extend {@link MachineBlockEntity} and inherit an inventory, a processing progress counter,
 * a {@code WorldlyContainer} implementation, four upgrade slots and an owner UUID — none of which they
 * have any use for. The base carried three opt-out hooks to keep that fiction working
 * ({@code tracksOwner()}, {@code saveEnergyOnly()/loadEnergyOnly()}, {@code hasUpgradePanel()}); this
 * split removes the first two entirely, because a transport segment now simply is not a machine.
 *
 * <p><b>What goes where.</b> Energy, tick and sync live here; inventory, progress, upgrades, ownership
 * and sided automation live in {@link MachineBlockEntity}. A new block extends this class if it only
 * needs a buffer and a tick, and {@link MachineBlockEntity} if it has slots or a screen.
 *
 * <p><b>Save format.</b> The buffer is written under the canonical {@code "Energy"} key, exactly where
 * the machine path wrote it before the split, so existing worlds load without a migration.
 */
public abstract class EnergyBlockEntity extends BlockEntity implements EnergyPortHost {

	/** Idle-sleep safety net (R-29): how long an idle block skips its full tick before re-checking. */
	protected static final int IDLE_SLEEP_TICKS = 40;

	/**
	 * Minimum ticks between two full block-entity packets for the same block (MOD-400).
	 *
	 * <p>Five ticks — a quarter second — is the point where the two costs cross. {@code getUpdateTag}
	 * ships the block entity's WHOLE nbt (a machine's entire inventory, its owner, a pipe's face
	 * configuration) to every player tracking the chunk, and the container methods below fire it on
	 * every single item movement: an item pipe running at one stack per tick used to send twenty full
	 * packets per second per machine it touched. Capping the rate at four per second cuts that by 5×,
	 * while a quarter second is short enough that no block-model change reads as lag.
	 *
	 * <p>The cadence only ever coalesces a <em>burst</em>: the first change after a quiet period is
	 * sent on the spot (see {@link #syncBlockEntityToClient()}), so a one-off player action — a wrench
	 * click, an insulating stand, an item dropped into a slot — is as immediate as it was before.
	 */
	private static final int VISUAL_SYNC_INTERVAL_TICKS = 5;

	protected final EnergyBuffer energy;
	protected final EnergyTier tier;

	/** Ticks left to skip {@link #onServerTick}; reset to 0 by {@link #wake()} when external state changes. */
	private int sleepTicks;

	/**
	 * Game tick from which the next full packet may go out. Starts at 0 (game time is never negative),
	 * so the very first sync of a freshly loaded block entity is immediate.
	 */
	private long nextVisualSyncTick;

	/** A change happened while the cadence was closed; the update still owes the watching clients. */
	private boolean visualSyncPending;

	protected EnergyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, long capacity, long maxInsert, long maxExtract) {
		super(type, pos, state);
		this.tier = tier;
		// The onCommit hook fires once when the outermost transaction that moved energy through this
		// buffer commits (was MachineEnergyStorage.onFinalCommit): persist + GUI-sync, then wake. A
		// committed insert/extract is external delivery/draw, which must wake an idle consumer (R-29);
		// internal per-tick drain mutates `energy.getAmount()` directly (no transaction) and never fires this.
		this.energy = new EnergyBuffer(capacity, maxInsert, maxExtract, this::onEnergyTransactionCommitted);
	}

	/**
	 * Called after a transaction that changed this block's energy buffer commits. Subclasses may extend
	 * the hook, but must call {@code super}: persistence and idle wake-up live here.
	 */
	protected void onEnergyTransactionCommitted() {
		setChangedQuietly();
		wake();
	}

	// --- server tick ---------------------------------------------------------------------------

	/**
	 * Server-side tick entry point (called from the block's ticker). Wraps {@link #onServerTick} with
	 * an idle-sleep gate (R-29): when a block reports it has no work to do, it skips the next few ticks
	 * instead of re-running its full logic every tick. External changes — inventory mutation, energy
	 * delivery (via the {@link EnergyBuffer} commit hook), or an explicit {@link #wake()} — reset the
	 * timer so a sleeping block resumes on the very next tick. Generators, storage and the pump return
	 * 0 (never sleep); processing machines and an idle cable segment return {@link #IDLE_SLEEP_TICKS}.
	 *
	 * <p>The queued client update is flushed <b>outside</b> the sleep gate on purpose: a block that
	 * just went to sleep must still deliver the change that put it to sleep, or the watching clients
	 * keep rendering the state from before it.
	 */
	public final void serverTick(Level level, BlockPos pos, BlockState state) {
		flushVisualSync();
		if (sleepTicks > 0) {
			sleepTicks--;
			return;
		}
		sleepTicks = Math.max(0, onServerTick(level, pos, state));
	}

	/**
	 * Per-tick logic. Returns the number of ticks this block may sleep before its next full tick:
	 * 0 keeps it ticking every tick, a positive value lets the {@link #serverTick} gate skip that many
	 * ticks. Was {@code serverTick} before the idle-sleep gate (R-29) was introduced.
	 */
	protected abstract int onServerTick(Level level, BlockPos pos, BlockState state);

	/** Wake a sleeping block so its next {@link #serverTick} runs {@link #onServerTick} immediately. */
	public void wake() {
		sleepTicks = 0;
	}

	// --- energy --------------------------------------------------------------------------------

	public EnergyBuffer getEnergyStorage() {
		return energy;
	}

	public EnergyTier getTier() {
		return tier;
	}

	/**
	 * Energy role exposed on a given WORLD face (R-NRG-03). Default {@link EnergyRole#BOTH}
	 * (cables / symmetric storage); generators override to OUT, consumer machines to IN, and the
	 * BatteryBox to a mixed output-face layout.
	 */
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.BOTH;
	}

	/**
	 * Applies a block's working energy role to every face EXCEPT its {@link HorizontalMachineBlock#FACING}
	 * front, which is inert ({@link EnergyRole#NONE}) — no cable/hopper connects through it (R-NRG-03).
	 * Blocks without a FACING property keep the working role on all faces.
	 */
	protected EnergyRole facingAwareRole(Direction worldFace, EnergyRole workingRole) {
		BlockState st = getBlockState();
		if (st.hasProperty(HorizontalMachineBlock.FACING) && worldFace == st.getValue(HorizontalMachineBlock.FACING)) {
			return EnergyRole.NONE;
		}
		return workingRole;
	}

	/** The neutral energy-port view for a face, enforcing its role; {@code null} means the face is inert. */
	@Override
	public EnergyPort energyPort(Direction worldFace) {
		EnergyRole role = energyRoleForFace(worldFace);
		return role == EnergyRole.NONE ? null : new FaceEnergyPort(energy, role);
	}

	/**
	 * True if this block is a pure energy <em>store</em> (e.g. BatteryBox) rather than a working machine.
	 * The {@link dev.alaindustrial.core.energy.EnergyNetwork} serves working machines before storage sinks so
	 * a large buffer can't starve them, and never charges a sink from itself (MOD-009). Default false.
	 */
	public boolean isEnergyStorageSink() {
		return false;
	}

	/**
	 * True if this store may take part in the storage→storage cascade (MOD-314): a fuller battery topping
	 * up an emptier one until their fill fractions meet.
	 *
	 * <p>Deliberately narrower than {@link #isEnergyStorageSink()} and deliberately a named predicate
	 * rather than an {@code instanceof BatteryBoxBlockEntity} at the call site. The Teleporter is also a
	 * storage sink, with a buffer 25× a Battery Box's, so a fraction-balancing cascade would quietly drain
	 * a full box into it down to ~2 % with no generator on the network and no action by the player — a
	 * balance change nobody asked for, and one that contradicts the Teleporter's own documented model of
	 * banking EU from a surplus. Whoever adds the next store answers this question explicitly instead of
	 * inheriting an answer from a type check somewhere else. Default false.
	 */
	public boolean acceptsCascade() {
		return false;
	}

	/**
	 * EU/tick this block may draw from a <b>store</b> over cable when nothing else on the segment is
	 * asking for power (MOD-353). Default 0 — the channel is opt-in, so no existing block changes.
	 *
	 * <p>This is the third and last of a trio that is easy to confuse, so state all three together:
	 * <ul>
	 *   <li>{@link #isEnergyStorageSink()} — "serve me after the working machines". It says nothing about
	 *       where the energy may come from.</li>
	 *   <li>{@link #acceptsCascade()} — "another store may equalise into me by fill fraction".
	 *       Proportional, and therefore refused to anything with a buffer far larger than a Battery
	 *       Box (MOD-314 R3).</li>
	 *   <li>{@code storageFeedRate()} — "a store may trickle into me at this flat rate, and only from
	 *       what it holds above its own reserve". Absolute, capped and reserve-guarded, which is exactly
	 *       what makes it safe for the blocks the cascade must keep refusing.</li>
	 * </ul>
	 *
	 * <p>A block that is <em>not</em> an {@link #isEnergyStorageSink()} has no use for this: an ordinary
	 * machine already creates machine demand, which opens the existing backup-discharge stage.
	 */
	public long storageFeedRate() {
		return 0L;
	}

	// --- persistence + client sync --------------------------------------------------------------

	/**
	 * Mark the block entity dirty so its chunk saves — and nothing else. Named for what it does: it
	 * sends no packet, because the two values that call it (stored EU, processing progress) reach an
	 * open screen through {@link MachineBlockEntity#getDataAccess()} / {@code ContainerData}, which the
	 * menu re-reads every tick on its own. A block whose MODEL changed wants
	 * {@link #syncBlockEntityToClient()} instead.
	 */
	public void setChangedQuietly() {
		setChanged();
	}

	/**
	 * Push nbt-visible changes (inventory, face configuration, accessories) to the clients watching this
	 * chunk, at most once per {@link #VISUAL_SYNC_INTERVAL_TICKS}.
	 *
	 * <p>Leading edge immediate, trailing edge coalesced: the first call after a quiet period sends
	 * straight away, and further calls inside the window only raise {@link #visualSyncPending}, which
	 * {@link #serverTick} flushes as soon as the cadence reopens. So a single player action is never
	 * delayed, while a machine being fed by a pipe stops broadcasting its whole inventory twenty times
	 * a second.
	 *
	 * <p><b>Invariant this depends on:</b> every block whose block entity extends this class has a
	 * server ticker (checked across all 30 of them for MOD-400), so a deferred update always has a
	 * flush point. {@link #setRemoved()} covers the end of that lifetime. A future energy block entity
	 * with NO server ticker must therefore either get one or stop deferring — otherwise its last change
	 * before going quiet would never reach the watching clients.
	 */
	protected void syncBlockEntityToClient() {
		if (level == null || level.isClientSide()) {
			return;
		}
		if (level.getGameTime() >= nextVisualSyncTick) {
			sendVisualUpdate();
		} else {
			visualSyncPending = true;
		}
	}

	/** Send a queued update as soon as the cadence allows it; a no-op when nothing is pending. */
	private void flushVisualSync() {
		if (visualSyncPending && level != null && !level.isClientSide()
				&& level.getGameTime() >= nextVisualSyncTick) {
			sendVisualUpdate();
		}
	}

	private void sendVisualUpdate() {
		visualSyncPending = false;
		nextVisualSyncTick = level.getGameTime() + VISUAL_SYNC_INTERVAL_TICKS;
		BlockState state = getBlockState();
		level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
	}

	/**
	 * A block entity that is going away stops ticking, so a queued update would die with it — flush it
	 * here. {@code sendBlockUpdated} only marks the section as changed for the chunk map's next
	 * broadcast, so this is safe to call from the middle of a block replacement: the packet is built
	 * later, from whatever state the world ends up in.
	 */
	@Override
	public void setRemoved() {
		if (visualSyncPending && level != null && !level.isClientSide()) {
			sendVisualUpdate();
		}
		super.setRemoved();
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return saveWithoutMetadata(provider);
	}

	/**
	 * Flip the block's {@code lit} state to match whether the machine is working, so it shows its
	 * active ("on") model. No-op for blocks without a {@code lit} blockstate (cables, solar panels).
	 */
	protected void updateLit(boolean working) {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = getBlockState();
		if (state.hasProperty(BlockStateProperties.LIT)
				&& state.getValue(BlockStateProperties.LIT) != working) {
			level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, working), Block.UPDATE_CLIENTS);
		}
	}

	// --- persistence (26.2 ValueInput/ValueOutput) ---

	/**
	 * Writes the energy buffer under the canonical {@code "Energy"} key — the same key and the same
	 * position in the write order the machine path used before MOD-400, so existing saves round-trip
	 * without a migration.
	 */
	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("Energy", energy.getAmount());
	}

	/**
	 * Set once the first time a save is read whose stored charge exceeds the block's current capacity,
	 * so the warning below is printed once per server run instead of once per block entity.
	 */
	private static boolean warnedAboutClampedCharge;

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		long stored = input.getLongOr("Energy", 0L);
		energy.setAmountUntracked(stored);
		// The clamp inside setAmountUntracked is right — a buffer must not hold more than its capacity —
		// but it is silent, and a save legitimately CAN carry more: lowering a buffer knob in
		// config/alaindustrial.json shrinks every existing block of that kind on its next chunk load.
		// Before MOD-400 the raw field kept the surplus, so an operator who did that saw nothing change
		// and now sees energy vanish. Say so once, with a real number, instead of leaving them to guess.
		if (stored > energy.getCapacity() && !warnedAboutClampedCharge) {
			warnedAboutClampedCharge = true;
			Industrialization.LOGGER.warn(
					"[energy] {} at {} was saved holding {} EU but its buffer is now {} EU — the surplus is"
							+ " dropped. This is expected after LOWERING a buffer value in"
							+ " config/alaindustrial.json; further occurrences this run are not logged.",
					getType(), worldPosition, stored, energy.getCapacity());
		}
	}
}
