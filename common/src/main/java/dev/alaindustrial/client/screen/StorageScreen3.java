package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.StorageMenu3;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Warehouse window of 3 rows — a lone module, which has nothing to scroll to. */
public class StorageScreen3 extends AbstractStorageScreen<StorageMenu3> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/storage_module_3.png");

	public StorageScreen3(StorageMenu3 menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, 3, IMAGE_WIDTH, TEXTURE);
	}
}
