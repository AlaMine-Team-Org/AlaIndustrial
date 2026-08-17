package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.upgrade.OverclockMath;
import dev.alaindustrial.item.misc.OverclockerChipItem;
import dev.alaindustrial.registry.ModContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
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

/**
 * The spine of every Industrialization machine: the energy half inherited from
 * {@link EnergyBlockEntity} plus an item inventory, a generic processing progress counter, upgrade
 * slots, ownership and live GUI sync.
 *
 * <p>To add a new machine, subclass this, pass slot count / tier / capacity / I-O limits to
 * the constructor, and implement {@link #onServerTick}. The base handles inventory
 * ({@link Container}), NBT persistence (via {@link ValueInput}/{@link ValueOutput}), and the
 * {@link #dataAccess} bridge that syncs energy + progress to an open screen.
 *
 * <p><b>A machine, not merely a powered block (MOD-400).</b> Transport blocks — the cable and the two
 * pipes — extend {@link EnergyBlockEntity} directly: they have no inventory, no progress, no upgrade
 * panel and no owner, so nothing here applies to them. Everything in this class may therefore assume
 * it is looking at a real machine.
 */
public abstract class MachineBlockEntity extends EnergyBlockEntity implements WorldlyContainer {

	/** Upgrade slots appended to the tail of every GUI machine's inventory (MOD-080). */
	public static final int UPGRADE_SLOT_COUNT = 4;
	/** The active upgrade slot on the MVP panel (upgrade-block index 0); the mute chip goes here. */
	public static final int ACTIVE_UPGRADE_INDEX = 0;

	protected final NonNullList<ItemStack> items;
	/** Count of machine-specific slots (indices 0..baseSlots-1); upgrade slots follow at the tail. */
	protected final int baseSlots;
	protected int progress;
	protected int maxProgress;

	/**
	 * The player who placed this machine (MOD-133). Set once at placement by
	 * {@link dev.alaindustrial.block.AbstractMachineBlock#setPlacedBy}; re-assigned on every re-place
	 * (it does not ride the dropped item). Null for a machine placed by non-player means (structure,
	 * {@code /ala demo} stand) — the player-stats hooks treat a null owner as a no-op. Gated by
	 * {@link #tracksOwner()}.
	 */
	@Nullable
	private UUID owner;
	/** Owner's name at placement time — a display snapshot (no UUID→name lookup for offline players). */
	private String ownerName = "";

	protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, int slots, long capacity, long maxInsert, long maxExtract) {
		super(type, pos, state, tier, capacity, maxInsert, maxExtract);
		this.baseSlots = slots;
		// Every GUI machine gets four upgrade slots appended to the tail of `items` (MOD-080). "GUI
		// machine" = a MenuProvider; the screenless blocks (the electric heater, the charging station)
		// are not, so they keep their zero slots. Appending at the tail leaves existing indices
		// (0=input, 1=output, …)
		// and their gametests untouched. `this instanceof` is well-defined here: the object's runtime
		// type is the concrete subclass throughout super-construction.
		int total = slots + (this instanceof MenuProvider && hasUpgradePanel() ? UPGRADE_SLOT_COUNT : 0);
		this.items = NonNullList.withSize(total, ItemStack.EMPTY);
	}

	/**
	 * Whether this block gets the four upgrade slots. Default true for every GUI machine (MOD-080).
	 *
	 * <p>Opt-out exists because a panel is a promise: a block that shows upgrade slots is telling the
	 * player those upgrades do something there. The Energy Condenser (MOD-393) is the first block for
	 * which that would be a lie — it is meant to sip surplus, and an overclocker in it would turn it
	 * into a pump on the grid.
	 *
	 * <p>Called from the constructor, so an override MUST be a constant — it cannot read subclass
	 * fields, which are not initialised yet. Same contract as the {@code instanceof MenuProvider} test
	 * beside it.
	 */
	public boolean hasUpgradePanel() {
		return true;
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
		this.owner = owner;
		this.ownerName = ownerName == null ? "" : ownerName;
		setChanged();
	}

	/** The placer's UUID, or null for a machine placed by non-player means. */
	@Nullable
	public UUID getOwner() {
		return owner;
	}

	/** The placer's name snapshot, or {@code ""} when there is no owner. */
	public String getOwnerName() {
		return ownerName;
	}

	/** True when {@code player} is this machine's owner. */
	public boolean isOwner(UUID player) {
		return owner != null && owner.equals(player);
	}

	/**
	 * MOD-133: credit one completed unit of useful work (its full EU cost) to this machine's owner —
	 * the sole XP source. Called once per completed operation (never per tick), so a redstone
	 * contraption that aborts an operation before completion burns EU but earns no XP. A no-op
	 * off-server, without an owner, or for non-positive cost; the tracker additionally ignores it when
	 * the owner is offline or in creative.
	 */
	protected void creditUsefulWork(net.minecraft.world.level.Level level, long euCost) {
		if (euCost <= 0 || owner == null
				|| !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
			return;
		}
		dev.alaindustrial.stats.PlayerStatsTracker.get()
				.recordUsefulWork(serverLevel.getServer(), owner, euCost);
	}

	/** The energy/progress data bridge a {@code MachineMenu} binds for live GUI sync. */
	public ContainerData getDataAccess() {
		return dataAccess;
	}

	/**
	 * How many sync channels {@link #getDataAccess()} projects — the single source of the width, read by
	 * both sides (MOD-235). The block entity's {@code getCount()} returns it, and the machine's <b>client</b>
	 * menu constructor sizes its {@code SimpleContainerData} from it, so the two can no longer drift: a
	 * subclass that adds a channel bumps its own {@code DATA_COUNT} and the client stub follows. Before
	 * MOD-235 the width was a literal on both sides, and adding a channel to the block entity alone threw
	 * {@code ArrayIndexOutOfBoundsException} in the client render thread while every server test stayed green.
	 */
	public static final int DATA_COUNT = 4;

	/** Index map for {@link #dataAccess}: 0 energy, 1 capacity, 2 progress, 3 maxProgress. */
	public final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> (int) Math.min(Integer.MAX_VALUE, energy.getAmount());
				case 1 -> (int) Math.min(Integer.MAX_VALUE, energy.getCapacity());
				case 2 -> progress;
				case 3 -> maxProgress;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> energy.setAmountUntracked(value);
				case 2 -> progress = value;
				case 3 -> maxProgress = value;
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	// --- persistence (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		// super writes the energy buffer under "Energy" — the machine keys follow it, in the order they
		// have always been written, so an existing save round-trips unchanged.
		super.saveAdditional(output);
		output.putInt("Progress", progress);
		output.putInt("MaxProgress", maxProgress);
		ContainerHelper.saveAllItems(output, items);
		saveStats(output);
		// MOD-133: owner persisted here (NBT keys "Owner"/"OwnerName") for every tracking machine.
		// The teleporter station used the same keys before this moved to the base, so existing
		// stations round-trip without a data migration.
		if (tracksOwner()) {
			output.storeNullable("Owner", UUIDUtil.CODEC, owner);
			output.putString("OwnerName", ownerName);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		progress = input.getIntOr("Progress", 0);
		maxProgress = input.getIntOr("MaxProgress", 0);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
		loadStats(input);
		if (tracksOwner()) {
			owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
			ownerName = input.getStringOr("OwnerName", "");
		}
	}

	// --- Block statistics (MOD-125) --------------------------------------------------------------

	/**
	 * Ticks this block has actually WORKED — produced or consumed EU — not ticks since it was placed.
	 *
	 * <p>The distinction is the whole meaning of the readout. A furnace sitting unpowered in a corner for
	 * a week has an age of a week and a working time of zero, and reporting the age under the label
	 * "working time" is simply a false number: it climbs on a machine that has never run once.
	 *
	 * <p>Accumulated per tick, which is safe precisely BECAUSE of the sleep gate rather than in spite of
	 * it: a block skips its tick only while idle, and an idle tick is one this counter must not count
	 * anyway. (An age-since-placed counter is the opposite case and could NOT be accumulated this way.)
	 */
	private long activeTicks;

	/** Last per-tick EU rate this block reported. Session state: never persisted, 0 after a reload. */
	private int currentEuRate;

	/** Highest {@link #currentEuRate} seen since the block entity loaded. Session state, deliberately. */
	private int peakEuRate;

	/**
	 * Whether a statistics chip is fitted (MOD-125). Without one this block measures nothing at all: no
	 * counters advance, nothing is written to its save tag, and no packet is ever built for it.
	 *
	 * <p>That is the point of making it a chip rather than a free feature — telemetry is something the
	 * player chooses to install where it matters, and a base full of un-instrumented machines costs
	 * exactly what it did before this task existed.
	 */
	public boolean hasStatsChip() {
		if (!hasUpgradeSlots()) {
			return false;
		}
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			if (getUpgradeStack(i).is(ModContent.STATS_CHIP.get())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Record this tick's EU rate — production for a generator, draw for a consumer. Called from the
	 * generator/machine tick, so a subclass never has to remember to feed the statistics panel.
	 *
	 * <p>A non-zero rate is also this block's definition of "working", so the working-time counter is
	 * advanced from the same call: the two answers can never disagree about whether the machine ran.
	 *
	 * <p>Gated on the chip, and the energy counters in the buffer are gated with it, so "measuring
	 * starts when you install the sensor" holds for every number the panel can show — not just the ones
	 * that happen to pass through here.
	 */
	public void recordEuRate(int euPerTick) {
		boolean measuring = hasStatsChip();
		energy.setCountersEnabled(measuring);
		if (!measuring) {
			currentEuRate = 0;
			return;
		}
		int rate = Math.max(0, euPerTick);
		currentEuRate = rate;
		if (rate > peakEuRate) {
			peakEuRate = rate;
		}
		if (rate > 0) {
			activeTicks++;
		}
	}

	/** Ticks this block spent actually working. */
	public long activeTicks() {
		return activeTicks;
	}

	public int currentEuRate() {
		return currentEuRate;
	}

	public int peakEuRate() {
		return peakEuRate;
	}

	/**
	 * How many of the six direct faces currently sit against a usable energy port, split into sources
	 * (something that could feed this block) and sinks (something this block could feed).
	 *
	 * <p>Six lookups, run only when a statistics packet is actually being built — not per tick, and never
	 * a walk of the cable graph. A cable neighbour counts as one connection, not as everything behind it:
	 * answering "what is on the other end of the wire" is the network analyzer's job, not this panel's.
	 *
	 * @return sources in the low 16 bits, sinks in the next 16 — packed because the pair travels together
	 *     and a two-field return would need a record no other caller wants
	 */
	public int countDirectConnections() {
		if (level == null) {
			return 0;
		}
		int sources = 0;
		int sinks = 0;
		EnergyLookup lookup = EnergyLookup.get();
		for (Direction dir : Direction.values()) {
			EnergyRole role = energyRoleForFace(dir);
			if (role == EnergyRole.NONE) {
				continue;
			}
			EnergyPort port = lookup.find(level, worldPosition.relative(dir), dir.getOpposite());
			if (port == null) {
				continue;
			}
			// Mirror the direct-push rules (R-NRG-03): a face that cannot extract has no sink behind it
			// even when the neighbour would accept energy, and vice versa.
			if (role.canInsert() && port.supportsExtraction()) {
				sources++;
			}
			if (role.canExtract() && port.supportsInsertion()) {
				sinks++;
			}
		}
		return (sources & 0xFFFF) | ((sinks & 0xFFFF) << 16);
	}

	private void saveStats(ValueOutput output) {
		output.putLong("StatsActiveTicks", activeTicks);
		output.putLong("StatsEnergyIn", energy.getTotalEnergyIn());
		output.putLong("StatsEnergyOut", energy.getTotalEnergyOut());
		output.putLong("StatsEnergyGenerated", energy.getTotalEnergyGenerated());
		output.putLong("StatsEnergyConsumed", energy.getTotalEnergyConsumed());
		output.putLong("StatsItemsProcessed", totalItemsProcessed);
	}

	private void loadStats(ValueInput input) {
		activeTicks = input.getLongOr("StatsActiveTicks", 0L);
		energy.restoreCounters(
				input.getLongOr("StatsEnergyIn", 0L),
				input.getLongOr("StatsEnergyOut", 0L),
				input.getLongOr("StatsEnergyGenerated", 0L),
				input.getLongOr("StatsEnergyConsumed", 0L));
		totalItemsProcessed = input.getLongOr("StatsItemsProcessed", 0L);
	}

	/**
	 * Completed operations over this block's lifetime. Counted and persisted from the start (MOD-125) even
	 * though the MVP panel does not draw it: the machines task that will show it then needs no save
	 * migration, and a counter that starts at zero on every existing machine would be worse than useless.
	 */
	private long totalItemsProcessed;

	public long totalItemsProcessed() {
		return totalItemsProcessed;
	}

	/** Count one finished operation. Called by the processing machines when they commit a result. */
	public void recordItemProcessed() {
		totalItemsProcessed++;
	}

	// --- Evolvable persistence helper (shared by SolarPanelBlockEntity + WindMillBlockEntity) ---

	/**
	 * Write the evolution counter under the canonical NBT key. Both base-tier evolvable generators
	 * (solar panel, wind mill) persist their chip progress identically; centralising the literal here
	 * keeps the save format consistent and makes a future rename a one-line change. The key stays
	 * {@code "EvolveProgress"} for backwards compatibility with existing single-player saves.
	 */
	protected static void saveEvolve(ValueOutput output, int evolveProgress) {
		output.putInt("EvolveProgress", evolveProgress);
	}

	/**
	 * Read the evolution counter written by {@link #saveEvolve}; {@code 0} when the key is absent
	 * (pre-evolution save, or a freshly placed block).
	 */
	protected static int loadEvolve(ValueInput input) {
		return input.getIntOr("EvolveProgress", 0);
	}

	// --- Container over `items` ---

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
		if (!removed.isEmpty()) {
			setChanged();
			syncBlockEntityToClient();
			wake(); // output pulled / input taken — re-evaluate next tick (R-29)
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		wake();
		ItemStack removed = ContainerHelper.takeItem(items, slot);
		if (!removed.isEmpty()) {
			setChanged();
			syncBlockEntityToClient();
		}
		return removed;
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

	/**
	 * Output stack cap. A result slot never grows past this even for an item whose own max stack size is
	 * larger; for the ordinary 64-stack item the two limits coincide.
	 *
	 * <p>One declaration for the whole machine family. Before MOD-440 nine block entities each carried a
	 * private {@code OUTPUT_MAX = 64} and five of them a private copy of {@link #canOutput}/
	 * {@link #addOutput} — identical in behaviour, free to drift in text.
	 */
	protected static final int OUTPUT_MAX = 64;

	/**
	 * Whether {@code slot} can take one more {@code result} stack: empty, or the same item with room for
	 * the whole result under {@code min(OUTPUT_MAX, maxStackSize)}.
	 *
	 * <p>Item identity only, deliberately — this is the merge rule every processing machine has always
	 * used, and a recipe result carries no components that could tell two stacks of it apart. A machine
	 * whose output CAN differ by components (the incubator's graded results) keeps its own component-aware
	 * test instead of using this one. An empty result never "fits": there is nothing to place, and a
	 * caller asking is a machine with no recipe.
	 */
	protected final boolean canOutput(int slot, ItemStack result) {
		if (result.isEmpty()) {
			return false;
		}
		ItemStack out = items.get(slot);
		return out.isEmpty()
				|| (out.getItem() == result.getItem()
						&& out.getCount() + result.getCount() <= Math.min(OUTPUT_MAX, out.getMaxStackSize()));
	}

	/**
	 * Place one {@code result} stack into {@code slot}, growing the matching stack already there. The
	 * caller has checked {@link #canOutput} first — this method does not re-check.
	 */
	protected final void addOutput(int slot, ItemStack result) {
		ItemStack out = items.get(slot);
		if (out.isEmpty()) {
			items.set(slot, result.copy());
		} else {
			out.grow(result.getCount());
		}
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		// `baseSlots > 0` guards machines whose input is slot 0; a base-0 machine's slot 0 is an upgrade
		// slot (MOD-080), so installing a chip there must not touch processing progress.
		if (slot == 0 && baseSlots > 0 && resetProgressOnInputChange()
				&& !ItemStack.isSameItem(items.get(0), stack)) {
			progress = 0; // input item changed -> restart the operation (TC-MACH-001-FUN04)
		}
		items.set(slot, stack);
		setChanged();
		syncBlockEntityToClient();
		wake(); // new input / output change — re-evaluate next tick (R-29)
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
		setChanged();
		syncBlockEntityToClient();
		wake();
	}

	// --- Upgrade slots (MOD-080): GUI-only slots appended to the tail of `items` ---

	/** First index of the upgrade block in {@link #items}; equals {@link #baseSlots}. */
	public int upgradeSlotStart() {
		return baseSlots;
	}

	/** Whether this machine carries upgrade slots (all GUI machines do). */
	public boolean hasUpgradeSlots() {
		return items.size() > baseSlots;
	}

	/** Whether {@code slot} is one of the appended upgrade slots. */
	public boolean isUpgradeSlot(int slot) {
		return slot >= baseSlots && slot < items.size();
	}

	/** The stack in upgrade-block index {@code i} (0-based), or empty when there are no upgrade slots. */
	public ItemStack getUpgradeStack(int i) {
		int idx = baseSlots + i;
		return idx >= baseSlots && idx < items.size() ? items.get(idx) : ItemStack.EMPTY;
	}

	/**
	 * Whether a mute chip sits in ANY upgrade slot. Single source of truth for silencing this
	 * machine: the client hum manager and any future machine sound MUST honor it. Safe to read
	 * client-side — upgrade-slot contents sync with the block entity (getUpdateTag), so no extra
	 * networking is needed.
	 *
	 * <p>Scans every slot rather than only {@link #ACTIVE_UPGRADE_INDEX}: since MOD-392 all four
	 * slots accept upgrades, so the player may park the mute chip anywhere in the panel.
	 */
	public boolean isMuted() {
		if (!hasUpgradeSlots()) {
			return false;
		}
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			if (getUpgradeStack(i).is(ModContent.MUTE_CHIP.get())) {
				return true;
			}
		}
		return false;
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

	/**
	 * How many overclocker chips this machine can actually use.
	 *
	 * <p>Not a flat constant: a machine may hold no more chips than its own voltage tier can feed.
	 * Beyond that point the block would demand more EU/t than {@link EnergyTier#maxVoltage()} lets it
	 * accept in a tick, so the extra chips could never pay off — the machine would simply starve while
	 * the player believes they bought speed. Derived by stepping the factor instead of a logarithm, so
	 * a retuned {@link Config#overclockerEuFactor} cannot drift the two apart through rounding.
	 */
	public int overclockerCap() {
		if (!supportsOverclock() || !hasUpgradeSlots()) {
			return 0;
		}
		return OverclockMath.cap(baseEuPerTick(), tier.maxVoltage(),
				Config.overclockerEuFactor, Config.overclockerMaxPerMachine);
	}

	/**
	 * Steps of overclocking actually in effect: the tier of the chip in the panel, clamped to
	 * {@link #overclockerCap()}.
	 *
	 * <p>The tier rides on the item (three separate chips) rather than on a stack size, so "how much
	 * speed" is a property of what the player crafted, not of how many copies they crammed into a slot.
	 * The clamp still applies: a machine whose voltage tier cannot feed the chip runs at what it can.
	 */
	public int overclockerCount() {
		int cap = overclockerCap();
		if (cap <= 0) {
			return 0;
		}
		int tier = 0;
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			tier = Math.max(tier, OverclockerChipItem.tierOf(getUpgradeStack(i)));
		}
		return Math.min(tier, cap);
	}

	/**
	 * The EU/t this machine actually draws: its base rate scaled by the global speed multiplier and
	 * then by one {@link Config#overclockerEuFactor} per installed chip.
	 *
	 * <p>Machines MUST call this instead of {@code Config.machineEuPerTickEffective()} — that method is
	 * static and knows nothing about a specific block, so it can never see the chips. Enforced by
	 * {@code docs/tools/arch_check.py}.
	 */
	public int effectiveEuPerTick(int baseEuPerTick) {
		return OverclockMath.euPerTick(baseEuPerTick, Config.globalMachineSpeedMultiplier,
				Config.overclockerEuFactor, overclockerCount());
	}

	/**
	 * The operation length in ticks: the base duration after the global speed multiplier, then one
	 * {@link Config#overclockerSpeedFactor} per installed chip.
	 *
	 * <p>{@code baseTicks} must be the UNSCALED duration (recipe energy divided by the machine's raw
	 * tariff, not by the multiplied one) — scaling twice is the classic way to make a retuned server
	 * silently run at the square of its configured speed.
	 */
	public int effectiveDuration(int baseTicks) {
		return OverclockMath.duration(Config.scaledDuration(baseTicks),
				Config.overclockerSpeedFactor, overclockerCount());
	}

	// --- Tier evolution (MOD-211, generalized in MOD-278): replace this block with a grown branch ---

	/**
	 * Replace this machine with its evolved branch — shared by the solar panel and wind mill
	 * evolution paths (MOD-211) and the Mob Repeller tier ladder (MOD-278). Carries stored EU
	 * (clamped to the evolved block's capacity), preserves the FACING blockstate when both the old
	 * and new blocks have one, and consumes the trigger slot (the caller passes {@code slotOverrides}
	 * that does both jobs specific to this machine's slot layout — e.g. clearing {@code CHIP_SLOT} on
	 * both, and snapshotting the wind-mill rotor so it can be re-placed on the evolved mill).
	 *
	 * <p>Lived in {@code AbstractGeneratorBlockEntity} until MOD-278: nothing in the body is
	 * generator-specific (owner, energy, FACING, slots — all base state), and the repeller is a
	 * consumer, so the seam moved up rather than being copied down.
	 *
	 * @param target the block to evolve into (e.g. {@code ModContent.DAYLIGHT_SOLAR_PANEL.get()})
	 * @param slotOverrides additional slots to copy from this machine into the evolved block BEFORE
	 *     the energy transfer (e.g. the wind mill's rotor slot). Map of slot index → stack to place.
	 *     Empty for the solar panel.
	 */
	protected void evolveInto(net.minecraft.world.level.Level level, BlockPos pos,
			net.minecraft.world.level.block.Block target,
			java.util.Map<Integer, ItemStack> slotOverrides) {
		long saved = energy.getAmount();
		// Carry ownership across the evolution. The evolved block is created via setBlockAndUpdate — NOT a
		// player placement — so setPlacedBy never runs and the new block entity would default to a null
		// owner. Without this, an evolved T2 generator (wind mills, solar panels) attributes none of its
		// production to the player: it silently vanished from the profile's per-generator breakdown (MOD-133).
		java.util.UUID savedOwner = getOwner();
		String savedOwnerName = getOwnerName();
		for (java.util.Map.Entry<Integer, ItemStack> entry : slotOverrides.entrySet()) {
			// Caller has already snapshotted these into the overrides map; clear the source slot so the
			// block's inventory reads empty before the swap (the chip slot is always cleared here too —
			// see callers).
			items.set(entry.getKey(), ItemStack.EMPTY);
		}
		BlockState oldState = getBlockState();
		BlockState newState = target.defaultBlockState();
		// Preserve FACING when both old and new blocks have it (wind mill family); the solar panels
		// have no FACING, so this is a no-op for them.
		if (oldState.hasProperty(HorizontalMachineBlock.FACING)
				&& newState.hasProperty(HorizontalMachineBlock.FACING)) {
			newState = newState.setValue(HorizontalMachineBlock.FACING, oldState.getValue(HorizontalMachineBlock.FACING));
		}
		level.setBlockAndUpdate(pos, newState);
		if (level.getBlockEntity(pos) instanceof MachineBlockEntity evolved) {
			evolved.setOwner(savedOwner, savedOwnerName);
			evolved.getEnergyStorage().setAmountUntracked(Math.min(saved, evolved.getEnergyStorage().getCapacity()));
			for (java.util.Map.Entry<Integer, ItemStack> entry : slotOverrides.entrySet()) {
				int slot = entry.getKey();
				ItemStack stack = entry.getValue();
				if (!stack.isEmpty() && slot >= 0 && slot < evolved.getContainerSize()) {
					evolved.setItem(slot, stack);
				}
			}
			evolved.setChanged();
		}
	}

	// --- Sided automation (R-GUI-05/R-GUI-07): hoppers/pipes must respect slot roles ---

	/** Which slots are extractable by automation. Default: none (storage/generators keep their items). */
	protected boolean isOutputSlot(int slot) {
		return false;
	}

	/** Shared empty answer for faces automation must not touch (the FACING face, MOD-179). */
	private static final int[] NO_AUTOMATION_SLOTS = new int[0];

	@Override
	public int[] getSlotsForFace(Direction side) {
		// The front (FACING) face is the machine's working face and is inert for automation, matching
		// the energy side (facingAwareRole) and the block specs ("hoppers do not work through it").
		// Before MOD-179 this method ignored `side`, so a hopper aimed at the front face could insert.
		BlockState state = getBlockState();
		if (state.hasProperty(HorizontalMachineBlock.FACING)
				&& side == state.getValue(HorizontalMachineBlock.FACING)) {
			return NO_AUTOMATION_SLOTS;
		}
		// Automation sees machine slots only; upgrade slots (MOD-080) are GUI-only and excluded here
		// so hoppers/pipes can neither fill nor drain them, on either loader.
		int[] slots = new int[baseSlots];
		for (int i = 0; i < baseSlots; i++) {
			slots[i] = i;
		}
		return slots;
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
