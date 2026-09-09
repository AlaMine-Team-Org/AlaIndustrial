package dev.alaindustrial.block.entity;

import dev.alaindustrial.menu.DiamondChestMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Diamond Chest block entity (MOD-599) — the top storage tier: 108 slots, twelve rows of nine.
 * Everything else (container, lid animation, opener counting, sound, persistence) is shared with the
 * other tiers in {@link AbstractChestBlockEntity}.
 *
 * <p><b>Why 108 and not more.</b> The window is the limit, not the block: a container panel may be
 * six rows tall before it runs out of the 240 px the game guarantees, and the scrolling window built
 * for the electrum tier reaches twelve rows through those six. Twelve rows is where that machinery
 * ends — a thirteenth would need a different window, not a different chest.
 *
 * <p>A pair is 216 slots and costs no code: {@code DoubleChestMenu} takes its row count from the
 * container it is given.
 */
public class DiamondChestBlockEntity extends AbstractChestBlockEntity {
	/** Twelve rows of nine — a third more than the electrum tier's nine. */
	public static final int CONTAINER_SIZE = 108;

	public DiamondChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.DIAMOND_CHEST_BE.get(), pos, state, CONTAINER_SIZE,
				"block.alaindustrial.diamond_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return DiamondChestMenu.server(syncId, playerInventory, this);
	}
}
