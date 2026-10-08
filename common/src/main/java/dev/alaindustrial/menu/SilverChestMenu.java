package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;

/**
 * Menu for the Silver Chest — 45 storage slots (5 rows of 9) plus the player inventory and hotbar.
 * Layout, shift-click behaviour and opener bookkeeping live in {@link AbstractChestMenu}; this tier
 * only supplies its row count, menu type and owning block.
 */
public class SilverChestMenu extends AbstractChestMenu {
	private static final int ROWS = 5;

	/** Server side — bound to the real block entity's inventory. */
	public SilverChestMenu(int syncId, Inventory playerInventory, SilverChestBlockEntity chest) {
		this(ModContent.SILVER_CHEST_MENU.get(), syncId, playerInventory, chest,
				ContainerLevelAccess.create(chest.getLevel(), chest.getBlockPos()));
	}

	/** Client side — a dummy empty container the vanilla menu-sync fills in. */
	public SilverChestMenu(int syncId, Inventory playerInventory) {
		this(ModContent.SILVER_CHEST_MENU.get(), syncId, playerInventory,
				new SimpleContainer(SilverChestBlockEntity.CONTAINER_SIZE), ContainerLevelAccess.NULL);
	}

	/** Server side over a chest boat's container (MOD-785) — validity is the boat's own check. */
	public static SilverChestMenu forEntity(int syncId, Inventory playerInventory, Container chest) {
		return new SilverChestMenu(ModContent.SILVER_CHEST_MENU.get(), syncId, playerInventory, chest,
				ContainerLevelAccess.NULL);
	}

	private SilverChestMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container chest,
			ContainerLevelAccess access) {
		super(type, syncId, playerInventory, chest, access, ROWS, () -> ModContent.SILVER_CHEST.get());
	}
}
