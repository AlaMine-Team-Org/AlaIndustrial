package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.item.ItemHostRedirect;
import dev.alaindustrial.registry.BlockCapabilityRoster;
import java.util.List;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * MOD-951: a block that lends another block's inventory ({@link ItemHostRedirect} — the quarry's socket foundation)
 * publishes that inventory as its own item capability, sided as the host decides; the same rule Fabric replays.
 */
public final class ItemLenderCapabilities {

	private ItemLenderCapabilities() {
	}

	public static void register(RegisterCapabilitiesEvent event) {
		List<Block> lenders = BlockCapabilityRoster.itemLenders();
		if (lenders.isEmpty()) {
			return;
		}
		event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> {
			WorldlyContainer lent = ItemHostRedirect.lent(level, pos, state, side);
			return lent == null ? null : ContainerAsItemResourceHandler.of(lent, side);
		}, lenders.toArray(new Block[0]));
	}
}
