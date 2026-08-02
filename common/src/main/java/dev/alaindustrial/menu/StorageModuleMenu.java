package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageCluster;
import dev.alaindustrial.storage.StorageWindow;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared spine of the modular-warehouse window (MOD-287) — three rows of nine per connected module,
 * shown through a window of at most {@link StorageCluster#MAX_VISIBLE_ROWS} rows.
 *
 * <p><b>Why two subclasses instead of one class with two sizes.</b> The client builds its menu from
 * {@code (syncId, Inventory)} alone: no block position, no payload, so it cannot look the cluster up
 * and cannot learn how tall the window should be. The size therefore has to be carried by the menu
 * type, exactly as vanilla does with {@code GENERIC_9x1 … GENERIC_9x6}. Registering two types against
 * <em>one</em> class would work at runtime but would quietly disable the compile-time menu↔screen
 * guard (MOD-198): with every type erased to the same class, pointing a screen at the wrong size stops
 * being a type error.
 *
 * <p><b>Two sizes, not four.</b> A window is 3 rows for a lone module and 6 for anything larger; a
 * cluster of three or four modules is the same 6-row window with the rest reached by scrolling
 * (see {@link StorageWindow}). Nine- and twelve-row windows existed until the sizes were measured
 * against the GUI scale the game actually picks and did not fit — see the Balance section of the
 * module's spec.
 *
 * <p><b>The two synced numbers.</b> {@code ContainerData} slot 0 is the warehouse's total row count
 * and slot 1 is the top row of the window. The client needs the first to size the scrollbar and the
 * second to place its thumb; the server owns both. The width is 2 on <em>both</em> sides — a menu
 * whose two sides disagree on the width of its {@code ContainerData} crashes on the first sync.
 */
public abstract class StorageModuleMenu extends AbstractChestMenu {
	/** Slots one module contributes; the cluster and the menu must not disagree on this. */
	public static final int SLOTS_PER_MODULE = StorageModuleBlockEntity.CONTAINER_SIZE;
	/** {@code ContainerData} index of the warehouse's total row count. */
	public static final int DATA_TOTAL_ROWS = 0;
	/** {@code ContainerData} index of the window's top row. */
	public static final int DATA_TOP_ROW = 1;
	private static final int DATA_COUNT = 2;

	private final int rows;
	private final ContainerData data;
	/** Server side only — {@code null} on the client, which never translates a slot index itself. */
	private final StorageWindow window;

	/** Client side — a dummy container of the right size and a blank data block, both filled by sync. */
	protected StorageModuleMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container storage,
			int rows) {
		this(type, syncId, playerInventory, storage, rows, null, new SimpleContainerData(DATA_COUNT));
	}

	protected StorageModuleMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container storage,
			int rows, StorageWindow window, ContainerData data) {
		super(type, syncId, playerInventory, storage, ContainerLevelAccess.NULL, rows,
				() -> ModContent.STORAGE_MODULE.get());
		this.rows = rows;
		this.window = window;
		this.data = data;
		addDataSlots(data);
	}

	/** Rows of nine this window SHOWS (3 or 6) — the screen sizes itself from this. */
	public int getRows() {
		return rows;
	}

	/** Rows of nine the warehouse holds in total (3, 6, 9 or 12). */
	public int getTotalRows() {
		return Math.max(rows, data.get(DATA_TOTAL_ROWS));
	}

	/** The window's top row, as the server last reported it. */
	public int getTopRow() {
		return Math.max(0, Math.min(data.get(DATA_TOP_ROW), getMaxTopRow()));
	}

	/** How far the window can slide; 0 when the whole warehouse is on screen and there is no scrolling. */
	public int getMaxTopRow() {
		return Math.max(0, getTotalRows() - rows);
	}

	/** Whether this window has rows the player cannot see without scrolling. */
	public boolean isScrollable() {
		return getMaxTopRow() > 0;
	}

	/**
	 * Scroll: the button id IS the requested top row. Clamped by {@link StorageWindow#setTopRow}, so a
	 * packet asking for row 500 lands on the last row rather than reading past the warehouse.
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
	 * currently in view — so on a twelve-row warehouse a shift-click would stop after filling six rows
	 * and report the store full while half of it was empty. Moving INTO the warehouse therefore goes
	 * to the container behind the window; moving out of it is unchanged, because a player can only
	 * shift-click a slot they can see.
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
	 * Merge {@code stack} into the whole warehouse in place: first onto matching stacks that still
	 * have room, then into empty slots — the same two passes vanilla's {@code moveItemStackTo} makes,
	 * but over the container rather than over the menu's slots.
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

	/** Server side — the size follows the cluster, so the class does too. */
	public static AbstractContainerMenu server(int syncId, Inventory playerInventory, StorageCluster cluster) {
		int visibleRows = cluster.visibleRows();
		StorageWindow window = new StorageWindow(cluster.container(), visibleRows);
		ContainerData data = new ContainerData() {
			@Override
			public int get(int index) {
				return index == DATA_TOTAL_ROWS ? window.totalRows() : window.topRow();
			}

			@Override
			public void set(int index, int value) {
				if (index == DATA_TOP_ROW) {
					window.setTopRow(value);
				}
			}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		};
		return visibleRows <= StorageCluster.ROWS_PER_MODULE
				? new StorageMenu3(syncId, playerInventory, window, data)
				: new StorageMenu6(syncId, playerInventory, window, data);
	}
}
