package dev.alaindustrial.registry;

import dev.alaindustrial.fluid.BiofuelFluid;
import dev.alaindustrial.fluid.DieselFluid;
import dev.alaindustrial.fluid.FuelOilFluid;
import dev.alaindustrial.fluid.NutrientSolutionFluid;
import dev.alaindustrial.fluid.OilFluid;
import dev.alaindustrial.fluid.SteamFluid;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * Every fluid the mod registers, declared once (MOD-708) and replayed by both loaders: Fabric registers
 * the entries eagerly in {@code ModFluids}, NeoForge queues them, with one {@code FluidType} each, in
 * {@code ModFluidsNeoForge}; both clients derive their fluid models from this list
 * ({@code ClientContentManifest.FLUID_MODELS}).
 *
 * <p>A new fluid is one {@link #pair} or {@link #single} entry here plus its {@code Fluid} class and its
 * assets; neither loader's source changes. The entry carries the ids, the factories, the
 * {@link FluidPhysics} NeoForge states on the fluid type, the type's description key, and the
 * {@link ModContent} slots each loader binds.
 *
 * <p><b>Order is registration order</b> on both loaders. The factories construct the common fluid classes
 * only when a loader registers them, never during this class's initialisation.
 */
public final class ModFluidsManifest {
	private ModFluidsManifest() {
	}

	/**
	 * The numbers a loader may state about a fluid: density, viscosity and temperature (kelvin) — what a
	 * NeoForge {@code FluidType} reports to other mods. Physics the player meets (damping, spread) lives in
	 * the fluid classes themselves.
	 */
	public record FluidPhysics(int density, int viscosity, int temperature) {
	}

	/**
	 * One fluid: a still (or only) form and, for a fluid that exists in the world, a flowing form.
	 *
	 * @param id                 the still fluid's registry path, also its fluid type's and model texture's
	 * @param source             builds the still (or only) fluid
	 * @param flowingId          the flowing form's registry path, or {@code null} for a fluid without one
	 * @param flowing            builds the flowing form, or {@code null} with {@code flowingId}
	 * @param physics            what the NeoForge fluid type states
	 * @param descriptionId      the translation key the NeoForge fluid type names the fluid by
	 * @param transferAttributes whether Fabric registers a Transfer API attribute handler reporting
	 *                           {@code physics.viscosity()}; only crude oil has one (a known defect: the
	 *                           others report the Transfer API defaults on Fabric)
	 * @param bindSource         publishes the registered still fluid into its {@link ModContent} slot
	 * @param bindFlowing        publishes the flowing form, or {@code null} with {@code flowingId}
	 * @param <T>                the handle type of the {@link ModContent} slots
	 */
	public record FluidDef<T extends Fluid>(String id, Supplier<? extends T> source, @Nullable String flowingId,
			@Nullable Supplier<? extends T> flowing, FluidPhysics physics, String descriptionId,
			boolean transferAttributes, Consumer<Supplier<T>> bindSource, @Nullable Consumer<Supplier<T>> bindFlowing) {
		public FluidDef {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(physics, "physics");
			Objects.requireNonNull(descriptionId, "descriptionId");
			Objects.requireNonNull(bindSource, "bindSource");
			if ((flowingId == null) != (flowing == null) || (flowingId == null) != (bindFlowing == null)) {
				throw new IllegalArgumentException("fluid " + id + ": flowing id, factory and slot go together");
			}
		}

		/** Whether the fluid has a flowing form (and so a liquid block and a bucket). */
		public boolean hasFlowing() {
			return flowingId != null;
		}

		/** The same entry with a Fabric Transfer API attribute handler. */
		FluidDef<T> withTransferAttributes() {
			return new FluidDef<>(id, source, flowingId, flowing, physics, descriptionId, true, bindSource,
					bindFlowing);
		}
	}

	/** A world fluid: still and flowing forms, named {@code id} and {@code flowing_<id>}. */
	private static FluidDef<FlowingFluid> pair(String id, Supplier<? extends FlowingFluid> source,
			Supplier<? extends FlowingFluid> flowing, FluidPhysics physics, Consumer<Supplier<FlowingFluid>> bindSource,
			Consumer<Supplier<FlowingFluid>> bindFlowing) {
		return new FluidDef<>(id, source, "flowing_" + id, flowing, physics, "block.alaindustrial." + id, false,
				bindSource, bindFlowing);
	}

	/** A fluid without a world form: one entry, no flowing fluid, no block, no bucket. */
	private static FluidDef<Fluid> single(String id, Supplier<? extends Fluid> source, FluidPhysics physics,
			String descriptionId, Consumer<Supplier<Fluid>> bind) {
		return new FluidDef<>(id, source, null, null, physics, descriptionId, false, bind, null);
	}

	/** Every fluid, in registration order. */
	public static final List<FluidDef<?>> FLUIDS = List.of(
			// MOD-238: crude oil — heavy-ish and thick. The one fluid with a Fabric attribute handler.
			pair("oil", OilFluid.Source::new, OilFluid.Flowing::new, new FluidPhysics(900, 3000, 300),
					s -> ModContent.OIL = s, s -> ModContent.FLOWING_OIL = s).withTransferAttributes(),
			// MOD-251: the distillation fractions — diesel light and thin, fuel oil the heavy residue.
			pair("diesel", DieselFluid.Source::new, DieselFluid.Flowing::new, new FluidPhysics(850, 1200, 300),
					s -> ModContent.DIESEL = s, s -> ModContent.FLOWING_DIESEL = s),
			pair("fuel_oil", FuelOilFluid.Source::new, FuelOilFluid.Flowing::new, new FluidPhysics(950, 2400, 300),
					s -> ModContent.FUEL_OIL = s, s -> ModContent.FLOWING_FUEL_OIL = s),
			// MOD-146/MOD-525: the organic chain — brewed biofuel, and the thinnest thing the mod makes.
			pair("biofuel", BiofuelFluid.Source::new, BiofuelFluid.Flowing::new, new FluidPhysics(900, 1000, 300),
					s -> ModContent.BIOFUEL = s, s -> ModContent.FLOWING_BIOFUEL = s),
			pair("nutrient_solution", NutrientSolutionFluid.Source::new, NutrientSolutionFluid.Flowing::new,
					new FluidPhysics(1000, 900, 300), s -> ModContent.NUTRIENT_SOLUTION = s,
					s -> ModContent.FLOWING_NUTRIENT_SOLUTION = s),
			// MOD-468: steam lives only in tanks and pipes — "water vapour at the boil". It has no block to
			// borrow a name from, so its type uses the fluid key every lang file carries.
			single("steam", SteamFluid::new, new FluidPhysics(1, 200, 373), "fluid.alaindustrial.steam",
					s -> ModContent.STEAM = s));

	/** The entry with the still id {@code id}; throws for an unknown id. */
	public static FluidDef<?> byId(String id) {
		for (FluidDef<?> def : FLUIDS) {
			if (def.id().equals(id)) {
				return def;
			}
		}
		throw new IllegalArgumentException("no fluid " + id + " in ModFluidsManifest.FLUIDS");
	}
}
