package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.DiamondChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageWindow;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Menu for the Diamond Chest (MOD-599) — 108 slots behind a {@value #VISIBLE_ROWS}-row scrolling
 * window, plus the player inventory and hotbar. The scrolling itself lives in
 * {@link AbstractScrollingChestMenu}; this class supplies the window size, the menu type and the
 * owning block, exactly as the electrum tier does.
 */
public class DiamondChestMenu extends AbstractScrollingChestMenu {
	/** Rows the window SHOWS. The container behind it holds twelve; the rest are scrolled to. */
	public static final int VISIBLE_ROWS = 6;

	/** Client side — a dummy 6-row container and a blank data block, both filled by vanilla sync. */
	public DiamondChestMenu(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(VISIBLE_ROWS * StorageWindow.COLUMNS),
				ContainerLevelAccess.NULL, null, new SimpleContainerData(DATA_COUNT));
	}

	private DiamondChestMenu(int syncId, Inventory playerInventory, Container storage,
			ContainerLevelAccess access, StorageWindow window, ContainerData data) {
		super(ModContent.DIAMOND_CHEST_MENU.get(), syncId, playerInventory, storage, access,
				VISIBLE_ROWS, () -> ModContent.DIAMOND_CHEST.get(), window, data);
	}

	/** Server side — the window slides over the block entity's own 108 slots. */
	public static DiamondChestMenu server(int syncId, Inventory playerInventory,
			DiamondChestBlockEntity chest) {
		StorageWindow window = new StorageWindow(chest, VISIBLE_ROWS);
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
		return new DiamondChestMenu(syncId, playerInventory, window,
				ContainerLevelAccess.create(chest.getLevel(), chest.getBlockPos()), window, data);
	}
}
