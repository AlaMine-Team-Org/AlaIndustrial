package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidPort;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Fabric implementation of the neutral {@link FluidLookup} SPI (MOD-028): resolves the per-face fluid port
 * through Fabric's {@code FluidStorage.SIDED} block lookup and wraps the result as a neutral
 * {@link FluidPort}. This is the per-loader capability-lookup seam — mirrors {@link FabricEnergyLookup}.
 */
public final class FabricFluidLookup implements FluidLookup {

	@Override
	public FluidPort find(Level level, BlockPos pos, Direction side) {
		return FabricFluidPort.of(FluidStorage.SIDED.find(level, pos, side));
	}
}
