package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.MonitorCoreBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.item.misc.CapacityCardItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.menu.MonitorCoreMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The brain of a monitoring wall (MOD-480): the one block that holds the energy buffer, the capacity
 * cards, and the link to the smart wire that reads the player's chests.
 *
 * <p><b>Cards, not tiers.</b> Ten slots, filled by right-clicking a card into the rack, each card
 * raising how many DIFFERENT item types the whole wall can watch at once. Nothing is stored in them
 * — the items stay in the player's chests where they were put; a card buys the right to track a type,
 * not a place to keep one. That is the whole distance between this system and a digital storage
 * network.
 */
public class MonitorCoreBlockEntity extends EnergyBlockEntity implements Container, MenuProvider {

	/** Five to a side on the model — the rack the player reads at a glance. */
	public static final int CARD_SLOTS = 10;

	/** The longest stretch one upkeep debit may cover, and the cadence an idle core re-checks on. */
	private static final int UPKEEP_INTERVAL_TICKS = 20;

	private static final long NEVER_CHARGED = Long.MIN_VALUE;

	private final NonNullList<ItemStack> cards = NonNullList.withSize(CARD_SLOTS, ItemStack.EMPTY);

	private boolean registered;

	/**
	 * Game time of the last upkeep debit. Not persisted: a core that was unloaded was not running,
	 * so after a reload the clock simply starts again.
	 */
	private long lastUpkeepTick = NEVER_CHARGED;

	/** How many panels the last scan actually served — what the next upkeep debit is priced on. */
	private int servedPanels;

	/** How many DIFFERENT items the wall is asking for, and whether the last scan could be paid for.
	 * Both are facts of the network, not of this block, so the network publishes them here for the
	 * screen to read — the alternative is a second sync path for two integers. */
	private int watchedTypes;
	private boolean powered;

	/**
	 * What the open screen reads. Index order is the contract with {@link MonitorCoreMenu}; changing it
	 * without changing the menu silently renumbers every readout.
	 */
	private final ContainerData coreData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> (int) Math.min(Integer.MAX_VALUE, getEnergyStorage().getAmount());
				case 1 -> (int) Math.min(Integer.MAX_VALUE, getEnergyStorage().getCapacity());
				case 2 -> trackableTypes();
				case 3 -> seatedCards();
				case 4 -> watchedTypes;
				case 5 -> servedPanels;
				case 6 -> upkeepPerTick();
				case 7 -> powered ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			// Read-only: every number here is derived on the server.
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	/** Number of synced values — mirrored by the menu's client-side stub. */
	public static final int DATA_COUNT = 8;

	/** What the wall costs right now: the core's own upkeep plus every panel showing a number. */
	public int upkeepPerTick() {
		return Math.max(0, Config.monitorCoreIdleEuPerTick)
				+ servedPanels * Math.max(0, Config.monitorPanelEuPerTick);
	}

	/** Facts the network owns, handed over once per scan so the screen can show them. */
	public void publishStats(int watched, boolean paid) {
		this.watchedTypes = watched;
		this.powered = paid;
	}

	public ContainerData getCoreData() {
		return coreData;
	}

	public MonitorCoreBlockEntity(BlockPos pos, BlockState state) {
		// A sink: nothing ever flows back out of a monitor core.
		super(ModContent.MONITOR_CORE_BE.get(), pos, state, EnergyTier.LV,
				EnergyTier.LV.capacity(), EnergyTier.LV.maxVoltage(), 0L);
	}

	/**
	 * How many distinct item types the fitted cards allow the wall to watch.
	 *
	 * <p>A bare rack allows NOTHING (owner, 2026-09-20). The core used to hand out four types for
	 * free, which made an unfitted rack and a working wall look like the same thing: the panels
	 * showed numbers while every card slot stood empty, and nothing on the block said why. Cards are
	 * the price of the feature, not an upgrade on top of it.
	 */
	public int trackableTypes() {
		int total = 0;
		for (ItemStack card : cards) {
			if (card.getItem() instanceof CapacityCardItem capacityCard) {
				total += capacityCard.trackedTypes();
			}
		}
		return total;
	}

	/** How many slots of the rack are occupied. */
	public int seatedCards() {
		int seated = 0;
		for (ItemStack card : cards) {
			if (!card.isEmpty()) {
				seated++;
			}
		}
		return seated;
	}

	// --- Container: the ten card slots, so the screen can hold them as ordinary slots -------------
	//
	// The rack used to be reachable only by right-clicking the block, which made a full rack refuse
	// a card in silence. As a Container it is a normal inventory the menu shows — and, because
	// BlockCapabilityRoster derives item capability from this interface, a hopper may feed cards too.
	// Only capacity cards are ever accepted: canPlaceItem is the one gate both the menu and the
	// loaders' automation go through.

	@Override
	public int getContainerSize() {
		return CARD_SLOTS;
	}

	@Override
	public boolean isEmpty() {
		return seatedCards() == 0;
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot >= 0 && slot < cards.size() ? cards.get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack taken = ContainerHelper.removeItem(cards, slot, amount);
		if (!taken.isEmpty()) {
			onCardsChanged();
		}
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack taken = ContainerHelper.takeItem(cards, slot);
		if (!taken.isEmpty()) {
			onCardsChanged();
		}
		return taken;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= cards.size()) {
			return;
		}
		cards.set(slot, stack);
		if (stack.getCount() > getMaxStackSize()) {
			stack.setCount(getMaxStackSize());
		}
		onCardsChanged();
	}

	/** One card per slot: the rack's ten sockets are ten cards, not ten stacks of them. */
	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.getItem() instanceof CapacityCardItem;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		cards.clear();
		onCardsChanged();
	}

	// --- MenuProvider ------------------------------------------------------------------------------

	@Override
	public Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new MonitorCoreMenu(syncId, inventory, this, coreData);
	}

	public ItemStack getCard(int slot) {
		return slot >= 0 && slot < cards.size() ? cards.get(slot) : ItemStack.EMPTY;
	}

	/** Fit a card into the first free slot. @return whether it went in */
	public boolean insertCard(ItemStack stack) {
		for (int i = 0; i < cards.size(); i++) {
			if (cards.get(i).isEmpty()) {
				cards.set(i, stack.copyWithCount(1));
				onCardsChanged();
				return true;
			}
		}
		return false;
	}

	/** Pull the last fitted card back out, or {@link ItemStack#EMPTY} when the rack is bare. */
	public ItemStack removeLastCard() {
		for (int i = cards.size() - 1; i >= 0; i--) {
			if (!cards.get(i).isEmpty()) {
				ItemStack removed = cards.get(i);
				cards.set(i, ItemStack.EMPTY);
				onCardsChanged();
				return removed;
			}
		}
		return ItemStack.EMPTY;
	}

	private void onCardsChanged() {
		setChanged();
		if (level != null) {
			// Publish the count into the block state: the rack's face is a plain model chosen by this
			// property, so a seated card is visible from across the room without a renderer.
			BlockState state = getBlockState();
			int seated = seatedCards();
			if (state.hasProperty(MonitorCoreBlock.CARDS) && state.getValue(MonitorCoreBlock.CARDS) != seated) {
				level.setBlock(worldPosition, state.setValue(MonitorCoreBlock.CARDS, seated), 3);
			}
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
		if (level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.demandChanged(serverLevel, worldPosition);
		}
	}

	/**
	 * Charge for running {@code panels} panels since the last visit.
	 *
	 * <p>Priced on ticks that actually elapsed, never a fixed batch: energy arriving wakes the block
	 * entity through the buffer's commit hook, so a core on a live cable runs this every tick, and a
	 * per-visit debit would bill a second of upkeep per tick.
	 *
	 * @return whether the wall could be paid for
	 */
	public boolean payUpkeep(Level level, int panels) {
		this.servedPanels = panels;
		long now = level.getGameTime();
		if (lastUpkeepTick == NEVER_CHARGED) {
			lastUpkeepTick = now;
			return getEnergyStorage().getAmount() > 0L;
		}
		long elapsed = Mth.clamp(now - lastUpkeepTick, 0L, UPKEEP_INTERVAL_TICKS);
		lastUpkeepTick = now;
		long due = elapsed * (Config.monitorCoreIdleEuPerTick
				+ (long) panels * Config.monitorPanelEuPerTick);
		if (due <= 0L) {
			return getEnergyStorage().getAmount() > 0L;
		}
		if (getEnergyStorage().getAmount() < due) {
			// Take what is there anyway: a wall that cannot be paid for goes dark, and leaving the
			// charge untouched would let it flicker back on for one scan every time a trickle arrives.
			getEnergyStorage().drainInternal(getEnergyStorage().getAmount());
			setChanged();
			return false;
		}
		getEnergyStorage().drainInternal(due);
		setChanged();
		return true;
	}

	public int servedPanels() {
		return servedPanels;
	}

	public void ensureRegistered() {
		if (registered || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		registered = true;
		MonitorNetworkManager.register(serverLevel, worldPosition);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		ensureRegistered();
		// The network drives the wall; the core only has to stay enrolled and awake enough to notice
		// power arriving. Never IDLE_SLEEP_TICKS: a block that consumes has nothing to be idle about.
		return UPKEEP_INTERVAL_TICKS;
	}

	/** Power goes in through any face but the screen side (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.unregister(serverLevel, worldPosition);
			registered = false;
		}
		super.setRemoved();
	}

	/** Cards come back out when the core is broken — see the panel for why this hook and not the block's. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			for (ItemStack card : cards) {
				if (!card.isEmpty()) {
					Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5,
							pos.getZ() + 0.5, card.copy());
				}
			}
			cards.clear();
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, cards, true);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		cards.clear();
		ContainerHelper.loadAllItems(input, cards);
	}
}
