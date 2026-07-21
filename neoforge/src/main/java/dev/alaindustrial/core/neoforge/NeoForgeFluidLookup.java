package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import dev.alaindustrial.core.energy.EnergyRole;

/**
 * NeoForge implementation of the neutral {@link FluidLookup} SPI (MOD-028): resolves the per-face fluid
 * port through {@code Capabilities.Fluid.BLOCK} (verified against 26.2.0.8-beta:
 * {@code BlockCapability<ResourceHandler<FluidResource>, @Nullable Direction>}) and wraps the result as a
 * neutral {@link FluidPort}. This is the per-loader capability-lookup seam — mirrors
 * {@link NeoForgeEnergyLookup}.
 *
 * <p><b>Identity preservation (mirrors {@link NeoForgeEnergyLookup}'s load-bearing lesson).</b> For our own
 * blocks, take the face-scoped {@link FluidPort} straight from {@link FluidPortHost} rather than round
 * tripping through {@code Capabilities.Fluid.BLOCK}: unlike energy's per-face role, a fluid tank's
 * {@code canInsert}/{@code canExtract} predicates (e.g. the geothermal generator's tank always refusing
 * extraction, R-CON-08) are baked into the {@link dev.alaindustrial.core.fluid.FluidTank} instance itself, so
 * they survive the capability round-trip either way. Still, resolving the host directly avoids an
 * unnecessary capability-registry hop for our own blocks and mirrors the energy lookup's precedent, so any
 * future per-face fluid restriction added to {@link FluidPortHost} is not silently lost the way per-face
 * {@code EnergyRole} would have been. Foreign blocks fall back to the capability.
 */
public final class NeoForgeFluidLookup implements FluidLookup {

	@Override
	public FluidPort find(Level level, BlockPos pos, Direction side) {
		if (level.getBlockEntity(pos) instanceof FluidPortHost host) {
			return host.fluidPort(side);
		}
		ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
		return NeoForgeFluidPort.of(handler);
	}
}
