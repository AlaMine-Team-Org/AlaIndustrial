package dev.alaindustrial.client.compat;

import dev.alaindustrial.client.screen.GuiRect;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

/**
 * The special recipe forms — every recipe-viewer tab that is not one processing card per
 * {@link ModRecipes.Kind} (MOD-716, CLI-6). Declared once here; the JEI plugin and the REI plugin replay
 * this list instead of each writing every form out by hand.
 *
 * <p>A form says what both viewers show the same way: the tab's id, title and icon, the machines a player
 * clicks to open it, the GUI areas that open it, and — for a page form — its pages. How a viewer DRAWS the
 * form is its own thin adapter ({@code JeiRecipeForms}, {@code ReiRecipeForms}) over the neutral layouts
 * of this package ({@link RecipeViewerLayout}, {@link FluidOutputViewerModel}, {@link CanningExchange},
 * {@link RecipeViewerInfo}).
 *
 * <p><b>The declaration order here is NOT a tab order.</b> Each viewer keeps the tab order it shipped with
 * (owner decision 2026-10-04): JEI and REI file distillation and the two page forms differently, and
 * unifying them would move a tab a player knows. The order lives next to each adapter, and a test holds it
 * to be a permutation of {@link #values()}, so a new form cannot be shown by one viewer and forgotten by
 * the other.
 *
 * <p>Adding a form: a constant here, a case in each adapter's {@code switch} (the compiler names the one
 * left out), and the form's place in each viewer's tab order.
 */
public enum RecipeViewerForm {
	/** The Polymerizer's fluid → item family (MOD-019). */
	POLYMERIZING(ModRecipes.POLYMERIZING.id(), true, blockName(ModRecipes.POLYMERIZING.station()),
			ModRecipes.POLYMERIZING.station(), List.of(ModRecipes.POLYMERIZING.station())),
	/** The alloy smelter's multi-component family (MOD-064). */
	ALLOYING(ModRecipes.ALLOYING.id(), true, blockName(ModRecipes.ALLOYING.station()),
			ModRecipes.ALLOYING.station(), List.of(ModRecipes.ALLOYING.station())),
	/** The distillation column's fluid → two-fluids family (MOD-251). */
	DISTILLING(ModRecipes.DISTILLING.id(), true, blockName(ModRecipes.DISTILLING.station()),
			ModRecipes.DISTILLING.station(), List.of(ModRecipes.DISTILLING.station())),
	/**
	 * The canning machine (MOD-383): no recipe type at all — the cards come from the item registry
	 * ({@link CanningExchange}), so the title is its own lang key rather than a block name.
	 */
	CANNING("canning", false, RecipeCategoryTitle::canning,
			() -> ModContent.CANNING_MACHINE.get(), List.of(() -> ModContent.CANNING_MACHINE.get())),
	/**
	 * Machines with no recipe of any kind (MOD-420): the geothermal generator and the energy condenser.
	 * Both are worked at the machine they describe, so clicking either block opens the tab.
	 */
	MACHINE_INFO("machine_info", false, key("jei.alaindustrial.category.machine_info"),
			() -> ModContent.GEOTHERMAL_GENERATOR.get(),
			List.of(() -> ModContent.GEOTHERMAL_GENERATOR.get(), () -> ModContent.ENERGY_CONDENSER.get())),
	/**
	 * Evolution pages (MOD-043, MOD-118, MOD-600/638): solar branches, rarity grades, world-made items. The
	 * two T2 solar panels are its stations; the base panel is craftable, so it is not one.
	 */
	EVOLUTION_INFO("evolution_info", false, key("jei.alaindustrial.category.evolution"),
			() -> ModContent.ALIGNMENT_CHIP_DAY.get(),
			List.of(() -> ModContent.DAYLIGHT_SOLAR_PANEL.get(), () -> ModContent.MOONLIT_SOLAR_PANEL.get())),
	/**
	 * The kok sagyz plant (MOD-584). No station — there is no screen to click; the pages are reached with
	 * the recipe key on the seeds or the root.
	 */
	PLANT_INFO("plant_info", false, key("jei.alaindustrial.category.plant_info"),
			() -> ModContent.KOK_SAGYZ_SEEDS.get(), List.of());

	/** A GUI area that opens this form's tab, on one screen class. */
	public record ClickArea(Class<? extends AbstractContainerScreen<?>> screenClass, GuiRect rect) {
	}

	private final String id;
	private final boolean recipeFamily;
	private final Supplier<Component> title;
	private final Supplier<? extends ItemLike> icon;
	private final List<Supplier<? extends ItemLike>> stations;

	RecipeViewerForm(String id, boolean recipeFamily, Supplier<Component> title, Supplier<? extends ItemLike> icon,
			List<Supplier<? extends ItemLike>> stations) {
		this.id = id;
		this.recipeFamily = recipeFamily;
		this.title = title;
		this.icon = icon;
		this.stations = stations;
	}

	/** The tab's id path under the mod namespace — the same in both viewers. */
	public String id() {
		return id;
	}

	/**
	 * Whether the form is a recipe family the recipe manager holds (polymerizing, alloying, distilling), as
	 * opposed to cards or pages computed on the client. JEI's summary line counts exactly these.
	 */
	public boolean recipeFamily() {
		return recipeFamily;
	}

	/** The tab's title; built anew on each call, as the plugins built it. */
	public Component title() {
		return title.get();
	}

	/** The item the tab shows as its icon. */
	public ItemLike icon() {
		return icon.get();
	}

	/** The machines a player clicks in a viewer to open the tab, in the order the viewer lists them. */
	public List<ItemLike> stations() {
		List<ItemLike> out = new ArrayList<>(stations.size());
		for (Supplier<? extends ItemLike> station : stations) {
			out.add(station.get());
		}
		return out;
	}

	/** The GUI areas that open this tab — the screens' own constants, via {@link MachineRecipeViewerTargets}. */
	public List<ClickArea> clickAreas() {
		List<ClickArea> out = new ArrayList<>();
		switch (this) {
			case POLYMERIZING, DISTILLING -> {
				for (MachineRecipeViewerTargets.FluidTarget t : MachineRecipeViewerTargets.FLUID_ALL) {
					if (t.kind().id().equals(id)) {
						out.add(new ClickArea(t.screenClass(), t.progressArea()));
					}
				}
			}
			case ALLOYING -> {
				for (MachineRecipeViewerTargets.AlloyTarget t : MachineRecipeViewerTargets.ALLOY_ALL) {
					out.add(new ClickArea(t.screenClass(), t.progressArea()));
				}
			}
			case CANNING -> {
				for (MachineRecipeViewerTargets.CanningTarget t : MachineRecipeViewerTargets.CANNING_ALL) {
					out.add(new ClickArea(t.screenClass(), t.progressArea()));
				}
			}
			case MACHINE_INFO -> {
				for (MachineRecipeViewerTargets.InfoTarget t : MachineRecipeViewerTargets.INFO_ALL) {
					out.add(new ClickArea(t.screenClass(), t.progressArea()));
				}
			}
			case EVOLUTION_INFO, PLANT_INFO -> {
				// Reached from the item, never from a GUI.
			}
		}
		return out;
	}

	/**
	 * The pages of a page form, in the order both viewers show them; empty for a card form. The evolution
	 * tab holds the solar branches, the rarity grades and the world-made items, in that order.
	 */
	public List<RecipeViewerInfo.Entry> pages() {
		return switch (this) {
			case MACHINE_INFO -> RecipeViewerInfo.machineInfoEntries();
			case EVOLUTION_INFO -> {
				List<RecipeViewerInfo.Entry> pages = new ArrayList<>(RecipeViewerInfo.solarEvolutionEntries());
				pages.addAll(RecipeViewerInfo.mutationGradeEntries());
				pages.addAll(RecipeViewerInfo.worldMadeEntries());
				yield pages;
			}
			case PLANT_INFO -> RecipeViewerInfo.kokSagyzEntries();
			case POLYMERIZING, ALLOYING, DISTILLING, CANNING -> List.of();
		};
	}

	private static Supplier<Component> blockName(Supplier<? extends net.minecraft.world.level.block.Block> block) {
		return () -> block.get().getName();
	}

	private static Supplier<Component> key(String translationKey) {
		return () -> Component.translatable(translationKey);
	}
}
