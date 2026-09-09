package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.monitor.ItemView;
import dev.alaindustrial.core.monitor.ItemViewLookup;
import dev.alaindustrial.core.monitor.StockTally;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric implementation of the read-only storage lookup (MOD-480).
 *
 * <p>No transaction is opened, and that is not an optimisation: a transaction exists to make a
 * change reversible, and this code never changes anything. Iterating the storage's views is a plain
 * read.
 */
public final class FabricItemViewLookup implements ItemViewLookup {

	@Override
	public @Nullable ItemView find(Level level, BlockPos pos, @Nullable Direction side) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
		if (storage == null) {
			return null;
		}
		return sink -> tally(storage, sink);
	}

	private static void tally(Storage<ItemVariant> storage, StockTally sink) {
		for (StorageView<ItemVariant> view : storage) {
			if (view.isResourceBlank() || view.getAmount() <= 0L) {
				continue;
			}
			ItemVariant variant = view.getResource();
			for (int i = 0; i < sink.size(); i++) {
				ItemStack sample = sink.sample(i);
				if (variant.matches(sample)) {
					sink.add(i, view.getAmount());
					break;
				}
			}
		}
	}
}
