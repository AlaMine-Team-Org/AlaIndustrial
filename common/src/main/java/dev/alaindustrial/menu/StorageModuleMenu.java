package dev.alaindustrial.menu;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageCluster;
import dev.alaindustrial.storage.StorageWindow;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Shared spine of the modular-warehouse window (MOD-287) — three rows of nine per connected module,
 * shown through a window of at most {@link StorageCluster#MAX_VISIBLE_ROWS} rows. The scrolling
 * machinery itself (window, {@code ContainerData} pair, shift-click into hidden rows) lives in
 * {@link AbstractScrollingChestMenu} since MOD-391, shared with the double chest; this class keeps
 * the warehouse-specific parts: the owner block, and the size-per-cluster server factory.
 *
 * <p><b>Why two subclasses instead of one class with two sizes.</b> The client builds its menu from
 * {@code (syncId, Inventory)} alone: no block position, no payload, so it cannot look the cluster up
 * and cannot learn how tall the window should be. The size therefore has to be carried by the menu
 * type, exactly as vanilla does with {@code GENERIC_9x1 … 9x6}. Registering two types against
 * <em>one</em> class would work at runtime but would quietly disable the compile-time menu↔screen
 * guard (MOD-198): with every type erased to the same class, pointing a screen at the wrong size stops
 * being a type error.
 *
 * <p><b>Two sizes, not four.</b> A window is 3 rows for a lone module and 6 for anything larger; a
 * cluster of three or four modules is the same 6-row window with the rest reached by scrolling
 * (see {@link StorageWindow}). Nine- and twelve-row windows existed until the sizes were measured
 * against the GUI scale the game actually picks and did not fit — see the Balance section of the
 * module's spec.
 */
public abstract class StorageModuleMenu extends AbstractScrollingChestMenu {
	/** Client side — a dummy container of the right size and a blank data block, both filled by sync. */
	protected StorageModuleMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container storage,
			int rows) {
		this(type, syncId, playerInventory, storage, rows, null, new SimpleContainerData(DATA_COUNT));
	}

	protected StorageModuleMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container storage,
			int rows, StorageWindow window, ContainerData data) {
		super(type, syncId, playerInventory, storage, ContainerLevelAccess.NULL, rows,
				() -> ModContent.STORAGE_MODULE.get(), window, data);
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
