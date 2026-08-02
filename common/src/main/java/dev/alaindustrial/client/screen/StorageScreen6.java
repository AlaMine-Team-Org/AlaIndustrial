package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.StorageMenu6;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Warehouse window of 6 rows — two modules or more, the larger ones scrolled. */
public class StorageScreen6 extends AbstractStorageScreen<StorageMenu6> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/storage_module_6.png");

	public StorageScreen6(StorageMenu6 menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, 6, IMAGE_WIDTH_SCROLL, TEXTURE);
	}
}
