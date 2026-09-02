package dev.alaindustrial.client.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.CanningExchange;
import dev.alaindustrial.client.compat.MachineRecipeViewerTargets;
import dev.alaindustrial.client.compat.RecipeCategoryTitle;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
import dev.alaindustrial.client.screen.MachineScreen;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.AlloyingRecipe;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.recipe.VanillaSmeltingMirror;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
import net.minecraft.network.chat.Component;
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
		// MOD-019: the Polymerizer's fluid → item family. A single family, so the plain block name titles
		// it. Its card layout is unlike the processing one, so it is registered by hand — but the block
		// still comes from the family, not from a second mention of it here.
		Block polymerizer = ModRecipes.POLYMERIZING.station().get();
		registration.addRecipeCategories(new PolymerizingJeiCategory(AlaJeiRecipeTypes.POLYMERIZING,
				polymerizer, polymerizer.getName(), guiHelper));
		// MOD-064: the alloy smelter. A single family, so the plain block name titles it.
		Block alloySmelter = ModRecipes.ALLOYING.station().get();
		registration.addRecipeCategories(new AlloyingJeiCategory(AlaJeiRecipeTypes.ALLOYING,
				alloySmelter, alloySmelter.getName(), guiHelper));
		// MOD-251: the distillation column's fluid → two-fluids family (the MOD-257 contract,
		// registered now that the real workstation exists).
		Block column = ModRecipes.DISTILLING.station().get();
		registration.addRecipeCategories(new FluidOutputJeiCategory(AlaJeiRecipeTypes.DISTILLING,
				column, column.getName(), guiHelper));
		// MOD-383: the canning machine. No recipe type at all — the cards are computed from the item
		// registry (CanningExchange), so the title comes from its own lang key rather than a block name,
		// and the block cannot come from a recipe family because it has none.
		registration.addRecipeCategories(new CanningJeiCategory(AlaJeiRecipeTypes.CANNING,
				ModContent.CANNING_MACHINE.get(), RecipeCategoryTitle.canning(), guiHelper));
		// MOD-420: machines with no recipe of any kind. Its own category rather than JEI's built-in
		// ingredient info, because a click area opens a category unfocused — see MachineInfoJeiCategory.
		registration.addRecipeCategories(new MachineInfoJeiCategory(AlaJeiRecipeTypes.MACHINE_INFO,
				ModContent.GEOTHERMAL_GENERATOR.get(),
				Component.translatable("jei.alaindustrial.category.machine_info"), guiHelper));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		Collection<RecipeHolder<?>> recipes = clientSyncedRecipes();
		for (ModRecipes.Kind kind : ModRecipes.kinds()) {
			List<RecipeHolder<AlaProcessingRecipe>> machineRecipes = recipesFor(recipes, kind);
			// MOD-086: the electric furnace also runs every vanilla smelt (RecipeType.SMELTING fallback),
			// so its category lists those too — otherwise players opening it see only the mod's recipes and
			// cannot tell the machine smelts ores, food and sand as well.
			if (kind == ModRecipes.SMELTING) {
				machineRecipes.addAll(VanillaSmeltingMirror.mirrorAll(recipes));
			}
			Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI recipe(s) for {}", machineRecipes.size(),
					kind.id());
			registration.addRecipes(AlaJeiRecipeTypes.byKind(kind), machineRecipes);
		}
		// MOD-019: the Polymerizer's recipes live in their own class, so they are collected separately.
		List<RecipeHolder<PolymerizingRecipe>> polymerizing = polymerizingRecipes(recipes);
		Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI recipe(s) for {}", polymerizing.size(),
				ModRecipes.POLYMERIZING.id());
		registration.addRecipes(AlaJeiRecipeTypes.POLYMERIZING, polymerizing);
		// MOD-064: likewise the alloy smelter's own recipe class.
		List<RecipeHolder<AlloyingRecipe>> alloying = alloyingRecipes(recipes);
		Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI recipe(s) for {}", alloying.size(),
				ModRecipes.ALLOYING.id());
		registration.addRecipes(AlaJeiRecipeTypes.ALLOYING, alloying);
		// MOD-251: likewise the distillation column's own recipe class.
		List<RecipeHolder<FluidOutputRecipe>> distilling = distillingRecipes(recipes);
		Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI recipe(s) for {}", distilling.size(),
				ModRecipes.DISTILLING.id());
		registration.addRecipes(AlaJeiRecipeTypes.DISTILLING, distilling);
		// MOD-383: one canning card per accepted food, derived from the (by now frozen) item registry
		// rather than from the recipe map — this machine has no recipes to collect.
		List<CanningExchange.Card> canning = CanningExchange.cards();
		Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI canning card(s)", canning.size());
		registration.addRecipes(AlaJeiRecipeTypes.CANNING, canning);
		// Informational pages (MOD-043): for blocks/items with no crafting recipe — the solar panel
		// evolution line today — JEI's built-in ingredient info gives a paginated, auto-wrapping page.
		// Title + lines come from the same loader-neutral source the REI integration uses.
		for (RecipeViewerInfo.Entry entry : RecipeViewerInfo.solarEvolutionEntries()) {
			List<Component> description = new ArrayList<>();
			description.add(RecipeViewerInfo.title(entry));
			description.addAll(RecipeViewerInfo.buildLines(entry));
			registration.addIngredientInfo((ItemLike) entry.owner().get(),
					description.toArray(new Component[0]));
		}

		// MOD-118: the incubator's rarity grades — a second roll on top of every success, which no
		// recipe card has room for.
		for (RecipeViewerInfo.Entry entry : RecipeViewerInfo.mutationGradeEntries()) {
			List<Component> description = new ArrayList<>();
			description.add(RecipeViewerInfo.title(entry));
			description.addAll(RecipeViewerInfo.buildLines(entry));
			registration.addIngredientInfo((ItemLike) entry.owner().get(),
					description.toArray(new Component[0]));
		}

		// MOD-420: the geothermal generator and the energy condenser. These go into our own category
		// rather than addIngredientInfo, because their GUI click areas have to open something focused.
		List<RecipeViewerInfo.Entry> machineInfo = MachineInfoJeiCategory.pages();
		Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI machine-info page(s)", machineInfo.size());
		registration.addRecipes(AlaJeiRecipeTypes.MACHINE_INFO, machineInfo);
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (ModRecipes.Kind kind : ModRecipes.kinds()) {
			registration.addCraftingStation(AlaJeiRecipeTypes.byKind(kind), kind.station().get());
		}
		registration.addCraftingStation(AlaJeiRecipeTypes.POLYMERIZING, ModRecipes.POLYMERIZING.station().get());
		registration.addCraftingStation(AlaJeiRecipeTypes.ALLOYING, ModRecipes.ALLOYING.station().get());
		// MOD-251: the distillation column performs the distilling family.
		registration.addCraftingStation(AlaJeiRecipeTypes.DISTILLING, ModRecipes.DISTILLING.station().get());
		// MOD-383: the canning machine works its own (recipe-less) category.
		registration.addCraftingStation(AlaJeiRecipeTypes.CANNING, ModContent.CANNING_MACHINE.get());
		// MOD-420: both machine-info pages are "worked at" the machine they describe, so clicking either
		// block in JEI opens the category — the twin of REI's addWorkstations calls.
		registration.addCraftingStation(AlaJeiRecipeTypes.MACHINE_INFO, ModContent.GEOTHERMAL_GENERATOR.get());
		registration.addCraftingStation(AlaJeiRecipeTypes.MACHINE_INFO, ModContent.ENERGY_CONDENSER.get());
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
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
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
		// MOD-019: fluid-fed machines carry their own recipe type, so they list separately —
		// resolved per kind since MOD-251 added a second fluid family (distilling).
		for (MachineRecipeViewerTargets.FluidTarget target : MachineRecipeViewerTargets.FLUID_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registration.addRecipeClickArea(
					target.screenClass(),
					rect.x(), rect.y(), rect.width(), rect.height(),
					AlaJeiRecipeTypes.byFluidKind(target.kind()));
		}
		// MOD-064: the alloy smelter carries its own recipe type too.
		for (MachineRecipeViewerTargets.AlloyTarget target : MachineRecipeViewerTargets.ALLOY_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registration.addRecipeClickArea(
					target.screenClass(),
					rect.x(), rect.y(), rect.width(), rect.height(),
					AlaJeiRecipeTypes.ALLOYING);
		}
		// MOD-383: the canning machine has no recipe kind, so its target list carries only the hitbox.
		for (MachineRecipeViewerTargets.CanningTarget target : MachineRecipeViewerTargets.CANNING_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registration.addRecipeClickArea(
					target.screenClass(),
					rect.x(), rect.y(), rect.width(), rect.height(),
					AlaJeiRecipeTypes.CANNING);
		}
		// MOD-420: machines with no recipe at all open their informational page instead.
		for (MachineRecipeViewerTargets.InfoTarget target : MachineRecipeViewerTargets.INFO_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registration.addRecipeClickArea(
					target.screenClass(),
					rect.x(), rect.y(), rect.width(), rect.height(),
					AlaJeiRecipeTypes.MACHINE_INFO);
		}
		// MOD-080: keep JEI's item grid clear of the upgrade panel + gear tab on every machine screen.
		registration.addGuiContainerHandler((Class) MachineScreen.class, new AlaJeiGuiExtraAreasHandler());
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

	private static List<RecipeHolder<AlloyingRecipe>> alloyingRecipes(Collection<RecipeHolder<?>> recipes) {
		List<RecipeHolder<AlloyingRecipe>> result = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value() instanceof AlloyingRecipe) {
				@SuppressWarnings("unchecked")
				RecipeHolder<AlloyingRecipe> typed = (RecipeHolder<AlloyingRecipe>) holder;
				result.add(typed);
			}
		}
		return result;
	}

	private static List<RecipeHolder<PolymerizingRecipe>> polymerizingRecipes(Collection<RecipeHolder<?>> recipes) {
		List<RecipeHolder<PolymerizingRecipe>> result = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value() instanceof PolymerizingRecipe) {
				@SuppressWarnings("unchecked")
				RecipeHolder<PolymerizingRecipe> typed = (RecipeHolder<PolymerizingRecipe>) holder;
				result.add(typed);
			}
		}
		return result;
	}

	/** The distilling family's recipes (MOD-251) — the {@link #polymerizingRecipes} twin. */
	private static List<RecipeHolder<FluidOutputRecipe>> distillingRecipes(Collection<RecipeHolder<?>> recipes) {
		List<RecipeHolder<FluidOutputRecipe>> result = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value() instanceof FluidOutputRecipe) {
				@SuppressWarnings("unchecked")
				RecipeHolder<FluidOutputRecipe> typed = (RecipeHolder<FluidOutputRecipe>) holder;
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
