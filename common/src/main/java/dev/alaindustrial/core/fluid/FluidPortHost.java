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
 *
 * <p><b>Implementing this on a manifest block entity IS the registration (MOD-433).</b> Both loaders
 * replay {@code BlockCapabilityRoster.fluidHosts()} — every {@code ContentManifest.BLOCK_ENTITIES}
 * entry whose type implements this interface — so there is no per-loader line to add for a new block
 * entity, and a both-loader gametest sweep fails on any face where the capability and
 * {@link #fluidPort(Direction)} disagree.
 */
public interface FluidPortHost {

	/**
	 * The fluid port exposed on {@code side}, or {@code null} if that face exposes no fluid tank.
	 * {@code side} is the world face being queried; never {@code null} — a side-less lookup goes through
	 * {@link #fluidPortForLookup(Direction)}.
	 */
	FluidPort fluidPort(Direction side);

	/**
	 * What a capability lookup gets, including the side-less one (MOD-448) — the fluid counterpart of
	 * {@link EnergyPortHost#energyPortForLookup(Direction)}, with the same contract: a named face answers
	 * with that face's port, "no particular side" answers with the first face that publishes one.
	 *
	 * <p>Answering the side-less query with nothing is <em>not</em> an option here: our own fluid HUD
	 * reads the tank that way (TC-FLUID-MOD126 — a bucket-fed Geothermal Generator must publish its lava
	 * to the HUD through a {@code side=null} lookup), so returning {@code null} would blank the readout.
	 */
	default FluidPort fluidPortForLookup(Direction side) {
		if (side != null) {
			return fluidPort(side);
		}
		for (Direction face : Direction.values()) {
			FluidPort port = fluidPort(face);
			if (port != null) {
				return port;
			}
		}
		return null;
	}
}
