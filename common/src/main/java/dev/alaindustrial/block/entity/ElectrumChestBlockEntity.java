package dev.alaindustrial.block.entity;

import dev.alaindustrial.menu.ElectrumChestMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electrum Chest block entity (MOD-409) — the top storage tier: 81 slots, nine rows of nine. All
 * container, lid-animation, opener-counting, sound and persistence behaviour is shared with the
 * other tiers in {@link AbstractChestBlockEntity}; this class only pins the size, the display name
 * and the menu it opens.
 *
 * <p><b>Why the jump is +27 and not the usual +9.</b> The three tiers below grow by one row each
 * (36 → 45 → 54) because their windows grow with them, and that stopped being possible exactly at
 * gold: a six-row panel is 220 px, which is already the vanilla double chest — the tallest container
 * window the game itself ships — and the guaranteed GUI height is 240 px. A seventh row would leave
 * a one-pixel margin. This tier therefore changes the WINDOW rather than adding a row: the nine rows
 * are reached through a six-row scrolling window ({@link ElectrumChestMenu}), and once the window is
 * no longer the limit there is no reason to charge a full electrum ring for a token nine slots.
 */
public class ElectrumChestBlockEntity extends AbstractChestBlockEntity {
	/** Nine rows of nine — a full square, where the gold chest was a 6×9 rectangle. */
	public static final int CONTAINER_SIZE = 81;

	public ElectrumChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ELECTRUM_CHEST_BE.get(), pos, state, CONTAINER_SIZE,
				"block.alaindustrial.electrum_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		// Server-side factory rather than a constructor: the menu needs its StorageWindow built
		// before the super() call, and that view is both the menu's container and its scroll state.
		return ElectrumChestMenu.server(syncId, playerInventory, this);
	}
}
