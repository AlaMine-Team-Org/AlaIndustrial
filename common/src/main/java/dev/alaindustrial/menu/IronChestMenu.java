package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;

/**
 * Menu for the Iron Chest — 36 storage slots (4 rows of 9) plus the player inventory and hotbar.
 * Layout, shift-click behaviour and opener bookkeeping live in {@link AbstractChestMenu}; this tier
 * only supplies its row count, menu type and owning block.
 */
public class IronChestMenu extends AbstractChestMenu {
	private static final int ROWS = 4;

	/** Server side — bound to the real block entity's inventory. */
	public IronChestMenu(int syncId, Inventory playerInventory, IronChestBlockEntity chest) {
		this(ModContent.IRON_CHEST_MENU.get(), syncId, playerInventory, chest,
				ContainerLevelAccess.create(chest.getLevel(), chest.getBlockPos()));
	}

	/** Client side — a dummy empty container the vanilla menu-sync fills in. */
	public IronChestMenu(int syncId, Inventory playerInventory) {
		this(ModContent.IRON_CHEST_MENU.get(), syncId, playerInventory,
				new SimpleContainer(IronChestBlockEntity.CONTAINER_SIZE), ContainerLevelAccess.NULL);
	}

	private IronChestMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container chest,
			ContainerLevelAccess access) {
		super(type, syncId, playerInventory, chest, access, ROWS, () -> ModContent.IRON_CHEST.get());
	}
}
