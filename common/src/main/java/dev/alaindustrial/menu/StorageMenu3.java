package dev.alaindustrial.menu;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageWindow;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/**
 * Warehouse window of 3 rows — a lone module, which never scrolls. See {@link StorageModuleMenu} for
 * why each size is its own class.
 */
public class StorageMenu3 extends StorageModuleMenu {
	private static final int ROWS = 3;

	/** Client side — a dummy container of the right size; the vanilla menu sync fills the stacks in. */
	public StorageMenu3(int syncId, Inventory playerInventory) {
		super(ModContent.STORAGE_MODULE_MENU_3.get(), syncId, playerInventory,
				new SimpleContainer(ROWS * StorageWindow.COLUMNS), ROWS);
	}

	/** Server side — bound to a window over the cluster's modules. */
	public StorageMenu3(int syncId, Inventory playerInventory, StorageWindow window, ContainerData data) {
		super(ModContent.STORAGE_MODULE_MENU_3.get(), syncId, playerInventory, window, ROWS, window, data);
	}
}
