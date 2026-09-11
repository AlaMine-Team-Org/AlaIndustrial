package dev.alaindustrial.core.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Platform-neutral per-side energy lookup (MOD-022 Phase 2): resolves the {@link EnergyPort} exposed by
 * the block at a world position on a given face, or {@code null} if none. This is the neutral face of the
 * loaders' capability lookups — Fabric {@code EnergyStorage.SIDED.find(level, pos, side)} versus NeoForge
 * {@code level.getCapability(Capabilities.Energy.BLOCK, pos, side)} — so common transport can walk the
 * network graph without importing loader capability types.
 *
 * <p>The active implementation is installed once at mod init by each loader's entrypoint via
 * {@link #install(EnergyLookup)}; common code reaches it through {@link #get()}.
 */
public interface EnergyLookup {

	/** The energy port exposed at {@code pos} on {@code side}, or {@code null} if that face exposes none. */
	EnergyPort find(Level level, BlockPos pos, Direction side);

	// --- service locator (installed by the loader entrypoint) ---

	EnergyLookup[] INSTANCE = new EnergyLookup[1];

	/**
	 * Install the loader's implementation (called once from the loader entrypoint at mod init).
	 *
	 * <p>Wrapped so that a block lending another block's port ({@link EnergyHostRedirect}, MOD-608) is
	 * resolved here, once, for every caller: the network's endpoint scan, its per-tick face checks, the
	 * direct push and the statistics panel. Resolving it inside each loader's lookup would be two copies
	 * of one rule, and NeoForge's copy would have to run before its fallback, which reads a bare
	 * capability as {@link EnergyRole#BOTH}.
	 */
	static void install(EnergyLookup impl) {
		INSTANCE[0] = (level, pos, side) -> {
			BlockPos host = EnergyHostRedirect.hostOf(level, pos, side);
			return host == null ? null : impl.find(level, host, side);
		};
	}

	/** The installed loader implementation. Throws if the entrypoint has not installed one yet. */
	static EnergyLookup get() {
		EnergyLookup impl = INSTANCE[0];
		if (impl == null) {
			throw new IllegalStateException(
					"EnergyLookup not installed — the loader entrypoint must call install() at init");
		}
		return impl;
	}
}
