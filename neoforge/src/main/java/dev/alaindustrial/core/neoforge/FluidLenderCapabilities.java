package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.fluid.FluidHostRedirect;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.registry.BlockCapabilityRoster;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * MOD-952: a block that lends another block's tank ({@link FluidHostRedirect} - the quarry's socket foundation)
 * publishes that tank as its own fluid capability; the same rule Fabric replays.
 */
public final class FluidLenderCapabilities {

	private FluidLenderCapabilities() {
	}

	public static void register(RegisterCapabilitiesEvent event) {
		List<Block> lenders = BlockCapabilityRoster.fluidLenders();
		if (lenders.isEmpty()) {
			return;
		}
		event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
			FluidPort lent = FluidHostRedirect.lent(level, pos, state, side);
			return lent == null ? null : TankAsResourceHandler.of(lent);
		}, lenders.toArray(new Block[0]));
	}
}
