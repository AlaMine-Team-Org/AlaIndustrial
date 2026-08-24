package dev.alaindustrial.block.entity;

import dev.alaindustrial.menu.ShieldingChestMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shielding Chest block entity (MOD-474) — 36 slots, the same size as the iron chest, because this
 * block sells shielding rather than room (see {@link dev.alaindustrial.block.ShieldingChestBlock}).
 *
 * <p><b>This class is the shielding.</b> Not through anything it does — it does nothing special at
 * all — but by being its own type: {@code RadiationSources.collectContainers} walks the world's
 * containers and skips exactly this one. That is why the exemption is written against the type and
 * not, say, against a marker interface or a block tag: a datapack cannot hand shielding to a barrel,
 * and a future chest tier cannot acquire it by accident.
 *
 * <p>All container, lid-animation, opener-counting, sound and persistence behaviour is shared with the
 * storage tiers in {@link AbstractChestBlockEntity}.
 */
public class ShieldingChestBlockEntity extends AbstractChestBlockEntity {
	/** Same as the iron chest (36): the block gives no storage advantage, only shielding. */
	public static final int CONTAINER_SIZE = 36;

	public ShieldingChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SHIELDING_CHEST_BE.get(), pos, state, CONTAINER_SIZE,
				"block.alaindustrial.shielding_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return new ShieldingChestMenu(syncId, playerInventory, this);
	}
}
