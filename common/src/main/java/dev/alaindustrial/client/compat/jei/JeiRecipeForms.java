package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.client.compat.CanningExchange;
import dev.alaindustrial.client.compat.RecipeViewerForm;
import dev.alaindustrial.recipe.AlloyingRecipe;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;

/**
 * The JEI adapter of the special recipe forms (MOD-716, CLI-6): for each {@link RecipeViewerForm}, the JEI
 * recipe type, the category that draws it and the recipes it lists — and JEI's own tab order.
 */
final class JeiRecipeForms {

	/**
	 * JEI's tab order of the special forms, as shipped. REI orders them differently (distillation before
	 * alloying, the evolution pages before the machine pages), and each viewer keeps its own (owner
	 * decision 2026-10-04) — so the order lives here, not on the shared list.
	 */
	static final List<RecipeViewerForm> TAB_ORDER = List.of(
			RecipeViewerForm.POLYMERIZING,
			RecipeViewerForm.ALLOYING,
			RecipeViewerForm.DISTILLING,
			RecipeViewerForm.CANNING,
			RecipeViewerForm.MACHINE_INFO,
			RecipeViewerForm.EVOLUTION_INFO,
			RecipeViewerForm.PLANT_INFO);

	private JeiRecipeForms() {
	}

	/** How JEI shows one form: its type, the category for it, and the recipes the category lists. */
	record Binding<T>(IRecipeType<T> type, Function<IGuiHelper, IRecipeCategory<T>> category,
			Function<Collection<RecipeHolder<?>>, List<T>> recipes) {

		/** Adds the form's recipes; returns how many, for the summary line. */
		int addRecipes(IRecipeRegistration registration, Collection<RecipeHolder<?>> synced) {
			List<T> list = recipes.apply(synced);
			registration.addRecipes(type, list);
			return list.size();
		}
	}

	static Binding<?> of(RecipeViewerForm form) {
		return switch (form) {
			case POLYMERIZING -> new Binding<>(AlaJeiRecipeTypes.POLYMERIZING,
					gui -> new PolymerizingJeiCategory(AlaJeiRecipeTypes.POLYMERIZING, form.icon(), form.title(), gui),
					synced -> holders(synced, PolymerizingRecipe.class));
			case ALLOYING -> new Binding<>(AlaJeiRecipeTypes.ALLOYING,
					gui -> new AlloyingJeiCategory(AlaJeiRecipeTypes.ALLOYING, form.icon(), form.title(), gui),
					synced -> holders(synced, AlloyingRecipe.class));
			case DISTILLING -> new Binding<>(AlaJeiRecipeTypes.DISTILLING,
					gui -> new FluidOutputJeiCategory(AlaJeiRecipeTypes.DISTILLING, form.icon(), form.title(), gui),
					synced -> holders(synced, FluidOutputRecipe.class));
			// One card per accepted food, derived from the (by now frozen) item registry — no recipes to collect.
			case CANNING -> new Binding<>(AlaJeiRecipeTypes.CANNING,
					gui -> new CanningJeiCategory(AlaJeiRecipeTypes.CANNING, form.icon(), form.title(), gui),
					synced -> CanningExchange.cards());
			// The page forms: own categories rather than addIngredientInfo, whose text is frozen at
			// registration, before the server's balance arrives (MOD-695); a click area must also open
			// something of ours, not JEI's shared info tab (MOD-420).
			case MACHINE_INFO, EVOLUTION_INFO, PLANT_INFO -> new Binding<>(AlaJeiRecipeTypes.pages(form),
					gui -> new MachineInfoJeiCategory(AlaJeiRecipeTypes.pages(form), form.icon(), form.title(), gui,
							form::pages, form.id() + "/"),
					synced -> form.pages());
		};
	}

	/**
	 * The machines JEI lists as the form's crafting stations. The evolution tab has none in JEI, although
	 * REI lists the two T2 solar panels for it — the difference shipped and is kept (a change would add
	 * catalysts a JEI player never saw).
	 */
	static List<ItemLike> stations(RecipeViewerForm form) {
		return form == RecipeViewerForm.EVOLUTION_INFO ? List.of() : form.stations();
	}

	/** Every synced recipe of one class, typed — the recipe families each hold a recipe class of their own. */
	private static <R extends Recipe<?>> List<RecipeHolder<R>> holders(Collection<RecipeHolder<?>> synced,
			Class<R> recipeClass) {
		List<RecipeHolder<R>> result = new ArrayList<>();
		for (RecipeHolder<?> holder : synced) {
			if (recipeClass.isInstance(holder.value())) {
				@SuppressWarnings("unchecked")
				RecipeHolder<R> typed = (RecipeHolder<R>) holder;
				result.add(typed);
			}
		}
		return result;
	}
}
