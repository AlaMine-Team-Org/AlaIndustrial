package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the LV water mill: one required, non-consumed water-wheel component. */
public class WaterMillMenu extends MachineMenu {
	/** Server side. */
	public WaterMillMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.WATER_MILL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.WATER_MILL.get());
	}

	/** Client side. */
	public WaterMillMenu(int syncId, Inventory playerInventory) {
		super(ModContent.WATER_MILL_MENU.get(), syncId, playerInventory, new SimpleContainer(1 + UPGRADE_SLOT_COUNT),
				new net.minecraft.world.inventory.SimpleContainerData(4), ContainerLevelAccess.NULL, ModContent.WATER_MILL.get());
	}

	/**
	 * Status mode carried by the {@code maxProgress} sync channel (slot 3):
	 * {@link WaterMillBlockEntity#MODE_OK} or {@link WaterMillBlockEntity#MODE_INTERFERENCE}. The screen
	 * uses it to show the "wheels clash" label.
	 */
	public int getMode() {
		return getMaxProgress();
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, WaterMillBlockEntity.WHEEL_SLOT, 84, 23) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(WaterMillBlockEntity.WHEEL_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
	}

	@Override
	protected int playerInventoryY() {
		return 96;
	}

	@Override
	protected int hotbarY() {
		return 154;
	}
}
