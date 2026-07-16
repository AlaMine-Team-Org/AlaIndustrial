package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Gold Chest — 54 storage slots (6 rows of 9, one row more than the silver chest's 45)
 * plus the player inventory + hotbar. Identical structure to {@link IronChestMenu} /
 * {@link SilverChestMenu}.
 *
 * <p>Slot coordinates mirror the {@code gold_chest.png} GUI atlas (176×220, one row taller than the
 * silver chest's 176×202): chest grid starts at {@code (8, 18)} with an 18px stride, the player
 * inventory starts at {@code (8, 139)} (silver: 121, +18 for the extra chest row), and the hotbar
 * at {@code (8, 197)} (silver: 179, +18). The visible GUI panel is 176×220.
 */
public class GoldChestMenu extends AbstractContainerMenu {
	/** Top-of-grid Y for the 6 chest rows. */
	private static final int CHEST_TOP_Y = 18;
	/** Top-of-grid Y for the 3 player-inventory rows (silver: 121 + 18 for the extra chest row). */
	private static final int PLAYER_INV_TOP_Y = 139;
	/** Top-of-grid Y for the hotbar row (silver: 179 + 18). */
	private static final int HOTBAR_TOP_Y = 197;
	/** Left X of every 9-wide row (chest + player + hotbar all share this). */
	private static final int GRID_LEFT_X = 8;
	/** Stride between adjacent slots (1px bevel + 16px slot). */
	private static final int SLOT_STRIDE = 18;
	private static final int ROWS = 6;
	private static final int COLS = 9;

	private final Container chest;
	private final ContainerLevelAccess access;

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
		super(type, syncId);
		this.chest = chest;
		this.access = access;
		checkContainerSize(chest, ROWS * COLS);
		// Chest grid (slot indices 0..53).
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLS; col++) {
				addSlot(new Slot(chest, row * COLS + col,
						GRID_LEFT_X + col * SLOT_STRIDE,
						CHEST_TOP_Y + row * SLOT_STRIDE));
			}
		}
		// Player inventory (3 rows).
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < COLS; col++) {
				addSlot(new Slot(playerInventory, 9 + row * COLS + col,
						GRID_LEFT_X + col * SLOT_STRIDE,
						PLAYER_INV_TOP_Y + row * SLOT_STRIDE));
			}
		}
		// Hotbar.
		for (int col = 0; col < COLS; col++) {
			addSlot(new Slot(playerInventory, col,
					GRID_LEFT_X + col * SLOT_STRIDE,
					HOTBAR_TOP_Y));
		}
		// Notify the container a player opened it — this drives the opener counter on the gold chest
		// block entity, which broadcasts a block event so the lid lifts (matches vanilla ChestMenu).
		chest.startOpen(playerInventory.player);
	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(access, player, ModContent.GOLD_CHEST.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			int chestEnd = ROWS * COLS;
			int invEnd = chestEnd + 36;
			if (index < chestEnd) {
				// Shift-click inside the chest → move to the player inventory/hotbar.
				if (!moveItemStackTo(stack, chestEnd, invEnd, true)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(stack, 0, chestEnd, false)) {
				// Shift-click from player inv → move into the chest.
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}

	@Override
	public void removed(Player player) {
		// Notify the container the player closed it — decrements the opener counter on the gold
		// chest block entity so the lid lowers (matches vanilla ChestMenu#removed).
		super.removed(player);
		chest.stopOpen(player);
	}

	/** The chest inventory backing this menu (for screen/HUD lookups). */
	public Container getContainer() {
		return chest;
	}
}
