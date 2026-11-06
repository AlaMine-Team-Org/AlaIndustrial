package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.item.ItemHostRedirect;
import dev.alaindustrial.registry.BlockCapabilityRoster;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.Block;

/**
 * MOD-951: a block that lends another block's inventory ({@link ItemHostRedirect} — the quarry's socket foundation)
 * answers the item lookup with that inventory, sided as the host decides. An explicit provider, so it wins over
 * Fabric's {@code WorldlyContainerHolder} fallback and both loaders answer from the same rule.
 */
public final class ItemLenderStorage {

	private ItemLenderStorage() {
	}

	/** Called once the blocks are registered; registers the tank lenders too ({@link FluidLenderStorage}). */
	public static void register() {
		FluidLenderStorage.register();
		List<Block> lenders = BlockCapabilityRoster.itemLenders();
		if (lenders.isEmpty()) {
			return;
		}
		ItemStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> {
			WorldlyContainer lent = ItemHostRedirect.lent(level, pos, state, side);
			return lent == null ? null : ContainerStorage.of(lent, side);
		}, lenders.toArray(new Block[0]));
	}
}
