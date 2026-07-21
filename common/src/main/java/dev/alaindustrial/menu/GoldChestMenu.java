package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;

/**
 * Menu for the Gold Chest — 54 storage slots (6 rows of 9) plus the player inventory and hotbar.
 * Layout, shift-click behaviour and opener bookkeeping live in {@link AbstractChestMenu}; this tier
 * only supplies its row count, menu type and owning block.
 */
public class GoldChestMenu extends AbstractChestMenu {
	private static final int ROWS = 6;

	/** Server side — bound to the real block entity's inventory. */
	public GoldChestMenu(int syncId, Inventory playerInventory, GoldChestBlockEntity chest) {
		this(ModContent.GOLD_CHEST_MENU.get(), syncId, playerInventory, chest,
				ContainerLevelAccess.create(chest.getLevel(), chest.getBlockPos()));
	}

	/** Client side — a dummy empty container the vanilla menu-sync fills in. */
	public GoldChestMenu(int syncId, Inventory playerInventory) {
		this(ModContent.GOLD_CHEST_MENU.get(), syncId, playerInventory,
				new SimpleContainer(GoldChestBlockEntity.CONTAINER_SIZE), ContainerLevelAccess.NULL);
	}

	private GoldChestMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container chest,
			ContainerLevelAccess access) {
		super(type, syncId, playerInventory, chest, access, ROWS, () -> ModContent.GOLD_CHEST.get());
	}
}
