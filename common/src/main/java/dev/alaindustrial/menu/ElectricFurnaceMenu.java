package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the LV electric furnace (input slot + result-only output slot). */
public class ElectricFurnaceMenu extends MachineMenu {
	/** Server side. */
	public ElectricFurnaceMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.ELECTRIC_FURNACE_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.ELECTRIC_FURNACE.get());
	}

	/** Client side. */
	public ElectricFurnaceMenu(int syncId, Inventory playerInventory) {
		super(ModContent.ELECTRIC_FURNACE_MENU.get(), syncId, playerInventory, new SimpleContainer(2 + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(4), ContainerLevelAccess.NULL, ModContent.ELECTRIC_FURNACE.get());
	}

	@Override
	protected void addMachineSlots() {
		// Slot positions aligned to the electric_furnace.png GUI atlas.
		addSlot(new Slot(machine, 0, 56, 35));
		// Output slot: result only, no manual insertion (spec).
		addSlot(new Slot(machine, 1, 117, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
	}
}
