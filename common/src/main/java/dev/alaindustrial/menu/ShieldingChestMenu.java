package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;

/**
 * Menu for the Shielding Chest (MOD-474) — 36 storage slots (4 rows of 9) plus the player inventory
 * and hotbar, the same window the iron chest opens. Layout, shift-click behaviour and opener
 * bookkeeping live in {@link AbstractChestMenu}; this chest only supplies its row count, menu type and
 * owning block.
 */
public class ShieldingChestMenu extends AbstractChestMenu {
	private static final int ROWS = 4;

	/** Server side — bound to the real block entity's inventory. */
	public ShieldingChestMenu(int syncId, Inventory playerInventory, ShieldingChestBlockEntity chest) {
		this(ModContent.SHIELDING_CHEST_MENU.get(), syncId, playerInventory, chest,
				ContainerLevelAccess.create(chest.getLevel(), chest.getBlockPos()));
	}

	/** Client side — a dummy empty container the vanilla menu-sync fills in. */
	public ShieldingChestMenu(int syncId, Inventory playerInventory) {
		this(ModContent.SHIELDING_CHEST_MENU.get(), syncId, playerInventory,
				new SimpleContainer(ShieldingChestBlockEntity.CONTAINER_SIZE), ContainerLevelAccess.NULL);
	}

	private ShieldingChestMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container chest,
			ContainerLevelAccess access) {
		super(type, syncId, playerInventory, chest, access, ROWS, () -> ModContent.SHIELDING_CHEST.get());
	}
}
