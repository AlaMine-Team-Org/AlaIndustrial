package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/** Menu for the storm wind mill (T2, LV) — no slots; energy readout only. */
public class StormWindMillMenu extends MachineMenu {
	/** Server side. */
	public StormWindMillMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.STORM_WIND_MILL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.STORM_WIND_MILL.get());
	}

	/** Client side. */
	public StormWindMillMenu(int syncId, Inventory playerInventory) {
		super(ModContent.STORM_WIND_MILL_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new SimpleContainerData(4), ContainerLevelAccess.NULL, ModContent.STORM_WIND_MILL.get());
	}

	@Override
	protected void addMachineSlots() {
		// No inventory — energy display only.
	}

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}
}
