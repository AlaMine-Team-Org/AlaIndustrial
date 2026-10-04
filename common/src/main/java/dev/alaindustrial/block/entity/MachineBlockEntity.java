package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.machine.BatteryDrawer;
import dev.alaindustrial.block.entity.machine.ChannelBridge;
import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.block.entity.machine.MachineInventory;
import dev.alaindustrial.block.entity.machine.MachineTelemetry;
import dev.alaindustrial.block.entity.machine.OwnerRecord;
import dev.alaindustrial.block.entity.machine.SlotLayout;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.block.entity.machine.UpgradePanel;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.machine.MachineRates;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// size-justified: what is left is the base's own job — the persistence order, the default title, role and
// GUI channels, the WorldlyContainer and tick hooks — plus one-line delegates to the components (ADR-042)
// that keep every call site, the characterization gametests included, saying machine.activeTicks().
/**
 * The base of every Industrialization machine: the energy half inherited from {@link EnergyBlockEntity}
 * plus an item inventory, a generic processing progress counter and live GUI sync.
 *
 * <p>To add a new machine, subclass this, pass slot count / tier / capacity / I-O limits to
 * the constructor, and implement {@link #onServerTick}. The base handles inventory
 * ({@link Container}), NBT persistence (via {@link ValueInput}/{@link ValueOutput}), the
 * {@link #getDataAccess()} channels that sync energy + progress to an open screen, the screen title (the
 * block's own name) and the consumer energy role (every face but the front) — override those two only
 * when the machine is not the usual case. A machine with GUI channels of its own declares them once, as
 * an enum starting with the four of {@link MachineChannels}, and binds them in {@link #createChannels()}
 * on {@link SyncChannels}; its menu reads them by name (MOD-712 BE-7, ADR-042).
 *
 * <p><b>Cross-cutting capabilities are components (MOD-712, BE-1)</b> in
 * {@code dev.alaindustrial.block.entity.machine}, each held in one field here with thin delegates, so a
 * call site keeps saying {@code machine.activeTicks()}: {@link SlotLayout} (where the machine's own slots,
 * the upgrade block and the battery drawer sit — read by the menu too), {@link MachineInventory} (what a
 * change to the container does, the automation faces, {@code Items}), {@link MachineTelemetry}
 * (statistics, {@code Stats*} keys), {@link OwnerRecord} ({@code Owner}/{@code OwnerName}),
 * {@link UpgradePanel} (mute and statistics chips, overclocker), {@link BatteryDrawer} (the drawer's
 * drain), and the static {@link dev.alaindustrial.block.entity.machine.EvolutionHelper} that the four
 * evolving machines call by name. A new capability is a new component there plus one field here — not
 * more code in this class.
 * The processing tick itself is the separate {@link ProcessingCycle} a machine composes (ADR-021); its
 * end, shared with the four machines that keep their own loop, is {@link #completeOperation}.
 *
 * <p><b>A machine, not merely a powered block (MOD-400).</b> Transport blocks — the cable and the two
 * pipes — extend {@link EnergyBlockEntity} directly: they have no inventory, no progress, no upgrade
 * panel and no owner, so nothing here applies to them. Everything in this class may therefore assume
 * it is looking at a real machine.
 */
public abstract class MachineBlockEntity extends EnergyBlockEntity implements WorldlyContainer {

	/** Upgrade slots appended to the tail of every GUI machine's inventory (MOD-080). */
	public static final int UPGRADE_SLOT_COUNT = UpgradePanel.SLOT_COUNT;
	/** The active upgrade slot on the MVP panel (upgrade-block index 0); the mute chip goes here. */
	public static final int ACTIVE_UPGRADE_INDEX = 0;

	protected final NonNullList<ItemStack> items;
	/** Count of machine-specific slots (indices 0..baseSlots-1); upgrade slots follow at the tail. */
	protected final int baseSlots;
	/** Where the machine's own slots, the upgrade block and the battery drawer sit in {@link #items}. */
	private final SlotLayout layout;
	/** What taking, placing and clearing a stack does, and which slots each face exposes (MOD-712, BE-1). */
	private final MachineInventory inventory;
	protected int progress;
	protected int maxProgress;

	/** Who placed this machine (MOD-133), under {@code Owner}/{@code OwnerName}; see {@link #tracksOwner()}. */
	private final OwnerRecord owner = new OwnerRecord();

	/** The upgrade panel's chips and what they do (MOD-080/125/392, MOD-712 BE-1); its slots live in {@link #items}. */
	private final UpgradePanel upgrades;

	/** The battery drawer's drain (MOD-679, MOD-712 BE-1); its slot is the last of {@link #items}. */
	private final BatteryDrawer drawer;

	protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, int slots, long capacity, long maxInsert, long maxExtract) {
		super(type, pos, state, tier, capacity, maxInsert, maxExtract);
		this.baseSlots = slots;
		// Every GUI machine (a MenuProvider with a panel) gets four upgrade slots at the tail of `items` (MOD-080),
		// and a battery-fed one the drawer after them (MOD-679) — see SlotLayout for why in that order.
		// `this instanceof` sees the concrete subclass throughout super-construction.
		this.layout = SlotLayout.of(slots, this instanceof MenuProvider, hasUpgradePanel(), this instanceof BatteryFed);
		this.items = NonNullList.withSize(layout.size(), ItemStack.EMPTY);
		this.inventory = new MachineInventory(items, slots, this::contentsChanged, this::wake);
		this.upgrades = new UpgradePanel(this, items, layout.upgradeStart(), layout.batterySlot());
		this.drawer = new BatteryDrawer(layout, items, energy, tier);
	}

	/**
	 * Whether this block gets the four upgrade slots: every GUI machine (MOD-080) but a
	 * {@link NoUpgradePanel} — a panel is a promise that the upgrades do something here. A marker rather
	 * than an override so the client menu, which has no block entity, reaches the same answer
	 * ({@link SlotLayout#hasPanel}).
	 */
	public final boolean hasUpgradePanel() {
		return !(this instanceof NoUpgradePanel);
	}

	/** Where this machine's slots sit; the menu reads the same object server-side (MOD-712, BE-1). */
	public SlotLayout slotLayout() {
		return layout;
	}

	// --- Ownership (MOD-133): who placed this machine, for per-player statistics/XP ---

	/**
	 * Whether this machine records its placer as {@code owner}. Default {@code true} for every
	 * working machine, generator and storage block: they all earn player stats.
	 *
	 * <p>Before MOD-400 this also existed so the transport blocks could opt out — carrying a per-segment
	 * UUID on the most numerous block in a base is pure NBT ballast. They are no longer machines at all,
	 * so nothing overrides this today; it stays as the seam for a machine that must not record a placer.
	 * When false, {@code owner} is neither set at placement nor persisted.
	 */
	public boolean tracksOwner() {
		return true;
	}

	/** Set at placement (and re-place); a null UUID clears ownership. Persisted when {@link #tracksOwner()}. */
	public void setOwner(@Nullable UUID owner, @Nullable String ownerName) {
		this.owner.set(owner, ownerName);
		setChanged();
	}

	/** The placer's UUID, or null for a machine placed by non-player means. */
	@Nullable
	public UUID getOwner() {
		return owner.uuid();
	}

	/** The placer's name snapshot, or {@code ""} when there is no owner. */
	public String getOwnerName() {
		return owner.name();
	}

	/** True when {@code player} is this machine's owner. */
	public boolean isOwner(UUID player) {
		return owner.is(player);
	}

	/** Credit one completed operation's EU cost to the owner (MOD-133): see {@link OwnerRecord#creditUsefulWork}. */
	protected void creditUsefulWork(Level level, long euCost) {
		owner.creditUsefulWork(level, euCost);
	}

	/**
	 * The shared end of one completed operation (MOD-712, BE-3): count it in the lifetime statistics
	 * (MOD-125) and credit its EU cost to the owner (MOD-133) — the mod's only XP source, paid per completed
	 * operation and never per tick, so a contraption that aborts one mid-run burns EU and earns nothing.
	 * {@code ProcessingCycle} ends every operation through this, and so do the four machines with a cycle of
	 * their own (assembler, incubator, distillation column, thermal centrifuge). A non-positive
	 * {@code euCost} counts the operation and credits nothing — an incubator attempt that missed.
	 */
	protected final void completeOperation(Level level, long euCost) {
		recordItemProcessed();
		creditUsefulWork(level, euCost);
	}

	/** One operation tick's energy, Mechanic skills included (MOD-483, MOD-712 D4): see {@link OperationEnergy}. */
	protected final boolean spendOperationEnergy(Level level, int euPerTick, boolean canWork, boolean ready) {
		return OperationEnergy.spend(this, level, euPerTick, canWork, ready);
	}

	/**
	 * The screen title of a machine that opens a menu: its block's own name, so the title and the item in
	 * the player's hand can never disagree (MOD-712, BE-10). The machines whose screen carries another
	 * title (the upgrade table) override it; a block entity serving several blocks names each of them.
	 */
	public Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	/**
	 * A machine is a consumer: it takes EU on every face but its {@code FACING} front, which stays inert
	 * (R-NRG-03, MOD-712 BE-10). Generators, storage and the machines with a layout of their own override
	 * this; before it was the default, fourteen consumers each wrote it out.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	/**
	 * The GUI sync channels a {@code MachineMenu} binds (MOD-235, MOD-712 BE-7): built once, on first use,
	 * from {@link #createChannels()}.
	 */
	public final ContainerData getDataAccess() {
		return channelBridge.data();
	}

	/** {@code channels} as the vanilla {@link ContainerData} a menu binds: see {@link ChannelBridge#of}. */
	protected static ContainerData asContainerData(SyncChannels channels) {
		return ChannelBridge.of(channels);
	}

	/** Builds {@link #getDataAccess()} on first use; a server-side object, a client menu has a stub. */
	private final ChannelBridge channelBridge = new ChannelBridge(this::createChannels);

	/**
	 * This machine's channels. The default is the four of {@link MachineChannels}; a machine with channels of
	 * its own declares them in an enum that starts with those four and binds the rest on {@link #channels}.
	 */
	protected SyncChannels createChannels() {
		return channels(MachineChannels.class).build();
	}

	/**
	 * A channel builder over {@code type} with the four base channels already bound: energy and capacity
	 * (clamped to {@code int}), progress and its length. Energy, progress and length take a write.
	 */
	protected final <C extends Enum<C>> SyncChannels.Builder<C> channels(Class<C> type) {
		return SyncChannels.of(type)
				.inherit(MachineChannels.ENERGY, () -> SyncChannels.clampInt(energy.getAmount()),
						value -> energy.setAmountUntracked(value))
				.inherit(MachineChannels.CAPACITY, () -> SyncChannels.clampInt(energy.getCapacity()), null)
				.inherit(MachineChannels.PROGRESS, () -> progress, value -> progress = value)
				.inherit(MachineChannels.MAX_PROGRESS, () -> maxProgress, value -> maxProgress = value);
	}

	/**
	 * How many sync channels the base projects — read by both sides (MOD-235): the block entity's
	 * {@code getCount()} and a <b>client</b> menu stub's width come from the same enum, so they cannot drift.
	 * A machine with channels of its own has its own {@code DATA_COUNT}, the size of its channel enum.
	 */
	public static final int DATA_COUNT = MachineChannels.values().length;

	// --- persistence (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		// super writes the energy buffer under "Energy" — the machine keys follow it, in the order they
		// have always been written, so an existing save round-trips unchanged.
		super.saveAdditional(output);
		output.putInt("Progress", progress);
		output.putInt("MaxProgress", maxProgress);
		inventory.save(output);
		telemetry.save(output);
		// MOD-133: owner persisted here (NBT keys "Owner"/"OwnerName") for every tracking machine.
		if (tracksOwner()) {
			owner.save(output);
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		progress = input.getIntOr("Progress", 0);
		maxProgress = input.getIntOr("MaxProgress", 0);
		inventory.load(input);
		telemetry.load(input);
		if (tracksOwner()) {
			owner.load(input);
		}
	}

	// --- Block statistics (MOD-125) --------------------------------------------------------------

	/** Working time, reported EU rate, completed operations and their save keys (MOD-125, MOD-712 BE-1). */
	private final MachineTelemetry telemetry = new MachineTelemetry(energy);

	/**
	 * Whether a statistics chip is fitted (MOD-125) — or the owner's Free Telemetry makes one unnecessary.
	 * Without it this block measures nothing at all: see {@link UpgradePanel#hasStatsChip()}.
	 */
	public boolean hasStatsChip() {
		return upgrades.hasStatsChip();
	}

	/**
	 * Switch the buffer's energy counters to follow the statistics chip before every tick (MOD-692). They
	 * used to be switched from {@link #recordEuRate} alone, so a block whose tick never reported a rate —
	 * the pump, the drone station, both energy stores — kept them off with a chip fitted and its panel
	 * read zero. Here no block has to remember. The rule itself, including what a block with no upgrade
	 * panel does under Free Telemetry, is {@link StatsCounterGate}.
	 */
	@Override
	protected void beforeServerTick() {
		StatsCounterGate.sync(energy, hasUpgradeSlots(), this::hasStatsChip);
	}

	/**
	 * Record this tick's EU rate — production for a generator, draw for a consumer. Called from the
	 * generator/machine tick, so a subclass never has to remember to feed the statistics panel. A non-zero
	 * rate also advances the working time; both only while {@link #beforeServerTick} switched the buffer's
	 * counters on for this tick — see {@link MachineTelemetry#recordEuRate}.
	 */
	public void recordEuRate(int euPerTick) {
		telemetry.recordEuRate(euPerTick);
	}

	/** Ticks this block spent actually working. */
	public long activeTicks() {
		return telemetry.activeTicks();
	}

	public int currentEuRate() {
		return telemetry.currentEuRate();
	}

	public int peakEuRate() {
		return telemetry.peakEuRate();
	}

	/** Direct energy neighbours, sources low 16 bits, sinks high: {@link MachineTelemetry#countDirectConnections}. */
	public int countDirectConnections() {
		return MachineTelemetry.countDirectConnections(this);
	}

	/** Completed operations over this block's lifetime (MOD-125), persisted under {@code StatsItemsProcessed}. */
	public long totalItemsProcessed() {
		return telemetry.totalItemsProcessed();
	}

	/** Count one finished operation. Called by the processing machines when they commit a result. */
	public void recordItemProcessed() {
		telemetry.recordItemProcessed();
	}

	// --- Container over `items` ---

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return inventory.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		return inventory.removeItem(slot, amount);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return inventory.removeItemNoUpdate(slot);
	}

	/** The {@code changed} hook of {@link MachineInventory}: mark dirty, then sync to the client. */
	private void contentsChanged() {
		setChanged();
		syncBlockEntityToClient();
	}

	/**
	 * Whether swapping the input item mid-operation resets processing progress. Processing machines
	 * (macerator/furnace/compressor/extractor) override to {@code true} per spec (TC-MACH-001-FUN04):
	 * changing the input starts the new operation from zero. Generators keep {@code false}.
	 */
	protected boolean resetProgressOnInputChange() {
		return false;
	}

	// --- Result slot helpers (MOD-440): one predicate for every machine that fills an output slot ---

	/** Output stack cap of the whole machine family: see {@link MachineInventory#OUTPUT_MAX}. */
	protected static final int OUTPUT_MAX = MachineInventory.OUTPUT_MAX;

	/**
	 * Whether {@code slot} can take one more {@code result} stack — empty, or the same item with room for
	 * all of it: see {@link MachineInventory#canOutput}.
	 */
	protected final boolean canOutput(int slot, ItemStack result) {
		return inventory.canOutput(slot, result);
	}

	/** Place one {@code result} stack into {@code slot}; the caller has checked {@link #canOutput} first. */
	protected final void addOutput(int slot, ItemStack result) {
		inventory.addOutput(slot, result);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		// `baseSlots > 0` guards machines whose input is slot 0; a base-0 machine's slot 0 is an upgrade
		// slot (MOD-080), so installing a chip there must not touch processing progress.
		if (slot == 0 && baseSlots > 0 && resetProgressOnInputChange()
				&& !ItemStack.isSameItem(items.get(0), stack)) {
			progress = 0; // input item changed -> restart the operation (TC-MACH-001-FUN04)
		}
		inventory.setItem(slot, stack);
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		inventory.clear();
	}

	// --- Upgrade slots (MOD-080): GUI-only slots appended to the tail of `items` ---

	/** First index of the upgrade block in {@link #items}; equals {@link #baseSlots}. */
	public int upgradeSlotStart() {
		return layout.upgradeStart();
	}

	/** Whether this machine carries upgrade slots (all GUI machines do). */
	public boolean hasUpgradeSlots() {
		return items.size() > baseSlots;
	}

	/** The stack in upgrade-block index {@code i} (0-based), or empty when there are no upgrade slots. */
	public ItemStack getUpgradeStack(int i) {
		return upgrades.stack(i);
	}

	// --- Battery drawer (MOD-679): one slot whose item the machine drains into its own buffer ---

	/** Whether this machine has a battery drawer. */
	public boolean hasBatterySlot() {
		return layout.batteryDrawer();
	}

	/** Container index of the battery drawer slot, or -1 when there is none. The last index when present. */
	public int batterySlotIndex() {
		return layout.batterySlot();
	}

	/** Drain the drawer into the buffer, one tier-voltage packet at most: see {@link BatteryDrawer#drain()}. */
	@Override
	protected boolean pullStoredCharge() {
		if (!drawer.drain()) {
			return false;
		}
		setChanged();
		return true;
	}

	/**
	 * Whether a mute chip sits in ANY upgrade slot — the single source of truth for silencing this machine,
	 * readable on the client too: see {@link UpgradePanel#isMuted()}.
	 */
	public boolean isMuted() {
		return upgrades.isMuted();
	}

	// --- Overclocking (MOD-392): speed chips in the upgrade panel ---

	/**
	 * Base EU/t this machine draws while working, BEFORE the global speed multiplier and any
	 * overclocker chip. Default is the shared processing rate; machines with their own tariff (alloy
	 * smelter, assembler, incubator, electric heater) override it.
	 *
	 * <p>Read live from {@link Config} rather than cached, so a config reload takes effect without a
	 * world restart — and so {@link #overclockerCap()} re-derives against the current value.
	 */
	public int baseEuPerTick() {
		return Config.machineEuPerTick;
	}

	/**
	 * Whether overclocker chips do anything in this block — declared by implementing
	 * {@link Overclockable}, not by overriding this.
	 *
	 * <p>Opt-in on purpose: a block that has not routed its tick through
	 * {@link #effectiveEuPerTick}/{@link #effectiveDuration} must not advertise a chip slot that
	 * silently does nothing — a dead upgrade reads as a bug, and a machine added later would inherit
	 * the lie for free. Generators and storage stay {@code false}.
	 *
	 * <p>{@code final}, and reading a marker interface rather than a per-class boolean, because the
	 * upgrade panel has to answer the same question on the CLIENT, where there is no block entity at
	 * all — see {@link Overclockable} and
	 * {@link dev.alaindustrial.registry.ContentManifest#isOverclockable}. Two independently maintained
	 * answers to one question is exactly how the slot and the effect would drift apart.
	 */
	public final boolean supportsOverclock() {
		return this instanceof Overclockable;
	}

	/** How many overclocker chips this machine can actually use: see {@link UpgradePanel#overclockerCap()}. */
	public int overclockerCap() {
		return upgrades.overclockerCap();
	}

	/** Steps of overclocking in effect, clamped to {@link #overclockerCap()}: see {@link UpgradePanel}. */
	public int overclockerCount() {
		return upgrades.overclockerCount();
	}

	/**
	 * The EU/t this machine actually draws: its base rate scaled by the global speed multiplier and
	 * then by one {@link Config#overclockerEuFactor} per installed chip.
	 *
	 * <p>Machines MUST call this instead of the static tariff formula {@code MachineRates.euPerTick} — that
	 * is static and knows nothing about a specific block, so it can never see the chips. Enforced by the
	 * bytecode rule {@code ArchitectureRules.machinesUseOverclockHelpers} (ADR-014).
	 */
	public int effectiveEuPerTick(int baseEuPerTick) {
		return upgrades.effectiveEuPerTick(baseEuPerTick);
	}

	/**
	 * The operation length in ticks: the base duration after the global speed multiplier, then one
	 * {@link Config#overclockerSpeedFactor} per installed chip, then the owner's skills.
	 *
	 * <p>{@code baseTicks} must be the UNSCALED duration (recipe energy divided by the machine's raw
	 * tariff, not by the multiplied one) — scaling twice is the classic way to make a retuned server
	 * silently run at the square of its configured speed. The multiplier is applied here, not in the
	 * panel: {@code machinesUseOverclockHelpers} lets only this class call the static shortcut.
	 */
	public int effectiveDuration(int baseTicks) {
		return upgrades.effectiveDuration(MachineRates.duration(baseTicks, Config.globalMachineSpeedMultiplier));
	}

	// --- Sided automation (R-GUI-05/R-GUI-07): hoppers/pipes must respect slot roles ---

	/** Which slots are extractable by automation. Default: none (storage/generators keep their items). */
	protected boolean isOutputSlot(int slot) {
		return false;
	}

	/** The machine's own slots on every face but the front (MOD-179): see {@link MachineInventory#slotsForFace}. */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return inventory.slotsForFace(getBlockState(), side);
	}

	/** Automation may insert only where manual placement is allowed (e.g. never the output slot). */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return canPlaceItem(slot, stack);
	}

	/** Automation may extract only from output slots — never pull unprocessed input or stored fuel. */
	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return isOutputSlot(slot);
	}
}
