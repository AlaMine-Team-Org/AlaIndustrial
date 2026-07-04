package dev.alaindustrial.compat.rei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModRecipes;
import java.util.LinkedHashMap;
import java.util.Map;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

/**
 * REI integration for AlaIndustrial machines (MOD-018). Registers one category per processing
 * machine ({@link ModRecipes.Kind}) and fills it from the mod's real vanilla recipes, so players can
 * see what each machine grinds / smelts / compresses / extracts, with its EU cost and time.
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

	private static final Machine[] MACHINES = {
			machine(ModRecipes.MACERATION, ModBlocks.MACERATOR),
			machine(ModRecipes.SMELTING, ModBlocks.ELECTRIC_FURNACE),
			machine(ModRecipes.COMPRESSING, ModBlocks.COMPRESSOR),
			machine(ModRecipes.EXTRACTING, ModBlocks.EXTRACTOR),
	};

	private static final Map<ModRecipes.Kind, CategoryIdentifier<AlaProcessingDisplay>> CATEGORY_BY_KIND =
			new LinkedHashMap<>();
	private static final Map<String, ModRecipes.Kind> KIND_BY_ID = new LinkedHashMap<>();

	static {
		for (Machine m : MACHINES) {
			CATEGORY_BY_KIND.put(m.kind(), m.id());
			KIND_BY_ID.put(m.kind().id(), m.kind());
		}
	}

	/** Category a recipe of the given kind belongs to. */
	static CategoryIdentifier<AlaProcessingDisplay> categoryFor(ModRecipes.Kind kind) {
		return CATEGORY_BY_KIND.get(kind);
	}

	/** Resolve a {@link ModRecipes.Kind} back from its string id (used by the display serializer). */
	static ModRecipes.Kind kindById(String id) {
		return KIND_BY_ID.get(id);
	}

	@Override
	public void registerCategories(CategoryRegistry registry) {
		for (Machine m : MACHINES) {
			registry.add(new AlaProcessingCategory(m.id(), m.block()));
			// Clicking the machine block in REI opens its recipes.
			registry.addWorkstations(m.id(), EntryStacks.of(m.block()));
		}
	}

	@Override
	public void registerDisplays(DisplayRegistry registry) {
		// REI feeds every RecipeHolder from the recipe manager through the filler; we claim the four
		// AlaProcessingRecipe kinds and wrap each into a display (its kind picks the category).
		registry.beginFiller(RecipeHolder.class)
				.filter(holder -> holder.value() instanceof AlaProcessingRecipe)
				.fill(holder -> new AlaProcessingDisplay((AlaProcessingRecipe) holder.value()));
	}
}
