package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageWindow;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Menu for the Electrum Chest (MOD-409) — 81 slots behind a {@value #VISIBLE_ROWS}-row scrolling
 * window, plus the player inventory and hotbar.
 *
 * <p><b>The first SINGLE chest of the mod that scrolls.</b> The scrolling machinery is not new — it
 * was built for the modular warehouse (MOD-287) and reused by the double chest (MOD-391) — but until
 * now it only ever sat behind a multi-block or a pair. Here it sits behind one block, for the reason
 * spelled out in {@link ElectrumChestBlockEntity}: six rows (220 px) is the tallest container panel
 * that fits the 240 px the game guarantees, so a taller tier has to scroll rather than grow.
 *
 * <p>Everything that makes scrolling work — the sliding {@link StorageWindow}, the two-value
 * {@code ContainerData}, and the shift-click that reaches rows currently off screen — lives in
 * {@link AbstractScrollingChestMenu}. This class only supplies the window size, the menu type and
 * the owning block.
 */
public class ElectrumChestMenu extends AbstractScrollingChestMenu {
	/** Rows the window SHOWS. The container behind it holds nine; the rest are scrolled to. */
	public static final int VISIBLE_ROWS = 6;

	/** Client side — a dummy 6-row container and a blank data block, both filled by vanilla sync. */
	public ElectrumChestMenu(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(VISIBLE_ROWS * StorageWindow.COLUMNS),
				ContainerLevelAccess.NULL, null, new SimpleContainerData(DATA_COUNT));
	}

	private ElectrumChestMenu(int syncId, Inventory playerInventory, Container storage,
			ContainerLevelAccess access, StorageWindow window, ContainerData data) {
		super(ModContent.ELECTRUM_CHEST_MENU.get(), syncId, playerInventory, storage, access,
				VISIBLE_ROWS, () -> ModContent.ELECTRUM_CHEST.get(), window, data);
	}

	/**
	 * Server side — the window slides over the block entity's own 81 slots. The same
	 * {@link StorageWindow} is passed twice on purpose: it IS the container the menu's slots read
	 * (translated by the current top row) and it IS the scroll state the data block reports.
	 */
	public static ElectrumChestMenu server(int syncId, Inventory playerInventory,
			ElectrumChestBlockEntity chest) {
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
		return new ElectrumChestMenu(syncId, playerInventory, window,
				ContainerLevelAccess.create(chest.getLevel(), chest.getBlockPos()), window, data);
	}
}
