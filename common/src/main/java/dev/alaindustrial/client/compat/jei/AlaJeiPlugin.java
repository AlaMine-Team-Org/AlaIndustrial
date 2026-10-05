package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.MachineRecipeViewerTargets;
import dev.alaindustrial.client.compat.RecipeCategoryTitle;
import dev.alaindustrial.client.compat.RecipeViewerForm;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
import dev.alaindustrial.client.screen.GuiRect;
import dev.alaindustrial.client.screen.MachineScreen;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ChargedCraftRecipe;
import dev.alaindustrial.recipe.VanillaSmeltingMirror;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/**
 * The JEI integration — one implementation for both loaders (MOD-558).
 *
 * <p>It used to be two: `fabric/.../compat/jei` (MOD-541 — JEI cannot read REI plugins, so a Fabric
 * player who installs JEI instead of REI must still get the machine categories) and
 * `neoforge/.../compat/jei`, nine files each, of which only this one differed in anything but a
 * javadoc sentence. What differed was the source of the workstation blocks (each loader's own
 * registry class) and one accessor — and the JEI-specific half is identical because both loaders
 * compile against the same {@code jei-26.2-common-api}. Since the workstation is declared on the
 * recipe family itself ({@link ModRecipes.Kind#station()}), nothing loader-specific is left in the
 * plugin body, and the loaders keep only their entry point: the {@code @JeiPlugin} annotation on
 * NeoForge, the {@code jei_mod_plugin} entrypoint in {@code fabric.mod.json} on Fabric (JEI
 * discovers its own key there; Fabric has no annotation scan).
 *
 * <p>Loaded through that entry point only when JEI is installed. With REI — or with no viewer at all
 * — the class is never touched, and on Fabric the REI integration carries the categories instead.
 *
 * <p>Not {@code final}: the NeoForge entry point is an empty annotated subclass.
 */
public class AlaJeiPlugin implements IModPlugin {

	@Override
	public Identifier getPluginUid() {
		return Industrialization.id("jei");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
		// MOD-558: one category per recipe family, replayed from ModRecipes.kinds() — the same list the
		// loaders register the families from, with the machine that works each one declared on the
		// family. Before that this was a static MACHINES table resolved through a hand-written ladder,
		// and a family missing from the ladder threw out of the class initialiser: JEI dropped the whole
		// plugin and the player saw no card of ANY machine (MOD-146).
		for (ModRecipes.Kind kind : ModRecipes.kinds()) {
			Block block = kind.station().get();
			registration.addRecipeCategories(new AlaProcessingJeiCategory(AlaJeiRecipeTypes.byKind(kind),
					block, RecipeCategoryTitle.of(kind, block.getName()), guiHelper));
		}
		// MOD-716: the special recipe forms (polymerizing, alloying, distilling, canning, the page forms), each
		// declared once in RecipeViewerForm and drawn by its JeiRecipeForms binding, in JEI's own tab order.
		for (RecipeViewerForm form : JeiRecipeForms.TAB_ORDER) {
			registration.addRecipeCategories(JeiRecipeForms.of(form).category().apply(guiHelper));
		}
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		Collection<RecipeHolder<?>> recipes = clientSyncedRecipes();
		// MOD-651: one summary line instead of one per family. docs/tools/testing/jei_smoke_check.py reads it
		// (the jei-smoke CI lane), so the `name=count` pairs are a contract, not decoration.
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (ModRecipes.Kind kind : ModRecipes.kinds()) {
			List<RecipeHolder<AlaProcessingRecipe>> machineRecipes = recipesFor(recipes, kind);
			// MOD-086: the electric furnace also runs every vanilla smelt (RecipeType.SMELTING fallback),
			// so its category lists those too — otherwise players opening it see only the mod's recipes and
			// cannot tell the machine smelts ores, food and sand as well.
			if (kind == ModRecipes.SMELTING) {
				machineRecipes.addAll(VanillaSmeltingMirror.mirrorAll(recipes));
			}
			counts.put(kind.id(), machineRecipes.size());
			registration.addRecipes(AlaJeiRecipeTypes.byKind(kind), machineRecipes);
		}
		// MOD-716: the special forms. The three recipe families join the summary's name=count pairs (in JEI's tab
		// order, which lists them as before); the canning cards (MOD-383, derived from the item registry) and the
		// machine-info pages (MOD-420) are counted on their own. The page forms build their lines on every draw
		// (MOD-695), so a later reload of the server's balance shows through.
		Map<RecipeViewerForm, Integer> listed = new EnumMap<>(RecipeViewerForm.class);
		for (RecipeViewerForm form : JeiRecipeForms.TAB_ORDER) {
			int n = JeiRecipeForms.of(form).addRecipes(registration, recipes);
			listed.put(form, n);
			if (form.recipeFamily()) {
				counts.put(form.id(), n);
			}
		}
		Industrialization.LOGGER.info("Registered AlaIndustrial JEI recipes: {}; canning_cards={}; machine_info_pages={}",
				summary(counts), listed.get(RecipeViewerForm.CANNING), listed.get(RecipeViewerForm.MACHINE_INFO));
	}

	private static String summary(Map<String, Integer> counts) {
		StringBuilder out = new StringBuilder();
		counts.forEach((family, n) -> out.append(out.isEmpty() ? "" : ", ").append(family).append('=').append(n));
		return out.toString();
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (ModRecipes.Kind kind : ModRecipes.kinds()) {
			registration.addCraftingStation(AlaJeiRecipeTypes.byKind(kind), kind.station().get());
		}
		// MOD-716: each special form is worked at the machines its RecipeViewerForm names (MOD-420: both
		// machine-info pages at the machine they describe, so clicking either block opens the tab — the twin of
		// REI's addWorkstations calls).
		for (RecipeViewerForm form : JeiRecipeForms.TAB_ORDER) {
			for (ItemLike station : JeiRecipeForms.stations(form)) {
				registration.addCraftingStation(JeiRecipeForms.of(form).type(), station);
			}
		}
		// MOD-076: the electric furnace also performs vanilla smelting — ElectricFurnaceBlockEntity
		// falls back to RecipeType.SMELTING when no alaindustrial:smelting recipe matches — so it is a
		// crafting station for JEI's built-in minecraft:smelting category too (ore smelting,
		// sand → glass, food, etc.). The kinds loop above cannot cover this because its types are
		// IRecipeHolderType<AlaProcessingRecipe>, while vanilla smelting is IRecipeHolderType<SmeltingRecipe>.
		// BLASTING/SMOKING/CAMPFIRE are intentionally NOT added — the electric furnace cannot blast/smoke.
		registration.addCraftingStation(RecipeTypes.SMELTING, ModRecipes.SMELTING.station().get());
		// Iron furnace (MOD-115) — fuel-burning, runs the same vanilla smelting recipes, so it is a
		// station for the built-in smelting category too. It works no family of ours, hence ModContent.
		registration.addCraftingStation(RecipeTypes.SMELTING, ModContent.IRON_FURNACE.get());
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		for (MachineRecipeViewerTargets.Target target : MachineRecipeViewerTargets.ALL) {
			GuiRect rect = target.progressArea();
			// MOD-086: the electric furnace runs vanilla smelting as a fallback (see registerRecipeCatalysts),
			// so its progress arrow opens both categories at once. addRecipeClickArea takes IRecipeType<?>...,
			// and IRecipeHolderType extends IRecipeType, so both types fit one call.
			if (target.kind() == ModRecipes.SMELTING) {
				registration.addRecipeClickArea(
						target.screenClass(),
						rect.x(), rect.y(), rect.width(), rect.height(),
						AlaJeiRecipeTypes.byKind(target.kind()),
						RecipeTypes.SMELTING);
			} else if (MachineRecipeViewerTargets.isSawmill(target.kind())) {
				// MOD-150: the sawmill's arrow opens all four mode categories at once. addRecipeClickArea
				// takes IRecipeType<?>...; IRecipeHolderType extends IRecipeType, so the four fit one call.
				mezz.jei.api.recipe.types.IRecipeType<?>[] types = MachineRecipeViewerTargets.SAWMILL_KINDS.stream()
						.map(AlaJeiRecipeTypes::byKind)
						.toArray(mezz.jei.api.recipe.types.IRecipeType[]::new);
				registration.addRecipeClickArea(
						target.screenClass(),
						rect.x(), rect.y(), rect.width(), rect.height(),
						types);
			} else if (MachineRecipeViewerTargets.isMutation(target.kind())) {
				// MOD-118: likewise for the incubator's three chip modes.
				mezz.jei.api.recipe.types.IRecipeType<?>[] types = MachineRecipeViewerTargets.MUTATION_KINDS.stream()
						.map(AlaJeiRecipeTypes::byKind)
						.toArray(mezz.jei.api.recipe.types.IRecipeType[]::new);
				registration.addRecipeClickArea(
						target.screenClass(),
						rect.x(), rect.y(), rect.width(), rect.height(),
						types);
			} else {
				registration.addRecipeClickArea(
						target.screenClass(),
						rect.x(), rect.y(), rect.width(), rect.height(),
						AlaJeiRecipeTypes.byKind(target.kind()));
			}
		}
		// MOD-716: the special forms' click areas (the polymerizer and distillation column, MOD-019/251; the alloy
		// smelter, MOD-064; the canning machine, MOD-383; the machine-info screens, MOD-420) — each opens its form.
		for (RecipeViewerForm form : JeiRecipeForms.TAB_ORDER) {
			for (RecipeViewerForm.ClickArea area : form.clickAreas()) {
				GuiRect rect = area.rect();
				registration.addRecipeClickArea(
						area.screenClass(),
						rect.x(), rect.y(), rect.width(), rect.height(),
						JeiRecipeForms.of(form).type());
			}
		}
		// MOD-080: keep JEI's item grid clear of the upgrade panel + gear tab on every machine screen.
		registration.addGuiContainerHandler((Class) MachineScreen.class, new AlaJeiGuiExtraAreasHandler());
		// MOD-628: the teleporter remote is not a machine screen, but its tab strip sticks out the same way.
		registration.addGuiContainerHandler(dev.alaindustrial.client.screen.TeleporterRemoteScreen.class,
				new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
					@Override
					public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(
							dev.alaindustrial.client.screen.TeleporterRemoteScreen screen) {
						return screen.extraGuiAreas();
					}
				});
		// MOD-592: drag an item onto a magnet filter cell.
		registration.addGhostIngredientHandler(dev.alaindustrial.client.screen.MagnetScreen.class,
				new MagnetFilterGhostHandler());
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		// Hide items that ship registered-but-invisible for v1.0 (no creative-tab entry, no recipe —
		// see RecipeViewerInfo.hiddenFromRecipeViewerItems). Same list as the REI side, so the
		// recipe viewer grid stays in sync across viewers and loaders.
		List<ItemStack> hidden = new ArrayList<>();
		for (Supplier<? extends ItemLike> item : RecipeViewerInfo.hiddenFromRecipeViewerItems()) {
			hidden.add(new ItemStack(item.get().asItem()));
		}
		runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
		// MOD-683: report how many charge-transfer crafting recipes JEI itself holds. Our plugin does not
		// register these — JEI's vanilla plugin builds its crafting category from the recipes the client
		// was synced — so the count is the only place a missing opt-in (the Fabric recipe-sync list)
		// becomes visible. docs/tools/testing/jei_smoke_check.py compares it with the recipe files on disk.
		long charged = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
				.filter(holder -> holder.value() instanceof ChargedCraftRecipe)
				.count();
		Industrialization.LOGGER.info("AlaIndustrial JEI charged crafts: {}", charged);
	}

	private static List<RecipeHolder<AlaProcessingRecipe>> recipesFor(Collection<RecipeHolder<?>> recipes,
			ModRecipes.Kind kind) {
		List<RecipeHolder<AlaProcessingRecipe>> result = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value() instanceof AlaProcessingRecipe recipe && recipe.kind() == kind) {
				@SuppressWarnings("unchecked")
				RecipeHolder<AlaProcessingRecipe> typed = (RecipeHolder<AlaProcessingRecipe>) holder;
				result.add(typed);
			}
		}
		return result;
	}

	/**
	 * All recipes the client can see, as the plain collection vanilla's {@code RecipeManager} exposes.
	 *
	 * <p>{@code getRecipes()} rather than {@code recipeMap()}: the {@link RecipeMap} accessor is a
	 * NeoForge patch, so the shared implementation has to read what BOTH loaders have. The
	 * JEI-internal fallback below hands a {@link RecipeMap} back either way, which is unwrapped here.
	 */
	private static Collection<RecipeHolder<?>> clientSyncedRecipes() {
		MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			return server.getRecipeManager().getRecipes();
		}
		try {
			Class<?> internal = Class.forName("mezz.jei.common.Internal");
			Method method = internal.getMethod("getClientSyncedRecipes");
			Object value = method.invoke(null);
			if (value instanceof RecipeMap recipes) {
				return recipes.values();
			}
			Industrialization.LOGGER.warn("JEI returned unexpected synced recipe map: {}", value);
		} catch (ReflectiveOperationException | LinkageError error) {
			Industrialization.LOGGER.warn("Could not read JEI synced recipes; AlaIndustrial JEI categories will be empty.", error);
		}
		return List.of();
	}
}
