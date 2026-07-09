package dev.alaindustrial.compat.jei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.MachineRecipeViewerTargets;
import dev.alaindustrial.client.RecipeViewerInfo;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.neoforge.ModBlocksNeoForge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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
			registration.addRecipeCategories(new AlaProcessingJeiCategory(machine.type(), machine.block().get(), guiHelper));
		}
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		RecipeMap recipes = clientSyncedRecipes();
		for (Machine machine : MACHINES) {
			List<RecipeHolder<AlaProcessingRecipe>> machineRecipes = recipesFor(recipes, machine.kind());
			Industrialization.LOGGER.info("Registering {} AlaIndustrial JEI recipe(s) for {}", machineRecipes.size(),
					machine.kind().id());
			registration.addRecipes(machine.type(), machineRecipes);
		}
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
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (Machine machine : MACHINES) {
			registration.addCraftingStation(machine.type(), (ItemLike) machine.block().get());
		}
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		for (MachineRecipeViewerTargets.Target target : MachineRecipeViewerTargets.ALL) {
			MachineRecipeViewerTargets.GuiRect rect = target.progressArea();
			registration.addRecipeClickArea(
					target.screenClass(),
					rect.x(), rect.y(), rect.width(), rect.height(),
					AlaJeiRecipeTypes.byKind(target.kind()));
		}
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
