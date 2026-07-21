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

	/** Install the loader's implementation (called once from the loader entrypoint at mod init). */
	static void install(EnergyLookup impl) {
		INSTANCE[0] = impl;
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
