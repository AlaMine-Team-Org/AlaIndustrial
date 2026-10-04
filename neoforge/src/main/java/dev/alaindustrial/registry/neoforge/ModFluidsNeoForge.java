package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModFluidsManifest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge fluid registration: a replay of the shared {@link ModFluidsManifest#FLUIDS} list (MOD-708)
 * through two {@link DeferredRegister}s — the vanilla FLUID registry and NeoForge's FLUID_TYPES, one
 * {@link FluidType} per entry, under the entry's id.
 *
 * <p><b>No subclass per fluid.</b> NeoForge patches {@code Fluid.getFluidType()} to throw for a fluid it
 * does not know ({@code CommonHooks.getVanillaFluidType}). Instead of a subclass of every common fluid
 * class overriding it, the common fluid instances are registered as they are and recorded here with their
 * type's holder ({@link NeoForgeFluidTypes}); {@code FluidTypeLookupMixin} answers {@code getFluidType()} from that
 * record the first time NeoForge asks. The holder resolves lazily — FLUID_TYPES is a modded registry and
 * fires after the vanilla ones, exactly as the old subclasses' {@code OIL_TYPE.get()} did.
 *
 * <p><b>Event order:</b> the FLUID RegisterEvent fires before BLOCK (vanilla registration order in
 * {@code BuiltInRegistries}), so the liquid block factories may resolve their fluids while they build the
 * {@code LiquidBlock} fluid-state cache.
 */
public final class ModFluidsNeoForge {
	public static final DeferredRegister<Fluid> FLUIDS =
			DeferredRegister.create(Registries.FLUID, Industrialization.MOD_ID);
	public static final DeferredRegister<FluidType> FLUID_TYPES =
			DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Industrialization.MOD_ID);

	/** The {@link ModContent} bindings, run by {@link #init()}. */
	private static final List<Runnable> BINDERS = new ArrayList<>();

	static {
		for (ModFluidsManifest.FluidDef<?> def : ModFluidsManifest.FLUIDS) {
			register(def);
		}
	}

	private ModFluidsNeoForge() {
	}

	/** One entry: its fluid type, then its still and flowing fluids, each recorded with that type. */
	private static <T extends Fluid> void register(ModFluidsManifest.FluidDef<T> def) {
		DeferredHolder<FluidType, FluidType> type =
				FLUID_TYPES.register(def.id(), () -> new FluidType(typeProperties(def)));
		DeferredHolder<Fluid, T> source = FLUIDS.register(def.id(), () -> withType(def.source().get(), type));
		BINDERS.add(() -> def.bindSource().accept(source::get));
		if (def.hasFlowing()) {
			DeferredHolder<Fluid, T> flowing =
					FLUIDS.register(def.flowingId(), () -> withType(def.flowing().get(), type));
			BINDERS.add(() -> def.bindFlowing().accept(flowing::get));
		}
	}

	private static <T extends Fluid> T withType(T fluid, Supplier<FluidType> type) {
		NeoForgeFluidTypes.record(fluid, type);
		return fluid;
	}

	/**
	 * The {@link FluidType.Properties} of an entry, split out so the L1.5 guards can build a type without
	 * booting the registries (see {@code NeoForgeOilWorldGenTest}).
	 *
	 * <p><b>Every entity-physics field is set explicitly, and that is the point (MOD-238 audit, MOD-495).</b>
	 * The defaults are {@code canSwim=true}, {@code canDrown=true}, {@code canPushEntity=true},
	 * {@code motionScale=0.014}, {@code fallDistanceModifier=0.5F} — i.e. "behaves like water". NeoForge
	 * runs its {@code IEntityExtension} fluid-type integration ({@code Entity#getFallDistanceModifier},
	 * {@code isInFluidMatching} / {@code canSwimInFluidType}, {@code LivingEntity#getFluidTypeHeight}), so
	 * implicit defaults would let NeoForge players swim and drown in oil while Fabric players — whose custom
	 * fluids have no entity physics at all — fall through it. The common fluid classes carry the shared
	 * immersion physics ({@code FluidImmersion}) instead, on both loaders. The guard is
	 * {@code NeoForgeOilWorldGenTest#oilFluidTypeDeclaresItsEntityPhysics}.
	 *
	 * <p>{@code descriptionId} comes from the entry: the block's existing key for a world fluid, rather than
	 * the derived {@code fluid_type.alaindustrial.<id>} that has no translation — any foreign GUI naming the
	 * fluid type (JEI/EMI fluid entries, other mods' tanks) would print the raw key.
	 */
	public static FluidType.Properties typeProperties(ModFluidsManifest.FluidDef<?> def) {
		return FluidType.Properties.create()
				.descriptionId(def.descriptionId())
				.density(def.physics().density())
				.temperature(def.physics().temperature())
				.viscosity(def.physics().viscosity())
				.canConvertToSource(false)
				.canSwim(false)
				.canDrown(false)
				.canPushEntity(false)
				.motionScale(0.0D)
				.fallDistanceModifier(1.0F)
				.supportsBoating(false);
	}

	/** The crude-oil type properties (MOD-238) — what {@code NeoForgeOilWorldGenTest} asserts. */
	public static FluidType.Properties oilTypeProperties() {
		return typeProperties(ModFluidsManifest.byId("oil"));
	}

	/**
	 * Binds the fluid {@code DeferredHolder}s into the loader-neutral {@link ModContent} facade, mirroring the
	 * Fabric {@code ModFluids}. Called from the {@code @Mod} constructor after {@code FLUIDS.register(modBus)};
	 * the holders resolve lazily after the FLUID RegisterEvent.
	 */
	public static void init() {
		for (Runnable binder : BINDERS) {
			binder.run();
		}
	}
}
