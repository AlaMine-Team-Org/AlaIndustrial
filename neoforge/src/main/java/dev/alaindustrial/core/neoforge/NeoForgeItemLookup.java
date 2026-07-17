package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.ItemLookup;
import dev.alaindustrial.core.ItemPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** NeoForge item-capability implementation of the common item-port lookup seam. */
public final class NeoForgeItemLookup implements ItemLookup {
	@Override
	public ItemPort find(Level level, BlockPos pos, Direction side) {
		ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
		return NeoForgeItemPort.of(handler);
	}
}
