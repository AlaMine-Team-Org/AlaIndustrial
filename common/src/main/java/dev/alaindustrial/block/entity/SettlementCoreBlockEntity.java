package dev.alaindustrial.block.entity;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.chat.ModChat;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.SettlementCoreMenu;
import dev.alaindustrial.network.SettlementBorderPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementCoreUpkeep;
import dev.alaindustrial.settlement.SettlementGate;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.SettlementTerritory;
import dev.alaindustrial.settlement.shift.ShiftWorkerBattery;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.SettlementTier;
import dev.alaindustrial.settlement.works.PlanSheetNews;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.worldgen.town.TownBuildings;
import dev.alaindustrial.worldgen.town.TownFootprint;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// size-justified: the town's one ticking block — upkeep, territory, claiming, the second-core rule, screen sync
// and its workers' recharge (MOD-854); the pure parts already live in SettlementCoreUpkeep and ShiftWorkerBattery.
/**
 * The settlement core (MOD-831; epic MOD-824, decisions R5, R36, R37, R43, R44): the heart of an ALA town, standing
 * in its town hall. While it is paid its tier's upkeep in EU it is lit and the town holds its territory; without
 * energy it is dark and there is no territory. It takes the packets of the tier above its own (R44,
 * {@link SettlementTier#intakeTier()}): an LV core accepts an MV packet.
 *
 * <p><b>What it stores.</b> Only the number of its town ({@code SettlementId}) and its upkeep state. The town
 * itself — owner, tier, style, where its core stands, whether it is lit — is a record of {@link SettlementRegistry},
 * the source of truth a shift worker or a screen reads without loading this chunk. A player's core takes its place
 * when it is placed ({@link SettlementRegistry#adopt}), every core on each tick ({@link SettlementRegistry#place});
 * every time it lights or goes dark it tells the registry.
 *
 * <p><b>Upkeep.</b> {@link SettlementCoreUpkeep} decides, tick by tick, from what the buffer holds: a lit core pays
 * its tier's EU every tick and owes what it cannot pay; owing {@code settlementCoreGraceTicks} ticks of upkeep puts
 * it out. A dark core sleeps and is woken by the next delivery, like every starving machine.
 *
 * <p><b>Owner (R37).</b> The town's owner is the first player who opens the screen of the LIT core: energy arrives
 * by cable, and a cable has no author. A dead town cannot be claimed by merely finding it, and a second player
 * cannot take a claimed town over; roles and invitations come with the clan stage of the epic (R16). The machine
 * base's own owner (the placer, for statistics) is not used: two owners on one block would be one too many.
 *
 * <p><b>Breaking (R36, R43).</b> Only the town's owner breaks the core, with any pickaxe — for everyone else it is
 * unbreakable, as the other players' shift workers are invulnerable (R34). The core drops as a plain core: it does not
 * remember its town, and the town hibernates while it has no core. Any core a player places inside that town's
 * territory continues it; a second core in a town that has one is refused at placement (the block), and one that
 * appears there otherwise — a copy — stays dark and says where the town's core stands. Outside every territory a
 * player's core belongs to no town (R53): dark, and broken by whoever placed it — by anyone when that is unknown. The
 * client learns the owner from the block entity's update tag, so the owner sees the cracks and nobody else does.
 *
 * <p>No upgrade panel ({@link NoUpgradePanel}): an overclocker would only make a fixed upkeep dearer, and a menu
 * provider without the marker grows four hidden slots a hopper could fill.
 */
public final class SettlementCoreBlockEntity extends MachineBlockEntity implements MenuProvider, NoUpgradePanel {

	/** Screen title of a claimed core: the block's name and the owner's, the way a renamed chest carries its name. */
	public static final String OWNED_TITLE_KEY = "settlement.alaindustrial.core.title_owned";
	/** The line above the hotbar of a player who has just become the town's owner. */
	public static final String CLAIMED_MESSAGE_KEY = "settlement.alaindustrial.core.claimed";
	/** The chat line of a second core in a town that has its core (R43): where that core stands. */
	public static final String MISPLACED_MESSAGE_KEY = "settlement.alaindustrial.core.misplaced";
	/** The chat line of a player's core outside every town's territory (R53): it works only in a town hall. */
	public static final String UNBOUND_MESSAGE_KEY = "settlement.alaindustrial.core.unbound";

	/** {@link Channel#MISPLACED}: the core is its town's core. */
	public static final int PLACE_HOME = 0;
	/** {@link Channel#MISPLACED}: a second core of a town whose core stands elsewhere (R43). */
	public static final int PLACE_SECOND = 1;
	/** {@link Channel#MISPLACED}: a player's core outside every territory, of no town (R53). */
	public static final int PLACE_NONE = 2;

	/** {@link Channel#OWNER}: nobody has claimed the town. */
	public static final int OWNER_NONE = 0;
	/** {@link Channel#OWNER}: the player looking at the screen owns the town. */
	public static final int OWNER_VIEWER = 1;
	/** {@link Channel#OWNER}: somebody else owns it. */
	public static final int OWNER_OTHER = 2;

	private static final String KEY_SETTLEMENT = "SettlementId";
	private static final String KEY_ACTIVE = "Active";
	private static final String KEY_DEBT = "UpkeepDebt";
	/** Written only while the core belongs to no town (R53), so a core of a town saves exactly as before. */
	private static final String KEY_UNBOUND = "Unbound";
	private static final String KEY_PLACER = "Placer";
	/** Update-tag only: the town's owner, so a client knows whose pickaxe cracks the core. Never saved. */
	private static final String KEY_CLIENT_OWNER = "SettlementOwner";

	/** The number of this core's town in {@link SettlementRegistry}; 0 until the core first takes its place. */
	private int settlementId;
	/** Whether the core is lit, and what it owes. Saved. */
	private SettlementCoreUpkeep upkeep = new SettlementCoreUpkeep();
	/** What the town's living workers drain per tick, rounded up (MOD-854); refreshed by every recharge. Not saved. */
	private long workerDemand;
	/** Lit ticks until the next recharge of the workers; the first lit tick recharges. Not saved. */
	private int rechargeCountdown;
	/** The town's tier as last read from the registry; the screen and the network's per-tick need read it. */
	private SettlementTier tier = SettlementTier.FIRST;
	/** A second core of a town whose core stands elsewhere (R43): dark, founding no town. Recomputed every tick. */
	private boolean misplaced;
	/**
	 * A player's core placed outside every territory (R53): it belongs to no town, stays dark and founds none — not
	 * even on its own tick — until it is broken and placed in a town hall. Saved.
	 */
	private boolean unbound;
	/** Who placed an unbound core: the one player who breaks it; null when unknown (copied data), and then anyone. */
	@Nullable
	private UUID placer;
	/** Client side: the town's owner as the update tag brought it, or null. */
	@Nullable
	private UUID clientOwner;
	/**
	 * Whether this core already looked for its town's structure ({@link #learnFootprint}): once per load, so a core
	 * carried out of every piece of its town does not search the chunk's structure data on every tick. Not saved.
	 */
	private boolean footprintAsked;

	public SettlementCoreBlockEntity(BlockPos pos, BlockState state) {
		// No machine slots; input on every face (no front: the core has no FACING), no output. An LV core taking the
		// packets of the tier above (R44). The buffer's intake is fixed when the block entity is built, and every core
		// is built at the first tier: when cores move up a tier (an open question of the epic), the intake follows.
		super(ModContent.SETTLEMENT_CORE_BE.get(), pos, state, EnergyTier.LV, 0,
				SettlementConfig.settlementCoreBuffer, SettlementTier.FIRST.intakeTier().maxVoltage(), 0L);
	}

	/** The core's town number, or 0 before it first took its place. */
	public int settlementId() {
		return settlementId;
	}

	/** Whether the core is lit and its town holds its territory. */
	public boolean isActive() {
		return upkeep.active();
	}

	/** Whether this is a second core of a town whose core stands elsewhere (R43). */
	public boolean isMisplaced() {
		return misplaced;
	}

	/** Whether this is a player's core outside every territory, belonging to no town (R53). */
	public boolean isUnbound() {
		return unbound;
	}

	/** The town's tier as the core last read it. */
	public SettlementTier tier() {
		return tier;
	}

	/** The town's record, read from the registry; empty before the core took its place, or off the server. */
	public Optional<Settlement> settlement() {
		if (settlementId <= 0 || !(level instanceof ServerLevel serverLevel)) {
			return Optional.empty();
		}
		return SettlementRegistry.get(serverLevel.getServer()).find(settlementId);
	}

	/** The placer is not this block's owner — the town has one in the registry (see the class comment). */
	@Override
	public boolean tracksOwner() {
		return false;
	}

	/** Takes the core's place in its town (or a new one) and remembers whether it is its town's core. */
	private SettlementRegistry.Placement settle(ServerLevel serverLevel) {
		return accept(SettlementRegistry.get(serverLevel.getServer())
				.place(serverLevel.dimension(), worldPosition, settlementId));
	}

	private SettlementRegistry.Placement accept(SettlementRegistry.Placement placement) {
		if (placement.settlementId() != settlementId) {
			settlementId = placement.settlementId();
			setChanged();
		}
		misplaced = !placement.home();
		return placement;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return IDLE_SLEEP_TICKS;
		}
		if (unbound) {
			// Of no town (R53): dark, nothing to pay, and never a town of its own — that is a later stage (R18).
			recordEuRate(0);
			updateLit(false);
			return IDLE_SLEEP_TICKS;
		}
		SettlementRegistry registry = SettlementRegistry.get(serverLevel.getServer());
		settle(serverLevel);
		Optional<Settlement> settlement = registry.find(settlementId);
		tier = settlement.map(Settlement::tier).orElse(SettlementTier.FIRST);
		if (misplaced) {
			// A second core: dark, and nothing to pay for. Its town's record is not this core's to write.
			if (upkeep.active()) {
				upkeep = new SettlementCoreUpkeep();
				setChanged();
			}
			recordEuRate(0);
			updateLit(false);
			return IDLE_SLEEP_TICKS;
		}

		settlement = learnFootprint(serverLevel, registry, settlement);
		TownBuildings.learnOnce(serverLevel, registry, settlementId, worldPosition); // MOD-857: a town of an older save
		boolean wasActive = upkeep.active();
		long drain = upkeep.tick(energy.getAmount(), energy.getCapacity(), tier.euPerTick(),
				SettlementConfig.settlementCoreGraceTicks);
		if (drain > 0) {
			energy.drainInternal(drain);
			setChanged();
		}
		boolean active = upkeep.active();
		if (active) {
			rechargeWorkers(serverLevel);
			TownWorksScanner.tick(serverLevel, settlementId); // MOD-857: a lit town compares its buildings
		}
		recordEuRate(active ? (int) Math.min(Integer.MAX_VALUE, drain) : 0);
		if (active != wasActive) {
			setChanged();
			// INFO on purpose (MOD-833): the playtest protocol of the first slice reads these lines from the log.
			Industrialization.LOGGER.info("[ala-towns] settlement {} core {} at {}: hibernation {}", settlementId,
					active ? "powered" : "dark", pos, active ? "off" : "on");
		}
		// Also when the record disagrees without a change here: a core placed back, or a save written apart.
		if (settlement.map(Settlement::active).orElse(false) != active) {
			registry.setActive(settlementId, active);
			if (active) {
				// The workers go to sleep on their own when the core goes dark; waking them is the town's (MOD-832).
				ShiftWorkers.townLit(serverLevel, settlementId);
				PlanSheetNews.coreWoke(serverLevel, settlementId); // MOD-859: where the hall's plan sheets lie
			}
		}
		updateLit(active);
		return active ? 0 : IDLE_SLEEP_TICKS;
	}

	/**
	 * A town of an older save (MOD-848): its structure registered it with a style before towns knew their footprint, so
	 * its territory is the tier's square alone, and the town's farthest buildings stand outside it (epic decision R59).
	 * Its core finds the town's structure through the chunk's structure data, once per load, and gives the town its
	 * footprint. A town no structure built has no style and is left as it is; so is a town whose core was carried out
	 * of every piece of its structure, which then keeps the square.
	 */
	private Optional<Settlement> learnFootprint(ServerLevel level, SettlementRegistry registry,
			Optional<Settlement> town) {
		if (footprintAsked || town.isEmpty() || town.get().footprint().isPresent() || town.get().style().isEmpty()) {
			return town;
		}
		footprintAsked = true;
		Optional<SettlementTerritory.Area> found = TownFootprint.at(level, worldPosition);
		if (found.isEmpty()) {
			return town;
		}
		registry.setFootprint(settlementId, found.get());
		// INFO on purpose (MOD-833): the playtest protocol of the first slice reads these lines from the log.
		Industrialization.LOGGER.info("[ala-towns] settlement {} of an older save took its town's footprint {}",
				settlementId, found.get());
		return registry.find(settlementId);
	}

	/**
	 * The block was placed by {@code player}: take the core's place at once — in the town without its core whose
	 * territory this is (R43, {@link SettlementRegistry#adopt}) — and say so if it turned out a second core, or a core
	 * of no town outside every territory (R53), which then remembers {@code player} as the one who may break it.
	 */
	public void placedBy(ServerPlayer player) {
		if (level instanceof ServerLevel serverLevel) {
			SettlementRegistry.Placement placement =
					SettlementRegistry.get(serverLevel.getServer()).adopt(serverLevel.dimension(), worldPosition);
			if (placement.settlementId() <= 0) {
				unbind(player.getUUID());
				player.sendSystemMessage(ModChat.line(Component.translatable(UNBOUND_MESSAGE_KEY)), false);
			} else {
				unbound = false;
				placer = null;
				if (!accept(placement).home()) {
					settlement().ifPresent(town -> tellCoreStands(player, town));
				}
			}
		}
		wake();
	}

	/** The core belongs to no town (R53); {@code who} placed it and is the one who may break it. */
	private void unbind(UUID who) {
		unbound = true;
		placer = who;
		settlementId = 0;
		misplaced = false;
		setChanged();
		syncBlockEntityToClient(); // the placer's client must learn that its pickaxe may crack this core
	}

	/** The chat line telling {@code player} that {@code town} already has its core, and where it stands (R43). */
	public static void tellCoreStands(ServerPlayer player, Settlement town) {
		BlockPos core = town.corePos();
		player.sendSystemMessage(ModChat.line(Component.translatable(
				MISPLACED_MESSAGE_KEY, core.getX(), core.getY(), core.getZ())), false);
	}

	/** {@code player} claims this core's town — see {@link SettlementRegistry#claim}. */
	public SettlementRegistry.ClaimResult claim(UUID player, String playerName) {
		if (settlementId <= 0 || !(level instanceof ServerLevel serverLevel)) {
			return SettlementRegistry.ClaimResult.UNKNOWN;
		}
		SettlementRegistry.ClaimResult result =
				SettlementRegistry.get(serverLevel.getServer()).claim(settlementId, player, playerName);
		if (result == SettlementRegistry.ClaimResult.CLAIMED) {
			Industrialization.LOGGER.info("[ala-towns] settlement {} claimed by {}", settlementId, playerName);
			syncBlockEntityToClient(); // the owner's client must learn that its pickaxe may now crack the core
		}
		return result;
	}

	/**
	 * Whether {@code player} breaks this core when they mine it (R36): only the town's owner, and only with a tool
	 * that mines it — any pickaxe. A core of no town (R53) is nobody's: whoever placed it breaks it, anyone when that
	 * is unknown. Asked by the block's destroy progress on both sides; the server reads the owner from the registry,
	 * a client from the update tag.
	 */
	public boolean mayBeBrokenBy(Player player) {
		if (unbound) {
			return (placer == null || placer.equals(player.getUUID()))
					&& player.getMainHandItem().isCorrectToolForDrops(getBlockState());
		}
		Optional<UUID> owner = level != null && level.isClientSide()
				? Optional.ofNullable(clientOwner)
				: settlement().flatMap(Settlement::owner);
		return owner.isPresent() && owner.get().equals(player.getUUID())
				&& player.getMainHandItem().isCorrectToolForDrops(getBlockState());
	}

	/**
	 * Whether {@code player} may open and read this core (R98): a member of its town, an inspector, or anybody while
	 * nobody has woken the town; a core of no town is open to all. Asked on opening and by the open menu every tick.
	 */
	public boolean admits(Player player) {
		return SettlementGate.admits(town(), player);
	}

	/** The town the core speaks for: its own, a second core's town (R43), none for a core of no town (R53). */
	private Optional<Settlement> town() {
		return unbound ? Optional.empty() : settlement();
	}

	/** {@link Channel#OWNER} for {@code viewer}: nobody, the viewer, or somebody else. */
	private int ownerRelation(@Nullable UUID viewer) {
		Optional<UUID> owner = settlement().flatMap(Settlement::owner);
		if (owner.isEmpty()) {
			return OWNER_NONE;
		}
		return viewer != null && owner.get().equals(viewer) ? OWNER_VIEWER : OWNER_OTHER;
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt(KEY_SETTLEMENT, settlementId);
		output.putBoolean(KEY_ACTIVE, upkeep.active());
		output.putLong(KEY_DEBT, upkeep.debt());
		if (unbound) {
			output.putBoolean(KEY_UNBOUND, true);
			if (placer != null) {
				output.store(KEY_PLACER, UUIDUtil.CODEC, placer);
			}
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		settlementId = Math.max(0, input.getIntOr(KEY_SETTLEMENT, 0));
		upkeep = new SettlementCoreUpkeep(input.getBooleanOr(KEY_ACTIVE, false), input.getLongOr(KEY_DEBT, 0L));
		clientOwner = input.read(KEY_CLIENT_OWNER, UUIDUtil.CODEC).orElse(null);
		unbound = input.getBooleanOr(KEY_UNBOUND, false);
		placer = unbound ? input.read(KEY_PLACER, UUIDUtil.CODEC).orElse(null) : null;
	}

	/** The saved tag plus the owner, for the client's breaking prediction; the owner never reaches the save. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		CompoundTag tag = super.getUpdateTag(provider);
		settlement().flatMap(Settlement::owner).ifPresent(owner -> tag.store(KEY_CLIENT_OWNER, UUIDUtil.CODEC, owner));
		return tag;
	}

	// --- screen ---

	/**
	 * GUI channels (MOD-712, BE-7): the base four, then whether the core is lit, whether it stands outside its
	 * town, the tier, the territory's width and depth (MOD-848) and the limits, its upkeep and who owns the town —
	 * all computed here, on the server, so a server that retuned its tiers shows its own numbers. Every value is a
	 * block count or a small number well inside the short a channel carries. The core's position is not among them:
	 * a roster request names no core, the server takes it from the requester's open menu (MOD-854).
	 */
	public enum Channel {
		ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, ACTIVE, MISPLACED, TIER, TERRITORY_X, TERRITORY_Z, HOUSE_LIMIT,
		WORKER_LIMIT, UPKEEP, OWNER
	}

	/** Width of the channels, which the menu's client stub sizes itself from (MOD-235). */
	public static final int DATA_COUNT = Channel.values().length;

	/** The channels as an anonymous viewer sees them: a claimed town reads {@link #OWNER_OTHER}. */
	@Override
	protected SyncChannels createChannels() {
		return coreChannels(null);
	}

	/** The channels for one viewer, who may own the town — the station screen's per-viewer idiom (MOD-093). */
	public ContainerData viewerData(UUID viewer) {
		return asContainerData(coreChannels(viewer));
	}

	private SyncChannels coreChannels(@Nullable UUID viewer) {
		return channels(Channel.class)
				.read(Channel.ACTIVE, () -> upkeep.active() ? 1 : 0)
				.read(Channel.MISPLACED, () -> unbound ? PLACE_NONE : misplaced ? PLACE_SECOND : PLACE_HOME)
				.read(Channel.TIER, () -> tier.ordinal())
				.read(Channel.TERRITORY_X, () -> shortValue(territory().sizeX()))
				.read(Channel.TERRITORY_Z, () -> shortValue(territory().sizeZ()))
				.read(Channel.HOUSE_LIMIT, () -> shortValue(tier.houseLimit()))
				.read(Channel.WORKER_LIMIT, () -> shortValue(tier.workerLimit()))
				.read(Channel.UPKEEP, () -> shortValue(tier.euPerTick()))
				.read(Channel.OWNER, () -> ownerRelation(viewer))
				.build();
	}

	/**
	 * The territory the screen measures (MOD-848): the town's, which a structure's town stretches over its buildings;
	 * for a core of no town, the square its tier would hold around it.
	 */
	private SettlementTerritory.Area territory() {
		Optional<Settlement> town = unbound ? Optional.empty() : settlement();
		return town.map(Settlement::territory).orElseGet(() -> SettlementTerritory.of(worldPosition.getX(),
				worldPosition.getZ(), tier, Optional.empty()));
	}

	/** A channel carries a short ({@link SyncChannels}); an operator's absurd radius reads as the largest one. */
	private static int shortValue(int value) {
		return Math.max(0, Math.min(Short.MAX_VALUE, value));
	}

	/** The block's name, and the owner's once the town is claimed ({@link #OWNED_TITLE_KEY}). */
	@Override
	public Component getDisplayName() {
		Optional<Settlement> settlement = settlement();
		if (settlement.isPresent() && settlement.get().owner().isPresent()
				&& !settlement.get().ownerName().isEmpty()) {
			return Component.translatable(OWNED_TITLE_KEY, settlement.get().ownerName());
		}
		return super.getDisplayName();
	}

	/**
	 * Opens the screen — and, for the first player to open a lit core, claims the town (R37). The claim comes first
	 * so the screen opens already saying "you"; only a new owner hears about it, above the hotbar. A second core says
	 * again in chat where its town's core stands, a core of no town that it works only in a town hall. The viewer's
	 * client learns which town's border the screen's "show borders" button controls (MOD-843).
	 */
	@Override
	@Nullable
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		if (!SettlementGate.admitOrTell(town(), player)) {
			return null; // a stranger to a woken town (R98): the chat says why, the screen stays shut
		}
		if (player instanceof ServerPlayer serverPlayer) {
			sendBorder(serverPlayer, false);
			if (unbound) {
				serverPlayer.sendSystemMessage(ModChat.line(Component.translatable(UNBOUND_MESSAGE_KEY)), false);
			} else if (misplaced) {
				settlement().ifPresent(town -> tellCoreStands(serverPlayer, town));
			} else if (upkeep.active() && claim(serverPlayer.getUUID(), serverPlayer.getGameProfile().name())
					== SettlementRegistry.ClaimResult.CLAIMED) {
				serverPlayer.sendSystemMessage(Component.translatable(CLAIMED_MESSAGE_KEY), true);
			}
		}
		return new SettlementCoreMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	/**
	 * Tell {@code player} the border of this core's town — its own core's position and its territory (for a second
	 * core, R43, the town's core elsewhere; MOD-848, the whole rectangle a structure's town holds) — as a press of the
	 * "show borders" button ({@code toggle}) or as the opened screen's announcement (MOD-843). A core of no town (R53)
	 * has no border and says so.
	 */
	public void sendBorder(ServerPlayer player, boolean toggle) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Optional<Settlement> town = unbound ? Optional.empty() : settlement();
		if (town.isPresent()) {
			Settlement t = town.get();
			SettlementBorderPayload.send(player, t.dimension(), t.corePos(), Optional.of(t.territory()), toggle);
		} else {
			SettlementBorderPayload.send(player, serverLevel.dimension(), worldPosition, Optional.empty(), toggle);
		}
	}

	/**
	 * Its per-tick need for the network (MOD-789, ADR-049): its tier's upkeep, not the shared machine rate, plus what
	 * its living workers drain (MOD-854) — the core pays their recharge on top of its upkeep.
	 */
	@Override
	public long perTickDemand() {
		return Math.max(1, tier.euPerTick()) + workerDemand;
	}

	/**
	 * EU the core hands the town's builder drones (MOD-858, epic decision R94): a nest's charge, or the price of a
	 * block a drone placed or took away — never more than the buffer holds above {@code settlementCoreGraceTicks}
	 * ticks of upkeep, so the drones never eat the reserve that keeps the town lit, and nothing while the core is
	 * dark or a second core. Drains what it gives; returns it.
	 */
	public long giveToDrones(long wanted) {
		if (wanted <= 0 || !upkeep.active() || misplaced || unbound) {
			return 0L;
		}
		long reserve = (long) Math.max(1, tier.euPerTick()) * Math.max(1, SettlementConfig.settlementCoreGraceTicks);
		long given = Math.min(wanted, Math.max(0L, energy.getAmount() - reserve));
		if (given > 0) {
			energy.drainInternal(given);
			setChanged();
		}
		return given;
	}

	/**
	 * Recharges the town's workers once every {@link ShiftWorkerBattery#RECHARGE_INTERVAL} lit ticks (R67):
	 * from what the buffer holds above {@code settlementCoreGraceTicks} ticks of upkeep, so the workers never eat the
	 * reserve the upkeep needs, and drains what they took on top of the upkeep.
	 */
	private void rechargeWorkers(ServerLevel level) {
		if (--rechargeCountdown > 0) {
			return;
		}
		rechargeCountdown = ShiftWorkerBattery.RECHARGE_INTERVAL;
		int living = ShiftWorkers.livingWorkers(level.getServer(), settlementId);
		workerDemand = (long) Math.ceil(living * Math.max(0.0, SettlementConfig.shiftWorkerIdleEuPerTick));
		long reserve = (long) Math.max(1, tier.euPerTick()) * Math.max(1, SettlementConfig.settlementCoreGraceTicks);
		long used = ShiftWorkerBattery.recharge(level.getServer(), settlementId,
				Math.max(0L, energy.getAmount() - reserve));
		if (used > 0) {
			energy.drainInternal(used);
			setChanged();
		}
	}
}
