package dev.alaindustrial.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Shared menu base for Industrialization machines. Adds the machine's slots (subclass-defined) plus the
 * player inventory, and binds the machine's {@link ContainerData} so energy + progress sync to
 * the client automatically. Subclasses provide a server constructor (real block entity) and a
 * client constructor (dummy container + data the vanilla sync fills in).
 */
public abstract class MachineMenu extends AbstractContainerMenu {
	protected final Container machine;
	protected final ContainerData data;
	private final ContainerLevelAccess access;
	private final Block block;

	protected MachineMenu(MenuType<?> type, int syncId, Inventory playerInventory,
			Container machine, ContainerData data, ContainerLevelAccess access, Block block) {
		super(type, syncId);
		this.machine = machine;
		this.data = data;
		this.access = access;
		this.block = block;
		addMachineSlots();
		addPlayerInventory(playerInventory);
		addDataSlots(data);
	}

	/** Add the machine's own slots (slot indices 0..machineSize-1). */
	protected abstract void addMachineSlots();

	private void addPlayerInventory(Inventory inventory) {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, 8 + col * 18, 142));
		}
	}

	public int getEnergy() {
		return data.get(0);
	}

	public int getCapacity() {
		return data.get(1);
	}

	public int getProgress() {
		return data.get(2);
	}

	public int getMaxProgress() {
		return data.get(3);
	}

	/**
	 * Visual regression test helper — injects ContainerData without a server-side block entity.
	 * Only call this from client-game-test code; the values are overwritten on any real sync packet.
	 */
	public void injectTestData(int energy, int capacity, int progress, int maxProgress) {
		data.set(0, energy);
		data.set(1, capacity);
		data.set(2, progress);
		data.set(3, maxProgress);
	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(access, player, block);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			int machineSlots = machine.getContainerSize();
			int invEnd = machineSlots + 36;
			if (index < machineSlots) {
				if (!moveItemStackTo(stack, machineSlots, invEnd, true)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}
}
