package dev.alaindustrial.core.fluid;

import net.minecraft.core.Direction;
import dev.alaindustrial.core.energy.EnergyPortHost;

/**
 * Per-side fluid exposure contract (MOD-028), the fluid counterpart of {@link EnergyPortHost}. A block
 * entity that holds a fluid tank implements this so each loader's capability lookup can obtain the
 * face-scoped {@link FluidPort}:
 * <ul>
 *   <li><b>Fabric</b> — {@code FluidStorage.SIDED.registerForBlockEntity((be, dir) -> ...)}.</li>
 *   <li><b>NeoForge</b> — {@code RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.Fluid.BLOCK, ...)}.</li>
 * </ul>
 */
public interface FluidPortHost {

	/**
	 * The fluid port exposed on {@code side}, or {@code null} if that face exposes no fluid tank.
	 * {@code side} is the world face being queried.
	 */
	FluidPort fluidPort(Direction side);
}
