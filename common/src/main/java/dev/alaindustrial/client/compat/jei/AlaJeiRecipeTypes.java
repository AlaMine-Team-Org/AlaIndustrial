package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.CanningExchange;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.AlloyRecipeInput;
import dev.alaindustrial.recipe.AlloyingRecipe;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import dev.alaindustrial.recipe.FluidRecipeInput;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.item.crafting.Recipe;

/**
 * The viewer type JEI files each recipe family's cards under.
 *
 * <p><b>Built by replaying the family lists, not by a lookup ladder (MOD-558).</b> {@link #byKind}
 * used to be a chain of {@code if (kind == ModRecipes.X) return X;} ending in a {@code throw}, kept by
 * hand in each loader's copy of this class. The fermenter (MOD-146) arrived after that chain was
 * written and nobody added its branch — and because the plugin resolved every type from a STATIC
 * field, the throw escaped the class initialiser: JEI reported "Failed to load: AlaJeiPlugin" and the
 * player got no Ala Industrial recipe card at all, for any machine, for a whole release cycle. A
 * missing line in a lookup table cost the entire integration.
 *
 * <p>The maps below are filled from {@link ModRecipes#kinds()}, {@link ModRecipes#fluidKinds()} and
 * {@link ModRecipes#alloyKinds()} — the very lists both loaders register the families from. A family
 * that exists therefore has a viewer type, with no second list to keep in step and no branch to
 * forget; the {@code throw} that made a forgotten one fatal is gone with the ladder that needed it.
 */
final class AlaJeiRecipeTypes {

	/** Viewer types of the item-processing families, keyed by the family they belong to. */
	private static final Map<ModRecipes.Kind, IRecipeHolderType<AlaProcessingRecipe>> BY_KIND = kindTypes();

	/** The same for the fluid-fed families (MOD-019, MOD-251) and for alloying (MOD-064). */
	private static final Map<ModRecipes.FluidKind<?>, IRecipeHolderType<?>> BY_FLUID_KIND = fluidKindTypes();
	private static final Map<ModRecipes.AlloyKind<?>, IRecipeHolderType<?>> BY_ALLOY_KIND = alloyKindTypes();

	/** The Polymerizer's fluid → item family (MOD-019) — a different recipe class, so a separate type. */
	static final IRecipeHolderType<PolymerizingRecipe> POLYMERIZING = fluidType(ModRecipes.POLYMERIZING);
	/** The distillation column's fluid → two-fluids family (MOD-251). */
	static final IRecipeHolderType<FluidOutputRecipe> DISTILLING = fluidType(ModRecipes.DISTILLING);
	/** The alloy smelter's multi-component family (MOD-064). */
	static final IRecipeHolderType<AlloyingRecipe> ALLOYING = alloyType(ModRecipes.ALLOYING);

	/**
	 * The Canning Machine (MOD-383). The one viewer type here that is NOT an {@link IRecipeHolderType}:
	 * the machine matches no JSON recipe, so there is no {@code Recipe} class to hold — and no
	 * {@link ModRecipes.Kind} either, which is why it is written out rather than replayed. Its cards are
	 * {@link CanningExchange.Card} records derived from the item registry, and
	 * {@link IRecipeType#create(net.minecraft.resources.Identifier, Class)} accepts any POJO.
	 */
	static final IRecipeType<CanningExchange.Card> CANNING =
			IRecipeType.create(Industrialization.id("canning"), CanningExchange.Card.class);

	/**
	 * Machines with no recipe at all (MOD-420) — the geothermal generator and the energy condenser.
	 * Like {@link #CANNING} this is not an {@link IRecipeHolderType} and has no family to be replayed
	 * from: the "recipes" are the shared loader-neutral {@link RecipeViewerInfo.Entry} records, not
	 * anything the recipe manager holds.
	 */
	static final IRecipeType<RecipeViewerInfo.Entry> MACHINE_INFO =
			IRecipeType.create(Industrialization.id("machine_info"), RecipeViewerInfo.Entry.class);

	private AlaJeiRecipeTypes() {
	}

	/**
	 * The viewer type of one item-processing family. Never {@code null}: the keys ARE
	 * {@link ModRecipes#kinds()}, and a {@link ModRecipes.Kind} outside that list is not registered as a
	 * recipe family either, so it could not carry a recipe to show.
	 */
	static IRecipeHolderType<AlaProcessingRecipe> byKind(ModRecipes.Kind kind) {
		return BY_KIND.get(kind);
	}

	/** The fluid-fed families' viewer types by kind (MOD-251) — the FluidKind twin of {@link #byKind}. */
	static IRecipeHolderType<?> byFluidKind(ModRecipes.FluidKind<?> kind) {
		return BY_FLUID_KIND.get(kind);
	}

	private static Map<ModRecipes.Kind, IRecipeHolderType<AlaProcessingRecipe>> kindTypes() {
		Map<ModRecipes.Kind, IRecipeHolderType<AlaProcessingRecipe>> types = new LinkedHashMap<>();
		for (ModRecipes.Kind kind : ModRecipes.kinds()) {
			types.put(kind, IRecipeHolderType.create(Industrialization.id(kind.id())));
		}
		return Collections.unmodifiableMap(types);
	}

	private static Map<ModRecipes.FluidKind<?>, IRecipeHolderType<?>> fluidKindTypes() {
		Map<ModRecipes.FluidKind<?>, IRecipeHolderType<?>> types = new LinkedHashMap<>();
		for (ModRecipes.FluidKind<?> kind : ModRecipes.fluidKinds()) {
			types.put(kind, newFluidType(kind));
		}
		return Collections.unmodifiableMap(types);
	}

	private static Map<ModRecipes.AlloyKind<?>, IRecipeHolderType<?>> alloyKindTypes() {
		Map<ModRecipes.AlloyKind<?>, IRecipeHolderType<?>> types = new LinkedHashMap<>();
		for (ModRecipes.AlloyKind<?> kind : ModRecipes.alloyKinds()) {
			types.put(kind, newAlloyType(kind));
		}
		return Collections.unmodifiableMap(types);
	}

	private static <R extends Recipe<FluidRecipeInput>> IRecipeHolderType<R> newFluidType(ModRecipes.FluidKind<R> kind) {
		return IRecipeHolderType.create(Industrialization.id(kind.id()));
	}

	private static <R extends Recipe<AlloyRecipeInput>> IRecipeHolderType<R> newAlloyType(ModRecipes.AlloyKind<R> kind) {
		return IRecipeHolderType.create(Industrialization.id(kind.id()));
	}

	// The two casts below re-narrow what the map lost: `newFluidType(kind)` produced an
	// IRecipeHolderType<R> for exactly this kind's recipe class, and the map widened it to `<?>` only
	// because it holds every family at once. The alternative — a second, typed field per family — is
	// the hand-kept list this class was rewritten to remove.

	@SuppressWarnings("unchecked")
	private static <R extends Recipe<FluidRecipeInput>> IRecipeHolderType<R> fluidType(ModRecipes.FluidKind<R> kind) {
		return (IRecipeHolderType<R>) BY_FLUID_KIND.get(kind);
	}

	@SuppressWarnings("unchecked")
	private static <R extends Recipe<AlloyRecipeInput>> IRecipeHolderType<R> alloyType(ModRecipes.AlloyKind<R> kind) {
		return (IRecipeHolderType<R>) BY_ALLOY_KIND.get(kind);
	}
}
