package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.ItemLookup;
import dev.alaindustrial.core.ItemPort;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Fabric Transfer API implementation of the common item-port lookup seam. */
public final class FabricItemLookup implements ItemLookup {
	@Override
	public ItemPort find(Level level, BlockPos pos, Direction side) {
		return FabricItemPort.of(ItemStorage.SIDED.find(level, pos, side));
	}
}
