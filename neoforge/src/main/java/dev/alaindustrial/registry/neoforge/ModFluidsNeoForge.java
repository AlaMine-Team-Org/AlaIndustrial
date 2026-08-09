package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.fluid.DieselFluid;
import dev.alaindustrial.fluid.FuelOilFluid;
import dev.alaindustrial.fluid.OilFluid;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge fluid registration (MOD-238). Mirrors the Fabric {@code ModFluids} set 1:1 (same ids)
 * through two {@link DeferredRegister}s: the vanilla FLUID registry and NeoForge's own FLUID_TYPES.
 *
 * <p><b>Why local subclasses:</b> NeoForge patches {@code Fluid.getFluidType()} to throw
 * ({@code "Mod fluids must override getFluidType"}), so the common {@link OilFluid} variants cannot
 * be registered directly — {@link Still}/{@link Flowing} add the override, resolving the
 * {@link #OIL_TYPE} holder lazily at runtime (FLUID_TYPES is a modded registry and fires after the
 * vanilla ones).
 *
 * <p><b>Event order (verified 26.2):</b> the FLUID RegisterEvent fires before BLOCK (vanilla
 * registration order in {@code BuiltInRegistries}), so {@code ModBlocksNeoForge}'s oil block factory
 * may call {@code OIL.get()} safely while it builds the {@code LiquidBlock} fluid-state cache.
 */
public final class ModFluidsNeoForge {
	public static final DeferredRegister<Fluid> FLUIDS =
			DeferredRegister.create(Registries.FLUID, Industrialization.MOD_ID);
	public static final DeferredRegister<FluidType> FLUID_TYPES =
			DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Industrialization.MOD_ID);

	/** Physical behaviour NeoForge derives from the type: heavy-ish, thick, never re-forms sources. */
	public static final DeferredHolder<FluidType, FluidType> OIL_TYPE =
			FLUID_TYPES.register("oil", () -> new FluidType(oilTypeProperties()));

	/**
	 * The oil {@link FluidType.Properties}, split out so the L1 guard can assert them without booting
	 * the registries (see {@code NeoForgeOilFluidTypeTest}).
	 *
	 * <p><b>Every entity-physics field is set explicitly, and that is the point (MOD-238 audit).</b>
	 * The defaults are {@code canSwim=true}, {@code canDrown=true}, {@code canPushEntity=true},
	 * {@code motionScale=0.014}, {@code fallDistanceModifier=0.5F} — i.e. "behaves like water". In
	 * NeoForge 26.2.0.8-beta the whole {@code IEntityExtension} fluid-type integration
	 * ({@code isInFluidType}, {@code getFluidTypeHeight}, {@code canStartSwimming} and their
	 * {@code CommonHooks} call sites) is commented out, so none of those defaults do anything today
	 * and NeoForge accidentally matches Fabric, where custom fluids have no entity physics at all
	 * (decision 6 in the task: both loaders — "not water", the player falls straight through). The
	 * day NeoForge re-enables that code the accident ends: NeoForge players would start swimming and
	 * drowning in oil while Fabric players fall through it, and nothing in the build would notice.
	 * Declaring the intent now makes the parity survive that change instead of depending on it.
	 *
	 * <p>{@code descriptionId} points at the block's existing key rather than letting NeoForge derive
	 * {@code fluid_type.alaindustrial.oil}: that derived key has no translation, so any foreign GUI
	 * naming the fluid type (JEI/EMI fluid entries, other mods' tanks) would print the raw key. The
	 * block key is already translated in all 20 locales and reads exactly the same to the player.
	 */
	public static FluidType.Properties oilTypeProperties() {
		return FluidType.Properties.create()
				.descriptionId("block.alaindustrial.oil")
				.density(900)
				.viscosity(3000)
				.canConvertToSource(false)
				.canSwim(false)
				.canDrown(false)
				.canPushEntity(false)
				.motionScale(0.0D)
				.fallDistanceModifier(1.0F)
				.supportsBoating(false);
	}

	public static final DeferredHolder<Fluid, FlowingFluid> OIL = FLUIDS.register("oil", Still::new);
	public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_OIL =
			FLUIDS.register("flowing_oil", Flowing::new);

	// --- Distillation fractions (MOD-251) ---

	/**
	 * Diesel's {@link FluidType}: light and thin next to crude (density/viscosity between water and
	 * oil). Entity-physics fields are pinned explicitly for the same reason as
	 * {@link #oilTypeProperties()} — the fractions behave like water-that-does-nothing on both
	 * loaders, and must keep doing so if NeoForge re-enables its fluid-type entity integration.
	 * {@code descriptionId} points at the block key (translated in all 20 locales), not the derived
	 * {@code fluid_type.*} key which has no translation.
	 */
	public static final DeferredHolder<FluidType, FluidType> DIESEL_TYPE =
			FLUID_TYPES.register("diesel", () -> new FluidType(distillateTypeProperties(
					"block.alaindustrial.diesel", 850, 1200)));

	/** Fuel oil's {@link FluidType}: the heavy residue — denser and thicker than diesel. */
	public static final DeferredHolder<FluidType, FluidType> FUEL_OIL_TYPE =
			FLUID_TYPES.register("fuel_oil", () -> new FluidType(distillateTypeProperties(
					"block.alaindustrial.fuel_oil", 950, 2400)));

	/** Shared water-like-but-inert property chain for the two fractions (see {@link #DIESEL_TYPE}). */
	public static FluidType.Properties distillateTypeProperties(String descriptionId, int density,
			int viscosity) {
		return FluidType.Properties.create()
				.descriptionId(descriptionId)
				.density(density)
				.viscosity(viscosity)
				.canConvertToSource(false)
				.canSwim(false)
				.canDrown(false)
				.canPushEntity(false)
				.motionScale(0.0D)
				.fallDistanceModifier(1.0F)
				.supportsBoating(false);
	}

	public static final DeferredHolder<Fluid, FlowingFluid> DIESEL =
			FLUIDS.register("diesel", DieselStill::new);
	public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_DIESEL =
			FLUIDS.register("flowing_diesel", DieselFlowing::new);
	public static final DeferredHolder<Fluid, FlowingFluid> FUEL_OIL =
			FLUIDS.register("fuel_oil", FuelOilStill::new);
	public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_FUEL_OIL =
			FLUIDS.register("flowing_fuel_oil", FuelOilFlowing::new);

	private ModFluidsNeoForge() {
	}

	/** The still oil with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class Still extends OilFluid.Source {
		@Override
		public FluidType getFluidType() {
			return OIL_TYPE.get();
		}
	}

	/** The flowing oil with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class Flowing extends OilFluid.Flowing {
		@Override
		public FluidType getFluidType() {
			return OIL_TYPE.get();
		}
	}

	/** Still diesel with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class DieselStill extends DieselFluid.Source {
		@Override
		public FluidType getFluidType() {
			return DIESEL_TYPE.get();
		}
	}

	/** Flowing diesel with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class DieselFlowing extends DieselFluid.Flowing {
		@Override
		public FluidType getFluidType() {
			return DIESEL_TYPE.get();
		}
	}

	/** Still fuel oil with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class FuelOilStill extends FuelOilFluid.Source {
		@Override
		public FluidType getFluidType() {
			return FUEL_OIL_TYPE.get();
		}
	}

	/** Flowing fuel oil with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class FuelOilFlowing extends FuelOilFluid.Flowing {
		@Override
		public FluidType getFluidType() {
			return FUEL_OIL_TYPE.get();
		}
	}

	/**
	 * Binds the fluid {@code DeferredHolder}s into the loader-neutral {@link ModContent} facade,
	 * mirroring the Fabric {@code ModFluids.init()}. Called from the {@code @Mod} constructor after
	 * {@code FLUIDS.register(modBus)}; the holders resolve lazily after the FLUID RegisterEvent.
	 */
	public static void init() {
		ModContent.OIL = OIL::get;
		ModContent.FLOWING_OIL = FLOWING_OIL::get;
		ModContent.DIESEL = DIESEL::get;
		ModContent.FLOWING_DIESEL = FLOWING_DIESEL::get;
		ModContent.FUEL_OIL = FUEL_OIL::get;
		ModContent.FLOWING_FUEL_OIL = FLOWING_FUEL_OIL::get;
	}
}
