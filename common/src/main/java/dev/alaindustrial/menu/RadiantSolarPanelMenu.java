package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/** Menu for the Mirror Concentrator — no machine slots; shows energy, production and sky mode. */
public class RadiantSolarPanelMenu extends MachineMenu {
	/** Server side. */
	public RadiantSolarPanelMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.RADIANT_SOLAR_PANEL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.RADIANT_SOLAR_PANEL.get());
	}

	/** Client side. */
	public RadiantSolarPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.RADIANT_SOLAR_PANEL_MENU.get(), syncId, playerInventory,
				new SimpleContainer(RadiantSolarPanelBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(MachineBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.RADIANT_SOLAR_PANEL.get());
	}

	@Override
	protected void addMachineSlots() {
		// No inventory slots — the top of the branch has nothing left to slot a chip for.
	}

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}

	/** Sky mode; carried on the maxProgress channel. See the {@code MODE_*} codes on the block entity. */
	public int getMode() {
		return getMaxProgress();
	}
}
