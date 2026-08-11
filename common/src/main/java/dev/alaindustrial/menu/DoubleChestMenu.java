package dev.alaindustrial.menu;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageWindow;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * The double chest's window (MOD-391) — one menu type for all three tiers. A double holds 72 / 90 /
 * 108 slots (8 / 10 / 12 rows), none of which fits the guaranteed 240 px of GUI height, so the window
 * shows {@value #VISIBLE_ROWS} rows — the vanilla double chest's height, the tallest container window
 * the game itself ships — and the rest is scrolled ({@link AbstractScrollingChestMenu}).
 *
 * <p><b>One type, not three.</b> The visible size is the same for every tier's double; the only
 * per-tier difference is the total row count, and that travels in the {@code ContainerData} the
 * scrolling base already syncs — exactly how the warehouse window reports its cluster size. The
 * per-tier window <em>title</em> arrives with the open packet from the block's {@code MenuProvider}.
 *
 * <p>The server side wraps the two halves' {@code CompoundContainer} (right half first — vanilla's
 * FIRST/SECOND order) in a {@link StorageWindow}; the client builds a dummy 6-row container that the
 * vanilla slot sync fills in.
 */
public class DoubleChestMenu extends AbstractScrollingChestMenu {
	/** Rows the window shows; the panel is then 220 px — the vanilla double chest's height. */
	public static final int VISIBLE_ROWS = 6;

	/** Client side — a dummy 6-row container and a blank data block, both filled by sync. */
	public DoubleChestMenu(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(VISIBLE_ROWS * StorageWindow.COLUMNS),
				null, new SimpleContainerData(DATA_COUNT));
	}

	private DoubleChestMenu(int syncId, Inventory playerInventory, Container storage,
			StorageWindow window, ContainerData data) {
		// The owner-block supplier is never dereferenced: stillValid below replaces the block check
		// with the container's own reach check, and a single type serves all three tier blocks.
		super(ModContent.DOUBLE_CHEST_MENU.get(), syncId, playerInventory, storage,
				ContainerLevelAccess.NULL, VISIBLE_ROWS, () -> null, window, data);
	}

	/** Server side — {@code joined} is the two halves as one container, right (FIRST) half first. */
	public static DoubleChestMenu server(int syncId, Inventory playerInventory, Container joined) {
		StorageWindow window = new StorageWindow(joined, VISIBLE_ROWS);
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
		return new DoubleChestMenu(syncId, playerInventory, window, window, data);
	}

	/**
	 * Vanilla {@code ChestMenu} semantics instead of the owner-block check: the window is valid while
	 * BOTH halves are still there and in reach ({@code CompoundContainer.stillValid} AND-s them), so
	 * breaking either half under an open window closes it. On the client the dummy container always
	 * answers true, which matches vanilla — the server is the authority.
	 */
	@Override
	public boolean stillValid(Player player) {
		return getContainer().stillValid(player);
	}
}
