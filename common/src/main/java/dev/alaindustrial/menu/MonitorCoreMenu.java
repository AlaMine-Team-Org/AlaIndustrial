package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.item.misc.CapacityCardItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Monitor Core screen's menu (MOD-480): the ten card sockets plus the numbers the screen prints.
 *
 * <p><b>Not a {@link MachineMenu}.</b> That base is built for {@code MachineBlockEntity} — machine
 * slots plus the four upgrade sockets plus the stats panel — and the core is none of those: it is an
 * {@code EnergyBlockEntity} with a rack. Extending it would mean inventing four upgrade slots the
 * block does not have, so this menu sits on the vanilla base and adds exactly what exists.
 */
public class MonitorCoreMenu extends AbstractContainerMenu {

	/** Rack geometry, in GUI pixels of monitor_core.png — two columns of five. */
	public static final int RACK_X = 31;
	public static final int RACK_Y = 23;
	public static final int RACK_COLUMNS = 2;
	public static final int RACK_ROWS = 5;

	/**
	 * Player inventory origin, read off the atlas: a slot's frame sits one pixel up and left of the
	 * item, so the frame at (19,141) means the item goes at (20,142). Off by that one pixel and every
	 * item in the bag hangs over its own socket.
	 */
	private static final int INV_X = 20;
	private static final int INV_Y = 142;
	private static final int HOTBAR_Y = 200;

	private final Container rack;
	private final ContainerData data;

	/** Server side. */
	public MonitorCoreMenu(int syncId, Inventory playerInventory, Container rack, ContainerData data) {
		super(ModContent.MONITOR_CORE_MENU.get(), syncId);
		checkContainerSize(rack, MonitorCoreBlockEntity.CARD_SLOTS);
		checkContainerDataCount(data, MonitorCoreBlockEntity.DATA_COUNT);
		this.rack = rack;
		this.data = data;
		rack.startOpen(playerInventory.player);
		addRackSlots();
		addPlayerSlots(playerInventory);
		addDataSlots(data);
	}

	/** Client side: the same shape, backed by stubs until the server syncs. */
	public MonitorCoreMenu(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory,
				new SimpleContainer(MonitorCoreBlockEntity.CARD_SLOTS),
				new SimpleContainerData(MonitorCoreBlockEntity.DATA_COUNT));
	}

	private void addRackSlots() {
		for (int row = 0; row < RACK_ROWS; row++) {
			for (int col = 0; col < RACK_COLUMNS; col++) {
				int index = row * RACK_COLUMNS + col;
				addSlot(new Slot(rack, index, RACK_X + col * 18, RACK_Y + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						// Mirrored in the block entity's canPlaceItem: this one is the client's
						// prediction, that one answers hoppers and pipes.
						return stack.getItem() instanceof CapacityCardItem;
					}

					@Override
					public int getMaxStackSize() {
						return 1;
					}
				});
			}
		}
	}

	private void addPlayerSlots(Inventory inventory) {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack inSlot = slot.getItem();
		ItemStack copy = inSlot.copy();
		int rackEnd = MonitorCoreBlockEntity.CARD_SLOTS;
		int inventoryEnd = this.slots.size();
		if (index < rackEnd) {
			// Card out of the rack and into the player's bag.
			if (!moveItemStackTo(inSlot, rackEnd, inventoryEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(inSlot, 0, rackEnd, false)) {
			// Anything that is not a card has nowhere to go — the rack's mayPlace refuses it.
			return ItemStack.EMPTY;
		}
		if (inSlot.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return rack.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		rack.stopOpen(player);
	}

	// --- what the screen prints ---------------------------------------------------------------

	public int getEnergy() {
		return data.get(0);
	}

	public int getCapacity() {
		return data.get(1);
	}

	/** How many distinct item types the fitted cards allow. */
	public int getAllowance() {
		return data.get(2);
	}

	public int getSeatedCards() {
		return data.get(3);
	}

	/** How many types the panels are asking for — the numerator of "4 of 6". */
	public int getWatchedTypes() {
		return data.get(4);
	}

	public int getServedPanels() {
		return data.get(5);
	}

	public int getUpkeep() {
		return data.get(6);
	}

	public boolean isPowered() {
		return data.get(7) != 0;
	}
}
