package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.monitor.ItemView;
import dev.alaindustrial.core.monitor.ItemViewLookup;
import dev.alaindustrial.core.monitor.StockTally;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge implementation of the read-only storage lookup (MOD-480).
 *
 * <p>Reads through the 26.2 {@code ResourceHandler} API — the legacy item handler is gone. No
 * transaction is opened: a mutation inside one would make this handler snapshot every slot it owns,
 * which is pure allocation for work that never happens here.
 */
public final class NeoForgeItemViewLookup implements ItemViewLookup {

	@Override
	public @Nullable ItemView find(Level level, BlockPos pos, @Nullable Direction side) {
		ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
		if (handler == null) {
			return null;
		}
		return sink -> tally(handler, sink);
	}

	private static void tally(ResourceHandler<ItemResource> handler, StockTally sink) {
		for (int slot = 0; slot < handler.size(); slot++) {
			ItemResource resource = handler.getResource(slot);
			long amount = handler.getAmountAsLong(slot);
			if (resource.isEmpty() || amount <= 0L) {
				continue;
			}
			for (int i = 0; i < sink.size(); i++) {
				ItemStack sample = sink.sample(i);
				if (resource.matches(sample)) {
					sink.add(i, amount);
					break;
				}
			}
		}
	}
}
