package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Menu for the LV wind mill (no slots — energy readout only). */
public class WindMillMenu extends MachineMenu {
	/** Server side. */
	public WindMillMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.WIND_MILL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.WIND_MILL.get());
	}

	/** Client side. */
	public WindMillMenu(int syncId, Inventory playerInventory) {
		super(ModContent.WIND_MILL_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new net.minecraft.world.inventory.SimpleContainerData(4), ContainerLevelAccess.NULL, ModContent.WIND_MILL.get());
	}

	@Override
	protected void addMachineSlots() {
		// Wind mill has no inventory — energy display only.
	}
}
