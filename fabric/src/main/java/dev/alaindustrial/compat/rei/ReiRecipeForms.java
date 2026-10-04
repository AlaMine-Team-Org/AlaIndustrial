package dev.alaindustrial.compat.rei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.CanningExchange;
import dev.alaindustrial.client.compat.RecipeViewerForm;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;

/**
 * The REI adapter of the special recipe forms (MOD-716, CLI-6): for each {@link RecipeViewerForm}, the REI
 * category id, the category that draws it and the client-side displays it holds — and REI's own tab order.
 *
 * <p>The three recipe families (polymerizing, alloying, distilling) get their displays from the server
 * ({@link AlaReiCommonPlugin}); canning cards and the page forms are client data, added here.
 */
final class ReiRecipeForms {

	/**
	 * REI's tab order of the special forms, as shipped: distillation before alloying, the evolution pages
	 * before the machine pages — unlike JEI's. Each viewer keeps its own (owner decision 2026-10-04), so the
	 * order lives here, not on the shared list.
	 */
	static final List<RecipeViewerForm> TAB_ORDER = List.of(
			RecipeViewerForm.POLYMERIZING,
			RecipeViewerForm.DISTILLING,
			RecipeViewerForm.ALLOYING,
			RecipeViewerForm.CANNING,
			RecipeViewerForm.EVOLUTION_INFO,
			RecipeViewerForm.MACHINE_INFO,
			RecipeViewerForm.PLANT_INFO);

	private ReiRecipeForms() {
	}

	/** The form's REI category id — {@code alaindustrial:<form id>}, the constant of its display type. */
	static CategoryIdentifier<?> categoryId(RecipeViewerForm form) {
		return switch (form) {
			case POLYMERIZING -> PolymerizingDisplay.CATEGORY;
			case ALLOYING -> AlloyingDisplay.CATEGORY;
			case DISTILLING -> CategoryIdentifier.of(Industrialization.id(form.id()));
			case CANNING -> CanningDisplay.CATEGORY;
			case MACHINE_INFO -> AlaInfoDisplay.MACHINE_CATEGORY;
			case EVOLUTION_INFO -> AlaInfoDisplay.CATEGORY;
			case PLANT_INFO -> AlaInfoDisplay.PLANT_CATEGORY;
		};
	}

	/** The category that draws the form — the card and page layouts are the shared ones of client/compat. */
	@SuppressWarnings("unchecked")
	static DisplayCategory<?> category(RecipeViewerForm form) {
		return switch (form) {
			case POLYMERIZING -> new PolymerizingCategory(form.icon(), form.title());
			case ALLOYING -> new AlloyingCategory(form.icon(), form.title());
			case DISTILLING -> new FluidOutputCategory(
					(CategoryIdentifier<FluidOutputDisplay>) categoryId(form), form.icon(), form.title());
			case CANNING -> new CanningCategory(form.icon(), form.title());
			case MACHINE_INFO, EVOLUTION_INFO, PLANT_INFO -> new AlaInfoCategory(
					(CategoryIdentifier<AlaInfoDisplay>) categoryId(form), form::title, form.icon(), sizingPages(form));
		};
	}

	/**
	 * The pages a page tab sizes itself against (MOD-422). The evolution tab measures the solar branches and
	 * the rarity grades only — not the world-made items it also shows — as it shipped; JEI measures all
	 * three. Kept, because the height of the tab is what the player sees.
	 */
	private static List<RecipeViewerInfo.Entry> sizingPages(RecipeViewerForm form) {
		if (form != RecipeViewerForm.EVOLUTION_INFO) {
			return form.pages();
		}
		List<RecipeViewerInfo.Entry> pages = new ArrayList<>(RecipeViewerInfo.solarEvolutionEntries());
		pages.addAll(RecipeViewerInfo.mutationGradeEntries());
		return pages;
	}

	/** Adds the form's client-side displays; the recipe families arrive from the server instead. */
	@SuppressWarnings("unchecked")
	static void addDisplays(RecipeViewerForm form, DisplayRegistry registry) {
		switch (form) {
			case POLYMERIZING, ALLOYING, DISTILLING -> {
				// Filled server-side by AlaReiCommonPlugin and synced.
			}
			// One canning card per accepted food. The sweep over the (by now frozen) item registry happens on
			// the first call, here.
			case CANNING -> {
				for (CanningExchange.Card card : CanningExchange.cards()) {
					registry.add(new CanningDisplay(card));
				}
			}
			// Pure client-side data (block/item refs + balance values), so added directly rather than synced.
			case MACHINE_INFO, EVOLUTION_INFO, PLANT_INFO -> {
				CategoryIdentifier<AlaInfoDisplay> id = (CategoryIdentifier<AlaInfoDisplay>) categoryId(form);
				for (RecipeViewerInfo.Entry entry : form.pages()) {
					registry.add(new AlaInfoDisplay(entry, id));
				}
			}
		}
	}
}
