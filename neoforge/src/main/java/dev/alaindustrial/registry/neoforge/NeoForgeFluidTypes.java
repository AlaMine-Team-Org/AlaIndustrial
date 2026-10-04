package dev.alaindustrial.registry.neoforge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import org.jspecify.annotations.Nullable;

/**
 * Which {@link FluidType} each of the mod's fluids has (MOD-708): recorded by {@code ModFluidsNeoForge} when
 * it builds a fluid, read by {@code FluidTypeLookupMixin} when NeoForge first asks the fluid for its type.
 *
 * <p>A class of its own, with no other static state, because the mixin reaches it from
 * {@code Fluid.getFluidType()} — which vanilla fluids answer long before the mod's registers exist — and
 * initialising it must not pull those registers in.
 */
public final class NeoForgeFluidTypes {
	private static final Map<Fluid, Supplier<FluidType>> TYPE_OF = new ConcurrentHashMap<>();

	private NeoForgeFluidTypes() {
	}

	/** Records that {@code fluid} has the type {@code type} resolves to (lazily: types register after fluids). */
	static void record(Fluid fluid, Supplier<FluidType> type) {
		TYPE_OF.put(fluid, type);
	}

	/** The type of a fluid the mod registered, or {@code null} for any other fluid. */
	public static @Nullable FluidType typeOf(Fluid fluid) {
		Supplier<FluidType> type = TYPE_OF.get(fluid);
		return type == null ? null : type.get();
	}
}
