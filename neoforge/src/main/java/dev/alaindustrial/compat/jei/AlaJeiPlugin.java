package dev.alaindustrial.compat.jei;

import dev.alaindustrial.client.compat.RecipeCategoryTitle;
import dev.alaindustrial.client.screen.MachineScreen;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.MachineRecipeViewerTargets;
import dev.alaindustrial.client.compat.RecipeViewerInfo;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.recipe.VanillaSmeltingMirror;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.neoforge.ModBlocksNeoForge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeHolderType;
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
 * JEI integration for the NeoForge build. Mirrors Fabric's REI categories and progress-arrow click
 * areas, but keeps all JEI-specific API references in the NeoForge source set.
 */
@JeiPlugin
public final class AlaJeiPlugin implements IModPlugin {
	private record Machine(
			ModRecipes.Kind kind,
			Supplier<? extends Block> block,
			IRecipeHolderType<AlaProcessingRecipe> type) {
	}

	private static final Machine[] MACHINES = {
			machine(ModRecipes.MACERATION, ModBlocksNeoForge.MACERATOR::get),
			machine(ModRecipes.SMELTING, ModBlocksNeoForge.ELECTRIC_FURNACE::get),
			machine(ModRecipes.COMPRESSING, ModBlocksNeoForge.COMPRESSOR::get),
			machine(ModRecipes.EXTRACTING, ModBlocksNeoForge.EXTRACTOR::get),
			// Sawmill (MOD-150): four mode families, all worked at the same sawmill block.
			machine(ModRecipes.SAWING_PLANKS, ModBlocksNeoForge.SAWMILL::get),
			machine(ModRecipes.SAWING_STICKS, ModBlocksNeoForge.SAWMILL::get),
			machine(ModRecipes.SAWING_SLABS, ModBlocksNeoForge.SAWMILL::get),
			machine(ModRecipes.SAWING_STAIRS, ModBlocksNeoForge.SAWMILL::get),
			// Incubator (MOD-118): three mutation families, all worked at the same incubator block.
			machine(ModRecipes.MUTATION_TRANSFORM, ModBlocksNeoForge.INCUBATOR::get),
			machine(ModRecipes.MUTATION_DUPLICATE, ModBlocksNeoForge.INCUBATOR::get),
			machine(ModRecipes.MUTATION_CREATE, ModBlocksNeoForge.INCUBATOR::get),
	};

	private static Machine machine(ModRecipes.Kind kind, Supplier<? extends Block> block) {
		return new Machine(kind, block, AlaJeiRecipeTypes.byKind(kind));
	}

	@Override
	public Identifier getPluginUid() {
		return Industrialization.id("jei");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
		for (Machine machine : MACHINES) {
			Block block = machine.block().get();
			registration.addRecipeCategories(new AlaProcessingJeiCategory(machine.type(), block,
					RecipeCategoryTitle.of(machine.kind(), block.getName()), guiHelper));
		}
		// MOD-019: the Polymerizer's fluid → item family. A single family, so the plain block name titles it.
		Block polymerizer = ModBlocksNeoForge.POLYMERIZER.get();
		registration.addRecipeCategories(new PolymerizingJeiCategory(AlaJeiRecipeTypes.POLYMERIZING,
				polymerizer, polymerizer.getName(), guiHelper));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		RecipeMap recipes = clientSyncedRecipes();
		for (Machine machine : MACHINES) {
			List<RecipeHolder<AlaProcessingRecipe>> machineRecipes = recipesFor(recipes, machine.kind());
			// MOD-086: the electric furnace also runs every vanilla smelt (RecipeType.SMELTING fallback),
			// so its category lists those too — otherwise players opening it see only the mod's recipes and
			// cannot tell the machine smelts ores, food and sand as well.
			if (machine.kind() == ModRecipes.SMELTING) {
				machineRecipes.addAll(VanillaSmeltingMirror.mirrorAll(recipes));
			}
			Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI recipe(s) for {}", machineRecipes.size(),
					machine.kind().id());
			registration.addRecipes(machine.type(), machineRecipes);
		}
		// MOD-019: the Polymerizer's recipes live in their own class, so they are collected separately.
		List<RecipeHolder<PolymerizingRecipe>> polymerizing = polymerizingRecipes(recipes);
		Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI recipe(s) for {}", polymerizing.size(),
				ModRecipes.POLYMERIZING.id());
		registration.addRecipes(AlaJeiRecipeTypes.POLYMERIZING, polymerizing);
		// Informational pages (MOD-043): for blocks/items with no crafting recipe — the solar panel
		// evolution line today — JEI's built-in ingredient info gives a paginated, auto-wrapping page.
		// Title + lines come from the same loader-neutral source the Fabric REI integration uses.
		for (RecipeViewerInfo.Entry entry : RecipeViewerInfo.solarEvolutionEntries()) {
			List<Component> description = new ArrayList<>();
			description.add(RecipeViewerInfo.title(entry));
			description.addAll(RecipeViewerInfo.buildLines(entry));
			registration.addIngredientInfo((net.minecraft.world.level.ItemLike) entry.owner().get(),
					description.toArray(new Component[0]));
		}

		// MOD-118: the incubator's rarity grades — a second roll on top of every success, which no
		// recipe card has room for.
		for (RecipeViewerInfo.Entry entry : RecipeViewerInfo.mutationGradeEntries()) {
			List<Component> description = new ArrayList<>();
			description.add(RecipeViewerInfo.title(entry));
			description.addAll(RecipeViewerInfo.buildLines(entry));
			registration.addIngredientInfo((net.minecraft.world.level.ItemLike) entry.owner().get(),
					description.toArray(new Component[0]));
		}
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (Machine machine : MACHINES) {
			registration.addCraftingStation(machine.type(), (ItemLike) machine.block().get());
		}
		registration.addCraftingStation(AlaJeiRecipeTypes.POLYMERIZING,
				(ItemLike) ModBlocksNeoForge.POLYMERIZER.get());
		// MOD-076: the electric furnace also performs vanilla smelting — ElectricFurnaceBlockEntity
		// falls back to RecipeType.SMELTING when no alaindustrial:smelting recipe matches — so it is a
		// crafting station for JEI's built-in minecraft:smelting category too (ore smelting,
		// sand → glass, food, etc.). The MACHINES loop above cannot cover this because it is typed to
		// IRecipeHolderType<AlaProcessingRecipe>, while vanilla smelting is IRecipeHolderType<SmeltingRecipe>.
		// BLASTING/SMOKING/CAMPFIRE are intentionally NOT added — the electric furnace cannot blast/smoke.
		registration.addCraftingStation(
				RecipeTypes.SMELTING,
				(ItemLike) ModBlocksNeoForge.ELECTRIC_FURNACE.get());
		// Iron furnace (MOD-115) — fuel-burning, runs the same vanilla smelting recipes, so it is a
		// station for the built-in smelting category too.
		registration.addCraftingStation(
				RecipeTypes.SMELTING,
				(ItemLike) ModBlocksNeoForge.IRON_FURNACE.get());
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
		// MOD-019: fluid-fed machines carry their own recipe type, so they list separately.
		for (MachineRecipeViewerTargets.FluidTarget target : MachineRecipeViewerTargets.FLUID_ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registration.addRecipeClickArea(
					target.screenClass(),
					rect.x(), rect.y(), rect.width(), rect.height(),
					AlaJeiRecipeTypes.POLYMERIZING);
		}
		// MOD-080: keep JEI's item grid clear of the upgrade panel + gear tab on every machine screen.
		registration.addGuiContainerHandler((Class) MachineScreen.class, new AlaJeiGuiExtraAreasHandler());
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		// Hide items that ship registered-but-invisible for v1.0 (no creative-tab entry, no recipe —
		// see RecipeViewerInfo.hiddenFromRecipeViewerItems). Same list as the Fabric/REI side, so the
		// recipe viewer grid stays in sync across loaders.
		List<ItemStack> hidden = new ArrayList<>();
		for (Supplier<? extends ItemLike> item : RecipeViewerInfo.hiddenFromRecipeViewerItems()) {
			hidden.add(new ItemStack(item.get().asItem()));
		}
		runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
	}

	private static List<RecipeHolder<AlaProcessingRecipe>> recipesFor(RecipeMap recipes, ModRecipes.Kind kind) {
		Collection<RecipeHolder<?>> holders = recipes.values();
		List<RecipeHolder<AlaProcessingRecipe>> result = new ArrayList<>();
		for (RecipeHolder<?> holder : holders) {
			if (holder.value() instanceof AlaProcessingRecipe recipe && recipe.kind() == kind) {
				@SuppressWarnings("unchecked")
				RecipeHolder<AlaProcessingRecipe> typed = (RecipeHolder<AlaProcessingRecipe>) holder;
				result.add(typed);
			}
		}
		return result;
	}

	private static List<RecipeHolder<PolymerizingRecipe>> polymerizingRecipes(RecipeMap recipes) {
		List<RecipeHolder<PolymerizingRecipe>> result = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes.values()) {
			if (holder.value() instanceof PolymerizingRecipe) {
				@SuppressWarnings("unchecked")
				RecipeHolder<PolymerizingRecipe> typed = (RecipeHolder<PolymerizingRecipe>) holder;
				result.add(typed);
			}
		}
		return result;
	}

	private static RecipeMap clientSyncedRecipes() {
		MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			return server.getRecipeManager().recipeMap();
		}
		try {
			Class<?> internal = Class.forName("mezz.jei.common.Internal");
			Method method = internal.getMethod("getClientSyncedRecipes");
			Object value = method.invoke(null);
			if (value instanceof RecipeMap recipes) {
				return recipes;
			}
			Industrialization.LOGGER.warn("JEI returned unexpected synced recipe map: {}", value);
		} catch (ReflectiveOperationException | LinkageError error) {
			Industrialization.LOGGER.warn("Could not read JEI synced recipes; AlaIndustrial JEI categories will be empty.", error);
		}
		return RecipeMap.EMPTY;
	}
}
