package dev.alaindustrial.compat.rei;

import dev.alaindustrial.client.compat.RecipeCategoryTitle;
import dev.alaindustrial.client.screen.GuiRect;
import dev.alaindustrial.client.screen.MachineScreen;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.MachineRecipeViewerTargets;
import dev.alaindustrial.client.compat.RecipeViewerForm;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModRecipes;
import java.util.function.Supplier;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.entry.renderer.EntryRendererRegistry;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.level.ItemLike;
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

	private static CategoryIdentifier<AlaProcessingDisplay> categoryId(ModRecipes.Kind kind) {
		return CategoryIdentifier.of(Industrialization.id(kind.id()));
	}

	/**
	 * Gives fluid entries a texture (MOD-250). REI 26.2.820 ships a fluid renderer whose drawing code is
	 * commented out, so without this every fluid in REI — ours and vanilla's alike — is an empty slot.
	 * See {@link ReiFluidEntryRenderer}; the previous renderer is kept for the tooltip.
	 */
	@Override
	public void registerEntryRenderers(EntryRendererRegistry registry) {
		registry.register(VanillaEntryTypes.FLUID, (entry, last) -> new ReiFluidEntryRenderer(last));
	}

	@Override
	public void registerCategories(CategoryRegistry registry) {
		// MOD-558: one category per recipe family, replayed from ModRecipes.kinds() — the same list the
		// loaders register the families from. The machine each family is worked at is declared on the
		// family itself (ModRecipes.Kind#station), so this plugin keeps no table of its own to drift.
		for (ModRecipes.Kind kind : ModRecipes.kinds()) {
			Block block = kind.station().get();
			CategoryIdentifier<AlaProcessingDisplay> id = categoryId(kind);
			registry.add(new AlaProcessingCategory(id, block, RecipeCategoryTitle.of(kind, block.getName())));
			// Clicking the machine block in REI opens its recipes.
			registry.addWorkstations(id, EntryStacks.of(block));
		}
		// MOD-076: the electric furnace also performs vanilla smelting — ElectricFurnaceBlockEntity
		// falls back to RecipeType.SMELTING when no alaindustrial:smelting recipe matches — so it is a
		// workstation for REI's built-in "minecraft:plugins/smelting" category too (ore smelting,
		// sand → glass, food, etc.). This mirrors how vanilla FURNACE is registered for that category
		// by REI's DefaultClientPlugin. BuiltinPlugin.SMELTING (the constant) lives in the REI runtime
		// jar, not the compileOnly api jar, so the string form is used to stay compile-clean.
		registry.addWorkstations(
				CategoryIdentifier.of("minecraft", "plugins/smelting"),
				EntryStacks.of(ModBlocks.ELECTRIC_FURNACE));
		// Iron furnace (MOD-115) — fuel-burning station for the same vanilla smelting category.
		registry.addWorkstations(
				CategoryIdentifier.of("minecraft", "plugins/smelting"),
				EntryStacks.of(ModBlocks.IRON_FURNACE));
		// MOD-716: the special recipe forms (polymerizing, distilling, alloying, canning, the evolution, machine
		// and plant pages), each declared once in RecipeViewerForm and drawn by its ReiRecipeForms adapter, in
		// REI's own tab order. A form is worked at the machines it names, so clicking one in REI opens the tab
		// (MOD-420: both machine-info pages at the machine they describe; the evolution pages at the two T2
		// solar panels, the base panel being craftable; the plant pages at none — there is no screen to click).
		for (RecipeViewerForm form : ReiRecipeForms.TAB_ORDER) {
			registry.add(ReiRecipeForms.category(form));
			for (ItemLike station : form.stations()) {
				registry.addWorkstations(ReiRecipeForms.categoryId(form), EntryStacks.of(station));
			}
		}
	}

	@Override
	public void registerDisplays(DisplayRegistry registry) {
		// MOD-716: the client-side displays of the special forms — canning cards (MOD-383) and the evolution,
		// machine and plant pages (MOD-043, MOD-118, MOD-420, MOD-584, MOD-600/638). The recipe families arrive
		// from the server (AlaReiCommonPlugin).
		for (RecipeViewerForm form : ReiRecipeForms.TAB_ORDER) {
			ReiRecipeForms.addDisplays(form, registry);
		}
	}

	@Override
	public void registerEntries(EntryRegistry registry) {
		// Hide items that ship registered-but-invisible for v1.0 (no creative-tab entry, no recipe —
		// see RecipeViewerInfo.hiddenFromRecipeViewerItems). Same list as the NeoForge/JEI side, so the
		// recipe viewer grid stays in sync across loaders.
		for (Supplier<? extends ItemLike> item : RecipeViewerInfo.hiddenFromRecipeViewerItems()) {
			registry.removeEntry(EntryStacks.of(item.get()));
		}
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void registerScreens(ScreenRegistry registry) {
		for (MachineRecipeViewerTargets.Target target : MachineRecipeViewerTargets.ALL) {
			GuiRect rect = target.progressArea();
			// MOD-086: the electric furnace runs vanilla smelting as a fallback (see registerCategories),
			// so its progress arrow opens both categories at once. The string form of the built-in category
			// matches the addWorkstations call above — BuiltinPlugin.SMELTING lives in the runtime jar.
			if (target.kind() == ModRecipes.SMELTING) {
				registerClickArea(registry, target.screenClass(), rect,
						categoryId(target.kind()),
						CategoryIdentifier.of("minecraft", "plugins/smelting"));
			} else if (MachineRecipeViewerTargets.isSawmill(target.kind())) {
				// MOD-150: the sawmill's arrow opens all four mode categories at once.
				CategoryIdentifier<?>[] ids = MachineRecipeViewerTargets.SAWMILL_KINDS.stream()
						.map(AlaReiPlugin::categoryId)
						.toArray(CategoryIdentifier[]::new);
				registerClickArea(registry, target.screenClass(), rect, ids);
			} else if (MachineRecipeViewerTargets.isMutation(target.kind())) {
				// MOD-118: likewise for the incubator's three chip modes.
				CategoryIdentifier<?>[] ids = MachineRecipeViewerTargets.MUTATION_KINDS.stream()
						.map(AlaReiPlugin::categoryId)
						.toArray(CategoryIdentifier[]::new);
				registerClickArea(registry, target.screenClass(), rect, ids);
			} else {
				registerClickArea(registry, target.screenClass(), rect, categoryId(target.kind()));
			}
		}
		// MOD-716: the special forms' click areas (the polymerizer and distillation column, MOD-019/251; the alloy
		// smelter, MOD-064; the canning machine, MOD-383; the machine-info screens, MOD-420) — each opens its form.
		for (RecipeViewerForm form : ReiRecipeForms.TAB_ORDER) {
			for (RecipeViewerForm.ClickArea area : form.clickAreas()) {
				registerClickArea(registry, area.screenClass(), area.rect(), ReiRecipeForms.categoryId(form));
			}
		}
		// MOD-080: keep REI's item grid clear of the upgrade panel + gear tab on every machine screen.
		registry.exclusionZones().register((Class) MachineScreen.class, new AlaReiExclusionZones());
		// MOD-628: the teleporter remote is not a machine screen, but its tab strip sticks out the same way.
		registry.exclusionZones().register(dev.alaindustrial.client.screen.TeleporterRemoteScreen.class,
				screen -> screen.extraGuiAreas().stream()
						.map(r -> new me.shedaniel.math.Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight()))
						.toList());
		// MOD-592: drag an item onto a magnet filter cell.
		registry.registerDraggableStackVisitor(new MagnetFilterDragVisitor());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void registerClickArea(ScreenRegistry registry, Class<? extends AbstractContainerScreen<?>> screenClass,
			GuiRect rect, CategoryIdentifier<?>... categoryIds) {
		registry.registerContainerClickArea(
				new Rectangle(rect.x(), rect.y(), rect.width(), rect.height()),
				(Class) screenClass,
				categoryIds);
	}
}
