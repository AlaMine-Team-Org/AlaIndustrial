package dev.alaindustrial.core.energy;

import net.minecraft.core.Direction;

/**
 * Per-side energy exposure contract (MOD-022 Phase 2 scaffold). A block entity that holds an energy
 * buffer implements this so each loader's capability lookup can obtain the face-scoped
 * {@link EnergyPort}:
 * <ul>
 *   <li><b>Fabric</b> — {@code EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> ...)}.</li>
 *   <li><b>NeoForge</b> — {@code RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.Energy.BLOCK, ...)}.</li>
 * </ul>
 */
public interface EnergyPortHost {

	/**
	 * The energy port exposed on {@code side}, or {@code null} if that face exposes no energy
	 * ({@link EnergyRole#NONE}). {@code side} is the world face being queried.
	 */
	EnergyPort energyPort(Direction side);
}
