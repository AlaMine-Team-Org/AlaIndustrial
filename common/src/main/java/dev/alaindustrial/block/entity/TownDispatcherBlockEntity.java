package dev.alaindustrial.block.entity;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.block.entity.machine.TownBinding;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.misc.HousePlanItem;
import dev.alaindustrial.item.misc.PlanSheet;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.item.misc.RestorationPlanItem;
import dev.alaindustrial.item.misc.TownHallPlanSheetItem;
import dev.alaindustrial.menu.TownDispatcherMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementGate;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DispatcherBuffer;
import dev.alaindustrial.settlement.drone.DispatcherSpot;
import dev.alaindustrial.settlement.drone.DispatcherStatus;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.DispatcherViews;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import dev.alaindustrial.settlement.drone.HallBuildNews;
import dev.alaindustrial.settlement.drone.StalledOrders;
import dev.alaindustrial.settlement.drone.StartReadiness;
import dev.alaindustrial.settlement.drone.TownDroneRoster;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.HousePlans;
import dev.alaindustrial.settlement.works.MaterialSource;
import dev.alaindustrial.settlement.works.TownOrders;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJournal;
import dev.alaindustrial.settlement.works.WorkJournalCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// size-justified: the construction desk of a town — its slots, the plan assembly, the start, the orders'
// presses and the house plans it teaches; the rules live in DispatcherViews, TownOrders and PlanSheetRules.
/**
 * The town dispatcher (MOD-858; epic MOD-824, decisions R84, R91, R92, R95): the construction desk of an ALA town,
 * standing in its town hall. It puts the restoration plan together from three sheets (MOD-859), holds the material
 * buffer the builder drones take their blocks from ({@link #buffer()}), and shows whether the town's drones work.
 *
 * <p><b>Its town</b> is found and kept as the reception capsule does ({@link TownBinding}). It serves the town only
 * standing inside the town's hall ({@link TownHall#contains}) and only as the town's one dispatcher — the first that
 * reported in this run of the server ({@link TownDroneRoster}); a second one stands idle and says so.
 *
 * <p><b>Who controls it (R95, R98).</b> The town's restorer puts and takes items and assembles the plan; an operator
 * in creative opens the screen and only looks; a stranger to the town does not open it at all ({@link #admits}). The
 * slots check the player ({@link #mayControl}); the buffer stays open to hoppers and pipes, the sheet and plan slots
 * do not take them.
 *
 * <p>No energy: the drones charge from the settlement core through their nests. No upgrade panel.
 */
public final class TownDispatcherBlockEntity extends MachineBlockEntity implements MenuProvider, NoUpgradePanel {

	/** The three sheet slots. */
	public static final int SHEET_SLOT_FIRST = 0;
	public static final int SHEET_SLOTS = PlanSheetRules.SHEETS;
	/** The restoration plan's slot. */
	public static final int PLAN_SLOT = SHEET_SLOT_FIRST + SHEET_SLOTS;
	/** The material buffer: three rows of nine (R84). */
	public static final int BUFFER_FIRST = PLAN_SLOT + 1;
	public static final int BUFFER_SIZE = 27;
	public static final int SLOT_COUNT = BUFFER_FIRST + BUFFER_SIZE;

	/** Ticks between two looks at its town: well under the roster's {@link TownDroneRoster#ALIVE_TICKS}. */
	public static final int TICK_INTERVAL = 20;

	/** The assembly's refusal when the plan slot is not empty; the others are {@link PlanSheetRules.Reason}s. */
	public static final String PLAN_SLOT_FULL_KEY = "settlement.alaindustrial.dispatcher.plan_slot_full";
	/** Channel value of «no refusal»; a reason is its ordinal plus one, the full plan slot after them. */
	public static final int REASON_NONE = 0;
	public static final int REASON_PLAN_SLOT_FULL = PlanSheetRules.Reason.values().length + 1;

	private static final String KEY_JOURNAL = "Journal";
	/** Update-tag only: the tabs' lines. Never saved. */
	private static final String KEY_VIEW = "View";

	private final TownBinding binding = new TownBinding();
	private final DispatcherBuffer buffer = new DispatcherBuffer(this, BUFFER_FIRST, BUFFER_SIZE, this::setChanged);
	private DispatcherStatus status = DispatcherStatus.NO_TOWN;
	/** The tier's nest limit, the counted nests (a drone, over the hall) and the busy ones, as of the last look. */
	private int nestLimit;
	private int readyNests;
	private int busyNests;
	/** The last assembly's refusal ({@link #REASON_NONE}, a reason's ordinal + 1, or the full plan slot). */
	private int lastReason = REASON_NONE;
	/** The sheet numbers the last refusal found missing, one bit per number (bit 0 — sheet 1). */
	private int lastMissing;
	/** What the drones did, newest last (MOD-860); saved as an optional field — an old save reads an empty one. */
	private WorkJournal journal = new WorkJournal();
	/** The «Task» and «Materials» tabs, worked out on every look; carried in the update tag only. */
	private DispatcherView view = DispatcherView.EMPTY;
	/**
	 * The order the «Task» and «Materials» tabs show (round 2 of 0.1.219, Q1), by building index, or -1 — the first
	 * order. Picked on the «Orders» tab; not saved: a reload shows the first order again.
	 */
	private int selected = -1;
	/** The watch over orders that ended with their building unfinished (round 5 of 0.1.219). Not saved. */
	private final StalledOrders stalledOrders = new StalledOrders();

	public TownDispatcherBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.TOWN_DISPATCHER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT, 0L, 0L, 0L);
		journal.onChange(this::setChanged);
	}

	/**
	 * Tells the material stages of MOD-860 where a town's materials are: the buffer of its working dispatcher, while
	 * that dispatcher's chunk is loaded and it serves its town. Installed once, at server start (ServerHookRoster).
	 */
	public static void installMaterialSource(MinecraftServer ignored) {
		BuildMaterials.installSourceLookup(TownDispatcherBlockEntity::workingDispatcher);
	}

	/** The buffer of town {@code settlementId}'s working dispatcher, if it is loaded and serves its town. */
	private static Optional<MaterialSource> workingDispatcher(ServerLevel level, int settlementId) {
		return of(level, settlementId).map(TownDispatcherBlockEntity::buffer);
	}

	/** Town {@code settlementId}'s working dispatcher, if it is loaded and serves its town. */
	public static Optional<TownDispatcherBlockEntity> of(ServerLevel level, int settlementId) {
		OptionalLong pos = SettlementRegistry.get(level.getServer()).drones().dispatcherOf(settlementId,
				level.getGameTime());
		if (pos.isEmpty()) {
			return Optional.empty();
		}
		BlockPos at = BlockPos.of(pos.getAsLong());
		if (!level.isLoaded(at) || !(level.getBlockEntity(at) instanceof TownDispatcherBlockEntity dispatcher)
				|| !dispatcher.status.serves() || dispatcher.settlementId() != settlementId) {
			return Optional.empty();
		}
		return Optional.of(dispatcher);
	}

	/** The work journal the drones write to (MOD-860). */
	public WorkJournal journal() {
		return journal;
	}

	/** The «Task» and «Materials» tabs as the last look worked them out (on a client: as the update tag brought). */
	public DispatcherView view() {
		return view;
	}

	/** The order the «Task» and «Materials» tabs show, by building index, or -1 — the first order. */
	public int selected() {
		return selected;
	}

	/** The material buffer the builder drones take their blocks from (R84). */
	public DispatcherBuffer buffer() {
		return buffer;
	}

	/** The restoration plan in the plan slot, if there is one with a target. */
	public Optional<RestorationPlan> plan() {
		return Optional.ofNullable(RestorationPlanItem.planOf(getItem(PLAN_SLOT)));
	}

	/** What the dispatcher did on its last look. */
	public DispatcherStatus status() {
		return status;
	}

	/** The number of the dispatcher's town, or 0. */
	public int settlementId() {
		return binding.id();
	}

	/** Whether {@code player} may put, take and assemble here (R95): the town's owner, or anyone in a town of none. */
	public boolean mayControl(Player player) {
		return binding.mayControl(level, player);
	}

	/**
	 * Whether {@code player} may open and read the dispatcher at all (R98): not a stranger to its town. A stranger's
	 * menu is never opened, and one left open closes ({@code TownDispatcherMenu#stillValid}).
	 */
	public boolean admits(Player player) {
		return binding.admits(level, player);
	}

	/**
	 * {@code placer} has just put the dispatcher here (MOD-891, R158): it finds its town now, and on a cell of the
	 * town's hall where the plan wants a block the placer hears that it will stop the restoration, and where its
	 * place is ({@link DispatcherSpot#placed}).
	 */
	public void placedBy(ServerPlayer placer) {
		if (level instanceof ServerLevel serverLevel) {
			binding.resolve(serverLevel, worldPosition, this::setChanged).ifPresent(town -> DispatcherSpot
					.placed(serverLevel, town.id(), worldPosition, getBlockState(), placer));
		}
	}

	/** Whether {@code player} breaks this block: the town's owner, or anyone while the town has none. */
	public boolean mayBeBrokenBy(Player player) {
		return binding.mayControl(level, player);
	}

	@Override
	public boolean tracksOwner() {
		return false;
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.NONE;
	}

	@Override
	public long perTickDemand() {
		return 0L;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return IDLE_SLEEP_TICKS;
		}
		Optional<Settlement> town = binding.resolve(serverLevel, pos, this::setChanged);
		if (binding.ownerChanged(town)) {
			syncBlockEntityToClient();
		}
		DispatcherStatus before = status;
		status = look(serverLevel, pos, town);
		if (status != before) {
			setChanged();
		}
		updateLit(status == DispatcherStatus.WORKING);
		if (status.serves() && town.isPresent()) {
			recordFoundSheets(serverLevel, town.get());
			learnHousePlan(serverLevel, town.get());
			stalledOrders.check(serverLevel, town.get(), serverLevel.getGameTime());
		}
		DispatcherView next = DispatcherViews.of(serverLevel, this, town);
		if (!next.equals(view) || status != before) {
			view = next;
			syncBlockEntityToClient();
		}
		recordEuRate(0);
		return TICK_INTERVAL;
	}

	/**
	 * The town finds the plan sheets that lie here (MOD-859): a sheet of the town's style in a sheet slot, or all
	 * three when the plan slot holds the town's hall plan. Its own slots only; a found sheet stays found, so taking it
	 * out again changes nothing. What the core's «Construction» tab lists as found.
	 */
	private void recordFoundSheets(ServerLevel level, Settlement town) {
		String style = town.style();
		List<PlanSheetRules.Sheet> sheets = new ArrayList<>();
		for (int i = 0; i < SHEET_SLOTS; i++) {
			PlanSheet sheet = TownHallPlanSheetItem.sheetOf(getItem(SHEET_SLOT_FIRST + i));
			if (sheet != null) {
				sheets.add(sheet.asRuleSheet());
			}
		}
		int mask = PlanSheetRules.foundMask(sheets, style);
		Optional<RestorationPlan> plan = plan();
		if (plan.isPresent() && PlanSheetRules.TOWN_HALL.equals(plan.get().building())
				&& (style.isEmpty() || style.equals(plan.get().style().toString()))) {
			mask = PlanSheetRules.ALL_FOUND;
		}
		if (mask != 0) {
			SettlementRegistry.get(level.getServer()).works().town(town.id()).markSheetsFound(mask);
		}
	}

	/**
	 * A house plan (or the warehouse plan, MOD-892) of the town's style put into the plan slot is learned by the town
	 * and used up (round 2 of 0.1.219, Q4): the slot is the town hall's, a house plan only passes through it. A plan
	 * of another style, or one the town already knows, stays where it is.
	 */
	private void learnHousePlan(ServerLevel level, Settlement town) {
		ItemStack stack = getItem(PLAN_SLOT);
		RestorationPlan plan = stack.getItem() instanceof HousePlanItem ? RestorationPlanItem.planOf(stack) : null;
		if (plan == null || !PlanSheetRules.learned(plan.building())
				|| !town.style().isEmpty() && !town.style().equals(plan.style().toString())) {
			return;
		}
		// A plan the town knows stays in the slot, as on the core: the sheet is the player's, not used up for nothing.
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.id());
		if (HousePlans.knows(works, TownHall.index(works), plan.template())) {
			return;
		}
		removeItem(PLAN_SLOT, 1);
		HallBuildNews.housePlanLearned(level, town.id(), plan.template());
	}

	/** How many nests the core's tier lets work, as of the last look. */
	public int nestLimit() {
		return nestLimit;
	}

	/** How many nests with a drone stand over the hall and work — never more than the limit — as of the last look. */
	public int readyNests() {
		return readyNests;
	}

	public int busyNests() {
		return busyNests;
	}

	private DispatcherStatus look(ServerLevel level, BlockPos pos, Optional<Settlement> town) {
		nestLimit = 0;
		readyNests = 0;
		busyNests = 0;
		if (town.isEmpty()) {
			return DispatcherStatus.NO_TOWN;
		}
		int id = town.get().id();
		if (!TownHall.contains(level, id, pos)) {
			return DispatcherStatus.OUTSIDE_HALL;
		}
		TownDroneRoster roster = SettlementRegistry.get(level.getServer()).drones();
		if (!roster.dispatcherCheckIn(id, pos.asLong(), level.getGameTime())) {
			return DispatcherStatus.SECOND;
		}
		nestLimit = DroneNestRules.nestLimit(town.get().tier(), TownHall.restored(level, id));
		readyNests = Math.min(nestLimit, roster.nests(id, level.getGameTime()));
		busyNests = roster.busyNests(id, level.getGameTime(), nestLimit);
		return busyNests > 0 ? DispatcherStatus.WORKING : DispatcherStatus.IDLE;
	}

	/**
	 * «Assemble the plan», pressed by {@code player}: one sheet from each sheet slot makes the restoration plan in the
	 * plan slot ({@link PlanSheetRules#assemble}), if the sheets are three of the town's style and the plan slot is
	 * empty; otherwise nothing is taken and the screen says why. Only a player who controls the dispatcher.
	 */
	public void assemble(Player player) {
		if (!mayControl(player) || level == null || level.isClientSide()) {
			return;
		}
		lastMissing = 0;
		if (!getItem(PLAN_SLOT).isEmpty()) {
			lastReason = REASON_PLAN_SLOT_FULL;
			setChanged();
			return;
		}
		List<PlanSheetRules.Sheet> sheets = new ArrayList<>();
		for (int i = 0; i < SHEET_SLOTS; i++) {
			ItemStack stack = getItem(SHEET_SLOT_FIRST + i);
			if (!stack.isEmpty()) {
				PlanSheet sheet = TownHallPlanSheetItem.sheetOf(stack);
				sheets.add(sheet == null ? new PlanSheetRules.Sheet(null, 0)
						: new PlanSheetRules.Sheet(sheet.style().toString(), sheet.sheet()));
			}
		}
		String style = binding.find(level).map(Settlement::style).filter(s -> !s.isEmpty()).orElse(null);
		PlanSheetRules.Result result = PlanSheetRules.assemble(sheets, style);
		RestorationPlan plan = result.ok() ? RestorationPlan.of(result.plan()) : null;
		if (plan == null) {
			lastReason = result.reason() == null ? REASON_NONE : result.reason().ordinal() + 1;
			for (int number : result.missing()) {
				lastMissing |= 1 << (number - 1);
			}
			setChanged();
			return;
		}
		for (int i = 0; i < SHEET_SLOTS; i++) {
			removeItem(SHEET_SLOT_FIRST + i, 1);
		}
		setItem(PLAN_SLOT, RestorationPlanItem.of(plan));
		lastReason = REASON_NONE;
		setChanged();
	}

	/**
	 * «Start the construction», pressed by {@code player} on the «Task» tab (owner's round 1): raises a clearing and a
	 * building job on the town hall in the core's queue — what the core's «Construction» tab's two buttons do. The
	 * server asks {@link StartReadiness} again (owner's round 2): only the town's owner, a hall that can be restored
	 * and has no open job, the hall's plan in the plan slot and a nest with a drone over the hall; anything else
	 * changes nothing. Missing materials do not stop it: the clearing comes first and takes none.
	 */
	public void startConstruction(Player player) {
		if (!mayControl(player) || !(level instanceof ServerLevel serverLevel) || !status.serves()) {
			return;
		}
		DispatcherView now = DispatcherViews.of(serverLevel, this, binding.find(serverLevel));
		if (!StartReadiness.blockers(true, now.checklist(),
				plan().map(p -> p.template().toString()).orElse(null), readyNests).isEmpty()) {
			view = now;
			syncBlockEntityToClient();
			return;
		}
		int id = binding.id();
		TownWorks works = SettlementRegistry.get(serverLevel.getServer()).works().town(id);
		OptionalInt hall = TownHall.index(works);
		Optional<Settlement> town = binding.find(serverLevel);
		if (hall.isEmpty() || town.isEmpty() || works.buildings().get(hall.getAsInt()).intact().isEmpty()) {
			return;
		}
		// The hall's order (round 2 of 0.1.219, Q1): its clearing and building, first in the list.
		Optional<String> refused = TownOrders.place(serverLevel, town.get(), hall.getAsInt(),
				player.getName().getString());
		if (refused.isPresent()) {
			// Nothing raised (round 5 of 0.1.219): the hall's state may lag behind the world — measure it now, so the
			// screen shows what is true rather than a «Start» that did nothing.
			TownWorksScanner.measureNow(serverLevel, id, hall.getAsInt());
			Industrialization.LOGGER.info("[ala-towns] settlement {}: {} pressed Start, nothing was raised: {}", id,
					player.getName().getString(), refused.get());
		}
		works.moveOrder(hall.getAsInt(), -works.orders().size());
		selected = hall.getAsInt();
		view = DispatcherViews.of(serverLevel, this, town);
		syncBlockEntityToClient();
	}

	/** What a press on one order of the «Orders» tab does (round 2 of 0.1.219, Q3). */
	public enum OrderPress {
		/** Show this order on the «Task» and «Materials» tabs. Anybody who may read the dispatcher. */
		SELECT,
		/** One place earlier. The town's owner only. */
		UP,
		/** One place later. The town's owner only. */
		DOWN,
		/** Call the order off. The town's owner only. */
		CANCEL
	}

	/** {@code player} pressed {@code press} on the order of building {@code building}. */
	public void pressOrder(Player player, int building, OrderPress press) {
		if (!(level instanceof ServerLevel serverLevel) || !status.serves() || !admits(player)) {
			return;
		}
		TownWorks works = SettlementRegistry.get(serverLevel.getServer()).works().town(binding.id());
		if (!works.orders().contains(building)) {
			return;
		}
		if (press == OrderPress.SELECT) {
			selected = building;
		} else if (!mayControl(player)) {
			return;
		} else if (press == OrderPress.CANCEL) {
			TownOrders.cancel(works, building);
			Industrialization.LOGGER.info("[ala-towns] settlement {}: {} called off the order of building {}",
					binding.id(), player.getName().getString(), building);
		} else {
			works.moveOrder(building, press == OrderPress.UP ? -1 : 1);
		}
		view = DispatcherViews.of(serverLevel, this, binding.find(serverLevel));
		syncBlockEntityToClient();
	}

	/**
	 * What a hopper or a pipe may put where: materials into the buffer, nothing into the sheet and plan slots — those
	 * are the town owner's to fill by hand (the menu's slots check the player).
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot >= BUFFER_FIRST && slot < SLOT_COUNT && !isPlanPaper(stack);
	}

	/**
	 * Whether {@code stack} belongs in slot {@code slot} by hand: sheet N in sheet slot N only (owner's round 1 of
	 * 0.1.219: an empty slot shows the number still missing; a blank sheet fits none), the plan, or any material. A
	 * sheet or the
	 * plan never goes into the buffer (owner's round 1: a plan moved there looked put away, while the drones read it
	 * only from the plan slot).
	 */
	public static boolean fitsByHand(int slot, ItemStack stack) {
		if (slot >= SHEET_SLOT_FIRST && slot < PLAN_SLOT) {
			return TownHallPlanSheetItem.fitsSheetSlot(stack, slot - SHEET_SLOT_FIRST);
		}
		if (slot == PLAN_SLOT) {
			return stack.is(ModContent.RESTORATION_PLAN.get()) || stack.is(ModContent.HOUSE_PLAN.get());
		}
		return slot >= BUFFER_FIRST && slot < SLOT_COUNT && !isPlanPaper(stack);
	}

	private static boolean isPlanPaper(ItemStack stack) {
		return stack.is(ModContent.TOWN_HALL_PLAN_SHEET.get()) || stack.is(ModContent.RESTORATION_PLAN.get())
				|| stack.is(ModContent.HOUSE_PLAN.get());
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		binding.save(output);
		if (!journal.isEmpty()) {
			output.store(KEY_JOURNAL, WorkJournalCodec.CODEC, journal);
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		binding.load(input);
		journal = input.read(KEY_JOURNAL, WorkJournalCodec.CODEC).orElseGet(WorkJournal::new);
		journal.onChange(this::setChanged);
		view = input.read(KEY_VIEW, DispatcherView.CODEC).orElse(DispatcherView.EMPTY);
		// A save from before sheet N went into slot N only: every sheet back into its own slot, nothing dropped.
		TownHallPlanSheetItem.sortIntoOwnSlots(items, SHEET_SLOT_FIRST);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		CompoundTag tag = super.getUpdateTag(provider);
		binding.writeUpdate(tag, level);
		tag.store(KEY_VIEW, DispatcherView.CODEC, view);
		return tag;
	}

	// --- screen ---

	/**
	 * GUI channels (MOD-712): the base four (no energy, no progress), then the status, the tier's nest limit and the
	 * busy nests, the last assembly's refusal and the missing sheet numbers, whether the viewer controls the dispatcher
	 * — per viewer, as the settlement core's owner channel (in a town of no owner, everybody does) — and the counted
	 * nests: a drone in each, over the hall (owner's round 1).
	 */
	public enum Channel {
		ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, STATUS, NEST_LIMIT, BUSY_NESTS, REASON, MISSING, CONTROL, READY_NESTS
	}

	/** Width of the channels, which the menu's client stub sizes itself from (MOD-235). */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return dispatcherChannels(null);
	}

	/** The channels for one viewer: {@link Channel#CONTROL} says whether that viewer may change anything. */
	public ContainerData viewerData(UUID viewer) {
		return asContainerData(dispatcherChannels(viewer));
	}

	private SyncChannels dispatcherChannels(@Nullable UUID viewer) {
		return channels(Channel.class)
				.read(Channel.STATUS, () -> status.ordinal())
				.read(Channel.NEST_LIMIT, () -> Math.min(Short.MAX_VALUE, nestLimit))
				.read(Channel.BUSY_NESTS, () -> Math.min(Short.MAX_VALUE, busyNests))
				.read(Channel.REASON, () -> lastReason)
				.read(Channel.MISSING, () -> lastMissing)
				.read(Channel.CONTROL, () -> binding.owner(level).map(owner -> owner.equals(viewer)).orElse(true)
						? 1 : 0)
				.read(Channel.READY_NESTS, () -> Math.min(Short.MAX_VALUE, readyNests))
				.build();
	}

	/** Opens the screen for a member of the town (or anybody before it is woken); a stranger is told why not (R98). */
	@Override
	@Nullable
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		if (!SettlementGate.admitOrTell(binding.resolveNow(level, worldPosition, this::setChanged), player)) {
			return null;
		}
		return new TownDispatcherMenu(syncId, inventory, this, viewerData(player.getUUID()),
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
