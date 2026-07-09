package dev.alaindustrial.compat.rei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.MachineRecipeViewerTargets;
import dev.alaindustrial.client.RecipeViewerInfo;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModRecipes;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.level.block.Block;

/**
 * Client half of the AlaIndustrial REI integration. Registers two kinds of categories:
 * <ul>
 *   <li>one per processing machine ({@link ModRecipes.Kind}) — MOD-018; the recipe→display filling for
 *       these is done server-side by {@link AlaReiCommonPlugin} (MC 26.2 no longer ships full recipes
 *       to the client);</li>
 *   <li>an informational category ({@link AlaInfoCategory}, MOD-043) for blocks/items with no crafting
 *       recipe — the solar panel evolution line. Its displays are pure client-side data (block/item
 *       refs + {@link dev.alaindustrial.Config Config} values), so they are built and added directly in
 *       {@link #registerDisplays(DisplayRegistry)} without going through the server-side registry.</li>
 * </ul>
 *
 * <p>Optional dependency: this class is only loaded when REI itself invokes the {@code rei_client}
 * entrypoint, so the mod runs fine without REI installed.
 */
public class AlaReiPlugin implements REIClientPlugin {

	/** One machine family: its recipe {@link ModRecipes.Kind}, icon/workstation block and REI id. */
	private record Machine(ModRecipes.Kind kind, Block block, CategoryIdentifier<AlaProcessingDisplay> id) {
	}

	private static Machine machine(ModRecipes.Kind kind, Block block) {
		return new Machine(kind, block, CategoryIdentifier.of(Industrialization.id(kind.id())));
	}

	private static CategoryIdentifier<AlaProcessingDisplay> categoryId(ModRecipes.Kind kind) {
		return CategoryIdentifier.of(Industrialization.id(kind.id()));
	}

	private static final Machine[] MACHINES = {
			machine(ModRecipes.MACERATION, ModBlocks.MACERATOR),
			machine(ModRecipes.SMELTING, ModBlocks.ELECTRIC_FURNACE),
			machine(ModRecipes.COMPRESSING, ModBlocks.COMPRESSOR),
			machine(ModRecipes.EXTRACTING, ModBlocks.EXTRACTOR),
	};

	@Override
	public void registerCategories(CategoryRegistry registry) {
		for (Machine m : MACHINES) {
			registry.add(new AlaProcessingCategory(m.id(), m.block()));
			// Clicking the machine block in REI opens its recipes.
			registry.addWorkstations(m.id(), EntryStacks.of(m.block()));
		}
		// Informational category: the T2 solar branches (and future evolution lines) with no crafting
		// recipe. The base solar_panel is craftable, so it is intentionally not linked here.
		registry.add(new AlaInfoCategory());
		registry.addWorkstations(AlaInfoDisplay.CATEGORY, EntryStacks.of(ModBlocks.DAYLIGHT_SOLAR_PANEL));
		registry.addWorkstations(AlaInfoDisplay.CATEGORY, EntryStacks.of(ModBlocks.MOONLIT_SOLAR_PANEL));
	}

	@Override
	public void registerDisplays(DisplayRegistry registry) {
		// Build one static informational display per entry. Pure client-side data (block/item refs +
		// Config values), so it is added directly here rather than synced via ServerDisplayRegistry.
		for (RecipeViewerInfo.Entry entry : RecipeViewerInfo.solarEvolutionEntries()) {
			registry.add(new AlaInfoDisplay(entry));
		}
	}

	@Override
	public void registerScreens(ScreenRegistry registry) {
		for (MachineRecipeViewerTargets.Target target : MachineRecipeViewerTargets.ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registerClickArea(registry, target.screenClass(), rect, categoryId(target.kind()));
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void registerClickArea(ScreenRegistry registry, Class<? extends AbstractContainerScreen<?>> screenClass,
			MachineRecipeViewerTargets.GuiRect rect, CategoryIdentifier<?> categoryId) {
		registry.registerContainerClickArea(
				new Rectangle(rect.x(), rect.y(), rect.width(), rect.height()),
				(Class) screenClass,
				categoryId);
	}
}
