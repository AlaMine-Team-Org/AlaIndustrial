package dev.alaindustrial.storage;

import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A sliding view of {@value #COLUMNS}-wide rows over a taller warehouse (MOD-287, defect 2).
 *
 * <p>A cluster of four modules holds twelve rows and its window was 328 px tall, which does not fit
 * the screen at the GUI scale the game picks by itself: {@code Window.calculateScale} grows the scale
 * while {@code height / (scale + 1) >= 240}, so the usable height at "auto" is never more than about
 * 270 px and is exactly 240 on a 1440p or 4K screen. The window is therefore capped at
 * {@link StorageCluster#MAX_VISIBLE_ROWS} rows and the rows beyond it are reached by scrolling.
 *
 * <p><b>Why a view instead of moving the slots.</b> {@code Slot.x} and {@code Slot.y} are
 * {@code public final} and a menu's slot list is part of the network contract — the indices in
 * {@code ClientboundContainerSetSlot} are positions in that list, so rebuilding it under a live menu
 * desynchronises the client. Vanilla's one scrolling container (the creative inventory) solves this
 * the same way: the slots never move, and scrolling rewrites what the container behind them answers.
 * Here {@link #getItem(int)} simply adds {@code topRow * 9} before asking the warehouse, so a scroll
 * is one integer and the menu's slot list never changes shape.
 *
 * <p>The view is a server-side object. The client's menu is built from {@code (syncId, Inventory)}
 * with a dummy container that vanilla's own slot sync fills in, so it never needs to translate an
 * index — it only needs to know the total row count and where the thumb sits, which travel as two
 * {@code ContainerData} values.
 */
public final class StorageWindow implements Container {
	public static final int COLUMNS = 9;

	private final Container warehouse;
	private final int visibleRows;
	private int topRow;

	public StorageWindow(Container warehouse, int visibleRows) {
		this.warehouse = warehouse;
		this.visibleRows = visibleRows;
	}

	/** The whole warehouse behind the window — what a shift-click has to be allowed to reach. */
	public Container warehouse() {
		return warehouse;
	}

	/** Rows of nine the warehouse holds in total (9 for three modules, 12 for four). */
	public int totalRows() {
		return warehouse.getContainerSize() / COLUMNS;
	}

	/** The furthest the window can slide; 0 when the whole warehouse already fits. */
	public int maxTopRow() {
		return Math.max(0, totalRows() - visibleRows);
	}

	public int topRow() {
		return topRow;
	}

	/** Slide the window, clamping to the warehouse — a stale or hostile packet cannot read past it. */
	public void setTopRow(int row) {
		this.topRow = Math.max(0, Math.min(row, maxTopRow()));
	}

	private int index(int slot) {
		return slot + topRow * COLUMNS;
	}

	private boolean valid(int slot) {
		return slot >= 0 && slot < getContainerSize() && index(slot) < warehouse.getContainerSize();
	}

	@Override
	public int getContainerSize() {
		return visibleRows * COLUMNS;
	}

	@Override
	public boolean isEmpty() {
		for (int slot = 0; slot < getContainerSize(); slot++) {
			if (!getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return valid(slot) ? warehouse.getItem(index(slot)) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		return valid(slot) ? warehouse.removeItem(index(slot), amount) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return valid(slot) ? warehouse.removeItemNoUpdate(index(slot)) : ItemStack.EMPTY;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (valid(slot)) {
			warehouse.setItem(index(slot), stack);
		}
	}

	@Override
	public void setChanged() {
		warehouse.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return warehouse.stillValid(player);
	}

	@Override
	public void clearContent() {
		warehouse.clearContent();
	}

	@Override
	public void startOpen(ContainerUser user) {
		warehouse.startOpen(user);
	}

	@Override
	public void stopOpen(ContainerUser user) {
		warehouse.stopOpen(user);
	}

	@Override
	public int getMaxStackSize() {
		return warehouse.getMaxStackSize();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return valid(slot) && warehouse.canPlaceItem(index(slot), stack);
	}
}
