package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.fluid.DieselFluid;
import dev.alaindustrial.fluid.BiofuelFluid;
import dev.alaindustrial.fluid.FuelOilFluid;
import dev.alaindustrial.fluid.NutrientSolutionFluid;
import dev.alaindustrial.fluid.OilFluid;
import dev.alaindustrial.fluid.SteamFluid;
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
	 * {@code motionScale=0.014}, {@code fallDistanceModifier=0.5F} — i.e. "behaves like water".
	 *
	 * <p><b>That day has come (MOD-495).</b> When this was written on 26.2.0.8-beta the whole
	 * {@code IEntityExtension} fluid-type integration was commented out upstream, so none of the
	 * defaults did anything and NeoForge matched Fabric — where custom fluids have no entity physics
	 * at all — BY ACCIDENT (decision 6 in the task: both loaders "not water", the player falls
	 * straight through). NeoForge re-implemented the patches in 26.2.0.49-beta, and on 26.2.0.67 the
	 * call sites are live: {@code Entity#getFallDistanceModifier}, {@code isInFluidMatching} /
	 * {@code canSwimInFluidType} and {@code LivingEntity#getFluidTypeHeight} all run again. Had these
	 * fields been left implicit, NeoForge players would now swim and drown in oil while Fabric players
	 * fall through it, and nothing in the build would have noticed. They are explicit, so the parity
	 * survived the change — which is exactly what this block was written for. The guard that proves
	 * it is {@code NeoForgeOilWorldGenTest#oilFluidTypeDeclaresItsEntityPhysics}.
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

	/**
	 * Biofuel's {@link FluidType} (MOD-146): thinner than either oil fraction — it is brewed and
	 * watery, not refined out of crude.
	 */
	public static final DeferredHolder<FluidType, FluidType> BIOFUEL_TYPE =
			FLUID_TYPES.register("biofuel", () -> new FluidType(distillateTypeProperties(
					"block.alaindustrial.biofuel", 900, 1000)));

	/** Nutrient solution's {@link FluidType} (MOD-525): the thinnest thing the mod makes. */
	public static final DeferredHolder<FluidType, FluidType> NUTRIENT_SOLUTION_TYPE =
			FLUID_TYPES.register("nutrient_solution", () -> new FluidType(distillateTypeProperties(
					"block.alaindustrial.nutrient_solution", 1000, 900)));

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
	public static final DeferredHolder<Fluid, FlowingFluid> BIOFUEL =
			FLUIDS.register("biofuel", BiofuelStill::new);
	public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_BIOFUEL =
			FLUIDS.register("flowing_biofuel", BiofuelFlowing::new);
	public static final DeferredHolder<Fluid, FlowingFluid> NUTRIENT_SOLUTION =
			FLUIDS.register("nutrient_solution", NutrientSolutionStill::new);
	public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_NUTRIENT_SOLUTION =
			FLUIDS.register("flowing_nutrient_solution", NutrientSolutionFlowing::new);

	// --- Steam (MOD-468) ---

	/**
	 * Steam's {@link FluidType}. Steam is a gas that exists only inside tanks and pipes, so every
	 * number here is metadata another mod's GUI may print, never physics the player meets: density 1
	 * and temperature 373 K state "water vapour at the boil", and the entity-physics fields are pinned
	 * inert for the same reason as {@link #oilTypeProperties()} — Fabric gives a custom fluid no entity
	 * physics at all, and the parity must survive NeoForge re-enabling its {@code IEntityExtension}
	 * integration.
	 *
	 * <p>{@code descriptionId} is {@code fluid.alaindustrial.steam} and not a block key like the other
	 * three: steam has no block to borrow a name from. That key is the same one
	 * {@code FluidDisplayNames} falls back to, and it is present in every lang file the mod ships.
	 */
	public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE =
			FLUID_TYPES.register("steam", () -> new FluidType(steamTypeProperties()));

	/**
	 * The steam {@link FluidType.Properties}, split out like the other two so they can be asserted
	 * without booting the registries (the pattern {@code NeoForgeOilWorldGenTest} uses for oil).
	 */
	public static FluidType.Properties steamTypeProperties() {
		return FluidType.Properties.create()
				.descriptionId("fluid.alaindustrial.steam")
				.density(1)
				.temperature(373)
				.viscosity(200)
				.canConvertToSource(false)
				.canSwim(false)
				.canDrown(false)
				.canPushEntity(false)
				.motionScale(0.0D)
				.fallDistanceModifier(1.0F)
				.supportsBoating(false);
	}

	/** One entry, not a still/flowing pair — steam has no flowing form (see {@code SteamFluid}). */
	public static final DeferredHolder<Fluid, Fluid> STEAM = FLUIDS.register("steam", Steam::new);

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

	/** Still biofuel with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class BiofuelStill extends BiofuelFluid.Source {
		@Override
		public FluidType getFluidType() {
			return BIOFUEL_TYPE.get();
		}
	}

	/** Flowing biofuel with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class BiofuelFlowing extends BiofuelFluid.Flowing {
		@Override
		public FluidType getFluidType() {
			return BIOFUEL_TYPE.get();
		}
	}

	/** Still nutrient solution with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class NutrientSolutionStill extends NutrientSolutionFluid.Source {
		@Override
		public FluidType getFluidType() {
			return NUTRIENT_SOLUTION_TYPE.get();
		}
	}

	/** Flowing nutrient solution with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class NutrientSolutionFlowing extends NutrientSolutionFluid.Flowing {
		@Override
		public FluidType getFluidType() {
			return NUTRIENT_SOLUTION_TYPE.get();
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

	/** Steam with NeoForge's mandatory {@code getFluidType()} attached. */
	public static final class Steam extends SteamFluid {
		@Override
		public FluidType getFluidType() {
			return STEAM_TYPE.get();
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
		ModContent.BIOFUEL = BIOFUEL::get;
		ModContent.FLOWING_BIOFUEL = FLOWING_BIOFUEL::get;
		ModContent.NUTRIENT_SOLUTION = NUTRIENT_SOLUTION::get;
		ModContent.FLOWING_NUTRIENT_SOLUTION = FLOWING_NUTRIENT_SOLUTION::get;
		ModContent.STEAM = STEAM::get;
	}
}
