package dev.alaindustrial.menu;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageWindow;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/**
 * Warehouse window of 6 rows — two, three or four connected modules. Three and four modules hold more
 * rows than this window shows and are scrolled; see {@link StorageWindow}.
 */
public class StorageMenu6 extends StorageModuleMenu {
	private static final int ROWS = 6;

	/** Client side — a dummy container of the right size; the vanilla menu sync fills the stacks in. */
	public StorageMenu6(int syncId, Inventory playerInventory) {
		super(ModContent.STORAGE_MODULE_MENU_6.get(), syncId, playerInventory,
				new SimpleContainer(ROWS * StorageWindow.COLUMNS), ROWS);
	}

	/** Server side — bound to a window over the cluster's modules. */
	public StorageMenu6(int syncId, Inventory playerInventory, StorageWindow window, ContainerData data) {
		super(ModContent.STORAGE_MODULE_MENU_6.get(), syncId, playerInventory, window, ROWS, window, data);
	}
}
