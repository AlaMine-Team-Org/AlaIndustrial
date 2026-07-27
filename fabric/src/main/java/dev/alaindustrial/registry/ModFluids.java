package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.fluid.OilFluid;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/**
 * Central Fabric registration for the mod's fluids (MOD-238): oil, still + flowing. Mirrors the
 * vanilla {@code Fluids} class shape (eager {@code Registry.register}); NeoForge registers the same
 * ids through {@code ModFluidsNeoForge} with {@code FluidType}-carrying subclasses.
 *
 * <p><b>Order is load-bearing:</b> {@link #init()} must run before {@code ModBlocks.init()} — the
 * oil {@code LiquidBlock} constructor builds its fluid-state cache through
 * {@code OilFluid.getSource()/getFlowing()}, which read the {@link ModContent} fluid handles bound
 * here.
 */
public final class ModFluids {
	private ModFluids() {
	}

	public static final ResourceKey<Fluid> OIL_KEY = key("oil");
	public static final FlowingFluid OIL = register(OIL_KEY, new OilFluid.Source());

	public static final ResourceKey<Fluid> FLOWING_OIL_KEY = key("flowing_oil");
	public static final FlowingFluid FLOWING_OIL = register(FLOWING_OIL_KEY, new OilFluid.Flowing());

	private static ResourceKey<Fluid> key(String path) {
		return ResourceKey.create(Registries.FLUID, Industrialization.id(path));
	}

	private static <T extends Fluid> T register(ResourceKey<Fluid> key, T fluid) {
		return Registry.register(BuiltInRegistries.FLUID, key, fluid);
	}

	static {
		// Bind the ModContent handles at CLASS LOAD, not only in init(). "ModFluids.init() runs
		// before ModBlocks.init()" is not enough: the Fabric gametest entrypoint locator
		// (FabricGameTestModInitializer, a 'main' entrypoint of fabric-gametest-api-v1) class-loads
		// the @GameTest suites — and through them ModBlocks — BEFORE this mod's onInitialize ever
		// runs, and the OilLiquidBlock constructor immediately reads ModContent.OIL while building
		// its fluid-state cache (LiquidBlock ctor -> OilFluid.getSource()). ModBlocks references
		// ModFluids.OIL in that block's field initializer, so this static block is guaranteed to
		// have completed by then (JLS class-init happens-before the field read returns).
		bind();
	}

	/** Triggers the eager registrations above and publishes them into the {@link ModContent} facade. */
	public static void init() {
		bind();
	}

	private static void bind() {
		ModContent.OIL = () -> OIL;
		ModContent.FLOWING_OIL = () -> FLOWING_OIL;
	}
}
