package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.fluid.FluidHostRedirect;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.registry.BlockCapabilityRoster;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.world.level.block.Block;

/**
 * MOD-952: a block that lends another block's tank ({@link FluidHostRedirect} - the quarry's socket foundation)
 * answers the fluid lookup with that tank; the same rule NeoForge replays.
 */
public final class FluidLenderStorage {

	private FluidLenderStorage() {
	}

	/** Called once the blocks are registered. */
	public static void register() {
		List<Block> lenders = BlockCapabilityRoster.fluidLenders();
		if (lenders.isEmpty()) {
			return;
		}
		FluidStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> {
			FluidPort lent = FluidHostRedirect.lent(level, pos, state, side);
			return lent == null ? null : TankAsFluidStorage.of(lent);
		}, lenders.toArray(new Block[0]));
	}
}
