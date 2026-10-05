package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * Fabric fluid registration: a replay of the shared {@link ModFluidsManifest#FLUIDS} list (MOD-708).
 * Eager {@code Registry.register} like the vanilla {@code Fluids} class; NeoForge queues the same entries
 * through {@code ModFluidsNeoForge}, with a {@code FluidType} each.
 *
 * <p><b>Order is load-bearing:</b> the fluids must be registered and bound before {@code ModBlocks}
 * builds the liquid blocks — the oil {@code LiquidBlock} constructor builds its fluid-state cache through
 * {@code OilFluid.getSource()/getFlowing()}, which read the {@link ModContent} fluid handles bound here.
 */
public final class ModFluids {
	private ModFluids() {
	}

	/**
	 * Every fluid, registered and bound into {@link ModContent} the moment this class loads — not only in
	 * {@link #init()}. "ModFluids.init() runs before ModBlocks.init()" is not enough: the Fabric gametest
	 * entrypoint locator ({@code FabricGameTestModInitializer}, a 'main' entrypoint of fabric-gametest-api-v1)
	 * class-loads the {@code @GameTest} suites — and through them {@code ModBlocks} — BEFORE this mod's
	 * {@code onInitialize} ever runs, and the {@code OilLiquidBlock} constructor immediately reads
	 * {@code ModContent.OIL} while building its fluid-state cache. {@code ModBlocks} calls {@link #init()}
	 * before its first block factory, so this class's initialisation has completed by then (JLS class-init
	 * happens-before the call returns).
	 */
	private static final Map<String, Fluid> REGISTERED = registerAll();

	private static Map<String, Fluid> registerAll() {
		Map<String, Fluid> registered = new LinkedHashMap<>();
		for (ModFluidsManifest.FluidDef<?> def : ModFluidsManifest.FLUIDS) {
			register(def, registered);
		}
		return Map.copyOf(registered);
	}

	/** One entry: the still fluid, then the flowing form, each bound into its slot as a constant supplier. */
	private static <T extends Fluid> void register(ModFluidsManifest.FluidDef<T> def, Map<String, Fluid> registered) {
		T source = Registry.register(BuiltInRegistries.FLUID, key(def.id()), def.source().get());
		def.bindSource().accept(() -> source);
		put(registered, def.id(), source);
		if (def.hasFlowing()) {
			T flowing = Registry.register(BuiltInRegistries.FLUID, key(def.flowingId()), def.flowing().get());
			def.bindFlowing().accept(() -> flowing);
			put(registered, def.flowingId(), flowing);
		}
	}

	private static void put(Map<String, Fluid> registered, String id, Fluid fluid) {
		if (registered.put(id, fluid) != null) {
			throw new IllegalStateException("ModFluidsManifest.FLUIDS declares fluid id '" + id + "' twice");
		}
	}

	private static ResourceKey<Fluid> key(String path) {
		return ResourceKey.create(Registries.FLUID, Industrialization.id(path));
	}

	/**
	 * Class-load trigger: registration and binding happen in the static initializer above, so this only has
	 * to touch the class. Idempotent; {@code ModBlocks} calls it before its first block factory.
	 */
	public static void init() {
		if (REGISTERED.isEmpty()) {
			throw new IllegalStateException("ModFluids registered no fluid");
		}
	}

	/**
	 * Registers the Transfer API attribute handler of every entry that declares one — today only crude oil,
	 * whose handler reports its viscosity; the other fluids keep the Transfer API defaults on Fabric (a known
	 * defect with its own task). One handler serves the still and the flowing form.
	 */
	public static void registerTransferAttributes() {
		for (ModFluidsManifest.FluidDef<?> def : ModFluidsManifest.FLUIDS) {
			if (!def.transferAttributes()) {
				continue;
			}
			int viscosity = def.physics().viscosity();
			FluidVariantAttributeHandler handler = new FluidVariantAttributeHandler() {
				@Override
				public int getViscosity(FluidVariant variant, Level level) {
					return viscosity;
				}
			};
			FluidVariantAttributes.register(REGISTERED.get(def.id()), handler);
			if (def.hasFlowing()) {
				FluidVariantAttributes.register(REGISTERED.get(def.flowingId()), handler);
			}
		}
	}
}
