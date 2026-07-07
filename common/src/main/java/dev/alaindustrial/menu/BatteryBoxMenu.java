package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/** Menu for the BatteryBox — no machine slots; shows stored energy + charge level. */
public class BatteryBoxMenu extends MachineMenu {
	/** Server side. */
	public BatteryBoxMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.BATTERY_BOX_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.BATTERY_BOX.get());
	}

	/** Client side. */
	public BatteryBoxMenu(int syncId, Inventory playerInventory) {
		super(ModContent.BATTERY_BOX_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new SimpleContainerData(5), ContainerLevelAccess.NULL, ModContent.BATTERY_BOX.get());
	}

	@Override
	protected void addMachineSlots() {
		// Buffer node — no inventory slots in v0.1.
	}

	/** Charge level as a percentage of capacity. */
	public int getChargePercent() {
		int cap = getCapacity();
		return cap > 0 ? getEnergy() * 100 / cap : 0;
	}

	/** Per-tick output cap (EU/t) this BatteryBox can emit from its output face. */
	public int getOutputRate() {
		return data.get(4);
	}
}
