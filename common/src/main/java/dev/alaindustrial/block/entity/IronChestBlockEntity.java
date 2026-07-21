package dev.alaindustrial.block.entity;

import dev.alaindustrial.menu.IronChestMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Iron Chest block entity — the entry storage tier: 36 slots (one row more than the vanilla 27-slot
 * chest). All container, lid-animation, opener-counting, sound and persistence behaviour is shared
 * with the other tiers in {@link AbstractChestBlockEntity}; this class only pins the size, the display
 * name and the menu it opens.
 */
public class IronChestBlockEntity extends AbstractChestBlockEntity {
	/** One row more than the vanilla chest (27 → 36): 4 rows of 9 slots. */
	public static final int CONTAINER_SIZE = 36;

	public IronChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.IRON_CHEST_BE.get(), pos, state, CONTAINER_SIZE, "block.alaindustrial.iron_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return new IronChestMenu(syncId, playerInventory, this);
	}

	/** Client ticker — drives the lid interpolation each client tick. */
	public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, IronChestBlockEntity entity) {
		entity.tickLid();
	}
}
