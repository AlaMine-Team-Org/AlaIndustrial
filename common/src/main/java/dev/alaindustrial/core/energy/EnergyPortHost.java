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
 *
 * <p><b>Implementing this on a manifest block entity IS the registration (MOD-433).</b> Both loaders
 * replay {@code BlockCapabilityRoster.energyHosts()} — every {@code ContentManifest.BLOCK_ENTITIES}
 * entry whose type implements this interface, minus the two pipes in
 * {@code BlockCapabilityRoster.NO_ENERGY_CAPABILITY} — so there is no per-loader line to add for a
 * new block entity, and a both-loader gametest sweep fails on any face where the capability and
 * {@link #energyPort(Direction)} disagree.
 */
public interface EnergyPortHost {

	/**
	 * The energy port exposed on {@code side}, or {@code null} if that face exposes no energy
	 * ({@link EnergyRole#NONE}). {@code side} is the world face being queried; never {@code null} —
	 * a side-less lookup goes through {@link #energyPortForLookup(Direction)}.
	 */
	EnergyPort energyPort(Direction side);

	/**
	 * What a capability lookup gets, including the side-less one (MOD-448).
	 *
	 * <p>Both loaders let a caller ask without naming a face — {@code BlockApiLookup#find(level, pos, null)}
	 * on Fabric, a {@code null} side on NeoForge — and viewer mods (Jade) ask that way about every block
	 * under the crosshair. Implementations read the face ({@code energyRoleForFace}, packed per-face pipe
	 * modes), so handing the {@code null} down throws; that is the defect this method exists to prevent,
	 * and it predates the manifest derivation of MOD-433.
	 *
	 * <p>The answer for "no particular side" is <em>whatever this block publishes anywhere</em>: the first
	 * face with a port. A block that exposes nothing on every face still answers {@code null}, so an inert
	 * block stays invisible to foreign lookups. Face rules are untouched — an automation mod naming a face
	 * still gets exactly that face's port.
	 */
	default EnergyPort energyPortForLookup(Direction side) {
		if (side != null) {
			return energyPort(side);
		}
		for (Direction face : Direction.values()) {
			EnergyPort port = energyPort(face);
			if (port != null) {
				return port;
			}
		}
		return null;
	}
}
