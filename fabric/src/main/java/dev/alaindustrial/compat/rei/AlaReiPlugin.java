package dev.alaindustrial.compat.rei;

import dev.alaindustrial.client.compat.RecipeCategoryTitle;
import dev.alaindustrial.client.screen.MachineScreen;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.CanningExchange;
import dev.alaindustrial.client.compat.MachineRecipeViewerTargets;
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
			machine(ModRecipes.VULCANIZING, ModBlocks.VULCANIZER),
			machine(ModRecipes.GALVANIC_BATH, ModBlocks.GALVANIC_BATH),
			// Sawmill (MOD-150): four mode families, all worked at the same sawmill block.
			machine(ModRecipes.SAWING_PLANKS, ModBlocks.SAWMILL),
			machine(ModRecipes.SAWING_STICKS, ModBlocks.SAWMILL),
			machine(ModRecipes.SAWING_SLABS, ModBlocks.SAWMILL),
			machine(ModRecipes.SAWING_STAIRS, ModBlocks.SAWMILL),
			// Incubator (MOD-118): three mutation families, all worked at the same incubator block.
			machine(ModRecipes.MUTATION_TRANSFORM, ModBlocks.INCUBATOR),
			machine(ModRecipes.MUTATION_DUPLICATE, ModBlocks.INCUBATOR),
			machine(ModRecipes.MUTATION_CREATE, ModBlocks.INCUBATOR),
	};

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
		for (Machine m : MACHINES) {
			registry.add(new AlaProcessingCategory(m.id(), m.block(),
					RecipeCategoryTitle.of(m.kind(), m.block().getName())));
			// Clicking the machine block in REI opens its recipes.
			registry.addWorkstations(m.id(), EntryStacks.of(m.block()));
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
		// MOD-019: the Polymerizer's fluid → item family. One category, its own display type.
		registry.add(new PolymerizingCategory(ModBlocks.POLYMERIZER, ModBlocks.POLYMERIZER.getName()));
		registry.addWorkstations(PolymerizingDisplay.CATEGORY, EntryStacks.of(ModBlocks.POLYMERIZER));
		// MOD-251: the Distillation Column's fluid → two-fluids family (the MOD-257 display contract,
		// registered now that the real workstation exists).
		registry.add(new FluidOutputCategory(
				CategoryIdentifier.of(Industrialization.id(ModRecipes.DISTILLING.id())),
				ModBlocks.DISTILLATION_COLUMN, ModBlocks.DISTILLATION_COLUMN.getName()));
		registry.addWorkstations(CategoryIdentifier.of(Industrialization.id(ModRecipes.DISTILLING.id())),
				EntryStacks.of(ModBlocks.DISTILLATION_COLUMN));
		// MOD-064: the alloy smelter's multi-component family. One category, its own display type.
		registry.add(new AlloyingCategory(ModBlocks.ALLOY_SMELTER, ModBlocks.ALLOY_SMELTER.getName()));
		registry.addWorkstations(AlloyingDisplay.CATEGORY, EntryStacks.of(ModBlocks.ALLOY_SMELTER));
		// MOD-383: the canning machine. No recipe type at all — the cards are computed from the item
		// registry (CanningExchange), so the title comes from its own lang key rather than a block name.
		registry.add(new CanningCategory(ModBlocks.CANNING_MACHINE, RecipeCategoryTitle.canning()));
		registry.addWorkstations(CanningDisplay.CATEGORY, EntryStacks.of(ModBlocks.CANNING_MACHINE));
		// Informational category: the T2 solar branches (and future evolution lines) with no crafting
		// recipe. The base solar_panel is craftable, so it is intentionally not linked here.
		registry.add(new AlaInfoCategory(AlaInfoDisplay.CATEGORY, "jei.alaindustrial.category.evolution",
				dev.alaindustrial.registry.ModContent.ALIGNMENT_CHIP_DAY.get()));
		registry.addWorkstations(AlaInfoDisplay.CATEGORY, EntryStacks.of(ModBlocks.DAYLIGHT_SOLAR_PANEL));
		registry.addWorkstations(AlaInfoDisplay.CATEGORY, EntryStacks.of(ModBlocks.MOONLIT_SOLAR_PANEL));
		// MOD-420: machines that have no recipe at all — their GUIs used to answer nothing when clicked.
		registry.add(new AlaInfoCategory(AlaInfoDisplay.MACHINE_CATEGORY,
				"jei.alaindustrial.category.machine_info", ModBlocks.GEOTHERMAL_GENERATOR));
		registry.addWorkstations(AlaInfoDisplay.MACHINE_CATEGORY, EntryStacks.of(ModBlocks.GEOTHERMAL_GENERATOR));
		registry.addWorkstations(AlaInfoDisplay.MACHINE_CATEGORY, EntryStacks.of(ModBlocks.ENERGY_CONDENSER));
	}

	@Override
	public void registerDisplays(DisplayRegistry registry) {
		// Build one static informational display per entry. Pure client-side data (block/item refs +
		// Config values), so it is added directly here rather than synced via ServerDisplayRegistry.
		for (RecipeViewerInfo.Entry entry : RecipeViewerInfo.solarEvolutionEntries()) {
			registry.add(new AlaInfoDisplay(entry, AlaInfoDisplay.CATEGORY));
		}
		// MOD-118: the incubator's rarity grades — a second roll on top of every success, which no
		// recipe card has room for.
		for (RecipeViewerInfo.Entry entry : RecipeViewerInfo.mutationGradeEntries()) {
			registry.add(new AlaInfoDisplay(entry, AlaInfoDisplay.CATEGORY));
		}
		// MOD-420: the geothermal generator and the energy condenser — no recipe kind, no recipe JSON,
		// so the only thing a viewer can show for them is this page.
		for (RecipeViewerInfo.Entry entry : RecipeViewerInfo.machineInfoEntries()) {
			registry.add(new AlaInfoDisplay(entry, AlaInfoDisplay.MACHINE_CATEGORY));
		}
		// MOD-383: one canning card per accepted food. Also pure client-side data — the sweep over the
		// (by now frozen) item registry happens on the first call, here.
		for (CanningExchange.Card card : CanningExchange.cards()) {
			registry.add(new CanningDisplay(card));
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
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
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
		// MOD-019: fluid-fed machines carry their own display type, so they list separately.
		for (MachineRecipeViewerTargets.FluidTarget target : MachineRecipeViewerTargets.FLUID_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			// Per-kind category id (MOD-251): polymerizing and distilling each open their own card.
			registerClickArea(registry, target.screenClass(), rect,
					CategoryIdentifier.of(Industrialization.id(target.kind().id())));
		}
		// MOD-064: the alloy smelter likewise carries its own display type.
		for (MachineRecipeViewerTargets.AlloyTarget target : MachineRecipeViewerTargets.ALLOY_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registerClickArea(registry, target.screenClass(), rect, AlloyingDisplay.CATEGORY);
		}
		// MOD-383: the canning machine has no recipe kind, so its target list carries only the hitbox.
		for (MachineRecipeViewerTargets.CanningTarget target : MachineRecipeViewerTargets.CANNING_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registerClickArea(registry, target.screenClass(), rect, CanningDisplay.CATEGORY);
		}
		// MOD-420: machines with no recipe at all open their informational page instead.
		for (MachineRecipeViewerTargets.InfoTarget target : MachineRecipeViewerTargets.INFO_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registerClickArea(registry, target.screenClass(), rect, AlaInfoDisplay.MACHINE_CATEGORY);
		}
		// MOD-080: keep REI's item grid clear of the upgrade panel + gear tab on every machine screen.
		registry.exclusionZones().register((Class) MachineScreen.class, new AlaReiExclusionZones());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void registerClickArea(ScreenRegistry registry, Class<? extends AbstractContainerScreen<?>> screenClass,
			MachineRecipeViewerTargets.GuiRect rect, CategoryIdentifier<?>... categoryIds) {
		registry.registerContainerClickArea(
				new Rectangle(rect.x(), rect.y(), rect.width(), rect.height()),
				(Class) screenClass,
				categoryIds);
	}
}
