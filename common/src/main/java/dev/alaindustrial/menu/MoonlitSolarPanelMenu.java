package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Menu for the Moonlit Solar Panel — no machine slots; shows energy + production + sky mode.
 * Night-mirror of {@link SolarPanelMenu}.
 */
public class MoonlitSolarPanelMenu extends MachineMenu {
	/** Server side. */
	public MoonlitSolarPanelMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.MOONLIT_SOLAR_PANEL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.MOONLIT_SOLAR_PANEL.get());
	}

	/** Client side. */
	public MoonlitSolarPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.MOONLIT_SOLAR_PANEL_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new SimpleContainerData(4), ContainerLevelAccess.NULL, ModContent.MOONLIT_SOLAR_PANEL.get());
	}

	@Override
	protected void addMachineSlots() {
		// No inventory slots.
	}

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}

	/**
	 * Sky mode: 0 inactive/day, 1 night clear, 2 night weather; carried on the maxProgress channel.
	 */
	public int getMode() {
		return getMaxProgress();
	}
}
