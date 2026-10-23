package dev.alaindustrial.block.entity;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ReceptionCapsuleBlock;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.ReceptionCapsuleMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.ReceptionCapsuleRules;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Material;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Verdict;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementGate;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.ShiftWorkerHost;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.works.TownHousing;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The reception capsule's machine (MOD-833; epic MOD-824, decisions R6, R36, R39): in its town, while the core is
 * lit, it restores one shift worker at a time for EU and materials — a new one, with a new number from the
 * record-number service (MOD-827), or, with permadeath off, a dead one of the town brought back for double the
 * price.
 *
 * <p><b>Its town.</b> The capsule serves the town whose territory held it when it first looked — the one with the
 * nearest core, where two territories overlap — and keeps that town's number ({@code SettlementId}) for good. The
 * town is a record of {@link SettlementRegistry}; a capsule moved by its owner is a new block and looks again. A
 * capsule outside its town's territory, in a dark town or a town whose core is taken away restores nobody: that
 * is "works only inside the territory of a lit core", answered for one town rather than for whichever territory
 * reaches the spot — a neighbouring town's core cannot hand this capsule's workers to another owner.
 *
 * <p><b>The price is taken when the worker stands in the world, not before</b> ({@link ReceptionCapsuleRules}). The
 * buffer fills from the network like a store's ({@link #perTickDemand} 0, ADR-049); the materials sit in three
 * slots, one per material. When everything is in, the restoration runs for {@code capsuleRestoreTicks} ticks; if
 * anything fails on the way it stops where it was. On the last tick the capsule checks again and hands the worker
 * to the shift workers' service ({@link ShiftWorkers#CAPSULE_HOST}); only when the worker stands in the capsule
 * does it drain the EU and take the materials. A worker that could not be put into the world takes nothing, and
 * the capsule says the archive does not answer for one restoration's length before it tries again.
 *
 * <p><b>New or returning (R39).</b> The capsule restores a new worker unless the player picked one of the town's
 * dead who wait to come back ({@link #nextChoice}); bringing one back costs {@link ReceptionCapsuleRules#euPrice}
 * twice over, materials too. With permadeath on (the default) nobody waits and the choice never appears.
 *
 * <p><b>Breaking (R36)</b> mirrors the settlement core: only the owner of the capsule's town, with any pickaxe. The
 * client learns that owner from the update tag, so the owner sees the cracks and nobody else does.
 *
 * <p>No upgrade panel ({@link NoUpgradePanel}): an overclocker would only shorten a restoration that is a wait
 * for the price, not a cost per tick.
 */
public final class ReceptionCapsuleBlockEntity extends MachineBlockEntity implements MenuProvider, NoUpgradePanel {

	/** Slot of the electronic circuits. */
	public static final int CIRCUIT_SLOT = 0;
	/** Slot of the rubber. */
	public static final int RUBBER_SLOT = 1;
	/** Slot of the copper plates. */
	public static final int COPPER_PLATE_SLOT = 2;
	/** The capsule's own slots: one per {@link Material}, in material order. */
	public static final int SLOT_COUNT = Material.values().length;

	private static final String KEY_SETTLEMENT = "SettlementId";
	private static final String KEY_RETURNING = "Returning";
	/** Update-tag only: the owner of the capsule's town, so a client knows whose pickaxe cracks it. Never saved. */
	private static final String KEY_CLIENT_OWNER = "SettlementOwner";

	/** Two short channels carry one value: 15 bits each (MOD-833, see {@link Channel}). */
	private static final int HALF_BITS = 15;
	private static final int HALF_MASK = (1 << HALF_BITS) - 1;
	/** The largest value two channels carry. */
	public static final long CHANNEL_PAIR_MAX = (1L << (2 * HALF_BITS)) - 1;

	/** The number of the capsule's town in {@link SettlementRegistry}; 0 until the capsule finds one. */
	private int settlementId;
	/** The dead worker chosen to come back (R39), or null — a new worker. Saved. */
	@Nullable
	private RecordNumber returning;
	/** Why the capsule stands still, or {@link Verdict#READY}, as of its last tick. */
	private Verdict verdict = Verdict.NO_CORE;
	/** The town's living workers, its tier's limit and its dead who wait to come back, as of the last tick. */
	private int living;
	private int limit;
	private int housedLimit; // by the town's homes, at most the tier's limit: what the screen shows (round 1, 0.1.219)
	private int awaiting;
	/** Ticks left of "the archive does not answer" after a worker could not be put into the world. */
	private int silentTicks;
	/** The owner last sent to the clients, so a claim made after the chunk was sent reaches them too. */
	@Nullable
	private UUID sentOwner;
	/** Client side: the owner of the capsule's town as the update tag brought it, or null. */
	@Nullable
	private UUID clientOwner;
	/** Who makes the worker: the shift workers' service, or a game test's own host. Never saved. */
	private ShiftWorkerHost host = ShiftWorkers.CAPSULE_HOST;

	public ReceptionCapsuleBlockEntity(BlockPos pos, BlockState state) {
		// Three material slots; LV input on every face but the open front; a buffer for the dearest restoration.
		super(ModContent.RECEPTION_CAPSULE_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				ReceptionCapsuleRules.bufferCapacity(), EnergyTier.LV.maxVoltage(), 0L);
	}

	/** The number of the capsule's town, or 0 before it found one. */
	public int settlementId() {
		return settlementId;
	}

	/** Why the capsule stands still, or {@link Verdict#READY}, as of its last tick. */
	public Verdict verdict() {
		return verdict;
	}

	/** Ticks the current restoration has run; it goes back to 0 when a worker steps out. */
	public int restoreProgress() {
		return progress;
	}

	/** The dead worker chosen to come back, or empty — a new worker (R39). */
	public Optional<RecordNumber> returning() {
		return Optional.ofNullable(returning);
	}

	/** The town's record, read from the registry; empty before the capsule found its town, or off the server. */
	public Optional<Settlement> settlement() {
		if (settlementId <= 0 || !(level instanceof ServerLevel serverLevel)) {
			return Optional.empty();
		}
		return SettlementRegistry.get(serverLevel.getServer()).find(settlementId);
	}

	/** Hands the capsule a host of its own — a game test's, to make a release fail on purpose. Not saved. */
	public void useHost(ShiftWorkerHost replacement) {
		host = Objects.requireNonNull(replacement, "host");
		wake();
	}

	/**
	 * The next choice of whom to restore (R39): a new worker, then each of the town's dead who wait to come back in
	 * number order, then a new worker again. Called by the screen's button through the menu.
	 */
	public void nextChoice() {
		if (!(level instanceof ServerLevel serverLevel) || settlementId <= 0) {
			return;
		}
		List<RecordNumber> waiting = host.awaitingReturn(serverLevel.getServer(), settlementId);
		int at = returning == null ? -1 : waiting.indexOf(returning);
		returning = at + 1 < waiting.size() ? waiting.get(at + 1) : null;
		awaiting = waiting.size();
		setChanged();
		wake();
	}

	/** The town's owner is the capsule's owner; the placer is not recorded (two owners would be one too many). */
	@Override
	public boolean tracksOwner() {
		return false;
	}

	/** The flask above takes no EU: only the lower cell is the machine (every face but the open front). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return getBlockState().getValue(ReceptionCapsuleBlock.PART) == ReceptionCapsuleBlock.Part.LOWER
				? super.energyRoleForFace(worldFace) : EnergyRole.NONE;
	}

	/** It fills a buffer it spends per worker, so the network plans it as a store (MOD-789, ADR-049). */
	@Override
	public long perTickDemand() {
		return 0L;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)
				|| state.getValue(ReceptionCapsuleBlock.PART) != ReceptionCapsuleBlock.Part.LOWER) {
			return IDLE_SLEEP_TICKS;
		}
		Optional<Settlement> town = town(serverLevel);
		syncOwner(town);
		if (silentTicks > 0) {
			silentTicks--;
		}
		int duration = ReceptionCapsuleRules.restoreTicks();
		maxProgress = duration;
		verdict = ReceptionCapsuleRules.verdict(situation(serverLevel, town));
		int before = progress;
		progress = ReceptionCapsuleRules.advance(progress, duration, verdict);
		if (progress != before) {
			setChanged();
		}
		if (verdict == Verdict.READY && progress >= duration) {
			restore(serverLevel, pos, state, town.orElseThrow());
		}
		recordEuRate(0);
		return verdict == Verdict.READY ? 0 : IDLE_SLEEP_TICKS;
	}

	/**
	 * The capsule's town: the one it keeps, or — while it has none — the town whose territory holds it with the
	 * nearest core, lit or not. A town that left the registry (a lost save) frees the capsule to look again.
	 */
	private Optional<Settlement> town(ServerLevel level) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		if (settlementId > 0) {
			Optional<Settlement> kept = registry.find(settlementId);
			if (kept.isPresent()) {
				return kept;
			}
			settlementId = 0;
			setChanged();
		}
		Settlement nearest = null;
		double best = Double.MAX_VALUE;
		for (Settlement candidate : registry.all()) {
			if (candidate.territoryContains(level.dimension(), worldPosition)) {
				double distance = candidate.corePos().distSqr(worldPosition);
				if (distance < best) {
					best = distance;
					nearest = candidate;
				}
			}
		}
		if (nearest != null) {
			settlementId = nearest.id();
			setChanged();
		}
		return Optional.ofNullable(nearest);
	}

	/** Sends the update tag again when the town's owner changed since the clients last heard. */
	private void syncOwner(Optional<Settlement> town) {
		UUID owner = town.flatMap(Settlement::owner).orElse(null);
		if (!Objects.equals(owner, sentOwner)) {
			sentOwner = owner;
			syncBlockEntityToClient();
		}
	}

	/**
	 * What the capsule sees on this tick, for {@link ReceptionCapsuleRules#verdict}. A dead worker chosen to come
	 * back who no longer waits — brought back by another capsule — gives way to a new one.
	 */
	private ReceptionCapsuleRules.Situation situation(ServerLevel level, Optional<Settlement> town) {
		living = town.map(t -> host.livingWorkers(level.getServer(), t.id())).orElse(0);
		limit = town.map(t -> t.tier().workerLimit()).orElse(0);
		housedLimit = ReceptionCapsuleRules.housedLimit(limit,
				town.map(t -> TownHousing.homeCount(level, t.id())).orElse(0));
		List<RecordNumber> waiting = town.map(t -> host.awaitingReturn(level.getServer(), t.id())).orElse(List.of());
		awaiting = waiting.size();
		if (returning != null && !waiting.contains(returning)) {
			returning = null;
			setChanged();
		}
		boolean back = returning != null;
		return new ReceptionCapsuleRules.Situation(
				town.isPresent(),
				town.map(Settlement::corePlaced).orElse(false),
				town.map(t -> t.territoryContains(level.dimension(), worldPosition)).orElse(false),
				town.map(Settlement::active).orElse(false),
				town.flatMap(Settlement::owner).isPresent(),
				living, limit,
				energy.getAmount(), ReceptionCapsuleRules.euPrice(back),
				materialCounts(), ReceptionCapsuleRules.materialPrice(back),
				silentTicks == 0,
				back || town.map(t -> TownHousing.roomForNewWorker(level, t.id())).orElse(false));
	}

	/** The count of each material in its slot, in material order. */
	private int[] materialCounts() {
		Material[] all = Material.values();
		int[] counts = new int[all.length];
		for (int i = 0; i < all.length; i++) {
			ItemStack stack = items.get(slotOf(all[i]));
			counts[i] = stack.is(itemOf(all[i])) ? stack.getCount() : 0;
		}
		return counts;
	}

	/**
	 * The last tick of a restoration: the worker from the shift workers' service — new, or the chosen dead one
	 * brought back — and only then the price. A worker that could not be put into the world takes nothing.
	 */
	private void restore(ServerLevel level, BlockPos pos, BlockState state, Settlement town) {
		Vec3 standAt = new Vec3(pos.getX() + 0.5, pos.getY() + ReceptionCapsuleBlock.FLOOR_PX / 16.0,
				pos.getZ() + 0.5);
		float yaw = state.getValue(ReceptionCapsuleBlock.FACING).toYRot();
		RecordNumber chosen = returning;
		Optional<RecordNumber> released = chosen != null
				? (host.bringBack(level, standAt, yaw, chosen, town) ? Optional.of(chosen) : Optional.empty())
				: host.releaseNew(level, standAt, yaw, town);
		progress = 0;
		setChanged();
		if (released.isEmpty()) {
			silentTicks = ReceptionCapsuleRules.restoreTicks();
			verdict = Verdict.ARCHIVE_SILENT;
			Industrialization.LOGGER.warn("[ala-towns] settlement {}: the reception capsule at {} could not release a "
					+ "worker; nothing was taken", town.id(), pos);
			return;
		}
		boolean back = chosen != null;
		long price = ReceptionCapsuleRules.euPrice(back);
		energy.drainInternal(price);
		int[] need = ReceptionCapsuleRules.materialPrice(back);
		Material[] all = Material.values();
		for (int i = 0; i < all.length; i++) {
			if (need[i] > 0) {
				removeItem(slotOf(all[i]), need[i]);
			}
		}
		returning = null;
		living++;
		TownHousing.settle(level, town.id()); // MOD-862: the new worker moves into the free house at once
		completeOperation(level, price);
		Industrialization.LOGGER.info("[ala-towns] worker released {} ({}) by the reception capsule at {} of "
				+ "settlement {}", released.get(), back ? "brought back" : "new", pos, town.id());
	}

	/** The slot a material sits in. */
	public static int slotOf(Material material) {
		return switch (material) {
			case CIRCUIT -> CIRCUIT_SLOT;
			case RUBBER -> RUBBER_SLOT;
			case COPPER_PLATE -> COPPER_PLATE_SLOT;
		};
	}

	/** The item of a material. */
	public static Item itemOf(Material material) {
		return switch (material) {
			case CIRCUIT -> ModContent.ELECTRONIC_CIRCUIT.get();
			case RUBBER -> ModContent.RUBBER.get();
			case COPPER_PLATE -> ModContent.COPPER_PLATE.get();
		};
	}

	/** Each slot takes its own material only — what a player, a hopper or a pipe may put there. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		for (Material material : Material.values()) {
			if (slotOf(material) == slot) {
				return stack.is(itemOf(material));
			}
		}
		return false;
	}

	/** Whether {@code player} may open and read the capsule (R98): not a stranger to its town. Server side. */
	public boolean admits(Player player) {
		return SettlementGate.admits(settlement(), player);
	}

	/** Whether {@code player} may put, take and choose here (R98): a member of its town, or anybody before then. */
	public boolean mayControl(Player player) {
		return SettlementGate.controls(settlement(), player);
	}

	/**
	 * Whether {@code player} breaks this capsule when they mine it (R36): only the owner of its town, and only with
	 * a tool that mines it — any pickaxe. The server reads the owner from the registry, a client from the update tag.
	 */
	public boolean mayBeBrokenBy(Player player) {
		Optional<UUID> owner = level != null && level.isClientSide()
				? Optional.ofNullable(clientOwner)
				: settlement().flatMap(Settlement::owner);
		return owner.isPresent() && owner.get().equals(player.getUUID())
				&& player.getMainHandItem().isCorrectToolForDrops(getBlockState());
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt(KEY_SETTLEMENT, settlementId);
		if (returning != null) {
			output.putString(KEY_RETURNING, returning.toString());
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		settlementId = Math.max(0, input.getIntOr(KEY_SETTLEMENT, 0));
		returning = input.getString(KEY_RETURNING).flatMap(RecordNumber::parse).orElse(null);
		clientOwner = input.read(KEY_CLIENT_OWNER, UUIDUtil.CODEC).orElse(null);
	}

	/** The saved tag plus the town's owner, for the client's breaking prediction; the owner never reaches the save. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		CompoundTag tag = super.getUpdateTag(provider);
		settlement().flatMap(Settlement::owner).ifPresent(owner -> tag.store(KEY_CLIENT_OWNER, UUIDUtil.CODEC, owner));
		return tag;
	}

	// --- screen ---

	/**
	 * GUI channels (MOD-712, BE-7). The base four — ENERGY and CAPACITY carrying their low 15 bits, read-only,
	 * PROGRESS and MAX_PROGRESS the restoration in per mille with at least one percent once it started
	 * ({@link ReceptionCapsuleRules#shownProgress}, MOD-847) — then the high 15 bits of both and the EU price in two
	 * halves the same way: a channel syncs a short, and an operator may set a price whose buffer passes 32 767 (the
	 * menu joins the halves back). Then why the capsule stands still, the town's workers and limit, how many of each
	 * material the next worker costs, how many of the town's dead wait to come back, and the one chosen — its letter
	 * (0: a new worker) and its three digits; last, the limit by the town's homes the screen shows the workers
	 * against (owner's round 1 of 0.1.219). All computed here, so a server that retuned the price shows its own
	 * numbers.
	 */
	public enum Channel {
		ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, ENERGY_HI, CAPACITY_HI, PRICE_LO, PRICE_HI, VERDICT, LIVING, LIMIT,
		NEED_CIRCUITS, NEED_RUBBER, NEED_COPPER_PLATES, AWAITING, CHOICE_LETTER, CHOICE_DIGITS, HOUSED_LIMIT
	}

	/** Width of the channels, which the menu's client stub sizes itself from (MOD-235). */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.override(Channel.ENERGY, () -> low(energy.getAmount()))
				.override(Channel.CAPACITY, () -> low(energy.getCapacity()))
				.override(Channel.PROGRESS, () -> ReceptionCapsuleRules.shownProgress(progress,
						ReceptionCapsuleRules.restoreTicks()))
				.override(Channel.MAX_PROGRESS, () -> ReceptionCapsuleRules.SHOWN_SCALE)
				.read(Channel.ENERGY_HI, () -> high(energy.getAmount()))
				.read(Channel.CAPACITY_HI, () -> high(energy.getCapacity()))
				.read(Channel.PRICE_LO, () -> low(ReceptionCapsuleRules.euPrice(returning != null)))
				.read(Channel.PRICE_HI, () -> high(ReceptionCapsuleRules.euPrice(returning != null)))
				.read(Channel.VERDICT, () -> verdict.ordinal())
				.read(Channel.LIVING, () -> shortValue(living))
				.read(Channel.LIMIT, () -> shortValue(limit))
				.read(Channel.NEED_CIRCUITS, () -> Material.CIRCUIT.price(returning != null))
				.read(Channel.NEED_RUBBER, () -> Material.RUBBER.price(returning != null))
				.read(Channel.NEED_COPPER_PLATES, () -> Material.COPPER_PLATE.price(returning != null))
				.read(Channel.AWAITING, () -> shortValue(awaiting))
				.read(Channel.CHOICE_LETTER, () -> returning == null ? 0 : returning.index() / 1000 + 1)
				.read(Channel.CHOICE_DIGITS, () -> returning == null ? 0 : returning.digits())
				.read(Channel.HOUSED_LIMIT, () -> shortValue(housedLimit))
				.build();
	}

	/** The low 15 bits of {@code value}, clamped into what two channels carry. */
	static int low(long value) {
		return (int) (clampPair(value) & HALF_MASK);
	}

	/** The high 15 bits of {@code value}, clamped into what two channels carry. */
	static int high(long value) {
		return (int) ((clampPair(value) >> HALF_BITS) & HALF_MASK);
	}

	/** The value two channels carried: {@code high} and {@code low} joined back. */
	public static long join(int high, int low) {
		return ((long) (high & HALF_MASK) << HALF_BITS) | (low & HALF_MASK);
	}

	private static long clampPair(long value) {
		return Math.max(0L, Math.min(CHANNEL_PAIR_MAX, value));
	}

	/** A channel carries a short ({@link SyncChannels}); a count past it reads as the largest one. */
	private static int shortValue(int value) {
		return Math.max(0, Math.min(Short.MAX_VALUE, value));
	}

	/** Opens the screen for a member of the capsule's town (anybody before it is woken); a stranger is told (R98). */
	@Override
	@Nullable
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		Optional<Settlement> town = level instanceof ServerLevel serverLevel ? town(serverLevel) : Optional.empty();
		if (!SettlementGate.admitOrTell(town, player)) {
			return null;
		}
		return new ReceptionCapsuleMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
