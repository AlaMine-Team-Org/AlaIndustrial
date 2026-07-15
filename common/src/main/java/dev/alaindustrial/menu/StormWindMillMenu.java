package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the storm wind mill (T2, LV): one rotor slot, no evolution chip slot. */
public class StormWindMillMenu extends MachineMenu {
	/** Server side. */
	public StormWindMillMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.STORM_WIND_MILL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.STORM_WIND_MILL.get());
	}

	/** Client side. */
	public StormWindMillMenu(int syncId, Inventory playerInventory) {
		super(ModContent.STORM_WIND_MILL_MENU.get(), syncId, playerInventory, new SimpleContainer(1 + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(4), ContainerLevelAccess.NULL, ModContent.STORM_WIND_MILL.get());
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, StormWindMillBlockEntity.ROTOR_SLOT, 84, 23) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(StormWindMillBlockEntity.ROTOR_SLOT, stack);
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

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}

	/** Wind mode: 0 no rotor, 1 roofed, 2 calm, 3 breeze, 4 gale, 5 storm. */
	public int getMode() {
		return getMaxProgress();
	}
}
