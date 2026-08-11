package dev.alaindustrial.menu;

import dev.alaindustrial.storage.StorageWindow;
import java.util.function.Supplier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A chest menu whose container can hold more rows than the window shows, the rest reached by
 * scrolling — the machinery MOD-287 built for the warehouse window, extracted here (MOD-391) so the
 * double chest reuses it instead of duplicating it. Two things travel in a {@link ContainerData} of
 * width {@value #DATA_COUNT}: the total row count (the client sizes the scrollbar from it) and the
 * window's top row (the thumb position); the server owns both, and the width is pinned on
 * <em>both</em> sides — a menu whose two sides disagree on it crashes on the first sync.
 *
 * <p><b>The slots never move.</b> The menu's slot list is part of the network contract
 * ({@code ClientboundContainerSetSlot} addresses positions in it), so scrolling rewrites what the
 * {@link StorageWindow} behind the slots answers — one integer on the server — exactly like the
 * creative inventory, vanilla's only scrolling container. See {@link StorageWindow} for the maths.
 *
 * <p>Scrolling rides the vanilla {@code clickMenuButton} channel: the button id IS the requested top
 * row, clamped server-side by {@link StorageWindow#setTopRow} so a stale or hostile packet cannot
 * read past the container.
 */
public abstract class AbstractScrollingChestMenu extends AbstractChestMenu {
	/** {@code ContainerData} index of the container's total row count. */
	public static final int DATA_TOTAL_ROWS = 0;
	/** {@code ContainerData} index of the window's top row. */
	public static final int DATA_TOP_ROW = 1;
	protected static final int DATA_COUNT = 2;

	private final int rows;
	private final ContainerData data;
	/** Server side only — {@code null} on the client, which never translates a slot index itself. */
	private final StorageWindow window;

	protected AbstractScrollingChestMenu(MenuType<?> type, int syncId, Inventory playerInventory,
			Container storage, ContainerLevelAccess access, int rows, Supplier<Block> ownerBlock,
			StorageWindow window, ContainerData data) {
		super(type, syncId, playerInventory, storage, access, rows, ownerBlock);
		this.rows = rows;
		this.window = window;
		this.data = data;
		addDataSlots(data);
	}

	/** Rows of nine this window SHOWS — the screen sizes itself from this. */
	public int getRows() {
		return rows;
	}

	/** Rows of nine the container holds in total. */
	public int getTotalRows() {
		return Math.max(rows, data.get(DATA_TOTAL_ROWS));
	}

	/** The window's top row, as the server last reported it. */
	public int getTopRow() {
		return Math.max(0, Math.min(data.get(DATA_TOP_ROW), getMaxTopRow()));
	}

	/** How far the window can slide; 0 when everything is on screen and there is no scrolling. */
	public int getMaxTopRow() {
		return Math.max(0, getTotalRows() - rows);
	}

	/** Whether this window has rows the player cannot see without scrolling. */
	public boolean isScrollable() {
		return getMaxTopRow() > 0;
	}

	/**
	 * Scroll: the button id IS the requested top row. Clamped by {@link StorageWindow#setTopRow}, so a
	 * packet asking for row 500 lands on the last row rather than reading past the container.
	 */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (window == null) {
			return false;
		}
		window.setTopRow(id);
		return true;
	}

	/**
	 * Shift-click, with the scrolled-away rows included.
	 *
	 * <p>The base implementation moves between menu slots, and this menu only has slots for the rows
	 * currently in view — so on a taller container a shift-click would stop after filling the visible
	 * rows and report the store full while half of it was empty. Moving INTO the container therefore
	 * goes to the container behind the window; moving out of it is unchanged, because a player can
	 * only shift-click a slot they can see.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (window == null || window.maxTopRow() == 0 || index < rows * StorageWindow.COLUMNS) {
			return super.quickMoveStack(player, index);
		}
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack before = stack.copy();
		insertIntoWarehouse(stack);
		if (stack.getCount() == before.getCount()) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return before;
	}

	/**
	 * Merge {@code stack} into the whole backing container in place: first onto matching stacks that
	 * still have room, then into empty slots — the same two passes vanilla's {@code moveItemStackTo}
	 * makes, but over the container rather than over the menu's slots.
	 */
	private void insertIntoWarehouse(ItemStack stack) {
		Container warehouse = window.warehouse();
		int size = warehouse.getContainerSize();
		if (stack.isStackable()) {
			for (int i = 0; i < size && !stack.isEmpty(); i++) {
				ItemStack existing = warehouse.getItem(i);
				if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) {
					continue;
				}
				int limit = Math.min(warehouse.getMaxStackSize(existing), existing.getMaxStackSize());
				int moved = Math.min(stack.getCount(), limit - existing.getCount());
				if (moved > 0) {
					existing.grow(moved);
					stack.shrink(moved);
					warehouse.setItem(i, existing);
				}
			}
		}
		for (int i = 0; i < size && !stack.isEmpty(); i++) {
			if (!warehouse.getItem(i).isEmpty() || !warehouse.canPlaceItem(i, stack)) {
				continue;
			}
			int limit = Math.min(warehouse.getMaxStackSize(stack), stack.getMaxStackSize());
			warehouse.setItem(i, stack.split(Math.min(stack.getCount(), limit)));
		}
		warehouse.setChanged();
	}
}
