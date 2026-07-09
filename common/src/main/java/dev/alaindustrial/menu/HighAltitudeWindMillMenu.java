package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/** Menu for the high-altitude wind mill (T2, LV) — no slots; energy readout only. */
public class HighAltitudeWindMillMenu extends MachineMenu {
	/** Server side. */
	public HighAltitudeWindMillMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.HIGH_ALTITUDE_WIND_MILL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.HIGH_ALTITUDE_WIND_MILL.get());
	}

	/** Client side. */
	public HighAltitudeWindMillMenu(int syncId, Inventory playerInventory) {
		super(ModContent.HIGH_ALTITUDE_WIND_MILL_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new SimpleContainerData(4), ContainerLevelAccess.NULL, ModContent.HIGH_ALTITUDE_WIND_MILL.get());
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
