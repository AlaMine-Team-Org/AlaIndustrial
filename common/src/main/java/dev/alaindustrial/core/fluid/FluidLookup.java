package dev.alaindustrial.core.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import dev.alaindustrial.core.energy.EnergyLookup;

/**
 * Platform-neutral per-side fluid lookup (MOD-028): resolves the {@link FluidPort} exposed by the block at
 * a world position on a given face, or {@code null} if none. This is the neutral face of the loaders'
 * fluid capability lookups — Fabric {@code FluidStorage.SIDED.find(level, pos, side)} versus NeoForge
 * {@code level.getCapability(Capabilities.Fluid.BLOCK, pos, side)} — so common content (the pump) can walk
 * to a neighbour's fluid tank without importing loader capability types. Mirrors {@link EnergyLookup}.
 *
 * <p>The active implementation is installed once at mod init by each loader's entrypoint via
 * {@link #install(FluidLookup)}; common code reaches it through {@link #get()}.
 */
public interface FluidLookup {

	/** The fluid port exposed at {@code pos} on {@code side}, or {@code null} if that face exposes none. */
	FluidPort find(Level level, BlockPos pos, Direction side);

	// --- service locator (installed by the loader entrypoint) ---

	FluidLookup[] INSTANCE = new FluidLookup[1];

	/** Install the loader's implementation (called once from the loader entrypoint at mod init). */
	static void install(FluidLookup impl) {
		INSTANCE[0] = impl;
	}

	/** The installed loader implementation. Throws if the entrypoint has not installed one yet. */
	static FluidLookup get() {
		FluidLookup impl = INSTANCE[0];
		if (impl == null) {
			throw new IllegalStateException(
					"FluidLookup not installed — the loader entrypoint must call install() at init");
		}
		return impl;
	}
}
