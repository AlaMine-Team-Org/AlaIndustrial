package dev.alaindustrial.block.entity;

import dev.alaindustrial.menu.GoldChestMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gold Chest block entity — the top storage tier: 54 slots (double the vanilla chest). All container,
 * lid-animation, opener-counting, sound and persistence behaviour is shared with the other tiers in
 * {@link AbstractChestBlockEntity}; this class only pins the size, the display name and the menu it
 * opens.
 */
public class GoldChestBlockEntity extends AbstractChestBlockEntity {
	/** One row more than the silver chest (45 → 54): 6 rows of 9 slots. */
	public static final int CONTAINER_SIZE = 54;

	public GoldChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.GOLD_CHEST_BE.get(), pos, state, CONTAINER_SIZE, "block.alaindustrial.gold_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return new GoldChestMenu(syncId, playerInventory, this);
	}

	/** Client ticker — drives the lid interpolation each client tick. */
	public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, GoldChestBlockEntity entity) {
		entity.tickLid();
	}
}
