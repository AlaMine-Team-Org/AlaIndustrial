package dev.alaindustrial.block.entity;

import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Silver Chest block entity — the tier above the iron chest: 45 slots. All container, lid-animation,
 * opener-counting, sound and persistence behaviour is shared with the other tiers in
 * {@link AbstractChestBlockEntity}; this class only pins the size, the display name and the menu it
 * opens.
 */
public class SilverChestBlockEntity extends AbstractChestBlockEntity {
	/** One row more than the iron chest (36 → 45): 5 rows of 9 slots. */
	public static final int CONTAINER_SIZE = 45;

	public SilverChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SILVER_CHEST_BE.get(), pos, state, CONTAINER_SIZE, "block.alaindustrial.silver_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return new SilverChestMenu(syncId, playerInventory, this);
	}

	/** Client ticker — drives the lid interpolation each client tick. */
	public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, SilverChestBlockEntity entity) {
		entity.tickLid();
	}
}
