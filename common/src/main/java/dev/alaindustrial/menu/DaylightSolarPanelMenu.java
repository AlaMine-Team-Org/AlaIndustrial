package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/** Menu for the Daylight Solar Panel — no machine slots; shows energy + production + sky mode. */
public class DaylightSolarPanelMenu extends MachineMenu {
	/** Server side. */
	public DaylightSolarPanelMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.DAYLIGHT_SOLAR_PANEL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.DAYLIGHT_SOLAR_PANEL.get());
	}

	/** Client side. */
	public DaylightSolarPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.DAYLIGHT_SOLAR_PANEL_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new SimpleContainerData(4), ContainerLevelAccess.NULL, ModContent.DAYLIGHT_SOLAR_PANEL.get());
	}

	@Override
	protected void addMachineSlots() {
		// No inventory slots.
	}

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}

	/** Sky mode: 0 inactive/night, 1 day clear, 2 day weather; carried on the maxProgress channel. */
	public int getMode() {
		return getMaxProgress();
	}
}
