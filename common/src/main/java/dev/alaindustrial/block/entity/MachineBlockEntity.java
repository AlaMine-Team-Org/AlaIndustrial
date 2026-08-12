package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
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
		if (tracksOwner()) {
			owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
			ownerName = input.getStringOr("OwnerName", "");
		}
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
