package dev.alaindustrial.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Shared menu for the mod's tiered storage chests (iron 4 rows → silver 5 → gold 6). Backed by the
 * block entity's {@link Container}; deliberately does NOT extend {@link MachineMenu}, because a chest
 * has no energy or progress to sync and the {@code ContainerData} plumbing of the machine base would
 * be dead weight.
 *
 * <p><b>Layout.</b> The tiers use the same GUI atlas geometry, just taller by one 18px row each, so
 * every coordinate is derived from {@code rows} rather than hardcoded per tier: the chest grid starts
 * at {@code (8, 18)}, the player inventory sits {@link #PLAYER_INV_GAP} px below the last chest row,
 * and the hotbar {@link #HOTBAR_GAP} px below the inventory. That reproduces the previously
 * hand-written values exactly — iron 103/161, silver 121/179, gold 139/197 — so the GUI textures need
 * no change, and a new tier only has to pass its row count.
 */
public abstract class AbstractChestMenu extends AbstractContainerMenu {
	/** Top-of-grid Y for the chest rows. */
	private static final int CHEST_TOP_Y = 18;
	/** Left X of every 9-wide row (chest + player + hotbar all share this). */
	private static final int GRID_LEFT_X = 8;
	/** Stride between adjacent slots (1px bevel + 16px slot). */
	private static final int SLOT_STRIDE = 18;
	/** Vertical gap between the last chest row and the player inventory. */
	private static final int PLAYER_INV_GAP = 13;
	/** Vertical gap between the last inventory row and the hotbar. */
	private static final int HOTBAR_GAP = 4;
	private static final int COLS = 9;
	/** Rows of the player's own inventory (excluding the hotbar). */
	private static final int PLAYER_INV_ROWS = 3;
	/** Player inventory + hotbar slot count — the span shift-click moves items across. */
	private static final int PLAYER_SLOT_COUNT = PLAYER_INV_ROWS * COLS + COLS;

	private final Container chest;
	private final ContainerLevelAccess access;
	private final Supplier<Block> ownerBlock;
	private final int rows;

	protected AbstractChestMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container chest,
			ContainerLevelAccess access, int rows, Supplier<Block> ownerBlock) {
		super(type, syncId);
		this.chest = chest;
		this.access = access;
		this.ownerBlock = ownerBlock;
		this.rows = rows;
		checkContainerSize(chest, rows * COLS);

		int playerInvTopY = CHEST_TOP_Y + rows * SLOT_STRIDE + PLAYER_INV_GAP;
		int hotbarTopY = playerInvTopY + PLAYER_INV_ROWS * SLOT_STRIDE + HOTBAR_GAP;

		// Chest grid.
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < COLS; col++) {
				addSlot(new Slot(chest, row * COLS + col,
						GRID_LEFT_X + col * SLOT_STRIDE,
						CHEST_TOP_Y + row * SLOT_STRIDE));
			}
		}
		// Player inventory.
		for (int row = 0; row < PLAYER_INV_ROWS; row++) {
			for (int col = 0; col < COLS; col++) {
				addSlot(new Slot(playerInventory, COLS + row * COLS + col,
						GRID_LEFT_X + col * SLOT_STRIDE,
						playerInvTopY + row * SLOT_STRIDE));
			}
		}
		// Hotbar.
		for (int col = 0; col < COLS; col++) {
			addSlot(new Slot(playerInventory, col, GRID_LEFT_X + col * SLOT_STRIDE, hotbarTopY));
		}
		// Notify the container a player opened it — this drives the opener counter on the chest block
		// entity, which broadcasts a block event so the lid lifts (matches vanilla ChestMenu).
		chest.startOpen(playerInventory.player);
	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(access, player, ownerBlock.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			int chestEnd = rows * COLS;
			int invEnd = chestEnd + PLAYER_SLOT_COUNT;
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
		// Notify the container the player closed it — decrements the opener counter on the chest block
		// entity so the lid lowers (matches vanilla ChestMenu#removed).
		super.removed(player);
		chest.stopOpen(player);
	}

	/** The chest inventory backing this menu (for screen/HUD lookups). */
	public Container getContainer() {
		return chest;
	}
}
