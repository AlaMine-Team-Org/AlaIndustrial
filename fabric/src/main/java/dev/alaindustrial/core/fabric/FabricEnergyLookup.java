package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric implementation of the neutral {@link EnergyLookup} SPI (MOD-022 Phase 2): resolves the per-face
 * energy port through Team Reborn's {@code EnergyStorage.SIDED} block lookup and wraps the result as a
 * neutral {@link EnergyPort}. This is the per-loader capability-lookup seam.
 */
public final class FabricEnergyLookup implements EnergyLookup {

	@Override
	public EnergyPort find(Level level, BlockPos pos, Direction side) {
		return FabricEnergyPort.of(EnergyStorage.SIDED.find(level, pos, side));
	}
}
